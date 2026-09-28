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
        await expect(el).toHaveCSS('font-weight', '700');
      }
      if (path !== '/blog/') {
        await expect(page.locator('[data-hero] h1 em')).toHaveCSS('font-family', /Caveat/);
        await expect(page.locator('[data-hero] h1 em')).toHaveCSS('font-style', 'normal');
        await expect(page.locator('[data-hero] h1 em')).toHaveCSS('font-weight', '700');
      }
      // Only proves the Caveat bold face exists and loads (the nav wordmark uses it too); the font-weight checks above prove the accents use it.
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
      for (const el of await mnotes.all()) {
        await expect(el).toHaveCSS('font-family', /Caveat/);
        await expect(el).toHaveCSS('font-weight', '700');
      }
      await expect(page.locator('[data-hero] .mnote')).toHaveCSS('color', 'rgb(242, 178, 122)');
      await expect(page.locator('#how-it-works .mnote')).toHaveCSS('color', 'rgb(154, 69, 38)');
      await expect(page.locator('#premium .mnote')).toHaveCSS('color', 'rgb(242, 178, 122)');
      await expect(page.locator('#download .mnote')).toHaveCSS('color', 'rgb(242, 178, 122)');
    });
  }
});

test.describe('bildtexter i handstil', () => {
  const stats = {
    '/sv/': ['från vanliga till sällsynta', 'tjänas in när du hittar fåglar', 'din dagbok stannar hos dig'],
    '/': ['from common to rare', 'earned by finding birds', 'your journal stays with you'],
  } as const;
  for (const [path, texts] of Object.entries(stats)) {
    test(`siffernoter, kartans bildtext och sidfotens rad på ${path}`, async ({ page }) => {
      await page.goto(path);
      await expect(page.locator('#guide .stat-note')).toHaveText([...texts]);
      for (const sel of ['#guide .stat-note', '#guide .mapcap', 'footer .tag']) {
        await expect(page.locator(sel).first()).toHaveCSS('font-family', /Caveat/);
      }
      await expect(page.locator('#guide .stat-note').first()).toHaveCSS('color', 'rgb(154, 69, 38)');
      await expect(page.locator('#guide .mapcap')).toHaveCSS('color', 'rgb(154, 69, 38)');
      await expect(page.locator('footer .tag')).toHaveCSS('color', 'rgb(242, 178, 122)');
    });
  }

  for (const width of [320, 360, 390]) {
    for (const path of ['/sv/', '/'] as const) {
      test(`siffernoterna håller avstånd till kolumnkanterna på mobil ${width}px ${path}`, async ({ page }) => {
        await page.setViewportSize({ width, height: 844 });
        await page.goto(path);
        await page.evaluate(() => document.fonts.ready);
        // Settle two rAFs after fonts load so layout has caught up before measuring.
        await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))));
        const gaps = await page.evaluate(() => {
          const lis = [...document.querySelectorAll('#guide .stats li')];
          return lis.map((li) => {
            const note = li.querySelector('.stat-note');
            const liBox = li.getBoundingClientRect();
            if (!note || !note.textContent?.trim()) return { left: Infinity, right: Infinity };
            const r = document.createRange();
            r.selectNodeContents(note);
            const rects = [...r.getClientRects()];
            const textLeft = Math.min(...rects.map((rect) => rect.left));
            const textRight = Math.max(...rects.map((rect) => rect.right));
            return { left: textLeft - liBox.left, right: liBox.right - textRight };
          });
        });
        expect(gaps).toHaveLength(3);
        for (const gap of gaps) {
          expect(Number.isFinite(gap.left), 'vänster kant hittade ingen text').toBe(true);
          expect(Number.isFinite(gap.right), 'höger kant hittade ingen text').toBe(true);
          expect(gap.left, 'vänster luft till kolumnkanten').toBeGreaterThanOrEqual(4);
          expect(gap.right, 'höger luft till kolumnkanten').toBeGreaterThanOrEqual(4);
        }
      });
    }
  }
});

test.describe('rivna papperskanter', () => {
  const fills = (page: import('@playwright/test').Page) =>
    page.locator('.deckle path').evaluateAll((ps) => ps.map((p) => getComputedStyle(p).fill));

  test('startsidan: övre bandets färg river ner i det undre', async ({ page }) => {
    await page.goto('/sv/');
    // Named per section so a failure points straight at the broken edge, and a total-count
    // assertion so an extra/missing edge fails even if every named one still matches.
    const edges: [string, string][] = [
      ['#how-it-works > .deckle path', 'rgb(42, 29, 23)'],    // hero → Tre sätt att fånga
      ['#app > .deckle path', 'rgb(255, 250, 241)'],          // Fältboken → karusellen
      ['#guide > .deckle path', 'rgb(253, 229, 203)'],        // karusellen → Uppslagsverket
      ['#premium > .deckle path', 'rgb(246, 239, 226)'],      // Uppslagsverket → Premium
      ['#privacy > .deckle path', 'rgb(30, 20, 16)'],         // Premium → Integritet
      ['#download > .deckle path', 'rgb(255, 250, 241)'],     // Frågor → Ta med Birdy
      ['footer.footer > .deckle path', 'rgb(42, 29, 23)'],    // Ta med Birdy → sidfot
    ];
    await expect(page.locator('.deckle')).toHaveCount(7);
    for (const [selector, expected] of edges) {
      await expect(page.locator(selector), selector).toHaveCSS('fill', expected);
    }
    const sealZ = await page.locator('#premium .pseal').evaluate((el) => Number(getComputedStyle(el).zIndex));
    const edgeZ = await page.locator('#premium .deckle').evaluate((el) => Number(getComputedStyle(el).zIndex));
    expect(sealZ).toBeGreaterThan(edgeZ);
  });

  test('herotelefonen ligger ovanför kanten mot Så funkar det i 1024×768', async ({ page }) => {
    await page.setViewportSize({ width: 1024, height: 768 });
    await page.goto('/sv/');
    const phoneZ = await page.locator('.phone-slot').evaluate((el) => Number(getComputedStyle(el).zIndex));
    const edgeZ = await page.locator('#how-it-works > .deckle').evaluate((el) => Number(getComputedStyle(el).zIndex));
    expect(phoneZ).toBeGreaterThan(edgeZ);
  });

  test('bloggen och juridiken', async ({ page }) => {
    await page.goto('/sv/blog/');
    expect(await fills(page)).toEqual(['rgb(246, 239, 226)', 'rgb(246, 239, 226)']);
    await page.goto('/legal/privacy/');
    expect(await fills(page)).toEqual(['rgb(246, 239, 226)']);
    await page.goto('/sv/blog/why-birdy/');
    expect(await fills(page)).toEqual(['rgb(246, 239, 226)']);
  });
});

test.describe('delningsbilder', () => {
  const shares = {
    '/sv/': {
      alt: 'En rödhake i varmt ljus med orden Känn igen fågeln. Bevara stunden.',
      image: /\/og-field-sv\.png\?v=2$/,
    },
    '/': {
      alt: 'A European robin in warm light with the words Know the bird. Keep the moment.',
      image: /\/og-field-en\.png\?v=2$/,
    },
  } as const;
  for (const [path, { alt, image }] of Object.entries(shares)) {
    test(`delningsbildens alt-text på ${path}`, async ({ page }) => {
      await page.goto(path);
      await expect(page.locator('meta[property="og:image:alt"]')).toHaveAttribute('content', alt);
      await expect(page.locator('meta[name="twitter:image:alt"]')).toHaveAttribute('content', alt);
      await expect(page.locator('meta[property="og:image"]')).toHaveAttribute('content', image);
    });
  }
});
