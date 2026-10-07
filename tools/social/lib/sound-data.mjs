// Data for the four sound animations (prototypes, 2026-10-07). Everything is computed in Node
// from the clip that plays, so the page only looks values up by time: deterministic and in sync.
//   sonagram  field-guide sonagram: crisp ink matrix + the pen's height per column
//   waveform  loudness ribbon: smoothed band loudness and pitch colour, 200 values per second
//   halo      ring of bars: band loudness per video frame (instant attack, soft release)
//   notes     syllables: onset, offset, loudness and pitch contour
import { powerDb, excessOverNoise, bandOf, SAMPLE_RATE } from './spectrogram.mjs';

function percentile(values, p) {
  const s = Float32Array.from(values).sort();
  return s[Math.min(s.length - 1, Math.max(0, Math.round(p * (s.length - 1))))];
}

function clamp(x, a = 0, b = 1) {
  return x < a ? a : x > b ? b : x;
}

function smoothstep(a, b, x) {
  const t = clamp((x - a) / (b - a));
  return t * t * (3 - 2 * t);
}

/** Field-guide band: whole kHz, at least 3 kHz tall. */
export function fieldGuideBand({ fmin, fmax }, nyquist) {
  let lo = Math.floor(fmin / 1000) * 1000;
  let hi = Math.ceil(fmax / 1000) * 1000;
  while (hi - lo < 3000) {
    if (lo > 0) lo -= 1000;
    if (hi - lo < 3000) hi += 1000;
  }
  return { fmin: lo, fmax: Math.min(hi, Math.floor((0.95 * nyquist) / 1000) * 1000) };
}

/** Mean spectral flatness inside the band over the loudest 15 % of frames (0 tonal .. 1 noise). */
export function loudFlatness(spec, band) {
  const lo = Math.max(2, Math.round(Math.max(band.fmin, 300) / spec.binHz));
  const hi = Math.max(lo + 1, Math.round(band.fmax / spec.binHz));
  const frames = [];
  for (let f = 0; f < spec.frames; f++) {
    let e = 0;
    let lg = 0;
    for (let b = lo; b <= hi; b++) {
      const p = 10 ** (spec.db[f * spec.bins + b] / 10);
      e += p;
      lg += Math.log(p + 1e-20);
    }
    const n = hi - lo + 1;
    frames.push({ e, flat: Math.exp(lg / n) / (e / n) });
  }
  frames.sort((a, b) => b.e - a.e);
  const top = frames.slice(0, Math.max(5, Math.round(frames.length * 0.15)));
  return top.reduce((s, v) => s + v.flat, 0) / top.length;
}

/** A: crisp ink sonagram (alpha 0..255, top row = highest frequency) plus the pen's track. */
export function sonagramData(x, { sr = SAMPLE_RATE, cols = 1400 } = {}) {
  const duration = x.length / sr;
  const core = bandOf(x, sr);
  const hop = duration > 10 ? 128 : 64; // 2 to 4 ms: sharp in time
  // Field guides start at 0 kHz. A noisy, broadband call (high spectral flatness in its loudest
  // moments, like a magpie's chatter) is shown up to 8 kHz; whistles and coos keep a tight band.
  const probe = powerDb(x, { sr, n: 1024, hop });
  const probeEx = excessOverNoise(probe);
  const broadband = loudFlatness(probe, core) > 0.3;
  const band = fieldGuideBand({ fmin: core.fmin < 2000 ? 0 : core.fmin, fmax: broadband ? 8000 : core.fmax + 1000 }, sr / 2);
  const n = band.fmax <= 3000 ? 2048 : 1024;
  const spec = n === 1024 ? probe : powerDb(x, { sr, n, hop });
  const excess0 = n === 1024 ? probeEx : excessOverNoise(spec);
  // Sharpen across frequency (unsharp mask): tonal notes thin to lines, broadband calls stay bars.
  const excess = new Float32Array(excess0.length);
  const R = 6;
  for (let f = 0; f < spec.frames; f++) {
    for (let b = 0; b < spec.bins; b++) {
      let s = 0;
      let c = 0;
      for (let d = -R; d <= R; d++) {
        const bb = b + d;
        if (bb < 0 || bb >= spec.bins) continue;
        s += excess0[f * spec.bins + bb];
        c++;
      }
      const v = excess0[f * spec.bins + b];
      excess[f * spec.bins + b] = v + 1.2 * (v - s / c);
    }
  }
  const bLo = Math.round(band.fmin / spec.binHz);
  const bHi = Math.round(band.fmax / spec.binHz);
  const rows = bHi - bLo + 1;
  const outCols = Math.min(cols, spec.frames);
  const hopSec = hop / sr;
  const raw = new Float32Array(outCols * rows);
  for (let c = 0; c < outCols; c++) {
    const t0 = (c / outCols) * duration;
    const t1 = ((c + 1) / outCols) * duration;
    let f0 = Math.ceil(t0 / hopSec - 1e-9);
    let f1 = Math.ceil(t1 / hopSec - 1e-9) - 1;
    if (f1 < f0) f0 = f1 = Math.min(spec.frames - 1, Math.round((t0 + t1) / 2 / hopSec));
    f1 = Math.min(f1, spec.frames - 1);
    for (let r = 0; r < rows; r++) {
      let m = -Infinity;
      for (let f = f0; f <= f1; f++) m = Math.max(m, excess[f * spec.bins + (bHi - r)]);
      raw[r * outCols + c] = m;
    }
  }
  // Ink only where the sound clearly stands out: a high threshold and a short ramp give thin, crisp traces.
  const T = Math.max(16, percentile(raw, 0.94));
  const alpha = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) alpha[i] = Math.round(255 * smoothstep(T, T + 7, raw[i]));
  // Drop isolated specks: ink needs a few inked neighbours to stay.
  const keep = new Uint8Array(alpha.length);
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < outCols; c++) {
      if (!alpha[r * outCols + c]) continue;
      let n = 0;
      for (let dr = -2; dr <= 2; dr++) {
        for (let dc = -2; dc <= 2; dc++) {
          const rr = r + dr;
          const cc = c + dc;
          if (rr >= 0 && rr < rows && cc >= 0 && cc < outCols && alpha[rr * outCols + cc] > 100) n++;
        }
      }
      keep[r * outCols + c] = n >= 6 ? 1 : 0;
    }
  }
  for (let i = 0; i < alpha.length; i++) if (!keep[i]) alpha[i] = 0;
  // The pen follows the strongest inked row; between notes it drifts back to the middle.
  const penY = new Float32Array(outCols);
  const penInk = new Uint8Array(outCols);
  let y = 0.5;
  for (let c = 0; c < outCols; c++) {
    let best = -Infinity;
    let row = -1;
    for (let r = 0; r < rows; r++) {
      const i = r * outCols + c;
      if (alpha[i] > 140 && raw[i] > best) {
        best = raw[i];
        row = r;
      }
    }
    const target = row >= 0 ? row / (rows - 1) : 0.5;
    y += (target - y) * (row >= 0 ? 0.35 : 0.04);
    penY[c] = y;
    penInk[c] = row >= 0 ? 1 : 0;
  }
  const ticks = [];
  const step = band.fmax - band.fmin > 5000 ? 2000 : 1000;
  for (let f = band.fmin + step; f < band.fmax; f += step) ticks.push({ f, label: String(f / 1000) });
  return { cols: outCols, rows, alpha, penY, penInk, fmin: band.fmin, fmax: band.fmax, broadband, duration, freqTicks: ticks };
}

/** Band loudness (dB) and spectral centroid per STFT frame. */
function bandTrack(x, sr, band, n, hop) {
  const spec = powerDb(x, { sr, n, hop });
  const lo = Math.max(1, Math.round(Math.max(band.fmin, 150) / spec.binHz));
  const hi = Math.min(spec.bins - 1, Math.round(band.fmax / spec.binHz));
  const db = new Float32Array(spec.frames);
  const centroid = new Float32Array(spec.frames);
  const peak = new Float32Array(spec.frames);
  for (let f = 0; f < spec.frames; f++) {
    let e = 0;
    let c = 0;
    let best = -Infinity;
    let bestB = lo;
    for (let b = lo; b <= hi; b++) {
      const p = 10 ** (spec.db[f * spec.bins + b] / 10);
      e += p;
      c += p * b;
      if (spec.db[f * spec.bins + b] > best) {
        best = spec.db[f * spec.bins + b];
        bestB = b;
      }
    }
    db[f] = 10 * Math.log10(e + 1e-12);
    centroid[f] = e > 0 ? (c / e - lo) / Math.max(1, hi - lo) : 0.5;
    peak[f] = bestB * spec.binHz;
  }
  return { db, centroid, peak, rate: sr / hop, frames: spec.frames };
}

function gaussianSmooth(v, sigma) {
  const r = Math.ceil(sigma * 3);
  const k = [];
  let sum = 0;
  for (let i = -r; i <= r; i++) {
    const w = Math.exp(-(i * i) / (2 * sigma * sigma));
    k.push(w);
    sum += w;
  }
  const out = new Float32Array(v.length);
  for (let i = 0; i < v.length; i++) {
    let s = 0;
    let ws = 0;
    for (let j = -r; j <= r; j++) {
      const ii = i + j;
      if (ii < 0 || ii >= v.length) continue;
      s += v[ii] * k[j + r];
      ws += k[j + r];
    }
    out[i] = s / ws;
  }
  return out;
}

/** B: loudness ribbon, 200 values per second (0..1) and a pitch colour (0 low .. 1 high). */
export function waveformData(x, { sr = SAMPLE_RATE } = {}) {
  const band = bandOf(x, sr);
  const tr = bandTrack(x, sr, band, 512, sr / 200);
  const floor = percentile(tr.db, 0.1);
  const top = percentile(tr.db, 0.99);
  const raw = Float32Array.from(tr.db, (d) => clamp((d - floor - 3) / Math.max(6, top - floor - 3)));
  const env = gaussianSmooth(raw, 3); // 15 ms
  const colour = gaussianSmooth(tr.centroid, 6);
  return { rate: 200, env, colour, fmin: band.fmin, fmax: band.fmax, duration: x.length / sr };
}

/**
 * C: loudness in `bands` log-spaced bands for every video frame from the start of the song,
 * instant attack and a soft release, so a bar jumps on the sound and sinks after it.
 */
export function haloData(x, { sr = SAMPLE_RATE, fps = 30, bands = 40, tailSec = 1 } = {}) {
  const band = bandOf(x, sr);
  const lo = Math.max(band.fmin, 200);
  const hi = band.fmax;
  const n = 2048;
  const duration = x.length / sr;
  const frames = Math.ceil((duration + tailSec) * fps);
  const edges = Array.from({ length: bands + 1 }, (_, i) => lo * (hi / lo) ** (i / bands));
  const spec = powerDb(x, { sr, n, hop: Math.round(sr / fps) });
  const binOf = (f) => Math.min(spec.bins - 1, Math.max(1, Math.round(f / spec.binHz)));
  const level = new Float32Array(frames * bands);
  for (let k = 0; k < frames; k++) {
    const f = Math.min(spec.frames - 1, k); // STFT frame k is centred on k / fps seconds
    for (let b = 0; b < bands; b++) {
      let e = 0;
      let cnt = 0;
      for (let bin = binOf(edges[b]); bin <= Math.max(binOf(edges[b]), binOf(edges[b + 1]) - 1); bin++) {
        e += 10 ** (spec.db[f * spec.bins + bin] / 10);
        cnt++;
      }
      level[k * bands + b] = k * (1 / fps) <= duration ? 10 * Math.log10(e / cnt + 1e-12) : -200;
    }
  }
  // Per band: dB over the band's own floor, scaled by the clip's loud end.
  const out = new Float32Array(frames * bands);
  const excess = new Float32Array(frames * bands);
  for (let b = 0; b < bands; b++) {
    const col = [];
    for (let k = 0; k < frames; k++) if (level[k * bands + b] > -200) col.push(level[k * bands + b]);
    const floor = percentile(col, 0.25);
    for (let k = 0; k < frames; k++) excess[k * bands + b] = Math.max(0, level[k * bands + b] - floor - 3);
  }
  const top = Math.max(12, percentile(excess.filter((v) => v > 0), 0.97));
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

/** D: syllables found by band loudness with hysteresis, each with loudness and pitch contour. */
export function notesData(x, { sr = SAMPLE_RATE } = {}) {
  const band = bandOf(x, sr);
  const hop = sr / 200; // 5 ms
  const tr = bandTrack(x, sr, band, 512, hop);
  const db = gaussianSmooth(tr.db, 1.5);
  const floor = percentile(db, 0.2);
  const top = percentile(db, 0.995);
  const on = floor + Math.max(8, 0.4 * (top - floor));
  const off = on - 4;
  const segs = [];
  let start = -1;
  for (let i = 0; i < db.length; i++) {
    if (start < 0 && db[i] >= on) start = i;
    else if (start >= 0 && db[i] < off) {
      segs.push([start, i - 1]);
      start = -1;
    }
  }
  if (start >= 0) segs.push([start, db.length - 1]);
  const joined = [];
  for (const s of segs) {
    const last = joined.at(-1);
    if (last && s[0] - last[1] <= 2) last[1] = s[1];
    else joined.push([...s]);
  }
  // Split a segment at a dip of 5 dB or more below the peaks on both sides (a rattle is many notes).
  const merged = [];
  for (const [a0, b0] of joined) {
    let start = a0;
    for (let i = a0 + 5; i <= b0 - 5; i++) {
      if (!(db[i] <= db[i - 1] && db[i] <= db[i + 1])) continue;
      let left = -Infinity;
      let right = -Infinity;
      for (let j = start; j < i; j++) left = Math.max(left, db[j]);
      for (let j = i + 1; j <= Math.min(b0, i + 40); j++) right = Math.max(right, db[j]);
      if (i - start >= 5 && left - db[i] >= 5 && right - db[i] >= 5) {
        merged.push([start, i - 1]);
        start = i + 1;
      }
    }
    merged.push([start, b0]);
  }
  const fLo = Math.max(band.fmin, 250);
  const fHi = band.fmax;
  const syllables = merged
    .filter(([a, b]) => b - a + 1 >= 5)
    .map(([a, b]) => {
      let peak = -Infinity;
      for (let i = a; i <= b; i++) peak = Math.max(peak, db[i]);
      // A robust pitch line: the median peak frequency of the first, middle and last third.
      const third = Math.max(1, Math.floor((b - a + 1) / 3));
      const med = (i0, i1) => {
        const v = [];
        for (let i = i0; i <= Math.min(b, i1); i++) v.push(tr.peak[i]);
        v.sort((p, q) => p - q);
        return clamp(v[Math.floor(v.length / 2)], fLo, fHi);
      };
      const contour = [
        { t: a / tr.rate, f: med(a, a + third - 1) },
        { t: (a + b + 1) / 2 / tr.rate, f: med(a + third, b - third) },
        { t: (b + 1) / tr.rate, f: med(b - third + 1, b) },
      ];
      return { t0: a / tr.rate, t1: (b + 1) / tr.rate, loud: clamp((peak - on) / Math.max(6, top - on)), contour };
    });
  return { syllables, fmin: fLo, fmax: fHi, duration: x.length / sr, onDb: on };
}
