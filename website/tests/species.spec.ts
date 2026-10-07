import { test, expect, type Page } from '@playwright/test';
import { trackConsoleErrors } from './test-helpers';

// Runs against the TEST data (tests/fixtures/): build with `npm run build:fixtures` first.
// 17 species are published there, in 8 groups; two woodpeckers are unpublished, one is failed and one pending.
// (Blåkråka, absent in Sweden and the only species in "other", is published since Task 10: 16 and 7 before.)

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
      await expect(page.locator('.groups a')).toHaveCount(8);
      await expect(page.locator('[data-item]')).toHaveCount(17);
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
    await expect(page.locator('.catbar .chip')).toHaveCount(9);
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
    // purpose: the test exercises the safeguard, not a case the pipeline is expected to produce.
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

test.describe('artsidan', () => {
  test('talgoxe: rubrik, fakta, moduler, förväxlingsart, credits och språkbyte', async ({ page, request }) => {
    const errors = trackConsoleErrors(page);
    const res = await page.goto('/sv/arter/talgoxe/');
    expect(res?.status()).toBe(200);
    await expect(page.locator('h1')).toHaveText('Talgoxe');
    await expect(page.locator('.latin')).toHaveText('Parus major');

    const facts = page.locator('.facts');
    for (const text of ['Vetenskapligt namn', 'Mesar', 'Stannfågel', 'Cirka 14 cm', 'Livskraftig (LC)']) await expect(facts).toContainText(text);
    await expect(facts.locator('[data-redlist]')).toContainText('Inte rödlistad');
    await expect(page.locator('.sp-app')).toContainText('på foto eller läte');
    await expect(page.locator('.note')).toHaveText('Testanteckning i marginalen.');
    await expect(page.locator('h2')).toContainText(['Så känner du igen den', 'Läte', 'Var och när', 'Föda och beteende', 'Kan förväxlas med', 'Fler tättingar']);

    const audio = page.locator('audio');
    await expect(audio).toHaveAttribute('preload', 'none');
    const src = await audio.getAttribute('src');
    expect(src).toMatch(/^\/audio\/species\/Q25485\.[0-9a-f]{10}\.mp3$/);
    expect((await request.get(src!)).status()).toBe(200);
    await expect(page.locator('[data-credit-for="audio"]')).toContainText('bearbetad');

    await expect(page.locator('[data-chart] rect[data-month]')).toHaveCount(12);
    // The bar's hover title has no number: the values are scaled to a top month of 100, not per cent (Task 9 re-review).
    await expect(page.locator('[data-chart] rect[data-month="10"] title')).toHaveText('Talgoxe, oktober');
    await expect(page.locator('[data-map] path[data-county]')).toHaveCount(21);
    await expect(page.locator('.data-summary')).toHaveText('Rapporteras året runt. Rapporteras från alla 21 län.');
    await expect(page.locator('[data-data-credit]')).toContainText('Artportalen');

    const looks = page.locator('.looks li');
    await expect(looks).toHaveCount(1);
    await expect(looks.locator('.look-name a')).toHaveAttribute('href', '/sv/arter/blames/');
    await expect(looks.locator('.look-compare')).toHaveAttribute('href', '/sv/arter/blames-eller-talgoxe/');
    await expect(looks.locator('.look-compare')).toHaveText('Jämför blåmes och talgoxe');

    const keys = await page.locator('[data-photo], [data-audio]').evaluateAll((els) => els.map((e) => e.getAttribute('data-photo') ?? e.getAttribute('data-audio')));
    expect(keys).toEqual(['hero', 'audio', 'extra']);
    for (const key of keys) await expect(page.locator(`[data-credit-for="${key}"]`)).toHaveCount(1);
    await expect(page.locator('[data-wiki-credit] [data-wiki]')).toHaveCount(3);
    await expect(page.locator('[data-wiki-credit]')).toContainText('CC BY-SA 4.0');
    await expect(page.locator('[data-checked]')).toContainText('Kontrollerad mot källorna 20 november 2026');
    await expect(page.locator('time[data-reviewed]')).toHaveAttribute('datetime', '2026-11-20');
    await expect(page.locator('.credits a[href="/sv/arter/om-artsidorna/"]')).toHaveCount(1);
    await expect(page.locator('a[href*="utm_campaign%3Dtalgoxe"]')).toHaveCount(1);
    await expect(page.locator('#site-nav .links a[lang="en"]')).toHaveAttribute('href', '/species/great-tit/');
    // Not "page": the species page isn't the group's own page, so the active chip reads aria-current="true"
    // (controller review 2026-10-07, see Task 6's code block note).
    await expect(page.locator('.catbar .chip[aria-current="true"]')).toContainText('Tättingar');
    await expect(page.locator('[data-preview-banner]')).toHaveCount(0);
    await expect(page.locator('meta[name="robots"]')).toHaveCount(0);
    expect(errors).toEqual([]);
  });

  test('engelska sidan: kontrollraden och jämförelselänken', async ({ page }) => {
    await page.goto('/species/great-tit/');
    await expect(page.locator('h1')).toHaveText('Great Tit');
    await expect(page.locator('[data-checked]')).toContainText('Checked against sources on 20 November 2026.');
    await expect(page.locator('.look-compare')).toHaveText('Compare the Eurasian Blue Tit and the Great Tit');
    await expect(page.locator('.sp-app')).toContainText('from a photo or its song');
  });

  test('pärluggla: utan inspelning, data, extrafoto, föda och förväxlingsarter', async ({ page }) => {
    const errors = trackConsoleErrors(page);
    await page.goto('/sv/arter/parluggla/');
    await expect(page.locator('audio')).toHaveCount(0);
    await expect(page.locator('[data-chart], [data-map]')).toHaveCount(0);
    await expect(page.locator('[data-data-credit]')).toHaveCount(0);
    await expect(page.locator('[data-photo]')).toHaveCount(1);
    await expect(page.locator('h2', { hasText: 'Föda och beteende' })).toHaveCount(0);
    await expect(page.locator('h2', { hasText: 'Kan förväxlas med' })).toHaveCount(0);
    for (const label of ['I Sverige', 'Storlek', 'Svenska rödlistan 2025']) await expect(page.locator('.facts')).not.toContainText(label);
    await expect(page.locator('.sp-app')).toContainText('hjälper dig känna igen fåglarna');
    await expect(page.locator('[data-wiki-credit] [data-wiki]')).toHaveCount(1);
    expect(errors).toEqual([]);
  });

  for (const [path, text] of [['/sv/arter/kaja/', 'på lätet,'], ['/sv/arter/trana/', 'på foto,'], ['/species/western-jackdaw/', 'from its song']] as const) {
    test(`approtan på ${path} säger bara vad appen klarar`, async ({ page }) => {
      await page.goto(path);
      await expect(page.locator('.sp-app')).toContainText(text);
    });
  }

  test('global rödlista döljs för NE, svensk rödlista visas med kod', async ({ page }) => {
    await page.goto('/sv/arter/kaja/');
    await expect(page.locator('.facts')).not.toContainText('Global rödlista');
    await page.goto('/sv/arter/fiskmas/');
    await expect(page.locator('.facts [data-redlist]')).toContainText('Nära hotad (NT)');
  });

  test('förväxlingsarter utan egen sida får vetenskapligt namn utan länk', async ({ page }) => {
    await page.goto('/sv/arter/kattuggla/');
    await expect(page.locator('.looks .look-name')).toHaveText('Strix uralensis');
    await expect(page.locator('.looks .look-name a')).toHaveCount(0);
    await page.goto('/sv/arter/hornuggla/');
    await expect(page.locator('.looks .look-name')).toHaveText('Asio flammeus');
  });

  test('blåkråka: frånvarande art utan diagram och karta, men med datacredit och förväxlingsart utan sida', async ({ page }) => {
    const errors = trackConsoleErrors(page);
    const res = await page.goto('/sv/arter/blakraka/');
    expect(res?.status()).toBe(200);
    const facts = page.locator('.facts');
    await expect(facts).toContainText('I Sverige');
    await expect(facts).toContainText('Förekommer inte');
    await expect(facts).toContainText('Livskraftig (LC)');
    await expect(facts).not.toContainText('Svenska rödlistan 2025');
    await expect(page.locator('[data-chart], [data-map], audio')).toHaveCount(0);
    // data exists (the presence sentence, spec §9.2) without months/counties: the sentence and the report
    // credit show, the red list credit doesn't (controller decision, Task 9 re-review).
    await expect(page.locator('.data-summary')).toBeVisible();
    await expect(page.locator('.data-summary')).toHaveText('Förekommer inte i Sverige: inga rapporter i Artportalen 2016 till 2025.');
    await expect(page.locator('[data-data-credit]')).toHaveCount(1);
    await expect(page.locator('[data-data-credit]')).toContainText('Artportalen');
    await expect(page.locator('[data-data-credit]')).not.toContainText('Rödlista');
    await expect(page.locator('.sp-app')).toContainText('hjälper dig känna igen fåglarna');
    // The look-alike has a record but no page in this build (unpublished): its name, no link, photo or comparison.
    const look = page.locator('.looks li');
    await expect(look).toHaveCount(1);
    await expect(look.locator('.look-name')).toHaveText('Större hackspett');
    await expect(look.locator('a, img')).toHaveCount(0);
    // The only species in "other": no "Fler ..." section.
    await expect(page.locator('.more')).toHaveCount(0);
    expect(errors).toEqual([]);
  });

  test('JSON-LD: brödsmulor och WebPage med taxon, foto, inspelning och kontrolldatum', async ({ page }) => {
    await page.goto('/sv/arter/talgoxe/');
    const graph = JSON.parse((await page.locator('script[type="application/ld+json"]').textContent())!)['@graph'] as Record<string, any>[];
    const crumbs = graph.find((n) => n['@type'] === 'BreadcrumbList')!;
    expect(crumbs.itemListElement.map((c: { name: string }) => c.name)).toEqual(['Birdy', 'Arter', 'Tättingar', 'Talgoxe']);
    expect(crumbs.itemListElement[3].item).toBe('https://birdy.community/sv/arter/talgoxe/');
    const web = graph.find((n) => n['@type'] === 'WebPage')!;
    expect(web.inLanguage).toBe('sv');
    expect(web.lastReviewed).toBe('2026-11-20');
    expect(web).not.toHaveProperty('reviewedBy');
    expect(web.about).toMatchObject({ '@type': 'Taxon', name: 'Parus major', sameAs: 'https://www.wikidata.org/wiki/Q25485' });
    expect(web.primaryImageOfPage).toMatchObject({ '@type': 'ImageObject', creditText: 'Testfotograf' });
    const src = await page.locator('audio').getAttribute('src');
    expect(web.associatedMedia).toMatchObject({ '@type': 'AudioObject', contentUrl: `https://birdy.community${src}`, license: 'https://creativecommons.org/licenses/by-sa/4.0/' });
    await page.goto('/sv/arter/parluggla/');
    const plain = (JSON.parse((await page.locator('script[type="application/ld+json"]').textContent())!)['@graph'] as Record<string, any>[]).find((n) => n['@type'] === 'WebPage')!;
    expect(plain).not.toHaveProperty('associatedMedia');
  });

  test('public domain: "public domain" i gemener utan licenslänk, okänd upphovsperson när namnet saknas', async ({ page }) => {
    await page.goto('/sv/arter/koltrast/');
    const extra = page.locator('[data-credit-for="extra"]');
    await expect(extra).toContainText('Foto: Testfotograf två, public domain, via Wikimedia Commons, nedskalad');
    await expect(extra.locator('a')).toHaveText(['Wikimedia Commons']);
    await page.goto('/sv/arter/kattuggla/');
    const audio = page.locator('[data-credit-for="audio"]');
    await expect(audio).toContainText('Inspelning: okänd upphovsperson, public domain, via Wikimedia Commons, bearbetad');
    await expect(audio.locator('a')).toHaveText(['Wikimedia Commons']);
    // JSON-LD says no more than the page: no licence link and no creator for this recording.
    const graph = JSON.parse((await page.locator('script[type="application/ld+json"]').textContent())!)['@graph'] as Record<string, any>[];
    const media = graph.find((n) => n['@type'] === 'WebPage')!.associatedMedia;
    expect(media['@type']).toBe('AudioObject');
    expect(media).not.toHaveProperty('license');
    expect(media).not.toHaveProperty('creator');
    await page.goto('/species/tawny-owl/');
    await expect(page.locator('[data-credit-for="audio"]')).toContainText('Recording: unknown recordist, public domain, via Wikimedia Commons, edited');
  });

  test('opublicerade, väntande och misslyckade arter ger 404', async ({ page }) => {
    for (const path of ['/sv/arter/storre-hackspett/', '/sv/arter/grongoling/', '/sv/arter/spillkraka/', '/species/black-woodpecker/']) {
      expect((await page.goto(path))?.status(), path).toBe(404);
    }
  });

  test('vänsterspalten följer med på dator', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/sv/arter/talgoxe/');
    await page.evaluate(() => window.scrollTo({ top: 900, behavior: 'instant' }));
    const box = (await page.locator('.plate.hero').boundingBox())!;
    expect(box.y).toBeGreaterThanOrEqual(76);
    expect(box.y).toBeLessThan(260);
  });

  test('mobilen: rubrik, foto, fakta, ingress och approta i den ordningen', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/arter/talgoxe/');
    const y = async (sel: string) => (await page.locator(sel).first().boundingBox())!.y;
    const order = [await y('h1'), await y('.plate.hero'), await y('.facts'), await y('.sp-lead'), await y('.looks-sec'), await y('.sp-app')];
    for (let i = 1; i < order.length; i += 1) expect(order[i]).toBeGreaterThan(order[i - 1]);
  });

  for (const width of [360, 390, 430]) {
    test(`ingen sidledsscroll i ${width} px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 844 });
      for (const path of ['/sv/arter/talgoxe/', '/species/great-tit/', '/sv/arter/parluggla/', '/sv/arter/blakraka/']) {
        await page.goto(path);
        await noSideScroll(page);
      }
    });
  }
});
