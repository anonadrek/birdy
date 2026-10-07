#!/usr/bin/env node
// Checks rendered "See the song" videos before anyone posts them:
//   node tools/social/check-video.mjs <out>/<en-slug> [<out>/<en-slug> ...]
// 1. ffprobe: 1080 x 1920, 30 fps, H.264 + AAC, frame count and length as planned.
// 2. Loudness of the audio (ebur128): integrated about -16 LUFS, true peak under -1 dBFS.
// 3. QA frames at 0, 25, 50, 75 and 100 % of the video into <dir>/qa/ (look at them).
// 4. Sync, picture: the playhead's x in the decoded frame equals 88 + 904 * (t - A0) / D.
// 5. Sync, sound: the brightness of the drawn spectrogram, column by column, lines up with the
//    loudness of the video's own audio in the same frequency band (best lag about 0 s).
import { readFile, mkdir } from 'node:fs/promises';
import { join } from 'node:path';
import { run } from './lib/proc.mjs';
import { measureLoudness } from './lib/audio.mjs';

const W = 1080;
const H = 1920;
const PANEL = { x: 88, y: 430, w: 904, h: 640 };

async function frameRgb(mp4, n) {
  const { stdout } = await run('ffmpeg', ['-hide_banner', '-v', 'error', '-i', mp4, '-vf', `select=eq(n\\,${n})`, '-frames:v', '1', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-']);
  if (stdout.length !== W * H * 3) throw new Error(`frame ${n}: got ${stdout.length} bytes`);
  return stdout;
}

async function framePng(mp4, n, out) {
  await run('ffmpeg', ['-hide_banner', '-v', 'error', '-y', '-i', mp4, '-vf', `select=eq(n\\,${n})`, '-frames:v', '1', out]);
}

/** x of the playhead: the brightest warm pixel on a row just above the panel (only the playhead reaches there). */
function playheadX(rgb) {
  const y = PANEL.y - 6;
  let best = -1;
  let bestX = -1;
  for (let x = PANEL.x - 10; x <= PANEL.x + PANEL.w + 10; x++) {
    const i = (y * W + x) * 3;
    const score = rgb[i] + rgb[i + 1] - rgb[i + 2];
    if (score > best) {
      best = score;
      bestX = x;
    }
  }
  return bestX;
}

/** Band-limited RMS envelope of the video's audio, one value per 10 ms. */
async function audioEnvelope(mp4, fmin, fmax) {
  const sr = 16000;
  const hp = Math.max(20, fmin);
  const lp = Math.min(sr / 2 - 100, fmax);
  const { stdout } = await run('ffmpeg', ['-hide_banner', '-v', 'error', '-i', mp4, '-vn', '-ac', '1', '-ar', String(sr), '-af', `highpass=f=${hp},lowpass=f=${lp}`, '-f', 'f32le', '-']);
  const x = new Float32Array(stdout.buffer.slice(stdout.byteOffset, stdout.byteOffset + stdout.byteLength));
  const hop = sr / 100;
  const env = new Float64Array(Math.floor(x.length / hop));
  for (let k = 0; k < env.length; k++) {
    let s = 0;
    for (let i = k * hop; i < (k + 1) * hop; i++) s += x[i] * x[i];
    env[k] = Math.sqrt(s / hop);
  }
  return env;
}

function corr(a, b) {
  const n = Math.min(a.length, b.length);
  let ma = 0;
  let mb = 0;
  for (let i = 0; i < n; i++) {
    ma += a[i];
    mb += b[i];
  }
  ma /= n;
  mb /= n;
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

async function check(dir) {
  const info = JSON.parse(await readFile(join(dir, 'render-info.json'), 'utf8'));
  const mp4 = join(dir, 'see-the-song.mp4');
  const { A0, D, frames, T } = info.timeline;
  const fps = info.fps;
  const problems = [];
  const report = { dir };

  // 1. Streams
  const probe = JSON.parse((await run('ffprobe', ['-v', 'error', '-count_frames', '-show_entries', 'format=duration:stream=codec_type,codec_name,width,height,r_frame_rate,nb_read_frames,sample_rate,channels', '-of', 'json', mp4])).stdout.toString());
  const v = probe.streams.find((s) => s.codec_type === 'video');
  const a = probe.streams.find((s) => s.codec_type === 'audio');
  report.ffprobe = { duration: Number(probe.format.duration), video: v, audio: a };
  if (!v || v.codec_name !== 'h264' || v.width !== 1080 || v.height !== 1920 || v.r_frame_rate !== '30/1') problems.push('video stream is not H.264 1080x1920 at 30 fps');
  if (Number(v?.nb_read_frames) !== frames) problems.push(`video has ${v?.nb_read_frames} frames, planned ${frames}`);
  if (!a || a.codec_name !== 'aac') problems.push('no AAC audio');
  if (Math.abs(Number(probe.format.duration) - T) > 0.05) problems.push(`duration ${probe.format.duration} s, planned ${T} s`);

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

  // 4. Playhead position at 25, 50 and 75 % of the song
  report.playhead = [];
  for (const q of [0.25, 0.5, 0.75]) {
    const n = Math.round((A0 + q * D) * fps);
    const t = n / fps;
    const expected = PANEL.x + (PANEL.w * (t - A0)) / D;
    const found = playheadX(await frameRgb(mp4, n));
    report.playhead.push({ frame: n, t: Number(t.toFixed(3)), expectedX: Number(expected.toFixed(1)), foundX: found });
    if (Math.abs(found - expected) > 3) problems.push(`playhead at ${t.toFixed(2)} s is at x ${found}, expected ${expected.toFixed(1)}`);
  }

  // 5. Drawn spectrogram vs the audio: use a frame before the photo fades in (70 % of the song).
  const n = Math.round((A0 + 0.68 * D) * fps);
  const t = n / fps;
  const rgb = await frameRgb(mp4, n);
  const right = Math.floor(PANEL.x + (PANEL.w * (t - A0)) / D) - 10;
  const cols = [];
  for (let x = PANEL.x + 4; x < right; x++) {
    let s = 0;
    for (let y = PANEL.y + 2; y < PANEL.y + PANEL.h - 2; y++) {
      const i = (y * W + x) * 3;
      s += rgb[i] + rgb[i + 1] + rgb[i + 2];
    }
    cols.push({ tau: ((x - PANEL.x) / PANEL.w) * D, b: s });
  }
  const env = await audioEnvelope(mp4, info.spectrogram.fmin, info.spectrogram.fmax);
  const envAt = (sec) => {
    const k = Math.round(sec * 100);
    return k >= 0 && k < env.length ? env[k] : 0;
  };
  let best = { lag: 0, r: -2 };
  for (let lagMs = -300; lagMs <= 300; lagMs += 10) {
    const r = corr(cols.map((c) => c.b), cols.map((c) => envAt(c.tau + A0 + lagMs / 1000)));
    if (r > best.r) best = { lag: lagMs / 1000, r };
  }
  report.syncSound = { frame: n, columns: cols.length, bestLagSec: best.lag, correlation: Number(best.r.toFixed(3)) };
  if (Math.abs(best.lag) > 0.05) problems.push(`spectrogram and audio are ${best.lag} s apart`);
  if (best.r < 0.3) problems.push(`spectrogram and audio correlate weakly (${best.r.toFixed(2)})`);

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
