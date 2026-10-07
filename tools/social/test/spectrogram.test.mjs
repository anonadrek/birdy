import { test } from 'node:test';
import assert from 'node:assert/strict';
import { bandOf, roundRange } from '../lib/spectrogram.mjs';

const SR = 48000;

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

test('the band holds both tones and not much more', () => {
  const { fmin, fmax } = bandOf(synthetic(), SR);
  assert.ok(fmin < 3000 && fmax > 5000, `${fmin}..${fmax}`);
  assert.ok(fmin >= 1500 && fmax <= 7000, `${fmin}..${fmax}`);
});

test('a low coo gets a narrow low band', () => {
  const x = new Float32Array(4 * SR);
  for (let i = 0; i < x.length; i++) x[i] = (i % 7) * 1e-4 + (i / SR > 1 && i / SR < 2 ? 0.3 * Math.sin((2 * Math.PI * 500 * i) / SR) : 0);
  const { fmin, fmax } = bandOf(x, SR);
  assert.ok(fmin <= 500 && fmax >= 500 && fmax <= 1500, `${fmin}..${fmax}`);
});

test('rounding keeps at least 1 kHz and stays under the Nyquist limit', () => {
  const r = roundRange(400, 600, 24000);
  assert.ok(r.fmax - r.fmin >= 1000 && r.fmin <= 400 && r.fmax >= 600, JSON.stringify(r));
  const top = roundRange(9000, 14000, 24000);
  assert.ok(top.fmax <= 11000, JSON.stringify(top));
});
