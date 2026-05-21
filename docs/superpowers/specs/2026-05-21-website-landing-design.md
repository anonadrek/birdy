# Birdy v1.0 — Marketing landing page

> Status: spec · 2026-05-21
> Repo-mapp: `website/` (ny, samma repo som appen)
> Mål-tag: ingen (web-asset, fristående från app-versioner)
> Antar: v1.0.0 — alla planerade features (6a, 6b1, 6b2, 6b3) live när sidan publiceras

---

## 1. Mål

En statisk marketing-landing-page vars **enda primära jobb är att konvertera besökare till en Google Play-nedladdning** av Birdy v1.0.0.

Sidan är gjord under antagandet att appen är feature-komplett — det vill säga audio-ID (6b2) och premium-content (6b3) är levererade när landing-sidan går live. Vi bygger inte placeholder-versioner för halvfärdiga features; vi väntar med launch-deploy tills appen matchar sidan.

## 2. Icke-mål

- **Ingen email-insamling.** Sparar onödig backend, GDPR-formulering, dubbel opt-in-mejl.
- **Inga sociala bevis.** Inga testimonials, inget "as seen in", inga star-ratings — vi har inget riktigt content vid launch. Placeholder = sämre än tomt.
- **Ingen press-/about-sida vid launch.** Kan komma som v1.1 om någon faktiskt ber.
- **Ingen blog/changelog.** Tom blog signalerar övergivenhet.
- **Ingen email-länk för support-ticketing.** Bara `albin@birdyapp.se` (eller motsvarande) i footer + FAQ.
- **Ingen pricing-tabell.** Premium nämns mjukt; faktiska priser finns i appen.

## 3. Målgrupp & framgångskriterier

**Primär målgrupp.** Svenska och engelska besökare som ramlar in via Google Search, Reddit-länkar, Play Store-länkar, ord-på-vägen. Två arketyper:

1. **Nybörjaren.** Vill kunna identifiera fågeln på balkongen utan att bli expert. Måste se: enkelhet, gratis, ingen registrering.
2. **Fält-entusiasten.** Har en kikare. Vill ha en pålitlig digital field journal som inte är molntung. Måste se: 839 arter, offline, stamp-collector-mekanik.

**Framgångskriterier (mätbara, första 30 dagar post-launch):**
- Click-through till Play Store ≥ 35% av sessions
- Bounce rate ≤ 65%
- LCP ≤ 2.5s på mobil 3G
- Inga build-errors i CI under 30 dagar
- Båda språkversioner indexerade i Google

## 4. Sidstruktur

Narrativ båge inspirerad av [looplead.se](https://www.looplead.se/): hook → mekanik → djup → trust → objections → close. Inte feature-soppa.

```
0  Sticky nav
1  Hero
2  Loopen — Point · Match · Stamp · Browse (4 sub-kort i en sektion)
3  Listen — andra vägen in (kamera ELLER mikrofon)
4  Vad som finns inuti — 839 species · 25 stamps · daily streak
5  Premium — free first, then go deeper
6  Privacy — fully offline
7  FAQ — expand-on-click
8  Final CTA — Get it on Google Play
9  Footer
```

### Sektion 0 — Sticky nav

- **Layout:** Transparent över hero, fade till paper-bg på scroll past hero.
- **Innehåll:** Birdy-wordmark (vänster) · scroll-anchors "How it works · Inside · Privacy · FAQ" (mitten, kollapsar på mobil) · SV/EN-toggle + Play Store-knapp (höger).
- **Mobil:** Wordmark + Play Store-knapp synliga, anchors flyttas till en hamburgare.

### Sektion 1 — Hero

- **Layout:** Full-viewport, type-centred, copper L-bracket-hörn precis som slide 01. Wing-logo + Birdy-wordmark längst ner.
- **Innehåll:**
  - Eyebrow: "A BIRDY APP · 2026"
  - Headline (DM Serif Italic): "A *field journal* that looks like a field journal"
  - Sub (Caveat copper): "Identify birds with your camera. Keep what you see. Earn the stamps."
  - **Primär CTA: Google Play badge** (officiell SVG)
  - Sub-CTA (Caveat copper, scroll-cue): "See how it works ↓"
- **Visuellt:** Endast typografi + wing-logo. Inget device-mockup i hero — håller fokus på copy + CTA.

### Sektion 2 — Loopen (Point → Match → Stamp → Browse)

Det här är **mekanik-sektionen**. Hela kärnflödet förklaras i en sammanhållen sektion med fyra sub-kort som följer naturlig läsriktning.

- **Layout:** Centrerad rubrik + ornament + fyra-kortsrad (1×4 på desktop, 2×2 på tablet, 1×4 staplade på mobil).
- **Innehåll:**
  - Eyebrow: "HOW IT WORKS"
  - Headline (DM Serif Italic): "One loop."
  - Sub (Caveat): "Four steps. Every sighting closes the circle."
  - Fyra sub-kort, var och en med ikon + headline + en-rads-body:
    1. **Point** — "Open the camera. Aim. The AI guesses three times a second."
    2. **Match** — "When the confidence locks, the stamp appears."
    3. **Stamp** — "Your journal gets a new page. Same paper, new species."
    4. **Browse** — "Flip back any time. Or search 839 European species."
- **Visuellt:** Varje kort har en device-frame-thumbnail med rätt skärm (scan / match / observation-detail / archive). Subtle copper "→"-pil mellan korten på desktop för att visa flödet.

### Sektion 3 — Listen (andra vägen in)

- **Layout:** Asymmetrisk 2-col på desktop. Vänster: text. Höger: device-frame.
- **Innehåll:**
  - Eyebrow: "OR HOLD THE MIC · ON-DEVICE"
  - Headline (DM Serif Italic): "Hear it. *Name* it."
  - Sub (Caveat): "Three seconds. The bird names itself."
  - Body: "Hold the record button for 3 seconds. The same on-device AI listens for the bird. Same loop. Same journal. No internet needed."
- **Visuellt:** Audio-scan-screen device-frame med animerad waveform. **Placeholder** tills användarens 2× audio-screenshots levereras (post-6b2 device-verify).

### Sektion 4 — Vad som finns inuti

Stat-trio som visar djup utan att vara försäljning.

- **Layout:** Centrerad eyebrow + headline, sen 3-stat-rad (1×3 desktop, 3×1 mobil).
- **Innehåll:**
  - Eyebrow: "INSIDE THE APP"
  - Headline (DM Serif Italic): "An archive in your pocket."
  - Sub (Caveat): "every european bird, every stamp, every streak"
  - Tre stats:
    1. **839** species — "from common to rare"
    2. **25** stamps — "earn them by finding"
    3. **365** daily streak — "keep the journal alive"
- **Visuellt:** Två små device-frames under stat-raden (Archive species-list + Badges 5×5-grid).

### Sektion 5 — Premium

Free-first-framing per `feedback_autonomy.md`-ekvivalent-beslut: appen är **primärt gratis**, premium är för dom som vill ta det till nästa nivå.

- **Layout:** En enda mjuk card (paper-edge bg, ingen pricing).
- **Innehåll:**
  - Eyebrow: "FIELD MEMBER · OPTIONAL"
  - Headline (DM Serif Italic): "Free first."
  - Sub (Caveat): "Premium goes deeper — for the curious few."
  - Body: "The app is free. Always will be the core experience. Premium adds PDF-export, seasonal statistics, and 10 field marks for the obsessive collectors."
  - **Inga priser på sidan.** CTA: "See what's inside →" länkar till in-app premium-screen-tour.
- **Visuellt:** Subtle bird-photo bakom card med gradient-fade till paper-bg. Mirror appens `PremiumHeroCard`.

### Sektion 6 — Privacy

Speglar slide 05 visuellt 1:1.

- **Layout:** Full-width, type-only, copper L-bracket-hörn, vertical breathing room (extra padding).
- **Innehåll:**
  - Wing-logo (copper) längst upp
  - Eyebrow: "PRIVACY · NO PHOTOS LEAVE YOUR PHONE"
  - Headline (DM Serif Italic, väldigt stor): "Fully offline."
  - Sub (Caveat copper): "the AI lives in your phone"
  - Ornament divider
  - Body (2 rader): "No cloud. No tracking. No accounts. Just you and the birds."
  - Birdy-wordmark + "AVAILABLE ON GOOGLE PLAY" copper micro-label
- **Visuellt:** Identisk komposition som slide 05. Sätter förtroendet precis före FAQ + final CTA.

### Sektion 7 — FAQ

LoopLead-stil expand-on-click. Frågorna skall ta hand om de mest sannolika konverterings-hindren.

- **Layout:** Centrerad rubrik + accordion-lista.
- **Innehåll:**
  - Eyebrow: "QUESTIONS · ANSWERED"
  - Headline (DM Serif Italic): "Before you download."
  - Frågor (8 stycken, kollapsade by default):
    1. **Does it really work offline?** "Yes. The AI runs entirely on your phone — both for camera and audio. No data leaves the device."
    2. **Do I need an account?** "No. Open the app, point the camera, you're scanning. Your journal lives on the phone."
    3. **Where are my photos stored?** "On your device. Birdy never uploads photos to a server. There is no server."
    4. **How accurate is the AI?** "Top-3 accuracy ~72% on European species. Field-verified on a Galaxy S23 Ultra. Confidence shown for every match."
    5. **What's covered?** "839 European birds — every species in the EBBA2 atlas plus close relatives, with descriptions, photos and range information."
    6. **Is there an iOS version?** "Not yet. Android first. iOS comes after launch if there is demand."
    7. **What about audio?** "Hold the mic button for 3 seconds. BirdNET-Lite runs on-device, same offline guarantee."
    8. **Other questions?** "Email `{support_email}` — I read every one." (placeholder; resolved via open question #4)
- **Visuellt:** Caveat-italic question header, paper-edge card hover-state, copper "+/−"-toggle.

### Sektion 8 — Final CTA

- **Layout:** Centrerad, mycket vertical breathing room, paper-bg.
- **Innehåll:**
  - Wing-logo (stor, copper)
  - Headline (DM Serif Italic): "Get it on Google Play."
  - Sub (Caveat copper): "free · no account · offline"
  - **Stor Google Play-knapp** (officiell SVG, EN- eller SV-variant per locale)
- **Visuellt:** Mirror hero-sektionen kompositionsmässigt — samma L-bracket-hörn, samma wing-logo-position, men nu med CTA istället för headline.

### Sektion 9 — Footer

- **Layout:** Centrerad, kompakt, kontrastfärg (paper-edge `#E5DCC7`).
- **Innehåll:**
  - Birdy-wordmark (mindre, copper)
  - Länkar (Inter UPPERCASE, små): `Privacy · Terms · Email · SV` (eller `EN` om på SV-sidan)
  - Microtext: "Made by Albin · Sweden · 2026 · v1.0.0"

---

## 5. Visuellt språk (port från app)

### 5.1 Färgtokens

Identiska med appens Field Journal-tema (`composeApp/.../ui/theme/Color.kt`):

```css
:root {
  --paper-bg: #E8E2D2;
  --paper-edge: #E5DCC7;
  --paper-top: #EFE7D6;
  --moss-mid: #5C6E48;
  --moss-deep: #3F4F30;
  --moss-shadow: #2A3520;
  --copper: #A8552D;
  --copper-warm: #c9842f;
  --marginalia-ink: #3F4F30;
  --text-primary: #2A3525;
  --text-on-hero: #F0EAD8;
  --stamp-navy: #1F3A5F;
}
```

### 5.2 Typografi

| Font | Användning | Källa |
|---|---|---|
| **DM Serif Display** (Italic + Regular) | Stora headlines, eyebrow-detaljer | self-hosted woff2 |
| **Caveat** | Accent-sublines, Birdy-wordmark, FAQ-fråge-header | self-hosted woff2 |
| **Inter** | Body-text, UPPERCASE-labels | self-hosted woff2 |

Self-hostade i `website/public/fonts/` med `<link rel="preload">` i `<head>`. `font-display: swap`. Total font-load < 80 KB.

### 5.3 Visuell rytm

- Alla sektioner på samma `--paper-bg` med samma dot-texture som appens `paperBackground()`-modifier
- Sektion 6 (Privacy) och Sektion 8 (Final CTA) får extra vertical padding för "andnings-paus"
- Hero, Privacy, Final CTA är **type-driven** (inga screenshots)
- Loopen, Listen, Inside, Premium, FAQ är **mixed text+device-frames**
- Footer ändrar bg till `--paper-edge` för kontrast

### 5.4 Återanvändbara komponenter (`src/components/ui/`)

| Komponent | Vad | Var |
|---|---|---|
| `CornerBrackets.astro` | 4× copper L-bracket (28dp) | Hero, Privacy, Final CTA |
| `OrnamentRule.astro` | `─── ❦ ───` divider | Alla sektioner |
| `JournalHeadline.astro` | Parsar `*ord*` → Caveat-italic-rotation, övrigt → DM Serif | Alla rubriker |
| `WingLogo.astro` | Inline SVG av vinge-symbolen | Hero, Privacy, Final CTA, Footer |
| `Wordmark.astro` | Caveat copper "Birdy." inline SVG | Nav, Final CTA, Footer |
| `PlayStoreBadge.astro` | Officiell Google Play SVG, locale-aware | Nav, Hero, Final CTA |
| `DeviceFrame.astro` | Tunn rounded phone-bezel | Loopen-kort, Listen, Inside, Premium |
| `EyebrowLabel.astro` | UPPERCASE Inter, copper, letter-spacing 0.12em | Alla sektioner |
| `FAQItem.astro` | Accordion item med native `<details>` + `<summary>` (ingen JS) | FAQ |

### 5.5 Animationsdisciplin

- CSS-only + IntersectionObserver för fade-ins
- Ingen Framer Motion, ingen GSAP, ingen tung JS-runtime
- FAQ-accordion: en `<details>` element + minimal CSS — ingen JS-dependency alls
- Alla animationer respekterar `prefers-reduced-motion: reduce`

---

## 6. i18n

### 6.1 Strategi

- All synlig copy bor i `src/content/copy.{en,sv}.json`
- Komponenter läser via typad `t(key)`-helper
- Inga strängar hårdkodade i `.astro`-filer
- `*ord*`-syntax (samma som appens `JournalHeadline`) parsas till Caveat-italic-accent

### 6.2 Routing

- `astro.config.mjs`: `i18n: { defaultLocale: 'en', locales: ['en', 'sv'], routing: { prefixDefaultLocale: false } }`
- `/` → EN landing
- `/sv/` → SV landing
- `<link rel="alternate" hreflang="en" href="/" />` + `<link rel="alternate" hreflang="sv" href="/sv/" />` på båda
- `<link rel="alternate" hreflang="x-default" href="/" />`

### 6.3 Översättnings-arbetsflöde

- Engelsk copy skrivs först (matchar slides + Play Store-listing)
- Svensk översättning skrivs efteråt av användaren
- Vid launch: båda måste vara kompletta (CI failas om `copy.sv.json` saknar keys som finns i `copy.en.json`)

---

## 7. Asset-pipeline

### 7.1 Källor

| Källa | Var den ligger | Hur det hamnar i repo |
|---|---|---|
| 5 marketing-slides EN | `C:/Users/abbea/Desktop/Birdy AB/Birdy Play Store/Play Store English/birdy-screenshot-0{1..5}-EN.png` | `website/scripts/sync-assets.sh` kopierar in |
| 5 marketing-slides SV (om de finns) | samma mapp, `*-SV.png` | samma |
| Brand-assets (logos, OG-card) | `C:/Users/abbea/Desktop/Birdy AB/Birdy Play Store/Birdy Play store English/` | samma sync-script |
| App-screenshots | `docs/superpowers/screenshots/2026-05-15-v0.8.0-rc1/` (redan i repo) | direkt-import via Astro `<Image>` |
| Audio-scan-screenshots | Levereras post-6b2 | placeholder så länge, ersätts pre-launch |

### 7.2 Processing

- `website/src/assets/` för bilder som Astro processar (`<Image>` → WebP + AVIF + srcset)
- `website/public/` för direkt-servade filer (favicon, OG-card, robots.txt, sitemap.xml)
- Sync-script committar resultatet i repo — Desktop-källan är inte länkad runtime

### 7.3 Sync-script

`website/scripts/sync-assets.sh`:
- Kopierar från Desktop-mapparna till `website/src/assets/` + `website/public/`
- Idempotent (skriver bara om källa är nyare än destination)
- Körs manuellt vid asset-uppdatering, inte som del av CI
- Source-paths bor i `website/scripts/asset-manifest.json` så ändringar av Desktop-struktur bara kräver manifest-edit

---

## 8. SEO + meta

| Tag | Värde EN | Värde SV |
|---|---|---|
| `<title>` | "Birdy — A field journal for birds" | "Birdy — En fältdagbok för fåglar" |
| `<meta description>` | "Identify European birds with your phone camera or mic. 839 species. Offline AI. No accounts." | "Identifiera europeiska fåglar med kamera eller mikrofon. 839 arter. Offline-AI. Inga konton." |
| `<link rel="canonical">` | `https://<domain>/` | `https://<domain>/sv/` |
| `<link rel="alternate" hreflang>` | both directions | both directions |
| `<meta property="og:image">` | `/og.png` (befintlig 1200×630) | samma |
| `<meta property="og:locale">` | `en_US` | `sv_SE` |
| `<meta property="og:title/description>` | per-locale | per-locale |
| Twitter card | `summary_large_image` | samma |
| `<link rel="icon">` | `/favicon.svg` (från `birdy-logo-paper.svg`) | samma |
| `<link rel="apple-touch-icon">` | `/apple-touch-icon.png` 180×180 | samma |

### JSON-LD (`SoftwareApplication` schema)

```json
{
  "@context": "https://schema.org",
  "@type": "MobileApplication",
  "name": "Birdy",
  "operatingSystem": "Android",
  "applicationCategory": "LifestyleApplication",
  "downloadUrl": "https://play.google.com/store/apps/details?id=se.birdy.android",
  "offers": { "@type": "Offer", "price": "0", "priceCurrency": "SEK" },
  "screenshot": ["...slide URLs..."],
  "inLanguage": ["en", "sv"]
}
```

### Sitemap + robots

- `sitemap.xml` auto-genererad av `@astrojs/sitemap`, listar `/` + `/sv/`
- `robots.txt`: `User-agent: * / Allow: / / Sitemap: https://<domain>/sitemap.xml`

---

## 9. Performance-budget

| Metric | Mål | Verifiering |
|---|---|---|
| LCP | ≤ 2.5s på Slow 4G | Lighthouse CI, blockerar inte build |
| CLS | ≤ 0.05 | Lighthouse CI |
| TBT | ≤ 200ms | Lighthouse CI |
| Total JS | ≤ 30 KB gzipped | `astro build` output check |
| Total CSS | ≤ 20 KB gzipped | `astro build` output check |
| Total font-load | ≤ 80 KB | manuell measure |
| Hero LCP element | typografi, inte bild | design-disciplin |

Strategier:
- Self-hostade fonts med `<link rel="preload" as="font" crossorigin>` för LCP-fonts
- Astro `<Image>` på alla foto-bilder (auto AVIF/WebP)
- Inga client-side JS-libraries; FAQ-accordion via native `<details>`
- Inline kritisk CSS i `<head>`
- `<img loading="lazy">` på allt som inte är above-the-fold

---

## 10. Tech stack

| Lager | Val | Skäl |
|---|---|---|
| Static-site-generator | **Astro 5.x** | Built-in i18n, component model, image optimization, GitHub Pages-kompatibel |
| Styling | **Tailwind v4** | Inline-utility model, design tokens via CSS variables, ingen build-step-konflikt med Astro |
| Interaktivitet | Native `<details>` + CSS-only IntersectionObserver fades | Ingen React-runtime, inga JS-libs |
| Fonts | self-hosted woff2 | LCP-control, no CDN-roundtrip |
| Hosting | **GitHub Pages** | Samma repo, samma workflow, gratis |
| CI | GitHub Actions | Befintlig `pages.yml`-workflow uppdateras |
| Domän | TBD post-launch (placeholder: `anonadrek.github.io/birdy/`) | CNAME-fil läggs till när domän klar |

---

## 11. Filstruktur

```
birdy-bird-scanner/
├─ website/
│  ├─ package.json
│  ├─ package-lock.json
│  ├─ astro.config.mjs
│  ├─ tsconfig.json
│  ├─ tailwind.config.mjs
│  ├─ .gitignore                   ← node_modules, dist, .astro
│  ├─ scripts/
│  │  ├─ sync-assets.sh
│  │  └─ asset-manifest.json
│  ├─ src/
│  │  ├─ layouts/
│  │  │  └─ Layout.astro
│  │  ├─ components/
│  │  │  ├─ Nav.astro
│  │  │  ├─ Hero.astro
│  │  │  ├─ Loop.astro
│  │  │  ├─ Listen.astro
│  │  │  ├─ Inside.astro
│  │  │  ├─ Premium.astro
│  │  │  ├─ Privacy.astro
│  │  │  ├─ Faq.astro
│  │  │  ├─ FinalCta.astro
│  │  │  ├─ Footer.astro
│  │  │  └─ ui/
│  │  │     ├─ CornerBrackets.astro
│  │  │     ├─ OrnamentRule.astro
│  │  │     ├─ JournalHeadline.astro
│  │  │     ├─ WingLogo.astro
│  │  │     ├─ Wordmark.astro
│  │  │     ├─ PlayStoreBadge.astro
│  │  │     ├─ DeviceFrame.astro
│  │  │     ├─ EyebrowLabel.astro
│  │  │     └─ FaqItem.astro
│  │  ├─ content/
│  │  │  ├─ copy.en.json
│  │  │  └─ copy.sv.json
│  │  ├─ lib/
│  │  │  └─ i18n.ts                ← typad t(key)-helper, hreflang-builder
│  │  ├─ pages/
│  │  │  ├─ index.astro
│  │  │  └─ sv/
│  │  │     └─ index.astro
│  │  ├─ assets/
│  │  │  ├─ slides/                ← marketing-slides 01-05
│  │  │  └─ screens/               ← app-screenshots
│  │  └─ styles/
│  │     ├─ tokens.css
│  │     └─ global.css
│  ├─ public/
│  │  ├─ fonts/                    ← DM Serif Display, Caveat, Inter (woff2)
│  │  ├─ favicon.svg
│  │  ├─ apple-touch-icon.png
│  │  ├─ og.png
│  │  ├─ robots.txt
│  │  └─ (CNAME — tillkommer när domän klar)
│  └─ tests/
│     └─ smoke.spec.ts             ← Playwright: / + /sv/ → 200 + h1 finns
│
├─ docs/play-store/                ← oförändrat
│  ├─ privacy-policy.md
│  └─ terms.md
│
└─ .github/workflows/
   └─ pages.yml                    ← uppdateras
```

### .gitignore-tillägg

```
website/node_modules/
website/dist/
website/.astro/
```

---

## 12. Deploy-workflow

Uppdaterad `.github/workflows/pages.yml`:

```yaml
on:
  push:
    branches: [main]
    paths:
      - 'website/**'
      - 'docs/play-store/**'
      - '.github/workflows/pages.yml'
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      # Existerande pandoc-steg (kvar oförändrat)
      - name: Install pandoc
        run: sudo apt-get install -y pandoc
      - name: Build legal pages
        run: |
          pandoc docs/play-store/privacy-policy.md -o privacy.html \
            --standalone --metadata title="Privacy Policy"
          pandoc docs/play-store/terms.md -o terms.html \
            --standalone --metadata title="Terms of Service"

      # NYTT: Astro-build
      - uses: actions/setup-node@v4
        with:
          node-version: 22
          cache: npm
          cache-dependency-path: website/package-lock.json
      - name: Install website deps
        working-directory: website
        run: npm ci
      - name: Build website
        working-directory: website
        run: npm run build

      # Slå ihop output
      - name: Assemble site
        run: |
          mkdir -p _site
          cp -r website/dist/* _site/
          cp privacy.html terms.html _site/

      - uses: actions/upload-pages-artifact@v3
        with:
          path: _site

  deploy:
    needs: build
    runs-on: ubuntu-latest
    permissions:
      pages: write
      id-token: write
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - uses: actions/deploy-pages@v4
        id: deployment
```

---

## 13. Quality-gates

- `npm run build` måste passera (catches broken imports, missing translations, type-fel)
- `npm run test:smoke` — Playwright mini-tester:
  - `/` returnerar 200 + `<h1>` matchar EN headline-key
  - `/sv/` returnerar 200 + `<h1>` matchar SV headline-key
  - Play Store-länken är rätt URL i båda locales
  - Inga `console.error` vid sidladdning
- **i18n-parity-check** (CI-script): båda `copy.en.json` + `copy.sv.json` har identisk key-uppsättning, eller bygget failas
- Lighthouse CI på preview-build → rapport-artifact, varnar men blockerar inte
- ktlint/detekt **kör fortfarande** mot KMP-koden (samma trigger som idag) — orelaterat till webb-deploy

---

## 14. Öppna frågor & follow-ups

| # | Fråga | När det måste lösas |
|---|---|---|
| 1 | **Domän?** `birdy.se`, `birdyapp.se`, `getbirdy.app`? Påverkar OG-URLer + JSON-LD + CNAME. | Innan första live-deploy |
| 2 | **Audio-screenshots** för Listen-sektion (Plan 6b2 device-verify). | Innan v1.0-launch |
| 3 | **Svensk översättning** av all copy. | Innan v1.0-launch |
| 4 | **Email-adress** för support/FAQ — `albin@birdyapp.se` eller annan? | Innan FAQ-sektion skrivs |
| 5 | **Play Store-URL** — `se.birdy.android` är applicationId; full URL `play.google.com/store/apps/details?id=se.birdy.android` används men app måste vara published för att länken ska fungera publikt. Sidan kan deploya med "Coming soon"-CTA om vi vill publicera före appen. | Beslut före launch-deploy |
| 6 | **iOS-svar i FAQ #6** — "iOS comes after launch if there is demand" — vill användaren binda sig till det löftet eller mjukare formulering? | Innan FAQ skrivs |
| 7 | **Wing-logo SVG** — finns redan som `birdy-logo-paper.svg` / `birdy-logo-transparent.svg` — säkerställ vi använder rätt variant per context (mörk bg vs paper bg). | Implementations-fas |
| 8 | **Premium-screen-tour** (Premium CTA "See what's inside →" länkar dit) — är det en intern Play Store deeplink (`market://...`) eller in-app-route? | Beslut före Premium-sektion implementeras |
| 9 | **Analytics?** GoatCounter / Plausible / Vercel-analytics / inget? Påverkar privacy-statement. | Beslut före launch-deploy |

---

## 15. Risker

| Risk | Mitigering |
|---|---|
| Audio-screenshots dröjer → Listen-sektion blir placeholder vid launch | Vi launchar inte sidan förrän appen är feature-komplett; sektionen byggs med generisk audio-waveform-illustration så placeholder är acceptabel även vid soft-launch |
| SV-översättning glömd för någon ny copy-key | i18n-parity-check i CI failar bygget tills båda JSON är synkrona |
| Google Play-URL ger 404 vid soft-launch före app-publishing | Launch-checklist kräver att appen är published before web is live; alternativt visa "Coming soon"-CTA på sidan |
| Asset-källor på Desktop riskerar att försvinna (om datorbyte) | Sync-script committar alla assets i repo; Desktop-mappen är källa-of-truth men inte runtime-beroende |
| Fonts blockerar LCP | `<link rel="preload">` + `font-display: swap` + minimal woff2-subset (latin + latin-ext + Caveat-glyphs only) |
| Sidan ändras inte men workflow re-deployer onödigt | Path-filter på `website/**` + `docs/play-store/**` + själv-workflow |
| KMP-bygget bryts av att npm-deps installeras i CI | Path-filter separerar Android-CI och Pages-CI; ingen korsning |

---

## 16. Acceptanskriterier (vad räknas som färdig spec → ready to plan)

- [x] Sidans struktur är låst (9 sektioner + nav)
- [x] Tech stack är låst (Astro + Tailwind + GitHub Pages)
- [x] Visuellt språk är portat 1:1 från app (färger, fonts, primitiver)
- [x] i18n-strategi är definierad (`/` EN + `/sv/` SV, JSON-baserad copy)
- [x] Asset-källor är cataloged (Desktop → repo via sync-script)
- [x] Workflow-uppdatering är specifierad (pages.yml combined Astro + pandoc)
- [x] Performance-budget och quality-gates är skrivna
- [x] Öppna frågor är listade (men ej blockerande för att skriva implementation plan — kan lösas inom plan-tasks)

Specen är redo att gå vidare till `superpowers:writing-plans`.
