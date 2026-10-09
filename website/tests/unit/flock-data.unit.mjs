import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';
import { FLOCK, MARK } from '../../src/components/hero/flock-data.mjs';

// The flock Albin approved: the social profiles' "cover" flock, which the motion prototype draws. The source file is a
// browser script (window.FLOCK = ...), so it runs in a sandbox like scripts/import-flock-data.mjs does, and is copied
// through JSON so its arrays compare with this realm's.
const sandbox = { window: {} };
vm.runInNewContext(readFileSync(new URL('../../../docs/superpowers/specs/assets/2026-10-08-flocken-webben/flock-data.js', import.meta.url), 'utf8'), sandbox);
const source = JSON.parse(JSON.stringify(sandbox.window.FLOCK));

test('flockdatan är prototypens flock, fågel för fågel', () => {
  assert.equal(MARK.path, source.path);
  assert.deepEqual([MARK.w, MARK.h, MARK.cx, MARK.cy], [source.w, source.h, source.mec.cx, source.mec.cy]);
  assert.deepEqual([FLOCK.w, FLOCK.h, FLOCK.edge], [source.flocks.cover.w, source.flocks.cover.h, source.flocks.cover.edge]);
  assert.deepEqual(FLOCK.birds, source.flocks.cover.birds);
});

test('flockdatan: en fågel per art i Birdy, 178 i hjärtat, rimliga värden', () => {
  assert.equal(FLOCK.birds.length, 839);
  assert.equal(FLOCK.edge, 178);
  for (const [i, bird] of FLOCK.birds.entries()) {
    const [x, y, size, rot, colour, opacity] = bird;
    assert.equal(bird.length, 6, `fågel ${i}`);
    assert.ok(x >= 0 && x <= FLOCK.w, `fågel ${i}: x ${x}`);
    assert.ok(y >= 0 && y <= FLOCK.h * 1.2, `fågel ${i}: y ${y}`);
    assert.ok(size > 0 && size < 40, `fågel ${i}: storlek ${size}`);
    assert.ok(Number.isFinite(rot), `fågel ${i}: vridning ${rot}`);
    assert.ok([0, 1, 2, 3].includes(colour), `fågel ${i}: färg ${colour}`);
    assert.ok(opacity > 0 && opacity <= 1, `fågel ${i}: opacitet ${opacity}`);
  }
});
