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
  // This holds for the 1.3.0 snapshot (839 species against 178 birds in the heart); nothing in flockIndexFor
  // guarantees every heart bird gets at least one species. A swap that keeps the same count can still leave one empty.
  assert.equal(perBird.filter((n) => n > 0).length, FLOCK.edge, 'varje fågel i hjärtat står för minst en art');
  assert.ok(Math.max(...perBird) <= 14, `högst ${Math.max(...perBird)} arter på samma fågel (snitt ${(appSpecies.length / FLOCK.edge).toFixed(1)})`);
  const neighbours = ['Q25480', 'Q25481', 'Q25482', 'Q25483', 'Q25484', 'Q25485', 'Q25486', 'Q25487', 'Q25488', 'Q25489'].map(flockIndexFor);
  // Not just distinct (an old scheme assigning index+1 per neighbour would pass that too): a weak hash could still
  // map consecutive QIDs to adjacent birds, and only one bird is lit per day, so every pair here must land more
  // than one bird apart.
  for (let i = 0; i < neighbours.length; i += 1) {
    for (let j = i + 1; j < neighbours.length; j += 1) {
      assert.ok(Math.abs(neighbours[i] - neighbours[j]) > 1, `QID som skiljer på en siffra hamnar inte bredvid varandra (${neighbours[i]} och ${neighbours[j]})`);
    }
  }
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
  assert.ok(svg.includes(`<circle cx="${x}" cy="${y}" r="${Math.round(size * DISC_SCALE * 100) / 100}" fill="${LIT.disc}" stroke="${LIT.ring}" stroke-width="2" vector-effect="non-scaling-stroke"/>`));
  // Today's bird is drawn last, bigger, in rust: its matrix puts the mark's centre on the bird's place.
  const m = svg.match(/<use href="#flock-mark" fill="#9A4526" transform="matrix\(([^)]+)\)"\/><\/svg>$/);
  assert.ok(m, 'dagens fågel sist');
  const [a, b, c, d, e, f] = m[1].split(' ').map(Number);
  assert.ok(Math.abs(a * MARK.cx + c * MARK.cy + e - x) < 0.05, 'x');
  assert.ok(Math.abs(b * MARK.cx + d * MARK.cy + f - y) < 0.05, 'y');
  assert.ok(Math.abs(Math.hypot(a, b) - (size * LIT_SCALE) / MARK.w) < 1e-4, 'storlek');
  assert.ok(Math.abs((Math.atan2(b, a) * 180) / Math.PI - rot) < 0.01, 'vridning');
});

test('COLOURS och LIT: den godkända paletten, dagens fågel i rost på en varm vit platta', () => {
  assert.deepEqual(COLOURS, ['#B8893A', '#A8552D', '#72301A', '#4A1F12']);
  assert.deepEqual(LIT, { bird: '#9A4526', disc: 'rgba(255, 248, 238, 0.96)', ring: '#A8552D' });
});

test('flightPlan: exakta värden ur prototypens formler, varje annan fågel på landing()s plats med rätt färg och opacitet', () => {
  const fit = fitView({ left: 700, top: 100, width: 654, height: 692 });
  const plan = flightPlan({ fit, width: 1440, height: 820, litIndex: 2 });
  // Pinned to the prototype's buildPlan (seed 839, same order of random numbers). Only +, -, *, / and min/max are
  // involved, so these doubles are exact on every engine.
  assert.equal(plan.total, 4967.277437498409);
  assert.deepEqual(plan.birds[0], {
    fx: 1063.7144444444443, fy: 439.65666666666664, size: 18.261111111111113, rot: 12, colour: 1, op: 1,
    sx: -406.62228888086975, sy: 723.9004262106494, cx: 671.561277910886, cy: 657.3559759320691,
    delay: 1383.9299808702829, dur: 1847.5389748811722, amp: 16.1052699200809, freq: 0.9702341575175524, phase: 1.968890657867771,
  });
  // Every landed bird where landing() puts it, with its own colour and opacity (what the canvas draws when still).
  FLOCK.birds.forEach((b, i) => {
    if (i === 2) return;
    const p = plan.birds[i < 2 ? i : i - 1];
    assert.deepEqual({ x: p.fx, y: p.fy, size: p.size, rot: p.rot }, landing(b, fit), `bird ${i}`);
    assert.equal(p.colour, b[4], `bird ${i} colour`);
    assert.equal(p.op, b[5], `bird ${i} opacity`);
  });
});

test('flockSvg: varje annan fågel i rätt färg, plats, storlek, vridning och opacitet (inte bara antalet use)', () => {
  const svg = flockSvg({ litIndex: 2 });
  const groups = [...svg.matchAll(/<g fill="([^"]+)">(.*?)<\/g>/g)];
  const drawn = groups.flatMap(([, fill, body]) =>
    [...body.matchAll(/<use href="#flock-mark" transform="matrix\(([^)]+)\)"(?: fill-opacity="([^"]+)")?\/>/g)].map(([, m, op]) => {
      const [a, b, c, d, e, f] = m.split(' ').map(Number);
      return { fill, x: a * MARK.cx + c * MARK.cy + e, y: b * MARK.cx + d * MARK.cy + f, size: Math.hypot(a, b) * MARK.w, rot: (Math.atan2(b, a) * 180) / Math.PI, op: op === undefined ? 1 : Number(op) };
    }),
  );
  assert.equal(drawn.length, FLOCK.birds.length - 1);
  FLOCK.birds.forEach((bird, i) => {
    if (i === 2) return;
    const [x, y, size, rot, colour, op] = bird;
    const hit = drawn.find((u) => Math.abs(u.x - x) < 0.05 && Math.abs(u.y - y) < 0.05 && Math.abs(u.size - size) < 0.01 && Math.abs(u.rot - rot) < 0.05);
    assert.ok(hit, `bird ${i} drawn at its place, size and rotation`);
    assert.equal(hit.fill, COLOURS[colour], `bird ${i} colour`);
    assert.equal(hit.op, op, `bird ${i} opacity`);
  });
});

test('flightPlan: höjd 0 (fönstret inte uppmätt än) ger inga NaN eller Infinity', () => {
  const fit = fitView({ left: 0, top: 0, width: 680, height: 720 });
  const plan = flightPlan({ fit, width: 1440, height: 0, litIndex: 0 });
  // height = 0 makes sy = 0 too, so phase's sy / height is 0 / 0 before the guard; every other field is finite
  // regardless of height.
  for (const p of [...plan.birds, plan.lit]) {
    for (const [key, v] of Object.entries(p)) assert.ok(Number.isFinite(v), `${key} = ${v}`);
  }
});

test('flockSvg: fel index kastar ett tydligt fel, inte en rå TypeError på odefinierad fågel', () => {
  assert.throws(() => flockSvg({ litIndex: 9999 }), /ingen fågel/);
  assert.throws(() => flockSvg({ litIndex: -1 }), /ingen fågel/);
  // FLOCK.birds.length is 839, so the last valid index is 838: catches a > vs >= off-by-one in the guard.
  assert.throws(() => flockSvg({ litIndex: 839 }), /ingen fågel/);
});

test('flockSvg: otillåtet className kastar, det går oskyddat in i sidans markup via set:html', () => {
  assert.throws(() => flockSvg({ litIndex: 0, className: '"><script>' }), /className/);
  // Pins the regex's anchors: a half-anchored check (missing ^ or $) would accept text with valid characters
  // before or after the attack, such as this one.
  assert.throws(() => flockSvg({ litIndex: 0, className: 'a" onload="b' }), /className/);
});
