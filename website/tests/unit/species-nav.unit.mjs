import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { footerSpecies } from '../../src/lib/species-nav.mjs';

const { common } = JSON.parse(readFileSync(new URL('../../src/data/species-groups.json', import.meta.url), 'utf8'));
const sp = (qid) => ({ qid });

test('footerSpecies: noll byggda arter ger varken kolumn eller rad', () => {
  assert.deepEqual(footerSpecies([], common), { column: false, common: [] });
});

test('footerSpecies: arter men ingen av de tolv vanliga ger kolumnen men inte raden', () => {
  const rare = sp('Q25411');
  assert.ok(!common.includes(rare.qid));
  assert.deepEqual(footerSpecies([rare], common), { column: true, common: [] });
});

test('footerSpecies: vanliga arter i listans ordning, bara de som är byggda', () => {
  assert.equal(common.length, 12);
  const built = [sp('Q25411'), sp(common[5]), sp(common[1])];
  const out = footerSpecies(built, common);
  assert.equal(out.column, true);
  assert.deepEqual(out.common.map((s) => s.qid), [common[1], common[5]]);
});
