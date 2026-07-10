# Birdy — Project Knowledge för Claude (claude.ai)

> **Syfte:** Den här filen laddas upp till mitt Claude Project i webbläsaren och fungerar som **fristående bakgrund** så att Claude förstår vad Birdy är, var vi står, hur det ska se ut, hur det ska säljas — utan att se kodbasen.
>
> Skriven 2026-05-20 inför v1.0-launch. **Senast uppdaterad 2026-07-11** — v1.2 live i produktion på Google Play sedan 2026-06-17, iOS-spåret (v2) startat. Uppdatera när stora beslut tas.

---

## 1. Vad är Birdy?

**Birdy** är en AI-driven app för fågelidentifiering med ett digitalt fältdagbok-koncept. Tagline (SV): *"Identifiera fåglar med kameran. En fältdagbok som ser ut som en fältdagbok. Inga foton lämnar din telefon."* Store-positionering (SV kort-beskrivning): *"Identifiera fåglar på foto & ljud. Offline, privat fågelguide & dagbok."*

**Kärnloop:**
1. Användaren öppnar appen och får upp en livekamera (zoom 1–10× via preset-chips), laddar upp ett foto (med beskärning + 90°-rotation), **eller spelar in ett 3-sekunders ljudklipp** (audio-ID via BirdNET-Lite — gratis för alla).
2. AI:n (on-device, ingen cloud) klassar fågeln i realtid (3 fps för foto).
3. Resultatet visas i en av tre vyer beroende på säkerhet:
   - **Match** (≥50% confidence) — en stämpel slås mot pappret: "ny art" eller "gång N".
   - **Disambig** (35–50%) — 2–3 kandidater visas som kort, användaren väljer.
   - **NoBird** (<35%) — "ingen fågel hittad" + tips för bättre bild.
4. Användaren sparar fyndet i sin **dagbok** (fältjournal) — med foto, marginal-anteckning och (om opt-in) plats.
5. Saves låser upp **märken** (34 st: 27 gratis + 7 premium) — gamification utan att vara cringe. Märkena bor i **Troférummet**.

**Feature-set (v1.2, live):**
- **Encyclopedia** — 839 europeiska arter, organiserade i **ekologiska grupper** (alkor, hackspettar, duvor, tranor & rallar …). Browse + sök (klarar apostrofer/diakriter) + filter + artprofil.
- **Lifelist** — vilka arter användaren sett, statistik; livslista-märkesspår upp till 500 arter + rödlistat-spår.
- **Karta (Premium)** — privat fynd-karta som lever helt på enheten. Platsfångst är gratis och opt-in; själva kartvyn är Premium. Field Journal-tematiserade kartplattor + vax-sigill-pins.
- **Audio-ID (gratis, alltid)** — BirdNET-Lite; 3s-inspelning → art. Gratis pga licens (se §6).
- **Veckans uppslag** (weekly recap, visar veckans alla fynd) + **Dagens fågel** (jaga dagens art för ett gratis märke) + veckovis märkesprogression-notis.
- **PDF-export av dagboken** (Premium) + **säsongsstatistik** (Premium).
- **Onboarding** — 7-scens scroll-driven story (Hero/Foto/Ljud/Fältboken/Märken/Privatliv/Namn); kan spelas upp igen från Inställningar.

**Plattform:** Kotlin Multiplatform + Compose Multiplatform. **Android live på Google Play.** **iOS-spåret (v2) pågår** — feature-identisk parity från samma kodbas, plan i0–i6; StoreKit-paywall aktiv från dag 1 på iOS (till skillnad från Android-lanseringen).

**Geografi:** Norden/Europa, fokus Sverige först. (v2-roadmap: Asien-content; v3: hela världen.)

**Användare:** Två lager — nybörjare som vill lära sig + entusiaster i fält. Designen ska tilltala båda utan att klanga som "kids app" eller "pro tool".

---

## 2. Var vi står just nu (2026-07-11)

**🚀 LIVE I PRODUKTION på Google Play sedan 2026-06-17.** Listad som **"Birdy — Bird Identify & Guide"**: `play.google.com/store/apps/details?id=se.birdy.android`. Version vC125 / 1.2.0-rc3 — hela v1.2 ute (karta + premium-redesign + UX-polish-batch, ~97 av 127 audit-fynd åtgärdade, device-verifierad).

**Premium är öppet/gratis för alla under lanseringsperioden** (`PREMIUM_OPEN_FOR_LAUNCH=true`). Beslut: **grandfather** — användare från lanseringsperioden behåller premium för alltid när monetiseringen flippas på. Flippen väntar på Billing-runtime-verify + AB-bolagets Play-kontoflytt.

**Marketing-webbplatsen är live:** `https://birdy.community` (Astro + Vercel, EN/SV, `/legal/`-sidor, Glimpse-carousel med riktiga Play-kort, scroll-animerad hero-fågel).

**iOS (v2) startat 2026-07-07 på Mac:** plan i0 (miljö + ignition) nästan klar — alla KMP-moduler har iOS-targets och **Birdy bootar i iOS-simulatorn** (riktig artdatabas, fejkad skanner; scan/audio/karta stubbat "kommer snart"). Plan-spår i0–i6; **Milestone 1** = uppslagsverk + dagbok + live foto-skanning på fysisk iPhone (slutet av i2). Det man ser i simulatorn idag ligger alltså långt efter Android-appen — parity byggs plan för plan.

**Kvar på Android (ej blockerande nu):**
- **16 KB page-size-fix** — migrera TensorFlow Lite → LiteRT; krävs före nästa Android-uppdatering (vC126). Beslut: görs inne i iOS-spåret (plan i2) så iOS-ML byggs på nya biblioteket direkt.
- Bevaka launch-data: Android vitals (krasch/ANR), installs, recensioner, MapTiler-kvot.
- Billing-verify → monetiseringsflipp (med grandfather-gaten).

**Versionshistorik i korthet:** `v1.0.0` taggad 2026-05-23 → v1.1-batchen (zoom/crop, onboarding v2, omgjorda märken, Troférummet, website v2) → v1.2 (kartan, premium-redesign, polish) → produktion 2026-06-17.

**Paketnamn (locked):** Android `se.birdy.android` · iOS `se.birdy.ios`.

---

## 3. Visuellt språk — "Field Journal"

Birdys hela visuella identitet är byggd runt en **digital fältdagbok**: pappersbakgrund, italic-serif rubriker som ser handskrivna ut, stämplar som låses upp, små marginal-anteckningar med en penna-känsla.

### Färgpalett (locked 2026-04-30, finetuned i Plan 6a)

| Token | Hex | Roll |
|---|---|---|
| `PaperBg` | `#EFE7D6` | Huvudbakgrund — varmt creme-papper |
| `PaperTop` | `#E5DCC7` | Top-gradient på papper |
| `PaperEdge` | `#E5DCC7` | Pappersedge-skugga |
| `MarginaliaInk` | `#3F4F30` | Penna-mörkgrön (text på papper) — WCAG AA-kontrast |
| `HeroMossMid` | `#3F4F30` | Mossgrön (samma hex som MarginaliaInk men separat token) |
| `HeroMossDeep` | `#2A3520` | Djup mossgrön (hero-gradient) |
| `AccentCopper` | `#A8552D` | Koppar — CTA, aktiv flik, stat-siffror, stämpel-pill |
| `StampNavy` | `#1F3A5F` | Marin — för "låsta" stämplar och vissa accents |
| `TextOnAccent` | `#F0EAD8` | Varm offwhite för text på koppar/moss |

**Viktig regel:** Bakgrunden är **alltid** `PaperBg` med en subtil dot-texture (`Modifier.paperBackground()` i Compose). Aldrig ren vit, aldrig grå.

### Typografi

| Typsnitt | Användning |
|---|---|
| **DM Serif Display Italic** | Stora rubriker, artnamn på artprofilen, premium-headline. Italic, elegant, känns som tryckt i en gammal naturalist-bok. |
| **Caveat** (regular + bold) | Accent-ord, marginal-anteckningar, "ditt val", små copy-snuttar som ska se handskrivna ut. Roterade -3° till -6° för känslan av en penna. |
| **Inter** (sans, regular + medium + semibold) | Body-text, UI-element, knapptexter, allt funktionellt. |
| **System monospace** | Pris-rader, exakta siffror där det måste vara läsbart. |

**Konvention:** Rubriker använder syntaxen `*ord*` i koden för att markera accent-segment som ska renderas i Caveat-italic. Exempel: `"A *field birder's* / year."` → "A" + "field birder's" (Caveat, roterad) + "year." på rad 2.

### Komponenter (de viktigaste)

- **`PlateFrame`** — fotoramen för artprofilen och observationer. Ser ut som en präntad illustration i en naturalist-bok: tunn linje, ornament, stämpel-nummer i hörnet.
- **`StampSeal`** — den runda stämpeln som "slås" mot pappret när du låser upp en badge. Tre tillstånd: locked (grå outline), in-progress (StampNavy, halv-fyllning), unlocked (AccentCopper, full + glow-animation).
- **`JournalHeadline` / `JournalSubLine` / `JournalIntro`** — wrappers för rubriker/intro-text med `*word*`-syntaxen.
- **`MicroLabel`** — uppercase eyebrow-rubrik (`ABOUT BIRDY`, `ACCOUNT`).
- **`OrnamentRule`** — ❦ + horisontellt streck. Används för att separera sektioner i en artprofil.
- **`CornerBracket`** — L-shaped corners runt foton i premium-screen (kameraviewfinder-känsla).

### Tillkommet i v1.1/v1.2 (den visuella utbyggnaden)

- **Troférummet** — märkesvyn omgjord till ett troférum; märkena i sektioner (gratis + premium), stämpel-grid med progression. Veckovis notis om märkesprogression.
- **Kartans Field Journal-tema** — kartplattor i toner-stil med runtime-duotone (papper↔sepia via `ColorMatrix`) så kartan ser ut som en karta i fältdagboken, inte som Google Maps. Fynd markeras med **vax-sigill-pins med Birdy-fågeln**.
- **Vax-sigill som återkommande motiv** — förutom kart-pins även sigill-siffror på webbplatsens "It's a loop"-stegkort.
- **Premium-skärmen omgjord** — tydlig gratis-vs-Premium-översikt; en "titt på Premium" direkt efter onboarding (day-0-paywall); låsta features har en diskret **gating-glow** i stället för hårda lås.
- **Onboarding v2** — 7-scens scroll-story med `pageOffset`-drivna animationer; nya komponenter `IntroSceneScaffold`, `WaveformBars` (ljud-scenen), `StreakCounter`, `OfflineShield` (privatlivs-scenen).
- **Skanning** — zoom-preset-chips (1×/5×/10×) i vyfinnaren; beskärningsskärm med hörn-drag + 90°-rotation för uppladdade foton; scan-freeze ("frys" senaste klassificeringen med exakt sin frame).
- **Brand refresh (2026-05-22)** — ny launcher-ikon + custom splash med wordmark (en fågel, inte två).

### Layout-principer

- **Hero är en zon, inte ett kort.** Vertikal gradient flödar mot pappersbg, ingen hård kant.
- **Inga skarpa rektanglar.** Allt är antingen runda (stämplar, knappar), ramade (PlateFrame), eller övergångar (gradients).
- **Aktiva element i koppar** (`#A8552D`). Aktiv flik, primär CTA, stat-siffror — alla pekar mot samma färg-eko.
- **Marginal-texter i Caveat-italic, roterade**. Aldrig stora textmassor i Caveat — bara accents.

### Vad det INTE är

- **Inte Material Design 3** — vi använder M3-komponenter men tematiserar bort allt som ser "Google-default" ut.
- **Inte mörkt tema** (v1.x). Pappersbakgrund passar inte mörkt; light-only.
- **Inte glasmorfism, neumorfism, gradients-as-accent**, inga AI-genererade "futuristic UI"-troper. Tänk naturalist-fältdagbok från 1900, men digital och varm.

---

## 4. Ikoner & grafik

### Status idag

- **Launcher-ikon förnyad** i brand-refreshen 2026-05-22 (+ themed icon Android 13+, notification icon, splash-wordmark). Hi-res 512×512 + feature graphic finns i `docs/play-store/`.
- **Play Store-bilderna produceras nu av ett eget skärmdumpssystem i repot** (Playwright-renderer): rå-skärmdumpar → beskurna → låst kort-mall → 8 färdiga Play-PNG:er (EN) + **transparenta telefon-urklipp** som återanvänds i webbplatsens Glimpse-carousel. Feature graphic är ommatchad till samma kort-stil. En bild-källa, två kanaler.
- **SV-varianterna av Play-korten** är förberedda (tom SV-spegel av kortdatan) men inte producerade än.

### Vad som fortfarande behövs / kan polishas

1. **SV-versioner av de 8 Play-korten** (kortdatan finns, bara texterna som ska in).
2. **Promo-grafik utöver Play Store**: Instagram square (1:1), X-header (1500×500), Show HN-grafik — press-kit-texterna finns redan (se §7).
3. **iOS-grafik (kommer i plan i6):** app-ikon i Apple-format, launch screen, App Store-screenshots på iPhone-ramar.

### Ikon-prompts för Claude Project

När du ber Claude hjälpa med ikondesign:

- **Var alltid explicit med palett**: skicka in `#EFE7D6` (paper), `#A8552D` (copper), `#3F4F30` (marginalia ink), `#1F3A5F` (stamp navy).
- **Var explicit med stil**: "tunn linjekonst, naturalist-fältdagbok, ingen gradients, ingen glow, ingen 3D, inga emojis-look".
- **Be om SVG-output** för allt som ska skalas.
- **Be om dual-variant** (locked + unlocked) för stämplar.

---

## 5. Strategi & positionering

### USP-rangordning (locked 2026-05-15 efter 5-agents research; håller fortfarande)

1. **Field Journal-estetik** — ingen konkurrent har detta. Merlin är funktionell men ful. Picture Bird är aggressivt CTA-driven. Birda är social först. Birdy är *vackert* först.
2. **Ärlig osäkerhetshantering** — Match/Disambig/NoBird-systemet är USP:n som ingen säljer. "Honest about uncertainty" är ett förtroendemarkör i en bransch där alla bluffar 99% accuracy.
3. **On-device + offline + ingen cloud** — Privacy-as-feature. Merlin laddar upp foton till Cornell. Picture Bird kör via Glority-cloud. Birdy stannar på telefonen.

Sedan launch förstärkt av: **foto- OCH ljud-ID är gratis för alla** (inga trial-traps — rak kontrast mot Picture Bird).

**Sälj INTE AI-precision.** Vår AIY V1 ger ~72% top-3 accuracy. Merlin ligger på ~90%. Vi förlorar det racet och ska inte ens spela det.

### Konkurrenter

| App | Styrka | Svaghet vi exploiterar |
|---|---|---|
| **Merlin** (Cornell, gratis) | Bäst-i-klassen AI, 10M downloads, 4.91/5 | Ful UI, cloud-foton, ingen dagbok, ingen progression |
| **BirdNET** | Marknadsledare på audio-ID | Bara audio, ful UI, akademisk känsla |
| **Picture Bird** | Aggressiv ASO | Trial-traps, dark patterns, Glority-cloud, inga svenska arter prioriterade |
| **Birda** | Social, brittiskt community | Inget AI, social-first inte solo-first |
| **Fågelguiden** | Svenskt artregister, etablerat varumärke | Inget AI, ingen modern UX, inget v2026-känsla |

### Launch-läge (uppdaterat)

Lanserad i produktion 2026-06-17. Lokaliserade, längd-säkra titlar + korta beskrivningar finns klara för **12 språk** (en/sv/no/da/fi/de/nl/fr/es/it/pt/pl) — utrullning per marknad styrs från Play Console. Growth-mekanik i appen: **in-app review-prompt efter 3:e sparade fyndet**.

### KPI:er (måltal första 90 dagarna, mäts nu på riktigt)

- D7-retention >12%
- Observations/aktiv/vecka >2
- Premium-conversion 3-5% (mätbar först efter monetiseringsflippen)
- Play Store rating >4.4 (median för fågel-appar är 4.0)
- Recensioner som nämner "vackert" eller "estetik" eller "design" — egen kvalitativ KPI

---

## 6. Monetisering

### Modell (uppdaterad — viktig ändring mot ursprungsplanen)

**Audio-ID är GRATIS för alla** — BirdNET-Lite-modellen är licensierad **CC BY-NC-SA 4.0 (NonCommercial)** och får inte gate:as bakom betalvägg (beslut 2026-05-22). Premium består i stället av features vi byggt själva:

- **Free tier:** Foto-ID + audio-ID + Match-flow + Encyclopedia + Dagbok + platsfångst (opt-in) + 27 märken
- **Premium:** privat fynd-**karta**, **PDF-export** av dagboken, **säsongsstatistik**, **7 extra märken**
- **Produkter:** årsabonnemang (`premium_yearly_v1`) + lifetime engångsköp (`premium_lifetime_v1`) via Google Play Billing v8. Priser sätts i Play Console och hämtas runtime (lokaliserad valuta). Beslutad prisnivå 2026-05-15: 299 kr/år + 699 kr lifetime. **OBS:** appens fallback-strängar (visas innan Billing laddat) säger fortfarande 199/499 kr — synka vid Billing-go-live.
- **iOS:** StoreKit 2 i plan i5; paywall aktiv från dag 1 på App Store.

### Lanseringsläge

`PREMIUM_OPEN_FOR_LAUNCH=true` → alla har premium gratis just nu. **Grandfather-beslut:** användare från lanseringsperioden behåller premium för alltid. Monetiseringsflippen kräver: Billing-runtime-verify (checklista finns i repo-runbook) → AB-bolagets kontoflytt → flippa flaggan + bumpa version.

### Viktiga don'ts (från research; status)

- ~~"Spara 60%"-stämpeln på lifetime~~ ✅ borttagen (EU Omnibus + Google Play dark-pattern-policy).
- ~~Billing v6~~ ✅ Billing v8 implementerat, med Restore Purchases.
- **Throttla cold-start premium-modalen**: max 1× per 3 dagar, ej alls under första 7 dagarna (grace).
- Valuta-visning löst via Billing-formatterade priser — men se fallback-sträng-anmärkningen ovan.

### Realistisk prognos år 1

~85 000 kr brutto efter Google's 15% cut (small-business-tier). Inte en kassasuccé — målet är **proof-of-concept för Birdy AB**, inte primary income. Monetiseringen är ännu inte påslagen; real ramp efter flippen + iOS-launch.

---

## 7. Marketing & ASO

### Play Store listing (live)

**Titel (live):** `Birdy — Bird Identify & Guide` · **Länk:** `play.google.com/store/apps/details?id=se.birdy.android`

**Längd-säkra lokaliserade titlar (≤30 tecken) + korta beskrivningar (≤80),** verifierade för 12 språk. Mönster för nya språk: `Birdy: <kort funktions-tagg>` — behåll "Birdy" + kolon som brand-ankare, översätt bara taggen.

- SV: `Birdy: Fågel-ID & Guide` / `Identifiera fåglar på foto & ljud. Offline, privat fågelguide & dagbok.`
- EN: `Birdy: Bird ID & Field Guide` / `Identify birds by photo & sound. Offline, private field guide & journal.`

**Långbeskrivningens struktur** (SV+EN klara): SKANNA (foto + ljud, gratis, on-device) → LÄR (fågelguide, ekologiska grupper) → SAMLA (dagboken är din egen; karta med Premium) → STÄMPLA (27 gratis märken) → PREMIUM (valfritt; karta/PDF/statistik/7 märken; "kärnan är alltid gratis") → FUNGERAR OFFLINE → PRIVAT FRÅN GRUNDEN.

**Nyckelord (ASO, inbakade i texten):** artbestämning fåglar, fågelsång igenkänning, fågelläten app, fågelskådning app, fågelguide Sverige/Europa, fågeldagbok, identifiera fågel foto, offline fågelapp, privat fågelapp.

### Launch-material (färdigt, i repot)

- **Community-posts** (Show HN, Reddit r/birding + r/SideProject, Facebook "Fåglar inpå knuten", Instagram): `docs/play-store/growth/community-posts.md`
- **Press kit** (tech-/design-/Sverige-vinklarna): `docs/play-store/growth/press-kit.md`
- Kanalstrategin från researchen gäller: HN (KMP-vinkeln), Reddit (privacy-as-feature), svenska skådar-communities (Skådarpodden, Fåglar inpå knuten), Instagram (Field Journal-estetiken är native content).

### Kommande marketing-beats

1. **Monetiseringsflippen** — "grandfather"-berättelsen (tidiga användare belönas) är en positiv story, inte en paywall-story.
2. **iOS-launchen (v2)** — ny pressrunda; "nu även på iPhone" + KMP-tech-vinkeln för HN.

---

## 8. Brand voice & ton

### Skrift-ton

- **Lugn, vetenskaplig, men varm.** Inte ironisk, inte hipster, inte millennial-coy.
- **Aldrig "fågelvänner!" eller "hej kompis!"** — vi tilltalar inte användaren som ett barn.
- **Inte korporativt heller** — inte "vår plattform" eller "användarupplevelse".
- **Förebild:** En äldre fältornitolog som skriver in i sin dagbok efter en lyckad obs. Saklig, närvarande, glad utan att skrika.

### Exempel — bra vs dåligt

| Dåligt | Bra |
|---|---|
| "Wow! Du hittade en ny art! 🎉" | "Ny art." |
| "Premium ger dig superkrafter" | "Hela året som fältornitolog." |
| "Vår AI är 99% korrekt" | "Den här ser ut som en koltrast." |
| "Tap här för att börja!" | "Skanna" |
| "Kunde inte identifiera fågeln 😞" | "Ingen fågel hittad. Försök igen med skarpare bild." |

### Microcopy-konventioner

- **Ingen utropstecken** annat än absolut nödvändigt.
- **Sparsam emoji** (helst noll i UI; ❦ används som ornament, det räknas inte).
- **Lowercase eller capital case** — aldrig SHOUTING CAPS utom på MicroLabels.
- **Svensk text känns inte direktöversatt från engelska** — om en mening känns klumpig på SV, skriv om från noll snarare än ord-för-ord-översätta.

---

## 9. Beslut tagna (för konsistens i framtida samtal)

| Beslut | Datum | Motivering |
|---|---|---|
| Paketnamn `se.birdy.android` | <2026-05-15 | Wirad genom appen; oåterkalleligt efter första Play Store-upload |
| Field Journal-tema över Mossbädd | 2026-05-09 | Större emotional pull; Mossbädd var för "Google-default" |
| Personligt Play-konto först, AB-flytt senare | 2026-05-16 | AB-bolagsbildning för långsam för Closed Testing-deadline |
| Premium pricing: 299 kr/år + 699 kr lifetime | 2026-05-15 | Research visade att 199/499 var underpriced |
| Ta bort "spara 60%"-stämpeln | 2026-05-15 | EU Omnibus + Google Play policy |
| Billing v8, inte v6 | 2026-05-15 | v6 deprecated 2026-08-31 |
| Sverige/Norden först, sedan DE/NL | 2026-05-15 | Fågelguiden är svaga konkurrenten; bygg recensioner här |
| **Audio-ID gratis för alla** | 2026-05-22 | BirdNET-Lite är CC BY-NC-SA (NonCommercial) — får aldrig gate:as bakom Premium. Premium = bara egenbyggda features |
| **Premium öppet/gratis under lanseringsperioden** | 2026-05-22 | Testare + tidiga användare får hela upplevelsen; Billing overifierad vid launch |
| Kartan är en v1.2-feature (1.1→1.2-hopp) | 2026-06-08 | Roadmap-/data-safety-framing; ny minor för ny feature-klass |
| Platsfångst gratis + opt-in; kartvyn Premium | 2026-06-08 | Privacy-löftet håller; värdet (kartan) monetiseras, inte datan |
| Uppladdade foton platsmärks med nuvarande plats, inte EXIF | 2026-06-08 | Androids fotoväljare strippar GPS-EXIF; ny permission ej värd det |
| **Production-launch 2026-06-17 (vC125)** | 2026-06-17 | Godkänd + utrullad; 16 KB-grinden passerad via tillfällig per-version-skip |
| In-app review-prompt efter 3:e sparade fyndet | 2026-06-17 | Ratings av engagerade användare, inte drive-by |
| **Grandfather: lanseringsanvändare behåller premium för alltid** | 2026-06 | Hård gate på monetiseringsflippen; belönar early adopters |
| Proprietär LICENSE på repot | 2026-06-25 | Koden är publik att läsa men inte fri att återanvända |
| **iOS (v2): feature-parity, inga nya features under porten** | 2026-07-07 | Scope-disciplin; parity är mätbart |
| **iOS lanserar med StoreKit-paywall aktiv dag 1** | 2026-07-07 | Android-lanseringens gratis-period upprepas inte |
| LiteRT-migrationen (16 KB-fixen) görs i iOS-plan i2 | 2026-07-07 | iOS-ML byggs på nya biblioteket; Android blir release-ready på köpet |

---

## 10. Exempel-prompts att använda i Claude Project

> Claude Project har den här filen som Knowledge. Använd den som **referens**, inte som mall. Be Claude konsultera den när det behövs.

### Ikondesign

> "Designa en stämpel för 'Storgöksobservation' i Birdys Field Journal-stil. Stämpel-form rund, tunn linjekonst, palett: paper `#EFE7D6`, copper `#A8552D`, marginalia ink `#3F4F30`. Två varianter: locked (outline only) + unlocked (filled + AccentCopper). SVG-output, 96×96 viewBox."

### Marketing-copy

> "Skriv 3 varianter av en post om Birdys iOS-launch för r/birding. Vinkel: samma app, nu på iPhone; privacy-as-feature; solo-utvecklare. Inte clickbait."

### Store listing

> "Här är Birdys svenska långbeskrivning [klistra in]. Föreslå en variant av SKANNA-stycket som lyfter ljud-ID tydligare, i Birdys brand voice (lugn, vetenskaplig, varm)."

### Recensionssvar

> "Skriv ett svar på den här Play Store-recensionen [klistra in]. Birdy brand voice: sakligt, varmt, inga utropstecken, tacka utan att krypa. Max 350 tecken."

### Strategi-validering

> "Jag funderar på att lägga till en 'snabb-läge'-knapp på Scan-skärmen för att accelerera till 5 fps. Är det i linje med Birdys positionering? Argumentera båda sidor."

### Brand-konsistens-check

> "Här är ett UI-mockup för en kommande feature [bifoga skärmdump]. Granska mot Birdy Field Journal-tema. Vad bryter mot palette/typografi/ton?"

### iOS-parity-check

> "Här är en skärmdump från iOS-bygget bredvid samma vy på Android [bifoga]. Lista avvikelser i typografi, färgtokens, spacing och ton — iOS ska vara feature- och utseende-identisk."

---

## 11. Referenser & vidare läsning (för Claude att be om om relevant)

Dessa finns i repot (på min dev-miljö) — om jag bifogar dem ad-hoc kan Claude använda dem:

- **Designspec v1:** Full skärm-för-skärm-design för Android v1.0
- **Designspec v2 (iOS):** mål, i0–i6-plan, arkitektur (`2026-07-07-birdy-ios-v2-design.md`)
- **Store-listing SV/EN + lokaliserade titlar (12 språk):** `docs/play-store/store-listing-{sv,en}.md` + `store-listing-localized-titles.md`
- **Launch-material:** `docs/play-store/growth/{community-posts,press-kit}.md`
- **Research 2026-05-15:** 5 djupanalyser (Konkurrens / ASO / USP / Monetization / Marketing) — totalt ~120 KB strategisk grund
- **Billing-go-live-runbook:** verifieringschecklista + grandfather-gate inför monetiseringsflippen

Om Claude behöver något av detta — säg till mig så bifogar jag.

---

*Filen uppdaterad: 2026-07-11 (från 2026-05-20-versionen). Owner: Albin Lindblom. Repo: github.com/anonadrek/birdy.*
