# birdy.community i 1.3-looken — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bygga om birdy.community (startsida, blogg, juridiksidornas färger) i appens 1.3.0-look enligt de godkända mockuperna: palett "Mossa, rost & mässing", levande rödhake och Birdy-fågeln i första vyn, en appkarusell med åtta telefoner byggda i kod, och bloggen med bild per inlägg.

**Architecture:** Astro 5-sajten i `website/` behåller sin struktur (komponenter per sektion, texter i `src/content/copy.{sv,en}.json`, Playwright-tester mot det byggda resultatet). Färgerna blir nya tokens i `src/styles/tokens.css` med tillfälliga alias för de gamla namnen tills allt är omskrivet. Varje startsidesektion byts ut i en egen task: den nya komponenten, dess texter (SV + EN), sidorna som använder den, testet och borttagningen av den gamla komponenten och dess texter görs i samma commit, så att sajten bygger och testerna går gröna efter varje task. Telefonerna är vanliga Astro-komponenter med gemensam CSS (`src/styles/phone.css`), och rörelserna är CSS-animationer med små TypeScript-skript för scroll.

**Tech Stack:** Astro 5.18 (`astro:assets`, content collections med `image()`), Tailwind v4 (bara återställningen används), TypeScript, MapLibre GL 4.7, sharp 0.34 (bildverktyg), Playwright 1.60 (tester), Node-skript för vakter.

**Spec:** `docs/superpowers/specs/2026-09-24-website-1-3-lyft-design.md` (godkända mockups i `docs/superpowers/specs/assets/2026-09-24-website-1-3-lyft/`: `startsida-v5.html`, `blogg.html`; rödhakeunderlaget i `rodhake-utklipp/`).

---

## Förutsättningar och konventioner (läs först)

- **Arbetskatalog:** worktreen `C:/w/birdy-web` på grenen `website/1.3-lyft` (skapas i Task 1). Alla kommandon körs i `C:/w/birdy-web/website` om inget annat står. Rör aldrig huvudkatalogen `C:/Users/abbea/dev/1-mina-projekt/birdy` (andra sessioner arbetar där) och aldrig appens worktree `C:/w/birdy-130`. Lägg till exakta sökvägar i `git add`, aldrig `-A` eller `.`.
- **Skal:** Git Bash. Node och npm finns. Playwright använder systemets Chrome: prefixa alltid testkörningar med `PLAYWRIGHT_CHANNEL=chrome` (Playwrights egna webbläsare är inte installerade på maskinen).
- **Playwright testar det byggda resultatet** (`astro preview` på port 4321). Kör alltid `npm run build` innan `npx playwright test`, annars testas en gammal build.
- **Webbgaten** (grön före varje commit från och med Task 2):
  ```bash
  cd C:/w/birdy-web/website
  npm run test:i18n && npm run test:no-accuracy && npm run test:contrast && npm run test:no-dashes && npm run build && PLAYWRIGHT_CHANNEL=chrome npx playwright test
  ```
  Förväntat: varje vakt skriver `... OK`, bygget slutar med `Complete!`, och Playwright rapporterar `passed` utan `failed`.
- **`npm run check`** (astro check) har i utgångsläget ett känt typfel i `astro.config.mjs` (Vite/Tailwind-typer). Det får finnas kvar; inga nya fel får tillkomma. Körs i Task 1, 13 och 14.
- **Texter:** varje ny text läggs i BÅDA `src/content/copy.sv.json` och `src/content/copy.en.json` med samma nycklar (paritetsvakten fallerar annars). Inga tankstreck (— eller – mellan mellanslag) i publika texter; vakten `test:no-dashes` fångar det. Inga siffror om träffsäkerhet (vakten `test:no-accuracy` förbjuder `72`, `accuracy` och `träffsäker` i copy-filerna). Accentord i rubriker skrivs `*ord*` och renderas kursivt.
- **Mockupen är facit för utseendet.** Öppna `docs/superpowers/specs/assets/2026-09-24-website-1-3-lyft/startsida-v5.html` och `blogg.html` i Chrome (dubbelklick) och jämför. Specen går före mockupen där de skiljer sig (telefonens placering, Dagens fågel-texten, sticky meny, mässingssigill med mörk text).
- **Skärmdumpar för egen kontroll:** efter varje visuell task, ta skärmdumpar med skriptet i Task 1 steg 6 och titta på dem (Read-verktyget) för SV och EN innan du rapporterar.
- **Tillgänglighet:** allt som bara är dekor har `aria-hidden="true"`. Telefonerna har `role="img"` och en beskrivning. Rörelser stängs av med `prefers-reduced-motion`.
- **Commit-rad:** avsluta varje commit med `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.

## Filkarta

| Fil | Ansvar | Task |
|---|---|---|
| `website/scripts/check-contrast.mjs` (ny) | WCAG-vakt för palettens textpar | 2 |
| `website/scripts/check-no-dashes.mjs` (ny) | vakt mot tankstreck i publika texter | 2 |
| `website/package.json` | skripten `test:contrast`, `test:no-dashes` | 2 |
| `website/src/styles/tokens.css` | nya färgtokens + tillfälliga alias | 2, 13 |
| `website/src/styles/global.css` | typsnitt, grundstil, gemensamma klasser (`.wrap`, `.sec`, `.kick`, `.lead`, `.btn`, `.seal`, `.badges`, `svg.i`), reveal, rörelsepaus | 2 |
| `website/src/layouts/Layout.astro` | temafärg, förladdade typsnitt, valfri delningsbild, paus av loopar utanför skärmen | 2 |
| `website/src/components/ui/JournalHeadline.astro` | nya färger och rubrikmått | 2 |
| `website/src/components/ui/Icon.astro` (ny) | alla streckikoner från mockupen | 3 |
| `website/src/components/ui/Kicker.astro` (ny) | versalraden med streck | 3 |
| `website/src/components/ui/Accent.astro` (ny) | `*ord*` → `<em>` i löptext och telefonrubriker | 3 |
| `website/src/components/ui/AppStoreBadge.astro` | nedtonat märke utan "snart!"-lapp | 3 |
| `website/src/components/Nav.astro`, `Footer.astro` | ny meny (genomskinlig/mossgrön, sticky) och sidfot | 4 |
| `website/src/assets/photos/*.webp` + `SOURCES.md` (nya) | sex foton ur appens planscher + källor | 5 |
| `website/src/assets/hero/*` (nya) | platta, rödhakelager, telefonutsnitt | 5 |
| `website/public/brand/birdy-bird.png` (ny) | Birdy-fågelns mask | 5 |
| `website/src/styles/phone.css` (ny) | all stil för telefonerna | 6, 8 |
| `website/src/components/phone/PhoneFrame.astro`, `TabBar.astro` (nya) | telefonram och flikrad | 6, 8 |
| `website/src/components/phone/screens/*.astro` (nya) | åtta skärmar | 6, 8 |
| `website/src/components/Hero.astro` + `hero/HeroScene.astro`, `hero/BirdyBird.astro`, `hero/hero-motion.ts` (nya) | första vyn | 6 |
| `website/src/components/HowItWorks.astro`, `JournalSection.astro` (nya) | Så funkar det, Fältboken | 7 |
| `website/src/components/AppTour.astro`, `app-tour.ts` (nya) | karusellen | 8 |
| `website/src/components/Guide.astro`, `CoverageMap.astro` (nya), `tools/render-coverage-fallback.mjs` (ny), `public/coverage/coverage-fallback.webp` | Uppslagsverket, kartan, reservbild | 9 |
| `website/src/components/Premium.astro`, `Privacy.astro` | omskrivna | 10 |
| `website/src/content.config.ts`, `src/lib/field-notes.ts`, `src/components/NoteCard.astro` (ny), `FieldNotesIndex.astro`, `FieldNoteArticle.astro`, `FieldNotesTeaser.astro`, `src/styles/article-prose.css`, `src/content/field-notes/{sv,en}/why-birdy.md`, `BLOG.md` | bloggen | 11 |
| `website/src/components/Faq.astro`, `ui/FaqItem.astro`, `FinalCta.astro` | Frågor, Ta med Birdy ut i fält | 12 |
| `website/src/layouts/LegalLayout.astro`, `src/pages/legal/index.astro`, `src/styles/legal-prose.css`, `ui/EyebrowLabel.astro`, `tools/generate-og.mjs`, `public/og-field-{en,sv}.png` | juridik, delningsbilder, städning | 13 |
| `website/src/components/HomePage.astro` (ny) | gemensam startsida för båda språken, sektionsordningen | 4, 7–12 |
| `website/src/pages/index.astro`, `src/pages/sv/index.astro` | tunna omslag runt `HomePage` | 4 |
| `website/tests/home.spec.ts` (ny), `tests/smoke.spec.ts`, `tests/coverage.spec.ts` | tester | 6–12 |
| Tas bort: `Loop.astro`, `Listen.astro`, `Glimpse.astro`, `Inside.astro`, `Coverage.astro`, `ui/DeckleEdge.astro`, `ui/CornerBrackets.astro`, `ui/OrnamentRule.astro`, `ui/DeviceFrame.astro`, `ui/FieldIcon.astro` | städning | 7–13 |

---

### Task 1: Arbetsyta och baslinje

**Files:** inga ändringar i koden. Skapar worktreen `C:/w/birdy-web` och grenen `website/1.3-lyft`.

- [ ] **Step 1: Skapa worktree och gren från senaste main**

```bash
git -C C:/Users/abbea/dev/1-mina-projekt/birdy fetch origin
git -C C:/Users/abbea/dev/1-mina-projekt/birdy worktree add C:/w/birdy-web -b website/1.3-lyft origin/main
```
Förväntat: `Preparing worktree (new branch 'website/1.3-lyft')` och `HEAD is now at ...`.

- [ ] **Step 2: Installera beroenden**

```bash
cd C:/w/birdy-web/website && npm ci
```
Förväntat: `added N packages` utan `ERR!`.

- [ ] **Step 3: Kör baslinjen**

```bash
cd C:/w/birdy-web/website
npm run test:i18n && npm run test:no-accuracy && npm run build && PLAYWRIGHT_CHANNEL=chrome npx playwright test
```
Förväntat: `i18n parity OK`, `accuracy-guard OK`, bygget `Complete!`, Playwright `12 passed` (antalet kan skilja med ett par; notera det exakta antalet i rapporten). Om något är rött redan här: stoppa och rapportera, bygg inte vidare.

- [ ] **Step 4: Kör astro check och notera baslinjen**

```bash
cd C:/w/birdy-web/website && npm run check 2>&1 | tail -5
```
Förväntat: ett (1) fel, i `astro.config.mjs`. Notera antal fel/varningar i rapporten; senare tasks jämför mot detta.

- [ ] **Step 5: Pusha grenen och kontrollera att Vercel bygger en förhandsvisning**

```bash
cd C:/w/birdy-web && git push -u origin website/1.3-lyft
sleep 90; gh api repos/anonadrek/birdy/commits/website/1.3-lyft/status --jq '.statuses[] | {context, state, target_url}'
```
Förväntat: en rad med `context` som innehåller `Vercel` och `state` `success` eller `pending`, och en `target_url`. Saknas Vercel-raden helt efter ytterligare två minuter: skriv i rapporten att Albin behöver slå på förhandsvisningar för grenar i Vercel (Project Settings → Git). Det blockerar inte resten av planen.

- [ ] **Step 6: Lägg skärmdumpsskriptet i scratchpad (används i alla visuella tasks)**

Skapa `C:/w/birdy-web/.shots.mjs` (ligger utanför `website/` och ska inte committas; lägg till den i `.git/info/exclude`):

```js
// Usage (from C:/w/birdy-web/website, with `npm run preview` running on 4321):
//   node ../.shots.mjs /sv/ 390,1024,1440,1920 out-dir [--full] [--reduce]
import { createRequire } from 'node:module';
import { mkdirSync } from 'node:fs';
const require = createRequire(`${process.cwd()}/package.json`);
const { chromium } = require('@playwright/test');
const [, , path = '/sv/', widths = '390,1024,1440,1920', out = '../.shots', ...flags] = process.argv;
mkdirSync(out, { recursive: true });
const browser = await chromium.launch({ channel: 'chrome' });
for (const w of widths.split(',').map(Number)) {
  const page = await browser.newPage({ viewport: { width: w, height: 900 }, reducedMotion: flags.includes('--reduce') ? 'reduce' : 'no-preference' });
  await page.goto(`http://localhost:4321${path}`);
  await page.waitForTimeout(2500);
  const name = `${out}/${path.replace(/\W+/g, '_') || 'root'}-${w}.png`;
  await page.screenshot({ path: name, fullPage: flags.includes('--full') });
  console.log(name);
  await page.close();
}
await browser.close();
```
```bash
cd C:/w/birdy-web && printf '.shots.mjs\n.shots/\n' >> .git/info/exclude
```
Om `.git` är en fil (worktree): använd `git -C C:/w/birdy-web rev-parse --git-path info/exclude` för att hitta rätt exclude-fil och lägg raderna där.

Ingen commit i Task 1.

---

### Task 2: Färger, typsnitt och grundstilar

**Files:**
- Create: `website/scripts/check-contrast.mjs`
- Create: `website/scripts/check-no-dashes.mjs`
- Modify: `website/package.json` (scripts)
- Modify: `website/src/styles/tokens.css` (hela filen)
- Modify: `website/src/styles/global.css` (hela filen)
- Modify: `website/src/layouts/Layout.astro` (hela filen)
- Modify: `website/src/components/ui/JournalHeadline.astro` (stilblocket)

- [ ] **Step 1: Skriv kontrastvakten**

Create `website/scripts/check-contrast.mjs`:

```js
#!/usr/bin/env node
// WCAG 2.1 contrast guard for the text colour pairs the site uses (palette in src/styles/tokens.css).
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const css = readFileSync(resolve(root, 'src/styles/tokens.css'), 'utf8');
const tokens = Object.fromEntries(
  [...css.matchAll(/--([a-z0-9-]+):\s*(#[0-9a-fA-F]{6})\s*;/g)].map((m) => [m[1], m[2]]),
);

const channel = (v) => {
  const c = v / 255;
  return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
};
const luminance = (hex) => {
  const n = parseInt(hex.slice(1), 16);
  return 0.2126 * channel((n >> 16) & 255) + 0.7152 * channel((n >> 8) & 255) + 0.0722 * channel(n & 255);
};
const ratio = (a, b) => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};

// [text, background, minimum ratio]
const pairs = [
  ['ink', 'paper', 4.5], ['muted', 'paper', 4.5], ['rust', 'paper', 4.5],
  ['ink', 'card', 4.5], ['muted', 'card', 4.5], ['rust', 'card', 4.5],
  ['cream', 'moss', 4.5], ['apricot', 'moss', 4.5], ['brass-hi', 'moss', 4.5],
  ['cream', 'moss-deep', 4.5], ['apricot', 'moss-deep', 4.5],
  ['cream', 'rust', 4.5], ['cream', 'rust-deep', 4.5],
  ['brass-ink', 'brass', 4.5],
];

let failed = false;
for (const [fg, bg, min] of pairs) {
  const missing = [fg, bg].find((name) => !tokens[name]);
  if (missing) {
    console.error(`contrast-guard FAILED: token --${missing} saknas i tokens.css`);
    failed = true;
    continue;
  }
  const r = ratio(tokens[fg], tokens[bg]);
  if (r < min) {
    console.error(`contrast-guard FAILED: --${fg} på --${bg} = ${r.toFixed(2)}:1 (kräver ${min}:1)`);
    failed = true;
  }
}
if (failed) process.exit(1);
console.log(`contrast-guard OK (${pairs.length} par)`);
```

- [ ] **Step 2: Skriv tankstrecksvakten**

Create `website/scripts/check-no-dashes.mjs`:

```js
#!/usr/bin/env node
// Public copy must not use dash punctuation (see BLOG.md). Guards both copy decks and every field note.
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, join } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const notesDir = resolve(root, 'src/content/field-notes');
const files = [
  'src/content/copy.en.json',
  'src/content/copy.sv.json',
  ...readdirSync(notesDir, { recursive: true })
    .filter((f) => String(f).endsWith('.md'))
    .map((f) => join('src/content/field-notes', String(f))),
];
const banned = [
  { re: /\u2014/, name: 'tankstreck (—)' },
  { re: /\s\u2013\s/, name: 'tankstreck ( – )' },
];

let failed = false;
for (const file of files) {
  const lines = readFileSync(resolve(root, file), 'utf8').split('\n');
  lines.forEach((line, i) => {
    for (const { re, name } of banned) {
      if (re.test(line)) {
        console.error(`no-dashes FAILED: ${file}:${i + 1} innehåller ${name}`);
        failed = true;
      }
    }
  });
}
if (failed) process.exit(1);
console.log(`no-dashes OK (${files.length} filer)`);
```

- [ ] **Step 3: Lägg till skripten i package.json**

I `website/package.json`, lägg till två rader i `"scripts"` direkt efter `"test:no-accuracy"`:

```json
    "test:contrast": "node scripts/check-contrast.mjs",
    "test:no-dashes": "node scripts/check-no-dashes.mjs",
```

- [ ] **Step 4: Kör vakterna och se kontrastvakten falla**

```bash
cd C:/w/birdy-web/website && npm run test:no-dashes && npm run test:contrast
```
Förväntat: `no-dashes OK (4 filer)`, sedan `contrast-guard FAILED: token --ink saknas i tokens.css` (flera rader) och exit 1.

- [ ] **Step 5: Skriv de nya färgtokens**

Replace hela `website/src/styles/tokens.css` med:

```css
/* Birdy 1.3: palett "Mossa, rost & mässing", samma som appen (spec §4.1). */
:root {
  --paper: #F6EFE2;
  --card: #FFFAF1;
  --ink: #26301F;
  --muted: #5B6350;
  --line: #DFD2BA;
  --rust: #9A4526;
  --rust-deep: #72301A;
  --apricot: #F2B27A;
  --moss: #1F2A19;
  --moss-2: #2B3A23;
  --moss-deep: #172013;
  --brass: #B8893A;
  --brass-hi: #E2C07E;
  --brass-ink: #241B0C;
  --cream: #FFF8EE;
  --navy: #1F3A5F;

  --font-serif: 'DM Serif Display', Georgia, 'Times New Roman', serif;
  --font-script: 'Caveat', 'Brush Script MT', cursive;
  --font-sans: 'Inter', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
  --ease-paper: cubic-bezier(0.22, 1, 0.36, 1);
  --section-padding-y: clamp(4.5rem, 8vw, 6rem);
  --container-max: 76rem;

  /* TILLFÄLLIGA alias för sidor som inte är omskrivna ännu. Tas bort i Task 13. */
  --color-paper-bg: var(--paper);
  --color-paper-edge: var(--line);
  --color-paper-top: var(--card);
  --color-rust: var(--rust);
  --color-rust-deep: var(--rust-deep);
  --color-rust-shadow: var(--moss-deep);
  --color-orange: var(--rust);
  --color-apricot: var(--apricot);
  --color-text-primary: var(--ink);
  --color-text-on-hero: var(--cream);
  --color-marginalia-ink: var(--muted);
  --color-stamp-navy: var(--navy);
}
```

- [ ] **Step 6: Kör kontrastvakten igen**

```bash
cd C:/w/birdy-web/website && npm run test:contrast
```
Förväntat: `contrast-guard OK (14 par)`.

- [ ] **Step 7: Skriv om global.css**

Replace hela `website/src/styles/global.css` med:

```css
@import 'tailwindcss';
@import './tokens.css';

@font-face { font-family: 'DM Serif Display'; font-style: italic; font-weight: 400; font-display: swap; src: url('/fonts/dm-serif-display-italic.woff2') format('woff2'); }
@font-face { font-family: 'DM Serif Display'; font-style: normal; font-weight: 400; font-display: swap; src: url('/fonts/dm-serif-display-regular.woff2') format('woff2'); }
@font-face { font-family: 'Caveat'; font-style: normal; font-weight: 400; font-display: swap; src: url('/fonts/caveat-regular.woff2') format('woff2'); }
@font-face { font-family: 'Caveat'; font-style: normal; font-weight: 700; font-display: swap; src: url('/fonts/caveat-bold.woff2') format('woff2'); }
@font-face { font-family: 'Inter'; font-style: normal; font-weight: 400; font-display: swap; src: url('/fonts/inter-regular.woff2') format('woff2'); }
@font-face { font-family: 'Inter'; font-style: normal; font-weight: 600; font-display: swap; src: url('/fonts/inter-semibold.woff2') format('woff2'); }

/* Tailwind utilities are not used; the theme mirrors the tokens for completeness. */
@theme inline {
  --color-paper: var(--paper);
  --color-card: var(--card);
  --color-ink: var(--ink);
  --color-muted: var(--muted);
  --color-line: var(--line);
  --color-rust: var(--rust);
  --color-apricot: var(--apricot);
  --color-moss: var(--moss);
  --color-brass: var(--brass);
  --color-cream: var(--cream);
}

html { scroll-behavior: smooth; scroll-padding-top: 5.5rem; }
body { margin: 0; font-family: var(--font-sans); color: var(--ink); background: var(--paper); -webkit-font-smoothing: antialiased; overflow-x: clip; }
::selection { background: var(--apricot); color: var(--moss-deep); }
a { color: inherit; text-decoration: none; }
button { font: inherit; }
:focus-visible { outline: 3px solid var(--rust); outline-offset: 3px; }
h1, h2, h3, h4, h5 { font-family: var(--font-serif); font-weight: 400; margin: 0; }
em { font-style: italic; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }

/* Shared building blocks (spec §4.3). */
.wrap { max-width: 1160px; margin: 0 auto; padding: 0 44px; }
.sec { padding: 96px 0; }
.kick { display: flex; align-items: center; gap: 10px; margin: 0 0 14px; font-size: 11px; font-weight: 600; letter-spacing: .17em; text-transform: uppercase; color: var(--kick-color, var(--rust)); }
.kick-line { width: 22px; height: 1px; background: currentColor; flex: none; }
.kick--center { justify-content: center; }
.lead { font-size: 16px; line-height: 1.65; color: var(--lead-color, var(--muted)); margin: 14px 0 0; max-width: 34rem; }
.btn { display: inline-flex; align-items: center; gap: 8px; background: linear-gradient(135deg, var(--rust), var(--rust-deep)); color: var(--cream); padding: 10px 16px; border-radius: 10px; font-weight: 600; font-size: 13px; box-shadow: inset 0 1px 0 rgba(255, 255, 255, .18); transition: transform .25s var(--ease-paper); }
.btn:hover { transform: translateY(-2px); }
.badges { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; }
.free { display: inline-block; margin-left: 8px; font-family: var(--font-sans); font-size: 9.5px; letter-spacing: .12em; text-transform: uppercase; font-weight: 600; color: var(--rust); border: 1px solid rgba(154, 69, 38, .35); border-radius: 20px; padding: 2px 8px; vertical-align: middle; }
svg.i { fill: none; stroke: currentColor; stroke-width: 1.6; stroke-linecap: round; stroke-linejoin: round; flex: none; }

/* Stamps (seals), same shapes as the app. Text on brass is dark (brass-ink), never cream. */
.seal { width: 46px; height: 46px; border-radius: 50%; display: grid; place-items: center; color: var(--cream); position: relative; transform: rotate(-8deg); flex: none; background: radial-gradient(circle at 35% 30%, rgba(255, 255, 255, .25), transparent 55%), var(--rust); box-shadow: 0 3px 8px rgba(31, 42, 25, .3); font-family: var(--font-serif); font-style: italic; font-size: 12px; line-height: 1; }
.seal::before { content: ''; position: absolute; inset: 3px; border-radius: 50%; border: 1px solid rgba(255, 255, 255, .4); }
.seal--brass { background: radial-gradient(circle at 35% 30%, rgba(255, 255, 255, .45), transparent 55%), var(--brass); color: var(--brass-ink); }
.seal--navy { background: radial-gradient(circle at 35% 30%, rgba(255, 255, 255, .2), transparent 55%), var(--navy); }
.seal--off { background: none; box-shadow: none; border: 1.3px dashed var(--line); color: transparent; }
.seal--off::before { display: none; }
.seal--mini { width: 22px; height: 22px; font-size: 7px; transform: rotate(-6deg); }
.seal--mini::before { inset: 2px; }

@media (max-width: 760px) {
  .wrap { padding: 0 20px; }
  .sec { padding: 72px 0; }
}

/* Scroll reveal (unchanged behaviour). */
html.motion-ready [data-reveal] { opacity: 0; transform: translate3d(0, 20px, 0); transition: opacity .68s var(--ease-paper) var(--rd, 0ms), transform .68s var(--ease-paper) var(--rd, 0ms); }
html.motion-ready [data-reveal].is-visible { opacity: 1; transform: none; }
html.motion-ready [data-reveal="scale"] { transform: translate3d(0, 18px, 0) scale(.975); }
html.motion-ready [data-reveal="scale"].is-visible { transform: none; }
html.motion-ready [data-reveal="slide"] { transform: translate3d(-22px, 0, 0); }
html.motion-ready [data-reveal="slide"].is-visible { transform: none; }

/* Looping animations pause while their section is off screen (Layout script toggles .is-paused). */
.is-paused, .is-paused * { animation-play-state: paused !important; }

@media (prefers-reduced-motion: reduce) {
  html { scroll-behavior: auto; }
  html.motion-ready [data-reveal], html.motion-ready [data-reveal="scale"], html.motion-ready [data-reveal="slide"] { opacity: 1; transform: none; }
  /* Delays are zeroed too: an animation waiting out its delay still counts as running. */
  *, *::before, *::after { animation-duration: 0.01ms !important; animation-delay: 0s !important; animation-iteration-count: 1 !important; transition-duration: 0.01ms !important; transition-delay: 0s !important; }
}
```

- [ ] **Step 8: Uppdatera JournalHeadline till nya färger och rubrikmått**

I `website/src/components/ui/JournalHeadline.astro`, ersätt hela `<style>`-blocket med:

```astro
<style>
  /* Colors resolve via --jh-* so dark panels can retheme headlines
     by setting the vars on any ancestor. */
  .journal-headline {
    font-family: var(--font-serif);
    font-style: normal;
    font-weight: 400;
    font-size: var(--size);
    line-height: 1.02;
    letter-spacing: -.018em;
    color: var(--jh-ink, var(--ink));
    text-align: var(--jh-ta, center);
    margin: 0;
  }
  .plain { font-family: var(--font-serif); font-style: normal; }
  .accent {
    font-family: var(--font-serif);
    font-style: italic;
    font-weight: 400;
    font-size: 1em;
    color: var(--jh-accent, var(--rust));
  }
</style>
```

- [ ] **Step 9: Skriv om Layout.astro**

Replace hela `website/src/layouts/Layout.astro` med:

```astro
---
import '../styles/global.css';
import Analytics from '@vercel/analytics/astro';
import { getCopy, alternateHref, type Locale } from '../lib/i18n';

interface Props {
  locale: Locale;
  pathname: string;
  noAlternateLocale?: boolean;
  title?: string;
  description?: string;
  articleDate?: string;
  /** Site-relative or absolute URL of a 1200×630 share image. Defaults to the locale's og-field image. */
  ogImage?: string;
  ogImageAlt?: string;
}

const { locale, pathname, noAlternateLocale = false, title, description, articleDate, ogImage, ogImageAlt } = Astro.props;
const copy = getCopy(locale);
const metaTitle = title ?? copy.meta.title;
const metaDescription = description ?? copy.meta.description;
const altLocale: Locale = locale === 'en' ? 'sv' : 'en';
const altPath = alternateHref(locale, pathname);
const canonical = new URL(pathname, Astro.site).toString();
const ogImageUrl = new URL(ogImage ?? `/og-field-${locale}.png`, Astro.site).toString();
const ogAlt = ogImageAlt ?? copy.alt.panorama;
const englishPath = locale === 'en' ? pathname : altPath;

const jsonLd = {
  '@context': 'https://schema.org',
  '@graph': [
    {
      '@type': 'WebSite',
      name: 'Birdy',
      url: new URL('/', Astro.site).toString(),
      inLanguage: ['en', 'sv'],
    },
    ...((pathname === '/' || pathname === '/sv/') ? [{
      '@type': 'MobileApplication',
      name: 'Birdy',
      description: metaDescription,
      operatingSystem: 'Android',
      applicationCategory: 'LifestyleApplication',
      url: canonical,
      downloadUrl: 'https://play.google.com/store/apps/details?id=se.birdy.android',
      offers: { '@type': 'Offer', price: '0', priceCurrency: 'SEK' },
      inLanguage: locale,
      image: ogImageUrl,
    }] : []),
    ...(articleDate ? [{
      '@type': 'BlogPosting',
      headline: metaTitle.replace(/ \| Birdy$/, ''),
      description: metaDescription,
      datePublished: articleDate,
      inLanguage: locale,
      mainEntityOfPage: canonical,
      author: { '@type': 'Organization', name: 'Birdy' },
      publisher: { '@type': 'Organization', name: 'Birdy' },
      image: ogImageUrl,
    }] : []),
    ...((pathname === '/' || pathname === '/sv/') ? [{
      '@type': 'FAQPage',
      mainEntity: copy.faq.items.map((item) => ({
        '@type': 'Question',
        name: item.q,
        acceptedAnswer: { '@type': 'Answer', text: item.a },
      })),
    }] : []),
  ],
};
---

<!doctype html>
<html lang={copy.meta.htmlLang}>
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <meta name="generator" content={Astro.generator} />

    <title>{metaTitle}</title>
    <meta name="description" content={metaDescription} />

    <link rel="canonical" href={canonical} />
    {!noAlternateLocale && <link rel="alternate" hreflang={locale} href={canonical} />}
    {!noAlternateLocale && (
      <link
        rel="alternate"
        hreflang={altLocale}
        href={new URL(altPath, Astro.site).toString()}
      />
    )}
    {!noAlternateLocale && <link rel="alternate" hreflang="x-default" href={new URL(englishPath, Astro.site).toString()} />}

    <link rel="icon" href="/favicon.ico" sizes="any" />
    <link rel="icon" type="image/svg+xml" href="/favicon.svg" />
    <link rel="icon" type="image/png" sizes="16x16" href="/favicon-16.png" />
    <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32.png" />
    <link rel="icon" type="image/png" sizes="192x192" href="/favicon-192.png" />
    <link rel="apple-touch-icon" sizes="180x180" href="/apple-touch-icon.png" />

    <link rel="preload" href="/fonts/dm-serif-display-regular.woff2" as="font" type="font/woff2" crossorigin />
    <link rel="preload" href="/fonts/dm-serif-display-italic.woff2" as="font" type="font/woff2" crossorigin />
    <link rel="preload" href="/fonts/inter-regular.woff2" as="font" type="font/woff2" crossorigin />
    <link rel="preload" href="/fonts/caveat-bold.woff2" as="font" type="font/woff2" crossorigin />

    <meta property="og:type" content={articleDate ? 'article' : 'website'} />
    {articleDate && <meta property="article:published_time" content={articleDate} />}
    <meta property="og:locale" content={copy.meta.ogLocale} />
    <meta property="og:title" content={metaTitle} />
    <meta property="og:description" content={metaDescription} />
    <meta property="og:url" content={canonical} />
    <meta property="og:image" content={ogImageUrl} />
    <meta property="og:image:alt" content={ogAlt} />
    <meta property="og:image:width" content="1200" />
    <meta property="og:image:height" content="630" />
    <meta property="og:site_name" content="Birdy" />

    <meta name="twitter:card" content="summary_large_image" />
    <meta name="twitter:title" content={metaTitle} />
    <meta name="twitter:description" content={metaDescription} />
    <meta name="twitter:image" content={ogImageUrl} />

    <meta name="theme-color" content="#1F2A19" />

    <script is:inline type="application/ld+json" set:html={JSON.stringify(jsonLd)} />
  </head>
  <body>
    <slot />
    <Analytics />
    <script>
      const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
      const revealTargets = [...document.querySelectorAll<HTMLElement>('[data-reveal]')];
      if (revealTargets.length && !reduceMotion && 'IntersectionObserver' in window) {
        document.documentElement.classList.add('motion-ready');
        const observer = new IntersectionObserver((entries) => {
          for (const entry of entries) {
            if (!entry.isIntersecting) continue;
            entry.target.classList.add('is-visible');
            observer.unobserve(entry.target);
          }
        }, { threshold: 0.12, rootMargin: '0px 0px -32px 0px' });
        for (const target of revealTargets) observer.observe(target);
      }

      // Looping animations (robin, sound waves) pause while their section is off screen.
      const loops = [...document.querySelectorAll<HTMLElement>('[data-loop]')];
      if (loops.length && 'IntersectionObserver' in window) {
        const pauser = new IntersectionObserver((entries) => {
          for (const entry of entries) entry.target.classList.toggle('is-paused', !entry.isIntersecting);
        });
        for (const el of loops) pauser.observe(el);
      }
    </script>
  </body>
</html>
```

- [ ] **Step 10: Kör webbgaten**

Kör webbgaten (se Förutsättningar). Förväntat: alla vakter OK, bygget grönt och samma antal Playwright-tester gröna som i Task 1. Sidan ser nu grön-rost ut i stället för orange (de gamla komponenterna läser färgerna via aliasen).

- [ ] **Step 11: Commit**

```bash
cd C:/w/birdy-web
git add website/scripts/check-contrast.mjs website/scripts/check-no-dashes.mjs website/package.json website/src/styles/tokens.css website/src/styles/global.css website/src/layouts/Layout.astro website/src/components/ui/JournalHeadline.astro
git commit -m "feat(website): 1.3-palett, grundstilar och vakter för kontrast och tankstreck

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Små byggstenar — ikoner, kicker, accent och App Store-märket

**Files:**
- Create: `website/src/components/ui/Icon.astro`
- Create: `website/src/components/ui/Kicker.astro`
- Create: `website/src/components/ui/Accent.astro`
- Modify: `website/src/components/ui/AppStoreBadge.astro` (hela filen)
- Modify: `website/src/content/copy.sv.json`, `website/src/content/copy.en.json` (ta bort `appStoreBadge.note`)

- [ ] **Step 1: Skapa Icon.astro**

Create `website/src/components/ui/Icon.astro`:

```astro
---
// Stroke icons from the approved mockup (24×24 grid). Styled by the global `svg.i` rule.
const paths = {
  camera: '<path d="M4 8h3l2-2.5h6L17 8h3v11H4z"/><circle cx="12" cy="13" r="3.5"/>',
  photo: '<rect x="4" y="5" width="16" height="14" rx="1.5"/><circle cx="9" cy="10" r="1.6"/><path d="M4 17l5-4 4 3 3-2 4 3"/>',
  wave: '<path d="M4 12h1M8 8v8M12 5v14M16 9v6M20 11v2"/>',
  pencil: '<path d="M4 20h4L19 9l-4-4L4 16z"/>',
  map: '<path d="M9 5 4 7v12l5-2 6 2 5-2V5l-5 2z"/><path d="M9 5v12M15 7v12"/>',
  pdf: '<path d="M6 3h9l4 4v14H6z"/><path d="M15 3v4h4M9 13h6M9 17h4"/>',
  chart: '<path d="M5 19V11M10 19V6M15 19v-5M20 19V9"/>',
  badge: '<circle cx="12" cy="10" r="5"/><path d="M9 14.5 8 20l4-2 4 2-1-5.5"/>',
  'no-account': '<circle cx="12" cy="8" r="3.5"/><path d="M5 20c1.5-4 4-5.5 7-5.5s5.5 1.5 7 5.5"/><path d="M4 4l16 16"/>',
  phone: '<rect x="7" y="3" width="10" height="18" rx="2"/><path d="M11 17h2"/>',
  pin: '<path d="M12 21s-6-5.5-6-11a6 6 0 0 1 12 0c0 5.5-6 11-6 11z"/><circle cx="12" cy="10" r="2.2"/>',
  'arrow-left': '<path d="M15 6l-6 6 6 6"/>',
  'arrow-right': '<path d="M9 6l6 6-6 6"/>',
  menu: '<path d="M4 9h16M4 15h16"/>',
  'tab-identify': '<path d="M4 8V5h3M17 5h3v3M20 16v3h-3M7 19H4v-3"/><circle cx="12" cy="12" r="2.5"/>',
  'tab-journal': '<path d="M5 4h9a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3z"/>',
  'tab-archive': '<path d="M7 4h10v16l-5-3.5L7 20z"/>',
  'tab-badges': '<circle cx="12" cy="10" r="5"/><path d="M9 14.5 8 20l4-2 4 2-1-5.5"/>',
  'tab-map': '<path d="M9 5 4 7v12l5-2 6 2 5-2V5l-5 2z"/>',
} as const;

export type IconName = keyof typeof paths;

interface Props {
  name: IconName;
  size?: number;
  class?: string;
}
const { name, size = 18, class: cls } = Astro.props;
---

<svg class:list={['i', cls]} width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" focusable="false" set:html={paths[name]} />
```

- [ ] **Step 2: Skapa Kicker.astro**

Create `website/src/components/ui/Kicker.astro`:

```astro
---
// The small uppercase line with a hairline before it (and after it when centered).
// Colour comes from --kick-color on an ancestor (rust by default, apricot on moss).
interface Props {
  text: string;
  center?: boolean;
  class?: string;
}
const { text, center = false, class: cls } = Astro.props;
---

<p class:list={['kick', { 'kick--center': center }, cls]}><span class="kick-line" aria-hidden="true"></span>{text}{center && <span class="kick-line" aria-hidden="true"></span>}</p>
```

- [ ] **Step 3: Skapa Accent.astro**

Create `website/src/components/ui/Accent.astro`:

```astro
---
// Renders copy that uses the `*word*` accent syntax with <em> for the accent, for places
// where JournalHeadline's heading wrapper is not wanted (phone screens, card titles).
import { parseHeadline } from '../../lib/headline';

interface Props { text: string }
const tokens = parseHeadline(Astro.props.text);
---

{tokens.map((t) => (t.kind === 'accent' ? <em>{t.text}</em> : t.text))}
```

- [ ] **Step 4: Skriv om App Store-märket (nedtonat, ingen lapp)**

Replace hela `website/src/components/ui/AppStoreBadge.astro` med:

```astro
---
import { type Locale, getCopy } from '../../lib/i18n';

// Store-style App Store badge rendered inline (no official asset while the iOS app is
// unreleased). Deliberately NOT a link and dimmed: the iPhone app is on its way.
interface Props {
  locale: Locale;
  size?: 'small' | 'large';
  class?: string;
}
const { locale, size = 'small', class: cls = '' } = Astro.props;
const t = getCopy(locale);
const w = size === 'large' ? 190 : 140;
const h = size === 'large' ? 68 : 50;
---

<span class={`appstore ${cls}`} role="img" aria-label={t.alt.appStoreBadge} title={t.alt.appStoreBadge} aria-disabled="true">
  <svg width={w} height={h} viewBox="0 0 190 68" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <rect x="0.5" y="0.5" width="189" height="67" rx="10" fill="#0B0F09" stroke="#FFFFFF" stroke-opacity="0.35" />
    <g transform="translate(17 16) scale(1.5)">
      <path
        fill="#FFF8EE"
        d="M12.152 6.896c-.948 0-2.415-1.078-3.96-1.04-2.04.027-3.91 1.183-4.961 3.014-2.117 3.675-.546 9.103 1.519 12.09 1.013 1.454 2.208 3.09 3.792 3.039 1.52-.065 2.09-.987 3.935-.987 1.831 0 2.35.987 3.96.948 1.637-.026 2.676-1.48 3.676-2.948 1.156-1.688 1.636-3.325 1.662-3.415-.039-.013-3.182-1.221-3.22-4.857-.026-3.04 2.48-4.494 2.597-4.559-1.429-2.09-3.623-2.324-4.39-2.376-2-.156-3.675 1.09-4.61 1.09zM15.53 3.83c.843-1.012 1.4-2.427 1.245-3.83-1.207.052-2.662.805-3.532 1.818-.78.896-1.454 2.338-1.273 3.714 1.338.104 2.715-.688 3.56-1.702"
      />
    </g>
    <text x="62" y="28" fill="#FFF8EE" font-family="Inter, system-ui, sans-serif" font-size="11" letter-spacing="0.02em">{t.appStoreBadge.top}</text>
    <text x="62" y="50" fill="#FFF8EE" font-family="Inter, system-ui, sans-serif" font-size="21" font-weight="600" letter-spacing="0.01em">{t.appStoreBadge.bottom}</text>
  </svg>
</span>

<style>
  .appstore { display: inline-block; line-height: 0; cursor: default; user-select: none; opacity: .62; }
  .appstore svg { display: block; width: 100%; height: auto; }
</style>
```

- [ ] **Step 5: Ta bort den oanvända texten `appStoreBadge.note`**

I `website/src/content/copy.sv.json`, ersätt objektet `"appStoreBadge"` med:

```json
  "appStoreBadge": {
    "top": "Snart på",
    "bottom": "App Store"
  },
```

I `website/src/content/copy.en.json`, ersätt objektet `"appStoreBadge"` med:

```json
  "appStoreBadge": {
    "top": "Coming soon to the",
    "bottom": "App Store"
  },
```

- [ ] **Step 6: Kontrollera att inget annat läser `appStoreBadge.note`**

```bash
cd C:/w/birdy-web/website && grep -rn "appStoreBadge.note" src || echo "inga träffar"
```
Förväntat: `inga träffar`.

- [ ] **Step 7: Kör webbgaten**

Förväntat: grönt. (`Icon`, `Kicker` och `Accent` används först i senare tasks; App Store-märket syns redan nedtonat utan lapp i heron och slutsektionen.)

- [ ] **Step 8: Commit**

```bash
cd C:/w/birdy-web
git add website/src/components/ui/Icon.astro website/src/components/ui/Kicker.astro website/src/components/ui/Accent.astro website/src/components/ui/AppStoreBadge.astro website/src/content/copy.sv.json website/src/content/copy.en.json
git commit -m "feat(website): ikoner, kicker, accent och nedtonat App Store-märke

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Sidans ram — meny och sidfot

**Files:**
- Modify: `website/src/components/Nav.astro` (hela filen)
- Modify: `website/src/components/Footer.astro` (hela filen)
- Modify: `website/src/content/copy.sv.json`, `website/src/content/copy.en.json` (objekten `nav` och `footer`)
- Create: `website/src/components/HomePage.astro` (gemensam startsida för båda språken)
- Modify: `website/src/pages/index.astro`, `website/src/pages/sv/index.astro` (blir tunna omslag runt `HomePage`)
- Create: `website/tests/home.spec.ts`

- [ ] **Step 1: Skriv de fallerande testerna**

Create `website/tests/home.spec.ts`:

```ts
import { test, expect } from '@playwright/test';
import { trackConsoleErrors } from './test-helpers';

test.describe('meny och sidfot', () => {
  for (const [path, label, getApp] of [['/sv/', 'Så funkar det', 'Hämta appen'], ['/', 'How it works', 'Get the app']] as const) {
    test(`menyn på ${path} har nya länkar och blir mossgrön efter första vyn`, async ({ page }) => {
      const errors = trackConsoleErrors(page);
      await page.setViewportSize({ width: 1280, height: 800 });
      await page.goto(path);
      const nav = page.locator('#site-nav');
      await expect(nav.locator('.links a').first()).toHaveText(label);
      await expect(nav.locator('.nav-cta')).toHaveText(getApp);
      await expect(nav.locator('.nav-cta')).toHaveAttribute('href', `${path}#download`);
      await page.mouse.wheel(0, 3000);
      await expect(nav).toHaveClass(/is-solid/);
      expect(errors).toEqual([]);
    });
  }

  test('mobilmenyn öppnas och stängs med Esc', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/');
    const toggle = page.locator('#site-nav .menu-toggle');
    await expect(page.locator('#mobile-menu')).toBeHidden();
    await toggle.click();
    await expect(page.locator('#mobile-menu')).toBeVisible();
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await expect(page.locator('#mobile-menu a', { hasText: 'Integritet' })).toHaveAttribute('href', '/sv/#privacy');
    await page.keyboard.press('Escape');
    await expect(page.locator('#mobile-menu')).toBeHidden();
  });

  test('sidfoten har kolumnerna och albIT-länken', async ({ page }) => {
    await page.goto('/sv/');
    const footer = page.locator('footer.footer');
    await expect(footer.locator('.fh')).toHaveText(['Utforska', 'Läs', 'Information']);
    await expect(footer.locator('a[href="/legal/privacy/"]')).toHaveText('Integritetspolicy');
    await expect(footer.locator('a[href="https://www.albit.se/#produkter"]')).toHaveText('albIT');
  });
});
```

- [ ] **Step 2: Kör testerna och se dem falla**

```bash
cd C:/w/birdy-web/website && npm run build && PLAYWRIGHT_CHANNEL=chrome npx playwright test tests/home.spec.ts
```
Förväntat: 4 failed (t.ex. `locator('#site-nav .links a')` hittas inte).

- [ ] **Step 3: Nya menytexter**

I `website/src/content/copy.sv.json`, ersätt hela objektet `"nav"` med:

```json
  "nav": {
    "label": "Huvudmeny",
    "howItWorks": "Så funkar det",
    "app": "Appen",
    "premium": "Premium",
    "fieldNotes": "Fältanteckningar",
    "guide": "Uppslagsverket",
    "privacy": "Integritet",
    "faq": "Frågor",
    "switchLang": "EN",
    "switchLangHref": "/",
    "getApp": "Hämta appen",
    "menuOpen": "Öppna menyn",
    "menuClose": "Stäng menyn"
  },
```

I `website/src/content/copy.en.json`, ersätt hela objektet `"nav"` med:

```json
  "nav": {
    "label": "Main navigation",
    "howItWorks": "How it works",
    "app": "The app",
    "premium": "Premium",
    "fieldNotes": "Field notes",
    "guide": "Field guide",
    "privacy": "Privacy",
    "faq": "FAQ",
    "switchLang": "SV",
    "switchLangHref": "/sv/",
    "getApp": "Get the app",
    "menuOpen": "Open menu",
    "menuClose": "Close menu"
  },
```

- [ ] **Step 4: Nya sidfotstexter**

I `website/src/content/copy.sv.json`, ersätt hela objektet `"footer"` med:

```json
  "footer": {
    "tagline": "Känn igen fågeln. Bevara stunden.",
    "explore": "Utforska",
    "read": "Läs",
    "information": "Information",
    "whyBirdy": "Varför Birdy finns",
    "writeUs": "Skriv till oss",
    "privacyPolicy": "Integritetspolicy",
    "terms": "Villkor",
    "dataSafety": "Datasäkerhet",
    "otherLanguage": "English",
    "copyright": "© 2026 Birdy · Skapad i Sverige",
    "builtBy": "Byggd av"
  },
```

I `website/src/content/copy.en.json`, ersätt hela objektet `"footer"` med:

```json
  "footer": {
    "tagline": "Know the bird. Keep the moment.",
    "explore": "Explore",
    "read": "Read",
    "information": "Information",
    "whyBirdy": "Why Birdy exists",
    "writeUs": "Write to us",
    "privacyPolicy": "Privacy policy",
    "terms": "Terms",
    "dataSafety": "Data safety",
    "otherLanguage": "Svenska",
    "copyright": "© 2026 Birdy · Made in Sweden",
    "builtBy": "Built by"
  },
```

- [ ] **Step 5: Skriv om Nav.astro**

Replace hela `website/src/components/Nav.astro` med:

```astro
---
import Wordmark from './ui/Wordmark.astro';
import Icon from './ui/Icon.astro';
import { type Locale, getCopy } from '../lib/i18n';
import { fieldNotesHref } from '../lib/field-notes';

interface Props {
  locale: Locale;
  switchLangHref?: string;
  /** overlay = transparent over a dark first view until it scrolls past; solid = moss bar in the flow. */
  variant?: 'overlay' | 'solid';
}
const { locale, switchLangHref, variant = 'solid' } = Astro.props;
const t = getCopy(locale);
const home = locale === 'sv' ? '/sv/' : '/';
const langHref = switchLangHref ?? t.nav.switchLangHref;
const langAttr = locale === 'sv' ? 'en' : 'sv';
const links = [
  { href: `${home}#how-it-works`, label: t.nav.howItWorks },
  { href: `${home}#app`, label: t.nav.app },
  { href: `${home}#premium`, label: t.nav.premium },
  { href: fieldNotesHref(locale), label: t.nav.fieldNotes },
];
const mobileOnly = [
  { href: `${home}#guide`, label: t.nav.guide },
  { href: `${home}#privacy`, label: t.nav.privacy },
  { href: `${home}#faq`, label: t.nav.faq },
];
---

<nav id="site-nav" class:list={['nav', `nav--${variant}`]} aria-label={t.nav.label} data-variant={variant}>
  <div class="nav-inner">
    <a href={home} class="brand" aria-label="Birdy"><Wordmark size="32px" alt={t.alt.wordmark} color="var(--cream)" /></a>
    <div class="links">
      {links.map((l) => <a href={l.href}>{l.label}</a>)}
      <a class="lang" href={langHref} lang={langAttr}>{t.nav.switchLang}</a>
    </div>
    <a class="btn nav-cta" href={`${home}#download`}>{t.nav.getApp}</a>
    <button class="menu-toggle" type="button" aria-controls="mobile-menu" aria-expanded="false" aria-label={t.nav.menuOpen} data-label-open={t.nav.menuOpen} data-label-close={t.nav.menuClose}>
      <Icon name="menu" size={24} />
    </button>
  </div>
  <div id="mobile-menu" class="mobile-menu" hidden>
    {[...links, ...mobileOnly].map((l) => <a href={l.href}>{l.label}</a>)}
    <a href={langHref} lang={langAttr}>{t.nav.switchLang}</a>
    <a class="btn" href={`${home}#download`}>{t.nav.getApp}</a>
  </div>
</nav>

<script>
  const nav = document.getElementById('site-nav');
  const toggle = nav?.querySelector<HTMLButtonElement>('.menu-toggle');
  const menu = document.getElementById('mobile-menu');
  const setOpen = (open: boolean) => {
    if (!nav || !toggle || !menu) return;
    toggle.setAttribute('aria-expanded', String(open));
    toggle.setAttribute('aria-label', (open ? toggle.dataset.labelClose : toggle.dataset.labelOpen) ?? '');
    menu.hidden = !open;
    nav.classList.toggle('is-open', open);
  };
  toggle?.addEventListener('click', () => setOpen(toggle.getAttribute('aria-expanded') !== 'true'));
  menu?.querySelectorAll('a').forEach((a) => a.addEventListener('click', () => setOpen(false)));
  addEventListener('keydown', (e) => { if (e.key === 'Escape') setOpen(false); });
  addEventListener('resize', () => { if (innerWidth > 900) setOpen(false); });

  // Overlay variant: transparent over the dark first view ([data-nav-until]), moss once it has scrolled past.
  if (nav?.dataset.variant === 'overlay') {
    const until = document.querySelector<HTMLElement>('[data-nav-until]');
    if (!until || !('IntersectionObserver' in window)) {
      nav.classList.add('is-solid');
    } else {
      new IntersectionObserver(([entry]) => nav.classList.toggle('is-solid', !entry.isIntersecting), {
        rootMargin: `-${nav.offsetHeight}px 0px 0px 0px`,
      }).observe(until);
    }
  }
</script>

<style>
  .nav { z-index: 100; color: var(--cream); font-size: 13px; font-weight: 600; transition: background-color .3s var(--ease-paper), box-shadow .3s var(--ease-paper); }
  .nav--overlay { position: fixed; top: 0; left: 0; right: 0; }
  .nav--solid { position: sticky; top: 0; }
  .nav--solid, .nav.is-solid, .nav.is-open { background: var(--moss); box-shadow: 0 1px 0 rgba(255, 255, 255, .08); }
  .nav :global(:focus-visible) { outline-color: var(--apricot); }
  .nav-inner { max-width: 1320px; margin: 0 auto; height: 76px; display: flex; align-items: center; gap: 26px; padding: 0 44px; }
  .brand { margin-right: auto; display: inline-flex; }
  .links { display: flex; align-items: center; gap: 26px; }
  .links a { opacity: .82; transition: opacity .2s; }
  .links a:hover { opacity: 1; }
  .menu-toggle { display: none; background: none; border: 0; color: inherit; width: 44px; height: 44px; align-items: center; justify-content: center; cursor: pointer; }
  .mobile-menu { display: grid; padding: 4px 20px 20px; }
  .mobile-menu[hidden] { display: none; }
  .mobile-menu a { padding: 14px 0; border-bottom: 1px solid rgba(255, 255, 255, .1); font-size: 15px; }
  .mobile-menu .btn { margin-top: 16px; justify-content: center; border-bottom: 0; }
  @media (max-width: 900px) {
    .links, .nav-cta { display: none; }
    .menu-toggle { display: inline-flex; }
    .nav-inner { height: 64px; padding: 0 20px; }
  }
</style>
```

- [ ] **Step 6: Skriv om Footer.astro**

Replace hela `website/src/components/Footer.astro` med:

```astro
---
import Wordmark from './ui/Wordmark.astro';
import { type Locale, getCopy } from '../lib/i18n';
import { fieldNotesHref } from '../lib/field-notes';

interface Props { locale: Locale; switchLangHref?: string }
const { locale, switchLangHref } = Astro.props;
const t = getCopy(locale);
const home = locale === 'sv' ? '/sv/' : '/';
const blogPrefix = locale === 'sv' ? '/sv' : '';
---

<footer class="footer">
  <div class="wrap">
    <div class="fgrid">
      <div class="fbrand">
        <a href={home} aria-label="Birdy"><Wordmark size="38px" alt={t.alt.wordmark} color="var(--apricot)" /></a>
        <p class="tag">{t.footer.tagline}</p>
      </div>
      <div class="col">
        <p class="fh">{t.footer.explore}</p>
        <a href={`${home}#how-it-works`}>{t.nav.howItWorks}</a>
        <a href={`${home}#app`}>{t.nav.app}</a>
        <a href={`${home}#premium`}>{t.nav.premium}</a>
        <a href={`${home}#faq`}>{t.nav.faq}</a>
      </div>
      <div class="col">
        <p class="fh">{t.footer.read}</p>
        <a href={fieldNotesHref(locale)}>{t.nav.fieldNotes}</a>
        <a href={`${blogPrefix}/blog/why-birdy/`}>{t.footer.whyBirdy}</a>
        <a href="mailto:albin@abrahamssons.se">{t.footer.writeUs}</a>
      </div>
      <div class="col">
        <p class="fh">{t.footer.information}</p>
        <a href="/legal/privacy/">{t.footer.privacyPolicy}</a>
        <a href="/legal/terms/">{t.footer.terms}</a>
        <a href="/legal/data-safety/">{t.footer.dataSafety}</a>
        <a href={switchLangHref ?? t.nav.switchLangHref} lang={locale === 'sv' ? 'en' : 'sv'}>{t.footer.otherLanguage}</a>
      </div>
    </div>
    <div class="fbot">
      <span>{t.footer.copyright}</span>
      <span>{t.footer.builtBy} <a href="https://www.albit.se/#produkter">albIT</a></span>
    </div>
  </div>
</footer>

<style>
  .footer { background: var(--moss-deep); color: #E9E2D2; padding: 70px 0 26px; }
  .footer :global(:focus-visible) { outline-color: var(--apricot); }
  .fgrid { display: grid; grid-template-columns: 1.6fr 1fr 1fr 1fr; gap: 40px; }
  .tag { margin: 12px 0 0; color: rgba(233, 226, 210, .7); font-size: 14px; }
  .col { display: flex; flex-direction: column; align-items: flex-start; }
  .fh { font-size: 11px; letter-spacing: .16em; text-transform: uppercase; color: var(--apricot); margin: 0 0 14px; font-weight: 600; }
  .col a { font-size: 14px; color: rgba(233, 226, 210, .82); margin-bottom: 10px; transition: color .2s; }
  .col a:hover { color: var(--apricot); }
  .fbot { display: flex; justify-content: space-between; gap: 20px; margin-top: 56px; padding-top: 18px; border-top: 1px solid rgba(255, 255, 255, .1); font-size: 12.5px; color: rgba(233, 226, 210, .55); }
  .fbot a { color: var(--apricot); }
  @media (max-width: 760px) {
    .fgrid { grid-template-columns: 1fr 1fr; }
    .fbrand { grid-column: 1 / -1; }
    .fbot { flex-direction: column; }
  }
</style>
```

- [ ] **Step 7: En gemensam startsida för båda språken, med den genomskinliga menyn**

`index.astro` och `sv/index.astro` är identiska förutom språket. Samla innehållet i en komponent så att senare tasks bara ändrar på ett ställe.

Create `website/src/components/HomePage.astro`:

```astro
---
import Layout from '../layouts/Layout.astro';
import Nav from './Nav.astro';
import Hero from './Hero.astro';
import Loop from './Loop.astro';
import Glimpse from './Glimpse.astro';
import Listen from './Listen.astro';
import Inside from './Inside.astro';
import Coverage from './Coverage.astro';
import Premium from './Premium.astro';
import Privacy from './Privacy.astro';
import Faq from './Faq.astro';
import FieldNotesTeaser from './FieldNotesTeaser.astro';
import FinalCta from './FinalCta.astro';
import Footer from './Footer.astro';
import type { Locale } from '../lib/i18n';

interface Props { locale: Locale }
const { locale } = Astro.props;
const pathname = locale === 'sv' ? '/sv/' : '/';
---

<Layout locale={locale} pathname={pathname}>
  <Nav locale={locale} variant="overlay" />
  <main>
    <Hero locale={locale} />
    <Loop locale={locale} />
    <Glimpse locale={locale} />
    <Listen locale={locale} />
    <Inside locale={locale} />
    <Coverage locale={locale} />
    <Premium locale={locale} />
    <Privacy locale={locale} />
    <Faq locale={locale} />
    <FieldNotesTeaser locale={locale} />
    <FinalCta locale={locale} />
  </main>
  <Footer locale={locale} />
</Layout>
```

Replace hela `website/src/pages/index.astro` med:

```astro
---
import HomePage from '../components/HomePage.astro';
---

<HomePage locale="en" />
```

Replace hela `website/src/pages/sv/index.astro` med:

```astro
---
import HomePage from '../../components/HomePage.astro';
---

<HomePage locale="sv" />
```

Övriga sidor (blogg, juridik) anropar `<Nav ... />` utan variant och får därmed den mossgröna menyn.

- [ ] **Step 8: Kör testerna**

```bash
cd C:/w/birdy-web/website && npm run build && PLAYWRIGHT_CHANNEL=chrome npx playwright test tests/home.spec.ts
```
Förväntat: `4 passed`. Om testet "blir mossgrön" fallerar: startsidans gamla hero saknar `data-nav-until` fram till Task 6, så menyn ska vara mossgrön direkt; kontrollera att skriptet lägger `is-solid` när `[data-nav-until]` saknas.

- [ ] **Step 9: Kör webbgaten och titta på skärmdumpar**

Kör webbgaten. Starta sedan förhandsvisningen i en egen terminal (`npm run preview`) och ta skärmdumpar:

```bash
cd C:/w/birdy-web/website && node ../.shots.mjs /sv/ 390,1440 && node ../.shots.mjs /sv/blog/ 390,1440 && node ../.shots.mjs /legal/ 1440
```
Titta på bilderna: menyn är mossgrön med cremefärgad text på blogg och juridik, och sidfoten är mörk mossa med fyra kolumner (två på mobil).

- [ ] **Step 10: Commit**

```bash
cd C:/w/birdy-web
git add website/src/components/Nav.astro website/src/components/Footer.astro website/src/components/HomePage.astro website/src/content/copy.sv.json website/src/content/copy.en.json website/src/pages/index.astro website/src/pages/sv/index.astro website/tests/home.spec.ts
git commit -m "feat(website): ny meny (genomskinlig till mossgrön, sticky), sidfot och gemensam startsida

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Bilder — foton, rödhakelager och Birdy-fågeln

**Files:**
- Create: `website/src/assets/hero/robin-plate.webp`, `website/src/assets/hero/robin-layer.png`, `website/src/assets/hero/phone-robin.webp` (kopior från spec-mappen)
- Create: `website/public/brand/birdy-bird.png` (kopia från spec-mappen)
- Create: `website/src/assets/photos/{stjartmes-q170831,domherre-q25382,talgoxe-q25485,ladusvala-q25429,skaggmes-q192817,rodhake-q25334}.webp`
- Create: `website/src/assets/photos/SOURCES.md`

Rödhakeunderlaget togs fram i brainstormen (`docs/superpowers/specs/assets/2026-09-24-website-1-3-lyft/rodhake-utklipp/`): plattan är `hero-robin.webp` med rödhaken borttagen och ifylld (WebP kvalitet 95), lagret är rödhaken med alfa (PNG, 419×502), och telefonutsnittet är ett förbeskuret utsnitt av rödhaken (500×600). Geometrin står i `layers.json` där.

- [ ] **Step 1: Kopiera rödhakebilderna och fågelmasken**

```bash
cd C:/w/birdy-web
S=docs/superpowers/specs/assets/2026-09-24-website-1-3-lyft
mkdir -p website/src/assets/hero website/public/brand
cp "$S/rodhake-utklipp/robin-plate.webp" "$S/rodhake-utklipp/robin-layer.png" "$S/rodhake-utklipp/phone-robin.webp" website/src/assets/hero/
cp "$S/bird.png" website/public/brand/birdy-bird.png
```

- [ ] **Step 2: Skala ner de sex fotona ur appens planscher**

```bash
cd C:/w/birdy-web/website && node -e "
const sharp = require('sharp');
const fs = require('fs');
const src = '../asset-pack/src/main/assets/images';
const photos = { 'stjartmes-q170831': 'Q170831', 'domherre-q25382': 'Q25382', 'talgoxe-q25485': 'Q25485', 'ladusvala-q25429': 'Q25429', 'skaggmes-q192817': 'Q192817', 'rodhake-q25334': 'Q25334' };
fs.mkdirSync('src/assets/photos', { recursive: true });
(async () => {
  for (const [name, qid] of Object.entries(photos)) {
    const out = 'src/assets/photos/' + name + '.webp';
    await sharp(src + '/' + qid + '/hero.webp').resize({ width: 1600, withoutEnlargement: true }).webp({ quality: 86 }).toFile(out);
    const m = await sharp(out).metadata();
    console.log(name, m.width + 'x' + m.height);
  }
})();"
```
Förväntat: sex rader, alla `1600x...` (domherre `1600x1455`, talgoxe och skäggmes `1600x1067`, övriga `1600x1200`).

- [ ] **Step 3: Kontrollera rödhakebildernas mått**

```bash
cd C:/w/birdy-web/website && node -e "
const sharp = require('sharp');
(async () => { for (const f of ['src/assets/hero/robin-plate.webp', 'src/assets/hero/robin-layer.png', 'src/assets/hero/phone-robin.webp', 'public/brand/birdy-bird.png']) { const m = await sharp(f).metadata(); console.log(f, m.width + 'x' + m.height, m.hasAlpha ? 'alpha' : ''); } })();"
```
Förväntat: `robin-plate.webp 1672x941`, `robin-layer.png 419x502 alpha`, `phone-robin.webp 500x600`, `birdy-bird.png 237x229 alpha`.

- [ ] **Step 4: Skriv källförteckningen**

Create `website/src/assets/photos/SOURCES.md`:

```markdown
# Bildkällor

Fotona är appens egna planschfoton (`asset-pack/src/main/assets/images/<QID>/hero.webp`), nedskalade till 1600 px. Metadata finns i `shared/content/species/**/<QID>.yaml`. Alla är CC0 eller public domain, så ingen namngivning krävs, men källan ska stå här för varje bild som läggs till (även bilder till blogginlägg).

| Fil | Art | QID | Fotograf | Licens | Används i |
|---|---|---|---|---|---|
| `stjartmes-q170831.webp` | Stjärtmes | Q170831 | Membeth | CC0 | karusellen: Identifiera |
| `domherre-q25382.webp` | Domherre | Q25382 | Estormiz | CC0 | karusellen: Träff och Fältboken |
| `talgoxe-q25485.webp` | Talgoxe | Q25485 | Hobbyfotowiki | CC0 | karusellen: Artprofil |
| `ladusvala-q25429.webp` | Ladusvala | Q25429 | Аимаина хикари | CC0 | Fältboken (planschen) |
| `skaggmes-q192817.webp` | Skäggmes | Q192817 | Hobbyfotowiki | CC0 | Ta med Birdy ut i fält |
| `rodhake-q25334.webp` | Rödhake | Q25334 | Rob Hille | Public domain | blogginlägget "Varför Birdy finns" |

Heron (`src/assets/hero/`) bygger på webbens AI-genererade `src/assets/hero-robin.webp`. Underlag och skript: `docs/superpowers/specs/assets/2026-09-24-website-1-3-lyft/rodhake-utklipp/`.
```

- [ ] **Step 5: Kör webbgaten**

Förväntat: grönt (bilderna används först i Task 6).

- [ ] **Step 6: Commit**

```bash
cd C:/w/birdy-web
git add website/src/assets/hero website/public/brand/birdy-bird.png website/src/assets/photos
git commit -m "feat(website): foton ur appens planscher, rödhakelager och Birdy-fågelns mask

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Första vyn — levande rödhake, Birdy-fågeln och telefonen

**Files:**
- Create: `website/src/styles/phone.css`
- Create: `website/src/components/phone/PhoneFrame.astro`
- Create: `website/src/components/phone/screens/MatchScreen.astro`
- Create: `website/src/components/hero/HeroScene.astro`
- Create: `website/src/components/hero/BirdyBird.astro`
- Create: `website/src/components/hero/hero-motion.ts`
- Modify: `website/src/components/Hero.astro` (hela filen)
- Modify: `website/src/content/copy.sv.json`, `website/src/content/copy.en.json` (`hero` ersätts, `phone` läggs till)
- Modify: `website/tests/home.spec.ts` (nya tester sist i filen)

Kompositionsreglerna (spec §5.2) är uppmätta i en prototyp i 390–2560 px: under 1024 px ligger fotot som ett band överst och texten under; 1024–1279 px har två spalter med fotot högerställt och telefonen under texten; från 1280 px vänsterställs och breddas fotot så att rödhaken flyttar åt höger och telefonen står i luckan (`left: 548px` i `.wrap`); från 2000 px har fotot en fast maxbredd och tonas in från mossan. Siffrorna nedan ska inte ändras utan att geometritesterna körs.

- [ ] **Step 1: Skriv de fallerande testerna**

Lägg till sist i `website/tests/home.spec.ts`:

```ts
test.describe('första vyn', () => {
  test.use({ contextOptions: { reducedMotion: 'reduce' } });

  for (const [path, line1, line2, kicker] of [
    ['/sv/', 'Känn igen fågeln.', 'Bevara stunden.', 'Fågelguide och fältdagbok'],
    ['/', 'Know the bird.', 'Keep the moment.', 'Bird guide and field journal'],
  ] as const) {
    test(`rubrik, kicker, metarad och telefon på ${path}`, async ({ page }) => {
      const errors = trackConsoleErrors(page);
      await page.goto(path);
      const hero = page.locator('[data-hero]');
      await expect(hero.locator('h1')).toContainText(line1);
      await expect(hero.locator('h1 em')).toHaveText(line2);
      await expect(hero.locator('.copy .kick')).toHaveText(kicker);
      await expect(hero.locator('.meta li')).toHaveCount(3);
      await expect(hero.locator('[data-hero-phone] .ph[role="img"]')).toHaveCount(1);
      await expect(hero.locator('[data-robin] img').first()).toBeVisible();
      await expect(hero.locator('[data-birdy]')).toBeVisible();
      expect(errors).toEqual([]);
    });
  }

  for (const width of [390, 1024, 1280, 1440, 1920]) {
    test(`telefonen täcker inte rödhaken och rödhaken syns helt i ${width} px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await page.goto('/sv/');
      const phone = (await page.locator('[data-hero-phone] .ph').boundingBox())!;
      const robin = (await page.locator('[data-robin]').boundingBox())!;
      const overlaps = phone.x < robin.x + robin.width && robin.x < phone.x + phone.width
        && phone.y < robin.y + robin.height && robin.y < phone.y + phone.height;
      expect(overlaps, `telefon ${JSON.stringify(phone)} rödhake ${JSON.stringify(robin)}`).toBe(false);
      expect(robin.x).toBeGreaterThanOrEqual(0);
      expect(robin.x + robin.width).toBeLessThanOrEqual(width);
    });
  }

  test('rödhakens ruta börjar under menyn i 1920 px', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 900 });
    await page.goto('/sv/');
    const nav = (await page.locator('#site-nav').boundingBox())!;
    const robin = (await page.locator('[data-robin]').boundingBox())!;
    expect(robin.y).toBeGreaterThanOrEqual(nav.y + nav.height);
  });

  test('rubriken ryms på två rader på dator', async ({ page }) => {
    for (const width of [1024, 1280, 1440, 1920]) {
      await page.setViewportSize({ width, height: 900 });
      await page.goto('/sv/');
      const lines = await page.locator('[data-hero] h1').evaluate((h) => Math.round(h.getBoundingClientRect().height / parseFloat(getComputedStyle(h).lineHeight)));
      expect(lines, `${width} px`).toBeLessThanOrEqual(2);
    }
  });
});
```

- [ ] **Step 2: Kör testerna och se dem falla**

```bash
cd C:/w/birdy-web/website && npm run build && PLAYWRIGHT_CHANNEL=chrome npx playwright test tests/home.spec.ts -g "första vyn"
```
Förväntat: FAIL (`[data-hero]` finns inte).

- [ ] **Step 3: Nya texter för heron och träffskärmen**

I `website/src/content/copy.sv.json`, ersätt hela objektet `"hero"` med följande och lägg till objektet `"phone"` direkt efter det:

```json
  "hero": {
    "kicker": "Fågelguide och fältdagbok",
    "headline": "Känn igen fågeln.",
    "line2": "Bevara stunden.",
    "sub": "Rikta kameran, välj ett foto eller låt fågeln sjunga. Birdy föreslår arten direkt i telefonen, och du sparar fyndet i din egen fältdagbok.",
    "metaLabel": "Birdy i korthet",
    "meta": ["839 europeiska arter", "Fungerar utan täckning", "Inget konto"]
  },
  "phone": {
    "match": {
      "label": "Birdys träffskärm: rödhake, säker match 94 procent, första i fältboken",
      "kicker": "Match · Fynd nr 12",
      "species": "Rödhake",
      "latin": "Erithacus rubecula",
      "confidence": "Säker match · 94 %",
      "newSpecies": "Ny art",
      "first": "Första i din fältbok!",
      "seal": "Nr 12",
      "note": "Lägg till en anteckning",
      "save": "Spara i fältboken",
      "more": "Inte den? Se fler förslag"
    }
  },
```

I `website/src/content/copy.en.json`, ersätt hela objektet `"hero"` med följande och lägg till `"phone"` direkt efter det:

```json
  "hero": {
    "kicker": "Bird guide and field journal",
    "headline": "Know the bird.",
    "line2": "Keep the moment.",
    "sub": "Point the camera, pick a photo or let the bird sing. Birdy suggests the species right on your phone, and you save the sighting in your own field journal.",
    "metaLabel": "Birdy at a glance",
    "meta": ["839 European species", "Works without a signal", "No account"]
  },
  "phone": {
    "match": {
      "label": "Birdy's match screen: European robin, a confident 94 percent match, first in the field journal",
      "kicker": "Match · Find no. 12",
      "species": "European Robin",
      "latin": "Erithacus rubecula",
      "confidence": "Confident match · 94%",
      "newSpecies": "New species",
      "first": "First in your field journal!",
      "seal": "No. 12",
      "note": "Add a note",
      "save": "Save to field journal",
      "more": "Not it? See more suggestions"
    }
  },
```

- [ ] **Step 4: Telefonernas gemensamma stil**

Create `website/src/styles/phone.css`:

```css
/* Phone mockups drawn in code (hero + app tour), ported from the approved mockup.
   Everything is prefixed ph- so it never collides with page classes. Design size 250×520 px. */
.ph { width: 250px; height: 520px; border-radius: 34px; overflow: hidden; position: relative; flex: none; background: var(--paper); color: var(--ink); font-family: var(--font-sans); font-size: 10px; line-height: 1.3; text-align: left; user-select: none; box-shadow: 0 0 0 7px #0F140C, 0 0 0 8px rgba(255, 255, 255, .1), 0 30px 50px rgba(0, 0, 0, .4); }
.ph-screen { position: absolute; inset: 0; }
.ph-status { position: absolute; top: 10px; left: 22px; right: 22px; display: flex; justify-content: space-between; font-size: 9.5px; font-weight: 600; z-index: 6; color: var(--cream); }
.ph-status--dark { color: var(--ink); }
.ph h4, .ph h5 { font-family: var(--font-serif); font-weight: 400; margin: 0; }
.ph p { margin: 0; }
.ph .kick { font-size: 7.5px; gap: 6px; margin: 0 0 6px; letter-spacing: .14em; }
.ph .kick-line { width: 12px; }

/* Photo top with the name on a moss gradient */
.ph-hero { position: relative; overflow: hidden; }
.ph-hero > img { position: absolute; inset: 0; width: 100%; height: 100%; max-width: none; object-fit: cover; }
.ph-hero::after { content: ''; position: absolute; inset: 0; background: linear-gradient(0deg, rgba(31, 42, 25, .97) 0%, rgba(31, 42, 25, .6) 40%, rgba(31, 42, 25, .05) 78%, rgba(0, 0, 0, .2) 100%); }
.ph-hc { position: absolute; left: 16px; right: 16px; bottom: 14px; z-index: 2; color: var(--cream); --kick-color: var(--apricot); }
.ph-hc h4 { font-size: 32px; line-height: .95; letter-spacing: -.02em; }
.ph-hc h4 em { display: block; font-size: 15px; color: var(--apricot); margin-top: 4px; letter-spacing: 0; }
.ph-hc .ph-name { font-size: 34px; }
.ph-latin { font-family: var(--font-serif); font-style: italic; color: rgba(255, 248, 238, .75); font-size: 11px; }
.ph-pmeta { display: flex; justify-content: space-between; margin-top: 9px; padding-top: 6px; border-top: 1px solid rgba(255, 255, 255, .28); font-size: 7px; letter-spacing: .12em; text-transform: uppercase; color: rgba(255, 248, 238, .72); }

/* Match (result) */
.ph-conf { display: flex; align-items: center; gap: 7px; margin-top: 7px; font-size: 8.5px; font-weight: 600; }
.ph-conf-bar { width: 56px; height: 3px; border-radius: 3px; background: rgba(255, 255, 255, .2); overflow: hidden; }
.ph-conf-bar span { display: block; height: 100%; background: var(--apricot); transform-origin: left; }
.ph-sheet { position: absolute; left: 0; right: 0; bottom: 0; background: var(--paper); border-radius: 20px 20px 0 0; padding: 14px 16px 0; }
.ph-srow { display: flex; align-items: center; gap: 9px; }
.ph-srow .t { flex: 1; }
.ph-srow small { display: block; font-size: 7px; color: var(--muted); letter-spacing: .12em; text-transform: uppercase; font-weight: 600; }
.ph-srow b { display: block; font-family: var(--font-script); font-size: 18px; color: var(--rust); line-height: 1; }
.ph-noterow { margin: 10px 0; padding: 8px 0; border-bottom: 1px solid var(--line); color: var(--muted); font-size: 9.5px; display: flex; gap: 6px; align-items: center; }
.ph-cta { display: flex; justify-content: center; padding: 10px; border-radius: 12px; color: var(--cream); font-weight: 600; font-size: 10.5px; background: linear-gradient(135deg, var(--rust), var(--rust-deep)); box-shadow: 0 5px 12px rgba(31, 42, 25, .25), inset 0 1px 0 rgba(255, 255, 255, .18); }
.ph-ghost { text-align: center; margin-top: 8px; font-size: 9.5px; color: var(--rust); font-weight: 600; }

/* Entry animation, hero phone only */
.ph--animate .ph-conf-bar span { animation: ph-fill 1.2s .9s var(--ease-paper) both; }
.ph--animate .ph-srow .seal { animation: ph-press .7s 1.6s var(--ease-paper) both; }
@keyframes ph-fill { from { transform: scaleX(0); } to { transform: scaleX(1); } }
@keyframes ph-press { 0% { transform: scale(1.6) rotate(-20deg); opacity: 0; } 60% { transform: scale(.92) rotate(-6deg); opacity: 1; } 100% { transform: scale(1) rotate(-8deg); } }
```

- [ ] **Step 5: Telefonramen**

Create `website/src/components/phone/PhoneFrame.astro`:

```astro
---
import '../../styles/phone.css';

interface Props {
  /** What the screen shows, read by screen readers. The drawn UI inside is decorative. */
  label: string;
  /** Dark status bar text, for screens with a light top. */
  darkStatus?: boolean;
  /** Plays the confidence bar and stamp entry animation (hero). */
  animate?: boolean;
  class?: string;
}
const { label, darkStatus = false, animate = false, class: cls } = Astro.props;
---

<div class:list={['ph', { 'ph--animate': animate }, cls]} role="img" aria-label={label}>
  <div class:list={['ph-status', { 'ph-status--dark': darkStatus }]} aria-hidden="true"><span>9:41</span><span>●●● ▮</span></div>
  <div class="ph-screen" aria-hidden="true"><slot /></div>
</div>
```

- [ ] **Step 6: Träffskärmen**

Create `website/src/components/phone/screens/MatchScreen.astro`:

```astro
---
import { Image } from 'astro:assets';
import type { ImageMetadata } from 'astro';
import Icon from '../../ui/Icon.astro';
import Kicker from '../../ui/Kicker.astro';

interface Props {
  photo: ImageMetadata;
  photoPosition?: string;
  copy: {
    kicker: string; species: string; latin: string; confidence: string; newSpecies: string;
    first: string; seal: string; note: string; save: string; more: string;
  };
  /** 0 to 100, drawn as the confidence bar. */
  confidence: number;
  eager?: boolean;
}
const { photo, photoPosition = 'center', copy, confidence, eager = false } = Astro.props;
---

<div class="ph-hero" style="height:300px">
  <Image src={photo} alt="" widths={[250, 500]} sizes="250px" style={`object-position:${photoPosition}`} loading={eager ? 'eager' : 'lazy'} decoding="async" />
  <div class="ph-hc" style="bottom:34px">
    <Kicker text={copy.kicker} />
    <h4 class="ph-name">{copy.species}</h4>
    <i class="ph-latin">{copy.latin}</i>
    <div class="ph-conf"><div class="ph-conf-bar"><span style={`width:${confidence}%`}></span></div>{copy.confidence}</div>
  </div>
</div>
<div class="ph-sheet" style="top:278px">
  <div class="ph-srow">
    <div class="t"><small>{copy.newSpecies}</small><b>{copy.first}</b></div>
    <div class="seal">{copy.seal}</div>
  </div>
  <div class="ph-noterow"><Icon name="pencil" size={12} />{copy.note}</div>
  <div class="ph-cta">{copy.save}</div>
  <div class="ph-ghost">{copy.more}</div>
</div>
```

- [ ] **Step 7: Det levande fotot**

Create `website/src/components/hero/HeroScene.astro`:

```astro
---
import { Image } from 'astro:assets';
import plate from '../../assets/hero/robin-plate.webp';
import robin from '../../assets/hero/robin-layer.png';

// Geometry (percent of the 1672×941 photo) from
// docs/superpowers/specs/assets/2026-09-24-website-1-3-lyft/rodhake-utklipp/layers.json
const glints = [
  { left: 58.61, top: 93.09, delay: 0.6 },
  { left: 62.68, top: 90.97, delay: 2.3 },
  { left: 80.44, top: 78.11, delay: 3.4 },
  { left: 53.11, top: 79.91, delay: 1.5 },
];
---

<div class="scene-clip" aria-hidden="true" data-loop>
  <div class="scene" data-scene>
    <Image class="plate" src={plate} alt="" widths={[800, 1200, 1672]} sizes="(max-width: 1023px) 782px, 100vw" loading="eager" fetchpriority="high" decoding="async" />
    <div class="rb" data-robin>
      <div class="rb-bob" data-robin-bob>
        <div class="rb-breathe">
          <Image src={robin} alt="" width={419} height={502} format="webp" loading="eager" decoding="async" />
          <i class="lid"><Image src={robin} alt="" width={419} height={502} format="webp" loading="eager" decoding="async" /></i>
        </div>
      </div>
    </div>
    {glints.map((g) => <i class="glint" style={`left:${g.left}%;top:${g.top}%;animation-delay:${g.delay}s`}></i>)}
  </div>
</div>

<style>
  /* Up to 1023 px: a photo band at the top, right-anchored so the robin stays in view (mockup, mobile). */
  .scene-clip { position: absolute; top: 0; left: 0; right: 0; height: max(440px, 56.28vw); overflow: hidden; }
  .scene-clip::after { content: ''; position: absolute; inset: 0; background: linear-gradient(180deg, rgba(31, 42, 25, .35) 0, rgba(31, 42, 25, 0) 25%, rgba(31, 42, 25, 0) 48%, rgba(31, 42, 25, .88) 80%, var(--moss) 100%); }
  .scene { position: absolute; top: 0; right: 0; width: max(782px, 100%); aspect-ratio: 1672 / 941; }
  .scene :global(.plate) { position: absolute; inset: 0; width: 100%; height: 100%; max-width: none; }

  /* The robin layer sits exactly where it was cut out; feet are the transform origin. */
  .rb { position: absolute; left: 63.038%; top: 15.409%; width: 25.06%; height: 53.348%; }
  .rb-bob, .rb-breathe { position: absolute; inset: 0; transform-origin: 33.93% 98.61%; }
  .rb :global(img) { position: absolute; inset: 0; width: 100%; height: 100%; max-width: none; }
  .rb-breathe { animation: robin-breathe 3.8s ease-in-out infinite; }
  .rb-bob { animation: robin-bob 8s 2.4s infinite; }
  /* Eyelid: the feathers above the eye, slid down over it for a blink. */
  .lid { position: absolute; left: 28.04%; top: 10.64%; width: 7.64%; height: 6.37%; border-radius: 50%; overflow: hidden; transform: scaleY(0); transform-origin: 50% 0; -webkit-mask: radial-gradient(closest-side, #000 72%, transparent); mask: radial-gradient(closest-side, #000 72%, transparent); animation: robin-blink 5.3s 1.2s infinite; }
  .lid :global(img) { inset: auto; left: -367%; top: -72%; width: 1308.9%; height: 1569.9%; max-width: none; }
  .glint { position: absolute; width: 16px; height: 16px; margin: -8px 0 0 -8px; border-radius: 50%; background: radial-gradient(circle, rgba(255, 252, 240, 1) 0, rgba(255, 236, 196, .55) 28%, rgba(255, 236, 196, 0) 68%); opacity: 0; pointer-events: none; animation: dew-glint 5s infinite; }

  @keyframes robin-breathe { 0%, 100% { transform: none; } 50% { transform: scale(1.007, 1.016); } }
  @keyframes robin-bob { 0%, 85%, 100% { transform: none; } 87.5% { transform: scale(1.012, .962); } 90.5% { transform: scale(.994, 1.022); } 93.5% { transform: none; } }
  @keyframes robin-blink { 0%, 94%, 100% { transform: scaleY(0); } 95.5%, 96.5% { transform: scaleY(1); } }
  @keyframes dew-glint { 0%, 72%, 100% { opacity: 0; transform: scale(.3); } 80% { opacity: 1; transform: scale(1.15); } 90% { opacity: 0; transform: scale(.5); } }
  @media (prefers-reduced-motion: reduce) { .rb-breathe, .rb-bob, .lid, .glint { animation: none; } }

  /* 1024 px and up: the photo fills the first view (cover), right-anchored. */
  @media (min-width: 1024px) {
    .scene-clip { inset: 0; height: auto; container-type: size; }
    .scene-clip::after { background: linear-gradient(90deg, rgba(31, 42, 25, .95) 0%, rgba(31, 42, 25, .8) 34%, rgba(31, 42, 25, .2) 62%, rgba(31, 42, 25, 0) 80%), linear-gradient(0deg, rgba(31, 42, 25, .6), transparent 34%); }
    .scene { width: max(100cqw, 100cqh * 1.7768); }
  }
  /* 1280 px and up: left-anchored and up to 12 % wider, so the robin moves right and the phone fits
     between text and robin; capped so the robin's feet stay inside the first view. */
  @media (min-width: 1280px) {
    .scene { right: auto; left: 0; width: max(100cqw, 100cqh * 1.7768, min(112cqw, (100cqh - 12px) * 2.5826)); }
  }
  /* 2000 px and up: fixed maximum size, right-anchored, fading into the moss on the left. */
  @media (min-width: 2000px) {
    .scene { left: auto; right: 0; width: min(2100px, (100cqh - 12px) * 2.5826); -webkit-mask-image: linear-gradient(90deg, transparent, #000 18%); mask-image: linear-gradient(90deg, transparent, #000 18%); }
  }
</style>
```

- [ ] **Step 8: Birdy-fågeln**

Create `website/src/components/hero/BirdyBird.astro`:

```astro
---
// The app's bird (public/brand/birdy-bird.png) as an apricot mask. It lands above the kicker on
// load; hero-motion.ts flies it towards the robin on scroll ([data-birdy] gets an inline transform).
---

<div class="birdy" data-birdy aria-hidden="true"><i class="birdy-shape"></i></div>

<style>
  .birdy { position: relative; z-index: 6; width: 62px; height: 62px; margin: 0 0 20px; will-change: transform, opacity; }
  .birdy-shape { display: block; width: 100%; height: 100%; background: var(--apricot); -webkit-mask: url('/brand/birdy-bird.png') center / contain no-repeat; mask: url('/brand/birdy-bird.png') center / contain no-repeat; opacity: 0; animation: birdy-land 1.2s .15s var(--ease-paper) forwards; }
  @keyframes birdy-land {
    0% { opacity: 0; transform: translate(-38px, -28px) rotate(-18deg) scale(.78); }
    65% { opacity: 1; transform: translate(2px, 2px) rotate(3deg) scale(1.04); }
    100% { opacity: 1; transform: none; }
  }
  @media (prefers-reduced-motion: reduce) { .birdy-shape { opacity: 1; animation: none; } }
  @media (max-width: 760px) { .birdy { width: 52px; height: 52px; margin-bottom: 16px; } }
</style>
```

- [ ] **Step 9: Scrollrörelserna**

Create `website/src/components/hero/hero-motion.ts`:

```ts
// Scroll-driven motion for the first view: photo parallax, the Birdy bird's flight towards the
// robin, and the robin's answering bob. Progressive enhancement only; nothing here is needed to read the page.
const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
const hero = document.querySelector<HTMLElement>('[data-hero]');
const scene = document.querySelector<HTMLElement>('[data-scene]');
const bird = document.querySelector<HTMLElement>('[data-birdy]');
const bob = document.querySelector<HTMLElement>('[data-robin-bob]');

if (hero && !reduce) {
  const FLIGHT = 320; // px of scroll over which the bird flies away
  let raf = 0;
  let armed = true;

  const update = () => {
    raf = 0;
    const y = Math.max(0, Math.min(scrollY, hero.offsetHeight));
    if (scene) scene.style.translate = `0 ${(y * 0.14).toFixed(1)}px`;
    if (!bird) return;
    const p = Math.min(y, FLIGHT) / FLIGHT;
    const dx = innerWidth * 0.55 * Math.pow(p, 0.6);
    bird.style.transform = `translate(${dx.toFixed(1)}px, ${(-p * 90).toFixed(1)}px) rotate(${(-p * 24).toFixed(1)}deg) scale(${(1 - p * 0.55).toFixed(3)})`;
    bird.style.opacity = String(1 - p * p);
    if (bob && armed && p > 0.35) {
      armed = false;
      bob.animate(
        [{ transform: 'none' }, { transform: 'scale(1.012, .962)', offset: 0.3 }, { transform: 'scale(.994, 1.022)', offset: 0.6 }, { transform: 'none' }],
        { duration: 520, easing: 'ease-out' },
      );
    }
    if (p < 0.1) armed = true;
  };

  const schedule = () => { if (!raf) raf = requestAnimationFrame(update); };
  addEventListener('scroll', schedule, { passive: true });
  addEventListener('resize', schedule, { passive: true });
  update();
}

export {};
```

- [ ] **Step 10: Skriv om Hero.astro**

Replace hela `website/src/components/Hero.astro` med:

```astro
---
import PlayStoreBadge from './ui/PlayStoreBadge.astro';
import AppStoreBadge from './ui/AppStoreBadge.astro';
import Kicker from './ui/Kicker.astro';
import HeroScene from './hero/HeroScene.astro';
import BirdyBird from './hero/BirdyBird.astro';
import PhoneFrame from './phone/PhoneFrame.astro';
import MatchScreen from './phone/screens/MatchScreen.astro';
import phoneRobin from '../assets/hero/phone-robin.webp';
import { type Locale, getCopy } from '../lib/i18n';

interface Props { locale: Locale }
const { locale } = Astro.props;
const t = getCopy(locale);
const playUrl = 'https://play.google.com/store/apps/details?id=se.birdy.android';
---

<header class="hero" data-hero data-nav-until>
  <div class="photo">
    <HeroScene />
    <div class="copy-wrap">
      <div class="wrap">
        <div class="copy">
          <BirdyBird />
          <Kicker text={t.hero.kicker} />
          <h1>{t.hero.headline}<em>{t.hero.line2}</em></h1>
          <p class="sub">{t.hero.sub}</p>
          <div class="badges">
            <PlayStoreBadge locale={locale} href={playUrl} alt={t.alt.playStoreBadge} size="small" loading="eager" />
            <AppStoreBadge locale={locale} size="small" />
          </div>
        </div>
      </div>
    </div>
    <div class="meta-wrap">
      <div class="wrap"><ul class="meta" aria-label={t.hero.metaLabel}>{t.hero.meta.map((m) => <li>{m}</li>)}</ul></div>
    </div>
  </div>
  <div class="phone-slot">
    <div class="wrap">
      <div class="hero-phone" data-hero-phone>
        <PhoneFrame label={t.phone.match.label} animate>
          <MatchScreen photo={phoneRobin} copy={t.phone.match} confidence={94} eager />
        </PhoneFrame>
      </div>
    </div>
  </div>
</header>

<script>
  import './hero/hero-motion';
</script>

<style>
  .hero { position: relative; background: var(--moss); color: var(--cream); --kick-color: var(--apricot); }
  .hero :global(:focus-visible) { outline-color: var(--apricot); }
  .photo { position: relative; }
  .copy-wrap { position: relative; z-index: 2; padding-top: calc(max(440px, 56.28vw) - 110px); }
  .copy { max-width: 470px; }
  h1 { font-size: 44px; line-height: .95; letter-spacing: -.025em; }
  h1 em { display: block; color: var(--apricot); }
  .sub { font-size: 15px; line-height: 1.65; color: rgba(255, 248, 238, .86); margin: 16px 0 20px; max-width: 28rem; }
  .meta-wrap { position: relative; z-index: 2; }
  .meta { display: flex; flex-wrap: wrap; gap: 8px 14px; margin: 26px 0 0; padding: 10px 0 22px; border-top: 1px solid rgba(255, 255, 255, .28); font-size: 9px; letter-spacing: .14em; text-transform: uppercase; color: rgba(255, 248, 238, .78); }
  /* Below 1280 px the phone hangs under the text and overlaps the next section (mockup, mobile). */
  .phone-slot { position: relative; z-index: 5; }
  .phone-slot > .wrap { display: flex; justify-content: center; }
  .hero-phone { margin: -10px 0 -150px; transform: scale(.86) rotate(-2deg); }

  @media (min-width: 761px) {
    h1 { font-size: clamp(46px, 4.6vw, 64px); }
    .sub { font-size: 16px; margin: 22px 0 26px; }
  }
  @media (min-width: 1024px) {
    .photo { height: clamp(640px, min(86svh, 58vw), 820px); }
    .copy-wrap { position: absolute; inset: 0; display: flex; align-items: center; padding: 0; }
    .copy-wrap > .wrap { width: 100%; }
    .meta-wrap { position: absolute; left: 0; right: 0; bottom: 22px; }
    .meta { gap: 34px; margin: 0; padding: 12px 0 0; font-size: 10.5px; }
    .hero-phone { margin: -40px 0 -150px; transform: rotate(-3deg); }
  }
  /* 1280 px and up: the phone stands in the gap between the text column and the robin. */
  @media (min-width: 1280px) {
    .phone-slot { position: absolute; inset: 0; pointer-events: none; }
    .phone-slot > .wrap { display: block; position: relative; height: 100%; }
    .hero-phone { position: absolute; left: 548px; top: 190px; margin: 0; pointer-events: auto; animation: hero-rise 1s .2s var(--ease-paper) both; }
  }
  @keyframes hero-rise { from { opacity: 0; translate: 0 30px; } to { opacity: 1; translate: 0 0; } }
</style>
```

- [ ] **Step 11: Kör testerna**

```bash
cd C:/w/birdy-web/website && npm run build && PLAYWRIGHT_CHANNEL=chrome npx playwright test tests/home.spec.ts
```
Förväntat: alla gröna (4 från Task 4 + 9 nya). Fallerar ett geometritest: ändra inte testet. Jämför siffrorna i felmeddelandet med reglerna i HeroScene/Hero (prototypen gav i 1280 px telefon 595–871 och rödhake 904–1263) och rätta CSS:en.

- [ ] **Step 12: Titta på första vyn**

Starta `npm run preview` i en egen terminal och kör:

```bash
cd C:/w/birdy-web/website && node ../.shots.mjs /sv/ 390,1024,1280,1440,1920 && node ../.shots.mjs / 390,1440 && node ../.shots.mjs /sv/ 1440 ../.shots/still --reduce
```
Titta på bilderna och jämför med `startsida-v5.html`: rödhaken syns hel och är skarp, plattan har ingen synlig "spökrödhake" där fågeln klipptes ut, Birdy-fågeln står ovanför kickern, telefonen står fritt från rödhaken, metaraden ligger längst ned i fotot. I `still`-bilden står allt stilla och fågeln syns.

- [ ] **Step 13: Kör webbgaten och committa**

```bash
cd C:/w/birdy-web
git add website/src/styles/phone.css website/src/components/phone website/src/components/hero website/src/components/Hero.astro website/src/content/copy.sv.json website/src/content/copy.en.json website/tests/home.spec.ts
git commit -m "feat(website): första vyn med levande rödhake, Birdy-fågeln och träffskärmen

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Så funkar det och Fältboken

**Files:**
- Create: `website/src/components/HowItWorks.astro`
- Create: `website/src/components/JournalSection.astro`
- Modify: `website/src/components/HomePage.astro` (hela filen)
- Modify: `website/src/content/copy.sv.json`, `website/src/content/copy.en.json` (`howItWorks` och `journal` läggs till, `loop` och `listen` tas bort)
- Delete: `website/src/components/Loop.astro`, `website/src/components/Listen.astro`
- Modify: `website/tests/home.spec.ts`

- [ ] **Step 1: Skriv de fallerande testerna**

Lägg till sist i `website/tests/home.spec.ts`:

```ts
test.describe('så funkar det och fältboken', () => {
  for (const [path, how, journal, free] of [
    ['/sv/', 'Tre sätt att fånga.', 'Varje fynd får en egen sida.', 'Gratis'],
    ['/', 'Three ways to catch it.', 'Every sighting gets its own page.', 'Free'],
  ] as const) {
    test(`sektionerna finns på ${path}`, async ({ page }) => {
      await page.goto(path);
      await expect(page.locator('#how-it-works h2')).toHaveText(how);
      await expect(page.locator('#how-it-works .row')).toHaveCount(3);
      await expect(page.locator('#how-it-works .free')).toHaveText(free);
      await expect(page.locator('#journal h2')).toHaveText(journal);
      await expect(page.locator('#journal .facts dt')).toHaveText(['34', '27', '0']);
      await expect(page.locator('#journal img')).toHaveAttribute('alt', /.+/);
    });
  }
});
```

- [ ] **Step 2: Kör testerna och se dem falla**

```bash
cd C:/w/birdy-web/website && npm run build && PLAYWRIGHT_CHANNEL=chrome npx playwright test tests/home.spec.ts -g "så funkar det"
```
Förväntat: 2 failed (`#how-it-works` finns inte).

- [ ] **Step 3: Texterna**

I `website/src/content/copy.sv.json`: ta bort objekten `"loop"` och `"listen"`, och lägg till följande två objekt direkt efter `"phone"`:

```json
  "howItWorks": {
    "kicker": "Så funkar det",
    "headline": "Tre sätt att *fånga.*",
    "lead": "Samma tre ingångar som i appen. Birdy visar alltid hur säker den är, och det är du som bestämmer vad som sparas.",
    "free": "Gratis",
    "rows": [
      { "title": "Live med kameran", "body": "Rikta mot fågeln. Birdy tittar flera gånger i sekunden och visar vilken art den ser." },
      { "title": "Från ett foto", "body": "Välj en bild ur galleriet, beskär runt fågeln och låt Birdy titta." },
      { "title": "Lyssna på lätet", "body": "Spela in sången så föreslår Birdy arten. Ljud-ID är gratis för alla." }
    ]
  },
  "journal": {
    "kicker": "Fältboken",
    "headline": "Varje fynd får *en egen sida.*",
    "lead": "Spara träffen med datum, en egen anteckning och platsen om du vill. Nya arter får en präglad stämpel, och varje vecka samlas dina fynd i ett eget uppslag.",
    "marginalia": "första för året",
    "plateSpecies": "Ladusvala",
    "plateNote": "14 maj, vid ladan",
    "plateSeal": "Nr 31",
    "plateAlt": "En ladusvala som sitter på en gren",
    "facts": [
      { "value": "34", "label": "märken att samla" },
      { "value": "27", "label": "av dem gratis" },
      { "value": "0", "label": "konton" }
    ]
  },
```

I `website/src/content/copy.en.json`: ta bort `"loop"` och `"listen"`, och lägg till efter `"phone"`:

```json
  "howItWorks": {
    "kicker": "How it works",
    "headline": "Three ways to *catch it.*",
    "lead": "The same three ways in as in the app. Birdy always shows how sure it is, and you decide what gets saved.",
    "free": "Free",
    "rows": [
      { "title": "Live with the camera", "body": "Point at the bird. Birdy looks several times a second and shows the species it sees." },
      { "title": "From a photo", "body": "Pick a picture from your gallery, crop around the bird and let Birdy take a look." },
      { "title": "Listen to the song", "body": "Record the song and Birdy suggests the species. Sound ID is free for everyone." }
    ]
  },
  "journal": {
    "kicker": "The field journal",
    "headline": "Every sighting gets *its own page.*",
    "lead": "Save the match with the date, a note of your own and the place if you like. New species get an embossed stamp, and every week your sightings come together in a spread of their own.",
    "marginalia": "first of the year",
    "plateSpecies": "Barn Swallow",
    "plateNote": "14 May, by the barn",
    "plateSeal": "No. 31",
    "plateAlt": "A barn swallow perched on a branch",
    "facts": [
      { "value": "34", "label": "badges to collect" },
      { "value": "27", "label": "of them free" },
      { "value": "0", "label": "accounts" }
    ]
  },
```

- [ ] **Step 4: Så funkar det**

Create `website/src/components/HowItWorks.astro`:

```astro
---
import Kicker from './ui/Kicker.astro';
import JournalHeadline from './ui/JournalHeadline.astro';
import Icon from './ui/Icon.astro';
import { type Locale, getCopy } from '../lib/i18n';

interface Props { locale: Locale }
const { locale } = Astro.props;
const t = getCopy(locale);
const icons = ['camera', 'photo', 'wave'] as const;
---

<section id="how-it-works" class="sec how">
  <div class="wrap grid">
    <div data-reveal>
      <Kicker text={t.howItWorks.kicker} />
      <JournalHeadline text={t.howItWorks.headline} level="h2" align="left" size="clamp(34px, 4.4vw, 52px)" />
      <p class="lead">{t.howItWorks.lead}</p>
    </div>
    <ul class="rows">
      {t.howItWorks.rows.map((row, i) => (
        <li class="row" data-reveal="slide" style={`--rd:${i * 85}ms`}>
          <span class="ic"><Icon name={icons[i]} size={21} /></span>
          <div>
            <h3>{row.title}{i === 2 && <span class="free">{t.howItWorks.free}</span>}</h3>
            <p>{row.body}</p>
          </div>
        </li>
      ))}
    </ul>
  </div>
</section>

<style>
  .grid { display: grid; grid-template-columns: 1fr 1.05fr; gap: 70px; align-items: start; padding-top: 40px; }
  .row { display: flex; align-items: flex-start; gap: 16px; padding: 20px 0; border-bottom: 1px solid var(--line); }
  .row:first-child { border-top: 1px solid var(--line); }
  .ic { width: 46px; height: 46px; border-radius: 50%; display: grid; place-items: center; color: var(--rust); background: var(--card); box-shadow: inset 0 0 0 1px var(--line); flex: none; }
  h3 { font-size: 22px; line-height: 1.1; }
  .row p { margin: 6px 0 0; color: var(--muted); font-size: 14.5px; line-height: 1.55; }
  /* Below 1280 px the hero phone hangs into this section (see Hero.astro). */
  @media (max-width: 1279px) { .grid { padding-top: 110px; } }
  @media (max-width: 760px) { .grid { grid-template-columns: 1fr; gap: 30px; padding-top: 150px; } }
</style>
```

- [ ] **Step 5: Fältboken**

Create `website/src/components/JournalSection.astro`:

```astro
---
import { Image } from 'astro:assets';
import Kicker from './ui/Kicker.astro';
import JournalHeadline from './ui/JournalHeadline.astro';
import swallow from '../assets/photos/ladusvala-q25429.webp';
import { type Locale, getCopy } from '../lib/i18n';

interface Props { locale: Locale }
const { locale } = Astro.props;
const t = getCopy(locale);
---

<section id="journal" class="sec journal">
  <div class="wrap grid">
    <div data-reveal="scale">
      <figure class="plate">
        <span class="marg" aria-hidden="true">{t.journal.marginalia}</span>
        <Image class="plate-img" src={swallow} alt={t.journal.plateAlt} widths={[440, 880]} sizes="(max-width: 760px) 292px, 412px" loading="lazy" decoding="async" />
        <figcaption class="cap"><b>{t.journal.plateSpecies}</b> · {t.journal.plateNote}</figcaption>
        <span class="seal plate-seal" aria-hidden="true">{t.journal.plateSeal}</span>
      </figure>
    </div>
    <div data-reveal>
      <Kicker text={t.journal.kicker} />
      <JournalHeadline text={t.journal.headline} level="h2" align="left" size="clamp(34px, 4.4vw, 52px)" />
      <p class="lead">{t.journal.lead}</p>
      <dl class="facts">
        {t.journal.facts.map((f) => <div><dt>{f.value}</dt><dd>{f.label}</dd></div>)}
      </dl>
    </div>
  </div>
</section>

<style>
  .journal { background: var(--card); border-top: 1px solid var(--line); border-bottom: 1px solid var(--line); }
  .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 70px; align-items: center; }
  /* The rotation lives on the figure, the reveal on its wrapper, so they never fight over transform. */
  .plate { position: relative; max-width: 440px; margin: 0 auto; background: #fff; padding: 14px 14px 46px; box-shadow: 0 20px 40px rgba(31, 42, 25, .16); transform: rotate(-2.5deg); }
  .plate :global(.plate-img) { display: block; width: 100%; height: auto; aspect-ratio: 4 / 3; object-fit: cover; object-position: center 40%; }
  .cap { margin-top: 12px; font-family: var(--font-script); font-size: 22px; color: var(--muted); }
  .cap b { font-family: var(--font-serif); font-weight: 400; font-style: italic; color: var(--ink); font-size: 21px; }
  .plate-seal { position: absolute; right: -18px; bottom: -18px; width: 86px; height: 86px; font-size: 18px; }
  .marg { position: absolute; left: -10px; top: -34px; font-family: var(--font-script); font-size: 24px; color: var(--rust); transform: rotate(-4deg); }
  .facts { display: flex; margin: 28px 0 0; border-top: 1px solid var(--line); }
  .facts div { flex: 1; padding: 14px 0 0; }
  .facts dt { font-family: var(--font-serif); font-size: 30px; color: var(--rust); line-height: 1; }
  .facts dd { margin: 0; font-size: 12.5px; color: var(--muted); }
  @media (max-width: 760px) {
    .grid { grid-template-columns: 1fr; gap: 40px; }
    .plate { max-width: 320px; }
  }
</style>
```

- [ ] **Step 6: Byt sektionerna på startsidan och ta bort de gamla**

Replace hela `website/src/components/HomePage.astro` med:

```astro
---
import Layout from '../layouts/Layout.astro';
import Nav from './Nav.astro';
import Hero from './Hero.astro';
import HowItWorks from './HowItWorks.astro';
import JournalSection from './JournalSection.astro';
import Glimpse from './Glimpse.astro';
import Inside from './Inside.astro';
import Coverage from './Coverage.astro';
import Premium from './Premium.astro';
import Privacy from './Privacy.astro';
import Faq from './Faq.astro';
import FieldNotesTeaser from './FieldNotesTeaser.astro';
import FinalCta from './FinalCta.astro';
import Footer from './Footer.astro';
import type { Locale } from '../lib/i18n';

interface Props { locale: Locale }
const { locale } = Astro.props;
const pathname = locale === 'sv' ? '/sv/' : '/';
---

<Layout locale={locale} pathname={pathname}>
  <Nav locale={locale} variant="overlay" />
  <main>
    <Hero locale={locale} />
    <HowItWorks locale={locale} />
    <JournalSection locale={locale} />
    <Glimpse locale={locale} />
    <Inside locale={locale} />
    <Coverage locale={locale} />
    <Premium locale={locale} />
    <Privacy locale={locale} />
    <Faq locale={locale} />
    <FieldNotesTeaser locale={locale} />
    <FinalCta locale={locale} />
  </main>
  <Footer locale={locale} />
</Layout>
```

```bash
cd C:/w/birdy-web && git rm -q website/src/components/Loop.astro website/src/components/Listen.astro
```

- [ ] **Step 7: Kör testerna och titta**

```bash
cd C:/w/birdy-web/website && npm run build && PLAYWRIGHT_CHANNEL=chrome npx playwright test tests/home.spec.ts
```
Förväntat: alla gröna. Ta skärmdumpar (`node ../.shots.mjs /sv/ 390,1024,1440 ../.shots --full` med `npm run preview` igång) och kontrollera: telefonen från heron hänger ner över Så funkar det utan att täcka rubriken (390 och 1024 px), planschen lutar och har stämpeln i hörnet.

- [ ] **Step 8: Kör webbgaten och committa**

```bash
cd C:/w/birdy-web
git add website/src/components/HowItWorks.astro website/src/components/JournalSection.astro website/src/components/HomePage.astro website/src/content/copy.sv.json website/src/content/copy.en.json website/tests/home.spec.ts
git commit -m "feat(website): Så funkar det och Fältboken ersätter Loop och Listen

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Appkarusellen med åtta telefoner

**Files:**
- Modify: `website/src/styles/phone.css` (tillägg sist)
- Create: `website/src/components/phone/TabBar.astro`
- Create: `website/src/components/phone/screens/IdentifyScreen.astro`, `ListenScreen.astro`, `JournalScreen.astro`, `SpeciesScreen.astro`, `BadgesScreen.astro`, `FindsMapScreen.astro`, `SeasonScreen.astro`
- Create: `website/src/components/AppTour.astro`, `website/src/components/app-tour.ts`
- Modify: `website/src/components/HomePage.astro` (hela filen)
- Modify: `website/src/content/copy.sv.json`, `website/src/content/copy.en.json` (`tour` läggs till, `phone` utökas, `glimpse` tas bort)
- Delete: `website/src/components/Glimpse.astro`
- Modify: `website/tests/home.spec.ts`

Innehållet i skärmarna följer `startsida-v5.html` (artfakta kontrollerade mot appens artdatabas i brainstormen). Två rättelser mot mockupen enligt specen: Dagens fågel säger "här just nu" (inte "finns nära dig nu") och "Inte fångad än" (appens sträng) i stället för "1 / 3 fångade". Mässingssigill har mörk text.

- [ ] **Step 1: Skriv de fallerande testerna**

Lägg till sist i `website/tests/home.spec.ts`:

```ts
test.describe('appkarusellen', () => {
  test('åtta telefoner och pilarna byter text (SV)', async ({ page }) => {
    const errors = trackConsoleErrors(page);
    await page.goto('/sv/');
    const tour = page.locator('#app');
    await expect(tour.locator('.slide')).toHaveCount(8);
    await expect(tour.locator('.slide .ph[role="img"]')).toHaveCount(8);
    await tour.scrollIntoViewIfNeeded();
    const title = tour.locator('[data-ch]');
    await expect(title).toHaveText('Tre sätt att fånga');
    await tour.locator('[data-next]').click();
    await expect(title).toHaveText('Ärlig om hur säker den är');
    await tour.locator('[data-next]').click();
    await expect(title).toHaveText('Lyssna på lätet');
    await tour.locator('[data-prev]').click();
    await expect(title).toHaveText('Ärlig om hur säker den är');
    expect(errors).toEqual([]);
  });

  test('sista skärmen är märkt Premium (EN)', async ({ page }) => {
    await page.goto('/');
    const tour = page.locator('#app');
    await tour.scrollIntoViewIfNeeded();
    await tour.locator('[data-track]').evaluate((t) => t.scrollTo({ left: t.scrollWidth }));
    await expect(tour.locator('[data-ch]')).toHaveText('A year in the field');
    await expect(tour.locator('[data-cp]')).toBeVisible();
    await expect(tour.locator('[data-cp]')).toHaveText('Premium');
  });

  test('alla telefonbilder är laddade när man når karusellen', async ({ page }) => {
    await page.goto('/sv/');
    await page.locator('#app').scrollIntoViewIfNeeded();
    await expect.poll(() => page.locator('#app img').evaluateAll((imgs) =>
      imgs.every((img) => (img as HTMLImageElement).complete && (img as HTMLImageElement).naturalWidth > 0))).toBe(true);
  });

  test.describe('med minskad rörelse', () => {
    test.use({ contextOptions: { reducedMotion: 'reduce' } });
    test('karusellen och rödhaken står still', async ({ page }) => {
      await page.goto('/sv/');
      await page.locator('#app').scrollIntoViewIfNeeded();
      const transforms = await page.locator('#app .slide').evaluateAll((els) => els.map((e) => getComputedStyle(e).transform));
      expect(transforms.every((t) => t === 'none')).toBe(true);
      const running = await page.evaluate(() => document.getAnimations().filter((a) => a.playState === 'running').length);
      expect(running).toBe(0);
    });
  });
});
```

- [ ] **Step 2: Kör testerna och se dem falla**

```bash
cd C:/w/birdy-web/website && npm run build && PLAYWRIGHT_CHANNEL=chrome npx playwright test tests/home.spec.ts -g "appkarusellen"
```
Förväntat: FAIL (`#app` finns inte).

- [ ] **Step 3: Texterna för karusellen och skärmarna**

I `website/src/content/copy.sv.json`: ta bort objektet `"glimpse"`. Lägg till objektet `"tour"` direkt efter `"journal"`:

```json
  "tour": {
    "kicker": "Appen",
    "headline": "Ett varv i *fältboken.*",
    "lead": "Från första träffen till ditt eget uppslagsverk. Så här ser Birdy ut i telefonen.",
    "label": "Skärmar ur appen",
    "prev": "Föregående skärm",
    "next": "Nästa skärm",
    "premium": "Premium",
    "slides": [
      { "k": "Identifiera", "h": "Tre sätt att fånga", "p": "Kamera, foto eller läte. Dagens fågel visar en art som finns i Sverige just nu.", "premium": false },
      { "k": "Träff", "h": "Ärlig om hur säker den är", "p": "Birdy visar säkerheten tydligt. Är den osäker får du fler förslag att välja mellan.", "premium": false },
      { "k": "Ljud-ID · gratis", "h": "Lyssna på lätet", "p": "Spela in sången så föreslår Birdy arten. Ljudidentifieringen är gratis för alla.", "premium": false },
      { "k": "Fältboken", "h": "Din egen fältbok", "p": "Varje fynd sparas i telefonen. Nya arter får en präglad stämpel.", "premium": false },
      { "k": "Artprofil", "h": "Allt om arten på ett uppslag", "p": "Foton, beskrivning och när arten finns i Sverige. Du ser direkt om du har stämplat den.", "premium": false },
      { "k": "Märken", "h": "Kom hem med ett märke", "p": "34 märken att samla, 27 av dem gratis. Troférummet visar allt du har jagat ihop.", "premium": false },
      { "k": "Fynd-kartan", "h": "Överallt du har varit", "p": "Dina fynd på en egen karta. Platsen sparas bara om du vill, och bara i telefonen.", "premium": true },
      { "k": "Säsong", "h": "Ett år i fält", "p": "Stora siffror och lugna diagram. Se mönstren i dina fynd, månad för månad.", "premium": true }
    ]
  },
```

I objektet `"phone"` i `copy.sv.json`, lägg till följande nycklar efter `"match"` (glöm inte kommat efter `match`-objektet):

```json
    "matchTour": {
      "label": "Birdys träffskärm: domherre, säker match 91 procent",
      "kicker": "Match · Fynd nr 24",
      "species": "Domherre",
      "latin": "Pyrrhula pyrrhula",
      "confidence": "Säker match · 91 %",
      "seal": "Nr 24"
    },
    "identify": {
      "label": "Birdys startskärm Identifiera med dagens fågel, en stjärtmes",
      "dailyBird": "Dagens fågel",
      "species": "Stjärtmes",
      "status": "här just nu.",
      "latin": "Aegithalos caudatus",
      "caught": "Inte fångad än",
      "kicker": "Identifiera",
      "headline": "Tre sätt att *fånga.*",
      "weekNote": "7 fynd den här veckan",
      "startCamera": "Starta kameran",
      "photoTitle": "Från ett foto",
      "photoBody": "Välj en bild i galleriet",
      "listenTitle": "Lyssna på lätet",
      "listenBody": "Spela in sång eller läte"
    },
    "listen": {
      "label": "Birdys ljud-ID som lyssnar och hör en koltrast",
      "kicker": "Lyssnar",
      "headline": "Låt den *sjunga.*",
      "hearing": "Hör: Koltrast",
      "hint1": "Håll telefonen stilla.",
      "hint2": "Birdy stannar själv när den är säker."
    },
    "journal": {
      "label": "Birdys fältbok med 24 arter och veckans uppslag",
      "kicker": "Mina arter",
      "headline": "Din *fältbok.*",
      "stats": [
        { "value": "24", "label": "arter" },
        { "value": "9", "label": "märken" },
        { "value": "5", "label": "dagar i rad" }
      ],
      "weekLabel": "Veckans uppslag",
      "weekSpecies": "Domherre",
      "entries": [
        { "no": "24", "species": "Domherre", "when": "12 jan · Hagaparken" },
        { "no": "23", "species": "Talgoxe", "when": "10 jan · Trädgården" },
        { "no": "22", "species": "Sidensvans", "when": "8 jan · Centrum" },
        { "no": "21", "species": "Stjärtmes", "when": "6 jan · Skogsstigen" }
      ]
    },
    "species": {
      "label": "Birdys artprofil för talgoxe, stämplad med 12 fynd",
      "family": "Mesar · Paridae",
      "name": "Talgoxe",
      "latin": "Parus major",
      "stamped": "Stämplad · 12 fynd",
      "abundance": "Allmän",
      "status": "Livskraftig (LC)",
      "aboutLabel": "Om arten",
      "about": "Talgoxen är en vanlig fågel som förekommer över stora delar av Europa. Den är mycket anpassningsbar och trivs i alla slags skogslandskap, från täta lövskogar till öppnare terränger.",
      "seasonLabel": "Finns i Sverige",
      "months": ["J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"]
    },
    "badges": {
      "label": "Birdys märken med troférummet och tolv stämplar",
      "kicker": "Märken · Troférum",
      "headline": "Kom hem med ett *märke.*",
      "roomLabel": "Ditt troférum",
      "rank": "Fältmedlem",
      "trophies": "3 troféer",
      "unlocked": "upplåsta",
      "remaining": "kvar att jaga"
    },
    "map": {
      "label": "Birdys fynd-karta med fyra fynd som sigill",
      "kicker": "Fynd-kartan",
      "headline": "Överallt du *varit.*"
    },
    "season": {
      "label": "Birdys säsongsstatistik för 2026 med 23 arter och 57 observationer",
      "kicker": "Säsong 2026",
      "headline": "Ett *år* i fält.",
      "species": "arter",
      "observations": "observationer",
      "perMonth": "Observationer per månad",
      "perSeason": "Per säsong",
      "seasons": ["Vinter 7", "Vår 22", "Sommar 17", "Höst 11"],
      "mostSeen": "Mest sedda",
      "top": [
        { "name": "Talgoxe", "count": "12" },
        { "name": "Koltrast", "count": "8" }
      ]
    },
    "premiumBadge": "Premium"
```

I `website/src/content/copy.en.json`: ta bort `"glimpse"`. Lägg till efter `"journal"`:

```json
  "tour": {
    "kicker": "The app",
    "headline": "A tour of *the field journal.*",
    "lead": "From the first match to your own field guide. This is Birdy on your phone.",
    "label": "Screens from the app",
    "prev": "Previous screen",
    "next": "Next screen",
    "premium": "Premium",
    "slides": [
      { "k": "Identify", "h": "Three ways to catch it", "p": "Camera, photo or song. Bird of the day shows a species you can find in Sweden right now.", "premium": false },
      { "k": "Match", "h": "Honest about how sure it is", "p": "Birdy shows its confidence clearly. When it is unsure, you get more suggestions to choose from.", "premium": false },
      { "k": "Sound ID · free", "h": "Listen to the song", "p": "Record the song and Birdy suggests the species. Sound identification is free for everyone.", "premium": false },
      { "k": "Field journal", "h": "Your own field journal", "p": "Every sighting is saved on your phone. New species get an embossed stamp.", "premium": false },
      { "k": "Species profile", "h": "Everything about a species on one spread", "p": "Photos, a description and when the species is in Sweden. You see right away if you have stamped it.", "premium": false },
      { "k": "Badges", "h": "Come home with a badge", "p": "34 badges to collect, 27 of them free. The trophy room shows everything you have tracked down.", "premium": false },
      { "k": "Finds map", "h": "Everywhere you have been", "p": "Your sightings on a map of their own. The location is only saved if you want, and only on your phone.", "premium": true },
      { "k": "Season", "h": "A year in the field", "p": "Big numbers and calm charts. See the patterns in your sightings, month by month.", "premium": true }
    ]
  },
```

I objektet `"phone"` i `copy.en.json`, lägg till efter `"match"`:

```json
    "matchTour": {
      "label": "Birdy's match screen: Eurasian bullfinch, a confident 91 percent match",
      "kicker": "Match · Find no. 24",
      "species": "Eurasian Bullfinch",
      "latin": "Pyrrhula pyrrhula",
      "confidence": "Confident match · 91%",
      "seal": "No. 24"
    },
    "identify": {
      "label": "Birdy's Identify screen with the bird of the day, a long-tailed tit",
      "dailyBird": "Bird of the day",
      "species": "Long-tailed Tit",
      "status": "here right now.",
      "latin": "Aegithalos caudatus",
      "caught": "Not caught yet",
      "kicker": "Identify",
      "headline": "Three ways to *catch it.*",
      "weekNote": "7 sightings this week",
      "startCamera": "Start the camera",
      "photoTitle": "From a photo",
      "photoBody": "Pick a picture from your gallery",
      "listenTitle": "Listen to the song",
      "listenBody": "Record a song or call"
    },
    "listen": {
      "label": "Birdy's sound ID listening and hearing a common blackbird",
      "kicker": "Listening",
      "headline": "Let it *sing.*",
      "hearing": "Hearing: Common Blackbird",
      "hint1": "Hold the phone still.",
      "hint2": "Birdy stops by itself when it is sure."
    },
    "journal": {
      "label": "Birdy's field journal with 24 species and this week's spread",
      "kicker": "My species",
      "headline": "Your *field journal.*",
      "stats": [
        { "value": "24", "label": "species" },
        { "value": "9", "label": "badges" },
        { "value": "5", "label": "day streak" }
      ],
      "weekLabel": "This week's spread",
      "weekSpecies": "Eurasian Bullfinch",
      "entries": [
        { "no": "24", "species": "Eurasian Bullfinch", "when": "12 Jan · Hagaparken" },
        { "no": "23", "species": "Great Tit", "when": "10 Jan · Garden" },
        { "no": "22", "species": "Bohemian Waxwing", "when": "8 Jan · Town centre" },
        { "no": "21", "species": "Long-tailed Tit", "when": "6 Jan · Forest trail" }
      ]
    },
    "species": {
      "label": "Birdy's species profile for the great tit, stamped with 12 sightings",
      "family": "Tits · Paridae",
      "name": "Great Tit",
      "latin": "Parus major",
      "stamped": "Stamped · 12 sightings",
      "abundance": "Common",
      "status": "Least Concern (LC)",
      "aboutLabel": "About the species",
      "about": "The great tit is a small, strikingly patterned passerine with distinctive markings that make it easy to identify. It is highly adaptable and readily visits gardens and parks.",
      "seasonLabel": "In Sweden",
      "months": ["J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"]
    },
    "badges": {
      "label": "Birdy's badges with the trophy room and twelve stamps",
      "kicker": "Badges · Trophy room",
      "headline": "Come home with a *badge.*",
      "roomLabel": "Your trophy room",
      "rank": "Field member",
      "trophies": "3 trophies",
      "unlocked": "unlocked",
      "remaining": "left to find"
    },
    "map": {
      "label": "Birdy's finds map with four sightings shown as seals",
      "kicker": "Finds map",
      "headline": "Everywhere you have *been.*"
    },
    "season": {
      "label": "Birdy's season statistics for 2026 with 23 species and 57 observations",
      "kicker": "Season 2026",
      "headline": "A *year* in the field.",
      "species": "species",
      "observations": "observations",
      "perMonth": "Observations per month",
      "perSeason": "By season",
      "seasons": ["Winter 7", "Spring 22", "Summer 17", "Autumn 11"],
      "mostSeen": "Most seen",
      "top": [
        { "name": "Great Tit", "count": "12" },
        { "name": "Blackbird", "count": "8" }
      ]
    },
    "premiumBadge": "Premium"
```

- [ ] **Step 4: Kontrollera texterna**

```bash
cd C:/w/birdy-web/website && npm run test:i18n && npm run test:no-accuracy && npm run test:no-dashes
```
Förväntat: tre `OK`.

- [ ] **Step 5: Stilar för de nya skärmarna**

Lägg till sist i `website/src/styles/phone.css`:

```css
/* Paper body under the status bar */
.ph-body { position: relative; padding: 14px 16px 0; }
.ph-body--top { padding-top: 36px; }
.ph-body h5 { font-size: 21px; line-height: 1.02; letter-spacing: -.01em; }
.ph-body h5 em { color: var(--rust); }
.ph-note { font-family: var(--font-script); font-size: 14px; color: var(--muted); margin: 2px 0 9px; }
.ph-prim { display: flex; align-items: center; gap: 8px; padding: 10px 12px; border-radius: 12px; color: var(--cream); font-weight: 600; font-size: 10.5px; background: linear-gradient(135deg, var(--rust), var(--rust-deep)); box-shadow: 0 5px 12px rgba(31, 42, 25, .25), inset 0 1px 0 rgba(255, 255, 255, .18); }
.ph-prim span { flex: 1; }
.ph-row { display: flex; align-items: center; gap: 9px; padding: 8px 0; border-bottom: 1px solid var(--line); }
.ph-ic { width: 27px; height: 27px; border-radius: 50%; display: grid; place-items: center; color: var(--rust); background: var(--card); box-shadow: inset 0 0 0 1px var(--line); flex: none; }
.ph-row .t { flex: 1; }
.ph-row b { display: block; font-family: var(--font-serif); font-weight: 400; font-size: 13px; }
.ph-row small { font-size: 8.5px; color: var(--muted); }
.ph-tabs { position: absolute; bottom: 0; left: 0; right: 0; height: 48px; background: var(--paper); border-top: 1px solid var(--line); display: flex; justify-content: space-around; align-items: center; color: #8F8A78; }
.ph-tabs .on { color: var(--rust); }

/* Listen */
.ph-listen { height: 100%; background: linear-gradient(180deg, #24321C 0%, var(--moss) 60%); color: #F2EADC; padding: 40px 18px 0; text-align: center; --kick-color: var(--apricot); }
.ph-listen h5 { font-size: 24px; color: var(--cream); }
.ph-listen h5 em { color: var(--apricot); }
.ph-chip { display: inline-flex; align-items: center; gap: 6px; margin: 16px 0 0; padding: 5px 10px; border-radius: 20px; background: rgba(242, 178, 122, .14); border: 1px solid rgba(242, 178, 122, .35); font-size: 9.5px; color: var(--cream); }
.ph-chip i { width: 6px; height: 6px; border-radius: 50%; background: var(--apricot); animation: ph-pulse 1.4s infinite; }
.ph-wave { display: flex; align-items: center; justify-content: center; gap: 3px; height: 90px; margin: 18px 0; }
.ph-wave span { width: 3px; border-radius: 2px; background: var(--apricot); opacity: .9; animation: ph-wave 1.1s ease-in-out infinite; }
.ph-rec { width: 70px; height: 70px; margin: 6px auto 0; border-radius: 50%; display: grid; place-items: center; background: radial-gradient(circle at 35% 30%, rgba(255, 255, 255, .2), transparent 55%), var(--rust); box-shadow: 0 0 0 8px rgba(154, 69, 38, .22), 0 0 0 16px rgba(154, 69, 38, .1); }
.ph-rec b { width: 18px; height: 18px; border-radius: 4px; background: var(--cream); }
.ph-listen small { display: block; margin-top: 22px; font-size: 9px; color: rgba(242, 234, 220, .6); line-height: 1.5; }
@keyframes ph-wave { 0%, 100% { transform: scaleY(.35); } 50% { transform: scaleY(1); } }
@keyframes ph-pulse { 0%, 100% { opacity: 1; } 50% { opacity: .3; } }

/* My species (journal) */
.ph-nums { display: flex; margin: 10px 0 9px; padding: 7px 0; border-top: 1px solid var(--line); border-bottom: 1px solid var(--line); }
.ph-nums div { text-align: center; flex: 1; }
.ph-nums b { display: block; font-family: var(--font-serif); font-weight: 400; font-size: 21px; color: var(--rust); line-height: 1; }
.ph-nums small { font-size: 6.5px; letter-spacing: .12em; text-transform: uppercase; color: var(--muted); font-weight: 600; }
.ph-week { height: 92px; border-radius: 12px; position: relative; overflow: hidden; }
.ph-week > img { position: absolute; inset: 0; width: 100%; height: 100%; max-width: none; object-fit: cover; object-position: center 35%; }
.ph-week::after { content: ''; position: absolute; inset: 0; background: linear-gradient(0deg, rgba(31, 42, 25, .92), rgba(31, 42, 25, 0) 70%); }
.ph-week div { position: absolute; left: 10px; bottom: 8px; z-index: 2; color: var(--cream); }
.ph-week small { font-size: 6.5px; letter-spacing: .15em; text-transform: uppercase; color: var(--apricot); font-weight: 600; }
.ph-week b { display: block; font-family: var(--font-serif); font-weight: 400; font-size: 15px; }
.ph-jr { display: flex; align-items: center; gap: 8px; padding: 7px 0; border-bottom: 1px solid var(--line); }
.ph-jr .t { flex: 1; }
.ph-jr b { display: block; font-family: var(--font-serif); font-weight: 400; font-size: 12.5px; }
.ph-jr small { color: var(--muted); font-size: 8px; }

/* Species profile */
.ph-pills { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 8px; }
.ph-pill { font-size: 7.5px; padding: 3px 7px; border-radius: 20px; border: 1px solid rgba(255, 255, 255, .3); color: var(--cream); }
.ph-pill--on { background: var(--rust); border-color: var(--rust); }
.ph-lbl { font-size: 7px; letter-spacing: .14em; text-transform: uppercase; color: var(--muted); font-weight: 600; margin: 0 0 5px; }
.ph-desc { font-size: 9.5px; line-height: 1.55; color: var(--ink); margin: 0 0 10px; }
.ph-months { display: flex; gap: 3px; }
.ph-months span { flex: 1; height: 8px; border-radius: 2px; background: var(--rust); opacity: .85; }
.ph-ml { display: flex; gap: 3px; margin-top: 3px; }
.ph-ml span { flex: 1; text-align: center; font-size: 6px; color: var(--muted); }
.ph-thumbs { display: flex; gap: 6px; margin-top: 10px; }
.ph-thumbs span { flex: 1; height: 52px; border-radius: 8px; overflow: hidden; position: relative; }
.ph-thumbs img { position: absolute; inset: 0; width: 100%; height: 100%; max-width: none; object-fit: cover; }

/* Badges and trophy room */
.ph-troph { display: flex; align-items: center; gap: 10px; padding: 11px; border-radius: 14px; color: var(--cream); background: radial-gradient(120% 140% at 0% 0%, rgba(255, 255, 255, .08), transparent 50%), var(--moss); box-shadow: 0 8px 18px rgba(31, 42, 25, .3); margin-top: 10px; }
.ph-troph .seal { width: 40px; height: 40px; font-size: 9px; }
.ph-troph .t { flex: 1; }
.ph-troph small { display: block; font-size: 6.5px; letter-spacing: .15em; text-transform: uppercase; color: var(--apricot); font-weight: 600; }
.ph-troph b { display: block; font-family: var(--font-serif); font-weight: 400; font-size: 16px; }
.ph-troph i { font-family: var(--font-script); font-style: normal; font-size: 12px; color: rgba(255, 248, 238, .75); }
.ph-tstats { display: flex; justify-content: space-between; margin: 11px 2px 9px; font-size: 7.5px; letter-spacing: .12em; text-transform: uppercase; color: var(--muted); font-weight: 600; }
.ph-tstats em { font-family: var(--font-serif); font-style: normal; font-size: 15px; color: var(--rust); letter-spacing: 0; margin-right: 3px; }
.ph-sgrid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 9px; }
.ph-sgrid .seal { width: auto; height: auto; aspect-ratio: 1; font-size: 11px; }

/* Finds map */
.ph-map { position: absolute; inset: 0; background: #EFE6D3; }
.ph-map svg { position: absolute; inset: 0; width: 100%; height: 100%; }
.ph-pin { position: absolute; width: 30px; height: 30px; font-size: 8px; }
.ph-pbadge { display: inline-block; font-size: 7px; letter-spacing: .12em; text-transform: uppercase; font-weight: 600; color: var(--brass-ink); background: linear-gradient(135deg, #E8C98A, var(--brass)); padding: 3px 7px; border-radius: 10px; margin-left: 6px; vertical-align: middle; }

/* Season statistics */
.ph-big { display: flex; gap: 18px; margin: 10px 0; }
.ph-big b { display: block; font-family: var(--font-serif); font-weight: 400; font-size: 28px; color: var(--rust); line-height: 1; }
.ph-big small { font-size: 7px; letter-spacing: .12em; text-transform: uppercase; color: var(--muted); font-weight: 600; }
.ph-pcard { background: var(--card); border-radius: 12px; box-shadow: inset 0 0 0 1px var(--line); padding: 9px 10px; margin-bottom: 8px; }
.ph-bars { display: flex; align-items: flex-end; gap: 4px; height: 62px; }
.ph-bars span { flex: 1; background: #D9C7A8; border-radius: 3px 3px 0 0; }
.ph-bars span.now { background: linear-gradient(180deg, var(--brass-hi), var(--brass)); }
.ph-seas { display: flex; height: 8px; border-radius: 4px; overflow: hidden; }
.ph-sl { display: flex; justify-content: space-between; font-size: 6.5px; color: var(--muted); margin-top: 4px; }
.ph-tp { display: flex; align-items: center; gap: 6px; font-size: 8px; padding: 2px 0; }
.ph-tp em { font-family: var(--font-serif); font-style: normal; font-size: 10.5px; width: 60px; }
.ph-tb { flex: 1; height: 4px; background: #EEE4D2; border-radius: 3px; overflow: hidden; }
.ph-tb span { display: block; height: 100%; background: var(--rust); }
```

- [ ] **Step 6: Flikraden**

Create `website/src/components/phone/TabBar.astro`:

```astro
---
import Icon from '../ui/Icon.astro';

interface Props { active: number }
const { active } = Astro.props;
const tabs = ['tab-identify', 'tab-journal', 'tab-archive', 'tab-badges', 'tab-map'] as const;
---

<div class="ph-tabs">{tabs.map((name, i) => <span class:list={[{ on: i === active }]}><Icon name={name} size={15} /></span>)}</div>
```

- [ ] **Step 7: Identifiera**

Create `website/src/components/phone/screens/IdentifyScreen.astro`:

```astro
---
import { Image } from 'astro:assets';
import Icon from '../../ui/Icon.astro';
import Kicker from '../../ui/Kicker.astro';
import Accent from '../../ui/Accent.astro';
import TabBar from '../TabBar.astro';
import longTailedTit from '../../../assets/photos/stjartmes-q170831.webp';

interface Props {
  copy: {
    dailyBird: string; species: string; status: string; latin: string; caught: string; kicker: string;
    headline: string; weekNote: string; startCamera: string; photoTitle: string; photoBody: string;
    listenTitle: string; listenBody: string;
  };
}
const { copy } = Astro.props;
---

<div class="ph-hero" style="height:208px">
  <Image src={longTailedTit} alt="" widths={[250, 500]} sizes="250px" style="object-position:center 35%" loading="lazy" decoding="async" />
  <div class="ph-hc">
    <Kicker text={copy.dailyBird} />
    <h4>{copy.species}<em>{copy.status}</em></h4>
    <div class="ph-pmeta"><span>{copy.latin}</span><span>{copy.caught}</span></div>
  </div>
</div>
<div class="ph-body">
  <Kicker text={copy.kicker} />
  <h5><Accent text={copy.headline} /></h5>
  <p class="ph-note">{copy.weekNote}</p>
  <div class="ph-prim"><Icon name="camera" size={14} /><span>{copy.startCamera}</span></div>
  <div class="ph-row"><span class="ph-ic"><Icon name="photo" size={13} /></span><span class="t"><b>{copy.photoTitle}</b><small>{copy.photoBody}</small></span></div>
  <div class="ph-row"><span class="ph-ic"><Icon name="wave" size={13} /></span><span class="t"><b>{copy.listenTitle}</b><small>{copy.listenBody}</small></span></div>
</div>
<TabBar active={0} />
```

- [ ] **Step 8: Ljud-ID**

Create `website/src/components/phone/screens/ListenScreen.astro`:

```astro
---
import Kicker from '../../ui/Kicker.astro';
import Accent from '../../ui/Accent.astro';

interface Props { copy: { kicker: string; headline: string; hearing: string; hint1: string; hint2: string } }
const { copy } = Astro.props;
// Same bar heights as the mockup's generated waveform.
const bars = Array.from({ length: 34 }, (_, i) => ({
  height: 18 + Math.round(Math.abs(Math.sin(i * 0.7)) * 62 + (i % 5) * 4),
  delay: (i * 0.045).toFixed(2),
}));
---

<div class="ph-listen">
  <Kicker text={copy.kicker} center />
  <h5><Accent text={copy.headline} /></h5>
  <div class="ph-chip"><i></i>{copy.hearing}</div>
  <div class="ph-wave">{bars.map((b) => <span style={`height:${b.height}px;animation-delay:${b.delay}s`}></span>)}</div>
  <div class="ph-rec"><b></b></div>
  <small>{copy.hint1}<br />{copy.hint2}</small>
</div>
```

- [ ] **Step 9: Mina arter**

Create `website/src/components/phone/screens/JournalScreen.astro`:

```astro
---
import { Image } from 'astro:assets';
import Kicker from '../../ui/Kicker.astro';
import Accent from '../../ui/Accent.astro';
import TabBar from '../TabBar.astro';
import bullfinch from '../../../assets/photos/domherre-q25382.webp';

interface Props {
  copy: {
    kicker: string; headline: string; stats: { value: string; label: string }[];
    weekLabel: string; weekSpecies: string; entries: { no: string; species: string; when: string }[];
  };
}
const { copy } = Astro.props;
---

<div class="ph-body ph-body--top">
  <Kicker text={copy.kicker} />
  <h5><Accent text={copy.headline} /></h5>
  <div class="ph-nums">{copy.stats.map((s) => <div><b>{s.value}</b><small>{s.label}</small></div>)}</div>
  <div class="ph-week">
    <Image src={bullfinch} alt="" widths={[250, 500]} sizes="218px" loading="lazy" decoding="async" />
    <div><small>{copy.weekLabel}</small><b>{copy.weekSpecies}</b></div>
  </div>
  {copy.entries.map((e, i) => (
    <div class="ph-jr">
      <span class:list={['seal', 'seal--mini', { 'seal--brass': i === 2 }]}>{e.no}</span>
      <span class="t"><b>{e.species}</b><small>{e.when}</small></span>
    </div>
  ))}
</div>
<TabBar active={1} />
```

- [ ] **Step 10: Artprofil**

Create `website/src/components/phone/screens/SpeciesScreen.astro`:

```astro
---
import { Image } from 'astro:assets';
import Kicker from '../../ui/Kicker.astro';
import greatTit from '../../../assets/photos/talgoxe-q25485.webp';

interface Props {
  copy: {
    family: string; name: string; latin: string; stamped: string; abundance: string; status: string;
    aboutLabel: string; about: string; seasonLabel: string; months: string[];
  };
}
const { copy } = Astro.props;
const thumbPositions = ['center', '20% 70%', '80% 20%'];
---

<div class="ph-hero" style="height:250px">
  <Image src={greatTit} alt="" widths={[250, 500]} sizes="250px" style="object-position:center 30%" loading="lazy" decoding="async" />
  <div class="ph-hc" style="bottom:30px">
    <Kicker text={copy.family} />
    <h4>{copy.name}</h4>
    <i class="ph-latin">{copy.latin}</i>
    <div class="ph-pills">
      <span class="ph-pill ph-pill--on">{copy.stamped}</span>
      <span class="ph-pill">{copy.abundance}</span>
      <span class="ph-pill">{copy.status}</span>
    </div>
  </div>
</div>
<div class="ph-sheet" style="top:232px">
  <p class="ph-lbl">{copy.aboutLabel}</p>
  <p class="ph-desc">{copy.about}</p>
  <p class="ph-lbl">{copy.seasonLabel}</p>
  <div class="ph-months">{copy.months.map(() => <span></span>)}</div>
  <div class="ph-ml">{copy.months.map((m) => <span>{m}</span>)}</div>
  <div class="ph-thumbs">
    {thumbPositions.map((pos) => <span><Image src={greatTit} alt="" widths={[136]} sizes="68px" style={`object-position:${pos}`} loading="lazy" decoding="async" /></span>)}
  </div>
</div>
```

- [ ] **Step 11: Märken**

Create `website/src/components/phone/screens/BadgesScreen.astro`:

```astro
---
import Kicker from '../../ui/Kicker.astro';
import Accent from '../../ui/Accent.astro';
import TabBar from '../TabBar.astro';

interface Props {
  copy: { kicker: string; headline: string; roomLabel: string; rank: string; trophies: string; unlocked: string; remaining: string };
}
const { copy } = Astro.props;
const seals: { n: string; kind?: 'navy' | 'brass' | 'off' }[] = [
  { n: '1' }, { n: '2' }, { n: '3', kind: 'navy' }, { n: '4' }, { n: '5', kind: 'brass' }, { n: '6' },
  { n: '7', kind: 'off' }, { n: '8', kind: 'off' }, { n: '9', kind: 'off' }, { n: '10', kind: 'off' }, { n: '11', kind: 'off' }, { n: '12', kind: 'off' },
];
---

<div class="ph-body ph-body--top">
  <Kicker text={copy.kicker} />
  <h5><Accent text={copy.headline} /></h5>
  <div class="ph-troph">
    <span class="seal seal--brass">№28</span>
    <span class="t"><small>{copy.roomLabel}</small><b>{copy.rank}</b><i>{copy.trophies}</i></span>
  </div>
  <div class="ph-tstats"><span><em>12</em>{copy.unlocked}</span><span><em>22</em>{copy.remaining}</span></div>
  <div class="ph-sgrid">{seals.map((s) => <span class:list={['seal', s.kind && `seal--${s.kind}`]}>{s.n}</span>)}</div>
</div>
<TabBar active={3} />
```

- [ ] **Step 12: Fynd-kartan**

Create `website/src/components/phone/screens/FindsMapScreen.astro`:

```astro
---
import Accent from '../../ui/Accent.astro';
import TabBar from '../TabBar.astro';

interface Props { copy: { kicker: string; headline: string }; premium: string }
const { copy, premium } = Astro.props;
const pins: { n: string; left: number; top: number; kind?: 'brass' | 'navy' }[] = [
  { n: '24', left: 60, top: 190 },
  { n: '23', left: 150, top: 250 },
  { n: '22', left: 100, top: 330, kind: 'brass' },
  { n: '19', left: 175, top: 150, kind: 'navy' },
];
---

<div class="ph-map">
  <svg viewBox="0 0 250 520" preserveAspectRatio="none">
    <path d="M-10 120 C 60 100, 90 160, 150 140 S 230 90, 270 110" stroke="#CDBF9F" stroke-width="10" fill="none" />
    <path d="M-10 330 C 70 300, 120 360, 180 320 S 240 300, 270 320" stroke="#D6C9AB" stroke-width="16" fill="none" />
    <path d="M60 -10 C 70 120, 40 240, 90 540" stroke="#E2D6BB" stroke-width="3" fill="none" />
    <path d="M170 -10 C 160 140, 200 260, 160 540" stroke="#E2D6BB" stroke-width="3" fill="none" />
    <path d="M0 230 L 250 250" stroke="#E2D6BB" stroke-width="2" fill="none" />
    <ellipse cx="190" cy="420" rx="60" ry="40" fill="#D6CCB3" />
  </svg>
</div>
<div class="ph-body ph-body--top">
  <p class="kick"><span class="kick-line"></span>{copy.kicker}<span class="ph-pbadge">{premium}</span></p>
  <h5><Accent text={copy.headline} /></h5>
</div>
{pins.map((p) => <span class:list={['seal', 'seal--mini', 'ph-pin', p.kind && `seal--${p.kind}`]} style={`left:${p.left}px;top:${p.top}px`}>{p.n}</span>)}
<TabBar active={4} />
```

- [ ] **Step 13: Säsong**

Create `website/src/components/phone/screens/SeasonScreen.astro`:

```astro
---
import Accent from '../../ui/Accent.astro';
import TabBar from '../TabBar.astro';

interface Props {
  copy: {
    kicker: string; headline: string; species: string; observations: string; perMonth: string; perSeason: string;
    seasons: string[]; mostSeen: string; top: { name: string; count: string }[];
  };
  premium: string;
}
const { copy, premium } = Astro.props;
const months = [10, 14, 30, 55, 80, 62, 40, 35, 70, 4, 3, 3]; // percent of the busiest month; September is "now"
const seasonBar: [string, string][] = [['12%', '#8FA0B5'], ['38%', '#6F8A4E'], ['30%', 'var(--brass)'], ['20%', 'var(--rust)']];
const topWidths = ['90%', '60%'];
---

<div class="ph-body ph-body--top">
  <p class="kick"><span class="kick-line"></span>{copy.kicker}<span class="ph-pbadge">{premium}</span></p>
  <h5><Accent text={copy.headline} /></h5>
  <div class="ph-big"><div><b>23</b><small>{copy.species}</small></div><div><b>57</b><small>{copy.observations}</small></div></div>
  <div class="ph-pcard">
    <p class="ph-lbl">{copy.perMonth}</p>
    <div class="ph-bars">{months.map((h, i) => <span class:list={[{ now: i === 8 }]} style={`height:${h}%`}></span>)}</div>
  </div>
  <div class="ph-pcard">
    <p class="ph-lbl">{copy.perSeason}</p>
    <div class="ph-seas">{seasonBar.map(([w, c]) => <span style={`width:${w};background:${c}`}></span>)}</div>
    <div class="ph-sl">{copy.seasons.map((s) => <span>{s}</span>)}</div>
  </div>
  <div class="ph-pcard">
    <p class="ph-lbl">{copy.mostSeen}</p>
    {copy.top.map((row, i) => <div class="ph-tp"><em>{row.name}</em><div class="ph-tb"><span style={`width:${topWidths[i]}`}></span></div>{row.count}</div>)}
  </div>
</div>
<TabBar active={1} />
```

- [ ] **Step 14: Karusellens skript**

Create `website/src/components/app-tour.ts`:

```ts
// App tour carousel (ported from the approved mockup): scroll-snap track with depth (neighbours
// scale, tilt and fade), a caption that follows the centred phone, arrows, arrow keys,
// click-to-centre and mouse drag with momentum. Touch uses native scrolling.
const root = document.querySelector<HTMLElement>('[data-tour]');
const track = root?.querySelector<HTMLElement>('[data-track]');

if (root && track) {
  const slides = [...track.querySelectorAll<HTMLElement>('.slide')];
  const rail = root.querySelector<HTMLElement>('[data-rail]');
  const capInner = root.querySelector<HTMLElement>('[data-cap]');
  const ck = root.querySelector<HTMLElement>('[data-ck]');
  const ch = root.querySelector<HTMLElement>('[data-ch]');
  const cpp = root.querySelector<HTMLElement>('[data-cpp]');
  const cp = root.querySelector<HTMLElement>('[data-cp]');
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  let active = 0;
  let raf = 0;
  let moved = false;

  const centerOf = (s: HTMLElement) => s.offsetLeft + s.offsetWidth / 2;

  const setCaption = (i: number) => {
    const s = slides[i];
    const apply = () => {
      if (ck) ck.textContent = s.dataset.k ?? '';
      if (ch) ch.textContent = s.dataset.h ?? '';
      if (cpp) cpp.textContent = s.dataset.p ?? '';
      if (cp) cp.hidden = !s.dataset.premium;
      capInner?.classList.remove('out');
    };
    if (reduce) { apply(); return; }
    capInner?.classList.add('out');
    setTimeout(apply, 160);
  };

  const frame = () => {
    raf = 0;
    const mid = track.scrollLeft + track.clientWidth / 2;
    const step = slides.length > 1 ? centerOf(slides[1]) - centerOf(slides[0]) : 1;
    let best = 0;
    let bestDist = Infinity;
    slides.forEach((s, i) => {
      const d = (centerOf(s) - mid) / step;
      const a = Math.min(Math.abs(d), 1.4);
      if (!reduce) {
        s.style.transform = `translate3d(0, ${(a * 22).toFixed(2)}px, 0) scale(${(1 - a * 0.13).toFixed(4)}) rotate(${(Math.max(-1.4, Math.min(1.4, d)) * -2.2).toFixed(2)}deg)`;
        s.style.opacity = (1 - Math.min(a, 1) * 0.55).toFixed(3);
      }
      if (Math.abs(d) < bestDist) { bestDist = Math.abs(d); best = i; }
    });
    const progress = track.scrollLeft / Math.max(1, track.scrollWidth - track.clientWidth);
    rail?.style.setProperty('--p', (progress * (slides.length - 1)).toFixed(4));
    if (best !== active) { active = best; setCaption(best); }
  };
  const schedule = () => { if (!raf) raf = requestAnimationFrame(frame); };

  const go = (i: number) => {
    const target = Math.max(0, Math.min(slides.length - 1, i));
    track.scrollTo({ left: centerOf(slides[target]) - track.clientWidth / 2, behavior: reduce ? 'auto' : 'smooth' });
  };

  track.addEventListener('scroll', schedule, { passive: true });
  addEventListener('resize', schedule, { passive: true });
  root.querySelector('[data-prev]')?.addEventListener('click', () => go(active - 1));
  root.querySelector('[data-next]')?.addEventListener('click', () => go(active + 1));
  track.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowRight') { e.preventDefault(); go(active + 1); }
    if (e.key === 'ArrowLeft') { e.preventDefault(); go(active - 1); }
  });
  slides.forEach((s, i) => s.addEventListener('click', () => { if (!moved && i !== active) go(i); }));

  let down = false;
  let startX = 0;
  let startLeft = 0;
  let lastX = 0;
  let lastT = 0;
  let velocity = 0;
  track.addEventListener('pointerdown', (e) => {
    if (e.pointerType !== 'mouse' || e.button !== 0) return;
    down = true; moved = false; startX = lastX = e.clientX; startLeft = track.scrollLeft; lastT = performance.now(); velocity = 0;
    track.setPointerCapture(e.pointerId);
  });
  track.addEventListener('pointermove', (e) => {
    if (!down) return;
    const dx = e.clientX - startX;
    if (!moved && Math.abs(dx) > 4) { moved = true; track.classList.add('dragging'); }
    if (!moved) return;
    track.scrollLeft = startLeft - dx;
    const now = performance.now();
    velocity = (e.clientX - lastX) / Math.max(1, now - lastT);
    lastX = e.clientX; lastT = now;
  });
  const end = () => {
    if (!down) return;
    down = false;
    if (!moved) return;
    track.classList.remove('dragging');
    const mid = track.scrollLeft + track.clientWidth / 2 - velocity * 180;
    let best = 0;
    let bestDist = Infinity;
    slides.forEach((s, i) => { const d = Math.abs(centerOf(s) - mid); if (d < bestDist) { bestDist = d; best = i; } });
    go(best);
    setTimeout(() => { moved = false; }, 50);
  };
  track.addEventListener('pointerup', end);
  track.addEventListener('pointercancel', end);

  // Load every phone's photos together as the section approaches, so nothing pops in while browsing
  // (lazy images clipped by the horizontal scroller would otherwise wait until each is scrolled to).
  const lazyImages = [...track.querySelectorAll<HTMLImageElement>('img[loading="lazy"]')];
  if ('IntersectionObserver' in window) {
    const preload = new IntersectionObserver((entries) => {
      if (!entries.some((e) => e.isIntersecting)) return;
      preload.disconnect();
      for (const img of lazyImages) img.loading = 'eager';
    }, { rootMargin: '800px 0px' });
    preload.observe(root);
  }

  frame();
}

export {};
```

- [ ] **Step 15: Karusellsektionen**

Create `website/src/components/AppTour.astro`:

```astro
---
import Kicker from './ui/Kicker.astro';
import JournalHeadline from './ui/JournalHeadline.astro';
import Icon from './ui/Icon.astro';
import PhoneFrame from './phone/PhoneFrame.astro';
import IdentifyScreen from './phone/screens/IdentifyScreen.astro';
import MatchScreen from './phone/screens/MatchScreen.astro';
import ListenScreen from './phone/screens/ListenScreen.astro';
import JournalScreen from './phone/screens/JournalScreen.astro';
import SpeciesScreen from './phone/screens/SpeciesScreen.astro';
import BadgesScreen from './phone/screens/BadgesScreen.astro';
import FindsMapScreen from './phone/screens/FindsMapScreen.astro';
import SeasonScreen from './phone/screens/SeasonScreen.astro';
import bullfinch from '../assets/photos/domherre-q25382.webp';
import { type Locale, getCopy } from '../lib/i18n';

interface Props { locale: Locale }
const { locale } = Astro.props;
const t = getCopy(locale);
const p = t.phone;
const slides = t.tour.slides;
const slideData = (i: number) => ({
  'data-k': slides[i].k,
  'data-h': slides[i].h,
  'data-p': slides[i].p,
  'data-premium': slides[i].premium ? '1' : undefined,
});
const first = slides[0];
---

<section id="app" class="tour" data-tour data-loop>
  <div class="tour-head" data-reveal>
    <Kicker text={t.tour.kicker} center />
    <JournalHeadline text={t.tour.headline} level="h2" size="clamp(34px, 4.4vw, 52px)" />
    <p>{t.tour.lead}</p>
  </div>
  <ul class="track" tabindex="0" aria-label={t.tour.label} data-track>
    <li class="slide" {...slideData(0)}><PhoneFrame label={p.identify.label}><IdentifyScreen copy={p.identify} /></PhoneFrame></li>
    <li class="slide" {...slideData(1)}><PhoneFrame label={p.matchTour.label}><MatchScreen photo={bullfinch} photoPosition="40% 35%" copy={{ ...p.match, ...p.matchTour }} confidence={91} /></PhoneFrame></li>
    <li class="slide" {...slideData(2)}><PhoneFrame label={p.listen.label}><ListenScreen copy={p.listen} /></PhoneFrame></li>
    <li class="slide" {...slideData(3)}><PhoneFrame label={p.journal.label} darkStatus><JournalScreen copy={p.journal} /></PhoneFrame></li>
    <li class="slide" {...slideData(4)}><PhoneFrame label={p.species.label}><SpeciesScreen copy={p.species} /></PhoneFrame></li>
    <li class="slide" {...slideData(5)}><PhoneFrame label={p.badges.label} darkStatus><BadgesScreen copy={p.badges} /></PhoneFrame></li>
    <li class="slide" {...slideData(6)}><PhoneFrame label={p.map.label} darkStatus><FindsMapScreen copy={p.map} premium={p.premiumBadge} /></PhoneFrame></li>
    <li class="slide" {...slideData(7)}><PhoneFrame label={p.season.label} darkStatus><SeasonScreen copy={p.season} premium={p.premiumBadge} /></PhoneFrame></li>
  </ul>
  <div class="foot">
    <button class="arrow" type="button" data-prev aria-label={t.tour.prev}><Icon name="arrow-left" /></button>
    <div class="cap" aria-live="polite">
      <div class="cap-inner" data-cap>
        <p class="kick kick--center"><span class="kick-line" aria-hidden="true"></span><span data-ck>{first.k}</span><span class="pr2" data-cp hidden={!first.premium}>{t.tour.premium}</span><span class="kick-line" aria-hidden="true"></span></p>
        <h3 data-ch>{first.h}</h3>
        <p class="cap-text" data-cpp>{first.p}</p>
      </div>
    </div>
    <button class="arrow" type="button" data-next aria-label={t.tour.next}><Icon name="arrow-right" /></button>
  </div>
  <div class="rail" style="--n:8;--p:0" data-rail aria-hidden="true"><span></span></div>
</section>

<script>
  import './app-tour';
</script>

<style>
  .tour { position: relative; overflow: hidden; color: #F2EADC; background: radial-gradient(90% 70% at 50% 45%, var(--moss-2) 0%, var(--moss) 55%, var(--moss-deep) 100%); padding: 96px 0 70px; --kick-color: var(--apricot); --jh-ink: var(--cream); --jh-accent: var(--apricot); }
  .tour::before { content: ''; position: absolute; inset: 0; background-image: radial-gradient(rgba(242, 234, 220, .05) 1px, transparent 1.2px); background-size: 18px 18px; pointer-events: none; }
  .tour :global(:focus-visible) { outline-color: var(--apricot); }
  .tour-head { position: relative; text-align: center; padding: 0 24px; }
  .tour-head p { margin: 14px auto 0; max-width: 480px; font-size: 15px; line-height: 1.6; color: rgba(242, 234, 220, .72); }
  .track { --w: 250px; --gap: 34px; position: relative; display: flex; gap: var(--gap); overflow-x: auto; overflow-y: hidden; scroll-snap-type: x mandatory; margin: 0; padding: 40px calc(50% - var(--w) / 2) 34px; scrollbar-width: none; cursor: grab; overscroll-behavior-x: contain; }
  .track::-webkit-scrollbar { display: none; }
  .track:global(.dragging) { cursor: grabbing; scroll-snap-type: none; }
  .slide { flex: 0 0 var(--w); scroll-snap-align: center; will-change: transform, opacity; transform-origin: 50% 60%; }
  .foot { position: relative; display: grid; grid-template-columns: 48px 1fr 48px; align-items: center; gap: 18px; max-width: 640px; margin: 0 auto; padding: 0 24px; }
  .arrow { width: 48px; height: 48px; border-radius: 50%; border: 1px solid rgba(242, 234, 220, .28); background: transparent; color: #F2EADC; display: grid; place-items: center; cursor: pointer; transition: background .25s, border-color .25s, transform .25s; }
  .arrow:hover { background: rgba(242, 234, 220, .1); border-color: var(--apricot); }
  .arrow:active { transform: scale(.94); }
  .cap { text-align: center; min-height: 92px; }
  .cap-inner { transition: opacity .35s var(--ease-paper), transform .35s var(--ease-paper); }
  .cap-inner:global(.out) { opacity: 0; transform: translateY(6px); }
  .cap .kick { margin-bottom: 6px; }
  .pr2 { color: var(--brass-ink); background: var(--brass-hi); padding: 2px 7px; border-radius: 10px; letter-spacing: .1em; font-size: 8.5px; margin-left: 4px; }
  .cap h3 { font-size: 26px; color: var(--cream); }
  .cap-text { margin: 6px 0 0; font-size: 14px; color: rgba(242, 234, 220, .72); line-height: 1.5; }
  .rail { position: relative; width: 200px; height: 2px; margin: 20px auto 0; background: rgba(242, 234, 220, .15); border-radius: 2px; overflow: hidden; }
  .rail span { position: absolute; top: 0; bottom: 0; width: calc(100% / var(--n)); background: var(--apricot); border-radius: 2px; transform: translateX(calc(var(--p) * 100%)); }
  @media (max-width: 760px) { .track { --gap: 22px; } }
</style>
```

- [ ] **Step 16: Byt Glimpse mot karusellen och ta bort den**

Replace hela `website/src/components/HomePage.astro` med:

```astro
---
import Layout from '../layouts/Layout.astro';
import Nav from './Nav.astro';
import Hero from './Hero.astro';
import HowItWorks from './HowItWorks.astro';
import JournalSection from './JournalSection.astro';
import AppTour from './AppTour.astro';
import Inside from './Inside.astro';
import Coverage from './Coverage.astro';
import Premium from './Premium.astro';
import Privacy from './Privacy.astro';
import Faq from './Faq.astro';
import FieldNotesTeaser from './FieldNotesTeaser.astro';
import FinalCta from './FinalCta.astro';
import Footer from './Footer.astro';
import type { Locale } from '../lib/i18n';

interface Props { locale: Locale }
const { locale } = Astro.props;
const pathname = locale === 'sv' ? '/sv/' : '/';
---

<Layout locale={locale} pathname={pathname}>
  <Nav locale={locale} variant="overlay" />
  <main>
    <Hero locale={locale} />
    <HowItWorks locale={locale} />
    <JournalSection locale={locale} />
    <AppTour locale={locale} />
    <Inside locale={locale} />
    <Coverage locale={locale} />
    <Premium locale={locale} />
    <Privacy locale={locale} />
    <Faq locale={locale} />
    <FieldNotesTeaser locale={locale} />
    <FinalCta locale={locale} />
  </main>
  <Footer locale={locale} />
</Layout>
```

```bash
cd C:/w/birdy-web && git rm -q website/src/components/Glimpse.astro
```

- [ ] **Step 17: Kör testerna och titta på alla åtta skärmar**

```bash
cd C:/w/birdy-web/website && npm run build && PLAYWRIGHT_CHANNEL=chrome npx playwright test tests/home.spec.ts
```
Förväntat: alla gröna. Ta skärmdumpar av karusellen på SV och EN och bläddra igenom alla åtta skärmar (klicka på nästa-pilen i ett eget Playwright-skript eller kör `node ../.shots.mjs /sv/ 1440 ../.shots --full` och titta på sektionen). Jämför med `startsida-v5.html`: inga texter klipps eller bryts fult i telefonerna på engelska (särskilt Artprofil och Säsong), mässingssigillen har mörk text.

- [ ] **Step 18: Kör webbgaten och committa**

```bash
cd C:/w/birdy-web
git add website/src/styles/phone.css website/src/components/phone website/src/components/AppTour.astro website/src/components/app-tour.ts website/src/components/HomePage.astro website/src/content/copy.sv.json website/src/content/copy.en.json website/tests/home.spec.ts
git commit -m "feat(website): appkarusellen med åtta telefoner byggda i kod ersätter Play-korten

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: Uppslagsverket och kartan

**Files:**
- Create: `website/tools/render-coverage-fallback.mjs`
- Modify: `website/package.json` (skriptet `assets:coverage`)
- Modify: `website/public/coverage/coverage-fallback.webp` (genereras om, 1600×700)
- Create: `website/src/components/CoverageMap.astro` (kartlogiken från `Coverage.astro`, omfärgad)
- Create: `website/src/components/Guide.astro`
- Modify: `website/src/components/HomePage.astro` (hela filen)
- Modify: `website/src/content/copy.sv.json`, `website/src/content/copy.en.json` (`guide` läggs till, `inside` och `coverage` tas bort)
- Delete: `website/src/components/Inside.astro`, `website/src/components/Coverage.astro`
- Modify: `website/tests/coverage.spec.ts` (hela filen)

- [ ] **Step 1: Skriv om kartans test så att det faller**

Replace hela `website/tests/coverage.spec.ts` med:

```ts
import { test, expect } from '@playwright/test';
import { trackConsoleErrors } from './test-helpers';

for (const { path, headline, stat } of [
  { path: '/', headline: 'pocket', stat: 'species' },
  { path: '/sv/', headline: 'fickan', stat: 'arter' },
]) {
  test(`guide section with coverage map renders on ${path}`, async ({ page }) => {
    const consoleErrors = trackConsoleErrors(page);

    await page.goto(path);
    const section = page.locator('section#guide');
    await expect(section).toBeAttached();
    await expect(section.locator('h2')).toContainText(headline);
    await expect(section.locator('.stats li')).toHaveCount(3);
    await expect(section.locator('.stat-label').first()).toHaveText(stat);
    await expect(section.locator('[data-coverage-map]')).toBeAttached();

    // The live map is lazy AND key-gated: it only initialises when PUBLIC_MAPTILER_KEY is baked
    // into the build (production). CI and local builds have no key and show the static fallback.
    await section.locator('[data-coverage-map]').scrollIntoViewIfNeeded();
    await page.waitForTimeout(3000);

    const canvas = section.locator('canvas.maplibregl-canvas');
    if ((await canvas.count()) > 0) {
      await expect(canvas.first()).toBeVisible();
    } else {
      await expect(section.locator('img.fallback')).toBeVisible();
    }

    expect(consoleErrors, `Console errors: ${consoleErrors.join('\n')}`).toEqual([]);
  });
}
```

```bash
cd C:/w/birdy-web/website && npm run build && PLAYWRIGHT_CHANNEL=chrome npx playwright test tests/coverage.spec.ts
```
Förväntat: 2 failed (`section#guide` finns inte).

- [ ] **Step 2: Reservbilden ritas ur GeoJSON-filen**

Create `website/tools/render-coverage-fallback.mjs`:

```js
// Renders public/coverage/coverage-fallback.webp from coverage-europe.geojson in the field journal
// colours (spec §5.6). Shown before the live map loads, without JavaScript, and wherever the map key
// is missing (CI, previews). No map service is needed. Run: npm run assets:coverage
import sharp from 'sharp';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const gj = JSON.parse(readFileSync(resolve(root, 'public/coverage/coverage-europe.geojson'), 'utf8'));

const W = 1600;
const H = 700;
const PAD = 24;
const WATER = '#D9CCAF';
const FILL = '#9A4526';
const STROKE = '#72301A';
const PAPER = '#F1E8D6';
const BOUNDS = { west: -25, east: 42, south: 34, north: 71.5 };

// Web Mercator, fitted to BOUNDS and centred in the canvas.
const mx = (lon) => (lon * Math.PI) / 180;
const my = (lat) => Math.log(Math.tan(Math.PI / 4 + (lat * Math.PI) / 360));
const x0 = mx(BOUNDS.west);
const x1 = mx(BOUNDS.east);
const y0 = my(BOUNDS.north);
const y1 = my(BOUNDS.south);
const scale = Math.min((W - 2 * PAD) / (x1 - x0), (H - 2 * PAD) / (y0 - y1));
const offX = (W - scale * (x1 - x0)) / 2;
const offY = (H - scale * (y0 - y1)) / 2;
const project = ([lon, lat]) => [offX + (mx(lon) - x0) * scale, offY + (y0 - my(lat)) * scale];

const ringPath = (ring) =>
  ring.map((pt, i) => { const [x, y] = project(pt); return `${i ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`; }).join('') + 'Z';
const polygons = gj.features.flatMap((f) => (f.geometry.type === 'Polygon' ? [f.geometry.coordinates] : f.geometry.coordinates));
const d = polygons.map((poly) => poly.map(ringPath).join('')).join('');

// Decorative sample pins, the same five cities as the live map (not real sightings).
const pins = [[18.07, 59.33], [13.4, 52.52], [2.35, 48.85], [12.49, 41.9], [-3.7, 40.42]].map(project);

const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
  <rect width="${W}" height="${H}" fill="${WATER}"/>
  <path d="${d}" fill="${FILL}" fill-opacity="0.55" fill-rule="evenodd" stroke="${STROKE}" stroke-width="1.2" stroke-linejoin="round"/>
  ${pins.map(([x, y]) => `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="11" fill="${PAPER}" stroke="${FILL}" stroke-width="4"/>`).join('')}
</svg>`;

await sharp(Buffer.from(svg)).webp({ quality: 82 }).toFile(resolve(root, 'public/coverage/coverage-fallback.webp'));
console.log(`coverage-fallback.webp ${W}x${H}`);
```

I `website/package.json`, lägg till i `"scripts"` direkt efter `"assets:og"`:

```json
    "assets:coverage": "node tools/render-coverage-fallback.mjs",
```

```bash
cd C:/w/birdy-web/website && npm run assets:coverage
```
Förväntat: `coverage-fallback.webp 1600x700`. Titta på bilden (Read-verktyget): Europas länder i rost på en ljus beige botten med fem små cirklar (Stockholm, Berlin, Paris, Rom, Madrid).

- [ ] **Step 3: Texterna**

I `website/src/content/copy.sv.json`: ta bort objekten `"inside"` och `"coverage"`, och lägg till efter `"tour"`:

```json
  "guide": {
    "kicker": "Uppslagsverket",
    "headline": "Europas fåglar, *i fickan.*",
    "lead": "839 arter med foton, beskrivningar och utbredning. Allt ligger i telefonen, så guiden fungerar även där täckningen tar slut.",
    "stats": [
      { "value": "839", "label": "arter" },
      { "value": "34", "label": "märken" },
      { "value": "0", "label": "konton" }
    ],
    "mapCaption": "Birdy känner igen fåglar i hela Europa, från trädgårdens stammisar till sällsynta gäster.",
    "fallbackAlt": "Karta över Europa där länderna som Birdy täcker är ifyllda i rost",
    "attribution": "© MapTiler © OpenStreetMap contributors"
  },
```

I `website/src/content/copy.en.json`: ta bort `"inside"` och `"coverage"`, och lägg till efter `"tour"`:

```json
  "guide": {
    "kicker": "The field guide",
    "headline": "Europe's birds, *in your pocket.*",
    "lead": "839 species with photos, descriptions and range. Everything lives on your phone, so the guide keeps working where the signal ends.",
    "stats": [
      { "value": "839", "label": "species" },
      { "value": "34", "label": "badges" },
      { "value": "0", "label": "accounts" }
    ],
    "mapCaption": "Birdy recognises birds across Europe, from garden regulars to rare visitors.",
    "fallbackAlt": "Map of Europe with the countries Birdy covers filled in rust",
    "attribution": "© MapTiler © OpenStreetMap contributors"
  },
```

- [ ] **Step 4: Kartkomponenten**

Create `website/src/components/CoverageMap.astro`:

```astro
---
import { type Locale, getCopy } from '../lib/i18n';

interface Props { locale: Locale }
const t = getCopy(Astro.props.locale);
---

<figure class="mapbox" data-reveal>
  <div class="map" data-coverage-map role="img" aria-label={t.guide.fallbackAlt}>
    <img class="fallback" src="/coverage/coverage-fallback.webp" alt="" width="1600" height="700" loading="lazy" decoding="async" />
    <span class="attrib">{t.guide.attribution}</span>
  </div>
</figure>

<style>
  .mapbox { margin: 44px 0 0; }
  .map { position: relative; aspect-ratio: 16 / 7; border-radius: 16px; overflow: hidden; box-shadow: 0 0 0 1px var(--line), 0 20px 44px rgba(31, 42, 25, .12); background: #EFE6D3; cursor: grab; }
  .map:active { cursor: grabbing; }
  .fallback { position: absolute; inset: 0; width: 100%; height: 100%; max-width: none; object-fit: cover; display: block; }
  .attrib { position: absolute; left: 10px; bottom: 8px; z-index: 3; font-size: 10px; color: var(--muted); background: rgba(255, 250, 241, .85); padding: 2px 6px; border-radius: 6px; }
  /* MapLibre injects its canvas into .map; let it cover the fallback. */
  .map :global(.maplibregl-canvas) { position: absolute; inset: 0; }
  @media (max-width: 760px) { .map { aspect-ratio: 4 / 3; } }
</style>

<script>
  import 'maplibre-gl/dist/maplibre-gl.css';

  const KEY = import.meta.env.PUBLIC_MAPTILER_KEY as string | undefined;
  // Field journal colours (spec §5.6): paper, ink and rust, with the pins as rust seals and a moss bird.
  const PAPER = '#F1E8D6';
  const WATER = '#D9CCAF';
  const INK = '#26301F';
  const RUST = '#9A4526';
  const RUST_DEEP = '#72301A';
  const MOSS = '#1F2A19';
  const BOUNDS: [[number, number], [number, number]] = [[-25, 34], [42, 71.5]];

  const container = document.querySelector<HTMLElement>('[data-coverage-map]');
  if (container && KEY) {
    const io = new IntersectionObserver((entries, obs) => {
      if (entries.some((e) => e.isIntersecting)) { obs.disconnect(); initMap(container, KEY); }
    }, { rootMargin: '200px' });
    io.observe(container);
  }

  async function initMap(el: HTMLElement, key: string) {
    const maplibregl = (await import('maplibre-gl')).default;
    // Localise MapLibre's cooperative-gesture hint on /sv/.
    const localeOverride = document.documentElement.lang === 'sv' ? {
      'CooperativeGesturesHandler.WindowsHelpText': 'Använd Ctrl + scroll för att zooma kartan',
      'CooperativeGesturesHandler.MacHelpText': 'Använd ⌘ + scroll för att zooma kartan',
      'CooperativeGesturesHandler.MobileHelpText': 'Använd två fingrar för att flytta kartan',
    } : undefined;
    const map = new maplibregl.Map({
      container: el,
      attributionControl: false,
      bounds: BOUNDS,
      fitBoundsOptions: { padding: 12 },
      minZoom: 1.1,
      maxZoom: 8,
      dragRotate: false,
      pitchWithRotate: false,
      touchPitch: false,
      cooperativeGestures: true,
      locale: localeOverride,
      style: `https://api.maptiler.com/maps/dataviz-light/style.json?key=${key}`,
    });
    map.touchZoomRotate.disableRotation();

    // The box gets its height from aspect-ratio after layout; keep the canvas in sync.
    const fit = () => { try { map.resize(); } catch { /* map not ready yet */ } };
    requestAnimationFrame(fit);
    new ResizeObserver(fit).observe(el);

    map.on('load', () => {
      fit();
      recolour(map);

      let firstSymbol: string | undefined;
      for (const l of map.getStyle().layers) { if (l.type === 'symbol') { firstSymbol = l.id; break; } }

      fetch('/coverage/coverage-europe.geojson')
        .then((r) => r.json())
        .then((gj) => {
          map.addSource('coverage', { type: 'geojson', data: gj });
          map.addLayer({ id: 'coverage-fill', type: 'fill', source: 'coverage', paint: { 'fill-color': RUST, 'fill-opacity': 0.42 } }, firstSymbol);
          map.addLayer({ id: 'coverage-line', type: 'line', source: 'coverage', paint: { 'line-color': RUST_DEEP, 'line-width': 1, 'line-opacity': 0.9 } }, firstSymbol);
        })
        .catch((err) => console.warn('coverage fill failed to load', err));

      const bird = new Image();
      bird.onload = () => {
        const data = buildSeal(bird);
        if (data && !map.hasImage('seal')) map.addImage('seal', data, { pixelRatio: 2 });
        // Decorative sample pins (NOT real user finds): Stockholm, Berlin, Paris, Rome, Madrid.
        const pinCoords = [[18.07, 59.33], [13.4, 52.52], [2.35, 48.85], [12.49, 41.9], [-3.7, 40.42]];
        map.addSource('finds', { type: 'geojson', data: {
          type: 'FeatureCollection',
          features: pinCoords.map((c) => ({ type: 'Feature', properties: {}, geometry: { type: 'Point', coordinates: c } })),
        } });
        map.addLayer({ id: 'finds', type: 'symbol', source: 'finds', layout: { 'icon-image': 'seal', 'icon-anchor': 'bottom', 'icon-allow-overlap': true, 'icon-size': 0.55 } });
      };
      bird.src = '/coverage/seal-bird.png';
    });
  }

  function buildSeal(birdImg: HTMLImageElement) {
    const S = 2, R = 40, ring = 6, pt = 18, pad = 6;
    const w = 2 * (R + pad), h = 2 * (R + pad) + pt;
    const cvs = document.createElement('canvas');
    cvs.width = w * S; cvs.height = h * S;
    const ctx = cvs.getContext('2d');
    if (!ctx) return null;
    ctx.scale(S, S);
    const cx = w / 2, cy = R + pad;
    ctx.fillStyle = RUST;
    ctx.beginPath(); ctx.moveTo(cx - 7, cy + R - 2); ctx.lineTo(cx + 7, cy + R - 2); ctx.lineTo(cx, cy + R + pt); ctx.closePath(); ctx.fill();
    const grad = ctx.createRadialGradient(cx - R * 0.25, cy - R * 0.3, R * 0.2, cx, cy, R * 1.25);
    grad.addColorStop(0, '#FFF8EE'); grad.addColorStop(1, '#EFE6D3');
    ctx.fillStyle = grad; ctx.beginPath(); ctx.arc(cx, cy, R, 0, 7); ctx.fill();
    ctx.lineWidth = ring; ctx.strokeStyle = RUST; ctx.beginPath(); ctx.arc(cx, cy, R - ring / 2, 0, 7); ctx.stroke();
    const tint = document.createElement('canvas'); tint.width = birdImg.width; tint.height = birdImg.height;
    const tintCtx = tint.getContext('2d');
    if (tintCtx) {
      tintCtx.drawImage(birdImg, 0, 0);
      tintCtx.globalCompositeOperation = 'source-in';
      tintCtx.fillStyle = MOSS; tintCtx.fillRect(0, 0, tint.width, tint.height);
      const bw = R * 1.25, bh = bw * (birdImg.height / birdImg.width);
      ctx.drawImage(tint, cx - bw / 2, cy - bh / 2, bw, bh);
    }
    return ctx.getImageData(0, 0, cvs.width, cvs.height);
  }

  function recolour(map: { getStyle: () => { layers: { id: string; type: string }[] }; setPaintProperty: (id: string, prop: string, value: unknown) => void }) {
    for (const l of map.getStyle().layers) {
      try {
        if (l.type === 'background') map.setPaintProperty(l.id, 'background-color', PAPER);
        else if (l.type === 'fill') {
          if (/water|sea|ocean|lake|river|bath/i.test(l.id)) map.setPaintProperty(l.id, 'fill-color', WATER);
          else { map.setPaintProperty(l.id, 'fill-color', PAPER); map.setPaintProperty(l.id, 'fill-opacity', 1); }
        } else if (l.type === 'line') map.setPaintProperty(l.id, 'line-color', INK);
        else if (l.type === 'symbol') {
          map.setPaintProperty(l.id, 'text-color', INK);
          map.setPaintProperty(l.id, 'text-halo-color', PAPER);
          map.setPaintProperty(l.id, 'text-halo-width', 1.4);
        }
      } catch { /* layer without that paint property */ }
    }
  }
</script>
```

- [ ] **Step 5: Uppslagsverket**

Create `website/src/components/Guide.astro`:

```astro
---
import Kicker from './ui/Kicker.astro';
import JournalHeadline from './ui/JournalHeadline.astro';
import CoverageMap from './CoverageMap.astro';
import { type Locale, getCopy } from '../lib/i18n';

interface Props { locale: Locale }
const { locale } = Astro.props;
const t = getCopy(locale);
---

<section id="guide" class="sec guide">
  <div class="wrap">
    <div class="head" data-reveal>
      <div>
        <Kicker text={t.guide.kicker} />
        <JournalHeadline text={t.guide.headline} level="h2" align="left" size="clamp(34px, 4.4vw, 52px)" />
      </div>
      <p class="lead">{t.guide.lead}</p>
    </div>
    <ul class="stats">
      {t.guide.stats.map((s, i) => (
        <li data-stat data-reveal style={`--rd:${i * 120}ms`}>
          <span class="sr-only">{s.value} {s.label}</span>
          <b data-count={s.value} aria-hidden="true">{s.value}</b>
          <span class="stat-label" aria-hidden="true">{s.label}</span>
        </li>
      ))}
    </ul>
    <CoverageMap locale={locale} />
    <p class="mapcap">{t.guide.mapCaption}</p>
  </div>
</section>

<script>
  // Count the stats up when they come into view (same behaviour as the old Inside section).
  const stats = document.querySelectorAll<HTMLElement>('[data-stat]');
  if (stats.length && 'IntersectionObserver' in window && !matchMedia('(prefers-reduced-motion: reduce)').matches) {
    const counter = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        counter.unobserve(entry.target);
        const value = entry.target.querySelector<HTMLElement>('[data-count]');
        const target = Number(value?.dataset.count);
        if (!value || !Number.isFinite(target) || target === 0) continue;
        const start = performance.now();
        const tick = (now: number) => {
          const progress = Math.min(1, (now - start) / 1100);
          value.textContent = String(Math.round(target * (1 - Math.pow(1 - progress, 3))));
          if (progress < 1) requestAnimationFrame(tick);
        };
        requestAnimationFrame(tick);
      }
    }, { threshold: 0.45 });
    stats.forEach((s) => counter.observe(s));
  }
</script>

<style>
  .head { display: grid; grid-template-columns: 1fr 1fr; gap: 70px; align-items: end; }
  .stats { display: flex; margin: 44px 0 0; border-top: 1px solid var(--line); border-bottom: 1px solid var(--line); }
  .stats li { flex: 1; padding: 18px 0; text-align: center; }
  .stats li + li { border-left: 1px solid var(--line); }
  .stats b { display: block; font-family: var(--font-serif); font-weight: 400; font-size: 52px; color: var(--rust); line-height: 1; }
  .stat-label { font-size: 12px; letter-spacing: .14em; text-transform: uppercase; color: var(--muted); font-weight: 600; }
  .mapcap { text-align: center; margin: 14px 0 0; font-size: 13px; color: var(--muted); }
  @media (max-width: 760px) {
    .head { grid-template-columns: 1fr; gap: 24px; }
    .stats b { font-size: 38px; }
  }
</style>
```

- [ ] **Step 6: Byt Inside och Coverage mot Uppslagsverket**

Replace hela `website/src/components/HomePage.astro` med:

```astro
---
import Layout from '../layouts/Layout.astro';
import Nav from './Nav.astro';
import Hero from './Hero.astro';
import HowItWorks from './HowItWorks.astro';
import JournalSection from './JournalSection.astro';
import AppTour from './AppTour.astro';
import Guide from './Guide.astro';
import Premium from './Premium.astro';
import Privacy from './Privacy.astro';
import Faq from './Faq.astro';
import FieldNotesTeaser from './FieldNotesTeaser.astro';
import FinalCta from './FinalCta.astro';
import Footer from './Footer.astro';
import type { Locale } from '../lib/i18n';

interface Props { locale: Locale }
const { locale } = Astro.props;
const pathname = locale === 'sv' ? '/sv/' : '/';
---

<Layout locale={locale} pathname={pathname}>
  <Nav locale={locale} variant="overlay" />
  <main>
    <Hero locale={locale} />
    <HowItWorks locale={locale} />
    <JournalSection locale={locale} />
    <AppTour locale={locale} />
    <Guide locale={locale} />
    <Premium locale={locale} />
    <Privacy locale={locale} />
    <Faq locale={locale} />
    <FieldNotesTeaser locale={locale} />
    <FinalCta locale={locale} />
  </main>
  <Footer locale={locale} />
</Layout>
```

```bash
cd C:/w/birdy-web && git rm -q website/src/components/Inside.astro website/src/components/Coverage.astro
```

- [ ] **Step 7: Kör testerna**

```bash
cd C:/w/birdy-web/website && npm run build && PLAYWRIGHT_CHANNEL=chrome npx playwright test tests/coverage.spec.ts tests/home.spec.ts
```
Förväntat: alla gröna (kartan visar reservbilden eftersom ingen nyckel finns lokalt).

- [ ] **Step 8: Kör webbgaten och committa**

```bash
cd C:/w/birdy-web
git add website/tools/render-coverage-fallback.mjs website/package.json website/public/coverage/coverage-fallback.webp website/src/components/CoverageMap.astro website/src/components/Guide.astro website/src/components/HomePage.astro website/src/content/copy.sv.json website/src/content/copy.en.json website/tests/coverage.spec.ts
git commit -m "feat(website): Uppslagsverket med karta i fältbokens färger och ny reservbild

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: Premium och Integritet

**Files:**
- Modify: `website/src/components/Premium.astro` (hela filen)
- Modify: `website/src/components/Privacy.astro` (hela filen)
- Modify: `website/src/content/copy.sv.json`, `website/src/content/copy.en.json` (objekten `premium` och `privacy` ersätts)
- Modify: `website/tests/home.spec.ts`

- [ ] **Step 1: Skriv de fallerande testerna**

Lägg till sist i `website/tests/home.spec.ts`:

```ts
test.describe('premium och integritet', () => {
  for (const [path, firstFeature, freeLabel, firstCol] of [
    ['/sv/', 'Fynd-kartan', 'Alltid gratis:', 'Inget konto'],
    ['/', 'Finds map', 'Always free:', 'No account'],
  ] as const) {
    test(`premium och integritet på ${path}`, async ({ page }) => {
      await page.goto(path);
      const prem = page.locator('#premium');
      await expect(prem.locator('.feat h3')).toHaveCount(4);
      await expect(prem.locator('.feat h3').first()).toHaveText(firstFeature);
      await expect(prem.locator('.alw b')).toHaveText(freeLabel);
      await expect(prem).not.toContainText(/\d+\s?kr\b|SEK|€|\$/);
      const priv = page.locator('#privacy');
      await expect(priv.locator('.cols li')).toHaveCount(3);
      await expect(priv.locator('.cols h3').first()).toHaveText(firstCol);
      await expect(priv.locator('a.plink')).toHaveAttribute('href', '/legal/privacy/');
    });
  }
});
```

```bash
cd C:/w/birdy-web/website && npm run build && PLAYWRIGHT_CHANNEL=chrome npx playwright test tests/home.spec.ts -g "premium och integritet"
```
Förväntat: 2 failed.

- [ ] **Step 2: Texterna**

I `website/src/content/copy.sv.json`, ersätt objekten `"premium"` och `"privacy"` med:

```json
  "premium": {
    "kicker": "Birdy Premium",
    "headline": "För dig som vill *se mer.*",
    "lead": "Allt du behöver för att känna igen och spara fåglar är gratis. Premium lägger till fyra saker för dig som vill följa ditt år i fält.",
    "alwaysFreeLabel": "Alltid gratis:",
    "alwaysFree": "identifiering med kamera, foto och ljud · fältdagboken · 839 arter · 27 märken",
    "purchaseNote": "Köp och priser sköts i appen, via Google Play.",
    "seal": "Premium",
    "features": [
      { "title": "Fynd-kartan", "body": "Se var du har sett dina fåglar" },
      { "title": "Fältdagboken som PDF", "body": "Hela dagboken, redo att spara eller skriva ut" },
      { "title": "Säsongsstatistik", "body": "Mönster månad för månad" },
      { "title": "7 premiummärken", "body": "Extra stämplar att jaga" }
    ]
  },
  "privacy": {
    "kicker": "Integritet",
    "headline": "AI:n bor i *telefonen.*",
    "lead": "Birdy känner igen fåglar utan att skicka dina bilder eller ljud någonstans.",
    "cols": [
      { "title": "Inget konto", "body": "Öppna appen och börja. Det finns inget att logga in på." },
      { "title": "Stannar i telefonen", "body": "Bilder, ljud och fältdagbok sparas hos dig. Inget laddas upp för att identifiera en fågel." },
      { "title": "Plats bara om du vill", "body": "Platsen sparas bara om du slår på det, och då bara i telefonen." }
    ],
    "policyLink": "Läs integritetspolicyn"
  },
```

I `website/src/content/copy.en.json`, ersätt `"premium"` och `"privacy"` med:

```json
  "premium": {
    "kicker": "Birdy Premium",
    "headline": "For when you want *to see more.*",
    "lead": "Everything you need to identify and save birds is free. Premium adds four things for when you want to follow your year in the field.",
    "alwaysFreeLabel": "Always free:",
    "alwaysFree": "identification with camera, photo and sound · the field journal · 839 species · 27 badges",
    "purchaseNote": "Purchases and prices are handled in the app, through Google Play.",
    "seal": "Premium",
    "features": [
      { "title": "Finds map", "body": "See where you have seen your birds" },
      { "title": "Field journal as PDF", "body": "The whole journal, ready to save or print" },
      { "title": "Season statistics", "body": "Patterns month by month" },
      { "title": "7 premium badges", "body": "Extra stamps to chase" }
    ]
  },
  "privacy": {
    "kicker": "Privacy",
    "headline": "The AI lives *on your phone.*",
    "lead": "Birdy identifies birds without sending your photos or sound anywhere.",
    "cols": [
      { "title": "No account", "body": "Open the app and start. There is nothing to sign in to." },
      { "title": "Stays on your phone", "body": "Photos, sound and your field journal are stored with you. Nothing is uploaded to identify a bird." },
      { "title": "Location only if you want", "body": "Location is only saved if you turn it on, and then only on your phone." }
    ],
    "policyLink": "Read the privacy policy"
  },
```

- [ ] **Step 3: Premium**

Replace hela `website/src/components/Premium.astro` med:

```astro
---
import Kicker from './ui/Kicker.astro';
import JournalHeadline from './ui/JournalHeadline.astro';
import Icon from './ui/Icon.astro';
import { type Locale, getCopy } from '../lib/i18n';

interface Props { locale: Locale }
const { locale } = Astro.props;
const t = getCopy(locale);
const icons = ['map', 'pdf', 'chart', 'badge'] as const;
---

<section id="premium" class="sec prem">
  <span class="seal seal--brass pseal" aria-hidden="true">{t.premium.seal}</span>
  <div class="wrap grid">
    <div data-reveal>
      <Kicker text={t.premium.kicker} />
      <JournalHeadline text={t.premium.headline} level="h2" align="left" size="clamp(34px, 4.4vw, 52px)" />
      <p class="lead">{t.premium.lead}</p>
      <p class="alw"><b>{t.premium.alwaysFreeLabel}</b> {t.premium.alwaysFree}</p>
      <p class="pnote">{t.premium.purchaseNote}</p>
    </div>
    <ul class="feats">
      {t.premium.features.map((f, i) => (
        <li class="feat" data-reveal="slide" style={`--rd:${i * 90}ms`}>
          <span class="fic"><Icon name={icons[i]} size={21} /></span>
          <div><h3>{f.title}</h3><p>{f.body}</p></div>
        </li>
      ))}
    </ul>
  </div>
</section>

<style>
  .prem { position: relative; color: #F2EADC; background: linear-gradient(180deg, var(--moss) 0%, #18200F 100%); --kick-color: var(--brass-hi); --jh-ink: var(--cream); --jh-accent: var(--brass-hi); --lead-color: rgba(242, 234, 220, .75); }
  .prem::before { content: ''; position: absolute; inset: 0; background: radial-gradient(60% 80% at 85% 20%, rgba(226, 192, 126, .1), transparent 60%); pointer-events: none; }
  .prem :global(:focus-visible) { outline-color: var(--apricot); }
  .grid { position: relative; display: grid; grid-template-columns: 1fr 1fr; gap: 70px; }
  .alw { margin: 28px 0 0; padding: 16px 0; border-top: 1px solid rgba(255, 255, 255, .12); border-bottom: 1px solid rgba(255, 255, 255, .12); font-size: 14px; line-height: 1.7; color: rgba(242, 234, 220, .8); }
  .alw b { color: var(--cream); font-weight: 600; }
  .pnote { font-size: 12.5px; color: rgba(242, 234, 220, .62); margin: 16px 0 0; }
  .feat { display: flex; align-items: center; gap: 16px; padding: 18px 0; border-bottom: 1px solid rgba(255, 255, 255, .1); }
  .feat:first-child { padding-top: 4px; }
  .fic { width: 48px; height: 48px; border-radius: 50%; border: 1px solid rgba(226, 192, 126, .45); color: var(--brass-hi); display: grid; place-items: center; flex: none; }
  .feat h3 { font-size: 21px; color: var(--cream); }
  .feat p { margin: 3px 0 0; font-size: 13.5px; color: rgba(242, 234, 220, .66); }
  .pseal { position: absolute; z-index: 3; right: 9%; top: -46px; width: 92px; height: 92px; font-size: 17px; transform: rotate(-10deg); }
  @media (max-width: 760px) {
    .grid { grid-template-columns: 1fr; gap: 40px; }
    .pseal { right: 20px; top: -40px; width: 74px; height: 74px; }
  }
</style>
```

- [ ] **Step 4: Integritet**

Replace hela `website/src/components/Privacy.astro` med:

```astro
---
import Kicker from './ui/Kicker.astro';
import JournalHeadline from './ui/JournalHeadline.astro';
import Icon from './ui/Icon.astro';
import { type Locale, getCopy } from '../lib/i18n';

interface Props { locale: Locale }
const { locale } = Astro.props;
const t = getCopy(locale);
const icons = ['no-account', 'phone', 'pin'] as const;
---

<section id="privacy" class="sec priv">
  <div class="wrap">
    <div class="top" data-reveal>
      <Kicker text={t.privacy.kicker} center />
      <JournalHeadline text={t.privacy.headline} level="h2" size="clamp(34px, 4.4vw, 52px)" />
      <p class="lead">{t.privacy.lead}</p>
    </div>
    <ul class="cols">
      {t.privacy.cols.map((c, i) => (
        <li data-reveal style={`--rd:${i * 90}ms`}>
          <span class="ic"><Icon name={icons[i]} size={21} /></span>
          <h3>{c.title}</h3>
          <p>{c.body}</p>
        </li>
      ))}
    </ul>
    <a class="plink" href="/legal/privacy/">{t.privacy.policyLink}</a>
  </div>
</section>

<style>
  .top { text-align: center; max-width: 640px; margin: 0 auto; }
  .top .lead { margin-left: auto; margin-right: auto; }
  .cols { display: grid; grid-template-columns: repeat(3, 1fr); margin: 48px 0 0; border-top: 1px solid var(--line); }
  .cols li { padding: 28px 28px 0; text-align: center; }
  .cols li + li { border-left: 1px solid var(--line); }
  .ic { width: 46px; height: 46px; border-radius: 50%; display: grid; place-items: center; color: var(--rust); background: var(--card); box-shadow: inset 0 0 0 1px var(--line); margin: 0 auto 14px; }
  h3 { font-size: 21px; }
  .cols p { margin: 8px 0 0; font-size: 14.5px; line-height: 1.55; color: var(--muted); }
  .plink { display: table; margin: 30px auto 0; font-size: 13.5px; font-weight: 600; color: var(--rust); border-bottom: 1px solid currentColor; }
  @media (max-width: 760px) {
    .cols { grid-template-columns: 1fr; }
    .cols li + li { border-left: 0; border-top: 1px solid var(--line); }
    .cols li { padding: 26px 6px; }
  }
</style>
```

- [ ] **Step 5: Kör testerna, titta och committa**

```bash
cd C:/w/birdy-web/website && npm run build && PLAYWRIGHT_CHANNEL=chrome npx playwright test tests/home.spec.ts
```
Förväntat: alla gröna. Titta på skärmdumpar av Premium och Integritet (SV + EN, 390 och 1440): mässingssigillet "Premium" sticker upp över sektionskanten med mörk text, ingen text är för mörk mot mossan. Kör webbgaten och committa:

```bash
cd C:/w/birdy-web
git add website/src/components/Premium.astro website/src/components/Privacy.astro website/src/content/copy.sv.json website/src/content/copy.en.json website/tests/home.spec.ts
git commit -m "feat(website): Premium i mossa och mässing, Integritet i tre kolumner

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 11: Fältanteckningarna — bild per inlägg, listan, inlägget och kortet på startsidan

**Files:**
- Modify: `website/src/content.config.ts` (hela filen)
- Modify: `website/src/lib/field-notes.ts` (två nya funktioner sist)
- Create: `website/src/components/NoteCard.astro`
- Modify: `website/src/components/FieldNotesIndex.astro`, `FieldNoteArticle.astro`, `FieldNotesTeaser.astro` (hela filerna)
- Modify: `website/src/styles/article-prose.css` (hela filen)
- Modify: `website/src/content/field-notes/sv/why-birdy.md`, `website/src/content/field-notes/en/why-birdy.md` (hela filerna)
- Modify: `website/BLOG.md` (hela filen)
- Modify: `website/src/content/copy.sv.json`, `website/src/content/copy.en.json` (`fieldNotes` och `blog` läggs till)
- Modify: `website/tests/home.spec.ts`, `website/tests/smoke.spec.ts`

- [ ] **Step 1: Skriv de fallerande testerna**

Lägg till sist i `website/tests/home.spec.ts`:

```ts
test.describe('bloggen', () => {
  for (const [prefix, minRead, allNotes] of [
    ['/sv', 'min läsning', 'Alla fältanteckningar'],
    ['', 'min read', 'All field notes'],
  ] as const) {
    test(`listan och inlägget med bild på ${prefix || 'EN'}`, async ({ page }) => {
      const errors = trackConsoleErrors(page);
      await page.goto(`${prefix}/blog/`);
      await expect(page.locator('#site-nav')).toHaveClass(/nav--solid/);
      const card = page.locator(`main a.ncard[href="${prefix}/blog/why-birdy/"]`);
      await expect(card).toBeVisible();
      await expect(card.locator('img')).toHaveAttribute('alt', /.+/);
      await expect(card.locator('.ncard-meta')).toContainText(minRead);

      await page.goto(`${prefix}/blog/why-birdy/`);
      await expect(page.locator('.ahero img')).toBeVisible();
      await expect(page.locator('.ahero .ameta')).toContainText(minRead);
      await expect(page.locator('.article-prose blockquote')).toHaveCount(1);
      await expect(page.locator('.aend a[href*="play.google.com"]')).toHaveCount(1);
      await expect(page.locator('.aback a').first()).toContainText(allNotes);
      await expect(page.locator('meta[property="og:image"]')).toHaveAttribute('content', /\/_astro\/rodhake-q25334[^/]*\.jpg$/);
      expect(errors).toEqual([]);
    });
  }

  test('startsidan visar senaste inlägget som fotokort', async ({ page }) => {
    await page.goto('/sv/');
    await expect(page.locator('#field-notes a.ncard[href="/sv/blog/why-birdy/"] img')).toBeVisible();
  });
});
```

I `website/tests/smoke.spec.ts`, ersätt testet `'Swedish pages fit a narrow mobile viewport'` med:

```ts
  test('pages fit a narrow mobile viewport (SV and EN)', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    for (const path of ['/sv/', '/sv/blog/', '/sv/blog/why-birdy/', '/', '/blog/', '/blog/why-birdy/']) {
      await page.goto(path);
      const width = await page.evaluate(() => document.documentElement.scrollWidth);
      expect(width, `${path} should not overflow horizontally`).toBeLessThanOrEqual(390);
    }
  });
```

```bash
cd C:/w/birdy-web/website && npm run build && PLAYWRIGHT_CHANNEL=chrome npx playwright test tests/home.spec.ts -g "bloggen"
```
Förväntat: 3 failed.

- [ ] **Step 2: Bildfält i innehållsmodellen**

Replace hela `website/src/content.config.ts` med:

```ts
import { defineCollection, z } from 'astro:content';
import { glob } from 'astro/loaders';

const fieldNotes = defineCollection({
  loader: glob({
    pattern: '**/*.md',
    base: './src/content/field-notes',
    generateId: ({ entry }) => entry.replace(/\.md$/, '').replace(/\\/g, '/'),
  }),
  schema: ({ image }) => z.object({
    locale: z.enum(['en', 'sv']),
    slug: z.string(),
    title: z.string(),
    description: z.string(),
    date: z.coerce.date(),
    category: z.string(),
    // Every note has its own photo (spec §6): header, cards and share image. The build fails without it.
    image: image(),
    imageAlt: z.string().min(1),
    imageCaption: z.string().optional(),
    imagePosition: z.string().optional(),
  }),
});

export const collections = { fieldNotes };
```

- [ ] **Step 3: Lästid och datum**

Lägg till sist i `website/src/lib/field-notes.ts`:

```ts
/** Minutes to read a note's markdown body at 200 words per minute, at least 1. */
export function readingMinutes(body: string | undefined): number {
  const words = (body ?? '').replace(/[#>*_`[\]()!]/g, ' ').split(/\s+/).filter(Boolean).length;
  return Math.max(1, Math.round(words / 200));
}

export function formatNoteDate(date: Date, locale: Locale): string {
  return date.toLocaleDateString(locale === 'sv' ? 'sv-SE' : 'en-GB', { year: 'numeric', month: 'long', day: 'numeric' });
}
```

- [ ] **Step 4: Inläggen får bild och citat**

Replace hela `website/src/content/field-notes/sv/why-birdy.md` med:

```markdown
---
locale: sv
slug: why-birdy
title: Varför Birdy finns
description: Birdy hjälper dig att identifiera europeiska fåglar och spara varje fynd i en privat fältdagbok. Därför hör de två delarna ihop.
date: 2026-09-24
category: Bakom Birdy
image: ../../../assets/photos/rodhake-q25334.webp
imageAlt: En rödhake som sitter på en vissnad hortensia och tittar åt vänster
imageCaption: Rödhake, Erithacus rubecula
imagePosition: 30% 35%
---

Du ser en fågel och vill veta vilken art det är. Birdy hjälper dig med kameran, ett foto eller en kort ljudinspelning. När du har fått en träff kan du spara fyndet i din egen fältdagbok.

Det är därför Birdy finns.

> Identifieringen ger dig ett namn. Dagboken hjälper dig att behålla fyndet.

## Identifieringen är första steget

Öppna kameran så föreslår Birdy en art medan du tittar på fågeln. Du kan också välja ett foto ur galleriet eller spela in sången. Appen visar hur säker den är. Du granskar träffen innan något sparas.

I dagboken samlas dina fynd på ett ställe. Du kan gå tillbaka till fågeln du såg förra veckan, se vilka arter du har hittat och bygga upp en egen samling från platserna du besöker. Den finns kvar när du har stängt kameran.

## Guiden fungerar utan täckning

Birdy innehåller 839 europeiska arter med bilder och beskrivningar. Du kan söka i guiden när du är ute, även om mobilen har dålig täckning. Modellerna för bild och ljud körs också på telefonen.

Du behöver inget konto för att börja. Dina foton och din dagbok stannar på enheten. Birdy behöver inte ladda upp ett fågelfoto för att identifiera det.

## Byggd för att du ska lära dig mer

Birdy fungerar när du lär dig de vanliga fåglarna i trädgården och när du vill samla alla arter du stöter på. Appen kan vara osäker på en träff. Därför är det alltid du som bestämmer vad som hamnar i dagboken.

Androidappen finns att hämta nu. En version för iPhone är under utveckling. Båda bygger på samma tanke: det ska vara enkelt att känna igen en fågel, lära sig mer om den och minnas var du såg den.
```

Replace hela `website/src/content/field-notes/en/why-birdy.md` med:

```markdown
---
locale: en
slug: why-birdy
title: Why Birdy exists
description: Birdy helps you identify European birds and save each sighting in a private field journal. Here is why those two parts belong together.
date: 2026-09-24
category: Behind Birdy
image: ../../../assets/photos/rodhake-q25334.webp
imageAlt: A European robin perched on a faded hydrangea, looking left
imageCaption: European Robin, Erithacus rubecula
imagePosition: 30% 35%
---

You see a bird and want to know what it is. Birdy helps you identify it with the camera, a photo or a short recording. Once you have a match, you can save the sighting in your own field journal.

That is why Birdy exists.

> The identification gives you a name. The journal lets you keep the find.

## Identification is the first step

Open the camera and Birdy suggests a species while you look at the bird. You can also choose a photo from your gallery or record its song. The app shows how confident it is, and you review the match before you save anything.

Your journal brings those sightings together. You can return to a bird you saw last week, look through the species you have found and build a record of the places you visit. The result is useful long after you have closed the camera.

## The guide works without a signal

Birdy includes 839 European species with photos and descriptions. You can search the guide while you are outside, even when mobile coverage is poor. The models for photos and sound also run on your phone.

You do not need an account to get started. Your photos and journal stay on your device. Birdy does not need to upload a bird photo to identify it.

## Made to help you learn

Birdy is useful when you are learning common garden birds and when you want a record of everything you have found. It can be unsure about a match, so you always make the final call before it goes into your journal.

The Android app is available now. An iPhone version is in development. Both are part of the same idea: make it simple to recognise a bird, learn about it and remember where you found it.
```

- [ ] **Step 5: Texterna**

I `website/src/content/copy.sv.json`, lägg till efter `"privacy"`:

```json
  "fieldNotes": {
    "kicker": "Fältanteckningar",
    "headline": "Från *fältboken.*",
    "all": "Alla fältanteckningar"
  },
  "blog": {
    "kicker": "Fältanteckningar",
    "headline": "Anteckningar *från fältet.*",
    "lead": "Om fåglarna vi möter, hur Birdy fungerar och varför den är byggd som den är.",
    "minRead": "{n} min läsning",
    "allNotes": "Alla fältanteckningar",
    "seeHow": "Se hur Birdy fungerar",
    "endHeadline": "Ta med Birdy *ut i fält.*",
    "endSub": "Gratis att ladda ner. Inget konto. Fungerar utan täckning.",
    "indexTitle": "Fältanteckningar: artiklar om Birdy och fåglar | Birdy",
    "indexDescription": "Läs om hur Birdy byggs, hur appen fungerar och fåglarna du kan upptäcka med den. Artiklar och guider på svenska."
  },
```

I `website/src/content/copy.en.json`, lägg till efter `"privacy"`:

```json
  "fieldNotes": {
    "kicker": "Field notes",
    "headline": "From *the field journal.*",
    "all": "All field notes"
  },
  "blog": {
    "kicker": "Field notes",
    "headline": "Notes *from the field.*",
    "lead": "About the birds we meet, how Birdy works and why it is built the way it is.",
    "minRead": "{n} min read",
    "allNotes": "All field notes",
    "seeHow": "See how Birdy works",
    "endHeadline": "Take Birdy *into the field.*",
    "endSub": "Free to download. No account. Works without a signal.",
    "indexTitle": "Field Notes: articles about Birdy and birds | Birdy",
    "indexDescription": "Read how Birdy is built, how the app works and the birds you can discover with it. Articles and guides in English."
  },
```

- [ ] **Step 6: Kortet (delas av listan och startsidan)**

Create `website/src/components/NoteCard.astro`:

```astro
---
import { Image } from 'astro:assets';
import Kicker from './ui/Kicker.astro';
import { fieldNoteHref, formatNoteDate, readingMinutes, type FieldNote } from '../lib/field-notes';
import { getCopy } from '../lib/i18n';

interface Props {
  note: FieldNote;
  /** large = photo beside the text (latest note); small = photo above the text (grid). */
  size?: 'large' | 'small';
  headingLevel?: 'h2' | 'h3';
}
const { note, size = 'small', headingLevel = 'h3' } = Astro.props;
const { locale, title, description, category, date, image, imageAlt, imagePosition } = note.data;
const t = getCopy(locale);
const Heading = headingLevel;
const minutes = t.blog.minRead.replace('{n}', String(readingMinutes(note.body)));
---

<a class:list={['ncard', `ncard--${size}`]} href={fieldNoteHref(note)}>
  <div class="ncard-img">
    <Image
      src={image}
      alt={imageAlt}
      widths={size === 'large' ? [600, 900, 1300] : [400, 800]}
      sizes={size === 'large' ? '(max-width: 760px) 100vw, 620px' : '(max-width: 760px) 100vw, 360px'}
      style={imagePosition ? `object-position:${imagePosition}` : undefined}
      loading="lazy"
      decoding="async"
    />
  </div>
  <div class="ncard-body">
    <Kicker text={category} />
    <Heading class="ncard-title">{title}</Heading>
    {size === 'large' && <p class="ncard-desc">{description}</p>}
    <p class="ncard-meta"><time datetime={date.toISOString().slice(0, 10)}>{formatNoteDate(date, locale)}</time> · {minutes}</p>
  </div>
</a>

<style>
  .ncard { display: grid; background: var(--card); border-radius: 18px; overflow: hidden; box-shadow: 0 0 0 1px var(--line); transition: transform .35s var(--ease-paper), box-shadow .35s var(--ease-paper); }
  .ncard:hover { transform: translateY(-3px); box-shadow: 0 0 0 1px var(--line), 0 18px 36px rgba(31, 42, 25, .12); }
  .ncard-img { position: relative; overflow: hidden; }
  .ncard-img :global(img) { position: absolute; inset: 0; width: 100%; height: 100%; max-width: none; object-fit: cover; }
  .ncard-body { padding: 20px 20px 22px; }
  .ncard-body :global(.kick) { margin-bottom: 8px; font-size: 10px; }
  .ncard-title { font-size: 22px; line-height: 1.1; }
  .ncard-desc { color: var(--muted); font-size: 15px; line-height: 1.6; margin: 14px 0 0; }
  .ncard-meta { margin: 14px 0 0; font-size: 11.5px; color: var(--muted); letter-spacing: .06em; text-transform: uppercase; }
  .ncard--small .ncard-img { aspect-ratio: 4 / 3; }
  .ncard--large { grid-template-columns: 1.2fr 1fr; }
  .ncard--large .ncard-img { min-height: 320px; }
  .ncard--large .ncard-body { padding: 36px; }
  .ncard--large .ncard-body :global(.kick) { margin-bottom: 12px; font-size: 11px; }
  .ncard--large .ncard-title { font-size: 34px; line-height: 1.05; }
  .ncard--large .ncard-meta { margin-top: 22px; }
  @media (max-width: 760px) {
    .ncard--large { grid-template-columns: 1fr; }
    .ncard--large .ncard-img { min-height: 200px; }
    .ncard--large .ncard-body { padding: 24px; }
    .ncard--large .ncard-title { font-size: 28px; }
  }
</style>
```

- [ ] **Step 7: Listan**

Replace hela `website/src/components/FieldNotesIndex.astro` med:

```astro
---
import Layout from '../layouts/Layout.astro';
import Nav from './Nav.astro';
import Footer from './Footer.astro';
import Kicker from './ui/Kicker.astro';
import JournalHeadline from './ui/JournalHeadline.astro';
import NoteCard from './NoteCard.astro';
import { getFieldNotes, fieldNotesHref } from '../lib/field-notes';
import { type Locale, getCopy } from '../lib/i18n';

interface Props { locale: Locale }
const { locale } = Astro.props;
const t = getCopy(locale);
const [latest, ...rest] = await getFieldNotes(locale);
const otherLocale: Locale = locale === 'sv' ? 'en' : 'sv';
---

<Layout locale={locale} pathname={fieldNotesHref(locale)} title={t.blog.indexTitle} description={t.blog.indexDescription}>
  <Nav locale={locale} switchLangHref={fieldNotesHref(otherLocale)} />
  <main>
    <header class="bhead">
      <div class="wrap">
        <Kicker text={t.blog.kicker} />
        <JournalHeadline text={t.blog.headline} level="h1" align="left" size="clamp(40px, 5vw, 60px)" />
        <p>{t.blog.lead}</p>
      </div>
    </header>
    <div class="wrap list">
      {latest && <div class="first"><NoteCard note={latest} size="large" headingLevel="h2" /></div>}
      {rest.length > 0 && <div class="grid">{rest.map((note) => <NoteCard note={note} headingLevel="h2" />)}</div>}
    </div>
  </main>
  <Footer locale={locale} switchLangHref={fieldNotesHref(otherLocale)} />
</Layout>

<style>
  .bhead { background: var(--moss); color: var(--cream); padding: 34px 0 70px; --kick-color: var(--apricot); --jh-ink: var(--cream); --jh-accent: var(--apricot); }
  .bhead p { color: rgba(255, 248, 238, .8); font-size: 16px; line-height: 1.6; max-width: 34rem; margin: 16px 0 0; }
  .list { padding-bottom: 96px; }
  .first { position: relative; margin-top: -40px; }
  .grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 22px; margin-top: 44px; }
  @media (max-width: 760px) {
    .bhead { padding: 24px 0 60px; }
    .grid { grid-template-columns: 1fr; }
  }
</style>
```

- [ ] **Step 8: Inlägget**

Replace hela `website/src/components/FieldNoteArticle.astro` med:

```astro
---
import { render } from 'astro:content';
import { Image, getImage } from 'astro:assets';
import Layout from '../layouts/Layout.astro';
import Nav from './Nav.astro';
import Footer from './Footer.astro';
import Kicker from './ui/Kicker.astro';
import JournalHeadline from './ui/JournalHeadline.astro';
import PlayStoreBadge from './ui/PlayStoreBadge.astro';
import { fieldNoteHref, fieldNotesHref, formatNoteDate, readingMinutes, type FieldNote } from '../lib/field-notes';
import { getCopy } from '../lib/i18n';
import '../styles/article-prose.css';

interface Props { note: FieldNote }
const { note } = Astro.props;
const { Content } = await render(note);
const { locale, title, description, date, category, image, imageAlt, imageCaption, imagePosition } = note.data;
const t = getCopy(locale);
const sv = locale === 'sv';
const pathname = fieldNoteHref(note);
const otherPath = `${sv ? '' : '/sv'}/blog/${note.data.slug}/`;
const home = sv ? '/sv/' : '/';
const minutes = t.blog.minRead.replace('{n}', String(readingMinutes(note.body)));
// The note's own photo, cropped to 1200×630, is the share image (spec §6).
const share = await getImage({ src: image, width: 1200, height: 630, fit: 'cover', format: 'jpg', quality: 82 });
const playUrl = 'https://play.google.com/store/apps/details?id=se.birdy.android';
---

<Layout locale={locale} pathname={pathname} title={`${title} | Birdy`} description={description} articleDate={date.toISOString()} ogImage={share.src} ogImageAlt={imageAlt}>
  <Nav locale={locale} variant="overlay" switchLangHref={otherPath} />
  <main>
    <header class="ahero" data-nav-until>
      <Image class="ahero-img" src={image} alt={imageAlt} widths={[800, 1200, 1600]} sizes="100vw" style={imagePosition ? `object-position:${imagePosition}` : undefined} loading="eager" fetchpriority="high" decoding="async" />
      <div class="wrap">
        <div class="in">
          <Kicker text={category} />
          <h1>{title}</h1>
          <p class="ameta"><time datetime={date.toISOString().slice(0, 10)}>{formatNoteDate(date, locale)}</time> · {minutes}</p>
        </div>
      </div>
      {imageCaption && <span class="cap" aria-hidden="true">{imageCaption}</span>}
    </header>
    <article class="asheet">
      <div class="article-prose"><Content /></div>
      <aside class="aend">
        <div>
          <JournalHeadline text={t.blog.endHeadline} level="h2" align="left" size="28px" />
          <p>{t.blog.endSub}</p>
        </div>
        <PlayStoreBadge locale={locale} href={playUrl} alt={t.alt.playStoreBadge} size="small" />
      </aside>
      <nav class="aback" aria-label={t.blog.kicker}>
        <a href={fieldNotesHref(locale)}><span aria-hidden="true">← </span>{t.blog.allNotes}</a>
        <a href={`${home}#how-it-works`}>{t.blog.seeHow}</a>
      </nav>
    </article>
  </main>
  <Footer locale={locale} switchLangHref={otherPath} />
</Layout>

<style>
  .ahero { position: relative; min-height: 540px; display: flex; align-items: flex-end; color: var(--cream); background: var(--moss); overflow: hidden; --kick-color: var(--apricot); }
  .ahero :global(.ahero-img) { position: absolute; inset: 0; width: 100%; height: 100%; max-width: none; object-fit: cover; object-position: center; }
  .ahero::before { content: ''; position: absolute; inset: 0; z-index: 1; background: linear-gradient(0deg, rgba(31, 42, 25, .96) 0%, rgba(31, 42, 25, .55) 45%, rgba(31, 42, 25, .1) 80%), linear-gradient(180deg, rgba(31, 42, 25, .55), transparent 120px); }
  .ahero .wrap { position: relative; z-index: 2; width: 100%; padding-bottom: 96px; }
  .in { max-width: 760px; margin: 0 auto; }
  h1 { font-size: clamp(40px, 4.8vw, 58px); line-height: 1.02; letter-spacing: -.02em; max-width: 15ch; }
  .ameta { margin: 18px 0 0; font-size: 11.5px; letter-spacing: .12em; text-transform: uppercase; color: rgba(255, 248, 238, .75); }
  .cap { position: absolute; z-index: 2; right: 44px; bottom: 100px; font-family: var(--font-script); font-size: 20px; color: rgba(255, 248, 238, .8); transform: rotate(-3deg); }
  .asheet { position: relative; z-index: 3; max-width: 760px; margin: -54px auto 0; background: var(--paper); border-radius: 24px 24px 0 0; padding: 52px 60px 10px; }
  .aend { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin: 52px 0 30px; padding: 30px 32px; border-radius: 18px; background: var(--moss); color: var(--cream); --jh-ink: var(--cream); --jh-accent: var(--apricot); }
  .aend p { margin: 8px 0 0; font-size: 14px; color: rgba(255, 248, 238, .78); }
  .aend :global(:focus-visible) { outline-color: var(--apricot); }
  .aback { display: flex; justify-content: space-between; gap: 16px; font-size: 14px; font-weight: 600; color: var(--rust); padding: 22px 0 60px; border-top: 1px solid var(--line); }
  @media (max-width: 760px) {
    .ahero { min-height: 470px; }
    .ahero .wrap { padding-bottom: 80px; }
    .cap { display: none; }
    .asheet { margin-top: -40px; padding: 34px 20px 6px; border-radius: 20px 20px 0 0; }
    .aend { flex-direction: column; align-items: flex-start; }
  }
</style>
```

- [ ] **Step 9: Brödtextens stil**

Replace hela `website/src/styles/article-prose.css` med:

```css
/* Field note body (spec §6): calm serif headings, a narrow readable column and a rust pull quote. */
.article-prose { font-size: 17px; line-height: 1.75; color: #3B4434; }
.article-prose > p:first-child { font-size: 20px; line-height: 1.6; color: var(--ink); margin: 0 0 26px; }
.article-prose p { margin: 0 0 18px; }
.article-prose h2 { font-size: 30px; line-height: 1.1; letter-spacing: -.01em; color: var(--ink); margin: 44px 0 14px; }
.article-prose h3 { font-size: 23px; line-height: 1.2; color: var(--ink); margin: 32px 0 10px; }
.article-prose a { color: var(--rust); font-weight: 600; border-bottom: 1px solid currentColor; }
.article-prose ul, .article-prose ol { padding-left: 1.4rem; margin: 0 0 18px; }
.article-prose ul { list-style: disc; }
.article-prose ol { list-style: decimal; }
.article-prose li { margin-bottom: 6px; }
.article-prose strong { font-weight: 600; color: var(--ink); }
.article-prose blockquote { margin: 34px 0; padding: 6px 0 6px 24px; border-left: 2px solid var(--rust); font-family: var(--font-serif); font-style: italic; font-size: 27px; line-height: 1.25; color: var(--rust-deep); }
.article-prose blockquote p { margin: 0; font-size: inherit; line-height: inherit; color: inherit; }
@media (max-width: 760px) {
  .article-prose { font-size: 16px; }
  .article-prose > p:first-child { font-size: 18px; }
  .article-prose h2 { font-size: 25px; }
  .article-prose blockquote { font-size: 23px; }
}
```

- [ ] **Step 10: Kortet på startsidan**

Replace hela `website/src/components/FieldNotesTeaser.astro` med:

```astro
---
import Kicker from './ui/Kicker.astro';
import JournalHeadline from './ui/JournalHeadline.astro';
import NoteCard from './NoteCard.astro';
import { getFieldNotes, fieldNotesHref } from '../lib/field-notes';
import { type Locale, getCopy } from '../lib/i18n';

interface Props { locale: Locale }
const { locale } = Astro.props;
const t = getCopy(locale);
const latest = (await getFieldNotes(locale))[0];
---

{latest && (
  <section id="field-notes" class="sec notes">
    <div class="wrap">
      <div class="head" data-reveal>
        <div>
          <Kicker text={t.fieldNotes.kicker} />
          <JournalHeadline text={t.fieldNotes.headline} level="h2" align="left" size="clamp(34px, 4.4vw, 52px)" />
        </div>
        <a class="all" href={fieldNotesHref(locale)}>{t.fieldNotes.all}</a>
      </div>
      <div class="card" data-reveal><NoteCard note={latest} size="large" /></div>
    </div>
  </section>
)}

<style>
  .notes { padding-top: 0; }
  .head { display: flex; justify-content: space-between; align-items: end; gap: 30px; }
  .all { font-size: 14px; font-weight: 600; color: var(--rust); border-bottom: 1px solid currentColor; padding-bottom: 3px; white-space: nowrap; }
  .card { margin-top: 36px; }
  @media (max-width: 760px) { .head { flex-direction: column; align-items: flex-start; } }
</style>
```

- [ ] **Step 11: Skrivguiden**

Replace hela `website/BLOG.md` med:

```markdown
# Birdy Field Notes

Add each article as two Markdown files in `src/content/field-notes/en/` and `src/content/field-notes/sv/`. Use the same `slug` in both frontmatters; the build checks that every article has both translations.

Required frontmatter: `locale`, `slug`, `title`, `description`, `date` (`YYYY-MM-DD`), `category`, `image` and `imageAlt`. Optional: `imageCaption` (a short handwritten caption shown on the photo on desktop, for example the species name) and `imagePosition` (CSS `object-position` for the photo, for example `30% 35%`).

Put the photo in `src/assets/photos/` and point to it with a path relative to the Markdown file, for example `image: ../../../assets/photos/rodhake-q25334.webp`. Add the photo's source and licence to `src/assets/photos/SOURCES.md`. The build stops if `image` or `imageAlt` is missing. The photo is also used on the list page, on the homepage card and as the share image when the article is posted on social media.

The body goes below the frontmatter and may use headings, lists, links and one quote (`> ...`), which is shown as a large pull quote. The first paragraph is shown as the lead. Reading time is calculated from the text.

New posts appear automatically at `/blog/` and `/sv/blog/`, newest first. The latest post appears on both homepages. Titles, descriptions, canonical URLs, language alternatives and article metadata come from the frontmatter. Use a specific title and description for each language, and check all links and product claims before publishing.

Use the Swedish and English writing on `albit.se` as the tone reference. Write directly about what Birdy does, who it helps and why a feature matters. Avoid dash punctuation in public copy in both languages (`npm run test:no-dashes` checks it).
```

- [ ] **Step 12: Kör testerna och titta**

```bash
cd C:/w/birdy-web/website && npm run build && PLAYWRIGHT_CHANNEL=chrome npx playwright test
```
Förväntat: alla gröna, inklusive de gamla bloggtesterna i `smoke.spec.ts` (en h1 i `main`, tre h2 i `.article-prose`, `og:type` article, hreflang). Titta på skärmdumpar av `/sv/blog/` och `/sv/blog/why-birdy/` i 390 och 1440 px och jämför med `blogg.html`.

- [ ] **Step 13: Kör webbgaten och committa**

```bash
cd C:/w/birdy-web
git add website/src/content.config.ts website/src/lib/field-notes.ts website/src/components/NoteCard.astro website/src/components/FieldNotesIndex.astro website/src/components/FieldNoteArticle.astro website/src/components/FieldNotesTeaser.astro website/src/styles/article-prose.css website/src/content/field-notes website/BLOG.md website/src/content/copy.sv.json website/src/content/copy.en.json website/tests/home.spec.ts website/tests/smoke.spec.ts
git commit -m "feat(website): bloggen med bild per inlägg, pappersark och delningsbild

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 12: Frågor och Ta med Birdy ut i fält

**Files:**
- Modify: `website/src/components/Faq.astro`, `website/src/components/ui/FaqItem.astro`, `website/src/components/FinalCta.astro` (hela filerna)
- Modify: `website/src/components/HomePage.astro` (hela filen, slutlig ordning)
- Modify: `website/src/content/copy.sv.json`, `website/src/content/copy.en.json` (`faq` ersätts, `download` läggs till, `finalCta` tas bort)
- Modify: `website/tests/home.spec.ts`

- [ ] **Step 1: Skriv de fallerande testerna**

Lägg till sist i `website/tests/home.spec.ts`:

```ts
test.describe('frågor, slutet och ordningen', () => {
  test('startsidans sektioner kommer i rätt ordning', async ({ page }) => {
    for (const path of ['/sv/', '/']) {
      await page.goto(path);
      const ids = await page.locator('main > section[id]').evaluateAll((els) => els.map((e) => e.id));
      expect(ids).toEqual(['how-it-works', 'journal', 'app', 'guide', 'premium', 'privacy', 'field-notes', 'faq', 'download']);
    }
  });

  for (const [path, firstQ, headline] of [
    ['/sv/', 'Fungerar Birdy utan täckning?', 'Ta med Birdy ut i fält.'],
    ['/', 'Does Birdy work without a signal?', 'Take Birdy into the field.'],
  ] as const) {
    test(`frågor och slutsektion på ${path}`, async ({ page }) => {
      await page.goto(path);
      const faq = page.locator('#faq');
      await expect(faq.locator('details')).toHaveCount(5);
      await expect(faq.locator('details').first()).toHaveAttribute('open', '');
      await expect(faq.locator('summary .q').first()).toHaveText(firstQ);
      await expect(page.locator('#download h2')).toHaveText(headline);
      await expect(page.locator('#download a[href*="play.google.com"]')).toHaveCount(1);
      const ld = JSON.parse((await page.locator('script[type="application/ld+json"]').textContent()) ?? '{}');
      const faqLd = ld['@graph'].find((n: { '@type': string }) => n['@type'] === 'FAQPage');
      expect(faqLd.mainEntity).toHaveLength(5);
    });
  }

  test('inget iPhone-datum och ingen "håll i 3 sekunder" på startsidan', async ({ page }) => {
    for (const path of ['/sv/', '/']) {
      await page.goto(path);
      const text = await page.locator('main').innerText();
      expect(text).not.toMatch(/slutet av september|end of September|3 sekunder|3 seconds/i);
    }
  });
});
```

```bash
cd C:/w/birdy-web/website && npm run build && PLAYWRIGHT_CHANNEL=chrome npx playwright test tests/home.spec.ts -g "frågor, slutet"
```
Förväntat: FAIL (fel ordning, nio frågor, ingen `#download h2` med ny text).

- [ ] **Step 2: Texterna**

I `website/src/content/copy.sv.json`: ta bort objektet `"finalCta"`. Ersätt objektet `"faq"` med följande och lägg till `"download"` direkt efter det:

```json
  "faq": {
    "kicker": "Frågor",
    "headline": "Innan du *laddar ner.*",
    "items": [
      { "q": "Fungerar Birdy utan täckning?", "a": "Ja. Identifiering med kamera, foto och ljud körs i telefonen, och guiden och dagboken finns alltid med. Bara kartbilderna i Premium-kartan behöver uppkoppling." },
      { "q": "Behöver jag ett konto?", "a": "Nej. Öppna appen och börja. Din dagbok finns i telefonen." },
      { "q": "Hur säker är identifieringen?", "a": "Tillräckligt bra för att lära sig av, och ärlig när den är osäker. Varje träff visar hur säker Birdy är, och du bekräftar alltid innan något sparas." },
      { "q": "Vad kostar Birdy?", "a": "Birdy är gratis att ladda ner och använda. Premium är ett tillval med kartan, PDF-export, säsongsstatistik och extra märken. Priset ser du i appen." },
      { "q": "Finns Birdy för iPhone?", "a": "iPhone-appen är på väg. Android-appen finns att hämta nu, och länken till App Store dyker upp här när iPhone-versionen är ute." }
    ]
  },
  "download": {
    "kicker": "Birdy för Android och snart iPhone",
    "headline": "Ta med Birdy *ut i fält.*",
    "sub": "Gratis att ladda ner. Inget konto. Fungerar utan täckning."
  },
```

I `website/src/content/copy.en.json`: ta bort `"finalCta"`. Ersätt `"faq"` och lägg till `"download"` efter det:

```json
  "faq": {
    "kicker": "Questions",
    "headline": "Before you *download.*",
    "items": [
      { "q": "Does Birdy work without a signal?", "a": "Yes. Identification with the camera, photos and sound runs on your phone, and the guide and journal are always there. Only the map tiles in the Premium map need a connection." },
      { "q": "Do I need an account?", "a": "No. Open the app and start. Your journal lives on your phone." },
      { "q": "How sure is the identification?", "a": "Good enough to learn from, and honest when it is unsure. Every match shows how sure Birdy is, and you always confirm before anything is saved." },
      { "q": "What does Birdy cost?", "a": "Birdy is free to download and use. Premium is an optional upgrade with the map, PDF export, season statistics and extra badges. You see the price in the app." },
      { "q": "Is Birdy available for iPhone?", "a": "The iPhone app is on its way. The Android app is available now, and the App Store link will appear here when the iPhone version is out." }
    ]
  },
  "download": {
    "kicker": "Birdy for Android, soon on iPhone",
    "headline": "Take Birdy *into the field.*",
    "sub": "Free to download. No account. Works without a signal."
  },
```

- [ ] **Step 3: Frågorna**

Replace hela `website/src/components/ui/FaqItem.astro` med:

```astro
---
interface Props {
  question: string;
  answer: string;
  open?: boolean;
}
const { question, answer, open = false } = Astro.props;
---

<details class="faq-item" open={open}>
  <summary>
    <span class="q">{question}</span>
    <span class="toggle" aria-hidden="true">+</span>
  </summary>
  <p class="a">{answer}</p>
</details>

<style>
  /* Smooth open where the browser supports ::details-content; elsewhere it opens instantly. */
  .faq-item { border-bottom: 1px solid var(--line); interpolate-size: allow-keywords; }
  .faq-item:first-of-type { border-top: 1px solid var(--line); }
  summary { list-style: none; cursor: pointer; display: flex; justify-content: space-between; align-items: center; gap: 20px; padding: 22px 0; }
  summary::-webkit-details-marker { display: none; }
  .q { font-family: var(--font-serif); font-size: 21px; line-height: 1.2; color: var(--ink); }
  .toggle { width: 30px; height: 30px; border-radius: 50%; box-shadow: inset 0 0 0 1px var(--line); display: grid; place-items: center; color: var(--rust); font-size: 18px; flex: none; transition: transform .35s var(--ease-paper), background .25s; }
  .faq-item[open] .toggle { transform: rotate(45deg); background: var(--paper); }
  .faq-item::details-content { block-size: 0; overflow: hidden; transition: block-size .4s var(--ease-paper), content-visibility .4s allow-discrete; }
  .faq-item[open]::details-content { block-size: auto; }
  .a { margin: 0 0 22px; color: var(--muted); font-size: 15px; line-height: 1.65; max-width: 62ch; }
  @media (max-width: 760px) { .q { font-size: 18px; } }
</style>
```

Replace hela `website/src/components/Faq.astro` med:

```astro
---
import Kicker from './ui/Kicker.astro';
import JournalHeadline from './ui/JournalHeadline.astro';
import FaqItem from './ui/FaqItem.astro';
import { type Locale, getCopy } from '../lib/i18n';

interface Props { locale: Locale }
const { locale } = Astro.props;
const t = getCopy(locale);
---

<section id="faq" class="sec faq">
  <div class="wrap">
    <div class="top" data-reveal>
      <Kicker text={t.faq.kicker} center />
      <JournalHeadline text={t.faq.headline} level="h2" size="clamp(34px, 4.4vw, 52px)" />
    </div>
    <div data-reveal style="--rd:120ms">
      {t.faq.items.map((item, i) => <FaqItem question={item.q} answer={item.a} open={i === 0} />)}
    </div>
  </div>
</section>

<style>
  .faq { background: var(--card); border-top: 1px solid var(--line); }
  .faq .wrap { max-width: 860px; }
  .top { text-align: center; margin-bottom: 40px; }
</style>
```

- [ ] **Step 4: Ta med Birdy ut i fält**

Replace hela `website/src/components/FinalCta.astro` med:

```astro
---
import { Image } from 'astro:assets';
import Kicker from './ui/Kicker.astro';
import JournalHeadline from './ui/JournalHeadline.astro';
import PlayStoreBadge from './ui/PlayStoreBadge.astro';
import AppStoreBadge from './ui/AppStoreBadge.astro';
import reedling from '../assets/photos/skaggmes-q192817.webp';
import { type Locale, getCopy } from '../lib/i18n';

interface Props { locale: Locale }
const { locale } = Astro.props;
const t = getCopy(locale);
const playUrl = 'https://play.google.com/store/apps/details?id=se.birdy.android';
---

<section id="download" class="final">
  <Image class="final-bg" src={reedling} alt="" widths={[800, 1200, 1600]} sizes="100vw" loading="lazy" decoding="async" />
  <div class="wrap" data-reveal>
    <Kicker text={t.download.kicker} />
    <JournalHeadline text={t.download.headline} level="h2" align="left" size="clamp(40px, 5.4vw, 66px)" />
    <p class="sub">{t.download.sub}</p>
    <div class="badges">
      <PlayStoreBadge locale={locale} href={playUrl} alt={t.alt.playStoreBadge} size="small" />
      <AppStoreBadge locale={locale} size="small" />
    </div>
  </div>
</section>

<style>
  .final { position: relative; min-height: 520px; display: flex; align-items: center; color: var(--cream); background: var(--moss); overflow: hidden; --kick-color: var(--apricot); --jh-ink: var(--cream); --jh-accent: var(--apricot); }
  .final :global(.final-bg) { position: absolute; inset: 0; width: 100%; height: 100%; max-width: none; object-fit: cover; object-position: center 40%; }
  .final::before { content: ''; position: absolute; inset: 0; z-index: 1; background: linear-gradient(90deg, rgba(31, 42, 25, .94) 0%, rgba(31, 42, 25, .78) 40%, rgba(31, 42, 25, .15) 75%); }
  .final :global(:focus-visible) { outline-color: var(--apricot); }
  .final .wrap { position: relative; z-index: 2; width: 100%; }
  .final :global(.journal-headline) { max-width: 560px; }
  .sub { font-size: 16px; color: rgba(255, 248, 238, .85); margin: 18px 0 26px; }
  @media (max-width: 760px) {
    .final { min-height: 560px; align-items: flex-end; }
    .final :global(.final-bg) { object-position: 65% center; }
    .final::before { background: linear-gradient(0deg, rgba(31, 42, 25, .97) 0%, rgba(31, 42, 25, .8) 45%, rgba(31, 42, 25, .1) 85%); }
    .final .wrap { padding-bottom: 44px; }
  }
</style>
```

- [ ] **Step 5: Slutlig ordning på startsidan**

Replace hela `website/src/components/HomePage.astro` med:

```astro
---
import Layout from '../layouts/Layout.astro';
import Nav from './Nav.astro';
import Hero from './Hero.astro';
import HowItWorks from './HowItWorks.astro';
import JournalSection from './JournalSection.astro';
import AppTour from './AppTour.astro';
import Guide from './Guide.astro';
import Premium from './Premium.astro';
import Privacy from './Privacy.astro';
import FieldNotesTeaser from './FieldNotesTeaser.astro';
import Faq from './Faq.astro';
import FinalCta from './FinalCta.astro';
import Footer from './Footer.astro';
import type { Locale } from '../lib/i18n';

interface Props { locale: Locale }
const { locale } = Astro.props;
const pathname = locale === 'sv' ? '/sv/' : '/';
---

<Layout locale={locale} pathname={pathname}>
  <Nav locale={locale} variant="overlay" />
  <main>
    <Hero locale={locale} />
    <HowItWorks locale={locale} />
    <JournalSection locale={locale} />
    <AppTour locale={locale} />
    <Guide locale={locale} />
    <Premium locale={locale} />
    <Privacy locale={locale} />
    <FieldNotesTeaser locale={locale} />
    <Faq locale={locale} />
    <FinalCta locale={locale} />
  </main>
  <Footer locale={locale} />
</Layout>
```

- [ ] **Step 6: Kör testerna, titta, kör webbgaten och committa**

```bash
cd C:/w/birdy-web/website && npm run build && PLAYWRIGHT_CHANNEL=chrome npx playwright test
```
Förväntat: alla gröna. Ta helsidesskärmdumpar (`node ../.shots.mjs /sv/ 390,1440 ../.shots --full` och samma för `/`) och jämför hela sidan med `startsida-v5.html`. Kör webbgaten och committa:

```bash
cd C:/w/birdy-web
git add website/src/components/Faq.astro website/src/components/ui/FaqItem.astro website/src/components/FinalCta.astro website/src/components/HomePage.astro website/src/content/copy.sv.json website/src/content/copy.en.json website/tests/home.spec.ts
git commit -m "feat(website): fem frågor, Ta med Birdy ut i fält och slutlig sektionsordning

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 13: Juridiksidor, delningsbilder och städning

**Files:**
- Modify: `website/src/layouts/LegalLayout.astro` (`<style>`-blocket)
- Modify: `website/src/pages/legal/index.astro` (`<style>`-blocket)
- Modify: `website/src/styles/legal-prose.css` (hela filen)
- Modify: `website/src/components/ui/EyebrowLabel.astro`, `website/src/components/ui/Wordmark.astro` (standardfärgen)
- Modify: `website/src/styles/tokens.css` (aliasen tas bort)
- Modify: `website/src/content/copy.sv.json`, `website/src/content/copy.en.json` (`alt` rensas)
- Modify: `website/tools/generate-og.mjs` (hela filen), `website/public/og-field-en.png`, `website/public/og-field-sv.png`
- Delete: `website/src/components/ui/DeckleEdge.astro`, `CornerBrackets.astro`, `OrnamentRule.astro`, `DeviceFrame.astro`, `FieldIcon.astro`

- [ ] **Step 1: Se vad som återstår av de gamla färgnamnen**

```bash
cd C:/w/birdy-web/website && grep -rn "var(--color-" src
```
Förväntat: träffar i `LegalLayout.astro`, `pages/legal/index.astro`, `legal-prose.css`, `EyebrowLabel.astro`, `Wordmark.astro` och de fem oanvända ui-komponenterna. Efter den här tasken ska kommandot inte ge några träffar.

- [ ] **Step 2: Ta bort oanvända komponenter**

```bash
cd C:/w/birdy-web/website && for c in DeckleEdge CornerBrackets OrnamentRule DeviceFrame FieldIcon; do grep -rln "$c" src --include=*.astro | grep -v "ui/$c.astro" && echo "STOPP: $c används fortfarande"; done; echo klart
```
Förväntat: bara `klart`. Om någon rad `STOPP` visas: ta inte bort den komponenten, rapportera i stället.

```bash
cd C:/w/birdy-web && git rm -q website/src/components/ui/DeckleEdge.astro website/src/components/ui/CornerBrackets.astro website/src/components/ui/OrnamentRule.astro website/src/components/ui/DeviceFrame.astro website/src/components/ui/FieldIcon.astro
```

- [ ] **Step 3: Juridiksidornas stilar på nya färgnamn**

I `website/src/layouts/LegalLayout.astro`, ersätt hela `<style>`-blocket med:

```astro
<style>
  .legal-page { padding: 4rem 1.5rem 2rem; max-width: 64rem; margin: 0 auto; }
  .hero { text-align: center; margin-bottom: 3rem; display: flex; flex-direction: column; align-items: center; gap: 1rem; }
  .meta { font-family: var(--font-script); font-size: 1rem; color: var(--muted); margin: 0; }
  .more { max-width: 60ch; margin: 4rem auto 0; padding-top: 2rem; border-top: 1px dashed var(--line); }
  .more-label { font-family: var(--font-sans); font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.12em; color: var(--muted); margin: 0 0 1rem; }
  .more ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.75rem; }
  .more a { color: var(--rust); font-family: var(--font-serif); font-style: italic; font-size: 1.125rem; }
  .more .dash, .more .desc { font-family: var(--font-sans); font-size: 0.9375rem; color: var(--ink); }
</style>
```

I `website/src/pages/legal/index.astro`, ersätt hela `<style>`-blocket med:

```astro
<style>
  .legal-index { padding: 4rem 1.5rem 2rem; max-width: 60rem; margin: 0 auto; }
  .hero { text-align: center; margin-bottom: 3rem; display: flex; flex-direction: column; align-items: center; gap: 1rem; }
  .sub { font-family: var(--font-script); font-size: 1.25rem; color: var(--muted); margin: 0; }
  .docs { list-style: none; margin: 0 auto; padding: 0; display: grid; gap: 1.5rem; max-width: 42rem; }
  .doc-link { display: block; padding: 2rem; background: var(--card); border: 1px solid var(--line); border-radius: 16px; text-decoration: none; transition: transform 0.2s var(--ease-paper), box-shadow 0.2s var(--ease-paper); }
  .doc-link:hover, .doc-link:focus-visible { transform: translateY(-2px); box-shadow: 0 10px 24px rgba(31, 42, 25, 0.1); }
  .doc-title { font-family: var(--font-serif); font-style: italic; font-weight: 400; font-size: 1.75rem; color: var(--ink); margin: 0 0 0.5rem; }
  .doc-desc { font-family: var(--font-sans); font-size: 1rem; line-height: 1.5; color: var(--ink); margin: 0 0 1rem; }
  .doc-cta { font-family: var(--font-script); font-size: 1.125rem; color: var(--rust); font-weight: 700; }
</style>
```

Replace hela `website/src/styles/legal-prose.css` med:

```css
.legal-prose { font-family: var(--font-sans); font-size: 1rem; line-height: 1.65; color: var(--ink); max-width: 60ch; margin: 0 auto; }
.legal-prose h2 { font-family: var(--font-serif); font-style: italic; font-weight: 400; font-size: 1.5rem; line-height: 1.25; margin: 2.5rem 0 0.75rem; color: var(--ink); }
.legal-prose h3 { font-family: var(--font-serif); font-style: italic; font-weight: 400; font-size: 1.125rem; line-height: 1.3; margin: 2rem 0 0.5rem; }
.legal-prose p { margin: 0 0 1rem; }
.legal-prose ul, .legal-prose ol { margin: 0 0 1rem 1.5rem; padding: 0; }
.legal-prose ul { list-style: disc; }
.legal-prose ol { list-style: decimal; }
.legal-prose li { margin: 0.25rem 0; }
.legal-prose a { color: var(--rust); text-decoration: underline; text-decoration-color: rgba(154, 69, 38, 0.4); text-underline-offset: 3px; }
.legal-prose a:hover { text-decoration-color: var(--rust); }
.legal-prose strong { font-weight: 600; }
.legal-prose hr { border: none; border-top: 1px dashed var(--line); margin: 2.5rem 0; }
.legal-prose code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 0.875em; background: var(--card); padding: 0.1em 0.35em; border-radius: 3px; }
```

I `website/src/components/ui/EyebrowLabel.astro`, ändra raden `const { text, color = 'var(--color-orange)', plate } = Astro.props;` till:

```astro
const { text, color = 'var(--rust)', plate } = Astro.props;
```

I `website/src/components/ui/Wordmark.astro`, ändra raden `const { size = '120px', color = 'var(--color-orange)', alt, class: cls = '' } = Astro.props;` till:

```astro
const { size = '120px', color = 'var(--rust)', alt, class: cls = '' } = Astro.props;
```

- [ ] **Step 4: Ta bort de tillfälliga aliasen**

I `website/src/styles/tokens.css`, ta bort hela blocket från raden `/* TILLFÄLLIGA alias ...` till och med raden `--color-stamp-navy: var(--navy);` (tolv alias och kommentaren). Kör sedan:

```bash
cd C:/w/birdy-web/website && grep -rn "var(--color-" src || echo "inga gamla färgnamn kvar"
```
Förväntat: `inga gamla färgnamn kvar`.

- [ ] **Step 5: Rensa bildtexterna**

I `website/src/content/copy.sv.json`, ersätt hela objektet `"alt"` med:

```json
  "alt": {
    "wordmark": "Birdys ordbild",
    "playStoreBadge": "Hämta den på Google Play",
    "appStoreBadge": "Snart på App Store",
    "panorama": "Birdys appvyer för fågelidentifiering, artinformation och sparade fynd"
  }
```

I `website/src/content/copy.en.json`, ersätt hela objektet `"alt"` med:

```json
  "alt": {
    "wordmark": "Birdy wordmark",
    "playStoreBadge": "Get it on Google Play",
    "appStoreBadge": "Coming soon to the App Store",
    "panorama": "Birdy app screens showing bird identification, species information and saved sightings"
  }
```

```bash
cd C:/w/birdy-web/website && grep -rn "t\.alt\.\|copy\.alt\." src | grep -v -E "alt\.(wordmark|playStoreBadge|appStoreBadge|panorama)" || echo "alla alt-nycklar som används finns kvar"
```
Förväntat: `alla alt-nycklar som används finns kvar`.

- [ ] **Step 6: Delningsbilderna i nya färger**

Replace hela `website/tools/generate-og.mjs` med:

```js
// Share images (1200×630) for the homepages in the 1.3 palette: the robin photo under a moss shade.
// Run: npm run assets:og
import sharp from 'sharp';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const photo = resolve(root, 'src/assets/hero-robin.webp');
const variants = [
  { locale: 'en', line1: 'Know the bird.', line2: 'Keep the moment.', note: 'IDENTIFY · EXPLORE · REMEMBER' },
  { locale: 'sv', line1: 'Känn igen fågeln.', line2: 'Bevara stunden.', note: 'IDENTIFIERA · UTFORSKA · BEVARA' },
];

for (const item of variants) {
  const overlay = Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630">
    <defs><linearGradient id="shade"><stop offset="0" stop-color="#1F2A19" stop-opacity=".96"/><stop offset=".48" stop-color="#1F2A19" stop-opacity=".8"/><stop offset="1" stop-color="#1F2A19" stop-opacity=".06"/></linearGradient></defs>
    <rect width="1200" height="630" fill="url(#shade)"/>
    <text x="72" y="85" fill="#FFF8EE" font-family="Georgia,serif" font-style="italic" font-size="42">Birdy.</text>
    <text x="72" y="182" fill="#F2B27A" font-family="Arial,sans-serif" font-size="17" letter-spacing="3">${item.note}</text>
    <text x="72" y="320" fill="#FFF8EE" font-family="Georgia,serif" font-size="72">${item.line1}</text>
    <text x="72" y="410" fill="#F2B27A" font-family="Georgia,serif" font-style="italic" font-size="72">${item.line2}</text>
    <line x1="72" y1="546" x2="1128" y2="546" stroke="#FFF8EE" stroke-opacity=".45"/>
    <text x="72" y="584" fill="#FFF8EE" font-family="Arial,sans-serif" font-size="19">birdy.community</text>
  </svg>`);
  await sharp(photo)
    .resize(1200, 630, { fit: 'cover', position: 'centre' })
    .composite([{ input: overlay }])
    .png({ compressionLevel: 9 })
    .toFile(resolve(root, `public/og-field-${item.locale}.png`));
  console.log(`og-field-${item.locale}.png`);
}
```

```bash
cd C:/w/birdy-web/website && npm run assets:og
```
Förväntat: `og-field-en.png` och `og-field-sv.png`. Titta på båda bilderna (Read-verktyget): mossgrön toning över rödhaken, aprikosfärgad andra rad.

- [ ] **Step 7: Full kontroll**

```bash
cd C:/w/birdy-web/website && npm run check 2>&1 | tail -5
```
Förväntat: samma antal fel som baslinjen i Task 1 (ett känt fel i `astro.config.mjs`). Nya fel i filer från den här planen rättas innan commit.

Kör webbgaten. Ta skärmdumpar av `/legal/` och `/legal/privacy/` i 390 och 1440 px och titta på dem: mossgrön meny, papper, rostfärgade länkar.

- [ ] **Step 8: Commit**

```bash
cd C:/w/birdy-web
git add website/src/layouts/LegalLayout.astro website/src/pages/legal/index.astro website/src/styles/legal-prose.css website/src/components/ui/EyebrowLabel.astro website/src/components/ui/Wordmark.astro website/src/styles/tokens.css website/src/content/copy.sv.json website/src/content/copy.en.json website/tools/generate-og.mjs website/public/og-field-en.png website/public/og-field-sv.png
git commit -m "feat(website): juridiksidor och delningsbilder i 1.3-färgerna, gamla komponenter och alias bort

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 14: Kvalitetskontroll, förhandsvisning och status

**Files:**
- Modify: `CLAUDE.md` på `main` (via en tillfällig worktree, se Step 6)

- [ ] **Step 1: Hela gaten från rent läge**

```bash
cd C:/w/birdy-web/website && rm -rf dist .astro && npm run test:i18n && npm run test:no-accuracy && npm run test:contrast && npm run test:no-dashes && npm run build && PLAYWRIGHT_CHANNEL=chrome npx playwright test && npm run check 2>&1 | tail -3
```
Förväntat: allt grönt och samma `astro check`-resultat som baslinjen.

- [ ] **Step 2: Skärmdumpar av alla sidor i fyra bredder**

Med `npm run preview` igång:

```bash
cd C:/w/birdy-web/website
for p in / /sv/ /blog/ /sv/blog/ /blog/why-birdy/ /sv/blog/why-birdy/ /legal/ /legal/privacy/; do node ../.shots.mjs "$p" 390,1024,1440,1920 ../.shots/qa --full; done
```
Titta på varje bild och jämför startsidan med `startsida-v5.html` och bloggen med `blogg.html`. Kontrollera särskilt: inga texter som klipps eller överlappar, telefonen och rödhaken i första vyn, karusellens telefoner på engelska, mässingssigill med mörk text, att inget sticker ut i sidled i 390 px. Rätta fel, kör gaten igen och committa rättelserna med exakta sökvägar.

- [ ] **Step 3: Lighthouse (mobil)**

Med `npm run preview` igång:

```bash
cd C:/w/birdy-web/website && mkdir -p ../.shots
npx --yes lighthouse@12 http://localhost:4321/sv/ --only-categories=performance,accessibility,best-practices,seo --chrome-flags="--headless=new" --output=json --output-path=../.shots/lh-sv.json --quiet
npx --yes lighthouse@12 http://localhost:4321/ --only-categories=performance,accessibility,best-practices,seo --chrome-flags="--headless=new" --output=json --output-path=../.shots/lh-en.json --quiet
node -e "for (const f of ['../.shots/lh-sv.json', '../.shots/lh-en.json']) { const r = require(f); console.log(f, Object.fromEntries(Object.entries(r.categories).map(([k, v]) => [k, Math.round(v.score * 100)])), 'CLS', r.audits['cumulative-layout-shift'].displayValue, 'LCP', r.audits['largest-contentful-paint'].displayValue); }"
```
Förväntat: två rader med poäng per kategori samt CLS och LCP.

Mål (spec §8): Prestanda minst 90, Tillgänglighet minst 95 och CLS under 0,05. Ligger något under: undersök det Lighthouse pekar ut (oftast bildstorlek, förladdade typsnitt eller layoutskift när en bild saknar mått), rätta, kör gaten och Lighthouse igen. Rapportera de slutliga siffrorna.

- [ ] **Step 4: Sakpåståenden mot appens 1.3.0**

Läs appens strängar från release-grenen utan att röra appens worktree:

```bash
cd C:/Users/abbea/dev/1-mina-projekt/birdy && git fetch origin
for f in values values-en; do echo "== $f"; git show origin/release/1.3.0:composeApp/src/commonMain/composeResources/$f/strings.xml | grep -n -i -E 'premium_species_subtitle|premium_archive|listen_card_audio_body|daily_bird_eyebrow_present|3-second|3 sekunder|3 sek'; done
```
Gå igenom listan "Sakpåståenden som kontrolleras" i specens bilaga A. Om appen på release-grenen säger något annat än webben (till exempel att ljud-ID fortfarande beskrivs som "3 sekunder" i appen, eller att Premium innehåller något annat än Fynd-kartan, PDF, säsongsstatistik och 7 märken): ändra inte appen, utan skriv avvikelsen i rapporten till Albin.

- [ ] **Step 5: Pusha och hämta förhandsvisningens adress**

```bash
cd C:/w/birdy-web && git push
sleep 120; gh api repos/anonadrek/birdy/commits/website/1.3-lyft/status --jq '.statuses[] | select(.context | test("Vercel")) | {state, target_url}'
```
Förväntat: `state` `success` och en `target_url` (Vercels sida för bygget, med länk till förhandsvisningen). Öppna förhandsvisningen i Chrome (Playwright med `channel: 'chrome'`) och kontrollera att `/sv/` visar den nya första vyn. Kartan visar reservbilden där (nyckeln är låst till birdy.community); det är väntat.

- [ ] **Step 6: Status i CLAUDE.md på main**

Rör inte huvudkatalogen. Använd en tillfällig worktree:

```bash
git -C C:/Users/abbea/dev/1-mina-projekt/birdy fetch origin
git -C C:/Users/abbea/dev/1-mina-projekt/birdy worktree add --detach C:/w/birdy-docs origin/main
```
I `C:/w/birdy-docs/CLAUDE.md`, ersätt hela punkten som börjar med `- **🌐 WEBBEN I 1.3-LOOKEN — SPEC SKRIVEN` med en punkt som börjar `- **🌐 WEBBEN I 1.3-LOOKEN — BYGGD, väntar på genomgång och 1.3.0-release (<dagens datum>, Windows):**` och som innehåller: grenen `website/1.3-lyft` och dess senaste commit-hash, förhandsvisningens adress, Lighthouse-siffrorna från Step 3, eventuella avvikelser från Step 4, att sajten går live enligt Task 15 samma dag som vC129 är i produktion, och att specen och planen ligger i `docs/superpowers/specs/2026-09-24-website-1-3-lyft-design.md` och `docs/superpowers/plans/2026-09-24-website-1-3-lyft.md`.

```bash
cd C:/w/birdy-docs
git add CLAUDE.md
git commit -m "docs: CLAUDE.md-status för webben i 1.3-looken (förhandsvisning klar)

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
git push origin HEAD:main || (git pull --rebase origin main && git push origin HEAD:main)
git -C C:/Users/abbea/dev/1-mina-projekt/birdy worktree remove C:/w/birdy-docs
```

- [ ] **Step 7: Rapportera till Albin**

Skriv kort och utan tekniska ord: förhandsvisningens länk, att sidan går live samma dag som 1.3.0 finns på Google Play, Lighthouse-siffrorna, eventuella avvikelser mellan appen och webben, och att Albin och kompanjonen gärna tittar igenom förhandsvisningen i telefon och dator före dess.

---

### Task 15: Lanseringsdagen (körs först när vC129 / 1.3.0 finns i produktion)

**Förutsättning:** Albin har bekräftat att 1.3.0 syns som aktuell version på Google Play (`https://play.google.com/store/apps/details?id=se.birdy.android`). Tidigare än så körs ingenting här.

- [ ] **Step 1: Ta in main i grenen**

```bash
cd C:/w/birdy-web && git fetch origin && git merge --no-edit origin/main
```
Blir det konflikt i `CLAUDE.md`: behåll texten från `origin/main` och lägg tillbaka webbens statuspunkt. Andra konflikter ska inte uppstå (grenen ändrar bara `website/` och `docs/`); uppstår de ändå, stoppa och rapportera.

- [ ] **Step 2: Sista kontroll**

Kör Task 14 Step 1 (hela gaten) och Step 4 (sakpåståenden, nu mot `origin/main` om release-grenen redan är ihopslagen, annars mot `origin/release/1.3.0`). Allt ska vara grönt och stämma.

- [ ] **Step 3: Publicera**

```bash
cd C:/w/birdy-web && git push origin website/1.3-lyft && git push origin website/1.3-lyft:main
```
Förväntat: den andra pushen är en snabbspolning av `main`. Avvisas den (main har hunnit ändras): gör om Step 1 och Step 3.

- [ ] **Step 4: Kontrollera live**

```bash
sleep 150
for p in / /sv/ /blog/ /sv/blog/why-birdy/ /legal/privacy/; do printf '%s ' "$p"; curl -s -o /dev/null -w '%{http_code}\n' "https://birdy.community$p"; done
curl -s https://birdy.community/sv/ | grep -c "Fågelguide och fältdagbok"
```
Förväntat: `200` för alla sidor och `1` (eller fler) för den sista raden. Öppna `https://birdy.community/sv/#guide` i Chrome och kontrollera att den levande kartan (MapLibre-canvas) laddar på riktigt här; det gick inte att se i förhandsvisningen.

- [ ] **Step 5: Efterarbete**

- Be Albin begära indexering av `https://birdy.community/` och `https://birdy.community/sv/` i Search Console (manuellt steg).
- Uppdatera webbens statuspunkt i `CLAUDE.md` på `main` (samma tillfälliga worktree-metod som i Task 14 Step 6) till "LIVE sedan <datum>".
- Ta bort worktreen: `git -C C:/Users/abbea/dev/1-mina-projekt/birdy worktree remove C:/w/birdy-web`.

**Om 1.3.0 inte är ute den 30 september:** gör bara följande på `main` (tillfällig worktree som i Task 14 Step 6), inget annat. I `website/src/content/copy.sv.json`, byt svaret på frågan `"Finns Birdy för iPhone?"` till `"Birdy för iPhone är på väg. Android finns att hämta nu. Vi lägger till länken till App Store när appen finns där."`. I `website/src/content/copy.en.json`, byt svaret på `"Is there an iOS version?"` till `"Birdy for iPhone is on its way. Android is available today, and the App Store link will appear here once the iPhone app is live."`. Kör `npm run test:i18n && npm run build` i worktreen, committa med meddelandet `fix(website): ta bort iPhone-datumet i frågorna` och pusha till `main`.

---

## Självgranskning (gjord när planen skrevs)

- **Täckning mot specen:** §3 publicering (Task 1, 14, 15), §4 färger/typsnitt/delar (Task 2, 3), §5.1 meny (4), §5.2 första vyn med komposition och geometritester (5, 6), §5.3–5.4 (7), §5.5 karusell (8), §5.6 uppslagsverk och karta (9), §5.7–5.8 (10), §5.9 och §6 bloggen (11), §5.10–5.11 (12, sidfoten i 4), §7 juridik, delningsbilder, källor och städning (5, 13), §8 rörelse/prestanda/tillgänglighet (2, 6, 8, 14), §10 test (4 och 6–14), bilaga A (texterna i respektive task), bilaga B (Task 5).
- **Avvikelser från mockupen, alla beslutade i specen:** telefonens placering, Dagens fågel-texterna, sticky meny, mörk text på mässing, officiella Google Play-märket, självhostade typsnitt.

