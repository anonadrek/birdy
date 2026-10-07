// node --test "tests/unit/*.unit.mjs"  (the .unit.mjs suffix keeps Playwright from picking it up)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  assetsDir, audioPublicPath, builtSpeciesMedia, comparisonsDir, isComparisonBuilt, isPreview, isSpeciesBuilt, readJsonDir, speciesDir, useEmptyData,
} from '../../src/lib/species-source.mjs';

const ok = {
  status: 'ok', publish: true,
  verification: { method: 'auto', at: '2026-11-20', model: 'claude-sonnet-5', spotChecked: false },
};

/** Runs fn with the given env vars set, restoring the previous values (or absence) afterwards. */
function withEnv(vars, fn) {
  const prev = {};
  for (const k of Object.keys(vars)) prev[k] = process.env[k];
  for (const [k, v] of Object.entries(vars)) {
    if (v === undefined) delete process.env[k];
    else process.env[k] = v;
  }
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

test('useEmptyData läser SPECIES_EMPTY från miljön', () => {
  withEnv({ SPECIES_EMPTY: '1', VERCEL_ENV: undefined }, () => {
    assert.equal(useEmptyData(), true);
  });
  withEnv({ SPECIES_EMPTY: undefined, VERCEL_ENV: undefined }, () => {
    assert.equal(useEmptyData(), false);
  });
  withEnv({ SPECIES_EMPTY: '0', VERCEL_ENV: undefined }, () => {
    assert.equal(useEmptyData(), false);
  });
});

test('VERCEL_ENV=production spärrar SPECIES_EMPTY=1 (det tomma testläget får aldrig byggas i Production)', () => {
  withEnv({ SPECIES_EMPTY: '1', VERCEL_ENV: 'production' }, () => {
    assert.throws(() => useEmptyData(), /VERCEL_ENV=production/);
  });
});

test('VERCEL_ENV=preview (eller inget VERCEL_ENV) tillåter SPECIES_EMPTY=1', () => {
  withEnv({ SPECIES_EMPTY: '1', VERCEL_ENV: 'preview' }, () => {
    assert.equal(useEmptyData(), true);
  });
  withEnv({ SPECIES_EMPTY: '1', VERCEL_ENV: undefined }, () => {
    assert.equal(useEmptyData(), true);
  });
});

test('SPECIES_EMPTY pekar katalogerna på tests/fixtures/empty/, oavsett SPECIES_FIXTURES', () => {
  withEnv({ SPECIES_EMPTY: '1', SPECIES_FIXTURES: undefined, VERCEL_ENV: undefined }, () => {
    assert.equal(speciesDir(), 'tests/fixtures/empty/species');
    assert.equal(comparisonsDir(), 'tests/fixtures/empty/comparisons');
    assert.equal(assetsDir(), 'tests/fixtures/empty/species-assets');
  });
  // SPECIES_EMPTY wins even when SPECIES_FIXTURES is also set (defensive precedence, not a real combination).
  withEnv({ SPECIES_EMPTY: '1', SPECIES_FIXTURES: '1', VERCEL_ENV: undefined }, () => {
    assert.equal(speciesDir(), 'tests/fixtures/empty/species');
    assert.equal(comparisonsDir(), 'tests/fixtures/empty/comparisons');
    assert.equal(assetsDir(), 'tests/fixtures/empty/species-assets');
  });
});

test('utan SPECIES_EMPTY pekar katalogerna som förut: fixtures eller src/data/', () => {
  withEnv({ SPECIES_EMPTY: undefined, SPECIES_FIXTURES: '1', VERCEL_ENV: undefined }, () => {
    assert.equal(speciesDir(), 'tests/fixtures/species');
    assert.equal(comparisonsDir(), 'tests/fixtures/comparisons');
    assert.equal(assetsDir(), 'tests/fixtures/species-assets');
  });
  withEnv({ SPECIES_EMPTY: undefined, SPECIES_FIXTURES: undefined, VERCEL_ENV: undefined }, () => {
    assert.equal(speciesDir(), 'src/data/species');
    assert.equal(comparisonsDir(), 'src/data/comparisons');
    assert.equal(assetsDir(), 'src/assets/species');
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

test('builtSpeciesMedia: foton och inspelningar bara för arter som får en sida, med samma adress som audioPublicPath', () => {
  const website = join(dirname(fileURLToPath(import.meta.url)), '../..');
  withEnv({ SPECIES_FIXTURES: '1', VERCEL_ENV: undefined }, () => {
    const normal = builtSpeciesMedia(website, false);
    const preview = builtSpeciesMedia(website, true);
    // 16 published species with a hero, two of them with an extra photo; the preview adds two woodpeckers
    // and the unpublished absent species (controller review, Task 9), three heroes, no extras.
    assert.equal(normal.images.length, 18);
    assert.equal(preview.images.length, 21);
    for (const qid of ['Q26209', 'Q210418', 'Q143284', 'Q166171']) {
      assert.equal(normal.images.some((f) => f.startsWith(`${qid}/`)), false, qid);
    }
    assert.ok(preview.images.includes('Q26209/hero.webp') && preview.images.includes('Q210418/hero.webp'));
    assert.equal(preview.images.some((f) => /^Q(143284|166171)\//.test(f)), false);
    assert.deepEqual(normal.audio.map((a) => a.qid), ['Q25234', 'Q25404', 'Q25485', 'Q25756']);
    for (const a of normal.audio) {
      assert.equal(a.file, `${a.qid}/voice.mp3`);
      assert.equal(a.href, audioPublicPath(website, { qid: a.qid, audio: { file: a.file } }));
    }
  });
});
