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

/** A schedule row for `date`: 08:00 Stockholm with the right UTC offset (summer time ends on 2026-10-25). */
export function rowFor(date, item) {
  if (!isValidIsoDate(date)) throw new Error(`a date must be YYYY-MM-DD, got "${date}"`);
  const offset = stockholmOffset(date, POST_TIME);
  return { date, time: POST_TIME, timezone: TIME_ZONE, datetime: `${date}T${POST_TIME}:00${offset}`, ...item };
}

/** items already in posting order, one a day from `start`. */
export function scheduleRows(items, { start }) {
  if (!isValidIsoDate(start)) throw new Error(`--start must be YYYY-MM-DD, got "${start}"`);
  return items.map((item, i) => rowFor(addDays(start, i), item));
}

/**
 * The posting date of every selected species, fixed per species: a species already in the
 * schedule keeps its date; a new one gets start + its place in the selection (so a species that
 * fails does not move the ones after it). Two species on one day is an error, raised before
 * anything is rendered, so the fix is to pass another --start.
 */
export function planDates(qids, existingRows, { start }) {
  if (!isValidIsoDate(start)) throw new Error(`--start must be YYYY-MM-DD, got "${start}"`);
  const dateOf = new Map(existingRows.map((r) => [r.qid, r.date]));
  const byDate = new Map(existingRows.map((r) => [r.date, r.qid]));
  const plan = new Map();
  qids.forEach((qid, i) => {
    const date = dateOf.get(qid) ?? addDays(start, i);
    const taken = byDate.get(date);
    if (taken && taken !== qid) throw new Error(`${date} already belongs to ${taken} in schedule.csv; pass a --start where ${qid} gets a free day`);
    byDate.set(date, qid);
    plan.set(qid, date);
  });
  return plan;
}

/** The existing rows with this run's rows put in by QID (a re-run replaces, never shifts), by date. */
export function mergeSchedule(existingRows, newRows) {
  const byQid = new Map(existingRows.map((r) => [r.qid, r]));
  for (const r of newRows) byQid.set(r.qid, r);
  return [...byQid.values()].sort((a, b) => a.date.localeCompare(b.date));
}

/** Reads toCsv's output back (RFC 4180: quoted cells may hold commas, quotes and new lines). */
export function parseCsv(text) {
  const src = text.replace(/^\uFEFF/, '');
  const rows = [];
  let row = [];
  let cell = '';
  let quoted = false;
  for (let i = 0; i < src.length; i++) {
    const ch = src[i];
    if (quoted) {
      if (ch === '"' && src[i + 1] === '"') {
        cell += '"';
        i++;
      } else if (ch === '"') quoted = false;
      else cell += ch;
    } else if (ch === '"') quoted = true;
    else if (ch === ',') {
      row.push(cell);
      cell = '';
    } else if (ch === '\r' || ch === '\n') {
      if (ch === '\r' && src[i + 1] === '\n') i++;
      row.push(cell);
      rows.push(row);
      row = [];
      cell = '';
    } else cell += ch;
  }
  if (cell !== '' || row.length) {
    row.push(cell);
    rows.push(row);
  }
  const [header, ...body] = rows;
  if (!header) return [];
  return body.filter((r) => r.length > 1 || r[0] !== '').map((r) => Object.fromEntries(header.map((h, i) => [h, r[i] ?? ''])));
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
