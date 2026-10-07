// node --test "tests/unit/*.unit.mjs"  (the .unit.mjs suffix keeps Playwright from picking it up)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { audioPublicPath, isComparisonBuilt, isPreview, isSpeciesBuilt, readJsonDir } from '../../src/lib/species-source.mjs';

const ok = {
  status: 'ok', publish: true,
  verification: { method: 'auto', at: '2026-11-20', model: 'claude-sonnet-5', spotChecked: false },
};

/** Runs fn with the given env vars set, restoring the previous values (or absence) afterwards. */
function withEnv(vars, fn) {
  const prev = {};
  for (const k of Object.keys(vars)) prev[k] = process.env[k];
  Object.assign(process.env, vars);
  try {
    return fn();
  } finally {
    for (const k of Object.keys(vars)) {
      if (prev[k] === undefined) delete process.env[k];
      else process.env[k] = prev[k];
    }
  }
}

/** A fresh temp directory, cleaned up after fn runs (sync or async). */
async function withTempDir(fn) {
  const root = mkdtempSync(join(tmpdir(), 'species-source-'));
  try {
    return await fn(root);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
}

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

test('publish som sträng ("true") räknas inte som publicerad', () => {
  const stringPublish = { ...ok, publish: 'true' };
  assert.equal(isSpeciesBuilt(stringPublish, false), false);
  const cmp = { status: 'ok', publish: 'true', a: 'Q1', b: 'Q2' };
  assert.equal(isComparisonBuilt(cmp, new Set(['Q1', 'Q2']), false), false);
});

test('standardparametern läser SPECIES_PREVIEW från miljön', () => {
  const draft = { ...ok, publish: false };
  withEnv({ SPECIES_PREVIEW: '1', VERCEL_ENV: undefined }, () => {
    assert.equal(isSpeciesBuilt(draft), true);
  });
  withEnv({ SPECIES_PREVIEW: undefined, VERCEL_ENV: undefined }, () => {
    assert.equal(isSpeciesBuilt(draft), false);
  });
});

test('VERCEL_ENV=production spärrar SPECIES_PREVIEW=1 (förhandsgranskning får aldrig byggas i Production)', () => {
  withEnv({ SPECIES_PREVIEW: '1', VERCEL_ENV: 'production' }, () => {
    assert.throws(() => isPreview(), /VERCEL_ENV=production/);
  });
});

test('VERCEL_ENV=preview (eller inget VERCEL_ENV) tillåter SPECIES_PREVIEW=1', () => {
  withEnv({ SPECIES_PREVIEW: '1', VERCEL_ENV: 'preview' }, () => {
    assert.equal(isPreview(), true);
  });
  withEnv({ SPECIES_PREVIEW: '1', VERCEL_ENV: undefined }, () => {
    assert.equal(isPreview(), true);
  });
});

test('readJsonDir: saknad mapp ger tom lista', async () => {
  await withTempDir((root) => {
    assert.deepEqual(readJsonDir(root, 'does-not-exist'), []);
  });
});

test('readJsonDir: sorterar efter filnamn, bara .json, hoppar över andra filer', async () => {
  await withTempDir((root) => {
    const dir = join(root, 'sub');
    mkdirSync(dir);
    writeFileSync(join(dir, 'b.json'), JSON.stringify({ n: 'b' }));
    writeFileSync(join(dir, 'a.json'), JSON.stringify({ n: 'a' }));
    writeFileSync(join(dir, 'readme.txt'), 'inte json, ska hoppas över');
    assert.deepEqual(readJsonDir(root, 'sub'), [{ n: 'a' }, { n: 'b' }]);
  });
});

test('readJsonDir: trasig fil namnger filen i felet, en UTF-8 BOM upptäcks explicit', async () => {
  await withTempDir((root) => {
    const dir = join(root, 'sub');
    mkdirSync(dir);
    const bom = String.fromCharCode(0xfeff);
    writeFileSync(join(dir, 'broken.json'), `${bom}{bad json`);
    assert.throws(() => readJsonDir(root, 'sub'), (err) => {
      assert.match(err.message, /sub[\\/]broken\.json/);
      assert.match(err.message, /UTF-8 BOM/);
      assert.ok(err.cause instanceof Error);
      return true;
    });
  });
});

test('readJsonDir: trasig fil UTAN BOM namnger filen men inte BOM-fallet', async () => {
  await withTempDir((root) => {
    const dir = join(root, 'sub');
    mkdirSync(dir);
    writeFileSync(join(dir, 'broken.json'), '{bad json');
    assert.throws(() => readJsonDir(root, 'sub'), (err) => {
      assert.match(err.message, /sub[\\/]broken\.json/);
      assert.doesNotMatch(err.message, /UTF-8 BOM/);
      return true;
    });
  });
});

test('audioPublicPath: formatet är /audio/species/<QID>.<10 hex>.mp3, och hashen ändras med innehållet', async () => {
  await withTempDir((root) => {
    withEnv({ SPECIES_FIXTURES: '1' }, () => {
      const dir = join(root, 'tests/fixtures/species-assets', 'Q99999');
      mkdirSync(dir, { recursive: true });
      const file = join(dir, 'voice.mp3');
      const record = { qid: 'Q99999', audio: { file: 'Q99999/voice.mp3' } };

      writeFileSync(file, Buffer.from([1, 2, 3]));
      const path1 = audioPublicPath(root, record);
      assert.match(path1, /^\/audio\/species\/Q99999\.[0-9a-f]{10}\.mp3$/);

      writeFileSync(file, Buffer.from([4, 5, 6]));
      const path2 = audioPublicPath(root, record);
      assert.match(path2, /^\/audio\/species\/Q99999\.[0-9a-f]{10}\.mp3$/);
      assert.notEqual(path1, path2);
    });
  });
});
