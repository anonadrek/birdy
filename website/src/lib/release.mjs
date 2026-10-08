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
 * The day Birdy 1.3 goes out on Google Play (Europe/Stockholm, YYYY-MM-DD), or null while it has no date. Since
 * 2026-10-08 it has none: Albin waits for BirdNET's answer before payments are turned on, and 1.3 then ships together
 * with what was planned for 1.3.1. Three things on the site turn on it: the hero's "samma fågel som i appen" line
 * (before it, the phones run 1.2's old picker, so the app's bird is not the one the site computes), the Premium page's
 * timeline entry for 1.3 ("Härnäst" until the day, the date from then on) and the Premium page's early users' lead and
 * buy steps (releaseDependentCopy). While it is null none of them says that 1.3 is out. Set the date here once the
 * release day is decided (plan "Att komma ihåg"); the fixture build sets BIRDY_APP_LIVE_FROM so the tests still see a
 * live day.
 */
export const APP_1_3_LIVE_FROM = process.env.BIRDY_APP_LIVE_FROM || null;

/**
 * Whether the app this site describes is in people's phones on `iso` (YYYY-MM-DD, Europe/Stockholm). Never while the
 * release has no date.
 * @param {string} iso
 * @param {string | null} [liveFrom]
 */
export const isAppLive = (iso, liveFrom = APP_1_3_LIVE_FROM) => liveFrom != null && iso >= liveFrom;

/**
 * The release day in words, "15 oktober 2026" or "15 October 2026". Only called once the day has come.
 * @param {'sv' | 'en'} locale
 * @param {string | null} [liveFrom]
 */
export function formatLiveDate(locale, liveFrom = APP_1_3_LIVE_FROM) {
  if (liveFrom == null) throw new Error('formatLiveDate: Birdy 1.3 har inget releasedatum än (APP_1_3_LIVE_FROM)');
  return new Intl.DateTimeFormat(locale === 'sv' ? 'sv-SE' : 'en-GB', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })
    .format(new Date(`${liveFrom}T00:00:00Z`));
}

/**
 * The Premium timeline's entry for Birdy 1.3 (copy premiumPage.situation.timeline[1]): before the day it says 1.3 is
 * coming (whenSoon, whatSoon, moreSoon), from the day on the date (when's {date}) and the released wording, so the
 * page never states a release that has not happened.
 * @param {{ when: string, what: string, more: string, whenSoon?: string, whatSoon?: string, moreSoon?: string }} entry
 * @param {string} dateIso today, Europe/Stockholm (buildDate in daily-bird.mjs)
 * @param {'sv' | 'en'} locale
 * @param {string | null} [liveFrom]
 * @returns {{ when: string, what: string, more: string }}
 */
export function releaseTimelineEntry(entry, dateIso, locale, liveFrom = APP_1_3_LIVE_FROM) {
  if (isAppLive(dateIso, liveFrom)) {
    return { when: entry.when.replace('{date}', formatLiveDate(locale, liveFrom)), what: entry.what, more: entry.more };
  }
  return { when: entry.whenSoon, what: entry.whatSoon, more: entry.moreSoon };
}

/**
 * The Premium page's other two texts that describe 1.3 (review 2026-10-08): the early users' "update the app, and the
 * thank-you screen greets you" and the buy steps (open Premium in the app, pay in Google Play). Both are only true once
 * 1.3 is out, so before the day the page uses the copy's leadSoon and stepsSoon ("when Birdy 1.3 arrives").
 * @param {{ early: { lead: string, leadSoon: string }, buy: { steps: string[], stepsSoon: string[] } }} page copy premiumPage
 * @param {string} dateIso today, Europe/Stockholm (buildDate in daily-bird.mjs)
 * @param {string | null} [liveFrom]
 * @returns {{ earlyLead: string, buySteps: string[] }}
 */
export function releaseDependentCopy(page, dateIso, liveFrom = APP_1_3_LIVE_FROM) {
  const live = isAppLive(dateIso, liveFrom);
  return { earlyLead: live ? page.early.lead : page.early.leadSoon, buySteps: live ? page.buy.steps : page.buy.stepsSoon };
}
