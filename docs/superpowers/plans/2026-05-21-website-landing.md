# Birdy v1.0 Marketing Landing Page — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a bilingual (EN/SV) marketing landing page for Birdy v1.0 in `website/` that converts visitors to Google Play downloads, deployed via the existing GitHub Pages workflow alongside the current pandoc-rendered legal pages.

**Architecture:** Astro 5 static-site generator with built-in i18n (`/` = EN, `/sv/` = SV) + Tailwind v4 for styling via design-token CSS variables that mirror the app's Field Journal theme. Native `<details>` for FAQ (no JS framework). Self-hosted woff2 fonts. Assets sourced from user's Desktop folders via a sync-script that commits results to the repo. CI builds Astro + runs pandoc + assembles both into `_site/`.

**Tech Stack:** Astro 5.x, Tailwind v4 (`@tailwindcss/vite`), TypeScript, Playwright (smoke tests), pandoc (legal pages, existing), GitHub Pages, GitHub Actions.

**Spec:** [`docs/superpowers/specs/2026-05-21-website-landing-design.md`](../specs/2026-05-21-website-landing-design.md)

**Resolved open questions (vs spec §14):**
- #1 Domain → `birdy.app` (already used in `feedback@birdy.app`); CNAME set in T22.
- #4 Support email → `feedback@birdy.app` (matches existing `pages.yml`).
- #5 Play Store URL → `https://play.google.com/store/apps/details?id=se.birdy.android`; site deploys only after app is published.
- #7 Wing-logo → paper-bg variant (`birdy-logo-paper.svg`) on light sections; transparent variant (`birdy-logo-transparent.svg`) on hero-photo overlays.

**Deferred to follow-ups (do not block plan):**
- #2 Audio screenshots → placeholder waveform-illustration shipped in T13; real screenshots swap-in post-6b2.
- #3 SV translation → English copy authored in this plan; SV authored by user in `copy.sv.json` per i18n-parity-check.
- #6 iOS FAQ wording → keep spec wording ("iOS comes after launch if there is demand"); user may soften pre-launch.
- #8 Premium CTA target → Play Store listing URL (same as primary CTA) until in-app deeplink decided.
- #9 Analytics → none in v1; revisit post-launch.

**Versioning:** This is a web asset, not a phone-app version. No `v0.x` tag.

---

## File Structure

```
website/
├─ package.json
├─ package-lock.json                    (after npm install)
├─ astro.config.mjs
├─ tsconfig.json
├─ .gitignore
├─ playwright.config.ts
├─ scripts/
│  ├─ sync-assets.sh
│  ├─ resolve-manifest.mjs              prints TSV for sync-assets.sh
│  ├─ asset-manifest.json
│  └─ check-i18n-parity.mjs
├─ src/
│  ├─ layouts/
│  │  └─ Layout.astro
│  ├─ components/
│  │  ├─ Nav.astro                      Section 0
│  │  ├─ Hero.astro                     Section 1
│  │  ├─ Loop.astro                     Section 2 (4 sub-cards)
│  │  ├─ Listen.astro                   Section 3
│  │  ├─ Inside.astro                   Section 4
│  │  ├─ Premium.astro                  Section 5
│  │  ├─ Privacy.astro                  Section 6
│  │  ├─ Faq.astro                      Section 7
│  │  ├─ FinalCta.astro                 Section 8
│  │  ├─ Footer.astro                   Section 9
│  │  └─ ui/
│  │     ├─ CornerBrackets.astro
│  │     ├─ OrnamentRule.astro
│  │     ├─ EyebrowLabel.astro
│  │     ├─ WingLogo.astro
│  │     ├─ Wordmark.astro
│  │     ├─ JournalHeadline.astro
│  │     ├─ PlayStoreBadge.astro
│  │     ├─ DeviceFrame.astro
│  │     └─ FaqItem.astro
│  ├─ content/
│  │  ├─ copy.en.json
│  │  └─ copy.sv.json
│  ├─ lib/
│  │  ├─ i18n.ts
│  │  └─ headline.ts                    *word* → tokens parser
│  ├─ pages/
│  │  ├─ index.astro                    EN landing
│  │  └─ sv/
│  │     └─ index.astro                 SV landing
│  ├─ assets/
│  │  ├─ slides/                        marketing screenshots
│  │  └─ screens/                       app screenshots
│  └─ styles/
│     ├─ tokens.css                     CSS variables (Field Journal palette)
│     └─ global.css                     @import tailwindcss + base styles
├─ public/
│  ├─ fonts/                            self-hosted woff2 (6 files)
│  ├─ favicon.svg
│  ├─ apple-touch-icon.png
│  ├─ og.png
│  ├─ robots.txt
│  ├─ play-badge-en.svg
│  ├─ play-badge-sv.svg
│  └─ CNAME                             "birdy.app"
└─ tests/
   └─ smoke.spec.ts                     Playwright (/ + /sv/)

.github/workflows/pages.yml             MODIFIED (add Astro build + assemble)
.gitignore                              MODIFIED (add website/node_modules, dist, .astro)
```

---

## Task 1: Astro project scaffold + Tailwind v4 + .gitignore

**Files:**
- Create: `website/package.json`
- Create: `website/astro.config.mjs`
- Create: `website/tsconfig.json`
- Create: `website/.gitignore`
- Modify: `.gitignore` (project root)

- [ ] **Step 1: Create `website/` and initialize npm**

```bash
mkdir -p website
cd website
npm init -y
```

- [ ] **Step 2: Install Astro 5 + Tailwind v4 + sitemap integration**

Run from `website/`:
```bash
npm install astro@^5 @astrojs/sitemap@^3 @tailwindcss/vite@^4 tailwindcss@^4
npm install -D typescript @types/node
```

- [ ] **Step 3: Write `website/package.json` scripts**

Replace `package.json` contents with:
```json
{
  "name": "birdy-website",
  "type": "module",
  "version": "0.0.0",
  "private": true,
  "scripts": {
    "dev": "astro dev",
    "build": "astro build",
    "preview": "astro preview",
    "check": "astro check",
    "test:i18n": "node scripts/check-i18n-parity.mjs",
    "test:smoke": "playwright test"
  },
  "dependencies": {
    "astro": "^5.0.0",
    "@astrojs/sitemap": "^3.0.0",
    "@tailwindcss/vite": "^4.0.0",
    "tailwindcss": "^4.0.0"
  },
  "devDependencies": {
    "typescript": "^5.0.0",
    "@types/node": "^22.0.0"
  }
}
```

Re-install to lock the file:
```bash
npm install
```

- [ ] **Step 4: Write `website/astro.config.mjs`**

```js
// @ts-check
import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  site: 'https://birdy.app',
  trailingSlash: 'ignore',
  i18n: {
    defaultLocale: 'en',
    locales: ['en', 'sv'],
    routing: {
      prefixDefaultLocale: false,
    },
  },
  integrations: [sitemap()],
  vite: {
    plugins: [tailwindcss()],
  },
});
```

- [ ] **Step 5: Write `website/tsconfig.json`**

```json
{
  "extends": "astro/tsconfigs/strict",
  "include": [".astro/types.d.ts", "**/*"],
  "exclude": ["dist"]
}
```

- [ ] **Step 6: Write `website/.gitignore`**

```
node_modules/
dist/
.astro/
.env
.env.production
.DS_Store
playwright-report/
test-results/
```

- [ ] **Step 7: Update root `.gitignore`**

Read root `.gitignore`, then append:
```
website/node_modules/
website/dist/
website/.astro/
website/playwright-report/
website/test-results/
```

- [ ] **Step 8: Commit**

```bash
git add website/package.json website/package-lock.json website/astro.config.mjs website/tsconfig.json website/.gitignore .gitignore
git commit -m "feat(website/t1): Astro 5 + Tailwind v4 scaffold

- npm init + Astro 5 + @astrojs/sitemap + Tailwind v4 (Vite plugin)
- i18n: en (root) + sv (/sv/ prefix), defaultLocale=en
- TypeScript strict via astro/tsconfigs/strict
- gitignore: website/node_modules, dist, .astro

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 2: Design tokens + global CSS + Tailwind theme

**Files:**
- Create: `website/src/styles/tokens.css`
- Create: `website/src/styles/global.css`

- [ ] **Step 1: Write `website/src/styles/tokens.css`**

Mirrors `composeApp/.../ui/theme/Color.kt` Field Journal palette:
```css
:root {
  /* Paper backgrounds */
  --color-paper-bg: #E8E2D2;
  --color-paper-edge: #E5DCC7;
  --color-paper-top: #EFE7D6;

  /* Moss greens */
  --color-moss-mid: #5C6E48;
  --color-moss-deep: #3F4F30;
  --color-moss-shadow: #2A3520;

  /* Copper (accent / CTA) */
  --color-copper: #A8552D;
  --color-copper-warm: #C9842F;

  /* Text */
  --color-text-primary: #2A3525;
  --color-text-on-hero: #F0EAD8;
  --color-marginalia-ink: #3F4F30;

  /* Stamps */
  --color-stamp-navy: #1F3A5F;

  /* Typography */
  --font-serif: 'DM Serif Display', Georgia, 'Times New Roman', serif;
  --font-script: 'Caveat', 'Brush Script MT', cursive;
  --font-sans: 'Inter', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;

  /* Spacing rhythm */
  --section-padding-y: clamp(4rem, 8vw, 8rem);
  --container-max: 72rem;

  /* Animation */
  --ease-paper: cubic-bezier(0.22, 1, 0.36, 1);
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
}
```

- [ ] **Step 2: Write `website/src/styles/global.css`**

Tailwind v4 with `@theme` directive mapping tokens to Tailwind utilities:
```css
@import "tailwindcss";
@import "./tokens.css";

@theme {
  --color-paper-bg: #E8E2D2;
  --color-paper-edge: #E5DCC7;
  --color-paper-top: #EFE7D6;
  --color-moss-mid: #5C6E48;
  --color-moss-deep: #3F4F30;
  --color-moss-shadow: #2A3520;
  --color-copper: #A8552D;
  --color-copper-warm: #C9842F;
  --color-text-primary: #2A3525;
  --color-text-on-hero: #F0EAD8;
  --color-marginalia-ink: #3F4F30;
  --color-stamp-navy: #1F3A5F;

  --font-serif: 'DM Serif Display', Georgia, serif;
  --font-script: 'Caveat', cursive;
  --font-sans: 'Inter', system-ui, sans-serif;

  --breakpoint-xs: 24rem;
}

.paper-bg {
  background-color: var(--color-paper-bg);
  background-image: radial-gradient(
    circle at 1px 1px,
    rgba(63, 79, 48, 0.06) 1px,
    transparent 0
  );
  background-size: 24px 24px;
}

.corner-bracket {
  position: absolute;
  width: 28px;
  height: 28px;
  border: 2px solid var(--color-copper);
}

.accent-rotate {
  font-family: var(--font-script);
  font-style: italic;
  font-weight: 600;
  color: var(--color-copper);
  display: inline-block;
  transform: rotate(-3deg);
  line-height: 0.95;
}

body {
  font-family: var(--font-sans);
  color: var(--color-text-primary);
  background-color: var(--color-paper-bg);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

h1, h2, h3 {
  font-family: var(--font-serif);
  font-style: italic;
  font-weight: 400;
  line-height: 1.1;
}

a { color: inherit; text-decoration: none; }
```

- [ ] **Step 3: Commit**

```bash
git add website/src/styles/
git commit -m "feat(website/t2): design tokens + Tailwind v4 theme

- tokens.css: Field Journal palette ported from composeApp Color.kt
- global.css: @import tailwindcss + @theme bridging tokens to utilities
- .paper-bg utility with dot-texture (mirrors paperBackground() modifier)
- .corner-bracket + .accent-rotate primitives
- prefers-reduced-motion guard

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 3: Self-hosted fonts (DM Serif Display, Caveat, Inter)

**Files:**
- Create: `website/public/fonts/` (6 woff2 files)
- Modify: `website/src/styles/global.css` (prepend `@font-face` blocks)

- [ ] **Step 1: Create fonts directory and download woff2 subsets**

```bash
mkdir -p website/public/fonts
cd website/public/fonts

curl -L -o dm-serif-display-italic.woff2 \
  "https://cdn.jsdelivr.net/fontsource/fonts/dm-serif-display@latest/latin-400-italic.woff2"
curl -L -o dm-serif-display-regular.woff2 \
  "https://cdn.jsdelivr.net/fontsource/fonts/dm-serif-display@latest/latin-400-normal.woff2"
curl -L -o caveat-regular.woff2 \
  "https://cdn.jsdelivr.net/fontsource/fonts/caveat@latest/latin-400-normal.woff2"
curl -L -o caveat-bold.woff2 \
  "https://cdn.jsdelivr.net/fontsource/fonts/caveat@latest/latin-700-normal.woff2"
curl -L -o inter-regular.woff2 \
  "https://cdn.jsdelivr.net/fontsource/fonts/inter@latest/latin-400-normal.woff2"
curl -L -o inter-semibold.woff2 \
  "https://cdn.jsdelivr.net/fontsource/fonts/inter@latest/latin-600-normal.woff2"

ls -lh
```
Expected: 6 files, each under 30KB.

- [ ] **Step 2: Prepend `@font-face` declarations to global.css**

Edit `website/src/styles/global.css` — insert BEFORE the `@import "tailwindcss";` line:
```css
@font-face {
  font-family: 'DM Serif Display';
  font-style: italic;
  font-weight: 400;
  font-display: swap;
  src: url('/fonts/dm-serif-display-italic.woff2') format('woff2');
}
@font-face {
  font-family: 'DM Serif Display';
  font-style: normal;
  font-weight: 400;
  font-display: swap;
  src: url('/fonts/dm-serif-display-regular.woff2') format('woff2');
}
@font-face {
  font-family: 'Caveat';
  font-style: normal;
  font-weight: 400;
  font-display: swap;
  src: url('/fonts/caveat-regular.woff2') format('woff2');
}
@font-face {
  font-family: 'Caveat';
  font-style: normal;
  font-weight: 700;
  font-display: swap;
  src: url('/fonts/caveat-bold.woff2') format('woff2');
}
@font-face {
  font-family: 'Inter';
  font-style: normal;
  font-weight: 400;
  font-display: swap;
  src: url('/fonts/inter-regular.woff2') format('woff2');
}
@font-face {
  font-family: 'Inter';
  font-style: normal;
  font-weight: 600;
  font-display: swap;
  src: url('/fonts/inter-semibold.woff2') format('woff2');
}
```

- [ ] **Step 3: Commit**

```bash
git add website/public/fonts/ website/src/styles/global.css
git commit -m "feat(website/t3): self-host DM Serif Display, Caveat, Inter (woff2 latin)

- 6 woff2 files from fontsource (latin only, ~25KB each)
- @font-face with font-display: swap
- preload added per-page in Layout.astro (T6)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 4: i18n infrastructure — copy JSON + typed helper + parity check

**Files:**
- Create: `website/src/content/copy.en.json`
- Create: `website/src/content/copy.sv.json`
- Create: `website/src/lib/i18n.ts`
- Create: `website/scripts/check-i18n-parity.mjs`

- [ ] **Step 1: Write `website/src/content/copy.en.json`**

Complete English copy for every section. Uses `*word*` syntax for Caveat-italic accents:
```json
{
  "meta": {
    "title": "Birdy — A field journal for birds",
    "description": "Identify European birds with your phone camera or mic. 839 species. Offline AI. No accounts.",
    "ogLocale": "en_US",
    "htmlLang": "en"
  },
  "nav": {
    "howItWorks": "How it works",
    "inside": "Inside",
    "privacy": "Privacy",
    "faq": "FAQ",
    "switchLang": "SV",
    "switchLangHref": "/sv/",
    "getApp": "Get the app"
  },
  "hero": {
    "eyebrow": "A BIRDY APP · 2026",
    "headline": "A *field journal* that looks like a field journal",
    "sub": "Identify birds with your camera. Keep what you see. Earn the stamps.",
    "scrollCue": "See how it works ↓"
  },
  "loop": {
    "eyebrow": "HOW IT WORKS",
    "headline": "One *loop.*",
    "sub": "Four steps. Every sighting closes the circle.",
    "cards": [
      { "title": "Point", "body": "Open the camera. Aim. The AI guesses three times a second." },
      { "title": "Match", "body": "When the confidence locks, the stamp appears." },
      { "title": "Stamp", "body": "Your journal gets a new page. Same paper, new species." },
      { "title": "Browse", "body": "Flip back any time. Or search 839 European species." }
    ]
  },
  "listen": {
    "eyebrow": "OR HOLD THE MIC · ON-DEVICE",
    "headline": "Hear it. *Name* it.",
    "sub": "Three seconds. The bird names itself.",
    "body": "Hold the record button for 3 seconds. The same on-device AI listens for the bird. Same loop. Same journal. No internet needed."
  },
  "inside": {
    "eyebrow": "INSIDE THE APP",
    "headline": "An *archive* in your pocket.",
    "sub": "every european bird, every stamp, every streak",
    "stats": [
      { "value": "839", "label": "species", "caption": "from common to rare" },
      { "value": "25", "label": "stamps", "caption": "earn them by finding" },
      { "value": "365", "label": "daily streak", "caption": "keep the journal alive" }
    ]
  },
  "premium": {
    "eyebrow": "FIELD MEMBER · OPTIONAL",
    "headline": "*Free* first.",
    "sub": "Premium goes deeper — for the curious few.",
    "body": "The app is free. Always will be the core experience. Premium adds PDF-export, seasonal statistics, and 10 field marks for the obsessive collectors.",
    "cta": "See what's inside →",
    "ctaHref": "https://play.google.com/store/apps/details?id=se.birdy.android"
  },
  "privacy": {
    "eyebrow": "PRIVACY · NO PHOTOS LEAVE YOUR PHONE",
    "headline": "Fully *offline.*",
    "sub": "the AI lives in your phone",
    "body": "No cloud. No tracking. No accounts. Just you and the birds.",
    "wordmarkLabel": "AVAILABLE ON GOOGLE PLAY"
  },
  "faq": {
    "eyebrow": "QUESTIONS · ANSWERED",
    "headline": "Before you *download.*",
    "items": [
      { "q": "Does it really work offline?", "a": "Yes. The AI runs entirely on your phone — both for camera and audio. No data leaves the device." },
      { "q": "Do I need an account?", "a": "No. Open the app, point the camera, you're scanning. Your journal lives on the phone." },
      { "q": "Where are my photos stored?", "a": "On your device. Birdy never uploads photos to a server. There is no server." },
      { "q": "How accurate is the AI?", "a": "Top-3 accuracy ~72% on European species. Field-verified on a Galaxy S23 Ultra. Confidence shown for every match." },
      { "q": "What's covered?", "a": "839 European birds — every species in the EBBA2 atlas plus close relatives, with descriptions, photos and range information." },
      { "q": "Is there an iOS version?", "a": "Not yet. Android first. iOS comes after launch if there is demand." },
      { "q": "What about audio?", "a": "Hold the mic button for 3 seconds. BirdNET-Lite runs on-device, same offline guarantee." },
      { "q": "Other questions?", "a": "Email feedback@birdy.app — I read every one." }
    ]
  },
  "finalCta": {
    "headline": "Get it on *Google Play.*",
    "sub": "free · no account · offline"
  },
  "footer": {
    "links": [
      { "label": "Privacy", "href": "/privacy.html" },
      { "label": "Terms", "href": "/terms.html" },
      { "label": "Email", "href": "mailto:feedback@birdy.app" },
      { "label": "SV", "href": "/sv/" }
    ],
    "credit": "Made by Albin · Sweden · 2026 · v1.0.0"
  },
  "alt": {
    "wingLogo": "Birdy wing logo",
    "wordmark": "Birdy wordmark",
    "playStoreBadge": "Get it on Google Play",
    "scanScreen": "Birdy scan screen showing a live camera identifying a bird",
    "matchScreen": "Birdy match screen showing a confirmed species with stamp",
    "observationDetail": "Birdy observation detail page in field-journal style",
    "archiveScreen": "Birdy archive screen browsing European species",
    "audioScan": "Birdy audio scan screen with waveform",
    "badgesGrid": "Birdy badges page with 5x5 stamp grid"
  }
}
```

- [ ] **Step 2: Write `website/src/content/copy.sv.json` (placeholder with same keys)**

Swedish copy with every key present (user refines wording later):
```json
{
  "meta": {
    "title": "Birdy — En fältdagbok för fåglar",
    "description": "Identifiera europeiska fåglar med kamera eller mikrofon. 839 arter. Offline-AI. Inga konton.",
    "ogLocale": "sv_SE",
    "htmlLang": "sv"
  },
  "nav": {
    "howItWorks": "Så fungerar det",
    "inside": "Innehåll",
    "privacy": "Integritet",
    "faq": "FAQ",
    "switchLang": "EN",
    "switchLangHref": "/",
    "getApp": "Hämta appen"
  },
  "hero": {
    "eyebrow": "EN BIRDY-APP · 2026",
    "headline": "En *fältdagbok* som ser ut som en fältdagbok",
    "sub": "Identifiera fåglar med kameran. Spara det du ser. Samla stämplarna.",
    "scrollCue": "Så fungerar det ↓"
  },
  "loop": {
    "eyebrow": "SÅ FUNGERAR DET",
    "headline": "En *loop.*",
    "sub": "Fyra steg. Varje fynd sluter cirkeln.",
    "cards": [
      { "title": "Sikta", "body": "Öppna kameran. Sikta. AI:n gissar tre gånger per sekund." },
      { "title": "Träff", "body": "När säkerheten låser sig dyker stämpeln upp." },
      { "title": "Stämpla", "body": "Din dagbok får en ny sida. Samma papper, ny art." },
      { "title": "Bläddra", "body": "Gå tillbaka när du vill. Eller sök bland 839 europeiska arter." }
    ]
  },
  "listen": {
    "eyebrow": "ELLER HÅLL NER MIKEN · PÅ ENHETEN",
    "headline": "Hör den. *Namnge* den.",
    "sub": "Tre sekunder. Fågeln namnger sig själv.",
    "body": "Håll inspelningsknappen i 3 sekunder. Samma AI på enheten lyssnar efter fågeln. Samma loop. Samma dagbok. Inget internet behövs."
  },
  "inside": {
    "eyebrow": "INUTI APPEN",
    "headline": "Ett *arkiv* i fickan.",
    "sub": "varje europeisk fågel, varje stämpel, varje serie",
    "stats": [
      { "value": "839", "label": "arter", "caption": "från vanliga till sällsynta" },
      { "value": "25", "label": "stämplar", "caption": "tjänas in genom fynd" },
      { "value": "365", "label": "daglig serie", "caption": "håll dagboken levande" }
    ]
  },
  "premium": {
    "eyebrow": "FÄLTMEDLEM · VALFRITT",
    "headline": "*Gratis* först.",
    "sub": "Premium går djupare — för dom mest nyfikna.",
    "body": "Appen är gratis. Kärnupplevelsen kommer alltid att vara det. Premium lägger till PDF-export, säsongsstatistik och 10 fältmärken för obsessiva samlare.",
    "cta": "Se vad som finns →",
    "ctaHref": "https://play.google.com/store/apps/details?id=se.birdy.android"
  },
  "privacy": {
    "eyebrow": "INTEGRITET · INGA FOTON LÄMNAR DIN TELEFON",
    "headline": "Helt *offline.*",
    "sub": "AI:n bor i din telefon",
    "body": "Inget moln. Ingen spårning. Inga konton. Bara du och fåglarna.",
    "wordmarkLabel": "FINNS PÅ GOOGLE PLAY"
  },
  "faq": {
    "eyebrow": "FRÅGOR · BESVARADE",
    "headline": "Innan du *laddar ner.*",
    "items": [
      { "q": "Funkar den verkligen offline?", "a": "Ja. AI:n körs helt på din telefon — både för kamera och ljud. Ingen data lämnar enheten." },
      { "q": "Behöver jag ett konto?", "a": "Nej. Öppna appen, rikta kameran, du skannar. Din dagbok bor på telefonen." },
      { "q": "Var lagras mina foton?", "a": "På din enhet. Birdy laddar aldrig upp foton till en server. Det finns ingen server." },
      { "q": "Hur exakt är AI:n?", "a": "Top-3-träffsäkerhet ~72% på europeiska arter. Fältverifierat på en Galaxy S23 Ultra. Säkerhet visas för varje träff." },
      { "q": "Vad ingår?", "a": "839 europeiska fåglar — varje art i EBBA2-atlasen plus nära släktingar, med beskrivningar, foton och utbredningsinformation." },
      { "q": "Finns en iOS-version?", "a": "Inte än. Android först. iOS kommer efter lansering om det finns efterfrågan." },
      { "q": "Hur funkar ljudet?", "a": "Håll mikrofonknappen i 3 sekunder. BirdNET-Lite körs på enheten, samma offline-garanti." },
      { "q": "Andra frågor?", "a": "Mejla feedback@birdy.app — jag läser varje meddelande." }
    ]
  },
  "finalCta": {
    "headline": "Hämta den på *Google Play.*",
    "sub": "gratis · inget konto · offline"
  },
  "footer": {
    "links": [
      { "label": "Integritet", "href": "/privacy.html" },
      { "label": "Villkor", "href": "/terms.html" },
      { "label": "E-post", "href": "mailto:feedback@birdy.app" },
      { "label": "EN", "href": "/" }
    ],
    "credit": "Skapad av Albin · Sverige · 2026 · v1.0.0"
  },
  "alt": {
    "wingLogo": "Birdys vinge-logo",
    "wordmark": "Birdy-ordmärke",
    "playStoreBadge": "Hämta den på Google Play",
    "scanScreen": "Birdys scan-skärm med en live kamera som identifierar en fågel",
    "matchScreen": "Birdys träff-skärm med en bekräftad art och stämpel",
    "observationDetail": "Birdys observationsdetaljsida i fältdagbok-stil",
    "archiveScreen": "Birdys arkiv-skärm med europeiska arter",
    "audioScan": "Birdys ljudscan-skärm med vågform",
    "badgesGrid": "Birdys märkessida med 5x5 stämpel-rutnät"
  }
}
```

- [ ] **Step 3: Write `website/src/lib/i18n.ts` (typed helper)**

```ts
import enCopy from '../content/copy.en.json';
import svCopy from '../content/copy.sv.json';

export type Locale = 'en' | 'sv';
export type Copy = typeof enCopy;

const copyByLocale: Record<Locale, Copy> = {
  en: enCopy as Copy,
  sv: svCopy as Copy,
};

export function getCopy(locale: Locale): Copy {
  return copyByLocale[locale];
}

export function getLocaleFromUrl(url: URL): Locale {
  return url.pathname.startsWith('/sv') ? 'sv' : 'en';
}

export function alternateHref(currentLocale: Locale, currentPath: string): string {
  if (currentLocale === 'en') {
    return currentPath === '/' ? '/sv/' : `/sv${currentPath}`;
  }
  return currentPath.replace(/^\/sv/, '') || '/';
}
```

- [ ] **Step 4: Write `website/scripts/check-i18n-parity.mjs` (parity check)**

```js
#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = resolve(__dirname, '..');

const en = JSON.parse(readFileSync(resolve(root, 'src/content/copy.en.json'), 'utf8'));
const sv = JSON.parse(readFileSync(resolve(root, 'src/content/copy.sv.json'), 'utf8'));

function collectKeys(obj, prefix = '') {
  const keys = [];
  for (const [k, v] of Object.entries(obj)) {
    const path = prefix ? `${prefix}.${k}` : k;
    if (v && typeof v === 'object' && !Array.isArray(v)) {
      keys.push(...collectKeys(v, path));
    } else if (Array.isArray(v)) {
      keys.push(`${path}[len=${v.length}]`);
      v.forEach((item, i) => {
        if (item && typeof item === 'object') {
          keys.push(...collectKeys(item, `${path}[${i}]`));
        }
      });
    } else {
      keys.push(path);
    }
  }
  return keys.sort();
}

const enKeys = collectKeys(en);
const svKeys = collectKeys(sv);
const enSet = new Set(enKeys);
const svSet = new Set(svKeys);

const missingInSv = enKeys.filter((k) => !svSet.has(k));
const missingInEn = svKeys.filter((k) => !enSet.has(k));

if (missingInSv.length === 0 && missingInEn.length === 0) {
  console.log(`i18n parity OK (${enKeys.length} keys)`);
  process.exit(0);
}

console.error('i18n parity FAILED');
if (missingInSv.length) {
  console.error('  Missing in sv:');
  missingInSv.forEach((k) => console.error(`    - ${k}`));
}
if (missingInEn.length) {
  console.error('  Missing in en:');
  missingInEn.forEach((k) => console.error(`    - ${k}`));
}
process.exit(1);
```

- [ ] **Step 5: Run parity check**

```bash
cd website
node scripts/check-i18n-parity.mjs
```
Expected: `i18n parity OK (NN keys)` and exit 0.

- [ ] **Step 6: Commit**

```bash
git add website/src/content/ website/src/lib/i18n.ts website/scripts/check-i18n-parity.mjs
git commit -m "feat(website/t4): i18n infrastructure — copy JSON + typed helper + parity check

- copy.en.json + copy.sv.json with full landing-page copy for 9 sections
- *word* syntax preserved for Caveat-italic accent rendering (parser in T8)
- src/lib/i18n.ts: getCopy(locale), getLocaleFromUrl, alternateHref
- scripts/check-i18n-parity.mjs: recursive key-walker; exits 1 on drift
- Resolves spec open questions #1 (domain birdy.app) and #4 (feedback@birdy.app)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 5: Asset sync script + manifest

**Files:**
- Create: `website/scripts/asset-manifest.json`
- Create: `website/scripts/resolve-manifest.mjs`
- Create: `website/scripts/sync-assets.sh`
- Create: `website/src/assets/slides/.gitkeep`
- Create: `website/src/assets/screens/.gitkeep`
- Created by script: `website/public/og.png`, `website/public/favicon.svg`, `website/public/apple-touch-icon.png`, `website/src/assets/...`
- Create: `website/public/robots.txt`

- [ ] **Step 1: Write `website/scripts/asset-manifest.json`**

Maps Desktop source paths → repo destination paths.

```json
{
  "_comment": "Source paths use Windows-bash format. Sync script normalizes per OS.",
  "sources": {
    "playStoreSlidesEn": "C:/Users/abbea/Desktop/Birdy AB/Birdy Play Store/Play Store English",
    "playStoreSlidesSv": "C:/Users/abbea/Desktop/Birdy AB/Birdy Play Store/Play Store Swedish",
    "brandAssets": "C:/Users/abbea/Desktop/Birdy AB/Birdy Play Store/Birdy Play store English"
  },
  "files": [
    { "from": "brandAssets/birdy-og-card-1200x630.png", "to": "public/og.png" },
    { "from": "brandAssets/birdy-logo-paper.svg", "to": "public/favicon.svg" },
    { "from": "brandAssets/birdy-logo-paper.svg", "to": "src/assets/wing-logo-paper.svg" },
    { "from": "brandAssets/birdy-logo-transparent.svg", "to": "src/assets/wing-logo-transparent.svg" },
    { "from": "brandAssets/birdy-wordmark-2048-transparent.png", "to": "src/assets/wordmark-2048.png" },
    { "from": "brandAssets/birdy-square-1024-paper.png", "to": "public/apple-touch-icon-src.png" },
    { "from": "playStoreSlidesEn/birdy-screenshot-01-EN.png", "to": "src/assets/slides/slide-01-en.png" },
    { "from": "playStoreSlidesEn/birdy-screenshot-02-EN.png", "to": "src/assets/slides/slide-02-en.png" },
    { "from": "playStoreSlidesEn/birdy-screenshot-03-EN.png", "to": "src/assets/slides/slide-03-en.png" },
    { "from": "playStoreSlidesEn/birdy-screenshot-04-EN.png", "to": "src/assets/slides/slide-04-en.png" },
    { "from": "playStoreSlidesEn/birdy-screenshot-05-EN.png", "to": "src/assets/slides/slide-05-en.png" }
  ]
}
```

- [ ] **Step 2: Write `website/scripts/resolve-manifest.mjs` (TSV emitter)**

A small standalone helper that the shell script calls. It only reads its own manifest file (no shell-variable interpolation into JS source).

```js
#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const manifestPath = resolve(__dirname, 'asset-manifest.json');
const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));

for (const file of manifest.files) {
  const [rootKey, ...rest] = file.from.split('/');
  const sourceRoot = manifest.sources[rootKey];
  if (!sourceRoot) {
    console.error(`Unknown source root: ${rootKey}`);
    process.exit(1);
  }
  const sourceRel = rest.join('/');
  process.stdout.write(`${sourceRel}\t${file.to}\t${sourceRoot}\n`);
}
```

- [ ] **Step 3: Write `website/scripts/sync-assets.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WEBSITE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

copied=0
skipped=0
missing=0

while IFS=$'\t' read -r src_rel dst_rel src_root; do
  src="$src_root/$src_rel"
  dst="$WEBSITE_DIR/$dst_rel"

  if [ ! -f "$src" ]; then
    echo "  MISS  $src"
    missing=$((missing + 1))
    continue
  fi

  mkdir -p "$(dirname "$dst")"

  if [ -f "$dst" ] && [ "$src" -ot "$dst" ]; then
    skipped=$((skipped + 1))
    continue
  fi

  cp "$src" "$dst"
  echo "  COPY  $src_rel -> $dst_rel"
  copied=$((copied + 1))
done < <(node "$SCRIPT_DIR/resolve-manifest.mjs")

echo ""
echo "Sync complete: $copied copied, $skipped skipped, $missing missing"
[ "$missing" -gt 0 ] && exit 1
exit 0
```

Make executable:
```bash
chmod +x website/scripts/sync-assets.sh
```

- [ ] **Step 4: Run the sync script**

```bash
cd website
./scripts/sync-assets.sh
```
Expected: `Sync complete: 11 copied, 0 skipped, 0 missing` (or note any source files missing on Desktop).

- [ ] **Step 5: Rename apple-touch-icon source**

The source PNG is 1024×1024; browsers downscale fine. Rename:
```bash
mv website/public/apple-touch-icon-src.png website/public/apple-touch-icon.png
```

- [ ] **Step 6: Write `website/public/robots.txt`**

```
User-agent: *
Allow: /

Sitemap: https://birdy.app/sitemap-index.xml
```

- [ ] **Step 7: Create .gitkeep markers for empty asset directories**

```bash
touch website/src/assets/slides/.gitkeep
touch website/src/assets/screens/.gitkeep
```

- [ ] **Step 8: Commit synced assets + scripts**

```bash
git add website/scripts/ website/src/assets/ website/public/og.png website/public/favicon.svg website/public/apple-touch-icon.png website/public/robots.txt
git commit -m "feat(website/t5): asset sync-script + initial marketing assets

- asset-manifest.json maps Desktop source paths to repo destinations
- resolve-manifest.mjs: standalone Node helper, prints TSV (no shell
  variable interpolation into JS source)
- sync-assets.sh: idempotent (mtime check), reports copied/skipped/missing
- Initial sync: 5 EN marketing slides, brand SVGs, OG card, favicon,
  apple-touch-icon, wordmark
- robots.txt allows all + points to /sitemap-index.xml

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 6: Layout.astro — base HTML head, meta, OG, hreflang, JSON-LD

**Files:**
- Create: `website/src/layouts/Layout.astro`

- [ ] **Step 1: Write `website/src/layouts/Layout.astro`**

```astro
---
import '../styles/global.css';
import { getCopy, alternateHref, type Locale } from '../lib/i18n';

interface Props {
  locale: Locale;
  pathname: string;
}

const { locale, pathname } = Astro.props;
const copy = getCopy(locale);
const altLocale: Locale = locale === 'en' ? 'sv' : 'en';
const altPath = alternateHref(locale, pathname);
const canonical = new URL(pathname, Astro.site).toString();
const ogImageUrl = new URL('/og.png', Astro.site).toString();

const jsonLd = {
  '@context': 'https://schema.org',
  '@type': 'MobileApplication',
  name: 'Birdy',
  operatingSystem: 'Android',
  applicationCategory: 'LifestyleApplication',
  downloadUrl: 'https://play.google.com/store/apps/details?id=se.birdy.android',
  offers: { '@type': 'Offer', price: '0', priceCurrency: 'SEK' },
  inLanguage: ['en', 'sv'],
  image: ogImageUrl,
};
---

<!doctype html>
<html lang={copy.meta.htmlLang}>
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <meta name="generator" content={Astro.generator} />

    <title>{copy.meta.title}</title>
    <meta name="description" content={copy.meta.description} />

    <link rel="canonical" href={canonical} />
    <link rel="alternate" hreflang={locale} href={canonical} />
    <link
      rel="alternate"
      hreflang={altLocale}
      href={new URL(altPath, Astro.site).toString()}
    />
    <link rel="alternate" hreflang="x-default" href={new URL('/', Astro.site).toString()} />

    <link rel="icon" type="image/svg+xml" href="/favicon.svg" />
    <link rel="apple-touch-icon" sizes="180x180" href="/apple-touch-icon.png" />

    <link rel="preload" href="/fonts/dm-serif-display-italic.woff2" as="font" type="font/woff2" crossorigin />
    <link rel="preload" href="/fonts/caveat-regular.woff2" as="font" type="font/woff2" crossorigin />
    <link rel="preload" href="/fonts/inter-regular.woff2" as="font" type="font/woff2" crossorigin />

    <meta property="og:type" content="website" />
    <meta property="og:locale" content={copy.meta.ogLocale} />
    <meta property="og:title" content={copy.meta.title} />
    <meta property="og:description" content={copy.meta.description} />
    <meta property="og:url" content={canonical} />
    <meta property="og:image" content={ogImageUrl} />
    <meta property="og:image:width" content="1200" />
    <meta property="og:image:height" content="630" />
    <meta property="og:site_name" content="Birdy" />

    <meta name="twitter:card" content="summary_large_image" />
    <meta name="twitter:title" content={copy.meta.title} />
    <meta name="twitter:description" content={copy.meta.description} />
    <meta name="twitter:image" content={ogImageUrl} />

    <meta name="theme-color" content="#E8E2D2" />

    <script type="application/ld+json" set:html={JSON.stringify(jsonLd)} />
  </head>
  <body class="paper-bg">
    <slot />
  </body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add website/src/layouts/Layout.astro
git commit -m "feat(website/t6): Layout.astro with meta, OG, hreflang, JSON-LD

- per-locale <title>, description, og:locale, html lang
- canonical + hreflang (en, sv, x-default) from Astro.site
- 3 preload links for LCP-critical fonts
- OG card 1200x630 + Twitter summary_large_image
- JSON-LD MobileApplication schema (price=0 SEK, downloadUrl)
- theme-color matches paper-bg

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 7: UI primitives — CornerBrackets, OrnamentRule, EyebrowLabel, WingLogo, Wordmark

**Files:**
- Create: `website/src/components/ui/CornerBrackets.astro`
- Create: `website/src/components/ui/OrnamentRule.astro`
- Create: `website/src/components/ui/EyebrowLabel.astro`
- Create: `website/src/components/ui/WingLogo.astro`
- Create: `website/src/components/ui/Wordmark.astro`

- [ ] **Step 1: `CornerBrackets.astro`**

```astro
---
interface Props {
  color?: string;
  size?: string;
}
const { color = 'var(--color-copper)', size = '28px' } = Astro.props;
---

<div class="pointer-events-none absolute inset-0" aria-hidden="true">
  <span class="bracket bracket-tl" style={`--c:${color};--s:${size}`}></span>
  <span class="bracket bracket-tr" style={`--c:${color};--s:${size}`}></span>
  <span class="bracket bracket-bl" style={`--c:${color};--s:${size}`}></span>
  <span class="bracket bracket-br" style={`--c:${color};--s:${size}`}></span>
</div>

<style>
  .bracket {
    position: absolute;
    width: var(--s);
    height: var(--s);
  }
  .bracket-tl { top: 24px; left: 24px; border-top: 2px solid var(--c); border-left: 2px solid var(--c); }
  .bracket-tr { top: 24px; right: 24px; border-top: 2px solid var(--c); border-right: 2px solid var(--c); }
  .bracket-bl { bottom: 24px; left: 24px; border-bottom: 2px solid var(--c); border-left: 2px solid var(--c); }
  .bracket-br { bottom: 24px; right: 24px; border-bottom: 2px solid var(--c); border-right: 2px solid var(--c); }
</style>
```

- [ ] **Step 2: `OrnamentRule.astro`**

```astro
---
interface Props {
  width?: string;
}
const { width = '180px' } = Astro.props;
---

<div class="ornament" style={`--w:${width}`} aria-hidden="true">
  <span class="line"></span>
  <span class="glyph">❦</span>
  <span class="line"></span>
</div>

<style>
  .ornament {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    color: var(--color-marginalia-ink);
    margin: 1rem auto;
  }
  .line {
    height: 1px;
    width: var(--w);
    background: var(--color-marginalia-ink);
    opacity: 0.4;
  }
  .glyph {
    font-family: var(--font-serif);
    font-size: 1.25rem;
    opacity: 0.6;
  }
</style>
```

- [ ] **Step 3: `EyebrowLabel.astro`**

```astro
---
interface Props {
  text: string;
  color?: string;
}
const { text, color = 'var(--color-copper)' } = Astro.props;
---

<div class="eyebrow" style={`--c:${color}`}>
  {text}
</div>

<style>
  .eyebrow {
    font-family: var(--font-sans);
    font-size: 0.75rem;
    text-transform: uppercase;
    letter-spacing: 0.12em;
    color: var(--c);
    font-weight: 600;
  }
</style>
```

- [ ] **Step 4: `WingLogo.astro`**

```astro
---
interface Props {
  variant?: 'paper' | 'transparent';
  size?: string;
  alt: string;
  class?: string;
}
const { variant = 'paper', size = '48px', alt, class: cls = '' } = Astro.props;
const src = variant === 'paper' ? '/favicon.svg' : '/favicon.svg';
---

<img
  src={src}
  alt={alt}
  width={size.replace('px', '')}
  height={size.replace('px', '')}
  style={`width:${size};height:${size}`}
  class={cls}
  loading="lazy"
  decoding="async"
/>
```

Both variants currently point to `/favicon.svg`; when a transparent SVG is added under `public/` later, switch one branch of the ternary.

- [ ] **Step 5: `Wordmark.astro`**

```astro
---
interface Props {
  size?: string;
  color?: string;
  alt: string;
  class?: string;
}
const { size = '120px', color = 'var(--color-copper)', alt, class: cls = '' } = Astro.props;
---

<span class={`wordmark ${cls}`} style={`--w:${size};--c:${color}`} aria-label={alt}>
  Birdy<span class="dot">.</span>
</span>

<style>
  .wordmark {
    font-family: var(--font-script);
    font-weight: 700;
    color: var(--c);
    font-size: var(--w);
    line-height: 1;
    display: inline-block;
  }
  .dot { color: var(--c); }
</style>
```

- [ ] **Step 6: Commit**

```bash
git add website/src/components/ui/CornerBrackets.astro website/src/components/ui/OrnamentRule.astro website/src/components/ui/EyebrowLabel.astro website/src/components/ui/WingLogo.astro website/src/components/ui/Wordmark.astro
git commit -m "feat(website/t7): UI primitives — brackets, ornament, eyebrow, wing-logo, wordmark

- CornerBrackets: 4x copper L-shapes positioned absolute in parent
- OrnamentRule: --- ❦ --- divider, marginalia-ink
- EyebrowLabel: UPPERCASE Inter with letter-spacing 0.12em
- WingLogo: <img> to /favicon.svg with variant + size + alt props
- Wordmark: Caveat 'Birdy.' inline span with --w/--c CSS vars

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 8: JournalHeadline parser + component

**Files:**
- Create: `website/src/lib/headline.ts`
- Create: `website/src/components/ui/JournalHeadline.astro`

- [ ] **Step 1: Write `website/src/lib/headline.ts` (parser)**

Mirrors the Kotlin `JournalHeadline` parser in `composeApp/.../ui/components/JournalHeadline.kt`.
```ts
export type Token =
  | { kind: 'plain'; text: string }
  | { kind: 'accent'; text: string };

/**
 * Parse "A *word* and more" into tokens.
 * - `*word*` becomes an accent token (Caveat-italic, rotated)
 * - Plain text becomes plain tokens
 * - Asymmetric asterisks are kept as literal text
 */
export function parseHeadline(input: string): Token[] {
  const tokens: Token[] = [];
  const re = /\*([^*]+)\*/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  while ((match = re.exec(input)) !== null) {
    if (match.index > lastIndex) {
      tokens.push({ kind: 'plain', text: input.slice(lastIndex, match.index) });
    }
    tokens.push({ kind: 'accent', text: match[1] });
    lastIndex = re.lastIndex;
  }
  if (lastIndex < input.length) {
    tokens.push({ kind: 'plain', text: input.slice(lastIndex) });
  }
  if (tokens.length === 0) {
    tokens.push({ kind: 'plain', text: input });
  }
  return tokens;
}
```

- [ ] **Step 2: Write `website/src/components/ui/JournalHeadline.astro`**

```astro
---
import { parseHeadline } from '../../lib/headline';

interface Props {
  text: string;
  level?: 'h1' | 'h2' | 'h3';
  size?: string;
  align?: 'left' | 'center';
  class?: string;
}

const {
  text,
  level = 'h2',
  size = 'clamp(2rem, 5vw, 3.5rem)',
  align = 'center',
  class: cls = '',
} = Astro.props;

const tokens = parseHeadline(text);
const Tag = level;
---

<Tag class={`journal-headline ${cls}`} style={`--size:${size};text-align:${align}`}>
  {tokens.map((t) =>
    t.kind === 'plain' ? (
      <span class="plain">{t.text}</span>
    ) : (
      <span class="accent">{t.text}</span>
    ),
  )}
</Tag>

<style>
  .journal-headline {
    font-family: var(--font-serif);
    font-style: italic;
    font-weight: 400;
    font-size: var(--size);
    line-height: 1.05;
    color: var(--color-text-primary);
    margin: 0;
  }
  .plain { font-family: var(--font-serif); font-style: italic; }
  .accent {
    font-family: var(--font-script);
    font-style: italic;
    font-weight: 700;
    color: var(--color-copper);
    display: inline-block;
    transform: rotate(-3deg);
    margin: 0 0.15em;
    line-height: 0.95;
  }
</style>
```

- [ ] **Step 3: Commit**

```bash
git add website/src/lib/headline.ts website/src/components/ui/JournalHeadline.astro
git commit -m "feat(website/t8): JournalHeadline parser + component

- lib/headline.ts: parseHeadline('A *word* here') -> Token[]
- JournalHeadline.astro: plain = DM Serif Italic, accent = Caveat-italic 700 + copper + rotate(-3deg)
- props: text, level (h1/h2/h3), size (CSS), align, class

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 9: UI primitives — PlayStoreBadge, DeviceFrame, FaqItem

**Files:**
- Create: `website/src/components/ui/PlayStoreBadge.astro`
- Create: `website/src/components/ui/DeviceFrame.astro`
- Create: `website/src/components/ui/FaqItem.astro`
- Create: `website/public/play-badge-en.svg`
- Create: `website/public/play-badge-sv.svg`

- [ ] **Step 1: Write `website/public/play-badge-en.svg` and `play-badge-sv.svg`**

These are PLACEHOLDER simplified badges sufficient for layout. Replace with official Google partner badges from `play.google.com/intl/en_us/badges/` pre-launch (tracked as follow-up #1).

`website/public/play-badge-en.svg`:
```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 180 56" role="img" aria-label="Get it on Google Play">
  <rect width="180" height="56" rx="8" fill="#000"/>
  <g transform="translate(14,11)">
    <path fill="#fff" d="M3 0v34l17-17z" opacity=".9"/>
    <path fill="#ea4335" d="M20 17l-9-9v18z" opacity=".95"/>
    <path fill="#4285f4" d="M3 0l17 17-9 9z" opacity=".9"/>
    <path fill="#fbbc05" d="M11 8l9 9-9 9z" opacity=".9"/>
  </g>
  <text x="50" y="22" fill="#fff" font-family="Inter, Arial, sans-serif" font-size="9" letter-spacing="1">GET IT ON</text>
  <text x="50" y="42" fill="#fff" font-family="Inter, Arial, sans-serif" font-size="17" font-weight="600">Google Play</text>
</svg>
```

`website/public/play-badge-sv.svg`:
```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 180 56" role="img" aria-label="Hämta den på Google Play">
  <rect width="180" height="56" rx="8" fill="#000"/>
  <g transform="translate(14,11)">
    <path fill="#fff" d="M3 0v34l17-17z" opacity=".9"/>
    <path fill="#ea4335" d="M20 17l-9-9v18z" opacity=".95"/>
    <path fill="#4285f4" d="M3 0l17 17-9 9z" opacity=".9"/>
    <path fill="#fbbc05" d="M11 8l9 9-9 9z" opacity=".9"/>
  </g>
  <text x="50" y="22" fill="#fff" font-family="Inter, Arial, sans-serif" font-size="9" letter-spacing="1">HÄMTA DEN PÅ</text>
  <text x="50" y="42" fill="#fff" font-family="Inter, Arial, sans-serif" font-size="17" font-weight="600">Google Play</text>
</svg>
```

- [ ] **Step 2: Write `website/src/components/ui/PlayStoreBadge.astro`**

```astro
---
import { type Locale } from '../../lib/i18n';

interface Props {
  locale: Locale;
  href: string;
  alt: string;
  size?: 'small' | 'large';
  class?: string;
}
const { locale, href, alt, size = 'large', class: cls = '' } = Astro.props;
const src = locale === 'sv' ? '/play-badge-sv.svg' : '/play-badge-en.svg';
const w = size === 'large' ? 220 : 160;
const h = size === 'large' ? 68 : 50;
---

<a href={href} class={`badge ${cls}`} rel="noopener" target="_blank">
  <img src={src} alt={alt} width={w} height={h} loading="lazy" decoding="async" />
</a>

<style>
  .badge {
    display: inline-block;
    line-height: 0;
    transition: transform 0.2s var(--ease-paper);
  }
  .badge:hover { transform: translateY(-2px); }
  .badge img { display: block; }
</style>
```

- [ ] **Step 3: Write `website/src/components/ui/DeviceFrame.astro`**

```astro
---
interface Props {
  caption?: string;
  class?: string;
}
const { caption, class: cls = '' } = Astro.props;
---

<figure class={`device ${cls}`}>
  <div class="bezel">
    <div class="screen">
      <slot />
    </div>
  </div>
  {caption && <figcaption>{caption}</figcaption>}
</figure>

<style>
  .device {
    margin: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.5rem;
  }
  .bezel {
    padding: 8px;
    background: linear-gradient(180deg, #2a2620 0%, #1a1612 100%);
    border-radius: 36px;
    box-shadow:
      0 1px 0 rgba(255, 255, 255, 0.08) inset,
      0 30px 60px -20px rgba(0, 0, 0, 0.45),
      0 4px 12px rgba(0, 0, 0, 0.18);
  }
  .screen {
    overflow: hidden;
    border-radius: 28px;
    background: var(--color-paper-bg);
    aspect-ratio: 9 / 19.5;
    width: clamp(180px, 26vw, 280px);
    position: relative;
  }
  .screen :global(img) {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
  figcaption {
    font-family: var(--font-script);
    font-size: 1rem;
    color: var(--color-marginalia-ink);
  }
</style>
```

- [ ] **Step 4: Write `website/src/components/ui/FaqItem.astro`**

```astro
---
interface Props {
  question: string;
  answer: string;
}
const { question, answer } = Astro.props;
---

<details class="faq-item">
  <summary>
    <span class="q">{question}</span>
    <span class="toggle" aria-hidden="true">+</span>
  </summary>
  <div class="a">{answer}</div>
</details>

<style>
  .faq-item {
    border-bottom: 1px solid rgba(63, 79, 48, 0.18);
    padding: 1.25rem 0;
  }
  .faq-item summary {
    list-style: none;
    cursor: pointer;
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 1.5rem;
  }
  .faq-item summary::-webkit-details-marker { display: none; }
  .q {
    font-family: var(--font-script);
    font-size: 1.5rem;
    color: var(--color-text-primary);
    font-weight: 600;
    line-height: 1.2;
  }
  .toggle {
    font-family: var(--font-serif);
    font-size: 1.5rem;
    color: var(--color-copper);
    width: 1.5rem;
    text-align: center;
    transition: transform 0.2s var(--ease-paper);
  }
  .faq-item[open] .toggle { transform: rotate(45deg); }
  .a {
    margin-top: 0.75rem;
    font-family: var(--font-sans);
    font-size: 1rem;
    line-height: 1.6;
    color: var(--color-text-primary);
    max-width: 60ch;
  }
</style>
```

- [ ] **Step 5: Commit**

```bash
git add website/src/components/ui/PlayStoreBadge.astro website/src/components/ui/DeviceFrame.astro website/src/components/ui/FaqItem.astro website/public/play-badge-en.svg website/public/play-badge-sv.svg
git commit -m "feat(website/t9): PlayStoreBadge + DeviceFrame + FaqItem primitives

- PlayStoreBadge: locale-aware SVG, opens Play in new tab, hover lift
- DeviceFrame: dark bezel + paper-bg screen, 9/19.5 aspect, Caveat caption
- FaqItem: native <details>/<summary>, copper +/x toggle rotates 45deg [open]
- Play badges are placeholder SVGs; swap for official Google partner badges pre-launch

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 10: Nav.astro — sticky top nav (Section 0)

**Files:**
- Create: `website/src/components/Nav.astro`

- [ ] **Step 1: Write `website/src/components/Nav.astro`**

```astro
---
import Wordmark from './ui/Wordmark.astro';
import PlayStoreBadge from './ui/PlayStoreBadge.astro';
import { type Locale, getCopy } from '../lib/i18n';

interface Props {
  locale: Locale;
}
const { locale } = Astro.props;
const t = getCopy(locale);
const playUrl = 'https://play.google.com/store/apps/details?id=se.birdy.android';
---

<nav class="nav" aria-label="Primary">
  <a href={locale === 'sv' ? '/sv/' : '/'} class="brand" aria-label="Birdy">
    <Wordmark size="36px" alt={t.alt.wordmark} />
  </a>

  <ul class="links" role="list">
    <li><a href="#loop">{t.nav.howItWorks}</a></li>
    <li><a href="#inside">{t.nav.inside}</a></li>
    <li><a href="#privacy">{t.nav.privacy}</a></li>
    <li><a href="#faq">{t.nav.faq}</a></li>
  </ul>

  <div class="actions">
    <a href={t.nav.switchLangHref} class="lang">{t.nav.switchLang}</a>
    <PlayStoreBadge locale={locale} href={playUrl} alt={t.alt.playStoreBadge} size="small" />
  </div>
</nav>

<style>
  .nav {
    position: sticky;
    top: 0;
    z-index: 50;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0.75rem 1.5rem;
    background: rgba(232, 226, 210, 0.0);
    backdrop-filter: blur(0px);
    transition:
      background-color 0.3s var(--ease-paper),
      backdrop-filter 0.3s var(--ease-paper);
  }
  .nav.scrolled {
    background: rgba(232, 226, 210, 0.92);
    backdrop-filter: blur(8px);
  }
  .brand { line-height: 0; }
  .links {
    display: flex;
    gap: 1.5rem;
    margin: 0;
    padding: 0;
    list-style: none;
    font-family: var(--font-sans);
    font-size: 0.875rem;
    font-weight: 500;
    text-transform: uppercase;
    letter-spacing: 0.08em;
  }
  .links a:hover { color: var(--color-copper); }
  .actions {
    display: flex;
    align-items: center;
    gap: 1rem;
  }
  .lang {
    font-family: var(--font-sans);
    font-size: 0.875rem;
    font-weight: 600;
    letter-spacing: 0.08em;
    color: var(--color-copper);
    padding: 0.25rem 0.5rem;
    border: 1px solid var(--color-copper);
    border-radius: 4px;
  }
  @media (max-width: 768px) {
    .links { display: none; }
  }
</style>

<script>
  const nav = document.querySelector('.nav');
  if (nav) {
    const onScroll = () => {
      if (window.scrollY > 80) nav.classList.add('scrolled');
      else nav.classList.remove('scrolled');
    };
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
  }
</script>
```

- [ ] **Step 2: Commit**

```bash
git add website/src/components/Nav.astro
git commit -m "feat(website/t10): Nav.astro — sticky top nav

- Transparent over hero, paper-bg + blur on scroll (>80px)
- Wordmark left, 4 anchor links centre, lang-toggle + small Play badge right
- Mobile: anchor links hidden (single-column layout makes scroll-discovery obvious)
- 8 lines of vanilla JS for scroll-class toggle

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 11: Hero.astro — Section 1

**Files:**
- Create: `website/src/components/Hero.astro`

- [ ] **Step 1: Write `website/src/components/Hero.astro`**

```astro
---
import CornerBrackets from './ui/CornerBrackets.astro';
import EyebrowLabel from './ui/EyebrowLabel.astro';
import JournalHeadline from './ui/JournalHeadline.astro';
import PlayStoreBadge from './ui/PlayStoreBadge.astro';
import WingLogo from './ui/WingLogo.astro';
import Wordmark from './ui/Wordmark.astro';
import { type Locale, getCopy } from '../lib/i18n';

interface Props {
  locale: Locale;
}
const { locale } = Astro.props;
const t = getCopy(locale);
const playUrl = 'https://play.google.com/store/apps/details?id=se.birdy.android';
---

<section class="hero">
  <CornerBrackets />
  <div class="content">
    <EyebrowLabel text={t.hero.eyebrow} />
    <JournalHeadline
      text={t.hero.headline}
      level="h1"
      size="clamp(2.5rem, 7vw, 5.5rem)"
    />
    <p class="sub">{t.hero.sub}</p>
    <div class="cta">
      <PlayStoreBadge locale={locale} href={playUrl} alt={t.alt.playStoreBadge} size="large" />
    </div>
    <a href="#loop" class="scroll-cue">{t.hero.scrollCue}</a>
  </div>
  <div class="brand-foot">
    <WingLogo alt={t.alt.wingLogo} size="40px" />
    <Wordmark size="44px" alt={t.alt.wordmark} />
  </div>
</section>

<style>
  .hero {
    position: relative;
    min-height: 100vh;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: var(--section-padding-y) 1.5rem 4rem;
    text-align: center;
  }
  .content {
    max-width: 48rem;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 1.25rem;
  }
  .sub {
    font-family: var(--font-script);
    font-size: clamp(1.25rem, 2.5vw, 1.75rem);
    color: var(--color-copper);
    margin: 0.5rem 0 0.5rem;
    line-height: 1.3;
    max-width: 32rem;
  }
  .cta { margin-top: 1rem; }
  .scroll-cue {
    font-family: var(--font-script);
    font-size: 1.125rem;
    color: var(--color-copper);
    margin-top: 2.5rem;
    opacity: 0.85;
    transition: opacity 0.2s;
  }
  .scroll-cue:hover { opacity: 1; }
  .brand-foot {
    position: absolute;
    bottom: 2rem;
    left: 50%;
    transform: translateX(-50%);
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add website/src/components/Hero.astro
git commit -m "feat(website/t11): Hero.astro — Section 1

- Full-viewport, type-centred, CornerBrackets framing
- EyebrowLabel + h1 JournalHeadline (clamp 2.5-5.5rem) + Caveat-copper sub
- Primary CTA = large PlayStoreBadge
- Sub-CTA scroll-cue anchors to #loop
- WingLogo + Wordmark pinned to hero bottom (slide 01 composition)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 12: Loop.astro — Section 2 with 4 sub-cards

**Files:**
- Create: `website/src/components/Loop.astro`

- [ ] **Step 1: Write `website/src/components/Loop.astro`**

```astro
---
import EyebrowLabel from './ui/EyebrowLabel.astro';
import JournalHeadline from './ui/JournalHeadline.astro';
import OrnamentRule from './ui/OrnamentRule.astro';
import { type Locale, getCopy } from '../lib/i18n';

interface Props {
  locale: Locale;
}
const { locale } = Astro.props;
const t = getCopy(locale);
---

<section id="loop" class="loop">
  <div class="head">
    <EyebrowLabel text={t.loop.eyebrow} />
    <JournalHeadline text={t.loop.headline} level="h2" />
    <p class="sub">{t.loop.sub}</p>
    <OrnamentRule />
  </div>

  <ol class="cards" role="list">
    {t.loop.cards.map((card, i) => (
      <li class="card">
        <div class="num">0{i + 1}</div>
        <h3 class="title">{card.title}</h3>
        <p class="body">{card.body}</p>
        {i < t.loop.cards.length - 1 && (
          <span class="arrow" aria-hidden="true">→</span>
        )}
      </li>
    ))}
  </ol>
</section>

<style>
  .loop {
    padding: var(--section-padding-y) 1.5rem;
    max-width: var(--container-max);
    margin: 0 auto;
  }
  .head { text-align: center; display: flex; flex-direction: column; align-items: center; gap: 0.75rem; }
  .sub {
    font-family: var(--font-script);
    font-size: 1.25rem;
    color: var(--color-copper);
    margin: 0;
  }
  .cards {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 2rem;
    list-style: none;
    padding: 0;
    margin: 2rem 0 0;
    position: relative;
  }
  .card {
    position: relative;
    padding: 1.5rem 1rem;
    text-align: center;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.75rem;
  }
  .num {
    font-family: var(--font-serif);
    font-style: italic;
    font-size: 2rem;
    color: var(--color-copper);
    line-height: 1;
  }
  .title {
    font-family: var(--font-serif);
    font-style: italic;
    font-size: 1.75rem;
    color: var(--color-text-primary);
    margin: 0;
  }
  .body {
    font-family: var(--font-sans);
    font-size: 1rem;
    line-height: 1.5;
    color: var(--color-text-primary);
    margin: 0;
    max-width: 22ch;
  }
  .arrow {
    position: absolute;
    right: -1.5rem;
    top: 50%;
    transform: translateY(-50%);
    font-family: var(--font-serif);
    font-size: 1.5rem;
    color: var(--color-copper);
    opacity: 0.5;
  }
  @media (max-width: 1024px) {
    .cards { grid-template-columns: repeat(2, 1fr); }
    .card:nth-child(2) .arrow,
    .card:nth-child(4) .arrow { display: none; }
  }
  @media (max-width: 640px) {
    .cards { grid-template-columns: 1fr; }
    .arrow { display: none; }
  }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add website/src/components/Loop.astro
git commit -m "feat(website/t12): Loop.astro — Section 2 with 4 sub-cards (Point/Match/Stamp/Browse)

- 1x4 grid desktop, 2x2 tablet, 1x4 stacked mobile
- Numbered (01-04) DM Serif Italic titles + ornament divider
- Copper '->' arrows between cards on desktop only

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 13: Listen.astro — Section 3 with placeholder waveform

**Files:**
- Create: `website/src/components/Listen.astro`

- [ ] **Step 1: Write `website/src/components/Listen.astro`**

```astro
---
import EyebrowLabel from './ui/EyebrowLabel.astro';
import JournalHeadline from './ui/JournalHeadline.astro';
import DeviceFrame from './ui/DeviceFrame.astro';
import { type Locale, getCopy } from '../lib/i18n';

interface Props {
  locale: Locale;
}
const { locale } = Astro.props;
const t = getCopy(locale);

const barCount = 48;
const bars = Array.from({ length: barCount }).map((_, i) => {
  const seed = Math.sin(i * 1.7) * Math.cos(i * 0.6);
  return 20 + Math.abs(seed) * 60;
});
---

<section id="listen" class="listen">
  <div class="grid">
    <div class="text">
      <EyebrowLabel text={t.listen.eyebrow} />
      <JournalHeadline text={t.listen.headline} level="h2" align="left" />
      <p class="sub">{t.listen.sub}</p>
      <p class="body">{t.listen.body}</p>
    </div>
    <div class="visual">
      <DeviceFrame caption="audio scan (placeholder)">
        <div class="audio-screen">
          <div class="waveform" aria-label={t.alt.audioScan}>
            {bars.map((h) => (
              <span class="bar" style={`height:${h}%`}></span>
            ))}
          </div>
          <div class="mic-button">●</div>
          <div class="hint">hold 3s</div>
        </div>
      </DeviceFrame>
    </div>
  </div>
</section>

<style>
  .listen {
    padding: var(--section-padding-y) 1.5rem;
    max-width: var(--container-max);
    margin: 0 auto;
  }
  .grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 4rem;
    align-items: center;
  }
  .text { display: flex; flex-direction: column; gap: 1rem; }
  .sub {
    font-family: var(--font-script);
    font-size: 1.5rem;
    color: var(--color-copper);
    margin: 0;
  }
  .body {
    font-family: var(--font-sans);
    font-size: 1.0625rem;
    line-height: 1.6;
    color: var(--color-text-primary);
    max-width: 32rem;
    margin: 0;
  }
  .visual { display: flex; justify-content: center; }
  .audio-screen {
    width: 100%;
    height: 100%;
    background: var(--color-paper-bg);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 1.5rem;
    padding: 2rem 1rem;
  }
  .waveform {
    display: flex;
    align-items: center;
    gap: 3px;
    height: 80px;
  }
  .bar {
    display: inline-block;
    width: 3px;
    background: var(--color-copper);
    border-radius: 2px;
    opacity: 0.85;
    animation: pulse 1.8s var(--ease-paper) infinite;
  }
  .bar:nth-child(odd) { animation-delay: -0.4s; }
  .bar:nth-child(3n) { animation-delay: -0.9s; }
  @keyframes pulse {
    0%, 100% { transform: scaleY(1); }
    50% { transform: scaleY(0.5); }
  }
  .mic-button {
    width: 64px;
    height: 64px;
    border-radius: 50%;
    background: var(--color-copper);
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 1.5rem;
    box-shadow: 0 4px 12px rgba(168, 85, 45, 0.3);
  }
  .hint {
    font-family: var(--font-script);
    font-size: 1.125rem;
    color: var(--color-marginalia-ink);
  }
  @media (max-width: 768px) {
    .grid { grid-template-columns: 1fr; gap: 2rem; }
  }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add website/src/components/Listen.astro
git commit -m "feat(website/t13): Listen.astro — Section 3 with placeholder audio screen

- 2-col grid desktop, stacked mobile
- DeviceFrame contains a rendered fake audio screen (waveform + mic + hint)
- 48 bars with sin/cos heights pre-computed in frontmatter + CSS pulse
- Placeholder for real 6b2 audio screenshots — swap DeviceFrame content
  with <Image> once user delivers 2 audio screens

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 14: Inside.astro — Section 4 stat trio

**Files:**
- Create: `website/src/components/Inside.astro`

- [ ] **Step 1: Write `website/src/components/Inside.astro`**

```astro
---
import EyebrowLabel from './ui/EyebrowLabel.astro';
import JournalHeadline from './ui/JournalHeadline.astro';
import OrnamentRule from './ui/OrnamentRule.astro';
import { type Locale, getCopy } from '../lib/i18n';

interface Props {
  locale: Locale;
}
const { locale } = Astro.props;
const t = getCopy(locale);
---

<section id="inside" class="inside">
  <div class="head">
    <EyebrowLabel text={t.inside.eyebrow} />
    <JournalHeadline text={t.inside.headline} level="h2" />
    <p class="sub">{t.inside.sub}</p>
    <OrnamentRule />
  </div>

  <ul class="stats" role="list">
    {t.inside.stats.map((stat) => (
      <li class="stat">
        <span class="value">{stat.value}</span>
        <span class="label">{stat.label}</span>
        <span class="caption">{stat.caption}</span>
      </li>
    ))}
  </ul>
</section>

<style>
  .inside {
    padding: var(--section-padding-y) 1.5rem;
    max-width: var(--container-max);
    margin: 0 auto;
  }
  .head { text-align: center; display: flex; flex-direction: column; align-items: center; gap: 0.75rem; }
  .sub {
    font-family: var(--font-script);
    font-size: 1.25rem;
    color: var(--color-marginalia-ink);
    margin: 0;
  }
  .stats {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 3rem;
    margin: 2rem auto 0;
    max-width: 56rem;
    list-style: none;
    padding: 0;
  }
  .stat {
    text-align: center;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.25rem;
  }
  .value {
    font-family: var(--font-serif);
    font-style: italic;
    font-size: clamp(3rem, 6vw, 5rem);
    color: var(--color-copper);
    line-height: 1;
  }
  .label {
    font-family: var(--font-sans);
    font-size: 0.875rem;
    text-transform: uppercase;
    letter-spacing: 0.12em;
    color: var(--color-text-primary);
    margin-top: 0.25rem;
  }
  .caption {
    font-family: var(--font-script);
    font-size: 1.125rem;
    color: var(--color-marginalia-ink);
    margin-top: 0.5rem;
  }
  @media (max-width: 640px) {
    .stats { grid-template-columns: 1fr; gap: 2rem; }
  }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add website/src/components/Inside.astro
git commit -m "feat(website/t14): Inside.astro — Section 4 stat trio (839/25/365)

- Centred eyebrow + JournalHeadline + ornament rule
- 3-col stat grid: 839 species / 25 stamps / 365 daily streak
- DM Serif Italic copper values clamp(3rem, 6vw, 5rem)
- Caveat-italic captions in marginalia ink
- Stacks to 1 column on mobile (<640px)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 15: Premium.astro — Section 5 soft card

**Files:**
- Create: `website/src/components/Premium.astro`

- [ ] **Step 1: Write `website/src/components/Premium.astro`**

```astro
---
import EyebrowLabel from './ui/EyebrowLabel.astro';
import JournalHeadline from './ui/JournalHeadline.astro';
import { type Locale, getCopy } from '../lib/i18n';

interface Props {
  locale: Locale;
}
const { locale } = Astro.props;
const t = getCopy(locale);
---

<section id="premium" class="premium">
  <div class="card">
    <EyebrowLabel text={t.premium.eyebrow} />
    <JournalHeadline text={t.premium.headline} level="h2" size="clamp(2rem, 4.5vw, 3rem)" />
    <p class="sub">{t.premium.sub}</p>
    <p class="body">{t.premium.body}</p>
    <a href={t.premium.ctaHref} class="cta" rel="noopener" target="_blank">{t.premium.cta}</a>
  </div>
</section>

<style>
  .premium {
    padding: var(--section-padding-y) 1.5rem;
    max-width: 60rem;
    margin: 0 auto;
  }
  .card {
    background: var(--color-paper-edge);
    border: 1px solid rgba(63, 79, 48, 0.1);
    border-radius: 12px;
    padding: 3rem 2.5rem;
    text-align: center;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 1rem;
  }
  .sub {
    font-family: var(--font-script);
    font-size: 1.375rem;
    color: var(--color-copper);
    margin: 0;
  }
  .body {
    font-family: var(--font-sans);
    font-size: 1rem;
    line-height: 1.6;
    max-width: 38rem;
    margin: 0;
  }
  .cta {
    margin-top: 1rem;
    font-family: var(--font-script);
    font-size: 1.25rem;
    color: var(--color-copper);
    font-weight: 700;
    text-decoration: underline;
    text-underline-offset: 4px;
    text-decoration-color: rgba(168, 85, 45, 0.35);
  }
  .cta:hover { text-decoration-color: var(--color-copper); }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add website/src/components/Premium.astro
git commit -m "feat(website/t15): Premium.astro — Section 5 free-first soft card

- Single paper-edge card, no pricing, no comparison table
- Eyebrow 'FIELD MEMBER · OPTIONAL' + 'Free first.' headline
- Body explains free is core, premium is for collectors
- CTA = 'See what's inside ->' linked to Play Store listing

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 16: Privacy.astro — Section 6 (mirrors slide 05)

**Files:**
- Create: `website/src/components/Privacy.astro`

- [ ] **Step 1: Write `website/src/components/Privacy.astro`**

```astro
---
import CornerBrackets from './ui/CornerBrackets.astro';
import EyebrowLabel from './ui/EyebrowLabel.astro';
import JournalHeadline from './ui/JournalHeadline.astro';
import OrnamentRule from './ui/OrnamentRule.astro';
import WingLogo from './ui/WingLogo.astro';
import Wordmark from './ui/Wordmark.astro';
import { type Locale, getCopy } from '../lib/i18n';

interface Props {
  locale: Locale;
}
const { locale } = Astro.props;
const t = getCopy(locale);
---

<section id="privacy" class="privacy">
  <CornerBrackets />
  <div class="content">
    <WingLogo alt={t.alt.wingLogo} size="56px" />
    <EyebrowLabel text={t.privacy.eyebrow} />
    <JournalHeadline
      text={t.privacy.headline}
      level="h2"
      size="clamp(3rem, 8vw, 6rem)"
    />
    <p class="sub">{t.privacy.sub}</p>
    <OrnamentRule />
    <p class="body">{t.privacy.body}</p>
    <div class="brand-line">
      <Wordmark size="44px" alt={t.alt.wordmark} />
      <span class="micro">{t.privacy.wordmarkLabel}</span>
    </div>
  </div>
</section>

<style>
  .privacy {
    position: relative;
    padding: calc(var(--section-padding-y) * 1.5) 1.5rem;
    text-align: center;
  }
  .content {
    max-width: 48rem;
    margin: 0 auto;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 1rem;
  }
  .sub {
    font-family: var(--font-script);
    font-size: clamp(1.25rem, 2.5vw, 1.75rem);
    color: var(--color-copper);
    margin: 0;
  }
  .body {
    font-family: var(--font-sans);
    font-size: 1.125rem;
    line-height: 1.6;
    color: var(--color-text-primary);
    max-width: 36rem;
    margin: 0;
  }
  .brand-line {
    margin-top: 2rem;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.5rem;
  }
  .micro {
    font-family: var(--font-sans);
    font-size: 0.6875rem;
    text-transform: uppercase;
    letter-spacing: 0.18em;
    color: var(--color-copper);
  }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add website/src/components/Privacy.astro
git commit -m "feat(website/t16): Privacy.astro — Section 6 (slide 05 1:1 port)

- CornerBrackets framing + 1.5x section padding
- WingLogo -> eyebrow -> giant JournalHeadline 'Fully offline.' (clamp 3-6rem)
  -> Caveat-copper sub -> ornament -> body -> Wordmark + 'AVAILABLE ON GOOGLE PLAY'
- Matches slide 05 composition for visual continuity

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 17: Faq.astro — Section 7

**Files:**
- Create: `website/src/components/Faq.astro`

- [ ] **Step 1: Write `website/src/components/Faq.astro`**

```astro
---
import EyebrowLabel from './ui/EyebrowLabel.astro';
import JournalHeadline from './ui/JournalHeadline.astro';
import FaqItem from './ui/FaqItem.astro';
import { type Locale, getCopy } from '../lib/i18n';

interface Props {
  locale: Locale;
}
const { locale } = Astro.props;
const t = getCopy(locale);
---

<section id="faq" class="faq">
  <div class="head">
    <EyebrowLabel text={t.faq.eyebrow} />
    <JournalHeadline text={t.faq.headline} level="h2" />
  </div>
  <div class="list">
    {t.faq.items.map((item) => (
      <FaqItem question={item.q} answer={item.a} />
    ))}
  </div>
</section>

<style>
  .faq {
    padding: var(--section-padding-y) 1.5rem;
    max-width: 48rem;
    margin: 0 auto;
  }
  .head { text-align: center; display: flex; flex-direction: column; align-items: center; gap: 0.75rem; margin-bottom: 2rem; }
  .list {
    border-top: 1px solid rgba(63, 79, 48, 0.18);
  }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add website/src/components/Faq.astro
git commit -m "feat(website/t17): Faq.astro — Section 7

- Centred head + 8 FaqItem rows from copy.faq.items[]
- Each item is native <details>/<summary> (no JS framework)
- Caveat-script questions + Inter body answers + copper '+/x' toggle

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 18: FinalCta.astro — Section 8

**Files:**
- Create: `website/src/components/FinalCta.astro`

- [ ] **Step 1: Write `website/src/components/FinalCta.astro`**

```astro
---
import CornerBrackets from './ui/CornerBrackets.astro';
import JournalHeadline from './ui/JournalHeadline.astro';
import PlayStoreBadge from './ui/PlayStoreBadge.astro';
import WingLogo from './ui/WingLogo.astro';
import { type Locale, getCopy } from '../lib/i18n';

interface Props {
  locale: Locale;
}
const { locale } = Astro.props;
const t = getCopy(locale);
const playUrl = 'https://play.google.com/store/apps/details?id=se.birdy.android';
---

<section class="final-cta">
  <CornerBrackets />
  <div class="content">
    <WingLogo alt={t.alt.wingLogo} size="72px" />
    <JournalHeadline
      text={t.finalCta.headline}
      level="h2"
      size="clamp(2.5rem, 6vw, 4.5rem)"
    />
    <p class="sub">{t.finalCta.sub}</p>
    <div class="cta">
      <PlayStoreBadge locale={locale} href={playUrl} alt={t.alt.playStoreBadge} size="large" />
    </div>
  </div>
</section>

<style>
  .final-cta {
    position: relative;
    padding: calc(var(--section-padding-y) * 1.5) 1.5rem;
    text-align: center;
  }
  .content {
    max-width: 42rem;
    margin: 0 auto;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 1.25rem;
  }
  .sub {
    font-family: var(--font-script);
    font-size: clamp(1.125rem, 2vw, 1.5rem);
    color: var(--color-copper);
    margin: 0.25rem 0;
  }
  .cta { margin-top: 1rem; }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add website/src/components/FinalCta.astro
git commit -m "feat(website/t18): FinalCta.astro — Section 8

- Mirrors Hero composition: CornerBrackets + WingLogo + headline + sub + CTA
- Headline 'Get it on Google Play.' (Caveat-italic accent on 'Google Play')
- 1.5x section padding for breathing room before footer
- Same large PlayStoreBadge as hero (locale-aware)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 19: Footer.astro — Section 9

**Files:**
- Create: `website/src/components/Footer.astro`

- [ ] **Step 1: Write `website/src/components/Footer.astro`**

```astro
---
import Wordmark from './ui/Wordmark.astro';
import { type Locale, getCopy } from '../lib/i18n';

interface Props {
  locale: Locale;
}
const { locale } = Astro.props;
const t = getCopy(locale);
---

<footer class="footer">
  <Wordmark size="32px" alt={t.alt.wordmark} />
  <ul class="links" role="list">
    {t.footer.links.map((link) => (
      <li><a href={link.href}>{link.label}</a></li>
    ))}
  </ul>
  <p class="credit">{t.footer.credit}</p>
</footer>

<style>
  .footer {
    background: var(--color-paper-edge);
    padding: 2.5rem 1.5rem;
    text-align: center;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 1rem;
  }
  .links {
    display: flex;
    gap: 1.5rem;
    list-style: none;
    margin: 0;
    padding: 0;
    font-family: var(--font-sans);
    font-size: 0.75rem;
    text-transform: uppercase;
    letter-spacing: 0.12em;
    color: var(--color-marginalia-ink);
    flex-wrap: wrap;
    justify-content: center;
  }
  .links a:hover { color: var(--color-copper); }
  .credit {
    font-family: var(--font-script);
    font-size: 0.9375rem;
    color: var(--color-marginalia-ink);
    margin: 0;
  }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add website/src/components/Footer.astro
git commit -m "feat(website/t19): Footer.astro — Section 9

- paper-edge background for contrast vs main paper-bg
- Wordmark + UPPERCASE link row (Privacy, Terms, Email, lang-toggle)
- Caveat-script credit: 'Made by Albin · Sweden · 2026 · v1.0.0'

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 20: Pages assembly — `/` (EN) and `/sv/` (SV)

**Files:**
- Create: `website/src/pages/index.astro`
- Create: `website/src/pages/sv/index.astro`

- [ ] **Step 1: Write `website/src/pages/index.astro` (EN landing)**

```astro
---
import Layout from '../layouts/Layout.astro';
import Nav from '../components/Nav.astro';
import Hero from '../components/Hero.astro';
import Loop from '../components/Loop.astro';
import Listen from '../components/Listen.astro';
import Inside from '../components/Inside.astro';
import Premium from '../components/Premium.astro';
import Privacy from '../components/Privacy.astro';
import Faq from '../components/Faq.astro';
import FinalCta from '../components/FinalCta.astro';
import Footer from '../components/Footer.astro';

const locale = 'en' as const;
const pathname = '/';
---

<Layout locale={locale} pathname={pathname}>
  <Nav locale={locale} />
  <main>
    <Hero locale={locale} />
    <Loop locale={locale} />
    <Listen locale={locale} />
    <Inside locale={locale} />
    <Premium locale={locale} />
    <Privacy locale={locale} />
    <Faq locale={locale} />
    <FinalCta locale={locale} />
  </main>
  <Footer locale={locale} />
</Layout>
```

- [ ] **Step 2: Write `website/src/pages/sv/index.astro` (SV landing)**

```astro
---
import Layout from '../../layouts/Layout.astro';
import Nav from '../../components/Nav.astro';
import Hero from '../../components/Hero.astro';
import Loop from '../../components/Loop.astro';
import Listen from '../../components/Listen.astro';
import Inside from '../../components/Inside.astro';
import Premium from '../../components/Premium.astro';
import Privacy from '../../components/Privacy.astro';
import Faq from '../../components/Faq.astro';
import FinalCta from '../../components/FinalCta.astro';
import Footer from '../../components/Footer.astro';

const locale = 'sv' as const;
const pathname = '/sv/';
---

<Layout locale={locale} pathname={pathname}>
  <Nav locale={locale} />
  <main>
    <Hero locale={locale} />
    <Loop locale={locale} />
    <Listen locale={locale} />
    <Inside locale={locale} />
    <Premium locale={locale} />
    <Privacy locale={locale} />
    <Faq locale={locale} />
    <FinalCta locale={locale} />
  </main>
  <Footer locale={locale} />
</Layout>
```

- [ ] **Step 3: Run build to verify both pages compile**

```bash
cd website
npm run build
```
Expected: `dist/index.html` + `dist/sv/index.html` + `dist/sitemap-index.xml` written, no errors.

- [ ] **Step 4: Sanity-check the built output**

```bash
ls -lh website/dist/
ls -lh website/dist/sv/
```

Then check headlines are present:
```bash
grep -c 'field journal' website/dist/index.html
grep -c 'fältdagbok' website/dist/sv/index.html
```
Expected: both > 0.

- [ ] **Step 5: Commit**

```bash
git add website/src/pages/
git commit -m "feat(website/t20): assemble EN (/) and SV (/sv/) landing pages

- Both pages import same 9 components in identical order
- Locale passed explicitly to each component as 'en' | 'sv' literal
- Layout receives locale + pathname for canonical/hreflang generation
- npm run build produces dist/index.html + dist/sv/index.html + sitemap

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 21: Playwright smoke tests

**Files:**
- Create: `website/playwright.config.ts`
- Create: `website/tests/smoke.spec.ts`
- Modify: `website/package.json` (devDependency `@playwright/test`)

- [ ] **Step 1: Install Playwright**

```bash
cd website
npm install -D @playwright/test
npx playwright install chromium
```

- [ ] **Step 2: Write `website/playwright.config.ts`**

```ts
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:4321',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'npm run preview -- --port 4321',
    url: 'http://localhost:4321',
    timeout: 60_000,
    reuseExistingServer: !process.env.CI,
  },
});
```

- [ ] **Step 3: Write `website/tests/smoke.spec.ts`**

```ts
import { test, expect } from '@playwright/test';

test.describe('EN landing /', () => {
  test('returns 200 + correct h1 + Play Store link', async ({ page }) => {
    const consoleErrors: string[] = [];
    page.on('pageerror', (e) => consoleErrors.push(e.message));
    page.on('console', (msg) => {
      if (msg.type() === 'error') consoleErrors.push(msg.text());
    });

    const response = await page.goto('/');
    expect(response?.status()).toBe(200);

    await expect(page.locator('h1')).toContainText('field journal');

    const playLink = page.locator(
      'a[href*="play.google.com/store/apps/details?id=se.birdy.android"]',
    );
    expect(await playLink.count()).toBeGreaterThan(0);

    expect(consoleErrors, `Console errors: ${consoleErrors.join('\n')}`).toEqual([]);
  });

  test('has hreflang to /sv/ and back', async ({ page }) => {
    await page.goto('/');
    const sv = await page
      .locator('link[rel="alternate"][hreflang="sv"]')
      .getAttribute('href');
    expect(sv).toContain('/sv/');
  });
});

test.describe('SV landing /sv/', () => {
  test('returns 200 + correct h1 + Play Store link', async ({ page }) => {
    const consoleErrors: string[] = [];
    page.on('pageerror', (e) => consoleErrors.push(e.message));
    page.on('console', (msg) => {
      if (msg.type() === 'error') consoleErrors.push(msg.text());
    });

    const response = await page.goto('/sv/');
    expect(response?.status()).toBe(200);

    await expect(page.locator('h1')).toContainText('fältdagbok');

    const playLink = page.locator(
      'a[href*="play.google.com/store/apps/details?id=se.birdy.android"]',
    );
    expect(await playLink.count()).toBeGreaterThan(0);

    expect(consoleErrors, `Console errors: ${consoleErrors.join('\n')}`).toEqual([]);
  });
});
```

- [ ] **Step 4: Build then run smoke tests**

```bash
cd website
npm run build
npm run test:smoke
```
Expected: 3 tests pass.

- [ ] **Step 5: Run i18n parity check too**

```bash
cd website
npm run test:i18n
```
Expected: `i18n parity OK (NN keys)`.

- [ ] **Step 6: Commit**

```bash
git add website/playwright.config.ts website/tests/smoke.spec.ts website/package.json website/package-lock.json
git commit -m "test(website/t21): Playwright smoke tests + i18n parity gate

- playwright.config.ts: chromium-only, webServer = npm run preview :4321
- smoke.spec.ts: 3 tests
  - / returns 200, h1 has 'field journal', Play link present, no console errors
  - /sv/ returns 200, h1 has 'fältdagbok', Play link, no console errors
  - hreflang sv link correct
- pageerror + console.error capture (catches font-load failures, runtime issues)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 22: Update `.github/workflows/pages.yml` + add CNAME

**Files:**
- Modify: `.github/workflows/pages.yml`
- Create: `website/public/CNAME`

- [ ] **Step 1: Write `website/public/CNAME`**

```
birdy.app
```

- [ ] **Step 2: Replace `.github/workflows/pages.yml`**

The new workflow adds an Astro build alongside the existing pandoc rendering. The old inline `index.html` heredoc is removed (Astro produces `index.html` now). The 6 pandoc-rendered legal/listing pages keep working.

```yaml
name: Deploy GitHub Pages

on:
  push:
    branches: [main]
    paths:
      - 'docs/play-store/**'
      - 'website/**'
      - '.github/workflows/pages.yml'
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: pages
  cancel-in-progress: false

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Install pandoc
        run: sudo apt-get update && sudo apt-get install -y pandoc

      - name: Build legal + listing pages (pandoc)
        run: |
          set -euo pipefail
          mkdir -p _legal
          STYLE='<style>
            body { font-family: Georgia, "Times New Roman", serif; max-width: 38rem; margin: 3rem auto; padding: 0 1rem; color: #2A3525; background: #EFE7D6; line-height: 1.55; }
            h1, h2, h3 { font-family: "DM Serif Display", Georgia, serif; }
            h1 { font-style: italic; }
            a { color: #A8552D; }
            hr { border: none; border-top: 1px dashed #4A3F2A; margin: 2rem 0; }
          </style>'
          render() {
            local src=$1 dst=$2 title=$3
            pandoc --from gfm --to html5 \
              --metadata pagetitle="${title} — Birdy" \
              -H <(echo "${STYLE}") \
              "${src}" -o "_legal/${dst}"
          }
          render docs/play-store/privacy-policy.md privacy.html "Privacy Policy"
          render docs/play-store/terms.md          terms.html   "Terms of Use"
          render docs/play-store/store-listing-en.md store-listing-en.html "Store Listing (EN)"
          render docs/play-store/store-listing-sv.md store-listing-sv.html "Store Listing (SV)"
          render docs/play-store/data-safety-form.md data-safety.html "Data Safety"
          render docs/play-store/closed-testing-tester-instructions.md tester-instructions.html "Closed Testing Instructions"

      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: npm
          cache-dependency-path: website/package-lock.json

      - name: Install website deps
        working-directory: website
        run: npm ci

      - name: i18n parity check
        working-directory: website
        run: npm run test:i18n

      - name: Build website (Astro)
        working-directory: website
        run: npm run build

      - name: Assemble _site (Astro + legal pages)
        run: |
          set -euo pipefail
          mkdir -p _site
          cp -r website/dist/. _site/
          cp _legal/*.html _site/

      - uses: actions/configure-pages@v5
      - uses: actions/upload-pages-artifact@v3
        with:
          path: _site

  deploy:
    needs: build
    runs-on: ubuntu-latest
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - id: deployment
        uses: actions/deploy-pages@v4
```

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/pages.yml website/public/CNAME
git commit -m "ci(website/t22): wire Astro build into pages.yml + CNAME = birdy.app

- Adds website/** path filter alongside existing docs/play-store/**
- Setup Node 22 + npm cache keyed on website/package-lock.json
- Runs i18n parity check before build (fails fast on key drift)
- Astro dist/ + 6 pandoc legal pages assembled into _site/
- Removes old inline index.html heredoc — Astro produces /index.html now
- CNAME pinned to birdy.app (resolves spec open question #1)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 23: Final build + manual verification + push

**Files:**
- (none — verification only)

- [ ] **Step 1: Full clean build**

```bash
cd website
rm -rf dist node_modules
npm ci
npm run test:i18n
npm run build
npm run test:smoke
```
Expected: all three pass; `dist/` contains `index.html`, `sv/index.html`, `sitemap-index.xml`, `_astro/`, `fonts/`, `og.png`, `favicon.svg`, `apple-touch-icon.png`, `robots.txt`, `CNAME`, `play-badge-*.svg`.

- [ ] **Step 2: Manual smoke (dev server)**

```bash
cd website
npm run preview
```
Open `http://localhost:4321/` and `http://localhost:4321/sv/` in a browser. Verify:
- Hero renders with Caveat-italic accent on "field journal" / "fältdagbok"
- All 9 sections appear in order with the right copy
- Nav switches to paper-bg + blur on scroll past hero
- FAQ accordions expand on click (native `<details>`)
- Lang toggle navigates between `/` and `/sv/`
- Play Store badges link to the right URL
- No console errors (DevTools)

Kill the preview server with Ctrl+C.

- [ ] **Step 3: Inspect bundle size**

```bash
cd website
du -sh dist/_astro/*.js 2>/dev/null || echo "(no JS chunks)"
du -sh dist/_astro/*.css
ls -lh dist/fonts/
```
Expected: total JS < 30 KB gzipped (Astro produces minimal JS — only the Nav scroll handler), CSS < 20 KB gzipped, fonts ≤ 80 KB total.

- [ ] **Step 4: No-op step (verification only — commit only if T1-T22 left uncommitted fixes)**

If any of Step 1-3 surfaces an issue, fix it and commit with `fix(website/t23): <description>`. Otherwise this task has no commit.

- [ ] **Step 5: Push branch and watch CI deploy**

```bash
git log --oneline -25
git push origin main
```
Watch `.github/workflows/pages.yml` run on GitHub Actions:
- pandoc renders 6 legal pages
- i18n parity passes
- Astro build succeeds
- Assemble copies both into `_site/`
- Deploy publishes to GitHub Pages

After deploy, verify on the live URL:
- `https://birdy.app/` (or `https://anonadrek.github.io/birdy/` until DNS propagates)
- `https://birdy.app/sv/`
- `https://birdy.app/privacy.html` (still works — pandoc-rendered)

---

## Post-launch follow-ups (out of scope; track separately)

These do NOT block the plan but are known gaps the user must address:

1. **Real Google Play partner badges** — replace `play-badge-en.svg` / `play-badge-sv.svg` with official downloads from `play.google.com/intl/en_us/badges/`.
2. **Audio screenshots** for Listen section — when Plan 6b2 device-verify produces 2 real audio screens, swap into `DeviceFrame` content in `Listen.astro`.
3. **SV copy review** — user edits `copy.sv.json` for natural phrasing.
4. **Loop cards screenshots** — add 4 real app screenshots (scan / match / observation-detail / archive) inside each card.
5. **Lighthouse CI** — add a `.github/workflows/lighthouse.yml` PR-check workflow once the live URL is stable.
6. **OG image regeneration** — consider a landing-specific variant with wordmark + "A field journal" headline rather than the Play Store OG card.
7. **Wing-logo transparent variant** — when user provides a separate transparent SVG, drop into `public/wing-logo-transparent.svg` and update the ternary in `WingLogo.astro`.
8. **iOS FAQ wording** — soften "iOS comes after launch if there is demand" if user prefers a non-commitment.
9. **Analytics** — pick GoatCounter / Plausible / nothing; if added, also update `privacy-policy.md`.

---

## Self-Review Notes

- **Spec coverage:** All 9 sections + nav + footer mapped to T10-T19. Visual language tokens in T2-T3. i18n in T4. Assets in T5. Layout/meta in T6. UI primitives in T7-T9. Pages assembly T20. Quality gates T21. CI T22. Verification T23.
- **Placeholder scan:** No "TBD" in code blocks. Open spec questions #1, #4, #5, #7, #8 resolved at plan top. #2, #3, #6, #9 explicitly deferred with rationale. Play Store badges flagged as placeholder with replacement instructions in T9 and follow-up #1.
- **Type consistency:** `Locale = 'en' | 'sv'` used everywhere. `getCopy(locale): Copy` typed via JSON inference. `parseHeadline` returns `Token[]` consumed only by `JournalHeadline.astro`. Component prop interfaces are local to each `.astro` file.
- **Scope check:** Single static site, one deploy target, manageable as 23 bite-sized tasks.
