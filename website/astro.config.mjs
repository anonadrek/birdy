// @ts-check
import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';
import tailwindcss from '@tailwindcss/vite';
import sharp from 'sharp';
import { copyFileSync, mkdirSync, readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { assetsDir, audioPublicPath, isSpeciesBuilt, readJsonDir, speciesDir } from './src/lib/species-source.mjs';

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

// Recordings live beside the photos (src/assets/species/<QID>/voice.mp3) and are copied into dist only
// for species that get a page in this build, under the content-hashed name the page links to, so an
// unpublished recording is never served (spec 2026-09-25 §9.9; deviation 9 in the plan).
/** @type {import('astro').AstroIntegration} */
const speciesAudio = {
  name: 'birdy-species-audio',
  hooks: {
    'astro:build:done': ({ dir, logger }) => {
      const out = fileURLToPath(new URL('audio/species/', dir));
      let copied = 0;
      for (const record of readJsonDir(root, speciesDir()).filter((r) => isSpeciesBuilt(r) && r.audio)) {
        mkdirSync(out, { recursive: true });
        const name = audioPublicPath(root, record).split('/').pop();
        copyFileSync(resolve(root, assetsDir(), record.audio.file), resolve(out, /** @type {string} */ (name)));
        copied += 1;
      }
      logger.info(`${copied} inspelningar kopierade till audio/species/`);
    },
  },
};

// Photos of the species that get a page in this build, as one virtual module that src/lib/species.ts
// imports (spec 2026-09-25 §9.1: a photo is only served once a built page uses it). An import.meta.glob
// over src/assets/species/ would not do: Vite emits every globbed image into dist/_astro/ as soon as it
// loads it, used or not (lazy globs too), so every unpublished species' photo would go online, and a
// normal build would also carry every test photo under tests/fixtures/ (verified with Astro 7.3.5).
// SPECIES_FIXTURES=1 reads the test photos instead (assetsDir()), SPECIES_PREVIEW=1 adds verified
// unpublished species, the same rule as the pages and the recordings above.
const SPECIES_IMAGES = 'virtual:birdy-species-images';
/** @type {import('vite').Plugin} */
const speciesImages = {
  name: 'birdy-species-images',
  resolveId(id) {
    return id === SPECIES_IMAGES ? `\0${SPECIES_IMAGES}` : undefined;
  },
  load(id) {
    if (id !== `\0${SPECIES_IMAGES}`) return undefined;
    /** @type {string[]} */
    const files = readJsonDir(root, speciesDir())
      .filter((r) => isSpeciesBuilt(r))
      .flatMap((r) => (r.images ?? []).map((/** @type {{ file: string }} */ i) => i.file));
    // Root-relative ids ("/tests/fixtures/species-assets/Q25485/hero.webp"), which Vite resolves on every OS.
    const imports = files.map((file, n) => `import img${n} from ${JSON.stringify(`/${assetsDir()}/${file}`)};`);
    const entries = files.map((file, n) => `[${JSON.stringify(file)}, img${n}]`);
    return `${imports.join('\n')}\nexport default new Map([${entries.join(', ')}]);\n`;
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
    serialize(item) {
      const d = noteDates.get(new URL(item.url).pathname);
      if (d) item.lastmod = d;
      return item;
    },
  }), speciesAudio],
  vite: {
    plugins: [tailwindcss(), speciesImages],
  },
});
