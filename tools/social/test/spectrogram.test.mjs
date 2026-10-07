import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spectrogramMatrix, freqTicks, timeTicks, roundRange } from '../lib/spectrogram.mjs';

const SR = 32000;

// 8 s of quiet noise with two tone bursts: 3 kHz at 2.0 to 2.5 s and 5 kHz at 6.0 to 6.4 s.
function synthetic() {
  const x = new Float32Array(8 * SR);
  let seed = 12345;
  for (let i = 0; i < x.length; i++) {
    seed = (seed * 1103515245 + 12345) >>> 0;
    x[i] = ((seed / 2 ** 32) * 2 - 1) * 0.002;
    const t = i / SR;
    if (t >= 2.0 && t < 2.5) x[i] += 0.3 * Math.sin(2 * Math.PI * 3000 * t);
    if (t >= 6.0 && t < 6.4) x[i] += 0.3 * Math.sin(2 * Math.PI * 5000 * t);
  }
  return x;
}

function at(m, tSec, fHz) {
  const c = Math.floor((tSec / m.duration) * m.cols);
  const r = Math.round(((m.fmax - fHz) / (m.fmax - m.fmin)) * (m.rows - 1));
  return m.alpha[r * m.cols + c];
}

test('the band holds both tones and each tone sits at its own time', () => {
  const m = spectrogramMatrix(synthetic(), { sr: SR, cols: 800 });
  assert.equal(m.duration, 8);
  assert.ok(m.fmin < 3000 && m.fmax > 5000, `${m.fmin}..${m.fmax}`);
  assert.ok(at(m, 2.25, 3000) > 200, 'tone 1 drawn at 2.25 s');
  assert.ok(at(m, 6.2, 5000) > 200, 'tone 2 drawn at 6.2 s');
  assert.ok(at(m, 1.0, 3000) < 20, 'nothing before tone 1');
  assert.ok(at(m, 4.0, 3000) < 20, 'nothing between the tones');
  assert.ok(at(m, 2.25, 5000) < 20, 'tone 1 is not at 5 kHz');
  // Column x of the drawing is time x / width * duration: the tone starts within one 10 ms frame of 2.0 s.
  const r = Math.round(((m.fmax - 3000) / (m.fmax - m.fmin)) * (m.rows - 1));
  let firstLit = -1;
  for (let c = 0; c < m.cols; c++) if (m.alpha[r * m.cols + c] > 100) { firstLit = c; break; }
  assert.ok(Math.abs((firstLit / m.cols) * m.duration - 2.0) <= 0.06, `starts at ${(firstLit / m.cols) * m.duration} s`);
});

test('ticks and rounding', () => {
  assert.deepEqual(freqTicks(1500, 5000).map((t) => t.label), ['2', '3', '4']);
  assert.deepEqual(freqTicks(250, 1250).map((t) => t.label), ['0.5', '0.75', '1']);
  assert.deepEqual(timeTicks(20).map((t) => t.label), ['0 s', '5 s', '10 s', '15 s', '20 s']);
  assert.deepEqual(timeTicks(3.2).map((t) => t.label), ['0 s', '1 s', '2 s', '3 s']);
  const r = roundRange(400, 600, 16000);
  assert.ok(r.fmax - r.fmin >= 1000 && r.fmin <= 400 && r.fmax >= 600, JSON.stringify(r));
});
