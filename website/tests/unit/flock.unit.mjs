import assert from 'node:assert/strict';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { FLOCK, MARK } from '../../src/components/hero/flock-data.mjs';
import { COLOURS, DISC_SCALE, LIT, LIT_SCALE, VIEW, fitView, flightPlan, flockIndexFor, flockSvg, landing, rng } from '../../src/components/hero/flock.mjs';
import { loadAppSpeciesSnapshot } from '../../src/lib/daily-bird.mjs';

const appSpecies = loadAppSpeciesSnapshot(fileURLToPath(new URL('../..', import.meta.url)));

test('flockIndexFor: samma art får alltid samma fågel, alltid en i hjärtat', () => {
  assert.equal(flockIndexFor('Q25384'), 2); // Hornuggla, testbyggets Dagens fågel
  assert.equal(flockIndexFor('Q25485'), 107); // Talgoxe, reservfågeln när ingen art har sida
  assert.equal(flockIndexFor('Q26026'), 84); // Ringduva, prototypens Dagens fågel
  assert.equal(flockIndexFor('Q25334'), 74); // Rödhake, delningsbilden
  for (const s of appSpecies) {
    const i = flockIndexFor(s.id);
    assert.ok(Number.isInteger(i) && i >= 0 && i < FLOCK.edge, `${s.id} -> ${i}`);
  }
});

test('flockIndexFor: appens arter sprids över hela hjärtat, grannar hamnar isär', () => {
  assert.equal(appSpecies.length, FLOCK.birds.length, 'en fågel i flocken för varje art i appen');
  const perBird = new Array(FLOCK.edge).fill(0);
  for (const s of appSpecies) perBird[flockIndexFor(s.id)] += 1;
  assert.equal(perBird.filter((n) => n > 0).length, FLOCK.edge, 'varje fågel i hjärtat står för minst en art');
  assert.ok(Math.max(...perBird) <= 14, `högst ${Math.max(...perBird)} arter på samma fågel (snitt ${(appSpecies.length / FLOCK.edge).toFixed(1)})`);
  const neighbours = ['Q25480', 'Q25481', 'Q25482', 'Q25483', 'Q25484', 'Q25485', 'Q25486', 'Q25487', 'Q25488', 'Q25489'].map(flockIndexFor);
  assert.equal(new Set(neighbours).size, neighbours.length, 'QID som skiljer på en siffra får olika fåglar');
});

test('fitView: VIEW i en ruta som xMidYMid meet', () => {
  assert.deepEqual(VIEW, { x: 830, y: 0, w: 680, h: 720 });
  assert.deepEqual(fitView({ left: 0, top: 0, width: 680, height: 720 }), { s: 1, ox: -830, oy: 0 });
  assert.deepEqual(fitView({ left: 100, top: 50, width: 1360, height: 720 }), { s: 1, ox: -390, oy: 50 });
  const tall = fitView({ left: 0, top: 0, width: 340, height: 1000 });
  assert.equal(tall.s, 0.5);
  assert.equal(tall.oy, (1000 - 360) / 2);
});

test('landing: fågelns plats, storlek och vridning i rutans pixlar', () => {
  const fit = fitView({ left: 0, top: 0, width: 680, height: 720 });
  const [x, y, size, rot] = FLOCK.birds[0];
  assert.deepEqual(landing(FLOCK.birds[0], fit), { x: x - 830, y, size, rot });
});

test('rng: samma frö ger samma följd', () => {
  const a = rng(839);
  const b = rng(839);
  const first = [a(), a(), a()];
  assert.deepEqual(first, [b(), b(), b()]);
  assert.equal(first[0], 0.8682231577113271);
  for (const v of first) assert.ok(v >= 0 && v < 1);
});

test('flightPlan: samma flykt varje gång, dagens fågel landar sist', () => {
  const fit = fitView({ left: 700, top: 100, width: 654, height: 692 });
  const input = { fit, width: 1440, height: 820, litIndex: 2 };
  const plan = flightPlan(input);
  assert.deepEqual(flightPlan(input), plan);
  assert.equal(plan.birds.length, FLOCK.birds.length - 1);
  const lastOther = Math.max(...plan.birds.map((p) => p.delay + p.dur));
  assert.equal(plan.lit.delay + plan.lit.dur, plan.total);
  assert.ok(plan.total > lastOther, 'dagens fågel landar sist');
  assert.ok(plan.total > 4500 && plan.total < 5500, `flykten tar ${Math.round(plan.total)} ms`);
  const lit = landing(FLOCK.birds[2], fit);
  assert.equal(plan.lit.fx, lit.x);
  assert.equal(plan.lit.fy, lit.y);
  for (const p of plan.birds) assert.ok(p.sx < 0 && p.sy > 820 * 0.8, 'alla startar nere till vänster');
  assert.throws(() => flightPlan({ ...input, litIndex: 9999 }), /ingen fågel/);
});

test('flockSvg: den landade flocken med dagens fågel tänd, i VIEW:s koordinater', () => {
  const svg = flockSvg({ litIndex: 2 });
  assert.ok(svg.startsWith('<svg class="flock-still" viewBox="830 0 680 720" preserveAspectRatio="xMidYMid meet" aria-hidden="true" focusable="false">'));
  assert.equal((svg.match(/<use /g) ?? []).length, FLOCK.birds.length);
  assert.equal((svg.match(/<circle /g) ?? []).length, 1);
  for (const colour of COLOURS) assert.ok(svg.includes(`<g fill="${colour}">`), colour);
  assert.ok(!svg.includes('NaN'));
  const [x, y, size, rot] = FLOCK.birds[2];
  assert.ok(svg.includes(`<circle cx="${x}" cy="${y}" r="${Math.round(size * DISC_SCALE * 100) / 100}" fill="${LIT.disc}" stroke="${LIT.ring}"`));
  // Today's bird is drawn last, bigger, in rust: its matrix puts the mark's centre on the bird's place.
  const m = svg.match(/<use href="#flock-mark" fill="#9A4526" transform="matrix\(([^)]+)\)"\/><\/svg>$/);
  assert.ok(m, 'dagens fågel sist');
  const [a, b, c, d, e, f] = m[1].split(' ').map(Number);
  assert.ok(Math.abs(a * MARK.cx + c * MARK.cy + e - x) < 0.05, 'x');
  assert.ok(Math.abs(b * MARK.cx + d * MARK.cy + f - y) < 0.05, 'y');
  assert.ok(Math.abs(Math.hypot(a, b) - (size * LIT_SCALE) / MARK.w) < 1e-4, 'storlek');
  assert.ok(Math.abs((Math.atan2(b, a) * 180) / Math.PI - rot) < 0.01, 'vridning');
});
