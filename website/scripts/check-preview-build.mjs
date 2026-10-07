#!/usr/bin/env node
// Checks the publishing rule (spec 2026-09-25 §14) on the TEST data: dist/ is the normal fixture build
// (npm run build:fixtures) and dist-preview/ the preview build (npm run build:preview-fixtures).
//
// Since Task 4's fix wave (2026-10-07): no photo or recording of a species without a page in a build may
// reach that build. Task 14 adds its page checks (unpublished pages, noindex, banner, sitemap) to this
// same script instead of replacing it.
//
// A test file is found in a build in two ways:
// - byte-identical anywhere in the build (photo originals in _astro/, recordings in audio/species/);
// - as any resized or re-encoded copy of a photo (<Image> variants, share images): every test photo is
//   one flat colour, and the test data gives each species its own hue (make-species-fixtures.mjs), so a
//   flat image in the build with the same colour is that photo. Astro deletes an original that is only
//   used through <Image>, so the byte check alone would miss those.
// The positive checks (published media in dist/, verified unpublished media in dist-preview/) only run
// once the build has species pages (Task 7 and on): until a page imports src/lib/species.ts, no species
// photo is in any build.
import { createHash } from 'node:crypto';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';
import { isSpeciesBuilt, readJsonDir } from '../src/lib/species-source.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const dist = resolve(root, 'dist');
const preview = resolve(root, 'dist-preview');
for (const dir of [dist, preview]) {
  if (!existsSync(dir)) {
    console.error(`check-preview-build: ${dir} saknas (npm run build:fixtures och npm run build:preview-fixtures)`);
    process.exit(1);
  }
}
const errors = [];

// -- Photos and recordings ---------------------------------------------------------------------------
const ASSETS = 'tests/fixtures/species-assets';
const IMAGE = /\.(webp|jpe?g|png|avif)$/i;
/** Largest per-channel difference (0-255) between two colours that still counts as the same photo. */
const TOLERANCE = 6;

const sha256 = (file) => createHash('sha256').update(readFileSync(file)).digest('hex');
/** Every file under `dir`, at any depth. */
const filesUnder = (dir) =>
  readdirSync(dir, { recursive: true, withFileTypes: true })
    .filter((entry) => entry.isFile())
    .map((entry) => join(entry.parentPath, entry.name));
/** The mean colour of an image that is one flat colour, or null for any other image. */
async function flatColour(file) {
  const { channels } = await sharp(file).stats();
  const rgb = channels.slice(0, 3);
  return rgb.every((c) => c.stdev < 4) ? rgb.map((c) => c.mean) : null;
}
const near = (a, b) => a.every((v, i) => Math.abs(v - b[i]) <= TOLERANCE);

// The test data, sorted by what each build may show (isSpeciesBuilt, the same rule as the pages).
const records = readJsonDir(root, 'tests/fixtures/species');
const published = records.filter((r) => isSpeciesBuilt(r, false));
const previewOnly = records.filter((r) => isSpeciesBuilt(r, true) && !isSpeciesBuilt(r, false));
const never = records.filter((r) => !isSpeciesBuilt(r, true));
for (const [name, list] of [['publicerade', published], ['bara i förhandsbygget', previewOnly], ['aldrig byggda', never]]) {
  if (!list.length) errors.push(`testdatan har inga ${name} arter, kontrollen säger då ingenting`);
}

/** Every test file: its path under species-assets/, hash and (for a photo) colour. */
const media = [];
for (const record of records) {
  const dir = resolve(root, ASSETS, record.qid);
  if (!existsSync(dir)) continue;
  for (const file of filesUnder(dir)) {
    const colour = IMAGE.test(file) ? await flatColour(file) : null;
    if (IMAGE.test(file) && !colour) errors.push(`${ASSETS}: ${file} är inte en enfärgad testbild (make-species-fixtures.mjs)`);
    media.push({ qid: record.qid, file: `${record.qid}/${file.slice(dir.length + 1).replace(/\\/g, '/')}`, hash: sha256(file), colour });
  }
}
// Two test photos closer than twice the tolerance could be mistaken for each other.
const photos = media.filter((m) => m.colour);
for (const [i, a] of photos.entries()) {
  for (const b of photos.slice(i + 1)) {
    if (a.colour.every((v, k) => Math.abs(v - b.colour[k]) <= 2 * TOLERANCE)) errors.push(`testfotona ${a.file} och ${b.file} har nästan samma färg`);
  }
}

/** What a build holds: file hashes, and the colours of its flat images. */
async function scan(dir) {
  const files = filesUnder(dir);
  const colours = [];
  for (const file of files.filter((f) => IMAGE.test(f))) {
    const colour = await flatColour(file).catch(() => null);
    if (colour) colours.push(colour);
  }
  return { hashes: new Set(files.map(sha256)), colours, hasSpeciesPages: existsSync(join(dir, 'sv', 'arter')) };
}
const builds = { dist: await scan(dist), 'dist-preview': await scan(preview) };
const isIn = (m, build) => builds[build].hashes.has(m.hash) || (m.colour !== null && builds[build].colours.some((c) => near(c, m.colour)));

const mustBe = (list, build, present, why) => {
  const qids = new Set(list.map((r) => r.qid));
  for (const m of media.filter((x) => qids.has(x.qid))) {
    if (isIn(m, build) !== present) errors.push(`${build}: ${ASSETS}/${m.file} ${present ? 'saknas' : 'finns'} (${why})`);
  }
};
mustBe(never, 'dist', false, 'arten får aldrig en sida');
mustBe(never, 'dist-preview', false, 'arten får aldrig en sida');
mustBe(previewOnly, 'dist', false, 'opublicerad art, bara förhandsbygget visar den');
const positive = builds.dist.hasSpeciesPages && builds['dist-preview'].hasSpeciesPages;
if (positive) {
  mustBe(previewOnly, 'dist-preview', true, 'kontrollerad men opublicerad art, förhandsbygget visar den');
  mustBe(published, 'dist', true, 'publicerad art; är dist/ ett fixturbygge?');
}

if (errors.length) {
  console.error(`check-preview-build FAILED (${errors.length} fel):\n${errors.join('\n')}`);
  process.exit(1);
}
console.log(
  `check-preview-build OK (foton och inspelningar: ${never.map((r) => r.qid).join(', ')} i inget bygge, ` +
    `${previewOnly.map((r) => r.qid).join(', ')} inte i dist/` +
    (positive
      ? ` men i dist-preview/, alla ${published.length} publicerade arter i dist/)`
      : `; inga artsidor i bygget än, så bara frånvaron är kontrollerad)`),
);
