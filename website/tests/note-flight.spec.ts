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
    // One flight between each pair of cards actually on the page, not a count tied to today's three posts.
    const cards = await page.locator('[data-note-flight] [data-flight-art]').count();
    const s = await flightState(page);
    expect(s.hidden).toBe('true');
    expect(s.flights).toBe(cards - 1);
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
      // One flight between each pair of cards actually on the page, not a count tied to today's three posts.
      const cards = await page.locator('[data-note-flight] [data-flight-art]').count();
      const s = await flightState(page);
      expect(s.flights).toBe(cards - 1);
      for (const n of s.perFlight) expect(n).toBeGreaterThanOrEqual(6);
      expect(s.overText).toBe(0);
      expect(s.outside).toBe(0);
    });
  }

  test('telefonens adressfält som glider undan ritar inte om fåglarna', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/blog/');
    await expect(page.locator('[data-note-flight] > svg.nflight')).toHaveCount(1);
    // Let any redraw still pending from load settle (ResizeObserver's first callback, fonts.ready, an image load)
    // before marking the svg, or the mark can land on a node a trailing redraw is about to replace.
    await page.evaluate(async () => {
      await document.fonts.ready;
      await Promise.all([...document.querySelectorAll<HTMLImageElement>('[data-flight-art] img')]
        .map((i) => (i.complete ? null : new Promise((r) => i.addEventListener('load', r, { once: true })))));
      await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
    });
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
    const result = await page.evaluate(() => {
      const svg = document.querySelector('[data-note-flight] > svg.nflight');
      const pointerEvents = svg ? getComputedStyle(svg).pointerEvents : null;
      const pics = [...document.querySelectorAll<HTMLElement>('[data-note-flight] [data-flight-art]')].map((a) => a.getBoundingClientRect());
      const uses = svg ? [...svg.querySelectorAll('use')] : [];
      // A bird drawn over a card's own picture: the point at its centre must land on the card's link under the
      // (unclickable) svg layer, not on the svg itself, proving the layer really passes clicks through rather than
      // merely saying so in its own computed style.
      const over = uses.map((u) => u.getBoundingClientRect()).find((b) => {
        const cx = b.left + b.width / 2;
        const cy = b.top + b.height / 2;
        return pics.some((p) => cx >= p.left && cx <= p.right && cy >= p.top && cy <= p.bottom);
      });
      const hit = over ? document.elementFromPoint(over.left + over.width / 2, over.top + over.height / 2) : null;
      return { pointerEvents, foundBirdOverPicture: !!over, onLink: !!hit?.closest('a.ncard') };
    });
    expect(result.pointerEvents).toBe('none');
    expect(result.foundBirdOverPicture).toBe(true);
    expect(result.onLink).toBe(true);
  });

  test('ett kort utan flygdata hoppas inte över: inget flyg når det första kortet från det tredje', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/blog/');
    await expect(page.locator('[data-note-flight] > svg.nflight')).toHaveCount(1);
    // As if the middle picture had no entry in note-art.json: strip its flight data entirely (all four attributes
    // come from the same lookup in NoteCard.astro, never just one), then force a redraw the way the "adressfältet"
    // test above does, by changing the viewport's width and back.
    const firstBox = await page.evaluate(() => {
      const mid = [...document.querySelectorAll<HTMLElement>('[data-note-flight] [data-flight-art]')][1];
      delete mid.dataset.flightArt;
      delete mid.dataset.exit;
      delete mid.dataset.land;
      delete mid.dataset.avoid;
      const r = document.querySelectorAll<HTMLElement>('[data-note-flight] .ncard-img')[0].getBoundingClientRect();
      return { left: r.left, top: r.top, right: r.right, bottom: r.bottom };
    });
    await page.setViewportSize({ width: 389, height: 844 });
    await page.evaluate(() => new Promise((done) => requestAnimationFrame(() => requestAnimationFrame(done))));
    await page.setViewportSize({ width: 390, height: 844 });
    await page.evaluate(() => new Promise((done) => requestAnimationFrame(() => requestAnimationFrame(done))));
    const touchesFirst = await page.evaluate((box) => {
      const svg = document.querySelector('[data-note-flight] > svg.nflight');
      if (!svg) return false;
      return [...svg.querySelectorAll('use')].some((u) => {
        const r = u.getBoundingClientRect();
        return r.left < box.right && box.left < r.right && r.top < box.bottom && box.top < r.bottom;
      });
    }, firstBox);
    expect(touchesFirst).toBe(false);
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

  test('bloggen: fåglarna tonar in trots att sidan saknar data-reveal', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/blog/');
    await expect(page.locator('[data-note-flight] > svg.nflight')).toHaveCount(1);
    // NoteFlight owns its own motion gate: the blog index has no [data-reveal], so Layout never adds
    // html.motion-ready there, and the fade-in used to never play on this page at all.
    await expect(page.locator('[data-note-flight]')).toHaveClass(/nflight-motion/);
    await expect.poll(
      () => page.evaluate(() => getComputedStyle(document.querySelector('[data-note-flight] > svg.nflight use')!).opacity),
      { timeout: 8000 },
    ).toBe('1');
  });
});
