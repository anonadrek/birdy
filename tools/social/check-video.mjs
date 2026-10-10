#!/usr/bin/env node
// Checks rendered "See the song" videos before anyone posts them, from the files alone:
//   node tools/social/check-video.mjs <out>/<en-slug> [<out>/<en-slug> ...]
// 1. ffprobe: 1080 x 1920, 30 fps, H.264 + AAC, frame count as planned, at most 30 s in all.
// 2. Loudness of the audio (ebur128): integrated about -16 LUFS, true peak under -1 dBFS.
// 3. QA frames at 0, 25, 50, 75 and 100 % of the video into <dir>/qa/ (look at them).
// 4. Sync: the ring's brightness, frame by frame through the song, follows the loudness of the
//    video's own audio in the ring's band (best lag about 0 s, clear correlation).
// 5. The pause: between "settled" and the reveal every frame is the same (one breath of stillness).
// 6. The cover: inside the circle there is only the dark background and the question mark,
//    no photo (every pixel lies on the line between those two colours).
import { readFile, mkdir } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { join } from 'node:path';
import { run } from './lib/proc.mjs';
import { measureLoudness } from './lib/audio.mjs';
import { MAX_VIDEO_SEC } from './lib/render.mjs';

const W = 1080;
const H = 1920;
const RING = { cx: 482, cy: 790, r: 210, gap: 20, lmax: 140 }; // as in stage/see-the-song.html
const CROP = { x: RING.cx - 380, y: RING.cy - 380, w: 760, h: 760 };

/** Calls onFrame(index, rgb) for every frame, cropped to the ring (CROP). */
function eachRingFrame(mp4, onFrame) {
  const size = CROP.w * CROP.h * 3;
  return new Promise((resolve, reject) => {
    const ff = spawn('ffmpeg', ['-hide_banner', '-v', 'error', '-i', mp4, '-vf', `crop=${CROP.w}:${CROP.h}:${CROP.x}:${CROP.y}`, '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-'], { stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true });
    let buf = Buffer.alloc(0);
    let n = 0;
    let err = '';
    ff.stderr.on('data', (d) => (err += d));
    ff.stdout.on('data', (d) => {
      buf = Buffer.concat([buf, d]);
      while (buf.length >= size) {
        onFrame(n++, buf.subarray(0, size));
        buf = buf.subarray(size);
      }
    });
    ff.on('error', reject);
    ff.on('close', (code) => (code === 0 ? resolve(n) : reject(new Error(err))));
  });
}

async function framePng(mp4, n, out) {
  await run('ffmpeg', ['-hide_banner', '-v', 'error', '-y', '-i', mp4, '-vf', `select=eq(n\\,${n})`, '-frames:v', '1', out]);
}

async function imageRgb(file) {
  const { stdout } = await run('ffmpeg', ['-hide_banner', '-v', 'error', '-i', file, '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-']);
  if (stdout.length !== W * H * 3) throw new Error(`${file}: got ${stdout.length} bytes`);
  return stdout;
}

/** Band-limited loudness of the MP4's audio in dB, 100 values per second. */
async function audioDb(mp4, fmin, fmax) {
  const sr = 32000;
  const { stdout } = await run('ffmpeg', ['-hide_banner', '-v', 'error', '-i', mp4, '-vn', '-ac', '1', '-ar', String(sr), '-af', `highpass=f=${Math.max(60, fmin)},lowpass=f=${Math.min(15000, fmax)}`, '-f', 'f32le', '-']);
  const x = new Float32Array(stdout.buffer.slice(stdout.byteOffset, stdout.byteOffset + stdout.byteLength));
  const hop = sr / 100;
  const out = new Float64Array(Math.floor(x.length / hop));
  for (let k = 0; k < out.length; k++) {
    let s = 0;
    for (let i = k * hop; i < (k + 1) * hop; i++) s += x[i] * x[i];
    out[k] = 10 * Math.log10(s / hop + 1e-10);
  }
  return out;
}

function corr(a, b) {
  const n = Math.min(a.length, b.length);
  const ma = a.reduce((s, v) => s + v, 0) / n;
  const mb = b.reduce((s, v) => s + v, 0) / n;
  let num = 0;
  let da = 0;
  let db = 0;
  for (let i = 0; i < n; i++) {
    num += (a[i] - ma) * (b[i] - mb);
    da += (a[i] - ma) ** 2;
    db += (b[i] - mb) ** 2;
  }
  return num / Math.sqrt(da * db || 1);
}

/** Best lag (s) in -0.3..0.3 between a visual series sampled at times ts and the audio dB curve. */
function bestLag(ts, values, audio) {
  const at = (sec) => {
    const k = Math.round(sec * 100);
    return k >= 0 && k < audio.length ? audio[k] : audio[0];
  };
  let best = { lag: 0, r: -2 };
  for (let ms = -300; ms <= 300; ms += 10) {
    const r = corr(values, ts.map((t) => at(t + ms / 1000)));
    if (r > best.r) best = { lag: ms / 1000, r };
  }
  return { bestLagSec: best.lag, correlation: Number(best.r.toFixed(3)) };
}

/** Sum of brightness over the annulus where the bars are. */
function ringBrightness(rgb) {
  const base = RING.r + RING.gap;
  let s = 0;
  for (let r = base + 6; r <= base + RING.lmax; r += 4) {
    for (let a = 0; a < 360; a++) {
      const x = Math.round(RING.cx - CROP.x + r * Math.cos((a * Math.PI) / 180));
      const y = Math.round(RING.cy - CROP.y + r * Math.sin((a * Math.PI) / 180));
      const i = (y * CROP.w + x) * 3;
      s += rgb[i] + rgb[i + 1] + rgb[i + 2];
    }
  }
  return s;
}

function meanAbsDiff(a, b) {
  let s = 0;
  for (let i = 0; i < a.length; i++) s += Math.abs(a[i] - b[i]);
  return s / a.length;
}

/**
 * Share of pixels inside the circle that are neither the dark background nor the question
 * mark's apricot (nor a blend of the two): any photo would show up here.
 */
function offPalette(rgb, width = W) {
  const dark = [36, 24, 15];
  const apricot = [242, 178, 122];
  const d = apricot.map((v, i) => v - dark[i]);
  const len = Math.hypot(...d);
  const u = d.map((v) => v / len);
  let off = 0;
  let n = 0;
  for (let y = -195; y <= 195; y++) {
    for (let x = -195; x <= 195; x++) {
      if (x * x + y * y > 195 * 195) continue;
      const i = ((RING.cy + y) * width + RING.cx + x) * 3;
      const p = [rgb[i] - dark[0], rgb[i + 1] - dark[1], rgb[i + 2] - dark[2]];
      const along = p[0] * u[0] + p[1] * u[1] + p[2] * u[2];
      const dist = Math.hypot(p[0] - along * u[0], p[1] - along * u[1], p[2] - along * u[2]);
      if (dist > 30) off++;
      n++;
    }
  }
  return off / n;
}

async function check(dir) {
  const info = JSON.parse(await readFile(join(dir, 'render-info.json'), 'utf8'));
  const mp4 = join(dir, 'see-the-song.mp4');
  const { A0, E, settled, R, frames, T } = info.timeline;
  const fps = info.fps;
  const problems = [];
  const report = { dir };

  // 1. Streams and length
  const probe = JSON.parse((await run('ffprobe', ['-v', 'error', '-count_frames', '-show_entries', 'format=duration:stream=codec_type,codec_name,width,height,r_frame_rate,nb_read_frames,sample_rate,channels', '-of', 'json', mp4])).stdout.toString());
  const v = probe.streams.find((s) => s.codec_type === 'video');
  const a = probe.streams.find((s) => s.codec_type === 'audio');
  report.ffprobe = { duration: Number(probe.format.duration), video: v, audio: a };
  if (!v || v.codec_name !== 'h264' || v.width !== W || v.height !== H || v.r_frame_rate !== '30/1') problems.push('video stream is not H.264 1080x1920 at 30 fps');
  if (Number(v?.nb_read_frames) !== frames) problems.push(`video has ${v?.nb_read_frames} frames, planned ${frames}`);
  if (!a || a.codec_name !== 'aac') problems.push('no AAC audio');
  if (Math.abs(Number(probe.format.duration) - T) > 0.05) problems.push(`duration ${probe.format.duration} s, planned ${T} s`);
  if (Number(probe.format.duration) > MAX_VIDEO_SEC + 0.05) problems.push(`the video is ${probe.format.duration} s, over ${MAX_VIDEO_SEC} s`);

  // 2. Loudness
  report.loudness = await measureLoudness(mp4);
  if (Math.abs(report.loudness.integrated + 16) > 1) problems.push(`integrated loudness ${report.loudness.integrated} LUFS is not about -16`);
  if (!(report.loudness.truePeak < -1)) problems.push(`true peak ${report.loudness.truePeak} dBFS`);

  // 3. QA frames
  const qa = join(dir, 'qa');
  await mkdir(qa, { recursive: true });
  report.frames = [];
  for (const pct of [0, 25, 50, 75, 100]) {
    const n = Math.min(frames - 1, Math.round(((frames - 1) * pct) / 100));
    const file = join(qa, `frame-${String(pct).padStart(3, '0')}.png`);
    await framePng(mp4, n, file);
    report.frames.push({ pct, frame: n, t: Number((n / fps).toFixed(3)), file });
  }

  // 4 and 5: one pass over the frames (cropped to the ring)
  const series = { t: [], v: [] };
  const pauseFirst = Math.ceil((settled + 0.1) * fps);
  const pauseLast = Math.floor((R - 0.05) * fps);
  let first = null;
  let last = null;
  await eachRingFrame(mp4, (n, rgb) => {
    const t = n / fps;
    if (t > A0 + 0.05 && t < E - 0.05) {
      series.t.push(t);
      series.v.push(ringBrightness(rgb));
    }
    if (n === pauseFirst) first = Buffer.from(rgb);
    if (n === pauseLast) last = Buffer.from(rgb);
  });
  const audio = await audioDb(mp4, info.halo.fmin, info.halo.fmax);
  report.sync = { frames: series.v.length, ...bestLag(series.t, series.v, audio) };
  if (Math.abs(report.sync.bestLagSec) > 0.05) problems.push(`the ring and the audio are ${report.sync.bestLagSec} s apart`);
  if (report.sync.correlation < 0.4) problems.push(`the ring follows the audio weakly (${report.sync.correlation})`);
  report.pause = { from: Number((pauseFirst / fps).toFixed(3)), to: Number((pauseLast / fps).toFixed(3)), meanAbsDiff: first && last ? Number(meanAbsDiff(first, last).toFixed(3)) : null };
  if (!(report.pause.meanAbsDiff < 1)) problems.push(`the picture moves during the pause (${report.pause.meanAbsDiff})`);

  // 6. Cover
  const cover = await imageRgb(join(dir, 'cover.jpg'));
  report.cover = { offPalette: Number(offPalette(cover).toFixed(4)), at: info.coverAt };
  if (report.cover.offPalette > 0.01) problems.push(`the cover may show the photo (${(report.cover.offPalette * 100).toFixed(1)} % of the circle is off the palette)`);
  if (!(info.coverAt >= A0 && info.coverAt <= E)) problems.push(`the cover is not taken during the song (${info.coverAt} s)`);

  report.ok = problems.length === 0;
  report.problems = problems;
  return report;
}

let failed = 0;
for (const dir of process.argv.slice(2)) {
  const r = await check(dir);
  console.log(JSON.stringify(r, null, 2));
  if (!r.ok) failed++;
}
process.exit(failed ? 1 : 0);
