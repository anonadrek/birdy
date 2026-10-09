// node --test "tests/unit/*.unit.mjs": the clips page's rules (src/lib/clips.mjs, spec 2026-10-09-klippsidan).
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { clipLink, clipsHref, clipsModuleSource, creditParts, licenceDeed, visibleClips } from '../../src/lib/clips.mjs';

const LF = String.fromCharCode(10);
const clip = (date, slug, qid) => ({
  date,
  qid,
  slug,
  names: { sv: `${slug} (sv)`, en: `${slug} (en)`, scientific: 'Genus species' },
  silhouette: { author: 'Andy Wilson', licence: 'CC0', url: 'https://www.phylopic.org/images/abfaf52a-6fa1-48b7-91ba-b65d369a2fe2', adapted: false },
});
const all = [clip('2026-10-09', 'eurasian-blue-tit', 'Q25404'), clip('2026-10-10', 'common-crane', 'Q4764'), clip('2026-10-16', 'european-robin', 'Q25334')];

test('clipsHref: /clips/ på engelska och /sv/klipp/ på svenska', () => {
  assert.equal(clipsHref('en'), '/clips/');
  assert.equal(clipsHref('sv'), '/sv/klipp/');
});

test('visibleClips: klippen till och med dagens datum, nyaste först, utan att ändra listan', () => {
  const before = JSON.stringify(all);
  assert.deepEqual(visibleClips(all, '2026-10-15').map((c) => c.slug), ['common-crane', 'eurasian-blue-tit']);
  assert.deepEqual(visibleClips(all, '2026-10-16').map((c) => c.slug), ['european-robin', 'common-crane', 'eurasian-blue-tit']);
  assert.deepEqual(visibleClips(all, '2026-10-08'), []);
  assert.equal(JSON.stringify(all), before);
});

// Runs the generated module with each cover import replaced by its path, so the test sees the exported list.
async function evaluate(source) {
  const stubbed = source.replace(/^import (cover\d+) from (".*");$/gm, 'const $1 = $2;');
  return (await import(`data:text/javascript,${encodeURIComponent(stubbed)}`)).clips;
}

test('clipsModuleSource: importerar bara de visade klippens omslag, nyaste först', async () => {
  const source = clipsModuleSource(all, '2026-10-15');
  assert.deepEqual(source.match(/^import .*$/gm), [
    'import cover0 from "/src/assets/clips/common-crane.jpg";',
    'import cover1 from "/src/assets/clips/eurasian-blue-tit.jpg";',
  ]);
  assert.ok(!source.includes('european-robin'), 'ett senare klipps omslag importeras inte');
  const clips = await evaluate(source);
  assert.deepEqual(clips.map((c) => [c.date, c.slug, c.cover]), [
    ['2026-10-10', 'common-crane', '/src/assets/clips/common-crane.jpg'],
    ['2026-10-09', 'eurasian-blue-tit', '/src/assets/clips/eurasian-blue-tit.jpg'],
  ]);
  assert.deepEqual(clips[0].names, all[1].names);
  assert.deepEqual(clips[0].silhouette, all[1].silhouette);
});

test('clipsModuleSource: före det första klippet en tom lista och inga importer', async () => {
  const source = clipsModuleSource(all, '2026-10-08');
  assert.equal(source, `export const clips = [];${LF}`);
  assert.deepEqual(await evaluate(source), []);
});

test('clipLink: artsidans namn och adress när bygget har sidan, annars klippets namn utan länk', () => {
  const crane = { names: { sv: 'Trana', en: 'Common Crane' }, slug: { sv: 'trana', en: 'common-crane' } };
  const hrefOf = (s, locale) => (locale === 'sv' ? `/sv/arter/${s.slug.sv}/` : `/species/${s.slug.en}/`);
  const c = { ...clip('2026-10-10', 'common-crane', 'Q4764'), names: { sv: 'Trana i klippet', en: 'Crane in the clip', scientific: 'Grus grus' } };
  assert.deepEqual(clipLink(c, crane, 'sv', hrefOf), { name: 'Trana', href: '/sv/arter/trana/' });
  assert.deepEqual(clipLink(c, crane, 'en', hrefOf), { name: 'Common Crane', href: '/species/common-crane/' });
  assert.deepEqual(clipLink(c, undefined, 'sv', hrefOf), { name: 'Trana i klippet', href: undefined });
  assert.deepEqual(clipLink(c, undefined, 'en', hrefOf), { name: 'Crane in the clip', href: undefined });
});

test('licenceDeed: CC0, CC BY, CC BY-SA och public domain mark, deed.sv på svenska, okänd licens utan adress', () => {
  assert.equal(licenceDeed('CC0', 'en'), 'https://creativecommons.org/publicdomain/zero/1.0/');
  assert.equal(licenceDeed('CC0', 'sv'), 'https://creativecommons.org/publicdomain/zero/1.0/deed.sv');
  assert.equal(licenceDeed('CC BY 3.0', 'en'), 'https://creativecommons.org/licenses/by/3.0/');
  assert.equal(licenceDeed('CC BY 4.0', 'sv'), 'https://creativecommons.org/licenses/by/4.0/deed.sv');
  assert.equal(licenceDeed('CC BY-SA 4.0', 'en'), 'https://creativecommons.org/licenses/by-sa/4.0/');
  assert.equal(licenceDeed('Public domain mark', 'en'), 'https://creativecommons.org/publicdomain/mark/1.0/');
  assert.equal(licenceDeed('All rights reserved', 'en'), undefined);
});

const words = { en: { label: 'Silhouette', adapted: 'adapted' }, sv: { label: 'Siluett', adapted: 'bearbetad' } };
const asText = (parts) => parts.map((p) => p.text).join('');

test('creditParts: bildtexternas ord, licensen och PhyloPic som länkar, CC0 utan "adapted"', () => {
  const s = all[1].silhouette;
  const parts = creditParts(s, 'en', words.en);
  assert.equal(asText(parts), 'Silhouette: Andy Wilson, CC0, via PhyloPic');
  assert.deepEqual(parts.filter((p) => p.href).map((p) => [p.text, p.href]), [
    ['CC0', 'https://creativecommons.org/publicdomain/zero/1.0/'],
    ['PhyloPic', s.url],
  ]);
  assert.equal(asText(creditParts(s, 'sv', words.sv)), 'Siluett: Andy Wilson, CC0, via PhyloPic');
});

test('creditParts: en CC BY-silhuett är bearbetad, "adapted" och "bearbetad"', () => {
  const s = { author: 'Maxime Dahirel', licence: 'CC BY 3.0', url: 'https://www.phylopic.org/images/0b72dd38-2dc1-4675-990e-a46259855ce1', adapted: true };
  assert.equal(asText(creditParts(s, 'en', words.en)), 'Silhouette: Maxime Dahirel, CC BY 3.0, via PhyloPic, adapted');
  assert.equal(asText(creditParts(s, 'sv', words.sv)), 'Siluett: Maxime Dahirel, CC BY 3.0, via PhyloPic, bearbetad');
  assert.equal(creditParts(s, 'sv', words.sv)[1].href, 'https://creativecommons.org/licenses/by/3.0/deed.sv');
});

test('creditParts: en okänd licens står kvar som text utan länk', () => {
  const parts = creditParts({ author: 'B', licence: 'Egen licens', url: 'https://www.phylopic.org/images/x', adapted: false }, 'en', words.en);
  assert.deepEqual(parts[1], { text: 'Egen licens' });
});
