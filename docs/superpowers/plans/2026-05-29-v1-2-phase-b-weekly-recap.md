# Weekly Recap ("Veckans uppslag") Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** En adaptiv "Veckans uppslag"-skärm + en enad söndags-push som firar aktiva veckor och uppmuntrar tysta, byggd på användarens egna observationer.

**Architecture:** Ren `WeeklyRecapBuilder` (commonMain) aggregerar veckans data från `observationRepository.observeAll()` + badge-unlocks + species-abundans, via Phase A:s `StreakHelpers` (ISO-vecka). En `RecapViewModel` matar `RecapScreen` (Field Journal-tema, Approach A). En `WeeklyRecapWorker` (Android WorkManager, söndag kväll) **ersätter** den fristående streak-risk-pushen. Deep-link `birdy://recap`. Allt gratis-tier.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, kotlinx-datetime, WorkManager, Coil 3 (AsyncImage), SQLDelight, compose-resources, kotlin.test + Turbine.

**Spec:** `docs/superpowers/specs/2026-05-29-v1-2-phase-b-weekly-recap-design.md`

**Bash gradle-prefix (Windows):**
```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
```

---

## File Structure

**Skapas:**
- `composeApp/src/commonMain/kotlin/se/birdy/app/recap/WeeklyRecap.kt` — modeller (`WeeklyRecapSummary`, `HeroFind`, `WeeklyRecap`)
- `composeApp/src/commonMain/kotlin/se/birdy/app/recap/WeeklyRecapBuilder.kt` — ren aggregator
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/recap/RecapUiState.kt`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/recap/RecapViewModel.kt`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/recap/RecapScreen.kt`
- `composeApp/src/androidMain/kotlin/se/birdy/app/notifications/workers/WeeklyRecapWorker.kt`
- `composeApp/src/commonTest/kotlin/se/birdy/domain/badge/CurrentWeeklyStreakTest.kt`
- `composeApp/src/commonTest/kotlin/se/birdy/app/recap/WeeklyRecapBuilderTest.kt`
- `composeApp/src/commonTest/kotlin/se/birdy/app/ui/recap/RecapViewModelTest.kt`

**Modifieras:**
- `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/StreakHelpers.kt` — `WeekKey.prev()` + `currentWeeklyStreak(...)`
- `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/UserPreferences.kt` (+ Android-impl) — `weeklyRecapPushEnabled`
- `shared/domain/src/commonMain/kotlin/se/birdy/domain/notification/NotificationScheduler.kt` — `scheduleWeeklyRecap()` / `cancelWeeklyRecap()`
- `composeApp/src/androidMain/kotlin/se/birdy/app/notifications/NotificationSchedulerImpl.kt`
- `composeApp/src/androidMain/kotlin/se/birdy/app/notifications/NotificationChannels.kt` — `WEEKLY_RECAP`-kanal
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt` — `WeeklyRecap`-route
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt` — route-registrering + deep-link "recap" + Lifelist-navigering
- `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt` — `weeklyRecapViewModel()` + `weeklyRecapBuilder`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/LifelistScreen.kt` — "Veckans uppslag"-entry-kort
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsScreen.kt` + `SettingsViewModel.kt` — recap-toggle ersätter streak-risk + DEV-trigger
- `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt` — schemalägg recap, avbryt streak-risk
- `composeApp/src/commonMain/composeResources/values/strings.xml` + `values-en/strings.xml`

---

## Task 1: `WeekKey.prev()` + `currentWeeklyStreak()` i StreakHelpers

**Files:**
- Modify: `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/StreakHelpers.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/domain/badge/CurrentWeeklyStreakTest.kt`

- [ ] **Step 1: Skriv failande test**

Skapa `composeApp/src/commonTest/kotlin/se/birdy/domain/badge/CurrentWeeklyStreakTest.kt`:

```kotlin
package se.birdy.domain.badge

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class CurrentWeeklyStreakTest {
    private val utc = TimeZone.UTC

    // 2026-05-25 är en måndag (ISO-vecka 22). Veckor bakåt: v21 mån = 2026-05-18, v20 = 2026-05-11.
    private fun monday(week: Int): Instant {
        val base = Instant.parse("2026-05-25T10:00:00Z") // v22 mån
        val weeksBack = 22 - week
        return base.minus(weeksBack * 7L * 24 * 3600, kotlinx.datetime.DateTimeUnit.SECOND)
    }

    private val now = Instant.parse("2026-05-27T10:00:00Z") // onsdag v22

    @Test
    fun `empty list yields zero`() {
        assertEquals(0, currentWeeklyStreak(emptyList(), utc, now))
    }

    @Test
    fun `obs this week and two prior weeks yields three`() {
        val obs = listOf(monday(22), monday(21), monday(20))
        assertEquals(3, currentWeeklyStreak(obs, utc, now))
    }

    @Test
    fun `gap breaks the streak`() {
        val obs = listOf(monday(22), monday(20)) // v21 saknas
        assertEquals(1, currentWeeklyStreak(obs, utc, now))
    }

    @Test
    fun `quiet current week but last two weeks active keeps streak alive`() {
        val obs = listOf(monday(21), monday(20)) // inget i v22
        assertEquals(2, currentWeeklyStreak(obs, utc, now))
    }

    @Test
    fun `no obs current or last week yields zero`() {
        val obs = listOf(monday(20), monday(19))
        assertEquals(0, currentWeeklyStreak(obs, utc, now))
    }

    @Test
    fun `prev crosses year boundary`() {
        val w1 = WeekKey(2026, 1)
        assertEquals(WeekKey(2025, isoWeeksInYear(2025)), w1.prev())
    }
}
```

- [ ] **Step 2: Kör testet, verifiera FAIL**

Run:
```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.domain.badge.CurrentWeeklyStreakTest"
```
Expected: FAIL — `currentWeeklyStreak` och `WeekKey.prev` finns inte (kompileringsfel). `isoWeeksInYear` är `internal` i samma modul men testet ligger i composeApp; gör den `public` i Step 3 om kompileringen klagar (se nedan).

- [ ] **Step 3: Implementera i StreakHelpers.kt**

I `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/StreakHelpers.kt`: gör `isoWeeksInYear` `public` (ta bort `internal`/`private` om satt) och lägg till `prev()` i `WeekKey` + ny toppnivå-funktion:

```kotlin
// Inuti data class WeekKey, bredvid next():
fun prev(): WeekKey =
    if (isoWeek > 1) {
        WeekKey(isoYear, isoWeek - 1)
    } else {
        WeekKey(isoYear - 1, isoWeeksInYear(isoYear - 1))
    }
```

```kotlin
/**
 * Nuvarande sammanhängande veckostreak: antal ISO-veckor i rad (bakåt) med minst en observation,
 * räknat från innevarande vecka om den har obs, annars från förra veckan om DEN har obs
 * (streaken lever men är "i fara"), annars 0.
 */
fun currentWeeklyStreak(
    instants: List<Instant>,
    zone: TimeZone,
    now: Instant,
): Int {
    if (instants.isEmpty()) return 0
    val weeks = instants.map { weekKey(it, zone) }.toSet()
    val current = weekKey(now, zone)
    var anchor =
        when {
            weeks.contains(current) -> current
            weeks.contains(current.prev()) -> current.prev()
            else -> return 0
        }
    var count = 0
    while (weeks.contains(anchor)) {
        count++
        anchor = anchor.prev()
    }
    return count
}
```

Lägg till import `import kotlinx.datetime.Instant` och `import kotlinx.datetime.TimeZone` om de inte redan finns.

- [ ] **Step 4: Kör testet, verifiera PASS**

Run:
```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.domain.badge.CurrentWeeklyStreakTest"
```
Expected: PASS (6 tester gröna).

- [ ] **Step 5: Commit**

```bash
git add shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/StreakHelpers.kt composeApp/src/commonTest/kotlin/se/birdy/domain/badge/CurrentWeeklyStreakTest.kt
git commit -m "feat(streak): WeekKey.prev() + currentWeeklyStreak() för recap"
```

---

## Task 2: WeeklyRecap-modeller

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/recap/WeeklyRecap.kt`

- [ ] **Step 1: Skapa modellfilen**

```kotlin
package se.birdy.app.recap

import se.birdy.domain.badge.WeekKey

/** Räknbar veckosummering (push + skärm). Beräknas utan species-data. */
data class WeeklyRecapSummary(
    val week: WeekKey,
    val observationCount: Int,
    val newSpeciesCount: Int,
    val newBadgeIds: List<String>,
    val weeklyStreak: Int,
    val deltaVsLastWeek: Int,
    val streakAtRisk: Boolean,
) {
    val isQuiet: Boolean get() = observationCount == 0
}

/** Veckans fynd — hjälte i PlateFrame. */
data class HeroFind(
    val observationId: String,
    val speciesId: String?,
    val photoPath: String,
    val heroImagePath: String?,
    val isNewSpecies: Boolean,
)

/** Full recap för skärmen. */
data class WeeklyRecap(
    val summary: WeeklyRecapSummary,
    val hero: HeroFind?,
)
```

- [ ] **Step 2: Verifiera kompilering**

Run:
```bash
./gradlew :composeApp:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/recap/WeeklyRecap.kt
git commit -m "feat(recap): WeeklyRecap/Summary/HeroFind-modeller"
```

---

## Task 3: `WeeklyRecapBuilder.summarize()`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/recap/WeeklyRecapBuilder.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/recap/WeeklyRecapBuilderTest.kt`

- [ ] **Step 1: Skriv failande test för summarize**

Skapa `composeApp/src/commonTest/kotlin/se/birdy/app/recap/WeeklyRecapBuilderTest.kt`:

```kotlin
package se.birdy.app.recap

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeeklyRecapBuilderTest {
    private val utc = TimeZone.UTC
    private val builder = WeeklyRecapBuilder(utc)
    private val now = Instant.parse("2026-05-27T10:00:00Z") // onsdag v22

    private fun daysAgo(d: Int) = now.minus(d.toLong() * 24 * 3600, DateTimeUnit.SECOND)

    private fun obs(
        id: String,
        speciesId: String?,
        at: Instant,
        photoPath: String = "/p/$id.jpg",
        source: ObservationSource = ObservationSource.Photo,
        audioPath: String? = null,
    ) = Observation(
        id = id,
        speciesId = speciesId,
        capturedAt = at,
        savedAt = at,
        photoPath = photoPath,
        note = "",
        confidence = 0.9f,
        latitude = null,
        longitude = null,
        locationLabel = null,
        stampNumber = 0,
        audioPath = audioPath,
        sourceType = source,
    )

    @Test
    fun `quiet week has zero count and isQuiet`() {
        val s = builder.summarize(emptyList(), emptyList(), now)
        assertEquals(0, s.observationCount)
        assertTrue(s.isQuiet)
        assertFalse(s.streakAtRisk)
    }

    @Test
    fun `counts only current week observations`() {
        val list = listOf(
            obs("a", "Q1", daysAgo(1)), // denna vecka
            obs("b", "Q2", daysAgo(2)), // denna vecka
            obs("c", "Q3", daysAgo(9)), // förra veckan
        )
        val s = builder.summarize(list, emptyList(), now)
        assertEquals(2, s.observationCount)
        assertEquals(1, s.deltaVsLastWeek) // 2 - 1
    }

    @Test
    fun `new species counted only when first sighting falls this week`() {
        val list = listOf(
            obs("old", "Q1", daysAgo(30)), // Q1 sågs först förra månaden → ej ny
            obs("now1", "Q1", daysAgo(1)),
            obs("now2", "Q2", daysAgo(1)), // Q2 helt ny denna vecka
        )
        val s = builder.summarize(list, emptyList(), now)
        assertEquals(1, s.newSpeciesCount)
    }

    @Test
    fun `badges unlocked this week are listed`() {
        val unlocks = listOf(
            BadgeUnlock("premium_early_pilgrim", daysAgo(1)),
            BadgeUnlock("weekly_5", daysAgo(20)), // förra månaden
        )
        val s = builder.summarize(listOf(obs("a", "Q1", daysAgo(1))), unlocks, now)
        assertEquals(listOf("premium_early_pilgrim"), s.newBadgeIds)
    }

    @Test
    fun `streak at risk when quiet week but prior weeks active`() {
        val list = listOf(
            obs("w21", "Q1", daysAgo(8)),  // förra veckan
            obs("w20", "Q1", daysAgo(15)), // veckan innan
        )
        val s = builder.summarize(list, emptyList(), now)
        assertEquals(0, s.observationCount)
        assertEquals(2, s.weeklyStreak)
        assertTrue(s.streakAtRisk)
    }
}
```

- [ ] **Step 2: Kör testet, verifiera FAIL**

Run:
```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.recap.WeeklyRecapBuilderTest"
```
Expected: FAIL — `WeeklyRecapBuilder` finns inte.

- [ ] **Step 3: Implementera WeeklyRecapBuilder.summarize**

Skapa `composeApp/src/commonMain/kotlin/se/birdy/app/recap/WeeklyRecapBuilder.kt`:

```kotlin
package se.birdy.app.recap

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.domain.badge.currentWeeklyStreak
import se.birdy.domain.badge.weekKey
import se.birdy.domain.observation.Observation

class WeeklyRecapBuilder(
    private val zone: TimeZone,
) {
    fun summarize(
        observations: List<Observation>,
        unlocks: List<BadgeUnlock>,
        now: Instant,
    ): WeeklyRecapSummary {
        val current = weekKey(now, zone)
        val prev = current.prev()
        val thisWeekCount = observations.count { weekKey(it.capturedAt, zone) == current }
        val lastWeekCount = observations.count { weekKey(it.capturedAt, zone) == prev }

        val firstByQid =
            observations.asSequence()
                .filter { it.speciesId != null }
                .groupBy { it.speciesId!! }
                .mapValues { (_, obs) -> obs.minOf { it.capturedAt } }
        val newSpeciesCount = firstByQid.count { weekKey(it.value, zone) == current }

        val streak = currentWeeklyStreak(observations.map { it.capturedAt }, zone, now)
        val streakAtRisk = thisWeekCount == 0 && streak >= 2

        return WeeklyRecapSummary(
            week = current,
            observationCount = thisWeekCount,
            newSpeciesCount = newSpeciesCount,
            newBadgeIds = unlocks.filter { weekKey(it.unlockedAt, zone) == current }.map { it.badgeId },
            weeklyStreak = streak,
            deltaVsLastWeek = thisWeekCount - lastWeekCount,
            streakAtRisk = streakAtRisk,
        )
    }
}
```

- [ ] **Step 4: Kör testet, verifiera PASS**

Run:
```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.recap.WeeklyRecapBuilderTest"
```
Expected: PASS (5 tester gröna).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/recap/WeeklyRecapBuilder.kt composeApp/src/commonTest/kotlin/se/birdy/app/recap/WeeklyRecapBuilderTest.kt
git commit -m "feat(recap): WeeklyRecapBuilder.summarize() + tester"
```

---

## Task 4: `WeeklyRecapBuilder.selectHero()` + `build()`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/recap/WeeklyRecapBuilder.kt`
- Modify: `composeApp/src/commonTest/kotlin/se/birdy/app/recap/WeeklyRecapBuilderTest.kt`

> **OBS:** Bekräfta `SpeciesId`-konstruktorn och `Species.abundance`-typen genom att öppna `shared/content/src/commonMain/kotlin/se/birdy/content/Species.kt` och `Abundance.kt`. `Abundance.ordinal`: ALLMÄN=0 … SÄLLSYNT=3 (rarast = högst ordinal). `Species.heroImagePath: String?` finns.

- [ ] **Step 1: Skriv failande test för selectHero**

Lägg till i `WeeklyRecapBuilderTest.kt` (import överst: `import se.birdy.content.Abundance`, `import se.birdy.content.Species`, `import se.birdy.content.SpeciesId`):

```kotlin
    private fun species(qid: String, abundance: Abundance, hero: String? = "$qid/hero.webp"): Species =
        Species(
            id = SpeciesId(qid),
            scientificName = "Sci $qid",
            taxonomy = se.birdy.content.SpeciesTaxonomy(family = "fam", order = "ord"),
            name = "Namn $qid",
            abundance = abundance,
            iucnStatus = "LC",
            regions = listOf("SE"),
            season = emptyMap(),
            images = emptyList(),
            heroImagePath = hero,
            // Fyll ev. övriga obligatoriska fält enligt Species.kt med tomma defaults.
        )

    @Test
    fun `hero prefers new lifelist species`() {
        val list = listOf(
            obs("old", "Q1", daysAgo(40)),  // Q1 ej ny
            obs("c1", "Q1", daysAgo(1)),
            obs("c2", "Q2", daysAgo(2)),     // Q2 ny denna vecka
        )
        val sp = mapOf(SpeciesId("Q1") to species("Q1", Abundance.SÄLLSYNT), SpeciesId("Q2") to species("Q2", Abundance.ALLMÄN))
        val hero = builder.selectHero(list, sp, now)!!
        assertEquals("c2", hero.observationId)
        assertTrue(hero.isNewSpecies)
    }

    @Test
    fun `hero falls back to rarest when no new species`() {
        val list = listOf(
            obs("a", "Q1", daysAgo(40)), obs("a2", "Q1", daysAgo(1)), // Q1 ej ny, allmän
            obs("b", "Q2", daysAgo(40)), obs("b2", "Q2", daysAgo(2)), // Q2 ej ny, sällsynt
        )
        val sp = mapOf(SpeciesId("Q1") to species("Q1", Abundance.ALLMÄN), SpeciesId("Q2") to species("Q2", Abundance.SÄLLSYNT))
        val hero = builder.selectHero(list, sp, now)!!
        assertEquals("b2", hero.observationId) // Q2 sällsynt
        assertFalse(hero.isNewSpecies)
    }

    @Test
    fun `audio-only hero uses species heroImagePath fallback`() {
        val list = listOf(obs("au", "Q2", daysAgo(40)), obs("au2", "Q2", daysAgo(1), photoPath = "", source = ObservationSource.Audio, audioPath = "/a/au2.ogg"))
        val sp = mapOf(SpeciesId("Q2") to species("Q2", Abundance.SÄLLSYNT, hero = "Q2/hero.webp"))
        val hero = builder.selectHero(list, sp, now)!!
        assertEquals("", hero.photoPath)
        assertEquals("Q2/hero.webp", hero.heroImagePath)
    }

    @Test
    fun `no hero on quiet week`() {
        assertEquals(null, builder.selectHero(emptyList(), emptyMap(), now))
    }
```

- [ ] **Step 2: Kör testet, verifiera FAIL**

Run:
```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.recap.WeeklyRecapBuilderTest"
```
Expected: FAIL — `selectHero` finns inte.

- [ ] **Step 3: Implementera selectHero + build**

Lägg till i `WeeklyRecapBuilder.kt` (nya imports: `import se.birdy.content.Species`, `import se.birdy.content.SpeciesId`):

```kotlin
    fun selectHero(
        observations: List<Observation>,
        speciesByQid: Map<SpeciesId, Species>,
        now: Instant,
    ): HeroFind? {
        val current = weekKey(now, zone)
        val thisWeek = observations.filter { weekKey(it.capturedAt, zone) == current }
        if (thisWeek.isEmpty()) return null

        val firstByQid =
            observations.asSequence()
                .filter { it.speciesId != null }
                .groupBy { it.speciesId!! }
                .mapValues { (_, obs) -> obs.minOf { it.capturedAt } }

        fun isNew(o: Observation): Boolean =
            o.speciesId != null && firstByQid[o.speciesId]?.let { weekKey(it, zone) == current } == true

        val chosen =
            thisWeek.filter { isNew(it) }.maxByOrNull { it.capturedAt }
                ?: thisWeek
                    .filter { it.speciesId != null }
                    .maxWithOrNull(
                        compareBy<Observation> {
                            speciesByQid[SpeciesId(it.speciesId!!)]?.abundance?.ordinal ?: -1
                        }.thenBy { it.capturedAt },
                    )
                ?: thisWeek.maxByOrNull { it.capturedAt }
                ?: return null

        val species = chosen.speciesId?.let { speciesByQid[SpeciesId(it)] }
        return HeroFind(
            observationId = chosen.id,
            speciesId = chosen.speciesId,
            photoPath = chosen.photoPath,
            heroImagePath = species?.heroImagePath,
            isNewSpecies = isNew(chosen),
        )
    }

    fun build(
        observations: List<Observation>,
        speciesByQid: Map<SpeciesId, Species>,
        unlocks: List<BadgeUnlock>,
        now: Instant,
    ): WeeklyRecap =
        WeeklyRecap(
            summary = summarize(observations, unlocks, now),
            hero = selectHero(observations, speciesByQid, now),
        )
```

- [ ] **Step 4: Kör testet, verifiera PASS**

Run:
```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.recap.WeeklyRecapBuilderTest"
```
Expected: PASS (9 tester totalt). Om `Species(...)`-konstruktorn klagar på saknade fält — fyll i dem enligt `Species.kt` med tomma/neutrala värden i `species()`-helpern.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/recap/WeeklyRecapBuilder.kt composeApp/src/commonTest/kotlin/se/birdy/app/recap/WeeklyRecapBuilderTest.kt
git commit -m "feat(recap): selectHero (ny art > ovanligast > senaste) + build()"
```

---

## Task 5: Strängar (sv + en)

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`

- [ ] **Step 1: Lägg till svenska strängar**

I `values/strings.xml` (använd raw `'`/`’` inte `\'`; inga `%%`):

```xml
<string name="recap_eyebrow_fmt">Fältrapport · Vecka %1$s</string>
<string name="recap_headline_active">En vecka i *fält*.</string>
<string name="recap_headline_quiet">En *lugn* vecka.</string>
<string name="recap_plate_caption_fmt">Pl. — %1$s</string>
<string name="recap_summary_active_fmt">%1$s fynd den här veckan.</string>
<string name="recap_summary_active_new">%1$s fynd den här veckan — och en ny bekantskap.</string>
<string name="recap_stats_fmt">%1$s fynd · %2$s ny art · %3$s v streak</string>
<string name="recap_new_badge_fmt">Ny stämpel: %1$s</string>
<string name="recap_delta_fmt">vs förra veckan: %1$s fynd</string>
<string name="recap_empty_plate">Sidan är tom — inga fynd ännu.</string>
<string name="recap_quiet_encouragement">Ingen brådska. Tio minuter i parken räcker för veckans första rad.</string>
<string name="recap_streak_label">Din streak</string>
<string name="recap_streak_nudge_fmt">%1$s veckor i rad — håll den vid liv innan söndagen är slut. En talgoxe i parken räcker.</string>
<string name="recap_cta_open_camera">Öppna kameran →</string>
<string name="recap_lifelist_entry_title">Veckans uppslag</string>
<string name="recap_lifelist_entry_active_fmt">Vecka %1$s · %2$s fynd</string>
<string name="recap_lifelist_entry_quiet">En lugn vecka hittills</string>
<string name="recap_a11y_fmt">Veckans uppslag, vecka %1$s</string>
<string name="notification_recap_active_title">Veckan i fält</string>
<string name="notification_recap_active_body_fmt">%1$s fynd, %2$s ny art — se veckans uppslag.</string>
<string name="notification_recap_streak_title">Bara kvällen kvar</string>
<string name="notification_recap_streak_body">En talgoxe i parken räcker — fortsätt din streak.</string>
<string name="settings_toggle_weekly_recap">Veckans recap — söndag kväll</string>
<string name="settings_dev_trigger_recap">DEV: Trigga veckans recap-push</string>
```

- [ ] **Step 2: Lägg till engelska strängar**

I `values-en/strings.xml`:

```xml
<string name="recap_eyebrow_fmt">Field report · Week %1$s</string>
<string name="recap_headline_active">A week in the *field*.</string>
<string name="recap_headline_quiet">A *quiet* week.</string>
<string name="recap_plate_caption_fmt">Pl. — %1$s</string>
<string name="recap_summary_active_fmt">%1$s sightings this week.</string>
<string name="recap_summary_active_new">%1$s sightings this week — and a new acquaintance.</string>
<string name="recap_stats_fmt">%1$s sightings · %2$s new · %3$s wk streak</string>
<string name="recap_new_badge_fmt">New stamp: %1$s</string>
<string name="recap_delta_fmt">vs last week: %1$s sightings</string>
<string name="recap_empty_plate">The page is blank — no sightings yet.</string>
<string name="recap_quiet_encouragement">No rush. Ten minutes in the park is enough for the week’s first line.</string>
<string name="recap_streak_label">Your streak</string>
<string name="recap_streak_nudge_fmt">%1$s weeks running — keep it alive before Sunday ends. A great tit in the park is enough.</string>
<string name="recap_cta_open_camera">Open the camera →</string>
<string name="recap_lifelist_entry_title">This week’s page</string>
<string name="recap_lifelist_entry_active_fmt">Week %1$s · %2$s sightings</string>
<string name="recap_lifelist_entry_quiet">A quiet week so far</string>
<string name="recap_a11y_fmt">This week’s page, week %1$s</string>
<string name="notification_recap_active_title">Your week in the field</string>
<string name="notification_recap_active_body_fmt">%1$s sightings, %2$s new — see this week’s page.</string>
<string name="notification_recap_streak_title">Only the evening left</string>
<string name="notification_recap_streak_body">A great tit in the park is enough — keep your streak going.</string>
<string name="settings_toggle_weekly_recap">Weekly recap — Sunday evening</string>
<string name="settings_dev_trigger_recap">DEV: Trigger weekly recap push</string>
```

- [ ] **Step 3: Verifiera generering**

Run:
```bash
./gradlew :composeApp:generateComposeResClass
```
Expected: BUILD SUCCESSFUL (genererar `Res.string.recap_*`).

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(recap): strängar sv+en"
```

---

## Task 6: `weeklyRecapPushEnabled` i UserPreferences

**Files:**
- Modify: `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/UserPreferences.kt`
- Modify: Android-impl av UserPreferences (sök `class .*UserPreferences` i `shared/datastore/src/androidMain` eller `commonMain` — samma fil som implementerar `streakRiskPushEnabled`)

> Öppna `streakRiskPushEnabled`-implementationen och spegla exakt (DataStore `booleanPreferencesKey`, default `true`).

- [ ] **Step 1: Lägg till i interface**

I `UserPreferences.kt`, bredvid `streakRiskPushEnabled`:

```kotlin
val weeklyRecapPushEnabled: Flow<Boolean>
suspend fun setWeeklyRecapPushEnabled(value: Boolean)
```

- [ ] **Step 2: Implementera i DataStore-klassen**

Spegla `streakRiskPushEnabled`-paret (ny nyckel `weekly_recap_push_enabled`, default `true`):

```kotlin
private val weeklyRecapPushEnabledKey = booleanPreferencesKey("weekly_recap_push_enabled")

override val weeklyRecapPushEnabled: Flow<Boolean> =
    dataStore.data.map { it[weeklyRecapPushEnabledKey] ?: true }

override suspend fun setWeeklyRecapPushEnabled(value: Boolean) {
    dataStore.edit { it[weeklyRecapPushEnabledKey] = value }
}
```

Uppdatera ev. in-memory/fake-implementationer av `UserPreferences` (sök `: UserPreferences` i testkällor) så de implementerar de nya medlemmarna (returnera `flowOf(true)` / no-op).

- [ ] **Step 3: Verifiera kompilering (inkl. tester)**

Run:
```bash
./gradlew :shared:datastore:compileKotlinJvm :composeApp:compileDebugKotlin :composeApp:compileDebugUnitTestKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add shared/datastore/ composeApp/
git commit -m "feat(prefs): weeklyRecapPushEnabled (default på)"
```

---

## Task 7: NotificationScheduler — schedule/cancel WeeklyRecap

**Files:**
- Modify: `shared/domain/src/commonMain/kotlin/se/birdy/domain/notification/NotificationScheduler.kt`
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/notifications/NotificationSchedulerImpl.kt`
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/notifications/NotificationChannels.kt`

- [ ] **Step 1: Utöka interface**

I `NotificationScheduler.kt`:

```kotlin
fun scheduleWeeklyRecap()
fun cancelWeeklyRecap()
```

- [ ] **Step 2: Lägg till kanal**

I `NotificationChannels.kt`: lägg till konstant + skapande (spegla STREAK_RISK):

```kotlin
const val WEEKLY_RECAP = "weekly_recap"
```
Inuti `ensureCreated`:
```kotlin
if (mgr.getNotificationChannel(WEEKLY_RECAP) == null) {
    mgr.createNotificationChannel(
        NotificationChannel(WEEKLY_RECAP, "Veckans recap", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Sunday-evening recap of your week." },
    )
}
```

- [ ] **Step 3: Implementera i NotificationSchedulerImpl**

Lägg till konstant + metoder (spegla `scheduleStreakRiskCheck`, samma 7-dagars-period + söndag 18:00):

```kotlin
// i companion object:
const val UNIQUE_WEEKLY_RECAP = "birdy_weekly_recap_worker"

override fun scheduleWeeklyRecap() {
    val request =
        PeriodicWorkRequestBuilder<WeeklyRecapWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(millisUntilNextSunday(hour = 18, minute = 0), TimeUnit.MILLISECONDS)
            .build()
    workManager.enqueueUniquePeriodicWork(UNIQUE_WEEKLY_RECAP, ExistingPeriodicWorkPolicy.KEEP, request)
}

override fun cancelWeeklyRecap() {
    workManager.cancelUniqueWork(UNIQUE_WEEKLY_RECAP)
}
```

(Behåll `scheduleStreakRiskCheck`/`cancelStreakRiskCheck` i interfacet/impl — de anropas inte längre vid start men `cancelStreakRiskCheck()` används för migration i Task 11. `import` för `WeeklyRecapWorker` läggs till när Task 8 skapat klassen — bygg först efter Task 8.)

- [ ] **Step 4: Commit (kompileras efter Task 8)**

```bash
git add shared/domain/src/commonMain/kotlin/se/birdy/domain/notification/NotificationScheduler.kt composeApp/src/androidMain/kotlin/se/birdy/app/notifications/NotificationChannels.kt composeApp/src/androidMain/kotlin/se/birdy/app/notifications/NotificationSchedulerImpl.kt
git commit -m "feat(notif): WEEKLY_RECAP-kanal + scheduleWeeklyRecap()"
```

---

## Task 8: `WeeklyRecapWorker`

**Files:**
- Create: `composeApp/src/androidMain/kotlin/se/birdy/app/notifications/workers/WeeklyRecapWorker.kt`

> Mall: `StreakRiskWorker.kt` + `DailyBirdWorker.kt`. AppGraph-fält som behövs: `observationRepository`, `badgeRepository` (`observeUnlocks()`), `userPreferences`, `repository` (species), `defaultLocale`, `timeZone`. Bekräfta `graph.repository.allByQid(locale)`-signaturen i AppGraph.

- [ ] **Step 1: Skapa workern**

```kotlin
package se.birdy.app.notifications.workers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.notification_recap_active_body_fmt
import birdy_bird_scanner.composeapp.generated.resources.notification_recap_active_title
import birdy_bird_scanner.composeapp.generated.resources.notification_recap_streak_body
import birdy_bird_scanner.composeapp.generated.resources.notification_recap_streak_title
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import org.jetbrains.compose.resources.getString
import se.birdy.app.R
import se.birdy.app.di.AndroidAppGraphHolder
import se.birdy.app.notifications.NotificationChannels
import se.birdy.app.recap.WeeklyRecapBuilder

class WeeklyRecapWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = AndroidAppGraphHolder.current ?: return Result.success()
        val forceForDev = inputData.getBoolean(KEY_FORCE_FOR_DEV, false)
        if (!forceForDev && !graph.userPreferences.weeklyRecapPushEnabled.first()) return Result.success()

        val observations = graph.observationRepository.observeAll().first()
        val unlocks = graph.badgeRepository.observeUnlocks().first()
        val builder = WeeklyRecapBuilder(graph.timeZone)
        val summary = builder.summarize(observations, unlocks, Clock.System.now())

        val (title, body) =
            when {
                !summary.isQuiet ->
                    getString(Res.string.notification_recap_active_title) to
                        getString(
                            Res.string.notification_recap_active_body_fmt,
                            summary.observationCount.toString(),
                            summary.newSpeciesCount.toString(),
                        )
                summary.streakAtRisk ->
                    getString(Res.string.notification_recap_streak_title) to
                        getString(Res.string.notification_recap_streak_body)
                // Tyst vecka utan streak att rädda → ingen push (spec §3.6)
                else -> return Result.success()
            }

        NotificationChannels.ensureCreated(applicationContext)

        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse("birdy://recap"))
                .setPackage(applicationContext.packageName)
        val pi =
            PendingIntent.getActivity(
                applicationContext,
                NOTIF_ID_WEEKLY_RECAP,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notif =
            NotificationCompat
                .Builder(applicationContext, NotificationChannels.WEEKLY_RECAP)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

        if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID_WEEKLY_RECAP, notif)
        }
        return Result.success()
    }

    companion object {
        const val NOTIF_ID_WEEKLY_RECAP = 1003
        const val KEY_FORCE_FOR_DEV = "force_for_dev"
    }
}
```

> Bekräfta `R`-importen (`se.birdy.app.R` vs `se.birdy.android.R`) genom att titta i `StreakRiskWorker.kt`s imports och spegla exakt.

- [ ] **Step 2: Verifiera kompilering (Android)**

Run:
```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```
Expected: BUILD SUCCESSFUL (nu kompilerar även Task 7:s `WeeklyRecapWorker`-referens).

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain/kotlin/se/birdy/app/notifications/workers/WeeklyRecapWorker.kt
git commit -m "feat(recap): WeeklyRecapWorker (adaptiv copy, birdy://recap, ingen tom-push)"
```

---

## Task 9: `RecapUiState` + `RecapViewModel`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/recap/RecapUiState.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/recap/RecapViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/recap/RecapViewModelTest.kt`

> Mall: `BadgesViewModel`. Species-namnsuppslag för caption: `graph.repository.allByQid(locale)` ger `Map<SpeciesId, Species>` (samma som BadgesViewModel `speciesByQid`).

- [ ] **Step 1: Skapa UiState**

```kotlin
package se.birdy.app.ui.recap

import se.birdy.app.recap.WeeklyRecap

sealed interface RecapUiState {
    data object Loading : RecapUiState
    data class Loaded(
        val recap: WeeklyRecap,
        val heroSpeciesName: String?,
        val newBadgeNames: List<String>,
    ) : RecapUiState
}
```

- [ ] **Step 2: Skriv failande VM-test**

Skapa `composeApp/src/commonTest/kotlin/se/birdy/app/ui/recap/RecapViewModelTest.kt`:

```kotlin
package se.birdy.app.ui.recap

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeObservationRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecapViewModelTest {
    @BeforeTest fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun resetMain() = Dispatchers.resetMain()

    private fun vm(obs: FakeObservationRepository, badges: FakeBadgeRepository) =
        RecapViewModel(
            obsRepo = obs,
            badgeRepo = badges,
            speciesByQid = { emptyMap() },
            badgeNameFor = { it },
            zone = TimeZone.UTC,
            now = { Clock.System.now() },
        )

    @Test
    fun `quiet week emits Loaded with quiet summary`() =
        runTest {
            val vm = vm(FakeObservationRepository(), FakeBadgeRepository())
            vm.state.test {
                var item = awaitItem()
                while (item is RecapUiState.Loading) item = awaitItem()
                val loaded = item as RecapUiState.Loaded
                assertTrue(loaded.recap.summary.isQuiet)
            }
        }
}
```

- [ ] **Step 3: Kör test, verifiera FAIL**

Run:
```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.recap.RecapViewModelTest"
```
Expected: FAIL — `RecapViewModel` finns inte.

- [ ] **Step 4: Implementera RecapViewModel**

```kotlin
package se.birdy.app.ui.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.recap.WeeklyRecapBuilder
import se.birdy.content.Species
import se.birdy.content.SpeciesId
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository
import se.birdy.domain.badge.BadgeRepository

class RecapViewModel(
    private val obsRepo: ObservationRepository,
    private val badgeRepo: BadgeRepository,
    private val speciesByQid: suspend () -> Map<SpeciesId, Species>,
    private val badgeNameFor: (String) -> String,
    private val zone: TimeZone,
    private val now: () -> Instant = { Clock.System.now() },
) : ViewModel() {
    private val builder = WeeklyRecapBuilder(zone)

    val state: StateFlow<RecapUiState> =
        combine(obsRepo.observeAll(), badgeRepo.observeUnlocks()) { obs, unlocks ->
            buildState(obs, unlocks)
        }.onStart { /* Loading emitteras av stateIn-initial */ }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecapUiState.Loading)

    private suspend fun buildState(
        obs: List<Observation>,
        unlocks: List<BadgeUnlock>,
    ): RecapUiState {
        val species = speciesByQid()
        val recap = builder.build(obs, species, unlocks, now())
        val heroName = recap.hero?.speciesId?.let { species[SpeciesId(it)]?.name }
        val badgeNames = recap.summary.newBadgeIds.map(badgeNameFor)
        return RecapUiState.Loaded(recap, heroName, badgeNames)
    }
}
```

> `combine` med en `suspend` transform: om din coroutines-version inte tillåter suspend-lambda direkt i `combine`, byt till `.map`-kedja eller hämta `species` en gång i en `flow {}`. Bekräfta mot hur `BadgesViewModel` gör (den anropar `speciesByQid()` inuti combine-transformen — spegla exakt).

- [ ] **Step 5: Kör test, verifiera PASS**

Run:
```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.recap.RecapViewModelTest"
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/recap/ composeApp/src/commonTest/kotlin/se/birdy/app/ui/recap/
git commit -m "feat(recap): RecapViewModel + UiState + test"
```

---

## Task 10: `RecapScreen` (aktiv + tyst läge)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/recap/RecapScreen.kt`

> Komponenter: `JournalHeadline(text, fontSize, plainColor, accentColor)` (parsar `*accent*`), `OrnamentRule()`, `Modifier.paperBackground()`, `AsyncImage`. Foto: `AsyncImage(model = "file://${hero.photoPath}")`; audio-fallback: `AsyncImage(model = speciesImageUri(hero.heroImagePath))`. Färger: `se.birdy.app.ui.theme.AccentCopper`, `MarginaliaInk`, `PaperEdge`. Fonts: `rememberDmSerifDisplay()`, `rememberCaveat()`.

- [ ] **Step 1: Skapa skärmen**

```kotlin
package se.birdy.app.ui.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.recap_cta_open_camera
import birdy_bird_scanner.composeapp.generated.resources.recap_delta_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_empty_plate
import birdy_bird_scanner.composeapp.generated.resources.recap_eyebrow_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_headline_active
import birdy_bird_scanner.composeapp.generated.resources.recap_headline_quiet
import birdy_bird_scanner.composeapp.generated.resources.recap_new_badge_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_quiet_encouragement
import birdy_bird_scanner.composeapp.generated.resources.recap_stats_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_streak_label
import birdy_bird_scanner.composeapp.generated.resources.recap_streak_nudge_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_summary_active_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_summary_active_new
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalHeadline
import se.birdy.app.ui.components.OrnamentRule
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.paperBackground
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.util.speciesImageUri

@Composable
fun RecapScreen(
    viewModel: RecapViewModel,
    onOpenCamera: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize().paperBackground()) {
        when (val s = state) {
            RecapUiState.Loading -> Unit
            is RecapUiState.Loaded -> RecapContent(s, onOpenCamera)
        }
    }
}

@Composable
private fun RecapContent(
    state: RecapUiState.Loaded,
    onOpenCamera: () -> Unit,
) {
    val summary = state.recap.recapSummary()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Text(
            stringResource(Res.string.recap_eyebrow_fmt, summary.week.isoWeek.toString()),
            color = AccentCopper,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
        )
        Box(modifier = Modifier.height(8.dp))
        JournalHeadline(
            text =
                stringResource(
                    if (summary.isQuiet) Res.string.recap_headline_quiet else Res.string.recap_headline_active,
                ),
            fontSize = 32.sp,
        )
        Box(modifier = Modifier.height(14.dp))

        if (summary.isQuiet) {
            QuietBody(state, onOpenCamera)
        } else {
            ActiveBody(state)
        }
    }
}

@Composable
private fun ActiveBody(state: RecapUiState.Loaded) {
    val s = state.recap.recapSummary()
    val hero = state.recap.hero
    val caveat = rememberCaveat()
    if (hero != null) {
        val model = if (hero.photoPath.isNotBlank()) "file://${hero.photoPath}" else speciesImageUri(hero.heroImagePath ?: "")
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(6.dp)),
        )
        state.heroSpeciesName?.let {
            Text(it, color = MarginaliaInk, fontFamily = caveat, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
    Box(modifier = Modifier.height(12.dp))
    Text(
        if (s.newSpeciesCount > 0) {
            stringResource(Res.string.recap_summary_active_new, s.observationCount.toString())
        } else {
            stringResource(Res.string.recap_summary_active_fmt, s.observationCount.toString())
        },
        color = MarginaliaInk,
        fontFamily = caveat,
        fontSize = 21.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Box(modifier = Modifier.height(8.dp))
    OrnamentRule()
    Box(modifier = Modifier.height(8.dp))
    Text(
        stringResource(
            Res.string.recap_stats_fmt,
            s.observationCount.toString(),
            s.newSpeciesCount.toString(),
            s.weeklyStreak.toString(),
        ),
        color = AccentCopper,
        fontFamily = caveat,
        fontSize = 20.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    state.newBadgeNames.forEach { name ->
        Text(
            stringResource(Res.string.recap_new_badge_fmt, name),
            color = MarginaliaInk,
            fontFamily = caveat,
            fontSize = 18.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    Text(
        stringResource(Res.string.recap_delta_fmt, formatSigned(s.deltaVsLastWeek)),
        color = MarginaliaInk,
        fontFamily = caveat,
        fontSize = 16.sp,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun QuietBody(
    state: RecapUiState.Loaded,
    onOpenCamera: () -> Unit,
) {
    val s = state.recap.recapSummary()
    val caveat = rememberCaveat()
    Text(
        stringResource(Res.string.recap_empty_plate),
        color = MarginaliaInk,
        fontFamily = caveat,
        fontSize = 18.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    )
    Text(
        stringResource(Res.string.recap_quiet_encouragement),
        color = MarginaliaInk,
        fontFamily = caveat,
        fontSize = 21.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Box(modifier = Modifier.height(8.dp))
    OrnamentRule()
    if (s.streakAtRisk) {
        Box(modifier = Modifier.height(12.dp))
        Text(
            stringResource(Res.string.recap_streak_label),
            color = AccentCopper,
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
        )
        Text(
            stringResource(Res.string.recap_streak_nudge_fmt, s.weeklyStreak.toString()),
            color = MarginaliaInk,
            fontFamily = caveat,
            fontSize = 19.sp,
        )
    }
    Box(modifier = Modifier.height(16.dp))
    Text(
        stringResource(Res.string.recap_cta_open_camera),
        color = AccentCopper,
        fontSize = 14.sp,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .padding(12.dp),
        textAlign = TextAlign.Center,
    )
    // Klickbarhet: lägg .clickable(onClick = onOpenCamera) på raden ovan (import androidx.compose.foundation.clickable).
}

private fun formatSigned(n: Int): String = if (n >= 0) "+$n" else "$n"
```

Lägg till en liten extension i `RecapScreen.kt` för läsbarhet (eller använd `state.recap.summary` direkt — byt `recapSummary()`-anropen mot `.summary`):

```kotlin
private fun se.birdy.app.recap.WeeklyRecap.recapSummary() = summary
```

> Förenkla gärna: ersätt `state.recap.recapSummary()` med `state.recap.summary` överallt och ta bort extensionen. Lägg `clickable` på CTA-raden + på hjälte-bilden vid behov.

- [ ] **Step 2: Verifiera kompilering**

Run:
```bash
./gradlew :composeApp:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL. Åtgärda ev. färg-/font-import-namn mot faktiska i `ui/theme/` (t.ex. `MarginaliaInk` finns i `Color.kt`).

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/recap/RecapScreen.kt
git commit -m "feat(recap): RecapScreen (aktiv + tyst läge, Field Journal-tema)"
```

---

## Task 11: Route, deep-link, AppGraph-factory, scheduling

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt`

- [ ] **Step 1: Lägg till route**

I `AppRoute.kt`:
```kotlin
@Serializable data object WeeklyRecap : AppRoute
```

- [ ] **Step 2: AppGraph-factory**

I `AppGraph.kt`, lägg till en factory (spegla `badgesViewModel()`; `badgeNameFor` använder samma map som BadgesScreen använder — `se.birdy.app.ui.badges.BadgeStringMap` har en namn-resolver; bekräfta dess API och använd den, annars returnera id):

```kotlin
fun weeklyRecapViewModel(): se.birdy.app.ui.recap.RecapViewModel =
    se.birdy.app.ui.recap.RecapViewModel(
        obsRepo = observationRepository,
        badgeRepo = badgeRepository,
        speciesByQid = { repository.allByQid(defaultLocale) },
        badgeNameFor = { id -> id }, // ersätt med BadgeStringMap-namnuppslag om tillgängligt
        zone = timeZone,
    )
```

- [ ] **Step 3: Registrera skärm + deep-link i AppScaffold**

I `AppScaffold.kt`, lägg till i NavHost:
```kotlin
composable<AppRoute.WeeklyRecap> {
    se.birdy.app.ui.recap.RecapScreen(
        viewModel = remember(graph) { graph.weeklyRecapViewModel() },
        onOpenCamera = { navController.navigate(AppRoute.Scan) { launchSingleTop = true } },
    )
}
```
Och i deep-link `when (host)`-blocket:
```kotlin
"recap" -> {
    navController.navigate(AppRoute.WeeklyRecap) { launchSingleTop = true }
}
```

- [ ] **Step 4: Schemaläggning i MainActivity**

I `MainActivity.kt`: på de tre ställen där `scheduleStreakRiskCheck()` anropas (rader ~93, ~106, ~217), **byt** anropet mot:
```kotlin
appGraph.notificationScheduler?.scheduleWeeklyRecap()
appGraph.notificationScheduler?.cancelStreakRiskCheck()
```
(`cancelStreakRiskCheck()` rensar den gamla periodiska streak-risk-workern på uppgradering. `scheduleDailyBird()`-anropen lämnas orörda.)

- [ ] **Step 5: Verifiera kompilering + bygg debug-APK**

Run:
```bash
./gradlew :composeApp:compileDebugKotlin :androidApp:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt
git commit -m "feat(recap): route + birdy://recap deep-link + schemaläggning ersätter streak-risk"
```

---

## Task 12: Lifelist-entry-kort → recap

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/LifelistScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt`

> Mall: det befintliga `onSeasonStatsClick`-entryt på LifelistScreen (samma mönster: en rad/kort högst upp som navigerar). Lägg `onRecapClick: () -> Unit = {}` som ny param på `LifelistScreen` och rendera ett paper-kort ovanför observationslistan i `LoadedLifelist`.

- [ ] **Step 1: Lägg till param + kort**

I `LifelistScreen.kt`: lägg `onRecapClick: () -> Unit = {}` i `LifelistScreen`-signaturen och vidarebefordra till `LoadedLifelist`. I `LoadedLifelist`, ovanför observationslistan, lägg ett klickbart kort:

```kotlin
import androidx.compose.foundation.clickable
import birdy_bird_scanner.composeapp.generated.resources.recap_lifelist_entry_title
import birdy_bird_scanner.composeapp.generated.resources.recap_lifelist_entry_quiet

// inuti LoadedLifelist, efter JournalIntro/OrnamentRule:
Row(
    modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onRecapClick)
            .padding(14.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            stringResource(Res.string.recap_lifelist_entry_title),
            color = AccentCopper,
            fontFamily = rememberDmSerifDisplay(),
            fontStyle = FontStyle.Italic,
            fontSize = 20.sp,
        )
    }
    Text("›", color = AccentCopper, fontSize = 22.sp)
}
```

> Matcha exakt befintlig kort-styling på skärmen (PaperCard/border) — spegla `onSeasonStatsClick`-kortet om det finns ett, annars använd `Modifier.paperBackground()`/PaperCard-stilen från skärmen.

- [ ] **Step 2: Koppla navigering i AppScaffold**

I `composable<AppRoute.Lifelist>`-blocket, lägg till:
```kotlin
onRecapClick = { navController.navigate(AppRoute.WeeklyRecap) { launchSingleTop = true } },
```

- [ ] **Step 3: Verifiera kompilering**

Run:
```bash
./gradlew :composeApp:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/LifelistScreen.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt
git commit -m "feat(recap): Veckans uppslag-entry på Lifelist"
```

---

## Task 13: Settings — recap-toggle ersätter streak-risk + DEV-trigger

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: SettingsViewModel — byt streak-risk mot recap**

I `SettingsViewModel.kt`, ersätt `streakRiskPushEnabled`-paret med:
```kotlin
val weeklyRecapPushEnabled: StateFlow<Boolean> =
    prefs.weeklyRecapPushEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)

fun setWeeklyRecapPushEnabled(value: Boolean) {
    viewModelScope.launch {
        prefs.setWeeklyRecapPushEnabled(value)
        if (value) notificationScheduler?.scheduleWeeklyRecap() else notificationScheduler?.cancelWeeklyRecap()
    }
}
```
Behåll `dailyBirdPushEnabled` orört. (Ta bort `streakRiskPushEnabled`-VM-medlemmarna.)

- [ ] **Step 2: SettingsScreen — byt toggle-rad + DEV-knapp**

I `SettingsScreen.kt`, byt streak-risk-`ToggleRow` mot:
```kotlin
ToggleRow(
    icon = Icons.Outlined.Notifications,
    label = stringResource(Res.string.settings_toggle_weekly_recap),
    checked = weeklyRecapEnabled,
    onCheckedChange = viewModel::setWeeklyRecapPushEnabled,
)
```
(`val weeklyRecapEnabled by viewModel.weeklyRecapPushEnabled.collectAsState()`.) Byt den befintliga DEV-knappen "DEV: Trigger Streak Risk push" mot en som triggar recap-workern — spegla hur DailyBird/StreakRisk-DEV-knapparna enqueuear sin worker, t.ex.:
```kotlin
// label = stringResource(Res.string.settings_dev_trigger_recap)
// onClick enqueuear WeeklyRecapWorker med KEY_FORCE_FOR_DEV = true (samma mönster som StreakRiskWorker-DEV-knappen)
```

> DEV-knapparnas enqueue-kod ligger i `SettingsScreen.kt` (Android) / via en callback. Spegla exakt den befintliga streak-risk-DEV-knappen men peka på `WeeklyRecapWorker` + dess `KEY_FORCE_FOR_DEV`.

- [ ] **Step 3: Verifiera kompilering + ktlint/detekt**

Run:
```bash
./gradlew :composeApp:compileDebugKotlin ktlintCheck detekt
```
Expected: BUILD SUCCESSFUL, inga ktlint/detekt-fel. Kör `./gradlew ktlintFormat` vid behov.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/
git commit -m "feat(recap): Settings recap-toggle + DEV-trigger ersätter streak-risk"
```

---

## Task 14: Full testsvit + lint

**Files:** (inga nya — verifieringssteg)

- [ ] **Step 1: Kör alla relevanta unit-tester**

Run:
```bash
./gradlew :shared:domain:jvmTest :composeApp:testDebugUnitTest
```
Expected: PASS (inkl. CurrentWeeklyStreakTest, WeeklyRecapBuilderTest, RecapViewModelTest).

- [ ] **Step 2: Lint + statisk analys**

Run:
```bash
./gradlew ktlintCheck detekt
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Bygg debug-APK**

Run:
```bash
./gradlew :androidApp:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit (om ktlintFormat ändrade något)**

```bash
git add -A
git commit -m "chore(recap): ktlintFormat + grön testsvit" || echo "inget att committa"
```

---

## Task 15: Device-verify på SM-S918B

**Files:** (manuell verifiering — se [[feedback_personal_device_verify]]: be användaren lägga ifrån sig telefonen + verifiera via screencap)

> Debug-paket: `se.birdy.android.debug`. Component: `se.birdy.android.debug/se.birdy.android.MainActivity`. `MSYS_NO_PATHCONV=1` före adb-kommandon med `/sdcard/...`.

- [ ] **Step 1: Installera**

```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android.debug/se.birdy.android.MainActivity
```

- [ ] **Step 2: Verifiera recap-skärm (tyst vecka — färsk install)**

Gå till Lifelist → tappa "Veckans uppslag"-kortet. Förväntat: tyst-läge (om inga obs denna vecka) med "En lugn vecka.", tom plate, uppmuntran. Screenshot:
```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" exec-out screencap -p > docs/superpowers/screenshots/v1.2-recap/01-recap-quiet.png
```

- [ ] **Step 3: Verifiera aktiv vecka**

Spara minst en observation denna vecka (foto-flöde), gå till recap igen. Förväntat: aktiv-läge med hjälte-foto centrerat + sammanfattning + stats. Screenshot `02-recap-active.png`.

- [ ] **Step 4: Verifiera deep-link + push (DEV)**

```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -a android.intent.action.VIEW -d "birdy://recap" se.birdy.android.debug
```
Förväntat: RecapScreen öppnas. Screenshot `03-recap-deeplink.png`. Settings → DEV: Trigga recap-push → kontrollera notis i shade (aktiv vecka). Screenshot `04-recap-push.png`.

- [ ] **Step 5: Commit screenshots**

```bash
git add docs/superpowers/screenshots/v1.2-recap/
git commit -m "docs(screenshots): v1.2 weekly recap device-verify på SM-S918B"
```

---

## Task 16: versionCode-bump + CLAUDE.md/status

**Files:**
- Modify: `androidApp/build.gradle.kts`
- Modify: `CLAUDE.md`

- [ ] **Step 1: Bumpa version**

I `androidApp/build.gradle.kts`: `versionCode` 116 → 117, `versionName` "1.1.0-rc1" → "1.2.0-rc1" (eller enligt rådande release-konvention vid implementeringstillfället — bekräfta nuvarande värden först).

- [ ] **Step 2: Uppdatera CLAUDE.md Status + plan-of-plans-tabell**

Lägg in en statusrad om Weekly Recap (Phase B) + markera plan-doc-pekaren.

- [ ] **Step 3: Commit + tag**

```bash
git add androidApp/build.gradle.kts CLAUDE.md
git commit -m "release: v1.2.0-rc1 — Phase B Weekly Recap"
git tag v1.2.0-rc1
git push origin main --tags
```

---

## Self-Review (gjord av plan-författaren)

**Spec coverage:**
- §3.1 adaptiv ton → Task 10 (aktiv/tyst), Task 8 (adaptiv push). ✓
- §3.2 enad push subsumerar streak-risk → Task 8 + Task 11 (byter scheduling) + Task 13 (toggle). ✓
- §3.3 innevarande vecka live → Task 3 (`weekKey(now)`), Task 9. ✓
- §3.4 Approach A-layout → Task 10. ✓
- §3.5 fynd-urval ny>ovanligast>senaste → Task 4. ✓
- §3.6 ingen tom-push → Task 8 (`else -> Result.success()`). ✓
- §3.7 Lifelist-entry → Task 12. ✓
- §3.8 tema → Task 10 (JournalHeadline/paperBackground/Caveat). ✓
- §4 builder/veckodata → Task 3-4. ✓ §5 skärm → Task 10. ✓ §6 worker → Task 8. ✓ §7 entry → Task 12. ✓ §8 arkitektur → alla. ✓ §9 edge cases (audio/null-art/ny användare) → Task 4 + Task 3-test. ✓ §10 test → Task 1,3,4,9,14. ✓

**Placeholder-scan:** Inga "TBD". Tre platser har explicita "bekräfta mot befintlig kod"-noteringar (R-import, SpeciesId-konstruktor, combine-suspend, BadgeStringMap-namn, DEV-knapp-enqueue) — dessa är medvetna verifieringspunkter mot exakt nuvarande kod, inte uteblivet innehåll; faktisk kod ges i varje fall.

**Type-konsistens:** `WeeklyRecapSummary`/`HeroFind`/`WeeklyRecap` används konsekvent (Task 2→3→4→9→10). `currentWeeklyStreak(instants, zone, now)` / `WeekKey.prev()` (Task 1) anropas identiskt i Task 3. `weeklyRecapPushEnabled` (Task 6) används i Task 8/13. `scheduleWeeklyRecap()`/`cancelWeeklyRecap()` (Task 7) i Task 11/13. `birdy://recap` + `NOTIF_ID_WEEKLY_RECAP=1003` (Task 8) ↔ deep-link (Task 11). Konsekvent.
