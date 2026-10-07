import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import {
  KotlinRandom, SAME_AS_APP_FROM, buildDate, dateParts, freeImage, javaHashCode, loadAppSpecies, parseSpeciesYaml, selectAppDailyBird, siteDailyBird,
} from '../../src/lib/daily-bird.mjs';

// Written by the app's own Kotlin DailyBirdSelector and kotlin.random.Random (see the file's _about).
const golden = JSON.parse(readFileSync(new URL('../fixtures/daily-bird-golden.json', import.meta.url), 'utf8'));
const websiteRoot = fileURLToPath(new URL('../../', import.meta.url));
const day = (iso) => {
  const [y, m, d] = iso.split('-').map(Number);
  return dateParts(y, m, d);
};

test('javaHashCode: samma tal som Kotlins String.hashCode', () => {
  for (const [text, hash] of Object.entries(golden.hash)) assert.equal(javaHashCode(text), hash, text);
});

test('KotlinRandom: samma följd som kotlin.random.Random(seed), med och utan gräns', () => {
  assert.ok(golden.rng.length > 60);
  for (const row of golden.rng) {
    const rng = new KotlinRandom(BigInt(row.seed));
    if (row.raw) assert.deepEqual(row.raw.map(() => rng.nextInt()), row.raw, `seed ${row.seed}`);
    else assert.deepEqual(row.draws.map(() => rng.nextInt(row.n)), row.draws, `seed ${row.seed}, n ${row.n}`);
  }
});

test('selectAppDailyBird: samma fågel som appen varje dag 2026-10-01 till 2027-12-31', () => {
  const days = Object.entries(golden.days);
  assert.equal(days.length, 457);
  const wrong = days.filter(([iso, qid]) => selectAppDailyBird(golden.species, day(iso)) !== qid);
  assert.deepEqual(wrong, []);
});

test('selectAppDailyBird: 2026-10-07 är Gulärla med 1.3.0-regeln (Råka var den gamla regeln)', () => {
  // The store image's Råka (Q25386) on 7 Oct was picked before release/1.3.0 12c7526f (17.55 that day), when
  // unreviewed "ovanlig" species could still win every fourth day.
  assert.equal(golden.days['2026-10-07'], 'Q25984');
  assert.equal(selectAppDailyBird(golden.species, day('2026-10-07')), 'Q25984');
});

test('selectAppDailyBird: bara regelbundna, levande, nordiska arter med säsong för månaden', () => {
  const base = { abundance: 'allmän', iucn: 'LC', regions: ['SE'], season: { oct: 'present' } };
  const only = (s) => selectAppDailyBird([s], day('2026-10-08'));
  assert.equal(only({ ...base, id: 'Q1' }), 'Q1');
  assert.equal(only({ ...base, id: 'Q1', abundance: 'mindre allmän' }), 'Q1');
  assert.equal(only({ ...base, id: 'Q1', abundance: 'ovanlig' }), null);
  assert.equal(only({ ...base, id: 'Q1', iucn: 'EX' }), null);
  assert.equal(only({ ...base, id: 'Q1', iucn: 'EW' }), null);
  assert.equal(only({ ...base, id: 'Q1', regions: ['DE'] }), null);
  assert.equal(only({ ...base, id: 'Q1', season: { nov: 'present' } }), null);
  assert.equal(only({ ...base, id: 'Q1', season: { oct: 'absent' } }), null);
  assert.equal(only({ ...base, id: 'Q1', season: { oct: 'Breeding' } }), 'Q1');
  assert.equal(selectAppDailyBird([], day('2026-10-08')), null);
});

test('selectAppDailyBird: ordningen arterna läses i spelar ingen roll', () => {
  const reversed = [...golden.species].reverse();
  for (const iso of ['2026-10-08', '2026-12-24', '2027-06-01']) {
    assert.equal(selectAppDailyBird(reversed, day(iso)), golden.days[iso]);
  }
});

test('siteDailyBird: appens fågel när den har en sida, raden om appen först från 1.3.0-dagen', () => {
  const before = day('2026-10-14');
  const appBefore = golden.days[before.iso];
  const out = siteDailyBird({ appQid: selectAppDailyBird(golden.species, before), pageQids: ['Q25485', appBefore], date: before });
  assert.deepEqual(out, { qid: appBefore, appQid: appBefore, sameAsApp: false });

  const from = day(SAME_AS_APP_FROM);
  const appFrom = golden.days[from.iso];
  assert.deepEqual(siteDailyBird({ appQid: selectAppDailyBird(golden.species, from), pageQids: [appFrom], date: from }), { qid: appFrom, appQid: appFrom, sameAsApp: true });
});

test('siteDailyBird: utan sida för appens fågel väljs en art med sida, samma hela dagen och utan raden', () => {
  const date = day('2026-10-20');
  const appQid = golden.days[date.iso];
  const pages = ['Q25485', 'Q25404', 'Q14683', 'Q4764'].filter((q) => q !== appQid);
  const a = siteDailyBird({ appQid: selectAppDailyBird(golden.species, date), pageQids: pages, date });
  const b = siteDailyBird({ appQid: selectAppDailyBird(golden.species, date), pageQids: [...pages].reverse(), date });
  assert.ok(a && pages.includes(a.qid));
  assert.deepEqual(a, b);
  assert.equal(a.sameAsApp, false);
  assert.equal(a.appQid, appQid);
  assert.equal(siteDailyBird({ appQid: selectAppDailyBird(golden.species, date), pageQids: [], date }), null);
});

test('freeImage: bara CC0 och public domain, huvudfotot först', () => {
  const hero = (license) => ({ role: 'hero', license });
  const extra = (license) => ({ role: 'extra', license });
  assert.deepEqual(freeImage([hero('CC0'), extra('Public domain')]), hero('CC0'));
  assert.deepEqual(freeImage([hero('CC BY 4.0'), extra('Public domain')]), extra('Public domain'));
  assert.equal(freeImage([hero('CC BY-SA 4.0'), extra('CC BY 2.0')]), undefined);
  assert.equal(freeImage([]), undefined);
});

test('dateParts: planschnumret är dagens nummer på året', () => {
  assert.equal(day('2026-10-07').dayOfYear, 280);
  assert.equal(day('2026-10-08').dayOfYear, 281);
  assert.equal(day('2026-01-01').dayOfYear, 1);
  assert.equal(day('2028-12-31').dayOfYear, 366);
  assert.equal(day('2026-10-08').weekday, 3); // torsdag
});

test('buildDate: svensk tid, inte UTC', () => {
  // 22.30 UTC on 7 Oct is 00.30 on 8 Oct in Stockholm (summer time), 23.30 UTC on 31 Dec is 1 Jan (winter time).
  assert.equal(buildDate(new Date('2026-10-07T22:30:00Z'), {}).iso, '2026-10-08');
  assert.equal(buildDate(new Date('2026-10-07T21:30:00Z'), {}).iso, '2026-10-07');
  assert.equal(buildDate(new Date('2026-12-31T23:30:00Z'), {}).iso, '2027-01-01');
});

test('buildDate: BIRDY_TODAY låser datumet i testbyggen men aldrig i Vercels Production', () => {
  assert.equal(buildDate(new Date('2026-10-08T12:00:00Z'), { BIRDY_TODAY: '2026-10-16' }).iso, '2026-10-16');
  assert.throws(() => buildDate(new Date(), { BIRDY_TODAY: '2026-10-16', VERCEL_ENV: 'production' }), /Production/);
  assert.throws(() => buildDate(new Date(), { BIRDY_TODAY: '2026-02-30' }), /YYYY-MM-DD/);
  assert.throws(() => buildDate(new Date(), { BIRDY_TODAY: '8 okt' }), /YYYY-MM-DD/);
});

test('parseSpeciesYaml: läser fälten som väljaren använder', () => {
  const text = [
    'id: Q25386',
    'scientific_name: Corvus frugilegus',
    'names:',
    '  sv: Råka',
    "  en: 'Rook'",
    'abundance: allmän',
    'iucn_status: LC',
    'season:',
    '  jan: present',
    '  oct: breeding',
    'regions:',
    '- SE',
    "- 'NO'",
    '- DK',
    'description:',
    "  sv: '# Råka",
    '',
    "    jan: inte en månad'",
    'review_notes: "x: y"',
  ].join('\n');
  assert.deepEqual(parseSpeciesYaml(text), {
    id: 'Q25386', abundance: 'allmän', iucn: 'LC', regions: ['SE', 'NO', 'DK'], season: { jan: 'present', oct: 'breeding' },
  });
  assert.deepEqual(parseSpeciesYaml('id: Q1\nabundance: \'mindre allmän\'\niucn_status: "NE"\nseason: {}\nregions: []\n').regions, []);
  assert.throws(() => parseSpeciesYaml('id: Q1\niucn_status: LC\n'), /abundance saknas/);
  assert.throws(() => parseSpeciesYaml('id: Q1\nabundance: allmän\niucn_status: LC\nseason:\n  okt: present\n'), /season/);
  assert.throws(() => parseSpeciesYaml('id: Q1\nabundance: allmän\niucn_status: LC\nseason: {jan: present}\n'), /på en rad/);
});

test('loadAppSpecies: läser appens arter ur shared/content/species/', () => {
  const species = loadAppSpecies(websiteRoot);
  assert.ok(species.length >= 839, String(species.length));
  assert.ok(species.every((s) => /^Q\d+$/.test(s.id) && s.regions.length > 0 && Object.keys(s.season).length > 0));
  assert.equal(species.find((s) => s.id === 'Q25386')?.regions.includes('SE'), true);
});
