// Data for the "Song halo" (chosen 2026-10-07): a ring of bars around the hidden bird, one bar
// per frequency band, that answers the sound. Everything is computed in Node from the clip that
// plays, one value per band and video frame, so the stage only looks values up by time:
// deterministic and in step with the audio.
import { powerDb, bandOf, SAMPLE_RATE } from './spectrogram.mjs';

function clamp(x, a = 0, b = 1) {
  return x < a ? a : x > b ? b : x;
}

function percentile(values, p) {
  const s = Float32Array.from(values).sort();
  return s[Math.min(s.length - 1, Math.max(0, Math.round(p * (s.length - 1))))];
}

/**
 * Loudness in `bands` log-spaced bands across the bird's band, for every video frame from the
 * start of the clip. Each band is measured over its own noise floor and scaled by the clip's
 * loud end; instant attack and a soft release, so a bar jumps on a note and sinks after it.
 * Returns { fps, bands, frames, level (frames x bands, 0..1), fmin, fmax, duration }.
 */
export function haloData(x, { sr = SAMPLE_RATE, fps = 30, bands = 40, tailSec = 1 } = {}) {
  const hop = sr / fps;
  if (!Number.isInteger(hop)) throw new Error(`the sample rate ${sr} must be a whole number of samples per frame at ${fps} fps`);
  const band = bandOf(x, sr);
  const lo = Math.max(band.fmin, 200);
  const hi = band.fmax;
  const duration = x.length / sr;
  const frames = Math.ceil((duration + tailSec) * fps);
  const edges = Array.from({ length: bands + 1 }, (_, i) => lo * (hi / lo) ** (i / bands));
  const spec = powerDb(x, { sr, n: 2048, hop }); // STFT frame k is centred on k / fps seconds
  const binOf = (f) => Math.min(spec.bins - 1, Math.max(1, Math.round(f / spec.binHz)));
  const SILENT = -200;
  const level = new Float32Array(frames * bands);
  for (let k = 0; k < frames; k++) {
    const f = Math.min(spec.frames - 1, k);
    for (let b = 0; b < bands; b++) {
      const b0 = binOf(edges[b]);
      const b1 = Math.max(b0, binOf(edges[b + 1]) - 1);
      let e = 0;
      for (let bin = b0; bin <= b1; bin++) e += 10 ** (spec.db[f * spec.bins + bin] / 10);
      level[k * bands + b] = k / fps <= duration ? 10 * Math.log10(e / (b1 - b0 + 1) + 1e-12) : SILENT;
    }
  }
  // Per band: dB over the band's own floor (25th percentile over the clip).
  const excess = new Float32Array(frames * bands);
  for (let b = 0; b < bands; b++) {
    const col = [];
    for (let k = 0; k < frames; k++) if (level[k * bands + b] > SILENT) col.push(level[k * bands + b]);
    const floor = percentile(col, 0.25);
    for (let k = 0; k < frames; k++) excess[k * bands + b] = Math.max(0, level[k * bands + b] - floor - 3);
  }
  const loud = excess.filter((v) => v > 0);
  // Nothing above the noise floor anywhere: a silent or constant clip. Fail here instead of
  // drawing a ring from NaN.
  if (loud.length === 0) throw new Error('the clip has no sound above its noise floor (silent or constant), so there is no ring to draw');
  const top = Math.max(12, percentile(loud, 0.97));
  const out = new Float32Array(frames * bands);
  const prev = new Float32Array(bands);
  for (let k = 0; k < frames; k++) {
    for (let b = 0; b < bands; b++) {
      const v = clamp(excess[k * bands + b] / top) ** 0.9;
      prev[b] = Math.max(v, prev[b] * 0.8);
      out[k * bands + b] = prev[b];
    }
  }
  return { fps, bands, frames, level: out, fmin: lo, fmax: hi, duration };
}

/** The frame (from the start of the clip) where the whole ring is fullest: the cover's moment. */
export function fullestFrame({ level, bands, frames, fps, duration }) {
  let best = -1;
  let at = 0;
  for (let k = 0; k < frames && k / fps <= duration; k++) {
    let sum = 0;
    for (let b = 0; b < bands; b++) sum += level[k * bands + b];
    if (sum > best) {
      best = sum;
      at = k;
    }
  }
  return at;
}
