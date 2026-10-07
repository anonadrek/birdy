// Builds the data each sound style needs (see stage/see-the-song.html) from the clip's samples.
import { spectrogramMatrix, timeTicks } from './spectrogram.mjs';
import { sonagramData, waveformData, haloData, notesData } from './sound-data.mjs';

export const STYLES = ['glow', 'sonagram', 'waveform', 'halo', 'notes'];
/** Option letters used for the preview files (a to d are the four options of 2026-10-07). */
export const STYLE_LETTERS = { glow: '0', sonagram: 'a', waveform: 'b', halo: 'c', notes: 'd' };

const b64 = (a) => Buffer.from(a.buffer, a.byteOffset, a.byteLength).toString('base64');

function argmax(values) {
  let best = -Infinity;
  let at = 0;
  values.forEach((v, i) => {
    if (v > best) {
      best = v;
      at = i;
    }
  });
  return at;
}

/**
 * Returns { key, data, band, stillAt }: `data` goes into the stage config under `key`,
 * `band` is the frequency band the animation shows, `stillAt` is the audio time of a
 * representative still.
 */
export function styleData(style, samples, { fps, D }) {
  switch (style) {
    case 'glow': {
      const s = spectrogramMatrix(samples, { cols: 1808 });
      const data = { cols: s.cols, rows: s.rows, alphaB64: b64(s.alpha), fmin: s.fmin, fmax: s.fmax, freqTicks: s.freqTicks, timeTicks: s.timeTicks, duration: D };
      return { key: 'spec', data, band: { fmin: s.fmin, fmax: s.fmax }, stillAt: D + 0.05 };
    }
    case 'sonagram': {
      const s = sonagramData(samples);
      const data = { cols: s.cols, rows: s.rows, alphaB64: b64(s.alpha), penYB64: b64(s.penY), penInkB64: b64(s.penInk), fmin: s.fmin, fmax: s.fmax, freqTicks: s.freqTicks, timeTicks: timeTicks(D), duration: D };
      return { key: 'sonagram', data, band: { fmin: s.fmin, fmax: s.fmax }, stillAt: D + 0.05 };
    }
    case 'waveform': {
      const s = waveformData(samples);
      const data = { rate: s.rate, envB64: b64(s.env), colourB64: b64(s.colour), duration: D };
      return { key: 'waveform', data, band: { fmin: s.fmin, fmax: s.fmax }, stillAt: argmax(Array.from(s.env)) / s.rate };
    }
    case 'halo': {
      const s = haloData(samples, { fps });
      const sums = [];
      for (let k = 0; k < s.frames; k++) {
        let sum = 0;
        for (let b = 0; b < s.bands; b++) sum += s.level[k * s.bands + b];
        sums.push(k / fps <= D ? sum : -1);
      }
      const data = { fps, bands: s.bands, frames: s.frames, levelB64: b64(s.level) };
      return { key: 'halo', data, band: { fmin: s.fmin, fmax: s.fmax }, stillAt: argmax(sums) / fps };
    }
    case 'notes': {
      const s = notesData(samples);
      const data = { syllables: s.syllables, fmin: s.fmin, fmax: s.fmax, duration: D };
      return { key: 'notes', data, band: { fmin: s.fmin, fmax: s.fmax }, stillAt: D + 0.05, syllables: s.syllables.map(({ t0, t1, loud }) => ({ t0, t1, loud })) };
    }
    default:
      throw new Error(`unknown --style "${style}" (one of ${STYLES.join(', ')})`);
  }
}
