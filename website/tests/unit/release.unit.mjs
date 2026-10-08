import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { APP_1_3_LIVE_FROM, formatLiveDate, isAppLive, releaseDependentCopy, releaseTimelineEntry } from '../../src/lib/release.mjs';

const copy = {
  sv: JSON.parse(readFileSync(new URL('../../src/content/copy.sv.json', import.meta.url), 'utf8')),
  en: JSON.parse(readFileSync(new URL('../../src/content/copy.en.json', import.meta.url), 'utf8')),
};

// A release day for the tests; the site's own APP_1_3_LIVE_FROM has no date while Albin waits for BirdNET (2026-10-08).
const LIVE = '2026-10-15';

test('APP_1_3_LIVE_FROM: ett datum som YYYY-MM-DD eller inget datum alls', () => {
  if (APP_1_3_LIVE_FROM !== null) assert.match(APP_1_3_LIVE_FROM, /^\d{4}-\d{2}-\d{2}$/);
});

test('isAppLive: vänder exakt releasedagen, och aldrig utan datum', () => {
  assert.equal(isAppLive('2026-10-14', LIVE), false);
  assert.equal(isAppLive(LIVE, LIVE), true);
  assert.equal(isAppLive('2027-01-01', LIVE), true);
  for (const day of ['2026-10-15', '2027-01-01', '2099-12-31']) assert.equal(isAppLive(day, null), false, day);
});

test('formatLiveDate: datumet i ord på båda språken, fel utan datum', () => {
  assert.equal(formatLiveDate('sv', LIVE), '15 oktober 2026');
  assert.equal(formatLiveDate('en', LIVE), '15 October 2026');
  assert.throws(() => formatLiveDate('sv', null));
});

test('Premium-tidslinjen: "Härnäst" före dagen och utan datum, datumet från och med dagen, på båda språken', () => {
  for (const locale of ['sv', 'en']) {
    const entry = copy[locale].premiumPage.situation.timeline[1];
    // The date is injected from release.mjs, never typed into the copy, and the soon wording names no month.
    assert.equal(entry.when, '{date}');
    assert.ok(entry.whenSoon && entry.whatSoon && entry.moreSoon, locale);
    const soon = { when: entry.whenSoon, what: entry.whatSoon, more: entry.moreSoon };
    assert.deepEqual(releaseTimelineEntry(entry, '2026-10-14', locale, LIVE), soon);
    assert.deepEqual(releaseTimelineEntry(entry, '2027-06-01', locale, null), soon);
    assert.doesNotMatch(entry.whenSoon + entry.whatSoon + entry.moreSoon, /oktober|october|november|december|2026/i);
    assert.deepEqual(releaseTimelineEntry(entry, LIVE, locale, LIVE), { when: formatLiveDate(locale, LIVE), what: entry.what, more: entry.more });
  }
  assert.equal(releaseTimelineEntry(copy.en.premiumPage.situation.timeline[1], '2026-11-01', 'en', LIVE).when, '15 October 2026');
});

test('Premium-sidans löfte och köpsteg: 1.3-orden först från dagen, före den och utan datum "när Birdy 1.3 kommer"', () => {
  for (const locale of ['sv', 'en']) {
    const page = copy[locale].premiumPage;
    assert.equal(page.buy.stepsSoon.length, page.buy.steps.length, locale);
    for (const before of [releaseDependentCopy(page, '2026-10-14', LIVE), releaseDependentCopy(page, '2027-06-01', null)]) {
      assert.equal(before.earlyLead, page.early.leadSoon);
      assert.deepEqual(before.buySteps, page.buy.stepsSoon);
      // Before the day neither text may tell people to update to, or buy in, an app version that is not out yet,
      // and neither names a month the release may slip past.
      assert.match(before.earlyLead, /1\.3/);
      assert.ok(before.buySteps.some((step) => /1\.3/.test(step)), locale);
      assert.doesNotMatch(before.earlyLead, /^(Du behöver inte göra något\. Uppdatera appen|You don't need to do anything\. Update the app)/);
      assert.doesNotMatch(before.earlyLead + before.buySteps.join(' '), /oktober|october/i);
    }
    const after = releaseDependentCopy(page, LIVE, LIVE);
    assert.equal(after.earlyLead, page.early.lead);
    assert.deepEqual(after.buySteps, page.buy.steps);
  }
});

test('regeln för tidiga användare: ingen datumgräns i texten, "använde Birdy före version 1.3" (Albins val b, 2026-10-08)', () => {
  for (const locale of ['sv', 'en']) {
    const { early, situation } = copy[locale].premiumPage;
    const texts = [early.bigMark, ...situation.timeline.flatMap((e) => [e.when, e.what, e.more, e.whenSoon ?? '', e.moreSoon ?? ''])];
    for (const text of texts) assert.doesNotMatch(text, /17 (oktober|october)/i, `${locale}: ${text}`);
  }
  assert.match(copy.sv.premiumPage.early.bigMark, /^Använde du Birdy före version 1\.3 /);
  assert.match(copy.en.premiumPage.early.bigMark, /^If you used Birdy before version 1\.3, /);
});
