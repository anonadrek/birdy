import { test, expect } from '@playwright/test';

// Own visits (spec 2026-09-25 §15): ?va-ignore=1 marks the browser, ?va-ignore=0 removes the mark.
test('filtret för egna besök sätts och tas bort med en adressparameter', async ({ page }) => {
  await page.goto('/?va-ignore=1');
  expect(await page.evaluate(() => localStorage.getItem('birdy-va-ignore'))).toBe('1');
  expect(await page.evaluate(() => typeof (window as unknown as { webAnalyticsBeforeSend?: unknown }).webAnalyticsBeforeSend)).toBe('function');
  expect(await page.evaluate(() => (window as unknown as { webAnalyticsBeforeSend: (e: unknown) => unknown }).webAnalyticsBeforeSend({ type: 'pageview', url: location.href }))).toBeNull();

  await page.goto('/sv/');
  expect(await page.evaluate(() => typeof (window as unknown as { webAnalyticsBeforeSend?: unknown }).webAnalyticsBeforeSend)).toBe('function');

  await page.goto('/?va-ignore=0');
  expect(await page.evaluate(() => localStorage.getItem('birdy-va-ignore'))).toBeNull();
  await page.goto('/sv/');
  expect(await page.evaluate(() => typeof (window as unknown as { webAnalyticsBeforeSend?: unknown }).webAnalyticsBeforeSend)).toBe('undefined');
});
