#!/usr/bin/env node
// Writes TEST data for the species pages (spec 2026-09-25 appendix C and D), so the pages can be built
// and tested before the pipeline has produced real files. Never real facts: every text says
// "Testtext" / "Test text". Output lives under tests/fixtures/ and is only read when the build runs
// with SPECIES_FIXTURES=1 (npm run build:fixtures). Re-run after changing this file:
//   node tests/fixtures/make-species-fixtures.mjs
//
// This script DELETES and rewrites its three output folders (species/, comparisons/, species-assets/)
// from scratch on every run — nothing hand-written can live there, it would be wiped on the next run.
import { mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const here = dirname(fileURLToPath(import.meta.url));
const OUT = {
  species: resolve(here, 'species'),
  comparisons: resolve(here, 'comparisons'),
  assets: resolve(here, 'species-assets'),
};

const COUNTY_CODES = ['SE-AB', 'SE-AC', 'SE-BD', 'SE-C', 'SE-D', 'SE-E', 'SE-F', 'SE-G', 'SE-H', 'SE-I', 'SE-K', 'SE-M', 'SE-N', 'SE-O', 'SE-S', 'SE-T', 'SE-U', 'SE-W', 'SE-X', 'SE-Y', 'SE-Z'];
const YEAR_ROUND = [72, 58, 61, 55, 70, 79, 64, 68, 74, 100, 66, 69];
const SUMMER = [0, 0, 1, 36, 100, 51, 49, 65, 51, 10, 1, 0];

// The twelve "common species" in the footer must all be here, or the footer stops the build.
const SPECIES = [
  { qid: 'Q25485', sv: 'Talgoxe', en: 'Great Tit', sci: 'Parus major', fam: ['Paridae', 'Mesar'], group: 'songbirds', slug: ['talgoxe', 'great-tit'], iucn: 'LC', red: 'not_listed', id: [true, true], extra: true, audio: 'trimmed', marginalia: true, de: true, months: YEAR_ROUND, status: 'resident', size: ['Cirka 14 cm', 'About 14 cm'], look: ['Q25404'] },
  { qid: 'Q25404', sv: 'Blåmes', en: 'Eurasian Blue Tit', sci: 'Cyanistes caeruleus', fam: ['Paridae', 'Mesar'], group: 'songbirds', slug: ['blames', 'eurasian-blue-tit'], iucn: 'LC', red: 'not_listed', id: [true, true], audio: 'full', de: true, months: YEAR_ROUND, status: 'resident', size: ['Cirka 12 cm', 'About 12 cm'], look: ['Q25485'] },
  { qid: 'Q25234', sv: 'Koltrast', en: 'Common Blackbird', sci: 'Turdus merula', fam: ['Turdidae', 'Trastar'], group: 'songbirds', slug: ['koltrast', 'common-blackbird'], iucn: 'LC', red: 'not_listed', id: [true, true], extra: true, audio: 'trimmed', de: true, months: YEAR_ROUND, status: 'resident' },
  { qid: 'Q25334', sv: 'Rödhake', en: 'European Robin', sci: 'Erithacus rubecula', fam: ['Muscicapidae', 'Flugsnappare'], group: 'songbirds', slug: ['rodhake', 'european-robin'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident' },
  { qid: 'Q14683', sv: 'Gråsparv', en: 'House Sparrow', sci: 'Passer domesticus', fam: ['Passeridae', 'Sparvfinkar'], group: 'songbirds', slug: ['grasparv', 'house-sparrow'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident' },
  { qid: 'Q25307', sv: 'Skata', en: 'Eurasian Magpie', sci: 'Pica pica', fam: ['Corvidae', 'Kråkfåglar'], group: 'songbirds', slug: ['skata', 'eurasian-magpie'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident', look: ['Q25345384'] },
  { qid: 'Q25345384', sv: 'Kaja', en: 'Western Jackdaw', sci: 'Coloeus monedula', fam: ['Corvidae', 'Kråkfåglar'], group: 'songbirds', slug: ['kaja', 'western-jackdaw'], iucn: 'NE', red: 'not_listed', id: [false, true], de: true, months: YEAR_ROUND, status: 'resident', look: ['Q25307'] },
  { qid: 'Q25383', sv: 'Bofink', en: 'Eurasian Chaffinch', sci: 'Fringilla coelebs', fam: ['Fringillidae', 'Finkar'], group: 'songbirds', slug: ['bofink', 'eurasian-chaffinch'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: SUMMER, status: 'breeding_migrant' },
  { qid: 'Q25348', sv: 'Gräsand', en: 'Mallard', sci: 'Anas platyrhynchos', fam: ['Anatidae', 'Egentliga andfåglar'], group: 'waterfowl', slug: ['grasand', 'mallard'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident' },
  { qid: 'Q26427', sv: 'Fiskmås', en: 'Common Gull', sci: 'Larus canus', fam: ['Laridae', 'Måsfåglar'], group: 'gulls_terns', slug: ['fiskmas', 'common-gull'], iucn: 'LC', red: 'NT', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident' },
  { qid: 'Q25385', sv: 'Ormvråk', en: 'Common Buzzard', sci: 'Buteo buteo', fam: ['Accipitridae', 'Hökar'], group: 'raptors', slug: ['ormvrak', 'common-buzzard'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident' },
  { qid: 'Q4764', sv: 'Trana', en: 'Common Crane', sci: 'Grus grus', fam: ['Gruidae', 'Tranor'], group: 'cranes_rails', slug: ['trana', 'common-crane'], iucn: 'LC', red: 'not_listed', id: [true, false], de: true, months: SUMMER, status: 'breeding_migrant' },
  { qid: 'Q25756', sv: 'Kattuggla', en: 'Tawny Owl', sci: 'Strix aluco', fam: ['Strigidae', 'Egentliga ugglor'], group: 'owls', slug: ['kattuggla', 'tawny-owl'], iucn: 'LC', red: 'not_listed', id: [true, true], audio: 'full', de: true, months: YEAR_ROUND, status: 'resident', look: ['Strix uralensis'] },
  { qid: 'Q25384', sv: 'Hornuggla', en: 'Long-eared Owl', sci: 'Asio otus', fam: ['Strigidae', 'Egentliga ugglor'], group: 'owls', slug: ['hornuggla', 'long-eared-owl'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident', look: ['Q25769'] },
  // Minimal record: no audio, no report data, no extra photo, no behaviour or look-alikes, no size,
  // status or Swedish red list, Swedish article only.
  { qid: 'Q174466', sv: 'Pärluggla', en: 'Boreal Owl', sci: 'Aegolius funereus', fam: ['Strigidae', 'Egentliga ugglor'], group: 'owls', slug: ['parluggla', 'boreal-owl'], iucn: 'LC', red: null, id: [false, false], minimal: true },
  // The only seabird, so its group page gets noindex.
  { qid: 'Q25440', sv: 'Storskarv', en: 'Great Cormorant', sci: 'Phalacrocorax carbo', fam: ['Phalacrocoracidae', 'Skarvar'], group: 'seabirds', slug: ['storskarv', 'great-cormorant'], iucn: 'LC', red: 'not_listed', id: [false, true], de: true, months: YEAR_ROUND, status: 'resident' },
  // Reviewed and written but not published: only preview builds (SPECIES_PREVIEW=1) show them.
  { qid: 'Q26209', sv: 'Större hackspett', en: 'Great Spotted Woodpecker', sci: 'Dendrocopos major', fam: ['Picidae', 'Hackspettar'], group: 'woodpeckers', slug: ['storre-hackspett', 'great-spotted-woodpecker'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident', publish: false, look: ['Q210418'] },
  { qid: 'Q210418', sv: 'Tretåig hackspett', en: 'Eurasian Three-toed Woodpecker', sci: 'Picoides tridactylus', fam: ['Picidae', 'Hackspettar'], group: 'woodpeckers', slug: ['tretaig-hackspett', 'eurasian-three-toed-woodpecker'], iucn: 'LC', red: 'NT', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident', publish: false, look: ['Q26209'] },
  // Never a page: one failed, one pending (facts exist, text not written yet).
  { qid: 'Q166171', sv: 'Gröngöling', en: 'European Green Woodpecker', sci: 'Picus viridis', fam: ['Picidae', 'Hackspettar'], group: 'woodpeckers', slug: ['grongoling', 'european-green-woodpecker'], iucn: 'LC', red: 'not_listed', id: [true, true], recordStatus: 'failed' },
  { qid: 'Q143284', sv: 'Spillkråka', en: 'Black Woodpecker', sci: 'Dryocopus martius', fam: ['Picidae', 'Hackspettar'], group: 'woodpeckers', slug: ['spillkraka', 'black-woodpecker'], iucn: 'LC', red: 'not_listed', id: [true, true], recordStatus: 'pending' },
];

const S = (text, factIds) => ({ text, factIds });
const lowerSv = (name) => name.toLocaleLowerCase('sv');

function meta(name, lang) {
  let s = lang === 'sv'
    ? `${name}: testtext för artsidornas bygge. Kännetecken, läte och när arten syns i Sverige, med foton och karta.`
    : `${name}: test text for building the species pages. Field marks, call and when it is seen in Sweden, with photos.`;
  while (s.length < 120) s += lang === 'sv' ? ' Testdata.' : ' Test data.';
  if (s.length > 155) throw new Error(`metaDescription är ${s.length} tecken: ${s}`);
  return s;
}

function textFor(sp, lang) {
  const sv = lang === 'sv';
  const name = sv ? sp.sv : sp.en;
  return {
    lead: [S(sv ? `Testtext: ${name} används som exempel när artsidorna byggs och testas.` : `Test text: the ${name} is used as an example when the species pages are built and tested.`, ['f01'])],
    fieldMarks: (sv
      ? [`Testpunkt ett om hur ${lowerSv(name)} ser ut`, 'Testpunkt två om storlek och form', 'Testpunkt tre om beteende i fält']
      : ['Test point one about what it looks like', 'Test point two about size and shape', 'Test point three about behaviour in the field']
    ).map((m) => S(m, ['f02'])),
    voice: [S(sv ? 'Testtext om lätet, skriven så att sidan går att bygga utan riktig data.' : 'Test text about the call, written so the page can be built without real data.', ['f04'])],
    whereWhen: [S(sv ? 'Testtext om var och när arten syns i Sverige.' : 'Test text about where and when it is seen in Sweden.', ['f06', 'd01'])],
    ...(sp.minimal ? {} : { behaviour: [S(sv ? 'Testtext om föda och beteende.' : 'Test text about food and behaviour.', ['f07'])] }),
    lookAlikes: (sp.look ?? []).map((other) => ({ other, text: [S(sv ? 'Testtext om hur de skiljer sig åt.' : 'Test text about how to tell them apart.', ['f09'])] })),
    metaDescription: meta(name, lang),
    facts: {
      size: sp.size ? { value: sv ? sp.size[0] : sp.size[1], factIds: ['f03'] } : null,
      swedenStatus: sp.status ? { value: sp.status, factIds: ['s01'] } : null,
    },
  };
}

function lookalikeFact(other) {
  const known = SPECIES.find((x) => x.qid === other);
  const scientific = known?.sci ?? (other === 'Q25769' ? 'Asio flammeus' : other);
  return {
    id: 'f09', topic: 'lookalike', sv: 'Testfaktum om förväxling.',
    other: other.startsWith('Q') ? { scientific, qid: other } : { scientific },
    sources: [{ article: 'sv', quote: 'Testcitat som bara finns i testdata.' }],
  };
}

function dataFor(sp) {
  const summer = sp.months === SUMMER;
  return {
    fetchedAt: '2026-10-15',
    gbifTaxonKey: 1,
    totalReports: 1000,
    months: sp.months,
    counties: Object.fromEntries(COUNTY_CODES.map((c, i) => [c, (i * 37 + sp.qid.length * 11) % 101])),
    raw: { speciesByMonth: [], allBirdsByMonth: [], speciesByCounty: {}, allBirdsByCounty: {} },
    sentences: summer
      ? {
          sv: ['Rapporteras mest i maj.', 'Nästan aldrig i november till mars.', 'Vanligast i rapporterna från Testlän, Provlän och Exempellän.'],
          en: ['Reported most in May.', 'Almost never in November to March.', 'Most common in reports from Testshire, Sampleshire and Exampleshire.'],
        }
      : {
          sv: ['Rapporteras året runt.', 'Vanligast i rapporterna från Testlän, Provlän och Exempellän.'],
          en: ['Reported all year round.', 'Most common in reports from Testshire, Sampleshire and Exampleshire.'],
        },
    statusSignal: { contradicts: null },
  };
}

function record(sp) {
  const status = sp.recordStatus ?? 'ok';
  const commons = (suffix) => `https://commons.wikimedia.org/wiki/File:Fixture_${sp.qid}_${suffix}`;
  return {
    qid: sp.qid,
    status,
    publish: status === 'ok' && sp.publish !== false,
    slug: { sv: sp.slug[0], en: sp.slug[1] },
    names: { sv: sp.sv, en: sp.en, scientific: sp.sci },
    family: { latin: sp.fam[0], sv: sp.fam[1] },
    group: sp.group,
    iucn: sp.iucn,
    ...(sp.red ? { swedishRedList: sp.red } : {}),
    identifiable: { photo: sp.id[0], sound: sp.id[1] },
    ...(sp.marginalia ? { marginalia: { sv: 'Testanteckning i marginalen.', en: 'A test note in the margin.' } } : {}),
    images: [
      { role: 'hero', file: `${sp.qid}/hero.webp`, width: 1200, height: 800, author: 'Testfotograf', license: 'CC0', licenseUrl: null, sourceUrl: commons('hero.jpg') },
      ...(sp.extra ? [{ role: 'extra', file: `${sp.qid}/extra.webp`, width: 1200, height: 800, author: 'Testfotograf två', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0/', sourceUrl: commons('extra.jpg') }] : []),
    ],
    ...(sp.audio ? {
      audio: {
        file: `${sp.qid}/voice.mp3`, durationSec: 1, trimmed: sp.audio === 'trimmed',
        author: 'Testinspelare', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0/', sourceUrl: commons('song.ogg'),
      },
    } : {}),
    wikipedia: {
      sv: { title: sp.sv, revision: '1000001' },
      ...(sp.minimal ? {} : { en: { title: sp.en, revision: '2000002' } }),
      ...(sp.de ? { de: { title: `${sp.en} (Testartikel)`, revision: '3000003' } } : {}),
    },
    ...(sp.months ? { data: dataFor(sp) } : {}),
    facts: [
      { id: 'f01', topic: 'appearance', sv: 'Testfaktum.', sources: [{ article: 'sv', quote: 'Testcitat som bara finns i testdata.' }] },
      ...(sp.look ?? []).map(lookalikeFact),
    ],
    review: { wave: status === 'pending' ? 2 : sp.publish === false ? 2 : 1 },
    ...(status !== 'pending' ? { verification: { method: 'auto', at: '2026-11-20', model: 'fixture', spotChecked: false } } : {}),
    text: status === 'ok' ? { sv: textFor(sp, 'sv'), en: textFor(sp, 'en') } : null,
    ...(status === 'failed' ? { rejectedText: null } : {}),
    generated: {
      facts: { model: 'fixture', prompt: 'fixture', effort: 'none', at: '2026-10-20' },
      ...(status === 'ok' ? { text: { model: 'fixture', prompt: 'fixture', effort: 'none', checker: 'fixture', at: '2026-11-25' } } : {}),
    },
    errors: status === 'failed' ? ['Testfel: kontrollen failade.'] : [],
  };
}

// a and b follow the Swedish slug order (appendix D); the file name has the QIDs in string order.
const COMPARISONS = [
  { file: 'Q25404_Q25485', a: 'Q25404', b: 'Q25485', slug: ['blames-eller-talgoxe', 'eurasian-blue-tit-vs-great-tit'], publish: true },
  { file: 'Q25307_Q25345384', a: 'Q25345384', b: 'Q25307', slug: ['kaja-eller-skata', 'eurasian-magpie-vs-western-jackdaw'], publish: true },
  { file: 'Q210418_Q26209', a: 'Q26209', b: 'Q210418', slug: ['storre-hackspett-eller-tretaig-hackspett', 'eurasian-three-toed-woodpecker-vs-great-spotted-woodpecker'], publish: false },
  { file: 'Q25384_Q25756', a: 'Q25384', b: 'Q25756', slug: ['hornuggla-eller-kattuggla', 'long-eared-owl-vs-tawny-owl'], status: 'pending' },
];

function comparison(c) {
  const [a, b] = [SPECIES.find((s) => s.qid === c.a), SPECIES.find((s) => s.qid === c.b)];
  const status = c.status ?? 'ok';
  const lang = (l) => {
    const sv = l === 'sv';
    const [na, nb] = sv ? [a.sv, lowerSv(b.sv)] : [a.en, b.en];
    let metaDescription = sv
      ? `${na} eller ${nb}? Testtext för jämförelsesidan: så skiljer du dem åt i fält, på storlek, färg och läte.`
      : `${na} or ${nb}? Test text for the comparison page: how to tell them apart by size, colour and call.`;
    while (metaDescription.length < 120) metaDescription += sv ? ' Testdata.' : ' Test data.';
    if (metaDescription.length > 155) throw new Error(`jämförelsens metaDescription är ${metaDescription.length} tecken`);
    return {
      shortAnswer: [S(sv ? `Testtext: det kortaste svaret på hur ${lowerSv(na)} och ${nb} skiljer sig åt.` : `Test text: the shortest answer to how the ${na} and the ${nb} differ.`, ['a:f01', 'b:f01'])],
      rows: (sv ? ['Storlek', 'Huvud', 'Läte'] : ['Size', 'Head', 'Call']).map((feature, i) => ({
        feature,
        a: S(sv ? `Testcell ${i + 1} för den första arten` : `Test cell ${i + 1} for the first species`, ['a:f02']),
        b: S(sv ? `Testcell ${i + 1} för den andra arten` : `Test cell ${i + 1} for the second species`, ['b:f02']),
      })),
      metaDescription,
    };
  };
  return {
    a: c.a,
    b: c.b,
    status,
    publish: status === 'ok' && c.publish === true,
    slug: { sv: c.slug[0], en: c.slug[1] },
    volumes: { sv: 100, en: 50 },
    text: status === 'ok' ? { sv: lang('sv'), en: lang('en') } : null,
    generated: { model: 'fixture', prompt: 'fixture', checker: 'fixture', at: '2026-11-26' },
    errors: [],
  };
}

// Colour alone distinguishes species and roles (no <text>): text rendering depends on which fonts
// are installed, so an SVG with a <text> element rasterizes to different pixels (and thus different
// WebP bytes) on Windows vs macOS vs CI. A flat-colour rectangle is byte-identical everywhere. The
// hue is a deterministic hash of the QID, so screenshots taken during testing can tell species
// apart; extra.webp is a lighter shade of the same hue as hero.webp.
function hashHue(qid) {
  let h = 0;
  for (let i = 0; i < qid.length; i++) h = (h * 31 + qid.charCodeAt(i)) >>> 0;
  return h % 360;
}
function hslToHex(h, s, l) {
  const sat = s / 100;
  const light = l / 100;
  const k = (n) => (n + h / 30) % 12;
  const a = sat * Math.min(light, 1 - light);
  const f = (n) => light - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)));
  const toHex = (x) => Math.round(255 * x).toString(16).padStart(2, '0');
  return `#${toHex(f(0))}${toHex(f(8))}${toHex(f(4))}`;
}
async function photo(qid, role) {
  const dir = resolve(OUT.assets, qid);
  mkdirSync(dir, { recursive: true });
  const hue = hashHue(qid);
  const bg = role === 'hero' ? hslToHex(hue, 55, 50) : hslToHex(hue, 55, 72);
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="800"><rect width="1200" height="800" fill="${bg}"/></svg>`;
  await sharp(Buffer.from(svg)).webp({ quality: 60 }).toFile(resolve(dir, `${role}.webp`));
}

// About one second of silent MP3 (MPEG-1 Layer III, 128 kbit/s, 44.1 kHz, mono, empty side info):
// the audio player needs a real file to point at, laid out like the pipeline's voice.mp3.
function silentMp3(frames = 38) {
  const frame = Buffer.alloc(417);
  frame[0] = 0xff; frame[1] = 0xfb; frame[2] = 0x90; frame[3] = 0xc0;
  return Buffer.concat(Array.from({ length: frames }, () => frame));
}

for (const dir of Object.values(OUT)) {
  rmSync(dir, { recursive: true, force: true });
  mkdirSync(dir, { recursive: true });
}
for (const sp of SPECIES) {
  writeFileSync(resolve(OUT.species, `${sp.qid}.json`), `${JSON.stringify(record(sp), null, 2)}\n`);
  await photo(sp.qid, 'hero');
  if (sp.extra) await photo(sp.qid, 'extra');
  if (sp.audio) writeFileSync(resolve(OUT.assets, sp.qid, 'voice.mp3'), silentMp3());
}
for (const c of COMPARISONS) writeFileSync(resolve(OUT.comparisons, `${c.file}.json`), `${JSON.stringify(comparison(c), null, 2)}\n`);
console.log(`fixtures: ${SPECIES.length} arter och ${COMPARISONS.length} jämförelser i tests/fixtures/`);
