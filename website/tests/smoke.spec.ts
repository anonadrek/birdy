import { test, expect } from '@playwright/test';
import { trackConsoleErrors } from './test-helpers';

test.describe('EN landing /', () => {
  test('returns 200 + correct h1 + Play Store link', async ({ page }) => {
    const consoleErrors = trackConsoleErrors(page);

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
    const consoleErrors = trackConsoleErrors(page);

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

test.describe('Legal section', () => {
  let consoleErrors: string[];

  test.beforeEach(({ page }) => {
    consoleErrors = trackConsoleErrors(page);
  });

  test('/legal/ index returns 200 + links to 3 docs', async ({ page }) => {
    const response = await page.goto('/legal/');
    expect(response?.status()).toBe(200);

    await expect(page.locator('h1')).toContainText('fine print');

    for (const slug of ['privacy', 'terms', 'data-safety']) {
      const link = page.locator(`a[href="/legal/${slug}/"]`);
      expect(await link.count()).toBeGreaterThan(0);
    }

    expect(consoleErrors, `Console errors: ${consoleErrors.join('\n')}`).toEqual([]);
  });

  test('/legal/privacy/ renders markdown body + cross-links', async ({ page }) => {
    const response = await page.goto('/legal/privacy/');
    expect(response?.status()).toBe(200);

    await expect(page.locator('h1')).toContainText('Privacy');
    expect(await page.locator('.legal-prose h2').count()).toBeGreaterThan(0);

    const termsLink = page.locator('aside.more a[href="/legal/terms/"]');
    expect(await termsLink.count()).toBe(1);

    expect(consoleErrors, `Console errors: ${consoleErrors.join('\n')}`).toEqual([]);
  });

  test('/legal/terms/ renders', async ({ page }) => {
    const response = await page.goto('/legal/terms/');
    expect(response?.status()).toBe(200);
    await expect(page.locator('h1')).toContainText('Terms');
    expect(consoleErrors, `Console errors: ${consoleErrors.join('\n')}`).toEqual([]);
  });

  test('/legal/data-safety/ renders', async ({ page }) => {
    const response = await page.goto('/legal/data-safety/');
    expect(response?.status()).toBe(200);
    await expect(page.locator('h1')).toContainText('Data Safety');
    expect(consoleErrors, `Console errors: ${consoleErrors.join('\n')}`).toEqual([]);
  });
});
