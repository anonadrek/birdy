# Klippsidan: every See the song clip, with a link to its bird · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `/clips/` (English) and `/sv/klipp/` (Swedish) to birdy.community: every "See the song" clip posted so far, newest first, each card with the clip's flock cover, the bird's name, the posting day, a link to the bird's species page when that page is published, and the silhouette's credit; plus a footer link next to the social channels. The page becomes the bio link on Instagram and TikTok.

**Architecture:** A re-runnable Node script copies the clips' schedule (dates, species, names) and silhouette credits from the social videos' worktree into `website/src/data/clips.json`, and the flock covers into `website/src/assets/clips/`. A pure module, `src/lib/clips.mjs`, holds every rule (which clips show on a date, newest first, the link guard, the credit). A Vite virtual module, `virtual:birdy-clips`, built in `astro.config.mjs` like Dagens fågel's, imports only the covers of the clips posted by the build's date (`buildDate()`: Europe/Stockholm, `BIRDY_TODAY` in test builds), so the nightly rebuild adds each day's clip and a later clip's cover is never in `dist/` early. One Astro component renders both language pages.

**Tech Stack:** Astro 7 (`.astro`, scoped styles, `astro:assets` `<Image>`), plain JS modules with JSDoc (`.mjs`), a Vite plugin, `node --test`, Playwright 1.60 + `@axe-core/playwright`, the site's guard scripts. No new dependency.

---

## Context for the implementer

**The spec** (approved by Albin 2026-10-09): `docs/superpowers/specs/2026-10-09-klippsidan-design.md`. Read it once; it is short.

**Where the data comes from.** The clips are rendered in the worktree `C:/w/birdy-social` (branch `social/see-the-song`), folder `tools/social/`, here called "the social folder". Its `out/` is gitignored, so this plan copies what the site needs into the website.
- One `schedule.csv` per scheduled group: `out/week1/` (9 to 15 Oct, 7 clips), `out/week1-reserves/` (Rödhake, Bofink, Fiskmås, 16 to 18 Oct) and `out/oct19-nov7/` (20 clips): 30 clips, one a day, 9 Oct to 7 Nov 2026. **`out/schedule.csv`, directly in `out/`, is not a combined schedule:** it is a leftover from a test render (Ringduva 9 Oct and Skata 10 Oct, `publish` false) and must never be read. The CSVs start with a BOM and hold quoted, multi-line captions. Their `publish` column says whether the species page was published when the video was rendered (Bergfink: false); this plan does not use it, the site decides links when it is built.
- Per clip, `out/<group>/<slug>/`: `cover.jpg` is the flock cover (1080×1920, 180 to 280 kB, byte-identical to `cover-flock.jpg`; `cover-v1.jpg` is the old cover and is never used) and `caption.json` (`qid`, `names.sv`, `names.en`, `names.scientific`, the captions).
- `cover/covers.json`, keyed by QID: `silhouette.author`, `silhouette.licence` (CC0, CC BY 3.0, CC BY 4.0 or Public domain mark), `silhouette.url` (the silhouette's PhyloPic page) and `silhouette.note` (not used).
- The captions' credit (`tools/social/lib/captions.mjs`): "Silhouette: Andy Wilson, CC0, via PhyloPic", with ", adapted" after a CC BY or CC BY-SA silhouette (the cover recolours it), and dashes in a person's name written as hyphens. Five of the 30 are CC BY: Bofink (17 Oct), Sångsvan (21 Oct), Nötväcka (25 Oct), Gråhäger (28 Oct), Bergfink (7 Nov).

**Decisions in this plan, beyond the spec's words:**
1. The covers come in through a virtual module, not `import.meta.glob`. `astro.config.mjs` explains why for the species photos: Vite emits every globbed image into `dist/_astro/`, used or not, so a glob would put every later clip's cover online before its day. Task 5 checks `dist/_astro/`.
2. The link guard is "this build has the species page" (`getAllSpecies()`), the same rule as the field notes' species links on `website/socials` (`unwrapUnbuiltSpeciesLinks`). In Production that is exactly the published pages; a Vercel Preview build with `SPECIES_PREVIEW=1` also links verified unpublished pages, which exist there with noindex. With a page, the card shows the species page's name; without one, the clip's own name (spec, item 4).
3. The Swedish page's credit is Swedish: "Siluett: Andy Wilson, CC0, via PhyloPic" and ", bearbetad"; licence names stay as written. The licence links to its Creative Commons deed (`deed.sv` on the Swedish page, like the species pages' credits) and "PhyloPic" links to the silhouette's page: CC BY 3.0 and 4.0 ask for a link to the licence, which an Instagram caption can't give.
4. "Lazy loading except the first row": the first four cards (one row on a wide screen) load eagerly.
5. No sitemap `lastmod` for the clips pages: `tests/home.spec.ts` asserts that only field notes and species pages carry one.
6. Both pages are held to `check-seo.mjs`'s full rules (its `NEW` list): title 40 to 60 characters, description 120 to 155, breadcrumbs with a matching BreadcrumbList, hreflang both ways, in the sitemap. So the page has breadcrumbs, "Birdy › Clips", like the Premium page. The copy below is counted: titles 51 and 52 characters, descriptions 138 and 141.
7. Timing, accepted by the spec and flagged to the controller: the nightly build runs at 00.05 Swedish time and the posts go out at 08.00, so a day's clip is on the page about eight hours before its post. The spec says "from its posting day".
8. The scientific name stays in the data but is not on the card (the spec lists what a card shows).

**Builds and the test data.** Playwright serves `dist/` with `astro preview`, so run `npm run build:fixtures` before every Playwright run. The fixture build pins `BIRDY_TODAY=2026-10-15`: the page shows the seven clips 9 to 15 Oct (Blåmes, Trana, Gråsparv, Gräsand, Koltrast, Ormvråk, Talgoxe); all seven species have a published page in the test data (`tests/fixtures/species/`), and all seven silhouettes are CC0 (Wouter Koch for Blåmes, Anthony Caravaggi for Koltrast, Andy Wilson for the other five). The empty build (`npm run test:empty-hub`) has no species page and the real date, so its clips show without links, and its `check-seo` fails on any dead link: that tests the no-link path end to end. The unlinked and CC BY cards are only seen in Task 6's look at a build pinned to 7 Nov (30 cards, 11 with links, 5 adapted). The empty state ("the first clip is coming soon") only shows in a build dated before 9 Oct; it is covered by a unit test of the generated module, not by Playwright.

**Commands.** Work in bash in the worktree `C:/w/birdy-klipp` (branch `website/klipp`, from `main` at `28a5b748`, not pushed). Every website command runs in `C:/w/birdy-klipp/website`; each command below starts with its own `cd`, since a new shell may start elsewhere. Playwright: `PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test <files> --workers=2`. Port 4761 belongs to this worktree; Playwright reuses whatever already listens there, so Task 1 checks that it is free. Run at most one heavy job (`npm ci`, a build, Playwright, `astro check`) at a time, never two in parallel or one in the background next to another.

**House rules.** No dashes (U+2013, U+2014) in copy or data (`test:no-dashes`, which Task 2 extends to `clips.json`); code comments in English like the surrounding code; unit and Playwright test names in Swedish like the rest; commit messages in Swedish, ending with the trailer line shown in each commit step; no new dependency; do not edit `CLAUDE.md` (the controller updates the status on `main`); never push to or merge into `main`: Task 6 pushes this branch for a Vercel preview and stops. Create files with the Write and Edit tools, not with shell heredocs (heredocs have dropped backslashes on this machine), and keep the `String.fromCharCode` forms this plan uses for invisible and dash characters (a backslash-u escape typed into a tool call can turn into the real character).

**Shared files, kept small for the later merges.** `website/flocken-lyfter` (worktree `C:/w/birdy-flock`) changes the copy decks' `hero` and `daily` blocks, `Hero.astro`, `Nav.astro`, `HomePage.astro`, `MonthBirds.astro`, `tokens.css`, `check-contrast.mjs` and `check-empty-hub.mjs`; `website/socials` changes the footer's follow heading id and `check-empty-hub.mjs`. This plan touches none of those except the copy decks, where it only inserts a new `clips` block just before `"footer"` (no existing line changes). Its other shared edits: in `Footer.astro` one import line, one link after the channel list's `</ul>` and one CSS rule; the `NEW` list in `check-seo.mjs`; one entry in `check-no-dashes.mjs`; one script in `package.json`; the plugin in `astro.config.mjs`. No new colour pair: the page uses `--ink`, `--muted` and `--rust` on `--peach` and `--card`, which `check-contrast.mjs` already checks.

**Out of scope (spec):** links from the species pages to the clips, post addresses, embedded players, Albin's bio-link steps in the Instagram and TikTok apps.

## File structure

| File | Responsibility | Task |
|---|---|---|
| `website/scripts/import-clips.mjs` | Reads the social folder's schedules, captions and silhouette credits; writes `clips.json`, copies the covers, removes stale covers | 2 |
| `website/tests/unit/import-clips.unit.mjs` | CSV parsing, the per-clip checks, the import in temporary folders, the committed data | 2 |
| `website/src/data/clips.json` | Generated: the 30 clips (date, QID, slug, names, silhouette credit), oldest first | 2 |
| `website/src/assets/clips/<slug>.jpg` | Generated: the 30 flock covers | 2 |
| `website/scripts/check-no-dashes.mjs` | Also checks `clips.json` | 2 |
| `website/package.json` | `npm run clips:import` | 2 |
| `website/src/lib/clips.mjs` | `clipsHref`, `loadClips`, `visibleClips`, `clipsModuleSource`, `clipLink`, `licenceDeed`, `creditParts` | 3 |
| `website/tests/unit/clips.unit.mjs` | Unit tests for `clips.mjs` | 3 |
| `website/astro.config.mjs` | The virtual module `virtual:birdy-clips` | 3 |
| `website/src/lib/virtual-clips.d.ts` | Its types | 3 |
| `website/src/content/copy.en.json`, `website/src/content/copy.sv.json` | The `clips` block | 4 |
| `website/src/components/clips/ClipsPage.astro` | Both pages | 4 |
| `website/src/pages/clips.astro`, `website/src/pages/sv/klipp.astro` | The routes | 4 |
| `website/scripts/check-seo.mjs` | The clips pages under the full rules | 4 |
| `website/tests/clips.spec.ts` | Playwright: the pages (Task 4); footer, network, narrow screens, the build, axe (Task 5) | 4, 5 |
| `website/src/components/Footer.astro` | The link next to the channels | 5 |

---

### Task 1: Install and baselines

**Files:** none in the repo. QA output goes to `C:/w/birdy-klipp-qa/` (outside the repo).

- [ ] **Step 1: Check the worktree, the source folder and the port**

```bash
cd C:/w/birdy-klipp && git status --short && git branch --show-current && git log --oneline -2
ls C:/w/birdy-social/tools/social/out/week1/schedule.csv C:/w/birdy-social/tools/social/out/week1-reserves/schedule.csv C:/w/birdy-social/tools/social/out/oct19-nov7/schedule.csv C:/w/birdy-social/tools/social/cover/covers.json
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:4761/ || true
mkdir -p C:/w/birdy-klipp-qa
```

Expected: a clean tree on `website/klipp`, the plan's commit on top of `28a5b748`; the four files listed; curl prints `000` (nothing listens on 4761). If anything answers on 4761, stop and ask the controller.

- [ ] **Step 2: Install**

```bash
cd C:/w/birdy-klipp/website && npm ci
```

Expected: `added N packages` and no error.

- [ ] **Step 3: Baselines, one at a time**

```bash
cd C:/w/birdy-klipp/website && npm run test:unit 2>&1 | tail -8
cd C:/w/birdy-klipp/website && npm run test:i18n && npm run test:no-accuracy && npm run test:contrast && npm run test:no-dashes && npm run test:palette
cd C:/w/birdy-klipp/website && npm run build:fixtures && npm run test:seo
cd C:/w/birdy-klipp/website && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test --workers=2 2>&1 | tail -5
cd C:/w/birdy-klipp/website && npm run test:empty-hub
cd C:/w/birdy-klipp/website && npx astro check 2>&1 | tail -3
```

Expected: the unit tests pass (note the count); every guard prints OK; `check-seo OK`; Playwright ends with `N passed` (note N); `check-empty-hub OK` and a second `check-seo OK`; `astro check` prints a `Result (... files):` line (note its error count; Task 6 must not add errors). If anything is red on this unchanged branch, stop and report it to the controller instead of fixing it. No commit in this task.

---

### Task 2: The clips data from the schedule

**Files:**
- Create: `website/scripts/import-clips.mjs`
- Create: `website/tests/unit/import-clips.unit.mjs`
- Create (generated in Step 5): `website/src/data/clips.json`, `website/src/assets/clips/*.jpg`
- Modify: `website/scripts/check-no-dashes.mjs:81`, `website/package.json:30`

- [ ] **Step 1: Write the failing tests**

Create `website/tests/unit/import-clips.unit.mjs`:

```js
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
  });
});

test('isAdapted: CC BY och CC BY-SA är bearbetade, CC0 och public domain mark inte (bildtexternas regel)', () => {
  for (const licence of ['CC BY 3.0', 'CC BY 4.0', 'CC BY-SA 4.0']) assert.equal(isAdapted(licence), true, licence);
  for (const licence of ['CC0', 'Public domain mark']) assert.equal(isAdapted(licence), false, licence);
});

test('clipFromSources: ett tankstreck i upphovspersonens namn blir bindestreck, som i bildtexterna', () => {
  const dashed = { silhouette: { ...cover.silhouette, author: `Anna${EN_DASH}Lena Berg` } };
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
    Q25348: { silhouette: { author: 'Andy Wilson', licence: 'CC0', url: 'https://www.phylopic.org/images/97f833ff-fcd8-4113-8948-721c75372462' } },
    Q25404: { silhouette: { author: 'Wouter Koch', licence: 'CC0', url: 'https://www.phylopic.org/images/069c4833-e1ac-48e7-90d5-f7bd11000588' } },
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

// The data the script wrote from the real schedule (Step 5). The first 30 days are pinned here; later clips must keep
// one a day, in order, each with its cover.
const isoDay = (offset) => new Date(Date.UTC(2026, 9, 9 + offset)).toISOString().slice(0, 10);

test('src/data/clips.json: ett klipp om dagen 9 oktober till 7 november, ett omslag per klipp och inga andra', () => {
  const { clips } = JSON.parse(readFileSync(join(websiteRoot, 'src', 'data', 'clips.json'), 'utf8'));
  assert.ok(clips.length >= 30, `${clips.length} klipp`);
  assert.deepEqual(clips.slice(0, 30).map((c) => c.date), Array.from({ length: 30 }, (_, i) => isoDay(i)));
  for (let i = 1; i < clips.length; i += 1) assert.ok(clips[i - 1].date < clips[i].date, `${clips[i].slug} efter ${clips[i - 1].slug}`);
  const at = (date) => clips.find((c) => c.date === date);
  assert.deepEqual([at('2026-10-09').qid, at('2026-10-09').names.sv, at('2026-10-09').silhouette.author], ['Q25404', 'Blåmes', 'Wouter Koch']);
  assert.deepEqual([at('2026-10-17').names.sv, at('2026-10-17').silhouette.licence, at('2026-10-17').silhouette.adapted], ['Bofink', 'CC BY 3.0', true]);
  assert.deepEqual([at('2026-11-07').slug, at('2026-11-07').names.scientific], ['brambling', 'Fringilla montifringilla']);
  for (const c of clips) assert.equal(c.silhouette.adapted, isAdapted(c.silhouette.licence), c.slug);
  assert.deepEqual(readdirSync(join(websiteRoot, 'src', 'assets', 'clips')).sort(), clips.map((c) => `${c.slug}.jpg`).sort());
});
```

- [ ] **Step 2: Run them and watch them fail**

```bash
cd C:/w/birdy-klipp/website && node --test tests/unit/import-clips.unit.mjs 2>&1 | tail -6
```

Expected: FAIL, `Cannot find module '.../scripts/import-clips.mjs'`.

- [ ] **Step 3: Write the script**

Create `website/scripts/import-clips.mjs`:

```js
#!/usr/bin/env node
// Writes the clips page's data (spec docs/superpowers/specs/2026-10-09-klippsidan-design.md) from the See the song
// schedule, so the page and the posts always have the same days:
//   node scripts/import-clips.mjs [social-folder]        (npm run clips:import -- [social-folder])
// social-folder is tools/social on the branch social/see-the-song (default: the worktree C:/w/birdy-social on Albin's
// Windows machine). The script reads out/<group>/schedule.csv for every scheduled group (a schedule.csv straight in
// out/ belongs to a test render and is never read), each clip's out/<group>/<slug>/caption.json (the names, the
// scientific one included) and cover/covers.json (the silhouette's credit). It writes src/data/clips.json, oldest
// first, and copies each clip's flock cover (out/<group>/<slug>/cover.jpg) to src/assets/clips/<slug>.jpg. A cover
// that no clip uses any more is removed, so the script can simply be run again when the schedule grows.
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

export const DEFAULT_SOURCE = 'C:/w/birdy-social/tools/social';
const ABOUT = 'Written by scripts/import-clips.mjs from the See the song schedule (tools/social on the branch social/see-the-song). Do not edit by hand: run the script again when the schedule changes.';

// Characters built with fromCharCode, not escapes, so the source is unambiguous on disk (as in check-no-dashes.mjs).
const BOM = String.fromCharCode(0xfeff);
const CR = String.fromCharCode(13);
const LF = String.fromCharCode(10);
const DASH_CHARS = `[${String.fromCharCode(0x2013)}${String.fromCharCode(0x2014)}]`;
const HAS_DASH = new RegExp(DASH_CHARS);
const ALL_DASHES = new RegExp(DASH_CHARS, 'g');
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const QID = /^Q\d+$/;
const SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

/**
 * RFC 4180 CSV as rows of fields: a quoted field may hold commas, line breaks and doubled quotes. A leading BOM is
 * dropped (the schedules start with one).
 * @param {string} text
 * @returns {string[][]}
 */
export function parseCsv(text) {
  const src = text.startsWith(BOM) ? text.slice(1) : text;
  const rows = [];
  let row = [];
  let field = '';
  let quoted = false;
  for (let i = 0; i < src.length; i += 1) {
    const ch = src[i];
    if (quoted) {
      if (ch !== '"') field += ch;
      else if (src[i + 1] === '"') {
        field += '"';
        i += 1;
      } else quoted = false;
    } else if (ch === '"') quoted = true;
    else if (ch === ',') {
      row.push(field);
      field = '';
    } else if (ch === CR || ch === LF) {
      if (ch === CR && src[i + 1] === LF) i += 1;
      row.push(field);
      rows.push(row);
      row = [];
      field = '';
    } else field += ch;
  }
  if (quoted) throw new Error('CSV: ett citattecken stängs aldrig');
  if (field !== '' || row.length > 0) {
    row.push(field);
    rows.push(row);
  }
  return rows;
}

/**
 * The CSV's rows as objects keyed by its header row. Blank lines are skipped; a row with another number of fields
 * than the header is an error.
 * @param {string} text
 * @returns {Record<string, string>[]}
 */
export function csvRecords(text) {
  const [header, ...rows] = parseCsv(text).filter((r) => !(r.length === 1 && r[0] === ''));
  if (!header) return [];
  return rows.map((r, n) => {
    if (r.length !== header.length) throw new Error(`CSV: rad ${n + 2} har ${r.length} fält, rubrikraden ${header.length}`);
    return Object.fromEntries(header.map((key, i) => [key, r[i]]));
  });
}

/**
 * "adapted" for a CC BY or CC BY-SA silhouette: the cover recolours and resizes it. The captions' rule
 * (tools/social/lib/captions.mjs, silhouetteCreditPart); CC0 and the public domain mark ask for nothing.
 * @param {string} licence
 */
export const isAdapted = (licence) => /^cc by\b/i.test(String(licence).trim());

/**
 * A person's name as the captions write it (captions.mjs, cleanName): trimmed, any dash written as a hyphen.
 * @param {unknown} name
 */
export const cleanName = (name) => String(name ?? '').trim().replace(ALL_DASHES, '-');

/**
 * One clip for src/data/clips.json from its schedule row, its caption.json and its covers.json entry. Throws, naming
 * the clip, on anything the page would show wrong.
 * @param {Record<string, string>} row
 * @param {{ qid?: string, names?: { sv?: string, en?: string, scientific?: string } }} caption
 * @param {{ silhouette?: { author?: string, licence?: string, url?: string } } | undefined} cover
 */
export function clipFromSources(row, caption, cover) {
  const where = `${row.date} ${row.slug}`;
  if (!ISO_DATE.test(row.date ?? '')) throw new Error(`${where}: datumet ska vara YYYY-MM-DD`);
  if (!QID.test(row.qid ?? '')) throw new Error(`${where}: ${row.qid} är inget QID`);
  if (!SLUG.test(row.slug ?? '')) throw new Error(`${where}: ogiltig slug`);
  if (caption.qid !== row.qid) throw new Error(`${where}: caption.json gäller ${caption.qid}, schemat ${row.qid}`);
  const names = { sv: caption.names?.sv ?? '', en: caption.names?.en ?? '', scientific: caption.names?.scientific ?? '' };
  for (const [key, value] of Object.entries(names)) {
    if (!value.trim()) throw new Error(`${where}: names.${key} saknas i caption.json`);
    if (HAS_DASH.test(value)) throw new Error(`${where}: names.${key} har ett tankstreck`);
  }
  if (names.sv !== row.name_sv || names.en !== row.name_en) {
    throw new Error(`${where}: namnen i caption.json (${names.sv}, ${names.en}) är inte schemats (${row.name_sv}, ${row.name_en})`);
  }
  const s = cover?.silhouette;
  if (!s) throw new Error(`${where}: covers.json saknar silhuetten för ${row.qid}`);
  const author = cleanName(s.author);
  const licence = String(s.licence ?? '').trim();
  const url = String(s.url ?? '').trim();
  if (!author || !licence) throw new Error(`${where}: silhuetten saknar upphovsperson eller licens`);
  if (!url.startsWith('https://www.phylopic.org/')) throw new Error(`${where}: silhuettens adress ska vara en sida på PhyloPic, inte ${url}`);
  return { date: row.date, qid: row.qid, slug: row.slug, names, silhouette: { author, licence, url, adapted: isAdapted(licence) } };
}

/**
 * The clips oldest first. Two clips with the same date, slug or species is an error: one clip a day, and the slug
 * names the cover's file.
 * @template {{ date: string, slug: string, qid: string }} T
 * @param {T[]} clips
 * @returns {T[]}
 */
export function sortClips(clips) {
  for (const key of /** @type {const} */ (['date', 'slug', 'qid'])) {
    const seen = new Set();
    for (const clip of clips) {
      if (seen.has(clip[key])) throw new Error(`två klipp har samma ${key}: ${clip[key]}`);
      seen.add(clip[key]);
    }
  }
  return [...clips].sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
}

/**
 * Reads the schedule under `source` (the tools/social folder) and writes src/data/clips.json and
 * src/assets/clips/<slug>.jpg under `websiteRoot`. Every clip is checked before anything is written.
 * @param {string} source
 * @param {string} websiteRoot
 * @returns {{ clips: ReturnType<typeof clipFromSources>[], removed: string[] }}
 */
export function importClips(source, websiteRoot) {
  const out = join(source, 'out');
  const coversFile = join(source, 'cover', 'covers.json');
  if (!existsSync(out) || !existsSync(coversFile)) {
    throw new Error(`${source} har inte out/ och cover/covers.json: ange mappen tools/social på grenen social/see-the-song`);
  }
  const covers = JSON.parse(readFileSync(coversFile, 'utf8'));
  const groups = readdirSync(out, { withFileTypes: true })
    .filter((d) => d.isDirectory() && existsSync(join(out, d.name, 'schedule.csv')))
    .map((d) => d.name)
    .sort();
  const clips = [];
  /** @type {Map<string, string>} slug to the cover file in the social folder */
  const coverFiles = new Map();
  for (const group of groups) {
    const dir = join(out, group);
    for (const row of csvRecords(readFileSync(join(dir, 'schedule.csv'), 'utf8'))) {
      if (basename(row.cover ?? '') !== 'cover.jpg') throw new Error(`${group}: ${row.slug} har omslaget ${row.cover}, ska vara flockomslaget cover.jpg`);
      const coverFile = join(dir, row.cover);
      if (!existsSync(coverFile)) throw new Error(`${group}: omslaget ${row.cover} saknas`);
      const caption = JSON.parse(readFileSync(join(dir, row.caption_json), 'utf8'));
      clips.push(clipFromSources(row, caption, covers[row.qid]));
      coverFiles.set(row.slug, coverFile);
    }
  }
  const sorted = sortClips(clips);
  if (sorted.length === 0) throw new Error(`${out}: inget schema har några klipp`);

  const assets = join(websiteRoot, 'src', 'assets', 'clips');
  mkdirSync(assets, { recursive: true });
  for (const [slug, file] of coverFiles) copyFileSync(file, join(assets, `${slug}.jpg`));
  const removed = readdirSync(assets).filter((f) => f.endsWith('.jpg') && !coverFiles.has(f.slice(0, -'.jpg'.length))).sort();
  for (const f of removed) rmSync(join(assets, f));

  const dataFile = join(websiteRoot, 'src', 'data', 'clips.json');
  mkdirSync(dirname(dataFile), { recursive: true });
  writeFileSync(dataFile, `${JSON.stringify({ _about: ABOUT, clips: sorted }, null, 2)}${LF}`);
  return { clips: sorted, removed };
}

function main() {
  const websiteRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  const source = resolve(process.argv[2] ?? DEFAULT_SOURCE);
  const { clips, removed } = importClips(source, websiteRoot);
  const gone = removed.length ? `; borttagna omslag: ${removed.join(', ')}` : '';
  console.log(`${clips.length} klipp, ${clips[0].date} till ${clips.at(-1).date}, omslagen i src/assets/clips/${gone}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    main();
  } catch (e) {
    console.error(`import-clips: ${e instanceof Error ? e.message : e}`);
    process.exit(1);
  }
}
```

- [ ] **Step 4: Run the tests: all but the data test pass**

```bash
cd C:/w/birdy-klipp/website && node --test tests/unit/import-clips.unit.mjs 2>&1 | tail -12
```

Expected: `pass 9`, `fail 1`; the one failure is the `src/data/clips.json` test with `ENOENT` (the data is not written yet).

- [ ] **Step 5: The npm script, then the real import**

In `website/package.json`, after the line `"app-species:snapshot": "node scripts/app-species-snapshot.mjs",` add:

```json
    "clips:import": "node scripts/import-clips.mjs",
```

Then run it against the social worktree (the default folder):

```bash
cd C:/w/birdy-klipp/website && npm run clips:import && ls src/assets/clips | wc -l && head -20 src/data/clips.json
```

Expected: `30 klipp, 2026-10-09 till 2026-11-07, omslagen i src/assets/clips/`, then `30`, then the file starting with `"_about"` and the first clip (2026-10-09, Q25404, `eurasian-blue-tit`, Blåmes, Wouter Koch, CC0, `"adapted": false`). If the script stops with an error, read it: it names the clip and the problem. Do not edit the social folder (another session owns it); report to the controller.

- [ ] **Step 6: All unit tests pass**

```bash
cd C:/w/birdy-klipp/website && node --test tests/unit/import-clips.unit.mjs 2>&1 | tail -8 && npm run test:unit 2>&1 | tail -8
```

Expected: `pass 10`, `fail 0`; then the whole unit suite passes (baseline + 10).

- [ ] **Step 7: The dash guard reads the data too**

In `website/scripts/check-no-dashes.mjs`, replace the line `  'src/data/species-groups.json',` (line 81) with:

```js
  'src/data/species-groups.json',
  // The clips page's data (scripts/import-clips.mjs): the bird names and silhouette credits are shown on the page.
  'src/data/clips.json',
```

```bash
cd C:/w/birdy-klipp/website && npm run test:no-dashes
```

Expected: `no-dashes OK (N filer)`, one file more than in the baseline.

- [ ] **Step 8: Commit**

```bash
cd C:/w/birdy-klipp && git add website/scripts/import-clips.mjs website/tests/unit/import-clips.unit.mjs website/src/data/clips.json website/src/assets/clips website/scripts/check-no-dashes.mjs website/package.json && git commit -m "feat(webb): klippdatan ur schemat för See the song, 30 klipp med flockomslag" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: The clips logic and the virtual module

**Files:**
- Create: `website/src/lib/clips.mjs`
- Create: `website/tests/unit/clips.unit.mjs`
- Create: `website/src/lib/virtual-clips.d.ts`
- Modify: `website/astro.config.mjs:11`, `:221`, `:244`

- [ ] **Step 1: Write the failing tests**

Create `website/tests/unit/clips.unit.mjs`:

```js
// node --test "tests/unit/*.unit.mjs": the clips page's rules (src/lib/clips.mjs, spec 2026-10-09-klippsidan).
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { clipLink, clipsHref, clipsModuleSource, creditParts, licenceDeed, visibleClips } from '../../src/lib/clips.mjs';

const LF = String.fromCharCode(10);
const clip = (date, slug, qid) => ({
  date,
  qid,
  slug,
  names: { sv: `${slug} (sv)`, en: `${slug} (en)`, scientific: 'Genus species' },
  silhouette: { author: 'Andy Wilson', licence: 'CC0', url: 'https://www.phylopic.org/images/abfaf52a-6fa1-48b7-91ba-b65d369a2fe2', adapted: false },
});
const all = [clip('2026-10-09', 'eurasian-blue-tit', 'Q25404'), clip('2026-10-10', 'common-crane', 'Q4764'), clip('2026-10-16', 'european-robin', 'Q25334')];

test('clipsHref: /clips/ på engelska och /sv/klipp/ på svenska', () => {
  assert.equal(clipsHref('en'), '/clips/');
  assert.equal(clipsHref('sv'), '/sv/klipp/');
});

test('visibleClips: klippen till och med dagens datum, nyaste först, utan att ändra listan', () => {
  const before = JSON.stringify(all);
  assert.deepEqual(visibleClips(all, '2026-10-15').map((c) => c.slug), ['common-crane', 'eurasian-blue-tit']);
  assert.deepEqual(visibleClips(all, '2026-10-16').map((c) => c.slug), ['european-robin', 'common-crane', 'eurasian-blue-tit']);
  assert.deepEqual(visibleClips(all, '2026-10-08'), []);
  assert.equal(JSON.stringify(all), before);
});

// Runs the generated module with each cover import replaced by its path, so the test sees the exported list.
async function evaluate(source) {
  const stubbed = source.replace(/^import (cover\d+) from (".*");$/gm, 'const $1 = $2;');
  return (await import(`data:text/javascript,${encodeURIComponent(stubbed)}`)).clips;
}

test('clipsModuleSource: importerar bara de visade klippens omslag, nyaste först', async () => {
  const source = clipsModuleSource(all, '2026-10-15');
  assert.deepEqual(source.match(/^import .*$/gm), [
    'import cover0 from "/src/assets/clips/common-crane.jpg";',
    'import cover1 from "/src/assets/clips/eurasian-blue-tit.jpg";',
  ]);
  assert.ok(!source.includes('european-robin'), 'ett senare klipps omslag importeras inte');
  const clips = await evaluate(source);
  assert.deepEqual(clips.map((c) => [c.date, c.slug, c.cover]), [
    ['2026-10-10', 'common-crane', '/src/assets/clips/common-crane.jpg'],
    ['2026-10-09', 'eurasian-blue-tit', '/src/assets/clips/eurasian-blue-tit.jpg'],
  ]);
  assert.deepEqual(clips[0].names, all[1].names);
  assert.deepEqual(clips[0].silhouette, all[1].silhouette);
});

test('clipsModuleSource: före det första klippet en tom lista och inga importer', async () => {
  const source = clipsModuleSource(all, '2026-10-08');
  assert.equal(source, `export const clips = [];${LF}`);
  assert.deepEqual(await evaluate(source), []);
});

test('clipLink: artsidans namn och adress när bygget har sidan, annars klippets namn utan länk', () => {
  const crane = { names: { sv: 'Trana', en: 'Common Crane' }, slug: { sv: 'trana', en: 'common-crane' } };
  const hrefOf = (s, locale) => (locale === 'sv' ? `/sv/arter/${s.slug.sv}/` : `/species/${s.slug.en}/`);
  const c = { ...clip('2026-10-10', 'common-crane', 'Q4764'), names: { sv: 'Trana i klippet', en: 'Crane in the clip', scientific: 'Grus grus' } };
  assert.deepEqual(clipLink(c, crane, 'sv', hrefOf), { name: 'Trana', href: '/sv/arter/trana/' });
  assert.deepEqual(clipLink(c, crane, 'en', hrefOf), { name: 'Common Crane', href: '/species/common-crane/' });
  assert.deepEqual(clipLink(c, undefined, 'sv', hrefOf), { name: 'Trana i klippet', href: undefined });
  assert.deepEqual(clipLink(c, undefined, 'en', hrefOf), { name: 'Crane in the clip', href: undefined });
});

test('licenceDeed: CC0, CC BY, CC BY-SA och public domain mark, deed.sv på svenska, okänd licens utan adress', () => {
  assert.equal(licenceDeed('CC0', 'en'), 'https://creativecommons.org/publicdomain/zero/1.0/');
  assert.equal(licenceDeed('CC0', 'sv'), 'https://creativecommons.org/publicdomain/zero/1.0/deed.sv');
  assert.equal(licenceDeed('CC BY 3.0', 'en'), 'https://creativecommons.org/licenses/by/3.0/');
  assert.equal(licenceDeed('CC BY 4.0', 'sv'), 'https://creativecommons.org/licenses/by/4.0/deed.sv');
  assert.equal(licenceDeed('CC BY-SA 4.0', 'en'), 'https://creativecommons.org/licenses/by-sa/4.0/');
  assert.equal(licenceDeed('Public domain mark', 'en'), 'https://creativecommons.org/publicdomain/mark/1.0/');
  assert.equal(licenceDeed('All rights reserved', 'en'), undefined);
});

const words = { en: { label: 'Silhouette', adapted: 'adapted' }, sv: { label: 'Siluett', adapted: 'bearbetad' } };
const asText = (parts) => parts.map((p) => p.text).join('');

test('creditParts: bildtexternas ord, licensen och PhyloPic som länkar, CC0 utan "adapted"', () => {
  const s = all[1].silhouette;
  const parts = creditParts(s, 'en', words.en);
  assert.equal(asText(parts), 'Silhouette: Andy Wilson, CC0, via PhyloPic');
  assert.deepEqual(parts.filter((p) => p.href).map((p) => [p.text, p.href]), [
    ['CC0', 'https://creativecommons.org/publicdomain/zero/1.0/'],
    ['PhyloPic', s.url],
  ]);
  assert.equal(asText(creditParts(s, 'sv', words.sv)), 'Siluett: Andy Wilson, CC0, via PhyloPic');
});

test('creditParts: en CC BY-silhuett är bearbetad, "adapted" och "bearbetad"', () => {
  const s = { author: 'Maxime Dahirel', licence: 'CC BY 3.0', url: 'https://www.phylopic.org/images/0b72dd38-2dc1-4675-990e-a46259855ce1', adapted: true };
  assert.equal(asText(creditParts(s, 'en', words.en)), 'Silhouette: Maxime Dahirel, CC BY 3.0, via PhyloPic, adapted');
  assert.equal(asText(creditParts(s, 'sv', words.sv)), 'Siluett: Maxime Dahirel, CC BY 3.0, via PhyloPic, bearbetad');
  assert.equal(creditParts(s, 'sv', words.sv)[1].href, 'https://creativecommons.org/licenses/by/3.0/deed.sv');
});

test('creditParts: en okänd licens står kvar som text utan länk', () => {
  const parts = creditParts({ author: 'B', licence: 'Egen licens', url: 'https://www.phylopic.org/images/x', adapted: false }, 'en', words.en);
  assert.deepEqual(parts[1], { text: 'Egen licens' });
});
```

- [ ] **Step 2: Run them and watch them fail**

```bash
cd C:/w/birdy-klipp/website && node --test tests/unit/clips.unit.mjs 2>&1 | tail -6
```

Expected: FAIL, `Cannot find module '.../src/lib/clips.mjs'`.

- [ ] **Step 3: Write the module**

Create `website/src/lib/clips.mjs`:

```js
// The clips page, /clips/ and /sv/klipp/ (spec docs/superpowers/specs/2026-10-09-klippsidan-design.md): every See the
// song clip posted so far, newest first, each linking to its bird's page. Plain JS, so astro.config.mjs (the virtual
// module virtual:birdy-clips), the page, the footer and the unit tests (tests/unit/clips.unit.mjs) share one rule. The
// data is src/data/clips.json, written by scripts/import-clips.mjs.
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const LF = String.fromCharCode(10);
const CC0_DEED = 'https://creativecommons.org/publicdomain/zero/1.0/';
const PDM_DEED = 'https://creativecommons.org/publicdomain/mark/1.0/';

/**
 * @typedef {{ author: string, licence: string, url: string, adapted: boolean }} Silhouette
 * @typedef {{ date: string, qid: string, slug: string, names: { sv: string, en: string, scientific: string }, silhouette: Silhouette }} ClipData
 */

/**
 * The clips page in each language.
 * @param {'sv' | 'en'} locale
 */
export const clipsHref = (locale) => (locale === 'sv' ? '/sv/klipp/' : '/clips/');

/**
 * src/data/clips.json's clips, oldest first as the import script writes them.
 * @param {string} websiteRoot
 * @returns {ClipData[]}
 */
export function loadClips(websiteRoot) {
  const file = resolve(websiteRoot, 'src', 'data', 'clips.json');
  const data = JSON.parse(readFileSync(file, 'utf8'));
  if (!Array.isArray(data.clips)) throw new Error(`${file}: clips saknas (kör npm run clips:import)`);
  return data.clips;
}

/**
 * The clips posted on or before `todayIso` (YYYY-MM-DD in Europe/Stockholm, the build's date), newest first. A clip
 * shows from its posting day, so the nightly rebuild adds each day's clip by itself.
 * @template {{ date: string }} T
 * @param {T[]} clips
 * @param {string} todayIso
 * @returns {T[]}
 */
export function visibleClips(clips, todayIso) {
  return clips.filter((c) => c.date <= todayIso).sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0));
}

/**
 * The source of virtual:birdy-clips (astro.config.mjs): the clips visible on `todayIso`, newest first, each with its
 * cover imported for astro:assets. Only their covers are imported: Vite emits every image a module imports into
 * dist/_astro/, used or not, so a later clip's cover would otherwise be online before its day.
 * @param {ClipData[]} clips
 * @param {string} todayIso
 */
export function clipsModuleSource(clips, todayIso) {
  const shown = visibleClips(clips, todayIso);
  const imports = shown.map((c, n) => `import cover${n} from ${JSON.stringify(`/src/assets/clips/${c.slug}.jpg`)};`);
  const entries = shown.map((c, n) => `{ ...${JSON.stringify(c)}, cover: cover${n} }`);
  return [...imports, `export const clips = [${entries.join(', ')}];`, ''].join(LF);
}

/**
 * What a card shows for its bird: the name and the address of its species page when this build has that page, in the
 * page's language; otherwise the clip's own name and no link (the same rule as the field notes' species links).
 * @template {{ names: { sv: string, en: string } }} S
 * @param {{ names: { sv: string, en: string } }} clip
 * @param {S | undefined} species the clip's species, when it has a page in this build
 * @param {'sv' | 'en'} locale
 * @param {(species: S, locale: 'sv' | 'en') => string} hrefOf
 * @returns {{ name: string, href: string | undefined }}
 */
export function clipLink(clip, species, locale, hrefOf) {
  if (!species) return { name: clip.names[locale], href: undefined };
  return { name: species.names[locale], href: hrefOf(species, locale) };
}

/**
 * The deed of a silhouette's licence, the Swedish deed on the Swedish page, or undefined for a licence the site does
 * not know (the credit then names it without a link).
 * @param {string} licence
 * @param {'sv' | 'en'} locale
 * @returns {string | undefined}
 */
export function licenceDeed(licence, locale) {
  const l = licence.trim();
  const by = l.match(/^CC (BY(?:-SA)?) ([1-4]\.0)$/i);
  let url;
  if (by) url = `https://creativecommons.org/licenses/${by[1].toLowerCase()}/${by[2]}/`;
  else if (/^CC0(?: 1\.0)?$/i.test(l)) url = CC0_DEED;
  else if (/^Public domain mark(?: 1\.0)?$/i.test(l)) url = PDM_DEED;
  if (!url) return undefined;
  return locale === 'sv' ? `${url}deed.sv` : url;
}

/**
 * The silhouette's credit under a card, in the captions' words ("Silhouette: Andy Wilson, CC0, via PhyloPic", and
 * ", adapted" after a CC BY silhouette, which the cover recolours), as text and links: the licence links to its deed,
 * "PhyloPic" to the silhouette's page.
 * @param {Silhouette} silhouette
 * @param {'sv' | 'en'} locale
 * @param {{ label: string, adapted: string }} words copy clips.silhouette and clips.adapted
 * @returns {{ text: string, href?: string }[]}
 */
export function creditParts(silhouette, locale, words) {
  const deed = licenceDeed(silhouette.licence, locale);
  return [
    { text: `${words.label}: ${silhouette.author}, ` },
    deed ? { text: silhouette.licence, href: deed } : { text: silhouette.licence },
    { text: ', via ' },
    { text: 'PhyloPic', href: silhouette.url },
    ...(silhouette.adapted ? [{ text: `, ${words.adapted}` }] : []),
  ];
}
```

- [ ] **Step 4: Run the tests**

```bash
cd C:/w/birdy-klipp/website && node --test tests/unit/clips.unit.mjs 2>&1 | tail -8
```

Expected: `pass 9`, `fail 0`.

- [ ] **Step 5: The virtual module**

Create `website/src/lib/virtual-clips.d.ts`:

```ts
// The virtual module astro.config.mjs builds (plugin birdy-clips) from src/lib/clips.mjs: the See the song clips posted
// on or before the build's date (Europe/Stockholm, BIRDY_TODAY in test builds), newest first, each with its flock cover.
declare module 'virtual:birdy-clips' {
  export const clips: {
    date: string;
    qid: string;
    slug: string;
    names: { sv: string; en: string; scientific: string };
    silhouette: { author: string; licence: string; url: string; adapted: boolean };
    cover: import('astro').ImageMetadata;
  }[];
}
```

In `website/astro.config.mjs`, three edits:

1. After the line `import { buildDate, loadAppSpeciesSnapshot, selectAppDailyBird } from './src/lib/daily-bird.mjs';` add:

```js
import { clipsModuleSource, loadClips } from './src/lib/clips.mjs';
```

2. Just before the line `export default defineConfig({` insert:

```js
// The clips page (spec 2026-10-09-klippsidan): the See the song clips posted on or before the build's date, newest
// first, with the same date as Dagens fågel above (buildDate: Europe/Stockholm, BIRDY_TODAY in test builds), so the
// nightly rebuild adds each day's clip by itself. A virtual module, like the species photos', because Vite emits every
// image a module imports into dist/_astro/, used or not: only the shown clips' covers are imported (clipsModuleSource),
// so a later clip's cover is not online before its day. The resolved id carries Vite's NUL prefix, as above.
const CLIPS = 'virtual:birdy-clips';
const RESOLVED_CLIPS = `${String.fromCharCode(0)}${CLIPS}`;
/** @type {import('vite').Plugin} */
const clipsModule = {
  name: 'birdy-clips',
  resolveId(id) {
    return id === CLIPS ? RESOLVED_CLIPS : undefined;
  },
  load(id) {
    if (id !== RESOLVED_CLIPS) return undefined;
    return clipsModuleSource(loadClips(root), buildDate().iso);
  },
};

```

3. Replace `    plugins: [tailwindcss(), speciesMediaModule, dailyBirdModule],` with:

```js
    plugins: [tailwindcss(), speciesMediaModule, dailyBirdModule, clipsModule],
```

Nothing imports the module before Task 4, so the build cannot exercise it yet. Check the generated source with the real data instead:

```bash
cd C:/w/birdy-klipp/website && node --input-type=module -e "import { clipsModuleSource, loadClips } from './src/lib/clips.mjs'; console.log(clipsModuleSource(loadClips('.'), '2026-10-15'))" | cut -c1-110
```

Expected: seven lines `import cover0 from "/src/assets/clips/great-tit.jpg";` down to `import cover6 from "/src/assets/clips/eurasian-blue-tit.jpg";` (great-tit, common-buzzard, common-blackbird, mallard, house-sparrow, common-crane, eurasian-blue-tit), then `export const clips = [{ ...{"date":"2026-10-15","qid":"Q25485","slug":"great-tit",...`. No `european-robin`.

- [ ] **Step 6: The whole unit suite**

```bash
cd C:/w/birdy-klipp/website && npm run test:unit 2>&1 | tail -8
```

Expected: all pass (baseline + 19).

- [ ] **Step 7: Commit**

```bash
cd C:/w/birdy-klipp && git add website/src/lib/clips.mjs website/tests/unit/clips.unit.mjs website/src/lib/virtual-clips.d.ts website/astro.config.mjs && git commit -m "feat(webb): klippens regler och den virtuella modulen, bara visade klipps omslag i bygget" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: The two pages

**Files:**
- Create: `website/tests/clips.spec.ts`
- Modify: `website/src/content/copy.en.json:508`, `website/src/content/copy.sv.json:508` (insert before `"footer"`)
- Create: `website/src/components/clips/ClipsPage.astro`, `website/src/pages/clips.astro`, `website/src/pages/sv/klipp.astro`
- Modify: `website/scripts/check-seo.mjs:12-13`

- [ ] **Step 1: Write the failing Playwright tests**

Create `website/tests/clips.spec.ts`:

```ts
import { expect, test } from '@playwright/test';
import { trackConsoleErrors } from './test-helpers';

// The clips page (spec docs/superpowers/specs/2026-10-09-klippsidan-design.md). The fixture build (npm run
// build:fixtures) pins BIRDY_TODAY=2026-10-15, so the page shows the first week, 9 to 15 October, and nothing later.
// The week is written out here rather than read from src/data/clips.json, so a wrong day or name in the data fails.
// All seven birds have a published page in the test data, and all seven silhouettes are CC0.
const WEEK_ONE = [
  { date: '2026-10-15', slug: 'great-tit', en: 'Great Tit', sv: 'Talgoxe', svSlug: 'talgoxe', author: 'Andy Wilson' },
  { date: '2026-10-14', slug: 'common-buzzard', en: 'Common Buzzard', sv: 'Ormvråk', svSlug: 'ormvrak', author: 'Andy Wilson' },
  { date: '2026-10-13', slug: 'common-blackbird', en: 'Common Blackbird', sv: 'Koltrast', svSlug: 'koltrast', author: 'Anthony Caravaggi' },
  { date: '2026-10-12', slug: 'mallard', en: 'Mallard', sv: 'Gräsand', svSlug: 'grasand', author: 'Andy Wilson' },
  { date: '2026-10-11', slug: 'house-sparrow', en: 'House Sparrow', sv: 'Gråsparv', svSlug: 'grasparv', author: 'Andy Wilson' },
  { date: '2026-10-10', slug: 'common-crane', en: 'Common Crane', sv: 'Trana', svSlug: 'trana', author: 'Andy Wilson' },
  { date: '2026-10-09', slug: 'eurasian-blue-tit', en: 'Eurasian Blue Tit', sv: 'Blåmes', svSlug: 'blames', author: 'Wouter Koch' },
] as const;
type Clip = (typeof WEEK_ONE)[number];

const PAGES = [
  {
    path: '/clips/', other: '/sv/klipp/', locale: 'en', crumb: 'Clips', silhouette: 'Silhouette', deedSuffix: '',
    name: (c: Clip) => c.en,
    href: (c: Clip) => `/species/${c.slug}/`,
    posted: (c: Clip) => `Posted ${Number(c.date.slice(8))} October 2026`,
    firstAlt: "The clip's cover: a flock of small birds in the shape of the Great Tit",
  },
  {
    path: '/sv/klipp/', other: '/clips/', locale: 'sv', crumb: 'Klipp', silhouette: 'Siluett', deedSuffix: 'deed.sv',
    name: (c: Clip) => c.sv,
    href: (c: Clip) => `/sv/arter/${c.svSlug}/`,
    posted: (c: Clip) => `Publicerat ${Number(c.date.slice(8))} oktober 2026`,
    firstAlt: 'Klippets omslag: en flock små fåglar i form av en talgoxe',
  },
] as const;

test.describe('Klippsidan', () => {
  for (const p of PAGES) {
    test(`${p.path} svarar och har rubrik, brödsmulor, hreflang, språkbyte och strukturerad data`, async ({ page }) => {
      const errors = trackConsoleErrors(page);
      const res = await page.goto(p.path);
      expect(res?.status()).toBe(200);
      await expect(page.locator('h1')).toHaveText('See the song');
      await expect(page.locator('[data-crumb]').last()).toHaveText(p.crumb);
      await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', `https://birdy.community${p.path}`);
      await expect(page.locator(`link[rel="alternate"][hreflang="${p.locale === 'sv' ? 'en' : 'sv'}"]`)).toHaveAttribute('href', `https://birdy.community${p.other}`);
      await expect(page.locator('link[rel="alternate"][hreflang="x-default"]')).toHaveAttribute('href', 'https://birdy.community/clips/');
      // The menu's language switch goes to the other language's clips page, not to its home page.
      await expect(page.locator('#site-nav .links a.lang')).toHaveAttribute('href', p.other);
      const graph = JSON.parse((await page.locator('script[type="application/ld+json"]').textContent()) ?? '{}')['@graph'] as { '@type': string }[];
      expect(graph.map((n) => n['@type'])).toEqual(expect.arrayContaining(['CollectionPage', 'BreadcrumbList']));
      expect(errors).toEqual([]);
    });

    test(`${p.path} visar klippen 9 till 15 oktober, nyaste först, och inga senare`, async ({ page }) => {
      await page.goto(p.path);
      const cards = page.locator('[data-clip]');
      expect(await cards.evaluateAll((els) => els.map((el) => el.getAttribute('data-clip')))).toEqual(WEEK_ONE.map((c) => c.slug));
      for (const [i, c] of WEEK_ONE.entries()) {
        await expect(cards.nth(i).locator('h2')).toHaveText(p.name(c));
        await expect(cards.nth(i).locator('time')).toHaveAttribute('datetime', c.date);
        await expect(cards.nth(i).locator('[data-clip-date]')).toHaveText(p.posted(c));
      }
      await expect(page.locator('[data-clip="european-robin"]')).toHaveCount(0);
      await expect(page.locator('[data-clips-empty]')).toHaveCount(0);
    });

    test(`${p.path}: varje klipp länkar till sin artsida på sidans språk, och sidan finns`, async ({ page, request }) => {
      await page.goto(p.path);
      await expect(page.locator('[data-clip-link]')).toHaveCount(WEEK_ONE.length);
      for (const c of WEEK_ONE) {
        const link = page.locator(`[data-clip="${c.slug}"] [data-clip-link]`);
        await expect(link).toHaveAttribute('href', p.href(c));
        await expect(link).toHaveAccessibleName(p.name(c));
        expect((await request.get(p.href(c))).status(), p.href(c)).toBe(200);
      }
    });

    test(`${p.path}: omslagen har alt och mått, första raden laddas direkt, silhuettens kredit under kortet`, async ({ page }) => {
      await page.goto(p.path);
      const covers = page.locator('[data-clip] .card img');
      await expect(covers).toHaveCount(WEEK_ONE.length);
      await expect(covers.first()).toHaveAttribute('alt', p.firstAlt);
      for (let i = 0; i < WEEK_ONE.length; i += 1) {
        await expect(covers.nth(i)).toHaveAttribute('width', '540');
        await expect(covers.nth(i)).toHaveAttribute('height', '960');
        await expect(covers.nth(i)).toHaveAttribute('loading', i < 4 ? 'eager' : 'lazy');
      }
      for (const c of WEEK_ONE) {
        const credit = page.locator(`[data-clip="${c.slug}"] > [data-clip-credit]`);
        await expect(credit).toHaveText(`${p.silhouette}: ${c.author}, CC0, via PhyloPic`);
        await expect(credit.getByRole('link', { name: 'CC0' })).toHaveAttribute('href', `https://creativecommons.org/publicdomain/zero/1.0/${p.deedSuffix}`);
        await expect(credit.getByRole('link', { name: 'PhyloPic' })).toHaveAttribute('href', /^https:\/\/www\.phylopic\.org\/images\/[0-9a-f-]{36}$/);
      }
    });
  }

  test('webbplatskartan har båda klippsidorna', async ({ request }) => {
    const xml = await (await request.get('/sitemap-0.xml')).text();
    expect(xml).toContain('<loc>https://birdy.community/clips/</loc>');
    expect(xml).toContain('<loc>https://birdy.community/sv/klipp/</loc>');
  });
});
```

- [ ] **Step 2: Run them and watch them fail**

```bash
cd C:/w/birdy-klipp/website && npm run build:fixtures && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test tests/clips.spec.ts --workers=2 --max-failures=1 2>&1 | tail -15
```

Expected: FAIL, the first failure `Expected: 200 Received: 404` (the pages don't exist yet).

- [ ] **Step 3: The copy**

In `website/src/content/copy.en.json`, insert this block on the line before `  "footer": {` (line 508), so that no existing line changes:

```json
  "clips": {
    "title": "See the song: all the clips and their birds | Birdy",
    "description": "Every See the song clip from Instagram, TikTok, Facebook and YouTube, newest first. Hear the bird, see it, then read more on its own page.",
    "crumbHome": "Birdy",
    "crumb": "Clips",
    "crumbLabel": "Breadcrumbs",
    "kicker": "Clips",
    "headline": "See the *song*",
    "note": "hear the bird first, then meet it",
    "lead": "Short videos on Instagram, Facebook, YouTube and TikTok. Each one plays a bird's song or call first and shows the bird after a short pause. Here are all the clips so far, newest first, with links to the birds' pages.",
    "posted": "Posted {date}",
    "coverAlt": "The clip's cover: a flock of small birds in the shape of the {name}",
    "silhouette": "Silhouette",
    "adapted": "adapted",
    "empty": "The first clip is coming soon.",
    "footerLink": "Clips"
  },
```

In `website/src/content/copy.sv.json`, the same place (before `  "footer": {`, line 508):

```json
  "clips": {
    "title": "See the song: alla klipp och fåglarnas sidor | Birdy",
    "description": "Alla klipp i See the song från Instagram, TikTok, Facebook och YouTube, de nyaste först. Hör fågeln, se den och läs sedan mer på artens sida.",
    "crumbHome": "Birdy",
    "crumb": "Klipp",
    "crumbLabel": "Brödsmulor",
    "kicker": "Klipp",
    "headline": "See the *song*",
    "note": "hör fågeln först, se den sedan",
    "lead": "Korta videor på Instagram, Facebook, YouTube och TikTok. Varje video spelar fågelns sång eller läte först och visar fågeln efter en kort paus. Här är alla klipp hittills, de nyaste först, med länkar till fåglarnas sidor.",
    "posted": "Publicerat {date}",
    "coverAlt": "Klippets omslag: en flock små fåglar i form av en {name}",
    "silhouette": "Siluett",
    "adapted": "bearbetad",
    "empty": "Det första klippet kommer snart.",
    "footerLink": "Klipp"
  },
```

The series name "See the song" stays English on the Swedish page, as in the blog post (it is a proper name). `{name}` in the Swedish `coverAlt` gets the name in lower case ("en talgoxe"); the component does that.

- [ ] **Step 4: The page component**

Create `website/src/components/clips/ClipsPage.astro`:

```astro
---
// The clips page, /clips/ and /sv/klipp/ (spec docs/superpowers/specs/2026-10-09-klippsidan-design.md): every See the
// song clip posted so far, newest first, and the bio link on Instagram and TikTok, where a caption can't link. A card
// has the clip's flock cover, the bird's name, the day the clip was posted and, when this build has the bird's page, a
// link to it over the whole card; the silhouette's credit sits under the card. No players, scripts or cookies from the
// platforms (spec, "Ramar").
import { Image } from 'astro:assets';
import { clips } from 'virtual:birdy-clips';
import Layout from '../../layouts/Layout.astro';
import Nav from '../Nav.astro';
import Footer from '../Footer.astro';
import Kicker from '../ui/Kicker.astro';
import JournalHeadline from '../ui/JournalHeadline.astro';
import MarginNote from '../ui/MarginNote.astro';
import DeckleEdge from '../ui/DeckleEdge.astro';
import { type Locale, getCopy } from '../../lib/i18n';
import { clipLink, clipsHref, creditParts } from '../../lib/clips.mjs';
import { SITE, breadcrumbJsonLd, formatDate, getAllSpecies, speciesHref } from '../../lib/species';

interface Props { locale: Locale }
const { locale } = Astro.props;
const t = getCopy(locale);
const c = t.clips;
const other: Locale = locale === 'sv' ? 'en' : 'sv';
const pathname = clipsHref(locale);
// The species with a page in this build, by QID. A clip links to its bird's page only when that page exists, the same
// rule as the field notes' species links, so a clip whose bird has no page yet shows its name without a link.
const built = new Map((await getAllSpecies()).map((s) => [s.qid, s]));
const [postedBefore, postedAfter] = c.posted.split('{date}');
const cards = clips.map((clip, i) => {
  const { name, href } = clipLink(clip, built.get(clip.qid), locale, speciesHref);
  return {
    clip,
    name,
    href,
    alt: c.coverAlt.replace('{name}', locale === 'sv' ? name.toLocaleLowerCase('sv') : name),
    credit: creditParts(clip.silhouette, locale, { label: c.silhouette, adapted: c.adapted }),
    // The first row loads at once (four cards on a wide screen), the rest as they come into view.
    loading: i < 4 ? ('eager' as const) : ('lazy' as const),
  };
});
const crumbs = [
  { name: c.crumbHome, href: locale === 'sv' ? '/sv/' : '/' },
  { name: c.crumb, href: pathname },
];
const jsonLd = [
  {
    '@type': 'CollectionPage',
    name: c.title.replace(/ \| Birdy$/, ''),
    description: c.description,
    url: new URL(pathname, SITE).toString(),
    inLanguage: locale,
    isPartOf: { '@type': 'WebSite', name: 'Birdy', url: new URL('/', SITE).toString() },
  },
  breadcrumbJsonLd(crumbs),
];
---

<Layout locale={locale} pathname={pathname} alternatePath={clipsHref(other)} title={c.title} description={c.description} jsonLd={jsonLd}>
  <Nav locale={locale} variant="solid" switchLangHref={clipsHref(other)} />
  <main id="main" tabindex="-1" class="clips-page" data-clips-page>
    <DeckleEdge color="var(--dark)" />
    <div class="wrap">
      <nav class="crumbs" aria-label={c.crumbLabel}>
        <ol>
          <li><a href={crumbs[0].href} data-crumb>{crumbs[0].name}</a></li>
          <li><span aria-current="page" data-crumb>{crumbs[1].name}</span></li>
        </ol>
      </nav>
      <Kicker text={c.kicker} />
      <JournalHeadline text={c.headline} level="h1" align="left" size="clamp(44px, 7vw, 80px)" />
      <MarginNote text={c.note} />
      <p class="lead">{c.lead}</p>
      {cards.length === 0 ? (
        <p class="empty" data-clips-empty>{c.empty}</p>
      ) : (
        <ol class="grid" role="list">
          {cards.map(({ clip, name, href, alt, credit, loading }) => (
            <li class="clip" data-clip={clip.slug}>
              <div class:list={['card', { linked: !!href }]}>
                <Image src={clip.cover} alt={alt} width={540} widths={[240, 360, 540]} sizes="(min-width: 1160px) 236px, (min-width: 900px) 22vw, (min-width: 640px) 30vw, 44vw" loading={loading} decoding="async" />
                <h2 class="name">{href ? <a href={href} data-clip-link>{name}</a> : name}</h2>
                <p class="date" data-clip-date>{postedBefore}<time datetime={clip.date}>{formatDate(clip.date, locale)}</time>{postedAfter}</p>
              </div>
              <p class="credit" data-clip-credit>{credit.map((part) => (part.href ? <a href={part.href}>{part.text}</a> : part.text))}</p>
            </li>
          ))}
        </ol>
      )}
    </div>
  </main>
  <Footer locale={locale} switchLangHref={clipsHref(other)} edgeColor="var(--peach)" />
</Layout>

<style>
  /* Peach paper like the social profiles; text only in --ink, --muted and --rust (pairs checked in check-contrast.mjs). */
  .clips-page { position: relative; background: var(--peach); padding: 64px 0 104px; }
  .crumbs ol { display: flex; flex-wrap: wrap; gap: 6px; list-style: none; margin: 0 0 22px; padding: 0; font-size: 12.5px; color: var(--muted); }
  .crumbs li + li::before { content: '›'; margin-right: 6px; }
  .crumbs a { text-decoration: underline; text-underline-offset: 3px; }
  .crumbs a:hover { color: var(--rust); }
  .lead { max-width: 38rem; }
  .empty { margin: 44px 0 0; font-family: var(--font-script); font-weight: 700; font-size: 26px; color: var(--rust); }
  .grid { list-style: none; margin: 48px 0 0; padding: 0; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 30px 14px; }
  /* A card is a sheet of the site's card paper, so the peach cover stands out from the peach page. */
  .card { position: relative; padding: 8px 8px 14px; background: var(--card); border-radius: 14px; box-shadow: 0 0 0 1px var(--line); }
  .card :global(img) { display: block; width: 100%; height: auto; aspect-ratio: 9 / 16; object-fit: cover; border-radius: 9px; }
  .name { margin: 12px 6px 0; font-size: clamp(19px, 2vw, 23px); line-height: 1.1; color: var(--ink); overflow-wrap: anywhere; hyphens: auto; }
  .name a { text-decoration: underline; text-decoration-color: rgba(154, 69, 38, .45); text-underline-offset: 4px; }
  /* The name's link covers the whole card, cover and date too: one link per card, named after the bird. */
  .name a::after { content: ''; position: absolute; inset: 0; border-radius: 14px; }
  .linked { transition: transform .3s var(--ease-paper), box-shadow .3s var(--ease-paper); }
  .linked:hover { transform: translateY(-3px); box-shadow: 0 0 0 1px var(--line), 0 16px 32px rgba(var(--dark-rgb), .12); }
  .linked:hover .name a { color: var(--rust); }
  .date { margin: 6px 6px 0; font-size: 11.5px; letter-spacing: .06em; text-transform: uppercase; color: var(--muted); }
  .credit { margin: 10px 4px 0; font-size: 12px; line-height: 1.5; color: var(--muted); }
  .credit a { text-decoration: underline; text-underline-offset: 2px; }
  .credit a:hover { color: var(--rust); }
  @media (min-width: 640px) {
    .grid { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 40px 22px; }
  }
  @media (min-width: 900px) {
    .grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
  }
  @media (max-width: 760px) {
    .clips-page { padding: 48px 0 84px; }
  }
</style>
```

`width={540}` makes the `src` fallback the largest srcset width (without it Astro also writes a full 1080 px copy) and gives the `<img>` `width="540" height="960"`.

- [ ] **Step 5: The routes**

Create `website/src/pages/clips.astro`:

```astro
---
import ClipsPage from '../components/clips/ClipsPage.astro';
---
<ClipsPage locale="en" />
```

Create `website/src/pages/sv/klipp.astro`:

```astro
---
import ClipsPage from '../../components/clips/ClipsPage.astro';
---
<ClipsPage locale="sv" />
```

- [ ] **Step 6: The SEO rules cover the clips pages**

In `website/scripts/check-seo.mjs`, replace lines 12 and 13:

```js
// The Premium page (plan 2026-10-08 Task 6) is held to the same rules as the species pages.
const NEW = ['/species/', '/sv/arter/', '/premium/', '/sv/premium/'];
```

with:

```js
// The Premium page (plan 2026-10-08 Task 6) and the clips page (spec 2026-10-09-klippsidan) are held to the same rules
// as the species pages.
const NEW = ['/species/', '/sv/arter/', '/premium/', '/sv/premium/', '/clips/', '/sv/klipp/'];
```

- [ ] **Step 7: Build, check SEO, run the tests**

```bash
cd C:/w/birdy-klipp/website && npm run build:fixtures && npm run test:seo
cd C:/w/birdy-klipp/website && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test tests/clips.spec.ts --workers=2 2>&1 | tail -5
```

Expected: the build ends without an error (its log has `[birdy-daily-bird] 2026-10-15 (Europe/Stockholm, BIRDY_TODAY)`); `check-seo OK`; Playwright `9 passed`. Keep the `&&`: a failed build must stop the chain, or check-seo reads the previous `dist/`. If the build cannot resolve `virtual:birdy-clips` or an `/src/assets/clips/...` import, compare with `speciesMediaModule` in `astro.config.mjs`, which imports root-relative image ids the same way. If check-seo names a title or description length, the copy changed: the counts must stay 40 to 60 and 120 to 155.

- [ ] **Step 8: The guards**

```bash
cd C:/w/birdy-klipp/website && npm run test:i18n && npm run test:no-dashes && npm run test:contrast && npm run test:palette && npm run test:no-accuracy
```

Expected: every guard OK; `i18n parity OK` with 15 keys more than the baseline.

- [ ] **Step 9: Commit**

```bash
cd C:/w/birdy-klipp && git add website/src/content/copy.en.json website/src/content/copy.sv.json website/src/components/clips/ClipsPage.astro website/src/pages/clips.astro website/src/pages/sv/klipp.astro website/scripts/check-seo.mjs website/tests/clips.spec.ts && git commit -m "feat(webb): klippsidan /clips/ och /sv/klipp/ med länk till artsidan" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: The footer link and the remaining checks

**Files:**
- Modify: `website/tests/clips.spec.ts` (imports at the top, tests at the end)
- Modify: `website/src/components/Footer.astro:7`, `:44`, `:101`

- [ ] **Step 1: Write the tests**

In `website/tests/clips.spec.ts`, replace the first two lines (the imports) with:

```ts
import { readdirSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { trackConsoleErrors } from './test-helpers';

const websiteRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
```

Then add at the end of the file:

```ts
test.describe('Klippsidan: sidfoten, nätet, smala skärmar och bygget', () => {
  for (const [path, href, text] of [
    ['/', '/clips/', 'Clips'],
    ['/sv/', '/sv/klipp/', 'Klipp'],
    ['/clips/', '/clips/', 'Clips'],
    ['/sv/klipp/', '/sv/klipp/', 'Klipp'],
    ['/premium/', '/clips/', 'Clips'],
    ['/sv/arter/talgoxe/', '/sv/klipp/', 'Klipp'],
  ] as const) {
    test(`sidfoten på ${path} länkar till klippsidan bredvid kanalerna`, async ({ page }) => {
      await page.goto(path);
      const link = page.locator('footer.footer .fbrand [data-footer-clips]');
      await expect(link).toHaveAttribute('href', href);
      await expect(link).toHaveText(text);
      // Next to the channels, not one of them: the follow row still has exactly the four channels.
      await expect(page.locator('footer.footer ul.fsoc a')).toHaveCount(4);
    });
  }

  for (const path of ['/clips/', '/sv/klipp/']) {
    test(`${path} hämtar inget från andra värdar och har inga spelare`, async ({ page, baseURL }) => {
      const hosts = new Set<string>();
      page.on('request', (req) => {
        const url = new URL(req.url());
        if (url.protocol === 'http:' || url.protocol === 'https:') hosts.add(url.host);
      });
      await page.goto(path);
      // The lazy covers too: scroll to the footer and let the network settle.
      await page.locator('footer.footer').scrollIntoViewIfNeeded();
      await page.waitForLoadState('networkidle');
      expect([...hosts]).toEqual([new URL(baseURL!).host]);
      await expect(page.locator('iframe, video, audio, object, embed')).toHaveCount(0);
    });
  }

  for (const width of [320, 390]) {
    test(`inget sidledes scroll på klippsidan i ${width} px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      for (const path of ['/clips/', '/sv/klipp/']) {
        await page.goto(path);
        expect(await page.evaluate(() => document.documentElement.scrollWidth), path).toBeLessThanOrEqual(width);
      }
    });
  }

  // Only the shown clips' covers reach the build (virtual:birdy-clips imports nothing else), so a later clip's cover is
  // not online before its day. Astro names an image's files after it: <slug>.<hash>_<hash>.webp.
  test('bara de visade klippens omslag finns i bygget', () => {
    const files = readdirSync(resolve(websiteRoot, 'dist', '_astro'));
    const { clips } = JSON.parse(readFileSync(resolve(websiteRoot, 'src', 'data', 'clips.json'), 'utf8')) as { clips: { date: string; slug: string }[] };
    for (const c of clips) {
      const emitted = files.filter((f) => f.startsWith(`${c.slug}.`));
      if (c.date <= '2026-10-15') expect(emitted.length, c.slug).toBeGreaterThan(0);
      else expect(emitted, c.slug).toEqual([]);
    }
  });
});

test.describe('axe på klippsidan', () => {
  // Reduced motion, as in the other axe runs: every element in its final colours.
  test.use({ contextOptions: { reducedMotion: 'reduce' } });
  for (const path of ['/clips/', '/sv/klipp/']) {
    for (const width of [390, 1440]) {
      test(`axe: ${path} i ${width} px`, async ({ page }) => {
        await page.setViewportSize({ width, height: 900 });
        await page.goto(path);
        const results = await new AxeBuilder({ page }).analyze();
        expect(results.violations, JSON.stringify(results.violations, null, 2)).toEqual([]);
      });
    }
  }
});
```

- [ ] **Step 2: Run them: the footer tests fail**

```bash
cd C:/w/birdy-klipp/website && npm run build:fixtures && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test tests/clips.spec.ts --workers=2 2>&1 | tail -15
```

Expected: the six `sidfoten på ...` tests FAIL (no `[data-footer-clips]`); the other new tests may already pass (they lock in what Task 4 built). Any other failure: fix it in `ClipsPage.astro` before going on (an axe violation's JSON names the element and the rule).

- [ ] **Step 3: The footer link**

In `website/src/components/Footer.astro`:

1. After the line `import { SOCIAL_ICON_PATHS } from '../lib/social-icons';` add:

```astro
import { clipsHref } from '../lib/clips.mjs';
```

2. Replace

```astro
          ))}
        </ul>
      </div>
      {speciesColumn && (
```

with

```astro
          ))}
        </ul>
        {/* The clips page (spec 2026-10-09-klippsidan) next to the channels: every See the song clip with its bird's page. */}
        <a class="fclips" href={clipsHref(locale)} data-footer-clips>{t.clips.footerLink}</a>
      </div>
      {speciesColumn && (
```

3. After the line `  .fsoc svg { display: block; }` add:

```css
  /* The clips link under the channels, in the link colour of .col a (lighter than .fbot's .55, which
     scripts/check-contrast.mjs checks on --dark-deep), padded to a comfortable target. */
  .fclips { display: inline-block; margin-top: 10px; padding: 4px 0; font-size: 14px; color: rgba(233, 226, 210, .82); text-decoration: underline; text-decoration-color: rgba(233, 226, 210, .3); text-underline-offset: 3px; transition: color .2s; }
  .fclips:hover { color: var(--apricot); }
```

- [ ] **Step 4: Build and run the clips, home and Premium tests**

```bash
cd C:/w/birdy-klipp/website && npm run build:fixtures && npm run test:seo && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test tests/clips.spec.ts tests/home.spec.ts tests/premium.spec.ts --workers=2 2>&1 | tail -5
```

Expected: `check-seo OK`; every test passes (`tests/clips.spec.ts` alone has 24). The home tests that count the four channel links (`ul.fsoc a`) still pass because the clips link sits outside the list.

- [ ] **Step 5: Commit**

```bash
cd C:/w/birdy-klipp && git add website/src/components/Footer.astro website/tests/clips.spec.ts && git commit -m "feat(webb): sidfoten länkar till klippsidan, kontroller för nätet, smala skärmar, bygget och axe" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: A look at all 30, the whole gate, and a Vercel preview

**Files:** none in the repo (the QA script and screenshots go to `C:/w/birdy-klipp-qa/`).

- [ ] **Step 1: A look with every clip**

The tests only see the first week (all linked, all CC0). Build once as on 7 Nov, against the test data: 30 cards, 11 of them linked (the clips whose species has a published page in `tests/fixtures/species/`: Blåmes, Trana, Gråsparv, Gräsand, Koltrast, Ormvråk, Talgoxe, Rödhake, Bofink, Fiskmås, Skata; Större hackspett's test page is unpublished), 5 adapted.

Save as `C:/w/birdy-klipp-qa/clips-qa.mjs` (outside the repo):

```js
// A look at the clips page with every clip (plan 2026-10-09-klippsidan Task 6), outside the repo. Serves
// C:/w/birdy-klipp/website/dist on port 4762, saves full-page screenshots of both pages at 390 and 1440 px next to this
// file and prints what the cards show. Usage: node C:/w/birdy-klipp-qa/clips-qa.mjs
import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';

const website = 'C:/w/birdy-klipp/website';
const out = 'C:/w/birdy-klipp-qa';
const port = 4762;
const { chromium } = createRequire(`${website}/package.json`)('playwright');
const server = spawn(process.execPath, ['node_modules/astro/bin/astro.mjs', 'preview', '--port', String(port), '--strictPort'], { cwd: website, stdio: 'ignore' });
try {
  for (let tries = 0; ; tries += 1) {
    try {
      if ((await fetch(`http://localhost:${port}/clips/`)).ok) break;
    } catch {
      // not listening yet
    }
    if (tries > 60) throw new Error(`no answer on port ${port}`);
    await new Promise((r) => setTimeout(r, 500));
  }
  const browser = await chromium.launch({ channel: 'chrome' });
  for (const [path, tag] of [['/clips/', 'en'], ['/sv/klipp/', 'sv']]) {
    for (const width of [390, 1440]) {
      const page = await browser.newPage({ viewport: { width, height: 900 }, reducedMotion: 'reduce' });
      await page.goto(`http://localhost:${port}${path}`);
      await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
      await page.waitForLoadState('networkidle');
      await page.screenshot({ path: `${out}/clips-${tag}-${width}.png`, fullPage: true });
      const seen = await page.evaluate(() => ({
        cards: document.querySelectorAll('[data-clip]').length,
        links: document.querySelectorAll('[data-clip-link]').length,
        adapted: [...document.querySelectorAll('[data-clip-credit]')].filter((p) => /adapted|bearbetad/.test(p.textContent ?? '')).length,
        scrollWidth: document.documentElement.scrollWidth,
      }));
      console.log(path, width, JSON.stringify(seen));
      await page.close();
    }
  }
  await browser.close();
} finally {
  server.kill();
}
```

```bash
cd C:/w/birdy-klipp/website && node scripts/env-run.mjs SPECIES_FIXTURES=1 BIRDY_TODAY=2026-11-07 -- npx astro build --force && node C:/w/birdy-klipp-qa/clips-qa.mjs
```

Expected: the build ends without an error (its log has `[birdy-daily-bird] 2026-11-07`), then four lines such as `/clips/ 390 {"cards":30,"links":11,"adapted":5,"scrollWidth":390}`: 30 cards, 11 links and 5 adapted credits on both pages at both widths, and a `scrollWidth` no larger than the width (7 cards means the QA build did not replace the fixture build). Then open the four PNGs (`clips-en-390.png`, `clips-en-1440.png`, `clips-sv-390.png`, `clips-sv-1440.png`) with the Read tool and check: two columns at 390 px, four at 1440; covers whole, not stretched; long names ("Great Spotted Woodpecker", "Större hackspett", "European Herring Gull") wrap inside their card; unlinked cards have no underline; each credit sits under its card, the five CC BY ones end in "adapted" or "bearbetad"; the peach page under the espresso menu with a torn edge; "Clips" or "Klipp" under the channel icons in the footer. Fix anything wrong in `ClipsPage.astro` or `Footer.astro`, commit (`fix(webb): ...` with the trailer), and run this step again.

- [ ] **Step 2: The whole gate, one job at a time**

`build:fixtures` (inside `verify:fixtures`) puts the fixture build back in `dist/` before Playwright.

```bash
cd C:/w/birdy-klipp/website && npm run verify:fixtures 2>&1 | tail -12 && npm run test:no-accuracy
cd C:/w/birdy-klipp/website && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test --workers=2 2>&1 | tail -5
cd C:/w/birdy-klipp/website && npm run test:empty-hub
cd C:/w/birdy-klipp/website && npx astro check 2>&1 | tail -3
```

Expected: `verify:fixtures` ends with `check-preview-build` OK after `check-seo OK`, the unit tests (baseline + 19) and every guard; `accuracy-guard OK`; Playwright `N + 24 passed` (N from Task 1) and no failure; `check-empty-hub OK` and `check-seo OK` (the empty build's clips have no links, so no dead link); `astro check` has no more errors than in Task 1. If `verify:fixtures` fails, run it again without `| tail -12` to see the whole error. Fix anything red, commit the fix, and rerun the gate.

- [ ] **Step 3: Push the branch**

```bash
cd C:/w/birdy-klipp && git status --short && git log --oneline origin/main..HEAD && git push -u origin website/klipp
```

Expected: a clean tree; the plan's commit and this plan's task commits listed; the push creates `website/klipp` on GitHub. Vercel builds a preview for every pushed branch of this project.

- [ ] **Step 4: Fetch the preview address**

Vercel reports the preview as a GitHub deployment for the pushed commit; it usually takes 1 to 3 minutes. Run this until it prints `success` (wait between tries with your harness's wait mechanism, for example Monitor with an until-loop, not a foreground `sleep`):

```bash
cd C:/w/birdy-klipp && ID=$(gh api "repos/anonadrek/birdy/deployments?sha=$(git rev-parse HEAD)" --jq '.[0].id') && gh api "repos/anonadrek/birdy/deployments/$ID/statuses" --jq '.[0] | "\(.state) \(.environment_url)"'
```

Expected at the end: `success https://birdy-...-albtab.vercel.app`. Before Vercel has registered the deployment the first call prints nothing and the second fails; that only means "not yet". On `failure`, read the build log in the Vercel dashboard (or ask the controller to), fix, commit, push and repeat.

- [ ] **Step 5: Hand over and stop**

The preview answers `302` to curl (Vercel Authentication); Albin opens it logged in to Vercel. Report to the controller: the preview addresses (`environment_url` plus `/clips/` and `/sv/klipp/`), the branch and its last commit, the test counts before and after, the four QA screenshots in `C:/w/birdy-klipp-qa/`, and these notes for Albin: the preview is built with the real date, so it shows only the clips posted so far (one on 9 Oct, one more each day; the screenshots show all 30); a day's clip appears with the 00.05 build, about eight hours before its 08.00 post. **Stop here.** Merging `website/klipp` into `main`, which takes the page live, happens only after Albin has looked at the preview; the controller does it, together with the status in `CLAUDE.md` on `main`. After that, Albin puts `birdy.community/clips/` in the Instagram and TikTok bios (spec).
