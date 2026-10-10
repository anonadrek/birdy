import { test } from 'node:test';
import assert from 'node:assert/strict';
import { orderForSchedule, scheduleRows, stockholmOffset, toCsv, addDays, isValidIsoDate, rowFor, planDates, mergeSchedule, parseCsv } from '../lib/schedule.mjs';
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

test('dates are fixed per species: a known species keeps its date, a new one gets start + its place', () => {
  const existing = [rowFor('2026-10-09', { qid: 'Q1' }), rowFor('2026-10-10', { qid: 'Q2' }), rowFor('2026-10-12', { qid: 'Q4' })];
  // Re-running the whole week, with Q3 (which failed last time) back in its place.
  const plan = planDates(['Q1', 'Q2', 'Q3', 'Q4'], existing, { start: '2026-10-09' });
  assert.deepEqual([...plan], [['Q1', '2026-10-09'], ['Q2', '2026-10-10'], ['Q3', '2026-10-11'], ['Q4', '2026-10-12']]);
  // Re-running one species keeps its date whatever --start says.
  assert.deepEqual([...planDates(['Q4'], existing, { start: '2026-10-09' })], [['Q4', '2026-10-12']]);
  // A failed species leaves its day empty instead of moving the later ones.
  assert.deepEqual([...planDates(['A', 'B', 'C'], [], { start: '2026-10-09' })].map(([, d]) => d), ['2026-10-09', '2026-10-10', '2026-10-11']);
});

test('two species on one day is an error that names the fix', () => {
  const existing = [rowFor('2026-10-09', { qid: 'Q1' })];
  assert.throws(() => planDates(['Q9'], existing, { start: '2026-10-09' }), /2026-10-09 already belongs to Q1.*--start/);
});

test('merging replaces rows by species and never shifts the others', () => {
  const existing = [rowFor('2026-10-09', { qid: 'Q1', note: 'old' }), rowFor('2026-10-10', { qid: 'Q2', note: 'old' })];
  const merged = mergeSchedule(existing, [rowFor('2026-10-10', { qid: 'Q2', note: 'new' })]);
  assert.deepEqual(merged.map((r) => [r.date, r.qid, r.note]), [['2026-10-09', 'Q1', 'old'], ['2026-10-10', 'Q2', 'new']]);
});

test('the CSV reads back as written', () => {
  const columns = ['date', 'qid', 'text'];
  const rows = [
    { date: '2026-10-09', qid: 'Q1', text: 'x, y and "z"' },
    { date: '2026-10-10', qid: 'Q2', text: 'line1\nline2\n\nline4' },
  ];
  assert.deepEqual(parseCsv(toCsv(rows, columns)), rows);
  assert.deepEqual(parseCsv(''), []);
});
