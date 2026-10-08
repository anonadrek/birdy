#!/usr/bin/env node
// IndexNow: tells the search engines that share it (Bing, Yandex, Seznam, Naver and others) which addresses on
// birdy.community are new or changed, so they don't wait for their next crawl. Bing's index also feeds Copilot,
// DuckDuckGo and part of ChatGPT search. Google doesn't take part; it reads the sitemap (Search Console).
// The key is public by design: the engines fetch https://birdy.community/<key>.txt (public/<key>.txt) and
// compare it with the key in the request, so it proves the site, it is not a secret.
//   node scripts/indexnow.mjs --all               every address in the live sitemap
//   node scripts/indexnow.mjs URL [URL ...]       only these addresses (they must be on birdy.community)
//   node scripts/indexnow.mjs --all --dry-run     list what would be sent, send nothing
// Runs every Monday from .github/workflows/weekly-indexnow.yml (runbook 2026-10-08-sociala-schemalaggning.md,
// Sökindex), and by hand after a publishing round.
import { readdirSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const HOST = 'birdy.community';
export const SITE = `https://${HOST}`;
export const SITEMAP = `${SITE}/sitemap-index.xml`;
export const ENDPOINT = 'https://api.indexnow.org/indexnow';
// IndexNow takes at most 10,000 addresses per request.
export const MAX_URLS = 10_000;
const KEY_FILE = /^([0-9a-f]{32})\.txt$/;
const TIMEOUT_MS = 30_000;

/** The one key in `publicDir`: a file `<key>.txt` whose content is exactly the key. */
export function findKey(publicDir) {
  const keys = readdirSync(publicDir)
    .map((name) => name.match(KEY_FILE))
    .filter((m) => m && readFileSync(join(publicDir, m[0]), 'utf8').trim() === m[1])
    .map((m) => m[1]);
  if (keys.length !== 1) {
    throw new Error(`indexnow: väntade exakt en nyckelfil <32 hex>.txt med nyckeln som innehåll i ${publicDir}, hittade ${keys.length}`);
  }
  return keys[0];
}

/** Every <loc> in a sitemap or sitemap index, in order. */
export function sitemapLocs(xml) {
  return [...xml.matchAll(/<loc>\s*([^<\s]+)\s*<\/loc>/g)].map((m) => m[1].replace(/&amp;/g, '&'));
}

/** Only https addresses on birdy.community, each once, in the order given. Anything else is listed in `skipped`. */
export function ownUrls(urls) {
  const kept = [];
  const skipped = [];
  const seen = new Set();
  for (const raw of urls) {
    let url;
    try {
      url = new URL(raw);
    } catch {
      skipped.push(raw);
      continue;
    }
    if (url.protocol !== 'https:' || url.host !== HOST) {
      skipped.push(raw);
      continue;
    }
    if (!seen.has(url.href)) {
      seen.add(url.href);
      kept.push(url.href);
    }
  }
  return { kept, skipped };
}

/** `urls` in chunks of at most `size`. */
export function batches(urls, size = MAX_URLS) {
  const out = [];
  for (let i = 0; i < urls.length; i += size) out.push(urls.slice(i, i + size));
  return out;
}

/** The JSON body IndexNow expects. */
export function payload(key, urlList) {
  return { host: HOST, key, keyLocation: `${SITE}/${key}.txt`, urlList };
}

/** What IndexNow's status codes mean (indexnow.org/documentation); 200 and 202 are both a success. */
export function describeStatus(status) {
  const known = {
    200: 'OK, adresserna är mottagna',
    202: 'Accepted, mottagna; nyckeln kontrolleras fortfarande',
    400: 'Bad request, fel format',
    403: 'Forbidden, nyckeln godtogs inte (nyckelfilen saknas eller har fel innehåll)',
    422: 'Unprocessable, adresserna hör inte till värden eller nyckeln matchar inte',
    429: 'Too many requests, vänta och försök igen',
  };
  return known[status] ?? 'okänt svar';
}

export function parseArgs(argv) {
  const out = { all: false, dryRun: false, urls: [] };
  for (const a of argv) {
    if (a === '--all') out.all = true;
    else if (a === '--dry-run') out.dryRun = true;
    else if (a.startsWith('--')) throw new Error(`indexnow: okänd flagga ${a}`);
    else out.urls.push(a);
  }
  if (out.all === (out.urls.length > 0)) throw new Error('indexnow: ange antingen --all eller en eller flera adresser');
  return out;
}

async function getText(url) {
  const res = await fetch(url, { signal: AbortSignal.timeout(TIMEOUT_MS), headers: { 'cache-control': 'no-cache' } });
  if (!res.ok) throw new Error(`indexnow: ${url} svarade ${res.status}`);
  return res.text();
}

/** Every page address in the live sitemap index (one level of nested sitemaps). */
async function liveSitemapUrls() {
  const index = sitemapLocs(await getText(SITEMAP));
  const urls = [];
  for (const sitemap of index) urls.push(...sitemapLocs(await getText(sitemap)));
  return urls;
}

async function main(argv) {
  const opts = parseArgs(argv);
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  const key = findKey(resolve(root, 'public'));
  const { kept, skipped } = ownUrls(opts.all ? await liveSitemapUrls() : opts.urls);
  for (const s of skipped) console.warn(`indexnow: hoppar över ${s} (inte https://${HOST}/)`);
  if (kept.length === 0) throw new Error('indexnow: inga adresser att skicka');
  console.log(`indexnow: ${kept.length} adresser${opts.dryRun ? ' (torrkörning, inget skickas)' : ''}`);
  if (opts.dryRun) {
    for (const u of kept) console.log(`  ${u}`);
    return;
  }
  // Without the live key file every request answers 403, so stop with a clear message instead.
  const live = (await getText(`${SITE}/${key}.txt`)).trim();
  if (live !== key) throw new Error(`indexnow: ${SITE}/${key}.txt är inte live med rätt innehåll än (deploya först)`);
  let failed = false;
  for (const urlList of batches(kept)) {
    const res = await fetch(ENDPOINT, {
      method: 'POST',
      headers: { 'content-type': 'application/json; charset=utf-8' },
      body: JSON.stringify(payload(key, urlList)),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    console.log(`indexnow: ${urlList.length} adresser, svar ${res.status} (${describeStatus(res.status)})`);
    if (res.status !== 200 && res.status !== 202) failed = true;
  }
  if (failed) process.exitCode = 1;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main(process.argv.slice(2)).catch((err) => {
    console.error(err.message);
    process.exit(1);
  });
}
