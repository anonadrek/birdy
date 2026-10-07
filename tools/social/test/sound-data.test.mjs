import { test } from 'node:test';
import assert from 'node:assert/strict';
import { notesData, haloData, waveformData, sonagramData, loudFlatness } from '../lib/sound-data.mjs';
import { powerDb } from '../lib/spectrogram.mjs';

const SR = 32000;

function noise(seconds, level, seed = 7) {
  const x = new Float32Array(Math.round(seconds * SR));
  let s = seed;
  for (let i = 0; i < x.length; i++) {
    s = (s * 1103515245 + 12345) >>> 0;
    x[i] = ((s / 2 ** 32) * 2 - 1) * level;
  }
  return x;
}

function addTone(x, t0, t1, f0, f1 = f0, amp = 0.3) {
  let phase = 0;
  for (let i = Math.round(t0 * SR); i < Math.round(t1 * SR); i++) {
    const u = (i / SR - t0) / (t1 - t0);
    phase += (2 * Math.PI * (f0 + (f1 - f0) * u)) / SR;
    x[i] += amp * Math.sin(phase);
  }
  return x;
}

test('notes: one note per tone burst, at its onset, with its pitch', () => {
  const x = noise(2.2, 0.002);
  addTone(x, 0.5, 0.6, 2000);
  addTone(x, 1.0, 1.12, 3000);
  addTone(x, 1.5, 1.65, 2000, 3200); // rising
  const n = notesData(x, { sr: SR });
  assert.equal(n.syllables.length, 3);
  for (const [s, t0] of n.syllables.map((s, i) => [s, [0.5, 1.0, 1.5][i]])) assert.ok(Math.abs(s.t0 - t0) <= 0.02, `onset ${s.t0} vs ${t0}`);
  assert.ok(Math.abs(n.syllables[0].contour[1].f - 2000) < 80);
  assert.ok(Math.abs(n.syllables[1].contour[1].f - 3000) < 80);
  assert.ok(n.syllables[2].contour[2].f > n.syllables[2].contour[0].f + 600, 'a rising note rises');
});

test('notes: a rattle (pulses with short dips) becomes one note per pulse', () => {
  const x = noise(1.2, 0.002);
  for (let k = 0; k < 6; k++) addTone(x, 0.2 + k * 0.08, 0.2 + k * 0.08 + 0.055, 2500);
  const n = notesData(x, { sr: SR });
  assert.equal(n.syllables.length, 6);
});

test('halo: bars jump on the sound and sink after it', () => {
  const x = noise(2, 0.002);
  addTone(x, 1.0, 1.2, 3000);
  const h = haloData(x, { sr: SR, fps: 30, bands: 20 });
  const sum = (k) => Array.from(h.level.subarray(k * h.bands, (k + 1) * h.bands)).reduce((s, v) => s + v, 0);
  assert.ok(sum(33) > 3 * sum(15) + 0.5, 'loud during the tone');
  assert.ok(sum(29) < sum(31), 'rises at the onset, not before');
  assert.ok(sum(45) < sum(35) && sum(45) > 0, 'sinks after the tone with a soft release');
});

test('waveform: the ribbon is high on the sound and flat in silence', () => {
  const x = noise(2, 0.002);
  addTone(x, 1.0, 1.3, 2500);
  const w = waveformData(x, { sr: SR });
  assert.equal(w.rate, 200);
  assert.ok(w.env[Math.round(1.15 * 200)] > 0.7);
  assert.ok(w.env[Math.round(0.5 * 200)] < 0.1);
});

test('sonagram: tonal song gets a tight band from 0 kHz, a noisy call is shown to 8 kHz', () => {
  const tonal = addTone(noise(2, 0.002), 0.5, 1.2, 2200, 3000);
  const s1 = sonagramData(tonal, { sr: SR });
  assert.equal(s1.broadband, false);
  assert.equal(s1.fmin, 0);
  assert.ok(s1.fmax >= 4000 && s1.fmax <= 6000, `fmax ${s1.fmax}`);
  const harsh = noise(2, 0.002);
  const burst = noise(0.4, 0.3, 99);
  harsh.set(burst, Math.round(0.8 * SR));
  const s2 = sonagramData(harsh, { sr: SR });
  assert.equal(s2.broadband, true);
  assert.equal(s2.fmax, 8000);
  // the pen inks while the tone sounds
  const c = Math.round((0.85 / 2) * s1.cols);
  assert.equal(s1.penInk[c], 1);
});

test('flatness: a whistle is tonal, a hiss is not', () => {
  const tone = addTone(noise(1, 0.001), 0, 1, 2500);
  const hiss = noise(1, 0.3);
  const band = { fmin: 1000, fmax: 5000 };
  assert.ok(loudFlatness(powerDb(tone, { sr: SR, n: 1024, hop: 160 }), band) < 0.1);
  assert.ok(loudFlatness(powerDb(hiss, { sr: SR, n: 1024, hop: 160 }), band) > 0.3);
});
