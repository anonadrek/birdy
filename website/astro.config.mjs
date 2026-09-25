// @ts-check
import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';
import tailwindcss from '@tailwindcss/vite';
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const root = dirname(fileURLToPath(import.meta.url));

// Field note `lastmod` dates for the sitemap: albit.se reads new posts from it (spec §6 amendment).
const noteDates = new Map();
for (const locale of ['en', 'sv']) {
  const dir = resolve(root, `src/content/field-notes/${locale}`);
  for (const file of readdirSync(dir).filter((f) => f.endsWith('.md'))) {
    const text = readFileSync(resolve(dir, file), 'utf8');
    const slug = text.match(/^slug:\s*(.+)$/m)?.[1]?.trim();
    const date = text.match(/^date:\s*(.+)$/m)?.[1]?.trim();
    if (!slug || !date) continue;
    const path = locale === 'sv' ? `/sv/blog/${slug}/` : `/blog/${slug}/`;
    noteDates.set(path, new Date(date).toISOString());
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
