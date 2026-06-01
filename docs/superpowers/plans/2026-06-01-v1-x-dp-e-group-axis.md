# DP E — Ekologisk `group`-axel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ersätt DP C:s provisoriska UI-chip-mappning med en kurerad ekologisk `group`-axel som en riktig DB-kolumn, genererad ur en enda `family_groups.yaml` vid DB-bygget, och propagera `family_sv` till Arkivets familje-sortrubriker.

**Architecture:** En kurerad `family → group`-map bor i `shared/content/src/jvmMain/resources/family_groups.yaml` (15 grupper, 14 namngivna + `other`). `FamilyGroups` (jvmMain) läser den; `SpeciesDbBuilder` stämplar in `group` i en ny `SpeciesTaxonomy.group_id`-kolumn vid DB-bygget (`SCHEMA_REV 2→3` → fingerprint-flip → DB-replace på uppgradering). `SqlDelightSpeciesRepository` propagerar `group` + `family_sv` till `SpeciesSummary`. `ArchiveChip` blir en tunn enum keyad på grupp-id; Arkivet filtrerar på `SpeciesSummary.group`. DP C:s `familySets`/`categoryOf`/`ArchiveChipMappingTest` rivs.

**Tech Stack:** Kotlin Multiplatform, SQLDelight 2.x, kaml (`com.charleskorn.kaml`, redan dep), Compose Multiplatform, compose-resources, JUnit5 (jvmTest) + kotlin.test (commonTest).

**Spec:** `docs/superpowers/specs/2026-06-01-v1-x-dp-e-group-axis-design.md`

---

## Kör-miljö (gäller alla gradle-kommandon)

Bash-kommandon mot `./gradlew` kräver Java-prefixet (annars hittar Gradle inte JDK 21):

```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
```

Snabba testkommandon:
- Content-modul (JUnit5): `./gradlew :shared:content:jvmTest`
- Filtrera: `./gradlew :shared:content:jvmTest --tests "se.birdy.content.build.FamilyGroupsTest"`
- App (commonMain/Android unit): `./gradlew :composeApp:testDebugUnitTest`
- Lint: `./gradlew ktlintCheck detekt` (autofix: `./gradlew ktlintFormat`)

---

## Task 1: `family_groups.yaml` + `FamilyGroups`-parser (ren logik, ingen DB)

**Files:**
- Create: `shared/content/src/jvmMain/resources/family_groups.yaml`
- Create: `shared/content/src/jvmMain/kotlin/se/birdy/content/build/FamilyGroups.kt`
- Test: `shared/content/src/jvmTest/kotlin/se/birdy/content/build/FamilyGroupsTest.kt`

- [ ] **Step 1: Skapa den kurerade mappnings-resursen**

Create `shared/content/src/jvmMain/resources/family_groups.yaml`:

```yaml
# DP E — ekologisk grupp-axel. ENDA sanningskällan för family → group.
# songbirds keyas på ioc_order==Passeriformes (täcker alla passerinfamiljer
# robust). Övriga 14 grupper keyas på latinsk familj (matchar taxonomy.family).
# Okänd familj faller tillbaka till "other" (builder loggar en varning).
order:
  - songbirds
  - waterfowl
  - waders
  - gulls_terns
  - auks
  - seabirds
  - grebes_divers
  - herons_storks
  - raptors
  - owls
  - gamebirds
  - doves
  - woodpeckers
  - cranes_rails
  - other
groups:
  songbirds:
    keyed_by: order
    ioc_order: Passeriformes
  waterfowl:
    families: [Anatidae]
  waders:
    families: [Scolopacidae, Charadriidae, Glareolidae, Burhinidae, Recurvirostridae, Haematopodidae, Rostratulidae, Jacanidae, Dromadidae]
  gulls_terns:
    families: [Laridae, Stercorariidae]
  auks:
    families: [Alcidae]
  seabirds:
    families: [Procellariidae, Hydrobatidae, Oceanitidae, Sulidae, Phalacrocoracidae, Anhingidae, Fregatidae, Phaethontidae]
  grebes_divers:
    families: [Podicipedidae, Gaviidae]
  herons_storks:
    families: [Ardeidae, Ciconiidae, Threskiornithidae, Pelecanidae, Phoenicopteridae, Scopidae]
  raptors:
    families: [Accipitridae, Falconidae, Pandionidae]
  owls:
    families: [Strigidae, Tytonidae]
  gamebirds:
    families: [Phasianidae, Odontophoridae, Numididae]
  doves:
    families: [Columbidae]
  woodpeckers:
    families: [Picidae]
  cranes_rails:
    families: [Rallidae, Gruidae]
  other:
    families: [Cuculidae, Apodidae, Caprimulgidae, Pteroclidae, Alcedinidae, Psittacidae, Otididae, Meropidae, Coraciidae, Psittaculidae, Turnicidae, Upupidae, Struthionidae, Bucerotidae]
```

- [ ] **Step 2: Skriv det fallerande testet**

Create `shared/content/src/jvmTest/kotlin/se/birdy/content/build/FamilyGroupsTest.kt`:

```kotlin
package se.birdy.content.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class FamilyGroupsTest {
    private val groups = FamilyGroups.loadDefault()

    // Real content (839 arter). Arbetskatalog för jvmTest = modulroten shared/content.
    private val content = SpeciesYamlParser().parseAll(Path.of("species"))

    @Test
    fun `order list matches the locked 15 group ids`() {
        val expected =
            listOf(
                "songbirds", "waterfowl", "waders", "gulls_terns", "auks", "seabirds",
                "grebes_divers", "herons_storks", "raptors", "owls", "gamebirds",
                "doves", "woodpeckers", "cranes_rails", "other",
            )
        assertEquals(expected, groups.groupIds)
    }

    @Test
    fun `routes representative families`() {
        assertEquals("auks", groups.groupFor("Alcidae", "Charadriiformes"))
        assertEquals("gulls_terns", groups.groupFor("Laridae", "Charadriiformes"))
        assertEquals("gulls_terns", groups.groupFor("Stercorariidae", "Charadriiformes"))
        assertEquals("waders", groups.groupFor("Scolopacidae", "Charadriiformes"))
        assertEquals("raptors", groups.groupFor("Falconidae", "Falconiformes"))
        assertEquals("woodpeckers", groups.groupFor("Picidae", "Piciformes"))
        assertEquals("doves", groups.groupFor("Columbidae", "Columbiformes"))
        assertEquals("songbirds", groups.groupFor("Paridae", "Passeriformes"))
        assertEquals("other", groups.groupFor("Cuculidae", "Cuculiformes"))
        assertEquals("other", groups.groupFor("Nonexistentidae", "Madeupiformes"))
    }

    @Test
    fun `every content family is explicitly mapped`() {
        val unmapped =
            content
                .map { (_, y) -> y.taxonomy.family to y.taxonomy.ioc_order }
                .distinct()
                .filterNot { (fam, order) -> groups.isExplicitlyMapped(fam, order) }
                .map { it.first }
                .toSortedSet()
        assertTrue(unmapped.isEmpty(), "Omappade familjer (lägg till i family_groups.yaml): $unmapped")
    }

    @Test
    fun `species counts per group match the locked taxonomy`() {
        val counts =
            content
                .groupingBy { (_, y) -> groups.groupFor(y.taxonomy.family, y.taxonomy.ioc_order) }
                .eachCount()
        assertEquals(378, counts["songbirds"])
        assertEquals(53, counts["waterfowl"])
        assertEquals(66, counts["waders"])
        assertEquals(44, counts["gulls_terns"])
        assertEquals(7, counts["auks"])
        assertEquals(37, counts["seabirds"])
        assertEquals(9, counts["grebes_divers"])
        assertEquals(31, counts["herons_storks"])
        assertEquals(51, counts["raptors"])
        assertEquals(23, counts["owls"])
        assertEquals(31, counts["gamebirds"])
        assertEquals(17, counts["doves"])
        assertEquals(17, counts["woodpeckers"])
        assertEquals(13, counts["cranes_rails"])
        assertEquals(62, counts["other"])
        assertEquals(839, counts.values.sum())
    }
}
```

- [ ] **Step 3: Kör testet — verifiera att det fallerar**

Run: `./gradlew :shared:content:jvmTest --tests "se.birdy.content.build.FamilyGroupsTest"`
Expected: FAIL med "Unresolved reference: FamilyGroups" (klassen finns inte än).

- [ ] **Step 4: Skriv `FamilyGroups`**

Create `shared/content/src/jvmMain/kotlin/se/birdy/content/build/FamilyGroups.kt`:

```kotlin
package se.birdy.content.build

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.Serializable

@Serializable
private data class FamilyGroupsYaml(
    val order: List<String>,
    val groups: Map<String, GroupDefYaml>,
)

@Serializable
private data class GroupDefYaml(
    val keyed_by: String? = null,
    val ioc_order: String? = null,
    val families: List<String> = emptyList(),
)

/**
 * DP E — kurerad ekologisk grupp-axel. Enda sanningskällan: family_groups.yaml
 * (jvmMain-resurs). `songbirds` keyas på ioc_order==Passeriformes; varje annan
 * grupp keyas på latinsk familj. Okänd familj → "other".
 */
class FamilyGroups internal constructor(
    val groupIds: List<String>,
    private val familyToGroup: Map<String, String>,
    private val orderKeyedGroupId: String?,
    private val orderKeyedIocOrder: String?,
) {
    /** Returnerar grupp-id för en art. Kastar aldrig; okänd familj → "other". */
    fun groupFor(
        family: String,
        iocOrder: String,
    ): String {
        if (orderKeyedGroupId != null && iocOrder == orderKeyedIocOrder) return orderKeyedGroupId
        return familyToGroup[family] ?: OTHER
    }

    /** True om arten matchas av en uttrycklig regel (order-key eller family-set), inte fallback. */
    fun isExplicitlyMapped(
        family: String,
        iocOrder: String,
    ): Boolean {
        if (orderKeyedGroupId != null && iocOrder == orderKeyedIocOrder) return true
        return familyToGroup.containsKey(family)
    }

    companion object {
        const val OTHER = "other"
        private const val RESOURCE = "/family_groups.yaml"
        private val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))

        fun loadDefault(): FamilyGroups {
            val text =
                FamilyGroups::class.java
                    .getResourceAsStream(RESOURCE)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: error("Missing classpath resource $RESOURCE")
            return parse(text)
        }

        fun parse(yamlText: String): FamilyGroups {
            val model = yaml.decodeFromString(FamilyGroupsYaml.serializer(), yamlText)
            val familyToGroup = LinkedHashMap<String, String>()
            var orderKeyedId: String? = null
            var orderKeyedIoc: String? = null
            for ((groupId, def) in model.groups) {
                if (def.keyed_by == "order") {
                    requireNotNull(def.ioc_order) { "Group '$groupId' keyed_by order saknar ioc_order" }
                    orderKeyedId = groupId
                    orderKeyedIoc = def.ioc_order
                    continue
                }
                for (family in def.families) {
                    val prev = familyToGroup.put(family, groupId)
                    require(prev == null) { "Familjen '$family' mappad till både '$prev' och '$groupId'" }
                }
            }
            require(model.order.toSet() == model.groups.keys) {
                "order-listan måste matcha grupp-id:na: ${model.order} vs ${model.groups.keys}"
            }
            return FamilyGroups(model.order, familyToGroup, orderKeyedId, orderKeyedIoc)
        }
    }
}
```

- [ ] **Step 5: Kör testet — verifiera att det passerar**

Run: `./gradlew :shared:content:jvmTest --tests "se.birdy.content.build.FamilyGroupsTest"`
Expected: PASS (4 tester gröna). Om `species counts`-testet fallerar: artkatalogens antal har drivit sedan snapshotten — uppdatera siffrorna i testet OCH spec §4 i samma commit (medvetet ankare).

- [ ] **Step 6: Commit**

```bash
git add shared/content/src/jvmMain/resources/family_groups.yaml \
        shared/content/src/jvmMain/kotlin/se/birdy/content/build/FamilyGroups.kt \
        shared/content/src/jvmTest/kotlin/se/birdy/content/build/FamilyGroupsTest.kt
git commit -m "feat(content): DP E — family_groups.yaml + FamilyGroups-parser med 15-grupps-axel"
```

---

## Task 2: `group_id`-kolumn + builder-wiring + `SCHEMA_REV 2→3`

**Files:**
- Modify: `shared/content/src/commonMain/sqldelight/se/birdy/content/SpeciesTaxonomy.sq`
- Modify: `shared/content/src/jvmMain/kotlin/se/birdy/content/build/SpeciesDbBuilder.kt`
- Test: `shared/content/src/jvmTest/kotlin/se/birdy/content/build/SpeciesDbBuilderTest.kt`

> **OBS:** `group` är ett reserverat SQL-nyckelord → kolumnen heter **`group_id`**. SQLDelight genererar property-namnet `group_id` (snake_case bevaras, som `family_sv`).

- [ ] **Step 1: Lägg kolumn + index + uppdaterad insert i schemat**

Modify `shared/content/src/commonMain/sqldelight/se/birdy/content/SpeciesTaxonomy.sq` — hela filen blir:

```sql
CREATE TABLE SpeciesTaxonomy (
    species_id TEXT NOT NULL PRIMARY KEY,
    family TEXT NOT NULL,
    family_sv TEXT,
    genus TEXT NOT NULL,
    ioc_order TEXT NOT NULL,
    group_id TEXT NOT NULL,
    FOREIGN KEY (species_id) REFERENCES Species(id) ON DELETE CASCADE
);

CREATE INDEX species_taxonomy_family ON SpeciesTaxonomy(family);

CREATE INDEX species_taxonomy_group ON SpeciesTaxonomy(group_id);

selectBySpecies:
SELECT * FROM SpeciesTaxonomy WHERE species_id = ?;

selectByFamily:
SELECT species_id FROM SpeciesTaxonomy WHERE family = ?;

selectAll:
SELECT * FROM SpeciesTaxonomy;

insert:
INSERT INTO SpeciesTaxonomy(species_id, family, family_sv, genus, ioc_order, group_id) VALUES (?, ?, ?, ?, ?, ?);
```

- [ ] **Step 2: Skriv det fallerande builder-testet**

Add to `shared/content/src/jvmTest/kotlin/se/birdy/content/build/SpeciesDbBuilderTest.kt` (ny `@Test` i klassen):

```kotlin
    @Test
    fun `stamps ecological group_id on taxonomy`(
        @TempDir tempDir: Path,
    ) {
        val items =
            parser.parseAll(Path.of("src/jvmTest/resources/fixtures/species"))
        val outDb = tempDir.resolve("species.db")
        SpeciesDbBuilder().build(
            items = items,
            sourceImageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
            targetDb = outDb,
            targetImageRoot = tempDir.resolve("images"),
        )

        val driver = JdbcSqliteDriver("jdbc:sqlite:${outDb.toAbsolutePath()}")
        val db = BirdyContent(driver)
        // Fixturen Q25485 = Paridae / Passeriformes → songbirds.
        val taxonomy = db.speciesTaxonomyQueries.selectBySpecies("Q25485").executeAsOne()
        assertEquals("songbirds", taxonomy.group_id)
        driver.close()
    }
```

- [ ] **Step 3: Kör testet — verifiera att det fallerar**

Run: `./gradlew :shared:content:jvmTest --tests "se.birdy.content.build.SpeciesDbBuilderTest"`
Expected: FAIL — kompileringsfel ("no parameter group_id" på `insert(...)` i builder) ELLER `selectBySpecies` saknar `group_id`-property tills schemat regenererats. (SQLDelight regenererar vid kompilering av modulen.)

- [ ] **Step 4: Wire:a in FamilyGroups + bumpa SCHEMA_REV i builder**

Modify `shared/content/src/jvmMain/kotlin/se/birdy/content/build/SpeciesDbBuilder.kt`:

(a) Bumpa konstanten (rad 12):

```kotlin
// Bumpa vid VARJE schema-ändring i Species*.sq → flippar application_id → tvingar DB-replace på uppgradering.
private const val SCHEMA_REV = 3
```

(b) Ge klassen en `FamilyGroups`-beroende med default (rad 14):

```kotlin
class SpeciesDbBuilder(
    private val familyGroups: FamilyGroups = FamilyGroups.loadDefault(),
) {
```

(c) I `insertSpecies`, beräkna group + skicka till insert. Ersätt det befintliga `db.speciesTaxonomyQueries.insert(...)`-blocket (rad 67-73) med:

```kotlin
        val group = familyGroups.groupFor(yaml.taxonomy.family, yaml.taxonomy.ioc_order)
        if (!familyGroups.isExplicitlyMapped(yaml.taxonomy.family, yaml.taxonomy.ioc_order)) {
            System.err.println(
                "WARN: familjen '${yaml.taxonomy.family}' saknas i family_groups.yaml → '$group' (lägg till den)",
            )
        }
        db.speciesTaxonomyQueries.insert(
            species_id = yaml.id,
            family = yaml.taxonomy.family,
            family_sv = yaml.taxonomy.family_sv,
            genus = yaml.taxonomy.genus,
            ioc_order = yaml.taxonomy.ioc_order,
            group_id = group,
        )
```

- [ ] **Step 5: Kör content-modulens tester — verifiera grönt**

Run: `./gradlew :shared:content:jvmTest`
Expected: PASS — alla content-tester gröna (det nya `group_id`-testet + befintliga builder/fingerprint/repo-tester). Det befintliga `schema rev change flips fingerprint`-testet (1 vs 2) påverkas inte av konstant-bumpen.

- [ ] **Step 6: Commit**

```bash
git add shared/content/src/commonMain/sqldelight/se/birdy/content/SpeciesTaxonomy.sq \
        shared/content/src/jvmMain/kotlin/se/birdy/content/build/SpeciesDbBuilder.kt \
        shared/content/src/jvmTest/kotlin/se/birdy/content/build/SpeciesDbBuilderTest.kt
git commit -m "feat(content): DP E — SpeciesTaxonomy.group_id-kolumn + builder-wiring + SCHEMA_REV 3"
```

---

## Task 3: Domänmodell-fält + repo-propagering

**Files:**
- Modify: `shared/content/src/commonMain/kotlin/se/birdy/content/Species.kt`
- Modify: `shared/content/src/commonMain/kotlin/se/birdy/content/SqlDelightSpeciesRepository.kt`
- Test: `shared/content/src/jvmTest/kotlin/se/birdy/content/SpeciesRepositoryTest.kt`

- [ ] **Step 1: Skriv det fallerande repo-testet**

Add to `shared/content/src/jvmTest/kotlin/se/birdy/content/SpeciesRepositoryTest.kt` (ny `@Test`):

```kotlin
    @Test
    fun `search carries ecological group and family_sv`(
        @TempDir tempDir: Path,
    ) = runTest {
        val driver = newDriverWithFixtures(tempDir)
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))

        val results = repo.search(query = "Talg", locale = Locale.SV, filters = SpeciesFilter()).first()
        val talgoxe = results.first { it.id == SpeciesId("Q25485") }
        assertEquals("songbirds", talgoxe.group)
        assertEquals("Mesar", talgoxe.familySv) // fixturens family_sv för Paridae

        driver.close()
    }
```

- [ ] **Step 2: Kör testet — verifiera att det fallerar**

Run: `./gradlew :shared:content:jvmTest --tests "se.birdy.content.SpeciesRepositoryTest"`
Expected: FAIL med "Unresolved reference: group" / "familySv" på `SpeciesSummary` (fälten finns inte än).

- [ ] **Step 3: Lägg fälten på domänmodellerna**

Modify `shared/content/src/commonMain/kotlin/se/birdy/content/Species.kt` — uppdatera de två data-klasserna:

```kotlin
data class SpeciesTaxonomy(
    val family: String,
    val familySv: String?,
    val genus: String,
    val iocOrder: String,
    val group: String = "",
)
```

```kotlin
data class SpeciesSummary(
    val id: SpeciesId,
    val name: String,
    val scientificName: String,
    val abundance: Abundance,
    val heroImagePath: String?,
    val iocOrder: String = "",
    val family: String = "",
    val familySv: String = "",
    val group: String = "",
)
```

- [ ] **Step 4: Propagera i repository**

Modify `shared/content/src/commonMain/kotlin/se/birdy/content/SqlDelightSpeciesRepository.kt` på fyra ställen:

(a) `getById` — domän-`SpeciesTaxonomy` (rad ~63-68): lägg `group = taxonomy.group_id`:

```kotlin
                    taxonomy =
                        SpeciesTaxonomy(
                            family = taxonomy.family,
                            familySv = taxonomy.family_sv,
                            genus = taxonomy.genus,
                            iocOrder = taxonomy.ioc_order,
                            group = taxonomy.group_id,
                        ),
```

(b) `search` — `SpeciesSummary` (rad ~145-158): lägg `familySv` + `group`:

```kotlin
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
                            familySv = taxonomy?.family_sv ?: "",
                            group = taxonomy?.group_id ?: "",
                        )
```

(c) `allByQid` — domän-`SpeciesTaxonomy` (rad ~244-249): lägg `group = taxonomy.group_id`:

```kotlin
                            taxonomy =
                                SpeciesTaxonomy(
                                    family = taxonomy.family,
                                    familySv = taxonomy.family_sv,
                                    genus = taxonomy.genus,
                                    iocOrder = taxonomy.ioc_order,
                                    group = taxonomy.group_id,
                                ),
```

(d) `summaryFor` — `SpeciesSummary` (rad ~291-299): lägg `familySv` + `group`:

```kotlin
        return SpeciesSummary(
            id = SpeciesId(sp.id),
            name = name,
            scientificName = sp.scientific_name,
            abundance = Abundance.fromCode(sp.abundance) ?: Abundance.OVANLIG,
            heroImagePath = hero,
            iocOrder = taxonomy?.ioc_order ?: "",
            family = taxonomy?.family ?: "",
            familySv = taxonomy?.family_sv ?: "",
            group = taxonomy?.group_id ?: "",
        )
```

- [ ] **Step 5: Kör content-modulens tester — verifiera grönt**

Run: `./gradlew :shared:content:jvmTest`
Expected: PASS — repo-propageringstestet + alla övriga gröna.

- [ ] **Step 6: Commit**

```bash
git add shared/content/src/commonMain/kotlin/se/birdy/content/Species.kt \
        shared/content/src/commonMain/kotlin/se/birdy/content/SqlDelightSpeciesRepository.kt \
        shared/content/src/jvmTest/kotlin/se/birdy/content/SpeciesRepositoryTest.kt
git commit -m "feat(content): DP E — propagera group + family_sv till SpeciesSummary/SpeciesTaxonomy"
```

---

## Task 4: `ArchiveChip`-omskrivning + ViewModel-filter (riv DP C-mappning)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveChip.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveViewModel.kt`
- Create: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/encyclopedia/ArchiveChipTest.kt`
- Delete: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/encyclopedia/ArchiveChipMappingTest.kt`

- [ ] **Step 1: Skriv det nya (fallerande) chip-testet**

Create `composeApp/src/commonTest/kotlin/se/birdy/app/ui/encyclopedia/ArchiveChipTest.kt`:

```kotlin
package se.birdy.app.ui.encyclopedia

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchiveChipTest {
    @Test
    fun `ALL matches any group and has empty key`() {
        assertEquals("", ArchiveChip.ALL.key)
        assertTrue(ArchiveChip.ALL.matches("auks"))
        assertTrue(ArchiveChip.ALL.matches(""))
    }

    @Test
    fun `non-ALL chips match only their own group key`() {
        assertTrue(ArchiveChip.AUKS.matches("auks"))
        assertFalse(ArchiveChip.AUKS.matches("songbirds"))
        assertTrue(ArchiveChip.WADERS.matches("waders"))
        assertFalse(ArchiveChip.WADERS.matches("auks"))
        assertTrue(ArchiveChip.WOODPECKERS.matches("woodpeckers"))
    }

    @Test
    fun `chip keys match the locked content group ids and are unique`() {
        val expected =
            setOf(
                "songbirds", "waterfowl", "waders", "gulls_terns", "auks", "seabirds",
                "grebes_divers", "herons_storks", "raptors", "owls", "gamebirds",
                "doves", "woodpeckers", "cranes_rails", "other",
            )
        val keys = ArchiveChip.entries.filter { it != ArchiveChip.ALL }.map { it.key }
        assertEquals(expected.size, keys.size, "dubbletter eller saknade chip-nycklar")
        assertEquals(expected, keys.toSet())
    }
}
```

- [ ] **Step 2: Ta bort DP C:s gamla mappnings-test**

Detta test refererar `ArchiveChip.familySets`/`categoryOf` som rivs i Step 4 → måste bort, annars kompileringsfel.

```bash
git rm composeApp/src/commonTest/kotlin/se/birdy/app/ui/encyclopedia/ArchiveChipMappingTest.kt
```

- [ ] **Step 3: Kör testet — verifiera att det fallerar**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.encyclopedia.ArchiveChipTest"`
Expected: FAIL — kompileringsfel ("Unresolved reference: key" / nya enum-värden saknas).

- [ ] **Step 4: Skriv om `ArchiveChip`**

Replace hela `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveChip.kt` med:

```kotlin
package se.birdy.app.ui.encyclopedia

/**
 * DP E — Arkivets ekologiska grupp-chips. `key` == content-grupp-id
 * (SpeciesSummary.group, materialiserad ur family_groups.yaml). ALL = inget filter.
 */
enum class ArchiveChip(
    val key: String,
) {
    ALL(""),
    SONGBIRDS("songbirds"),
    WATERFOWL("waterfowl"),
    WADERS("waders"),
    GULLS_TERNS("gulls_terns"),
    AUKS("auks"),
    SEABIRDS("seabirds"),
    GREBES_DIVERS("grebes_divers"),
    HERONS_STORKS("herons_storks"),
    RAPTORS("raptors"),
    OWLS("owls"),
    GAMEBIRDS("gamebirds"),
    DOVES("doves"),
    WOODPECKERS("woodpeckers"),
    CRANES_RAILS("cranes_rails"),
    OTHER("other"),
    ;

    /** Tom ALL = inget filter; annars matchar arten sin ekologiska grupp. */
    fun matches(group: String): Boolean = this == ALL || group == key
}
```

- [ ] **Step 5: Uppdatera ViewModel-filtret**

Modify `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveViewModel.kt` — i `toUiState` (rad ~120), byt filterraden:

```kotlin
        val filtered = list.filter { c.matches(it.group) }
```

- [ ] **Step 6: Kör app-testerna — verifiera grönt**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: PASS — `ArchiveChipTest` + befintliga `ArchiveViewModelTest` gröna. (`ArchiveViewModelTest` använder `ArchiveChip.OWLS` som finns kvar.)

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveChip.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveViewModel.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/ui/encyclopedia/ArchiveChipTest.kt
git rm --cached composeApp/src/commonTest/kotlin/se/birdy/app/ui/encyclopedia/ArchiveChipMappingTest.kt 2>/dev/null; true
git commit -m "feat(archive): DP E — ArchiveChip keyad på content-group + ViewModel-filter; riv DP C-mappning"
```

---

## Task 5: `ArchiveScreen` ChipBar + svenska familjerubriker + strängar

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`

> Ingen TDD-loop här (Compose-UI + statiska strängar; verifieras på enhet i Task 6). Kör bygge + lint efter ändringarna.

- [ ] **Step 1: Uppdatera SV-strängarna**

Modify `composeApp/src/commonMain/composeResources/values/strings.xml` — ersätt hela det befintliga `archive_chip_*`-blocket (rad 409-420) med (notera: `&` → `&amp;`, raw `ä/å/ö`):

```xml
    <string name="archive_chip_all">Alla</string>
    <string name="archive_chip_songbirds">Tättingar</string>
    <string name="archive_chip_waterfowl">Änder &amp; gäss</string>
    <string name="archive_chip_waders">Vadare</string>
    <string name="archive_chip_gulls_terns">Måsar &amp; tärnor</string>
    <string name="archive_chip_auks">Alkor</string>
    <string name="archive_chip_seabirds">Havsfåglar</string>
    <string name="archive_chip_grebes_divers">Doppingar &amp; lommar</string>
    <string name="archive_chip_herons_storks">Hägrar &amp; storkar</string>
    <string name="archive_chip_raptors">Rovfåglar</string>
    <string name="archive_chip_owls">Ugglor</string>
    <string name="archive_chip_gamebirds">Hönsfåglar</string>
    <string name="archive_chip_doves">Duvor</string>
    <string name="archive_chip_woodpeckers">Hackspettar</string>
    <string name="archive_chip_cranes_rails">Tranor &amp; rallar</string>
    <string name="archive_chip_other">Övriga</string>
```

(Ändringar: `archive_chip_gulls`→`archive_chip_gulls_terns` (drop "alkor"), `archive_chip_herons`→`archive_chip_herons_storks`; nya `auks`/`doves`/`woodpeckers`/`cranes_rails`; `other`-värde "Övrigt"→"Övriga".)

- [ ] **Step 2: Uppdatera EN-strängarna**

Modify `composeApp/src/commonMain/composeResources/values-en/strings.xml` — ersätt det befintliga `archive_chip_*`-blocket (rad 395-406) med:

```xml
    <string name="archive_chip_all">All</string>
    <string name="archive_chip_songbirds">Songbirds</string>
    <string name="archive_chip_waterfowl">Ducks &amp; geese</string>
    <string name="archive_chip_waders">Waders</string>
    <string name="archive_chip_gulls_terns">Gulls &amp; terns</string>
    <string name="archive_chip_auks">Auks</string>
    <string name="archive_chip_seabirds">Seabirds</string>
    <string name="archive_chip_grebes_divers">Grebes &amp; divers</string>
    <string name="archive_chip_herons_storks">Herons &amp; storks</string>
    <string name="archive_chip_raptors">Birds of prey</string>
    <string name="archive_chip_owls">Owls</string>
    <string name="archive_chip_gamebirds">Gamebirds</string>
    <string name="archive_chip_doves">Doves &amp; pigeons</string>
    <string name="archive_chip_woodpeckers">Woodpeckers</string>
    <string name="archive_chip_cranes_rails">Cranes &amp; rails</string>
    <string name="archive_chip_other">Other</string>
```

- [ ] **Step 3: Uppdatera ChipBar-imports i ArchiveScreen**

Modify `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveScreen.kt` — i import-blocket:
- **Ta bort:** `import birdy_bird_scanner.composeapp.generated.resources.archive_chip_gulls` och `...archive_chip_herons`.
- **Lägg till:** 
```kotlin
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_auks
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_cranes_rails
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_doves
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_gulls_terns
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_herons_storks
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_woodpeckers
```
(`archive_chip_{all,songbirds,raptors,owls,waders,waterfowl,seabirds,grebes_divers,gamebirds,other}` är redan importerade — behåll.)

- [ ] **Step 4: Uppdatera ChipBar-labels-listan**

I `ChipBar` (rad ~459-473), ersätt `labels`-listan med de 16 chiparna i visningsordning:

```kotlin
    val labels =
        listOf(
            ArchiveChip.ALL to stringResource(Res.string.archive_chip_all),
            ArchiveChip.SONGBIRDS to stringResource(Res.string.archive_chip_songbirds),
            ArchiveChip.WATERFOWL to stringResource(Res.string.archive_chip_waterfowl),
            ArchiveChip.WADERS to stringResource(Res.string.archive_chip_waders),
            ArchiveChip.GULLS_TERNS to stringResource(Res.string.archive_chip_gulls_terns),
            ArchiveChip.AUKS to stringResource(Res.string.archive_chip_auks),
            ArchiveChip.SEABIRDS to stringResource(Res.string.archive_chip_seabirds),
            ArchiveChip.GREBES_DIVERS to stringResource(Res.string.archive_chip_grebes_divers),
            ArchiveChip.HERONS_STORKS to stringResource(Res.string.archive_chip_herons_storks),
            ArchiveChip.RAPTORS to stringResource(Res.string.archive_chip_raptors),
            ArchiveChip.OWLS to stringResource(Res.string.archive_chip_owls),
            ArchiveChip.GAMEBIRDS to stringResource(Res.string.archive_chip_gamebirds),
            ArchiveChip.DOVES to stringResource(Res.string.archive_chip_doves),
            ArchiveChip.WOODPECKERS to stringResource(Res.string.archive_chip_woodpeckers),
            ArchiveChip.CRANES_RAILS to stringResource(Res.string.archive_chip_cranes_rails),
            ArchiveChip.OTHER to stringResource(Res.string.archive_chip_other),
        )
```

- [ ] **Step 5: Svenska familjerubriker (familySv)**

I `ArchiveScreen.kt`, FAMILY-sort-grenen (rad ~290-296), skicka med `familySv` till rubriken:

```kotlin
                    if (s.sort == ArchiveSort.FAMILY) {
                        val grouped = s.rows.groupBy { it.summary.family }
                        grouped.forEach { (family, rows) ->
                            stickyHeader(key = "family-$family") {
                                FamilyHeader(family = family, familySv = rows.first().summary.familySv)
                            }
                            items(rows, key = { it.summary.id.raw }) { row ->
                                SpeciesRow(
                                    summary = row.summary,
                                    isStamped = row.isStamped,
                                    stampNumber = row.stampNumber,
                                    onClick = { onSpeciesClick(row.summary.id) },
                                )
                            }
                        }
                    } else {
```

Och uppdatera `FamilyHeader`-composable (rad ~357-376):

```kotlin
@Composable
private fun FamilyHeader(
    family: String,
    familySv: String,
) {
    val serif = rememberDmSerifDisplay()
    val label = familySv.ifBlank { family } // svenska där det finns; latin annars (t.ex. EN-locale)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PaperBottom.copy(alpha = 0.85f))
                .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = MarginaliaInk,
            fontFamily = serif,
            fontStyle = FontStyle.Italic,
            fontSize = 12.sp,
            letterSpacing = 0.18.em,
        )
    }
}
```

- [ ] **Step 6: Bygg + lint — verifiera grönt**

Run: `./gradlew :composeApp:testDebugUnitTest ktlintCheck detekt`
Expected: PASS — kompilerar, alla unit-tester gröna, ktlint/detekt rena. (`ktlintFormat` vid behov.)

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveScreen.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(archive): DP E — 16 grupp-chips + svenska familjerubriker (familySv)"
```

---

## Task 6: Regenerera species.db + version-bump + device-verify

**Files:**
- Modify (genererad binär): `composeApp/src/commonMain/composeResources/files/species.db`
- Modify (genererade bilder): `asset-pack/src/main/assets/images/**` (oförändrade — bilderna rör inte taxonomin, men tasken rör katalogen)
- Modify: `androidApp/build.gradle.kts` (versionCode/versionName)
- Create: `docs/superpowers/screenshots/` (device-verify-skärmdumpar)

> **KRITISKT:** `species.db` är en incheckad binär som genereras av `buildSpeciesDb`. Utan regenerering har den bundlade DB:n ingen `group_id`-kolumn → appen kraschar på `SELECT * FROM SpeciesTaxonomy`. Detta steg är inte valfritt.

- [ ] **Step 1: Regenerera den bundlade species.db**

Run:
```bash
./gradlew :shared:content:buildSpeciesDb
```
Expected: "buildSpeciesDb: 839 species → ...species.db in NNNN ms". Verifierar samtidigt att `family_groups.yaml` täcker alla familjer (inga `WARN: familjen ... saknas` i utskriften — om någon dyker upp: lägg familjen i `family_groups.yaml` + uppdatera `FamilyGroupsTest`/spec §4 och kör om Task 1).

- [ ] **Step 2: Bekräfta att group_id finns i den genererade DB:n**

Run:
```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" version >/dev/null 2>&1 # (valfritt: bekräfta adb finns)
git status --short composeApp/src/commonMain/composeResources/files/species.db
```
Expected: `species.db` visas som modifierad (binär ändrad pga ny kolumn + flippad application_id).

- [ ] **Step 3: Bumpa version**

Modify `androidApp/build.gradle.kts` (rad 57-58):

```kotlin
        versionCode = 121
        versionName = "1.1.0-rc6"
```

- [ ] **Step 4: Installera på enhet**

Run:
```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android.debug/se.birdy.android.MainActivity
```
Expected: appen startar utan krasch (DB med `group_id` på plats). OBS: debug-paketet är `se.birdy.android.debug` (per [[project_onboarding_v2_status]]).

- [ ] **Step 5: Device-verify (per [[feedback_personal_device_verify]])**

> Be Albin om "händerna borta" först (SM-S918B = hans dagliga telefon). Snabba ADB-interaktioner, undvik scroll-loopar > ~10s, screencap-verifiera (`mCurrentFocus` ljuger under overlay), radera ev. fångat privat innehåll direkt. chip-`&`-etiketter står som `&amp;` i uiautomator-XML.

Verifiera i Arkivet (Uppslagsverk-fliken):
- Scrolla chip-raden → 16 chips i ordning (Alla · Tättingar · … · Övriga).
- Tappa **Vadare** → inga måsar/tärnor/alkor i listan.
- Tappa **Alkor** → egen chip; sillgrissla/tordmule synliga.
- Tappa **Hackspettar / Duvor / Tranor & rallar** → egna chips med innehåll.
- Tappa **Övriga** → gökar/seglare/papegojor m.fl. (inte hackspettar/duvor).
- Sortera på Familj → svenska familjerubriker (t.ex. "FALKFÅGLAR" ej "FALCONIDAE") i SV-locale.
- Ta screenshots → `docs/superpowers/screenshots/` (t.ex. `dp-e-chips.png`, `dp-e-auks.png`, `dp-e-family-headers-sv.png`).

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/composeResources/files/species.db \
        asset-pack/src/main/assets/images \
        androidApp/build.gradle.kts \
        docs/superpowers/screenshots
git commit -m "chore(release): DP E — regenerera species.db (group_id) + vC121/rc6 + device-verify screenshots"
```

> **Merge/integration:** efter device-verify grön, följ [[project_v1_1_release_train]] — DP E batchas in i v1.x-tåget. Vid AAB-upload gäller CLAUDE.md follow-up #8 (store-listning + release-notes; DP E:s ekologiska kategorier nämns redan där).

---

## Self-review (efter skrivning)

**Spec-täckning:**
- Spec §3 #1 (platt 15-grupp) → Task 1 yaml + FamilyGroupsTest ✔
- §3 #2 (bygg-tids-map) → Task 1 (yaml/parser) + Task 2 (builder-wiring) ✔
- §3 #3 (songbirds via ioc_order) → Task 1 `keyed_by: order` + `groupFor` ✔
- §3 #4 (other = explicit + bygg-varning) → Task 1 `other`-families + Task 2 `System.err` WARN ✔
- §3 #5 (SCHEMA_REV 2→3) → Task 2 Step 4a ✔
- §3 #6 (family_sv-rubriker, defaults) → Task 3 (defaults) + Task 5 Step 5 ✔
- §3 #7 (kantfall) → Task 1 yaml (Stercorariidae→gulls_terns; Pelecanidae/Phoenicopteridae→herons_storks) ✔
- §4 (taxonomi + antal) → Task 1 `species counts`-test (ankare) ✔
- §6 komponenter → Task 1-5 täcker alla 11 + `FamilyGroups.kt` ✔
- §7 fingerprint/migrering → Task 2 (SCHEMA_REV) + Task 6 (DB-regen); chip-pref via befintlig `runCatching` (oförändrad, ingen task behövs) ✔
- §8 tester → Task 1/2/3/4 + device-verify Task 6; `ArchiveChipMappingTest` borttaget (Task 4) ✔

**Placeholder-scan:** inga TBD/TODO; all kod komplett.

**Typ-konsistens:** `group_id` (DB-kolumn/SQLDelight-property) vs `group` (domänfält) konsekvent i Task 2/3; `ArchiveChip.key` + `matches(group)` konsekvent Task 4/5; `FamilyGroups.groupFor`/`isExplicitlyMapped`/`groupIds`/`loadDefault` konsekvent Task 1/2.
