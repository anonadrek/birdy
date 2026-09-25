import { test, expect } from '@playwright/test';
import { trackConsoleErrors } from './test-helpers';

for (const { path, headline, stat } of [
  { path: '/', headline: 'pocket', stat: 'species' },
  { path: '/sv/', headline: 'fickan', stat: 'arter' },
]) {
  test(`guide section with coverage map renders on ${path}`, async ({ page }) => {
    const consoleErrors = trackConsoleErrors(page);
    const requestUrls: string[] = [];
    page.on('request', (req) => requestUrls.push(req.url()));

    await page.goto(path);
    const section = page.locator('section#guide');
    await expect(section).toBeAttached();
    await expect(section.locator('h2')).toContainText(headline);
    await expect(section.locator('.stats li')).toHaveCount(3);
    await expect(section.locator('.stat-label').first()).toHaveText(stat);
    await expect(section.locator('[data-coverage-map]')).toBeAttached();

    // The live map is lazy AND key-gated: it only initialises when PUBLIC_MAPTILER_KEY is baked
    // into the build (production). CI and local builds have no key, so none of this can ever run
    // -- checked deterministically below instead of a fixed wait + a canvas-or-fallback branch.
    await section.locator('[data-coverage-map]').scrollIntoViewIfNeeded();

    await expect(section.locator('canvas.maplibregl-canvas')).toHaveCount(0);
    expect(requestUrls.some((u) => u.includes('maplibre'))).toBe(false);

    const fallback = section.locator('img.fallback');
    await expect(fallback).toBeVisible();
    await expect.poll(() => fallback.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBe(1600);

    await expect(section.locator('.attrib')).toBeHidden();

    expect(consoleErrors, `Console errors: ${consoleErrors.join('\n')}`).toEqual([]);
  });
}

test.describe('guide stats count-up', () => {
  for (const { path, sr } of [
    { path: '/', sr: ['839 species', '34 badges', '0 accounts'] },
    { path: '/sv/', sr: ['839 arter', '34 märken', '0 konton'] },
  ]) {
    test(`stats reach their final values on ${path}`, async ({ page }) => {
      await page.goto(path);
      await page.locator('#guide .stats').scrollIntoViewIfNeeded();
      await expect.poll(() => page.locator('#guide .stats b').allTextContents()).toEqual(['839', '34', '0']);
      await expect(page.locator('#guide .stats .sr-only')).toHaveText(sr);
    });
  }
});

test.describe('guide stats, reduced motion', () => {
  test.use({ contextOptions: { reducedMotion: 'reduce' } });

  test('stats show their final values immediately, without scrolling', async ({ page }) => {
    await page.goto('/');
    await expect.poll(() => page.locator('#guide .stats b').allTextContents()).toEqual(['839', '34', '0']);
  });
});
