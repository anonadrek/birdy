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

  test('telefonerna visar appens egna färger (mossgrön Lyssna, olivbläck)', async ({ page }) => {
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
    const shadow = await tour.locator('.ph').first().evaluate((el) => getComputedStyle(el).boxShadow);
    expect(shadow).toContain('rgba(42, 29, 23, 0.22)');
  });
});

test.describe('handskrivna accentord', () => {
  for (const path of ['/', '/sv/', '/blog/'] as const) {
    test(`accentorden är handskrivna på ${path}`, async ({ page }) => {
      await page.goto(path);
      const accents = page.locator('.journal-headline .accent');
      if (path === '/blog/') {
        expect(await accents.count()).toBeGreaterThan(0);
      } else {
        expect(await accents.count()).toBeGreaterThan(5);
      }
      for (const el of await accents.all()) {
        await expect(el).toHaveCSS('font-family', /Caveat/);
        await expect(el).toHaveCSS('font-style', 'normal');
      }
      if (path !== '/blog/') {
        await expect(page.locator('[data-hero] h1 em')).toHaveCSS('font-family', /Caveat/);
        await expect(page.locator('[data-hero] h1 em')).toHaveCSS('font-style', 'normal');
      }
      await page.evaluate(() => document.fonts.ready);
      expect(
        await page.evaluate(() =>
          [...document.fonts].some(
            (f) => f.family.replace(/"/g, '') === 'Caveat' && f.weight === '700' && f.status === 'loaded',
          ),
        ),
      ).toBe(true);
    });
  }
});

test.describe('marginalanteckningar', () => {
  const notes = {
    '/sv/': ['Se. Lyssna. Spara.', 'kamera, foto eller läte', 'så här ser det ut i fält', 'slå upp fågeln du just såg', 'helt valfritt, att känna igen fåglar är gratis', 'dina bilder stannar i telefonen', 'anteckningar från oss som bygger Birdy', 'det folk brukar undra', 'vi ses i fält'],
    '/': ['See. Listen. Keep.', 'camera, photo or song', 'this is how it looks in the field', 'look up the bird you just saw', 'optional, identifying birds is free', 'your photos stay on your phone', 'notes from the people who build Birdy', 'what people usually ask', 'see you out there'],
  } as const;
  for (const [path, texts] of Object.entries(notes)) {
    test(`en handskriven rad under varje rubrik på ${path}`, async ({ page }) => {
      await page.goto(path);
      const mnotes = page.locator('.mnote');
      await expect(mnotes).toHaveText([...texts]);
      for (const el of await mnotes.all()) await expect(el).toHaveCSS('font-family', /Caveat/);
      await expect(page.locator('[data-hero] .mnote')).toHaveCSS('color', 'rgb(242, 178, 122)');
      await expect(page.locator('#how-it-works .mnote')).toHaveCSS('color', 'rgb(154, 69, 38)');
    });
  }
});
