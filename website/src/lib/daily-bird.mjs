// Dagens fågel on the website: the same bird as the app's Dagens fågel (plan 2026-10-08 Task 1).
//
// An exact port of the app's DailyBirdSelector (shared/domain/.../dailybird/DailyBirdSelector.kt, the 1.3.0 rule
// from release/1.3.0 12c7526f): only species reviewed as regular in Sweden (abundance "allmän" or "mindre allmän"),
// never an extinct one (IUCN EX or EW), the month's season tag must be breeding, present or migrating, and one of
// the regions SE, NO, FI or DK. The candidates are sorted by QID, and the pick is
// kotlin.random.Random("${year}-${month}-${day}-NORDIC".hashCode().toLong()).nextInt(candidates.size), with
// Java's String.hashCode and Kotlin's XorWow generator reimplemented below. tests/unit/daily-bird.unit.mjs checks
// this file against a golden file written by the Kotlin code itself (tests/fixtures/daily-bird-golden.json).
//
// The species list is the app's own content, shared/content/species/**/<QID>.yaml, read when the site is built.
// Plain JS, so the unit tests run it with node --test and the Astro components import it as is.
import { readdirSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

export const MONTH_KEYS = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec'];
export const NORDIC_BUCKET = new Set(['SE', 'NO', 'FI', 'DK']);
/** Abundances given only to species reviewed as regular in Sweden (DailyBirdSelector.REGULAR_IN_SWEDEN). */
export const REGULAR_IN_SWEDEN = new Set(['allmän', 'mindre allmän']);
const SEASON_TAGS = new Set(['breeding', 'present', 'migrating']);
const REGION_SEED = 'NORDIC';

/**
 * The first day the website may say "the same bird as in the app today": the day Birdy 1.3.0 goes out on
 * Google Play with this rule. Before it, the app in people's phones (1.2) still picks with the old rule.
 */
export const SAME_AS_APP_FROM = '2026-10-15';

/** Java's String.hashCode (UTF-16 code units, 32-bit overflow), which Kotlin/JVM's String.hashCode is. */
export function javaHashCode(text) {
  let h = 0;
  for (let i = 0; i < text.length; i += 1) h = (Math.imul(31, h) + text.charCodeAt(i)) | 0;
  return h;
}

/**
 * kotlin.random.Random(seed: Long): XorWowRandom(seed.toInt(), seed.shr(32).toInt()), discarding the first 64
 * values, with Random.nextInt(until) as in the Kotlin standard library (power-of-two bound takes the upper bits,
 * any other bound rejects the values that would bias the remainder).
 */
export class KotlinRandom {
  /** @param {bigint | number} seed a Kotlin Long */
  constructor(seed) {
    const long = BigInt.asIntN(64, BigInt(seed));
    const seed1 = Number(BigInt.asIntN(32, long));
    const seed2 = Number(BigInt.asIntN(32, long >> 32n));
    this.x = seed1;
    this.y = seed2;
    this.z = 0;
    this.w = 0;
    this.v = ~seed1;
    this.addend = (seed1 << 10) ^ (seed2 >>> 4);
    if ((this.x | this.y | this.z | this.w | this.v) === 0) throw new Error('Initial state must have at least one non-zero element.');
    for (let i = 0; i < 64; i += 1) this.nextInt();
  }

  /**
   * XorWowRandom.nextInt(): Marsaglia's xorwow, 32-bit signed. With a bound, Random.nextInt(until).
   * @param {number} [until]
   */
  nextInt(until) {
    if (until !== undefined) return this.nextIntUntil(until);
    let t = this.x;
    t ^= t >>> 2;
    this.x = this.y;
    this.y = this.z;
    this.z = this.w;
    const v0 = this.v;
    this.w = v0;
    t = (t ^ (t << 1)) ^ v0 ^ (v0 << 4);
    this.v = t | 0;
    this.addend = (this.addend + 362437) | 0;
    return (t + this.addend) | 0;
  }

  /** Int.takeUpperBits(bitCount) of the next value. */
  nextBits(bitCount) {
    return (this.nextInt() >>> (32 - bitCount)) & ((-bitCount) >> 31);
  }

  /** Random.nextInt(0, until) for 0 < until <= Int.MAX_VALUE. */
  nextIntUntil(until) {
    if (!Number.isInteger(until) || until <= 0 || until > 2147483647) throw new Error(`nextInt(${until}): the bound must be a positive Int`);
    const n = until;
    if ((n & -n) === n) return this.nextBits(31 - Math.clz32(n));
    let bits;
    let v;
    do {
      bits = this.nextInt() >>> 1;
      v = bits % n;
    } while (((bits - v + (n - 1)) | 0) < 0);
    return v;
  }
}

/** The seed DailyBirdSelector uses for a date: no zero padding ("2026-10-8-NORDIC"). */
export function daySeed({ year, month, day }) {
  return javaHashCode(`${year}-${month}-${day}-${REGION_SEED}`);
}

const isExtinct = (iucn) => iucn === 'EX' || iucn === 'EW';
// Kotlin's sortedBy on String compares UTF-16 code units, which is what JS's < on strings does.
const byCodeUnits = (a, b) => (a < b ? -1 : a > b ? 1 : 0);

/**
 * The app's Dagens fågel for a date, as a QID, or null when no species qualifies.
 * @param {{ id: string, abundance: string, iucn: string, regions: string[], season: Record<string, string> }[]} species
 * @param {{ year: number, month: number, day: number }} date
 */
export function selectAppDailyBird(species, date) {
  const monthKey = MONTH_KEYS[date.month - 1];
  const candidates = species
    .filter((s) => {
      if (isExtinct(s.iucn)) return false;
      if (!REGULAR_IN_SWEDEN.has(s.abundance)) return false;
      const tag = s.season?.[monthKey];
      if (tag === undefined || !SEASON_TAGS.has(tag.toLowerCase())) return false;
      return s.regions.some((r) => NORDIC_BUCKET.has(r));
    })
    .map((s) => s.id)
    .sort(byCodeUnits);
  if (candidates.length === 0) return null;
  return candidates[new KotlinRandom(daySeed(date)).nextInt(candidates.length)];
}

/**
 * What the home page shows (plan Task 1): the app's bird (`appQid`, from selectAppDailyBird) when it has a species
 * page in this build, with the line "the same bird as in the app today" from SAME_AS_APP_FROM on. Otherwise a pick
 * among the species with a page, with the same seed, so it stays the same all day, and without that line. Null when
 * no species has a page.
 * @param {{ appQid: string | null, pageQids: string[], date: { year: number, month: number, day: number, iso: string } }} input
 * @returns {{ qid: string, appQid: string | null, sameAsApp: boolean } | null}
 */
export function siteDailyBird({ appQid, pageQids, date }) {
  if (appQid && pageQids.includes(appQid)) return { qid: appQid, appQid, sameAsApp: date.iso >= SAME_AS_APP_FROM };
  if (pageQids.length === 0) return null;
  const sorted = [...pageQids].sort(byCodeUnits);
  return { qid: sorted[new KotlinRandom(daySeed(date)).nextInt(sorted.length)], appQid, sameAsApp: false };
}

/** Licences that ask nothing of us, so the photo may be cropped or framed together with text (plan house rules). */
export const FREE_LICENSES = new Set(['CC0', 'Public domain']);

/**
 * The photo a plate or a card may show: the species' main photo when it is CC0 or public domain, else its first other
 * photo that is. Undefined when it has none; such a species gets no plate or card (plan house rules: only CC0 and
 * public domain photos are cropped or framed with text).
 * @template {{ role: string, license: string }} T
 * @param {T[]} images
 * @returns {T | undefined}
 */
export function freeImage(images) {
  return images.find((i) => i.role === 'hero' && FREE_LICENSES.has(i.license)) ?? images.find((i) => FREE_LICENSES.has(i.license));
}

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

/**
 * Today's date in Europe/Stockholm, the app's time zone in Sweden, when the site is built (the nightly build runs
 * a few minutes after midnight Swedish time). BIRDY_TODAY=YYYY-MM-DD pins it for test builds; never in Vercel's
 * Production, where a leaked value would freeze the bird.
 * @param {Date} [now]
 * @param {Record<string, string | undefined>} [env]
 */
export function buildDate(now = new Date(), env = process.env) {
  const pinned = env.BIRDY_TODAY;
  if (pinned) {
    if (env.VERCEL_ENV === 'production') {
      throw new Error('BIRDY_TODAY är satt men VERCEL_ENV=production: Dagens fågel får aldrig låsas till ett datum i Vercels Production.');
    }
    const m = pinned.match(ISO_DATE);
    const check = m && new Date(`${pinned}T12:00:00Z`);
    if (!m || Number.isNaN(check.valueOf()) || check.toISOString().slice(0, 10) !== pinned) throw new Error(`BIRDY_TODAY=${pinned}: skriv datumet som YYYY-MM-DD`);
    return dateParts(Number(m[1]), Number(m[2]), Number(m[3]));
  }
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Stockholm', year: 'numeric', month: '2-digit', day: '2-digit' })
      .formatToParts(now)
      .map((p) => [p.type, p.value]),
  );
  return dateParts(Number(parts.year), Number(parts.month), Number(parts.day));
}

/** { year, month, day, iso, weekday (0 = Monday), dayOfYear }. */
export function dateParts(year, month, day) {
  const utc = Date.UTC(year, month - 1, day);
  const iso = new Date(utc).toISOString().slice(0, 10);
  const dayOfYear = Math.round((utc - Date.UTC(year, 0, 1)) / 86_400_000) + 1;
  const weekday = (new Date(utc).getUTCDay() + 6) % 7;
  return { year, month, day, iso, weekday, dayOfYear };
}

// ---------------------------------------------------------------------------------------------------------------
// The app's species YAML, only the fields the selector reads. The files are machine-written (the content
// pipeline's PyYAML dump), so a small line reader is enough; anything it does not recognise is an error, never
// a silent default, because a misread field would change which bird everyone sees.

const unquote = (raw) => {
  const v = raw.trim();
  if (v.length >= 2 && ((v[0] === "'" && v.at(-1) === "'") || (v[0] === '"' && v.at(-1) === '"'))) {
    return v[0] === "'" ? v.slice(1, -1).replace(/''/g, "'") : JSON.parse(v);
  }
  return v;
};

const TOP_KEY = /^([A-Za-z_]\w*):(?:\s(.*))?$/;
const SEASON_LINE = /^ {2}([a-z]{3}):\s+(\S.*)$/;
const REGION_LINE = /^(?: {2})?- (.+)$/;

/**
 * @param {string} text one species file
 * @param {string} [where] the file, for error messages
 * @returns {{ id: string, abundance: string, iucn: string, regions: string[], season: Record<string, string> }}
 */
export function parseSpeciesYaml(text, where = 'species YAML') {
  const lines = text.replace(/^﻿/, '').split(/\r?\n/);
  /** @type {Record<string, string>} */
  const top = {};
  /** @type {Record<string, string>} */
  const season = {};
  /** @type {string[]} */
  const regions = [];
  let block = null;
  for (const line of lines) {
    if (line === '' || line.startsWith('#')) continue;
    if (!line.startsWith(' ') && !line.startsWith('-')) {
      const m = line.match(TOP_KEY);
      if (!m) { block = null; continue; }
      const key = m[1];
      const rest = (m[2] ?? '').trim();
      block = rest === '' ? key : null;
      if (key === 'season' || key === 'regions') {
        if (rest === '{}' || rest === '[]') continue;
        if (rest !== '') throw new Error(`${where}: ${key} på en rad (${rest}) går inte att läsa, skriv det som ett block`);
      }
      if (rest !== '') top[key] = unquote(rest);
      continue;
    }
    if (block === 'season') {
      const m = line.match(SEASON_LINE);
      if (!m) throw new Error(`${where}: oväntad rad i season: ${JSON.stringify(line)}`);
      season[m[1]] = unquote(m[2]);
    } else if (block === 'regions') {
      const m = line.match(REGION_LINE);
      if (!m) throw new Error(`${where}: oväntad rad i regions: ${JSON.stringify(line)}`);
      regions.push(unquote(m[1]));
    }
  }
  for (const key of ['id', 'abundance', 'iucn_status']) {
    if (typeof top[key] !== 'string' || top[key] === '') throw new Error(`${where}: ${key} saknas`);
  }
  if (!/^Q\d+$/.test(top.id)) throw new Error(`${where}: id ${top.id} är inget QID`);
  for (const key of Object.keys(season)) {
    if (!MONTH_KEYS.includes(key)) throw new Error(`${where}: okänd månad i season: ${key}`);
  }
  return { id: top.id, abundance: top.abundance, iucn: top.iucn_status, regions, season };
}

/** Every *.yaml under a folder, at any depth. */
function yamlFiles(dir) {
  return readdirSync(dir, { recursive: true, encoding: 'utf8' })
    .filter((f) => f.endsWith('.yaml'))
    .map((f) => join(dir, f));
}

/**
 * The app's species as frozen in the golden file (release 1.3.0), for test builds (SPECIES_FIXTURES=1): with the date
 * pinned by BIRDY_TODAY the fixture build always gets the same app bird, whatever happens to the YAML later.
 * @param {string} websiteRoot
 */
export function loadFixtureAppSpecies(websiteRoot) {
  const golden = JSON.parse(readFileSync(resolve(websiteRoot, 'tests', 'fixtures', 'daily-bird-golden.json'), 'utf8'));
  return /** @type {ReturnType<typeof parseSpeciesYaml>[]} */ (golden.species);
}

/** @type {{ root: string, species: ReturnType<typeof parseSpeciesYaml>[] } | undefined} */
let cached;
/**
 * The app's species, read from shared/content/species/ in the repository (the website root is website/, so one
 * level up). Read once per build.
 * @param {string} websiteRoot
 */
export function loadAppSpecies(websiteRoot) {
  if (cached?.root === websiteRoot) return cached.species;
  const dir = resolve(websiteRoot, '..', 'shared', 'content', 'species');
  const species = yamlFiles(dir).map((file) => parseSpeciesYaml(readFileSync(file, 'utf8'), file));
  if (species.length === 0) throw new Error(`${dir}: inga arter, Dagens fågel kan inte väljas`);
  const ids = new Set();
  for (const s of species) {
    if (ids.has(s.id)) throw new Error(`${dir}: ${s.id} finns två gånger`);
    ids.add(s.id);
  }
  cached = { root: websiteRoot, species };
  return species;
}
