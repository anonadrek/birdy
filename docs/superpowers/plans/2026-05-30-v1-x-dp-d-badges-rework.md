# DP D — Märken-omarbetning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bygg om märkes-systemet så ~80 % belönar skådar-prestation (var ~40 %), utan döda märken, med ett synligt rödlistat-spår, förlängd progression och städat premium — allt på data som redan finns på `Species`.

**Architecture:** Rent badge-lager (domän + composeApp). Inga content-/DB-/schemaändringar. Nya `BadgeRule`-subtyper + evaluator-grenar (familj = distinkta arter, familje-grupp, distinkta familjer/ordningar, rödlistad-IUCN), omskrivna `badges.yaml`/`premium_badges.yaml`, ny `tier`-axel på `BadgeCategory` för tvåsektions-layout, plus tre UI-fixar (avslöja rödlistat, "sne"-etikett, onboarding-scen 5).

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, kaml (YAML), compose-resources (strings), kotlin.test (commonTest/jvmTest).

**Spec:** `docs/superpowers/specs/2026-05-30-v1-x-dp-d-badges-rework-design.md`

**Branch:** Körs i fräsch session på egen branch från `main`: `git switch main && git pull && git switch -c feat/dp-d-badges-rework`. (Specen ligger committad på Phase B-branchen; cherry-picka in den om main saknar den: `git checkout feat/v1.2-phase-b-weekly-recap -- docs/superpowers/specs/2026-05-30-v1-x-dp-d-badges-rework-design.md`.)

**Bivillkor (håll i ALLA tasks):** alla UI-strängar i BÅDA `strings.xml` (SV `values/` + EN `values-en/`); aldrig accuracy-siffror; inget audio bakom premium (löses här); ingen schemaändring; raw `'`/`’` (ej `\'`); inga `%%`.

---

### Task 1: BadgeCategory — tier-axel + nya kategorier

**Files:**
- Modify: `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeCategory.kt`
- Grep/Modify: alla referenser till `BadgeCategory.RARE`

- [ ] **Step 1: Skriv om BadgeCategory.kt**

```kotlin
package se.birdy.domain.badge

enum class BadgeTier { MILESTONE, HABIT }

enum class BadgeCategory(
    val order: Int,
    val tier: BadgeTier,
) {
    PROGRESSION(0, BadgeTier.MILESTONE),
    FAMILY(1, BadgeTier.MILESTONE),
    BREADTH(2, BadgeTier.MILESTONE),
    REDLISTED(3, BadgeTier.MILESTONE),
    SEASON(4, BadgeTier.MILESTONE),
    AUDIO(5, BadgeTier.MILESTONE),
    STREAK_WEEKLY(6, BadgeTier.HABIT),
    STREAK_MONTHLY(7, BadgeTier.HABIT),
}
```

- [ ] **Step 2: Hitta alla referenser till den borttagna `RARE`**

Run: `git grep -n "BadgeCategory.RARE"`
Expected: träffar i `BadgesViewModel.kt` (rad ~137) + ev. test. Dessa hanteras i Task 8 (ViewModel) — notera dem, kompilera inte än.

- [ ] **Step 3: Verifiera domän-modulen kompilerar isolerat**

Run (bash, med JAVA_HOME-prefix från CLAUDE.md):
```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"; export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :shared:domain:compileKotlinJvm
```
Expected: BUILD SUCCESSFUL (domän har inga RARE-referenser).

- [ ] **Step 4: Commit**

```bash
git add shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeCategory.kt
git commit -m "feat(badges): BadgeCategory tier-axel + BREADTH/REDLISTED/AUDIO"
```

---

### Task 2: BadgeRule — fyra nya/ändrade subtyper

**Files:**
- Modify: `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeRule.kt`

- [ ] **Step 1: Lägg till de nya subtyperna i BadgeRule.kt**

Lägg in (behåll alla befintliga subtyper; `ObservedInFamily` byter bara semantik i evaluatorn, inte signatur):

```kotlin
    /** Distinct species observed whose taxonomy.family is in [families]. */
    data class ObservedInFamilyGroup(
        val families: Set<String>,
        override val target: Int,
    ) : BadgeRule

    /** Number of distinct taxonomy.family among observed species. */
    data class CountDistinctFamilies(
        override val target: Int,
    ) : BadgeRule

    /** Number of distinct taxonomy.iocOrder among observed species. */
    data class CountDistinctOrders(
        override val target: Int,
    ) : BadgeRule

    /** Distinct species observed whose iucnStatus is red-listed (NT/VU/CR). */
    data class ObservedRedListed(
        override val target: Int,
    ) : BadgeRule
```

- [ ] **Step 2: Markera ObservedWithAbundance för borttag**

`ObservedWithAbundance` blir oanvänt efter Task 3/4/5. Ta INTE bort än (bryter evaluator+loader+tester som städas i sina tasks). Lämna med en kommentar `// TODO(DP D): remove after Task 4` — eller behåll tyst; den städas i Task 3+4.

- [ ] **Step 3: Kompilera**

Run: `./gradlew :shared:domain:compileKotlinJvm`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeRule.kt
git commit -m "feat(badges): nya BadgeRule-subtyper (familje-grupp, bredd, rödlistad)"
```

---

### Task 3: RecalculateBadgesUseCase — evaluator (TDD)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/badges/RecalculateBadgesUseCase.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/badges/RecalculateBadgesUseCaseTest.kt`

- [ ] **Step 1: Uppdatera test-helpern `fakeSpecies` (parametrisera iocOrder + iucnStatus)**

I testfilen, ändra `fakeSpecies`-signaturen + body:

```kotlin
    private fun fakeSpecies(
        qid: String,
        family: String = "unknown",
        iocOrder: String = "Fakeiformes",
        iucnStatus: String = "LC",
    ): Species =
        Species(
            id = SpeciesId(qid),
            scientificName = "Fakeus speciesius",
            taxonomy = SpeciesTaxonomy(family = family, familySv = null, genus = "Fakeus", iocOrder = iocOrder),
            name = qid,
            abundance = ContentAbundance.OVANLIG,
            iucnStatus = iucnStatus,
            regions = emptyList(),
            season = emptyMap(),
            description = null,
            migration = null,
            images = emptyList(),
        )
```

Ta även bort de två `observed_with_abundance`-testerna (`sällsynt 1 match`, `non-matching abundance...`) och importen av `BadgeAbundance` om oanvänd. Ta bort `abundance`-parametern ovan (den användes bara av de testerna).

- [ ] **Step 2: Skriv de nya failing-testerna**

Lägg till i `RecalculateBadgesUseCaseTest`:

```kotlin
    @Test
    fun `observed_in_family — counts distinct species not observations`() {
        val species = mapOf(
            SpeciesId("Q1") to fakeSpecies("Q1", family = "paridae"),
            SpeciesId("Q2") to fakeSpecies("Q2", family = "paridae"),
        )
        // 3 obs but only 2 distinct paridae species
        val obs = listOf(obs("Q1", day = 1), obs("Q1", day = 2), obs("Q2", day = 3))
        val catalog = catalogOf(badge("fam", BadgeRule.ObservedInFamily("paridae", 3)))
        assertEquals(2, recalc.currentValue(BadgeRule.ObservedInFamily("paridae", 3), obs, species))
        assertEquals(emptyList(), recalc.newUnlocks(obs, species, catalog, emptySet()))
    }

    @Test
    fun `observed_in_family_group — distinct species across the family set`() {
        val species = mapOf(
            SpeciesId("Q1") to fakeSpecies("Q1", family = "phylloscopidae"),
            SpeciesId("Q2") to fakeSpecies("Q2", family = "sylviidae"),
            SpeciesId("Q3") to fakeSpecies("Q3", family = "corvidae"), // not a warbler
        )
        val obs = listOf(obs("Q1", day = 1), obs("Q2", day = 2), obs("Q3", day = 3))
        val rule = BadgeRule.ObservedInFamilyGroup(setOf("phylloscopidae", "sylviidae", "acrocephalidae"), target = 2)
        assertEquals(2, recalc.currentValue(rule, obs, species))
        assertEquals(listOf("warblers"), recalc.newUnlocks(obs, species, catalogOf(badge("warblers", rule)), emptySet()).map { it.badgeId })
    }

    @Test
    fun `count_distinct_families — counts unique families`() {
        val species = mapOf(
            SpeciesId("Q1") to fakeSpecies("Q1", family = "paridae"),
            SpeciesId("Q2") to fakeSpecies("Q2", family = "corvidae"),
            SpeciesId("Q3") to fakeSpecies("Q3", family = "corvidae"),
        )
        val obs = listOf(obs("Q1", day = 1), obs("Q2", day = 2), obs("Q3", day = 3))
        assertEquals(2, recalc.currentValue(BadgeRule.CountDistinctFamilies(3), obs, species))
    }

    @Test
    fun `count_distinct_orders — counts unique orders`() {
        val species = mapOf(
            SpeciesId("Q1") to fakeSpecies("Q1", iocOrder = "Passeriformes"),
            SpeciesId("Q2") to fakeSpecies("Q2", iocOrder = "Anseriformes"),
            SpeciesId("Q3") to fakeSpecies("Q3", iocOrder = "Passeriformes"),
        )
        val obs = listOf(obs("Q1", day = 1), obs("Q2", day = 2), obs("Q3", day = 3))
        assertEquals(2, recalc.currentValue(BadgeRule.CountDistinctOrders(5), obs, species))
    }

    @Test
    fun `observed_red_listed — counts distinct NT VU CR species, ignores LC`() {
        val species = mapOf(
            SpeciesId("Q1") to fakeSpecies("Q1", iucnStatus = "VU"),
            SpeciesId("Q2") to fakeSpecies("Q2", iucnStatus = "CR"),
            SpeciesId("Q3") to fakeSpecies("Q3", iucnStatus = "LC"),
            SpeciesId("Q4") to fakeSpecies("Q4", iucnStatus = "NT"),
        )
        val obs = listOf(obs("Q1", day = 1), obs("Q2", day = 2), obs("Q3", day = 3), obs("Q4", day = 4))
        val rule = BadgeRule.ObservedRedListed(target = 3)
        assertEquals(3, recalc.currentValue(rule, obs, species))
        assertEquals(listOf("rl"), recalc.newUnlocks(obs, species, catalogOf(badge("rl", rule)), emptySet()).map { it.badgeId })
    }
```

- [ ] **Step 3: Kör testerna — verifiera FAIL**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*RecalculateBadgesUseCaseTest*"`
Expected: kompileringsfel / FAIL (nya `when`-grenar saknas).

- [ ] **Step 4: Implementera evaluator-grenarna i `rawValue`**

I `RecalculateBadgesUseCase.rawValue`: ersätt `ObservedInFamily`-grenen (distinkt) och lägg till de fyra nya; ta bort `ObservedWithAbundance`-grenen + `mapAbundance()` + `BadgeAbundance`/`ContentAbundance`-importer:

```kotlin
            is BadgeRule.ObservedInFamily ->
                observations.mapNotNull { it.speciesId }.distinct().count { qid ->
                    speciesByQid[SpeciesId(qid)]?.taxonomy?.family == rule.family
                }
            is BadgeRule.ObservedInFamilyGroup ->
                observations.mapNotNull { it.speciesId }.distinct().count { qid ->
                    speciesByQid[SpeciesId(qid)]?.taxonomy?.family in rule.families
                }
            is BadgeRule.CountDistinctFamilies ->
                observations.mapNotNull { it.speciesId }
                    .mapNotNull { speciesByQid[SpeciesId(it)]?.taxonomy?.family }
                    .distinct().size
            is BadgeRule.CountDistinctOrders ->
                observations.mapNotNull { it.speciesId }
                    .mapNotNull { speciesByQid[SpeciesId(it)]?.taxonomy?.iocOrder }
                    .distinct().size
            is BadgeRule.ObservedRedListed ->
                observations.mapNotNull { it.speciesId }.distinct().count { qid ->
                    speciesByQid[SpeciesId(qid)]?.iucnStatus in RED_LISTED
                }
```

Lägg till en konstant i klassen (eller fil-topp):
```kotlin
private val RED_LISTED = setOf("NT", "VU", "CR")
```

- [ ] **Step 5: Kör testerna — verifiera PASS**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*RecalculateBadgesUseCaseTest*"`
Expected: PASS (alla gamla + 5 nya).

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/badges/RecalculateBadgesUseCase.kt composeApp/src/commonTest/kotlin/se/birdy/app/badges/RecalculateBadgesUseCaseTest.kt
git commit -m "feat(badges): evaluator distinkt familj + familje-grupp/bredd/rödlistad; ta bort abundance"
```

---

### Task 4: BadgeCatalogLoader — parsa nya regler & kategorier (TDD)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/badges/BadgeCatalogLoader.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/badges/BadgeCatalogLoaderTest.kt`

- [ ] **Step 1: Skriv failing-tester för ny parsing**

Lägg till i `BadgeCatalogLoaderTest` (följ befintligt mönster i filen — läs den först för `parse(...)`-anrop):

```kotlin
    @Test
    fun `parses observed_in_family_group with families list`() {
        val yaml = """
            version: 2
            badges:
              - id: warblers
                category: family
                rule:
                  type: observed_in_family_group
                  families: [phylloscopidae, sylviidae]
                  target: 15
        """.trimIndent()
        val cat = BadgeCatalogLoader.parse(yaml)
        val rule = cat.badges.single().rule
        assertTrue(rule is BadgeRule.ObservedInFamilyGroup)
        assertEquals(setOf("phylloscopidae", "sylviidae"), (rule as BadgeRule.ObservedInFamilyGroup).families)
        assertEquals(15, rule.target)
    }

    @Test
    fun `parses breadth and redlisted and audio categories`() {
        val yaml = """
            version: 2
            badges:
              - id: bf
                category: breadth
                rule: { type: count_distinct_families, target: 20 }
              - id: bo
                category: breadth
                rule: { type: count_distinct_orders, target: 20 }
              - id: rl
                category: redlisted
                rule: { type: observed_red_listed, target: 5 }
              - id: au
                category: audio
                rule: { type: audio_observation_count, target: 5 }
        """.trimIndent()
        val cat = BadgeCatalogLoader.parse(yaml)
        assertEquals(BadgeCategory.BREADTH, cat.findById("bf")!!.category)
        assertEquals(BadgeCategory.REDLISTED, cat.findById("rl")!!.category)
        assertEquals(BadgeCategory.AUDIO, cat.findById("au")!!.category)
        assertTrue(cat.findById("bo")!!.rule is BadgeRule.CountDistinctOrders)
    }
```

- [ ] **Step 2: Kör — verifiera FAIL**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*BadgeCatalogLoaderTest*"`
Expected: FAIL (okänd kategori/regeltyp).

- [ ] **Step 3: Implementera parsing**

I `BadgeCatalogLoader`:
1. Lägg till `families: List<String>? = null` i `RawRule`.
2. I `parseCategory`: ta bort `"rare"`; lägg till:
```kotlin
            "breadth" -> BadgeCategory.BREADTH
            "redlisted" -> BadgeCategory.REDLISTED
            "audio" -> BadgeCategory.AUDIO
```
3. I `parseRule`: ändra inget för `observed_in_family` (signaturen är oförändrad); ta bort `observed_with_abundance`-grenen + `parseAbundance` + `BadgeAbundance`-import; lägg till:
```kotlin
            "observed_in_family_group" ->
                BadgeRule.ObservedInFamilyGroup(
                    families = (raw.families ?: missing(badgeId, "families")).toSet(),
                    target = raw.target ?: missing(badgeId, "target"),
                )
            "count_distinct_families" ->
                BadgeRule.CountDistinctFamilies(raw.target ?: missing(badgeId, "target"))
            "count_distinct_orders" ->
                BadgeRule.CountDistinctOrders(raw.target ?: missing(badgeId, "target"))
            "observed_red_listed" ->
                BadgeRule.ObservedRedListed(raw.target ?: missing(badgeId, "target"))
```

- [ ] **Step 4: Kör — verifiera PASS**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*BadgeCatalogLoaderTest*"`
Expected: PASS.

- [ ] **Step 5: Ta bort BadgeAbundance + ObservedWithAbundance (städning)**

Run: `git grep -n "ObservedWithAbundance\|BadgeAbundance\|observed_with_abundance"`
Expected: inga kvar utom definitionerna. Ta bort `BadgeRule.ObservedWithAbundance`, `shared/domain/.../BadgeAbundance.kt`, och ev. kvarvarande importer. Om någon referens kvarstår — fixa den.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/badges/BadgeCatalogLoader.kt composeApp/src/commonTest/kotlin/se/birdy/app/badges/BadgeCatalogLoaderTest.kt shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/
git commit -m "feat(badges): loader parsar nya regler/kategorier; ta bort abundance"
```

---

### Task 5: badges.yaml — bygg om gratis-setet

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/files/badges.yaml`

- [ ] **Step 1: Ersätt hela badges.yaml**

```yaml
version: 2
badges:
  # ===== Livslista / Progression (5) =====
  - id: novice
    category: progression
    rule: { type: count_unique_species, target: 5 }
  - id: birder_bronze
    category: progression
    rule: { type: count_unique_species, target: 25 }
  - id: birder_silver
    category: progression
    rule: { type: count_unique_species, target: 100 }
  - id: birder_gold
    category: progression
    rule: { type: count_unique_species, target: 250 }
  - id: birder_legend
    category: progression
    rule: { type: count_unique_species, target: 500 }

  # ===== Familje-mästare (7) — distinkta arter =====
  - id: family_anatidae
    category: family
    rule: { type: observed_in_family, family: anatidae, target: 15 }
  - id: family_scolopacidae
    category: family
    rule: { type: observed_in_family, family: scolopacidae, target: 12 }
  - id: family_accipitridae
    category: family
    rule: { type: observed_in_family, family: accipitridae, target: 12 }
  - id: family_fringillidae
    category: family
    rule: { type: observed_in_family, family: fringillidae, target: 12 }
  - id: family_paridae
    category: family
    rule: { type: observed_in_family, family: paridae, target: 6 }
  - id: family_strigidae
    category: family
    rule: { type: observed_in_family, family: strigidae, target: 8 }
  - id: family_songbirds
    category: family
    rule:
      type: observed_in_family_group
      families: [phylloscopidae, acrocephalidae, sylviidae, cettiidae, locustellidae, cisticolidae]
      target: 15

  # ===== Taxonomisk bredd (3) =====
  - id: breadth_families_20
    category: breadth
    rule: { type: count_distinct_families, target: 20 }
  - id: breadth_families_50
    category: breadth
    rule: { type: count_distinct_families, target: 50 }
  - id: breadth_orders_20
    category: breadth
    rule: { type: count_distinct_orders, target: 20 }

  # ===== Rödlistade (3) — IUCN NT/VU/CR =====
  - id: redlisted_1
    category: redlisted
    rule: { type: observed_red_listed, target: 1 }
  - id: redlisted_5
    category: redlisted
    rule: { type: observed_red_listed, target: 5 }
  - id: redlisted_15
    category: redlisted
    rule: { type: observed_red_listed, target: 15 }

  # ===== Säsong (2) =====
  - id: season_all_year
    category: season
    rule: { type: observed_in_all_seasons, target: 1 }
  - id: season_faithful
    category: season
    rule: { type: species_across_seasons, seasons: 4, target: 1 }

  # ===== Ljud (1) =====
  - id: audio_scholar
    category: audio
    rule: { type: audio_observation_count, target: 5 }

  # ===== Vanor — streaks (5) =====
  - id: weekly_streak_4
    category: streak_weekly
    rule: { type: weekly_streak, target: 4 }
  - id: weekly_streak_12
    category: streak_weekly
    rule: { type: weekly_streak, target: 12 }
  - id: weekly_streak_52
    category: streak_weekly
    rule: { type: weekly_streak, target: 52 }
  - id: monthly_streak_3
    category: streak_monthly
    rule: { type: monthly_streak, target: 3 }
  - id: monthly_streak_12
    category: streak_monthly
    rule: { type: monthly_streak, target: 12 }
```

- [ ] **Step 2: Commit** (verifieras tillsammans med strängar i Task 7)

```bash
git add composeApp/src/commonMain/composeResources/files/badges.yaml
git commit -m "feat(badges): bygg om gratis-setet (livslista 5-500, familje-completion, bredd, rödlistad, säsong, ljud)"
```

---

### Task 6: premium_badges.yaml — trimma till 7

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/files/premium_badges.yaml`

- [ ] **Step 1: Ersätt hela premium_badges.yaml**

> Premium-kategori är kosmetisk (premium-sektionen renderas platt) — bara valida enum-värden krävs.

```yaml
# Premium-only badges (flair/hängivenhet). All progression & samling är GRATIS.
# Inget audio här (BirdNET CC BY-NC-SA). premium_field_member låses upp manuellt
# via PremiumActivationListener (rule: type=manual).
version: 4
badges:
  - id: premium_field_member
    category: progression
    rule: { type: manual, target: 1 }
  - id: premium_dawn_chorus
    category: season
    rule: { type: observed_before_hour, hour: 6, target: 5 }
  - id: premium_early_pilgrim
    category: season
    rule: { type: observed_in_hour_range, startHour: 5, endHourExclusive: 7, target: 1 }
  - id: premium_field_journalist
    category: progression
    rule: { type: observations_with_note, minLength: 30, target: 25 }
  - id: premium_winter_wanderer
    category: season
    rule: { type: observed_in_season, season: winter, target: 10 }
  - id: premium_sunday_birder
    category: streak_weekly
    rule: { type: sunday_streak, target: 4 }
  - id: premium_daily_bird_hunter
    category: progression
    rule: { type: daily_bird_matches, target: 3 }
```

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/commonMain/composeResources/files/premium_badges.yaml
git commit -m "feat(badges): premium 13->7 (ta bort krockar/dött; song_scholar->gratis)"
```

---

### Task 7: Strängar + BadgeStringMap

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` (SV)
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml` (EN)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeStringMap.kt`

- [ ] **Step 1: Lägg till nya strängar i `values/strings.xml` (SV)**

```xml
    <!-- DP D: nya/ändrade märken -->
    <string name="badge_name_birder_gold">Rutinerad skådare</string>
    <string name="badge_desc_birder_gold">250 arter i livslistan.</string>
    <string name="badge_name_birder_legend">Mästarskådare</string>
    <string name="badge_desc_birder_legend">500 arter — en livsgärning.</string>
    <string name="badge_name_family_scolopacidae">Vadarvän</string>
    <string name="badge_desc_family_scolopacidae">12 olika vadare sedda.</string>
    <string name="badge_name_family_strigidae">Uggleskådare</string>
    <string name="badge_desc_family_strigidae">8 olika ugglor sedda.</string>
    <string name="badge_name_family_songbirds">Sångarsamlare</string>
    <string name="badge_desc_family_songbirds">15 olika sångare sedda.</string>
    <string name="badge_name_breadth_families_20">Familjespanare</string>
    <string name="badge_desc_breadth_families_20">Arter ur 20 olika familjer.</string>
    <string name="badge_name_breadth_families_50">Familjekännare</string>
    <string name="badge_desc_breadth_families_50">Arter ur 50 olika familjer.</string>
    <string name="badge_name_breadth_orders_20">Ordningsresenär</string>
    <string name="badge_desc_breadth_orders_20">Arter ur 20 olika ordningar.</string>
    <string name="badge_name_redlisted_1">Rödlistad</string>
    <string name="badge_desc_redlisted_1">Din första rödlistade art.</string>
    <string name="badge_name_redlisted_5">Raritetsjägare</string>
    <string name="badge_desc_redlisted_5">5 rödlistade arter sedda.</string>
    <string name="badge_name_redlisted_15">Rödlistemästare</string>
    <string name="badge_desc_redlisted_15">15 rödlistade arter sedda.</string>
    <string name="badge_name_season_all_year">Året runt</string>
    <string name="badge_desc_season_all_year">Fynd i alla fyra årstider.</string>
    <string name="badge_name_season_faithful">Trogen följeslagare</string>
    <string name="badge_desc_season_faithful">Samma art i alla fyra årstider.</string>
    <string name="badge_name_audio_scholar">Ljudskådare</string>
    <string name="badge_desc_audio_scholar">5 arter identifierade på läte.</string>
    <!-- DP D: sektionsrubriker -->
    <string name="badges_section_milestones">SKÅDAR-MILSTOLPAR</string>
    <string name="badges_section_habits">VANOR</string>
```

Ändra de fyra behållna familje-strängarna (nytt mål → ny copy):
```xml
    <string name="badge_name_family_anatidae">Andsamlare</string>
    <string name="badge_desc_family_anatidae">15 olika andfåglar sedda.</string>
    <string name="badge_name_family_accipitridae">Rovfågelskådare</string>
    <string name="badge_desc_family_accipitridae">12 olika rovfåglar sedda.</string>
    <string name="badge_name_family_fringillidae">Finksamlare</string>
    <string name="badge_desc_family_fringillidae">12 olika finkar sedda.</string>
    <string name="badge_name_family_paridae">Messamlare</string>
    <string name="badge_desc_family_paridae">6 olika mesar sedda.</string>
```

- [ ] **Step 2: Spegla ALLT i `values-en/strings.xml` (EN)**

```xml
    <string name="badge_name_birder_gold">Seasoned birder</string>
    <string name="badge_desc_birder_gold">250 species on your life list.</string>
    <string name="badge_name_birder_legend">Master birder</string>
    <string name="badge_desc_birder_legend">500 species — a life\'s work.</string>
```
> OBS apostrof: compose-resources unescape:ar inte Android `\'` — använd raw `'` eller `’`. Skriv `a life's work.` med rak apostrof eller `life’s` (U+2019). INTE `\'`.

Resten EN:
```xml
    <string name="badge_name_family_scolopacidae">Wader watcher</string>
    <string name="badge_desc_family_scolopacidae">12 different waders seen.</string>
    <string name="badge_name_family_strigidae">Owl watcher</string>
    <string name="badge_desc_family_strigidae">8 different owls seen.</string>
    <string name="badge_name_family_songbirds">Warbler collector</string>
    <string name="badge_desc_family_songbirds">15 different warblers seen.</string>
    <string name="badge_name_breadth_families_20">Family spanner</string>
    <string name="badge_desc_breadth_families_20">Species from 20 different families.</string>
    <string name="badge_name_breadth_families_50">Family connoisseur</string>
    <string name="badge_desc_breadth_families_50">Species from 50 different families.</string>
    <string name="badge_name_breadth_orders_20">Order traveller</string>
    <string name="badge_desc_breadth_orders_20">Species from 20 different orders.</string>
    <string name="badge_name_redlisted_1">Red-listed</string>
    <string name="badge_desc_redlisted_1">Your first red-listed species.</string>
    <string name="badge_name_redlisted_5">Rarity hunter</string>
    <string name="badge_desc_redlisted_5">5 red-listed species seen.</string>
    <string name="badge_name_redlisted_15">Red-list master</string>
    <string name="badge_desc_redlisted_15">15 red-listed species seen.</string>
    <string name="badge_name_season_all_year">Year-round</string>
    <string name="badge_desc_season_all_year">Finds in all four seasons.</string>
    <string name="badge_name_season_faithful">Faithful companion</string>
    <string name="badge_desc_season_faithful">The same species in all four seasons.</string>
    <string name="badge_name_audio_scholar">Sound birder</string>
    <string name="badge_desc_audio_scholar">5 species identified by song.</string>
    <string name="badges_section_milestones">BIRDING MILESTONES</string>
    <string name="badges_section_habits">HABITS</string>
    <string name="badge_name_family_anatidae">Wildfowler</string>
    <string name="badge_desc_family_anatidae">15 different wildfowl seen.</string>
    <string name="badge_name_family_accipitridae">Raptor watcher</string>
    <string name="badge_desc_family_accipitridae">12 different raptors seen.</string>
    <string name="badge_name_family_fringillidae">Finch collector</string>
    <string name="badge_desc_family_fringillidae">12 different finches seen.</string>
    <string name="badge_name_family_paridae">Tit collector</string>
    <string name="badge_desc_family_paridae">6 different tits seen.</string>
```

- [ ] **Step 3: Ta bort döda strängar i BÅDA filerna**

Ta bort name+desc-par för: `weekly_streak_26`, `monthly_streak_6`, `season_winter`, `season_spring`, `season_summer`, `season_autumn`, `family_corvidae`, `family_turdidae`, `family_sylviidae`, `family_picidae`, `rare_first`, `rare_5`, `rare_10`, och premium `archive_curator`, `lifelist_legend`, `migration_mapper`, `seasonal_steward`, `rare_seeker`, `song_scholar`.
Run: `git grep -n "season_winter\|family_corvidae\|premium_archive_curator\|badge_name_rare_first" composeApp/src/commonMain/composeResources` — verifiera 0 efter borttag.

- [ ] **Step 4: Uppdatera BadgeStringMap.kt**

Ta bort imports + `when`-grenar för de borttagna id:na (steg 3-listan). Lägg till imports + grenar för de nya id:na i BÅDA `nameFor` och `descriptionFor`:
```
birder_gold, birder_legend, family_scolopacidae, family_strigidae, family_songbirds,
breadth_families_20, breadth_families_50, breadth_orders_20,
redlisted_1, redlisted_5, redlisted_15, season_all_year, season_faithful, audio_scholar
```
Exempel (nameFor):
```kotlin
            "birder_gold" -> Res.string.badge_name_birder_gold
            "redlisted_5" -> Res.string.badge_name_redlisted_5
            "family_songbirds" -> Res.string.badge_name_family_songbirds
            // … osv för alla 14 nya, + motsvarande _desc i descriptionFor
```

- [ ] **Step 5: Skriv coverage-test (alla katalog-id har strängar)**

Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/BadgeStringMapCoverageTest.kt`
```kotlin
package se.birdy.app.ui.badges

import kotlinx.coroutines.test.runTest
import se.birdy.app.badges.BadgeCatalogLoader
import kotlin.test.Test

class BadgeStringMapCoverageTest {
    @Test
    fun `every catalog badge has name and description resources`() = runTest {
        val catalog = BadgeCatalogLoader.loadFromResources()
        catalog.badges.forEach { b ->
            BadgeStringMap.nameFor(b.id) // throws if missing
            BadgeStringMap.descriptionFor(b.id)
        }
    }
}
```
> Om `loadFromResources()` inte går i commonTest (resurs-API), spegla istället med en hårdkodad id-lista som matchar yaml. Kör befintliga `BadgeCatalogLoaderTest` för att se mönstret.

- [ ] **Step 6: Kör badge-tester**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*Badge*"`
Expected: PASS (inkl. coverage-testet).

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeStringMap.kt composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/BadgeStringMapCoverageTest.kt
git commit -m "feat(badges): strängar + BadgeStringMap för nytt set (SV+EN); coverage-test"
```

---

### Task 8: BadgesViewModel — avslöja rödlistat, filtrera unlockedCount, tier-grupp (TDD)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/BadgesViewModelTest.kt`

- [ ] **Step 1: Skriv failing-tester**

Lägg till i `BadgesViewModelTest` (läs filen för dess setup-mönster: hur VM byggs, FakeObservationRepository/FakeBadgeRepository):

```kotlin
    @Test
    fun `redlisted badges show progress instead of hidden`() = runTest {
        // bygg VM med en redlisted-badge i katalogen + en obs av en VU-art
        // assert: motsvarande LockedBadgeProgress.state är InProgress eller Locked, ALDRIG Hidden
    }

    @Test
    fun `unlockedCount ignores unlocks not in catalog`() = runTest {
        // ge badgeRepo en unlock med id som inte finns i katalogen ("ghost_badge")
        // assert: state.unlockedCount räknar inte ghost_badge
    }
```
> Fyll i med samma fixtur-stil som befintliga tester i filen (använd deras `catalogOf`/repo-fakes). Behåll testerna konkreta — assertera på `BadgesUiState.Loaded`.

- [ ] **Step 2: Kör — verifiera FAIL**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*BadgesViewModelTest*"`
Expected: FAIL.

- [ ] **Step 3: Implementera**

I `computeLockedState` — ta bort raden:
```kotlin
        if (badge.category == BadgeCategory.RARE) return BadgeGridState.Hidden
```
(REDLISTED faller nu igenom till `recalc.currentValue` → InProgress/Locked.)

I `buildLoaded` — filtrera unlockedCount mot katalogen:
```kotlin
        val unlockedInCatalog = unlocks.count { catalog.findById(it.badgeId) != null }
```
och använd `unlockedCount = unlockedInCatalog` i `BadgesUiState.Loaded(...)` (istället för `unlocks.size`).

> `BadgeGridState.Hidden` kan bli oanvänt. Kör `git grep -n "BadgeGridState.Hidden"` — om inga andra referenser, lämna enum-värdet (ofarligt) eller ta bort om ktlint/detekt klagar.

- [ ] **Step 4: Kör — verifiera PASS**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*BadgesViewModelTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesViewModel.kt composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/BadgesViewModelTest.kt
git commit -m "feat(badges): avslöja rödlistat-spåret + filtrera unlockedCount mot katalog"
```

---

### Task 9: BadgesScreen — tvåsektions-layout (Milstolpar / Vanor)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesScreen.kt`

- [ ] **Step 1: Partitionera den lockade listan på tier i `LoadedContent`**

Ersätt det enda `items(items = state.locked …)`-blocket (+ den föregående "to_discover"-SectionLabel) med två grupper. Lägg överst i `LoadedContent`:
```kotlin
import se.birdy.domain.badge.BadgeTier
// …
        val milestones = state.locked.filter { it.badge.category.tier == BadgeTier.MILESTONE }
        val habits = state.locked.filter { it.badge.category.tier == BadgeTier.HABIT }
```
(Beräkna före `LazyVerticalGrid`, t.ex. som lokala `val` i funktionen.)

Byt ut "to_discover"-headern + items mot:
```kotlin
        if (milestones.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel(stringResource(Res.string.badges_section_milestones))
            }
            items(items = milestones, key = { it.badge.id }) { lbp ->
                BadgeGridCell(progress = lbp, onClick = { onLockedClick(lbp) }, modifier = Modifier.fillMaxWidth())
            }
        }
        if (habits.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel(stringResource(Res.string.badges_section_habits))
            }
            items(items = habits, key = { it.badge.id }) { lbp ->
                BadgeGridCell(progress = lbp, onClick = { onLockedClick(lbp) }, modifier = Modifier.fillMaxWidth())
            }
        }
```
Importera `badges_section_milestones` + `badges_section_habits`; ta bort den oanvända `badges_section_to_discover`-importen om den ej används på annat håll.

> "VANOR" nedtonas av sin position (efter milstolparna). Vill man förstärka: ge `SectionLabel` en valfri `alpha`-parameter och anropa habits-etiketten med `MarginaliaInk.copy(alpha = 0.55f)`. Håll det enkelt — ingen ny komponent.

- [ ] **Step 2: Bygg debug-APK för att fånga ev. kompileringsfel**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesScreen.kt
git commit -m "feat(badges): tvåsektions-layout — Skådar-milstolpar / Vanor"
```

---

### Task 10: StampSeal — fixa "sne" etikett

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/StampSeal.kt`

- [ ] **Step 1: Flytta rotationen från ytter-Column till stämpel-Box**

I `StampSeal` (rad ~124–133): ta bort `.rotate(state.rotationDegrees())` från ytter-`Column`:ens modifier (behåll `.semantics{…}`). Lägg in rotationen i `sealModifier` så endast cirkeln roterar:
```kotlin
        val sealModifier =
            Modifier
                .rotate(state.rotationDegrees())
                .size(size)
                .clip(CircleShape)
                .background(bg)
                .let { m -> /* border-when oförändrat */ }
                .let { m -> if (onClick != null) m.clickable(onClick = onClick) else m }
```
Ytter-Column blir:
```kotlin
    Column(
        modifier =
            modifier
                .semantics(mergeDescendants = true) {
                    contentDescription = semanticsLabel
                    if (onClick != null) role = Role.Button
                },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
```
Resultat: `Unlocked` (-3°) lutar cirkeln men namn-`Text`:en (utanför Box, i Column) står rakt. `Locked`/`InProgress` (0°) oförändrade.

- [ ] **Step 2: Verifiera kompilering**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL. (Visuellt verifieras på enhet i Task 12.)

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/StampSeal.kt
git commit -m "fix(badges): rak etikett under upplåst märke (rotera bara stämpel-cirkeln)"
```

---

### Task 11: SceneBadges — StreakCounter → StampTrack

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneBadges.kt`

- [ ] **Step 1: Ersätt StreakCounter med StampTrack**

Ta bort importen `se.birdy.app.ui.onboarding.components.StreakCounter` + `counterTrigger`-Animatable. Lägg till `import se.birdy.app.ui.components.StampTrack`. I `Column`-innehållet, byt
```kotlin
            StreakCounter(target = 7, trigger = counterTrigger.value > 0.5f)
```
mot en statisk samlings-rad (matchar märkes-skärmens StampTrack):
```kotlin
            StampTrack(
                filled = 5,
                total = 12,
                modifier = Modifier.fillMaxWidth(),
            )
```
Behåll `StampSeal`-flippen (front locked → back unlocked). Ta bort `counterTrigger`-relaterad `LaunchedEffect`-logik om den blir oanvänd; behåll `flipDegrees`. Lägg till `import androidx.compose.foundation.layout.fillMaxWidth` om saknas.

> Verifiera `StampTrack`-signaturen i `ui/components/StampTrack.kt` (`filled: Int, total: Int, modifier: Modifier`). Justera anropet om den skiljer sig.

- [ ] **Step 2: Verifiera kompilering**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL. Kör `git grep -n "StreakCounter"` — om komponenten nu är helt oanvänd, lämna filen (ofarlig) eller notera för framtida städning.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneBadges.kt
git commit -m "feat(onboarding): scen 5 visar stämpel-rad (StampTrack) istället för streak-räknare"
```

---

### Task 12: Full verifiering + device-verify

**Files:** —

- [ ] **Step 1: Hela unit-sviten + statisk analys**

Run:
```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"; export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :shared:domain:jvmTest :composeApp:testDebugUnitTest ktlintCheck detekt
```
Expected: BUILD SUCCESSFUL, alla gröna. Fixa ev. ktlint/detekt (`./gradlew ktlintFormat`).

- [ ] **Step 2: Installera på enhet**

> [[feedback_personal_device_verify]]: SM-S918B är Albins dagliga telefon — be om "händerna borta" innan ADB-driving; verifiera via screencap; radera ev. privat innehåll. Debug-paketet är `se.birdy.android.debug`.

Run:
```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android.debug/se.birdy.android.MainActivity
```

- [ ] **Step 3: Verifiera på Märken-skärmen + onboarding**

Checklista (screenshots → `docs/superpowers/screenshots/v1.x-dp-d-badges/`):
- Två sektioner: "SKÅDAR-MILSTOLPAR" (överst) + "VANOR" (under).
- Rödlistad-märket syns som mål (ej "?"-Hidden).
- En upplåst stämpel (om någon): etiketten står **rak** (sne-fixen).
- In-progress-stämpel visar `current/target`.
- Premium-sektionen renderar 7 märken (eller teaser om ej premium).
- Onboarding (Settings → "Visa introduktion igen") scen 5: stämpel-rad, ingen streak-räknare.
- Appen kraschar inte vid uppgradering från tidigare version (föräldralösa unlocks ignoreras).

- [ ] **Step 4: Versionsbump + slutcommit**

I `androidApp/build.gradle.kts`: bumpa `versionCode` (+1) och `versionName` enligt rådande schema (kolla nuvarande värde först: `git grep -n "versionCode\|versionName" androidApp/build.gradle.kts`).
```bash
git add androidApp/build.gradle.kts docs/superpowers/screenshots/v1.x-dp-d-badges/
git commit -m "chore(release): DP D märken-omarbetning — versionCode bump + device-verify screenshots"
```

- [ ] **Step 5: Uppdatera program-spec + finishing-branch**

Uppdatera `docs/superpowers/specs/2026-05-29-v1-x-tester-feedback-response-design.md` (DP D-raden) med pekare till denna plan. Använd sedan `superpowers:finishing-a-development-branch` för merge/PR-beslut.

---

## Self-Review (utförd vid planskrivning)

**Spec-täckning:** Beslut 1–12 i specens §4 mappar till tasks: struktur A → T9; livslista → T5; familje-completion (distinkt) → T3+T5; sångare → T2/T3/T4/T5; bredd → T2/T3/T4/T5; rödlistat+avslöja → T2/T3/T4/T5/T8; säsong→gratis → T5/T6; audio→gratis → T5/T6; premium P1 → T6; N1 (ingen nav-ändring) → ingen task (medvetet); scen 5 → T11; sne-fix → T10. Migration/unlock (§10) → T8 (unlockedCount-filter) + graceful mapNotNull (befintligt). Strängar i båda locale (§9, §11) → T7. Inga schemaändringar (§2) — bekräftat, inga content/DB-filer rörs.

**Placeholder-scan:** Två tasks (T7 coverage-test, T8 VM-tester) hänvisar till "befintligt fixtur-mönster i filen" istället för full kod — eftersom de testfilernas setup måste läsas in-situ. Allt annat har konkret kod. Acceptabelt: implementatören läser filen som anges.

**Typ-konsekvens:** Regelnamn konsekventa över T2/T3/T4 (`ObservedInFamilyGroup`, `CountDistinctFamilies`, `CountDistinctOrders`, `ObservedRedListed`). YAML-typsträngar (`observed_in_family_group`, `count_distinct_families`, `count_distinct_orders`, `observed_red_listed`, `audio`/`breadth`/`redlisted`-kategorier) konsekventa mellan T4 (parser) och T5/T6 (yaml). Badge-id konsekventa mellan T5/T6 (yaml) och T7 (strings/map). `BadgeTier` konsekvent T1↔T9.
