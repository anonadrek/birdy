// node --test "tests/unit/*.unit.mjs": the pure parts of scripts/indexnow.mjs (runbook 2026-10-08, Sökindex).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { HOST, MAX_URLS, batches, describeStatus, findKey, ownUrls, parseArgs, payload, sitemapLocs } from '../../scripts/indexnow.mjs';

const KEY = '0123456789abcdef0123456789abcdef';

test('sitemapLocs läser varje <loc> i en sitemap och i ett sitemapindex', () => {
  const index = '<sitemapindex><sitemap><loc>https://birdy.community/sitemap-0.xml</loc><lastmod>2026-10-08</lastmod></sitemap></sitemapindex>';
  assert.deepEqual(sitemapLocs(index), ['https://birdy.community/sitemap-0.xml']);
  const urlset = '<urlset><url><loc>https://birdy.community/</loc></url><url><loc> https://birdy.community/sv/arter/blames/ </loc><lastmod>x</lastmod></url><url><loc>https://birdy.community/?a=1&amp;b=2</loc></url></urlset>';
  assert.deepEqual(sitemapLocs(urlset), ['https://birdy.community/', 'https://birdy.community/sv/arter/blames/', 'https://birdy.community/?a=1&b=2']);
});

test('ownUrls behåller bara https på birdy.community, en gång var, i ordning', () => {
  const { kept, skipped } = ownUrls([
    'https://birdy.community/species/great-tit/',
    'http://birdy.community/sv/',
    'https://www.birdy.community/sv/',
    'https://example.com/',
    'inte en adress',
    'https://birdy.community/species/great-tit/',
    'https://birdy.community/sv/arter/',
  ]);
  assert.deepEqual(kept, ['https://birdy.community/species/great-tit/', 'https://birdy.community/sv/arter/']);
  assert.deepEqual(skipped, ['http://birdy.community/sv/', 'https://www.birdy.community/sv/', 'https://example.com/', 'inte en adress']);
});

test('batches delar i högst MAX_URLS (10 000) per anrop', () => {
  assert.equal(MAX_URLS, 10_000);
  assert.deepEqual(batches(['a', 'b', 'c', 'd', 'e'], 2), [['a', 'b'], ['c', 'd'], ['e']]);
  assert.deepEqual(batches([], 2), []);
  assert.equal(batches(Array.from({ length: 10_001 }, (_, i) => String(i))).length, 2);
});

test('payload har värd, nyckel, nyckelfilens adress och adresserna', () => {
  assert.deepEqual(payload(KEY, ['https://birdy.community/']), {
    host: HOST,
    key: KEY,
    keyLocation: `https://birdy.community/${KEY}.txt`,
    urlList: ['https://birdy.community/'],
  });
});

test('findKey hittar den enda nyckelfilen vars innehåll är nyckeln', () => {
  const dir = mkdtempSync(join(tmpdir(), 'indexnow-'));
  try {
    writeFileSync(join(dir, 'robots.txt'), 'User-agent: *\n');
    assert.throws(() => findKey(dir), /hittade 0/);
    writeFileSync(join(dir, `${KEY}.txt`), `${KEY}\n`);
    assert.equal(findKey(dir), KEY);
    // A file with the right name but other content is not a key (the engines would answer 403).
    writeFileSync(join(dir, 'fedcba9876543210fedcba9876543210.txt'), KEY);
    assert.equal(findKey(dir), KEY);
    writeFileSync(join(dir, 'fedcba9876543210fedcba9876543210.txt'), 'fedcba9876543210fedcba9876543210');
    assert.throws(() => findKey(dir), /hittade 2/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('parseArgs: antingen --all eller adresser, och --dry-run', () => {
  assert.deepEqual(parseArgs(['--all']), { all: true, dryRun: false, urls: [] });
  assert.deepEqual(parseArgs(['--dry-run', 'https://birdy.community/']), { all: false, dryRun: true, urls: ['https://birdy.community/'] });
  assert.throws(() => parseArgs([]), /antingen --all/);
  assert.throws(() => parseArgs(['--all', 'https://birdy.community/']), /antingen --all/);
  assert.throws(() => parseArgs(['--allt']), /okänd flagga/);
});

test('describeStatus: 200 och 202 är lyckade svar, 403 betyder nyckeln', () => {
  assert.match(describeStatus(200), /^OK/);
  assert.match(describeStatus(202), /^Accepted/);
  assert.match(describeStatus(403), /nyckeln/);
  assert.equal(describeStatus(418), 'okänt svar');
});
