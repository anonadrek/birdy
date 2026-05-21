# Birdy — Project Knowledge för Claude (claude.ai)

> **Syfte:** Den här filen laddas upp till mitt Claude Project i webbläsaren och fungerar som **fristående bakgrund** så att Claude förstår vad Birdy är, var vi står, hur det ska se ut, hur det ska säljas — utan att se kodbasen.
>
> Filen är skriven 2026-05-20 inför `v1.0.0`-launch (mål 2026-06-01). Uppdatera när stora beslut tas.

---

## 1. Vad är Birdy?

**Birdy** är en AI-driven Android-app för fågelidentifiering med ett digitalt fältdagbok-koncept. Tagline (SV): *"Identifiera fåglar med kameran. En fältdagbok som ser ut som en fältdagbok. Inga foton lämnar din telefon."*

**Kärnloop:**
1. Användaren öppnar appen och får upp en livekamera (eller laddar upp ett foto).
2. AI:n (on-device TensorFlow Lite) klassar fågeln i realtid (3 fps).
3. Resultatet visas i en av tre vyer beroende på säkerhet:
   - **Match** (≥50% confidence) — en stämpel slås mot pappret: "ny art" eller "gång N".
   - **Disambig** (35–50%) — 2–3 kandidater visas som kort, användaren väljer.
   - **NoBird** (<35%) — "ingen fågel hittad" + tips för bättre bild.
4. Användaren kan spara observationen i sin **dagbok** (fältjournal).
5. Saves låser upp **stämplar** (badges) — gamification utan att vara cringe.

**Sekundära features:**
- **Encyclopedia** — uppslagsverk över 839 europeiska fågelarter (browse + sök + filter + artprofil).
- **Lifelist** — vilka arter användaren har sett, statistik.
- **Listen** (i v1.0) — placeholder/launcher för audio-ID (BirdNET-Lite), levereras i Plan 6b.

**Plattform:** Kotlin Multiplatform + Compose Multiplatform. Android-first; iOS-skelett finns men är inte v1-scope.

**Geografi:** Norden/Europa, fokus Sverige först.

**Användare:** Två lager — nybörjare som vill lära sig + entusiaster i fält. Designen ska tilltala båda utan att klanga som "kids app" eller "pro tool".

---

## 2. Var vi står just nu (2026-05-20)

**Status:** `v0.8.0-rc1` är taggad och device-verifierad på Samsung Galaxy S23 Ultra (API 35). Allt UI är färdigbyggt; release-mekaniken (R8, signing, adaptive icon, splash, locale-switch SV↔EN, accessibility-bumps) är på plats.

**Återstår innan v1.0 launch (2026-06-01):** Plan 6b — **Google Play Billing v8** + **audio-ID via BirdNET-Lite** + **PDF-export** + **säsongs-statistik** + **10 premium fält-märken** + **ML preprocessing fix** (nuvarande field hit-rate ~10% är launch-blocker).

**Kritisk extern process:** **Closed Testing-spåret måste startas senast 2026-05-18** (Google kräver 14 dagars test för nya personliga developer-konton innan production access). Personligt konto först, AB-flytt post-launch via Account Transfer.

**Paketnamn (locked):** `se.birdy.android`

**Versionshantering:**
- `v0.8.0-rc1` = release candidate (UI klart, ingen billing)
- `v0.9.0a-billing` = Plan 6b1 milestone (Billing v8 wirad)
- `v1.0.0` = launch på Play Store

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

### Layout-principer

- **Hero är en zon, inte ett kort.** Vertikal gradient flödar mot pappersbg, ingen hård kant.
- **Inga skarpa rektanglar.** Allt är antingen runda (stämplar, knappar), ramade (PlateFrame), eller övergångar (gradients).
- **Aktiva element i koppar** (`#A8552D`). Aktiv flik, primär CTA, stat-siffror — alla pekar mot samma färg-eko.
- **Marginal-texter i Caveat-italic, roterade**. Aldrig stora textmassor i Caveat — bara accents.

### Vad det INTE är

- **Inte Material Design 3** — vi använder M3-komponenter men tematiserar bort allt som ser "Google-default" ut.
- **Inte mörkt tema** (v1). Pappersbakgrund passar inte mörkt; v1 är light-only.
- **Inte glasmorfism, neumorfism, gradients-as-accent**, inga AI-genererade "futuristic UI"-troper. Tänk naturalist-fältdagbok från 1900, men digital och varm.

---

## 4. Ikoner & grafik

### Status idag

Användaren har skapat ett eget Field Journal-ikonset i `docs/superpowers/icon-concepts/final/`:

- `ic_launcher_512.svg` — Play Store hi-res icon (512×512). Pappers-bakgrund (`#EBDEC2`) med kopparfärgad (`#A8552D`) stiliserad fågel-silhouette + radial warmth gradient + subtil grain-texture.
- `ic_launcher_monochrome.svg` — Themed icon (Android 13+).
- `ic_notification.svg` — Notification icon (status bar).
- `feature_graphic.svg` — Play Store feature graphic (1024×500).

**Adaptive launcher icon** finns wirad i appen via Plan 6a T4.

### Vad som fortfarande behövs / kan polishas

1. **Play Store screenshots** — minst 8 stycken (SV + EN) som visar:
   - Cover-screenshot med headline (ASO-rekommenderad: visa Match-vyn med en uppslagen stämpel + tagline)
   - Live-scan med viewfinder + bird-detection-chip
   - Match-resultat med stämpel "ny art"
   - Disambig (2-3 candidate cards)
   - Encyclopedia browse (artlista med thumbnails)
   - Artprofil (PlateFrame + body text)
   - Diary (månadsgrupperad lista med observations)
   - Badges (stämpel-grid med några unlocked + några locked)

2. **Promo-grafik utöver Play Store**:
   - Instagram square-format (1:1)
   - Twitter/X header (1500×500)
   - "Hunting graphic" för Show HN-post
   - Pressrelease-grafik

3. **Onboarding-illustrationer** — finns 3 sidor som idag har minimal grafik. Kan förstärkas med små Caveat-style sketch-illustrationer av en fågel/kikare/dagbok.

4. **Stämpel-design för 10 premium fält-märken** — Plan 6b ska leverera dessa. Idén: varje stämpel är en unik naturalist-illustration (fotspår, fjäder, ägg, sång-notation). Stilen är tunn linjekonst i `MarginaliaInk` med `AccentCopper` accent när unlocked.

### Ikon-prompts för Claude Project

När du ber Claude hjälpa med ikondesign:

- **Var alltid explicit med palett**: skicka in `#EFE7D6` (paper), `#A8552D` (copper), `#3F4F30` (marginalia ink), `#1F3A5F` (stamp navy).
- **Var explicit med stil**: "tunn linjekonst, naturalist-fältdagbok, ingen gradients, ingen glow, ingen 3D, inga emojis-look".
- **Be om SVG-output** för allt som ska skalas.
- **Be om dual-variant** (locked + unlocked) för stämplar.

---

## 5. Strategi & positionering

### USP-rangordning (locked 2026-05-15 efter 5-agents research)

1. **Field Journal-estetik** — ingen konkurrent har detta. Merlin är funktionell men ful. Picture Bird är aggressivt CTA-driven. Birda är social först. Birdy är *vackert* först.
2. **Ärlig osäkerhetshantering** — Match/Disambig/NoBird-systemet är USP:n som ingen säljer. "Honest about uncertainty" är ett förtroendemarkör i en bransch där alla bluffar 99% accuracy.
3. **On-device + offline + ingen cloud** — Privacy-as-feature. Merlin laddar upp foton till Cornell. Picture Bird kör via Glority-cloud. Birdy stannar på telefonen.

**Sälj INTE AI-precision.** Vår AIY V1 ger ~72% top-3 accuracy. Merlin ligger på ~90%. Vi förlorar det racet och ska inte ens spela det.

### Konkurrenter

| App | Styrka | Svaghet vi exploiterar |
|---|---|---|
| **Merlin** (Cornell, gratis) | Bäst-i-klassen AI, 10M downloads, 4.91/5 | Ful UI, cloud-foton, ingen dagbok, ingen progression |
| **BirdNET** | Marknadsledare på audio-ID | Bara audio, ful UI, akademisk känsla |
| **Picture Bird** | Aggressiv ASO | Trial-traps, dark patterns, Glority-cloud, inga svenska arter prioriterade |
| **Birda** | Social, brittiskt community | Inget AI, social-first inte solo-first |
| **Fågelguiden** | Svenskt artregister, etablerat varumärke | Inget AI, ingen modern UX, inget v2026-känsla |

### Soft-launch-strategi

- **Vecka 1-2:** Sverige only. Validera onboarding, retention D7, premium-conversion.
- **Vecka 3-4:** Norden (NO, DK, FI, IS).
- **Månad 2:** DE + NL (största birding-marknader i Europa).
- **Månad 3+:** UK, US, global.

### KPI:er (måltal v1.0 första 90 dagarna)

- D7-retention >12%
- Observations/aktiv/vecka >2
- Premium-conversion 3-5%
- Play Store rating >4.4 (median för fågel-appar är 4.0)
- Recensioner som nämner "vackert" eller "estetik" eller "design" — egen kvalitativ KPI

---

## 6. Monetisering

### Pricing (locked efter research)

- **Free tier:** Foto-ID + Match-flow + Encyclopedia + Diary (basic) + 15 badges
- **Premium yearly: 299 kr/år** (bumped från ursprunglig 199 kr efter research)
- **Premium lifetime: 699 kr engångskonst** (bumped från 499 kr)

### Premium-features (Plan 6b ska leverera)

1. Audio-ID (BirdNET-Lite)
2. PDF-export av dagboken
3. Säsongs-statistik (per-art trender, första/sista observation per art per år)
4. 10 fält-märken (extra badges)
5. Tema-varianter (mörkt papper, månadssäsongs-pappersfärger)

### Viktiga don'ts (från research)

- **Ta bort "spara 60%"-stämpeln** på lifetime — bryter EU Omnibus-direktivet (måste visa pris-referens) + Google Play dark-pattern-policy.
- **Använd Google Play Billing v8, inte v6** (v6 deprecated 2026-08-31).
- **Implementera "Restore Purchases"** — Google Play-krav.
- **Throttla cold-start premium-modalen**: max 1× per 3 dagar, ej alls under första 7 dagarna (grace).
- **Fixa EN-valuta-buggen** (idag visar "299 kr" även för EN-users i andra länder).

### Realistisk prognos år 1

~85 000 kr brutto efter Google's 15% cut (small-business-tier). Inte en kassasuccé — målet är **proof-of-concept för Birdy AB**, inte primary income. Real ramp efter v1.5 (kontosynk + karta + push) + v2 (community).

---

## 7. Marketing & ASO

### Play Store listing

**Titel:**
- SV: `Birdy: Fågel-ID & Fältdagbok`
- EN: `Birdy: Bird ID & Field Journal`

**Short description (80 tecken):**
- SV: `Identifiera fåglar med kameran. En vacker fältdagbok som följer med dig ut.`
- EN: `Identify birds with your camera. A beautiful field journal that goes with you.`

**Keywords (för ASO):**
- Primary: `fågel`, `birds`, `bird identification`, `birdwatching`, `field guide`
- Secondary: `nature journal`, `wildlife`, `binoculars`, `ornithology`, `species ID`
- Lokala (SV): `fåglar Sverige`, `fågelguide`, `skådning`, `art-app`

### Soft-launch checklist (vad jag måste fixa innan 2026-05-18)

- [ ] Skapa personligt Google Play developer-konto ($25)
- [ ] Starta Closed Testing-spår med 12 testare opt-in:ade (familj + vänner + några från Skådarpodden Discord)
- [ ] Privacy policy + Terms of Service publicerade på birdy.se eller liknande
- [ ] Data Safety-formulär ifyllt
- [ ] Screenshots (8 st × SV + EN = 16 st)
- [ ] Feature graphic (1024×500)
- [ ] Hi-res icon (512×512) — finns
- [ ] Store listing-text (SV + EN)
- [ ] Internal Testing → Closed Testing upgrade

### Dag-1-launch-kanaler

1. **Show HN på Hacker News** — tis/tor 15:00 svensk tid (peak US-traffic). Vinkel: "Show HN: I built a bird ID app with Compose Multiplatform that looks like a field journal". KMP-vinkeln drar HN-publik.
2. **Reddit r/birding + r/SideProject** — Reddit gillar privacy-as-feature.
3. **Facebook-gruppen "Fåglar inpå knuten"** (200k+ medlemmar) — svensk skådar-community.
4. **Instagram** — visuella appar performar bra här. Field Journal-estetiken är native-Instagram-content.
5. **Skådarpodden + andra svenska podcasts** — pitch som "soloutvecklare bygger Sveriges första vackra fågel-app".

### Pressrelease-vinklar

- **Tech-vinkel:** "Soloutvecklare bygger fågel-app i Kotlin Multiplatform — privacy-by-design"
- **Design-vinkel:** "Fågel-appen som ser ut som en fältdagbok från 1900"
- **Sverige-vinkel:** "Svensk app utmanar Cornell's Merlin med fokus på Norden"

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
| Personligt Play-konto först, AB-flytt senare | 2026-05-16 | AB-bolagsbildning för långsam för 2026-05-18 Closed Testing-deadline |
| Audio levereras i v1.0 (BirdNET-Lite) | 2026-05-15 | Listen-launcher lovar redan något; inte leverera = trust-brott |
| Premium pricing: 299 kr/år + 699 kr lifetime | 2026-05-15 | Research visade att 199/499 var underpriced |
| Ta bort "spara 60%"-stämpeln | 2026-05-15 | EU Omnibus + Google Play policy |
| Billing v8, inte v6 | 2026-05-15 | v6 deprecated 2026-08-31 |
| Sverige först, sedan Norden, sedan DE/NL | 2026-05-15 | Fågelguiden är svaga konkurrenten; bygg recensioner här |
| Launch-datum 2026-06-01 | 2026-05-15 | 14-dagars test + 2 dagar buffer |
| v1.0 är "Skanna & lär" — inte karta, kontosynk, push, community | 2026-04-30 | Scope-disciplin; v1.5 + v2 är post-launch |

---

## 10. Exempel-prompts att använda i Claude Project

> Claude Project har den här filen som Knowledge. Använd den som **referens**, inte som mall. Be Claude konsultera den när det behövs.

### Ikondesign

> "Designa en stämpel för 'Storgöksobservation' i Birdys Field Journal-stil. Stämpel-form rund, tunn linjekonst, palett: paper `#EFE7D6`, copper `#A8552D`, marginalia ink `#3F4F30`. Två varianter: locked (outline only) + unlocked (filled + AccentCopper). SVG-output, 96×96 viewBox."

### Marketing-copy

> "Skriv 3 varianter av en Show HN-titel för Birdy. Vinkel: soloutvecklare + Compose Multiplatform + privacy-as-feature + Field Journal-estetik. Inte clickbait. HN-publik."

### Store listing

> "Skriv den långa Play Store-beskrivningen (4000 tecken max) på svenska. Använd Birdy brand voice (lugn, vetenskaplig, varm). Inkludera USP-rangordningen (Field Journal-estetik → ärlig osäkerhet → privacy). Avsluta med 'Free to download. Premium tillgängligt.'"

### Screenshot-strategi

> "Vilka 8 screenshots ska jag prioritera till Play Store-listingen för v1.0? Sorterad efter conversion-impact. Med rationale per screenshot."

### Strategi-validering

> "Jag funderar på att lägga till en 'snabb-läge'-knapp på Scan-skärmen för att accelerera till 5 fps. Är det i linje med Birdys positionering? Argumentera båda sidor."

### Pressrelease

> "Skriv en pressrelease (svenska, 300 ord) inför Birdys launch 2026-06-01. Vinkel: design + Sverige + privacy. Tonalitet: saklig, inte säljig. Distribuera till: Skådarpodden, Vårfågeln-magasinet, Sveriges Ornitologiska Förening."

### Brand-konsistens-check

> "Här är ett UI-mockup för en kommande feature [bifoga skärmdump]. Granska mot Birdy Field Journal-tema. Vad bryter mot palette/typografi/ton?"

---

## 11. Referenser & vidare läsning (för Claude att be om om relevant)

Dessa finns i repot (på min lokala dev-miljö) — om jag bifogar dem ad-hoc kan Claude använda dem:

- **Designspec v1:** Full skärm-för-skärm-design för v1.0
- **Plan 6b-spec:** Billing v8 + audio + PDF + stats + 10 fält-märken + ML-fix
- **Research 2026-05-15:** 5 djupanalyser (Konkurrens / ASO / USP / Monetization / Marketing) — totalt ~120 KB strategisk grund
- **Plan 7c-status:** Field Journal redesign (alla komponenter förklarade)
- **Plan 7e-status:** Premium tier (skärm-arkitektur)

Om Claude behöver något av detta — säg till mig så bifogar jag.

---

*Filen uppdaterad: 2026-05-20. Owner: Albin Lindblom. Repo: github.com/anonadrek/birdy.*
