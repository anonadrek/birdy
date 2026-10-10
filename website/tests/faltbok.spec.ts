import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { test, expect } from '@playwright/test';

// Webben i fältbokens färger (docs/superpowers/specs/2026-09-28-webb-faltboksfarger-design.md).
const ESPRESSO = 'rgb(42, 29, 23)';
const ESPRESSO_DEEP = 'rgb(30, 20, 16)';
// The home page's hero is peach paper since 2026-10-09 (spec 2026-10-09-startsidan-flocken-lyfter).
const PEACH = 'rgb(253, 229, 203)';
// The menu bar and the gallery wall are the light theme since 2026-10-10 (Albin: no brown bar, no brown wall).
const PAPER = 'rgb(246, 239, 226)';
const INK = 'rgb(48, 32, 25)';
const RUST = 'rgb(154, 69, 38)';

// Läs copy-texterna direkt så framtida ordbyten inte kräver testredigering (spec 2026-09-28 §fixrunda).
const contentDir = resolve(dirname(fileURLToPath(import.meta.url)), '../src/content');
const copy = {
  sv: JSON.parse(readFileSync(resolve(contentDir, 'copy.sv.json'), 'utf8')),
  en: JSON.parse(readFileSync(resolve(contentDir, 'copy.en.json'), 'utf8')),
} as const;

test.describe('espresso i stället för mossa', () => {
  test('hjälten på /sv/ är persikopapper och de mörka partierna espresso', async ({ page }) => {
    await page.goto('/sv/');
    await expect(page.locator('[data-hero]')).toHaveCSS('background-color', PEACH);
    await expect(page.locator('#download')).toHaveCSS('background-color', ESPRESSO);
    await expect(page.locator('footer.footer')).toHaveCSS('background-color', ESPRESSO_DEEP);
    const prem = await page.locator('#premium').evaluate((el) => getComputedStyle(el).backgroundImage);
    expect(prem).toContain(ESPRESSO);
    expect(prem).toContain(ESPRESSO_DEEP);
    // The browser bar starts as the hero's peach (Layout's themeColor) and turns paper with the solid menu bar.
    await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute('content', '#FDE5CB');
    await page.evaluate(() => window.scrollTo({ top: 3000, behavior: 'instant' }));
    await expect(page.locator('#site-nav')).toHaveClass(/is-solid/);
    await expect(page.locator('#site-nav')).toHaveCSS('background-color', PAPER);
    await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute('content', /^#f6efe2$/i);
  });

  test('brödtexten är varm brun', async ({ page }) => {
    await page.goto('/sv/');
    await expect(page.locator('body')).toHaveCSS('color', 'rgb(48, 32, 25)');
  });

  test('telefonerna i karusellen är riktiga skärmbilder ur appen, inga ritade', async ({ page }) => {
    await page.goto('/sv/');
    await expect(page.locator('#app .phone img')).toHaveCount(10);
    await expect(page.locator('#app .ph')).toHaveCount(0);
    for (const alt of await page.locator('#app .phone img').evaluateAll((imgs) => imgs.map((i) => i.getAttribute('alt') ?? ''))) {
      expect(alt.length).toBeGreaterThan(20);
    }
  });

  test('bloggens rubrikband är espresso', async ({ page }) => {
    await page.goto('/sv/blog/');
    await expect(page.locator('.bhead')).toHaveCSS('background-color', ESPRESSO);
  });
});

test.describe('galleriväggen bakom karusellen', () => {
  test('karusellen hänger på det ljusa persikopappret med mörk text och en fastnålad bildtext', async ({ page }) => {
    await page.goto('/sv/');
    const tour = page.locator('#app');
    await expect(tour).toHaveCSS('background-color', PEACH);
    const bg = await tour.evaluate((el) => getComputedStyle(el).backgroundImage);
    expect(bg).toContain('rgb(254, 235, 214)'); // --peach-hi, the pool of light behind the phones
    expect(bg).toContain('rgb(248, 214, 180)'); // --peach-lo at the edges
    expect(bg).not.toContain(ESPRESSO_DEEP);
    await expect(tour.locator('.tour-head .journal-headline')).toHaveCSS('color', INK);
    await expect(tour.locator('.cap')).toHaveCSS('background-color', 'rgb(255, 250, 241)');
    await expect(tour.locator('.plno b').first()).toHaveText('Pl. I');
    await expect(tour.locator('.plno b').first()).toHaveCSS('color', RUST);
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
    '/sv/': [copy.sv.monthBirds.note, copy.sv.tour.note, copy.sv.howItWorks.note, copy.sv.guide.note, copy.sv.premium.note, copy.sv.privacy.note, copy.sv.fieldNotes.note, copy.sv.faq.note, copy.sv.download.note],
    '/': [copy.en.monthBirds.note, copy.en.tour.note, copy.en.howItWorks.note, copy.en.guide.note, copy.en.premium.note, copy.en.privacy.note, copy.en.fieldNotes.note, copy.en.faq.note, copy.en.download.note],
  } as const;
  for (const [path, texts] of Object.entries(notes)) {
    test(`en handskriven rad under varje rubrik på ${path}`, async ({ page }) => {
      // Guard mot att copy-driven-jämförelsen passerar tomt om en nyckel saknas eller flyttas.
      for (const t of texts) {
        expect(typeof t).toBe('string');
        expect(t.length).toBeGreaterThan(0);
      }
      await page.goto(path);
      const mnotes = page.locator('.mnote');
      await expect(mnotes).toHaveText([...texts]);
      for (const el of await mnotes.all()) {
        await expect(el).toHaveCSS('font-family', /Caveat/);
        await expect(el).toHaveCSS('font-weight', '700');
      }
      // Peach on the last section's wall (mockup lift-c.html), apricot on the Premium band. The hero has no margin note
      // since 2026-10-09: nothing is written next to the flock.
      await expect(page.locator('[data-hero] .mnote')).toHaveCount(0);
      await expect(page.locator('#how-it-works .mnote')).toHaveCSS('color', 'rgb(154, 69, 38)');
      await expect(page.locator('#premium .mnote')).toHaveCSS('color', 'rgb(242, 178, 122)');
      await expect(page.locator('#download .mnote')).toHaveCSS('color', 'rgb(253, 229, 203)');
    });
  }
});

test.describe('bildtexter i handstil', () => {
  const stats = {
    '/sv/': copy.sv.guide.stats.map((s: { note: string }) => s.note),
    '/': copy.en.guide.stats.map((s: { note: string }) => s.note),
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
      ['#season > .deckle path', PEACH],                      // hero (persikopapper) → Fåglarna i oktober
      ['#app > .deckle path', 'rgb(246, 239, 226)'],          // Fåglarna i oktober → karusellen
      ['#how-it-works > .deckle path', PEACH],                // karusellen (persikopapper) → Tre sätt att fånga
      ['#guide > .deckle path', 'rgb(255, 250, 241)'],        // Fältboken → Uppslagsverket
      ['#premium > .deckle path', 'rgb(246, 239, 226)'],      // Uppslagsverket → Premium
      ['#privacy > .deckle path', 'rgb(30, 20, 16)'],         // Premium → Integritet
      ['#download > .deckle path', 'rgb(255, 250, 241)'],     // Frågor → Ta med Birdy
      ['footer.footer > .deckle path', 'rgb(42, 29, 23)'],    // Ta med Birdy → sidfot
    ];
    await expect(page.locator('.deckle')).toHaveCount(8);
    for (const [selector, expected] of edges) {
      await expect(page.locator(selector), selector).toHaveCSS('fill', expected);
    }
    const sealZ = await page.locator('#premium .pseal').evaluate((el) => Number(getComputedStyle(el).zIndex));
    const edgeZ = await page.locator('#premium .deckle').evaluate((el) => Number(getComputedStyle(el).zIndex));
    expect(sealZ).toBeGreaterThan(edgeZ);
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
      alt: copy.sv.alt.shareImage,
      image: /\/og-field-sv\.jpg\?v=4$/,
    },
    '/': {
      alt: copy.en.alt.shareImage,
      image: /\/og-field-en\.jpg\?v=4$/,
    },
  } as const;
  for (const [path, { alt, image }] of Object.entries(shares)) {
    test(`delningsbildens alt-text på ${path}`, async ({ page }) => {
      // toHaveAttribute('content', alt) skulle passera lika gärna om alt vore undefined
      // (då kollas bara att attributet finns) — säkra att copy-värdet faktiskt är text.
      expect(typeof alt).toBe('string');
      expect(alt.trim().length).toBeGreaterThan(0);
      await page.goto(path);
      await expect(page.locator('meta[property="og:image:alt"]')).toHaveAttribute('content', alt);
      await expect(page.locator('meta[name="twitter:image:alt"]')).toHaveAttribute('content', alt);
      await expect(page.locator('meta[property="og:image"]')).toHaveAttribute('content', image);
    });
  }
});
