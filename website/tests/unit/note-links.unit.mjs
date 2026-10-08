import assert from 'node:assert/strict';
import { test } from 'node:test';
import { unwrapUnbuiltSpeciesLinks } from '../../src/lib/note-links.mjs';

const built = new Set(['/species/', '/sv/arter/', '/species/great-tit/', '/sv/arter/talgoxe/']);

test('unwrapUnbuiltSpeciesLinks: en länk till en byggd artsida står kvar', () => {
  const html = '<li>Torsdag: <a href="/sv/arter/talgoxe/">Talgoxe</a></li>';
  assert.equal(unwrapUnbuiltSpeciesLinks(html, built), html);
});

test('unwrapUnbuiltSpeciesLinks: en länk till en artsida som inte är byggd blir sin text', () => {
  assert.equal(
    unwrapUnbuiltSpeciesLinks('<li>Fredag: <a href="/sv/arter/blames/">Blåmes</a></li>', built),
    '<li>Fredag: Blåmes</li>',
  );
  assert.equal(
    unwrapUnbuiltSpeciesLinks('<p><a class="x" href="/species/eurasian-blue-tit/" title="t"><em>Eurasian</em> Blue Tit</a>.</p>', built),
    '<p><em>Eurasian</em> Blue Tit.</p>',
  );
});

test('unwrapUnbuiltSpeciesLinks: noll byggda arter tar bort varje artlänk men inget annat', () => {
  const html = '<p><a href="/species/great-tit/">Great Tit</a>, <a href="/blog/">blog</a>, <a href="https://www.youtube.com/@birdy.community">YouTube</a>, <a href="/video/see-the-song/eurasian-blue-tit.mp4">MP4</a></p>';
  assert.equal(
    unwrapUnbuiltSpeciesLinks(html, new Set()),
    '<p>Great Tit, <a href="/blog/">blog</a>, <a href="https://www.youtube.com/@birdy.community">YouTube</a>, <a href="/video/see-the-song/eurasian-blue-tit.mp4">MP4</a></p>',
  );
});

test('unwrapUnbuiltSpeciesLinks: ankare, frågetecken och adress utan snedstreck på slutet', () => {
  assert.equal(unwrapUnbuiltSpeciesLinks('<a href="/species/great-tit/#sound">x</a>', built), '<a href="/species/great-tit/#sound">x</a>');
  assert.equal(unwrapUnbuiltSpeciesLinks('<a href="/sv/arter/talgoxe">x</a>', built), '<a href="/sv/arter/talgoxe">x</a>');
  assert.equal(unwrapUnbuiltSpeciesLinks('<a href="/sv/arter/blames/?a=1#b">x</a>', built), 'x');
});

test('unwrapUnbuiltSpeciesLinks: länkar som bara liknar artsidor och länkar utan href rörs inte', () => {
  for (const html of ['<a href="/speciesx/a/">x</a>', '<a href="/sv/arterna/a/">x</a>', '<a href="https://birdy.community/species/x/">x</a>', '<a name="top">x</a>', '<abbr title="t">x</abbr>']) {
    assert.equal(unwrapUnbuiltSpeciesLinks(html, new Set()), html);
  }
});
