// node --test "tests/unit/*.unit.mjs"  (the .unit.mjs suffix keeps Playwright from picking it up)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { isComparisonBuilt, isSpeciesBuilt } from '../../src/lib/species-source.mjs';

const ok = {
  status: 'ok', publish: true,
  verification: { method: 'auto', at: '2026-11-20', model: 'claude-sonnet-5', spotChecked: false },
};

test('publicerad och kontrollerad art får sida', () => {
  assert.equal(isSpeciesBuilt(ok, false), true);
});

test('opublicerad art får sida bara i förhandsbygget', () => {
  const draft = { ...ok, publish: false };
  assert.equal(isSpeciesBuilt(draft, false), false);
  assert.equal(isSpeciesBuilt(draft, true), true);
});

test('pending och failed får aldrig sida', () => {
  for (const status of ['pending', 'failed']) assert.equal(isSpeciesBuilt({ ...ok, status }, true), false);
});

test('okontrollerat faktablad ger ingen sida, inte ens i förhandsbygget', () => {
  const { verification, ...unverified } = ok;
  assert.equal(isSpeciesBuilt(unverified, true), false);
});

test('jämförelse kräver båda arternas sidor', () => {
  const cmp = { status: 'ok', publish: true, a: 'Q1', b: 'Q2' };
  const both = new Set(['Q1', 'Q2']);
  assert.equal(isComparisonBuilt(cmp, both, false), true);
  assert.equal(isComparisonBuilt(cmp, new Set(['Q1']), false), false);
  assert.equal(isComparisonBuilt({ ...cmp, publish: false }, both, false), false);
  assert.equal(isComparisonBuilt({ ...cmp, publish: false }, both, true), true);
  assert.equal(isComparisonBuilt({ ...cmp, status: 'pending' }, both, true), false);
});
