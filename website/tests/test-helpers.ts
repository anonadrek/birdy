import type { Page } from '@playwright/test';

/**
 * Collect real console / page / network errors for a no-errors assertion,
 * ignoring the expected `/_vercel/insights/script.js` 404 that
 * `@vercel/analytics` injects. That script only resolves on Vercel itself, so
 * `astro preview` (local + CI) 404s it harmlessly — it must not fail tests.
 *
 * Network failures are detected via the `response` listener (it has the URL +
 * status), so the URL-less "Failed to load resource" console lines are dropped
 * as redundant. A genuine asset failure is still caught: any non-`/_vercel`
 * response with status >= 400 is recorded.
 */
export function trackConsoleErrors(page: Page): string[] {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  page.on('console', (msg) => {
    if (msg.type() !== 'error') return;
    if (/Failed to load resource/.test(msg.text())) return; // handled by the response listener
    errors.push(msg.text());
  });
  page.on('response', (resp) => {
    if (resp.status() >= 400 && !resp.url().includes('/_vercel/')) {
      errors.push(`${resp.status()} ${resp.url()}`);
    }
  });
  return errors;
}
