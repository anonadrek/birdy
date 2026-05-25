# Onboarding v2 — scroll-driven story Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bygg om onboarding-flödet till en 7-scens vertikal `VerticalPager`-baserad scroll-story med hybrid Field Journal-copy (poetisk headline + konkret USP-sub) och animationer som drivs av `pagerState.currentPageOffsetFraction`. Lägg till "Visa introduktion igen" i Settings så storyn kan ses fler än en gång.

**Architecture:** `VerticalPager` (7 scener) ersätter befintlig `HorizontalPager` (3 sidor). Varje scen är en `commonMain`-composable som tar `pageOffset: Float` och animerar via `Animatable<Float>` + `LaunchedEffect(pagerState.currentPage)`. Återanvänder Plan 7c-komponenter (`JournalHeadline`, `JournalIntro`, `StampSeal`, `PlateFrame`, `OrnamentRule`, `paperBackground`). `OnboardingViewModel` får en `isReplay: Boolean`-flagg som skippar DataStore-writes så replay-routen från Settings inte rör `hasSeenOnboarding`/`userName`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, `androidx.compose.foundation.pager.VerticalPager`, Compose `Canvas` + `Animatable` + `rememberInfiniteTransition`, `compose-resources` strings, Coil 3 `AsyncImage`. Inga nya externa deps.

**Spec:** `docs/superpowers/specs/2026-05-25-onboarding-scroll-story-design.md`

---

## File Structure

**Skapas:**

- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneHero.kt` — scen 1
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/ScenePhoto.kt` — scen 2
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneAudio.kt` — scen 3
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneJournal.kt` — scen 4
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneBadges.kt` — scen 5
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/ScenePrivacy.kt` — scen 6
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneName.kt` — scen 7
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/components/IntroSceneScaffold.kt`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/components/WaveformBars.kt`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/components/StreakCounter.kt`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/components/OfflineShield.kt`
- `docs/superpowers/screenshots/onboarding-v2/` — directory för device-screenshots

**Modifieras:**

- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/OnboardingScreen.kt` — `HorizontalPager` → `VerticalPager`, 7 scener, replay-stöd
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/OnboardingUiState.kt` — kommentar om 7 scener
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/OnboardingViewModel.kt` — `isReplay`, `MAX_PAGE_INDEX = 6`
- `composeApp/src/commonTest/kotlin/se/birdy/app/ui/onboarding/OnboardingViewModelTest.kt` — utöka tester
- `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt` — `onboardingViewModel(fallbackName, isReplay)`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt` — `OnboardingReplay` route
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt` — `composable<OnboardingReplay>` entry
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppGate.kt` — pass `isReplay = false`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsScreen.kt` — ny SettingsRow + `onShowIntroAgain` callback
- `composeApp/src/commonMain/composeResources/values/strings.xml` — strings cleanup + nya
- `composeApp/src/commonMain/composeResources/values-en/strings.xml` — strings cleanup + nya
- `androidApp/build.gradle.kts` — `versionCode 114 → 115`, `versionName "1.0.1" → "1.0.2"`
- `CLAUDE.md` — status-sektionen + Plan-of-plans-tabell

---

## Task 1: Strings cleanup + nya scen-strings

Vi börjar med strings så att alla efterföljande tasks kan referera resursnamn direkt. Att ändra `strings.xml` triggar compose-resources att regenerera `Res.string.*`-konstanter, så vi commitar detta separat innan vi rör Compose-koden.

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` (SV)
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml` (EN)

- [ ] **Step 1: Ta bort gamla onboarding-strängar (SV)**

Öppna `composeApp/src/commonMain/composeResources/values/strings.xml`. Radera följande rader (linje-nummer från senaste read; sökmotsv. om de drivit):

```
<string name="onboarding_journal_p1_body1">…</string>
<string name="onboarding_journal_p1_body2">…</string>
<string name="onboarding_journal_p2_label">…</string>
<string name="onboarding_journal_p2_headline">…</string>
<string name="onboarding_journal_p2_sub">…</string>
<string name="onboarding_journal_p3_label">…</string>
<string name="onboarding_journal_p3_headline">…</string>
<string name="onboarding_journal_p3_sub">…</string>
<string name="onboarding_journal_p3_cta">…</string>
<string name="onboarding_p1_breadcrumb">…</string>
<string name="onboarding_p1_headline">…</string>
<string name="onboarding_p1_body">…</string>
<string name="onboarding_p1_sub">…</string>
<string name="onboarding_p1_cta">…</string>
<string name="onboarding_p2_breadcrumb">…</string>
<string name="onboarding_p2_headline">…</string>
<string name="onboarding_p2_listen_name">…</string>
<string name="onboarding_p2_listen_desc">…</string>
<string name="onboarding_p2_archive_name">…</string>
<string name="onboarding_p2_archive_desc">…</string>
<string name="onboarding_p2_lifelist_name">…</string>
<string name="onboarding_p2_lifelist_desc">…</string>
<string name="onboarding_p2_badges_name">…</string>
<string name="onboarding_p2_badges_desc">…</string>
<string name="onboarding_p2_cta">…</string>
<string name="onboarding_p3_breadcrumb">…</string>
<string name="onboarding_p3_headline">…</string>
<string name="onboarding_p3_input_placeholder">…</string>
<string name="onboarding_p3_input_helper">…</string>
<string name="onboarding_p3_premium_label">…</string>
<string name="onboarding_p3_cta">…</string>
```

Behåll `onboarding_skip` och `onboarding_p3_fallback_name` (används av ViewModel + skip-knapp).

- [ ] **Step 2: Lägg in nya onboarding-scen-strängar (SV)**

Lägg in (gruppera under en kommentar `<!-- Onboarding v2 (Plan 2026-05-25 onboarding-scroll-story) -->`):

```xml
<!-- Onboarding v2 (Plan 2026-05-25 onboarding-scroll-story) -->
<string name="onboarding_s1_eyebrow">FÄLT-FÖLJESLAGARE · NO 1</string>
<string name="onboarding_s1_headline">*Birdy.*</string>
<string name="onboarding_s1_sub">Identifiera fåglar i fält — foto eller ljud, på enheten, utan internet.</string>

<string name="onboarding_s2_eyebrow">FOTO · NO 2</string>
<string name="onboarding_s2_headline">*Rikta*. Tryck. Stämpla.</string>
<string name="onboarding_s2_sub">On-device-AI känner igen 839 europeiska arter på sekunder.</string>
<string name="onboarding_s2_match_pill">MATCH</string>
<string name="onboarding_s2_species_demo">Talgoxe</string>

<string name="onboarding_s3_eyebrow">LJUD · NO 3</string>
<string name="onboarding_s3_headline">Eller — *lyssna*.</string>
<string name="onboarding_s3_sub">3 sekunders inspelning. Identifiera sången. Gratis, alltid.</string>
<string name="onboarding_s3_free_pill">GRATIS</string>
<string name="onboarding_s3_species_demo">Talgoxe — 94%</string>

<string name="onboarding_s4_eyebrow">FÄLTBOKEN · NO 4</string>
<string name="onboarding_s4_headline">Varje fynd — *en sida*.</string>
<string name="onboarding_s4_sub">Din samling växer med varje fågel. PDF-export när du vill.</string>
<string name="onboarding_s4_marginalia">16 maj · Norra Djurgården</string>

<string name="onboarding_s5_eyebrow">MÄRKEN · NO 5</string>
<string name="onboarding_s5_headline">*Förtjäna* märken.</string>
<string name="onboarding_s5_sub">Mängder av milstolpar att jaga. Håll svit levande.</string>

<string name="onboarding_s6_eyebrow">PRIVATLIV · NO 6</string>
<string name="onboarding_s6_headline">*Stannar* på enheten.</string>
<string name="onboarding_s6_sub">Inga konton. Ingen molnsynk. Dagboken är din.</string>

<string name="onboarding_s7_eyebrow">DITT NAMN · NO 7</string>
<string name="onboarding_s7_headline">*Vem* skriver?</string>
<string name="onboarding_s7_sub">Namnet visas i dagboken.</string>
<string name="onboarding_s7_input_placeholder">Albin</string>
<string name="onboarding_s7_input_helper">Lämna tomt om du vill — vi kallar dig \'Fältornitolog\'.</string>
<string name="onboarding_s7_cta">*Börja* dagboken</string>

<string name="onboarding_close_replay">Stäng</string>
<string name="settings_show_intro_again">Visa introduktion igen</string>
```

OBS: `\'` inom strängar måste escapas eller bytas mot Unicode `'` (U+2019). Använd raw `'` här (compose-resources hanterar enkel-apostrofer korrekt vid `\'` i Android-strings-format). Verifiera vid build.

- [ ] **Step 3: Spegla cleanup + adds i EN-strings**

Öppna `composeApp/src/commonMain/composeResources/values-en/strings.xml`. Radera samma keys som i Step 1. Lägg in:

```xml
<!-- Onboarding v2 (Plan 2026-05-25 onboarding-scroll-story) -->
<string name="onboarding_s1_eyebrow">FIELD COMPANION · NO 1</string>
<string name="onboarding_s1_headline">*Birdy.*</string>
<string name="onboarding_s1_sub">Identify birds in the field — photo or sound, on-device, no internet.</string>

<string name="onboarding_s2_eyebrow">PHOTO · NO 2</string>
<string name="onboarding_s2_headline">*Aim*. Tap. Stamp.</string>
<string name="onboarding_s2_sub">On-device AI recognizes 839 European species in seconds.</string>
<string name="onboarding_s2_match_pill">MATCH</string>
<string name="onboarding_s2_species_demo">Great Tit</string>

<string name="onboarding_s3_eyebrow">SOUND · NO 3</string>
<string name="onboarding_s3_headline">Or — *listen*.</string>
<string name="onboarding_s3_sub">3 seconds of recording. ID the song. Free, always.</string>
<string name="onboarding_s3_free_pill">FREE</string>
<string name="onboarding_s3_species_demo">Great Tit — 94%</string>

<string name="onboarding_s4_eyebrow">THE JOURNAL · NO 4</string>
<string name="onboarding_s4_headline">Every find — *a page*.</string>
<string name="onboarding_s4_sub">Your collection grows with every bird. PDF export when you want.</string>
<string name="onboarding_s4_marginalia">May 16 · Stockholm</string>

<string name="onboarding_s5_eyebrow">BADGES · NO 5</string>
<string name="onboarding_s5_headline">*Earn* badges.</string>
<string name="onboarding_s5_sub">Plenty of milestones to chase. Keep your streak alive.</string>

<string name="onboarding_s6_eyebrow">PRIVACY · NO 6</string>
<string name="onboarding_s6_headline">*Stays* on your device.</string>
<string name="onboarding_s6_sub">No accounts. No cloud sync. The journal is yours.</string>

<string name="onboarding_s7_eyebrow">YOUR NAME · NO 7</string>
<string name="onboarding_s7_headline">*Who* writes?</string>
<string name="onboarding_s7_sub">Shown in your journal.</string>
<string name="onboarding_s7_input_placeholder">Alex</string>
<string name="onboarding_s7_input_helper">Leave blank if you\'d rather — we\'ll call you \'Field birder\'.</string>
<string name="onboarding_s7_cta">*Begin* the journal</string>

<string name="onboarding_close_replay">Close</string>
<string name="settings_show_intro_again">Show intro again</string>
```

- [ ] **Step 4: Bygg för att verifiera strings.xml parsar**

Run: `JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10" PATH="$JAVA_HOME/bin:$PATH" ./gradlew :composeApp:generateComposeResClass`
Expected: BUILD SUCCESSFUL. Inga "duplicate resource" eller XML-parse-fel.

Detta steg kommer också att förstöra `OnboardingScreen.kt`-kompilering (alla `Res.string.onboarding_journal_p1_body1` etc. försvinner). Det är OK — Task 3 skriver om filen.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "strings(onboarding): rensa Plan 7a-strings + lägg in 26 v2-scen-strängar × 2 locales"
```

---

## Task 2: `OnboardingViewModel` — `isReplay` + `MAX_PAGE_INDEX = 6`

TDD: skriv tester för replay-mode + utökat sidintervall först, sen implementera.

**Files:**
- Modify: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/onboarding/OnboardingViewModelTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/OnboardingViewModel.kt`

- [ ] **Step 1: Skriv failing tests**

Lägg in i `OnboardingViewModelTest.kt` (efter sista existerande `@Test`):

```kotlin
    @Test
    fun `setPageIndex moves to page 6`() =
        runTest {
            val vm = OnboardingViewModel(InMemoryUserPreferences(), "Min")
            vm.setPageIndex(6)
            vm.state.test {
                val s = awaitItem()
                assertTrue(s is OnboardingUiState.Visible)
                assertEquals(6, s.pageIndex)
            }
        }

    @Test
    fun `setPageIndex coerces 7 to 6 (MAX_PAGE_INDEX)`() =
        runTest {
            val vm = OnboardingViewModel(InMemoryUserPreferences(), "Min")
            vm.setPageIndex(7)
            vm.state.test {
                val s = awaitItem()
                assertTrue(s is OnboardingUiState.Visible)
                assertEquals(6, s.pageIndex)
            }
        }

    @Test
    fun `replay mode does not write hasSeenOnboarding on complete`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            // simulate user has already seen onboarding before replay
            prefs.setHasSeenOnboarding(true)
            prefs.setUserName("Albin")
            val vm = OnboardingViewModel(prefs, defaultFallbackName = "Min", isReplay = true)
            vm.onNameChange("Ignored")
            vm.complete()
            prefs.userName.test { assertEquals("Albin", awaitItem()) } // unchanged
            prefs.hasSeenOnboarding.test { assertEquals(true, awaitItem()) }
        }

    @Test
    fun `replay mode still transitions state to Done`() =
        runTest {
            val vm = OnboardingViewModel(InMemoryUserPreferences(), "Min", isReplay = true)
            vm.complete()
            vm.state.test {
                assertEquals(OnboardingUiState.Done, awaitItem())
            }
        }
```

- [ ] **Step 2: Kör tester för att verifiera de failar**

Run: `JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10" PATH="$JAVA_HOME/bin:$PATH" ./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.onboarding.OnboardingViewModelTest"`
Expected: FAIL — de fyra nya testerna kompilerar inte (constructor `isReplay`-param + `setPageIndex(6)` coercas felaktigt till 2 idag).

- [ ] **Step 3: Implementera `isReplay` + utöka `MAX_PAGE_INDEX`**

Öppna `OnboardingViewModel.kt`. Ersätt:

```kotlin
class OnboardingViewModel(
    private val prefs: UserPreferences,
    private val defaultFallbackName: String,
) : ViewModel() {
```

med:

```kotlin
class OnboardingViewModel(
    private val prefs: UserPreferences,
    private val defaultFallbackName: String,
    private val isReplay: Boolean = false,
) : ViewModel() {
```

Ersätt `fun complete()`-blocket:

```kotlin
    fun complete() {
        val current = _state.value
        if (current !is OnboardingUiState.Visible) return
        val trimmed = current.nameInput.trim()
        val resolvedName = trimmed.ifEmpty { defaultFallbackName }
        viewModelScope.launch {
            prefs.setUserName(resolvedName)
            prefs.setHasSeenOnboarding(true)
            _state.value = OnboardingUiState.Done
        }
    }
```

med:

```kotlin
    fun complete() {
        val current = _state.value
        if (current !is OnboardingUiState.Visible) return
        val trimmed = current.nameInput.trim()
        val resolvedName = trimmed.ifEmpty { defaultFallbackName }
        viewModelScope.launch {
            if (!isReplay) {
                prefs.setUserName(resolvedName)
                prefs.setHasSeenOnboarding(true)
            }
            _state.value = OnboardingUiState.Done
        }
    }
```

Ändra `MAX_PAGE_INDEX`:

```kotlin
    private companion object {
        const val MAX_PAGE_INDEX = 6 // 7 pages: 0, 1, 2, 3, 4, 5, 6
    }
```

- [ ] **Step 4: Kör tester för att verifiera de passar**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.onboarding.OnboardingViewModelTest"`
Expected: PASS — alla 9 tester (5 gamla + 4 nya).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/OnboardingViewModel.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/ui/onboarding/OnboardingViewModelTest.kt
git commit -m "feat(onboarding): isReplay-flagg + MAX_PAGE_INDEX 2→6 (4 nya unit-tester)"
```

---

## Task 3: `AppGraph.onboardingViewModel` — propagera `isReplay`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt:300-301`

- [ ] **Step 1: Lägg till `isReplay`-parameter i factory**

Ersätt:

```kotlin
    fun onboardingViewModel(fallbackName: String): OnboardingViewModel =
        OnboardingViewModel(prefs = userPreferences, defaultFallbackName = fallbackName)
```

med:

```kotlin
    fun onboardingViewModel(fallbackName: String, isReplay: Boolean = false): OnboardingViewModel =
        OnboardingViewModel(prefs = userPreferences, defaultFallbackName = fallbackName, isReplay = isReplay)
```

- [ ] **Step 2: Verifiera AppGate fortfarande kompilerar (default-param = false)**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL (AppGate-anropet `graph.onboardingViewModel(fallback)` får default `isReplay = false`).

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt
git commit -m "feat(di): AppGraph.onboardingViewModel exponerar isReplay-param"
```

---

## Task 4: `IntroSceneScaffold` — gemensam scen-layout

Bygg scaffolden som scen 1–6 använder. Scen 7 har egen layout (interaktiv form) så använder inte scaffolden.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/components/IntroSceneScaffold.kt`

- [ ] **Step 1: Skapa filen med fullständig implementation**

```kotlin
package se.birdy.app.ui.onboarding.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.components.JournalIntro
import kotlin.math.absoluteValue

/**
 * Wrapper för Onboarding v2-scener 1–6. Standardiserar eyebrow + JournalHeadline
 * + sub + visualSlot, och drar fade + parallax baserat på [pageOffset] som
 * kommer från `pagerState.currentPageOffsetFraction` + (currentPage - thisPage).
 */
@Composable
fun IntroSceneScaffold(
    eyebrow: String,
    headline: String,
    sub: String,
    pageOffset: Float,
    modifier: Modifier = Modifier,
    visual: @Composable () -> Unit,
) {
    val abs = pageOffset.absoluteValue.coerceIn(0f, 1f)
    val visibility = 1f - abs
    val parallaxPx = (pageOffset * 80f) // px-skala — Compose layout-modifier converterar med density

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .alpha(visibility)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(0, parallaxPx.toInt())
                    }
                }
                .padding(horizontal = 0.dp, vertical = 0.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        JournalIntro(
            label = eyebrow,
            headline = headline,
            sub = sub,
            headlineFontSize = 36.sp,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            visual()
        }
    }
}
```

- [ ] **Step 2: Verifiera filen kompilerar**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/components/IntroSceneScaffold.kt
git commit -m "feat(onboarding): IntroSceneScaffold — gemensam scen-layout med fade + parallax"
```

---

## Task 5: Refactor `OnboardingScreen.kt` — `VerticalPager` + 7 placeholder-scener

Skriv om huvudfilen. Skene-implementationerna är placeholders i denna task; nästa tasks fyller in dem.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/OnboardingScreen.kt` (rewrite)

- [ ] **Step 1: Ersätt hela filinnehållet**

```kotlin
package se.birdy.app.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_close_replay
import birdy_bird_scanner.composeapp.generated.resources.onboarding_skip
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.PlatformBackHandler
import se.birdy.app.ui.onboarding.scenes.SceneAudio
import se.birdy.app.ui.onboarding.scenes.SceneBadges
import se.birdy.app.ui.onboarding.scenes.SceneHero
import se.birdy.app.ui.onboarding.scenes.SceneJournal
import se.birdy.app.ui.onboarding.scenes.SceneName
import se.birdy.app.ui.onboarding.scenes.ScenePhoto
import se.birdy.app.ui.onboarding.scenes.ScenePrivacy
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.paperBackground

private const val SCENE_COUNT = 7

@Composable
fun OnboardingScreen(
    state: OnboardingUiState.Visible,
    onPageChange: (Int) -> Unit,
    onNameChange: (String) -> Unit,
    onComplete: () -> Unit,
    isReplay: Boolean = false,
) {
    val pagerState =
        rememberPagerState(initialPage = state.pageIndex, pageCount = { SCENE_COUNT })

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onPageChange(it) }
    }
    LaunchedEffect(state.pageIndex) {
        if (pagerState.currentPage != state.pageIndex) {
            pagerState.animateScrollToPage(state.pageIndex)
        }
    }

    PlatformBackHandler(enabled = state.pageIndex > 0) {
        onPageChange(state.pageIndex - 1)
    }

    Box(modifier = Modifier.fillMaxSize().paperBackground().statusBarsPadding()) {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val pageOffset =
                (pagerState.currentPage - page).toFloat() + pagerState.currentPageOffsetFraction
            when (page) {
                0 -> SceneHero(pageOffset = pageOffset)
                1 -> ScenePhoto(pageOffset = pageOffset, isActive = pagerState.currentPage == 1)
                2 -> SceneAudio(pageOffset = pageOffset, isActive = pagerState.currentPage == 2)
                3 -> SceneJournal(pageOffset = pageOffset, isActive = pagerState.currentPage == 3)
                4 -> SceneBadges(pageOffset = pageOffset, isActive = pagerState.currentPage == 4)
                5 -> ScenePrivacy(pageOffset = pageOffset, isActive = pagerState.currentPage == 5)
                6 ->
                    SceneName(
                        nameInput = state.nameInput,
                        onNameChange = onNameChange,
                        onComplete = onComplete,
                    )
            }
        }

        TextButton(
            onClick = onComplete,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp),
        ) {
            Text(
                text =
                    stringResource(
                        if (isReplay) Res.string.onboarding_close_replay else Res.string.onboarding_skip,
                    ),
                color = MarginaliaInk.copy(alpha = 0.7f),
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
            )
        }

        PagerDots(
            currentPage = pagerState.currentPage,
            pageCount = SCENE_COUNT,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        bottom = if (pagerState.currentPage == SCENE_COUNT - 1) 148.dp else 28.dp,
                    ),
        )
    }
}

@Composable
private fun PagerDots(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(pageCount) { i ->
            val width by animateDpAsState(if (i == currentPage) 18.dp else 6.dp, label = "dot-width")
            Box(
                modifier =
                    Modifier
                        .size(width = width, height = 6.dp)
                        .clip(CircleShape)
                        .background(if (i == currentPage) AccentCopper else MarginaliaInk.copy(alpha = 0.25f)),
            )
        }
    }
}
```

- [ ] **Step 2: Skapa placeholder-scen-filer**

Skapa 7 placeholder-filer som bara renderar IntroSceneScaffold med strängarna. Nästa tasks ersätter dem en-i-taget med riktiga animationer. Skapa varje fil enligt mönstret nedan; säkerställ att filen kompilerar genom att inkludera ALLA imports som behövs.

`composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneHero.kt`:

```kotlin
package se.birdy.app.ui.onboarding.scenes

import androidx.compose.runtime.Composable
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s1_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s1_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s1_sub
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.onboarding.components.IntroSceneScaffold

@Composable
fun SceneHero(pageOffset: Float) {
    IntroSceneScaffold(
        eyebrow = stringResource(Res.string.onboarding_s1_eyebrow),
        headline = stringResource(Res.string.onboarding_s1_headline),
        sub = stringResource(Res.string.onboarding_s1_sub),
        pageOffset = pageOffset,
    ) {
        // Placeholder — Task 6 fyller in wordmark + fade-up
    }
}
```

Skapa motsvarande placeholders för:
- `ScenePhoto.kt` — signatur `fun ScenePhoto(pageOffset: Float, isActive: Boolean)`, använder `onboarding_s2_*`
- `SceneAudio.kt` — `(pageOffset: Float, isActive: Boolean)`, `onboarding_s3_*`
- `SceneJournal.kt` — `(pageOffset: Float, isActive: Boolean)`, `onboarding_s4_*`
- `SceneBadges.kt` — `(pageOffset: Float, isActive: Boolean)`, `onboarding_s5_*`
- `ScenePrivacy.kt` — `(pageOffset: Float, isActive: Boolean)`, `onboarding_s6_*`
- `SceneName.kt` — `fun SceneName(nameInput: String, onNameChange: (String) -> Unit, onComplete: () -> Unit)`. Använder INTE IntroSceneScaffold. Initial placeholder: rendera `JournalIntro` med eyebrow/headline/sub + en `Text` ("Placeholder för name-input") inuti en `Box(Modifier.fillMaxSize())` med `verticalArrangement = Arrangement.Center` (kopiera mönstret från befintliga Page3Name innan refactor).

För SceneName, placeholder-kod:

```kotlin
package se.birdy.app.ui.onboarding.scenes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s7_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s7_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s7_sub
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalIntro

@Composable
fun SceneName(
    nameInput: String,
    onNameChange: (String) -> Unit,
    onComplete: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        JournalIntro(
            label = stringResource(Res.string.onboarding_s7_eyebrow),
            headline = stringResource(Res.string.onboarding_s7_headline),
            sub = stringResource(Res.string.onboarding_s7_sub),
        )
        Text("Placeholder för name-input — fylls i Task 12")
    }
}
```

- [ ] **Step 3: Bygg + verifiera**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/OnboardingScreen.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/
git commit -m "feat(onboarding): VerticalPager + 7 placeholder-scener (Hero/Photo/Audio/Journal/Badges/Privacy/Name)"
```

---

## Task 6: Scen 1 — Hero (wordmark + fade-up)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneHero.kt` (rewrite)

- [ ] **Step 1: Implementera Hero-scen**

Ersätt filinnehållet:

```kotlin
package se.birdy.app.ui.onboarding.scenes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s1_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s1_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s1_sub
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.onboarding.components.IntroSceneScaffold

@OptIn(ExperimentalResourceApi::class)
@Composable
fun SceneHero(pageOffset: Float) {
    IntroSceneScaffold(
        eyebrow = stringResource(Res.string.onboarding_s1_eyebrow),
        headline = stringResource(Res.string.onboarding_s1_headline),
        sub = stringResource(Res.string.onboarding_s1_sub),
        pageOffset = pageOffset,
    ) {
        AsyncImage(
            model = Res.getUri("files/branding/wordmark.png"),
            contentDescription = "Birdy",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 64.dp),
            contentScale = ContentScale.Fit,
        )
    }
}
```

Wordmark fade:as redan automatiskt via `IntroSceneScaffold`'s `alpha = visibility`. Den explicita "skriv-in"-effekt som specificerades som "alternativ" i specen lämnas till device-verify-runda — om den känns för tråkig kan vi addera per-char-stagger i en T-task då.

- [ ] **Step 2: Bygg + verifiera**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneHero.kt
git commit -m "feat(onboarding): scen 1 Hero — wordmark + fade-up via IntroSceneScaffold"
```

---

## Task 7: Scen 2 — Foto (kamera-frame + plate-foto + StampSeal slam)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/ScenePhoto.kt` (rewrite)

- [ ] **Step 1: Implementera Foto-scen**

Ersätt filinnehållet. Talgoxe (Parus major) har QID `Q25485` och plate-foton finns redan i `:asset-pack/images/Q25485/hero.webp` (verifierad). `speciesImageUri()` (expect/actual i `se.birdy.app.util`) returnerar Coil-loadable URI.

```kotlin
package se.birdy.app.ui.onboarding.scenes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s2_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s2_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s2_match_pill
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s2_species_demo
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s2_sub
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.PlateFrame
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.onboarding.components.IntroSceneScaffold
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.util.speciesImageUri

@Composable
fun ScenePhoto(pageOffset: Float, isActive: Boolean) {
    val photoAlpha = remember { Animatable(0f) }
    val stampScale = remember { Animatable(0f) }
    val stampRotation = remember { Animatable(-8f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            photoAlpha.snapTo(0f)
            stampScale.snapTo(0f)
            stampRotation.snapTo(-8f)
            // 400ms photo fade-in
            photoAlpha.animateTo(1f, animationSpec = tween(durationMillis = 400))
            // 500ms stamp slam (scale 0 → 1.1 → 1.0 + rotation -8° → 0°)
            stampScale.animateTo(1.1f, animationSpec = tween(durationMillis = 300))
            stampScale.animateTo(1.0f, animationSpec = tween(durationMillis = 200))
            stampRotation.animateTo(0f, animationSpec = tween(durationMillis = 500))
        } else {
            photoAlpha.snapTo(0f)
            stampScale.snapTo(0f)
            stampRotation.snapTo(-8f)
        }
    }

    val speciesName = stringResource(Res.string.onboarding_s2_species_demo)
    val matchPill = stringResource(Res.string.onboarding_s2_match_pill)

    IntroSceneScaffold(
        eyebrow = stringResource(Res.string.onboarding_s2_eyebrow),
        headline = stringResource(Res.string.onboarding_s2_headline),
        sub = stringResource(Res.string.onboarding_s2_sub),
        pageOffset = pageOffset,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Plate-foto
            Box(modifier = Modifier.alpha(photoAlpha.value)) {
                PlateFrame(plateLabel = "I", captionLine = "$speciesName, in nature") {
                    AsyncImage(
                        model = speciesImageUri("Q25485/hero.webp"),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            // Camera-frame brackets overlay
            Canvas(modifier = Modifier.fillMaxSize().padding(28.dp)) {
                val s = 18.dp.toPx()
                val sw = 2.5.dp.toPx()
                val w = size.width
                val h = size.height
                val c = AccentCopper
                // 4 corner brackets
                drawLine(c, Offset(0f, 0f), Offset(s, 0f), sw)
                drawLine(c, Offset(0f, 0f), Offset(0f, s), sw)
                drawLine(c, Offset(w, 0f), Offset(w - s, 0f), sw)
                drawLine(c, Offset(w, 0f), Offset(w, s), sw)
                drawLine(c, Offset(0f, h), Offset(s, h), sw)
                drawLine(c, Offset(0f, h), Offset(0f, h - s), sw)
                drawLine(c, Offset(w, h), Offset(w - s, h), sw)
                drawLine(c, Offset(w, h), Offset(w, h - s), sw)
                // Crosshair circle in middle
                drawCircle(
                    color = c,
                    radius = 12.dp.toPx(),
                    center = Offset(w / 2f, h / 2f),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            // Slam stamp
            Box(
                modifier =
                    Modifier
                        .scale(stampScale.value)
                        .rotate(stampRotation.value),
            ) {
                StampSeal(
                    state = StampSealState.Unlocked(number = 1, glyph = null, name = matchPill),
                )
            }
        }
    }
}
```

OBS om plate-foto är ekvivalent med Stockholm-skuggig grön bg och inte syns bra: byt `speciesImageUri("Q25485/hero.webp")` till `speciesImageUri("Q25435/hero.webp")` (Domherre / Pyrrhula pyrrhula, dramatisk röd-svart-grå-färgsättning) som fallback. Verifiera vid device-test (Task 16).

- [ ] **Step 2: Bygg + verifiera**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/ScenePhoto.kt
git commit -m "feat(onboarding): scen 2 Foto — kamera-frame + Talgoxe-plate + StampSeal slam"
```

---

## Task 8: `WaveformBars` component + Scen 3 — Ljud

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/components/WaveformBars.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneAudio.kt` (rewrite)

- [ ] **Step 1: Skapa WaveformBars**

```kotlin
package se.birdy.app.ui.onboarding.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.theme.AccentCopper
import kotlin.math.abs
import kotlin.math.sin

private const val BAR_COUNT = 16

/**
 * 16 vertikala bars med sinus-baserad amplitud. När [active] är true rör de sig
 * via rememberInfiniteTransition; när false står de stilla på minimi-amplitud.
 */
@Composable
fun WaveformBars(
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "waveform-phase",
    )
    Canvas(modifier = modifier.height(64.dp)) {
        val totalWidth = size.width
        val barWidth = totalWidth / (BAR_COUNT * 2f)
        val gap = barWidth
        val midY = size.height / 2f
        val maxAmp = size.height * 0.4f
        val minAmp = size.height * 0.08f
        for (i in 0 until BAR_COUNT) {
            val amp =
                if (active) {
                    minAmp + abs(sin(phase + i * 0.4f)) * (maxAmp - minAmp)
                } else {
                    minAmp
                }
            val x = i * (barWidth + gap) + barWidth / 2f
            drawLine(
                color = AccentCopper,
                start = Offset(x, midY - amp),
                end = Offset(x, midY + amp),
                strokeWidth = barWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}
```

- [ ] **Step 2: Implementera SceneAudio**

```kotlin
package se.birdy.app.ui.onboarding.scenes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s3_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s3_free_pill
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s3_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s3_species_demo
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s3_sub
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.onboarding.components.IntroSceneScaffold
import se.birdy.app.ui.onboarding.components.WaveformBars
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.TextOnCreme

@Composable
fun SceneAudio(pageOffset: Float, isActive: Boolean) {
    val micScale = remember { Animatable(0f) }
    val ringSweep = remember { Animatable(0f) }
    val labelAlpha = remember { Animatable(0f) }
    val pillScale = remember { Animatable(0f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            micScale.snapTo(0f)
            ringSweep.snapTo(0f)
            labelAlpha.snapTo(0f)
            pillScale.snapTo(0f)
            micScale.animateTo(1f, tween(300))
            ringSweep.animateTo(360f, tween(3000))
            labelAlpha.animateTo(1f, tween(400))
            pillScale.animateTo(1.05f, tween(220))
            pillScale.animateTo(1.0f, tween(130))
        } else {
            micScale.snapTo(0f)
            ringSweep.snapTo(0f)
            labelAlpha.snapTo(0f)
            pillScale.snapTo(0f)
        }
    }

    IntroSceneScaffold(
        eyebrow = stringResource(Res.string.onboarding_s3_eyebrow),
        headline = stringResource(Res.string.onboarding_s3_headline),
        sub = stringResource(Res.string.onboarding_s3_sub),
        pageOffset = pageOffset,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            // Mic + countdown ring
            Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(96.dp)) {
                    drawArc(
                        color = AccentCopper,
                        startAngle = -90f,
                        sweepAngle = ringSweep.value,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .size(64.dp)
                            .scale(micScale.value)
                            .clip(CircleShape)
                            .background(AccentCopper.copy(alpha = 0.12f))
                            .border(1.dp, AccentCopper, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = AccentCopper,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            // Waveform
            WaveformBars(active = isActive, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
            // Species name
            Text(
                text = stringResource(Res.string.onboarding_s3_species_demo),
                color = TextOnCreme,
                fontSize = 18.sp,
                fontWeight = FontWeight.W500,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.alpha(labelAlpha.value),
            )
            // GRATIS pill
            Box(
                modifier =
                    Modifier
                        .scale(pillScale.value)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AccentCopper)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(Res.string.onboarding_s3_free_pill),
                    color = OffwhiteWarm,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
```

- [ ] **Step 3: Bygg + verifiera**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/components/WaveformBars.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneAudio.kt
git commit -m "feat(onboarding): scen 3 Ljud — mic + WaveformBars + countdown-ring + GRATIS-pill"
```

---

## Task 9: Scen 4 — Fältboken

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneJournal.kt` (rewrite)

- [ ] **Step 1: Implementera SceneJournal**

```kotlin
package se.birdy.app.ui.onboarding.scenes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s4_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s4_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s4_marginalia
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s4_sub
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.onboarding.components.IntroSceneScaffold
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.PaperTop
import se.birdy.app.ui.theme.rememberCaveat

@Composable
fun SceneJournal(pageOffset: Float, isActive: Boolean) {
    val pageAlpha = remember { Animatable(0f) }
    val pageOffsetY = remember { Animatable(32f) }
    val existingStamp1Alpha = remember { Animatable(0f) }
    val existingStamp2Alpha = remember { Animatable(0f) }
    val newStampScale = remember { Animatable(0f) }
    val marginaliaAlpha = remember { Animatable(0f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            pageAlpha.snapTo(0f)
            pageOffsetY.snapTo(32f)
            existingStamp1Alpha.snapTo(0f)
            existingStamp2Alpha.snapTo(0f)
            newStampScale.snapTo(0f)
            marginaliaAlpha.snapTo(0f)
            pageAlpha.animateTo(1f, tween(500))
            pageOffsetY.animateTo(0f, tween(500))
            existingStamp1Alpha.animateTo(0.6f, tween(300))
            existingStamp2Alpha.animateTo(0.6f, tween(300))
            newStampScale.animateTo(1.1f, tween(300))
            newStampScale.animateTo(1.0f, tween(200))
            marginaliaAlpha.animateTo(1f, tween(700))
        } else {
            listOf(pageAlpha, existingStamp1Alpha, existingStamp2Alpha, newStampScale, marginaliaAlpha)
                .forEach { it.snapTo(0f) }
            pageOffsetY.snapTo(32f)
        }
    }

    val caveat = rememberCaveat()

    IntroSceneScaffold(
        eyebrow = stringResource(Res.string.onboarding_s4_eyebrow),
        headline = stringResource(Res.string.onboarding_s4_headline),
        sub = stringResource(Res.string.onboarding_s4_sub),
        pageOffset = pageOffset,
    ) {
        // Journal page mockup
        Box(
            modifier =
                Modifier
                    .padding(horizontal = 32.dp)
                    .offset(y = pageOffsetY.value.dp)
                    .alpha(pageAlpha.value)
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(PaperTop)
                    .border(1.dp, MarginaliaInk.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                    .padding(20.dp),
        ) {
            // Pre-existing stamps (top-left + bottom-right)
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .alpha(existingStamp1Alpha.value)
                        .rotate(-6f),
            ) {
                StampSeal(
                    state = StampSealState.Unlocked(number = 12, glyph = null, name = null),
                    size = 56.dp,
                )
            }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .alpha(existingStamp2Alpha.value)
                        .rotate(4f),
            ) {
                StampSeal(
                    state = StampSealState.Unlocked(number = 27, glyph = null, name = null),
                    size = 56.dp,
                )
            }
            // Slam stamp in middle
            Box(
                modifier = Modifier.align(Alignment.Center).scale(newStampScale.value),
            ) {
                StampSeal(
                    state = StampSealState.Unlocked(number = 28, glyph = null, name = null),
                    size = 88.dp,
                )
            }
            // Marginalia date
            Row(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .alpha(marginaliaAlpha.value),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.width(2.dp).height(20.dp).background(AccentCopper))
                Text(
                    text = "  ${stringResource(Res.string.onboarding_s4_marginalia)}",
                    color = MarginaliaInk,
                    fontFamily = caveat,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Bygg + verifiera**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneJournal.kt
git commit -m "feat(onboarding): scen 4 Fältboken — page-mockup + 2 pre-stamps + slam + Caveat-marginalia"
```

---

## Task 10: `StreakCounter` component + Scen 5 — Märken

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/components/StreakCounter.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneBadges.kt` (rewrite)

- [ ] **Step 1: Skapa StreakCounter**

```kotlin
package se.birdy.app.ui.onboarding.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.rememberDmSerifDisplay

/**
 * Animerar 0 → [target] när [trigger] flippar till true. Använder DM Serif Italic.
 */
@Composable
fun StreakCounter(
    target: Int,
    trigger: Boolean,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 48.sp,
) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger) {
            animated.snapTo(0f)
            animated.animateTo(target.toFloat(), tween(durationMillis = 800))
        } else {
            animated.snapTo(0f)
        }
    }
    val display = animated.value.toInt()
    Text(
        text = "🔥 $display",
        color = AccentCopper,
        fontFamily = rememberDmSerifDisplay(),
        fontStyle = FontStyle.Italic,
        fontSize = fontSize,
        modifier = modifier,
    )
}
```

- [ ] **Step 2: Implementera SceneBadges**

```kotlin
package se.birdy.app.ui.onboarding.scenes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s5_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s5_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s5_sub
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.onboarding.components.IntroSceneScaffold
import se.birdy.app.ui.onboarding.components.StreakCounter

@Composable
fun SceneBadges(pageOffset: Float, isActive: Boolean) {
    val flipDegrees = remember { Animatable(0f) }
    val counterTrigger = remember { Animatable(0f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            flipDegrees.snapTo(0f)
            counterTrigger.snapTo(0f)
            kotlinx.coroutines.delay(300) // suspense
            flipDegrees.animateTo(180f, tween(500))
            counterTrigger.animateTo(1f, tween(50))
        } else {
            flipDegrees.snapTo(0f)
            counterTrigger.snapTo(0f)
        }
    }

    val showFront = flipDegrees.value < 90f

    IntroSceneScaffold(
        eyebrow = stringResource(Res.string.onboarding_s5_eyebrow),
        headline = stringResource(Res.string.onboarding_s5_headline),
        sub = stringResource(Res.string.onboarding_s5_sub),
        pageOffset = pageOffset,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Box(
                modifier = Modifier.graphicsLayer { rotationY = flipDegrees.value },
                contentAlignment = Alignment.Center,
            ) {
                if (showFront) {
                    StampSeal(state = StampSealState.Locked(name = null))
                } else {
                    Box(modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                        StampSeal(
                            state = StampSealState.Unlocked(number = 7, glyph = "❦", name = null),
                        )
                    }
                }
            }
            StreakCounter(target = 7, trigger = counterTrigger.value > 0.5f)
            Spacer(Modifier.height(8.dp))
        }
    }
}
```

- [ ] **Step 3: Bygg + verifiera**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/components/StreakCounter.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneBadges.kt
git commit -m "feat(onboarding): scen 5 Märken — locked→unlocked flip + StreakCounter 0→7"
```

---

## Task 11: `OfflineShield` component + Scen 6 — Privatliv

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/components/OfflineShield.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/ScenePrivacy.kt` (rewrite)

- [ ] **Step 1: Skapa OfflineShield**

```kotlin
package se.birdy.app.ui.onboarding.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import se.birdy.app.ui.theme.AccentCopper

/**
 * Ritar en "skydds-sköld"-outline runt scenens center. [progress] = 0..1 = hur
 * stor andel av path-omkretsen som ritats.
 */
@Composable
fun OfflineShield(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(w, h) * 0.42f

        // Shield-shape: rounded top, tapered bottom point
        val path = Path().apply {
            moveTo(cx, cy - radius)
            cubicTo(
                cx + radius * 0.9f, cy - radius,
                cx + radius * 1.1f, cy - radius * 0.2f,
                cx + radius * 0.85f, cy + radius * 0.3f,
            )
            cubicTo(
                cx + radius * 0.5f, cy + radius * 0.95f,
                cx, cy + radius,
                cx, cy + radius,
            )
            cubicTo(
                cx, cy + radius,
                cx - radius * 0.5f, cy + radius * 0.95f,
                cx - radius * 0.85f, cy + radius * 0.3f,
            )
            cubicTo(
                cx - radius * 1.1f, cy - radius * 0.2f,
                cx - radius * 0.9f, cy - radius,
                cx, cy - radius,
            )
            close()
        }

        val measure = PathMeasure().apply { setPath(path, false) }
        val totalLen = measure.length
        val drawLen = totalLen * progress.coerceIn(0f, 1f)
        val drawPath = Path()
        measure.getSegment(0f, drawLen, drawPath, true)

        drawPath(
            path = drawPath,
            color = AccentCopper,
            style = Stroke(width = 2.5.dp.toPx()),
        )
    }
}
```

- [ ] **Step 2: Implementera ScenePrivacy**

```kotlin
package se.birdy.app.ui.onboarding.scenes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s6_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s6_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s6_sub
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.onboarding.components.IntroSceneScaffold
import se.birdy.app.ui.onboarding.components.OfflineShield
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk

@Composable
fun ScenePrivacy(pageOffset: Float, isActive: Boolean) {
    val deviceAlpha = remember { Animatable(0f) }
    val lockScale = remember { Animatable(0f) }
    val shieldProgress = remember { Animatable(0f) }
    val wifiAlpha = remember { Animatable(0f) }
    val wifiStrokeProgress = remember { Animatable(0f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            listOf(deviceAlpha, lockScale, shieldProgress, wifiAlpha, wifiStrokeProgress).forEach { it.snapTo(0f) }
            deviceAlpha.animateTo(1f, tween(300))
            lockScale.animateTo(1.2f, tween(280))
            lockScale.animateTo(1.0f, tween(120))
            shieldProgress.animateTo(1f, tween(700))
            wifiAlpha.animateTo(0.6f, tween(200))
            wifiStrokeProgress.animateTo(1f, tween(200))
        } else {
            listOf(deviceAlpha, lockScale, shieldProgress, wifiAlpha, wifiStrokeProgress).forEach { it.snapTo(0f) }
        }
    }

    IntroSceneScaffold(
        eyebrow = stringResource(Res.string.onboarding_s6_eyebrow),
        headline = stringResource(Res.string.onboarding_s6_headline),
        sub = stringResource(Res.string.onboarding_s6_sub),
        pageOffset = pageOffset,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            // Device + lock + shield outline
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                OfflineShield(progress = shieldProgress.value, modifier = Modifier.size(160.dp))
                Box(modifier = Modifier.alpha(deviceAlpha.value)) {
                    Icon(
                        imageVector = Icons.Outlined.Smartphone,
                        contentDescription = null,
                        tint = MarginaliaInk,
                        modifier = Modifier.size(72.dp),
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .scale(lockScale.value)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(AccentCopper),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            // No-wifi
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.alpha(wifiAlpha.value)) {
                    Icon(
                        imageVector = Icons.Filled.WifiOff,
                        contentDescription = null,
                        tint = MarginaliaInk.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp),
                    )
                }
                // diagonal strike via rotated thin Box
                Box(
                    modifier =
                        Modifier
                            .size(width = (48.dp.value * wifiStrokeProgress.value).dp, height = 2.dp)
                            .rotate(35f)
                            .background(AccentCopper),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
```

- [ ] **Step 3: Bygg + verifiera**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/components/OfflineShield.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/ScenePrivacy.kt
git commit -m "feat(onboarding): scen 6 Privatliv — device + lock + OfflineShield + no-wifi strike"
```

---

## Task 12: Scen 7 — Namn + CTA

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneName.kt` (rewrite)

- [ ] **Step 1: Implementera SceneName**

```kotlin
package se.birdy.app.ui.onboarding.scenes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s7_cta
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s7_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s7_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s7_input_helper
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s7_input_placeholder
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s7_sub
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalHeadline
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.rememberCaveat

@Composable
fun SceneName(
    nameInput: String,
    onNameChange: (String) -> Unit,
    onComplete: () -> Unit,
) {
    val caveat = rememberCaveat()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        JournalIntro(
            label = stringResource(Res.string.onboarding_s7_eyebrow),
            headline = stringResource(Res.string.onboarding_s7_headline),
            sub = stringResource(Res.string.onboarding_s7_sub),
            headlineFontSize = 36.sp,
        )
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            OutlinedTextField(
                value = nameInput,
                onValueChange = onNameChange,
                placeholder = {
                    Text(
                        stringResource(Res.string.onboarding_s7_input_placeholder),
                        fontFamily = caveat,
                        fontSize = 18.sp,
                        color = MarginaliaInk.copy(alpha = 0.6f),
                    )
                },
                textStyle =
                    TextStyle(
                        fontFamily = caveat,
                        fontSize = 18.sp,
                        color = MarginaliaInk,
                    ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCopper.copy(alpha = 0.6f),
                        unfocusedBorderColor = AccentCopper.copy(alpha = 0.3f),
                        focusedContainerColor = Color.White.copy(alpha = 0.4f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.4f),
                    ),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.onboarding_s7_input_helper),
                color = MarginaliaInk.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = AccentCopper,
                        contentColor = OffwhiteWarm,
                    ),
                shape = RoundedCornerShape(12.dp),
            ) {
                JournalHeadline(
                    text = stringResource(Res.string.onboarding_s7_cta),
                    fontSize = 20.sp,
                    plainColor = OffwhiteWarm,
                    accentColor = OffwhiteWarm,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Bygg + verifiera**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneName.kt
git commit -m "feat(onboarding): scen 7 Namn — name-input + Caveat-placeholder + AccentCopper CTA"
```

---

## Task 13: `AppRoute.OnboardingReplay` + route i `AppScaffold`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt`

- [ ] **Step 1: Lägg till route i `AppRoute`**

I `AppRoute.kt`, lägg in efter `@Serializable data object About : AppRoute`:

```kotlin
    @Serializable data object OnboardingReplay : AppRoute
```

- [ ] **Step 2: Lägg in NavHost-entry i `AppScaffold`**

I `AppScaffold.kt`, lägg in efter `composable<AppRoute.About> { ... }`-blocket (linje ~228):

```kotlin
            composable<AppRoute.OnboardingReplay> {
                val fallback = stringResource(Res.string.onboarding_p3_fallback_name)
                val vm = remember(graph) { graph.onboardingViewModel(fallback, isReplay = true) }
                val state by vm.state.collectAsState()
                when (val s = state) {
                    is se.birdy.app.ui.onboarding.OnboardingUiState.Visible ->
                        se.birdy.app.ui.onboarding.OnboardingScreen(
                            state = s,
                            onPageChange = vm::setPageIndex,
                            onNameChange = vm::onNameChange,
                            onComplete = { navController.popBackStack() },
                            isReplay = true,
                        )
                    se.birdy.app.ui.onboarding.OnboardingUiState.Done -> {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    }
                    se.birdy.app.ui.onboarding.OnboardingUiState.Loading -> Unit
                }
            }
```

Lägg till nödvändiga imports (`birdy_bird_scanner.composeapp.generated.resources.onboarding_p3_fallback_name`).

- [ ] **Step 3: Bygg + verifiera**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt
git commit -m "feat(nav): AppRoute.OnboardingReplay + composable-entry med isReplay=true"
```

---

## Task 14: Settings — "Visa introduktion igen"-rad

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt` — pass `onShowIntroAgain` callback

- [ ] **Step 1: Lägg till `onShowIntroAgain`-callback i `SettingsScreen`**

I `SettingsScreen.kt` linje ~106-113, ändra:

```kotlin
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onPremiumClick: () -> Unit,
    onNavigateToAbout: () -> Unit,
    versionName: String,
) {
```

till:

```kotlin
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onPremiumClick: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onShowIntroAgain: () -> Unit,
    versionName: String,
) {
```

- [ ] **Step 2: Lägg till SettingsRow i OM BIRDY-sektionen**

I `SettingsScreen.kt`, hitta blocket som visar "OM BIRDY"-sektionen (PaperCard som börjar runt linje 178). Lägg in en ny SettingsRow + DashedDivider före `SettingsRow` för "Restore Purchases" (`settings_restore_purchases`):

```kotlin
                    DashedDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Refresh,
                        label = stringResource(Res.string.settings_show_intro_again),
                        value = null,
                        onClick = onShowIntroAgain,
                    )
                    DashedDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Refresh,
                        label = stringResource(Res.string.settings_restore_purchases),
                        ...
```

Lägg in nödvändig import:

```kotlin
import birdy_bird_scanner.composeapp.generated.resources.settings_show_intro_again
```

Använd en annan icon än `Refresh` för att inte krocka visuellt — välj `Icons.Outlined.Slideshow` (om tillgänglig i Material-iconlibrary) eller `Icons.AutoMirrored.Outlined.OpenInNew`. Föredra `Icons.Outlined.PlayCircleOutline` om det finns.

Verifiera vid bygg vilka icons som faktiskt finns; om ingen "play"-icon: använd `Icons.Outlined.Replay`:

```kotlin
import androidx.compose.material.icons.outlined.Replay
...
                    SettingsRow(
                        icon = Icons.Outlined.Replay,
                        label = stringResource(Res.string.settings_show_intro_again),
                        value = null,
                        onClick = onShowIntroAgain,
                    )
```

- [ ] **Step 3: Plumb callback från `AppScaffold`**

I `AppScaffold.kt` linje ~214-222, ändra `composable<AppRoute.Settings>`-blocket:

```kotlin
            composable<AppRoute.Settings> {
                se.birdy.app.ui.settings.SettingsScreen(
                    viewModel = remember(graph) { graph.settingsViewModel() },
                    onBack = { navController.popBackStack() },
                    onPremiumClick = { navController.navigate(AppRoute.Premium) },
                    onNavigateToAbout = { navController.navigate(AppRoute.About) },
                    onShowIntroAgain = { navController.navigate(AppRoute.OnboardingReplay) },
                    versionName = graph.versionName,
                )
            }
```

- [ ] **Step 4: Bygg + verifiera**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsScreen.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt
git commit -m "feat(settings): Visa introduktion igen-rad → navigerar till OnboardingReplay"
```

---

## Task 15: TalkBack semantics + a11y-pass

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/components/IntroSceneScaffold.kt`

- [ ] **Step 1: Lägg till `semantics`-modifier i IntroSceneScaffold**

I `IntroSceneScaffold.kt`, ändra Column-Modifier-chainen så att den inkluderar `semantics`:

```kotlin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
...
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .semantics(mergeDescendants = true) {
                    contentDescription = "$eyebrow. $headline. $sub."
                }
                .alpha(visibility)
                .layout { measurable, constraints ->
                    ...
```

Discrete animations (StampSeal slam, badge pop) lämnas dekorativa (deras egna composables har redan `contentDescription = null` eller mergeDescendants).

- [ ] **Step 2: Bygg + verifiera**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/components/IntroSceneScaffold.kt
git commit -m "feat(onboarding/a11y): TalkBack-semantics på IntroSceneScaffold (eyebrow + headline + sub)"
```

---

## Task 16: Lint + unit-tests grön + AppGate fortfarande funkar

Säkerhetskontroll mellan-step. Allt ska kompilera + ktlint/detekt grön + alla tester gröna.

- [ ] **Step 1: Kör full kvalitetspipeline**

Run: `JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10" PATH="$JAVA_HOME/bin:$PATH" ./gradlew ktlintCheck detekt :composeApp:testDebugUnitTest :shared:domain:jvmTest`

Expected: BUILD SUCCESSFUL. Alla 9 onboarding-tester gröna (5 ursprungliga + 4 nya).

- [ ] **Step 2: Om ktlint failar — autofixa**

Om `ktlintCheck` rapporterar overträdelser:

Run: `./gradlew ktlintFormat`

Re-run: `./gradlew ktlintCheck`
Expected: PASS.

- [ ] **Step 3: Om detekt failar — fixa manuellt**

Vanliga issues: TooManyFunctions, MagicNumber, LongMethod. Adjustera per fil. Ingen suppress utan kommentar.

- [ ] **Step 4: Commit endast om ktlintFormat ändrade filer**

```bash
git status
# Om diff: commit auto-format-ändringarna
git add -u
git commit -m "style: ktlintFormat onboarding-v2-filer"
```

---

## Task 17: Bygg + installera + device-verify + screenshots

Detta är manuell device-verify på SM-S918B. Krav: telefon kopplad via USB, ADB authorized.

- [ ] **Step 1: Bygg + installera debug**

Run: `JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10" PATH="$JAVA_HOME/bin:$PATH" ./gradlew :androidApp:installDebug`
Expected: APK installed.

- [ ] **Step 2: Reset DataStore för färsk onboarding-trigger**

```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell pm clear se.birdy.android
```

- [ ] **Step 3: Starta app + walka genom scener**

```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

För varje scen (1–7):
1. Vänta ~3s så animationen körs klart
2. Screenshot: `"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" exec-out screencap -p > "docs/superpowers/screenshots/onboarding-v2/0X-<scene>.png"` (X = sceniummer, scene = hero/photo/audio/journal/badges/privacy/name)
3. Swipa upp för att gå till nästa: `"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell input swipe 540 1500 540 500 200`

Filer:
- `01-hero.png`
- `02-photo.png`
- `03-audio.png`
- `04-journal.png`
- `05-badges.png`
- `06-privacy.png`
- `07-name.png`

- [ ] **Step 4: Verifiera CTA**

På scen 7, fyll in namnet "Test", tappa CTA:

```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell input tap 540 <cta-y>
```

Expected: navigerar till main-screen.

- [ ] **Step 5: Replay via Settings**

Tappa Settings-tab → bläddra till "OM BIRDY"-sektionen → tappa "Visa introduktion igen". Verifiera att onboarding-storyn visas igen. Skipp-knappen säger "Stäng".

Screenshot:
```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" exec-out screencap -p > "docs/superpowers/screenshots/onboarding-v2/08-settings-replay-row.png"
```

(Tag screenshot av Settings-skärmen med Replay-raden synlig, INTE av Onboarding igen.)

- [ ] **Step 6: Verifiera att replay INTE rör hasSeenOnboarding**

Efter replay-completion, kolla DataStore:

```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell run-as se.birdy.android cat "files/datastore/user_prefs.preferences_pb" | grep -a has_seen_onboarding
```

Expected: värdet är fortfarande true (oförändrat).

- [ ] **Step 7: Snabb-scroll-test (perf)**

Reset app igen (Step 2). Starta. Swipa snabbt 7 gånger i rad. Verifiera:
- Inga frame-drops synligt
- Inga crashes
- `adb shell dumpsys gfxinfo se.birdy.android | grep Janky` rapporterar <5%

- [ ] **Step 8: Commit screenshots**

```bash
git add docs/superpowers/screenshots/onboarding-v2/
git commit -m "docs(screenshots): onboarding v2 device-verify på SM-S918B (8 screenshots)"
```

---

## Task 18: versionCode bump + CLAUDE.md status-uppdatering

**Files:**
- Modify: `androidApp/build.gradle.kts`
- Modify: `CLAUDE.md`

- [ ] **Step 1: Bumpa versionCode + versionName**

I `androidApp/build.gradle.kts`, ändra:

```kotlin
        versionCode = 114
        versionName = "1.0.1"
```

till:

```kotlin
        versionCode = 115
        versionName = "1.0.2"
```

- [ ] **Step 2: Uppdatera CLAUDE.md status-sektion**

Lägg in ny statusrad högst upp i "Status (2026-05-25)"-sektionen i CLAUDE.md (eller skapa ny om datumet drivit):

```markdown
- **Onboarding v2 (2026-05-25):** 3-sidors Plan 7a-onboarding ersatt av 7-scens scroll-driven story (Hero/Foto/Ljud/Fältboken/Märken/Privatliv/Namn) via VerticalPager + pageOffset-driven animationer. Hybrid Field Journal-copy (poetisk DM Serif-headline + konkret USP-sub). 4 nya komponenter: IntroSceneScaffold, WaveformBars, StreakCounter, OfflineShield. Settings → "Visa introduktion igen" routes till replay-mode (`isReplay = true` skippar DataStore-writes). versionCode 114→115. Plan-doc: `docs/superpowers/plans/2026-05-25-onboarding-v2-scroll-story.md`.
```

- [ ] **Step 3: Bygg final release-AAB för smoke-test**

Run: `JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10" PATH="$JAVA_HOME/bin:$PATH" ./gradlew :androidApp:bundleRelease`
Expected: BUILD SUCCESSFUL. AAB i `androidApp/build/outputs/bundle/release/`.

- [ ] **Step 4: Final commit + tag**

```bash
git add androidApp/build.gradle.kts CLAUDE.md
git commit -m "release: v1.0.2 (versionCode 115) — onboarding v2 scroll-driven story"
git tag v1.0.2
```

- [ ] **Step 5: (Optional) Push**

Vänta med push tills user är klar med fysisk device-walk-through och godkänner. Då:

```bash
git push origin main --tags
```

---

## Acceptanskriterier

Spec är komplett implementerad när alla tasks T1–T18 är gröna OCH:

- [ ] `./gradlew :androidApp:installDebug ktlintCheck detekt :composeApp:testDebugUnitTest :shared:domain:jvmTest :shared:ml:jvmTest` är grön
- [ ] 7 scen-composables existerar i `ui/onboarding/scenes/`
- [ ] 4 nya hjälpkomponenter existerar i `ui/onboarding/components/`
- [ ] `OnboardingScreen.kt` använder `VerticalPager` med `SCENE_COUNT = 7`
- [ ] `OnboardingViewModel` har `isReplay`-flagg + `MAX_PAGE_INDEX = 6`
- [ ] `OnboardingViewModelTest` har 9 gröna tester (5 gamla + 4 nya)
- [ ] Settings har "Visa introduktion igen"-rad som routes till `AppRoute.OnboardingReplay`
- [ ] Skip-knappens label är "Stäng" i replay-mode, "Hoppa över" i first-run
- [ ] Alla 52 nya/ändrade strängar finns i `values/strings.xml` + `values-en/strings.xml`
- [ ] Alla gamla `onboarding_p1_*`, `onboarding_p2_*`, `onboarding_p3_*` (utom `_fallback_name`), `onboarding_journal_*` är raderade
- [ ] 8 device-screenshots i `docs/superpowers/screenshots/onboarding-v2/`
- [ ] versionCode bumpad till 115, versionName "1.0.2"
- [ ] CLAUDE.md status-sektion uppdaterad
- [ ] Replay-from-Settings verifierad: hasSeenOnboarding oförändrat efter replay-completion
