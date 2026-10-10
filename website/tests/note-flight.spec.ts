import { test, expect, type Page } from '@playwright/test';

async function flightState(page: Page) {
  return page.evaluate(() => {
    const svg = document.querySelector('[data-note-flight] > svg.nflight');
    const boxes = [...document.querySelectorAll('[data-note-flight] .ncard-body, [data-note-flight] .ctitle')].map((e) => e.getBoundingClientRect());
    const birds = svg ? [...svg.querySelectorAll('use')].map((u) => u.getBoundingClientRect()) : [];
    const overText = birds.filter((b) => boxes.some((t) => b.left < t.right && t.left < b.right && b.top < t.bottom && t.top < b.bottom)).length;
    return { hidden: svg?.getAttribute('aria-hidden'), flights: svg ? svg.querySelectorAll('g.nf-flight').length : 0, birds: birds.length, overText };
  });
}

test.describe('flocken flyger vidare mellan bloggens bilder', () => {
  test.use({ reducedMotion: 'reduce' });

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

  test('bloggen på telefon: varje bild flyger upp in i bilden ovanför, bakom texten', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/blog/');
    // The layer is drawn after layout (a requestAnimationFrame past load), so wait for it before reading it.
    await expect(page.locator('[data-note-flight] > svg.nflight')).toHaveCount(1);
    const s = await flightState(page);
    expect(s.flights).toBe(2);
    expect(s.overText).toBe(0);
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
