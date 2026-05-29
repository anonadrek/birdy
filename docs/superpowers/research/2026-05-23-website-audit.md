# Website-audit — birdy.community (2026-05-23)

> Konsoliderad rapport från 5 parallella Explore-agenter som djupdykt i layout, funktionalitet, copy/IA, performance/a11y/SEO och mobil-UX. Bocka av allt eftersom du jobbar dig igenom. Field Journal-temat (paper-bg, DM Serif Italic, Caveat, copper, mossgrön gradient) är den röda tråden mot Android-appen — alla föreslagna ändringar håller sig till det språket.

**Totalt: 71 findings** (20 High · 28 Medium · 23 Low) över 5 domäner.

---

## Innehåll

- [Sammanfattning & prioriteringsguide](#sammanfattning--prioriteringsguide)
- [Tidsbudget & launch-strategi](#tidsbudget--launch-strategi)
- [Domän 1 — Visuell design & tema](#domän-1--visuell-design--tema) (15)
- [Domän 2 — Funktionalitet & interaktivitet](#domän-2--funktionalitet--interaktivitet) (12)
- [Domän 3 — Copy, IA & röda tråden](#domän-3--copy-ia--röda-tråden) (16)
- [Domän 4 — Performance, a11y & SEO](#domän-4--performance-a11y--seo) (15)
- [Domän 5 — Mobil-UX](#domän-5--mobil-ux) (15)
- [Överlapp & noteringar](#överlapp--noteringar)

---

## Sammanfattning & prioriteringsguide

**Sajten håller god grundnivå.** Field Journal-temat är konsekvent på global nivå, privacy-löftet hålls (inga externa fonts/analytics), heading-hierarki och i18n-hreflang är korrekta. De största luckorna är:

1. **Mobil-UX:** 100vh-bugg på iOS, små tap-targets på badges & FAQ, ingen hamburgermeny för nav-länkar.
2. **Tillgänglighet:** Saknar `:focus-visible` på flera primära interaktiva element (Nav, FAQ-summary, dots, Premium-CTA).
3. **Copy-positionering:** Audio-ID-licensen kommuniceras tvetydigt (Play Store säger gratis, sajten gör det otydligt). Privacy-löftet kommer för sent i funneln.
4. **Tema-detaljer:** JournalHeadline-accent saknar italic, dot-texture för stark, DeviceFrame-bezel är ren svart utan moss-koppling, StampSeal-analog saknas helt.
5. **Glimpse-carousel:** Saknar keyboard-nav (Enter/Space på dots), reduced-motion-fix ofullständig, swipe-fallback otestad på äldre Android-WebView.

**Föreslagen ordning att jobba i:**

1. 🔴 Alla High-findings (snabba a11y/mobil-fixar med stor impact)
2. 🟡 Medium-findings inom samma domän som du redan rör (sparar context-switching)
3. Lägg till Playwright-tester för keyboard-nav + mobile (preventerar regression — se Funktionalitet #12)
4. 🟢 Low-findings när det blir tid över (polish-pass innan production-launch)

---

## Tidsbudget & launch-strategi

Realistisk uppskattning för solo-dev med Claude Code som hjälp.

### Per prio-band

| Prio | Antal | Per task (median) | Totalt | Realistisk pass |
|---|---|---|---|---|
| 🔴 High | 20 | 15–60 min (~30) | **~10–12 h** | 1.5–2 fokuserade dagar |
| 🟡 Medium | 28 | 15–45 min (~25) | **~10–14 h** | ~2 dagar |
| 🟢 Low | 23 | 5–20 min (~12) | **~4–6 h** | 1 dag eller utspritt |
| Overhead | — | — | **~5–8 h** | Device-verify, smoke-tests, commits, deploy-kontroller mellan batchar |

**Total budget: ~30–40 h fokuserat arbete.**

### Tre realistiska scenarier

- **Sprint-läge (heltid på sajten):** 4–5 arbetsdagar
- **Sidoarbete (3–4 h/dag parallellt med launch-arbete):** ~2 veckor
- **Bara 🔴 High inför production-launch:** 1.5–2 dagar (10–12 h)

### Tunga undantag som skevar uppskattningen

Räkna med att dessa kan blåsa upp:

- **2.3 Hamburgermeny** — 2–3 h (komponent + a11y + tester)
- **2.4 Play Store-URL env-var** — 1 h (men verifiera memo `project_website_play_link_strategy.md` först)
- **2.12 Nya Playwright-test-suites** — 3–5 h om man bygger alla fyra (accessibility/carousel/mobile/env-config)
- **3.2 Flytta Privacy-sektion** — 1–2 h, kräver designdiskussion
- **5.4 Glimpse-swipe på in-app-browsers** — 1–2 h, kräver fysisk Android + manuell Instagram/Threads-test

### Rekommenderad sequencing givet launch-tidslinjen

Med Internal Testing → Closed Testing (14d) → Production framför oss:

1. **Före Internal Testing-upload (~1.5 dagar):** Alla 🔴 High (mobil-buggar + a11y + audio-licens-copy). Det här är conversion- och juridisk-risk-fixar.
2. **Under Closed Testing (14 dagar tickande):** 🟡 Medium i bakgrunden mellan testar-feedback-cykler. Bygg Playwright-test-suites här.
3. **Efter production-launch:** 🟢 Low + SV legal-routes + email-bridge (CLAUDE.md follow-ups #3 & #7).

Med detta upplägg blir sajten production-ready på ~2 dagar utan att överbygga.

---

## Domän 1 — Visuell design & tema

Sajten implementerar Field Journal-temat **konsekvent på global nivå** med rätt palett, typografi och ornament. Bryts dock i microcopy-detaljer, color-tokens och saknad StampSeal-analog.

### 🔴 High

- [ ] **1.1 Token-inkonsistens PaperBg `#E8E2D2` vs canonical `#EFE7D6`**
  - `src/styles/tokens.css:3`, `src/styles/global.css:47`
  - CLAUDE.md säger PaperBg = `#EFE7D6`. Sajten använder `#E8E2D2`. Verifiera vilken som är rätt — kan vara intentional för att kompensera skärm-vs-print eller bara drift.
  - **Åtgärd:** Synka token-värdet med appens canonical-paletten ELLER dokumentera varför sajten avviker.

- [ ] **1.2 JournalHeadline-accent saknar `font-style: italic`**
  - `src/components/ui/JournalHeadline.astro:46-55`
  - `*ord*`-markdown ska rendera som handskriven Caveat italic med rotation. Idag är rotation + bold på plats men italic saknas → bryter mot Field Journal-spec.
  - **Åtgärd:** Lägg `font-style: italic;` på `.accent`-klassen.

- [ ] **1.3 Dot-texture för stark — `rgba(63,79,48,0.06)`**
  - `src/styles/global.css:69-75`
  - Mot `#E8E2D2` blir texturen visuellt för tjung. Appens paper är luftigare.
  - **Åtgärd:** Sänk opacity till `0.03`–`0.04`.

- [ ] **1.4 DeviceFrame-bezel är ren svart — bryter palette**
  - `src/components/ui/DeviceFrame.astro:28-29`
  - Bezel `#2a2620 → #1a1612` (ren svart-grå) skapar dissonans mot moss/copper.
  - **Åtgärd:** Härled från HeroMossShadow: `linear-gradient(180deg, var(--color-moss-shadow) 0%, rgba(42,53,32,0.8) 100%)`.

- [ ] **1.5 PlayStoreBadge saknar `:focus-visible`-state**
  - `src/components/ui/PlayStoreBadge.astro:22-30`
  - Tabb-användare ser ingen fokus.
  - **Åtgärd:** `.badge:focus-visible { outline: 2px solid var(--color-copper); outline-offset: 4px; }`

### 🟡 Medium

- [ ] **1.6 StampSeal-analog saknas helt**
  - Distribuerat över alla sektioner
  - Appen har StampSeal som visuell marker; sajten har inget motsvarande. T.ex. som badge för "identifierad art" eller "verifierad observation".
  - **Åtgärd:** Skapa `src/components/ui/StampSeal.astro` med roterad text i StampNavy, lågt opacity, placera subtilt i Listen-/Inside-sektion.

- [ ] **1.7 Plate-frame saknas på Glimpse-bilder**
  - `src/components/Glimpse.astro:212-250`
  - Generisk `border-radius: 20px` + `box-shadow` istället för naturalist-frame.
  - **Åtgärd:** `border: 1px solid var(--color-paper-edge); box-shadow: inset 0 0 0 4px var(--color-paper-bg), 0 2px 8px rgba(63,79,48,0.12);`

- [ ] **1.8 FaqItem-fråga använder Caveat 1.5rem — för stor**
  - `src/components/ui/FaqItem.astro:32-37`
  - Marginalia ska vara understated. 1.5rem skriker.
  - **Åtgärd:** `font-size: 1.125rem; font-weight: 400;`

- [ ] **1.9 Legal-prose links saknar font-family + hemlighting på hover**
  - `src/styles/legal-prose.css:29-35`
  - Underline-only utan font-spec → fallback. Bör matcha Field Journal.
  - **Åtgärd:** Explicit `font-family: var(--font-sans);` + hover-background `rgba(168,85,45,0.08)` med `transition: background 0.2s var(--ease-paper)`.

- [ ] **1.10 Loop-kort flat — ingen visuell hierarki**
  - `src/components/Loop.astro:67-88`
  - Alla 4 korten har samma vikt. Ingen "featured"-variant.
  - **Åtgärd:** Lägg variant-prop (`featured?: boolean`); featured-kort får `font-size: 2rem` + copper-accent på nummer.

- [ ] **1.11 Inside stats — färg-trio bryts**
  - `src/components/Inside.astro:62-68`
  - Value = copper, caption = marginalia-ink (mörkgrön). Behöver moss-mid som warm-cool-balans.
  - **Åtgärd:** Caption → `var(--color-moss-mid)`.

### 🟢 Low

- [ ] **1.12 Hero scroll-cue saknar hover-state**
  - `src/components/Hero.astro:74-82`
  - **Åtgärd:** `.scroll-cue:hover { color: var(--color-moss-deep); }`

- [ ] **1.13 OrnamentRule-glyph (❦) för svag — opacity 0.6**
  - `src/components/ui/OrnamentRule.astro:29-33`
  - **Åtgärd:** Opacity `0.8`, ev. `font-size: 1.5rem`.

- [ ] **1.14 CornerBrackets 2px tjocklek för heavy**
  - `src/components/ui/CornerBrackets.astro:22-25`
  - **Åtgärd:** `border: 1.5px solid` för finare linework.

- [ ] **1.15 Nav background hardkodad RGBA, inte token**
  - `src/components/Nav.astro:41, 48`
  - **Åtgärd:** Använd `var(--color-paper-bg)` med CSS-filter opacity istället för hardkodad `rgba(232,226,210,...)`.

---

## Domän 2 — Funktionalitet & interaktivitet

Astro 5 best practices, HTML5 `<details>` för FAQ, robust språkväxling, scroll-snap-carousel. Kritiska luckor: keyboard-nav på dots, saknad mobile-nav, hardkodad Play Store-URL.

### 🔴 High

- [ ] **2.1 Glimpse dots saknar Enter/Space-tangentbordsnav**
  - `src/components/Glimpse.astro:124-132`
  - Dots är `<button>` med `aria-selected` men event-listener binder bara `click`. Tabbar funkar, Enter/Space gör inget.
  - **Åtgärd:** Lägg `keydown`-listener för Enter/Space som triggar samma scroll-snap-logik.
  - **Test:** `tests/keyboard-navigation.spec.ts` med "Glimpse carousel kan navigeras med Tab + Enter".

- [ ] **2.2 FAQ `<summary>` saknar `:focus-visible`**
  - `src/components/ui/FaqItem.astro:17-55`
  - Built-in Enter/Space funkar men ingen visuell fokus → tab-användare vet inte var de är.
  - **Åtgärd:** `.faq-item summary:focus-visible { outline: 2px solid var(--color-copper); outline-offset: 2px; }`

- [ ] **2.3 Mobil nav-links döljs utan hamburgermeny**
  - `src/components/Nav.astro:80-82`
  - `@media (max-width: 768px) .links { display: none; }` utan toggle. Användare på mobil måste scrolla för att hitta sektioner.
  - **Åtgärd:** Hamburger-toggle med `<button aria-expanded>` + JS för visa/dölj `.links` på mobil. Hedra `prefers-reduced-motion` för transition.
  - **Test:** `tests/mobile-nav.spec.ts`.

- [ ] **2.4 PlayStoreBadge-URL hardkodad — bryter Closed Testing-strategi**
  - `src/components/Nav.astro:11`, `src/components/Hero.astro:15`, `src/components/FinalCta.astro:13`, `src/components/Premium.astro:62`
  - Alla pekar på `play.google.com/store/apps/details?id=se.birdy.android`. Under Internal/Closed Testing ska URL:en vara opt-in (`/apps/testing/se.birdy.android`).
  - ⚠️ Det finns redan en auto-memory om detta (`project_website_play_link_strategy.md`) — verifiera dagens status mot den innan ändring.
  - **Åtgärd:** Lägg env-variabel `BIRDY_PLAY_URL` i `astro.config.mjs` + helper i `src/lib/i18n.ts`:
    ```ts
    export function getPlayStoreUrl(): string {
      return import.meta.env.BIRDY_PLAY_URL || 'https://play.google.com/store/apps/details?id=se.birdy.android';
    }
    ```

### 🟡 Medium

- [ ] **2.5 SV saknar legal-routes — ingen `/sv/legal/*`**
  - `src/pages/legal/index.astro:13`, `src/layouts/LegalLayout.astro:20`
  - Legal är EN-only via `noAlternateLocale`. SV-footer cross-länkar till EN (intentional för Nordics-launch, se CLAUDE.md follow-up #7).
  - **Åtgärd:** Markera som post-launch follow-up tills SV-trafik motiverar översättning, eller kommentera tydligt i `LegalLayout.astro` att detta är beslutat.

- [ ] **2.6 Glimpse `prefers-reduced-motion` ofullständig**
  - `src/components/Glimpse.astro:285-293`
  - Sätter `scroll-behavior: auto` + `transition: none` men inte `transform: none` → kort kvarstår i `scale(0.94)` tills JS hinner uppdatera. Visuellt flimmer.
  - **Åtgärd:** Lägg till i RDM-block: `.strip[data-js-active] .card { transform: scale(1); }`

- [ ] **2.7 Nav scroll-trigger känslig för layout-shifts**
  - `src/components/Nav.astro:88-98`
  - `window.scrollY > 80` hardkodad. Vid font-load-shift kan nav redan vara triggad.
  - **Åtgärd:** IntersectionObserver på Hero-sektionen istället för pixel-offset.

- [ ] **2.8 FAQ öppnar inte på deeplink (`#faq-2`)**
  - `src/components/Faq.astro:20-22`, `src/components/ui/FaqItem.astro`
  - Inget URL-fragment-handling.
  - **Åtgärd:** JS i Faq.astro: läs `location.hash`, sätt `open` på matchande `<details>`, scrolla in.

### 🟢 Low

- [ ] **2.9 PlayStoreBadge: ingen explicit responsive sizing**
  - `src/components/ui/PlayStoreBadge.astro:13-15`
  - På <360px kan badge överstiga viewport.
  - **Åtgärd:** `.badge img { max-width: 100%; height: auto; }` + `sizes="(max-width: 480px) 120px, 130px"`.

- [ ] **2.10 Loop-arrows försvinner på mobil utan ersättning**
  - `src/components/Loop.astro:99-107`
  - Visual sekvens-indikering saknas på mobil.
  - **Åtgärd:** Visa "Step X/4"-label eller faktisk swipe-nav.

- [ ] **2.11 Premium CTA saknar `:focus-visible`**
  - `src/components/Premium.astro:53-63`
  - **Åtgärd:** `.cta:focus-visible { outline: 2px solid var(--color-copper); outline-offset: 4px; }`

- [ ] **2.12 Smoke-test täcker inte carousel/FAQ-interaktion**
  - `tests/smoke.spec.ts`
  - Bara HTTP 200 + hreflang + h1 testas.
  - **Åtgärd:** Utöka eller skapa separata tester:
    - `tests/accessibility.spec.ts` — keyboard nav, fokus-states, ARIA
    - `tests/carousel.spec.ts` — dots, scroll-snap, mobile swipe
    - `tests/mobile.spec.ts` — nav-toggle, responsive layout
    - `tests/env-config.spec.ts` — Play Store-URL-switching

---

## Domän 3 — Copy, IA & röda tråden

Stark röd tråd mellan Play Store-listing, hero och app. Field Journal-estetik konsekvent. **Audio-ID-licens kommuniceras tvetydigt** (största risken) och privacy-budskapet kommer för sent i funneln.

### 🔴 High

- [ ] **3.1 Audio-ID-positionering är motsägelsefull (gratis vs premium)**
  - `copy.{en,sv}.json` → `listen.eyebrow` + `premium.body`
  - Play Store säger "audio-ID is **not** behind a paywall". Sajten eyebrow "OR TAP THE MIC" och Premium-copy är otydlig. Detta är **kritiskt** eftersom BirdNET-licensen (CC BY-NC-SA) förbjuder att gate:a audio bakom Premium — om copyn implicerar att audio är premium = juridiskt problem.
  - **Föreslagen text:**
    - EN `listen.eyebrow`: `"AUDIO ID · ALSO FREE · ON-DEVICE"`
    - SV `listen.eyebrow`: `"LJUD-ID · OCKSÅ GRATIS · PÅ ENHETEN"`
    - EN `premium.body`: tillägg `"Audio ID is free for everyone."`
    - SV `premium.body`: tillägg `"Ljud-ID är gratis för alla."`

- [ ] **3.2 Privacy-budskap kommer för sent i sidans funnel**
  - `copy.{en,sv}.json` → `privacy`-sektion
  - Privacy ligger efter Loop, Glimpse, Listen, Inside. 2026-användare som är integritetsoroad bör se trust-signal direkt. Play Store-listing placerar "PRIVACY-FIRST" högre.
  - **Åtgärd:** Antingen flytta Privacy-sektion till mellan Hero och Loop, ELLER lägg till 1-rads trust-signal i Hero subtitle:
    - EN: `"Identify birds with your camera. Keep what you see. Earn the stamps. — *No cloud, no tracking, your data stays on your phone.*"`
    - SV: `"Identifiera fåglar med kameran. Spara det du ser. Samla stämplarna. — *Inget moln, ingen spårning, dina data stannar på din telefon.*"`

### 🟡 Medium

- [ ] **3.3 Audience-segmentering väger för mycket åt entusiaster**
  - `copy.en.json` flera platser
  - Jargong som alienerar nybörjare: "flip back" (Loop), "streak" (Inside), "curious few" (Premium).
  - **Föreslagen text:**
    - `loop.cards[3]`: `"Browse. Flip back any time. Or search all 839 species to learn more."`
    - `inside.sub`: `"every european bird, every find, every day"` (SV: `"varje europeisk fågel, varje fynd, varje dag"`)
    - `premium.sub`: `"Premium goes deeper — for anyone wanting more."` (SV: `"Premium går djupare — för alla som vill veta mer."`)

- [ ] **3.4 SV "obsessiva samlare" känns översatt från EN**
  - `copy.sv.json` → `premium.body`
  - Naturligare: `"...och 10 fältmärken för fågelskådare som vill gå djupare."`

- [ ] **3.5 Listen-copy förklarar inte 3s auto-stop**
  - `copy.en.json` → `listen.body`
  - Nuvarande "until you tap stop" döljer auto-stop-mekaniken.
  - **Föreslagen text:**
    - EN: `"Tap the record button. The same on-device AI listens — hold for up to 3 seconds for auto-stop. No internet needed. Great for birds you hear but can't see."`
    - SV: `"Tryck på inspelningsknappen. Samma AI på enheten lyssnar — håll i upp till 3 sekunder för automatisk stopp. Inget internet behövs. Bra för fåglar du hör men inte ser."`

- [ ] **3.6 Premium value prop otydlig — bara features, inga benefits**
  - `copy.en.json` → `premium.body`
  - "PDF export, seasonal statistics, 10 field marks" säger inte VARFÖR.
  - **Föreslagen text:**
    - EN: `"Premium adds PDF export to print your finds and share your birding, seasonal statistics to track your progress, and 10 premium stamps. Or just use the free app — it's complete."`
    - SV: `"Premium lägger till PDF-export för att skriva ut dina fynd och dela din fågelvandring, säsongsstatistik för att följa utvecklingen, och 10 premium-märken. Eller använd bara gratis-appen — den är komplett."`

- [ ] **3.7 CTA-rytm för aggressiv — 4 Play Store-länkar**
  - Hero + Inside + Premium + FinalCta. "CTA fatigue".
  - **Åtgärd:** Behåll Hero + FinalCta. Ändra Premium-CTA till intern länk (t.ex. expand accordion eller `/premium`-sida) eller ta bort.

- [ ] **3.8 SV "på enheten" + "AI" inkonsekvent översatta**
  - `copy.sv.json` flera platser
  - "På enheten" är överformell; "lokalt" är naturligare.
  - **Föreslagen text:**
    - `listen.eyebrow`: `"ELLER TRYCK PÅ MIKEN · HELT LOKALT"`
    - `listen.body`: `"Samma maskininlärning på din enhet lyssnar..."`
    - `privacy.sub`: behåll "AI:n" — acceptabel i 2026.

- [ ] **3.9 FAQ "Top-3 accuracy 72%" obegriplig för nybörjare**
  - `copy.en.json` → FAQ "How accurate is the AI?"
  - **Föreslagen text:**
    - EN: `"Yes, about 7 in 10 times on first try. If it misses, the second or third guess is often right. Every match shows a confidence score — use it to double-check in a field guide if you're unsure. It's a tool, not a replacement for a guide book."`
    - SV: analog översättning.

### 🟢 Low

- [ ] **3.10 FAQ saknar nybörjar-frågor: batteri, offline-verifiering, väder**
  - `copy.{en,sv}.json` → `faq.items`
  - **Lägg till:**
    - "Does audio ID drain the battery?" → "Minimal impact. Audio runs entirely on-device. Main drain is screen + camera."
    - "Really works offline? No trickery?" → "Really. The AI model (839 species) is bundled in the app. No cloud accounts, no internet calls. Uninstall removes everything."

- [ ] **3.11 FAQ iOS-svar "if there is demand" signalerar osäkerhet**
  - `copy.en.json` → FAQ iOS
  - **Föreslagen text:** `"Not yet. Android first. iOS is planned for later 2026."` (Justera när tidsplan är låst.)

- [ ] **3.12 Listen-eyebrow "OR TAP THE MIC" är slang**
  - `copy.en.json` → `listen.eyebrow`
  - **Föreslagen text:** `"ALSO TRY AUDIO · ON-DEVICE"` / SV: `"ELLER FÖRSÖK MED LJUD · HELT LOKALT"`

- [ ] **3.13 Hero "Earn the stamps" metaforisk utan kontext**
  - `copy.en.json` → `hero.sub`
  - **Föreslagen text:** `"Identify birds with your camera. Keep what you see. Build your collection."` eller `"Collect achievement stamps"`.

- [ ] **3.14 Privacy-eyebrow defensiv-framing**
  - `copy.en.json` → `privacy.eyebrow`
  - Nuvarande: "NO PHOTOS LEAVE YOUR PHONE" → defensiv.
  - **Föreslagen text:** `"PRIVACY · FULLY OFFLINE"` / SV: `"INTEGRITET · HELT OFFLINE"`

- [ ] **3.15 Inside stats "daily streak" gamification-jargong**
  - `copy.en.json` → `inside.stats[2]`
  - **Föreslagen text:** EN: `"consecutive days"` / SV: `"dagar i rad"`

- [ ] **3.16 Footer-credit minimal — bygger inte tillit**
  - `copy.{en,sv}.json` → `footer.credit`
  - **Föreslagen text:**
    - EN: `"Made by Albin Lindblom · Sweden · 2026 · v1.0.0 · Open source · Privacy-first"`
    - SV: `"Skapad av Albin Lindblom · Sverige · 2026 · v1.0.0 · Öppen källkod · Privacy-first"`
  - ⚠️ Verifiera open-source-status innan publicering.

---

## Domän 4 — Performance, a11y & SEO

Solidt fundament — privacy-löftet hålls (inga externa fonts, ingen analytics), WebP/srcset på plats, korrekt hreflang. Kritiska gaps: fokus-state på Nav, ej WebP:ade Play Store-badges/OG-bild, JSON-LD ofullständig.

### 🔴 High

- [ ] **4.1 Nav-länkar saknar synlig fokus-state**
  - `src/components/Nav.astro:64`
  - Bara `:hover`-state. Tab-användare ser inget.
  - **Åtgärd:** `.links a:focus-visible { outline: 2px solid var(--color-copper); outline-offset: 4px; }`

- [ ] **4.2 Play Store-badges inte WebP-optimerade**
  - `public/play-badge-en.png` (4.8 KB), `public/play-badge-sv.png` (17 KB — 3.5× större!)
  - **Åtgärd:** Konvertera till WebP/AVIF, uppdatera `PlayStoreBadge.astro` med `<picture>` + PNG-fallback.

- [ ] **4.3 OG-bild `og.png` är 28 KB PNG utan WebP**
  - `public/og.png`
  - **Åtgärd:** Konvertera. Notera: vissa sociala plattformar föredrar fortfarande PNG/JPEG för OG — behåll PNG som fallback men generera WebP-variant.

### 🟡 Medium

- [ ] **4.4 Glimpse-bilder har generisk alt-text**
  - `src/components/Glimpse.astro:20-27`
  - "Birdy intro screen — A field journal for finds" är vag.
  - **Åtgärd:** Mer beskrivande: `"Birdy app intro screen showing bird identification interface with field journal aesthetic"`.

- [ ] **4.5 Listen-DeviceFrame figcaption generic**
  - `src/components/Listen.astro:29`
  - `<figcaption>audio scan (placeholder)</figcaption>`
  - **Åtgärd:** `caption="Audio scan waveform visualization with recording button"`

- [ ] **4.6 CLS-risk från `font-display: swap`**
  - `src/layouts/Layout.astro:59-61`
  - DM Serif Display + Inter har olika baselines → FOUT vid swap.
  - **Åtgärd:** Byt till `font-display: fallback` för subtilare swap-fönster. Mät CLS efteråt.

- [ ] **4.7 OG saknar `og:url:locale:alternate` för multilingual SEO**
  - `src/layouts/Layout.astro:42-50`
  - hreflang är på plats men OG-metan har inte locale-alternates.
  - **Åtgärd:** Lägg till `<meta property="og:locale:alternate" content="sv_SE">` (på EN) / `"en_US"` (på SV).

- [ ] **4.8 Tailwind purge ej explicit content-path-konfad**
  - `astro.config.mjs`, ingen content-path
  - Default kan släppa igenom unused classes.
  - **Åtgärd:** Lägg till `content: ['./src/**/*.{astro,html,js,jsx,ts,tsx}']` i Tailwind-config.

### 🟢 Low

- [ ] **4.9 Legal-layout saknar locale-toggle/badge** — duplicate av 2.5 (skjut till post-launch)

- [ ] **4.10 JSON-LD `MobileApplication` ofullständig**
  - `src/layouts/Layout.astro:18-28`
  - Saknar `creator`, `datePublished`, ev. `applicationCategory`.
  - **Åtgärd:**
    ```json
    {
      "creator": { "@type": "Person", "name": "Albin Lindblom" },
      "datePublished": "2026-05-23",
      "applicationCategory": "EducationApplication"
    }
    ```

- [ ] **4.11 Legal-pages `lang="en"` hardkodad även om SV läggs till**
  - `src/layouts/LegalLayout.astro:20`
  - **Åtgärd:** Kommentera i kod att detta är intentional pre-launch + planerad migration.

- [ ] **4.12 PlayStoreBadge alt-text för link-image**
  - `src/components/ui/PlayStoreBadge.astro:19`
  - Bör beskriva länkmål, inte bara bilden.
  - **Åtgärd:** Uppdatera `copy.{en,sv}.json` → `alt.playStoreBadge` → `"Google Play: Get Birdy for Android"`.

- [ ] **4.13 Sitemap saknar `lastmod` + `changefreq`**
  - `dist/sitemap-0.xml`
  - **Åtgärd:** Konfigga `@astrojs/sitemap` i `astro.config.mjs`:
    ```js
    sitemap({ changefreq: 'weekly', lastmod: new Date() })
    ```

- [ ] **4.14 Footer-mail saknar `?subject=` pre-fill**
  - `src/components/Footer.astro:16`
  - **Åtgärd:** `mailto:albin@abrahamssons.se?subject=Birdy%20Feedback`
  - ⚠️ Notera CLAUDE.md follow-up #3: email-bridge är `albin@abrahamssons.se` tills `feedback@birdy.community` är live.

- [ ] **4.15 Listen waveform saknar `role="img"`**
  - `src/components/Listen.astro:31`
  - 48 `<span class="bar">` är dekorativa.
  - **Åtgärd:** `<div class="waveform" aria-label={t.alt.audioScan} role="img">`

---

## Domän 5 — Mobil-UX

Solid responsive grund, men flera kritiska tap-target- och iOS Safari-buggar som blockerar conversion. **Eftersom 100% av besökarna kommer för en Android-app är mobil-UX kritisk.**

### 🔴 High

- [ ] **5.1 Hero `min-height: 100vh` buggar på iOS Safari**
  - `src/components/Hero.astro:50`
  - iOS-toolbar-quirk → CTA hamnar under fold + layout-jump vid scroll.
  - **Åtgärd:** Byt till `min-height: 100dvh` (dynamic viewport). Fallback: `min-height: 100vh; min-height: 100dvh;` för äldre webbvyer.

- [ ] **5.2 PlayStoreBadge tap-target-area mindre än bilden**
  - `src/components/ui/PlayStoreBadge.astro:14-15`
  - 176×68px bild men ingen padding → tap-target = bilden själv. På singel-hand-hold är thumb-zone begränsad.
  - **Åtgärd:** `.badge { padding: 0.5rem 1rem; }` för 64×64+ effective tap-area.

- [ ] **5.3 FAQ-summary tap-höjd ~44px — nära gränsen**
  - `src/components/ui/FaqItem.astro:20-29`
  - `padding: 1.25rem 0` ger ca 40-44px. Toggle (`.toggle` `width: 1.5rem`) saknar explicit min-storlek.
  - **Åtgärd:** `summary { padding: 1rem 0; min-height: 48px; }` + wrappa toggle i `<button>` med `width: 44px; height: 44px;` flex-centered.

- [ ] **5.4 Glimpse-swipe otestad på äldre Android-WebView (Instagram/Threads)**
  - `src/components/Glimpse.astro:191-206`
  - CSS scroll-snap + `-webkit-overflow-scrolling: touch` (deprecated). Risk: laggig swipe i in-app-browsers.
  - **Åtgärd:** Testa fysiskt på Android Chrome 90+ och Instagram in-app-browser. Om laggig: implementera touch-event-baserad swipe-fallback eller förlita på dot-nav (redan implementerad).

- [ ] **5.5 Nav language-switcher under 44px tap-target**
  - `src/components/Nav.astro:52-82, 27, 70-79`
  - `.lang { padding: 0.25rem 0.5rem; }` = ~24-28px. Också: ingen safe-area-inset för iOS-notch.
  - **Åtgärd:**
    - `.lang { padding: 0.4rem 0.75rem; min-height: 44px; }`
    - `nav { padding-top: max(0.75rem, env(safe-area-inset-top)); }`
    - Tillsammans med 2.3 (hamburgermeny) löser detta båda mobil-nav-problemen.

- [ ] **5.6 Loop-arrows försvinner på mobil utan flow-indikering**
  - `src/components/Loop.astro:50-51, 104-107`
  - Användare förstår inte att 4 kort = 4-stegs-flöde.
  - **Åtgärd:** Visa "Steg X/4"-label per kort på mobil, ELLER inline-SVG-arrow som roterar 90° för vertikal layout. Också nämnt i Funktionalitet #10.

### 🟡 Medium

- [ ] **5.7 Hero headline clamp(2.5rem, 7vw, 5.5rem) — testa wrap på 320-428px**
  - `src/components/Hero.astro:25`
  - DM Serif Italic är tätt; Caveat-accent kan radbryta illa.
  - **Åtgärd:** Test fysiskt på 320/360/428/640px. Om wrap är dåligt: clamp-max → 4.5rem eller justera breakpoint.

- [ ] **5.8 Premium-card padding 3rem 2.5rem för stor på mobil**
  - `src/components/Premium.astro:33`
  - 25% horisontellt på 320px-skärm.
  - **Åtgärd:** `@media (max-width: 640px) { .card { padding: 1.5rem 1.25rem; } }`

- [ ] **5.9 Listen-grid 2→1 col-jump utan tablet-landscape-breakpoint**
  - `src/components/Listen.astro:50-55, 123-125`
  - iPad mini landscape (768×1024) faller till single-col.
  - **Åtgärd:** Container queries ELLER `@media (min-width: 900px) and (max-height: 500px) { .grid { ... } }`.

- [ ] **5.10 DeviceFrame `width: clamp(180px, 26vw, 280px)` för smal på mobil**
  - `src/components/ui/DeviceFrame.astro:39-40`
  - 26vw på 360px = 93px. Placeholder-skärmar blir thumbnail-storlek.
  - **Åtgärd:** `@media (max-width: 600px) { .screen { width: clamp(260px, 90vw, 340px); } }`

- [ ] **5.11 Glimpse-carousel side-peek för subtil**
  - `src/components/Glimpse.astro:166-171`
  - 72vw card på 320px = nästan hela viewporten. Användare upptäcker inte swipe.
  - **Åtgärd:** `--card-w: clamp(200px, 72vw, 300px)` (min 200 istället för 220) + starkare opacity-gradient på `::before/after`.

- [ ] **5.12 Section-padding-y skapar wall-of-text-känsla**
  - `src/styles/tokens.css:30`
  - `clamp(4rem, 8vw, 8rem)` på små skärmar → monotont scrollande mellan Listen/Premium/Privacy.
  - **Åtgärd:** `@media (max-width: 360px) { :root { --section-padding-y: clamp(2.5rem, 5vw, 4rem); } }` + ev. visual separator (OrnamentRule) mellan sektioner.

### 🟢 Low

- [ ] **5.13 PlayStoreBadge alt-text generic** — duplicate av 4.12

- [ ] **5.14 Language-switcher dold/svår att hitta på mobil** — löses av 2.3 (hamburgermeny)

- [ ] **5.15 `scroll-snap-stop: always` kan blocka momentum-scroll**
  - `src/components/Glimpse.astro:215`
  - Risk på äldre Android-WebView.
  - **Åtgärd:** Testa fysiskt. Om problem: ta bort `scroll-snap-stop` ELLER bara desktop-scope.

---

## Överlapp & noteringar

**Findings som löser varandra (jobba ihop):**

- **2.3 + 5.5 + 5.14** — Hamburgermeny på mobil löser nav-links, language-switcher tap-size och discoverability i ett.
- **1.5 + 2.11 + 4.1** — Alla saknar `:focus-visible` på copper-accent. Skapa en global `.focus-ring`-utility i `tokens.css` och applicera överallt.
- **2.9 + 5.2** — Båda om PlayStoreBadge tap-target/sizing. Lös tillsammans.
- **2.10 + 5.6** — Båda om Loop-arrows på mobil. Samma fix.
- **4.12 + 5.13** — Båda om PlayStoreBadge alt-text för link-target.

**Verify-before-fix (audit-fynd som kräver verifikation före åtgärd):**

- **1.1 (PaperBg-token)** — Avvikelse mellan `tokens.css` och CLAUDE.md kan vara intentional skärm-anpassning. Diskutera innan ändring.
- **2.4 (Play Store-URL)** — Det finns redan en strategi-memo (`project_website_play_link_strategy.md`). Verifiera nuvarande launch-fas (Internal Testing? Closed Testing? Production?) innan env-variabel sätts.
- **3.16 (open-source-claim i footer)** — Bekräfta att appen faktiskt är publicerad open-source innan claim publiceras.
- **3.2 (privacy-position)** — Test mot konversionsdata om sajten har analytics post-launch (idag har den ingen, så A/B-test behöver byggas separat).

**Skjut till post-launch (per CLAUDE.md follow-ups):**

- **2.5 / 4.9 / 4.11** — SV legal-routes (CLAUDE.md follow-up #7).
- **4.14** — Email-byte till `feedback@birdy.community` (follow-up #3).

**Föreslagna nya tester (preventera regression):**

- `tests/accessibility.spec.ts` — keyboard nav + focus-states + ARIA på Nav/FAQ/Glimpse/Premium
- `tests/carousel.spec.ts` — dots-klick, scroll-snap, mobile swipe-fallback
- `tests/mobile.spec.ts` — viewport 360px, nav-toggle, tap-target-sizes
- `tests/env-config.spec.ts` — `BIRDY_PLAY_URL`-switching mellan dev/prod

---

**Slutnot:** 71 findings är mycket att jobba igenom — picka High-domänvis (mobil först → a11y → copy-position → tema-detaljer), commit:a löpande, och dokumentera ändringar i en website-changelog-sektion i `CLAUDE.md` när planen är klar.
