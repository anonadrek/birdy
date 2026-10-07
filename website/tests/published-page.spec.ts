import { test, expect } from '@playwright/test';
import { trackConsoleErrors } from './test-helpers';

// The page the publish loop just built (scripts/publish-next.mjs, plan Task 16), checked on the production
// build before it is committed: PAGE_PATHS holds its address in both languages, comma-separated
// ("sv/arter/koboltmes/,species/azure-tit/"). Without PAGE_PATHS the test data's Talgoxe is checked, so the
// same tests run in the normal suite (npm run build:fixtures first). Data-independent on purpose: the
// fixture-specific checks live in species.spec.ts.
const SITE = 'https://birdy.community';
const paths = (process.env.PAGE_PATHS ?? 'sv/arter/talgoxe/,species/great-tit/').split(',').map((p) => p.trim()).filter(Boolean);

for (const path of paths) {
  test.describe(`publicerad sida /${path}`, () => {
    test('svarar 200, är indexerbar, finns i sitemapen och språkparet finns', async ({ page, request }) => {
      const errors = trackConsoleErrors(page);
      const res = await page.goto(`/${path}`);
      expect(res?.status()).toBe(200);
      const html = await page.content();
      expect(html).not.toContain('content="noindex');
      expect(html).not.toContain('data-preview-banner');
      await expect(page.locator('h1')).toHaveCount(1);
      await expect(page.locator('h1')).not.toBeEmpty();
      await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', `${SITE}/${path}`);

      const sitemap = await (await request.get('/sitemap-0.xml')).text();
      expect(sitemap).toContain(`<loc>${SITE}/${path}</loc>`);

      const other = await page.locator(`link[rel="alternate"][hreflang="${path.startsWith('sv/') ? 'en' : 'sv'}"]`).getAttribute('href');
      expect(other).toBeTruthy();
      expect((await request.get(new URL(other!).pathname)).status(), other!).toBe(200);

      const og = await page.locator('meta[property="og:image"]').getAttribute('content');
      expect((await request.get(new URL(og!).pathname)).status(), og!).toBe(200);

      // The first photo (the hero) is loaded, not just referenced.
      const first = page.locator('main img').first();
      await expect(first).toBeVisible();
      expect(await first.evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0)).toBe(true);

      for (const src of await page.locator('audio').evaluateAll((els) => els.map((e) => e.getAttribute('src')))) {
        expect(src).toBeTruthy();
        expect((await request.get(src!)).status(), src!).toBe(200);
      }
      await expect(page.locator('[data-reviewed]')).toHaveCount(1);
      expect(errors).toEqual([]);
    });

    for (const width of [360, 390, 430]) {
      test(`ingen sidledsscroll i ${width} px`, async ({ page }) => {
        await page.setViewportSize({ width, height: 844 });
        await page.goto(`/${path}`);
        const [scroll, client] = await page.evaluate(() => [document.documentElement.scrollWidth, document.documentElement.clientWidth]);
        expect(scroll).toBeLessThanOrEqual(client);
      });
    }
  });
}
