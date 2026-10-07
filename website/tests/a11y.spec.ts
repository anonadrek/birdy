import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

const path = process.env.AXE_PATH ?? 'sv/arter/';

test(`axe: /${path}`, async ({ page }) => {
  await page.goto(`/${path}`);
  const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations, JSON.stringify(results.violations, null, 2)).toEqual([]);
});
