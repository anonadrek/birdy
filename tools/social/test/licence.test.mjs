import { test } from 'node:test';
import assert from 'node:assert/strict';
import { licenceAllowed, qualifies, usesShareAlike } from '../lib/licence.mjs';
import { record, withHero, withAudio } from './fixtures.mjs';

test('allowed licences: CC0, public domain and CC BY 2.0, 3.0, 4.0', () => {
  for (const l of ['CC0', 'CC0 1.0', 'Public domain', 'public domain', 'CC BY 2.0', 'CC BY 3.0', 'CC BY 4.0', ' CC  BY 4.0 ']) {
    assert.equal(licenceAllowed(l), true, l);
  }
});

test('never share-alike, non-commercial, no-derivatives or unknown licences', () => {
  for (const l of ['CC BY-SA 4.0', 'CC BY-SA 3.0', 'CC BY-SA 2.0', 'CC BY-NC 4.0', 'CC BY-NC-SA 4.0', 'CC BY-ND 4.0', 'CC BY 2.5', 'GFDL', '', null, undefined]) {
    assert.equal(licenceAllowed(l), false, String(l));
  }
});

test('a species qualifies when both photo and recording are allowed', () => {
  assert.deepEqual(qualifies(record()), { ok: true, reasons: [] });
  assert.equal(qualifies(withHero(withAudio(record(), { license: 'CC0', author: '' }), { license: 'Public domain', author: '' })).ok, true);
});

test('a share-alike photo or recording disqualifies the species', () => {
  const photo = qualifies(withHero(record(), { license: 'CC BY-SA 4.0' }));
  assert.equal(photo.ok, false);
  assert.match(photo.reasons.join(), /photo licence "CC BY-SA 4.0"/);
  const sound = qualifies(withAudio(record(), { license: 'CC BY-SA 3.0' }));
  assert.equal(sound.ok, false);
  assert.match(sound.reasons.join(), /recording licence "CC BY-SA 3.0"/);
});

test('only the hero photo counts, not the extra photo', () => {
  // The fixture's extra photo is CC BY-SA and the species still qualifies.
  assert.equal(qualifies(record()).ok, true);
});

test('CC BY needs a named author', () => {
  const r = qualifies(withAudio(record(), { license: 'CC BY 4.0', author: '  ' }));
  assert.equal(r.ok, false);
  assert.match(r.reasons.join(), /recording is CC BY 4.0 but has no author/);
  assert.equal(qualifies(withHero(record(), { author: '' })).ok, false);
});

test('share-alike mode also admits CC BY-SA 2.0, 3.0, 4.0, never NC or ND', () => {
  const sa = { shareAlike: true };
  for (const l of ['CC BY-SA 2.0', 'CC BY-SA 3.0', 'CC BY-SA 4.0', 'CC0', 'Public domain', 'CC BY 4.0']) assert.equal(licenceAllowed(l, sa), true, l);
  for (const l of ['CC BY-NC 4.0', 'CC BY-NC-SA 4.0', 'CC BY-ND 4.0', 'CC BY-NC-ND 3.0', 'CC BY-SA 2.5', 'GFDL']) assert.equal(licenceAllowed(l, sa), false, l);
  const rec = withAudio(withHero(record(), { license: 'CC BY-SA 4.0' }), { license: 'CC BY-SA 3.0', author: 'Someone' });
  assert.equal(qualifies(rec).ok, false);
  assert.equal(qualifies(rec, sa).ok, true);
  assert.equal(usesShareAlike(rec), true);
  assert.equal(usesShareAlike(record()), false);
});

test('share-alike mode still needs a named author for CC BY-SA', () => {
  const rec = withAudio(record(), { license: 'CC BY-SA 4.0', author: '' });
  const r = qualifies(rec, { shareAlike: true });
  assert.equal(r.ok, false);
  assert.match(r.reasons.join(), /recording is CC BY-SA 4.0 but has no author/);
});

test('missing photo or recording disqualifies the species', () => {
  assert.equal(qualifies({ ...record(), audio: undefined }).ok, false);
  assert.equal(qualifies({ ...record(), images: [] }).ok, false);
});
