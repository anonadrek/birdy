// @ts-check
import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';
import tailwindcss from '@tailwindcss/vite';
import sharp from 'sharp';
import { copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { basename, dirname, resolve } from 'node:path';
import { SHARE_QUALITY, SHARE_SIZE, assetsDir, builtSpeciesMedia, isPreview, paperColour, speciesDir } from './src/lib/species-source.mjs';
import { readSpeciesSitemapInfo } from './src/lib/species-sitemap.mjs';
import { buildDate, loadAppSpeciesSnapshot, selectAppDailyBird } from './src/lib/daily-bird.mjs';
import { clipsDataFile, clipsModuleSource, loadClips } from './src/lib/clips.mjs';

const root = dirname(fileURLToPath(import.meta.url));

/**
 * Reads one field note's frontmatter block (the text between the first two `---` lines) and returns
 * its slug, date and image path, so every failure below can name the offending file instead of
 * crashing the whole `astro build`/`dev`/`check` with a bare "Invalid time value" stack trace.
 *
 * Values may be bare, single- or double-quoted, and may carry a trailing ` # comment`
 * (`date: 2026-09-24 # published`) — quotes are stripped and comments are cut before use.
 * @param {string} text
 * @param {string} filePath
 */
export function parseNoteFrontmatter(text, filePath) {
  const lines = text.split(/\r?\n/);
  const startIdx = lines.findIndex((/** @type {string} */ l) => l.trim() === '---');
  if (startIdx === -1) throw new Error(`Field note has no frontmatter block: ${filePath}`);
  const endIdx = lines.findIndex((/** @type {string} */ l, /** @type {number} */ i) => i > startIdx && l.trim() === '---');
  if (endIdx === -1) throw new Error(`Field note's frontmatter block is never closed: ${filePath}`);
  const block = lines.slice(startIdx + 1, endIdx);

  /** @param {string} name */
  const readField = (name) => {
    const line = block.find((l) => new RegExp(`^${name}:\\s*`).test(l));
    if (line === undefined) return undefined;
    let value = line.replace(new RegExp(`^${name}:\\s*`), '').trim();
    const quoted = value.match(/^"([^"]*)"$/) ?? value.match(/^'([^']*)'$/);
    if (quoted) return quoted[1];
    const hashAt = value.indexOf(' #');
    if (hashAt !== -1) value = value.slice(0, hashAt).trim();
    return value;
  };

  const slug = readField('slug');
  const rawDate = readField('date');
  const image = readField('image');
  if (!slug || !rawDate) {
    throw new Error(`Field note is missing slug or date for the sitemap: ${filePath}`);
  }
  const iso = /^\d{4}-\d{2}-\d{2}$/.test(rawDate) ? `${rawDate}T00:00:00Z` : rawDate;
  const date = new Date(iso);
  if (Number.isNaN(date.valueOf())) {
    throw new Error(`Field note has an invalid date for the sitemap: ${filePath} (frontmatter says ${JSON.stringify(rawDate)})`);
  }
  return { slug, date, image };
}

/**
 * Every `.md` file under `dir`, at any depth.
 * @param {string} dir
 */
export function listMarkdownFiles(dir) {
  return readdirSync(dir, { recursive: true, encoding: 'utf8' })
    .filter((f) => f.endsWith('.md'))
    .map((f) => resolve(dir, f));
}

const MIN_IMAGE_WIDTH = 1200;
const MIN_IMAGE_HEIGHT = 630;

// Field note `lastmod` dates for the sitemap: albit.se reads new posts from it (spec §6 amendment).
// This also enforces the note photo's minimum size (it doubles as the 1200×630 share image), since
// this collection's `glob()` loader only ever hands `image().refine()` an unresolved placeholder
// string, never real width/height (verified against astro@5.18.2) — see the comment in
// content.config.ts. This walk already opens every note's frontmatter for its date, so checking the
// real file on disk here, with a real path to name in the error, costs nothing extra.
const noteDates = new Map();
for (const locale of ['en', 'sv']) {
  const dir = resolve(root, `src/content/field-notes/${locale}`);
  for (const filePath of listMarkdownFiles(dir)) {
    const { slug, date, image } = parseNoteFrontmatter(readFileSync(filePath, 'utf8'), filePath);
    const path = locale === 'sv' ? `/sv/blog/${slug}/` : `/blog/${slug}/`;
    noteDates.set(path, date.toISOString());

    if (image) {
      const imagePath = resolve(dirname(filePath), image);
      const { width, height } = await sharp(imagePath).metadata();
      if (!width || !height || width < MIN_IMAGE_WIDTH || height < MIN_IMAGE_HEIGHT) {
        throw new Error(
          `Field note image is smaller than ${MIN_IMAGE_WIDTH}×${MIN_IMAGE_HEIGHT} (it is also the share image): ${filePath} -> ${width ?? '?'}×${height ?? '?'}`,
        );
      }
    }
  }
}

// Species pages: lastmod from each page's data, small groups and unpublished preview pages left out (spec §12 and §14).
const speciesInfo = readSpeciesSitemapInfo(root);

// The photos and recordings of the species that get a page in this build, read once per build on first
// use (after the content sync, so a broken record gets zod's message first). Spec 2026-09-25 §9.1 and
// §9.9: a photo or recording is only served once a built page uses it.
/** @type {ReturnType<typeof builtSpeciesMedia> | undefined} */
let media;
const speciesMedia = () => (media ??= builtSpeciesMedia(root));

// Recordings live beside the photos (src/assets/species/<QID>/voice.mp3) and are copied into dist only
// for species that get a page in this build, under the content-hashed name the page links to (the same
// href the virtual module gives the pages), so an unpublished recording is never served (deviation 9).
/** @type {import('astro').AstroIntegration} */
const speciesAudio = {
  name: 'birdy-species-audio',
  hooks: {
    'astro:build:done': ({ dir, logger }) => {
      const { audio } = speciesMedia();
      const out = fileURLToPath(new URL('audio/species/', dir));
      if (audio.length) mkdirSync(out, { recursive: true });
      for (const recording of audio) {
        copyFileSync(resolve(root, assetsDir(), recording.file), resolve(out, basename(recording.href)));
      }
      logger.info(`${audio.length} inspelningar kopierade till audio/species/`);
    },
  },
};

// The share images (og:image) of the species pages: the whole hero photo letterboxed on the paper colour,
// never cropped (sharePublicPath in species-source.mjs), drawn into dist only for species that get a page,
// under the hashed name the page links to. Astro's getImage can't do this (its `fit: 'contain'` pads with
// black, `background` only flattens transparency), so sharp draws them here. A drawn image is kept in
// node_modules/.cache/birdy-share/ under the same hashed name, so a build redraws only new or changed photos.
/** @type {import('astro').AstroIntegration} */
const speciesShare = {
  name: 'birdy-species-share',
  hooks: {
    // One line in every build log (Vercel's too): which species data the build reads, and VERCEL_ENV, which the
    // Production guard against preview pages (isPreview) depends on. "(saknas)" on Vercel means the project's
    // "Automatically expose System Environment Variables" is off and the guard can't see Production.
    'astro:build:start': ({ logger }) => {
      logger.info(`artdata ${speciesDir()}, förhandsbygge ${isPreview() ? 'på' : 'av'}, VERCEL_ENV=${process.env.VERCEL_ENV ?? '(saknas)'}`);
    },
    'astro:build:done': async ({ dir, logger }) => {
      const { share } = speciesMedia();
      const out = fileURLToPath(new URL('og/species/', dir));
      const cache = resolve(root, 'node_modules/.cache/birdy-share');
      if (share.length) mkdirSync(out, { recursive: true });
      mkdirSync(cache, { recursive: true });
      const background = paperColour(root);
      let drawn = 0;
      for (const image of share) {
        const name = basename(image.href);
        const cached = resolve(cache, name);
        if (!existsSync(cached)) {
          await sharp(resolve(root, assetsDir(), image.file))
            .rotate()
            .resize({ ...SHARE_SIZE, fit: 'contain', background })
            .flatten({ background })
            .jpeg({ quality: SHARE_QUALITY, mozjpeg: true })
            .toFile(cached);
          drawn += 1;
        }
        copyFileSync(cached, resolve(out, name));
      }
      logger.info(`${share.length} delningsbilder i og/species/ (${drawn} nyritade)`);
    },
  },
};

// The photos and recording links of the species that get a page, as one virtual module that
// src/lib/species.ts imports. An import.meta.glob over src/assets/species/ would not do: Vite emits every
// globbed image into dist/_astro/ as soon as it loads it, used or not (lazy globs too), so every
// unpublished species' photo would go online, and a normal build would also carry every test photo under
// tests/fixtures/ (verified with Astro 7.3.5). SPECIES_FIXTURES=1 reads the test photos instead
// (assetsDir()), SPECIES_PREVIEW=1 adds verified unpublished species, the same rule as the pages.
const SPECIES_MEDIA = 'virtual:birdy-species-media';
/** @type {import('vite').Plugin} */
const speciesMediaModule = {
  name: 'birdy-species-media',
  resolveId(id) {
    return id === SPECIES_MEDIA ? `\0${SPECIES_MEDIA}` : undefined;
  },
  load(id) {
    if (id !== `\0${SPECIES_MEDIA}`) return undefined;
    const { images, audio, share } = speciesMedia();
    // Root-relative ids ("/tests/fixtures/species-assets/Q25485/hero.webp"), which Vite resolves on every OS.
    const imports = images.map((file, n) => `import img${n} from ${JSON.stringify(`/${assetsDir()}/${file}`)};`);
    const entries = images.map((file, n) => `[${JSON.stringify(file)}, img${n}]`);
    return [
      ...imports,
      `export const images = new Map([${entries.join(', ')}]);`,
      `export const audio = new Map(${JSON.stringify(audio.map((a) => [a.qid, a.href]))});`,
      `export const share = new Map(${JSON.stringify(share.map((a) => [a.qid, a.href]))});`,
      '',
    ].join('\n');
  },
};

// Today's date in Europe/Stockholm, worked out once per build and shared by both plugins below: Dagens fågel and
// the clips page each read it, and two separate buildDate() calls could straddle midnight mid-build (one plugin
// loading just before 00.00, the other just after) and show one day's Dagens fågel next to the next day's clips.
// Memoised so the second reader gets the exact same value the first one computed, not a fresh "now". In `astro dev`
// this is once per server start, not once per request: a dev server left running across midnight keeps showing the
// previous day's date until it is restarted.
/** @type {ReturnType<typeof buildDate> | undefined} */
let cachedBuildDate;
const sharedBuildDate = () => (cachedBuildDate ??= buildDate());

// Dagens fågel (plan 2026-10-08 Task 1): today's date (sharedBuildDate above) and the app's pick for it, worked out
// from the shipped app's frozen species list (src/data/app-species-<version>.json, src/lib/daily-bird.mjs), never
// from the branch's YAML, which is not what people's phones run (npm run check:app-species tells whether the two
// agree). A virtual module, so the list is read here in Node and never enters Vite's module graph; the home page
// combines the pick with the species that have a page in this build. The nightly rebuild
// (.github/workflows/daily-site-build.yml) moves it to the next day.
const DAILY_BIRD = 'virtual:birdy-daily-bird';
/** @type {import('vite').Plugin} */
const dailyBirdModule = {
  name: 'birdy-daily-bird',
  resolveId(id) {
    return id === DAILY_BIRD ? `\0${DAILY_BIRD}` : undefined;
  },
  load(id) {
    if (id !== `\0${DAILY_BIRD}`) return undefined;
    const date = sharedBuildDate();
    const appQid = selectAppDailyBird(loadAppSpeciesSnapshot(root), date);
    const pinned = process.env.BIRDY_TODAY ? ', BIRDY_TODAY' : '';
    console.log(`[birdy-daily-bird] ${date.iso} (Europe/Stockholm${pinned}): appens Dagens fågel ${appQid ?? 'ingen'}`);
    return [`export const date = ${JSON.stringify(date)};`, `export const appQid = ${JSON.stringify(appQid)};`, ''].join('\n');
  },
};

// The clips page (spec 2026-10-09-klippsidan): the See the song clips posted on or before the build's date, newest
// first, with the same date as Dagens fågel above (sharedBuildDate: Europe/Stockholm, BIRDY_TODAY in test builds), so
// the nightly rebuild adds each day's clip by itself. A virtual module, like the species photos', because Vite emits
// every image a module imports into dist/_astro/, used or not: only the shown clips' covers are imported
// (clipsModuleSource), so a later clip's cover is not online before its day. The resolved id carries Vite's NUL
// prefix, as above.
const CLIPS = 'virtual:birdy-clips';
const RESOLVED_CLIPS = `${String.fromCharCode(0)}${CLIPS}`;
const CLIPS_DATA_FILE = clipsDataFile(root);
/** @type {import('vite').Plugin} */
const clipsModule = {
  name: 'birdy-clips',
  resolveId(id) {
    return id === CLIPS ? RESOLVED_CLIPS : undefined;
  },
  load(id) {
    if (id !== RESOLVED_CLIPS) return undefined;
    // So the dev server reloads this virtual module whenever scripts/import-clips.mjs rewrites clips.json.
    this.addWatchFile(CLIPS_DATA_FILE);
    return clipsModuleSource(loadClips(root), sharedBuildDate().iso);
  },
};

export default defineConfig({
  site: 'https://birdy.community',
  trailingSlash: 'ignore',
  // Astro 7 defaults to 'jsx', which strips whitespace between inline elements (the legal pages'
  // "Privacy Policy · …" links moved). `true` keeps the HTML-aware compression the site was built with.
  compressHTML: true,
  i18n: {
    defaultLocale: 'en',
    locales: ['en', 'sv'],
    routing: {
      prefixDefaultLocale: false,
    },
  },
  integrations: [sitemap({
    filter: (page) => !speciesInfo.noindex.has(new URL(page).pathname),
    serialize(item) {
      const path = new URL(item.url).pathname;
      const d = noteDates.get(path) ?? speciesInfo.lastmod.get(path);
      if (d) item.lastmod = new Date(d).toISOString();
      return item;
    },
  }), speciesAudio, speciesShare],
  vite: {
    plugins: [tailwindcss(), speciesMediaModule, dailyBirdModule, clipsModule],
  },
});
