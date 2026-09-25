import { readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { test, expect } from '@playwright/test';
import { trackConsoleErrors } from './test-helpers';

// Local (and CI) builds have no PUBLIC_MAPTILER_KEY, so CoverageMap.astro ships the static
// fallback only -- the whole `initMap` code path is dead-code-eliminated at build time (see
// scripts/build-coverage-geojson.mjs / CoverageMap.astro comments) and no maplibre-gl.*.js chunk
// exists in the output. A production build DOES carry a key, in which case the assertions below
// that assume the fallback-only path don't hold, so they're skipped instead of failing.
const distAstroDir = resolve(dirname(fileURLToPath(import.meta.url)), '../dist/_astro');
let hasMapKey = false;
try {
  hasMapKey = readdirSync(distAstroDir).some((f) => /^maplibre-gl\..*\.js$/.test(f));
} catch {
  hasMapKey = false; // dist/_astro missing (not built yet) -- treat as keyless
}

for (const { path, headline, stat } of [
  { path: '/', headline: 'pocket', stat: 'species' },
  { path: '/sv/', headline: 'fickan', stat: 'arter' },
]) {
  test(`guide section with coverage map renders on ${path}`, async ({ page }) => {
    test.skip(hasMapKey, 'build has a map key: live map instead of fallback');

    const consoleErrors = trackConsoleErrors(page);
    const requestUrls: string[] = [];
    page.on('request', (req) => requestUrls.push(req.url()));

    await page.goto(path);
    const section = page.locator('section#guide');
    await expect(section).toBeAttached();
    await expect(section.locator('h2')).toContainText(headline);
    await expect(section.locator('.stats li')).toHaveCount(3);
    await expect(section.locator('.stat-label').first()).toHaveText(stat);
    await expect(section.locator('[data-coverage-map]')).toBeAttached();

    // The live map is lazy AND key-gated: it only initialises when PUBLIC_MAPTILER_KEY is baked
    // into the build (production). CI and local builds have no key, so none of this can ever run
    // -- checked deterministically below instead of a fixed wait + a canvas-or-fallback branch.
    await section.locator('[data-coverage-map]').scrollIntoViewIfNeeded();

    await expect(section.locator('canvas.maplibregl-canvas')).toHaveCount(0);
    // Two independent guards: no maplibre-gl request at all, and no maplibre-gl stylesheet rule
    // landed in the document either (a rule could in principle appear without a request our
    // listener caught, e.g. an inline style -- checking document.styleSheets directly is the
    // ground truth for "did the library's CSS apply"). Our OWN `.maplibregl-canvas` positioning
    // rule (in CoverageMap.astro's scoped <style>) is expected and must not trip this check --
    // only the library's `.maplibregl-map` selector (which we never write ourselves) counts.
    expect(requestUrls.some((u) => u.includes('maplibre-gl'))).toBe(false);
    const maplibreSelectors = await page.evaluate(() => {
      const found: string[] = [];
      for (const sheet of Array.from(document.styleSheets)) {
        let rules: CSSRuleList;
        try {
          rules = sheet.cssRules;
        } catch {
          continue; // cross-origin sheet (e.g. Google Fonts) -- can't read its rules, not ours anyway
        }
        for (const rule of Array.from(rules)) {
          const selectorText = (rule as CSSStyleRule).selectorText;
          if (selectorText?.includes('.maplibregl-map')) found.push(selectorText);
        }
      }
      return found;
    });
    expect(maplibreSelectors, `unexpected maplibre-gl rules: ${JSON.stringify(maplibreSelectors)}`).toEqual([]);

    const fallback = section.locator('img.fallback');
    await expect(fallback).toBeVisible();
    await expect.poll(() => fallback.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBe(1600);

    await expect(section.locator('.attrib')).toBeHidden();

    expect(consoleErrors, `Console errors: ${consoleErrors.join('\n')}`).toEqual([]);
  });
}

test.describe('guide stats count-up', () => {
  for (const { path, sr } of [
    { path: '/', sr: ['839 species', '34 badges', '0 accounts'] },
    { path: '/sv/', sr: ['839 arter', '34 märken', '0 konton'] },
  ]) {
    test(`stats reach their final values on ${path}`, async ({ page }) => {
      await page.goto(path);

      // Installed BEFORE scrolling: records every value the first stat's <b> passes through, so
      // we can prove the count-up actually animated rather than jumping straight to the final
      // number (a frozen/instant count-up would still satisfy the plain "ends at 839/34/0" poll
      // below, so that alone can't tell a real animation from a no-op).
      await page.evaluate(() => {
        (window as unknown as { __counts: string[] }).__counts = [];
        const stats = document.querySelector('#guide .stats');
        const obs = new MutationObserver(() => {
          const b = document.querySelector('#guide .stats b');
          if (b) (window as unknown as { __counts: string[] }).__counts.push(b.textContent ?? '');
        });
        if (stats) obs.observe(stats, { childList: true, characterData: true, subtree: true });
      });

      await page.locator('#guide .stats').scrollIntoViewIfNeeded();
      // Deliberately NOT an active poll here (confirmed empirically): issuing any page query --
      // Locator calls, page.evaluate, even expect.poll's own underlying checks -- while this
      // requestAnimationFrame-driven count-up is in flight makes headless Chrome collapse the
      // whole animation into a single no-op frame (the very first tick ends up with progress>=1,
      // and since that already matches the server-rendered starting text, no DOM mutation ever
      // fires). Waiting out the known ~1100ms animation undisturbed, THEN querying, is what
      // reliably captures the intermediate frames below; a plain expect.poll immediately after
      // scrollIntoViewIfNeeded reproducibly recorded zero mutations across repeated runs.
      await page.waitForTimeout(1500);
      await expect.poll(() => page.locator('#guide .stats b').allTextContents()).toEqual(['839', '34', '0']);
      await expect(page.locator('#guide .stats .sr-only')).toHaveText(sr);

      const counts = await page.evaluate(() => (window as unknown as { __counts: string[] }).__counts);
      const sawIntermediateValue = counts.some((v) => Number(v) < 839);
      expect(sawIntermediateValue, `expected an intermediate (non-final) value during count-up, observed: ${JSON.stringify(counts)}`).toBe(true);
    });
  }
});

test.describe('guide stats, reduced motion', () => {
  test.use({ contextOptions: { reducedMotion: 'reduce' } });

  test('stats show their final values immediately, without scrolling', async ({ page }) => {
    await page.goto('/');

    await page.evaluate(() => {
      (window as unknown as { __counts: string[] }).__counts = [];
      const stats = document.querySelector('#guide .stats');
      const obs = new MutationObserver(() => {
        const b = document.querySelector('#guide .stats b');
        if (b) (window as unknown as { __counts: string[] }).__counts.push(b.textContent ?? '');
      });
      if (stats) obs.observe(stats, { childList: true, characterData: true, subtree: true });
    });

    await page.locator('#guide .stats').scrollIntoViewIfNeeded();
    // A fixed wait is justified here: the test asserts that NOTHING changes over time, which
    // `expect.poll` can't express (it stops as soon as a condition first holds, so it wouldn't
    // notice a mutation that fires shortly after).
    await page.waitForTimeout(1200);

    await expect.poll(() => page.locator('#guide .stats b').allTextContents()).toEqual(['839', '34', '0']);
    const counts = await page.evaluate(() => (window as unknown as { __counts: string[] }).__counts);
    expect(counts, `expected no count-up mutations under reduced motion, observed: ${JSON.stringify(counts)}`).toEqual([]);
  });
});
