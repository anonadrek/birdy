// Posting order and schedule.csv: one post per day at 08:00 Europe/Stockholm,
// published species first, then the most reported species (Artportalen) first.

export const POST_TIME = '08:00';
export const TIME_ZONE = 'Europe/Stockholm';

function reports(record) {
  return Number(record?.data?.totalReports ?? 0);
}

/** Published first, then by Artportalen report count (most common first), then English name. */
export function orderForSchedule(records) {
  return [...records].sort((a, b) => {
    const pa = a.publish === true ? 0 : 1;
    const pb = b.publish === true ? 0 : 1;
    if (pa !== pb) return pa - pb;
    if (reports(b) !== reports(a)) return reports(b) - reports(a);
    return a.names.en.localeCompare(b.names.en, 'en');
  });
}

export function addDays(isoDate, days) {
  const [y, m, d] = isoDate.split('-').map(Number);
  const t = new Date(Date.UTC(y, m - 1, d + days));
  return t.toISOString().slice(0, 10);
}

/** UTC offset ("+02:00" or "+01:00") of the given local date and time in Stockholm. */
export function stockholmOffset(isoDate, time = POST_TIME) {
  const [y, m, d] = isoDate.split('-').map(Number);
  const [hh, mm] = time.split(':').map(Number);
  const fmt = new Intl.DateTimeFormat('en-GB', { timeZone: TIME_ZONE, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
  for (const off of [1, 2]) {
    const local = fmt.format(new Date(Date.UTC(y, m - 1, d, hh - off, mm)));
    if (local === time) return `+0${off}:00`;
  }
  throw new Error(`no Stockholm offset for ${isoDate} ${time}`);
}

export function isValidIsoDate(s) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) return false;
  return addDays(s, 0) === s;
}

/**
 * items: [{ record, ...extra }] already in posting order. Returns rows with date, time and
 * an ISO datetime with the right offset (summer time ends on 2026-10-25).
 */
export function scheduleRows(items, { start }) {
  if (!isValidIsoDate(start)) throw new Error(`--start must be YYYY-MM-DD, got "${start}"`);
  return items.map((item, i) => {
    const date = addDays(start, i);
    const offset = stockholmOffset(date, POST_TIME);
    return { date, time: POST_TIME, timezone: TIME_ZONE, datetime: `${date}T${POST_TIME}:00${offset}`, ...item };
  });
}

function csvCell(v) {
  const s = v === undefined || v === null ? '' : String(v);
  return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

/** RFC 4180 CSV with CRLF line ends and a BOM so Excel reads å, ä and ö. */
export function toCsv(rows, columns) {
  const lines = [columns.map(csvCell).join(',')];
  for (const row of rows) lines.push(columns.map((c) => csvCell(row[c])).join(','));
  return `\uFEFF${lines.join('\r\n')}\r\n`;
}
