import { test, expect } from '@playwright/test';
import { trackConsoleErrors } from './test-helpers';

test.describe('meny och sidfot', () => {
  for (const [path, label, getApp] of [['/sv/', 'Så funkar det', 'Hämta appen'], ['/', 'How it works', 'Get the app']] as const) {
    test(`menyn på ${path} har nya länkar och blir mossgrön efter första vyn`, async ({ page }) => {
      const errors = trackConsoleErrors(page);
      await page.setViewportSize({ width: 1280, height: 800 });
      await page.goto(path);
      const nav = page.locator('#site-nav');
      await expect(nav.locator('.links a').first()).toHaveText(label);
      await expect(nav.locator('.nav-cta')).toHaveText(getApp);
      await expect(nav.locator('.nav-cta')).toHaveAttribute('href', `${path}#download`);
      await page.mouse.wheel(0, 3000);
      await expect(nav).toHaveClass(/is-solid/);
      expect(errors).toEqual([]);
    });
  }

  test('mobilmenyn öppnas och stängs med Esc', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/');
    const toggle = page.locator('#site-nav .menu-toggle');
    await expect(page.locator('#mobile-menu')).toBeHidden();
    await toggle.click();
    await expect(page.locator('#mobile-menu')).toBeVisible();
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await expect(page.locator('#mobile-menu a', { hasText: 'Integritet' })).toHaveAttribute('href', '/sv/#privacy');
    await page.keyboard.press('Escape');
    await expect(page.locator('#mobile-menu')).toBeHidden();
  });

  test('sidfoten har kolumnerna', async ({ page }) => {
    await page.goto('/sv/');
    const footer = page.locator('footer.footer');
    await expect(footer.locator('.fh')).toHaveText(['Utforska', 'Läs', 'Information']);
    await expect(footer.locator('a[href="/legal/privacy/"]')).toHaveText('Integritetspolicy');
  });

  for (const [path, albitHref, workshop, line] of [
    ['/sv/', 'https://www.albit.se/produkter/birdy/', 'Från samma verkstad', 'HR-verktyg för chefer i växande bolag'],
    ['/', 'https://www.albit.se/en/products/birdy/', 'From the same workshop', 'An HR tool for managers in growing companies'],
  ] as const) {
    test(`sidfoten på ${path} länkar till AlbIT och LoopLead`, async ({ page }) => {
      await page.goto(path);
      const footer = page.locator('footer.footer');
      await expect(footer.locator(`a[href="${albitHref}"]`)).toHaveText('AlbIT');
      await expect(footer.locator('.sibling')).toContainText(workshop);
      const looplead = footer.locator('a[href="https://looplead.se/"]');
      await expect(looplead).toContainText('LoopLead');
      await expect(looplead).toContainText(line);
    });
  }
});
