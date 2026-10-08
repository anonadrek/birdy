import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { APP_1_3_LIVE_FROM, formatLiveDate, isAppLive, releaseTimelineEntry } from '../../src/lib/release.mjs';

const copy = {
  sv: JSON.parse(readFileSync(new URL('../../src/content/copy.sv.json', import.meta.url), 'utf8')),
  en: JSON.parse(readFileSync(new URL('../../src/content/copy.en.json', import.meta.url), 'utf8')),
};

test('APP_1_3_LIVE_FROM: ett datum som YYYY-MM-DD, och isAppLive vänder exakt den dagen', () => {
  assert.match(APP_1_3_LIVE_FROM, /^\d{4}-\d{2}-\d{2}$/);
  assert.equal(isAppLive('2026-10-14'), false);
  assert.equal(isAppLive(APP_1_3_LIVE_FROM), true);
  assert.equal(isAppLive('2027-01-01'), true);
});

test('formatLiveDate: datumet i ord på båda språken', () => {
  assert.equal(formatLiveDate('sv'), '15 oktober 2026');
  assert.equal(formatLiveDate('en'), '15 October 2026');
});

test('Premium-tidslinjen: "kommer" före dagen, datumet från och med dagen, på båda språken', () => {
  for (const locale of ['sv', 'en']) {
    const entry = copy[locale].premiumPage.situation.timeline[1];
    // The date is injected from the constant, never typed into the copy.
    assert.equal(entry.when, '{date}');
    assert.ok(entry.whenSoon && entry.whatSoon && entry.moreSoon, locale);
    const before = releaseTimelineEntry(entry, '2026-10-14', locale);
    assert.deepEqual(before, { when: entry.whenSoon, what: entry.whatSoon, more: entry.moreSoon });
    assert.doesNotMatch(before.when + before.what + before.more, /15/);
    const after = releaseTimelineEntry(entry, APP_1_3_LIVE_FROM, locale);
    assert.deepEqual(after, { when: formatLiveDate(locale), what: entry.what, more: entry.more });
  }
  assert.equal(releaseTimelineEntry(copy.sv.premiumPage.situation.timeline[1], '2026-10-15', 'sv').when, '15 oktober 2026');
  assert.equal(releaseTimelineEntry(copy.en.premiumPage.situation.timeline[1], '2026-11-01', 'en').when, '15 October 2026');
});
