#!/usr/bin/env node
// Proves that a sound animation is in step with its audio, from the rendered MP4 alone:
//   node tools/social/check-sound.mjs <out>/previews/<option>-<slug>.json [...]
// (the .json is the render info written next to the .mp4).
// Every frame is decoded once and measured where the style draws:
//   sonagram, notes  the time caret's x against x0 + w * (t - A0) / D, every frame of the song
//   sonagram         ink per column of the finished drawing against the audio's loudness (best lag)
//   waveform         the ribbon's height near its head, frame by frame, against the audio (best lag)
//   halo             the ring's brightness, frame by frame, against the audio (best lag)
//   notes            each note is blank one frame before its onset and inked shortly after,
//                    and the onsets found again in the MP4's own audio match the notes' times
import { readFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { run } from './lib/proc.mjs';
import { notesData } from './lib/sound-data.mjs';

const W = 1080;
const H = 1920;
const FRAME = W * H * 3;

/** Calls onFrame(index, rgb) for every decoded frame. */
function eachFrame(mp4, onFrame) {
  return new Promise((resolve, reject) => {
    const ff = spawn('ffmpeg', ['-hide_banner', '-v', 'error', '-i', mp4, '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-'], { stdio: ['ignore', 'pipe', 'pipe'] });
    let buf = Buffer.alloc(0);
    let n = 0;
    let err = '';
    ff.stderr.on('data', (d) => (err += d));
    ff.stdout.on('data', (d) => {
      buf = Buffer.concat([buf, d]);
      while (buf.length >= FRAME) {
        onFrame(n++, buf.subarray(0, FRAME));
        buf = buf.subarray(FRAME);
      }
    });
    ff.on('error', reject);
    ff.on('close', (code) => (code === 0 ? resolve(n) : reject(new Error(err))));
  });
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
  return { db: out, x, sr };
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
function bestLag(ts, values, audio, offset = 0) {
  const at = (sec) => {
    const k = Math.round(sec * 100);
    return k >= 0 && k < audio.length ? audio[k] : audio[0];
  };
  let best = { lag: 0, r: -2 };
  for (let ms = -300; ms <= 300; ms += 10) {
    const r = corr(values, ts.map((t) => at(t + offset + ms / 1000)));
    if (r > best.r) best = { lag: ms / 1000, r };
  }
  return { bestLagSec: best.lag, correlation: Number(best.r.toFixed(3)) };
}

function isRust(rgb, i) {
  return Math.abs(rgb[i] - 154) < 45 && Math.abs(rgb[i + 1] - 69) < 45 && Math.abs(rgb[i + 2] - 38) < 45;
}

function caretX(rgb, y, x0, x1) {
  let s = 0;
  let n = 0;
  for (let x = x0; x <= x1; x++) {
    if (isRust(rgb, (y * W + x) * 3)) {
      s += x;
      n++;
    }
  }
  return n ? s / n : NaN;
}

async function check(infoPath) {
  const info = JSON.parse(await readFile(infoPath, 'utf8'));
  const mp4 = infoPath.replace(/\.json$/, '.mp4');
  const { A0, D, E } = info.timeline;
  const fps = info.fps;
  const si = info.layout.styleInfo;
  const style = info.style;
  const audio = await audioDb(mp4, info.spectrogram.fmin, info.spectrogram.fmax);
  const report = { clip: mp4, style };
  const problems = [];
  const series = { t: [], v: [] };
  const caret = [];
  const songFrames = (n) => n / fps > A0 + 0.05 && n / fps < E - 0.05;
  let finalFrame = null;
  const finalN = Math.round((A0 + 0.97 * D) * fps);
  const noteFrames = new Map();
  const marks = style === 'notes' ? si.marks.filter((m, i, all) => all.slice(0, i).every((p) => Math.hypot(p.x - m.x, p.y - m.y) > 40)) : [];
  for (const m of marks) {
    noteFrames.set(Math.floor((A0 + m.t0) * fps) - 1, [...(noteFrames.get(Math.floor((A0 + m.t0) * fps) - 1) || []), { m, when: 'before' }]);
    noteFrames.set(Math.ceil((A0 + m.t0) * fps) + 3, [...(noteFrames.get(Math.ceil((A0 + m.t0) * fps) + 3) || []), { m, when: 'after' }]);
  }
  const noteResults = [];

  await eachFrame(mp4, (n, rgb) => {
    const t = n / fps;
    if ((style === 'sonagram' || style === 'notes') && songFrames(n)) {
      const x0 = style === 'sonagram' ? si.panel.x : si.x0;
      const w = style === 'sonagram' ? si.panel.w : si.xw;
      const found = caretX(rgb, Math.round(si.caretY + 9), x0 - 20, x0 + w + 20);
      caret.push({ t, expected: x0 + (w * (t - A0)) / D, found });
    }
    if (style === 'sonagram' && n === finalN) finalFrame = Buffer.from(rgb);
    if (style === 'waveform' && songFrames(n)) {
      const x = si.probeX;
      let h = 0;
      for (let d = 0; d < si.hmax + 20; d++) {
        const i = ((si.cy - d) * W + x) * 3;
        if (rgb[i] + rgb[i + 1] + rgb[i + 2] < 330) break;
        h = d;
      }
      series.t.push(t - A0 - si.probeAgeSec);
      series.v.push(h);
    }
    if (style === 'halo' && songFrames(n)) {
      let s = 0;
      for (let r = si.base + 6; r <= si.base + si.lmax; r += 4) {
        for (let a = 0; a < 360; a += 1) {
          const x = Math.round(si.cx + r * Math.cos((a * Math.PI) / 180));
          const y = Math.round(si.cy + r * Math.sin((a * Math.PI) / 180));
          const i = (y * W + x) * 3;
          s += rgb[i] + rgb[i + 1] + rgb[i + 2];
        }
      }
      series.t.push(t - A0);
      series.v.push(s);
    }
    for (const { m, when } of noteFrames.get(n) || []) {
      const i = (Math.round(m.y) * W + Math.round(m.x)) * 3;
      const sum = rgb[i] + rgb[i + 1] + rgb[i + 2];
      noteResults.push({ t0: m.t0, when, sum, ok: when === 'before' ? sum > 600 : sum < 450 });
    }
  });

  if (caret.length) {
    const errs = caret.filter((c) => Number.isFinite(c.found)).map((c) => Math.abs(c.found - c.expected));
    report.caret = { frames: caret.length, measured: errs.length, maxErrorPx: Number(Math.max(...errs).toFixed(2)), meanErrorPx: Number((errs.reduce((s, v) => s + v, 0) / errs.length).toFixed(2)) };
    if (errs.length < caret.length * 0.95 || Math.max(...errs) > 3) problems.push(`caret off: ${JSON.stringify(report.caret)}`);
  }
  if (style === 'sonagram' && finalFrame) {
    const P = si.panel;
    const xEnd = P.x + P.w * 0.97 - 12;
    const ts = [];
    const vs = [];
    for (let x = P.x + 2; x < xEnd; x++) {
      let dark = 0;
      for (let y = P.y + 2; y < P.y + P.h - 2; y++) {
        const i = (y * W + x) * 3;
        dark += 765 - (finalFrame[i] + finalFrame[i + 1] + finalFrame[i + 2]);
      }
      ts.push(((x - P.x) / P.w) * D);
      vs.push(dark);
    }
    report.ink = bestLag(ts, vs, audio.db, A0);
  }
  if (series.v.length) report.motion = { frames: series.v.length, ...bestLag(series.t, series.v, audio.db, A0) };
  const lagReport = report.ink || report.motion;
  if (lagReport) {
    if (Math.abs(lagReport.bestLagSec) > 0.05) problems.push(`visual and audio are ${lagReport.bestLagSec} s apart`);
    if (lagReport.correlation < 0.4) problems.push(`weak correlation ${lagReport.correlation}`);
  }
  if (style === 'notes') {
    const ok = noteResults.filter((r) => r.ok).length;
    report.landing = { tests: noteResults.length, passed: ok };
    if (ok < noteResults.length) problems.push(`notes landing: ${ok}/${noteResults.length}`);
    // The notes' onsets found again in the MP4's own audio (the clip starts A0 into the track).
    const start = Math.round(A0 * audio.sr);
    const again = notesData(audio.x.subarray(start, start + Math.round(D * audio.sr)), { sr: audio.sr }).syllables.map((s) => s.t0);
    const diffs = si.marks.map((m) => Math.min(...again.map((a) => Math.abs(a - m.t0))));
    const matched = diffs.filter((d) => d <= 0.03);
    report.onsets = { notes: si.marks.length, foundInMp4: again.length, matchedWithin30ms: matched.length, meanAbsErrorMs: Number(((matched.reduce((s, v) => s + v, 0) / (matched.length || 1)) * 1000).toFixed(1)) };
    if (matched.length < si.marks.length * 0.8) problems.push(`onsets: ${JSON.stringify(report.onsets)}`);
  }
  report.ok = problems.length === 0;
  report.problems = problems;
  return report;
}

let failed = 0;
for (const p of process.argv.slice(2)) {
  const r = await check(p);
  console.log(JSON.stringify(r));
  if (!r.ok) failed++;
}
process.exit(failed ? 1 : 0);
