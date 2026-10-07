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

test.describe('gruppsidorna', () => {
  test('/sv/arter/ugglor/ har aktiv chip, arter och approta', async ({ page }) => {
    const errors = trackConsoleErrors(page);
    const res = await page.goto('/sv/arter/ugglor/');
    expect(res?.status()).toBe(200);
    await expect(page.locator('h1')).toHaveText('Ugglor');
    await expect(page.locator('.catbar .chip[aria-current="page"]')).toContainText('Ugglor');
    await expect(page.locator('.catbar .chip')).toHaveCount(8);
    await expect(page.locator('[data-item]')).toHaveCount(3);
    await expect(page.locator('meta[name="robots"]')).toHaveCount(0);
    await expect(page.locator('a[href*="utm_medium%3Dgroup"]')).toHaveCount(1);
    await expect(page.locator('.sp-app')).toContainText('hjälper dig känna igen fåglarna');
    expect(errors).toEqual([]);
  });

  test('Tättingar delas upp i familjer', async ({ page }) => {
    await page.goto('/sv/arter/tattingar/');
    await expect(page.locator('.family h3')).toHaveCount(6);
    // Swedish headings are Swedish text, not Latin: no pronunciation tag needed (controller review, Task 8 fix wave).
    await expect(page.locator('.family h3[lang="la"]')).toHaveCount(0);
  });

  test('familjer grupperas efter det latinska namnet, inte den svenska stavningen (Kaja och Skata är båda Corvidae men fixturen ger dem olika family.sv)', async ({ page }) => {
    await page.goto('/sv/arter/tattingar/');
    // One of the two Swedish spellings wins (whichever species is first in sort order), but there must be
    // only ONE Corvidae section either way, not a second one for the other spelling: family.latin, not
    // family.sv, drives the grouping (controller review, Task 8 fix wave). The pipeline now guarantees one
    // canonical Swedish name per Latin family, so real data never disagrees like this fixture does on
    // purpose — the test exercises the safeguard, not a case the pipeline is expected to produce.
    const corvids = page.locator('.family').filter({ has: page.locator('[data-item]', { hasText: 'Skata' }) });
    await expect(corvids).toHaveCount(1);
    await expect(corvids.locator('[data-item]')).toHaveCount(2);
    await expect(corvids).toContainText('Skata');
    await expect(corvids).toContainText('Kaja');
  });

  test('Songbirds (EN) delas också upp i sex familjer, rubrikerna är latin', async ({ page }) => {
    await page.goto('/species/songbirds/');
    await expect(page.locator('.family h3')).toHaveCount(6);
    await expect(page.locator('.family h3[lang="la"]')).toHaveCount(6);
    // Same family split as the Swedish page, grouped by family.latin, so one Corvidae section here too.
    const corvids = page.locator('.family').filter({ has: page.locator('h3', { hasText: 'Corvidae' }) });
    await expect(corvids).toHaveCount(1);
    await expect(corvids.locator('[data-item]')).toHaveCount(2);
  });

  test('de fyra första artkorten laddas direkt, resten lat (Tättingar, 8 arter i 6 familjer)', async ({ page }) => {
    await page.goto('/sv/arter/tattingar/');
    const images = page.locator('.sp-cards img');
    await expect(images).toHaveCount(8);
    const loading = await images.evaluateAll((els) => els.map((el) => el.getAttribute('loading')));
    expect(loading.slice(0, 4)).toEqual(['eager', 'eager', 'eager', 'eager']);
    expect(loading.slice(4)).toEqual(['lazy', 'lazy', 'lazy', 'lazy']);
  });

  test('en grupp med en enda art undviker "dem"/"them" i beskrivningen', async ({ page }) => {
    await page.goto('/sv/arter/havsfaglar/');
    const descSv = await page.locator('meta[name="description"]').getAttribute('content');
    expect(descSv).not.toContain('dem');
    expect(descSv).toContain('1 art');

    await page.goto('/species/seabirds/');
    const descEn = await page.locator('meta[name="description"]').getAttribute('content');
    expect(descEn).not.toContain('them');
    expect(descEn).toContain('1 species');
  });

  test('gruppsidans og:image är artens eget foto, inte standardbilden', async ({ page }) => {
    await page.goto('/sv/arter/ugglor/');
    const og = await page.locator('meta[property="og:image"]').getAttribute('content');
    expect(og).not.toContain('og-field-');
  });

  test('små grupper har noindex', async ({ page }) => {
    await page.goto('/sv/arter/havsfaglar/');
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'noindex, follow');
  });

  test('en grupp utan byggda arter får ingen sida', async ({ page }) => {
    expect((await page.goto('/sv/arter/hackspettar/'))?.status()).toBe(404);
  });

  test('kategoriraden sveps i sidled på 390 px utan att sidan gör det', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/arter/ugglor/');
    const [scroll, client] = await page.locator('[data-chips]').evaluate((el) => [el.scrollWidth, el.clientWidth]);
    expect(scroll).toBeGreaterThan(client);
    await noSideScroll(page);
  });

  test('engelska gruppsidan och språkbytet', async ({ page }) => {
    const res = await page.goto('/species/owls/');
    expect(res?.status()).toBe(200);
    await expect(page.locator('link[rel="alternate"][hreflang="sv"]')).toHaveAttribute('href', 'https://birdy.community/sv/arter/ugglor/');
  });
});
