# DP A — Sök-fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Söket hittar arter oavsett apostroftyp, diakriter och aktivt språk, plus familj-/genus-sök och prefix-boost.

**Architecture:** En normaliserad `search_text`-kolumn på `SpeciesName` (byggd vid DB-build via `expect/actual normalizeSearch` med `java.text.Normalizer`) som konkatenerar `name + scientific_name + family + family_sv + genus`. Söket matchar enbart mot `search_text` (query normaliseras med samma funktion), utan locale-filter (cross-locale + dedup). Content-DB-fingeravtrycket (`application_id`) flippas via en manuell `SCHEMA_REV`-konstant i `contentHash` så schemaändringen tvingar DB-replace på uppgradering (befintliga `needsCopy` jämför redan `application_id`).

**Tech Stack:** Kotlin Multiplatform (commonMain/jvmMain/androidMain), SQLDelight, `java.text.Normalizer`, kotlin.test, JdbcSqliteDriver (jvmTest).

**Spec:** `docs/superpowers/specs/2026-05-29-v1-x-tester-feedback-response-design.md` (§4 DP A). Research: `docs/superpowers/research/2026-05-29-hardcore-tester-feedback.md` (Problem 2).

**Bash gradle-prefix (Windows):**
```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
```

---

## File Structure

**Skapas:**
- `shared/content/src/commonMain/kotlin/se/birdy/content/search/SearchNormalize.kt` — `expect fun normalizeSearch(String): String`
- `shared/content/src/jvmMain/kotlin/se/birdy/content/search/SearchNormalize.jvm.kt` — `actual` (java.text.Normalizer)
- `shared/content/src/androidMain/kotlin/se/birdy/content/search/SearchNormalize.android.kt` — `actual` (identisk; runtime på Android)
- `shared/content/src/jvmTest/kotlin/se/birdy/content/search/SearchNormalizeTest.kt`

**Modifieras:**
- `shared/content/src/commonMain/sqldelight/se/birdy/content/SpeciesName.sq` — `search_text`-kolumn + index + `INSERT`-param + omskriven `searchByNameOrScientific`
- `shared/content/src/jvmMain/kotlin/se/birdy/content/build/SpeciesDbBuilder.kt` — beräkna `search_text` per namn-insert
- `shared/content/src/commonMain/kotlin/se/birdy/content/SqlDelightSpeciesRepository.kt` — normalisera query, cross-locale-dedup, visningsnamn i användarens locale
- `shared/content/src/jvmTest/kotlin/se/birdy/content/SpeciesRepositoryTest.kt` — regressionstester
- (Provider `SpeciesRepositoryProvider.android.kt` ÄNDRAS INTE — `needsCopy` jämför redan `application_id` som SCHEMA_REV flippar. Task 4 = regressionstest i `SpeciesDbBuilderTest.kt`.)

---

## Task 1: `normalizeSearch` (expect/actual) + test

**Files:**
- Create: `shared/content/src/commonMain/kotlin/se/birdy/content/search/SearchNormalize.kt`
- Create: `shared/content/src/jvmMain/kotlin/se/birdy/content/search/SearchNormalize.jvm.kt`
- Create: `shared/content/src/androidMain/kotlin/se/birdy/content/search/SearchNormalize.android.kt`
- Test: `shared/content/src/jvmTest/kotlin/se/birdy/content/search/SearchNormalizeTest.kt`

- [ ] **Step 1: Skriv failande test**

```kotlin
package se.birdy.content.search

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchNormalizeTest {
    @Test fun `ascii apostrophe stripped`() =
        assertEquals("eleonoras falcon", normalizeSearch("Eleonora's Falcon"))

    @Test fun `typographic apostrophe U2019 stripped`() =
        assertEquals("eleonoras falcon", normalizeSearch("Eleonora’s Falcon"))

    @Test fun `diacritics stripped`() =
        assertEquals("ruppells vulture", normalizeSearch("Rüppell's Vulture"))

    @Test fun `lowercased and whitespace collapsed`() =
        assertEquals("falco eleonorae", normalizeSearch("  Falco   eleonorae "))

    @Test fun `empty stays empty`() =
        assertEquals("", normalizeSearch(""))
}
```

- [ ] **Step 2: Kör test, verifiera FAIL**

Run:
```bash
./gradlew :shared:content:jvmTest --tests "se.birdy.content.search.SearchNormalizeTest"
```
Expected: FAIL — `normalizeSearch` finns inte.

- [ ] **Step 3: Skapa expect + actuals**

`SearchNormalize.kt` (commonMain):
```kotlin
package se.birdy.content.search

/**
 * Normalisera text för fritextsök: dekomponera + strippa diakriter, strippa
 * apostrof-varianter, lowercase, kollapsa whitespace. Samma funktion appliceras
 * på lagrad search_text (build-tid) och på query (runtime) → symmetrisk matchning.
 */
expect fun normalizeSearch(input: String): String
```

`SearchNormalize.jvm.kt` (jvmMain):
```kotlin
package se.birdy.content.search

import java.text.Normalizer

actual fun normalizeSearch(input: String): String =
    Normalizer.normalize(input, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "") // combining marks (diakriter)
        .replace(Regex("['’ʼ`]"), "") // ' ' ʼ ` → strippas
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()
```

`SearchNormalize.android.kt` (androidMain) — identisk kropp (java.text.Normalizer finns på Android):
```kotlin
package se.birdy.content.search

import java.text.Normalizer

actual fun normalizeSearch(input: String): String =
    Normalizer.normalize(input, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("['’ʼ`]"), "")
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()
```

> Skapa katalogen `shared/content/src/androidMain/kotlin/se/birdy/content/search/` (androidMain-källset finns deklarerat i `build.gradle.kts` men ingen `src/androidMain/`-mapp än — skapa den).

- [ ] **Step 4: Kör test, verifiera PASS**

Run:
```bash
./gradlew :shared:content:jvmTest --tests "se.birdy.content.search.SearchNormalizeTest"
```
Expected: PASS (5 tester).

- [ ] **Step 5: Commit**

```bash
git add shared/content/src/commonMain/kotlin/se/birdy/content/search/ shared/content/src/jvmMain/kotlin/se/birdy/content/search/ shared/content/src/androidMain/kotlin/se/birdy/content/search/ shared/content/src/jvmTest/kotlin/se/birdy/content/search/
git commit -m "feat(search): normalizeSearch expect/actual (apostrof + diakriter + lowercase)"
```

---

## Task 2: `search_text`-kolumn + builder-population

**Files:**
- Modify: `shared/content/src/commonMain/sqldelight/se/birdy/content/SpeciesName.sq`
- Modify: `shared/content/src/jvmMain/kotlin/se/birdy/content/build/SpeciesDbBuilder.kt`

> Den här tasken lämnar `searchByNameOrScientific` orörd (query rewrite sker i Task 3) så `SqlDelightSpeciesRepository` fortsätter kompilera. Endast schema + INSERT + builder ändras.

- [ ] **Step 1: Lägg till kolumn + index + INSERT-param**

I `SpeciesName.sq`, ändra `CREATE TABLE` + index + `insert`:
```sql
CREATE TABLE SpeciesName (
    species_id TEXT NOT NULL,
    locale TEXT NOT NULL,
    name TEXT NOT NULL,
    search_text TEXT NOT NULL DEFAULT '',
    PRIMARY KEY (species_id, locale),
    FOREIGN KEY (species_id) REFERENCES Species(id) ON DELETE CASCADE
);

CREATE INDEX species_name_locale_name ON SpeciesName(locale, name);
CREATE INDEX species_name_search ON SpeciesName(search_text);
```
Och `insert`:
```sql
insert:
INSERT INTO SpeciesName(species_id, locale, name, search_text) VALUES (?, ?, ?, ?);
```
(Lämna `searchByName`, `searchByNameOrScientific`, `selectByLocale`, `selectBySpecies`, `selectAll` oförändrade i denna task.)

- [ ] **Step 2: Folda `SCHEMA_REV` in i `contentHash` (upgrade-säkerhet)**

**VERIFIERAT 2026-05-29:** `shared/content` har INGEN migrations-katalog och INTE `deriveSchemaFromMigrations` → `BirdyContent.Schema.version` är hårdkodad `1` och ändras INTE av schema-ändringar. Dessutom hashar `contentHash` bara `id+generated_at`, så en ombyggd `species.db` med oförändrade YAML får IDENTISKT `application_id` → cachad gammal DB (utan `search_text`) behålls på uppgradering → **KRASCH**. Fix: folda en manuell schema-revision in i `contentHash` så `application_id` flippar. Befintliga `needsCopy` jämför redan `application_id` → ersätter DB:n. **Ingen migration, ingen provider-ändring** (Task 4 blir därför ett regressionstest, inte en provider-fix).

I `SpeciesDbBuilder.kt`: lägg en konstant i fil-/klass-scope och inkludera den i den hashade signaturen:
```kotlin
// Bumpa vid VARJE schema-ändring i Species*.sq → flippar application_id → tvingar DB-replace på uppgradering.
private const val SCHEMA_REV = 2
```
Ändra `contentHash` så signaturen prefixas med schema-revisionen:
```kotlin
private fun contentHash(items: List<Pair<Path, SpeciesYaml>>): Int {
    val signature =
        "schema=$SCHEMA_REV\n" +
            items
                .map { (_, y) -> "${y.id}:${y.generated_at}" }
                .sorted()
                .joinToString("\n")
    val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
    return ((digest[0].toInt() and 0xFF) shl 24) or
        ((digest[1].toInt() and 0xFF) shl 16) or
        ((digest[2].toInt() and 0xFF) shl 8) or
        (digest[3].toInt() and 0xFF)
}
```
(Lämna `PRAGMA user_version`-raden orörd — ofarlig.)

- [ ] **Step 3: Populera search_text i builder**

I `SpeciesDbBuilder.kt`, byt namn-insert-blocket (rad 70–73) mot:
```kotlin
val sci = yaml.scientific_name
val fam = yaml.taxonomy.family
val famSv = yaml.taxonomy.family_sv ?: ""
val genus = yaml.taxonomy.genus
if (!yaml.names.sv.isNullOrBlank()) {
    val sv = yaml.names.sv!!
    db.speciesNameQueries.insert(yaml.id, "sv", sv, normalizeSearch("$sv $sci $fam $famSv $genus"))
}
val en = yaml.names.en
db.speciesNameQueries.insert(yaml.id, "en", en, normalizeSearch("$en $sci $fam $famSv $genus"))
```
Lägg import: `import se.birdy.content.search.normalizeSearch`.

- [ ] **Step 4: Verifiera kompilering**

Run:
```bash
./gradlew :shared:content:compileKotlinJvm
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add shared/content/src/commonMain/sqldelight/ shared/content/src/jvmMain/kotlin/se/birdy/content/build/SpeciesDbBuilder.kt
git commit -m "feat(search): SpeciesName.search_text-kolumn + builder-population + SCHEMA_REV-bump"
```

---

## Task 3: Query-rewrite + repository (cross-locale, dedup, prefix-boost) + tester

**Files:**
- Modify: `shared/content/src/commonMain/sqldelight/se/birdy/content/SpeciesName.sq`
- Modify: `shared/content/src/commonMain/kotlin/se/birdy/content/SqlDelightSpeciesRepository.kt`
- Test: `shared/content/src/jvmTest/kotlin/se/birdy/content/SpeciesRepositoryTest.kt`

- [ ] **Step 1: Skriv failande regressionstester**

> Bekräfta hur `SpeciesRepositoryTest.kt` bygger sin in-memory-DB (JdbcSqliteDriver + `BirdyContent.Schema.create`). Spegla det och seeda via de genererade queries. `normalizeSearch` finns i jvmTest (jvmMain-actual). `search(query, locale, filters)` returnerar `Flow<List<SpeciesSummary>>` — samla med `.first()` (turbine/`runTest`).

Lägg till i `SpeciesRepositoryTest.kt` (ett helper som seedar en art + namn med search_text, sen testerna):
```kotlin
private fun BirdyContent.seedSpecies(
    id: String, sci: String, family: String, familySv: String, genus: String,
    sv: String, en: String, abundance: String = "ovanlig",
) {
    speciesQueries.insert(id, sci, abundance, "LC", "2026-01-01", "approved", null, null, null)
    speciesTaxonomyQueries.insert(id, family, familySv, genus, "Falconiformes")
    speciesNameQueries.insert(id, "sv", sv, normalizeSearch("$sv $sci $family $familySv $genus"))
    speciesNameQueries.insert(id, "en", en, normalizeSearch("$en $sci $family $familySv $genus"))
}

@Test
fun `finds Eleonora with plain apostrophe against U2019 data`() = runTest {
    val db = inMemoryDb() // befintlig helper i testfilen, annars skapa enligt mönstret
    db.seedSpecies("Q212243", "Falco eleonorae", "Falconidae", "falkfåglar", "Falco",
        sv = "Eleonorafalk", en = "Eleonora’s Falcon")
    val repo = SqlDelightSpeciesRepository(db)
    val hits = repo.search("Eleonora's Falcon", Locale.EN, SpeciesFilter()).first()
    assertEquals(1, hits.size)
    assertEquals("Q212243", hits.first().id.value)
}

@Test
fun `cross-locale finds english name while in SV locale`() = runTest {
    val db = inMemoryDb()
    db.seedSpecies("Q212243", "Falco eleonorae", "Falconidae", "falkfåglar", "Falco",
        sv = "Eleonorafalk", en = "Eleonora’s Falcon")
    val repo = SqlDelightSpeciesRepository(db)
    val hits = repo.search("Eleonora's Falcon", Locale.SV, SpeciesFilter()).first()
    assertEquals(1, hits.size)
    assertEquals("Eleonorafalk", hits.first().name) // visningsnamn i SV-locale
}

@Test
fun `diacritic-insensitive`() = runTest {
    val db = inMemoryDb()
    db.seedSpecies("Q1", "Gyps rueppellii", "Accipitridae", "hökartade rovfåglar", "Gyps",
        sv = "Rüppellgam", en = "Rüppell’s Vulture")
    val repo = SqlDelightSpeciesRepository(db)
    assertEquals(1, repo.search("ruppell", Locale.EN, SpeciesFilter()).first().size)
}

@Test
fun `family search returns family members`() = runTest {
    val db = inMemoryDb()
    db.seedSpecies("Q212243", "Falco eleonorae", "Falconidae", "falkfåglar", "Falco",
        sv = "Eleonorafalk", en = "Eleonora’s Falcon")
    val repo = SqlDelightSpeciesRepository(db)
    assertEquals(1, repo.search("falcon", Locale.EN, SpeciesFilter()).first().size) // via "Falconidae"
}
```

- [ ] **Step 2: Kör testerna, verifiera FAIL**

Run:
```bash
./gradlew :shared:content:jvmTest --tests "se.birdy.content.SpeciesRepositoryTest"
```
Expected: FAIL — söket matchar `name`-kolumnen med locale-filter, inte `search_text`; cross-locale + apostrof-fall ger fel antal.

- [ ] **Step 3: Skriv om query:n**

I `SpeciesName.sq`, ersätt `searchByNameOrScientific`:
```sql
searchByNameOrScientific:
SELECT sn.species_id AS species_id, sn.locale AS locale, sn.name AS name
FROM SpeciesName sn
WHERE sn.search_text LIKE ('%' || :query || '%')
ORDER BY CASE WHEN sn.search_text LIKE (:query || '%') THEN 0 ELSE 1 END, sn.name
LIMIT :max;
```
(Tar bort JOIN + locale-filter; matchar bara `search_text`; prefix-boost i ORDER BY. Genererad signatur blir `searchByNameOrScientific(query, max)` — `locale`-parametern försvinner.)

- [ ] **Step 4: Uppdatera repository.search**

I `SqlDelightSpeciesRepository.kt`, byt `search`-metoden (rad 93–151). Ändringar: normalisera query, droppa `locale` ur query-anropet, sätt visningsnamn i användarens locale:
```kotlin
override fun search(
    query: String,
    locale: Locale,
    filters: SpeciesFilter,
): Flow<List<SpeciesSummary>> =
    db.speciesNameQueries
        .searchByNameOrScientific(query = normalizeSearch(query), max = Long.MAX_VALUE)
        .asFlow()
        .mapToList(Dispatchers.Default)
        .map { rows ->
            rows
                .distinctBy { it.species_id }
                .mapNotNull { row ->
                    val sp =
                        db.speciesQueries
                            .selectById(row.species_id)
                            .executeAsOneOrNull() ?: return@mapNotNull null
                    val abundance =
                        Abundance.fromCode(sp.abundance) ?: Abundance.OVANLIG
                    if (filters.abundance.isNotEmpty() && abundance !in filters.abundance) {
                        return@mapNotNull null
                    }
                    if (filters.regions.isNotEmpty()) {
                        val speciesRegions =
                            db.speciesRegionQueries
                                .selectBySpecies(sp.id)
                                .executeAsList()
                                .toSet()
                        if (filters.regions.intersect(speciesRegions).isEmpty()) {
                            return@mapNotNull null
                        }
                    }
                    if (filters.activeInMonth != null) {
                        val seasons =
                            db.speciesSeasonQueries.selectBySpecies(sp.id).executeAsList()
                        val month = seasons.firstOrNull { it.month == filters.activeInMonth }
                        if (month == null || month.status == "absent") {
                            return@mapNotNull null
                        }
                    }
                    val taxonomy =
                        db.speciesTaxonomyQueries
                            .selectBySpecies(sp.id)
                            .executeAsOneOrNull()
                    val names = db.speciesNameQueries.selectBySpecies(sp.id).executeAsList()
                    val displayName =
                        names.firstOrNull { it.locale == locale.code }?.name
                            ?: names.firstOrNull()?.name
                            ?: sp.scientific_name
                    SpeciesSummary(
                        id = SpeciesId(sp.id),
                        name = displayName,
                        scientificName = sp.scientific_name,
                        abundance = abundance,
                        heroImagePath =
                            db.speciesImageQueries
                                .selectBySpecies(sp.id)
                                .executeAsList()
                                .firstOrNull { it.role == "hero" }
                                ?.path,
                        iocOrder = taxonomy?.ioc_order ?: "",
                        family = taxonomy?.family ?: "",
                    )
                }
        }
```
Lägg import: `import se.birdy.content.search.normalizeSearch`.

- [ ] **Step 5: Kör testerna, verifiera PASS**

Run:
```bash
./gradlew :shared:content:jvmTest --tests "se.birdy.content.SpeciesRepositoryTest"
```
Expected: PASS (inkl. de fyra nya + befintliga sök-tester). Justera ev. befintliga sök-tester som antog locale-filter/`name`-matchning.

- [ ] **Step 6: Commit**

```bash
git add shared/content/src/commonMain/sqldelight/se/birdy/content/SpeciesName.sq shared/content/src/commonMain/kotlin/se/birdy/content/SqlDelightSpeciesRepository.kt shared/content/src/jvmTest/kotlin/se/birdy/content/SpeciesRepositoryTest.kt
git commit -m "feat(search): cross-locale + normaliserad search_text-query + prefix-boost + tester"
```

---

## Task 4: Regressionstest för SCHEMA_REV-fingeravtrycket

**Files:**
- Modify: `shared/content/src/jvmMain/kotlin/se/birdy/content/build/SpeciesDbBuilder.kt` (extrahera testbar fingerprint-funktion)
- Modify: `shared/content/src/jvmTest/kotlin/se/birdy/content/build/SpeciesDbBuilderTest.kt`

> **Ingen provider-ändring behövs.** `SpeciesRepositoryProvider.android.kt:needsCopy` jämför redan `application_id` (offset 68) bundlad-vs-cachad och kopierar om vid skillnad. Upgrade-säkerheten hänger nu på att `SCHEMA_REV` (Task 2) flippar `application_id`. Den här tasken låser mekaniken med ett test så en framtida refaktor inte tyst tar bort `SCHEMA_REV` ur hashen.

- [ ] **Step 1: Extrahera testbar fingerprint-funktion**

I `SpeciesDbBuilder.kt`, refaktorera `contentHash` så schema-revisionen är en parameter och funktionen är `internal` (åtkomlig från jvmTest i samma modul):
```kotlin
private fun contentHash(items: List<Pair<Path, SpeciesYaml>>): Int =
    contentFingerprint(items, SCHEMA_REV)

internal fun contentFingerprint(
    items: List<Pair<Path, SpeciesYaml>>,
    schemaRev: Int,
): Int {
    val signature =
        "schema=$schemaRev\n" +
            items
                .map { (_, y) -> "${y.id}:${y.generated_at}" }
                .sorted()
                .joinToString("\n")
    val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
    return ((digest[0].toInt() and 0xFF) shl 24) or
        ((digest[1].toInt() and 0xFF) shl 16) or
        ((digest[2].toInt() and 0xFF) shl 8) or
        (digest[3].toInt() and 0xFF)
}
```

- [ ] **Step 2: Skriv testet**

> Återanvänd den befintliga YAML-fixturen i `SpeciesDbBuilderTest.kt` (testet `application_id is stable ... when generated_at changes` på ~rad 68/85 konstruerar redan `SpeciesYaml` + `yaml.copy(...)`). Spegla hur den bygger sin `items: List<Pair<Path, SpeciesYaml>>`.

```kotlin
@Test
fun `schema rev change flips fingerprint`() {
    val builder = SpeciesDbBuilder()
    val items = listOf(samplePath to sampleYaml()) // använd testfilens befintliga fixtur-helpers
    assertNotEquals(
        builder.contentFingerprint(items, 1),
        builder.contentFingerprint(items, 2),
    )
}

@Test
fun `fingerprint stable for same content and schema rev`() {
    val builder = SpeciesDbBuilder()
    val items = listOf(samplePath to sampleYaml())
    assertEquals(
        builder.contentFingerprint(items, 2),
        builder.contentFingerprint(items, 2),
    )
}
```
(`samplePath`/`sampleYaml()` = testfilens befintliga fixtur — använd de namn som redan finns där. Import `kotlin.test.assertNotEquals`.)

- [ ] **Step 3: Kör testet, verifiera PASS**

Run:
```bash
./gradlew :shared:content:jvmTest --tests "se.birdy.content.build.SpeciesDbBuilderTest"
```
Expected: PASS (de två nya + befintliga builder-tester).

- [ ] **Step 4: Commit**

```bash
git add shared/content/src/jvmMain/kotlin/se/birdy/content/build/SpeciesDbBuilder.kt shared/content/src/jvmTest/kotlin/se/birdy/content/build/SpeciesDbBuilderTest.kt
git commit -m "test(search): lås SCHEMA_REV-fingeravtrycket (schema-rev flippar application_id)"
```

---

## Task 5: Bygg om species.db + full verifiering

**Files:** (genererad asset + verifiering)

- [ ] **Step 1: Bygg om content-DB:n**

Run:
```bash
./gradlew :shared:content:buildSpeciesDb
```
Expected: BUILD SUCCESSFUL. Den nya `species.db` (i composeApp composeResources/files) har nu `search_text`-kolumnen populerad + nytt `application_id` (p.g.a. SCHEMA_REV).

- [ ] **Step 2: Kör hela testsviten + lint**

Run:
```bash
./gradlew :shared:content:jvmTest :composeApp:testDebugUnitTest ktlintCheck detekt
```
Expected: PASS / BUILD SUCCESSFUL. `./gradlew ktlintFormat` vid behov.

- [ ] **Step 3: Bygg debug-APK**

Run:
```bash
./gradlew :androidApp:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit (om buildSpeciesDb eller ktlintFormat ändrade något)**

```bash
git add -A
git commit -m "chore(search): bygg om species.db med search_text + grön svit" || echo "inget att committa"
```

---

## Task 6: Device-verify på SM-S918B

**Files:** (manuell verifiering — se [[feedback_personal_device_verify]]: be användaren lägga ifrån sig telefonen + verifiera via screencap. Debug-paket: `se.birdy.android.debug`. `MSYS_NO_PATHCONV=1` före `/sdcard/...`.)

> Kritiskt: testa uppgraderings-vägen (gammal cachad DB → ny). Installera FÖRST en build utan ändringen (eller behåll appdata från nuvarande installation), installera sedan den nya — verifiera att appen INTE kraschar (fingeravtryck-fixen tvingar DB-replace) och att söket fungerar.

- [ ] **Step 1: Installera ovanpå befintlig appdata (uppgraderings-simulering)**

```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android.debug/se.birdy.android.MainActivity
```
Verifiera: ingen krasch vid start (DB-replace skedde). Om krasch → fingeravtryck-fixen tog inte; kontrollera att SCHEMA_REV bumpades + att `species.db` byggdes om (nytt application_id).

- [ ] **Step 2: Verifiera sök i Uppslagsverk/Archive**

Sök (via app-UI:t i sök-fältet): "Eleonora's Falcon" (rak apostrof) → träff. "ruppell" → Rüppell-arter. Engelskt namn i SV-läge → träff. "falcon" → falk-familjen. Screenshots:
```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" exec-out screencap -p > docs/superpowers/screenshots/v1.x-dp-a-search/01-eleonora-plain-apostrophe.png
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" exec-out screencap -p > docs/superpowers/screenshots/v1.x-dp-a-search/02-family-search.png
```

- [ ] **Step 3: Commit screenshots**

```bash
git add docs/superpowers/screenshots/v1.x-dp-a-search/
git commit -m "docs(screenshots): DP A sök-fix device-verify på SM-S918B"
```

---

## Self-Review (gjord av plan-författaren)

**Spec-täckning (§4 DP A):**
- Beslut 1 (expect/actual Normalizer) → Task 1. ✓
- Beslut 2 (search_text-kolumn) → Task 2. ✓
- Beslut 3 (cross-locale, droppa locale-filter, dedup) → Task 3 (query + `distinctBy` + user-locale display name). ✓
- Beslut 4 (familj/genus via search_text-blob + prefix-boost) → Task 2 (blob) + Task 3 (ORDER BY). ✓ (genus täcks även av scientific_name i blobben.)
- Beslut 5 (fingeravtryck) → Task 2 (SCHEMA_REV i contentHash flippar application_id) + Task 4 (regressionstest). Provider `needsCopy` jämför redan application_id → ingen provider-ändring. (REVIDERAT: Schema.version är hårdkodad 1 i detta projekt, så user_version-strategin från spec §4.5 byttes mot SCHEMA_REV.) ✓
- Tester (apostrof/cross-locale/diakrit/familj/prefix) → Task 1 + Task 3. Build-normalisering → Task 2/5. Fingeravtryck → Task 4 + device-verify Task 6. ✓
- Edge cases: tom query → `normalizeSearch("")=""` → `LIKE %%` matchar alla (Task 3). scientific_name-sök → ingår i search_text-blobben. ✓

**Placeholder-scan:** Inga "TBD". Två medvetna verifieringspunkter mot exakt kod (in-memory-DB-helperns namn i SpeciesRepositoryTest Task 3; befintlig YAML-fixtur i SpeciesDbBuilderTest Task 4) — faktisk kod ges i varje fall; noteringen är var man speglar befintliga fixtur-namn.

**Typ-konsistens:** `normalizeSearch(String): String` (Task 1) anropas identiskt i builder (Task 2) + repo (Task 3). `searchByNameOrScientific(query, max)` (Task 3, locale borttagen) matchar repo-anropet. `search_text`-kolumn (Task 2) matchas i query (Task 3). `SCHEMA_REV` (Task 2) + `contentFingerprint(items, schemaRev)` (Task 4) konsekvent. Konsekvent.
