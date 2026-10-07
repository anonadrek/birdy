import { test } from 'node:test';
import assert from 'node:assert/strict';
import { matchesSearch, normalizeSearch } from '../../src/lib/species-search.mjs';

test('normalizeSearch strips diacritics ("blames" and "Blåmes" normalise the same)', () => {
  assert.equal(normalizeSearch('Blåmes'), normalizeSearch('blames'));
});

test('normalizeSearch turns a hyphen into a space ("Black-headed Gull")', () => {
  assert.equal(normalizeSearch('Black-headed Gull'), 'black headed gull');
});

test('normalizeSearch turns a hyphen into a space ("Long-tailed Tit")', () => {
  assert.equal(normalizeSearch('Long-tailed Tit'), 'long tailed tit');
});

test('normalizeSearch collapses punctuation and extra whitespace to single spaces', () => {
  assert.equal(normalizeSearch("  D'Arnaud's   Barbet "), 'd arnaud s barbet');
});

test('matchesSearch: hyphenated query word-for-word ("black headed gull")', () => {
  const key = normalizeSearch('Black-headed Gull Larus ridibundus');
  assert.equal(matchesSearch(key, normalizeSearch('black headed gull')), true);
});

test('matchesSearch: hyphenated query word-for-word ("long tailed tit")', () => {
  const key = normalizeSearch('Blåstjärtmes Long-tailed Tit Aegithalos caudatus');
  assert.equal(matchesSearch(key, normalizeSearch('long tailed tit')), true);
});

test('matchesSearch: word order does not matter ("tit great" matches "Great Tit")', () => {
  const key = normalizeSearch('Talgoxe Great Tit Parus major');
  assert.equal(matchesSearch(key, normalizeSearch('tit great')), true);
});

test('matchesSearch: every word must be present, not just one of them', () => {
  const key = normalizeSearch('Talgoxe Great Tit Parus major');
  assert.equal(matchesSearch(key, normalizeSearch('tit magpie')), false);
});

test('matchesSearch: a single word still matches as a substring ("talg")', () => {
  const key = normalizeSearch('Talgoxe Great Tit Parus major');
  assert.equal(matchesSearch(key, normalizeSearch('talg')), true);
});

test('matchesSearch: an empty query matches everything', () => {
  assert.equal(matchesSearch(normalizeSearch('Talgoxe Great Tit Parus major'), ''), true);
});

test('matchesSearch: no match when a word is missing entirely', () => {
  const key = normalizeSearch('Talgoxe Great Tit Parus major');
  assert.equal(matchesSearch(key, normalizeSearch('zzzz')), false);
});
