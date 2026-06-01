# Badges v2 — Troférummet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bygg ett "troférum" i Birdy — en kurerad showcase-vy som firar upplåsta märken, nådd via ett shimrande "Ditt troférum"-entrékort på Märken-fliken.

**Architecture:** Märken-fliken (`BadgesScreen`) får ett shimrande entrékort som navigerar till en ny egen vy `AppRoute.TrophyRoom` (ägd av Märken-tabben). Vyn visar fyra band (hjälte-trofé, senast upplåsta, sällsynta fynd i navy, nära att låsa upp) härledda ur befintlig märkesdata via en ren funktion `buildTrophyShowcase`. Ingen schema-/domänändring; `ShimmerSweep.kt` oförändrad (anropas bara nedtonad/långsam).

**Tech Stack:** Kotlin Multiplatform + Compose Multiplatform, SQLDelight (oförändrad), compose-resources (SV+EN), kotlin.test + Turbine för unit, ADB device-verify på SM-S918B.

**Spec:** `docs/superpowers/specs/2026-06-02-badges-v2-trophy-room-design.md`

**Gradle-prefix (bash):** Alla `./gradlew`-kommandon nedan kräver:
```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
```

**Branch:** Kör på egen branch `feat/badges-v2-trophy-room` (från `main`). Subagent-controllern verifierar branch-tip efter varje commit ([[feedback_subagent_git_detached_head]]).

---

## File Structure

**Nya filer:**
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/TrophyShowcase.kt` — `TrophyShowcase` data class + ren `buildTrophyShowcase`-funktion.
- `composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/TrophyShowcaseTest.kt` — unit-test.
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/TrophyRoomEntryCard.kt` — shimrande entrékort på Märken-fliken.
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/TrophyBand.kt` — band-wrapper + `TrophyStampItem`.
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/TrophyHero.kt` — hjälte-trofé med shimmer.
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/TrophyRoomScreen.kt` — fullskärmsvyn.
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/TrophyRoomRoute.kt` — VM-wiring + UnlockBottomSheet.

**Ändrade filer:**
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesUiState.kt` — `+trophyShowcase`.
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesViewModel.kt` — bygg showcase.
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/StampSeal.kt` — `+accentColor`-param.
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/Color.kt` — `+StampNavy`-token.
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt` — `+TrophyRoom`.
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BottomNavBar.kt` — Badges `ownedRoutes`.
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt` — `composable<TrophyRoom>` + pass callback.
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BadgesRoute.kt` — `+onOpenTrophyRoom`.
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesScreen.kt` — `+onOpenTrophyRoom`-param + entrékort.
- `composeApp/src/commonMain/composeResources/values/strings.xml` + `values-en/strings.xml` — nya nycklar.
- `composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/BadgesViewModelTest.kt` — `+trophyShowcase`-test.
- `androidApp/build.gradle.kts` — versionCode/name-bump.

---

## Task 1: `buildTrophyShowcase` (ren härledningsfunktion, TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/TrophyShowcase.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/TrophyShowcaseTest.kt`

- [ ] **Step 1: Skriv det failande testet**

`composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/TrophyShowcaseTest.kt`:
```kotlin
package se.birdy.app.ui.badges

import kotlinx.datetime.Instant
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrophyShowcaseTest {
    private fun bwu(
        id: String,
        ms: Long,
        category: BadgeCategory = BadgeCategory.PROGRESSION,
        stamp: Int = 0,
    ) = BadgeWithUnlock(
        badge = Badge(id = id, category = category, rule = BadgeRule.CountUniqueSpecies(1)),
        unlockedAt = Instant.fromEpochMilliseconds(ms),
        stampNumber = stamp,
    )

    private fun lbp(
        id: String,
        current: Int?,
        target: Int,
    ) = LockedBadgeProgress(
        badge = Badge(id = id, category = BadgeCategory.PROGRESSION, rule = BadgeRule.CountUniqueSpecies(target)),
        state = if (current != null) BadgeGridState.InProgress(current, target) else BadgeGridState.Locked,
    )

    @Test
    fun `hero is first recently unlocked and recent band drops it`() {
        val recent = listOf(bwu("a", 5000), bwu("b", 4000), bwu("c", 3000))
        val s = buildTrophyShowcase(recentlyUnlocked = recent, allUnlocked = recent, locked = emptyList())
        assertEquals("a", s.hero?.badge?.id)
        assertEquals(listOf("b", "c"), s.recentlyUnlocked.map { it.badge.id })
    }

    @Test
    fun `empty recently unlocked yields null hero and empty bands`() {
        val s = buildTrophyShowcase(emptyList(), emptyList(), emptyList())
        assertNull(s.hero)
        assertTrue(s.recentlyUnlocked.isEmpty())
        assertTrue(s.rareFinds.isEmpty())
        assertTrue(s.closeToUnlock.isEmpty())
    }

    @Test
    fun `rare finds keep only REDLISTED sorted by unlocked desc`() {
        val all =
            listOf(
                bwu("prog", 9000, BadgeCategory.PROGRESSION),
                bwu("red_old", 1000, BadgeCategory.REDLISTED),
                bwu("red_new", 8000, BadgeCategory.REDLISTED),
                bwu("fam", 7000, BadgeCategory.FAMILY),
            )
        val s = buildTrophyShowcase(recentlyUnlocked = all.take(5), allUnlocked = all, locked = emptyList())
        assertEquals(listOf("red_new", "red_old"), s.rareFinds.map { it.badge.id })
    }

    @Test
    fun `close to unlock sorts by ratio, takes 3, excludes locked and zero-target`() {
        val locked =
            listOf(
                lbp("almost", current = 4, target = 5), // 0.80
                lbp("half", current = 5, target = 10), // 0.50
                lbp("barely", current = 1, target = 100), // 0.01
                lbp("locked", current = null, target = 5), // exkluderas (Locked)
                lbp("zero", current = 0, target = 0), // exkluderas (target 0)
                lbp("nearest", current = 18, target = 20), // 0.90
            )
        val s = buildTrophyShowcase(emptyList(), emptyList(), locked)
        assertEquals(listOf("nearest", "almost", "half"), s.closeToUnlock.map { it.badge.id })
    }
}
```

- [ ] **Step 2: Kör testet och verifiera att det failar**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.badges.TrophyShowcaseTest"`
Expected: FAIL — "unresolved reference: buildTrophyShowcase" / "TrophyShowcase".

- [ ] **Step 3: Skriv minimal implementation**

`composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/TrophyShowcase.kt`:
```kotlin
package se.birdy.app.ui.badges

import se.birdy.domain.badge.BadgeCategory

/** Kurerad troférum-showcase — härledd helt ur befintlig märkesdata. */
data class TrophyShowcase(
    val hero: BadgeWithUnlock?,
    val recentlyUnlocked: List<BadgeWithUnlock>,
    val rareFinds: List<BadgeWithUnlock>,
    val closeToUnlock: List<LockedBadgeProgress>,
)

/**
 * Bygger troférummets showcase ur redan beräknade samlingar. Ren & unit-testbar —
 * inga repositories, ingen katalog-lookup.
 *
 * @param recentlyUnlocked top-5 upplåsta, DESC på unlockedAt (som i BadgesUiState.Loaded)
 * @param allUnlocked alla unlocks mappade till BadgeWithUnlock, DESC på unlockedAt
 * @param locked alla vanliga låsta märken med progress
 */
fun buildTrophyShowcase(
    recentlyUnlocked: List<BadgeWithUnlock>,
    allUnlocked: List<BadgeWithUnlock>,
    locked: List<LockedBadgeProgress>,
    maxClose: Int = 3,
): TrophyShowcase {
    val hero = recentlyUnlocked.firstOrNull()
    val recentBand = recentlyUnlocked.drop(1)
    val rareFinds =
        allUnlocked
            .filter { it.badge.category == BadgeCategory.REDLISTED }
            .sortedByDescending { it.unlockedAt }
    val closeToUnlock =
        locked
            .mapNotNull { lbp ->
                val s = lbp.state
                if (s is BadgeGridState.InProgress && s.target > 0) {
                    lbp to (s.current.toFloat() / s.target)
                } else {
                    null
                }
            }.sortedByDescending { it.second }
            .take(maxClose)
            .map { it.first }
    return TrophyShowcase(
        hero = hero,
        recentlyUnlocked = recentBand,
        rareFinds = rareFinds,
        closeToUnlock = closeToUnlock,
    )
}
```

- [ ] **Step 4: Kör testet och verifiera att det passar**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.badges.TrophyShowcaseTest"`
Expected: PASS (4 tester).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/TrophyShowcase.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/TrophyShowcaseTest.kt
git commit -m "feat(badges): pure buildTrophyShowcase deriver + tests"
```

---

## Task 2: Wira `trophyShowcase` in i `BadgesUiState.Loaded` + ViewModel (TDD)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesUiState.kt:10-20`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesViewModel.kt:79-131`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/BadgesViewModelTest.kt`

- [ ] **Step 1: Skriv det failande testet**

Lägg till i `BadgesViewModelTest.kt` (efter `unlockedCount ignores unlocks not in catalog`-testet, före `makeVm`):
```kotlin
    @Test
    fun `trophyShowcase hero is latest unlock and rareFinds keeps REDLISTED`() =
        runTest {
            val unlocks =
                listOf(
                    BadgeUnlock("novice", Instant.fromEpochMilliseconds(1_000L)),
                    BadgeUnlock("redlisted_1", Instant.fromEpochMilliseconds(2_000L)),
                )
            val catalog =
                BadgeCatalog(
                    version = 1,
                    badges =
                        listOf(
                            Badge("novice", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(5)),
                            Badge("redlisted_1", BadgeCategory.REDLISTED, BadgeRule.ObservedRedListed(1)),
                        ),
                )
            val vm = makeVm(observations = emptyList(), unlocks = unlocks, totalSpecies = 700, catalog = catalog)
            vm.state.test {
                var item = awaitItem()
                while (item is BadgesUiState.Loading) item = awaitItem()
                val loaded = item as BadgesUiState.Loaded
                assertEquals("redlisted_1", loaded.trophyShowcase.hero?.badge?.id)
                assertEquals(listOf("redlisted_1"), loaded.trophyShowcase.rareFinds.map { it.badge.id })
                cancelAndIgnoreRemainingEvents()
            }
        }
```

- [ ] **Step 2: Kör testet och verifiera att det failar**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.badges.BadgesViewModelTest"`
Expected: FAIL — kompileringsfel "unresolved reference: trophyShowcase" (fältet finns inte än).

- [ ] **Step 3: Lägg till fältet på `Loaded`**

I `BadgesUiState.kt`, lägg till fältet sist i `Loaded` (efter `premiumActive`):
```kotlin
    data class Loaded(
        val speciesProgress: SpeciesProgress,
        val unlockedCount: Int,
        val totalBadges: Int,
        val weeklyStreak: Int?, // null = dölj pillet
        val monthlyStreak: Int?,
        val recentlyUnlocked: List<BadgeWithUnlock>, // upp till 5 senaste, DESC
        val locked: List<LockedBadgeProgress>, // alla låsta, sorterade (regular only)
        val premiumBadges: List<PremiumBadgeProgress> = emptyList(), // 10 premium, locked + unlocked blandat
        val premiumActive: Boolean = false,
        val trophyShowcase: TrophyShowcase = TrophyShowcase(null, emptyList(), emptyList(), emptyList()),
    ) : BadgesUiState
```

- [ ] **Step 4: Bygg showcase i `BadgesViewModel.buildLoaded`**

I `BadgesViewModel.kt`, ersätt blocket som beräknar `recentlyUnlocked` (rad ~79-87):
```kotlin
        val recentlyUnlocked =
            unlocks
                .sortedByDescending { it.unlockedAt }
                .take(5)
                .mapNotNull { u ->
                    catalog.findById(u.badgeId)?.let { b ->
                        BadgeWithUnlock(b, u.unlockedAt, stampNumbersById[b.id] ?: 0)
                    }
                }
```
med:
```kotlin
        val allUnlocked =
            unlocks
                .sortedByDescending { it.unlockedAt }
                .mapNotNull { u ->
                    catalog.findById(u.badgeId)?.let { b ->
                        BadgeWithUnlock(b, u.unlockedAt, stampNumbersById[b.id] ?: 0)
                    }
                }
        val recentlyUnlocked = allUnlocked.take(5)
```
Lägg sedan till (efter att `locked` beräknats, före `weeklyStreak`):
```kotlin
        val trophyShowcase = buildTrophyShowcase(recentlyUnlocked, allUnlocked, locked)
```
Och i `return BadgesUiState.Loaded(...)`, lägg till sist:
```kotlin
            trophyShowcase = trophyShowcase,
```

- [ ] **Step 5: Kör testet och verifiera att det passar**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.badges.BadgesViewModelTest"`
Expected: PASS (alla tester inkl. det nya).

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesUiState.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesViewModel.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/BadgesViewModelTest.kt
git commit -m "feat(badges): expose trophyShowcase on BadgesUiState.Loaded"
```

---

## Task 3: `StampNavy`-token + `StampSeal accentColor`-param

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/Color.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/StampSeal.kt`

> Inga unit-tester (rena färg-/render-ändringar; `StampSealTest` testar bara sealed-state-funktioner och påverkas inte). Verifieras visuellt i Task 10.

- [ ] **Step 1: Lägg till `StampNavy`-token**

I `Color.kt`, efter `StampUnlockedBg`-raden (rad ~60), lägg till:
```kotlin

// Navy-variant av stämpeln — sällsynta/rödlistade troféer i troférummet (Field Journal "StampNavy").
val StampNavy = Color(0xFF1F3A5F)
```

- [ ] **Step 2: Lägg till `accentColor`-param i `StampSeal`**

I `StampSeal.kt`, ändra signaturen (rad ~87-92):
```kotlin
@Composable
fun StampSeal(
    state: StampSealState,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    accentColor: Color = AccentCopper,
    onClick: (() -> Unit)? = null,
) {
```

- [ ] **Step 3: Tråda `accentColor` genom border + №-text**

I `StampSeal.kt`, ersätt `borderColor`-blocket (rad ~95-100):
```kotlin
    val borderColor =
        when (state) {
            is StampSealState.Locked -> StampLocked
            is StampSealState.InProgress -> accentColor.copy(alpha = 0.6f)
            is StampSealState.Unlocked -> accentColor
        }
```
Och byt `color = AccentCopper` mot `color = accentColor` på de tre `№`-/progress-`Text`-elementen i InProgress- och Unlocked-grenarna (rad ~165, ~173, ~184 — de som visar `"№${state.number}"` och `state.progressLabel`). Lämna glyph (`TextOnCreme`), `?`-locked (`StampLocked`) och namn-texten orörda.

- [ ] **Step 4: Verifiera att StampSeal-testet fortfarande passar + kompilerar**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.components.StampSealTest"`
Expected: PASS (3 tester, oförändrade).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/Color.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/StampSeal.kt
git commit -m "feat(ui): StampNavy token + StampSeal accentColor param (default copper)"
```

---

## Task 4: `AppRoute.TrophyRoom` + Märken-tabbens `ownedRoutes`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt:36`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BottomNavBar.kt:72-73`

> Wiring-only; verifieras genom kompilering (Task 8) + device (Task 10).

- [ ] **Step 1: Lägg till routen**

I `AppRoute.kt`, direkt efter `@Serializable data object Badges : AppRoute`:
```kotlin
    @Serializable data object Badges : AppRoute

    /**
     * Badges v2: Troférummet — egen vy nådd via entrékortet på Märken-fliken.
     * Ägs av Märken-tabben (se BottomNavBar.ownedRoutes) så fliken förblir markerad.
     */
    @Serializable data object TrophyRoom : AppRoute
```

- [ ] **Step 2: Låt Märken-tabben äga TrophyRoom**

I `BottomNavBar.kt`, byt den positionella Badges-`TabSpec`-raden (rad ~72-73):
```kotlin
        TabSpec(AppRoute.Archive, Res.string.tab_archive, Icons.AutoMirrored.Filled.LibraryBooks),
        TabSpec(AppRoute.Lifelist, Res.string.tab_lifelist, Icons.Outlined.CollectionsBookmark),
        TabSpec(AppRoute.Badges, Res.string.tab_badges, Icons.Filled.Stars),
```
mot:
```kotlin
        TabSpec(AppRoute.Archive, Res.string.tab_archive, Icons.AutoMirrored.Filled.LibraryBooks),
        TabSpec(AppRoute.Lifelist, Res.string.tab_lifelist, Icons.Outlined.CollectionsBookmark),
        TabSpec(
            route = AppRoute.Badges,
            label = Res.string.tab_badges,
            icon = Icons.Filled.Stars,
            ownedRoutes = setOf(AppRoute.Badges::class, AppRoute.TrophyRoom::class),
        ),
```

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BottomNavBar.kt
git commit -m "feat(nav): AppRoute.TrophyRoom owned by Badges tab"
```

---

## Task 5: Strängar (SV + EN)

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`

> Läggs före komponenterna så `Res.string.*`-accessorer genereras innan de refereras. Vanliga UI-strängar — inte `badges.yaml`-bundna, ej build-validerade. Inga apostrofer/`%%` (trap-katalog).

- [ ] **Step 1: Lägg till SV-strängar**

I `values/strings.xml`, före avslutande `</resources>`:
```xml
    <!-- ===== Badges v2: Troférummet ===== -->
    <string name="trophy_room_intro_eyebrow">Skådar-profil</string>
    <string name="trophy_room_headline">Ditt *troférum*.</string>
    <string name="trophy_room_sub">%1$s av %2$s · %3$s väntar i fält</string>
    <string name="trophy_room_title">Ditt troférum</string>
    <string name="trophy_room_back">Tillbaka</string>
    <string name="trophy_room_entry_eyebrow">Ditt troférum</string>
    <string name="trophy_room_entry_count">%1$s troféer</string>
    <string name="trophy_room_entry_empty">Ditt troférum väntar</string>
    <string name="trophy_room_entry_a11y">Öppna ditt troférum, %1$s troféer</string>
    <string name="trophy_hero_recent_label">Senast vunnen</string>
    <string name="trophy_hero_empty_name">din första stämpel väntar</string>
    <string name="trophy_section_recent">Senast upplåsta</string>
    <string name="trophy_section_rare">Sällsynta fynd</string>
    <string name="trophy_section_close">Nära att låsa upp</string>
    <string name="trophy_close_remaining">%1$s kvar</string>
```

- [ ] **Step 2: Lägg till EN-strängar**

I `values-en/strings.xml`, före avslutande `</resources>`:
```xml
    <!-- ===== Badges v2: Trophy room ===== -->
    <string name="trophy_room_intro_eyebrow">Birder profile</string>
    <string name="trophy_room_headline">Your *trophy room*.</string>
    <string name="trophy_room_sub">%1$s of %2$s · %3$s waiting in the field</string>
    <string name="trophy_room_title">Your trophy room</string>
    <string name="trophy_room_back">Back</string>
    <string name="trophy_room_entry_eyebrow">Your trophy room</string>
    <string name="trophy_room_entry_count">%1$s trophies</string>
    <string name="trophy_room_entry_empty">Your trophy room awaits</string>
    <string name="trophy_room_entry_a11y">Open your trophy room, %1$s trophies</string>
    <string name="trophy_hero_recent_label">Most recent</string>
    <string name="trophy_hero_empty_name">your first stamp awaits</string>
    <string name="trophy_section_recent">Recently unlocked</string>
    <string name="trophy_section_rare">Rare finds</string>
    <string name="trophy_section_close">Close to unlocking</string>
    <string name="trophy_close_remaining">%1$s to go</string>
```

- [ ] **Step 3: Verifiera att resurser genereras (kompilering)**

Run: `./gradlew :composeApp:generateComposeResClass`
Expected: BUILD SUCCESSFUL (Res-accessorer genereras för de nya nycklarna).

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "i18n(badges): trophy room strings (SV + EN)"
```

---

## Task 6: `TrophyRoomEntryCard` (shimrande entrékort på Märken-fliken)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/TrophyRoomEntryCard.kt`

> UI-komponent — verifieras via kompilering + device (Task 10).

- [ ] **Step 1: Skapa komponenten**

`composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/TrophyRoomEntryCard.kt`:
```kotlin
package se.birdy.app.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_entry_a11y
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_entry_count
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_entry_empty
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_entry_eyebrow
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.shimmerSweep
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.HeroMossMid
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

@Composable
fun TrophyRoomEntryCard(
    hero: BadgeWithUnlock?,
    unlockedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val serif = rememberDmSerifDisplay()
    val caveat = rememberCaveat()
    val eyebrow = stringResource(Res.string.trophy_room_entry_eyebrow)
    val heroName = hero?.let { stringResource(BadgeStringMap.nameFor(it.badge.id)) }
    val countLabel =
        if (unlockedCount > 0) {
            stringResource(Res.string.trophy_room_entry_count, unlockedCount.toString())
        } else {
            stringResource(Res.string.trophy_room_entry_empty)
        }
    val a11y = stringResource(Res.string.trophy_room_entry_a11y, unlockedCount.toString())

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(HeroMossLight, HeroMossMid)))
                .clickable(onClick = onClick)
                .shimmerSweep(durationMillis = 6000, alpha = 0.20f)
                .semantics { contentDescription = a11y },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (hero != null) AccentCopper else AccentCopper.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = hero?.let { "№${it.stampNumber}" } ?: "✦",
                    color = Color.White,
                    fontFamily = caveat,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = eyebrow,
                    color = OffwhiteWarm.copy(alpha = 0.8f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 1.6.sp,
                )
                Text(
                    text = heroName ?: countLabel,
                    color = OffwhiteWarm,
                    fontFamily = serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (heroName != null) {
                    Text(
                        text = countLabel,
                        color = OffwhiteWarm.copy(alpha = 0.85f),
                        fontFamily = caveat,
                        fontSize = 14.sp,
                    )
                }
            }
            Text(
                text = "›",
                color = OffwhiteWarm,
                fontFamily = serif,
                fontStyle = FontStyle.Italic,
                fontSize = 22.sp,
            )
        }
    }
}
```

- [ ] **Step 2: Verifiera kompilering**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/TrophyRoomEntryCard.kt
git commit -m "feat(badges): TrophyRoomEntryCard (shimmering moss card)"
```

---

## Task 7: `TrophyBand` + `TrophyStampItem` + `TrophyHero`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/TrophyBand.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/TrophyHero.kt`

- [ ] **Step 1: Skapa `TrophyBand.kt`**

```kotlin
package se.birdy.app.ui.badges

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.components.MicroLabel
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.rememberCaveat

/** Sektionsrubrik + horisontell rad av troféer. */
@Composable
fun TrophyBand(
    label: String,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 18.dp)) {
        MicroLabel(label, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** En trofé i ett band: StampSeal + valfri caption ("X kvar"). */
@Composable
fun TrophyStampItem(
    state: StampSealState,
    modifier: Modifier = Modifier,
    accentColor: Color = AccentCopper,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        StampSeal(state = state, size = 66.dp, accentColor = accentColor, onClick = onClick)
        if (caption != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = caption,
                color = MarginaliaInk,
                fontFamily = rememberCaveat(),
                fontSize = 12.sp,
            )
        }
    }
}
```

- [ ] **Step 2: Skapa `TrophyHero.kt`**

```kotlin
package se.birdy.app.ui.badges

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.trophy_hero_empty_name
import birdy_bird_scanner.composeapp.generated.resources.trophy_hero_recent_label
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.MicroLabel
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.components.shimmerSweep
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.content.Locale

/** Hjälte-trofén: senast vunna stämpeln, stor, med nedtonad/långsam shimmer. Klick → detalj. */
@Composable
fun TrophyHero(
    hero: BadgeWithUnlock?,
    locale: Locale,
    zone: TimeZone,
    onHeroClick: (BadgeWithUnlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (hero != null) {
            val name = stringResource(BadgeStringMap.nameFor(hero.badge.id))
            Box(modifier = Modifier.shimmerSweep(durationMillis = 6000, alpha = 0.20f)) {
                StampSeal(
                    state = StampSealState.Unlocked(number = hero.stampNumber, glyph = null, name = name),
                    size = 104.dp,
                    onClick = { onHeroClick(hero) },
                )
            }
            Spacer(Modifier.height(8.dp))
            MicroLabel(stringResource(Res.string.trophy_hero_recent_label))
            Text(
                text = formatBadgeFullDate(hero.unlockedAt, zone, locale),
                color = MarginaliaInk,
                fontFamily = rememberCaveat(),
                fontSize = 15.sp,
            )
        } else {
            StampSeal(
                state = StampSealState.Locked(name = stringResource(Res.string.trophy_hero_empty_name)),
                size = 104.dp,
            )
        }
    }
}
```

- [ ] **Step 3: Verifiera kompilering**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/TrophyBand.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/TrophyHero.kt
git commit -m "feat(badges): TrophyBand + TrophyStampItem + TrophyHero"
```

---

## Task 8: `TrophyRoomScreen` (fullskärmsvyn)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/TrophyRoomScreen.kt`

- [ ] **Step 1: Skapa vyn**

```kotlin
package se.birdy.app.ui.badges

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.badges_load_error
import birdy_bird_scanner.composeapp.generated.resources.badges_load_error_retry
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_back
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_headline
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_intro_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_sub
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_title
import birdy_bird_scanner.composeapp.generated.resources.trophy_section_close
import birdy_bird_scanner.composeapp.generated.resources.trophy_section_rare
import birdy_bird_scanner.composeapp.generated.resources.trophy_section_recent
import birdy_bird_scanner.composeapp.generated.resources.trophy_close_remaining
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BackButton
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.JournalLoading
import se.birdy.app.ui.components.JournalScaffold
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.theme.StampNavy
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.content.Locale

@Composable
fun TrophyRoomScreen(
    state: BadgesUiState,
    locale: Locale,
    zone: TimeZone,
    onHeroClick: (BadgeWithUnlock) -> Unit,
    onStampClick: (BadgeWithUnlock) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JournalScaffold(
        modifier = modifier,
        topBar = { TrophyTopBar(onBack = onBack) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                is BadgesUiState.Loading -> JournalLoading()
                is BadgesUiState.Error ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(48.dp))
                        Text(stringResource(Res.string.badges_load_error))
                        TextButton(onClick = onRetry) { Text(stringResource(Res.string.badges_load_error_retry)) }
                    }
                is BadgesUiState.Loaded ->
                    LoadedTrophyRoom(
                        state = state,
                        locale = locale,
                        zone = zone,
                        onHeroClick = onHeroClick,
                        onStampClick = onStampClick,
                    )
            }
        }
    }
}

@Composable
private fun TrophyTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onClick = onBack, contentDescription = stringResource(Res.string.trophy_room_back))
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(Res.string.trophy_room_title),
            fontFamily = rememberDmSerifDisplay(),
            fontStyle = FontStyle.Italic,
            fontSize = 20.sp,
            color = TextOnCreme,
        )
    }
}

@Composable
private fun LoadedTrophyRoom(
    state: BadgesUiState.Loaded,
    locale: Locale,
    zone: TimeZone,
    onHeroClick: (BadgeWithUnlock) -> Unit,
    onStampClick: (BadgeWithUnlock) -> Unit,
) {
    val showcase = state.trophyShowcase
    val waiting = (state.totalBadges - state.unlockedCount).coerceAtLeast(0)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            JournalIntro(
                label = stringResource(Res.string.trophy_room_intro_eyebrow),
                headline = stringResource(Res.string.trophy_room_headline),
                sub =
                    stringResource(
                        Res.string.trophy_room_sub,
                        state.unlockedCount.toString(),
                        state.totalBadges.toString(),
                        waiting.toString(),
                    ),
            )
        }
        item { TrophyHero(hero = showcase.hero, locale = locale, zone = zone, onHeroClick = onHeroClick) }

        if (showcase.recentlyUnlocked.isNotEmpty()) {
            item {
                TrophyBand(label = stringResource(Res.string.trophy_section_recent)) {
                    items(items = showcase.recentlyUnlocked, key = { it.badge.id }) { bwu ->
                        val name = stringResource(BadgeStringMap.nameFor(bwu.badge.id))
                        TrophyStampItem(
                            state = StampSealState.Unlocked(number = bwu.stampNumber, glyph = null, name = name),
                            onClick = { onStampClick(bwu) },
                        )
                    }
                }
            }
        }
        if (showcase.rareFinds.isNotEmpty()) {
            item {
                TrophyBand(label = stringResource(Res.string.trophy_section_rare)) {
                    items(items = showcase.rareFinds, key = { it.badge.id }) { bwu ->
                        val name = stringResource(BadgeStringMap.nameFor(bwu.badge.id))
                        TrophyStampItem(
                            state = StampSealState.Unlocked(number = bwu.stampNumber, glyph = null, name = name),
                            accentColor = StampNavy,
                            onClick = { onStampClick(bwu) },
                        )
                    }
                }
            }
        }
        if (showcase.closeToUnlock.isNotEmpty()) {
            item {
                TrophyBand(label = stringResource(Res.string.trophy_section_close)) {
                    items(items = showcase.closeToUnlock, key = { it.badge.id }) { lbp ->
                        val name = stringResource(BadgeStringMap.nameFor(lbp.badge.id))
                        val s = lbp.state as BadgeGridState.InProgress
                        TrophyStampItem(
                            state =
                                StampSealState.InProgress(
                                    number = lbp.stampNumber,
                                    name = name,
                                    progressLabel = "${s.current}/${s.target}",
                                ),
                            caption = stringResource(Res.string.trophy_close_remaining, (s.target - s.current).toString()),
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verifiera kompilering**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/TrophyRoomScreen.kt
git commit -m "feat(badges): TrophyRoomScreen with four bands"
```

---

## Task 9: Route-wiring + entrékort på Märken-fliken

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/TrophyRoomRoute.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt:270-277`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BadgesRoute.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesScreen.kt`

- [ ] **Step 1: Skapa `TrophyRoomRoute.kt`** (speglar `BadgesRoute`-mönstret för UnlockBottomSheet)

```kotlin
package se.birdy.app.ui.scaffold

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.datetime.Instant
import se.birdy.app.di.AppGraph
import se.birdy.app.ui.badges.BadgeLadder
import se.birdy.app.ui.badges.BadgeStringMap
import se.birdy.app.ui.badges.BadgeWithUnlock
import se.birdy.app.ui.badges.BadgesUiState
import se.birdy.app.ui.badges.TrophyRoomScreen
import se.birdy.app.ui.badges.UnlockBottomSheet
import se.birdy.domain.badge.Badge

@Composable
fun TrophyRoomRoute(
    graph: AppGraph,
    onBack: () -> Unit,
) {
    val viewModel = remember(graph) { graph.badgesViewModel() }
    val state by viewModel.state.collectAsState()
    var bottomSheetUnlock by remember { mutableStateOf<Pair<Badge, Instant>?>(null) }

    val openDetail: (BadgeWithUnlock) -> Unit = { bwu ->
        bottomSheetUnlock = bwu.badge to bwu.unlockedAt
    }

    TrophyRoomScreen(
        state = state,
        locale = graph.defaultLocale,
        zone = graph.timeZone,
        onHeroClick = openDetail,
        onStampClick = openDetail,
        onBack = onBack,
        onRetry = { /* hot Flow — ingen manuell retry */ },
    )

    bottomSheetUnlock?.let { (badge, unlockedAt) ->
        val loaded = state as? BadgesUiState.Loaded
        val stampNumber =
            loaded?.let { l ->
                l.recentlyUnlocked.firstOrNull { it.badge.id == badge.id }?.stampNumber
                    ?: l.trophyShowcase.rareFinds.firstOrNull { it.badge.id == badge.id }?.stampNumber
            }
        val nextTier = loaded?.let { BadgeLadder.nextTier(badge, it.locked) }
        UnlockBottomSheet(
            badge = badge,
            unlockedAt = unlockedAt,
            isCelebration = false,
            locale = graph.defaultLocale,
            zone = graph.timeZone,
            nameRes = BadgeStringMap.nameFor(badge.id),
            descriptionRes = BadgeStringMap.descriptionFor(badge.id),
            onDismiss = { bottomSheetUnlock = null },
            stampNumber = stampNumber,
            nextTier = nextTier,
        )
    }
}
```

- [ ] **Step 2: Registrera routen + skicka callback i `AppScaffold.kt`**

Ersätt `composable<AppRoute.Badges>`-blocket (rad ~270-277):
```kotlin
            composable<AppRoute.Badges> {
                BadgesRoute(
                    graph = graph,
                    onSettingsClick = { navController.navigate(AppRoute.Settings) { launchSingleTop = true } },
                    onPremiumClick = { navController.navigate(AppRoute.Premium) },
                    onOpenTrophyRoom = { navController.navigate(AppRoute.TrophyRoom) { launchSingleTop = true } },
                    showPremiumTeaser = showPremiumTeaser,
                )
            }
            composable<AppRoute.TrophyRoom> {
                TrophyRoomRoute(
                    graph = graph,
                    onBack = { navController.popBackStack() },
                )
            }
```

- [ ] **Step 3: Lägg till `onOpenTrophyRoom` i `BadgesRoute.kt`**

Ändra signaturen + propagera till `BadgesScreen`:
```kotlin
@Composable
fun BadgesRoute(
    graph: AppGraph,
    onSettingsClick: () -> Unit,
    onPremiumClick: () -> Unit,
    onOpenTrophyRoom: () -> Unit,
    showPremiumTeaser: Boolean = true,
) {
```
och i `BadgesScreen(...)`-anropet, lägg till parametern:
```kotlin
        onSettingsClick = onSettingsClick,
        onPremiumClick = onPremiumClick,
        onOpenTrophyRoom = onOpenTrophyRoom,
        showPremiumTeaser = showPremiumTeaser,
```

- [ ] **Step 4: Lägg till entrékortet i `BadgesScreen.kt`**

Lägg `onOpenTrophyRoom: () -> Unit` i `BadgesScreen`-signaturen (efter `onPremiumClick`):
```kotlin
    onSettingsClick: () -> Unit,
    onPremiumClick: () -> Unit,
    onOpenTrophyRoom: () -> Unit,
    showPremiumTeaser: Boolean = true,
```
Propagera in i `LoadedContent`-anropet:
```kotlin
                is BadgesUiState.Loaded ->
                    LoadedContent(
                        state = state,
                        locale = locale,
                        zone = zone,
                        now = now,
                        onUnlockedClick = { badge, unlock -> onBadgeClick(badge, unlock) },
                        onLockedClick = { selectedLocked = it },
                        onSettingsClick = onSettingsClick,
                        onPremiumClick = onPremiumClick,
                        onOpenTrophyRoom = onOpenTrophyRoom,
                        showPremiumTeaser = showPremiumTeaser,
                    )
```
Lägg `onOpenTrophyRoom: () -> Unit` i `LoadedContent`-signaturen (efter `onPremiumClick`). I `LoadedContent`s `LazyVerticalGrid`, lägg ett nytt `item` direkt efter intro/StampTrack-item:t (efter blocket som renderar `JournalIntro` + `StampTrack`, före `if (state.recentlyUnlocked.isNotEmpty())`):
```kotlin
        item(span = { GridItemSpan(maxLineSpan) }) {
            TrophyRoomEntryCard(
                hero = state.trophyShowcase.hero,
                unlockedCount = state.unlockedCount,
                onClick = onOpenTrophyRoom,
            )
        }
```

- [ ] **Step 5: Kör alla unit-tester + kompilera**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: PASS (alla, inkl. Task 1/2-tester).

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/TrophyRoomRoute.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BadgesRoute.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesScreen.kt
git commit -m "feat(badges): wire TrophyRoom route + entry card on Märken"
```

---

## Task 10: Versionsbump, full build, ktlint + device-verify

**Files:**
- Modify: `androidApp/build.gradle.kts`

- [ ] **Step 1: Bumpa version**

Öppna `androidApp/build.gradle.kts`, hitta `versionCode`/`versionName` (verifiera nuvarande värden — förväntat `121` / `1.1.0-rc6`) och bumpa till nästa i v1.1-batchen:
```kotlin
        versionCode = 122
        versionName = "1.1.0-rc7"
```

- [ ] **Step 2: ktlintFormat + statisk analys + full unit-svit**

Run:
```bash
./gradlew ktlintFormat
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest ktlintCheck detekt
```
Expected: BUILD SUCCESSFUL, alla tester gröna. Committa ev. ktlintFormat-ändringar:
```bash
git add -A && git commit -m "style: ktlintFormat trophy room files"
```

- [ ] **Step 3: Installera på enhet**

> Be Albin om "händerna borta" från SM-S918B innan ADB-driving ([[feedback_personal_device_verify]]). OBS: `installDebug` lägger ut paketet `se.birdy.android.debug` (inte `se.birdy.android`).

Run:
```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android.debug/se.birdy.android.MainActivity
```

- [ ] **Step 4: Verifieringschecklista (manuell på enhet, screenshots till `docs/superpowers/screenshots/v1.x-badges-v2-trophy-room/`)**

- [ ] Märken-fliken: shimrande "Ditt troférum"-entrékort syns överst (nedtonad, långsam sweep), klickbart.
- [ ] Klick → TrophyRoom öppnas som egen vy; Märken-tabben förblir markerad i bottennavet.
- [ ] Back-pil + system-back → tillbaka till Märken-fliken.
- [ ] Hjälte-trofé: stor stämpel med subtil shimmer; klick → `UnlockBottomSheet`.
- [ ] "Senast upplåsta"-bandet renderar kopparstämplar.
- [ ] "Sällsynta fynd"-bandet renderar **navy**-stämplar (om någon rödlistad upplåst).
- [ ] "Nära att låsa upp"-bandet visar in-progress + "X kvar".
- [ ] "Alla märken"-rutnätet (Märken-fliken nedanför entrékortet) oförändrat.
- [ ] Byt enhetsspråk till engelska → alla troférum-strängar på engelska.
- [ ] (Om driveable) tomt läge: 0 troféer → entrékort "Ditt troférum väntar" + hjälte "din första stämpel väntar".

- [ ] **Step 5: Commit screenshots + slutfört**

```bash
git add docs/superpowers/screenshots/v1.x-badges-v2-trophy-room/
git commit -m "docs(screenshots): Badges v2 trophy room device-verify (SM-S918B)"
```

---

## Self-Review (kördes vid plan-skrivning)

**Spec-täckning:** §3 nav-modell → Task 4/9; §4 data → Task 1/2; §5 komponenter → Task 6/7/8/9 + StampSeal Task 3; §6 visuellt/shimmer → Task 3/6/7; §7 tomt läge → Task 6/7/8 (hero/entry empty-grenar + band döljs när tomma); §8 språk → Task 5; §9 felhantering → Task 8 (Loading/Error) + Task 1 (target 0-guard); §10 testning → Task 1/2 unit + Task 10 device; §11 risker → Task 3 (default-param), Task 4 (ownedRoutes); §13 release → Task 10. Inga gap.

**Placeholder-scan:** Inga "TBD/implement later/handle edge cases" utan kod. Alla steg har konkret kod eller exakta kommandon med förväntad output.

**Typkonsistens:** `buildTrophyShowcase(recentlyUnlocked, allUnlocked, locked, maxClose)` matchar anropet i Task 2. `TrophyShowcase(hero, recentlyUnlocked, rareFinds, closeToUnlock)` matchar default-värdet i `Loaded` + användningen i `TrophyRoomScreen`. `StampSeal(state, modifier, size, accentColor, onClick)` matchar alla nya anrop. `BadgeStringMap.nameFor/descriptionFor`, `BadgeLadder.nextTier`, `formatBadgeFullDate(instant, zone, locale)`, `UnlockBottomSheet(...)` — alla verifierade mot befintlig kod. `TrophyBand(label, modifier, content: LazyListScope.() -> Unit)` matchar anropen i `TrophyRoomScreen`.
