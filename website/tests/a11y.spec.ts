import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

// One page or several, comma-separated: AXE_PATH="sv/arter/talgoxe/,species/great-tit/" (the publish loop
// checks both language versions of the page it just built, Task 16).
const paths = (process.env.AXE_PATH ?? 'sv/arter/').split(',').map((p) => p.trim()).filter(Boolean);

// Reduced motion: axe then reads every section in its final colours, never halfway through a scroll reveal
// (the home and Premium pages fade their sections in, and a half-faded kicker reads as low contrast).
test.use({ contextOptions: { reducedMotion: 'reduce' } });

for (const path of paths) {
  test(`axe: /${path}`, async ({ page }) => {
    await page.goto(`/${path}`);
    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations, JSON.stringify(results.violations, null, 2)).toEqual([]);
  });
}
