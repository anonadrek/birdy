import { test, expect, type Page } from '@playwright/test';

async function flightState(page: Page) {
  return page.evaluate(() => {
    const svg = document.querySelector('[data-note-flight] > svg.nflight');
    const boxes = [...document.querySelectorAll('[data-note-flight] .ncard-body')].map((e) => e.getBoundingClientRect());
    const birds = svg ? [...svg.querySelectorAll('use')].map((u) => u.getBoundingClientRect()) : [];
    const overText = birds.filter((b) => boxes.some((t) => b.left < t.right && t.left < b.right && b.top < t.bottom && t.top < b.bottom)).length;
    const width = document.documentElement.clientWidth;
    return {
      hidden: svg?.getAttribute('aria-hidden'),
      flights: svg ? svg.querySelectorAll('g.nf-flight').length : 0,
      perFlight: svg ? [...svg.querySelectorAll('g.nf-flight')].map((g) => g.querySelectorAll('use').length) : [],
      birds: birds.length,
      overText,
      outside: birds.filter((b) => b.left < 0 || b.right > width).length,
    };
  });
}

test.describe('flocken flyger vidare mellan bloggens bilder', () => {
  test.use({ contextOptions: { reducedMotion: 'reduce' } });

  test('bloggen på bred skärm: upp in i det stora kortet och åt höger in i nästa', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/blog/');
    await expect(page.locator('[data-note-flight] > svg.nflight')).toHaveCount(1);
    const s = await flightState(page);
    expect(s.hidden).toBe('true');
    expect(s.flights).toBe(2);
    expect(s.birds).toBeGreaterThanOrEqual(8);
    expect(s.overText).toBe(0);
  });

  for (const path of ['/sv/blog/', '/blog/', '/']) {
    test(`telefon ${path}: varje bild flyger uppför marginalen in i bilden ovanför, hel, inte över text och inte utanför sidan`, async ({ page }) => {
      await page.setViewportSize({ width: 390, height: 844 });
      await page.goto(path);
      if (path === '/') await page.locator('#field-notes').scrollIntoViewIfNeeded();
      // The layer is drawn after layout (a requestAnimationFrame past load), so wait for it before reading it.
      await expect(page.locator('[data-note-flight] > svg.nflight')).toHaveCount(1);
      const s = await flightState(page);
      expect(s.flights).toBe(2);
      for (const n of s.perFlight) expect(n).toBeGreaterThanOrEqual(6);
      expect(s.overText).toBe(0);
      expect(s.outside).toBe(0);
    });
  }

  test('telefonens adressfält som glider undan ritar inte om fåglarna', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/blog/');
    await expect(page.locator('[data-note-flight] > svg.nflight')).toHaveCount(1);
    await page.evaluate(() => { (document.querySelector('[data-note-flight] > svg.nflight') as SVGElement).dataset.seen = '1'; });
    await page.setViewportSize({ width: 390, height: 760 });
    await page.evaluate(() => new Promise((done) => requestAnimationFrame(() => requestAnimationFrame(done))));
    expect(await page.evaluate(() => (document.querySelector('[data-note-flight] > svg.nflight') as SVGElement | null)?.dataset.seen)).toBe('1');
  });

  test('startsidan: anteckningarna sida vid sida flyger åt höger', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/');
    await page.locator('#field-notes').scrollIntoViewIfNeeded();
    await expect(page.locator('[data-note-flight] > svg.nflight')).toHaveCount(1);
    const s = await flightState(page);
    expect(s.flights).toBe(2);
    expect(s.overText).toBe(0);
  });

  test('lagret tar inga klick: korten går att öppna genom fåglarna', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/blog/');
    await expect(page.locator('[data-note-flight] > svg.nflight')).toHaveCount(1);
    // One page.evaluate, not locator.evaluate: a late image load can legitimately redraw (remove and re-append) the
    // svg between resolving a locator's handle and reading it, which otherwise reads a detached node's empty style.
    const pe = await page.evaluate(() => {
      const svg = document.querySelector('[data-note-flight] > svg.nflight');
      return svg ? getComputedStyle(svg).pointerEvents : null;
    });
    expect(pe).toBe('none');
  });
});

test.describe('flocken flyger vidare med rörelse på', () => {
  test.use({ contextOptions: { reducedMotion: 'no-preference' } });

  test('startsidan: lagret följer korten dit de glider in, inte dit de var när det ritades', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/');
    await expect(page.locator('[data-note-flight] > svg.nflight')).toHaveCount(1);
    // Redraw the layer now, where the cards stand at this moment: the page a pixel narrower and back (the list keeps its
    // size at this width; the page's own width changing is what redraws it).
    const frames = () => page.evaluate(() => new Promise((done) => requestAnimationFrame(() => requestAnimationFrame(done))));
    const drawNow = async () => {
      await page.setViewportSize({ width: 1439, height: 900 });
      await frames();
      await page.setViewportSize({ width: 1440, height: 900 });
      await frames();
      return page.evaluate(() => [...document.querySelectorAll('[data-note-flight] > svg.nflight use')].map((u) => u.getAttribute('transform') ?? ''));
    };
    // Before the section is in view its cards wait 20px low for the reveal; once in view they slide into place.
    await expect.poll(() => page.evaluate(() => getComputedStyle(document.querySelector('#field-notes .cards [data-reveal]')!).transform)).toBe('matrix(1, 0, 0, 1, 0, 20)');
    const before = await drawNow();
    await page.locator('#field-notes').scrollIntoViewIfNeeded();
    await expect.poll(() => page.evaluate(() => [...document.querySelectorAll('#field-notes .cards [data-reveal]')].every((e) => getComputedStyle(e).transform === 'none'))).toBe(true);
    const after = await drawNow();
    expect(before.length).toBeGreaterThan(0);
    expect(after).toEqual(before);
  });
});
