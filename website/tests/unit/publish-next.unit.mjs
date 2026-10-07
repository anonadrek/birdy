// node --test "tests/unit/*.unit.mjs": the pure parts of scripts/publish-next.mjs (plan Task 16).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  EXIT, TRAILER, commitMessage, dirtyRecordIds, nextArgs, pagePaths, parseArgs, parsePick, pickFiles, readExcluded,
} from '../../scripts/publish-next.mjs';

test('utgångskoderna: 0 publicerad, 1 fel, 3 inget att publicera', () => {
  assert.deepEqual(EXIT, { published: 0, failed: 1, none: 3 });
});

test('parseArgs: standard, --dry-run, --no-push och --port', () => {
  const before = process.env.PUBLISH_PORT;
  delete process.env.PUBLISH_PORT;
  try {
    assert.deepEqual(parseArgs([]), { dryRun: false, push: true, port: 4327 });
    assert.deepEqual(parseArgs(['--dry-run', '--port', '4799']), { dryRun: true, push: true, port: 4799 });
    assert.deepEqual(parseArgs(['--no-push']), { dryRun: false, push: false, port: 4327 });
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

test('pickFiles: artens JSON och mapp med foton och inspelning, jämförelsens JSON', () => {
  assert.deepEqual(pickFiles({ kind: 'species', id: 'Q25485' }), { json: 'src/data/species/Q25485.json', extra: ['src/assets/species/Q25485'] });
  assert.deepEqual(pickFiles({ kind: 'comparison', id: 'Q25404_Q25485' }), { json: 'src/data/comparisons/Q25404_Q25485.json', extra: [] });
});

test('pagePaths: sidans adress på båda språken, utan inledande snedstreck', () => {
  assert.deepEqual(pagePaths({ slug: { sv: 'talgoxe', en: 'great-tit' } }), ['sv/arter/talgoxe/', 'species/great-tit/']);
  assert.deepEqual(pagePaths({ slug: { sv: 'blames-eller-talgoxe', en: 'eurasian-blue-tit-vs-great-tit' } }), ['sv/arter/blames-eller-talgoxe/', 'species/eurasian-blue-tit-vs-great-tit/']);
});

test('commitMessage: planens format och Co-Authored-By sist', () => {
  assert.equal(commitMessage({ kind: 'species', id: 'Q25485' }, ['Talgoxe']), `data(artsidor): Talgoxe (Q25485)\n\n${TRAILER}\n`);
  assert.equal(commitMessage({ kind: 'comparison', id: 'Q25404_Q25485' }, ['Blåmes', 'Talgoxe']), `data(artsidor): Blåmes eller talgoxe (Q25404+Q25485)\n\n${TRAILER}\n`);
});
