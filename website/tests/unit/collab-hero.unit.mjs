// The Birdy × AlbIT hero (src/lib/collab-hero.mjs): deterministic, the birds on each side in their own brand's colours.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ALBIT, collabArt, collabDefs } from '../../src/lib/collab-hero.mjs';
import { COLOURS } from '../../src/components/hero/flock.mjs';

const birds = (svg) => [...svg.matchAll(/<use href="#bxa-m" fill="(#[0-9A-F]{6})"[^>]*transform="translate\(([-\d.]+) ([-\d.]+)\)/g)]
  .map(([, fill, x, y]) => ({ fill, x: Number(x), y: Number(y) }));

test('collabDefs: the mark and the bird of the flock, in Birdy colours only', () => {
  const defs = collabDefs();
  assert.match(defs, /<path id="bxa-m" d="M/);
  const flock = birds(defs);
  assert.ok(flock.length > 600, `${flock.length} birds form the bird`);
  for (const b of flock) assert.ok(COLOURS.includes(b.fill), `${b.fill} is one of the flock's colours`);
  assert.equal(collabDefs(), defs, 'the same birds every build');
});

for (const [variant, axis, seam] of [['wide', 'x', 800], ['tall', 'y', 500]]) {
  test(`collabArt(${variant}): gold birds on AlbIT's side, Birdy's colours on the paper, the bird and the wordmark`, () => {
    const svg = collabArt(variant, '/_astro/w.webp');
    assert.equal(collabArt(variant, '/_astro/w.webp'), svg, 'deterministic');
    assert.match(svg, new RegExp(`class="bxa-art bxa-${variant}"`));
    assert.match(svg, /<use href="#bxa-bird" transform="translate\([-\d.]+ [-\d.]+\) scale\([\d.]+\)"\/>/);
    assert.match(svg, /<image href="\/_astro\/w\.webp"/);
    assert.match(svg, />Birdy\.<\/text>/);
    const stream = birds(svg);
    assert.ok(stream.length > 60, `${stream.length} birds in the stream`);
    const gold = stream.filter((b) => ALBIT.golds.includes(b.fill));
    const rust = stream.filter((b) => COLOURS.includes(b.fill));
    assert.ok(gold.length > 20 && rust.length > 10, `${gold.length} gold, ${rust.length} in Birdy colours`);
    for (const b of gold) assert.ok(b[axis] < seam, `gold bird at ${axis}=${b[axis]} is on AlbIT's side`);
    for (const b of rust) assert.ok(b[axis] >= seam, `rust bird at ${axis}=${b[axis]} is on the paper`);
  });
}
