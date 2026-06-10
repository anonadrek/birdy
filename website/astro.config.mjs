// @ts-check
import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';
import tailwindcss from '@tailwindcss/vite';

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
  integrations: [
    sitemap({
      // The site ships EN at `/` and SV at `/sv/`. Emitting hreflang
      // alternates lets crawlers treat them as language variants of the
      // same page instead of duplicate content.
      i18n: {
        defaultLocale: 'en',
        locales: {
          en: 'en',
          sv: 'sv',
        },
      },
    }),
  ],
  vite: {
    plugins: [tailwindcss()],
  },
});
