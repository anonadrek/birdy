#!/usr/bin/env node
// Renders the flock cover (direction D: the flock forms this species' own silhouette) for
// every species of one posting set, from the set's schedule.csv and this directory's
// covers.json. Writes <out>/<set>/<slug>/cover-flock.jpg (never touches the existing cover.jpg,
// which belongs to the video tool) and a covers-sheet.jpg of the whole set for a quick look.
//
//   node cover/render-covers.mjs week1
//   node cover/render-covers.mjs oct19-nov7
import { chromium } from 'playwright';
import { createServer } from 'node:http';
import { readFile, mkdir, rm, writeFile } from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import { dirname, extname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseCsv } from '../lib/schedule.mjs';
import { writeFileAtomic } from '../lib/atomic.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const social = join(here, '..');
const EXPECTED_BIRDS = 839;
const CONTENT_TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.svg': 'image/svg+xml', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg' };

const set = process.argv[2];
if (!set) {
  process.stderr.write('usage: node cover/render-covers.mjs <set>   (e.g. week1, week1-reserves, oct19-nov7)\n');
  process.exit(2);
}
const outDir = join(social, 'out', set);

function serveStatic(root) {
  return createServer((req, res) => {
    const path = decodeURIComponent(new URL(req.url, 'http://x').pathname);
    if (path.includes('..')) {
      res.writeHead(400).end();
      return;
    }
    const file = join(root, path);
    const stream = createReadStream(file);
    stream.on('error', () => {
      res.writeHead(404).end('not found');
    });
    stream.on('open', () => {
      res.writeHead(200, { 'Content-Type': CONTENT_TYPES[extname(file)] || 'application/octet-stream' });
      stream.pipe(res);
    });
  });
}

async function listen(server) {
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  return server.address().port;
}

async function main() {
  const csvText = await readFile(join(outDir, 'schedule.csv'), 'utf8');
  const rows = parseCsv(csvText).filter((r) => r.qid);
  if (!rows.length) throw new Error(`no rows with a qid in ${join(outDir, 'schedule.csv')}`);

  const server = serveStatic(here);
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;
  const browser = await chromium.launch({ channel: 'chrome' });
  const results = [];
  try {
    const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
    for (const row of rows) {
      const { qid, slug } = row;
      const speciesDir = join(outDir, slug);
      await mkdir(speciesDir, { recursive: true });
      const errors = [];
      const onError = (e) => errors.push(String(e));
      page.on('pageerror', onError);
      try {
        await page.goto(`${base}/flock-cover.html?qid=${encodeURIComponent(qid)}`);
        await page.evaluate(() => window.__ready);
        await page.waitForTimeout(150);
        if (errors.length) throw new Error(`page errors: ${errors.join('; ')}`);
        const count = await page.evaluate(() => window.__count);
        if (count !== EXPECTED_BIRDS) throw new Error(`rendered ${count} birds, expected ${EXPECTED_BIRDS}`);
        const jpg = await page.screenshot({ type: 'jpeg', quality: 90 });
        await writeFileAtomic(join(speciesDir, 'cover-flock.jpg'), jpg);
        results.push({ qid, slug, ok: true, count });
        console.log(`ok    ${slug.padEnd(28)} birds=${count}`);
      } catch (e) {
        results.push({ qid, slug, ok: false, error: String(e.message || e) });
        console.log(`FAIL  ${slug.padEnd(28)} ${String(e.message || e)}`);
      } finally {
        page.off('pageerror', onError);
      }
    }
  } finally {
    await browser.close();
    await new Promise((resolve) => server.close(resolve));
  }

  const ok = results.filter((r) => r.ok);
  if (ok.length) await buildSheet(ok, outDir, set);

  const failed = results.filter((r) => !r.ok);
  console.log(`\n${ok.length}/${results.length} rendered` + (failed.length ? `, ${failed.length} failed` : ''));
  if (failed.length) process.exitCode = 1;
}

/** All of a set's covers side by side, each cropped to its centred 3:4 (y 240..1680), for review. */
async function buildSheet(rendered, outDir, set) {
  const tileW = 220;
  const tileH = Math.round((tileW * 4) / 3);
  // object-fit: cover centres and crops a 1080x1920 source to the middle 1080x1440 (3:4),
  // i.e. exactly y 240..1680, whatever the display size.
  const cells = rendered
    .map(
      ({ slug }) => `
      <figure>
        <img src="${slug}/cover-flock.jpg" width="${tileW}" height="${tileH}">
        <figcaption>${slug}</figcaption>
      </figure>`,
    )
    .join('');
  const html = `<!doctype html><html><head><meta charset="utf-8"><title>${set}</title><style>
    body { margin: 0; background: #FDE5CB; font-family: Arial, sans-serif; }
    .sheet { display: flex; flex-wrap: wrap; align-items: flex-start; }
    figure { margin: 0; padding: 6px; }
    img { display: block; width: ${tileW}px; height: ${tileH}px; object-fit: cover; object-position: center; border: 1px solid #D9B78F; }
    figcaption { font-size: 12px; text-align: center; margin-top: 3px; color: #302019; }
  </style></head><body><div class="sheet">${cells}</div></body></html>`;
  const sheetPath = join(outDir, '.covers-sheet-src.html');
  await writeFile(sheetPath, html);

  const server = serveStatic(outDir);
  const port = await listen(server);
  const browser = await chromium.launch({ channel: 'chrome' });
  try {
    const page = await browser.newPage({ viewport: { width: Math.min(1600, tileW * rendered.length + 40), height: 800 }, deviceScaleFactor: 1 });
    await page.goto(`http://127.0.0.1:${port}/.covers-sheet-src.html`);
    await page.waitForLoadState('networkidle');
    const jpg = await page.screenshot({ type: 'jpeg', quality: 90, fullPage: true });
    await writeFile(join(outDir, 'covers-sheet.jpg'), jpg);
    console.log(`wrote ${join(outDir, 'covers-sheet.jpg')}`);
  } finally {
    await browser.close();
    await new Promise((resolve) => server.close(resolve));
    await rm(sheetPath, { force: true });
  }
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
