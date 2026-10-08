// Checks cover/covers.json: every clue is short, ends with a full stop, has no en/em dash and
// does not give the species away by name or group word; every silhouette licence is one PhyloPic
// actually offers (CC0, public domain, or CC BY — never NC, SA or ND); every QID has its SVG.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile, access } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const coverDir = join(here, '..', 'cover');
const repo = join(here, '..', '..', '..');

const covers = JSON.parse(await readFile(join(coverDir, 'covers.json'), 'utf8'));

async function speciesNames(qid) {
  const text = await readFile(join(repo, 'website', 'src', 'data', 'species', `${qid}.json`), 'utf8');
  const { names } = JSON.parse(text);
  return names;
}

async function exists(p) {
  try {
    await access(p);
    return true;
  } catch {
    return false;
  }
}

// The species' own group, and every neighbouring group word a clue could give the game away
// with, fixed and checked against every clue regardless of which species it belongs to.
const GROUP_WORDS = [
  'swan', 'goose', 'duck', 'gull', 'crow', 'raven', 'magpie', 'heron', 'woodpecker', 'finch',
  'sparrow', 'tit', 'owl', 'wren', 'waxwing', 'nuthatch', 'bullfinch', 'goldfinch', 'greenfinch',
  'brambling', 'fieldfare', 'yellowhammer', 'goldeneye',
];

function silhouetteLicenceAllowed(licence) {
  if (typeof licence !== 'string' || !licence.trim()) return false;
  if (/-\s*(sa|nc|nd)\b/i.test(licence)) return false; // never ShareAlike, NonCommercial or NoDerivatives
  return /^cc0\b/i.test(licence) || /^public domain/i.test(licence) || /^cc by\b/i.test(licence);
}

test('covers.json has at least the nine already-posted species plus the upcoming sets', () => {
  assert.ok(Object.keys(covers).length >= 29, `expected at least 29 entries, got ${Object.keys(covers).length}`);
});

for (const [qid, entry] of Object.entries(covers)) {
  test(`${qid}: clue text follows the house rules`, async () => {
    const { clue } = entry;
    assert.equal(typeof clue, 'string', 'clue must be a string');
    assert.ok(clue.length <= 45, `clue is ${clue.length} chars (max 45): "${clue}"`);
    assert.match(clue, /\.$/, `clue must end with a full stop: "${clue}"`);
    assert.doesNotMatch(clue, /[–—]/, `clue must not contain an en or em dash: "${clue}"`);

    const names = await speciesNames(qid);
    const lower = clue.toLowerCase();
    assert.ok(!lower.includes(names.en.toLowerCase()), `clue contains the English name "${names.en}": "${clue}"`);
    assert.ok(!lower.includes(names.sv.toLowerCase()), `clue contains the Swedish name "${names.sv}": "${clue}"`);
    for (const word of GROUP_WORDS) {
      assert.ok(!lower.includes(word), `clue contains the group word "${word}": "${clue}"`);
    }
  });

  test(`${qid}: silhouette licence is one PhyloPic actually offers`, () => {
    const { silhouette } = entry;
    assert.ok(silhouette && typeof silhouette === 'object', 'silhouette must be an object');
    assert.ok(silhouetteLicenceAllowed(silhouette.licence), `licence "${silhouette.licence}" is not CC0, public domain or CC BY (never NC, SA or ND)`);
    assert.ok(silhouette.author && typeof silhouette.author === 'string', 'silhouette needs an author name');
    assert.match(silhouette.url || '', /^https:\/\/www\.phylopic\.org\/images\//, `silhouette url should be a PhyloPic image page: "${silhouette.url}"`);
    assert.ok(['own species', 'genus', 'family'].includes(silhouette.note), `silhouette note must be "own species", "genus" or "family", got "${silhouette.note}"`);
  });

  test(`${qid}: has its silhouette SVG on disk`, async () => {
    assert.ok(await exists(join(coverDir, 'sil', `${qid}.svg`)), `missing cover/sil/${qid}.svg`);
  });
}
