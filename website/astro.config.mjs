// @ts-check
import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';
import tailwindcss from '@tailwindcss/vite';
import sharp from 'sharp';
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

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

export default defineConfig({
  site: 'https://birdy.community',
  trailingSlash: 'ignore',
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
  })],
  vite: {
    plugins: [tailwindcss()],
  },
});
