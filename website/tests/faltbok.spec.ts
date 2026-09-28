import { test, expect } from '@playwright/test';

// Webben i fältbokens färger (docs/superpowers/specs/2026-09-28-webb-faltboksfarger-design.md).
const ESPRESSO = 'rgb(42, 29, 23)';
const ESPRESSO_DEEP = 'rgb(30, 20, 16)';

test.describe('espresso i stället för mossa', () => {
  test('de mörka partierna på /sv/ är espresso', async ({ page }) => {
    await page.goto('/sv/');
    await expect(page.locator('[data-hero]')).toHaveCSS('background-color', ESPRESSO);
    await expect(page.locator('#download')).toHaveCSS('background-color', ESPRESSO);
    await expect(page.locator('footer.footer')).toHaveCSS('background-color', ESPRESSO_DEEP);
    const prem = await page.locator('#premium').evaluate((el) => getComputedStyle(el).backgroundImage);
    expect(prem).toContain(ESPRESSO);
    expect(prem).toContain(ESPRESSO_DEEP);
    await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute('content', '#2A1D17');
  });

  test('brödtexten är varm brun', async ({ page }) => {
    await page.goto('/sv/');
    await expect(page.locator('body')).toHaveCSS('color', 'rgb(48, 32, 25)');
  });

  test('telefonerna visar appens egna färger (Lyssna-skärmen är mossgrön)', async ({ page }) => {
    await page.goto('/sv/');
    const bg = await page.locator('#app .ph-listen').first().evaluate((el) => getComputedStyle(el).backgroundImage);
    expect(bg).toContain('rgb(31, 42, 25)');
    await expect(page.locator('#app .ph').first()).toHaveCSS('color', 'rgb(38, 48, 31)');
  });

  test('bloggens rubrikband är espresso', async ({ page }) => {
    await page.goto('/sv/blog/');
    await expect(page.locator('.bhead')).toHaveCSS('background-color', ESPRESSO);
  });
});

test.describe('persika bakom karusellen', () => {
  test('karusellbandet är persika med mörk text', async ({ page }) => {
    await page.goto('/sv/');
    const tour = page.locator('#app');
    await expect(tour).toHaveCSS('background-color', 'rgb(253, 229, 203)');
    await expect(tour.locator('.tour-lead')).toHaveCSS('color', 'rgb(110, 88, 75)');
    await expect(tour.locator('.tour-head .journal-headline')).toHaveCSS('color', 'rgb(48, 32, 25)');
  });
});
