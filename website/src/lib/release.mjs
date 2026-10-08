// The app release the website describes (plan 2026-10-08). The release checklist (Plan 3 Task 11 on release/1.3.0, and
// every app release after it) updates this file together with the frozen species snapshot it names.
import { resolve } from 'node:path';

/** The shipped app's version, which names the frozen species list the home page picks Dagens fågel from. */
export const APP_VERSION = '1.3.0';

/**
 * src/data/app-species-<version>.json: the shipped app's species list (id, abundance, iucn_status, season, regions),
 * written from tests/fixtures/daily-bird-golden.json by scripts/app-species-snapshot.mjs. Regenerated per app release.
 * @param {string} websiteRoot
 */
export const appSpeciesSnapshotPath = (websiteRoot) => resolve(websiteRoot, 'src', 'data', `app-species-${APP_VERSION}.json`);

/**
 * The day Birdy 1.3 goes out on Google Play (Europe/Stockholm). Two things on the site turn on it: the hero's "samma
 * fågel som i appen" line (before it, the phones run 1.2's old picker, so the app's bird is not the one the site
 * computes) and the Premium page's timeline entry for 1.3 ("kommer" until the day, the date from then on). The
 * release checklist flips it if go-live slips (plan "Att komma ihåg").
 */
export const APP_1_3_LIVE_FROM = '2026-10-15';

/** Whether the app this site describes is in people's phones on `iso` (YYYY-MM-DD, Europe/Stockholm). */
export const isAppLive = (iso) => iso >= APP_1_3_LIVE_FROM;

/**
 * APP_1_3_LIVE_FROM in words, "15 oktober 2026" or "15 October 2026".
 * @param {'sv' | 'en'} locale
 */
export function formatLiveDate(locale) {
  return new Intl.DateTimeFormat(locale === 'sv' ? 'sv-SE' : 'en-GB', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })
    .format(new Date(`${APP_1_3_LIVE_FROM}T00:00:00Z`));
}

/**
 * The Premium timeline's entry for Birdy 1.3 (copy premiumPage.situation.timeline[1]): before the day it says 1.3 is
 * coming (whenSoon, whatSoon, moreSoon), from the day on the date (when's {date}) and the released wording, so the
 * page never states a release that has not happened.
 * @param {{ when: string, what: string, more: string, whenSoon?: string, whatSoon?: string, moreSoon?: string }} entry
 * @param {string} dateIso today, Europe/Stockholm (buildDate in daily-bird.mjs)
 * @param {'sv' | 'en'} locale
 * @returns {{ when: string, what: string, more: string }}
 */
export function releaseTimelineEntry(entry, dateIso, locale) {
  if (isAppLive(dateIso)) return { when: entry.when.replace('{date}', formatLiveDate(locale)), what: entry.what, more: entry.more };
  return { when: entry.whenSoon, what: entry.whatSoon, more: entry.moreSoon };
}

/**
 * The Premium page's other two texts that describe 1.3 (review 2026-10-08): the early users' "update the app, and the
 * thank-you screen greets you" and the buy steps (open Premium in the app, pay in Google Play). Both are only true once
 * 1.3 is out, so before the day the page uses the copy's leadSoon and stepsSoon ("when Birdy 1.3 arrives in October").
 * @param {{ early: { lead: string, leadSoon: string }, buy: { steps: string[], stepsSoon: string[] } }} page copy premiumPage
 * @param {string} dateIso today, Europe/Stockholm (buildDate in daily-bird.mjs)
 * @returns {{ earlyLead: string, buySteps: string[] }}
 */
export function releaseDependentCopy(page, dateIso) {
  const live = isAppLive(dateIso);
  return { earlyLead: live ? page.early.lead : page.early.leadSoon, buySteps: live ? page.buy.steps : page.buy.stepsSoon };
}
