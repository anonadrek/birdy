// The flock around the year (src/lib/year-flock.mjs): the Premium band's picture, 2026-10-10.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { BIRDS, MONTHS, RING, yearFlockSvg, yearFraction } from '../../src/lib/year-flock.mjs';

test('yearFraction: the middle of the day, from the top of the year round to its end', () => {
  assert.equal(yearFraction('2026-01-01'), 0.5 / 365);
  assert.equal(yearFraction('2026-12-31'), 364.5 / 365);
  assert.equal(yearFraction('2028-12-31'), 365.5 / 366); // a leap year has 366 days
  assert.ok(Math.abs(yearFraction('2026-07-02') - 0.5) < 0.002);
  assert.throws(() => yearFraction('10 October'), /inte ett datum/);
});

test('ringen har hela flocken: 839 fåglar, en av dem tänd för dagen', () => {
  const svg = yearFlockSvg({ iso: '2026-10-10' });
  const birds = svg.slice(svg.indexOf('<g class="yf-birds">'), svg.indexOf('<g class="yf-lit">'));
  const lit = svg.slice(svg.indexOf('<g class="yf-lit">'), svg.indexOf('<g class="yf-months">'));
  assert.equal(BIRDS, 839);
  assert.equal(birds.match(/<use /g).length + lit.match(/<use /g).length, BIRDS);
  assert.equal(lit.match(/<circle /g).length, 1);
});

test('dagens fågel sitter där året är: 10 oktober till vänster ovanför mitten, 1 januari överst', () => {
  const litAt = (iso) => {
    const svg = yearFlockSvg({ iso });
    const m = svg.match(/<g class="yf-lit"><circle cx="([\d.]+)" cy="([\d.]+)"/);
    return [Number(m[1]), Number(m[2])];
  };
  const [x, y] = litAt('2026-10-10');
  assert.ok(x < RING.cx - RING.r * 0.8, `x ${x}`);
  assert.ok(y < RING.cy, `y ${y}`);
  const [x1, y1] = litAt('2026-01-01');
  assert.ok(Math.abs(x1 - RING.cx) < 5 && y1 < RING.cy - RING.r * 0.99, `${x1} ${y1}`);
});

test('månaden står i rost, de andra elva inte; bokstäverna är årets tolv', () => {
  const svg = yearFlockSvg({ iso: '2026-10-01' });
  const letters = [...svg.matchAll(/<text [^>]*>([A-Z])<\/text>/g)].map((m) => m[1]);
  assert.deepEqual(letters, MONTHS);
  const now = [...svg.matchAll(/<text [^>]*class="yf-now"[^>]*>([A-Z])<\/text>/g)];
  assert.equal(now.length, 1);
  // 1 October is October, never September (twelve equal sectors would say September).
  assert.ok(svg.includes('class="yf-now">O</text>'));
});

test('dagens fågel är vänd efter ringens egen medsols tangent vid sin plats, inte ett fast håll', () => {
  const sample = (iso) => {
    const svg = yearFlockSvg({ iso });
    const circle = svg.match(/<g class="yf-lit"><circle cx="([-\d.]+)" cy="([-\d.]+)"/);
    const use = svg.match(/<g class="yf-lit">[\s\S]*?<use[^>]*transform="matrix\(([^)]+)\)"/);
    const [cx, cy] = [Number(circle[1]), Number(circle[2])];
    const [a, b] = use[1].trim().split(/\s+/).map(Number);
    return { cx, cy, rot: (Math.atan2(b, a) * 180) / Math.PI };
  };
  // Four spots around the ring (top, right, bottom, left): the tangent for clockwise flight is the outward radius
  // turned 90 degrees, never the old fixed -8 (which only ever matched the bird by coincidence at one spot).
  for (const iso of ['2026-01-01', '2026-04-10', '2026-07-20', '2026-10-10']) {
    const { cx, cy, rot } = sample(iso);
    const radial = (Math.atan2(cy - RING.cy, cx - RING.cx) * 180) / Math.PI;
    const expected = radial + 90;
    const diff = ((rot - expected + 540) % 360) - 180;
    assert.ok(Math.abs(diff) < 0.5, `${iso}: rot ${rot} väntat ${expected} (vid ${cx},${cy})`);
  }
});

test('samma dag ger samma bild, en annan dag flyttar bara fåglarna kring den tända', () => {
  assert.equal(yearFlockSvg({ iso: '2026-10-10' }), yearFlockSvg({ iso: '2026-10-10' }));
  const transforms = (iso) => new Set([...yearFlockSvg({ iso }).matchAll(/transform="([^"]+)"/g)].map((m) => m[1]));
  const today = transforms('2026-10-10');
  const moved = [...transforms('2026-10-11')].filter((t) => !today.has(t));
  // The lit bird and the few birds that make room for its disc, today's and tomorrow's: a few dozen of 839 (36 measured).
  assert.ok(moved.length > 0 && moved.length <= 40, `${moved.length} flyttade`);
});
