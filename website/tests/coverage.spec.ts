import { test, expect } from '@playwright/test';
import { trackConsoleErrors } from './test-helpers';

for (const { path, headline } of [
  { path: '/', headline: 'works' },
  { path: '/sv/', headline: 'funkar' },
]) {
  test(`coverage section renders on ${path}`, async ({ page }) => {
    const consoleErrors = trackConsoleErrors(page);

    await page.goto(path);
    const section = page.locator('section#coverage');
    await expect(section).toBeAttached();
    await expect(section.locator('h2')).toContainText(headline);
    await expect(section.locator('[data-coverage-map]')).toBeAttached();

    // The map is lazy AND key-gated: it only initialises when a
    // PUBLIC_MAPTILER_KEY is baked into the build. So this assertion is
    // path-aware — CI (no key) exercises the static fallback; a local/preview
    // build WITH a key exercises the live MapLibre canvas. Either way: no errors.
    await section.scrollIntoViewIfNeeded();
    await page.waitForTimeout(3000);

    const canvas = section.locator('canvas.maplibregl-canvas');
    if ((await canvas.count()) > 0) {
      await expect(canvas.first()).toBeVisible(); // live map path (key present)
    } else {
      await expect(section.locator('img.fallback')).toBeVisible(); // fallback path (no key, e.g. CI)
    }

    expect(consoleErrors, `Console errors: ${consoleErrors.join('\n')}`).toEqual([]);
  });
}
