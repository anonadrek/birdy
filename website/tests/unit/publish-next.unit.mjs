// node --test "tests/unit/*.unit.mjs": the pure parts of scripts/publish-next.mjs (plan Task 16), and its
// restore and clean-tree checks against a throwaway git repository.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import {
  DEFAULT_PORT, EXIT, GIT_ENV, TRAILER, assetFiles, axePaths, commitMessage, dirtyRecordIds, liveUrls, nextArgs, pagePaths, parseArgs,
  parsePick, readExcluded, recordFile, restoreFiles, treeIsClean,
} from '../../scripts/publish-next.mjs';

const robin = {
  slug: { sv: 'rodhake', en: 'european-robin' },
  group: 'songbirds',
  images: [{ role: 'hero', file: 'Q25334/hero.webp' }, { role: 'extra', file: 'Q25334/extra.webp' }],
  audio: { file: 'Q25334/voice.mp3' },
};
const groups = [{ key: 'owls', slug: { sv: 'ugglor', en: 'owls' } }, { key: 'songbirds', slug: { sv: 'tattingar', en: 'songbirds' } }];

test('utgångskoderna: 0 publicerad, 1 posten föll, 3 inget att publicera, 4 stoppa loopen', () => {
  assert.deepEqual(EXIT, { published: 0, failed: 1, none: 3, stop: 4 });
});

test('git frågar aldrig efter lösenord (en fråga som ingen svarar på skulle hänga loopen)', () => {
  assert.deepEqual(GIT_ENV, { GIT_TERMINAL_PROMPT: '0', GCM_INTERACTIVE: 'never' });
});

test('parseArgs: standard, --dry-run, --no-push och --port', () => {
  const before = process.env.PUBLISH_PORT;
  delete process.env.PUBLISH_PORT;
  try {
    assert.equal(DEFAULT_PORT, 46327);
    assert.deepEqual(parseArgs([]), { dryRun: false, push: true, port: 46327 });
    assert.deepEqual(parseArgs(['--dry-run', '--port', '4799']), { dryRun: true, push: true, port: 4799 });
    assert.deepEqual(parseArgs(['--no-push']), { dryRun: false, push: false, port: 46327 });
    assert.throws(() => parseArgs(['--publish-all']), /okänd flagga/);
    assert.throws(() => parseArgs(['--port', 'x']), /ogiltig port/);
  } finally {
    if (before !== undefined) process.env.PUBLISH_PORT = before;
  }
});

test('readExcluded: en post per rad, tomma rader, kommentarer och dubbletter bort, CRLF går bra', () => {
  assert.deepEqual(readExcluded(''), []);
  assert.deepEqual(readExcluded('Q25485\r\n\n# kommentar\nQ25404_Q25485\nQ25485\n  Q1  \n'), ['Q25485', 'Q25404_Q25485', 'Q1']);
});

test('dirtyRecordIds: bara artdatans och jämförelsernas JSON i git status --porcelain', () => {
  const porcelain = [
    ' M src/data/species/Q25485.json',
    '?? src/data/comparisons/Q25404_Q25485.json',
    ' M src/data/species/LICENSE.md',
    ' M src/assets/species/Q25485/voice.mp3',
    'M  website/src/data/species/Q4764.json',
  ].join('\n');
  assert.deepEqual(dirtyRecordIds(porcelain), ['Q25485', 'Q25404_Q25485', 'Q4764']);
  assert.deepEqual(dirtyRecordIds(''), []);
});

test('nextArgs: uteslutningarna som --exclude, alla jämförelser uteslutna så länge de är avstängda', () => {
  const base = ['run', 'birdy-fetcher', 'web', 'publish', '--next'];
  assert.deepEqual(nextArgs([], ['Q1_Q2'], true), base);
  assert.deepEqual(nextArgs(['Q25485'], ['Q1_Q2'], true), [...base, '--exclude', 'Q25485']);
  assert.deepEqual(nextArgs(['Q25485', 'Q1_Q2'], ['Q1_Q2', 'Q3_Q4'], false), [...base, '--exclude', 'Q25485', '--exclude', 'Q1_Q2', '--exclude', 'Q3_Q4']);
});

test('parsePick: art, jämförelse och none; allt annat är ett fel', () => {
  assert.deepEqual(parsePick('species Q25485\n'), { kind: 'species', id: 'Q25485' });
  assert.deepEqual(parsePick('\r\ncomparison Q25404_Q25485\r\n'), { kind: 'comparison', id: 'Q25404_Q25485' });
  assert.equal(parsePick('none\n'), null);
  assert.throws(() => parsePick(''), /0 rader/);
  assert.throws(() => parsePick('species Q1\nspecies Q2\n'), /2 rader/);
  assert.throws(() => parsePick('species talgoxe\n'), /oväntad rad/);
  assert.throws(() => parsePick('Publicerad: Q25485\n'), /oväntad rad/);
});

test('recordFile och assetFiles: postens JSON, och bara de foton och den inspelning som posten pekar på', () => {
  assert.equal(recordFile({ kind: 'species', id: 'Q25334' }), 'src/data/species/Q25334.json');
  assert.equal(recordFile({ kind: 'comparison', id: 'Q25404_Q25485' }), 'src/data/comparisons/Q25404_Q25485.json');
  assert.deepEqual(assetFiles({ kind: 'species', id: 'Q25334' }, robin), [
    'src/assets/species/Q25334/hero.webp', 'src/assets/species/Q25334/extra.webp', 'src/assets/species/Q25334/voice.mp3',
  ]);
  assert.deepEqual(assetFiles({ kind: 'species', id: 'Q1' }, { images: [{ file: 'Q1/hero.webp' }] }), ['src/assets/species/Q1/hero.webp']);
  assert.deepEqual(assetFiles({ kind: 'comparison', id: 'Q1_Q2' }, robin), []);
});

test('pagePaths och axePaths: sidan på båda språken, axe även på ingångssidan och artens grupp', () => {
  assert.deepEqual(pagePaths(robin), ['sv/arter/rodhake/', 'species/european-robin/']);
  assert.deepEqual(axePaths({ kind: 'species', id: 'Q25334' }, robin, groups), [
    'sv/arter/rodhake/', 'species/european-robin/', 'sv/arter/', 'species/', 'sv/arter/tattingar/', 'species/songbirds/',
  ]);
  const pair = { slug: { sv: 'blames-eller-talgoxe', en: 'eurasian-blue-tit-vs-great-tit' } };
  assert.deepEqual(axePaths({ kind: 'comparison', id: 'Q25404_Q25485' }, pair, groups), [
    'sv/arter/blames-eller-talgoxe/', 'species/eurasian-blue-tit-vs-great-tit/', 'sv/arter/', 'species/',
  ]);
});

test('liveUrls: båda adresserna med commitens hash som cachebrytare', () => {
  assert.deepEqual(liveUrls('https://birdy.community', ['sv/arter/rodhake/', 'species/european-robin/'], 'abc1234'), [
    'https://birdy.community/sv/arter/rodhake/?live=abc1234', 'https://birdy.community/species/european-robin/?live=abc1234',
  ]);
});

test('commitMessage: planens format och Co-Authored-By sist', () => {
  assert.equal(commitMessage({ kind: 'species', id: 'Q25485' }, ['Talgoxe']), `data(artsidor): Talgoxe (Q25485)\n\n${TRAILER}\n`);
  assert.equal(commitMessage({ kind: 'comparison', id: 'Q25404_Q25485' }, ['Blåmes', 'Talgoxe']), `data(artsidor): Blåmes eller talgoxe (Q25404+Q25485)\n\n${TRAILER}\n`);
});

/** A throwaway repository with website/src/data/species/Q1.json committed; fn gets its website folder. */
function withRepo(fn) {
  const dir = mkdtempSync(join(tmpdir(), 'publish-next-'));
  const g = (...args) => {
    const r = spawnSync('git', args, { cwd: dir, encoding: 'utf8' });
    assert.equal(r.status, 0, r.stderr);
  };
  try {
    g('init', '-q');
    g('config', 'user.email', 'test@example.invalid');
    g('config', 'user.name', 'Test');
    g('config', 'core.autocrlf', 'false');
    const site = join(dir, 'website');
    mkdirSync(join(site, 'src/data/species'), { recursive: true });
    writeFileSync(join(site, 'src/data/species/Q1.json'), '{"publish": false}\n');
    g('add', '-A');
    g('commit', '-q', '-m', 'start');
    fn({ dir, site, g });
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

test('restoreFiles: återställer från HEAD även efter git add (index och arbetskatalog), och kontrollerar resultatet', () => {
  withRepo(({ site, g }) => {
    const file = join(site, 'src/data/species/Q1.json');
    writeFileSync(file, '{"publish": true}\n');
    g('-C', site, 'add', '--', 'src/data/species/Q1.json');
    const result = restoreFiles(site, ['src/data/species/Q1.json']);
    assert.equal(result.ok, true, result.detail);
    assert.equal(readFileSync(file, 'utf8'), '{"publish": false}\n');
    const status = spawnSync('git', ['status', '--porcelain'], { cwd: site, encoding: 'utf8' });
    assert.equal(status.stdout, '');
    assert.deepEqual(restoreFiles(site, []), { ok: true, detail: '' });
  });
});

test('restoreFiles: en fil som inte går att återställa ger ok: false (loopen stoppar då)', () => {
  withRepo(({ site }) => {
    writeFileSync(join(site, 'src/data/species/Q2.json'), '{}\n');
    const result = restoreFiles(site, ['src/data/species/Q2.json']);
    assert.equal(result.ok, false);
    assert.match(result.detail, /Q2\.json/);
  });
});

test('treeIsClean: ändrade, köade och ospårade filer räknas, loopens egna reports/ gör det inte', () => {
  withRepo(({ dir, site, g }) => {
    assert.equal(treeIsClean(dir).clean, true);
    mkdirSync(join(site, 'reports'), { recursive: true });
    writeFileSync(join(site, 'reports/publish-loop-2026-10-08.md'), 'rapport\n');
    assert.equal(treeIsClean(dir).clean, true);
    writeFileSync(join(site, 'src/data/species/Q3.json'), '{}\n');
    assert.equal(treeIsClean(dir).clean, false);
    rmSync(join(site, 'src/data/species/Q3.json'));
    writeFileSync(join(site, 'src/data/species/Q1.json'), '{"publish": true}\n');
    g('add', '-A', '--', 'website/src/data');
    const dirty = treeIsClean(dir);
    assert.equal(dirty.clean, false);
    assert.match(dirty.detail, /Q1\.json/);
  });
});
