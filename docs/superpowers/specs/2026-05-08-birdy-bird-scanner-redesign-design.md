# Birdy Bird Scanner — Redesign Design Spec

**Datum:** 2026-05-08
**Status:** Utkast — väntar på användargranskning
**Spec-typ:** Post-v1.0 redesign (kandidat för Plan 7, körs efter Plan 6 / Play Store-release)
**Föregående:** Plan 5b (Gamification) klar `v0.5.0b-gamification`. Plan 4b (Real TFLite) 10/17 in progress. Plan 6 (Polish + Play Store) väntar på 4b.
**Författare:** Albin Lindblom + Claude Code (brainstormingsession 2026-05-08)

---

## 1. Bakgrund och syfte

Birdy v1 är funktionellt klar (med Plan 4b + 6 kvar). Den nuvarande Mossbädd-implementationen (Plan 1) är solid men är **byggd skärm-för-skärm** över sex planer — visuellt språket har drift och flera skärmar saknar den editorial-känsla som finns i hero-zonen. Användaren har levererat fem mockups som tar redesignen från "fungerande nordisk natur-app" till "samlar-bok du vill öppna varje dag".

Redesign:en är **inte bara visuell** — den introducerar:

- **Stamp-collecting-metafor** som central narrative-tråd (varje observation får ett sekventiellt `stamp_number`).
- **Ny IA**: bottom-bar går från `Skanna · Album · Dagbok · Märken` → `Listen · Archive · Lifelist · Badges`. "Listen" är en multi-modal hub (audio som premium-stub, kamera, foto).
- **Onboarding-flow** (3 sidor, premium-marker som skiner).
- **Personalisering**: användarens namn från onboarding visas på Lifelist-hero ("Albins samling.").
- **Geografi-neutralitet** i alla titlar (förbereder v1.5+ utanför Sverige).
- **Match-flow med threshold-logik** ersätter ClassificationResultScreen (Plan 4a).

Tonen behålls från Plan 5b: jordnära, editorial, ingen konfetti. Stamp-momentet (Match-skärmen) är celebrationen — resten är ritual.

**Redesign:en är klar när:**

- Bottom-bar visar de fyra nya flikarna med ikoner + textetiketter.
- Onboarding körs första gången appen startas (3 sidor, namn lagras i DataStore, premium-sparkle visas men är icke-aktiv).
- Listen-fliken öppnar hub-launcher (Audio locked-card + Camera + Photo).
- Archive heter `Birds.` och har chips + sort-toggle istället för bottom-sheet.
- Lifelist visar hero-stats (`X arter · Y stämplar · Z dagars svit`) + stamp-rader med stamp-nummer-badge.
- Match-flow har två viewports: Match-skärm (≥75%) och Disambig-skärm (35–74%); <35% returnerar till kameran med toast.
- Badges har `Märken.`-titel + progress-bar i hero, "Senast upplåsta"-carousel, "Att upptäcka"-grid (4 kol).
- Befintliga skärmar (species-profile, camera, photo, unlock-bottom-sheet) har applicerat det nya visuella språket.
- En settings-skärm finns där användaren kan byta namn och språk.
- Migration backfillar `stamp_number` chronologiskt över befintliga observationer.
- Tag `v0.7.0-redesign` pushad.

---

## 2. Visuell kalibrering (utökar Plan 1's tema)

**Färgpaletten (Plan 1) behålls helt.** Tre nya tokens läggs till:

| Token | Hex | Roll |
|---|---|---|
| Text on hero (offwhite warm) | `#FFFCF0` | Primär hero-text (tidigare `#F0EAD8`/`#F5EFD8`). Lite varmare och vit-tonad. |
| Hero italic accent (light copper) | `#E0A47C` | Italic copper på hero-text (tidigare `#D5946F`). Lite ljusare för pop mot mossgrönt. |
| Premium gold gradient | `#FFE8B5 → #E8C374 → #B88944` | Endast på premium-sparkle (icke-aktiv i v1). |

**Typografi-kalibrering:**

- **Crimson Pro** används som tidigare. Stilarna spridda mellan 500/600/700, regular + italic.
- **Italic-mixed-rubriker** är ett nytt mönster: ord eller halv-ord i italic copper (`Birdy.`, `Albin's *collection*`, `Three ways to *catch*`). Implementeras med inline-styled `Text` med `SpanStyle(fontStyle = Italic, color = AccentCopper)`.
- **Body weight 600** (var 500) — texten lyfts mot mossgrön bakgrund.
- **Text-shadow på hero-text** — mjuk drop-shadow (rgba 0,0,0,0.3) för djup utan att se "neon" ut. Kompositions-ekvivalent i Compose: `Modifier.drawBehind { drawText(... color = shadowColor) }` eller alpha-text-overlay.

**Layout-mönster:**

- Hero-zon har **rundade botten-hörn** (24dp) — hero "släpper" innehållet under sig istället för att vara en kant-till-kant-banner. Mönstret återanvänds över alla flikar.
- **Stat-rader i hero** (Lifelist + Badges): tunn copper separator-linje (1px alpha 0.25), siffror i 22–28px copper med soft glow, labels 9px offwhite uppercase letter-spacing 0.18em.
- **Cirkulär thumb är universell**: 44–56px för rader, 80px för carousel, 180px för Match-hero. Alltid 1.5–3px cream-ring (`#F0EAD8`) + drop-shadow.
- **Stamp-nummer-badge** (#218 etc): copper-pill `#8C5A3C` med 1.5px cream-ring, placerad i nedre högra hörnet på thumb. Crimson Pro 700, italic.

**Visuella mockups (autoritativ källa för per-skärm-detaljer):**

Allt visuellt språk som inte täcks av denna spec finns i HTML-mockups:

```
.superpowers/brainstorm/162773-1778192055/content/
  onboarding-v11.html        — Onboarding (3 pages, premium variant C)
  listen-launcher-v1.html    — Listen hub
  archive-v2.html            — Archive (vit hero + image thumbs)
  lifelist-v1.html           — Lifelist (hero stats + stamp-rows)
  match-flow-v1.html         — Match + Disambig screens
  badges-v1.html             — Badges hero + carousel + grid
```

`.superpowers/` är gitignore:ad — mockupsen är inte i commit-historik. Om de tappas behövs ny brainstormingsession.

---

## 3. Information-arkitektur

**Bottom-bar går från:**

```
Skanna  ·  Album  ·  Dagbok  ·  Märken
```

**Till:**

```
Listen  ·  Archive  ·  Lifelist  ·  Badges
```

(Alla tab-namn på engelska oavsett app-språk — editorial-feel, korta.)

| Tab | Skärm | Ersätter |
|---|---|---|
| **Listen** | Hub-launcher med 3 kort (Audio locked, Camera, Photo) → väljer mode → Match-flow | `ScanScreen` (Plan 4a) som default-tab |
| **Archive** | `Birds.`-titel + chips + alfabetisk lista över ~700 arter | `EncyclopediaScreen` (Plan 3) |
| **Lifelist** | `Albins samling.`-titel + hero-stats + stamp-rader sorterade nyast först | `DiaryScreen` (Plan 5a) |
| **Badges** | `Märken.`-titel + progress-bar + carousel + grid | `BadgesScreen` (Plan 5b) |

Tab-ikonerna är samma SVG-set som onboarding (öra, bok-stack, stamp-grid, stjärna).

---

## 4. Onboarding (ny, första-launch)

Mockup: `onboarding-v11.html`.

**Förekomst:** En gång, första gången appen öppnas. Spåras via DataStore-nyckel `has_seen_onboarding: Boolean`. Ingen "tillbaka till onboarding" från settings i v1 (kan läggas till senare).

**Flow:** 3 sidor med horizontal pager + 3 dots. Skip-länk i top-right på alla sidor (hoppar till sida 3 så användaren ändå kan ange namn — tomt namn tillåtet, fallback `Min` / `My` används).

### Sida 1 — Brand
- Mossgrön gradient hero (full-screen, mörkare botten).
- Breadcrumb `VÄLKOMMEN` / `WELCOME` i copper, 16px letter-spacing 0.32em.
- Big italic copper headline `Birdy.` (68px).
- Body 20px weight 600 — `Lyssna, känn igen, *samla*.` (italic copper på "samla").
- Mindre body 16px italic — `En fält-följeslagare bland fåglar — en stämpel i taget.`

### Sida 2 — Översikt
- Headline 56px **centrerad** — `Fyra ställen att *vara* på.` (italic copper på "vara"). Breadcrumb `ÖVERBLICK` vänsterställd.
- Feature-panel med 4 rader: Listen / Archive / Lifelist / Badges. Varje rad har:
  - Cream-ringad copper-icon-square (40px, kopparram + soft glow)
  - Namn i 19px Crimson Pro 700 offwhite
  - Beskrivning med italic copper-verb (`*Lyssna och fånga* — kamera, ljud eller foto.`, `*Bläddra och utforska* tusentals fåglar.`, `*Samla* stämplar — din lista växer för varje fynd.`, `*Förtjäna märken*, håll sviter levande.`)
- Pager-dots vid `bottom: 28px`.

### Sida 3 — Namn
- Hero med rundad botten + premium-marker (sparkle + "PREMIUM"-label staplade) i top-right. **Ej tappable i v1** — bara visuell signal att premium-tier kommer.
- Headline `Vad ska vi kalla din *samling*?` (italic copper på "samling").
- Sand-cream input med Crimson Pro 24px placeholder ("Albin").
- Helper-text italic 13px — `Du kan ändra detta senare i inställningarna.`
- Pager-dots vid `bottom: 100px` (ovanför CTA).
- CTA i koppar `Börja samla →` / `Start collecting →`.

**Lagring:** Vid CTA-tap, om input är tomt → använd fallback (`Min`/`My`); annars använd det användaren skrev. Skriv till DataStore som `user_name: String` + `has_seen_onboarding: true`. Onboarding visas aldrig igen.

---

## 5. Listen launcher (ny, ersätter Skanna-tab)

Mockup: `listen-launcher-v1.html`.

**Layout** — Hero-zon + tre stack:ade kort + bottom-nav.

### Hero
- Mossgrön gradient med rundade botten-hörn (24dp).
- Breadcrumb `LISTEN` i copper.
- Headline 30px italic-mixed — `Tre sätt att *fånga*.` / `Three ways to *catch*.`.
- Sub italic 14px offwhite 86% — `En stämpel väntar i varje.` / `A stamp waits in each.`.

### Tre kort (vertikal stack, 12dp gap)

| # | Kort | State |
|---|---|---|
| 1 | **Lyssna** (audio) | **Locked** — sand-grå kort, sparkle + "PREMIUM"-label uppe-höger, ingen chevron. Beskrivning: "Identifiera via läte — kommer snart." Tap → snackbar `Audio kommer snart` / `Audio coming soon`. |
| 2 | **Kika** (camera) | **Primary** — pale-cream gradient med kopparkant + soft copper glow + kopparfärgad chevron. "Realtidsskanning via kameran." Tap → CameraScreen. |
| 3 | **Leta upp** (photo) | **Secondary** — standard cream-kort, kopparfärgad chevron. "Välj foto från galleri eller ta nytt." Tap → PhotoAnalyzeScreen. |

### Bottom-nav
- 72dp, cream surface, 4 tabs. Listen aktiv → koppar-färg + bold + tjockare stroke.

---

## 6. Archive

Mockup: `archive-v2.html`.

**Ersätter** `EncyclopediaScreen` från Plan 3 (browse-vyn). Tap på rad → species-profile (Plan 3, restyle:ad — se §10).

### Hero
- Mossgrön gradient med rundade botten-hörn.
- Breadcrumb `ARCHIVE` (offwhite 88%).
- Italic vit headline `Birds.` (38px) — geografi-neutral.
- Sub italic — `Sök, filtrera, lär.` / `Search, filter, learn.`.

### Sökfält
- Cream input med koppar-search-ikon, Crimson Pro 14px italic placeholder.

### Chips (horizontal scroll)
- `Alla · Sångfåglar · Vatten · Rovfåglar · Ugglor · Vadare`. Aktiv chip = koppar-fyllning, vit text. Andra = cream med moss-text.
- Chips mappar mot `taxonomy.ioc_order` i species-YAML (verifierat — fältet finns).
- Tap chip → filtrerar lista. "Alla" är default.

### Sort-toggle (sektionsheader)
- Synlig knapp uppe-höger: ikon + "A–Ö" / "Familj" / "Senast tillagd". Tap cyklar.
- Sektionsheader: `B · 14 arter` när A–Ö; `Mesar · 8 arter` när Familj; `Senaste · 14 nya` när "Senast tillagd".

### Rader
- 44px rund thumb (cover-bild från species-YAML hero-image), namn 16px Crimson Pro 700, vetenskapligt 12px italic muted.
- Status-dot (7px) till höger: grön (`abundance: allmän`) / gul (`mindre vanlig`) / röd (`sällsynt`).
- För arter med ≥1 stamped observation: liten kopparpill `STÄMPLAD` / `STAMPED` (10px letter-spacing 0.12em) bredvid status-dot.

---

## 7. Lifelist

Mockup: `lifelist-v1.html`.

**Ersätter** `DiaryScreen` från Plan 5a. Schemat på `Observation` utökas — se §11.

### Hero
- Mossgrön gradient + rundade hörn.
- Breadcrumb `LIFELIST`.
- Headline 32px — `*Albins* samling.` / `*Albin's* collection.` (italic copper på namn). Fallback `Min samling.` om DataStore name saknas.
- 3 stats nedanför, separerade av tunna copper-linjer:
  - **Stat 1** (fast): `47 ARTER` / `47 SPECIES` — distinkta arter användaren stämplat (count distinct `species_q_id`).
  - **Stat 2** (fast): `218 STÄMPLAR` / `218 STAMPS` — total `Observation`-count.
  - **Stat 3** (toggle): default `28 DAGARS SVIT` / `28 DAY STREAK` (samma streak-data som Plan 5b). Tap chevron → cyklar till `47 ARTER I ÅR` / `12 ARTER DEN HÄR MÅNADEN` / `LÄNGSTA SVIT: 42`. Val sparas i DataStore.

### Section-header
- `Senaste · 218 stämplar` med sort-toggle uppe-höger (samma mönster som Archive). Tap cyklar mellan `Senaste` / `Stämpel #` / `Art`.

### Stamp-rader
- 50px rund thumb med stamp-nummer-badge i nedre högra hörnet (#218 i Crimson Pro 700 italic copper-pill).
- Namn 16px Crimson Pro 700.
- Meta 12px: `Parus major · för 2 h sen` (italic på relativ tid).
- Match-% till höger, färgkodad: grön ≥80%, gul 60–79%, röd <60%.
- "Just stamped"-fade: senaste raden har en svag kopparton-gradient från vänster i 24h efter save. Försvinner sen.

### Detail-screen (befintlig från Plan 5a)
- Restyle:as så hero matchar Lifelist (rundad gradient + stamp-nummer-badge på hero-image). Edit-note + delete-confirm samma logik.

---

## 8. Match-flow (ny, ersätter ClassificationResultScreen)

Mockup: `match-flow-v1.html`.

**Threshold-logik** (klassificerings-pipeline från Plan 4a/4b returnerar topp-N kandidater):

| Topp-1 confidence | Skärm |
|---|---|
| **≥ 75%** | Match-skärm direkt |
| **35–74%** | Disambig-skärm (top-3 kandidater) |
| **< 35%** | Tillbaka till kameran med toast `Kunde inte identifiera — försök igen` / `Couldn't identify — try again` |

Trösklarna är konfigurerbara konstanter i `domain` så vi kan tweaka utan UI-change.

### Match-skärm (high confidence)
- Full-viewport mossgrön gradient (mörkare än hub).
- Top-right close-button (cirkulär, alpha-cream icon, 32dp).
- Breadcrumb `STÄMPEL #219` / `STAMP #219` — visar nummer som **kommer** slås (preview, ej committat).
- Big italic copper `Match!` (56px med glow).
- Sub italic — `Vi tror den här är den.` / `We think this is the one.`.
- 180dp rund hero-image med 3px cream-ring, copper-halo (6dp alpha 0.2), drop-shadow. Stamp-badge `#219` i nedre högra hörnet (kopparpill 16px italic).
- Art-info centrerad: namn 28px → vetenskapligt 14px italic muted → `Match · 92%` med italic copper-siffra 18px.
- Primär CTA `Stämpla i samlingen →` / `Stamp my collection →` (koppar-knapp).
- Sekundär link `Inte rätt? Visa fler kandidater` / `Not quite? Show more candidates` → öppnar Disambig-skärm med samma top-N.

### Disambig-skärm (low confidence eller "show more")
- Hero med rundade hörn + close-button.
- Breadcrumb `HJÄLP TILL` / `HELP US OUT`.
- Headline 30px — `Vilken av *dem*?` / `Which *one*?`.
- Tre kandidat-kort:
  - **Top kandidat (#1)**: pale-cream gradient + kopparkant + soft glow. Markeras som "mest sannolik".
  - **#2 + #3**: standard cream.
  - Varje kort: 56dp rund thumb + namn 18px + vetenskapligt 13px italic + match-% i 18px (färgkodad samma som Lifelist) + label `Match` 9px uppercase.
- Tap på kort → Match-skärm med vald art (stamp-nummer förblir #219 — ingen ändring).
- Underst: `Inget av dom — försök igen` / `None of these — try again` (italic underlined link). Tap → tillbaka till kameran.

### Save-flow
- Tap `Stämpla i samlingen` på Match-skärmen:
  1. `SaveObservationUseCase.save()` skriver Observation med `stamp_number = (SELECT COALESCE(MAX(stamp_number), 0) + 1 FROM observation)` (atomic via SQLDelight) + `match_percent = topResult.confidence * 100`.
  2. Plan 5b's `BadgeRule`-eval körs → returnerar nya unlocks.
  3. Navigation: tillbaka till Listen-hub. Snackbar `Stämplad #219 — Talgoxe` visas i 3s.
  4. Om unlocks > 0 → UnlockBottomSheet visas (Plan 5b's queue), restyle:ad till nya färger.

---

## 9. Badges

Mockup: `badges-v1.html`.

**Ersätter** `BadgesScreen` från Plan 5b. Logik (rules, unlock-queue, backfill) **oförändrad**. Bara UI-omskrivning.

### Hero
- Mossgrön gradient + rundade hörn.
- Breadcrumb `BADGES`.
- Italic vit headline `Märken.` / `Discoveries.` (38px).
- Progress-rad ovanför separator-linje:
  - Big copper `*12* / 25` (italic på 12, total i mindre offwhite 70%).
  - Label `UPPLÅSTA MÄRKEN` / `BADGES UNLOCKED` 9px uppercase letter-spacing 0.18em.
  - Progress-bar 4dp hög, kopparton-gradient, soft copper-glow.

### Senast upplåsta (carousel)
- Horizontal scroll, 3–5 kort.
- Varje kort: 80px rund cream-cirkel med kopparkant 2.5px + halo (4dp alpha 0.18) + drop-shadow → ikon i koppar 38px → namn 13px Crimson Pro 700 → datum 11px italic ("i dag" / "2 dagar sen" / "v. förra").
- Visar bara märken där `unlocked_at` finns (sorterade desc, top 5).

### Att upptäcka (grid)
- 4-kolumns grid, 8dp gap horisontellt + 12dp gap vertikalt.
- Tre states per cell:
  - **Locked** (`unlocked_at = null`, ingen progress) — sand-cream cirkel `#D8D0BC`, dimmed `?`-ikon. Namn-text 10px italic muted (`???` om "hidden", annars namnet).
  - **In-progress** (rule-progress > 0 men < target) — pale-copper gradient cirkel + kopparton-ikon. Mini-progress-pill i nedre högra hörnet (`4/8`, `28/30`) i koppar.
  - **Hidden** — `???` istället för namn (för "rare"-kategorin där användaren ska bli överraskad).
- `BadgeRule.target` ger nämnaren. Logiken för "in-progress" hämtas från Plan 5b's `BadgeEvaluator` med ny `rawProgress(badgeId): Int` API som inte commit:ar unlocks utan bara mäter.

### Tap-on-locked
- **Tappable badge** (state: locked + namn synligt): tap → bottom-sheet med beskrivning + krav. Plan 5b's existing locked-detail-sheet, restyle:ad.
- **Hidden** badge (`???`): tap → snackbar `Hemligt — fortsätt skåda.` / `Secret — keep birding.`. Samma som Plan 5b.

---

## 10. Befintliga skärmar (lättare restyle)

### Species-profile (Plan 3 → restyle)
- Collapsing toolbar med hero-image **får ny gradient-overlay**: copper-mossgrön med rundade botten-hörn istället för rakt avskuret.
- Big italic-mixed headline: `Talgoxe.` (svenska) → `Parus *major*` (italic copper på epitet).
- Stat-block (`Habitat`, `Storlek`, `Häckning`, etc) restyle:as till sand-cream cards med italic copper-värden.
- Sparse-data-fallbacks oförändrade.

### Camera (Plan 4a → lätt restyle)
- ScanScreen variant C-layout (top-chip + crosshair + tap-to-freeze) **stannar exakt som den är**. Bara:
  - Top-chip backgrund → cream `rgba(245, 239, 216, 0.9)` istället för svart-alpha.
  - Chip-text Crimson Pro 14px med italic copper på arten.
  - Crosshair → koppar `#E0A47C` (mer pop).
  - Frame-counter och latens-debug oförändrade (de syns bara i debug-mode).

### Photo-analyze (Plan 4a → lätt restyle)
- Galleri-väljare + systemkamera-knapp samma struktur. Bara:
  - Knappar: koppar primary, cream secondary.
  - Hero-text: ny typografi (italic-mixed headline).

### Unlock-bottom-sheet (Plan 5b → lätt restyle)
- Logik (queue, glow-animation) oförändrad. Bara:
  - Background → cream `#F0EAD8`.
  - Märket centrerat med kopparkant + halo.
  - Headline italic-mixed.
  - Två CTA-stilar (primary cream-på-koppar, secondary link).

### Settings (ny)
- Minimal lista i Mossbädd-cream:
  - **Namn** — tap → text-input dialog (förifylld med nuvarande). Save → DataStore.
  - **Språk** — Swedish · English. Toggle.
  - **Om appen** — version, license, privacy-link.
- Inget "logga ut", inga konton (kommer i v1.5).
- Trigger: gear-ikon i top-right på Lifelist-skärmen. (Inte i bottom-bar — ses sällan.)

---

## 11. Datamodell

### Observation-schema (utökas)

Plan 5a's `Observation` har nu `id, species_q_id, image_path, match_label, note, created_at, lat, long, location_label`. Två nya kolumner:

```sql
ALTER TABLE observation ADD COLUMN stamp_number INTEGER;
ALTER TABLE observation ADD COLUMN match_percent INTEGER;  -- 0-100, nullable
```

**Constraint:** `stamp_number` ska vara unik per app-installation. Hanteras vid INSERT:

```sql
INSERT INTO observation (..., stamp_number, ...)
VALUES (..., (SELECT COALESCE(MAX(stamp_number), 0) + 1 FROM observation), ...)
```

Detta är atomic inom samma transaction → ingen race-condition mellan parallella saves (saves är sekventiella i UI ändå, men säkerhetsbälte).

### DataStore-keys (ny KMP-stack)

DataStore via okio (KMP). Setup som första gång i appen — lägg `:shared:datastore`-modul.

| Key | Typ | Default | Roll |
|---|---|---|---|
| `user_name` | String | `""` | Namn från onboarding |
| `has_seen_onboarding` | Boolean | `false` | Skip onboarding om true |
| `app_language` | String | `system` | `sv` / `en` / `system` |
| `lifelist_stat3_choice` | String | `streak` | `streak` / `species_year` / `species_month` / `longest_streak` |
| `archive_sort` | String | `alpha` | `alpha` / `family` / `recent` |
| `lifelist_sort` | String | `recent` | `recent` / `stamp_num` / `species` |

### Migration

SQLDelight-migration `1.sqm`:

1. `ALTER TABLE observation ADD COLUMN stamp_number INTEGER;`
2. `ALTER TABLE observation ADD COLUMN match_percent INTEGER;`
3. `UPDATE observation SET stamp_number = (SELECT COUNT(*) FROM observation o2 WHERE o2.created_at <= observation.created_at);` (chronologisk backfill, äldsta blir #1)
4. `match_percent` förblir `NULL` för gamla observationer. UI visar `—` istället för `87%`.

**SQLDelight-konfig:**

```kotlin
sqldelight {
  databases {
    create("BirdyData") {
      packageName.set("se.birdy.data.db")
      schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
      verifyMigrations.set(true)
    }
  }
}
```

Detta sätter migration-infrastrukturen för första gången (Plan 5a hade ingen `.sqm`).

---

## 12. Implementation-fas-plan

Redesign:en är för stor för en monolit. Föreslås tre fas-paket — **rekommenderas att 12.A → 12.B → 12.C går i ordning**, men 12.A kan släppas isolerat (visar nya färger + bottom-bar-namn utan strukturell IA-omdaning).

### 12.A — Foundation (~5–6 tasks)
- Tema-tokens uppdaterade (nya färger).
- Italic-mixed headline-helper (composable).
- HeroZone-composable med rundade botten-hörn.
- Bottom-bar-tab-namn bytta till Listen/Archive/Lifelist/Badges (inga IA-strukturella ändringar än — bara namn).
- Settings-skärm (basic).
- DataStore + onboarding (3 sidor).
- SQLDelight-migration: stamp_number + match_percent kolumner.

### 12.B — Skärm-redesigns (~7–8 tasks)
- Listen launcher (hub).
- Archive-redesign (chips, sort, geografi-neutral, image-thumbs).
- Lifelist-redesign (hero stats, stamp-rader).
- Badges-redesign (progress-bar, carousel, grid).
- Befintliga restyle (species-profile, camera, photo, unlock-sheet).
- Stamp-nummer-badges på alla relevanta thumbs.

### 12.C — Match-flow (~3–4 tasks)
- Threshold-logik i ML-pipeline.
- Match-skärm.
- Disambig-skärm.
- Toast vid <35%.
- Ersätter ClassificationResultScreen.

Tag `v0.7.0-redesign` efter 12.C.

---

## 13. Risker och öppna frågor

### Risker

- **DataStore-setup tar tid** — första gången i KMP-projektet. Foundation-fasen behöver runway för debugging.
- **SQLDelight verifyMigrations** — om vi någonsin redan tillåtit observationer på `v0.5.0a-diary` på Albins device behöver migration:en testas mot den faktiska DBn, inte bara fresh schema.
- **Italic-mixed text-pattern** — Compose's inline `SpanStyle` med italic + color fungerar men kan ha rendering-quirks med Crimson Pro variable-font + italic. Behöver verifieras tidigt.
- **Image-thumbs in Archive** — ~700 hero-images som cirkulära thumbs i en LazyColumn → Coil måste hantera caching väl. Kan behöva downscale till 88px på prepare-time och inte vid render.
- **Premium-sparkle-shimmer-animation** — om GPU-load blir hög på äldre devices, fallback till statisk gradient utan animation.
- **Backfill-migration på stora DBs** — om Albin har 200+ observationer kan UPDATE-loop ta sekunder. Kör i splash-screen (separat coroutine) inte synkron block.
- **"None of these"-flöde** — användaren har redan trustat appen att klassificera; om ingen kandidat stämmer behöver vi smidig återgång till kameran utan att tappa state.

### Öppna frågor (klargörs i implementationsplan)

- **Stamp-numreringen:** sekventiell per app-install — bekräftad. Vad händer om användaren raderar en observation? Stamp-nummer återanvänds inte (`MAX + 1` säkerställer detta) men det blir hål i serien (#1, #2, #4, #5). Acceptabelt?
- **Onboarding-Skip-flow:** om användaren skip:ar utan att ange namn — använd `Min`/`My` som fallback eller visa tomt? Föreslår fallback för icke-tom hero-text.
- **Sort-toggle på Archive — Senast tillagd:** behöver `created_at` på `species`-tabellen, vilken inte finns. Antingen lägg till (kräver content-pipeline-uppdatering) eller hoppa över denna sort-option.
- **Hidden-badges i grid:n:** vilka 3 av 25 är "hidden" som visar `???`? Föreslår: `RareBird1`, `RareBird2`, `RareBird3` (sällsynt-kategorin).
- **Personalisering på Match-skärmen:** ska den säga `Albin, vi tror...` istället för `Vi tror...`? Lite för bratty kanske; default = utan namn.
- **Stat 3-toggle persistens:** sparas valet i DataStore eller bara i ViewModel? Föreslår DataStore (förvänt-beteende att toggle:n minns sig).
- **Archive chip-mappning:** "Sångfåglar/Vatten/Rovfåglar/Ugglor/Vadare" är common-use-grupperingar som inte mappar 1:1 mot biologiska orders. `taxonomy.ioc_order` finns i species-YAML men chip-mappningen behöver spec:as som explicit dictionary i implementation-planen (t.ex. `Sångfåglar = Passeriformes`, `Vatten = Anseriformes ∪ Suliformes ∪ Pelecaniformes`, etc).
- **Stamp-numreringen vid disambig "byte av art":** användaren ser preview `STÄMPEL #219` på Match-skärmen, går till Disambig och väljer en annan art. Stamp-numret kvarstår — bekräftat. Men om användaren går *tillbaka* från Disambig (väljer "Inget av dom") och senare returnerar till samma kandidat, ska det vara samma stamp-nummer eller +1? Föreslår: stamp-nummer reserveras inte förrän save-tap; varje navigation till Match-skärmen visar `MAX + 1` baserat på nuvarande DB-state.

---

## 14. Plan-of-plans-position

Redesign:en är **Plan 7**, körs efter Plan 6 (Polish + Play Store-release). v1.0 går till Play Store med nuvarande Mossbädd-implementation; redesign blir v1.1 (eller v1.5).

**Skäl:**
1. Plan 6 är scoped till relativt korta tasks (bug-fixar, lokalisering, screenshot-pack, Play Store-listing) och är sista milstolpen för v1.0. Att infoga redesign före Plan 6 skulle skjuta Play Store-launch markant (3–4 veckor minimum).
2. Riktiga användarfeedback från v1.0 informerar redesign:en. Vi vet inte vad som faktiskt friktionerar förrän icke-Albin-användare provat.
3. Redesign:en är **inte refactor av broken code** — den är en visuell + IA-omdaning. v1.0 är fungerande och inte broken.
4. Stamp-metaforen funkar bättre när det finns "gamla" observationer att backfilla — Albin har redan ~10 i sin Lifelist från Plan 5a-verifiering.

**Undantag:** Om Plan 6 sluttar med signifikanta UX-issues som kräver omdesign (t.ex. "användare hittar inte fliken Y"), kan delar av 12.A (tema-tokens, italic-mixed headline) lyftas in i Plan 6.

---

## 15. Acceptance criteria

Redesign:en är klar när:

- [ ] Onboarding visas första gången, lagrar namn i DataStore, visas inte igen.
- [ ] Bottom-bar-tabs är `Listen · Archive · Lifelist · Badges` med korrekta ikoner.
- [ ] Tap Listen → hub visas. Tap "Kika" → kamera. Tap "Leta upp" → photo-analyze. Tap "Lyssna" → snackbar `Audio kommer snart`.
- [ ] Archive heter `Birds.`/`Birds.`, har chips, sort-toggle, image-thumbs, "STÄMPLAD"-pill för stamped arter.
- [ ] Lifelist heter `[Namn]s samling.`, har 3 stats (en toggleable), stamp-rader med stamp-nummer-badge, just-stamped-fade på senaste.
- [ ] Match-flow: ≥75% → Match-skärm; 35–74% → Disambig; <35% → toast.
- [ ] Save från Match-skärm → snackbar `Stämplad #X — [art]`. Eventuell unlock-bottom-sheet visas efter snackbar.
- [ ] Badges har `Märken.`-titel, progress-bar, carousel, 4-kol-grid med locked/in-progress/hidden states.
- [ ] Settings-skärm finns där namn + språk kan ändras.
- [ ] Migration backfillar stamp_number för befintliga observationer.
- [ ] Alla skärmar har Crimson Pro typografi, Mossbädd-paletten med tre nya tokens, italic-mixed headlines, hero-zoner med rundade botten-hörn.
- [ ] Build grön: `./gradlew :composeApp:assembleDebug :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest ktlintCheck detekt`.
- [ ] Manual device-verify på SM-S918B (Galaxy S23 Ultra) med screenshots av varje skärm.
- [ ] Tag `v0.7.0-redesign` pushad.

---

## 16. Out-of-scope (explicit)

- **Audio-mode**: Listen → Audio är bara en locked-card. Faktisk audio-pipeline (BirdNET/likn.) kommer i separat plan.
- **Premium-tier-logik**: sparkle är bara visuell, ingen IAP, inga gates på funktioner. Premium-tier i framtida plan.
- **Konton + cloud-sync**: lat/long-kolumnerna fanns redan i Plan 5a-schemat för v1.5, men ingen sync-pipeline byggs här.
- **Karta + community**: v1.5+ / v2.x.
- **Quiz/utbildningsläge**: v2.x.
- **Push-notifikationer**: v1.5.
- **iOS-appen**: KMP-shared-stacken kan teoretiskt köra på iOS men ingen iOS-UI byggs här.
