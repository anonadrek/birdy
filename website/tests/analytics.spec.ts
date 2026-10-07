import { test, expect } from '@playwright/test';

// Own visits (spec 2026-09-25 §15): ?va-ignore=1 marks the browser, ?va-ignore=0 removes the mark.
test('filtret för egna besök sätts och tas bort med en adressparameter, och adressen städas', async ({ page }) => {
  await page.goto('/?va-ignore=1');
  expect(await page.evaluate(() => localStorage.getItem('birdy-va-ignore'))).toBe('1');
  expect(new URL(page.url()).searchParams.has('va-ignore')).toBe(false);
  expect(await page.evaluate(() => typeof (window as unknown as { webAnalyticsBeforeSend?: unknown }).webAnalyticsBeforeSend)).toBe('function');
  expect(await page.evaluate(() => (window as unknown as { webAnalyticsBeforeSend: (e: unknown) => unknown }).webAnalyticsBeforeSend({ type: 'pageview', url: location.href }))).toBeNull();

  await page.goto('/sv/');
  expect(await page.evaluate(() => typeof (window as unknown as { webAnalyticsBeforeSend?: unknown }).webAnalyticsBeforeSend)).toBe('function');

  await page.goto('/?va-ignore=0');
  expect(await page.evaluate(() => localStorage.getItem('birdy-va-ignore'))).toBeNull();
  expect(new URL(page.url()).searchParams.has('va-ignore')).toBe(false);
  await page.goto('/sv/');
  expect(await page.evaluate(() => typeof (window as unknown as { webAnalyticsBeforeSend?: unknown }).webAnalyticsBeforeSend)).toBe('undefined');
});

test('flaggade sidor lämnar beforeSend först i Vercels analyticskö, oflaggade sidor lämnar ingen', async ({ page }) => {
  await page.goto('/?va-ignore=1');
  await page.goto('/sv/');
  const flaggedQueue = await page.evaluate(() => (window as unknown as { vaq?: unknown[][] }).vaq ?? []);
  expect(flaggedQueue.length).toBeGreaterThan(0);
  expect(flaggedQueue[0]?.[0]).toBe('beforeSend');

  await page.goto('/?va-ignore=0');
  await page.goto('/sv/');
  const unflaggedQueue = await page.evaluate(() => (window as unknown as { vaq?: unknown[][] }).vaq ?? []);
  expect(unflaggedQueue.some((entry) => entry[0] === 'beforeSend')).toBe(false);
});

test('en blockerad localStorage ger varken sidfel eller en aktiv beforeSend-hook', async ({ page }) => {
  const errors: Error[] = [];
  page.on('pageerror', (error) => errors.push(error));
  await page.addInitScript(() => {
    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      get() {
        throw new Error('storage blocked (private mode)');
      },
    });
  });

  await page.goto('/sv/');

  expect(errors).toEqual([]);
  expect(await page.evaluate(() => typeof (window as unknown as { webAnalyticsBeforeSend?: unknown }).webAnalyticsBeforeSend)).toBe('undefined');
});
