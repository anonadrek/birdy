import { test, expect } from '@playwright/test';
import { trackConsoleErrors } from './test-helpers';

test.describe('EN landing /', () => {
  test('returns 200 + correct h1 + Play Store link', async ({ page }) => {
    const consoleErrors = trackConsoleErrors(page);

    const response = await page.goto('/');
    expect(response?.status()).toBe(200);

    await expect(page.locator('h1')).toContainText('Know the bird.');
    await expect(page.locator('h1')).toContainText('Keep the moment.');

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

    await expect(page.locator('h1')).toContainText('Känn igen fågeln.');
    await expect(page.locator('h1')).toContainText('Bevara stunden.');

    const playLink = page.locator(
      'a[href*="play.google.com/store/apps/details?id=se.birdy.android"]',
    );
    expect(await playLink.count()).toBeGreaterThan(0);

    expect(consoleErrors, `Console errors: ${consoleErrors.join('\n')}`).toEqual([]);
  });
});

test.describe('Field Notes', () => {
  test('pages fit a narrow mobile viewport (SV and EN)', async ({ page }) => {
    for (const width of [360, 390]) {
      await page.setViewportSize({ width, height: 844 });
      for (const path of ['/sv/', '/sv/blog/', '/sv/blog/why-birdy/', '/', '/blog/', '/blog/why-birdy/']) {
        await page.goto(path);
        const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth);
        expect(scrollWidth, `${path} at ${width}px should not overflow horizontally`).toBeLessThanOrEqual(width);
      }
    }
  });

  for (const [locale, prefix, title] of [
    ['en', '', 'Why Birdy exists'],
    ['sv', '/sv', 'Varför Birdy finns'],
  ] as const) {
    test(`${locale} index and article have localized navigation and metadata`, async ({ page }) => {
      const consoleErrors = trackConsoleErrors(page);
      const index = await page.goto(`${prefix}/blog/`);
      expect(index?.status()).toBe(200);
      await expect(page.locator('main h1')).toHaveCount(1);
      await expect(page.locator(`main a[href="${prefix}/blog/why-birdy/"]`).first()).toBeVisible();

      const article = await page.goto(`${prefix}/blog/why-birdy/`);
      expect(article?.status()).toBe(200);
      await expect(page.locator('main h1')).toHaveText(title);
      await expect(page.locator('.article-prose h2')).toHaveCount(3);
      await expect(page.locator('meta[property="og:type"]')).toHaveAttribute('content', 'article');
      await expect(page.locator('link[rel="alternate"][hreflang="en"]')).toHaveAttribute('href', 'https://birdy.community/blog/why-birdy/');
      await expect(page.locator('link[rel="alternate"][hreflang="sv"]')).toHaveAttribute('href', 'https://birdy.community/sv/blog/why-birdy/');
      const albitHref = locale === 'sv' ? 'https://www.albit.se/produkter/birdy/' : 'https://www.albit.se/en/products/birdy/';
      await expect(page.locator(`footer a[href="${albitHref}"]`)).toHaveText('AlbIT');
      expect(consoleErrors).toEqual([]);
    });
  }
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
