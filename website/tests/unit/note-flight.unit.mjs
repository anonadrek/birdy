// The flight between the notes' pictures (src/lib/note-flight.mjs), spec 2026-10-10 "Bloggen som en flygväg".
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { pairFlights, at, flightCurve, birdsAlong, clearOf, birdTransform, artKey } from '../../src/lib/note-flight.mjs';
import { markMatrix } from '../../src/components/hero/flock.mjs';

const R = (x, y, w = 300, h = 158) => ({ x, y, w, h });

test('staplade kort: varje bild flyger upp in i bilden ovanför', () => {
  const rects = [R(16, 100), R(16, 500), R(16, 900)];
  assert.deepEqual(pairFlights(rects), [
    { from: 1, to: 0, dir: 'up' },
    { from: 2, to: 1, dir: 'up' },
  ]);
});

test('kort sida vid sida: flygningen går åt höger in i nästa kort', () => {
  const rects = [R(100, 200), R(450, 200), R(800, 203)];
  assert.deepEqual(pairFlights(rects), [
    { from: 0, to: 1, dir: 'right' },
    { from: 1, to: 2, dir: 'right' },
  ]);
});

test('bloggen på bred skärm: det stora kortet överst, två under sida vid sida', () => {
  const rects = [R(184, 390, 634, 333), R(184, 747, 525, 275), R(732, 747, 525, 275)];
  assert.deepEqual(pairFlights(rects), [
    { from: 1, to: 0, dir: 'up' },
    { from: 1, to: 2, dir: 'right' },
  ]);
});

test('en enda bild har ingen flygning', () => {
  assert.deepEqual(pairFlights([R(0, 0)]), []);
});

test('at: en punkt i bildens egna koordinater', () => {
  assert.deepEqual(at(R(100, 200, 400, 200), { x: 0.5, y: 0.25 }), { x: 300, y: 250 });
});

/** The direction a drawn bird faces, from what birdsAlong returns. */
const facing = (b) => (b.mirror ? b.rot + 180 : b.rot);
const norm = (d) => ((d % 360) + 540) % 360 - 180;

test('fåglarna längs en flygning uppåt pekar uppåt längs kurvan, ingen upp och ner', () => {
  const curve = flightCurve({ x: 200, y: 600 }, { x: 260, y: 150 }, 'up');
  const birds = birdsAlong(curve, { seed: 3 });
  assert.ok(birds.length >= 6);
  for (const b of birds) {
    const f = norm(facing(b));
    assert.ok(f < -20 && f > -160, `pekar uppåt: ${f}`);
    // Mirrored exactly when the heading points left, so the mark is never drawn belly up.
    assert.equal(b.mirror, Math.cos((f * Math.PI) / 180) < 0);
  }
});

test('fåglarna längs en flygning åt höger pekar åt höger och speglas inte', () => {
  const curve = flightCurve({ x: 100, y: 300 }, { x: 700, y: 320 }, 'right');
  for (const b of birdsAlong(curve, { seed: 5 })) {
    assert.equal(b.mirror, false);
    assert.ok(Math.abs(norm(facing(b))) < 90);
  }
});

test('ändarna hör till flockarna: ingen fågel närmare än 8 px start eller slut', () => {
  const start = { x: 0, y: 500 }, end = { x: 0, y: 0 };
  const birds = birdsAlong(flightCurve(start, end, 'up'), { seed: 1 });
  for (const b of birds) {
    assert.ok(Math.hypot(b.x - start.x, b.y - start.y) > 8);
    assert.ok(Math.hypot(b.x - end.x, b.y - end.y) > 8);
  }
});

test('samma frö ger samma fåglar', () => {
  const c = flightCurve({ x: 10, y: 400 }, { x: 90, y: 20 }, 'up');
  assert.deepEqual(birdsAlong(c, { seed: 9 }), birdsAlong(c, { seed: 9 }));
});

test('clearOf: fåglar över en textruta ritas inte', () => {
  const birds = [{ x: 50, y: 50, size: 10 }, { x: 200, y: 200, size: 10 }];
  assert.deepEqual(clearOf(birds, [{ x: 0, y: 0, w: 100, h: 100 }]), [birds[1]]);
});

test('birdTransform: utan spegling samma matris som flockens egen markMatrix', () => {
  assert.equal(birdTransform(120, 80, 14, -30, false), markMatrix(120, 80, 14, -30));
});

test('birdTransform: speglad vänder bara x-axeln (a och b byter tecken)', () => {
  const nums = (s) => s.slice(7, -1).split(' ').map(Number);
  const [a, b, c, d] = nums(birdTransform(0, 0, 14, 20, false));
  const [ma, mb, mc, md] = nums(birdTransform(0, 0, 14, 20, true));
  assert.deepEqual([ma, mb, mc, md], [-a, -b, c, d]);
});

test('artKey: bildfilens namn ur bygget och ur dev-servern', () => {
  assert.equal(artKey('/_astro/see-the-song-flock-q25404-en.DYGdC81L.webp'), 'see-the-song-flock-q25404-en');
  assert.equal(artKey('/@fs/C:/w/birdy-flyg/website/src/assets/photos/why-birdy-flock-q25334-sv.webp?origWidth=3200&origHeight=1680&origFormat=webp'), 'why-birdy-flock-q25334-sv');
  assert.equal(artKey('/_astro/birdy-x-albit.Ab12Cd34.webp'), 'birdy-x-albit');
});
