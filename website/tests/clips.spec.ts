import { readdirSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { trackConsoleErrors } from './test-helpers';

const websiteRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');

// The clips page (spec docs/superpowers/specs/2026-10-09-klippsidan-design.md). The fixture build (npm run
// build:fixtures) pins BIRDY_TODAY=2026-10-15, so the page shows the first week, 9 to 15 October, and nothing later.
// The week is written out here rather than read from src/data/clips.json, so a wrong day or name in the data fails.
// All seven birds have a published page in the test data, and all seven silhouettes are CC0.
const WEEK_ONE = [
  { date: '2026-10-15', slug: 'great-tit', en: 'Great Tit', sv: 'Talgoxe', svSlug: 'talgoxe', author: 'Andy Wilson' },
  { date: '2026-10-14', slug: 'common-buzzard', en: 'Common Buzzard', sv: 'Ormvråk', svSlug: 'ormvrak', author: 'Andy Wilson' },
  { date: '2026-10-13', slug: 'common-blackbird', en: 'Common Blackbird', sv: 'Koltrast', svSlug: 'koltrast', author: 'Anthony Caravaggi' },
  { date: '2026-10-12', slug: 'mallard', en: 'Mallard', sv: 'Gräsand', svSlug: 'grasand', author: 'Andy Wilson' },
  { date: '2026-10-11', slug: 'house-sparrow', en: 'House Sparrow', sv: 'Gråsparv', svSlug: 'grasparv', author: 'Andy Wilson' },
  { date: '2026-10-10', slug: 'common-crane', en: 'Common Crane', sv: 'Trana', svSlug: 'trana', author: 'Andy Wilson' },
  { date: '2026-10-09', slug: 'eurasian-blue-tit', en: 'Eurasian Blue Tit', sv: 'Blåmes', svSlug: 'blames', author: 'Wouter Koch' },
] as const;
type Clip = (typeof WEEK_ONE)[number];

const PAGES = [
  {
    path: '/clips/', other: '/sv/klipp/', locale: 'en', crumb: 'Clips', silhouette: 'Silhouette', deedSuffix: '',
    name: (c: Clip) => c.en,
    href: (c: Clip) => `/species/${c.slug}/`,
    posted: (c: Clip) => `Posted ${Number(c.date.slice(8))} October 2026`,
    firstAlt: "The clip's cover: a flock of small birds in the shape of the Great Tit, with the line “Sounds like a squeaky wheelbarrow.”",
  },
  {
    path: '/sv/klipp/', other: '/clips/', locale: 'sv', crumb: 'Klipp', silhouette: 'Siluett', deedSuffix: 'deed.sv',
    name: (c: Clip) => c.sv,
    href: (c: Clip) => `/sv/arter/${c.svSlug}/`,
    posted: (c: Clip) => `Publicerat ${Number(c.date.slice(8))} oktober 2026`,
    firstAlt: 'Klippets omslag: en flock små fåglar i form av en talgoxe och raden ”Sounds like a squeaky wheelbarrow.”',
  },
] as const;

test.describe('Klippsidan', () => {
  for (const p of PAGES) {
    test(`${p.path} svarar och har rubrik, brödsmulor, hreflang, språkbyte och strukturerad data`, async ({ page }) => {
      const errors = trackConsoleErrors(page);
      const res = await page.goto(p.path);
      expect(res?.status()).toBe(200);
      await expect(page.locator('h1')).toHaveText('See the song');
      await expect(page.locator('[data-crumb]').last()).toHaveText(p.crumb);
      await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', `https://birdy.community${p.path}`);
      await expect(page.locator(`link[rel="alternate"][hreflang="${p.locale === 'sv' ? 'en' : 'sv'}"]`)).toHaveAttribute('href', `https://birdy.community${p.other}`);
      await expect(page.locator('link[rel="alternate"][hreflang="x-default"]')).toHaveAttribute('href', 'https://birdy.community/clips/');
      // The menu's language switch goes to the other language's clips page, not to its home page.
      await expect(page.locator('#site-nav .links a.lang')).toHaveAttribute('href', p.other);
      const graph = JSON.parse((await page.locator('script[type="application/ld+json"]').textContent()) ?? '{}')['@graph'] as { '@type': string }[];
      expect(graph.map((n) => n['@type'])).toEqual(expect.arrayContaining(['CollectionPage', 'BreadcrumbList']));
      expect(errors).toEqual([]);
    });

    test(`${p.path} visar klippen 9 till 15 oktober, nyaste först, och inga senare`, async ({ page }) => {
      await page.goto(p.path);
      const cards = page.locator('[data-clip]');
      expect(await cards.evaluateAll((els) => els.map((el) => el.getAttribute('data-clip')))).toEqual(WEEK_ONE.map((c) => c.slug));
      for (const [i, c] of WEEK_ONE.entries()) {
        await expect(cards.nth(i).locator('h2')).toHaveText(p.name(c));
        await expect(cards.nth(i).locator('time')).toHaveAttribute('datetime', c.date);
        await expect(cards.nth(i).locator('[data-clip-date]')).toHaveText(p.posted(c));
      }
      await expect(page.locator('[data-clip="european-robin"]')).toHaveCount(0);
      await expect(page.locator('[data-clips-empty]')).toHaveCount(0);
    });

    test(`${p.path}: varje klipp länkar till sin artsida på sidans språk, och sidan finns`, async ({ page, request }) => {
      await page.goto(p.path);
      await expect(page.locator('[data-clip-link]')).toHaveCount(WEEK_ONE.length);
      for (const c of WEEK_ONE) {
        const link = page.locator(`[data-clip="${c.slug}"] [data-clip-link]`);
        await expect(link).toHaveAttribute('href', p.href(c));
        await expect(link).toHaveAccessibleName(p.name(c));
        expect((await request.get(p.href(c))).status(), p.href(c)).toBe(200);
      }
    });

    test(`${p.path}: omslagen har alt och mått, första raden laddas direkt, silhuettens kredit under kortet`, async ({ page }) => {
      await page.goto(p.path);
      const covers = page.locator('[data-clip] .card img');
      await expect(covers).toHaveCount(WEEK_ONE.length);
      await expect(covers.first()).toHaveAttribute('alt', p.firstAlt);
      for (let i = 0; i < WEEK_ONE.length; i += 1) {
        await expect(covers.nth(i)).toHaveAttribute('width', '540');
        await expect(covers.nth(i)).toHaveAttribute('height', '960');
        await expect(covers.nth(i)).toHaveAttribute('loading', i < 4 ? 'eager' : 'lazy');
      }
      for (const c of WEEK_ONE) {
        const credit = page.locator(`[data-clip="${c.slug}"] > [data-clip-credit]`);
        await expect(credit).toHaveText(`${p.silhouette}: ${c.author}, CC0, via PhyloPic`);
        await expect(credit.getByRole('link', { name: 'CC0' })).toHaveAttribute('href', `https://creativecommons.org/publicdomain/zero/1.0/${p.deedSuffix}`);
        await expect(credit.getByRole('link', { name: 'PhyloPic' })).toHaveAttribute('href', /^https:\/\/www\.phylopic\.org\/images\/[0-9a-f-]{36}$/);
      }
    });
  }

  test('webbplatskartan har båda klippsidorna', async ({ request }) => {
    const xml = await (await request.get('/sitemap-0.xml')).text();
    expect(xml).toContain('<loc>https://birdy.community/clips/</loc>');
    expect(xml).toContain('<loc>https://birdy.community/sv/klipp/</loc>');
  });
});

test.describe('Klippsidan: sidfoten, nätet, smala skärmar och bygget', () => {
  for (const [path, href, text] of [
    ['/', '/clips/', 'Clips'],
    ['/sv/', '/sv/klipp/', 'Klipp'],
    ['/clips/', '/clips/', 'Clips'],
    ['/sv/klipp/', '/sv/klipp/', 'Klipp'],
    ['/premium/', '/clips/', 'Clips'],
    ['/sv/arter/talgoxe/', '/sv/klipp/', 'Klipp'],
  ] as const) {
    test(`sidfoten på ${path} länkar till klippsidan bredvid kanalerna`, async ({ page }) => {
      await page.goto(path);
      const link = page.locator('footer.footer .fbrand [data-footer-clips]');
      await expect(link).toHaveAttribute('href', href);
      await expect(link).toHaveText(text);
      // Next to the channels, not one of them: the follow row still has exactly the four channels.
      await expect(page.locator('footer.footer ul.fsoc a')).toHaveCount(4);
    });
  }

  for (const path of ['/clips/', '/sv/klipp/']) {
    test(`${path} hämtar inget från andra värdar och har inga spelare`, async ({ page, baseURL }) => {
      const hosts = new Set<string>();
      page.on('request', (req) => {
        const url = new URL(req.url());
        if (url.protocol === 'http:' || url.protocol === 'https:') hosts.add(url.host);
      });
      await page.goto(path);
      // Scrolling to the footer fires whichever lazy covers load along the way, but a long build can still skip
      // some middle cards before the network goes idle; what this actually guarantees is that every request the
      // page makes is same-origin (covers are always served from /_astro/).
      await page.locator('footer.footer').scrollIntoViewIfNeeded();
      await page.waitForLoadState('networkidle');
      expect([...hosts]).toEqual([new URL(baseURL!).host]);
      await expect(page.locator('iframe, video, audio, object, embed')).toHaveCount(0);
    });
  }

  for (const width of [320, 390]) {
    test(`inget sidledes scroll på klippsidan i ${width} px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      for (const path of ['/clips/', '/sv/klipp/']) {
        await page.goto(path);
        expect(await page.evaluate(() => document.documentElement.scrollWidth), path).toBeLessThanOrEqual(width);
      }
    });
  }

  // Only the shown clips' covers reach the build (virtual:birdy-clips imports nothing else), so a later clip's cover is
  // not online before its day. Astro names an image's files after it: <slug>.<hash>_<hash>.webp.
  test('bara de visade klippens omslag finns i bygget', () => {
    const files = readdirSync(resolve(websiteRoot, 'dist', '_astro'));
    const { clips } = JSON.parse(readFileSync(resolve(websiteRoot, 'src', 'data', 'clips.json'), 'utf8')) as { clips: { date: string; slug: string }[] };
    for (const c of clips) {
      const slugPattern = c.slug.replace(/[|\\{}()[\]^$+*?.]/g, '\\$&');
      const emitted = files.filter((f) => new RegExp(`^${slugPattern}\\.[^.]+\\.webp$`).test(f));
      if (c.date <= '2026-10-15') expect(emitted.length, c.slug).toBeGreaterThan(0);
      else expect(emitted, c.slug).toEqual([]);
    }
  });
});

test.describe('axe på klippsidan', () => {
  // Reduced motion, as in the other axe runs: every element in its final colours.
  test.use({ contextOptions: { reducedMotion: 'reduce' } });
  for (const path of ['/clips/', '/sv/klipp/']) {
    for (const width of [390, 1440]) {
      test(`axe: ${path} i ${width} px`, async ({ page }) => {
        await page.setViewportSize({ width, height: 900 });
        await page.goto(path);
        const results = await new AxeBuilder({ page }).analyze();
        expect(results.violations, JSON.stringify(results.violations, null, 2)).toEqual([]);
      });
    }
  }
});
