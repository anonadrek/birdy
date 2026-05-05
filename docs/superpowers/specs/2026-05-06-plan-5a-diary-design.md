# Birdy Bird Scanner — Plan 5a: Diary Design Spec

**Datum:** 2026-05-06
**Status:** Utkast — väntar på användargranskning
**Spec-typ:** Plan 5a av 6 (split: 5a = Diary nu; 5b = Gamification senare som separat brainstorm)
**Föregående:** Plan 4a (ML & Camera UI) klar — `v0.4.0a-camera-ui`. Plan 2b (content backfill) kvar vid 97/700 arter, kan köra parallellt med 5a (pure-data).
**Författare:** Albin Lindblom + Claude Code (brainstormingsession 2026-05-06)

---

## 1. Bakgrund och syfte

Plan 5 i v1-specen är "Diary & Gamification": användaren ska kunna spara skannade fynd till en lokal dagbok och se progression (märken/arter sedda). Två orelaterade dimensioner ligger i samma plan: **persistens + CRUD-flöde** (DB, foto-lagring, save/edit/delete, list/detail-UI) och **gamification** (märken, statistik, "X av 700 arter"-progression, motiverande UX).

För att låta diary-arbetet röra sig utan att blockeras på gamification-design splittas Plan 5 i två:

- **Plan 5a (denna spec):** Bygger hela diary-stacken — `Observation`-datamodell, skrivbar SQLDelight-DB, `ObservationRepository`, `PhotoStorage`, Save-flow från `ClassificationResultScreen`, `DiaryScreen` (lista grupperad per månad), `ObservationDetailScreen` (collapsing toolbar + edit-note + delete). Schemat har nullable `latitude`/`longitude`/`location_label` från start så v1.5-location-feature kan fyllas in utan migration.
- **Plan 5b (separat brainstorm + spec senare):** Gamification — märken, "skannat X av 700 arter"-progression, statistik. Bygger ovanpå 5a:s `ObservationRepository` utan att ändra schema eller VM:er.

**Plan 5a är klar när:**

- "Spara i dagboken"-CTA på `ClassificationResultScreen` är aktiverad (Plan 4a hade den disabled med "kommer i Plan 5"-hint).
- Save-flow skalar foto till 1024px JPEG, persisterar till `filesDir/observations/{uuid}.jpg`, inserts DB-rad, visar snackbar "Sparad i dagboken", och stannar kvar på `ClassificationResultScreen`.
- Dagbok-fliken visar `DiaryScreen` med lista grupperad per månad (UPPERCASE-rubriker matchar Mossbädd-typografin).
- Empty-state visar "Du har inga skannade fynd än" + CTA till Skanna-fliken.
- Klick på rad öppnar `ObservationDetailScreen` med collapsing toolbar (samma mönster som Plan 3 `SpeciesProfileScreen`).
- Detail-sidan låter användaren redigera anteckningen (explicit Save-knapp), öppna art-länk till `SpeciesProfileScreen`, och radera observationen med bekräftelse-dialog.
- Foto + DB-rader överlever process-death (verifierat på device).
- Alla strängar i `strings.xml` (sv) + `values-en/strings.xml` (en).
- ViewModel-tester gröna; Repository-tester gröna med in-memory SQLDelight.
- Manuell device-verifiering på SM-S918B med skärmdumpar.
- Tag `v0.5.0a-diary` pushad.

Plan 5b kan starta direkt därefter eller deferreras tills v1.0-release.

---

## 2. Låsta beslut från brainstormingen

| # | Beslut | Motivering |
|---|---|---|
| 1 | **Plan 5 splittad:** 5a = Diary nu; 5b = Gamification separat brainstorm senare | Decoupla diary-CRUD från badge-design; 5a räcker för "Skanna & lär"-MVP; 5b kan vänta tills vi har riktiga användardata att designa motivationsmodellen mot |
| 2 | **Edit-scope = bara anteckning** | Species/datum/foto är fakta — bör inte kunna ändras post-hoc (auditerbarhet av användarens egna scans). Location deferreras till v1.5 där hela location-feature kommer in tillsammans. Note är fri text för anteckningar |
| 3 | **Schema med v1.5-redo `latitude`/`longitude`/`location_label`** som nullable från start | Ingen migration när location-feature kommer i v1.5; alltid null i 5a |
| 4 | **Foto = 1024px JPEG quality 85 till `filesDir/observations/{uuid}.jpg`** | Storleksbalans: skarpt nog för thumbnails + detail-vy, litet nog för lokal lagring av tusentals fynd. App-private storage = ingen READ_EXTERNAL_STORAGE-permission. Inga separata thumbnails — Coil cachar |
| 5 | **DiaryScreen-layout = dense list grouped by UPPERCASE month** (variant C i mockup) | Tät lista skannar snabbt över många observationer (Merlin/eBird-pattern); månadsrubriker ger temporal struktur ("vad såg jag i april?") + matchar Mossbädd-typografi |
| 6 | **DetailScreen = collapsing toolbar med foto i hero** | Matchar Plan 3 `SpeciesProfileScreen`-pattern; foto är fyndets identitet → ska dominera när expanded |
| 7 | **Save-flow = snackbar + stay** på ResultScreen | Användaren sparar för att kunna fortsätta scanna; auto-nav till diary skulle störa flödet. Snackbar bekräftar utan att flytta |
| 8 | **Settings/Export deferred till Plan 6** | 5a fokuserar på CRUD-grund. Export (CSV/JSON) + Settings (tema, locale) hör till release-polish |
| 9 | **Ny SQLDelight-DB i `shared/data`** (separat från read-only content-DB) | Plan 2b kan rebuilda content-DB:n utan att riskera dagboken; matchar v1-spec-intentionen att `shared/data` håller skrivbar data; isolerad migration-yta |
| 10 | **`ObservationRepository`-interface i `shared/domain`** | Plan 5b + framtida features kan injiceras mot interface utan deps på SQLDelight; matchar locked Plan 4a-pattern (`BirdClassifier`-interface i shared/ml) |
| 11 | **Note-edit = explicit Save-knapp** (inte auto-save på debounce) | Mindre risk för silent-data-loss; tydlig feedback via snackbar; auto-save som Plan 6-follow-up om användaren önskar |
| 12 | **Default sortering = senaste först (DESC på `captured_at_ms`)**, ingen filter-UI i 5a | Vanligaste use-caset; filter (per art, datumrange) är ren v1.5/Plan 6-feature |
| 13 | **Empty state = text + CTA till Scan** | Ingen onboarding-grafik; minimalt UI som vägleder första-användning |
| 14 | **NotFound-fallback för raderad species i content-DB** = visa observationen med "Okänd art" + disabled species-länk | Användarens data > content-rebuild-konsekvenser. DB-rebuilds (Plan 2b) får inte radera dagbok-rader pga linked-species saknas |
| 15 | **`Save` försöker rensa fotofilen om DB-insert failar efter foto-skriv** | Inga orphan-foton från crashed save. Try/catch kring photo-cleanup (no-throw) |
| 16 | **Delete = DB-row först, sedan foto-fil** | Användarens "raderad"-mental-modell följer DB-state. Orphan-foton vid foto-delete-fail loggas tyst |

---

## 3. Arkitektur och moduler

Plan 5a fyller den **tomma `shared/data`-modulen** som scaffoldats sedan Plan 1 men aldrig har haft kod, och utökar `composeApp` med två nya screens + nav-routes + AppGraph-utökning. Foto-lagring ligger som actual-implementation under `composeApp/androidMain` (i v1 är dagboken Android-only).

```
shared/domain
  └─ commonMain/
       ├─ Observation.kt                  data class
       └─ ObservationRepository.kt        interface (Flow-baserad)

shared/data
  ├─ build.gradle.kts                     SQLDelight 2.x plugin + sqldelight-coroutines
  ├─ commonMain/
  │    ├─ kotlin/se/birdy/data/
  │    │    ├─ ObservationRepositoryImpl.kt
  │    │    └─ DatabaseFactory.kt         expect class
  │    └─ sqldelight/se/birdy/data/db/
  │         └─ Observation.sq             schema + queries
  ├─ androidMain/
  │    └─ kotlin/se/birdy/data/
  │         └─ DatabaseFactory.kt         actual → AndroidSqliteDriver
  └─ iosMain/
       └─ kotlin/se/birdy/data/
            └─ DatabaseFactory.kt         actual → NativeSqliteDriver (skelett)

composeApp
  ├─ commonMain/
  │    └─ kotlin/se/birdy/app/
  │         ├─ ui/diary/
  │         │    ├─ DiaryScreen.kt              ersätter DiaryStubScreen
  │         │    ├─ DiaryViewModel.kt
  │         │    ├─ DiaryUiState.kt
  │         │    ├─ ObservationDetailScreen.kt
  │         │    ├─ ObservationDetailViewModel.kt
  │         │    ├─ ObservationDetailUiState.kt
  │         │    └─ formatRelativeDate.kt       i18n-helper
  │         ├─ usecase/
  │         │    └─ SaveObservationUseCase.kt   används av ClassificationResultViewModel
  │         ├─ photo/
  │         │    └─ PhotoStorage.kt             interface (commonMain)
  │         └─ navigation/
  │              └─ … (utökas med ObservationDetail-route)
  └─ androidMain/
       └─ kotlin/se/birdy/app/
            └─ photo/
                 └─ AndroidPhotoStorage.kt       actual → context.filesDir/observations/
```

**Beroende-riktning:** `composeApp` → `shared/data` → `shared/domain`. `composeApp` har ingen direkt SQLDelight-import — bara `ObservationRepository`-interface från domain. DI-bindning sker i `App.kt` (manuell DI eller Koin om existerande).

**Frågetecken under implementation:** verifiera om existerande projekt redan har Koin eller manuell DI — Task 1 i implementation-plan avgör.

---

## 4. Datamodell

### `shared/domain` — `Observation.kt`

```kotlin
package se.birdy.domain.observation

import kotlinx.datetime.Instant
import se.birdy.domain.species.SpeciesId

data class Observation(
    val id: String,                    // UUID v4 lowercase
    val speciesId: SpeciesId,          // återanvänder existerande value-class
    val capturedAt: Instant,           // när skannet/fotot togs (= ResultScreen-tidpunkt)
    val savedAt: Instant,              // när användaren tryckte Spara
    val photoPath: String,             // absolute path till 1024px JPEG
    val note: String,                  // tom sträng = ingen anteckning (default)
    val confidence: Float,             // 0.0..1.0 från classifier (top-1 vid save)
    val latitude: Double?,             // v1.5-redo, alltid null i 5a
    val longitude: Double?,
    val locationLabel: String?,
)
```

### `shared/data/.../sqldelight/.../Observation.sq`

```sql
CREATE TABLE observation (
    id              TEXT NOT NULL PRIMARY KEY,
    species_id      TEXT NOT NULL,
    captured_at_ms  INTEGER NOT NULL,
    saved_at_ms     INTEGER NOT NULL,
    photo_path      TEXT NOT NULL,
    note            TEXT NOT NULL DEFAULT '',
    confidence      REAL NOT NULL,
    latitude        REAL,
    longitude       REAL,
    location_label  TEXT
);

CREATE INDEX observation_captured_at_idx ON observation(captured_at_ms DESC);

selectAll:
SELECT * FROM observation ORDER BY captured_at_ms DESC;

selectById:
SELECT * FROM observation WHERE id = ?;

insert:
INSERT INTO observation(
    id, species_id, captured_at_ms, saved_at_ms,
    photo_path, note, confidence,
    latitude, longitude, location_label
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

updateNote:
UPDATE observation SET note = ? WHERE id = ?;

deleteById:
DELETE FROM observation WHERE id = ?;
```

**Mappnings-beslut:**

- `Instant` ↔ `INTEGER`-millisekunder (`Instant.toEpochMilliseconds()` / `Instant.fromEpochMilliseconds(ms)`). Sortering på samma kolumn = O(log n).
- `note` har `DEFAULT ''` → app-koden skriver alltid icke-null, default skyddar mot framtida insert-bugs.
- `confidence` sparas så detail-vy kan visa "87% säkerhet vid skanning".
- Index på `captured_at_ms DESC` matchar default-sortering.
- v1.5-kolumnerna är nullable från start — ingen migration när location-feature läggs till i v1.5.

---

## 5. Repository & PhotoStorage

### `shared/domain` — `ObservationRepository.kt`

```kotlin
package se.birdy.domain.observation

import kotlinx.coroutines.flow.Flow

interface ObservationRepository {
    fun observeAll(): Flow<List<Observation>>
    fun observeById(id: String): Flow<Observation?>
    suspend fun insert(observation: Observation)
    suspend fun updateNote(id: String, note: String)
    suspend fun delete(id: String)
}
```

### `shared/data/commonMain` — `ObservationRepositoryImpl.kt`

- Wraps `BirdyDatabase.observationQueries`.
- `observeAll()` = `queries.selectAll().asFlow().mapToList(Dispatchers.IO)` (sqldelight-coroutines-extensions).
- `observeById(id)` = `queries.selectById(id).asFlow().mapToOneOrNull(Dispatchers.IO)`.
- Konverterar mellan `Instant` ↔ `Long` (`captured_at_ms`, `saved_at_ms`).
- Konverterar `SpeciesId` ↔ `String` via existerande value-class-API.
- Suspending-funktionerna kör `withContext(Dispatchers.IO) { queries.insert(...) }` etc.

### `composeApp/commonMain` — `PhotoStorage.kt` (interface)

```kotlin
package se.birdy.app.photo

interface PhotoStorage {
    suspend fun persistJpeg(bytes: ByteArray): String   // returnerar absolute path
    suspend fun delete(path: String)                    // no-throw om filen saknas
}
```

### `composeApp/androidMain` — `AndroidPhotoStorage.kt`

- Tar `Context` (eller bara `filesDir: File`) i constructor.
- Skapar `filesDir/observations/`-katalogen on-demand via `mkdirs()`.
- Skriver bytes till `observations/{uuid}.jpg` via `FileOutputStream`.
- Returnerar `File.absolutePath`.
- `delete(path)` = `runCatching { File(path).delete() }` (ignorerar resultat) — rensning är best-effort.

### `composeApp/.../usecase/SaveObservationUseCase.kt`

```kotlin
class SaveObservationUseCase(
    private val repo: ObservationRepository,
    private val photoStorage: PhotoStorage,
    private val clock: Clock,
) {
    suspend fun save(
        speciesId: SpeciesId,
        capturedAt: Instant,
        confidence: Float,
        rawJpegBytes: ByteArray,
        note: String,
    ): String {
        val id = uuid4().toString()
        val scaledBytes = scaleAndEncode(rawJpegBytes, longestSide = 1024, quality = 85)
        val photoPath = photoStorage.persistJpeg(scaledBytes)
        try {
            repo.insert(
                Observation(
                    id = id,
                    speciesId = speciesId,
                    capturedAt = capturedAt,
                    savedAt = clock.now(),
                    photoPath = photoPath,
                    note = note,
                    confidence = confidence,
                    latitude = null,
                    longitude = null,
                    locationLabel = null,
                ),
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            runCatching { photoStorage.delete(photoPath) }
            throw t
        }
        return id
    }
}
```

**Beslut:**

- Use-case ligger i `composeApp` tills iOS-behov uppstår — då flyttas till `shared/data/commonMain`.
- `scaleAndEncode` är en Android-only helper som tar JPEG-bytes → `BitmapFactory.decodeByteArray` → skala till longestSide=1024 → `compress(JPEG, 85, ...)`. Återanvänder Plan 4a Task 8 photo-pipeline-mönster (decode på `Dispatchers.IO`).
- `clock` injiceras så test kan styra `savedAt`. Production = `Clock.System`.
- `CancellationException` rethrow:as alltid — locked Plan 4a-pattern.
- Foto-delete vid DB-fail är fire-and-forget (inga orphan-foton från crashed save).

---

## 6. UI

### 6.1 `DiaryScreen`

**Ersätter** `DiaryStubScreen.kt`. Bottom-nav-fliken "Dagbok" pekar dit.

**Toolbar:** vanlig (icke-collapsing) `TopAppBar` med titel "Dagbok" — Mossbädd hero-färg `#5C6E48`, text `#F0EAD8`. Crimson Pro.

**Stater (`DiaryUiState`):**

- `Loading` — `CircularProgressIndicator` centrerad, Mossbädd-tokens.
- `Empty` — centrerad `Column`: text "Du har inga skannade fynd än" + `Button` "Skanna en fågel" (koppar `#8C5A3C`) → navigerar till Scan-fliken.
- `Loaded(months: List<MonthGroup>)` — `LazyColumn` med två item-typer:
  - `MonthHeader(label: String)` — UPPERCASE "MAJ 2026", spärrad letter-spacing 1sp, color `#6B6F5C`, padding 10/12/6dp. Matchar `diary-layout.html` variant C-mockup.
  - `ObservationRow(obs)` — 44×44 thumbnail (Coil `AsyncImage` mot `photoPath`), namn (sv-locale), datum-relativ-formatering, confidence-pill i koppar. Klick → `ObservationDetailScreen(id)`.

**ViewModel (`DiaryViewModel`):**

```kotlin
class DiaryViewModel(
    private val repo: ObservationRepository,
    private val speciesRepo: SpeciesRepository,
    private val locale: Locale,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<DiaryUiState> = repo.observeAll()
        .combine(speciesRepo.observeAll()) { observations, species ->
            buildLoadedOrEmpty(observations, species, clock.now(), locale)
        }
        .catch { emit(DiaryUiState.Loading) /* + log */ }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiaryUiState.Loading)
}
```

- Grupperingen sker i VM:n (inte UI):
  - `kotlinx.datetime.Instant.toLocalDateTime(TimeZone.currentSystemDefault())` → `groupBy { (year, month) }`.
  - Ordning bibehålls via `LinkedHashMap` (DESC → senaste månad först).
- `species` slås upp via existerande `SpeciesRepository` så vi kan visa lokaliserat artnamn (inte bara Q-ID).
- Datum-formatering via `formatRelativeDate(instant, now, locale)` — i18n via `stringResource`-formatters.

### 6.2 `ObservationDetailScreen`

**Pattern:** Plan 3 collapsing toolbar (samma som `SpeciesProfileScreen`).

**Hero-sektion:** foto fyller toolbar:n när expanded; titel = `species.name` (sv); subtitel = formatterat datum + "%d%% säkerhet". Vid kollaps krymper till vanlig toolbar med ↩ back-knapp + species-namn. Save-knapp (för note-edit) i toolbar:n endast i `Loaded`-state.

**Body** (LazyColumn under toolbar):

- **"Anteckning"-sektion** — `OutlinedTextField` (multi-line, max 4 synliga rader, scroll-baserat). Default = befintlig note (eller tom). Save-knapp i toolbar är enabled när text != original.
- **"Detaljer"-sektion:**
  - Art (klickbar → `SpeciesProfileScreen` open encyclopedia entry). Disabled fallback "Okänd art" om species saknas i content-DB.
  - Datum (full lokaliserad: "3 maj 2026, 11:08").
  - Confidence ("87% säkerhet vid skanning").
  - Sparad ("Sparad 3 maj 2026, 11:09").
- **"Ta bort"-knapp** i koppar (separator över) → bekräftelse-dialog. Ja → `repo.delete(id)` + foto-fil-delete → pop tillbaka till DiaryScreen.

**ViewModel (`ObservationDetailViewModel(id)`):**

```kotlin
class ObservationDetailViewModel(
    private val id: String,
    private val repo: ObservationRepository,
    private val speciesRepo: SpeciesRepository,
    private val locale: Locale,
) : ViewModel() {
    val state: StateFlow<ObservationDetailUiState> = repo.observeById(id)
        .flatMapLatest { obs ->
            if (obs == null) flowOf(ObservationDetailUiState.NotFound)
            else speciesRepo.getById(obs.speciesId, locale)
                .map { species ->
                    ObservationDetailUiState.Loaded(observation = obs, species = species)
                }
        }
        .catch { emit(ObservationDetailUiState.Error(DetailErrorKind.LoadFailed)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ObservationDetailUiState.Loading)

    suspend fun saveNote(text: String) { runCatching { repo.updateNote(id, text) } /* … */ }
    suspend fun delete() { /* repo.delete + photoStorage.delete + nav.pop */ }
}
```

- `observeById`-Flow gör att note-edits reflekteras live.
- `species` = null → `Loaded`-state med fallback ("Okänd art" + disabled species-länk). Inte `NotFound` — användarens fynd ska inte försvinna pga content-DB-rebuild.
- `Error.Kind` enum: `LoadFailed | SaveNoteFailed | DeleteFailed` — UI mapar enum → `stringResource`. Locked Plan 4a-mönster.

### 6.3 Navigation

Lägg till i `composeApp/.../navigation`-tree:

- `Diary` (befintlig flik) → `DiaryScreen`
- `ObservationDetail(id: String)` → `ObservationDetailScreen` (ny route, type-safe Compose Navigation)

Bottom-nav uppdateras inte (Diary-fliken finns redan). `DiaryStubScreen` raderas.

### 6.4 Save-flow (`ClassificationResultScreen`)

- Plan 4a's `Save`-CTA är disabled med "kommer i Plan 5"-hint → enable den i 5a.
- Klick → `viewModel.saveToDiary()` → `SaveObservationUseCase.save(...)` med:
  - `speciesId` = top-1 från classification
  - `capturedAt` = scan-tidpunkt. **Ny nav-arg `capturedAtMs: Long`** läggs till på `ClassificationResult`-routen från `ScanScreen` (samma plats som `predictionsCsv` + `frameJpegPath` redan passeras). PhotoAnalyzeScreen-flödet sätter `capturedAtMs = Clock.now().toEpochMilliseconds()` vid klassificeringstid. Sätta i VM:n vid Save vore felaktigt: timestamp ska vara när fågeln faktiskt syntes, inte när användaren råkade trycka Spara.
  - `confidence` = top-1.confidence
  - `rawJpegBytes` = frame-bytes (laddade från `frameJpegPath` som Plan 4a redan persisterar till cacheDir). Om filen inte längre finns → `FrameUnavailable`-fel (se §7).
  - `note` = "" (ingen note vid save; kan editeras i Detail efteråt)
- Lyckat: snackbar "Sparad i dagboken" + stay på ResultScreen. Save-CTA:n disable:as efter save (visar "Sparad ✓") så användaren inte kan dubbel-spara.
- Fail: snackbar med error-meddelande (se §7).

---

## 7. Felhantering

**Save-flow (`ClassificationResultScreen` → `SaveObservationUseCase`):**

- **Foto-skala/encode-fail** (`Bitmap.compress` returnerar false eller OOM): VM:n går till Error-state med `kind = PhotoEncodeFailed`, snackbar "Kunde inte spara fotot". Ingen DB-row skapad.
- **Foto-skriv-fail** (`IOException` på `filesDir`): `kind = StorageFull`, snackbar "Lagringsutrymmet är fullt". Ingen DB-row.
- **DB-insert-fail** (efter foto-skriv lyckades): rensa foto-filen via `photoStorage.delete(path)` i runCatching (no-throw), `kind = DatabaseFailed`, snackbar "Kunde inte spara observationen".
- **Lyckat sparande:** snackbar "Sparad i dagboken".
- **CancellationException** rethrow:as alltid via `runCatching { ... }.onFailure { if (it is CancellationException) throw it }` — locked Plan 4a-mönster.

**Delete-flow (`ObservationDetailScreen`):**

- DB-delete först, sedan foto-fil-delete. Om foto-fil-delete failar: tyst (orphan på disk), telemetri = log-warning. Ingen "Could not delete photo"-error på användaren — DB-row är borta, det är vad användaren bad om.

**List & Detail-load:**

- `observeAll()` / `observeById()` är Flows från SQLDelight → fel ger `catch { emit(Loading) }` på list-sidan + Logcat-warn (graceful degradation till spinner). På Detail-sidan blir `NotFound` om id saknas; reellt DB-fel → `Error(LoadFailed)`.
- Om `species` i Detail returnerar null (raden raderad ur content-DB efter ny rebuild): visa `Loaded`-state men ersätt artnamn med fallback "Okänd art" + species-länken disable:as.

**Note-edit:**

- Snackbar "Anteckning sparad" vid lyckat. Vid fail: snackbar "Kunde inte spara anteckningen — försök igen". Behåll `OutlinedTextField`-värdet så användaren kan retry:a.

**i18n-strängar (sv primary + en mirror):**

```
diary_title
diary_empty_title, diary_empty_cta
diary_save_success, diary_save_error_photo, diary_save_error_storage, diary_save_error_db, diary_save_error_frame_unavailable
diary_saved_indicator                                    (för "Sparad ✓"-state på CTA)
diary_note_label, diary_note_save, diary_note_save_success, diary_note_save_error
diary_delete_button, diary_delete_confirm_title, diary_delete_confirm_body, diary_delete_confirm_yes, diary_delete_confirm_no
diary_detail_not_found, diary_detail_unknown_species
diary_detail_load_error
diary_relative_today                                     ("Idag, %s")
diary_relative_yesterday                                 ("Igår, %s")
diary_relative_date_full                                 ("%1$d %2$s, %3$s" → "3 maj, 11:08")
diary_month_header_format                                ("%1$s %2$d" → "MAJ 2026")
diary_confidence_format                                  ("%d%% säkerhet vid skanning")
```

UiState `Error.Kind`-enum för Save-flow: `PhotoEncodeFailed | StorageFull | DatabaseFailed | FrameUnavailable`. UiState `Error.Kind`-enum för Detail: `LoadFailed | SaveNoteFailed | DeleteFailed`. UI mapar enum → `stringResource`. Locked Plan 4a-mönster (ingen `Composable`-context i VM:n).

`FrameUnavailable` triggas om Plan 4a's frame-JPEG i `cacheDir` har purgats (>1h cache-TTL) innan användaren tryckte Spara. Snackbar: "Fotot är inte längre tillgängligt — skanna igen". Strängnyckel: `diary_save_error_frame_unavailable`.

---

## 8. Test-strategi

### Enhetstester (JVM, snabba)

- **`ObservationRepositoryImplTest`** (`shared/data/jvmTest`): in-memory SQLDelight-driver (`JdbcSqliteDriver(IN_MEMORY)` + `Schema.create`), Turbine för Flow-asserts. Täcker: insert→observeAll emits, observeById null-when-missing, updateNote, delete, sortering DESC på `captured_at_ms`, Instant↔Long round-trip.
- **`SaveObservationUseCaseTest`** (`composeApp/test`): `FakeObservationRepository` + `FakePhotoStorage` (in-memory map). Täcker: lyckat save inserts row + persisterar bytes, photo-fail kastar inte DB (insert hoppas över), db-fail efter photo rensar fotofilen, savedAt = `Clock.fixed`-värde, `CancellationException` rethrow:as.
- **`DiaryViewModelTest`**: `FakeObservationRepository` med pre-laddade rader över olika månader + `FakeSpeciesRepository.withDefaults()` (Plan 4a Task 9-fixturen). Täcker: Empty-state vid tom lista, Loaded-state med korrekt månadsgruppering inkl. ordning DESC, Loading-state innan första emit. `MainDispatcherRule` + `runTest`.
- **`ObservationDetailViewModelTest`**: `FakeObservationRepository` + `FakeSpeciesRepository.withDefaults()`. Täcker: Loaded med korrekt arts-data, NotFound när id saknas, "Okänd art"-fallback när species saknas, saveNote-success uppdaterar Flow, delete-success.
- **`FormatRelativeDateTest`** (`composeApp/test`): tabell-driven över sv- + en-locale med `now`-injection — verifierar "Idag", "Igår", "%d %s, %s" för ≤7 dagar / >7 dagar och cross-year-boundary.

### Instrumented / device-verification (manual via Galaxy S23 Ultra)

- Save från ResultScreen → snackbar visas → öppna Diary-flik → fynd visas i rätt månadsgrupp → tap → Detail öppnas → photo + species + datum stämmer → ändra note + Save → snackbar → backa → Diary visar fortfarande raden → Detail igen → note stämmer → Delete + bekräfta → Diary visar tom-state om enda raden.
- Empty-state CTA → navigerar till Scan-fliken.
- Re-launch app efter save → fynd persisterar (DB + foto överlever process-death).
- Skanna 5+ observationer över 2+ "datum" (testa via klock-injection eller vänta över midnatt) → DiaryScreen visar månadsgrupper korrekt.

### Screenshots (sista task efter polish)

- `2026-05-XX-05a-diary-empty.png` — empty-state med CTA
- `2026-05-XX-05a-diary-list.png` — minst 2 månadsgrupper synliga
- `2026-05-XX-05a-detail-expanded.png` — collapsing-toolbar expanded med foto-hero
- `2026-05-XX-05a-detail-collapsed.png` — kollapsad toolbar + body med detaljer
- `2026-05-XX-05a-detail-edit-note.png` — `OutlinedTextField` med fokus
- `2026-05-XX-05a-delete-confirm.png` — bekräftelse-dialog
- `2026-05-XX-05a-save-snackbar.png` — snackbar "Sparad i dagboken" på ResultScreen

### Ej i scope för 5a-tester

- iOS-test (data-modulen kompileras för iOS men ingen aktiv testpath).
- Performance/load-test (>1000 rader — Plan 6 om relevant).
- Migration-test (inget tidigare schema att migrera från — ren `CREATE TABLE`).
- E2E (instrumented Espresso/Compose — manuell device-verifiering räcker för 5a).

---

## 9. Återanvända Plan 4a-mönster

- **`Error.Kind` enum** (inte `String`) i alla UiState — UI mapar enum → `stringResource`. ViewModel stays Composable-context-free.
- **`runCatching { ... }.onFailure { if (it is CancellationException) throw it }`** för structured-concurrency-säkra suspend-blocks.
- **`@Composable expect fun X` + actual i androidMain** för Android-only Composables (om någon hero-foto-rendering kräver det — annars onödigt här).
- **`FakeRepository.withDefaults()`-fixture** — `FakeObservationRepository.withDefaults()` skapar 5 sample-observationer över 2 månader för VM-tester.
- **i18n-disciplin:** SV `values/strings.xml` är primary, EN `values-en/strings.xml` mirroras — båda måste ha varje key. Plan 4a Task 4-pattern.
- **`stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ...)`** för UI-state-flows.

---

## 10. Out of scope för Plan 5a

- **Gamification (märken, statistik, "X av 700 arter"-progression)** — Plan 5b.
- **Export (CSV/JSON)** — Plan 6.
- **Settings (tema-toggle, locale-override, manual-purge)** — Plan 6.
- **Filter / sök i Diary-listan** — v1.5/Plan 6.
- **Location-data (latitude, longitude, kart-pin)** — v1.5. Schemat har kolumnerna nullable redan.
- **Cloud-sync** — v1.5.
- **iOS-implementation** — v1.5+. Schemat + repo finns i KMP-common; iOS-actual för `DatabaseFactory` är ren SQLite-driver-bindning.
- **Auto-save på note-debounce** — Plan 6 om användaren önskar.
- **Edit av species/datum/foto** — uttalad designprincip (auditerbarhet).

---

## 11. Risker och osäkerheter

- **Bitmap-decoding av stora frames i Save**: Plan 4a captured-frame är cache:ad som JPEG i `cacheDir/frame-{ts}.jpg`. Om användaren har en låg-end-device kan decode + scale slå mot OOM. Mitigation: try/catch + `kind = PhotoEncodeFailed` (graceful failure). Plan 4a Task 8 hade samma pattern på photo-analyze-flödet.
- **Cache-purge mellan scan och save**: Plan 4a's frame-JPEG ligger i `cacheDir` med 1h purge. Om användaren scannar, går till annan app, kommer tillbaka >1h senare → frame-fil kan vara borta. Save-flow måste hantera `FileNotFoundException` → snackbar "Fotot är inte längre tillgängligt — skanna igen". Logga som ny error-kind: `FrameUnavailable`.
- **DiaryViewModel `combine(observations, species)`**: om species-DB:n är tom (osannolikt — content är read-only-asset packad i APK) skulle Loaded:n få "Okänd art" på alla rader. Acceptabel degradation.
- **process-death mid-save**: om appen kraschar mellan `photoStorage.persistJpeg` och `repo.insert` → orphan-foto på disk. Mitigation: vid app-start, scan `filesDir/observations/` mot `selectAll()` → radera filer utan motsvarande DB-row. **Defer:** lägg som Plan 6-housekeeping. Disk-cost är några MB i värsta fall.
- **`SpeciesId` value-class i Compose Navigation**: Plan 4a Task 9 visade att Compose Navigation type-safe routes med custom value-classes funkade — ObservationDetail-route använder `String` för enkelhet (UUID är String).
- **Existerande DI-system**: vet inte om projektet har Koin eller manuell DI. Task 1 i implementation-plan undersöker; om Koin finns används det, annars manuell wiring i `App.kt`. Ingen blocker — bara extra task.

---

## 12. Definition of Done för Plan 5a

- [ ] `shared/data` har SQLDelight-pluginen + `Observation.sq` + `ObservationRepositoryImpl` + `expect/actual DatabaseFactory`.
- [ ] `shared/domain` har `Observation` + `ObservationRepository`-interface.
- [ ] `composeApp` har `PhotoStorage`-interface + `AndroidPhotoStorage`-actual + `SaveObservationUseCase`.
- [ ] `DiaryStubScreen` ersatt av `DiaryScreen` med Loading/Empty/Loaded-states och månadsgruppering.
- [ ] `ObservationDetailScreen` med collapsing toolbar, edit-note, delete-confirm, "Okänd art"-fallback.
- [ ] `ClassificationResultScreen` Save-CTA enabled, kallar `SaveObservationUseCase`, visar snackbar, byter till "Sparad ✓"-state efter lyckat save.
- [ ] Compose Navigation utökad med `ObservationDetail(id: String)`-route.
- [ ] Alla strängar i sv + en. `i18n-disciplin`-pattern: båda har samma keys.
- [ ] Repository-tester (in-memory SQLDelight) gröna.
- [ ] Use-case-tester (foto+DB-fail-scenarier) gröna.
- [ ] VM-tester (Diary + Detail) gröna med Turbine.
- [ ] FormatRelativeDate-tester gröna över sv + en + edge-cases.
- [ ] Manuell device-verifiering på SM-S918B genomförd. Save → list → detail → edit → delete-flöde verifierat.
- [ ] Process-death-test: scanna+save → kill app via `adb shell am force-stop` → re-launch → fynd kvar.
- [ ] 7 screenshots committade till `docs/superpowers/screenshots/`.
- [ ] CLAUDE.md uppdaterad: status-rad, plan-of-plans-tabell, "Avslutade planer (referens)"-entry för Plan 5a med återanvändbara mönster.
- [ ] Auto-memory `project_plan_5a_status.md` skriven (eller `project_plan_5_status.md` om den ska överlappa 5b senare).
- [ ] `./gradlew :shared:domain:jvmTest :shared:data:jvmTest :composeApp:testDebugUnitTest ktlintCheck detekt :composeApp:assembleDebug` allt grönt.
- [ ] Tag `v0.5.0a-diary` skapad och pushad. CLAUDE.md status-rad pekar på den.

---

## 13. Brainstormingens tidslinje (kort)

- **2026-05-06 (denna spec):** Plan 5 brainstorm efter att Plan 4a tagged som `v0.4.0a-camera-ui`. 8 clarifying questions + arkitektur-approach-val (A: ny SQLDelight-DB i `shared/data` + interface i `shared/domain`). Visuell companion (port 59928) använd för DiaryScreen-layout-jämförelse (3 alternativ: dense list, foto-grid, månadsgrupperad lista) — variant C vald.

---

## 14. Nästa steg

- Användargranskning av denna spec.
- Justeringar baserat på feedback.
- `superpowers:writing-plans` invokas för att skapa implementation-plan med 8–10 task-uppdelningar (typiskt: setup → schema/repo → use-case → DiaryScreen → DetailScreen → Save-integration → tests → polish + screenshots → tag).
- Plan 5a exekveras via `superpowers:subagent-driven-development` (Sonnet 4.6 implementer + Opus 4.7 reviewer/controller — samma modell som Plan 3 + 4a).
