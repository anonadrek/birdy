import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { trackConsoleErrors } from './test-helpers';

// The Premium page (plan 2026-10-08 Task 6, mockups premium-a.html and premium-a-en.html).
const PRICE = /\d[\d\s.,]*(?:kr(?:onor)?|sek|eur|usd|:-)(?![\p{L}\p{N}])|(?<![\p{L}\p{N}])(?:kr|sek|eur|usd)\s?\d|[€$£]/iu;

test.describe('Premium-sidan', () => {
  for (const [path, other, h1, crumb, audio] of [
    ['/sv/premium/', '/premium/', 'Hela året som fältornitolog.', 'Premium', 'Ljud-ID är gratis för alla, alltid.'],
    ['/premium/', '/sv/premium/', 'A whole year as a field birder.', 'Premium', 'Audio ID is free for everyone, always.'],
  ] as const) {
    test(`${path} svarar, har rätt rubrik, hreflang och strukturerad data`, async ({ page }) => {
      const errors = trackConsoleErrors(page);
      const res = await page.goto(path);
      expect(res?.status()).toBe(200);
      await expect(page.locator('h1')).toHaveText(h1);
      await expect(page.locator('h1')).toHaveCount(1);
      await expect(page.locator('[data-crumb]').last()).toHaveText(crumb);
      await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', `https://birdy.community${path}`);
      await expect(page.locator(`link[rel="alternate"][hreflang="${path.startsWith('/sv/') ? 'en' : 'sv'}"]`)).toHaveAttribute('href', `https://birdy.community${other}`);
      await expect(page.locator('link[rel="alternate"][hreflang="x-default"]')).toHaveAttribute('href', 'https://birdy.community/premium/');
      await expect(page.locator('meta[property="og:image"]')).toHaveAttribute('content', new RegExp(`/og-premium-${path.startsWith('/sv/') ? 'sv' : 'en'}\\.jpg\\?v=\\d+$`));
      const graph = JSON.parse((await page.locator('script[type="application/ld+json"]').textContent()) ?? '{}')['@graph'] as { '@type': string }[];
      const types = graph.map((n) => n['@type']);
      expect(types).toContain('WebPage');
      expect(types).toContain('BreadcrumbList');
      expect(types).not.toContain('Offer');
      expect(types).not.toContain('Product');
      await expect(page.locator('[data-audio-free] b')).toHaveText(audio);
      // Premium is the map, the PDF, the season statistics and the badges; never audio ID.
      await expect(page.locator('[data-feature] h3')).toHaveCount(4);
      await expect(page.locator('[data-feature] h3')).not.toContainText([/ljud|sound|audio/i]);
      // The map's screen waits for its new style: a note, no picture.
      await expect(page.locator('[data-map-placeholder] img')).toHaveCount(0);
      expect(errors).toEqual([]);
    });
  }

  test('priserna står bara på den svenska sidan', async ({ page }) => {
    await page.goto('/sv/premium/');
    await expect(page.locator('[data-price-card] .amt')).toHaveText([/^199 kr/, /^499 kr/]);
    await expect(page.locator('main')).toContainText('inkluderar moms');
    await page.goto('/premium/');
    await expect(page.locator('[data-price-card]')).toHaveCount(2);
    await expect(page.locator('main')).not.toContainText(PRICE);
    await expect(page.locator('main')).not.toContainText(/\b(199|499)\b/);
    await expect(page.locator('main')).toContainText('in your currency');
  });

  // The fixture build's date (2026-10-15) is the live date, so the timeline shows the date, injected from release.mjs.
  test('tidslinjen visar lanseringsdatumet från release.mjs på båda språken', async ({ page }) => {
    await page.goto('/sv/premium/');
    await expect(page.locator('[data-timeline-release] .when')).toHaveText('15 oktober 2026');
    await expect(page.locator('[data-timeline-release] .what')).toHaveText('Birdy 1.3.');
    await page.goto('/premium/');
    await expect(page.locator('[data-timeline-release] .when')).toHaveText('15 October 2026');
    await expect(page.locator('[data-timeline-release] .what')).toHaveText('Birdy 1.3.');
  });

  // On the live date (the fixture build's 2026-10-15) the early users' lead and the buy steps are 1.3's own wording;
  // before it they say 1.3 is coming (releaseDependentCopy, tests/unit/release.unit.mjs; review 2026-10-08).
  test('löftet och köpstegen har 1.3-orden från lanseringsdagen', async ({ page }) => {
    await page.goto('/sv/premium/');
    await expect(page.locator('[data-early-lead]')).toHaveText('Du behöver inte göra något. Uppdatera appen, så möter tack-skärmen dig nästa gång du öppnar den.');
    await expect(page.locator('[data-buy-steps] li')).toHaveText(['Hämta Birdy från Google Play.', 'Öppna Premium i appen, till exempel under Inställningar.', 'Välj ett år eller för alltid och betala i Google Play.']);
    await page.goto('/premium/');
    await expect(page.locator('[data-early-lead]')).toHaveText("You don't need to do anything. Update the app, and the thank-you screen greets you the next time you open it.");
    await expect(page.locator('[data-buy-steps] li')).toHaveText(['Get Birdy from Google Play.', 'Open Premium in the app, for example under Settings.', 'Choose a year or for good and pay in Google Play.']);
  });

  test('regeln för tidiga användare säger "började använda" och tiden', async ({ page }) => {
    await page.goto('/sv/premium/');
    await expect(page.locator('[data-early-rule]')).toContainText('Började du använda Birdy före 17 oktober 2026 klockan 00.00 (svensk tid)');
    await page.goto('/premium/');
    await expect(page.locator('[data-early-rule]')).toContainText('started using Birdy before 17 October 2026, 00:00 Swedish time');
  });

  test('Se priserna hoppar till priserna', async ({ page }) => {
    await page.goto('/sv/premium/');
    await page.locator('.btn-brass').click();
    await expect(page).toHaveURL(/#priser$/);
    await expect(page.locator('#priser h2')).toBeInViewport();
  });

  for (const [home, target] of [['/sv/', '/sv/premium/'], ['/', '/premium/']] as const) {
    test(`startsidans Premium-band, menyn, frågan om pris och sidfoten länkar till ${target}`, async ({ page }) => {
      await page.setViewportSize({ width: 1280, height: 800 });
      await page.goto(home);
      await expect(page.locator('#premium [data-premium-link]')).toHaveAttribute('href', target);
      await expect(page.locator('#faq [data-faq-link]')).toHaveAttribute('href', target);
      await expect(page.locator(`footer a[href="${target}"]`)).toHaveCount(1);
      await page.locator(`#site-nav .links a[href="${target}"]`).click();
      await expect(page).toHaveURL(new RegExp(`${target}$`));
      await expect(page.locator(`#site-nav .links a[href="${target}"]`)).toHaveAttribute('aria-current', 'page');
      await page.goto(home);
      await page.locator('#premium [data-premium-link]').click();
      await expect(page).toHaveURL(new RegExp(`${target}$`));
    });
  }

  for (const width of [320, 390]) {
    test(`inget sidledes scroll på ${width} px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      for (const path of ['/sv/premium/', '/premium/']) {
        await page.goto(path);
        expect(await page.evaluate(() => document.documentElement.scrollWidth), path).toBeLessThanOrEqual(width);
      }
    });
  }
});

test.describe('axe på startsidan och Premium-sidan', () => {
  // Reduced motion, so axe reads the sections in their final colours and not halfway through a reveal.
  test.use({ contextOptions: { reducedMotion: 'reduce' } });
  for (const path of ['/', '/sv/', '/premium/', '/sv/premium/']) {
    for (const width of [390, 1440]) {
      test(`axe: ${path} i ${width} px`, async ({ page }) => {
        await page.setViewportSize({ width, height: 900 });
        await page.goto(path);
        const results = await new AxeBuilder({ page }).analyze();
        expect(results.violations, JSON.stringify(results.violations, null, 2)).toEqual([]);
      });
    }
  }
});
