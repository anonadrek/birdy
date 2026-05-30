# DP C — Kategori-hotfix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ersätt Arkivets 5 godtyckliga IOC-ordning-chips med ~10 ekologiska, family-mappade chips (+ Övrigt) så att alkor lämnar "Vadare", Charadriiformes-soptunnan delas, och de tidigare okategoriserade ~20 % får en hemvist.

**Architecture:** Ren UI-/mappnings-fix i `composeApp` — ingen schema-/content-ändring. En pure classifier `ArchiveChip.categoryOf(family, iocOrder)` (Passeriformes → SONGBIRDS via ordning; övriga via family-set; resten → OTHER) driver både `ArchiveChip.matches()` (ViewModel-filter) och en helt DB-fri regressionstest. `OTHER` är komplementet (självunderhållande).

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, `kotlin.test` (commonTest), compose-resources (strings.xml SV+EN).

**Spec:** `docs/superpowers/specs/2026-05-30-dp-c-category-hotfix-design.md`

---

## Bygg-prefix (alla `./gradlew`-kommandon via Bash)

```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
```

## File Structure

| Fil | Ansvar | Åtgärd |
|---|---|---|
| `composeApp/.../ui/encyclopedia/ArchiveChip.kt` | Enum + `familySets` + pure `categoryOf`/`matches` | Modify |
| `composeApp/.../ui/encyclopedia/ArchiveViewModel.kt` | Använd `matches()` i `toUiState`-filtret | Modify (rad ~120-126) |
| `composeApp/.../ui/encyclopedia/ArchiveScreen.kt` | `ChipBar` etiketter + string-imports | Modify (rad 56-61, 453-461) |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | SV chip-etiketter | Modify (rad 417-422) |
| `composeApp/src/commonMain/composeResources/values-en/strings.xml` | EN chip-etiketter | Modify (rad 404-409) |
| `composeApp/src/commonTest/.../ui/encyclopedia/ArchiveChipMappingTest.kt` | DB-fri regressionstest av mappningen | Create |

**Buildbarhet:** Task 1 är additiv (lägger nya enum-värden + ny API, behåller `WATER`/`orderSets`) → modulen kompilerar och testet kan köras. `WATER` + `orderSets` rivs i senare tasks när inget längre refererar dem.

---

## Task 1: Pure classifier `ArchiveChip` + regressionstest

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveChip.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/encyclopedia/ArchiveChipMappingTest.kt` (create)

- [ ] **Step 1: Write the failing test**

Create `composeApp/src/commonTest/kotlin/se/birdy/app/ui/encyclopedia/ArchiveChipMappingTest.kt`:

```kotlin
package se.birdy.app.ui.encyclopedia

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchiveChipMappingTest {
    @Test
    fun `family sets do not overlap and total 37`() {
        val sizeSum = ArchiveChip.familySets.values.sumOf { it.size }
        val distinct = ArchiveChip.familySets.values.flatten().toSet().size
        assertEquals(sizeSum, distinct, "A family appears in more than one chip")
        assertEquals(37, distinct, "Expected 37 explicitly mapped families")
    }

    @Test
    fun `mapped families are never passerine`() {
        val overlap = ArchiveChip.categorizedFamilies intersect PASSERINE_FAMILIES
        assertTrue(overlap.isEmpty(), "Passerine family in a familySet: $overlap")
    }

    @Test
    fun `every mapped and passerine family exists in content`() {
        val known = CONTENT_FAMILY_COUNTS.keys
        assertTrue((ArchiveChip.categorizedFamilies - known).isEmpty(), "Mapped family not in content (typo?)")
        assertTrue((PASSERINE_FAMILIES - known).isEmpty(), "Passerine family not in content (typo?)")
    }

    @Test
    fun `categoryOf routes representative families`() {
        assertEquals(ArchiveChip.GULLS, ArchiveChip.categoryOf("Alcidae", "Charadriiformes"))
        assertEquals(ArchiveChip.WADERS, ArchiveChip.categoryOf("Scolopacidae", "Charadriiformes"))
        assertEquals(ArchiveChip.GULLS, ArchiveChip.categoryOf("Laridae", "Charadriiformes"))
        assertEquals(ArchiveChip.GULLS, ArchiveChip.categoryOf("Stercorariidae", "Charadriiformes"))
        assertEquals(ArchiveChip.WATERFOWL, ArchiveChip.categoryOf("Anatidae", "Anseriformes"))
        assertEquals(ArchiveChip.RAPTORS, ArchiveChip.categoryOf("Accipitridae", "Accipitriformes"))
        assertEquals(ArchiveChip.OWLS, ArchiveChip.categoryOf("Strigidae", "Strigiformes"))
        assertEquals(ArchiveChip.GAMEBIRDS, ArchiveChip.categoryOf("Phasianidae", "Galliformes"))
        assertEquals(ArchiveChip.SEABIRDS, ArchiveChip.categoryOf("Procellariidae", "Procellariiformes"))
        assertEquals(ArchiveChip.HERONS, ArchiveChip.categoryOf("Ardeidae", "Pelecaniformes"))
        assertEquals(ArchiveChip.GREBES_DIVERS, ArchiveChip.categoryOf("Podicipedidae", "Podicipediformes"))
        assertEquals(ArchiveChip.SONGBIRDS, ArchiveChip.categoryOf("Fringillidae", "Passeriformes"))
        assertEquals(ArchiveChip.OTHER, ArchiveChip.categoryOf("Picidae", "Piciformes"))
        assertEquals(ArchiveChip.OTHER, ArchiveChip.categoryOf("Columbidae", "Columbiformes"))
    }

    @Test
    fun `species counts per chip match the content snapshot`() {
        val counts = mutableMapOf<ArchiveChip, Int>()
        for ((family, n) in CONTENT_FAMILY_COUNTS) {
            val order = if (family in PASSERINE_FAMILIES) "Passeriformes" else "_nonpasserine_"
            val chip = ArchiveChip.categoryOf(family, order)
            counts[chip] = (counts[chip] ?: 0) + n
        }
        assertEquals(378, counts[ArchiveChip.SONGBIRDS])
        assertEquals(53, counts[ArchiveChip.WATERFOWL])
        assertEquals(51, counts[ArchiveChip.RAPTORS])
        assertEquals(66, counts[ArchiveChip.WADERS])
        assertEquals(51, counts[ArchiveChip.GULLS])
        assertEquals(37, counts[ArchiveChip.SEABIRDS])
        assertEquals(31, counts[ArchiveChip.HERONS])
        assertEquals(9, counts[ArchiveChip.GREBES_DIVERS])
        assertEquals(31, counts[ArchiveChip.GAMEBIRDS])
        assertEquals(23, counts[ArchiveChip.OWLS])
        assertEquals(109, counts[ArchiveChip.OTHER])
        assertEquals(839, counts.values.sum())
        assertEquals(0, counts[ArchiveChip.ALL] ?: 0, "ALL must never be returned by categoryOf")
    }

    @Test
    fun `matches treats ALL as no-filter and others by category`() {
        assertTrue(ArchiveChip.ALL.matches("Picidae", "Piciformes"))
        assertTrue(ArchiveChip.GULLS.matches("Alcidae", "Charadriiformes"))
        assertFalse(ArchiveChip.WADERS.matches("Alcidae", "Charadriiformes"))
        assertTrue(ArchiveChip.OTHER.matches("Picidae", "Piciformes"))
    }

    private companion object {
        // Snapshot of shared/content/species, mätt 2026-05-30. Regressions-ankare + typo-vakt.
        val CONTENT_FAMILY_COUNTS: Map<String, Int> =
            mapOf(
                "Anatidae" to 53, "Muscicapidae" to 51, "Fringillidae" to 41, "Laridae" to 40,
                "Accipitridae" to 38, "Scolopacidae" to 34, "Phasianidae" to 28, "Alaudidae" to 27,
                "Sylviidae" to 23, "Strigidae" to 22, "Motacillidae" to 19, "Corvidae" to 19,
                "Acrocephalidae" to 19, "Procellariidae" to 18, "Phylloscopidae" to 18, "Picidae" to 17,
                "Passeridae" to 17, "Emberizidae" to 17, "Columbidae" to 17, "Charadriidae" to 17,
                "Ardeidae" to 16, "Sturnidae" to 13, "Falconidae" to 12, "Rallidae" to 10,
                "Paridae" to 10, "Estrildidae" to 10, "Turdidae" to 9, "Laniidae" to 9,
                "Cuculidae" to 9, "Apodidae" to 9, "Hirundinidae" to 8, "Caprimulgidae" to 8,
                "Pteroclidae" to 7, "Alcidae" to 7, "Sittidae" to 6, "Ploceidae" to 6,
                "Hydrobatidae" to 6, "Alcedinidae" to 6, "Threskiornithidae" to 5, "Pycnonotidae" to 5,
                "Psittacidae" to 5, "Prunellidae" to 5, "Podicipedidae" to 5, "Phalacrocoracidae" to 5,
                "Otididae" to 5, "Nectariniidae" to 5, "Meropidae" to 5, "Leiothrichidae" to 5,
                "Stercorariidae" to 4, "Locustellidae" to 4, "Glareolidae" to 4, "Gaviidae" to 4,
                "Cisticolidae" to 4, "Ciconiidae" to 4, "Burhinidae" to 4, "Sulidae" to 3,
                "Remizidae" to 3, "Regulidae" to 3, "Pelecanidae" to 3, "Gruidae" to 3,
                "Zosteropidae" to 2, "Recurvirostridae" to 2, "Psittaculidae" to 2, "Phoenicopteridae" to 2,
                "Paradoxornithidae" to 2, "Odontophoridae" to 2, "Oceanitidae" to 2, "Malaconotidae" to 2,
                "Haematopodidae" to 2, "Coraciidae" to 2, "Cettiidae" to 2, "Certhiidae" to 2,
                "Calcariidae" to 2, "Viduidae" to 1, "Upupidae" to 1, "Tytonidae" to 1,
                "Turnicidae" to 1, "Troglodytidae" to 1, "Tichodromidae" to 1, "Struthionidae" to 1,
                "Scopidae" to 1, "Rostratulidae" to 1, "Phaethontidae" to 1, "Panuridae" to 1,
                "Pandionidae" to 1, "Oriolidae" to 1, "Numididae" to 1, "Monarchidae" to 1,
                "Jacanidae" to 1, "Hypocoliidae" to 1, "Fregatidae" to 1, "Dromadidae" to 1,
                "Cinclidae" to 1, "Bucerotidae" to 1, "Bombycillidae" to 1, "Anhingidae" to 1,
                "Aegithalidae" to 1,
            )

        val PASSERINE_FAMILIES: Set<String> =
            setOf(
                "Muscicapidae", "Fringillidae", "Alaudidae", "Sylviidae", "Motacillidae", "Corvidae",
                "Acrocephalidae", "Phylloscopidae", "Passeridae", "Emberizidae", "Sturnidae", "Paridae",
                "Estrildidae", "Turdidae", "Laniidae", "Hirundinidae", "Sittidae", "Ploceidae",
                "Pycnonotidae", "Prunellidae", "Nectariniidae", "Leiothrichidae", "Locustellidae",
                "Cisticolidae", "Remizidae", "Regulidae", "Zosteropidae", "Paradoxornithidae",
                "Malaconotidae", "Cettiidae", "Certhiidae", "Calcariidae", "Viduidae", "Troglodytidae",
                "Tichodromidae", "Panuridae", "Oriolidae", "Monarchidae", "Hypocoliidae", "Cinclidae",
                "Bombycillidae", "Aegithalidae",
            )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.encyclopedia.ArchiveChipMappingTest"`
Expected: FAIL — kompileringsfel ("unresolved reference: familySets / categorizedFamilies / categoryOf / GULLS / WATERFOWL …"), eftersom den nya API:n inte finns än.

- [ ] **Step 3: Write the implementation (additiv — behåll WATER + orderSets)**

Replace the entire body of `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveChip.kt`:

```kotlin
package se.birdy.app.ui.encyclopedia

enum class ArchiveChip {
    ALL,
    SONGBIRDS,
    WATER, // legacy — tas bort i Task 4 när ChipBar inte längre refererar den
    RAPTORS,
    OWLS,
    WADERS,
    WATERFOWL,
    GULLS,
    SEABIRDS,
    HERONS,
    GREBES_DIVERS,
    GAMEBIRDS,
    OTHER,
    ;

    /** Tom ALL = inget filter; annars matchar arten den ekologiska kategorin. */
    fun matches(
        family: String,
        iocOrder: String,
    ): Boolean = this == ALL || categoryOf(family, iocOrder) == this

    companion object {
        const val PASSERINE_ORDER = "Passeriformes"

        // Legacy order-baserade set — tas bort i Task 2 när ArchiveViewModel bytt till matches().
        val orderSets: Map<ArchiveChip, Set<String>> =
            mapOf(
                ALL to emptySet(),
                SONGBIRDS to setOf("Passeriformes"),
                WATER to setOf("Anseriformes", "Suliformes", "Pelecaniformes", "Podicipediformes", "Gaviiformes"),
                RAPTORS to setOf("Accipitriformes", "Falconiformes"),
                OWLS to setOf("Strigiformes"),
                WADERS to setOf("Charadriiformes"),
            )

        /** Ekologiska chips → latinska familjer (matchar SpeciesSummary.family). SONGBIRDS via ordning; OTHER = komplement. */
        val familySets: Map<ArchiveChip, Set<String>> =
            mapOf(
                WATERFOWL to setOf("Anatidae"),
                RAPTORS to setOf("Accipitridae", "Falconidae", "Pandionidae"),
                WADERS to
                    setOf(
                        "Scolopacidae", "Charadriidae", "Glareolidae", "Burhinidae",
                        "Recurvirostridae", "Haematopodidae", "Rostratulidae", "Jacanidae", "Dromadidae",
                    ),
                GULLS to setOf("Laridae", "Stercorariidae", "Alcidae"),
                SEABIRDS to
                    setOf(
                        "Procellariidae", "Hydrobatidae", "Oceanitidae", "Sulidae",
                        "Phalacrocoracidae", "Anhingidae", "Fregatidae", "Phaethontidae",
                    ),
                HERONS to
                    setOf("Ardeidae", "Ciconiidae", "Threskiornithidae", "Pelecanidae", "Phoenicopteridae", "Scopidae"),
                GREBES_DIVERS to setOf("Podicipedidae", "Gaviidae"),
                GAMEBIRDS to setOf("Phasianidae", "Odontophoridae", "Numididae"),
                OWLS to setOf("Strigidae", "Tytonidae"),
            )

        val categorizedFamilies: Set<String> = familySets.values.flatten().toSet()

        /** Returnerar den ekologiska chip:en för en art. Aldrig ALL; faller till OTHER. */
        fun categoryOf(
            family: String,
            iocOrder: String,
        ): ArchiveChip {
            if (iocOrder == PASSERINE_ORDER) return SONGBIRDS
            familySets.forEach { (chip, families) -> if (family in families) return chip }
            return OTHER
        }
    }
}
```

- [ ] **Step 4: Format, then run test to verify it passes**

Run: `./gradlew ktlintFormat && ./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.encyclopedia.ArchiveChipMappingTest"`
Expected: PASS (alla 6 testmetoder gröna).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveChip.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/ui/encyclopedia/ArchiveChipMappingTest.kt
git commit -m "feat(archive): family-baserad ArchiveChip-classifier + regressionstest (DP C)"
```

---

## Task 2: Koppla in `matches()` i ArchiveViewModel-filtret

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveViewModel.kt` (rad ~120-126)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveChip.kt` (ta bort `orderSets`)

- [ ] **Step 1: Byt filtret i `toUiState`**

I `ArchiveViewModel.kt`, ersätt:

```kotlin
        val filtered =
            if (c == ArchiveChip.ALL) {
                list
            } else {
                val orders = ArchiveChip.orderSets[c].orEmpty()
                list.filter { it.iocOrder in orders }
            }
```

med:

```kotlin
        val filtered = list.filter { c.matches(it.family, it.iocOrder) }
```

- [ ] **Step 2: Ta bort `orderSets` ur ArchiveChip**

I `ArchiveChip.kt`, radera hela `orderSets`-blocket (inkl. kommentaren ovanför) ur companion-objektet. `PASSERINE_ORDER`, `familySets`, `categorizedFamilies` och `categoryOf` står kvar.

- [ ] **Step 3: Verifiera att inget annat refererar `orderSets`**

Run: `git grep -n "orderSets"`
Expected: inga träffar (utöver ev. denna plan-/spec-doc).

- [ ] **Step 4: Bygg + kör befintliga ViewModel-tester (regression)**

Run: `./gradlew ktlintFormat && ./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.encyclopedia.*"`
Expected: PASS. `ArchiveViewModelTest` (chip-persistens + sort) + `ArchiveChipMappingTest` gröna. *(Filter-korrektheten täcks av `ArchiveChipMappingTest` via den pure `matches()`/`categoryOf()`; ViewModeln applicerar bara det — ingen ny coroutine-/debounce-test behövs.)*

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveViewModel.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveChip.kt
git commit -m "refactor(archive): ViewModel filtrerar via ArchiveChip.matches, ta bort orderSets (DP C)"
```

---

## Task 3: Strängar SV + EN (lägg till nya, ta INTE bort water än)

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` (rad 417-422)
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml` (rad 404-409)

> **Trap:** `&` är ogiltigt råtecken i strings.xml — måste skrivas `&amp;` (XML-escape), annars build-fel. Apostrofer som `'`/`’` direkt (inte `\'`).

- [ ] **Step 1: SV — uppdatera songbirds-etiketten och lägg till 7 nya nycklar**

I `values/strings.xml`, ersätt raderna 417-422:

```xml
    <string name="archive_chip_all">Alla</string>
    <string name="archive_chip_songbirds">Sångfåglar</string>
    <string name="archive_chip_water">Vatten</string>
    <string name="archive_chip_raptors">Rovfåglar</string>
    <string name="archive_chip_owls">Ugglor</string>
    <string name="archive_chip_waders">Vadare</string>
```

med:

```xml
    <string name="archive_chip_all">Alla</string>
    <string name="archive_chip_songbirds">Tättingar</string>
    <string name="archive_chip_water">Vatten</string>
    <string name="archive_chip_raptors">Rovfåglar</string>
    <string name="archive_chip_owls">Ugglor</string>
    <string name="archive_chip_waders">Vadare</string>
    <string name="archive_chip_waterfowl">Änder &amp; gäss</string>
    <string name="archive_chip_gulls">Måsar, tärnor &amp; alkor</string>
    <string name="archive_chip_seabirds">Havsfåglar</string>
    <string name="archive_chip_herons">Hägrar &amp; storkar</string>
    <string name="archive_chip_grebes_divers">Doppingar &amp; lommar</string>
    <string name="archive_chip_gamebirds">Hönsfåglar</string>
    <string name="archive_chip_other">Övrigt</string>
```

- [ ] **Step 2: EN — lägg till samma 7 nya nycklar**

I `values-en/strings.xml`, ersätt raderna 404-409:

```xml
    <string name="archive_chip_all">All</string>
    <string name="archive_chip_songbirds">Songbirds</string>
    <string name="archive_chip_water">Water</string>
    <string name="archive_chip_raptors">Raptors</string>
    <string name="archive_chip_owls">Owls</string>
    <string name="archive_chip_waders">Waders</string>
```

med:

```xml
    <string name="archive_chip_all">All</string>
    <string name="archive_chip_songbirds">Songbirds</string>
    <string name="archive_chip_water">Water</string>
    <string name="archive_chip_raptors">Raptors</string>
    <string name="archive_chip_owls">Owls</string>
    <string name="archive_chip_waders">Waders</string>
    <string name="archive_chip_waterfowl">Ducks &amp; geese</string>
    <string name="archive_chip_gulls">Gulls, terns &amp; auks</string>
    <string name="archive_chip_seabirds">Seabirds</string>
    <string name="archive_chip_herons">Herons &amp; storks</string>
    <string name="archive_chip_grebes_divers">Grebes &amp; divers</string>
    <string name="archive_chip_gamebirds">Gamebirds</string>
    <string name="archive_chip_other">Other</string>
```

- [ ] **Step 2b: Verifiera SV/EN-paritet för de nya nycklarna**

Run: `git grep -c "archive_chip_" composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml`
Expected: båda filerna har samma antal `archive_chip_`-rader (13 vardera: 6 gamla + 7 nya).

- [ ] **Step 3: Bygg (verifiera att XML + nya resurser genereras)**

Run: `./gradlew :composeApp:generateComposeResClass :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL (inga XML-parsefel; `Res.string.archive_chip_*` genereras).

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "i18n(archive): chip-etiketter för nya ekologiska kategorier SV+EN (DP C)"
```

---

## Task 4: ChipBar → 12 chips + riv `WATER` och `archive_chip_water`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveScreen.kt` (rad 56-61 imports, 453-461 labels)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveChip.kt` (ta bort `WATER`)
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` + `values-en/strings.xml` (ta bort `archive_chip_water`)

- [ ] **Step 1: Byt string-imports i ArchiveScreen**

I `ArchiveScreen.kt`, ersätt importraderna 56-61:

```kotlin
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_all
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_owls
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_raptors
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_songbirds
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_waders
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_water
```

med:

```kotlin
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_all
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_gamebirds
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_grebes_divers
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_gulls
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_herons
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_other
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_owls
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_raptors
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_seabirds
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_songbirds
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_waders
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_waterfowl
```

- [ ] **Step 2: Byt `labels`-listan i ChipBar**

I `ArchiveScreen.kt`, ersätt `labels`-listan (rad 453-461):

```kotlin
    val labels =
        listOf(
            ArchiveChip.ALL to stringResource(Res.string.archive_chip_all),
            ArchiveChip.SONGBIRDS to stringResource(Res.string.archive_chip_songbirds),
            ArchiveChip.WATER to stringResource(Res.string.archive_chip_water),
            ArchiveChip.RAPTORS to stringResource(Res.string.archive_chip_raptors),
            ArchiveChip.OWLS to stringResource(Res.string.archive_chip_owls),
            ArchiveChip.WADERS to stringResource(Res.string.archive_chip_waders),
        )
```

med (ordning per spec §4):

```kotlin
    val labels =
        listOf(
            ArchiveChip.ALL to stringResource(Res.string.archive_chip_all),
            ArchiveChip.SONGBIRDS to stringResource(Res.string.archive_chip_songbirds),
            ArchiveChip.RAPTORS to stringResource(Res.string.archive_chip_raptors),
            ArchiveChip.OWLS to stringResource(Res.string.archive_chip_owls),
            ArchiveChip.GAMEBIRDS to stringResource(Res.string.archive_chip_gamebirds),
            ArchiveChip.WATERFOWL to stringResource(Res.string.archive_chip_waterfowl),
            ArchiveChip.GREBES_DIVERS to stringResource(Res.string.archive_chip_grebes_divers),
            ArchiveChip.HERONS to stringResource(Res.string.archive_chip_herons),
            ArchiveChip.WADERS to stringResource(Res.string.archive_chip_waders),
            ArchiveChip.GULLS to stringResource(Res.string.archive_chip_gulls),
            ArchiveChip.SEABIRDS to stringResource(Res.string.archive_chip_seabirds),
            ArchiveChip.OTHER to stringResource(Res.string.archive_chip_other),
        )
```

- [ ] **Step 3: Ta bort `WATER` ur enum**

I `ArchiveChip.kt`, radera raden `    WATER, // legacy …`. (Övriga enum-värden + companion oförändrade.)

- [ ] **Step 4: Ta bort `archive_chip_water` ur båda strings.xml**

Radera raden `<string name="archive_chip_water">…</string>` i BÅDA `values/strings.xml` och `values-en/strings.xml`.

- [ ] **Step 5: Verifiera att inga `WATER`/`archive_chip_water`-referenser kvarstår**

Run: `git grep -n "ArchiveChip.WATER\b\|archive_chip_water"`
Expected: inga träffar (utöver plan-/spec-doc).

- [ ] **Step 6: Bygg + kör tester**

Run: `./gradlew ktlintFormat && ./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.encyclopedia.*" && ./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveScreen.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveChip.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(archive): chip-rad med 12 ekologiska kategorier, riv legacy WATER (DP C)"
```

---

## Task 5: Full verifiering (lint + tester + APK)

**Files:** ingen (verifiering).

- [ ] **Step 1: ktlint + detekt + composeApp unit-tester**

Run: `./gradlew ktlintCheck detekt :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, alla tester gröna. Vid detekt-/ktlint-anmärkning på de nya filerna: åtgärda (t.ex. `./gradlew ktlintFormat`) och kör om.

- [ ] **Step 2: Bygg debug-APK (manifest/dep-trap-koll)**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit (om ktlintFormat ändrade något)**

```bash
git add -A
git commit -m "chore(archive): ktlintFormat + verifiering DP C" || echo "inget att committa"
```

---

## Task 6: Device-verify på SM-S918B (manuell)

**Files:** `docs/superpowers/screenshots/` (nya screenshots).

> Per [[feedback_personal_device_verify]]: SM-S918B är Albins dagliga telefon. Be om "händerna borta" innan ADB-driving, verifiera via `screencap` (mCurrentFocus kan ljuga under samtals-overlay), radera ev. fångat privat innehåll direkt. Debug-paketet är `se.birdy.android.debug`.

- [ ] **Step 1: Installera + starta**

```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android.debug/se.birdy.android.MainActivity
```

- [ ] **Step 2: Navigera till Arkivet/Uppslagsverket och verifiera chip-raden**

Kontrollera (scrolla chip-raden horisontellt):
- 12 chips visas i ordningen: Alla · Tättingar · Rovfåglar · Ugglor · Hönsfåglar · Änder & gäss · Doppingar & lommar · Hägrar & storkar · Vadare · Måsar, tärnor & alkor · Havsfåglar · Övrigt.
- **Vadare** → inga måsar/tärnor/alkor i listan (t.ex. ingen sillgrissla/fiskmås).
- **Måsar, tärnor & alkor** → alkor finns med (sök efter "sillgrissla"/"tordmule" i listan, eller verifiera att de dyker upp).
- **Övrigt** → hackspettar (t.ex. större hackspett) + duvor (t.ex. ringduva) finns med.
- Ingen chip är oväntat tom.

- [ ] **Step 3: Screenshot + spara**

```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" exec-out screencap -p > docs/superpowers/screenshots/2026-05-30-dp-c-chip-row.png
```

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/screenshots/2026-05-30-dp-c-chip-row.png
git commit -m "docs(screenshots): DP C kategori-chips device-verify på SM-S918B"
```

---

## Self-Review (utförd vid plan-skrivning)

- **Spec-täckning:** §3 besluten (launch-grade, family-mappning, SONGBIRDS via ordning, OTHER=komplement, alkor sammanslaget, hackspettar/duvor i Övrigt) → Task 1. §4 chip-taxonomi (alla 12 + mappning + antal) → Task 1 (familySets + regressionstest) + Task 4 (chip-rad). §5 komponenter → Task 1-4. §6 gratis migrering → täckt (persistereras som sträng; `valueOf`-fallback). §7 tester → Task 1 (mappning) + Task 6 (device). §8 bivillkor (strängar i båda xml, inga nya deps, ingen DB-rebuild) → Task 3/5. `family_sv`-fix + passerin-uppdelning uttryckligen INTE här (→ DP E). ✔
- **Placeholder-scan:** all kod är konkret; inga TBD/TODO. ✔
- **Typkonsistens:** `categoryOf(family, iocOrder)`, `matches(family, iocOrder)`, `familySets`, `categorizedFamilies`, `PASSERINE_ORDER` används identiskt i Task 1/2 och i testet. Enum-värden konsekventa. ✔
- **Buildbarhet per task:** Task 1 additiv (WATER + orderSets kvar) → kompilerar. Task 2 tar bort orderSets (enda referens = ViewModel, uppdateras samtidigt). Task 4 tar bort WATER + water-sträng först när ChipBar slutat referera dem. ✔
