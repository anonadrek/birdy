// node --test "tests/unit/*.unit.mjs": scripts/import-clips.mjs (spec 2026-10-09-klippsidan) and the data it wrote.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { clipFromSources, csvRecords, importClips, isAdapted, parseCsv, sortClips } from '../../scripts/import-clips.mjs';

const websiteRoot = fileURLToPath(new URL('../../', import.meta.url));
const BOM = String.fromCharCode(0xfeff);
const LF = String.fromCharCode(10);
const CRLF = String.fromCharCode(13, 10);
const EN_DASH = String.fromCharCode(0x2013);
const EM_DASH = String.fromCharCode(0x2014);

test('parseCsv: citat med komma, radbrytning och dubbla citattecken, BOM och CRLF', () => {
  const text = `${BOM}date,instagram,slug${CRLF}2026-10-09,"Hej, fågel${LF}rad två med ""citat""",eurasian-blue-tit${CRLF}`;
  assert.deepEqual(parseCsv(text), [
    ['date', 'instagram', 'slug'],
    ['2026-10-09', `Hej, fågel${LF}rad två med "citat"`, 'eurasian-blue-tit'],
  ]);
  assert.throws(() => parseCsv('a,"b'), /citattecken/);
});

test('csvRecords: rubrikraden blir nycklar, tomma rader hoppas över, fel antal fält är ett fel', () => {
  assert.deepEqual(csvRecords(`date,slug${LF}2026-10-12,mallard${LF}${LF}`), [{ date: '2026-10-12', slug: 'mallard' }]);
  assert.throws(() => csvRecords(`date,slug${LF}2026-10-12${LF}`), /fält/);
});

const row = { date: '2026-10-17', qid: 'Q25383', slug: 'eurasian-chaffinch', name_en: 'Eurasian Chaffinch', name_sv: 'Bofink', cover: 'eurasian-chaffinch/cover.jpg', caption_json: 'eurasian-chaffinch/caption.json' };
const caption = { qid: 'Q25383', names: { sv: 'Bofink', en: 'Eurasian Chaffinch', scientific: 'Fringilla coelebs' } };
const chaffinchUrl = 'https://www.phylopic.org/images/0b72dd38-2dc1-4675-990e-a46259855ce1';
const cover = { clue: 'x', silhouette: { author: 'Maxime Dahirel', licence: 'CC BY 3.0', url: chaffinchUrl, note: 'own species' } };

test('clipFromSources: datum, art, namnen ur caption.json och silhuettens kredit', () => {
  assert.deepEqual(clipFromSources(row, caption, cover), {
    date: '2026-10-17',
    qid: 'Q25383',
    slug: 'eurasian-chaffinch',
    names: { sv: 'Bofink', en: 'Eurasian Chaffinch', scientific: 'Fringilla coelebs' },
    silhouette: { author: 'Maxime Dahirel', licence: 'CC BY 3.0', url: chaffinchUrl, adapted: true },
    clue: 'x',
  });
});

test('isAdapted: CC BY och CC BY-SA är bearbetade, CC0 och public domain mark inte (bildtexternas regel)', () => {
  for (const licence of ['CC BY 3.0', 'CC BY 4.0', 'CC BY-SA 4.0']) assert.equal(isAdapted(licence), true, licence);
  for (const licence of ['CC0', 'Public domain mark']) assert.equal(isAdapted(licence), false, licence);
});

test('clipFromSources: ett tankstreck i upphovspersonens namn blir bindestreck, som i bildtexterna', () => {
  const dashed = { clue: cover.clue, silhouette: { ...cover.silhouette, author: `Anna${EN_DASH}Lena Berg` } };
  assert.equal(clipFromSources(row, caption, dashed).silhouette.author, 'Anna-Lena Berg');
});

test('clipFromSources: det sidan skulle visa fel stoppar importen', () => {
  assert.throws(() => clipFromSources({ ...row, date: '17 okt' }, caption, cover), /YYYY-MM-DD/);
  assert.throws(() => clipFromSources(row, { ...caption, qid: 'Q1' }, cover), /Q1/);
  assert.throws(() => clipFromSources(row, { ...caption, names: { ...caption.names, scientific: '' } }, cover), /scientific/);
  assert.throws(() => clipFromSources(row, { ...caption, names: { ...caption.names, sv: `Bo${EN_DASH}fink` } }, cover), /tankstreck/);
  assert.throws(() => clipFromSources({ ...row, name_sv: 'Bergfink' }, caption, cover), /namnen/);
  assert.throws(() => clipFromSources(row, caption, undefined), /silhuetten/);
  assert.throws(() => clipFromSources(row, caption, { silhouette: { ...cover.silhouette, url: 'https://example.com/x' } }), /PhyloPic/);
  assert.throws(() => clipFromSources(row, caption, { silhouette: cover.silhouette }), /ledtråden/);
});

test('sortClips: äldst först; samma datum, slug eller art två gånger är ett fel', () => {
  const a = clipFromSources(row, caption, cover);
  const b = { ...a, date: '2026-10-16', slug: 'european-robin', qid: 'Q25334' };
  assert.deepEqual(sortClips([a, b]).map((c) => c.date), ['2026-10-16', '2026-10-17']);
  assert.throws(() => sortClips([a, { ...b, date: a.date }]), /date/);
  assert.throws(() => sortClips([a, { ...b, slug: a.slug }]), /slug/);
  assert.throws(() => sortClips([a, { ...b, qid: a.qid }]), /qid/);
});

/** A social folder with one scheduled group (two clips) and a test render's schedule straight in out/. */
function socialFolder() {
  const social = mkdtempSync(join(tmpdir(), 'birdy-social-'));
  const write = (path, text) => {
    mkdirSync(dirname(join(social, path)), { recursive: true });
    writeFileSync(join(social, path), text);
  };
  const csv = (rows) => `${BOM}${['date,qid,slug,name_en,name_sv,cover,caption_json,instagram', ...rows].join(CRLF)}${CRLF}`;
  write('out/week1/schedule.csv', csv([
    `2026-10-12,Q25348,mallard,Mallard,Gräsand,mallard/cover.jpg,mallard/caption.json,"Rad ett${LF}rad två, med ""citat"""`,
    '2026-10-09,Q25404,eurasian-blue-tit,Eurasian Blue Tit,Blåmes,eurasian-blue-tit/cover.jpg,eurasian-blue-tit/caption.json,x',
  ]));
  write('out/schedule.csv', csv(['2026-10-09,Q26026,common-wood-pigeon,Common Wood Pigeon,Ringduva,common-wood-pigeon/cover.jpg,common-wood-pigeon/caption.json,x']));
  for (const [slug, qid, en, sv, scientific] of [
    ['mallard', 'Q25348', 'Mallard', 'Gräsand', 'Anas platyrhynchos'],
    ['eurasian-blue-tit', 'Q25404', 'Eurasian Blue Tit', 'Blåmes', 'Cyanistes caeruleus'],
  ]) {
    write(`out/week1/${slug}/cover.jpg`, `omslaget för ${slug}`);
    write(`out/week1/${slug}/cover-v1.jpg`, 'det gamla omslaget');
    write(`out/week1/${slug}/caption.json`, JSON.stringify({ qid, names: { sv, en, scientific } }));
  }
  write('cover/covers.json', JSON.stringify({
    Q25348: { clue: 'Tips forward to feed underwater.', silhouette: { author: 'Andy Wilson', licence: 'CC0', url: 'https://www.phylopic.org/images/97f833ff-fcd8-4113-8948-721c75372462' } },
    Q25404: { clue: 'Often hangs upside down to find food.', silhouette: { author: 'Wouter Koch', licence: 'CC0', url: 'https://www.phylopic.org/images/069c4833-e1ac-48e7-90d5-f7bd11000588' } },
  }));
  return social;
}

test('importClips: varje grupps schema men aldrig ett schema direkt i out/, data och flockomslag, gamla omslag bort, går att köra igen', () => {
  const social = socialFolder();
  const site = mkdtempSync(join(tmpdir(), 'birdy-site-'));
  try {
    mkdirSync(join(site, 'src', 'assets', 'clips'), { recursive: true });
    writeFileSync(join(site, 'src', 'assets', 'clips', 'old-bird.jpg'), 'ett omslag som inget klipp har längre');

    const result = importClips(social, site);
    assert.deepEqual(result.clips.map((c) => [c.date, c.slug]), [['2026-10-09', 'eurasian-blue-tit'], ['2026-10-12', 'mallard']]);
    assert.deepEqual(result.removed, ['old-bird.jpg']);
    const data = JSON.parse(readFileSync(join(site, 'src', 'data', 'clips.json'), 'utf8'));
    assert.deepEqual(data.clips, result.clips);
    assert.match(data._about, /import-clips\.mjs/);
    assert.deepEqual(readdirSync(join(site, 'src', 'assets', 'clips')).sort(), ['eurasian-blue-tit.jpg', 'mallard.jpg']);
    assert.equal(readFileSync(join(site, 'src', 'assets', 'clips', 'mallard.jpg'), 'utf8'), 'omslaget för mallard');

    assert.deepEqual(importClips(social, site).removed, []);
  } finally {
    rmSync(social, { recursive: true, force: true });
    rmSync(site, { recursive: true, force: true });
  }
});

test('importClips: utan källmappen, eller utan klipp, skrivs ingenting', () => {
  const social = mkdtempSync(join(tmpdir(), 'birdy-social-'));
  const site = mkdtempSync(join(tmpdir(), 'birdy-site-'));
  try {
    assert.throws(() => importClips(join(social, 'saknas'), site), /tools\/social/);
    mkdirSync(join(social, 'out', 'week1'), { recursive: true });
    mkdirSync(join(social, 'cover'), { recursive: true });
    writeFileSync(join(social, 'out', 'week1', 'schedule.csv'), 'date,qid,slug');
    writeFileSync(join(social, 'cover', 'covers.json'), '{}');
    assert.throws(() => importClips(social, site), /inget schema/);
    assert.equal(existsSync(join(site, 'src', 'data', 'clips.json')), false);
  } finally {
    rmSync(social, { recursive: true, force: true });
    rmSync(site, { recursive: true, force: true });
  }
});

test('importClips: trasig CSV i ett schema namnger gruppen och filen i felet', () => {
  const social = mkdtempSync(join(tmpdir(), 'birdy-social-'));
  const site = mkdtempSync(join(tmpdir(), 'birdy-site-'));
  try {
    mkdirSync(join(social, 'out', 'week1'), { recursive: true });
    mkdirSync(join(social, 'cover'), { recursive: true });
    writeFileSync(join(social, 'out', 'week1', 'schedule.csv'), `date,qid,slug${LF}"ostängt citat`);
    writeFileSync(join(social, 'cover', 'covers.json'), '{}');
    assert.throws(() => importClips(social, site), /week1\/schedule\.csv:.*citattecken/);
  } finally {
    rmSync(social, { recursive: true, force: true });
    rmSync(site, { recursive: true, force: true });
  }
});

test('importClips: trasig caption.json namnger gruppen och filen i felet', () => {
  const social = mkdtempSync(join(tmpdir(), 'birdy-social-'));
  const site = mkdtempSync(join(tmpdir(), 'birdy-site-'));
  const write = (path, text) => {
    mkdirSync(dirname(join(social, path)), { recursive: true });
    writeFileSync(join(social, path), text);
  };
  try {
    write('out/week1/schedule.csv', `date,qid,slug,name_en,name_sv,cover,caption_json${LF}2026-10-09,Q25404,eurasian-blue-tit,Eurasian Blue Tit,Blåmes,eurasian-blue-tit/cover.jpg,eurasian-blue-tit/caption.json${LF}`);
    write('out/week1/eurasian-blue-tit/cover.jpg', 'omslag');
    write('out/week1/eurasian-blue-tit/caption.json', '{inte json');
    write('cover/covers.json', '{}');
    assert.throws(() => importClips(social, site), /week1\/eurasian-blue-tit\/caption\.json:/);
  } finally {
    rmSync(social, { recursive: true, force: true });
    rmSync(site, { recursive: true, force: true });
  }
});

test('importClips: trasig covers.json namnger filen i felet', () => {
  const social = mkdtempSync(join(tmpdir(), 'birdy-social-'));
  const site = mkdtempSync(join(tmpdir(), 'birdy-site-'));
  try {
    mkdirSync(join(social, 'out', 'week1'), { recursive: true });
    mkdirSync(join(social, 'cover'), { recursive: true });
    writeFileSync(join(social, 'out', 'week1', 'schedule.csv'), 'date,qid,slug');
    writeFileSync(join(social, 'cover', 'covers.json'), '{inte json');
    assert.throws(() => importClips(social, site), /cover\/covers\.json:/);
  } finally {
    rmSync(social, { recursive: true, force: true });
    rmSync(site, { recursive: true, force: true });
  }
});

test('importClips: två klipp med samma datum i olika grupper namnger out-mappen i felet', () => {
  const social = mkdtempSync(join(tmpdir(), 'birdy-social-'));
  const site = mkdtempSync(join(tmpdir(), 'birdy-site-'));
  const write = (path, text) => {
    mkdirSync(dirname(join(social, path)), { recursive: true });
    writeFileSync(join(social, path), text);
  };
  try {
    const header = 'date,qid,slug,name_en,name_sv,cover,caption_json';
    write('out/week1/schedule.csv', `${header}${LF}2026-10-09,Q25404,eurasian-blue-tit,Eurasian Blue Tit,Blåmes,eurasian-blue-tit/cover.jpg,eurasian-blue-tit/caption.json${LF}`);
    write('out/week1-reserves/schedule.csv', `${header}${LF}2026-10-09,Q25348,mallard,Mallard,Gräsand,mallard/cover.jpg,mallard/caption.json${LF}`);
    for (const [slug, qid, en, sv, scientific, group] of [
      ['eurasian-blue-tit', 'Q25404', 'Eurasian Blue Tit', 'Blåmes', 'Cyanistes caeruleus', 'week1'],
      ['mallard', 'Q25348', 'Mallard', 'Gräsand', 'Anas platyrhynchos', 'week1-reserves'],
    ]) {
      write(`out/${group}/${slug}/cover.jpg`, `omslaget för ${slug}`);
      write(`out/${group}/${slug}/caption.json`, JSON.stringify({ qid, names: { sv, en, scientific } }));
    }
    write('cover/covers.json', JSON.stringify({
      Q25404: { clue: 'Often hangs upside down to find food.', silhouette: { author: 'Wouter Koch', licence: 'CC0', url: 'https://www.phylopic.org/images/069c4833-e1ac-48e7-90d5-f7bd11000588' } },
      Q25348: { clue: 'Tips forward to feed underwater.', silhouette: { author: 'Andy Wilson', licence: 'CC0', url: 'https://www.phylopic.org/images/97f833ff-fcd8-4113-8948-721c75372462' } },
    }));
    assert.throws(() => importClips(social, site), (err) => err instanceof Error && err.message.startsWith(`${join(social, 'out')}:`) && /samma date/.test(err.message));
  } finally {
    rmSync(social, { recursive: true, force: true });
    rmSync(site, { recursive: true, force: true });
  }
});

test('importClips: ett klipp vars omslag pekar på ett annat klipps slug är ett fel', () => {
  const social = mkdtempSync(join(tmpdir(), 'birdy-social-'));
  const site = mkdtempSync(join(tmpdir(), 'birdy-site-'));
  const write = (path, text) => {
    mkdirSync(dirname(join(social, path)), { recursive: true });
    writeFileSync(join(social, path), text);
  };
  try {
    write('out/week1/schedule.csv', `date,qid,slug,name_en,name_sv,cover,caption_json${LF}2026-10-09,Q25404,eurasian-blue-tit,Eurasian Blue Tit,Blåmes,mallard/cover.jpg,eurasian-blue-tit/caption.json${LF}`);
    write('out/week1/eurasian-blue-tit/caption.json', JSON.stringify({ qid: 'Q25404', names: { sv: 'Blåmes', en: 'Eurasian Blue Tit', scientific: 'Cyanistes caeruleus' } }));
    write('out/week1/mallard/cover.jpg', 'omslaget för mallard');
    write('cover/covers.json', JSON.stringify({ Q25404: { silhouette: { author: 'Wouter Koch', licence: 'CC0', url: 'https://www.phylopic.org/images/069c4833-e1ac-48e7-90d5-f7bd11000588' } } }));
    assert.throws(() => importClips(social, site), /eurasian-blue-tit.*mallard\/cover\.jpg/);
  } finally {
    rmSync(social, { recursive: true, force: true });
    rmSync(site, { recursive: true, force: true });
  }
});

// The data the script wrote from the real schedule (Step 5): every clip must be exactly one day after the one
// before it, starting at the first clip, so a deleted render folder that silently drops an already-posted clip
// (a gap, not just a reorder) fails this test.
const addDays = (isoDate, days) => {
  const date = new Date(`${isoDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
};

test('src/data/clips.json: ett klipp om dagen 9 oktober till 7 november, ett omslag per klipp och inga andra', () => {
  const { clips } = JSON.parse(readFileSync(join(websiteRoot, 'src', 'data', 'clips.json'), 'utf8'));
  assert.ok(clips.length >= 30, `${clips.length} klipp`);
  assert.equal(clips[0].date, '2026-10-09');
  assert.deepEqual(clips.map((c) => c.date), clips.map((_, i) => addDays(clips[0].date, i)));
  const at = (date) => clips.find((c) => c.date === date);
  assert.deepEqual([at('2026-10-09').qid, at('2026-10-09').names.sv, at('2026-10-09').silhouette.author], ['Q25404', 'Blåmes', 'Wouter Koch']);
  assert.deepEqual([at('2026-10-17').names.sv, at('2026-10-17').silhouette.licence, at('2026-10-17').silhouette.adapted], ['Bofink', 'CC BY 3.0', true]);
  assert.deepEqual([at('2026-11-07').slug, at('2026-11-07').names.scientific], ['brambling', 'Fringilla montifringilla']);
  for (const c of clips) assert.equal(c.silhouette.adapted, isAdapted(c.silhouette.licence), c.slug);
  for (const c of clips) {
    assert.ok(c.clue && c.clue.trim().length > 0, `${c.slug}: ledtråden saknas`);
    assert.ok(!c.clue.includes(EN_DASH) && !c.clue.includes(EM_DASH), `${c.slug}: ledtråden har ett tankstreck`);
  }
  assert.deepEqual(readdirSync(join(websiteRoot, 'src', 'assets', 'clips')).filter((f) => f.endsWith('.jpg')).sort(), clips.map((c) => `${c.slug}.jpg`).sort());
});
