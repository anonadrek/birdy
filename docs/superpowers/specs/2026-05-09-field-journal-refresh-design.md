# Field Journal Refresh — Design Spec

> Designhöjning av befintliga skärmar (Plan 7a + 7b) till en distinkt naturalist-fältdagbok-estetik. Bygger vidare på Mossbädd-paletten men ersätter solid-moss-hero med pappers-bg och introducerar handskriven typografisk accent.

**Datum:** 2026-05-09
**Status:** Brainstormad och godkänd. Implementationsplan skrivs härnäst.
**Pipeline-position:** Mellan Plan 7b (just shipped) och Plan 7c (Match-flow). 7c bygger ovanpå nya design-systemet.

---

## 1. Varför

Plan 7b lämnade appen funktionellt komplett men visuellt platt. Tre konkreta problem:

1. **Hero-zonerna känns klumpiga.** Solid moss-grön rektangel (HeroMossLight → HeroMossDeep) hugger av sig från cream-bakgrunden utan flyt. Specifikt påpekat: Badges "Discoveries" – grönt block + grid på cream = osammanhängande.
2. **Ingen röd tråd.** Varje skärm har sin egen layout men inget återkommande motiv binder dem ihop. Bottom-bar och italic serif räcker inte.
3. **För simpelt / inte levande.** Material 3 + moss + koppar ger en korrekt men generisk känsla. Birdy som naturalist-/observatör-app saknar personlighet att sticka ut.

Användarens citat: *"Jag vill ha en rödare tråd igenom alla flikar och jag vill sticka ut mer."*

## 2. Goal

Höj appens visuella identitet till **Field Journal** – en naturalist-fältdagbok från en alternativ värld där varje fågelfynd är ett stämpel-/sigill-moment och varje skärm en sida i en bunden volym. Designsystemet ska:

- Ge en **stark röd tråd** via återkommande motiv (mikro-label, italic+handskriven headline, ❦-rule, stämpel-glyph)
- Få varje skärm att **flyta** istället för att klippas av (papper-bg överallt, inga hårda hero-rektanglar)
- Vara **distinkt** nog att kännas som "Birdy" och inte "ännu en Material 3-app"

## 3. Visuell riktning: Field Journal

### Aestetik

18-1900-tals naturalist-fältdagbok / botanisk plate-bok / frimärks-album. Tänk: pressa-blad-mellan-sidor, bläckpenna-marginalanteckningar, postmaster-stämpel som markerar fynd. Inte skräp-scrapbook – ren typografi och rena linjer, men med en distinkt papper-och-bläck-personlighet.

### Den röda tråden (recurring page pattern)

Alla huvudskärmar följer samma typografiska intro:

```
[MIKRO-LABEL · NO XX]    ← Inter caps, copper, 0.28em letterspacing
[Italic-headline med handskriven accent.]    ← DM Serif Display italic + Caveat
[Handskriven sub-line.]    ← Caveat, mossgrön
─── ❦ ───    ← OrnamentRule: gradient line + ❦ + gradient line
[Innehåll]
```

Exempel:
- Listen launcher: `CATCH · NO 0` / `Three ways to *catch.*` / `A stamp waits in each.`
- Archive: `ARCHIVE · 700 SPECIES` / `*Birds.*` / `Search, filter, learn.`
- Badges: `DISCOVERIES · NO 12` / `*Twelve* found.` / `Twenty-five waiting in the field.`
- Lifelist: `MY COLLECTION · VOL. I` / `*Albin's* journal.` / `Twelve days. One found.`
- Species: `SPECIES · NO 248` / `*Svartmes.*` / `Periparus ater · Paridae`

(Accent-orden, omgärdade med `*…*`, renderas i Caveat-handskrift med koppar-färg och `-3°` rotation.)

### Stämpel-systemet

Stamps är det andra återkommande motivet, både visuellt och konceptuellt (vi har redan `stamp_number`):

- **Locked stamp:** dashed border 1.5px copper @ 40% alpha, paper-toned bg, "?" i italic serif, namn-label nedanför.
- **Unlocked stamp:** solid 2px AccentCopper border, copper-tinted bg (alpha ~0.12), `№N` i Caveat copper, ikon i DM Serif italic, **roterad -3°** för "stämplad-känsla". Namn-label i DM Serif italic, fet.
- **In-progress stamp:** solid border copper @ 60%, bg paper-toned, optional progress-text i Caveat under namnet ("3/5").
- **Mini-stamp** (Lifelist entry-row, Archive species-row): 32-36dp cirkel, solid 2px AccentCopper, `№N` i Caveat, roterad -4°.

## 4. Typografi

### Fonts (nya, lägga till)

| Roll | Font | Källa | Filer |
|---|---|---|---|
| Display / headline | **DM Serif Display** (regular + italic) | Google Fonts (OFL) | `DMSerifDisplay-Regular.ttf`, `DMSerifDisplay-Italic.ttf` |
| Handskriven accent | **Caveat** (regular, bold) | Google Fonts (OFL) | `Caveat-Regular.ttf`, `Caveat-Bold.ttf` |
| Body / UI sans | (befintlig) Inter eller system-sans | — | (oförändrat) |

**Crimson Pro fasas ut** – ersätts av DM Serif Display för all nuvarande Crimson Pro-användning. (Crimson var aldrig faktiskt bundlad – `FontFamily.Serif` defaultar till device-serif. DM Serif Display blir nu bundlad asset.)

### Användning per komponent

| Komponent | Font / Stil |
|---|---|
| Mikro-label (NN · No XX) | Inter, 9sp, weight 700, letterSpacing 0.28em, caps, AccentCopper |
| Headline | DM Serif Display Italic, 30-36sp, line-height 1.05, TextOnPaper |
| Headline accent-ord | Caveat Bold, 1.15× headline-storlek, AccentCopper, rotate -3° |
| Sub-line | Caveat Regular, 16-18sp, MossGreen, line-height 1.2 |
| Stat-siffra | DM Serif Display Italic, 28sp, AccentCopper |
| Stat-label | Inter, 8sp, weight 700, letterSpacing 0.22em, caps, MossGreen |
| Stamp №-nummer | Caveat Bold, 11sp, AccentCopper |
| Stamp namn-label | DM Serif Display Italic, 8-10sp, TextOnPaper |
| Marginalia / brödtext | DM Serif Display Regular, 12-13sp, line-height 1.5 |
| Marginal-note (handskriven citat) | Caveat Regular, 14sp, MossGreen, vänster-border AccentCopper |
| Pill / chip | DM Serif Display Italic, 10sp |
| Bottom-bar etikett | Inter, 10sp, weight 600 |
| Search-fält placeholder | Caveat Regular, 14sp, mossgrön @ 50% |

## 5. Color tokens

### Nya tokens (lägg till `Color.kt`)

```kotlin
// Field Journal paper background — gradient (replaces solid MossCreme as primary bg)
val PaperTop = Color(0xFFF0E7D0)    // ljus pergament uppe
val PaperBottom = Color(0xFFE6D8B8) // mörkare papper nere

// Paper texture overlay (radial-gradient dots @ low alpha — inte token i sig, applied via Modifier)
// Dots: rgba(60,80,40,0.06–0.10) @ varierande positioner

// Stamp-tints
val StampLocked = Color(0x668C5A3C)   // 40% AccentCopper för dashed border
val StampLockedBg = Color(0x99E8E2D2) // 60% MossCreme
val StampUnlockedBg = Color(0x1F8C5A3C) // 12% AccentCopper

// Existing tokens — beteende-förändring (inte färg-förändring)
// AccentCopper #8C5A3C       — fortfarande primär CTA + accent
// AccentCopperLight #C19073  — fortfarande hover/disabled
// MossGreen-familjen          — kvar men användning minskar:
//                              ingen mer hero-rektangel, men kvar för
//                              bottom-bar-ikoner, sub-text, plate-bg
// SandCreme #D8D0BC          — fortfarande chip + plate-frame bg
// TextOnCreme #2A3525        — fortfarande primär text
// TextOnHero #F0EAD8         — endast på faktisk hero-bild (Species plate)
```

### PaperTexture (ny Modifier eller Composable)

```kotlin
fun Modifier.paperTexture(): Modifier = drawBehind {
    // Radial-gradient dots @ låg alpha, deterministiska positioner
    // (för att undvika animation/jank — pre-renderad pattern via ImageBitmap, eller
    // 4-6 fasta drawCircle-anrop)
}
```

## 6. Per-screen redesigns

För varje skärm: vad ändras (layout/komponent) + vad behålls.

### 6.1 Listen launcher

**Ändrar:**
- Hero-block (moss gradient) → paper-bg + Field Journal-intro (`CATCH · NO 0` / `Three ways to *catch.*` / `A stamp waits in each.` / ❦-rule).
- 3 mode-cards: bg ändras från SandCreme till papper+inset, ikon-box blir cirkel med dashed border (locked) / solid (active), rubrik DM Serif italic, beskrivning Caveat, PREMIUM-pill behålls men i copper.

**Behåller:** card-radien, layout 1-kolumn, stick-out för "Look" (live-method), bottom-bar.

### 6.2 Archive (browse-listan)

**Ändrar:**
- Hero: `ARCHIVE · 700 SPECIES` / `*Birds.*` / `Search, filter, learn.` / ❦-rule. Inget grön-block.
- Search-fält: rounded 24dp, papper-bg, Caveat placeholder.
- Filter-chips: DM Serif italic, 10sp, papper-bg + outline (default), copper-fill (selected).
- Sort-pill (A-Z / Recent): samma chip-stil, höger-aligned.
- Listrader: thumb (36dp), DM Serif italic namn, Caveat-italic latin-namn under, **stamp-tag** №N höger-aligned (om species är observerad). Subtle bottom-divider (1px copper @ 10%).

**Behåller:** scroll-position, search/filter-logik, family-grouping under huven (men filter-chips visuella).

### 6.3 Badges

**Ändrar:** (störst diff – är användarens uttalade huvudpunkt)
- Hero ersätts: `DISCOVERIES · NO N` / `*N* found.` / `M waiting in the field.` / ❦-rule.
- Progress-bar ersätts av **stamp-track**: 25-cell rad (5×5 eller 5×N beroende på antal), filled = solid copper med vit siffra, empty = dashed copper border.
- Recently unlocked-carousel: behåller LazyRow men varje badge-card blir ett **stamp-card** (papper-bg, dashed-frame, datum i Caveat).
- Locked-grid (4-kolumns idag) → **3-kolumns stamp-grid** (större, mer luft, känns mer som ett album-uppslag): varje cell = locked/in-progress/unlocked stamp enligt §3.2.

**Behåller:** unlock/locked-state-logik, click-handler för locked (snackbar), `BadgeStringMap.nameFor()`.

### 6.4 Lifelist (loaded + empty)

**Loaded ändrar:**
- Hero: `MY COLLECTION · VOL. I` / `*{firstName}'s* journal.` / `{N} days. {M} found.` / ❦-rule.
- Stat-row (Species · Stamps · Days): siffra DM Serif italic copper, label Inter caps, separator "·" i DM Serif italic copper @ 50%.
- Recent entries: papper-card (rounded 10dp, white @ 40%, copper border @ 15%), mini-stamp №N copper (roterad -4°), name DM Serif italic, meta Caveat, %-confidence i Caveat copper höger.
- Empty-rad efter senaste: dashed mini-stamp + "Awaiting next find… / A page lies blank." i Caveat italic, opacity 0.55.

**Empty-state ändrar:**
- Hero: `MY COLLECTION · VOL. I` / `*My* collection.` / `An empty page, for now.` / ❦-rule.
- "Scan first bird" CTA: copper button med Caveat-text "*Scan* first bird" (handskriven accent på "Scan").

**Behåller:** observation-detail-navigation, månadsgruppering om vi vill ha kvar (annars tas bort till förmån för ren entries-lista).

### 6.5 Species Profile

**Ändrar:**
- Hero ersätts av Field Journal-intro: `SPECIES · NO {idx}` / `*{name}.*` / `{latin} · {family}`.
- Foto blir **plate**: papper-frame med moss-tonad bg-strip, foto centrerat, `Pl. {idx} — {name}, in nature` caption i Caveat italic mossgrön.
- Pills (abundance, family, IUCN): DM Serif italic, copper-fill för accent (Common/Common-here), outline för rest.
- Description: drop-cap (första bokstav 26sp DM Serif italic copper, float left), brödtext DM Serif regular 12sp.
- "Marginal note" (ny komponent): handskriven Caveat citation under description med vänster-border copper – kort poetisk one-liner ("A small thing in the spruce."). **Källa:** valfritt nytt YAML-fält `marginalia` i `species/*.yaml` (string, optional). Om fältet saknas: rendera inte komponenten. Inget auto-gen i v1 av detta spec — content-pipeline kan fyllas senare per familj. Inledningsvis tom för alla species; manuellt tillagd för ~5-10 demo-species så komponenten visas i screenshots.

**Behåller:** collapsing toolbar-beteende, save-flow, IUCN-källa, Coil-loading.

### 6.6 Observation detail (Lifelist → tap entry)

**Ändrar:**
- Hero med foto: behåller foto men adderar paper-frame under (plate-stil), titel `*{name}.*` med stamp №N höger som postmark.
- Note-sektion: Caveat-prompt "Add a note in the margin…", area med papper-bg.
- DETAILS-block: Inter caps labels, DM Serif italic värden.
- Footer-länk till Species: "*Periparus ater* — visit profile" i copper Caveat.

**Behåller:** note-edit-flow, delete-confirm, navigation till species.

### 6.7 Bottom-bar (gemensam)

**Ändrar:**
- Container-bg: papper-toned `#EFE8DA` istället för Material 3 lavendel-default.
- Selected indicator: koppar-fill capsule (inte lila pill), text + ikon i AccentCopper, weight 700.
- Inaktiv: ikon + label i MossGreen @ 60% opacity, weight 500.

**Behåller:** 4 destinationer (Listen, Archive, Lifelist, Badges), höjd 72dp, ikon-set.

## 7. Komponenter att lägga till / ändra

### Nya composables (commonMain)

| Komponent | Path | Purpose |
|---|---|---|
| `MicroLabel(text)` | `ui/components/MicroLabel.kt` | "NN · No XX"-formatet, Inter caps copper |
| `JournalHeadline(plain, accent, ...)` | `ui/components/JournalHeadline.kt` | Italic serif + Caveat accent-ord (-3° rot) |
| `JournalSubLine(text)` | `ui/components/JournalSubLine.kt` | Caveat sub-line (mossgrön) |
| `OrnamentRule()` | `ui/components/OrnamentRule.kt` | Gradient line + ❦ + gradient line |
| `JournalIntro(label, headlineText, subText)` | `ui/components/JournalIntro.kt` | Combo-component som wrapper för 4 ovan |
| `StampSeal(state, number, glyph?, name?, modifier)` | `ui/components/StampSeal.kt` | Den återkommande stämpel-cirkeln (locked/in-progress/unlocked) |
| `MiniStamp(number, modifier)` | `ui/components/MiniStamp.kt` | Liten stamp för inline-bruk i listrader |
| `PaperBackground()` modifier | `ui/theme/PaperBackground.kt` | `Modifier.paperBackground()` – gradient + texture-dots |

### Ändrade composables

- `HeroZone` → tas bort eller blir `JournalIntro`-wrapper (riv ut moss-block-implementation)
- `BadgeProgressBar` → ersätts av `StampTrack(filled, total)` med 25-cell layout
- `BadgeGridCell` → använder `StampSeal` istället för custom Box
- `BadgeRecentCard` → blir stamp-card (samma `StampSeal` med större size + datum)
- `ItalicMixedText` → behålls för bakåtkompatibilitet om det finns nån anrop, men nya skärmar använder `JournalHeadline` direkt

### Tema-tokens (Color.kt + Type.kt)

- Lägg till `PaperTop`, `PaperBottom`, `StampLocked`, `StampLockedBg`, `StampUnlockedBg`
- Lägg till `BirdyTypography.microLabel`, `journalHeadline`, `journalAccent`, `journalSub`, `stampNumber`, `marginalia`
- Markera nuvarande `headlineMedium`/`headlineSmall` deprecated om vi ersätter dem (annars: behåll men lägg till ny lager)

### Font assets

- `composeApp/src/commonMain/composeResources/font/dmserifdisplay-regular.ttf`
- `composeApp/src/commonMain/composeResources/font/dmserifdisplay-italic.ttf`
- `composeApp/src/commonMain/composeResources/font/caveat-regular.ttf`
- `composeApp/src/commonMain/composeResources/font/caveat-bold.ttf`

(Nedladdning från Google Fonts under OFL-licens. Inga runtime-fontfetch.)

## 8. Out of scope

- **Settings + Onboarding**: behålls med befintlig styling tills vidare. Kan anpassas i Plan 6 (Polish) om scope tillåter.
- **Scan / PhotoAnalyze / ClassificationResult (kameran-skärmar)**: kameravyn behåller svart bakgrund (kamera-foto syns) men chip + crosshair behåller nuvarande styling. Klassifikations-resultat-skärmen anpassas dock med journal-intro `MATCH · NO XX` / `*{topMatch}*?` / `A {confidence}% match.` (kan rymmas inom scope).
- **Plan 7c (Match-flow) nya skärmar**: Match-skärm + Disambig byggs i Plan 7c **ovanpå** detta design-system. Inte här.
- **Animations / motion**: gradient-pan, stamp-rotation-on-press etc. lämnas till en separat polish-iteration.
- **Dark mode**: appen är light-only just nu. Inget ändras här.

## 9. Plan-dependencies

Detta paket **måste shippa före Plan 7c** (Match-flow). Anledning: 7c lägger till ~3 nya skärmar (Match, Disambig, threshold-edit). Att bygga dem mot gamla design-systemet och sedan migrera vore bortkastad jobb.

**Default-numrering (rekommenderad):**
- Detta paket = **Plan 7c (Field Journal)** *(omdöpt — Match-flow flyttas)*
- Tidigare Plan 7c (Match-flow) → **Plan 7d (Match-flow)**
- Plan 6 (Polish + Play Store) – fortsätter pausad

Motivering: 7c-namnet i CLAUDE.md är just nu reserverat men ingen plan-fil är skriven — namnbytet är gratis. Numrera 7d (Match-flow) sist eftersom den bygger ovanpå design-systemet.

(Alternativ: behåll 7c-namnet för Match-flow och kalla detta `Plan 7d (Field Journal)` med not "implementeras före 7c" — fungerar men är mer förvirrande i loggar och tags. Inte rekommenderat.)

## 10. Testing

- **Per-skärm device-verify** på SM-S918B (Galaxy S23 Ultra) som vanligt.
- **Visual regression**: 9+ screenshots ersätter `docs/superpowers/screenshots/v0.7.0b-screens/` när nästa milstolpe taggas (sannolik tag: `v0.7.0c-field-journal`).
- **Font-loading-test**: bekräfta att DM Serif + Caveat laddas korrekt på cold start (ingen FOUT > 100ms).
- **Accessibility**: kontrast-kontroll – Caveat copper på paper-bg ska vara ≥4.5:1 (preliminär kontroll: 7.6:1 för #8C5A3C på #F0E7D0 ✓).
- **Compose-test**: snapshot-test för `JournalIntro`, `StampSeal`-states, `OrnamentRule`.

## 11. Acceptanskriterier

Plan är klar när:

- [ ] Alla 6 skärmar i §6 visuellt matchar mockups (jämför mot `.superpowers/brainstorm/.../full-app-c.html` om kvar, annars ny screenshot-set)
- [ ] DM Serif Display + Caveat bundlade som compose-resources, laddas på alla supported devices
- [ ] Inga gröna hero-block kvar någonstans (HeroZone gammal-stil borttagen)
- [ ] Mikro-label · italic-headline · handskriven sub · ❦-rule återkommer på Listen, Archive, Badges, Lifelist, Species
- [ ] StampSeal-systemet (locked/in-progress/unlocked) konsekvent över Badges, Lifelist, Archive
- [ ] Bottom-bar paper-toned (ej Material 3 lavendel-default)
- [ ] `./gradlew build` grön
- [ ] `./gradlew :composeApp:testDebugUnitTest` grön
- [ ] Device-verify på fysisk enhet med 9+ nya screenshots
- [ ] CLAUDE.md uppdaterad: status-rad + plan-of-plans-tabell
- [ ] Ny tag pushad: `v0.7.0c-field-journal` (default per §9-rekommendationen)

## 12. Risk / öppna frågor

1. **Font-storlek i APK**: DM Serif Display + Caveat ≈ 200-300 kB extra. Acceptabelt (modellen är 3.5 MB).
2. **Caveat-läsbarhet**: handskrift läses sämre på små storlekar. Mitigation: Caveat ENDAST för accent-ord (kort), sub-line (kort), stat-nummer-prefix `№N` (kort), och kort marginalia. Aldrig brödtext.
3. **`-3°` rotation på stämplar**: kan se "playful/scrapbook" snarare än "naturalist". Mitigation: konstant -3°/-4° (inte randomized), och endast på unlocked / mini-stamps. Locked är raka.
4. **Plan 5b unlock-bottom-sheet**: redan styled i Plan 7b. Måste re-styles igen för att matcha (StampSeal istället för cirkel-emoji).
5. **Plan-namnval (§9)**: default = rebrand till 7c, Match-flow → 7d. Bekräftas vid plan-skrivning.
6. **Empty-state-Lifelist text på engelska**: dagens app har strängar både sv/en. Spec-mockuparna är på engelska. Real implementation måste lokalisera (svenska-versioner: "Min samling · Vol. I", "*Albins* dagbok.", "Tolv dagar. Ett fynd.").

---

**Klar för implementation-plan.** Nästa steg: invoke `superpowers:writing-plans` med detta som input.
