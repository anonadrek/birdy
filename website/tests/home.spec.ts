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
      await expect(nav).not.toHaveClass(/is-solid/);
      await expect(nav).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
      await page.evaluate(() => window.scrollTo({ top: 3000, behavior: 'instant' }));
      await expect(nav).toHaveClass(/is-solid/);
      await expect(nav).toHaveCSS('background-color', 'rgb(31, 42, 25)');
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

test.describe('första vyn', () => {
  test.use({ contextOptions: { reducedMotion: 'reduce' } });

  for (const [path, line1, line2, kicker] of [
    ['/sv/', 'Känn igen fågeln.', 'Bevara stunden.', 'Fågelguide och fältdagbok'],
    ['/', 'Know the bird.', 'Keep the moment.', 'Bird guide and field journal'],
  ] as const) {
    test(`rubrik, kicker, metarad och telefon på ${path}`, async ({ page }) => {
      const errors = trackConsoleErrors(page);
      await page.goto(path);
      const hero = page.locator('[data-hero]');
      await expect(hero.locator('h1')).toContainText(line1);
      await expect(hero.locator('h1 em')).toHaveText(line2);
      await expect(hero.locator('.copy .kick')).toHaveText(kicker);
      await expect(hero.locator('.meta li')).toHaveCount(3);
      await expect(hero.locator('[data-hero-phone] .ph[role="img"]')).toHaveCount(1);
      await expect(hero.locator('[data-robin] img').first()).toBeVisible();
      await expect(hero.locator('[data-birdy]')).toBeVisible();
      expect(errors).toEqual([]);
    });
  }

  for (const width of [390, 1024, 1280, 1440, 1920]) {
    test(`telefonen täcker inte rödhaken och rödhaken syns helt i ${width} px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await page.goto('/sv/');
      const phone = (await page.locator('[data-hero-phone] .ph').boundingBox())!;
      const robin = (await page.locator('[data-robin]').boundingBox())!;
      const overlaps = phone.x < robin.x + robin.width && robin.x < phone.x + phone.width
        && phone.y < robin.y + robin.height && robin.y < phone.y + phone.height;
      expect(overlaps, `telefon ${JSON.stringify(phone)} rödhake ${JSON.stringify(robin)}`).toBe(false);
      expect(robin.x).toBeGreaterThanOrEqual(0);
      expect(robin.x + robin.width).toBeLessThanOrEqual(width);
    });
  }

  test('rödhakens ruta börjar under menyn i 1920 px', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 900 });
    await page.goto('/sv/');
    const nav = (await page.locator('#site-nav').boundingBox())!;
    const robin = (await page.locator('[data-robin]').boundingBox())!;
    expect(robin.y).toBeGreaterThanOrEqual(nav.y + nav.height);
  });

  test('rubriken ryms på två rader på dator', async ({ page }) => {
    for (const width of [1024, 1280, 1440, 1920]) {
      await page.setViewportSize({ width, height: 900 });
      await page.goto('/sv/');
      const lines = await page.locator('[data-hero] h1').evaluate((h) => Math.round(h.getBoundingClientRect().height / parseFloat(getComputedStyle(h).lineHeight)));
      expect(lines, `${width} px`).toBeLessThanOrEqual(2);
    }
  });
});
