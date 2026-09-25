import { test, expect } from '@playwright/test';
import { trackConsoleErrors } from './test-helpers';

for (const { path, headline, stat } of [
  { path: '/', headline: 'pocket', stat: 'species' },
  { path: '/sv/', headline: 'fickan', stat: 'arter' },
]) {
  test(`guide section with coverage map renders on ${path}`, async ({ page }) => {
    const consoleErrors = trackConsoleErrors(page);

    await page.goto(path);
    const section = page.locator('section#guide');
    await expect(section).toBeAttached();
    await expect(section.locator('h2')).toContainText(headline);
    await expect(section.locator('.stats li')).toHaveCount(3);
    await expect(section.locator('.stat-label').first()).toHaveText(stat);
    await expect(section.locator('[data-coverage-map]')).toBeAttached();

    // The live map is lazy AND key-gated: it only initialises when PUBLIC_MAPTILER_KEY is baked
    // into the build (production). CI and local builds have no key and show the static fallback.
    await section.locator('[data-coverage-map]').scrollIntoViewIfNeeded();
    await page.waitForTimeout(3000);

    const canvas = section.locator('canvas.maplibregl-canvas');
    if ((await canvas.count()) > 0) {
      await expect(canvas.first()).toBeVisible();
    } else {
      await expect(section.locator('img.fallback')).toBeVisible();
    }

    expect(consoleErrors, `Console errors: ${consoleErrors.join('\n')}`).toEqual([]);
  });
}
