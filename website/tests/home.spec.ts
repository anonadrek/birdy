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
    const menu = page.locator('#mobile-menu');
    await expect(menu).toBeHidden();
    await toggle.click();
    await expect(menu).toBeVisible();
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await expect(toggle).toHaveAttribute('aria-label', 'Stäng menyn');
    await expect(menu.locator('a', { hasText: 'Integritet' })).toHaveAttribute('href', '/sv/#privacy');
    await expect(menu.locator('a[href="/sv/#guide"]')).toHaveCount(1);
    await expect(menu.locator('a[href="/sv/#faq"]')).toHaveCount(1);
    await menu.locator('a').first().focus();
    await page.keyboard.press('Escape');
    await expect(menu).toBeHidden();
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await expect(toggle).toHaveAttribute('aria-label', 'Öppna menyn');
    await expect(toggle).toBeFocused();
  });

  test('mobilmenyn går att scrolla i liggande läge och stängs av en länk', async ({ page }) => {
    await page.setViewportSize({ width: 844, height: 390 });
    await page.goto('/sv/');
    await page.locator('#site-nav .menu-toggle').click();
    const cta = page.locator('#mobile-menu a.btn');
    await cta.scrollIntoViewIfNeeded();
    await expect(cta).toBeInViewport();
    await cta.click();
    await expect(page.locator('#mobile-menu')).toBeHidden();
  });

  test('menyn markerar Fältanteckningar på bloggen', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/sv/blog/');
    await expect(page.locator('#site-nav .links a[aria-current="page"]')).toHaveText('Fältanteckningar');
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

test.describe('utan JavaScript', () => {
  test.use({ javaScriptEnabled: false });

  test('startsidans meny är mossgrön och den döda menyknappen dold', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/');
    await expect(page.locator('#site-nav')).toHaveCSS('background-color', 'rgb(31, 42, 25)');
    await expect(page.locator('#site-nav .menu-toggle')).toBeHidden();
  });
});
