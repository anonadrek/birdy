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
  test('tidslinjen visar lanseringsdatumet och en egen rad för Premium i Google Play senare, på båda språken', async ({ page }) => {
    await page.goto('/sv/premium/');
    await expect(page.locator('[data-timeline-release] .when')).toHaveText('15 oktober 2026');
    await expect(page.locator('[data-timeline-release] .what')).toHaveText('Birdy 1.3.');
    await expect(page.locator('.timeline li').nth(2).locator('.when')).toHaveText('Senare');
    await expect(page.locator('.timeline li').nth(2).locator('.what')).toHaveText('Premium i Google Play.');
    await page.goto('/premium/');
    await expect(page.locator('[data-timeline-release] .when')).toHaveText('15 October 2026');
    await expect(page.locator('[data-timeline-release] .what')).toHaveText('Birdy 1.3.');
    await expect(page.locator('.timeline li').nth(2).locator('.when')).toHaveText('Later');
    await expect(page.locator('.timeline li').nth(2).locator('.what')).toHaveText('Premium in Google Play.');
    // The 1.3 entry is the highlighted one (it carries the early users' promise); the "Premium in Google Play" row
    // and the iPhone row after it are both open, undated future rows and share the "soon" styling, never the key one.
    await expect(page.locator('.timeline li.key')).toHaveCount(1);
    await expect(page.locator('.timeline li.key')).toHaveAttribute('data-timeline-release', '');
    const soonEntries = page.locator('.timeline li.soon');
    await expect(soonEntries).toHaveCount(2);
    await expect(soonEntries.first()).not.toHaveClass(/\bkey\b/);
    await expect(soonEntries.last()).not.toHaveClass(/\bkey\b/);
  });

  // Birdy 1.3 ships with Premium free for everyone (review 2026-10-09): the early users' lead and the buy steps no
  // longer depend on the release day, so they read the same whether 1.3 is "soon" or already out (the fixture build's
  // date, 2026-10-15, is the live date; releaseDependentCopy, tests/unit/release.unit.mjs).
  test('löftet och köpstegen säger att Premium är fritt tills vidare, oavsett releasedag', async ({ page }) => {
    await page.goto('/sv/premium/');
    await expect(page.locator('[data-early-lead]')).toHaveText('Du behöver inte göra något. När Premium börjar kosta uppdaterar du appen, så möter tack-skärmen dig nästa gång du öppnar den.');
    await expect(page.locator('[data-buy-steps] li')).toHaveText(['Hämta Birdy från Google Play.', 'När Premium går att köpa: öppna det i appen, till exempel under Inställningar.', 'Välj ett år eller för alltid och betala i Google Play.']);
    await page.goto('/premium/');
    await expect(page.locator('[data-early-lead]')).toHaveText("You don't need to do anything. When Premium starts costing money, update the app, and the thank-you screen greets you the next time you open it.");
    await expect(page.locator('[data-buy-steps] li')).toHaveText(['Get Birdy from Google Play.', 'When Premium can be bought, open it in the app, for example under Settings.', 'Choose a year or for good and pay in Google Play.']);
  });

  // Albin's choice b (2026-10-08): the rule names the version, not a date, so it stays true wherever the release day lands.
  test('regeln för tidiga användare säger "använde Birdy före version 1.3", utan datum', async ({ page }) => {
    await page.goto('/sv/premium/');
    await expect(page.locator('[data-early-rule]')).toHaveText('Använde du Birdy före version 1.3 behåller du Premium gratis, så länge du har appen.');
    await expect(page.locator('body')).not.toContainText('17 oktober');
    await page.goto('/premium/');
    await expect(page.locator('[data-early-rule]')).toHaveText('If you used Birdy before version 1.3, you keep Premium for free, for as long as you have the app.');
    await expect(page.locator('body')).not.toContainText('17 October');
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
