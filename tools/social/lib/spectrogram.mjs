// Spectrogram of the clip that plays in the video: short-time Fourier transform in plain JS,
// the background noise measured per frequency, and the frequency band that holds the bird.
// The ring (lib/halo.mjs) is drawn from this band.
import { run } from './proc.mjs';

// The clip is written at 48 kHz (lib/audio.mjs), so the analysis reads exactly what plays and
// one video frame at 30 fps is a whole number of samples (1600).
export const SAMPLE_RATE = 48000;
const HOP = 480; // 10 ms
const NOISE_PERCENTILE = 0.2;
const RANGE_MIN_EXCESS_DB = 12;
// Birds here sing below about 10 kHz; above that there is mostly hiss and MP3 artefacts.
const SEARCH_MAX_HZ = 10000;
const SHOW_MAX_HZ = 11000;

export async function decodeMono(file, sr = SAMPLE_RATE) {
  const { stdout } = await run('ffmpeg', ['-hide_banner', '-nostats', '-i', file, '-ac', '1', '-ar', String(sr), '-f', 'f32le', '-']);
  const copy = stdout.buffer.slice(stdout.byteOffset, stdout.byteOffset + stdout.byteLength);
  return new Float32Array(copy);
}

function makeFft(n) {
  const levels = Math.round(Math.log2(n));
  if (2 ** levels !== n) throw new Error(`FFT size must be a power of two, got ${n}`);
  const cos = new Float64Array(n / 2);
  const sin = new Float64Array(n / 2);
  for (let i = 0; i < n / 2; i++) {
    cos[i] = Math.cos((2 * Math.PI * i) / n);
    sin[i] = Math.sin((2 * Math.PI * i) / n);
  }
  const rev = new Uint32Array(n);
  for (let i = 0; i < n; i++) {
    let r = 0;
    for (let b = 0; b < levels; b++) r = (r << 1) | ((i >>> b) & 1);
    rev[i] = r;
  }
  return (re, im) => {
    for (let i = 0; i < n; i++) {
      const j = rev[i];
      if (j > i) {
        [re[i], re[j]] = [re[j], re[i]];
        [im[i], im[j]] = [im[j], im[i]];
      }
    }
    for (let size = 2; size <= n; size *= 2) {
      const half = size / 2;
      const step = n / size;
      for (let i = 0; i < n; i += size) {
        for (let j = 0; j < half; j++) {
          const k = j * step;
          const a = i + j;
          const b = a + half;
          const tre = re[b] * cos[k] + im[b] * sin[k];
          const tim = im[b] * cos[k] - re[b] * sin[k];
          re[b] = re[a] - tre;
          im[b] = im[a] - tim;
          re[a] += tre;
          im[a] += tim;
        }
      }
    }
  };
}

/** Power spectrogram in dB with frames centred at f * hop. */
export function powerDb(x, { sr = SAMPLE_RATE, n = 2048, hop = HOP } = {}) {
  const fft = makeFft(n);
  const win = new Float64Array(n);
  for (let i = 0; i < n; i++) win[i] = 0.5 - 0.5 * Math.cos((2 * Math.PI * i) / (n - 1));
  const frames = Math.floor(x.length / hop) + 1;
  const bins = n / 2 + 1;
  const power = new Float32Array(frames * bins);
  const re = new Float64Array(n);
  const im = new Float64Array(n);
  for (let f = 0; f < frames; f++) {
    const s = f * hop - n / 2;
    for (let i = 0; i < n; i++) {
      const k = s + i;
      re[i] = k >= 0 && k < x.length ? x[k] * win[i] : 0;
      im[i] = 0;
    }
    fft(re, im);
    for (let b = 0; b < bins; b++) power[f * bins + b] = re[b] * re[b] + im[b] * im[b];
  }
  // A 3 x 3 average over neighbouring frames and frequencies calms the noise speckle.
  const db = new Float32Array(frames * bins);
  for (let f = 0; f < frames; f++) {
    for (let b = 0; b < bins; b++) {
      let sum = 0;
      let count = 0;
      for (let df = -1; df <= 1; df++) {
        const ff = f + df;
        if (ff < 0 || ff >= frames) continue;
        for (let db2 = -1; db2 <= 1; db2++) {
          const bb = b + db2;
          if (bb < 0 || bb >= bins) continue;
          sum += power[ff * bins + bb];
          count++;
        }
      }
      db[f * bins + b] = 10 * Math.log10(sum / count + 1e-12);
    }
  }
  return { frames, bins, db, binHz: sr / n, hopSec: hop / sr };
}

function percentile(sorted, p) {
  return sorted[Math.min(sorted.length - 1, Math.max(0, Math.round(p * (sorted.length - 1))))];
}

/**
 * dB above each frequency's own noise floor (20th percentile over time). No floor is allowed
 * below the recording's typical floor minus 12 dB: above an MP3 encoder's low-pass the floor
 * is near silence, and leakage there would otherwise look as loud as the bird.
 */
export function excessOverNoise({ frames, bins, db, binHz }) {
  const out = new Float32Array(frames * bins);
  const col = new Float32Array(frames);
  const floors = new Float32Array(bins);
  for (let b = 0; b < bins; b++) {
    for (let f = 0; f < frames; f++) col[f] = db[f * bins + b];
    floors[b] = percentile(Float32Array.from(col).sort(), NOISE_PERCENTILE);
  }
  const lo = Math.ceil(150 / binHz);
  const hi = Math.min(bins - 1, Math.floor(8000 / binHz));
  const typical = percentile(Float32Array.from(floors.subarray(lo, hi + 1)).sort(), 0.5);
  for (let b = 0; b < bins; b++) {
    const floor = Math.max(floors[b], typical - 12);
    for (let f = 0; f < frames; f++) out[f * bins + b] = db[f * bins + b] - floor;
  }
  return out;
}

/**
 * Frequency band (Hz) that holds the bird: the power of every cell clearly above its noise
 * floor counts (so the loudest voice decides, not faint noise spread over many cells), then the
 * 4th to 96th percentile of that power, padded and rounded.
 */
export function chooseRange(excess, { frames, bins, binHz, db }, nyquist) {
  const lo = Math.ceil(150 / binHz);
  const hi = Math.min(bins - 1, Math.floor(Math.min(0.95 * nyquist, SEARCH_MAX_HZ) / binHz));
  let peak = -Infinity;
  for (let f = 0; f < frames; f++) for (let b = lo; b <= hi; b++) if (db[f * bins + b] > peak) peak = db[f * bins + b];
  const w = new Float64Array(bins);
  let total = 0;
  for (let f = 0; f < frames; f++) {
    for (let b = lo; b <= hi; b++) {
      const i = f * bins + b;
      if (excess[i] > RANGE_MIN_EXCESS_DB) {
        const p = 10 ** ((db[i] - peak) / 10);
        w[b] += p;
        total += p;
      }
    }
  }
  let fLo = 1000;
  let fHi = 8000;
  if (total > 0) {
    let acc = 0;
    let bLo = lo;
    let bHi = hi;
    let foundLo = false;
    for (let b = lo; b <= hi; b++) {
      acc += w[b];
      if (!foundLo && acc >= 0.04 * total) {
        bLo = b;
        foundLo = true;
      }
      if (acc >= 0.96 * total) {
        bHi = b;
        break;
      }
    }
    fLo = bLo * binHz;
    fHi = bHi * binHz;
  }
  return roundRange(fLo, fHi, nyquist);
}

export function roundRange(fLo, fHi, nyquist) {
  let span = Math.max(fHi - fLo, 1);
  let a = fLo - 0.15 * span;
  let b = fHi + 0.15 * span;
  if (b - a < 1200) {
    const mid = (a + b) / 2;
    a = mid - 600;
    b = mid + 600;
  }
  span = b - a;
  const step = span <= 2000 ? 250 : span <= 5000 ? 500 : 1000;
  let fmin = Math.max(0, Math.floor(a / step) * step);
  let fmax = Math.ceil(b / step) * step;
  const top = Math.floor(Math.min(0.95 * nyquist, SHOW_MAX_HZ) / step) * step;
  if (fmax > top) fmax = top;
  if (fmin >= fmax) fmin = Math.max(0, fmax - 2 * step);
  return { fmin, fmax };
}

/** The frequency band (Hz) that holds the bird in the clip `x` (mono, `sr` Hz). */
export function bandOf(x, sr = SAMPLE_RATE) {
  const spec = powerDb(x, { sr, n: 2048 });
  return chooseRange(excessOverNoise(spec), spec, sr / 2);
}
