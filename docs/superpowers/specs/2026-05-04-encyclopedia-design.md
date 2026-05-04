# Birdy Bird Scanner — Plan 3: Encyclopedia Design Spec

**Datum:** 2026-05-04
**Status:** Utkast — väntar på användargranskning
**Spec-typ:** Plan 3 av 6 (Encyclopedia + species profile för v1)
**Föregående:** Plan 2a (pipeline + walking-skeleton) klar — `v0.2.0a-pipeline`. Plan 2b (content backfill) **PAUSAD** vid 97/700 arter — återupptas efter Plan 3 ships.
**Författare:** Albin Lindblom + Claude Code (brainstormingsession 2026-05-04)

---

## 1. Bakgrund och syfte

Plan 2a levererade en pipeline + 5 walking-skeleton-arter; Plan 2b kör nu family-by-family backfill mot ~700 arter och har 97 committade. Det är mer än tillräckligt för att bygga och iterera Plan 3:s UI mot riktig SQLDelight-data. Plan 2b's resterande arter trickle in efter Plan 3 utan att blockera.

Plan 3 lägger till **uppslagsverkets två centrala skärmar** ovanpå den `SpeciesRepository` som Plan 2a Task 13 publicerade:

1. **EncyclopediaScreen** — list över alla arter med sökning, filtrering och gruppering på förekomst (allmän vs övriga).
2. **SpeciesProfileScreen** — full artprofil med collapsing toolbar, hero-foto, fakta, beskrivning, migrationstext, sekundärbilder.

Plus ett **app-skelett** med bottom-nav (4 flikar) där Plan 4 (Skanna), Plan 5 (Dagbok, Märken) får sina platser placeholder-fyllda.

**Plan 3 är klar när:**

- App startar med bottom-nav synlig och Uppslagsverk som default-flik.
- EncyclopediaScreen renderar 97+ arter på riktig data, grupperat allmän/övriga, med sök och filter fungerande.
- SpeciesProfileScreen pushas vid klick på art-rad och visar collapsing toolbar + alla sektioner.
- Sparse-arter (~30% av appens innehåll) renderas korrekt med inline-empty-states i alla sektioner.
- Alla strängar finns i `strings.xml` (sv) + `values-en/strings.xml` (en).
- ViewModel- och Compose-UI-tester gröna.
- Manuell device-verifiering på SM-S918B med skärmdumpar i `docs/superpowers/screenshots/`.
- Tag `v0.3.0-encyclopedia` pushad.

Plan 4 (ML & Camera) plockar upp den befintliga `Skanna`-fliken och fyller den med kameraflöde + TFLite-inference. Plan 5 (Diary & Gamification) tar Dagbok + Märken och lägger till "+ Lägg till i dagboken"-CTA på SpeciesProfileScreen.

---

## 2. Låsta beslut från brainstormingen

| # | Beslut | Motivering |
|---|---|---|
| 1 | **Scope:** Encyclopedia + Profile + bottom-nav-skelett med 3 stubbade flikar | Ger samma "app-känsla" som final v1, ingen extra arkitektur senare; stubs är ~5 minuter per skärm |
| 2 | **Browse-default:** lista grupperad på allmän/övriga (sticky-sektioner) med sök på toppen | Matchar nybörjarens hjärnmodell ("vad ser jag i Sverige?"); search hanterar entusiastens use-case |
| 3 | **Profile-layout:** collapsing toolbar (Material 3 LargeTopAppBar) | Hero-foto centralt vid ankomst, namn alltid synligt vid scroll |
| 4 | **Sparse-data-rendering:** sektioner behålls alltid; tomma renderas inline ("Beskrivning kommer.") | Konsistent struktur över alla 700 arter; läser sig som Wikipedia, inte som bug |
| 5 | **Sökning + filter-UX:** sökfält alltid synligt + filter-knapp som öppnar bottom sheet med count-pill | Sök är primär (vanligaste use-case); filter sekundär; bottom sheet skalar till 6+ filter |
| 6 | **Navigation:** Compose Multiplatform Navigation 2.x med type-safe routes (`@Serializable` data classes) | Stable sedan 2025; identisk på Android + iOS; ingen extra dep |
| 7 | **State management:** ViewModel + StateFlow via `lifecycle-viewmodel-compose` | KMP-kompatibel; en ViewModel per screen, repo via constructor |
| 8 | **Image loading:** Coil 3 (KMP-stable) mot `composeResources/files/images/` | Bilder finns redan lokalt sedan Plan 2a Task 14; ingen disk-cache, ingen nätverksdep |
| 9 | **i18n:** `compose-multiplatform-resources` med `strings.xml` (sv default) + `values-en/strings.xml` | Inbyggt i Compose Multiplatform; inget moko-resources |
| 10 | **DI:** Manuell constructor-injection via en `AppGraph`-klass i `composeApp` | Solodev + ~5 screens — Koin/Hilt overkill |
| 11 | **Bottom-nav-stubs:** Skanna ("Kommer i Plan 4"), Dagbok + Märken ("Kommer i Plan 5") | Bara placeholder-text + Mossbädd-tema, ingen logik |
| 12 | **Default-flik vid app-start:** Uppslagsverk | Det enda flik som har innehåll i v0.3; byts till Skanna när Plan 4 landar |

---

## 3. Arkitektur och moduler

Plan 3 lägger till **UI-arbete i en befintlig modul** och **inga nya gradle-moduler**. `shared/content` (Plan 2a Task 13) konsumeras nästan oförändrad — en mindre utökning av `search()` (se §4.1) krävs för att honorera `regions` + `activeInMonth` i `SpeciesFilter` och matcha `scientific_name`. Dessa fält finns redan i data-klassen men ignoreras i nuvarande implementation.

```
composeApp/
├── src/commonMain/kotlin/se/birdy/app/
│   ├── App.kt                                  ← uppdateras: byt HomeScreen mot AppScaffold
│   ├── di/
│   │   └── AppGraph.kt                         ← NY: håller repo + viewModelFactories
│   ├── ui/
│   │   ├── scaffold/
│   │   │   ├── AppScaffold.kt                  ← NY: bottom-nav + NavHost
│   │   │   ├── AppRoute.kt                     ← NY: sealed interface med @Serializable routes
│   │   │   ├── BottomNavBar.kt                 ← NY: 4 flikar
│   │   │   ├── ScanStubScreen.kt               ← NY: placeholder Plan 4
│   │   │   ├── DiaryStubScreen.kt              ← NY: placeholder Plan 5
│   │   │   └── BadgesStubScreen.kt             ← NY: placeholder Plan 5
│   │   ├── encyclopedia/
│   │   │   ├── EncyclopediaScreen.kt           ← NY: list + sök + filter
│   │   │   ├── EncyclopediaViewModel.kt        ← NY
│   │   │   ├── EncyclopediaUiState.kt          ← NY
│   │   │   ├── SpeciesRow.kt                   ← NY: list-row composable
│   │   │   └── FilterBottomSheet.kt            ← NY: filter UI
│   │   ├── profile/
│   │   │   ├── SpeciesProfileScreen.kt         ← NY: collapsing toolbar
│   │   │   ├── SpeciesProfileViewModel.kt      ← NY
│   │   │   └── SpeciesProfileUiState.kt        ← NY
│   │   ├── components/
│   │   │   ├── SectionBlock.kt                 ← NY: wrapper med tomt-tillstånd
│   │   │   ├── HeroImage.kt                    ← NY: Coil + sandig fallback
│   │   │   └── EmptyState.kt                   ← NY: söknollträff + saknad art
│   │   └── theme/                              ← (befintlig, oförändrad)
│   └── ui/HomeScreen.kt                        ← raderas (placeholder från Task 14)
└── build.gradle.kts                            ← uppdateras: nya deps

shared/content/                                 ← mindre utökning: SpeciesName.sq + SqlDelightSpeciesRepository.search()
shared/domain/                                  ← (oförändrad)
gradle/libs.versions.toml                      ← uppdateras: nya entries
```

### 3.1 Beroenden tillagda

I `gradle/libs.versions.toml`:

```toml
[versions]
androidx-lifecycle = "2.8.4"               # eller senare KMP-stable; verifiera senaste vid Task 1
androidx-navigation = "2.8.0"              # KMP-stable, type-safe routes
coil = "3.0.4"                             # KMP-stable; senaste version vid Task 1
turbine = "1.1.0"                          # test-dep för Flow-assertions

[libraries]
androidx-lifecycle-viewmodel-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "androidx-lifecycle" }
androidx-navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "androidx-navigation" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
```

(`coil-network-ktor` läggs INTE till — vi laddar bara lokala bundlade bilder i v1.)

### 3.2 Dataflöde — Encyclopedia

```
EncyclopediaViewModel
  ├── searchQuery: MutableStateFlow<String>("")
  ├── filter: MutableStateFlow<SpeciesFilter>(SpeciesFilter())
  └── uiState: StateFlow<EncyclopediaUiState> = combine(
        searchQuery.debounce(250).distinctUntilChanged(),
        filter,
      ) { q, f -> Pair(q, f) }
       .flatMapLatest { (q, f) -> repo.search(q, locale, f) }
       .map { species -> EncyclopediaUiState.Loaded(grouped = group(species)) }
       .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loading)

EncyclopediaScreen
  ├── observeAsState(uiState)
  └── render: SearchBar + FilterButton + LazyColumn (sektioner: allmän / övriga)
```

`group(species: List<SpeciesSummary>)` partitionerar på `abundance == ALLMÄN` och returnerar två listor. Båda renderas som sticky sections med count i headern.

### 3.3 Dataflöde — Profile

```
SpeciesProfileViewModel(speciesId: SpeciesId, locale: Locale)
  └── species: StateFlow<Species?> = repo.getById(speciesId, locale)
       .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

SpeciesProfileScreen
  ├── observeAsState(species)
  ├── om null: SpeciesNotFoundEmptyState (back-knapp)
  └── om laddat: LargeTopAppBar (collapsing) + LazyColumn med SectionBlock × 3 + photos
```

### 3.4 Navigation graph

```kotlin
@Serializable sealed interface AppRoute {
    @Serializable data object Scan : AppRoute
    @Serializable data object Encyclopedia : AppRoute
    @Serializable data class SpeciesProfile(val speciesId: String) : AppRoute
    @Serializable data object Diary : AppRoute
    @Serializable data object Badges : AppRoute
}

@Composable
fun AppScaffold(graph: AppGraph) {
    val navController = rememberNavController()
    Scaffold(bottomBar = { BottomNavBar(navController) }) { padding ->
        NavHost(navController, startDestination = AppRoute.Encyclopedia, modifier = Modifier.padding(padding)) {
            composable<AppRoute.Scan> { ScanStubScreen() }
            navigation<AppRoute.Encyclopedia>(startDestination = AppRoute.Encyclopedia) {
                composable<AppRoute.Encyclopedia> {
                    EncyclopediaScreen(
                        viewModel = graph.encyclopediaViewModel(),
                        onSpeciesClick = { id -> navController.navigate(AppRoute.SpeciesProfile(id.value)) },
                    )
                }
                composable<AppRoute.SpeciesProfile> { entry ->
                    val route = entry.toRoute<AppRoute.SpeciesProfile>()
                    SpeciesProfileScreen(
                        viewModel = graph.speciesProfileViewModel(SpeciesId(route.speciesId)),
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable<AppRoute.Diary> { DiaryStubScreen() }
            composable<AppRoute.Badges> { BadgesStubScreen() }
        }
    }
}
```

`Encyclopedia` är en *nested graph* så `SpeciesProfile` pushas på Encyclopedia-stacken — back-knappen returnerar till listan, inte byter flik.

---

## 4. Skärmar i detalj

### 4.1 EncyclopediaScreen

**Layout (top → bottom):**

1. **TopAppBar** med titeln "UPPSLAGSVERK" på Mossbädd-grön bakgrund.
2. **SearchBar** alltid synlig, sticky vid scroll. Placeholder: "Sök art, släkte eller familj…". Debounce 250ms.
3. **FilterButton** under sökfältet. Visar pill med antal aktiva filter när filter är applicerade. Klick öppnar `FilterBottomSheet`.
4. **LazyColumn** med två stickyHeaders. Sektionsrubriken anpassas efter aktivt region-filter:
   - Om regions-filter ⊇ {"SE"} eller är tom: "Allmänna i Sverige (N)"
   - Annars (t.ex. bara NO valt): "Allmänna (N)"
   - Och under: "Övriga (M)"

   Sektionerna grupperar resultat-listan på `abundance == ALLMÄN`. När en sektion blir tom (t.ex. inget allmänt matchar sökningen) renderas den inte alls.

Vid 0 träffar renderas `EmptyState`: "Ingen art matchar — prova andra filter eller sök på vetenskapligt namn."

**SpeciesRow:**

- 36dp thumbnail (hero-bild via Coil eller fallback-gradient om saknas)
- Svenskt namn (primär) + scientificName (italic, dämpad, mindre)
- Optional `ALLMÄN`-badge (koppar) i högerkant

**Sökmatchning** sker i `SpeciesRepository.search(query, locale, filter)`. Nuvarande implementation matchar bara `name LIKE '%query%'` på lokaliserat namn — Plan 3 utökar `SpeciesName.sq:searchByName` (eller adderar en ny query) till att även matcha `Species.scientific_name`, och kompletterar Kotlin-impl så att `filter.regions` + `filter.activeInMonth` faktiskt filtrerar resultatet. Implementation: case-insensitive `LIKE`. Performance med 700 rader verifieras i Task 4 — ingen FTS-tabell behövs (vid behov adderas senare). Familj-matchning är ute för v0.3 — senare iteration.

### 4.2 FilterBottomSheet

Tre filter-grupper, alla via chip-toggles:

| Grupp | Värden | Mappar till `SpeciesFilter` |
|---|---|---|
| FÖREKOMST | Allmän, Ovanlig | `abundance: Set<Abundance>` |
| REGION | SE, NO, FI, DK, DE | `regions: Set<String>` |
| MÅNAD | Jan-Dec (single-select i v0.3) | `activeInMonth: String?` ("jan".."dec") |

Bottom-CTA "Visa N arter" där N uppdateras live medan användaren tappar (subscriba på preview-flow).

Sekundär-CTA "Återställ" till vänster — clear all filters.

### 4.3 SpeciesProfileScreen

**Layout:**

```
LargeTopAppBar (scrollBehavior = exitUntilCollapsed)
├── Expanded (200dp): Hero-bild med gradient-overlay + namn + sci-name
└── Collapsed (64dp): back-pil + 32dp thumb + namn + sci-name

LazyColumn (innehåll)
├── FactRow (chips: ALLMÄN/OVANLIG · familjenamn · IUCN-status)
├── SectionBlock("BESKRIVNING", isEmpty=description.isNullOrBlank(), "Beskrivning kommer i en framtida uppdatering.")
│   └── Text(species.description)
├── SectionBlock("FLYTTNING", isEmpty=migration.isNullOrBlank(), "Migrationsdata saknas för denna art.")
│   └── Text(species.migration)
└── SectionBlock("FOTOGRAFIER", isEmpty=images.isEmpty(), "Inga foton tillgängliga.")
    └── PhotoStrip (3 thumbnails, klickbara → fullscreen-overlay senare iteration)
```

**Inga CTA i Plan 3** — "Lägg till i dagboken" hör hemma i Plan 5.

### 4.4 Stub-screens (3st)

Alla tre identiska struktur:
- Mossbädd-bg
- Centrerad ikon (samma som bottom-nav)
- Rubrik (t.ex. "Skanna") i Crimson Pro
- Brödtext: "Den här funktionen kommer i Plan 4 — ML & Camera." (eller motsv. för Plan 5)

---

## 5. Sparse-data, fel & edge cases

### 5.1 SectionBlock-helper

```kotlin
@Composable
fun SectionBlock(
    label: String,
    isEmpty: Boolean,
    emptyMessage: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AccentKoppar)
        if (isEmpty) {
            Box(modifier = Modifier.background(SandCremeAlpha).padding(...)) {
                Text(emptyMessage, fontStyle = Italic, alpha = 0.7f)
            }
        } else {
            content()
        }
    }
}
```

### 5.2 Tomma fält per kontext

| Kontext | Tomt tillstånd |
|---|---|
| Hero-foto saknas | Diagonal sandig pattern + 📷-glyf + "Foto saknas"-overlay |
| `description.<lang>` tom | Inline-empty: "Beskrivning kommer i en framtida uppdatering." |
| `migration.<lang>` tom | Inline-empty: "Migrationsdata saknas för denna art." |
| `images` tom | Inline-empty: "Inga foton tillgängliga." |
| List-thumbnail saknas | 32dp grön gradient (matchar hero-fallback i miniatyr) |

### 5.3 Locale-fallback

`SpeciesRepository.getById(id, locale=SV)` returnerar redan `description = description.sv ?: description.en` (Plan 2a Task 13). Plan 3 visar bara empty-state om båda är tomma.

### 5.4 Fel-scenarier

| Scenario | Beteende |
|---|---|
| Sökning ger 0 träffar | EmptyState i listan |
| `SpeciesId` i route finns inte i DB | SpeciesNotFoundEmptyState + back-knapp |
| Coil failar att ladda lokal asset | Hero-fallback (samma som saknad) |
| 700-arts lista | `LazyColumn` + `key = species.id.value` — Compose hanterar |

---

## 6. Testning

### 6.1 ViewModel unit-tests (`composeApp/commonTest`)

Per ViewModel: fake `SpeciesRepository` som returnerar in-memory `Flow`. Tester med `kotlinx.coroutines.test.runTest` + `Turbine`.

```kotlin
class EncyclopediaViewModelTest {
    @Test fun `default state shows allmänna grouped first`()
    @Test fun `search query debounces 250ms before triggering repo`()
    @Test fun `filter changes update visible list`()
    @Test fun `empty search result emits empty state`()
}

class SpeciesProfileViewModelTest {
    @Test fun `loaded state exposes species after repo emits`()
    @Test fun `unknown id keeps null state`()
}
```

### 6.2 Compose UI-tests (`composeApp/androidUnitTest`, Robolectric)

```kotlin
@Test fun `EncyclopediaScreen renders allmänna section above övriga`()
@Test fun `tapping species row triggers navigation callback`()
@Test fun `SectionBlock renders empty message when isEmpty=true`()
@Test fun `HeroImage shows fallback when imageRef is null`()
```

Via `runComposeUiTest` så de portas till iOS senare.

### 6.3 Repository-tests

Inga ändringar — `shared/content/jvmTest/SpeciesRepositoryTest` täcker redan `getById/search/listByFamily`. Plan 3 utövar dessa men adderar inga.

### 6.4 Vad vi medvetet INTE testar

- Ingen Android instrumented test på enhet (för dyrt för solo-dev; manuell device-verifiering räcker).
- Inga snapshot-tests (Compose Multiplatform saknar moget snapshot-bibliotek på KMP).
- Inga ML/kamera-tester (Plan 4-territorium).

### 6.5 Manuell device-verifiering

Per task: skärmdump in i `docs/superpowers/screenshots/` när en milstolpe körs på SM-S918B. Skärmdumpar dubbla som visuell regressions-katalog.

Konkreta skärmdumpar som ska tas under Plan 3:
- `2026-05-XX-bottom-nav.png` — alla 4 flikar
- `2026-05-XX-encyclopedia-list.png` — grouped allmän/övriga
- `2026-05-XX-encyclopedia-search.png` — sök på "blå" → blåmes m.fl.
- `2026-05-XX-encyclopedia-filter.png` — bottom sheet öppen
- `2026-05-XX-profile-talgoxe.png` — full-data art
- `2026-05-XX-profile-sandlärka.png` — sparse-data art (Q1083050 om committed, annars annan från alaudidae)
- `2026-05-XX-profile-collapsed.png` — collapsing toolbar i collapsed state

---

## 7. i18n

```
composeApp/src/commonMain/composeResources/
├── values/strings.xml          ← svenska (default)
└── values-en/strings.xml       ← engelska
```

Strängar att flytta in från hardcoded:

| Kategori | Antal | Exempel |
|---|---|---|
| Bottom-nav-etiketter | 4 | "Skanna", "Uppslagsverk", "Dagbok", "Märken" |
| Top-bar-titlar | 4 | "UPPSLAGSVERK", "SKANNA", … |
| Section-labels | 3 | "BESKRIVNING", "FLYTTNING", "FOTOGRAFIER" |
| Filter-labels | 4 | "FÖREKOMST", "REGION", "MÅNAD", "FILTER" |
| Empty-states | 4 | "Beskrivning kommer i en framtida uppdatering.", … |
| Badges | 2 | "ALLMÄN", "OVANLIG" (presentation; data-värde i DB är fortsatt svenska) |
| CTAs | 4 | "Visa N arter" (formatString), "Återställ", "Tillämpa", "Sök art…" |
| Stub-texter | 3 | "Den här funktionen kommer i Plan 4 — ML & Camera.", … |
| SpeciesNotFound | 1 | "Art saknas." |
| Sökresultat tomt | 1 | "Ingen art matchar — prova andra filter eller sök på vetenskapligt namn." |

API: `stringResource(Res.string.foo)` från `compose-multiplatform-resources`.

---

## 8. Image-assets

Inga nya assets bundlas i Plan 3 — bilder ligger redan på rätt plats sedan Plan 2a Task 14:

```
composeApp/src/commonMain/composeResources/files/images/{Q-ID}/{hero,secondary-1,secondary-2}.jpg
```

Coil pekar på `Res.getUri("files/images/${qId}/hero.jpg")` (KMP-resource-URI). Inga downloads, ingen nätverksdep — bundlade resources behöver ingen disk-cache eftersom de redan ligger på disk.

`SpeciesImage.path` i domain-modellen håller redan rätt path-string sedan Task 13.

---

## 9. Plan 3 — task-decomposition (preview)

`writing-plans`-skillen finliriar dessa i nästa steg, men ungefärlig struktur:

| # | Task | Output |
|---|---|---|
| 1 | App-shell: AppScaffold + BottomNav + 3 stub-screens + nav-graph | Bottom-nav synlig, byter flikar; stubs visar placeholder-text |
| 2 | AppGraph (DI-container) + ViewModel-skeleton | ViewModels kan instansieras med `repo` injectad |
| 3 | EncyclopediaScreen list-only (allmän/övriga grouping, ingen sök) | Lista renderar 97 arter på riktiga data |
| 4 | Utöka `SqlDelightSpeciesRepository.search()` att matcha `scientific_name` + filtrera på `regions` + `activeInMonth` (+ jvmTest) | `search()` honorerar alla `SpeciesFilter`-fält och matchar både svenskt och vetenskapligt namn |
| 5 | EncyclopediaScreen sökfält (debounced 250ms) wirat mot utökad `search()` | Sökning matchar både `name` + `scientificName` |
| 6 | EncyclopediaScreen filter (bottom sheet + count-pill) | Förekomst + region + månad filtrerar listan |
| 7 | SpeciesProfileScreen — collapsing toolbar + statiska sektioner | Profile öppnas vid klick, hero collapsar |
| 8 | SectionBlock-helper + sparse-data-rendering | Tomma sektioner får inline-empty-text |
| 9 | i18n: extrahera alla strängar till `strings.xml` + `values-en/strings.xml` | App fungerar i båda språk |
| 10 | Polish: empty-states, hero-fallback, list-thumbnail-fallback | Sandlärka (Q1083050) ser snyggt ut |
| 11 | CI + device-verifiering + skärmdumpar + tag `v0.3.0-encyclopedia` | Milstolpe |

~11 tasks, samma rytm som Plan 2a (15 tasks, ~3 dagar). Plan 3 är mindre i omfång.

---

## 10. Vad Plan 3 lämnar för senare planer

| Funktion | Plan |
|---|---|
| "+ Lägg till i dagboken"-CTA på SpeciesProfileScreen | Plan 5 (Diary) |
| Bird-call audio playback | Plan 5/6 |
| Sökhistorik / favoriter | Plan 5 |
| Familj-sökning (sök på "mes" matchar familjen) | Senare iteration av Plan 3 om tid finns; annars Plan 6 |
| Hoppa-till-familj-snabbnavigering | Plan 6 |
| Search-by-image (foto → top-N arter) | Plan 4 (Camera) |
| Kartvisning av artens utbredning | Inte i v1 scope |
| Plan 2b: resterande ~600 arter | Resumes efter Plan 3 ships |

---

## 11. Datakvalitets-konsekvenser från Plan 2b (designat för)

Lärdomar från paridae/accipitridae/acrocephalidae/alaudidae-batcherna informerar UI:

- **50%+ av arter (i sparse-familjer som alaudidae)** har tom `description.<lang>` → inline-empty-state är inte edge-case, det är vanligt.
- **30% av sparse-familjer har `allow_missing_images: true`** → species-card och profile måste tåla 0 bilder lika bra som 1 hero + 2 secondaries.
- **`family_sv` är inkonsistent** ("Mesar" walking-skeleton vs "Mesfåglar" Wikidata-batch) → om browse senare grupperar på familj måste det ske via en mapping eller normalisering, inte direkt strängjämförelse. Plan 3 berör inte familje-gruppering.
- **`abundance` är `allmän` eller `ovanlig`** — UI använder detta som primär gruppering (sektionerna).

---

## 12. Beroenden och förutsättningar

**Klart innan Plan 3 startar:**

- ✅ Plan 2a Task 13: `SpeciesRepository` med `getById/search/listByFamily/all` + `SpeciesFilter`.
- ✅ Plan 2a Task 14: `species.db` bundlad i APK + bilder på `composeResources/files/images/`.
- ✅ Plan 2b: 97 committade arter — > tillräckligt för att exercera all UI-logik (allmän + ovanlig, sparse + full, image + no-image).
- ✅ Mossbädd-tema i `composeApp/.../ui/theme/` (Plan 1).
- ✅ Build green på SM-S918B (Plan 2a Task 14).

**Ingen ny pipeline-funktionalitet behövs** för Plan 3. Mindre utökning av `SqlDelightSpeciesRepository.search()` (region + månad-filter, scientific-name-match) sker som Plan 3 Task 4 i samma `shared/content`-modul, men ändrar inget för pipeline eller datakällor.

---

## 13. Tidigare designval bevarade

- **Mossbädd-paletten:** locked 2026-04-30, oförändrad.
- **Crimson Pro + system sans:** typografi-stack samma.
- **KMP + Compose Multiplatform:** stack samma.
- **Bundled species.db:** distributionsmodellen samma — Plan 3 ändrar bara presentationslagret.

---

## 14. Risker och okända

| Risk | Sannolikhet | Mitigation |
|---|---|---|
| Compose Multiplatform Navigation 2.x har okänd bug på Android | Låg | Fallback till hand-rolled state-based navigation om upptäckt; reverteringskostnad ~1 task |
| Coil 3 KMP har okänd bug med `Res.getUri()` | Medel | Verifiera tidigt i Task 1; alternativ är att bygga en enkel `LocalImageLoader` som öppnar `Res.readBytes()` |
| Sökmatchning i SQLite blir för långsam vid 700 arter | Låg | Nuvarande `searchByName` använder `LIKE '%q%'` med `LIMIT 50` — verifiera prestanda i Task 4 mot 700-arters DB. Vid problem: index på `SpeciesName.name` + `Species.scientific_name`, alternativt FTS5-tabell |
| Sparse-data-arter rendererar tomt på ett feel-bad-sätt | Medel | Användartesta SectionBlock i Task 7 mot riktig sparse-art (alaudidae) före Task 8 |

---

## 15. Acceptanskriterier (sammanfattning)

Plan 3 är klar när:

- [ ] App startar med bottom-nav synlig och Uppslagsverk default
- [ ] Bottom-nav byter mellan Skanna (stub), Uppslagsverk, Dagbok (stub), Märken (stub)
- [ ] EncyclopediaScreen renderar alla committade arter grupperat allmän/övriga
- [ ] Sökfält debouncas 250ms och matchar `name` + `scientificName`
- [ ] Filter bottom sheet med 3 grupper (förekomst, region, månad) filtrerar listan
- [ ] Klick på art-rad pushar SpeciesProfileScreen
- [ ] Profile har collapsing toolbar med hero som krymper till compact header
- [ ] Profile renderar BESKRIVNING / FLYTTNING / FOTOGRAFIER med sparse-fallbacks
- [ ] Coil laddar lokala bilder; saknad bild renderar diagonal sandig fallback
- [ ] Alla strängar i `strings.xml` + `values-en/strings.xml`
- [ ] ViewModel-tester gröna (>= en test per public state-transition)
- [ ] Compose UI-tester gröna (>= en test per conditional rendering)
- [ ] CI grön på `assembleDebug` + `ktlintCheck` + `detekt`
- [ ] Device-verifiering på SM-S918B med skärmdumpar in i `docs/superpowers/screenshots/`
- [ ] Tag `v0.3.0-encyclopedia` pushad

---

**Nästa steg:** användaren granskar denna spec → vid godkännande invocas `superpowers:writing-plans` för att producera `docs/superpowers/plans/2026-05-04-v1-03-encyclopedia.md`.
