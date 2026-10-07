import { test, expect, type Page } from '@playwright/test';
import { trackConsoleErrors } from './test-helpers';

// Runs against the TEST data (tests/fixtures/): build with `npm run build:fixtures` first.
// 16 species are published there, in 7 groups; two woodpeckers are unpublished, one is failed and one pending.

async function noSideScroll(page: Page): Promise<void> {
  const [scroll, client] = await page.evaluate(() => [document.documentElement.scrollWidth, document.documentElement.clientWidth]);
  expect(scroll).toBeLessThanOrEqual(client);
}

test.describe('ingångssidan', () => {
  for (const [path, h1, other, about] of [
    ['/sv/arter/', 'Europa', '/species/', '/sv/arter/om-artsidorna/'],
    ['/species/', 'Europe', '/sv/arter/', '/species/about-these-pages/'],
  ] as const) {
    test(`${path} visar grupper, jämförelser och hela listan`, async ({ page }) => {
      const errors = trackConsoleErrors(page);
      const res = await page.goto(path);
      expect(res?.status()).toBe(200);
      await expect(page.locator('h1')).toContainText(h1);
      await expect(page.locator('.groups a')).toHaveCount(7);
      await expect(page.locator('[data-item]')).toHaveCount(16);
      await expect(page.locator('[data-compare-link]')).toHaveCount(2);
      await expect(page.locator(`a[href="${about}"]`)).toHaveCount(1);
      await expect(page.locator(`link[rel="alternate"][hreflang="${path.startsWith('/sv') ? 'en' : 'sv'}"]`)).toHaveAttribute('href', `https://birdy.community${other}`);
      expect(errors).toEqual([]);
    });
  }

  test('jämförelserna har namnen i svensk ordning', async ({ page }) => {
    await page.goto('/sv/arter/');
    await expect(page.locator('[data-compare-link]')).toHaveText(['Blåmes eller talgoxe', 'Kaja eller skata']);
  });

  test('opublicerade, väntande och misslyckade arter syns inte', async ({ page }) => {
    await page.goto('/sv/arter/');
    for (const name of ['Större hackspett', 'Tretåig hackspett', 'Gröngöling', 'Spillkråka']) {
      await expect(page.locator('[data-item]', { hasText: name })).toHaveCount(0);
    }
    await expect(page.locator('.catbar .chip', { hasText: 'Hackspettar' })).toHaveCount(0);
  });

  test('sökningen filtrerar och klarar å, ä och ö', async ({ page }) => {
    await page.goto('/sv/arter/');
    await page.locator('#species-search').fill('talg');
    await expect(page.locator('[data-item]:visible')).toHaveCount(1);
    await expect(page.locator('[data-item]:visible')).toContainText('Talgoxe');
    await page.locator('#species-search').fill('blames');
    await expect(page.locator('[data-item]:visible').first()).toContainText('Blåmes');
    await page.locator('#species-search').fill('zzzz');
    await expect(page.locator('[data-no-results]')).toBeVisible();
  });

  test('?q= fyller i sökfältet', async ({ page }) => {
    await page.goto('/sv/arter/?q=talg');
    await expect(page.locator('#species-search')).toHaveValue('talg');
    await expect(page.locator('[data-item]:visible')).toHaveCount(1);
  });

  test('sökningen struntar i ordordning ("tit great" hittar Talgoxe)', async ({ page }) => {
    await page.goto('/sv/arter/');
    await page.locator('#species-search').fill('tit great');
    await expect(page.locator('[data-item]:visible')).toHaveCount(1);
    await expect(page.locator('[data-item]:visible')).toContainText('Talgoxe');
  });

  test('sökningen struntar i bindestreck ("long eared owl" hittar Hornuggla)', async ({ page }) => {
    await page.goto('/sv/arter/');
    await page.locator('#species-search').fill('long eared owl');
    await expect(page.locator('[data-item]:visible')).toHaveCount(1);
    await expect(page.locator('[data-item]:visible')).toContainText('Hornuggla');
  });

  test('bokstavsavsnitt utan träff döljs vid sökning', async ({ page }) => {
    await page.goto('/sv/arter/');
    await page.locator('#species-search').fill('talg');
    const visible = page.locator('section.letter:visible');
    await expect(visible).toHaveCount(1);
    await expect(visible.locator('h3')).toHaveText('T');
  });

  test('sökträffarna annonseras i en statusrad för skärmläsare', async ({ page }) => {
    await page.goto('/sv/arter/');
    const status = page.locator('[data-count]');
    await expect(status).toHaveAttribute('role', 'status');
    await expect(status).toHaveText('');
    await page.locator('#species-search').fill('talg');
    await expect(status).toHaveText('1 art');
    await page.locator('#species-search').fill('zzzz');
    await expect(status).toHaveText('0 arter');
    await page.locator('#species-search').fill('');
    await expect(status).toHaveText('');
  });

  test('grupperna och jämförelserna döljs medan man söker, så resultaten hamnar direkt under sökfältet', async ({ page }) => {
    await page.goto('/sv/arter/');
    await expect(page.locator('[data-browse]')).toBeVisible();
    await page.locator('#species-search').fill('talg');
    await expect(page.locator('[data-browse]')).toBeHidden();
    await page.locator('#species-search').fill('');
    await expect(page.locator('[data-browse]')).toBeVisible();
  });

  test('sökresultatet hamnar ovanför vikningen på 390 px', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/arter/?q=talg');
    const box = await page.locator('[data-item]:visible').first().boundingBox();
    expect(box).not.toBeNull();
    expect(box!.y).toBeGreaterThanOrEqual(0);
    expect(box!.y).toBeLessThan(844);
  });

  test('390 px utan sidledsscroll', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/arter/');
    await noSideScroll(page);
  });
});

test.describe('ingångssidan utan JavaScript', () => {
  test.use({ javaScriptEnabled: false });
  test('hela listan syns', async ({ page }) => {
    await page.goto('/sv/arter/');
    const total = await page.locator('[data-item]').count();
    await expect(page.locator('[data-item]:visible')).toHaveCount(total);
  });
});
