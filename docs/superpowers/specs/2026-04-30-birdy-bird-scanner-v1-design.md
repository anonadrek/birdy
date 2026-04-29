# Birdy Bird Scanner — v1 Design Spec

**Datum:** 2026-04-30
**Status:** Utkast — väntar på användargranskning
**Spec-typ:** v1 (initial release på Google Play Store, Android)

---

## 1. Bakgrund och syfte

Birdy Bird Scanner är en AI-driven mobilapp som låter användare identifiera fåglar — antingen i realtid genom kameran eller genom att ladda upp ett foto — och sedan utforska ett rikt uppslagsverk om arten. Appen kombinerar "wow"-momentet i en lyckad realtidsidentifiering med djupet i ett uppslagsverk där användaren kan stanna kvar och lära sig.

**v1-mål:** Komma till Google Play Store med en sammanhängande kärnupplevelse — skanna, identifiera, utforska, samla. Inget eget backend krävs i v1.

**Senare milstolpar (utanför denna spec):**

- **v1.5 — "Karta & moln":** Konton, molnsynk av dagboken, karta med fynd från publika datakällor, push-notiser om sällsynta arter nära användaren.
- **v2 — "Community":** Delning av fynd, kommentarer, flöde, moderering.
- **v2.x:** Quiz/utbildningsläge, ljud-ID, fullt offline-läge för längre exkursioner.

## 2. Scope

### 2.1 Geografi

Norden och Europa (~500–700 arter, västpaleartisk fauna).

### 2.2 Målgrupp

Bred allmänhet med två lager:

- **Yt-nivå:** Enkel språk, vacker presentation, tilltalar nyfikna familjer och hobbyister.
- **Expert-nivå:** Taxonomi, vetenskapliga namn, IUCN-status, refs — en knapptryckning bort, inte ett separat läge.

### 2.3 Funktioner i v1

| Funktion | Beskrivning |
|---|---|
| Realtidsskanning via kamera | Kameraström, on-device TFLite-inferens 3 fps, overlay med toppgissning + confidence |
| Identifiering från uppladdat foto | Välj från galleri eller ta foto, kör klassificering, visa top-3 |
| Artprofil (uppslagsverk) | Hjältebild, namn, snabb-fakta, beskrivning, migrationstext, expert-toggle |
| Bläddra/söka i uppslagsverket | Lista, fritext, filter (familj, region, säsong) |
| Observationsdagbok | Lokalt sparade observationer från lyckade skanningar |
| Gamification | Lokala märken baserat på regel-DSL |
| Språkstöd | Svenska + engelska, både UI och innehåll |

### 2.4 Funktioner medvetet utanför v1

- Ljud-ID (parkerat till v2.x)
- Karta/heatmaps (v1.5)
- Push-notiser (v1.5)
- Konton och molnsynk (v1.5)
- Community/delning (v2)
- Quiz/utbildning (v2.x)
- Manuell observation utan föregående skanning (v1.5 — endast skannings-baserade observationer i v1)
- Realtidsdata om sällsyntheter via Artportalen/eBird API:er (utanför v1; statisk klassning används istället)

### 2.5 Resourcing

Solo-utvecklare med Claude Code och subagenter som motor; specs/plans i Markdown är källan till sanning. Användaren granskar genererad kod.

## 3. Arkitektur

### 3.1 Översikt

Kotlin Multiplatform-app där all affärslogik och UI är delad via Compose Multiplatform. Plattformsspecifik kod (kamera, ML-inferens, lokal DB-driver) lever bakom `expect`/`actual`-gränser. v1 körs helt utan eget backend — all data ligger antingen i appens AAB-bundle eller i en lokal SQLite-databas på telefonen.

### 3.2 Modulstruktur

```
birdy-bird-scanner/
├── composeApp/              ← Compose Multiplatform UI + view-modeller (delad)
├── shared/
│   ├── domain/              ← Use cases, modeller, business rules (ren Kotlin, delad)
│   ├── data/                ← Repositories, content provider-glue, SQLDelight-queries (delad)
│   ├── ml/                  ← ML-API (expect interface), bildpreprocessing (delad)
│   └── content/             ← Artdatabas-loading, gamification-regler (delad)
├── androidApp/              ← Android-entrypoint, plattforms-actuals (TFLite, CameraX, Room/SQLDelight-driver)
├── iosApp/                  ← Skelett v1, fylls i v2 när iOS blir aktuellt
└── ml-eval/                 ← Modellutvärdering, separat från app-bygge
```

### 3.3 Plattformsgränssnitt

| Gränssnitt | Ansvar | Android v1-implementation |
|---|---|---|
| `BirdClassifier` | Tar bild → top-N art-id + confidence | TFLite via `org.tensorflow:tensorflow-lite-task-vision` |
| `CameraSource` | Strömmar frames för realtidsläget | CameraX `ImageAnalysis` |
| `DatabaseDriverFactory` | Skapar SQLDelight-driver | `AndroidSqliteDriver` |
| `LocaleProvider` | Aktivt språk för UI/content | Android `Locale` |

### 3.4 Datakällor (allt bundlat i AAB)

| Resurs | Storlek | Innehåll |
|---|---|---|
| TFLite-modell | ~30–80 MB | Finetunad MobileNetV3-Large eller EfficientNet-Lite på iNaturalist Aves filtrerat till västpaleartiska arter |
| Artdatabas (SQLite) | ~5–15 MB | ~700 arter, taxonomi, sv/en-namn och beskrivningstexter, migration, abundansklass, säsong, region, IUCN-status, foto-refs |
| Artbilder | ~50–150 MB | 1–3 referensbilder per art, levereras via Play Asset Delivery |

**Total app-storlek:** ~100–250 MB. Acceptabelt för en kameracentrerad app (jämför Merlin Bird ID ~500 MB+).

**Lokalt på telefonen efter installation:** användarens observationsdagbok, kopierade fotografier (1024px JPEG), gamification-state — allt i en SQLite-databas i app-private storage.

### 3.5 Backend

Inget eget backend i v1. v1.5 introducerar Firebase Auth + Firestore för molnsynk; specas separat när tiden kommer.

## 4. Komponenter och domänmodell

### 4.1 Domänentiteter (`shared/domain/src/commonMain`)

| Entitet | Innehåll | Lagring |
|---|---|---|
| `Species` | id, taxonomi (familj/släkte/art), namn (sv/en), beskrivning (sv/en), migrationstext (sv/en), abundansklass (allmän/mindre allmän/ovanlig/sällsynt), säsongskalender, regional spridning, IUCN-status, lista av referensbild-asset-id:n (1–3 st) | Skrivskyddad SQLite i AAB |
| `Observation` | id, speciesId, datum, valfri foto-path (lokal fil i app-private storage), valfri grov plats, anteckning, källa (live/foto), confidence vid identifiering | Lokal SQLite (skrivbar) |
| `Classification` | top-N-lista av (speciesId, confidence), tidsstämpel, käll-frame eller foto | Transient (resultatskärmens viewmodel) |
| `Badge` | id, namn (sv/en), villkor (regel-DSL), upplåst-datum för användaren | Härleds från observationer; cachas |

### 4.2 Use cases (`shared/domain/usecases`)

- `ClassifyPhoto(bytes) → Classification` — engångsanalys av uppladdat foto
- `StreamLiveClassifications(frames) → Flow<Classification>` — realtidsläget; throttlad till 3 fps
- `GetSpeciesProfile(speciesId, locale) → SpeciesProfile` — sammansatt vy för uppslagsverket
- `SearchSpecies(query, locale, filters) → List<SpeciesSummary>`
- `LogObservation(input) → Observation` — sparar i dagboken, triggar `RecalculateBadges`
- `ListObservations(sort, filter) → Flow<List<Observation>>`
- `RecalculateBadges(observations) → List<Badge>` — ren funktion
- `GetBadgeProgress(userObservations) → List<BadgeProgress>`

### 4.3 Skärmar (Compose Multiplatform, i `composeApp`)

1. **`HomeScreen`** — stor "Skanna nu"-knapp, genvägar till dagbok och uppslagsverk, "samlade arter X / 700"-räknare
2. **`LiveScanScreen`** — kameravy fyller skärmen, overlay med toppgissning + confidence-bar, tap för att frysa & öppna resultat
3. **`PhotoAnalyzeScreen`** — välj foto eller ta bild, klassificera, visa resultat
4. **`ClassificationResultScreen`** — top-3 förslag med confidence, klickbara, "Spara som observation"-knapp
5. **`SpeciesProfileScreen`** — kärnan i appen; hjältebild → namn → snabb-fakta-rad → beskrivning → migrationstext → relaterade arter → "Lägg till i dagboken". Expert-toggle för taxonomi/IUCN/refs.
6. **`EncyclopediaScreen`** — bläddra/sök/filtrera, gruppering per familj som default
7. **`DiaryScreen`** — kronologisk lista, tap → detaljvy med redigera/radera
8. **`BadgesScreen`** — rutnät av låsta/upplåsta märken med progress
9. **`SettingsScreen`** — språk, plats-tillstånd, om appen, datasekretess, återställ data

### 4.4 Navigeringsstruktur

Bottom navigation med fyra flikar: **Skanna**, **Uppslagsverk**, **Dagbok**, **Märken**. Resultatskärm och artprofil pushas över. Inställningar nås via menyikon i hörnet.

### 4.5 Gamification-regelmotor

Märken definieras i en YAML-fil i `shared/content/badges.yaml`. `RecalculateBadges` är en ren funktion som tar in observationer + regler → returnerar märkesstatus.

```yaml
- id: nyborjare
  name_sv: "Nybörjare"
  name_en: "Beginner"
  rule: { type: count, target: 5, scope: any }
- id: faltbio_brons
  name_sv: "Fältbiologi (Brons)"
  rule: { type: count_unique_species, target: 25 }
- id: vinterfagel
  rule: { type: observed_in_season, season: winter, count: 10 }
- id: sallsynt_fynd
  rule: { type: observed_with_abundance, abundance: rare, count: 1 }
```

Stödjer minst dessa regeltyper i v1: `count`, `count_unique_species`, `observed_in_season`, `observed_with_abundance`, `observed_in_family`, `observed_in_region`. Att lägga till en ny regel kräver kod i regelmotorn och en ny rad i YAML.

## 5. Dataflöden

### 5.1 Realtidsskanning

```
[Kamera] ─frames@30fps→ [CameraSource (actual)]
                              │ throttla till 3 fps + downscale 224×224
                              ▼
                        [BirdClassifier (actual: TFLite)]
                              │ confidence-threshold 0.35 (annars "söker…")
                              ▼
                  [LiveScanViewModel: StateFlow<UiState>]
                              ▼
                      [Compose: overlay + bar]

[User: tap to freeze] → [Pause stream] → [ClassificationResultScreen]
```

Tre inbyggda beslut:

1. **3 fps är taket** för realtids-inferens. Mellan inferenser visas senast kända resultat.
2. **Frame-buffer på 1.** Frames som kommer medan inferens pågår slängs.
3. **Confidence-threshold 0.35.** Under detta visas "söker…" istället för en gissning.

### 5.2 Fotoanalys

```
[Galleri/Kamera] → [bytes] → [downscale + rotera] → [BirdClassifier]
                                                          │
                                                          ▼
                                          [Top-3 + confidences]
                                                          │
                                                          ▼
                              [ClassificationResultScreen]
```

Ingen throttling. Visa "Analyserar…"-progressindikator (kan ta 1–2 sekunder på äldre enheter).

### 5.3 Spara observation

```
[ResultScreen: tap "Spara"]
       │
       ▼
[LogObservation use case]
       │
       ├─ skriv till observations-tabell (SQLDelight)
       ├─ kopiera bild till app-private storage (1024px JPEG)
       │
       ▼
[Triggar badge-recalc i bakgrunden (coroutine)]
       │
       ▼
[Database emits → Diary/Badges-skärmar uppdateras reaktivt]
```

Reaktivitet via SQLDelight `Flow`-baserade queries. Inga manuella refresh-anrop i UI.

### 5.4 Uppslagsverket: bläddra/söka

```
[Sökfält / filterval] → [debounce 200ms] → [SearchSpecies use case]
                                                   │
                                                   ▼
              [SQLDelight query mot read-only species-DB i AAB]
                                                   │
                                                   ▼
                                      [Lista av SpeciesSummary]
```

Read-only-DB öppnas direkt från assets — ingen kopiering till app-storage.

### 5.5 Märkesberäkning

```
[Observations-tabell uppdateras]
       │ Flow-emit
       ▼
[BadgeRecalculator (debounced 500ms)]
       │ rena funktioner: regler.yaml + observations
       ▼
[Badge-state-cache (in-memory + SQLite för låsta-datum)]
       │
       ▼
[UI: nya märken ger snackbar "Märke upplåst!"]
```

Inga timers, ingen background-service. Allt deriveras från observationer när de ändras.

## 6. Felhantering och kantfall

**Princip:** Varje fel som användaren kan se ska ha en tydlig text på svenska/engelska + en tydlig nästa åtgärd. Inga råa exception-stackar visas.

### 6.1 Tillstånd (permissions)

| Tillstånd | När begärs | Om nekas |
|---|---|---|
| Kamera | Innan första gången realtids- eller fotokameraläget öppnas | "Kamera krävs för att skanna fåglar" + knapp till systeminställningar. Foto-upload från galleri funkar fortfarande. |
| Foton/galleri | Innan första gången användaren väljer foto från galleri | Förklara behov, knapp till inställningar. Realtidsläget funkar fortfarande. |
| Plats | Valfritt, frågas första gången användaren sparar en observation | Spara utan plats. Ingen blockering, ingen pushback. |
| Notiser | v1.5 — frågas inte i v1 | — |

### 6.2 ML-inferens

| Fall | Hantering |
|---|---|
| Modellen lyckas inte ladda vid uppstart | Logga, visa "Något gick fel — starta om appen". Skanning-flikar visar feltillstånd istället för krasch. |
| Confidence < 0.35 i realtid | UI visar "Söker…", inte en gissning. |
| Confidence < 0.35 vid fotoanalys | "Vi hittade ingen tydlig fågel — testa ett foto med fågeln närmare/i centrum". |
| Top-1 < 0.50 men top-3 finns | Visa alla tre med confidence — låt användaren välja. |
| Frame-pipelinen halkar efter på äldre telefon | Auto-throttla till 1.5 fps. Om det inte räcker: engångs-meddelande "Din enhet är långsam för realtidsskanning — använd foto-läget för bästa resultat". |

### 6.3 Kamera och hårdvara

| Fall | Hantering |
|---|---|
| Kamera upptagen av annan app | Snackbar, försök-igen-knapp. |
| Hårdvarufel mitt i streaming | Pausa stream, visa "Kameran kopplades från", knapp för omstart. |
| Enheten saknar bakåtkamera | Falla tillbaka till framsidekameran om den finns; annars dölj live-knappen. |

### 6.4 Lagring

| Fall | Hantering |
|---|---|
| Disk full när observation sparas | "Frigör utrymme för att spara fler observationer" — observationen sparas inte. Ingen partiell skrivning. |
| Lokal DB blir korrupt | Vid uppstart: detektera, döpa om till `.corrupt`, skapa ny tom DB, dialog "Tyvärr, dagboken kunde inte återställas. Den gamla finns kvar i [path]". |
| Asset-DB (artdatabas) saknas vid uppstart | Hård krasch med tydlig felrapport — installation är korrupt. |

### 6.5 Innehållsintegritet

| Fall | Hantering |
|---|---|
| Artprofil saknar svensk översättning | Falla tillbaka till engelska + diskret hint "Beskrivning på engelska". |
| Artprofil saknar engelsk översättning | Spegelvänd policy. |
| Saknad bild för art | Visa platshållare med fågel-silhouette + familj-glyf, inte tom ruta. |
| Modellen klassificerar art som inte finns i artdatabasen | Får inte hända. Build-time-validering kräver att alla model-output-id:n finns i species-tabellen; build failure annars. |

### 6.6 Användardata och GDPR

| Funktion | v1-implementation |
|---|---|
| Radera all data | Inställningar → "Återställ appen" → tömmer lokal DB + bilder + märken |
| Exportera dagbok | JSON-export till delningsmenyn (mejla, spara till Drive, etc.) |
| Plats-precision | Användaren väljer per observation: ingen plats / region / exakt GPS |
| Dataöverföring | Inget skickas till någon server i v1 (förutom anonyma kraschrapporter) |

### 6.7 Observability

- **Crashlytics** (Firebase) på Android för krasch-logging. Endast stacktraces, enhetstyp, app-version. Inga personliga data.
- **Inga analytics-events i v1.** Lägg till i v1.5 efter sekretessgranskning + opt-in-UI.

### 6.8 Lokalisering

- All UI-text via Compose Multiplatform-resurssystemet med `sv.xml` och `en.xml`.
- Datumformat och pluralisering via plattformens lokala API:er.
- Tom-state-illustrationer har egen text per locale.

## 7. Testning

### 7.1 Princip

Testlagren ska göra det säkert att låta Claude Code-agenter ändra kod utan manuell verifiering varje vändning. Ett ML-projekt har dessutom ett extra utvärderingslager.

### 7.2 Testpyramid

```
        ┌────────────────────────────┐
        │  Manuella enhetstest       │ ← du på riktig telefon
        ├────────────────────────────┤
        │  UI-test (Compose)         │ ← ~10–20 st
        ├────────────────────────────┤
        │  Integration (DB, ML-glue) │ ← ~30–50 st
        ├────────────────────────────┤
        │  Unit (domän, regler)      │ ← ~100+ st
        └────────────────────────────┘

   ┌──────────────────────────────────┐
   │  ML-modellutvärdering (separat)  │ ← egen pipeline
   └──────────────────────────────────┘
```

### 7.3 Unit-tester (`shared/domain/src/commonTest`)

- Use cases (klassificeringsbeslut, sannolikhets-mappning, fallback-logik)
- Badge-regelmotor — varje ny regel kräver minst ett testfall
- Söklogik (debounce, filtrering, ranking)
- i18n-fallback-policy
- Resultat-tröskling och top-N-logik

**Mål:** ≥85 % branch coverage i `shared/domain`.

### 7.4 Integrationstester (`shared/data`)

- SQLDelight-queries mot in-memory SQLite
- Repository-lager (artdatabas-läsning, observation-skrivning)
- Datamigrering: ett migreringstest per schema-ändring som påverkar v1-användare
- ML-pipeline: skicka känd test-bild genom preprocessing + dummy classifier → verifiera output-form

### 7.5 ML-modellutvärdering (`ml-eval/`)

Egen pipeline, körs separat från app-bygget i CI.

**Hold-out testdataset:** 10–20 bilder per art (~7 000–14 000 bilder totalt), aldrig sett under träning.

**Mätvärden** rapporterade efter varje träningsiteration:

- Top-1 accuracy (mål: ≥75 %)
- Top-3 accuracy (mål: ≥90 %)
- Per-art accuracy (för att hitta arter modellen är dålig på)
- Confusion matrix för de 50 vanligaste arterna

**Performance benchmarks:** inferenstid på tre referensenheter (lågpris, mellan, flaggskepp). Mål: <500 ms på mellan, <1500 ms på lågpris.

**Build-time-gate:** ny modellversion får inte slås in i `main` om Top-3 sjunker > 2 procentenheter mot förra modellen.

### 7.6 UI-tester (Compose Multiplatform UI tests)

Inte komplett täckning — bara kritiska flöden:

- Live-skanna → top-3 visas → tap → resultatskärm öppnas
- Foto-upload-flöde end-to-end (med fake classifier)
- Spara observation → dyker upp i dagboken
- Märke triggas vid 5 unika observationer
- Sök i uppslagsverket → klick → artprofil renderar
- Språkbyte → texten ändras

### 7.7 Device-tester (manuella)

En kort checklista (~15 punkter) körs på riktig telefon innan varje Play-release. Inkluderar: kamerabehörighet nekad, batterisparläge på, flygläge på (verifiera offline-stöd), liten skärm, dark mode. Lagras i `docs/superpowers/release-checklist.md`.

### 7.8 Build-time-validering (Gradle-tasks i CI)

- `validateSpeciesData` — varje art har giltiga fält, korrekt taxonomi, sv + en text (eller markerad fallback)
- `validateModelMapping` — alla output-id:n från TFLite-modellen finns som rader i species-tabellen
- `validateAssets` — varje art har minst en referensbild eller en uttalad placeholder-flagga
- `validateBadgeRules` — YAML parsar korrekt, refererade arter finns

Brist i något av dessa = build failure.

### 7.9 CI-pipeline (GitHub Actions)

```
[push/PR]
  ├─ lint (ktlint, detekt)
  ├─ unit + integration tests (shared)
  ├─ build-time validations
  ├─ androidApp build (debug)
  ├─ Compose UI tests (Robolectric)
  └─ [veckovis] full ML-evaluation + benchmarks
```

ML-utvärdering körs inte vid varje PR (GPU-tid), bara när `ml-eval/` ändras eller veckovis.

### 7.10 Medvetet utelämnat i v1

- E2E-test mot riktig kamera-hårdvara på CI (för spretigt — manuell validering täcker detta)
- Performance-regression-CI (lägg till när användarbas växer)
- Accessibility-CI-gate (designar accessibility-vänligt; automation i v1.5)

## 8. Datakällor och förberedelse

### 8.1 ML-modell

**Rekommendation:** finetuna **MobileNetV3-Large** eller **EfficientNet-Lite0/1** på iNaturalist Aves-data filtrerat till de västpaleartiska arter som ingår i artdatabasen. iNaturalists öppna träningsdata ger typiskt 200–2000 bilder per art för vanliga arter, och båda modellfamiljerna är beprövade för on-device-användning. Listan över ingående arter (modellens output-vokabulär) genereras från artdatabasens `species`-tabell vid build-tid; modellen och databasen versioneras tillsammans.

Alternativ att utvärdera under implementation: starta från en redan tränad fågelklassificerare på TF Hub om någon täcker tillräcklig mängd europeiska arter.

### 8.2 Artdatabas-innehåll

Sammanställs en gång (kan automatiseras med Claude Code) från:

- **Wikidata** — taxonomi, vetenskapliga och vernakulära namn på flera språk, IUCN-status
- **Wikipedia (sv + en)** — beskrivningstexter (kortas ner till 1–3 stycken per art med Claude)
- **BirdLife Sverige** + **eBird Status & Trends 2024** — abundansklass, säsongskalender, regional spridning
- **Wikimedia Commons** — referensbilder under fri licens

Artdatabasen byggs som en del av `ml-eval/`-pipen och resulterar i en `species.db` SQLite-fil som bundlas i AAB:n.

### 8.3 Versionering

Modell, artdatabas och bilder versioneras tillsammans. Build-time-validering säkrar att de matchar.

## 9. Beslut, defaults och öppna frågor

### 9.1 Bekräftade beslut

- Geografi: Norden/Europa
- Användningsscenario: Hybrid (realtid hooks + uppslagsverk djup)
- Målgrupp: Bred allmänhet, två lager
- Migration: Statisk artgenerell information
- Sannolikhet: Statisk klassning (allmän/sällsynt + säsong + region)
- Språk: Svenska + engelska, både UI och innehåll
- AI-upplägg: On-device TFLite
- Stack: Kotlin Multiplatform + Compose Multiplatform
- Lokal DB: SQLDelight (KMP-native, Flow-baserade queries — designen förutsätter detta genomgående)

### 9.2 Defaults antagna utan explicit bekräftelse

Dessa togs in i specen baserat på rimliga val; ändra om du vill.

- **Navigering:** Bottom nav med 4 flikar (Skanna / Uppslagsverk / Dagbok / Märken)
- **Resultatantal:** Top-3 förslag på resultatskärmen
- **Manuell loggning:** Endast skannings-baserade observationer i v1 (manuell loggning skjuts till v1.5)
- **Foto-kompression:** 1024px JPEG i dagboken
- **Plats:** Frivillig, valfri precision per observation
- **Export-format:** JSON
- **CI:** GitHub Actions
- **Observability:** Endast Crashlytics

### 9.3 Öppna frågor som tas i implementationsplanen

- Exakt val av base-modell (MobileNetV3-Large vs EfficientNet-Lite0/1) — bestäms efter en initial benchmark
- Play Asset Delivery vs alla bilder i bas-AAB — beror på slutlig totalstorlek
- Ikoner för märken — design tas i en separat designsession
- Visuellt språk för appen (färgpalett, typografi, ton) — designsession innan implementation, gärna med hjälp av visual companion

## 10. Beroenden och risker

### 10.1 Tekniska risker

| Risk | Sannolikhet | Påverkan | Mitigering |
|---|---|---|---|
| TFLite-modellen för stor (>150 MB) för rimlig totalstorlek | Medel | Medel | Kvantisering till int8, experiment med EfficientNet-Lite0 (mindre) |
| Realtids-inferens för långsam på lågpristelefon | Medel | Hög | Auto-throttle, fallback-meddelande, fokus på foto-läge för svaga enheter |
| Dålig accuracy på sällsynta arter (lite träningsdata) | Hög | Medel | Per-art-accuracy i utvärdering, dokumentera kända svaga arter, "låg-confidence"-meddelande hjälper användaren |
| iNaturalist-licens räcker inte för bildsamling i app | Låg | Hög | Använd endast Wikimedia Commons + egna bilder; iNaturalist endast för träning (separat licensvillkor) |
| Compose Multiplatform-stabilitet på iOS senare | Medel | Låg (för v1) | iOS är inte v1; vi commit:tar inte till iOS-release-tidsplan |

### 10.2 Innehållsrisker

| Risk | Mitigering |
|---|---|
| Översättningar saknas eller är dåliga för vissa arter | i18n-fallback policy + bygg-tid-rapport på vilka arter som har bara ett språk |
| Felaktig klassificering kan vilseleda nyfikna användare | Confidence visas alltid; "föreslå annan art"-funktion i v1.5 (parkerad) |
| Sekretess kring barn (om många under 13 använder appen) | Inga community-funktioner, ingen tracking, lokal lagring → låg risk i v1 |

## 11. Lansering och uppföljning

### 11.1 Krav för Play Store-release

- 100 % ovanstående test-pyramid grön
- Manuell device-checklist genomförd på minst tre enheter (lågpris/mellan/flaggskepp)
- Privacy Policy + sekretessmeddelande publicerat (inga personliga data lämnar enheten)
- Play Store-listning på svenska + engelska
- Beta-test med ~10 användare i minst två veckor

### 11.2 Tidig framgångssignal (efter release)

- Krasch-fri användarprocent ≥99 %
- Median realtidsskanning <500 ms
- Användare som skannar minst en gång och därefter öppnar artprofilen ≥60 %
- 50 + Play Store-recensioner med ≥4.0 snitt

## 12. Bilaga: terminologi

- **Art / "ras"** — *species* (taxonomisk nivå)
- **Familj / "stam"** — *family* (taxonomisk nivå över art och släkte)
- **Abundansklass** — hur vanlig arten är: allmän / mindre allmän / ovanlig / sällsynt
- **Västpaleartisk** — biogeografisk region som täcker Europa, Nordafrika, Mellanöstern
- **AAB** — Android App Bundle, Google Plays moderna distributionsformat
- **TFLite** — TensorFlow Lite, Googles ramverk för on-device-inferens
- **KMP** — Kotlin Multiplatform
- **expect/actual** — KMP-mekanism för att deklarera plattformsspecifika implementationer

---

**Författare:** Albin Lindblom + Claude Code (brainstormingsession)
**Nästa steg:** användargranskning av spec → implementationsplan via writing-plans-skill
