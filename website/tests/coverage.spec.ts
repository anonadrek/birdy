import { test, expect } from '@playwright/test';

for (const { path, headline } of [
  { path: '/', headline: 'works' },
  { path: '/sv/', headline: 'funkar' },
]) {
  test(`coverage section renders on ${path}`, async ({ page }) => {
    const consoleErrors: string[] = [];
    page.on('pageerror', (e) => consoleErrors.push(e.message));
    page.on('console', (msg) => { if (msg.type() === 'error') consoleErrors.push(msg.text()); });

    // Track non-404 network errors separately so we catch real errors but
    // ignore the pre-existing /_vercel/insights/script.js 404 that astro
    // preview doesn't serve (only served on Vercel itself).
    page.on('response', (resp) => {
      if (resp.status() === 404 && !resp.url().includes('/_vercel/')) {
        consoleErrors.push(`404: ${resp.url()}`);
      }
    });

    await page.goto(path);
    const section = page.locator('section#coverage');
    await expect(section).toBeAttached();
    await expect(section.locator('h2')).toContainText(headline);
    await expect(section.locator('[data-coverage-map]')).toBeAttached();

    // map is lazy; scroll it into view so the script (if any) runs
    await section.scrollIntoViewIfNeeded();
    await page.waitForTimeout(1500);
    // Filter out the generic "Failed to load resource" messages that come from
    // the known /_vercel/ 404 — those appear as console errors but have no URL
    // in the message text; real errors would be page errors or named 404s above.
    const realErrors = consoleErrors.filter(
      (e) => !e.startsWith('Failed to load resource') && !e.includes('/_vercel/')
    );
    expect(realErrors, `Console errors: ${realErrors.join('\n')}`).toEqual([]);
  });
}
