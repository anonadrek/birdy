import { test, expect } from '@playwright/test';

test.describe('EN landing /', () => {
  test('returns 200 + correct h1 + Play Store link', async ({ page }) => {
    const consoleErrors: string[] = [];
    page.on('pageerror', (e) => consoleErrors.push(e.message));
    page.on('console', (msg) => {
      if (msg.type() === 'error') consoleErrors.push(msg.text());
    });

    const response = await page.goto('/');
    expect(response?.status()).toBe(200);

    await expect(page.locator('h1')).toContainText('field journal');

    const playLink = page.locator(
      'a[href*="play.google.com/store/apps/details?id=se.birdy.android"]',
    );
    expect(await playLink.count()).toBeGreaterThan(0);

    expect(consoleErrors, `Console errors: ${consoleErrors.join('\n')}`).toEqual([]);
  });

  test('has hreflang to /sv/ and back', async ({ page }) => {
    await page.goto('/');
    const sv = await page
      .locator('link[rel="alternate"][hreflang="sv"]')
      .getAttribute('href');
    expect(sv).toContain('/sv/');
  });
});

test.describe('SV landing /sv/', () => {
  test('returns 200 + correct h1 + Play Store link', async ({ page }) => {
    const consoleErrors: string[] = [];
    page.on('pageerror', (e) => consoleErrors.push(e.message));
    page.on('console', (msg) => {
      if (msg.type() === 'error') consoleErrors.push(msg.text());
    });

    const response = await page.goto('/sv/');
    expect(response?.status()).toBe(200);

    await expect(page.locator('h1')).toContainText('fältdagbok');

    const playLink = page.locator(
      'a[href*="play.google.com/store/apps/details?id=se.birdy.android"]',
    );
    expect(await playLink.count()).toBeGreaterThan(0);

    expect(consoleErrors, `Console errors: ${consoleErrors.join('\n')}`).toEqual([]);
  });
});
