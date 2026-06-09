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

    // map is lazy; scroll it into view so the script (if any) runs
    await section.scrollIntoViewIfNeeded();
    await page.waitForTimeout(1500);
    expect(consoleErrors, `Console errors: ${consoleErrors.join('\n')}`).toEqual([]);
  });
}
