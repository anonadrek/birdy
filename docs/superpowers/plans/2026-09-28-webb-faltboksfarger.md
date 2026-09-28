# Webben i fältbokens färger: implementationsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** birdy.community behåller 1.3-layouten exakt men får espresso i stället för mossa, persika bakom telefonkarusellen, handskrivna accentord och marginalanteckningar, och rivna papperskanter.

**Architecture:** Mossans tokens byter namn till neutrala `--dark*` (plus `--dark-rgb` för genomskinliga toningar) och får espressovärden; alla hårdkodade gröna nyanser utanför telefonerna blir tokens, och en ny palettvakt (`test:palette`) håller det så. Handstilen är CSS i `JournalHeadline` och hero-rubriken plus en ny `MarginNote`-komponent med nya valfria `note`-nycklar i copy-filerna. Kanterna är julis `DeckleEdge` tillbaka som komponent. Telefonerna i karusellen får appens färger som lokala tokens i `phone.css`.

**Tech Stack:** Astro 5, Tailwind v4 (bara preflight), Playwright 1.60 (`@playwright/test`), Node-skript för vakterna, sharp för delningsbilderna.

**Spec:** `docs/superpowers/specs/2026-09-28-webb-faltboksfarger-design.md` (godkänd rendering i `docs/superpowers/specs/assets/2026-09-28-webb-faltboksfarger/`).

---

## Arbetsmiljö och regler (läs först)

- **Allt arbete sker i worktreen `C:/w/birdy-faltbok` på grenen `website/faltboksfarger`** (skapas i Task 0). Alla sökvägar nedan är relativa till `C:/w/birdy-faltbok/website` om inget annat står.
- **Bash (Git Bash) på Windows.** Filerna har CRLF i arbetskopian (`core.autocrlf=true`); skripten nedan tål båda. Git Bash skriver om argument som börjar med `/` till Windows-sökvägar: sätt `MSYS_NO_PATHCONV=1` framför kommandon som tar `/sv/` som argument.
- **Playwright kör mot `astro preview` (den byggda `dist/`).** Kör alltid `npm run build` före testerna och använd port **4761** (`PLAYWRIGHT_PORT=4761`); konfigurationen återanvänder en server som redan kör, och på andra portar kan en annan sessions bygge ligga.
- **Standardkommando för testerna i den här planen:**
  ```bash
  npm run build && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test tests/faltbok.spec.ts
  ```
- **Husregler för all ny text:** inga tankstreck (`test:no-dashes`), inga precisionssiffror (`test:no-accuracy`), samma nycklar på svenska och engelska (`test:i18n`).
- **`node_modules` i worktreen är en junction till huvudklonens `node_modules`.** Ta ALDRIG bort worktreen med `rm -rf` eller `git worktree remove` innan junctionen är borttagen med `cmd //c rmdir` (Task 10), annars kan huvudklonens `node_modules` raderas.
- **Commits:** små, en per task, meddelanden på svenska i formen `feat(website): …` och avslutade med raden
  `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.

## Filkarta

| Fil | Ansvar | Task |
|---|---|---|
| `scripts/check-palette.mjs` (ny) | Palettvakt: inget mossgrönt utanför telefonerna | 1, 7 |
| `package.json` | Nytt skript `test:palette` | 1 |
| `src/styles/tokens.css` | Palettens tokens | 1, 2 |
| `src/styles/global.css`, `article-prose.css`, `phone.css` | Delade stilar; telefonernas lokala appfärger | 1 |
| `src/layouts/Layout.astro` | `theme-color` | 1 |
| `src/components/{Hero,hero/HeroScene,Nav,AppTour,Premium,FinalCta,Footer,FieldNotesIndex,FieldNoteArticle,JournalSection,NoteCard,CoverageMap}.astro`, `src/pages/legal/index.astro`, `src/components/ui/Kicker.astro` | Mossa blir espresso | 1 |
| `src/components/AppTour.astro` | Persikabandet | 2 |
| `src/components/ui/JournalHeadline.astro`, `src/components/Hero.astro` | Handskrivna accentord | 3 |
| `src/components/ui/MarginNote.astro` (ny) | Marginalanteckning under rubrik | 4 |
| `src/content/copy.{sv,en}.json` | `note`-nycklar, siffernoter, alt-text | 4, 5, 7 |
| `src/components/{Guide,CoverageMap,Footer}.astro` | Siffernoter, kartans bildtext, sidfotens rad | 5 |
| `src/components/ui/DeckleEdge.astro` (ny) | Riven papperskant | 6 |
| `src/components/{HowItWorks,AppTour,Guide,Premium,Privacy,FinalCta,Footer,HomePage,FieldNotesIndex}.astro` | Kanternas placering | 6 |
| `scripts/check-contrast.mjs` | Kontrastpar för nya färger | 1, 2 |
| `tools/generate-og.mjs`, `public/og-field-{sv,en}.png` | Delningsbilder i espresso | 7 |
| `tests/faltbok.spec.ts` (ny), `tests/home.spec.ts` | Nya test, menyfärgen | 1–7 |
| Artsidornas spec + fas 2-plan, den här specen | Dokumentation | 8 |

## Avvikelser från specen (godkända i planeringen, bokförs i specen i Task 8)

1. `--dark-2` behövs bara så länge karusellen är mörk; den tas bort i Task 2 när karusellen blir persika (ingen annan använder den).
2. Artikelns hero får ingen riven kant: papperssidan (`.asheet`) ligger redan över heron med rundade hörn. Bloggens rubrikband får kanten (i nederkant).
3. Specens grep-kontroll i §3.2 blir en vakt (`npm run test:palette`) så att grönt inte smyger tillbaka.
4. Premiums marginalanteckning är aprikos som i de andra mörka partierna (som i den godkända renderingen).

---

### Task 0: Worktree och baslinje

**Files:** inga ändringar.

- [ ] **Step 1: Skapa grenen och worktreen**

```bash
cd /c/Users/abbea/dev/1-mina-projekt/birdy
git pull --ff-only
git worktree add -b website/faltboksfarger C:/w/birdy-faltbok main
```

Expected: `Preparing worktree (new branch 'website/faltboksfarger')`.

- [ ] **Step 2: Länka `node_modules` (PowerShell)**

```powershell
New-Item -ItemType Junction -Path "C:\w\birdy-faltbok\website\node_modules" -Target "C:\Users\abbea\dev\1-mina-projekt\birdy\website\node_modules" | Out-Null
```

- [ ] **Step 3: Kör baslinjen**

```bash
cd C:/w/birdy-faltbok/website
npm run test:i18n && npm run test:no-accuracy && npm run test:contrast && npm run test:no-dashes && npm run build
```

Expected: `i18n parity OK (N keys)` (skriv upp N), `contrast-guard OK (20 par)`, bygget klart utan fel.

---

### Task 1: Espresso i stället för mossa (palettvakt först)

**Files:**
- Create: `scripts/check-palette.mjs`, `tests/faltbok.spec.ts`
- Modify: `package.json`, `src/styles/tokens.css`, `src/styles/global.css`, `src/styles/article-prose.css`, `src/styles/phone.css`, `src/layouts/Layout.astro`, `src/components/AppTour.astro`, `src/components/CoverageMap.astro`, `src/components/FieldNoteArticle.astro`, `src/components/FieldNotesIndex.astro`, `src/components/FinalCta.astro`, `src/components/Footer.astro`, `src/components/hero/HeroScene.astro`, `src/components/Hero.astro`, `src/components/JournalSection.astro`, `src/components/Nav.astro`, `src/components/NoteCard.astro`, `src/components/Premium.astro`, `src/components/ui/Kicker.astro`, `src/pages/legal/index.astro`, `scripts/check-contrast.mjs`, `tests/home.spec.ts`

- [ ] **Step 1: Skriv palettvakten**

Skapa `scripts/check-palette.mjs`:

```js
#!/usr/bin/env node
// Palettvakt (spec 2026-09-28-webb-faltboksfarger §3): webbens egen yta har inget mossgrönt och
// ingen olivton. Appens färger får bara finnas i telefonerna (src/styles/phone.css och
// src/components/phone/), som visar appen som den är.
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, join } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const scanDirs = ['src'];
const allowed = (rel) => rel === 'src/styles/phone.css' || rel.startsWith('src/components/phone/');
const banned = [
  [/#1f2a19/i, 'appens mossa #1F2A19'],
  [/#2b3a23/i, 'appens mossa-2 #2B3A23'],
  [/#172013/i, 'appens djupa mossa #172013'],
  [/#18200f/i, 'Premiums mossgradient #18200F'],
  [/#26301f/i, 'olivbläck #26301F'],
  [/#5b6350/i, 'olivgrå #5B6350'],
  [/#3b4434/i, 'olivgrå brödtext #3B4434'],
  [/31,\s*42,\s*25\b/, 'mossa som rgb (31, 42, 25)'],
  [/-moss\b/, 'mossnamn (--moss*, --color-moss)'],
];

const files = scanDirs.flatMap((dir) =>
  readdirSync(resolve(root, dir), { recursive: true })
    .map((f) => join(dir, String(f)).split('\\').join('/'))
    .filter((rel) => /\.(astro|css|ts|mjs|js)$/.test(rel)),
);

const hits = [];
for (const rel of files) {
  if (allowed(rel)) continue;
  readFileSync(resolve(root, rel), 'utf8').split(/\r?\n/).forEach((line, i) => {
    for (const [re, what] of banned) if (re.test(line)) hits.push(`${rel}:${i + 1}  ${what}`);
  });
}

if (hits.length) {
  console.error(`palette-guard FAILED: ${hits.length} ställen med appens gröna färger utanför telefonerna`);
  for (const h of hits) console.error(`  ${h}`);
  process.exit(1);
}
console.log(`palette-guard OK (${files.length} filer, mossgrönt bara i telefonerna)`);
```

I `package.json`, lägg till skriptet direkt efter `test:no-dashes`:

```json
    "test:no-dashes": "node scripts/check-no-dashes.mjs",
    "test:palette": "node scripts/check-palette.mjs",
```

- [ ] **Step 2: Kör vakten och se den falla**

Run: `npm run test:palette`
Expected: `palette-guard FAILED: … ställen` med träffar i bland annat `src/styles/tokens.css`, `src/components/Hero.astro`, `src/components/hero/HeroScene.astro`, `src/components/Nav.astro`, `src/layouts/Layout.astro`, `src/styles/global.css`.

- [ ] **Step 3: Skriv de nya Playwright-testen**

Skapa `tests/faltbok.spec.ts`:

```ts
import { test, expect } from '@playwright/test';

// Webben i fältbokens färger (docs/superpowers/specs/2026-09-28-webb-faltboksfarger-design.md).
const ESPRESSO = 'rgb(42, 29, 23)';
const ESPRESSO_DEEP = 'rgb(30, 20, 16)';

test.describe('espresso i stället för mossa', () => {
  test('de mörka partierna på /sv/ är espresso', async ({ page }) => {
    await page.goto('/sv/');
    await expect(page.locator('[data-hero]')).toHaveCSS('background-color', ESPRESSO);
    await expect(page.locator('#download')).toHaveCSS('background-color', ESPRESSO);
    await expect(page.locator('footer.footer')).toHaveCSS('background-color', ESPRESSO_DEEP);
    const prem = await page.locator('#premium').evaluate((el) => getComputedStyle(el).backgroundImage);
    expect(prem).toContain(ESPRESSO);
    expect(prem).toContain(ESPRESSO_DEEP);
    await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute('content', '#2A1D17');
  });

  test('brödtexten är varm brun', async ({ page }) => {
    await page.goto('/sv/');
    await expect(page.locator('body')).toHaveCSS('color', 'rgb(48, 32, 25)');
  });

  test('telefonerna visar appens egna färger (Lyssna-skärmen är mossgrön)', async ({ page }) => {
    await page.goto('/sv/');
    const bg = await page.locator('#app .ph-listen').first().evaluate((el) => getComputedStyle(el).backgroundImage);
    expect(bg).toContain('rgb(31, 42, 25)');
  });

  test('bloggens rubrikband är espresso', async ({ page }) => {
    await page.goto('/sv/blog/');
    await expect(page.locator('.bhead')).toHaveCSS('background-color', ESPRESSO);
  });
});
```

- [ ] **Step 4: Kör testen och se dem falla**

Run: `npm run build && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test tests/faltbok.spec.ts`
Expected: 3 failed (hero, brödtext, bloggband: fortfarande mossa/oliv), 1 passed (telefonerna).

- [ ] **Step 5: Skriv om `src/styles/tokens.css`**

Ersätt hela filen med:

```css
/* Birdy-webben: fältbokens färger (spec 2026-09-28-webb-faltboksfarger §3). Espresso i stället för appens
   mossa och varm brun text. Telefonerna i karusellen har appens egna färger (styles/phone.css). */
:root {
  --paper: #F6EFE2;
  --card: #FFFAF1;
  --ink: #302019;
  --muted: #6E584B;
  --line: #DFD2BA;
  --rust: #9A4526;
  --rust-deep: #72301A;
  --apricot: #F2B27A;
  --dark: #2A1D17;
  --dark-2: #3B2A21;
  --dark-deep: #1E1410;
  --dark-rgb: 42, 29, 23;
  --brass: #B8893A;
  --brass-hi: #E2C07E;
  --brass-ink: #241B0C;
  --cream: #FFF8EE;
  --navy: #1F3A5F;

  --font-serif: 'DM Serif Display', Georgia, 'Times New Roman', serif;
  --font-script: 'Caveat', 'Brush Script MT', cursive;
  --font-sans: 'Inter', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
  --ease-paper: cubic-bezier(0.22, 1, 0.36, 1);
}
```

- [ ] **Step 6: Byt mossans tokens och nyanser i komponenterna**

```bash
FILES="src/components/AppTour.astro src/components/CoverageMap.astro src/components/FieldNoteArticle.astro src/components/FieldNotesIndex.astro src/components/FinalCta.astro src/components/Footer.astro src/components/hero/HeroScene.astro src/components/Hero.astro src/components/JournalSection.astro src/components/Nav.astro src/components/NoteCard.astro src/components/Premium.astro src/pages/legal/index.astro src/styles/global.css"
sed -i \
  -e 's/var(--moss-2)/var(--dark-2)/g' \
  -e 's/var(--moss-deep)/var(--dark-deep)/g' \
  -e 's/var(--moss)/var(--dark)/g' \
  -e 's/rgba(31, 42, 25, /rgba(var(--dark-rgb), /g' \
  -e 's/#18200F/var(--dark-deep)/g' \
  -e 's/--color-moss: var(--dark)/--color-dark: var(--dark)/' \
  $FILES
```

- [ ] **Step 7: Kartans färger, `theme-color`, artikeltexten och kommentarerna**

```bash
sed -i \
  -e "s/const INK = '#26301F';/const INK = '#302019';/" \
  -e "s/const MOSS = '#1F2A19';/const DARK = '#2A1D17';/" \
  -e 's/tintCtx.fillStyle = MOSS;/tintCtx.fillStyle = DARK;/' \
  -e 's/and a moss bird/and an espresso bird/' \
  src/components/CoverageMap.astro
sed -i 's/<meta name="theme-color" content="#1F2A19" \/>/<meta name="theme-color" content="#2A1D17" \/>/' src/layouts/Layout.astro
sed -i 's/color: #3B4434;/color: #3D2C24;/' src/styles/article-prose.css
sed -i \
  -e 's/solid = moss bar in the flow/solid = espresso bar in the flow/' \
  -e 's/with a moss gradient/with an espresso gradient/' \
  -e 's/moss once it has scrolled past/espresso once it has scrolled past/' \
  -e 's/Moss by default/Espresso by default/' \
  -e 's/gets its own moss/gets its own espresso/' \
  src/components/Nav.astro
sed -i 's/apricot on moss)/apricot on espresso)/' src/components/ui/Kicker.astro
sed -i \
  -e 's/fades fully into the moss/fades fully into the espresso/' \
  -e 's/fading into the moss on the left/fading into the espresso on the left/' \
  src/components/hero/HeroScene.astro
```

- [ ] **Step 8: Telefonerna behåller appens färger**

I `src/styles/phone.css`, ersätt de två första raderna:

```css
/* Phone mockups drawn in code (hero + app tour), ported from the approved mockup.
   Everything is prefixed ph- so it never collides with page classes. Design size 250×520 px. */
```

med:

```css
/* Phone mockups drawn in code (hero + app tour), ported from the approved mockup.
   Everything is prefixed ph- so it never collides with page classes. Design size 250×520 px. */
/* The phones show the app as it is, so they keep the app's own palette ("Mossa, rost & mässing")
   whatever the website's tokens say (spec 2026-09-28-webb-faltboksfarger §3.3). */
.ph { --moss: #1F2A19; --ink: #26301F; --muted: #5B6350; }
```

- [ ] **Step 9: Kontrastvakten får de nya namnen**

I `scripts/check-contrast.mjs`, ersätt:

```js
  ['cream', 'moss', 4.5], ['apricot', 'moss', 4.5], ['brass-hi', 'moss', 4.5],
  ['cream', 'moss-deep', 4.5], ['apricot', 'moss-deep', 4.5],
```

med:

```js
  ['cream', 'dark', 4.5], ['apricot', 'dark', 4.5], ['brass-hi', 'dark', 4.5],
  ['cream', 'dark-deep', 4.5], ['apricot', 'dark-deep', 4.5],
```

och ersätt blocket från `const mossDeep` till och med `];` efter `compositedPairs`:

```js
const mossDeep = tokens['moss-deep'] ? hexToRgb(tokens['moss-deep']) : null;
const moss2 = tokens['moss-2'] ? hexToRgb(tokens['moss-2']) : null;
// Ljusaste punkten i Premiums mossgröna gradient (mossa + mässingsglöden från .prem::before,
// mätt mitt i den radiella höjdpunkten) — finns inte som token, bara ett uppmätt läge.
const premiumGradientLight = hexToRgb('#323822');

const compositedPairs = [
  { label: 'Footer .fbot', fg: [233, 226, 210], alpha: 0.55, bg: mossDeep, min: 4.5 },
  { label: 'Footer .sib-kick', fg: [233, 226, 210], alpha: 0.6, bg: mossDeep, min: 4.5 },
  { label: 'Premium .pnote', fg: [242, 234, 220], alpha: 0.62, bg: premiumGradientLight, min: 4.5 },
  { label: 'Premium .feat p', fg: [242, 234, 220], alpha: 0.66, bg: premiumGradientLight, min: 4.5 },
  { label: 'AppTour .cap-text', fg: [242, 234, 220], alpha: 0.72, bg: moss2, min: 4.5 },
  { label: 'AppTour .tour-lead', fg: [242, 234, 220], alpha: 0.72, bg: moss2, min: 4.5 },
];
```

med:

```js
const darkDeep = tokens['dark-deep'] ? hexToRgb(tokens['dark-deep']) : null;
const dark2 = tokens['dark-2'] ? hexToRgb(tokens['dark-2']) : null;
// Ljusaste punkten i Premiums espressogradient: mässingsglöden från .prem::before (10 % av
// rgba(226, 192, 126)) över --dark, mitt i den radiella höjdpunkten. Finns inte som token.
const premiumGradientLight = hexToRgb('#3C2D21');

const compositedPairs = [
  { label: 'Footer .fbot', fg: [233, 226, 210], alpha: 0.55, bg: darkDeep, min: 4.5 },
  { label: 'Footer .sib-kick', fg: [233, 226, 210], alpha: 0.6, bg: darkDeep, min: 4.5 },
  { label: 'Premium .pnote', fg: [242, 234, 220], alpha: 0.62, bg: premiumGradientLight, min: 4.5 },
  { label: 'Premium .feat p', fg: [242, 234, 220], alpha: 0.66, bg: premiumGradientLight, min: 4.5 },
  { label: 'AppTour .cap-text', fg: [242, 234, 220], alpha: 0.72, bg: dark2, min: 4.5 },
  { label: 'AppTour .tour-lead', fg: [242, 234, 220], alpha: 0.72, bg: dark2, min: 4.5 },
];
```

- [ ] **Step 10: Menytesten i `tests/home.spec.ts` får espressofärgen**

```bash
sed -i \
  -e 's/rgb(31, 42, 25)/rgb(42, 29, 23)/g' \
  -e 's/blir mossgrön/blir espressobrun/g' \
  -e 's/är mossgrön/är espressobrun/g' \
  tests/home.spec.ts
grep -n "mossgr\|31, 42, 25" tests/home.spec.ts
```

Expected: grep skriver ingenting.

- [ ] **Step 11: Kör vakterna, bygget och testen**

```bash
npm run test:palette && npm run test:contrast
npm run build && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test tests/faltbok.spec.ts tests/home.spec.ts -g "espresso|menyn|meny"
```

Expected: `palette-guard OK (… filer, mossgrönt bara i telefonerna)`, `contrast-guard OK (20 par)`, alla valda test gröna. Om palettvakten hittar något mer: byt det mot motsvarande `--dark*`-token (eller `rgba(var(--dark-rgb), …)`) och kör igen.

- [ ] **Step 12: Commit**

```bash
git add -A && git commit -F - <<'EOF'
feat(website): espresso i stället för mossa, varm brun text och palettvakt

Mossans tokens heter nu --dark* (+ --dark-rgb), alla hårdkodade gröna
nyanser utanför telefonerna är tokens och npm run test:palette håller
det så. Telefonerna behåller appens färger lokalt i phone.css.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 2: Persika bakom telefonkarusellen

**Files:**
- Modify: `tests/faltbok.spec.ts`, `scripts/check-contrast.mjs`, `src/styles/tokens.css`, `src/components/AppTour.astro`

- [ ] **Step 1: Skriv testet**

Lägg till sist i `tests/faltbok.spec.ts`:

```ts
test.describe('persika bakom karusellen', () => {
  test('karusellbandet är persika med mörk text', async ({ page }) => {
    await page.goto('/sv/');
    const tour = page.locator('#app');
    await expect(tour).toHaveCSS('background-color', 'rgb(253, 229, 203)');
    await expect(tour.locator('.tour-lead')).toHaveCSS('color', 'rgb(110, 88, 75)');
    await expect(tour.locator('.tour-head .journal-headline')).toHaveCSS('color', 'rgb(48, 32, 25)');
  });
});
```

- [ ] **Step 2: Kontrastparen för persika (vakten ska falla)**

I `scripts/check-contrast.mjs`, ersätt raden

```js
  ['ink', 'card', 4.5], ['muted', 'card', 4.5], ['rust', 'card', 4.5],
```

med

```js
  ['ink', 'card', 4.5], ['muted', 'card', 4.5], ['rust', 'card', 4.5],
  ['ink', 'peach', 4.5], ['muted', 'peach', 4.5], ['rust', 'peach', 4.5],
```

ta bort raden `const dark2 = tokens['dark-2'] ? hexToRgb(tokens['dark-2']) : null;` och de två raderna som börjar med `{ label: 'AppTour .cap-text'` och `{ label: 'AppTour .tour-lead'`, och ändra kommentaren `eller bakgrunden i Footer.astro/Premium.astro/AppTour.astro, uppdatera paret här också` till `eller bakgrunden i Footer.astro/Premium.astro, uppdatera paret här också`.

Run: `npm run test:contrast`
Expected: FAIL, tre rader `contrast-guard FAILED: token --peach saknas eller är inte #RRGGBB i tokens.css`.

- [ ] **Step 3: Token `--peach` in, `--dark-2` ut**

I `src/styles/tokens.css`: lägg till `  --peach: #FDE5CB;` på raden efter `  --apricot: #F2B27A;` och ta bort raden `  --dark-2: #3B2A21;`.

- [ ] **Step 4: Karusellen på persika**

```bash
sed -i \
  -e 's|color: #F2EADC; background: radial-gradient(90% 70% at 50% 45%, var(--dark-2) 0%, var(--dark) 55%, var(--dark-deep) 100%);|color: var(--ink); background: var(--peach);|' \
  -e 's|--kick-color: var(--apricot); --jh-ink: var(--cream); --jh-accent: var(--apricot); }|--kick-color: var(--rust); --jh-ink: var(--ink); --jh-accent: var(--rust); }|' \
  -e 's|radial-gradient(rgba(242, 234, 220, .05) 1px, transparent 1.2px)|radial-gradient(rgba(154, 69, 38, .07) 1px, transparent 1.2px)|' \
  -e 's|.tour :global(:focus-visible) { outline-color: var(--apricot); }|.tour :global(:focus-visible) { outline-color: var(--rust); }|' \
  -e 's|rgba(242, 234, 220, .72)|var(--muted)|g' \
  -e 's|border: 1px solid rgba(242, 234, 220, .28); background: transparent; color: #F2EADC;|border: 1px solid rgba(48, 32, 25, .25); background: transparent; color: var(--ink);|' \
  -e 's|background: rgba(242, 234, 220, .1); border-color: var(--apricot);|background: rgba(154, 69, 38, .08); border-color: var(--rust);|' \
  -e 's|line-height: 1.15; color: var(--cream);|line-height: 1.15; color: var(--ink);|g' \
  -e 's|background: rgba(242, 234, 220, .15);|background: rgba(48, 32, 25, .15);|' \
  -e 's|width: calc(100% / var(--n)); background: var(--apricot);|width: calc(100% / var(--n)); background: var(--rust);|' \
  -e '/alpha checked in scripts\/check-contrast.mjs/d' \
  src/components/AppTour.astro
grep -nE "242, 234, 220|F2EADC|var\(--cream\)|var\(--apricot\)" src/components/AppTour.astro
grep -rn "dark-2" src scripts
```

Expected: båda grep skriver ingenting.

- [ ] **Step 5: Kör vakterna och testet**

```bash
npm run test:contrast && npm run test:palette
npm run build && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test tests/faltbok.spec.ts tests/home.spec.ts -g "persika|karusell|tour"
```

Expected: `contrast-guard OK (21 par)`, palettvakten OK, testen gröna.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -F - <<'EOF'
feat(website): persika bakom telefonkarusellen

Karusellbandet är persika med mörk text, rost accent och rostprickar.
--dark-2 behövs inte längre; kontrastvakten har persikaparen.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 3: Handskrivna accentord

**Files:**
- Modify: `tests/faltbok.spec.ts`, `src/components/ui/JournalHeadline.astro`, `src/components/Hero.astro`

- [ ] **Step 1: Skriv testet**

Lägg till sist i `tests/faltbok.spec.ts`:

```ts
test.describe('handskrivna accentord', () => {
  for (const path of ['/', '/sv/'] as const) {
    test(`accentorden och heroraden är handskrivna på ${path}`, async ({ page }) => {
      await page.goto(path);
      const accents = page.locator('.journal-headline .accent');
      expect(await accents.count()).toBeGreaterThan(5);
      for (const el of await accents.all()) {
        await expect(el).toHaveCSS('font-family', /Caveat/);
        await expect(el).toHaveCSS('font-style', 'normal');
      }
      await expect(page.locator('[data-hero] h1 em')).toHaveCSS('font-family', /Caveat/);
    });
  }
});
```

- [ ] **Step 2: Kör testet och se det falla**

Run: `npm run build && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test tests/faltbok.spec.ts -g "accentord"`
Expected: 2 failed (font-family är DM Serif Display).

- [ ] **Step 3: Accenten i `JournalHeadline`**

I `src/components/ui/JournalHeadline.astro`, ersätt:

```css
  .accent {
    font-family: var(--font-serif);
    font-style: italic;
    font-weight: 400;
    font-size: 1em;
    color: var(--jh-accent, var(--rust));
  }
```

med:

```css
  /* Handwritten accent word, the field journal's signature (spec 2026-09-28-webb-faltboksfarger §5.1). */
  .accent {
    font-family: var(--font-script);
    font-style: normal;
    font-weight: 700;
    font-size: 1.18em;
    letter-spacing: 0;
    line-height: .9;
    color: var(--jh-accent, var(--rust));
  }
```

- [ ] **Step 4: Heroradens handstil**

I `src/components/Hero.astro`, ersätt:

```css
  h1 em { display: block; color: var(--apricot); }
```

med:

```css
  h1 em { display: block; color: var(--apricot); font-family: var(--font-script); font-style: normal; font-weight: 700; font-size: 1.18em; letter-spacing: 0; line-height: .95; }
```

- [ ] **Step 5: Kör testen, också heroraderna i `home.spec.ts`**

```bash
npm run build && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test tests/faltbok.spec.ts tests/home.spec.ts -g "accentord|rubrik"
```

Expected: gröna, inklusive `rubriken ryms på två rader på dator`. Faller den testen: sänk `line-height` i `h1 em` till `.9` och kör igen.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -F - <<'EOF'
feat(website): handskrivna accentord i rubrikerna och heroraden

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 4: Marginalanteckningar under startsidans rubriker

**Files:**
- Create: `src/components/ui/MarginNote.astro`
- Modify: `tests/faltbok.spec.ts`, `src/content/copy.sv.json`, `src/content/copy.en.json`, `src/components/Hero.astro`, `src/components/HowItWorks.astro`, `src/components/AppTour.astro`, `src/components/Guide.astro`, `src/components/Premium.astro`, `src/components/Privacy.astro`, `src/components/FieldNotesTeaser.astro`, `src/components/Faq.astro`, `src/components/FinalCta.astro`

- [ ] **Step 1: Skriv testet**

Lägg till sist i `tests/faltbok.spec.ts`:

```ts
test.describe('marginalanteckningar', () => {
  const notes = {
    '/sv/': ['Se. Lyssna. Spara.', 'kamera, foto eller läte', 'så här ser det ut i fält', 'slå upp fågeln du just såg', 'helt valfritt, att känna igen fåglar är gratis', 'dina bilder stannar i telefonen', 'anteckningar från oss som bygger Birdy', 'det folk brukar undra', 'vi ses i fält'],
    '/': ['See. Listen. Keep.', 'camera, photo or song', 'this is how it looks in the field', 'look up the bird you just saw', 'optional, identifying birds is free', 'your photos stay on your phone', 'notes from the people who build Birdy', 'what people usually ask', 'see you out there'],
  } as const;
  for (const [path, texts] of Object.entries(notes)) {
    test(`en handskriven rad under varje rubrik på ${path}`, async ({ page }) => {
      await page.goto(path);
      const mnotes = page.locator('.mnote');
      await expect(mnotes).toHaveText([...texts]);
      for (const el of await mnotes.all()) await expect(el).toHaveCSS('font-family', /Caveat/);
      await expect(page.locator('[data-hero] .mnote')).toHaveCSS('color', 'rgb(242, 178, 122)');
      await expect(page.locator('#how-it-works .mnote')).toHaveCSS('color', 'rgb(154, 69, 38)');
    });
  }
});
```

- [ ] **Step 2: Kör testet och se det falla**

Run: `npm run build && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test tests/faltbok.spec.ts -g "marginal"`
Expected: 2 failed (inga `.mnote`).

- [ ] **Step 3: Komponenten**

Skapa `src/components/ui/MarginNote.astro`:

```astro
---
// A short handwritten line in the margin under a heading (spec 2026-09-28-webb-faltboksfarger §5.2).
// Colour comes from --mn-color on an ancestor: rust on light sections, apricot on the dark ones.
// Renders nothing when the copy has no note.
interface Props {
  text?: string;
  align?: 'left' | 'center';
}
const { text, align = 'left' } = Astro.props;
---

{text && <p class:list={['mnote', { 'mnote--center': align === 'center' }]}>{text}</p>}

<style>
  .mnote {
    width: fit-content;
    max-width: 100%;
    margin: 12px 0 0;
    font-family: var(--font-script);
    font-weight: 700;
    font-size: clamp(20px, 2vw, 24px);
    line-height: 1.15;
    color: var(--mn-color, var(--rust));
    transform: rotate(-1.4deg);
    text-wrap: balance;
  }
  .mnote--center { margin-left: auto; margin-right: auto; }
</style>
```

- [ ] **Step 4: Texterna i copy-filerna**

Kör från `C:/w/birdy-faltbok/website`:

```bash
node - <<'EOF'
const fs = require('fs');
const edits = {
  'src/content/copy.sv.json': [
    ['    "line2": "Bevara stunden.",', '    "note": "Se. Lyssna. Spara.",'],
    ['    "headline": "Tre sätt att *fånga.*",', '    "note": "kamera, foto eller läte",'],
    ['    "headline": "Ett varv i *fältboken.*",', '    "note": "så här ser det ut i fält",'],
    ['    "headline": "Europas fåglar, *i fickan.*",', '    "note": "slå upp fågeln du just såg",'],
    ['    "headline": "För dig som vill *se mer.*",', '    "note": "helt valfritt, att känna igen fåglar är gratis",'],
    ['    "headline": "AI:n bor i *telefonen.*",', '    "note": "dina bilder stannar i telefonen",'],
    ['    "headline": "Från *fältboken.*",', '    "note": "anteckningar från oss som bygger Birdy",'],
    ['    "headline": "Innan du *laddar ner.*",', '    "note": "det folk brukar undra",'],
    ['    "headline": "Ta med Birdy *ut i fält.*",', '    "note": "vi ses i fält",'],
  ],
  'src/content/copy.en.json': [
    ['    "line2": "Keep the moment.",', '    "note": "See. Listen. Keep.",'],
    ['    "headline": "Three ways to *catch it.*",', '    "note": "camera, photo or song",'],
    ['    "headline": "A tour of *the field journal.*",', '    "note": "this is how it looks in the field",'],
    ['    "headline": "Europe\'s birds, *in your pocket.*",', '    "note": "look up the bird you just saw",'],
    ['    "headline": "For when you want *to see more.*",', '    "note": "optional, identifying birds is free",'],
    ['    "headline": "The AI lives *on your phone.*",', '    "note": "your photos stay on your phone",'],
    ['    "headline": "From *the field journal.*",', '    "note": "notes from the people who build Birdy",'],
    ['    "headline": "Before you *download.*",', '    "note": "what people usually ask",'],
    ['    "headline": "Take Birdy *into the field.*",', '    "note": "see you out there",'],
  ],
};
for (const [file, pairs] of Object.entries(edits)) {
  const raw = fs.readFileSync(file, 'utf8');
  const eol = raw.includes('\r\n') ? '\r\n' : '\n';
  const lines = raw.split(/\r?\n/);
  for (const [anchor, insert] of pairs) {
    const at = lines.reduce((acc, l, i) => (l === anchor ? [...acc, i] : acc), []);
    if (at.length !== 1) throw new Error(`${file}: ankaret finns ${at.length} gånger: ${anchor}`);
    lines.splice(at[0] + 1, 0, insert);
  }
  fs.writeFileSync(file, lines.join(eol));
  JSON.parse(fs.readFileSync(file, 'utf8'));
  console.log(`${file}: ${pairs.length} anteckningar`);
}
EOF
npm run test:i18n && npm run test:no-dashes && npm run test:no-accuracy
```

Expected: `…copy.sv.json: 9 anteckningar`, `…copy.en.json: 9 anteckningar`, `i18n parity OK (N+9 keys)`, de andra vakterna OK.

- [ ] **Step 5: Importera komponenten**

```bash
sed -i "s#^import JournalHeadline from './ui/JournalHeadline.astro';#&\nimport MarginNote from './ui/MarginNote.astro';#" \
  src/components/HowItWorks.astro src/components/AppTour.astro src/components/Guide.astro src/components/Premium.astro \
  src/components/Privacy.astro src/components/FieldNotesTeaser.astro src/components/Faq.astro src/components/FinalCta.astro
sed -i "s#^import Kicker from './ui/Kicker.astro';#&\nimport MarginNote from './ui/MarginNote.astro';#" src/components/Hero.astro
grep -c "import MarginNote" src/components/{HowItWorks,AppTour,Guide,Premium,Privacy,FieldNotesTeaser,Faq,FinalCta,Hero}.astro
```

Expected: `1` för alla nio filer.

- [ ] **Step 6: Placera anteckningarna**

Gör följande ersättningar (Edit-verktyget; indraget ska vara exakt som här).

`src/components/Hero.astro`:
```
          <h1>{t.hero.headline} <em>{t.hero.line2}</em></h1>
          <p class="sub">{t.hero.sub}</p>
```
blir
```
          <h1>{t.hero.headline} <em>{t.hero.line2}</em></h1>
          <MarginNote text={t.hero.note} />
          <p class="sub">{t.hero.sub}</p>
```

`src/components/HowItWorks.astro`:
```
      <JournalHeadline text={t.howItWorks.headline} level="h2" align="left" size="clamp(34px, 4.4vw, 52px)" />
      <p class="lead">{t.howItWorks.lead}</p>
```
blir
```
      <JournalHeadline text={t.howItWorks.headline} level="h2" align="left" size="clamp(34px, 4.4vw, 52px)" />
      <MarginNote text={t.howItWorks.note} />
      <p class="lead">{t.howItWorks.lead}</p>
```

`src/components/AppTour.astro`:
```
    <JournalHeadline text={t.tour.headline} level="h2" size="clamp(34px, 4.4vw, 52px)" />
    <p class="tour-lead">{t.tour.lead}</p>
```
blir
```
    <JournalHeadline text={t.tour.headline} level="h2" size="clamp(34px, 4.4vw, 52px)" />
    <MarginNote text={t.tour.note} align="center" />
    <p class="tour-lead">{t.tour.lead}</p>
```

`src/components/Guide.astro`:
```
        <JournalHeadline text={t.guide.headline} level="h2" align="left" size="clamp(34px, 4.4vw, 52px)" />
      </div>
```
blir
```
        <JournalHeadline text={t.guide.headline} level="h2" align="left" size="clamp(34px, 4.4vw, 52px)" />
        <MarginNote text={t.guide.note} />
      </div>
```

`src/components/Premium.astro`:
```
      <JournalHeadline text={t.premium.headline} level="h2" align="left" size="clamp(34px, 4.4vw, 52px)" />
      <p class="lead">{t.premium.lead}</p>
```
blir
```
      <JournalHeadline text={t.premium.headline} level="h2" align="left" size="clamp(34px, 4.4vw, 52px)" />
      <MarginNote text={t.premium.note} />
      <p class="lead">{t.premium.lead}</p>
```

`src/components/Privacy.astro`:
```
      <JournalHeadline text={t.privacy.headline} level="h2" size="clamp(34px, 4.4vw, 52px)" />
      <p class="lead">{t.privacy.lead}</p>
```
blir
```
      <JournalHeadline text={t.privacy.headline} level="h2" size="clamp(34px, 4.4vw, 52px)" />
      <MarginNote text={t.privacy.note} align="center" />
      <p class="lead">{t.privacy.lead}</p>
```

`src/components/FieldNotesTeaser.astro`:
```
          <JournalHeadline text={t.fieldNotes.headline} level="h2" align="left" size="clamp(34px, 4.4vw, 52px)" />
        </div>
```
blir
```
          <JournalHeadline text={t.fieldNotes.headline} level="h2" align="left" size="clamp(34px, 4.4vw, 52px)" />
          <MarginNote text={t.fieldNotes.note} />
        </div>
```

`src/components/Faq.astro`:
```
      <JournalHeadline text={t.faq.headline} level="h2" size="clamp(34px, 4.4vw, 52px)" />
    </div>
```
blir
```
      <JournalHeadline text={t.faq.headline} level="h2" size="clamp(34px, 4.4vw, 52px)" />
      <MarginNote text={t.faq.note} align="center" />
    </div>
```

`src/components/FinalCta.astro`:
```
    <JournalHeadline text={t.download.headline} level="h2" align="left" size="clamp(40px, 5.4vw, 66px)" />
    <p class="sub">{t.download.sub}</p>
```
blir
```
    <JournalHeadline text={t.download.headline} level="h2" align="left" size="clamp(40px, 5.4vw, 66px)" />
    <MarginNote text={t.download.note} />
    <p class="sub">{t.download.sub}</p>
```

- [ ] **Step 7: Aprikos på de mörka partierna**

```bash
sed -i 's/--kick-color: var(--apricot); --band-h: max(440px, 56.28vw); }/--kick-color: var(--apricot); --mn-color: var(--apricot); --band-h: max(440px, 56.28vw); }/' src/components/Hero.astro
sed -i 's/--jh-accent: var(--brass-hi); --lead-color:/--jh-accent: var(--brass-hi); --mn-color: var(--apricot); --lead-color:/' src/components/Premium.astro
sed -i 's/--jh-ink: var(--cream); --jh-accent: var(--apricot); }/--jh-ink: var(--cream); --jh-accent: var(--apricot); --mn-color: var(--apricot); }/' src/components/FinalCta.astro
grep -c "mn-color" src/components/Hero.astro src/components/Premium.astro src/components/FinalCta.astro
```

Expected: `1` per fil.

- [ ] **Step 8: Kör testen**

```bash
npm run build && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test tests/faltbok.spec.ts tests/home.spec.ts
```

Expected: alla gröna.

- [ ] **Step 9: Commit**

```bash
git add -A && git commit -F - <<'EOF'
feat(website): handskrivna marginalanteckningar under startsidans rubriker

Ny MarginNote-komponent och valfria note-nycklar på svenska och engelska.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 5: Siffernoter, kartans bildtext och sidfotens rad i handstil

**Files:**
- Modify: `tests/faltbok.spec.ts`, `src/content/copy.sv.json`, `src/content/copy.en.json`, `src/components/Guide.astro`, `src/components/CoverageMap.astro`, `src/components/Footer.astro`

- [ ] **Step 1: Skriv testet**

Lägg till sist i `tests/faltbok.spec.ts`:

```ts
test.describe('bildtexter i handstil', () => {
  const stats = {
    '/sv/': ['från vanliga till sällsynta', 'tjänas in när du hittar fåglar', 'din dagbok stannar hos dig'],
    '/': ['from common to rare', 'earned by finding birds', 'your journal stays with you'],
  } as const;
  for (const [path, texts] of Object.entries(stats)) {
    test(`siffernoter, kartans bildtext och sidfotens rad på ${path}`, async ({ page }) => {
      await page.goto(path);
      await expect(page.locator('#guide .stat-note')).toHaveText([...texts]);
      for (const sel of ['#guide .stat-note', '#guide .mapcap', 'footer .tag']) {
        await expect(page.locator(sel).first()).toHaveCSS('font-family', /Caveat/);
      }
    });
  }
});
```

- [ ] **Step 2: Kör testet och se det falla**

Run: `npm run build && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test tests/faltbok.spec.ts -g "bildtexter"`
Expected: 2 failed.

- [ ] **Step 3: Siffernoterna i copy-filerna**

```bash
node - <<'EOF'
const fs = require('fs');
const notes = {
  'src/content/copy.sv.json': ['    "headline": "Europas fåglar, *i fickan.*",', ['från vanliga till sällsynta', 'tjänas in när du hittar fåglar', 'din dagbok stannar hos dig']],
  'src/content/copy.en.json': ['    "headline": "Europe\'s birds, *in your pocket.*",', ['from common to rare', 'earned by finding birds', 'your journal stays with you']],
};
for (const [file, [anchor, texts]] of Object.entries(notes)) {
  const raw = fs.readFileSync(file, 'utf8');
  const eol = raw.includes('\r\n') ? '\r\n' : '\n';
  const lines = raw.split(/\r?\n/);
  const start = lines.indexOf(anchor);
  if (start < 0 || lines.indexOf(anchor, start + 1) >= 0) throw new Error(`${file}: ankaret ska finnas exakt en gång`);
  let n = 0;
  for (let i = start; i < lines.length && n < texts.length; i++) {
    if (!/^\s*\{ "value": /.test(lines[i])) continue;
    lines[i] = lines[i].replace(/" \}(,?)$/, `", "note": "${texts[n]}" }$1`);
    n++;
  }
  if (n !== texts.length) throw new Error(`${file}: hittade ${n} siffror, väntade ${texts.length}`);
  fs.writeFileSync(file, lines.join(eol));
  JSON.parse(fs.readFileSync(file, 'utf8'));
  console.log(`${file}: ${n} siffernoter`);
}
EOF
npm run test:i18n && npm run test:no-dashes && npm run test:no-accuracy
```

Expected: `3 siffernoter` per fil och vakterna OK.

- [ ] **Step 4: Siffernoten i `Guide.astro`**

Ersätt:
```
          <span class="stat-label" aria-hidden="true">{s.label}</span>
```
med:
```
          <span class="stat-label" aria-hidden="true">{s.label}</span>
          <span class="stat-note">{s.note}</span>
```

och i `<style>`, ersätt:
```
  .stat-label { font-size: 12px; letter-spacing: .14em; text-transform: uppercase; color: var(--muted); font-weight: 600; }
```
med:
```
  .stat-label { font-size: 12px; letter-spacing: .14em; text-transform: uppercase; color: var(--muted); font-weight: 600; }
  .stat-note { display: block; margin-top: 4px; font-family: var(--font-script); font-size: 19px; line-height: 1.2; color: var(--rust); }
```

- [ ] **Step 5: Kartans bildtext och sidfotens rad**

I `src/components/CoverageMap.astro`, ersätt:
```
  .mapcap { text-align: center; margin: 14px 0 0; font-size: 13px; color: var(--muted); }
```
med:
```
  .mapcap { text-align: center; margin: 14px 0 0; font-family: var(--font-script); font-size: 21px; line-height: 1.25; color: var(--rust); }
```

I `src/components/Footer.astro`, ersätt:
```
  .tag { margin: 12px 0 0; color: rgba(233, 226, 210, .7); font-size: 14px; }
```
med:
```
  .tag { margin: 12px 0 0; font-family: var(--font-script); font-size: 21px; line-height: 1.2; color: var(--apricot); }
```

- [ ] **Step 6: Kör testen**

```bash
npm run build && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test tests/faltbok.spec.ts tests/home.spec.ts
```

Expected: alla gröna.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -F - <<'EOF'
feat(website): siffernoter, kartans bildtext och sidfotens rad i handstil

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 6: Rivna papperskanter

**Files:**
- Create: `src/components/ui/DeckleEdge.astro`
- Modify: `tests/faltbok.spec.ts`, `src/components/HowItWorks.astro`, `src/components/AppTour.astro`, `src/components/Guide.astro`, `src/components/Premium.astro`, `src/components/Privacy.astro`, `src/components/FinalCta.astro`, `src/components/Footer.astro`, `src/components/HomePage.astro`, `src/components/FieldNotesIndex.astro`

- [ ] **Step 1: Skriv testet**

Lägg till sist i `tests/faltbok.spec.ts`:

```ts
test.describe('rivna papperskanter', () => {
  const fills = (page: import('@playwright/test').Page) =>
    page.locator('.deckle path').evaluateAll((ps) => ps.map((p) => getComputedStyle(p).fill));

  test('startsidan: övre bandets färg river ner i det undre', async ({ page }) => {
    await page.goto('/sv/');
    expect(await fills(page)).toEqual([
      'rgb(42, 29, 23)',    // hero → Tre sätt att fånga
      'rgb(255, 250, 241)', // Fältboken → karusellen
      'rgb(253, 229, 203)', // karusellen → Uppslagsverket
      'rgb(246, 239, 226)', // Uppslagsverket → Premium
      'rgb(30, 20, 16)',    // Premium → Integritet
      'rgb(255, 250, 241)', // Frågor → Ta med Birdy
      'rgb(42, 29, 23)',    // Ta med Birdy → sidfot
    ]);
    const sealZ = await page.locator('#premium .pseal').evaluate((el) => Number(getComputedStyle(el).zIndex));
    const edgeZ = await page.locator('#premium .deckle').evaluate((el) => Number(getComputedStyle(el).zIndex));
    expect(sealZ).toBeGreaterThan(edgeZ);
  });

  test('bloggen och juridiken', async ({ page }) => {
    await page.goto('/sv/blog/');
    expect(await fills(page)).toEqual(['rgb(246, 239, 226)', 'rgb(246, 239, 226)']);
    await page.goto('/legal/privacy/');
    expect(await fills(page)).toEqual(['rgb(246, 239, 226)']);
    await page.goto('/sv/blog/why-birdy/');
    expect(await fills(page)).toEqual(['rgb(246, 239, 226)']);
  });
});
```

- [ ] **Step 2: Kör testet och se det falla**

Run: `npm run build && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test tests/faltbok.spec.ts -g "kanter"`
Expected: 2 failed (inga `.deckle`).

- [ ] **Step 3: Komponenten (julis kant, ny färgregel)**

Skapa `src/components/ui/DeckleEdge.astro`:

```astro
---
// Torn paper edge where a coloured band meets its neighbour (spec 2026-09-28-webb-faltboksfarger §6).
// Sits at the top of the lower band, filled with the upper band's colour, so the upper band tears down
// into it. side="bottom" flips it for a band whose lower neighbour is not its own section.
// The parent must be position: relative. The path is the July DeckleEdge's.
interface Props {
  color: string;
  side?: 'top' | 'bottom';
}
const { color, side = 'top' } = Astro.props;
---

<div class={`deckle deckle-${side}`} aria-hidden="true">
  <svg viewBox="0 0 1440 26" preserveAspectRatio="none" xmlns="http://www.w3.org/2000/svg" focusable="false">
    <path
      style={`fill: ${color}`}
      d="M0 0 H1440 V8 L1404 14 L1370 6 L1342 16 L1310 9 L1272 18 L1236 8 L1206 15 L1170 5 L1132 17 L1098 10 L1060 20 L1024 9 L988 16 L952 6 L918 14 L884 8 L846 19 L810 10 L774 16 L738 7 L700 18 L664 9 L630 15 L596 6 L558 17 L524 10 L488 19 L452 8 L418 15 L382 6 L346 16 L312 9 L276 18 L240 8 L206 14 L172 5 L136 16 L100 9 L64 17 L30 7 L0 13 Z"
    />
  </svg>
</div>

<style>
  .deckle { position: absolute; left: 0; right: 0; z-index: 2; line-height: 0; pointer-events: none; }
  .deckle svg { display: block; width: 100%; height: clamp(14px, 2vw, 26px); }
  .deckle-top { top: -1px; }
  .deckle-bottom { bottom: -1px; transform: scaleY(-1); }
</style>
```

- [ ] **Step 4: Importera komponenten**

```bash
sed -i "s#^import JournalHeadline from './ui/JournalHeadline.astro';#&\nimport DeckleEdge from './ui/DeckleEdge.astro';#" \
  src/components/HowItWorks.astro src/components/AppTour.astro src/components/Guide.astro src/components/Premium.astro \
  src/components/Privacy.astro src/components/FinalCta.astro src/components/FieldNotesIndex.astro
sed -i "s#^import Wordmark from './ui/Wordmark.astro';#&\nimport DeckleEdge from './ui/DeckleEdge.astro';#" src/components/Footer.astro
grep -c "import DeckleEdge" src/components/{HowItWorks,AppTour,Guide,Premium,Privacy,FinalCta,FieldNotesIndex,Footer}.astro
```

Expected: `1` för alla åtta filer.

- [ ] **Step 5: Placera kanterna på startsidan**

Ersättningar (Edit-verktyget):

`src/components/HowItWorks.astro`: `<section id="how-it-works" class="sec how">` blir
```
<section id="how-it-works" class="sec how">
  <DeckleEdge color="var(--dark)" />
```
och i `<style>`, ersätt
```
<style>
  .grid { display: grid; grid-template-columns: 1fr 1.05fr; gap: 70px; align-items: start; padding-top: 40px; }
```
med
```
<style>
  .how { position: relative; }
  .grid { display: grid; grid-template-columns: 1fr 1.05fr; gap: 70px; align-items: start; padding-top: 40px; }
```

`src/components/AppTour.astro`: `<section id="app" class="tour" data-tour data-loop>` blir
```
<section id="app" class="tour" data-tour data-loop>
  <DeckleEdge color="var(--card)" />
```

`src/components/Guide.astro`: `<section id="guide" class="sec guide">` blir
```
<section id="guide" class="sec guide">
  <DeckleEdge color="var(--peach)" />
```
och i `<style>`, ersätt
```
<style>
  .head { display: grid; grid-template-columns: 1fr 1fr; gap: 70px; align-items: end; }
```
med
```
<style>
  .guide { position: relative; }
  .head { display: grid; grid-template-columns: 1fr 1fr; gap: 70px; align-items: end; }
```

`src/components/Premium.astro`: `<section id="premium" class="sec prem">` blir
```
<section id="premium" class="sec prem">
  <DeckleEdge color="var(--paper)" />
```

`src/components/Privacy.astro`: `<section id="privacy" class="sec priv">` blir
```
<section id="privacy" class="sec priv">
  <DeckleEdge color="var(--dark-deep)" />
```
och i `<style>`, ersätt
```
<style>
  .top { text-align: center; max-width: 640px; margin: 0 auto; }
```
med
```
<style>
  .priv { position: relative; }
  .top { text-align: center; max-width: 640px; margin: 0 auto; }
```

`src/components/FinalCta.astro`: `<section id="download" class="final">` blir
```
<section id="download" class="final">
  <DeckleEdge color="var(--card)" />
```

- [ ] **Step 6: Sidfoten (färgen beror på sidan) och bloggens rubrikband**

`src/components/Footer.astro`: ersätt
```
interface Props { locale: Locale; switchLangHref?: string }
const { locale, switchLangHref } = Astro.props;
```
med
```
// edge: the colour of whatever sits above the footer; it tears down into the footer (spec §6).
interface Props { locale: Locale; switchLangHref?: string; edge?: string }
const { locale, switchLangHref, edge = 'var(--paper)' } = Astro.props;
```
ersätt `<footer class="footer">` med
```
<footer class="footer">
  <DeckleEdge color={edge} />
```
och ersätt
```
  .footer { background: var(--dark-deep); color: #E9E2D2; padding: 70px 0 26px; }
```
med
```
  .footer { position: relative; background: var(--dark-deep); color: #E9E2D2; padding: 70px 0 26px; }
```

`src/components/HomePage.astro`: ersätt `  <Footer locale={locale} />` med `  <Footer locale={locale} edge="var(--dark)" />`.

`src/components/FieldNotesIndex.astro`: ersätt
```
        <p class="blead">{t.blog.lead}</p>
      </div>
    </header>
```
med
```
        <p class="blead">{t.blog.lead}</p>
      </div>
      <DeckleEdge color="var(--paper)" side="bottom" />
    </header>
```
och i `<style>`, ersätt `  .bhead { background: var(--dark);` med `  .bhead { position: relative; background: var(--dark);` och `  .first { position: relative; margin-top: -40px; }` med `  .first { position: relative; z-index: 3; margin-top: -40px; }`.

- [ ] **Step 7: Kör testen**

```bash
npm run build && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test tests/faltbok.spec.ts tests/home.spec.ts
```

Expected: alla gröna.

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -F - <<'EOF'
feat(website): rivna papperskanter där espresso- och persikabanden möts

Julis DeckleEdge tillbaka som komponent: övre bandets färg river ner i
det undre. Sidfotens kant följer sidan (espresso på startsidan, papper
annars), bloggens rubrikband får kanten i nederkant.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 7: Delningsbilder i espresso och ny alt-text

**Files:**
- Modify: `scripts/check-palette.mjs`, `tests/faltbok.spec.ts`, `tools/generate-og.mjs`, `public/og-field-sv.png`, `public/og-field-en.png`, `src/content/copy.sv.json`, `src/content/copy.en.json`

- [ ] **Step 1: Vakten täcker även `tools/` (ska falla)**

I `scripts/check-palette.mjs`, ersätt `const scanDirs = ['src'];` med `const scanDirs = ['src', 'tools'];`.

Run: `npm run test:palette`
Expected: FAIL med träffar i `tools/generate-og.mjs` (`#1F2A19` tre gånger).

- [ ] **Step 2: Skriv testet för alt-texten**

Lägg till sist i `tests/faltbok.spec.ts`:

```ts
test.describe('delningsbilder', () => {
  const alts = {
    '/sv/': 'En rödhake i varmt ljus med orden Känn igen fågeln. Bevara stunden.',
    '/': 'A European robin in warm light with the words Know the bird. Keep the moment.',
  } as const;
  for (const [path, alt] of Object.entries(alts)) {
    test(`delningsbildens alt-text på ${path}`, async ({ page }) => {
      await page.goto(path);
      await expect(page.locator('meta[property="og:image:alt"]')).toHaveAttribute('content', alt);
    });
  }
});
```

Run: `npm run build && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test tests/faltbok.spec.ts -g "delningsbild"`
Expected: 2 failed (texten säger fortfarande mossgrönt).

- [ ] **Step 3: Espresso i generatorn och ny alt-text**

```bash
sed -i -e 's/#1F2A19/#2A1D17/g' -e 's/the robin photo under a moss shade/the robin photo under an espresso shade/' -e 's/in the 1.3 palette/in the field journal palette/' tools/generate-og.mjs
sed -i 's/En rödhake i mossgrönt ljus med orden/En rödhake i varmt ljus med orden/' src/content/copy.sv.json
sed -i 's/A European robin in moss green light with the words/A European robin in warm light with the words/' src/content/copy.en.json
npm run assets:og
```

Expected: `og-field-en.png` och `og-field-sv.png` skrivs ut. Öppna `public/og-field-sv.png` med Read-verktyget och kontrollera att toningen är varmt brun och texten läsbar.

- [ ] **Step 4: Kör vakterna och testen**

```bash
npm run test:palette && npm run test:i18n && npm run test:no-dashes
npm run build && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test tests/faltbok.spec.ts
```

Expected: alla OK och gröna.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -F - <<'EOF'
feat(website): delningsbilderna i espresso och alt-text utan mossgrönt

Palettvakten täcker nu även tools/.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 8: Dokumentationen (artsidorna och specens avvikelser)

**Files:**
- Modify: `C:/w/birdy-faltbok/docs/superpowers/specs/2026-09-25-artsidor-design.md`, `C:/w/birdy-faltbok/docs/superpowers/plans/2026-09-25-artsidor-fas2-sidor.md`, `C:/w/birdy-faltbok/docs/superpowers/specs/2026-09-28-webb-faltboksfarger-design.md`

- [ ] **Step 1: Artsidornas spec**

I `docs/superpowers/specs/2026-09-25-artsidor-design.md`, ersätt
```
  3. Appruta (mossgrön): rubrik "Osäker på vad du ser?" och text om att Birdy känner igen arten på foto eller läte utan täckning. Därunder det officiella Google Play-märket med UTM (avsnitt 12).
  4. Marginalanteckning i Caveat, bara där artfilen har `marginalia` (i dag fyra arter).
```
med
```
  3. Appruta (espresso, `var(--dark)`, enligt `2026-09-28-webb-faltboksfarger-design.md`): rubrik "Osäker på vad du ser?" och text om att Birdy känner igen arten på foto eller läte utan täckning. Därunder det officiella Google Play-märket med UTM (avsnitt 12).
  4. Marginalanteckning i Caveat med startsidans `MarginNote`-komponent, bara där artfilen har `marginalia` (i dag fyra arter).
```

och lägg till en rad direkt under rubriken `## 5. Artsidan (layout B)`:
```

Utseendet följer webben i fältbokens färger (`2026-09-28-webb-faltboksfarger-design.md`): inget grönt, espresso för mörka ytor, handskrivna accentord i rubrikerna via `JournalHeadline`.
```

- [ ] **Step 2: Fas 2-planen**

I `docs/superpowers/plans/2026-09-25-artsidor-fas2-sidor.md`, ersätt
```
.sp-app { background: var(--moss); color: var(--cream); border-radius: 14px; padding: 18px 20px; --jh-ink: var(--cream); }
```
med
```
.sp-app { background: var(--dark); color: var(--cream); border-radius: 14px; padding: 18px 20px; --jh-ink: var(--cream); }
```

Run: `grep -n "var(--moss" docs/superpowers/plans/2026-09-25-artsidor-fas2-sidor.md docs/superpowers/specs/2026-09-25-artsidor-design.md`
Expected: ingen utskrift. (Finns fler träffar: byt `--moss` mot `--dark`, `--moss-deep` mot `--dark-deep`.)

- [ ] **Step 3: Den här specens avvikelser**

I `docs/superpowers/specs/2026-09-28-webb-faltboksfarger-design.md`, lägg till sist:

```markdown

## 13. Avvikelser vid genomförandet (2026-09-28)

- `--dark-2` togs bort när karusellen blev persika; ingen annan del använde den.
- Artikelns hero har ingen riven kant: papperssidan ligger redan över heron med rundade hörn. Bloggens rubrikband har kanten i nederkant, och sidfotens kant följer sidan (espresso på startsidan, papper annars).
- Grep-kontrollen i §3.2 är en vakt: `npm run test:palette` (`website/scripts/check-palette.mjs`), som även täcker `website/tools/`.
- Premiums marginalanteckning är aprikos som i de andra mörka partierna.
```

- [ ] **Step 4: Commit**

```bash
cd C:/w/birdy-faltbok && git add -A docs && git commit -F - <<'EOF'
docs: artsidorna i fältbokens färger och specens avvikelser

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
cd website
```

---

### Task 9: Hela gaten, visuell genomgång, axe och Lighthouse

**Files:** inga (fynd åtgärdas i de filer de gäller och committas separat).

- [ ] **Step 1: Alla vakter, bygget och alla Playwright-test**

```bash
rm -rf dist .astro
npm run test:i18n && npm run test:no-accuracy && npm run test:contrast && npm run test:no-dashes && npm run test:palette && npm run build
PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4761 npx playwright test
npm run check
```

Expected: vakterna OK (`contrast-guard OK (21 par)`), alla Playwright-test gröna (90 gamla + de nya), `astro check` med bara det kända felet i `astro.config.mjs`.

- [ ] **Step 2: Skriv QA-skriptet (utanför repot)**

Skapa `C:/w/birdy-faltbok-qa/qa.mjs`:

```js
// Visuell genomgång och axe för webben i fältbokens färger. Körs från C:/w/birdy-faltbok/website
// medan `npm run preview -- --port 4761` är igång.
import { createRequire } from 'node:module';
import { mkdirSync } from 'node:fs';

const require = createRequire(`${process.cwd()}/package.json`);
const { chromium } = require('@playwright/test');

const BASE = 'http://localhost:4761';
const OUT = 'C:/w/birdy-faltbok-qa/shots';
const pages = ['/', '/sv/', '/blog/', '/sv/blog/', '/blog/why-birdy/', '/sv/blog/why-birdy/', '/legal/', '/legal/privacy/', '/legal/terms/', '/legal/data-safety/'];
mkdirSync(OUT, { recursive: true });
const axeSource = await (await fetch('https://cdnjs.cloudflare.com/ajax/libs/axe-core/4.10.2/axe.min.js')).text();

const browser = await chromium.launch({ channel: 'chrome' });
let axeFailures = 0;
for (const width of [1440, 390]) {
  const ctx = await browser.newContext({ viewport: { width, height: width > 500 ? 900 : 844 }, reducedMotion: 'reduce' });
  for (const path of pages) {
    const page = await ctx.newPage();
    await page.goto(BASE + path, { waitUntil: 'networkidle' });
    await page.evaluate(async () => {
      for (let y = 0; y < document.body.scrollHeight; y += 600) { window.scrollTo(0, y); await new Promise((r) => setTimeout(r, 50)); }
      window.scrollTo(0, 0);
    });
    await page.waitForTimeout(500);
    await page.screenshot({ path: `${OUT}/${width}${path.replaceAll('/', '_')}.png`, fullPage: true });
    if (width === 1440) {
      await page.addScriptTag({ content: axeSource });
      const res = await page.evaluate(async () => (await window.axe.run(document, { runOnly: ['wcag2a', 'wcag2aa'] })).violations.map((v) => `${v.id} (${v.nodes.length})`));
      console.log(`axe ${path}: ${res.length ? res.join(', ') : '0 fel'}`);
      axeFailures += res.length;
    }
    await page.close();
  }
  await ctx.close();
}
await browser.close();
console.log(`klart: skärmbilder i ${OUT}, axe-fel totalt ${axeFailures}`);
process.exitCode = axeFailures ? 1 : 0;
```

- [ ] **Step 3: Kör skriptet mot en förhandsvisning**

Starta förhandsvisningen i bakgrunden (Bash med `run_in_background: true`): `npm run preview -- --port 4761 --strictPort`. Kör sedan:

```bash
node C:/w/birdy-faltbok-qa/qa.mjs
```

Expected: `axe <sida>: 0 fel` för alla tio sidor och `axe-fel totalt 0`.

- [ ] **Step 4: Titta på varje skärmbild**

Öppna alla 20 bilderna i `C:/w/birdy-faltbok-qa/shots/` med Read-verktyget (dela långa bilder i bitar med sharp om de blir för små att läsa). Kontrollera och skriv ner fynd:

1. Inget grönt utanför telefonerna (menyn, hero-toningen, Premium, Ta med Birdy, sidfoten, bloggbandet, artikelns hero och slutruta).
2. Persikabandet bakom karusellen, rost accent, pilknappar och förloppsstreck syns.
3. Accentorden handskrivna; heroraden "Bevara stunden." ryms utan att krocka med telefonen.
4. Varje marginalanteckning står under sin rubrik, bryts snyggt på 390 px och krockar inte med ingressen.
5. Siffernoterna under 839 / 34 / 0 får plats på 390 px utan att klippas.
6. Kanterna: sju på startsidan i rätt färg, inget glapp (ingen tunn linje av fel färg ovanför eller under), Premiums sigill ligger över kanten, hero-telefonen ligger över kanten under 1280 px.
7. Bloggens rubrikband: kanten i nederkant, första kortet ligger över kanten.
8. Juridiksidorna och artikeln: bara sidfotens papperskant.

Varje fynd åtgärdas, gaten i Step 1 körs om, och fixen committas med ett eget meddelande (`fix(website): …`).

- [ ] **Step 5: Lighthouse (mobil)**

Med förhandsvisningen igång:

```bash
npx -y lighthouse@12 http://localhost:4761/sv/ --only-categories=performance,accessibility,best-practices,seo --chrome-flags="--headless=new" --output=json --output-path=C:/w/birdy-faltbok-qa/lh-sv.json --quiet
node -e "const r=require('C:/w/birdy-faltbok-qa/lh-sv.json'); console.log(Object.values(r.categories).map(c=>c.id+' '+Math.round(c.score*100)).join(' | '))"
```

Expected: minst `performance 93 | accessibility 100 | best-practices 96 | seo 100` (96 beror på den lokala `/_vercel/insights`-404:an). Ett `EPERM` från chrome-launcher efteråt är ofarligt. Stoppa sedan förhandsvisningen.

---

### Task 10: Slutgranskning, förhandsvisning, release och städning

**Files:** `CLAUDE.md` (på main).

- [ ] **Step 1: Slutgranskning av hela grenen**

Kör en slutgranskning (superpowers:requesting-code-review eller en granskare i subagent-driven-development) av `main..website/faltboksfarger` mot specen. Åtgärda Critical/Important, kör gaten i Task 9 Step 1 igen och committa.

- [ ] **Step 2: Pusha grenen och hämta förhandsvisningens adress**

```bash
cd C:/w/birdy-faltbok
git push -u origin website/faltboksfarger
gh api repos/anonadrek/birdy/commits/$(git rev-parse HEAD)/status --jq '.statuses[] | select(.context | test("Vercel")) | .target_url'
```

Vercel bygger en förhandsvisning per gren (bakom Vercel-inloggning). Kör `gh api`-raden igen tills en adress skrivs ut.

- [ ] **Step 3: STOPP: Albin godkänner förhandsvisningen**

Skicka adressen till Albin med en kort lista på vad som ändrats (färger, persika, handstil, kanter, delningsbilder) och de två sakerna han kan vilja justera: anteckningarnas ordalydelse och att karusellens Lyssna-telefon fortfarande är appens gröna. Kartan syns inte i förhandsvisningen (nyckeln är låst till birdy.community); det är väntat. Fortsätt först när Albin godkänt; ändringar han ber om görs, gatas och committas på grenen.

- [ ] **Step 4: Snabbspola in i main och pusha**

```bash
cd /c/Users/abbea/dev/1-mina-projekt/birdy
git pull --ff-only
git merge --ff-only website/faltboksfarger
git push
```

Går snabbspolningen inte (main har fått nya commits): `cd C:/w/birdy-faltbok && git rebase main`, kör gaten i Task 9 Step 1 igen, och gör om det här steget.

- [ ] **Step 5: Live-kontroll med en markör som bara finns i nya versionen**

```bash
node -e "
const until = Date.now() + 10 * 60 * 1000;
(async function poll() {
  const html = await (await fetch('https://birdy.community/sv/', { cache: 'no-store' })).text();
  if (html.includes('Se. Lyssna. Spara.')) { console.log('LIVE'); process.exitCode = 0; return; }
  if (Date.now() > until) { console.log('INTE LIVE efter 10 min'); process.exitCode = 1; return; }
  setTimeout(poll, 20000);
})();
"
```

Expected: `LIVE` inom några minuter. Öppna sedan `https://birdy.community/sv/` och `https://birdy.community/` i webbläsaren och kontrollera att den levande kartan laddar och att fågeln i kartnålarna är espresso.

- [ ] **Step 6: CLAUDE.md på main**

I `CLAUDE.md`: ersätt posten som börjar med `- **🎨 WEBBEN I FÄLTBOKENS FÄRGER: SPEC SKRIVEN` med en post som säger att webben i fältbokens färger är live (datum, merge-commit, vad som ändrades i en mening, antal Playwright-test, axe 0 fel, Lighthouse-siffrorna, plan och spec). Lägg till `&& npm run test:palette` efter `npm run test:no-dashes` i webbens rad under "Vanliga kommandon". Committa och pusha:

```bash
git add CLAUDE.md && git commit -F - <<'EOF'
docs: webben i fältbokens färger är live

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
git push
```

- [ ] **Step 7: Städa worktrees (junctionen först!)**

```bash
cmd //c rmdir "C:\\w\\birdy-faltbok\\website\\node_modules"
git worktree remove --force C:/w/birdy-faltbok   # --force är säkert först när junctionen är borta (dist/ och .astro/ ligger kvar)
git branch -d website/faltboksfarger
for w in birdy-old-0711 birdy-old-0924; do
  cmd //c rmdir "C:\\w\\$w\\website\\node_modules"
  git worktree remove --force C:/w/$w
done
git worktree list
ls /c/Users/abbea/dev/1-mina-projekt/birdy/website/node_modules | head -3
```

Expected: `git worktree list` visar bara huvudklonen och `C:/w/birdy-130`; sista raden visar att huvudklonens `node_modules` finns kvar. (`birdy-old-0711` och `birdy-old-0924` skapades under brainstormen för jämförelsebilderna och har också junctions.) Ta bort `C:/w/birdy-faltbok-qa` om skärmbilderna inte behövs.
