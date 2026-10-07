import { test } from 'node:test';
import assert from 'node:assert/strict';
import { orderForSchedule, scheduleRows, stockholmOffset, toCsv, addDays, isValidIsoDate } from '../lib/schedule.mjs';
import { record } from './fixtures.mjs';

const sp = (en, reports, publish = false) => record({ qid: `Q${en.length}${reports}`, names: { en, sv: en, scientific: en }, data: { totalReports: reports }, publish });

test('published species come first, then the most reported', () => {
  const list = [sp('Bearded Reedling', 63138), sp('Great Cormorant', 370197), sp('Lapland Longspur', 16756, true), sp('Common Wood Pigeon', 563738), sp('Northern Shoveler', 187896, true)];
  assert.deepEqual(orderForSchedule(list).map((r) => r.names.en), ['Northern Shoveler', 'Lapland Longspur', 'Common Wood Pigeon', 'Great Cormorant', 'Bearded Reedling']);
});

test('ties on report count fall back to the English name; missing counts sort last', () => {
  const list = [sp('Osprey', 5), sp('Barred Warbler', 5), { ...sp('Woodcock', 0), data: undefined }];
  assert.deepEqual(orderForSchedule(list).map((r) => r.names.en), ['Barred Warbler', 'Osprey', 'Woodcock']);
});

test('ordering does not mutate the input', () => {
  const list = [sp('B', 1), sp('A', 2)];
  orderForSchedule(list);
  assert.deepEqual(list.map((r) => r.names.en), ['B', 'A']);
});

test('one post per day from the start date at 08:00 Stockholm, summer time ends 25 October', () => {
  const items = Array.from({ length: 18 }, (_, i) => ({ species: `s${i}` }));
  const rows = scheduleRows(items, { start: '2026-10-09' });
  assert.equal(rows[0].date, '2026-10-09');
  assert.equal(rows[0].datetime, '2026-10-09T08:00:00+02:00');
  assert.equal(rows[15].date, '2026-10-24');
  assert.equal(rows[15].datetime, '2026-10-24T08:00:00+02:00');
  assert.equal(rows[16].date, '2026-10-25');
  assert.equal(rows[16].datetime, '2026-10-25T08:00:00+01:00');
  assert.ok(rows.every((r) => r.time === '08:00' && r.timezone === 'Europe/Stockholm'));
});

test('dates and offsets', () => {
  assert.equal(addDays('2026-10-31', 1), '2026-11-01');
  assert.equal(addDays('2026-12-31', 1), '2027-01-01');
  assert.equal(stockholmOffset('2026-07-01'), '+02:00');
  assert.equal(stockholmOffset('2026-12-01'), '+01:00');
  assert.equal(isValidIsoDate('2026-02-30'), false);
  assert.throws(() => scheduleRows([{}], { start: '9 Oct' }), /YYYY-MM-DD/);
});

test('CSV quotes commas, quotes and new lines and starts with a BOM', () => {
  const csv = toCsv([{ a: 'x, y', b: 'say "hi"', c: 'line1\nline2' }], ['a', 'b', 'c']);
  assert.equal(csv, '\uFEFFa,b,c\r\n"x, y","say ""hi""","line1\nline2"\r\n');
});
