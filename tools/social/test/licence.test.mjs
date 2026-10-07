import { test } from 'node:test';
import assert from 'node:assert/strict';
import { licenceAllowed, licenceUrl, qualifies, usesShareAlike } from '../lib/licence.mjs';
import { record, withHero, withAudio } from './fixtures.mjs';

test('allowed licences: CC0, public domain, CC BY and CC BY-SA 2.0, 3.0, 4.0', () => {
  for (const l of ['CC0', 'CC0 1.0', 'Public domain', 'public domain', 'CC BY 2.0', 'CC BY 3.0', 'CC BY 4.0', ' CC  BY 4.0 ', 'CC BY-SA 2.0', 'CC BY-SA 3.0', 'CC BY-SA 4.0']) {
    assert.equal(licenceAllowed(l), true, l);
  }
});

test('never non-commercial, no-derivatives or unknown licences', () => {
  for (const l of ['CC BY-NC 4.0', 'CC BY-NC-SA 4.0', 'CC BY-ND 4.0', 'CC BY-NC-ND 3.0', 'CC BY 2.5', 'CC BY-SA 2.5', 'GFDL', '', null, undefined]) {
    assert.equal(licenceAllowed(l), false, String(l));
  }
});

test('a species qualifies when both photo and recording are allowed', () => {
  assert.deepEqual(qualifies(record()), { ok: true, reasons: [] });
  assert.equal(qualifies(withHero(withAudio(record(), { license: 'CC0', author: '' }), { license: 'Public domain', author: '' })).ok, true);
  assert.equal(qualifies(withHero(withAudio(record(), { license: 'CC BY-SA 3.0' }), { license: 'CC BY-SA 4.0' })).ok, true);
});

test('a non-commercial photo or recording disqualifies the species', () => {
  const photo = qualifies(withHero(record(), { license: 'CC BY-NC 4.0' }));
  assert.equal(photo.ok, false);
  assert.match(photo.reasons.join(), /photo licence "CC BY-NC 4.0"/);
  const sound = qualifies(withAudio(record(), { license: 'CC BY-NC-SA 3.0' }));
  assert.equal(sound.ok, false);
  assert.match(sound.reasons.join(), /recording licence "CC BY-NC-SA 3.0"/);
});

test('only the hero photo counts, not the extra photo', () => {
  const rec = { ...record(), images: [...record().images, { role: 'extra', file: 'x.webp', license: 'CC BY-NC 4.0', author: 'x' }] };
  assert.equal(qualifies(rec).ok, true);
});

test('CC BY and CC BY-SA need a named author', () => {
  const r = qualifies(withAudio(record(), { license: 'CC BY 4.0', author: '  ' }));
  assert.equal(r.ok, false);
  assert.match(r.reasons.join(), /recording is CC BY 4.0 but has no author/);
  assert.equal(qualifies(withHero(record(), { author: '' })).ok, false);
  assert.match(qualifies(withAudio(record(), { license: 'CC BY-SA 4.0', author: '' })).reasons.join(), /recording is CC BY-SA 4.0 but has no author/);
});

test('share-alike material makes the video CC BY-SA', () => {
  assert.equal(usesShareAlike(record()), false);
  assert.equal(usesShareAlike(withAudio(record(), { license: 'CC BY-SA 3.0' })), true);
  assert.equal(usesShareAlike(withHero(record(), { license: 'CC BY-SA 2.0' })), true);
});

test('licence deed: the record URL first, else derived; none for public domain', () => {
  assert.equal(licenceUrl({ license: 'CC BY 4.0', licenseUrl: 'https://example.org/x' }), 'https://example.org/x');
  assert.equal(licenceUrl({ license: 'CC BY-SA 3.0' }), 'https://creativecommons.org/licenses/by-sa/3.0/');
  assert.equal(licenceUrl({ license: 'CC BY 2.0', licenseUrl: null }), 'https://creativecommons.org/licenses/by/2.0/');
  assert.equal(licenceUrl({ license: 'CC0', licenseUrl: null }), 'https://creativecommons.org/publicdomain/zero/1.0/');
  assert.equal(licenceUrl({ license: 'Public domain', licenseUrl: null }), null);
});

test('missing photo or recording disqualifies the species', () => {
  assert.equal(qualifies({ ...record(), audio: undefined }).ok, false);
  assert.equal(qualifies({ ...record(), images: [] }).ok, false);
});
