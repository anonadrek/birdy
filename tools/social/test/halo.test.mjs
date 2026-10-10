import { test } from 'node:test';
import assert from 'node:assert/strict';
import { haloData, fullestFrame } from '../lib/halo.mjs';

const SR = 48000;

function noise(seconds, level, seed = 7) {
  const x = new Float32Array(Math.round(seconds * SR));
  let s = seed;
  for (let i = 0; i < x.length; i++) {
    s = (s * 1103515245 + 12345) >>> 0;
    x[i] = ((s / 2 ** 32) * 2 - 1) * level;
  }
  return x;
}

function addTone(x, t0, t1, f, amp = 0.3) {
  for (let i = Math.round(t0 * SR); i < Math.round(t1 * SR); i++) x[i] += amp * Math.sin((2 * Math.PI * f * i) / SR);
  return x;
}

const sumAt = (h, k) => Array.from(h.level.subarray(k * h.bands, (k + 1) * h.bands)).reduce((s, v) => s + v, 0);

test('bars jump on the sound, not before, and sink after it with a soft release', () => {
  const h = haloData(addTone(noise(2, 0.002), 1.0, 1.2, 3000), { sr: SR, fps: 30, bands: 20 });
  assert.ok(sumAt(h, 33) > 3 * sumAt(h, 15) + 0.5, 'loud during the tone');
  assert.ok(sumAt(h, 28) < sumAt(h, 31), 'rises at the onset (frame 30), not before');
  assert.ok(sumAt(h, 45) < sumAt(h, 35) && sumAt(h, 45) > 0, 'sinks after the tone with a soft release');
});

test('the band of a tone lights its own bars: a high note lights higher bars than a low note', () => {
  const x = addTone(addTone(noise(3, 0.002), 0.5, 0.8, 2000), 2.0, 2.3, 5000);
  const h = haloData(x, { sr: SR, fps: 30, bands: 20 });
  const centroid = (k) => {
    let s = 0;
    let w = 0;
    for (let b = 0; b < h.bands; b++) {
      s += b * h.level[k * h.bands + b];
      w += h.level[k * h.bands + b];
    }
    return s / w;
  };
  assert.ok(h.fmin < 2000 && h.fmax > 5000, `${h.fmin}..${h.fmax}`);
  assert.ok(centroid(Math.round(2.15 * 30)) > centroid(Math.round(0.65 * 30)) + 3);
});

test('one value per band and video frame, through a one-second tail after the clip', () => {
  const h = haloData(noise(2, 0.002), { sr: SR, fps: 30, bands: 40 });
  assert.equal(h.frames, 90);
  assert.equal(h.level.length, 90 * 40);
  for (const v of h.level) assert.ok(v >= 0 && v <= 1);
});

test('the sample rate must give whole frames', () => {
  assert.throws(() => haloData(noise(1, 0.01), { sr: 32000, fps: 30 }), /whole number of samples/);
});

test('fullest frame: the loudest moment of the song, never in the tail', () => {
  const h = haloData(addTone(addTone(noise(3, 0.002), 0.5, 0.7, 3000, 0.05), 2.0, 2.4, 3000, 0.4), { sr: SR, fps: 30, bands: 20 });
  const k = fullestFrame(h);
  assert.ok(k >= 60 && k <= 75, `frame ${k}`);
  assert.ok(k / h.fps <= h.duration);
});

test('a silent clip fails loudly instead of giving NaN bars', () => {
  assert.throws(() => haloData(new Float32Array(2 * SR), { sr: SR, fps: 30, bands: 20 }), /no sound above its noise floor/);
});
