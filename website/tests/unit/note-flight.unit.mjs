// The flight between the notes' pictures (src/lib/note-flight.mjs), spec 2026-10-10 "Bloggen som en flygväg".
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { pairFlights, at, flightCurve, textInTheWay, marginLane, laneFlight, birdsAlong, clearOf, birdTransform, artKey } from '../../src/lib/note-flight.mjs';
import { markMatrix } from '../../src/components/hero/flock.mjs';
import { MARK } from '../../src/components/hero/flock-data.mjs';

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

/** How often the mirror state changes along a flight: a calm flight turns its birds over at most once. */
const flips = (birds) => birds.slice(1).filter((b, i) => b.mirror !== birds[i].mirror).length;

test('fåglarna längs en flygning uppåt pekar uppåt längs kurvan, ingen upp och ner', () => {
  const curve = flightCurve({ x: 200, y: 600 }, { x: 260, y: 150 }, 'up');
  const birds = birdsAlong(curve, { seed: 3 });
  assert.ok(birds.length >= 6);
  for (const b of birds) {
    const f = norm(facing(b));
    assert.ok(f < -20 && f > -160, `pekar uppåt: ${f}`);
    // Never belly up: a mirrored bird is turned as far as an unmirrored one, at most a little past upright.
    assert.ok(Math.abs(norm(b.rot)) <= 105, `rot ${b.rot}`);
  }
  assert.ok(flips(birds) <= 1);
});

test('en fågel på väg åt vänster speglas i stället för att vändas upp och ner', () => {
  const curve = flightCurve({ x: 700, y: 300 }, { x: 100, y: 320 }, 'right');
  const birds = birdsAlong(curve, { seed: 4 });
  for (const b of birds) {
    assert.equal(b.mirror, true);
    assert.ok(Math.abs(norm(facing(b))) > 90, `pekar åt vänster: ${facing(b)}`);
    assert.ok(Math.abs(norm(b.rot)) <= 90);
  }
});

test('rakt uppåt fladdrar fåglarna inte mellan speglad och inte: hela stigningen vänder samma sida', () => {
  // Straight up: every heading sits a few degrees either side of vertical, where a per bird rule would flip at random.
  const climb = [{ x: 50, y: 900 }, { x: 50, y: 600 }, { x: 50, y: 300 }, { x: 50, y: 0 }];
  for (const seed of [1, 2, 3, 4, 5, 6, 7, 8]) {
    const birds = birdsAlong(climb, { seed });
    assert.equal(flips(birds), 0, `frö ${seed}`);
    for (const b of birds) assert.ok(Math.abs(norm(b.rot)) <= 105);
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

// The phone's blog (390 px): three stacked cards, each picture above its own text, a 20 px margin on the right.
const phone = {
  upper: R(20, 516, 350, 184),
  text: R(20, 700, 350, 149),
  lower: R(20, 869, 350, 184),
  start: { x: 171, y: 873 },
  end: { x: 265, y: 590 },
};

test('textInTheWay: på telefonen står det övre kortets text mellan bilderna', () => {
  assert.deepEqual(textInTheWay(phone.start, phone.end, [R(20, 184, 350, 312), phone.text, R(20, 1053, 350, 125)]), phone.text);
});

test('textInTheWay: på bred skärm står det stora kortets text bredvid bilden, och flygningen går rakt upp', () => {
  // The blog at 1440 px: the large card's text to the right of its picture, the climb from the grid below it.
  const texts = [R(669, 0, 447, 333), R(44, 632, 524, 125), R(592, 632, 524, 125)];
  assert.equal(textInTheWay({ x: 541, y: 377 }, { x: 514, y: 153 }, texts), null);
});

test('textInTheWay: två rader i rutnätet, snett upp till kortet ovanför, bakom texten eller genom springan', () => {
  // Five notes at 1440 px: two rows of two under the large card; the second row's left card climbs to the right card
  // above it. From the middle of its picture the climb passes behind the first row's left text and takes the margin;
  // from its right edge it slips up through the gap between the two texts and stays direct.
  const texts = [R(669, 0, 447, 333), R(44, 632, 524, 125), R(592, 632, 524, 125), R(44, 1056, 524, 125), R(592, 1056, 524, 125)];
  assert.deepEqual(textInTheWay({ x: 270, y: 787 }, { x: 959, y: 467 }, texts), R(44, 632, 524, 125));
  assert.equal(textInTheWay({ x: 541, y: 801 }, { x: 722, y: 460 }, texts), null);
});

test('marginLane: mitt i marginalen, en fågel som får plats, inget alls när marginalen är för smal', () => {
  const lane = marginLane(370, 388);
  assert.equal(lane.x, 379);
  assert.ok(lane.size >= 8 && lane.size <= 10, `storlek ${lane.size}`);
  assert.equal(marginLane(724, 766).size, 11);
  assert.equal(marginLane(370, 378), null);
  // No cards measured (Math.max of nothing): no lane rather than a lane at NaN.
  assert.equal(marginLane(-Infinity, 388), null);
});

/** A straight line as a cubic Bézier (control points at a third and two thirds: even speed along it). */
const line = (a, b) => [a, { x: a.x + (b.x - a.x) / 3, y: a.y + (b.y - a.y) / 3 }, { x: a.x + (2 * (b.x - a.x)) / 3, y: a.y + (2 * (b.y - a.y)) / 3 }, b];

test('laneFlight: ut till marginalen, upp förbi texten och in i bilden ovanför, utan knyck i fogarna', () => {
  const path = laneFlight(phone.start, phone.end, phone.text, 379);
  assert.equal(path.length, 3);
  assert.deepEqual(path[0][0], phone.start);
  assert.deepEqual(path[2][3], phone.end);
  // The climb runs in the lane from the text's foot to its head.
  assert.deepEqual(path[1][0], { x: 379, y: 849 });
  assert.deepEqual(path[1][3], { x: 379, y: 700 });
  assert.ok(path[1].every((p) => p.x === 379));
  for (let i = 0; i < 2; i++) {
    // Each piece ends where the next begins, and both head straight up there.
    assert.deepEqual(path[i][3], path[i + 1][0]);
    assert.equal(path[i][2].x, path[i][3].x);
    assert.ok(path[i][2].y > path[i][3].y);
    assert.equal(path[i + 1][1].x, path[i + 1][0].x);
    assert.ok(path[i + 1][1].y < path[i + 1][0].y);
  }
});

test('birdsAlong över flera bitar: jämnt fördelade efter båglängd över hela vägen, vända efter varje bit', () => {
  const path = [line({ x: 0, y: 0 }, { x: 300, y: 0 }), line({ x: 300, y: 0 }, { x: 300, y: -300 })];
  const birds = birdsAlong(path, { gap: 50, seed: 2 });
  assert.equal(birds.length, 11);
  birds.forEach((b, i) => {
    const s = (i + 1) * 50;
    const want = s <= 300 ? { x: s, y: 0 } : { x: 300, y: 300 - s };
    assert.ok(Math.hypot(b.x - want.x, b.y - want.y) < 1.5, `fågel ${i}: ${b.x},${b.y} mot ${want.x},${want.y}`);
    assert.ok(Math.abs(b.t - s / 600) < 0.01);
    const f = norm(facing(b));
    if (s < 300) assert.ok(Math.abs(f) <= 3, `åt höger: ${f}`);
    if (s > 300) assert.ok(Math.abs(f + 90) <= 3, `uppåt: ${f}`);
  });
  assert.ok(flips(birds) === 0);
});

test('birdsAlong: en lång väg får högst max fåglar', () => {
  const birds = birdsAlong(line({ x: 0, y: 2000 }, { x: 0, y: 0 }), { gap: 26, max: 15 });
  assert.equal(birds.length, 14);
});

/** The x span a bird actually covers: the mark's own box, drawn with the same matrix as on the page. */
function span(b) {
  // matrix(a b c d e f): a point (u, v) of the mark lands at x = a u + c v + e.
  const m = birdTransform(b.x, b.y, b.size, b.rot, b.mirror).slice(7, -1).split(' ').map(Number);
  const xs = [[0, 0], [MARK.w, 0], [0, MARK.h], [MARK.w, MARK.h]].map(([u, v]) => m[0] * u + m[2] * v + m[4]);
  return { left: Math.min(...xs), right: Math.max(...xs) };
}

test('marginalvägen på telefonen: ingen fågel utanför sidan och ingen över texten, de flesta syns', () => {
  const lane = marginLane(370, 388);
  const path = laneFlight(phone.start, phone.end, phone.text, lane.x);
  const birds = birdsAlong(path, { size: lane.size, gap: 34, seed: 101 });
  assert.ok(birds.length >= 10 && birds.length <= 15, `${birds.length} fåglar`);
  for (const b of birds) {
    const { left, right } = span(b);
    assert.ok(right <= 388, `utanför sidan: ${right}`);
    // Beside the text, in the lane: clear of the card's right edge.
    if (b.y > phone.text.y && b.y < phone.text.y + phone.text.h) assert.ok(left >= 370, `över kortet: ${left}`);
  }
  // Nothing on the way is hidden behind the text: the whole flight shows.
  assert.equal(clearOf(birds, [phone.text, { x: 388, y: -1e5, w: 1e5, h: 2e5 }]).length, birds.length);
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
