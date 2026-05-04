# Birdy Bird Scanner — Plan 3: Encyclopedia Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Encyclopedia browse screen + species profile screen on top of the existing `SpeciesRepository`, plus an app-shell with bottom-nav (4 tabs, 3 stubbed). End state: a Galaxy S23 Ultra installs the debug APK, sees a Mossbädd-themed bottom-nav with **Uppslagsverk** as default, browses 97+ species grouped by abundance with sök + filter, taps a row, lands on a species profile with collapsing toolbar, and sparse-data species (e.g. Sandlärka) render correctly with inline-empty-states.

**Architecture:** Single-module UI work in `composeApp/`. ViewModels via `lifecycle-viewmodel-compose` (KMP), navigation via Compose Multiplatform Navigation 2.x (type-safe routes), images via Coil 3 (KMP), state via `StateFlow`, manual DI via an `AppGraph` class. One small extension to `shared/content` (Task 4) makes `SqlDelightSpeciesRepository.search()` honor all `SpeciesFilter` fields and match scientific names.

**Tech Stack:** Kotlin 2.1.20, Compose Multiplatform 1.7.3, AndroidX Lifecycle 2.8.4 (KMP), AndroidX Navigation 2.8.0 (KMP), Coil 3.0.4, Turbine 1.1.0 (test), JUnit Jupiter 5.11.3, kotlinx-coroutines-test, SQLDelight 2.0.2.

> **Version note:** Lock these the day Task 1 starts. Bump patch versions only without re-reading the plan. Run `./gradlew --refresh-dependencies` if a resolution conflict appears.

---

## Plan-of-plans context

This is **Plan 3 of 6** for v1. It owns the Encyclopedia + species-profile screens plus the app-shell (bottom-nav skeleton). **Plan 2b** is paused at 97/700 species and resumes after Plan 3 ships. Plan 4 (ML & Camera) replaces the Skanna-stub. Plan 5 (Diary & Gamification) replaces the Dagbok + Märken stubs and adds a "Lägg till i dagboken"-CTA on the profile screen.

Plan 3 leaves the project buildable + CI-green at every commit. After Plan 3, `composeApp:assembleDebug` produces an APK that boots into a 4-tab bottom-nav, Uppslagsverk is default, EncyclopediaScreen lists committed species with sök + filter, profile screen pushes on tap with a collapsing toolbar.

Spec: `docs/superpowers/specs/2026-05-04-encyclopedia-design.md` (commit `f042d3d`).

---

## File structure created by this plan

```
birdy-bird-scanner/
├── composeApp/
│   ├── build.gradle.kts                                        # MODIFIED: new deps, commonTest, androidUnitTest
│   └── src/
│       ├── commonMain/
│       │   ├── composeResources/
│       │   │   └── values/
│       │   │       └── strings.xml                             # NEW (Task 10): Swedish defaults
│       │   │   └── values-en/
│       │   │       └── strings.xml                             # NEW (Task 10): English overrides
│       │   └── kotlin/se/birdy/app/
│       │       ├── App.kt                                      # MODIFIED: AppScaffold replaces HomeScreen
│       │       ├── di/
│       │       │   └── AppGraph.kt                             # NEW (Task 1)
│       │       ├── ui/
│       │       │   ├── HomeScreen.kt                           # DELETED (Task 2)
│       │       │   ├── theme/                                  # unchanged
│       │       │   ├── scaffold/
│       │       │   │   ├── AppRoute.kt                         # NEW (Task 2)
│       │       │   │   ├── AppScaffold.kt                      # NEW (Task 2)
│       │       │   │   ├── BottomNavBar.kt                     # NEW (Task 2)
│       │       │   │   ├── ScanStubScreen.kt                   # NEW (Task 2)
│       │       │   │   ├── DiaryStubScreen.kt                  # NEW (Task 2)
│       │       │   │   └── BadgesStubScreen.kt                 # NEW (Task 2)
│       │       │   ├── encyclopedia/
│       │       │   │   ├── EncyclopediaScreen.kt               # NEW (Task 3, 5, 6)
│       │       │   │   ├── EncyclopediaViewModel.kt            # NEW (Task 3, 5, 6)
│       │       │   │   ├── EncyclopediaUiState.kt              # NEW (Task 3)
│       │       │   │   ├── SpeciesRow.kt                       # NEW (Task 3)
│       │       │   │   └── FilterBottomSheet.kt                # NEW (Task 6)
│       │       │   ├── profile/
│       │       │   │   ├── SpeciesProfileScreen.kt             # NEW (Task 7, 8)
│       │       │   │   ├── SpeciesProfileViewModel.kt          # NEW (Task 7)
│       │       │   │   └── SpeciesProfileUiState.kt            # NEW (Task 7)
│       │       │   └── components/
│       │       │       ├── SectionBlock.kt                     # NEW (Task 8)
│       │       │       ├── HeroImage.kt                        # NEW (Task 9)
│       │       │       └── EmptyState.kt                       # NEW (Task 5)
│       ├── commonTest/                                         # NEW (Task 1)
│       │   └── kotlin/se/birdy/app/
│       │       ├── ui/encyclopedia/EncyclopediaViewModelTest.kt  # NEW (Task 5)
│       │       ├── ui/profile/SpeciesProfileViewModelTest.kt    # NEW (Task 7)
│       │       └── testing/FakeSpeciesRepository.kt             # NEW (Task 1)
│
├── shared/content/                                             # MODIFIED (Task 4)
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/se/birdy/content/SqlDelightSpeciesRepository.kt  # MODIFIED: filter regions/activeInMonth + scientific name
│       │   └── sqldelight/se/birdy/content/SpeciesName.sq      # MODIFIED: searchByNameOrScientific
│       └── jvmTest/kotlin/se/birdy/content/
│           └── SpeciesRepositoryTest.kt                        # MODIFIED: 4 new tests
│
├── gradle/
│   └── libs.versions.toml                                      # MODIFIED (Task 1)
│
└── docs/superpowers/screenshots/                               # MODIFIED (Task 11)
    ├── 2026-05-XX-bottom-nav.png
    ├── 2026-05-XX-encyclopedia-list.png
    ├── 2026-05-XX-encyclopedia-search.png
    ├── 2026-05-XX-encyclopedia-filter.png
    ├── 2026-05-XX-profile-talgoxe.png
    ├── 2026-05-XX-profile-sparse.png
    └── 2026-05-XX-profile-collapsed.png
```

---

## Naming notes (read once before Task 1)

- `SpeciesId` is `@JvmInline value class SpeciesId(val raw: String)` — use `id.raw`, NOT `id.value`.
- The spec uses `id.value` in §3.4 — that's a spec bug. Use `id.raw` consistently.
- Existing `composeApp.build.gradle.kts` has only `commonMain` + `androidMain`. Task 1 adds `commonTest` + `androidUnitTest` source sets.
- composeResources are referenced as `Res.string.foo` and `Res.getUri("files/images/${qId}/hero.jpg")` — see Compose Multiplatform 1.7 docs.

---

## Tasks

### Task 1: Foundation — dependencies + commonTest + AppGraph skeleton

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `composeApp/build.gradle.kts`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`
- Create: `composeApp/src/commonTest/kotlin/se/birdy/app/testing/FakeSpeciesRepository.kt`

- [ ] **Step 1: Add new entries to `gradle/libs.versions.toml`**

Append to `[versions]`:

```toml
androidx-lifecycle = "2.8.4"
androidx-navigation = "2.8.0"
coil = "3.0.4"
turbine = "1.1.0"
```

Append to `[libraries]`:

```toml
androidx-lifecycle-viewmodel-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "androidx-lifecycle" }
androidx-navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "androidx-navigation" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
```

- [ ] **Step 2: Wire deps + new source sets in `composeApp/build.gradle.kts`**

Replace the `kotlin { ... }` block with:

```kotlin
kotlin {
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(project(":shared:domain"))
            implementation(project(":shared:data"))
            implementation(project(":shared:ml"))
            implementation(project(":shared:content"))
            implementation(libs.sqldelight.coroutines)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.coil.compose)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
    }
}
```

- [ ] **Step 3: Verify dependency resolution**

Run:
```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :composeApp:dependencies --configuration commonMainCompileClasspath
```

Expected: success, output mentions `coil-compose`, `lifecycle-viewmodel-compose`, `navigation-compose`. If a version is unresolvable, bump to the latest patch (e.g. `coil = "3.0.5"`) and re-run.

- [ ] **Step 4: Create `AppGraph.kt` skeleton**

```kotlin
package se.birdy.app.di

import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository

/**
 * Hand-rolled DI container — single instance constructed in MainActivity.
 * Holds the repo and ViewModel factories. Plan 3 keeps it manual; if scope
 * grows past ~10 ViewModels, consider Koin (out of scope for v1).
 */
class AppGraph(
    val repository: SpeciesRepository,
    val defaultLocale: Locale = Locale.SV,
)
```

Note: ViewModel factory methods get added in Tasks 5 and 7 — leave the class minimal here.

- [ ] **Step 5: Create `FakeSpeciesRepository.kt` for tests**

```kotlin
package se.birdy.app.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import se.birdy.content.Locale
import se.birdy.content.SpeciesFilter
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesSummary

/**
 * In-memory test double. Tests can swap the underlying flows to control timing
 * (e.g. emit empty list, then full list, then assert observed states).
 */
class FakeSpeciesRepository : SpeciesRepository {
    val searchResults = MutableStateFlow<List<SpeciesSummary>>(emptyList())
    val byId = MutableStateFlow<Map<SpeciesId, Species?>>(emptyMap())
    val byFamily = MutableStateFlow<Map<String, List<SpeciesSummary>>>(emptyMap())
    val allList = MutableStateFlow<List<SpeciesSummary>>(emptyList())

    var lastSearchCall: Triple<String, Locale, SpeciesFilter>? = null

    override fun getById(id: SpeciesId, locale: Locale): Flow<Species?> =
        flow { emit(byId.value[id]) }

    override fun search(
        query: String,
        locale: Locale,
        filters: SpeciesFilter,
    ): Flow<List<SpeciesSummary>> {
        lastSearchCall = Triple(query, locale, filters)
        return searchResults.asStateFlow()
    }

    override fun listByFamily(familyKey: String, locale: Locale): Flow<List<SpeciesSummary>> =
        flowOf(byFamily.value[familyKey].orEmpty())

    override fun all(locale: Locale): Flow<List<SpeciesSummary>> = allList.asStateFlow()
}
```

- [ ] **Step 6: Smoke-build**

Run:
```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileCommonMainKotlinMetadata
```

Expected: BUILD SUCCESSFUL. The new `commonTest` source set is recognized once a test exists, so don't try to compile commonTest yet.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml composeApp/build.gradle.kts \
  composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt \
  composeApp/src/commonTest/kotlin/se/birdy/app/testing/FakeSpeciesRepository.kt
git commit -m "$(cat <<'EOF'
chore(plan-3): foundation — deps + commonTest + AppGraph skeleton

Adds AndroidX Lifecycle 2.8.4, Navigation 2.8.0, Coil 3.0.4, Turbine 1.1.0
to libs.versions.toml. Wires them into composeApp commonMain + creates a
commonTest source set for ViewModel unit-tests.

AppGraph is a hand-rolled DI container holding the repo (factories added in
later tasks). FakeSpeciesRepository provides an in-memory test double with
mutable flows so tests can drive timing.

Plan 3 Task 1 of 11.
EOF
)"
```

---

### Task 2: Navigation graph + bottom-nav + 3 stub screens

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BottomNavBar.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/ScanStubScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/DiaryStubScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BadgesStubScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/App.kt`
- Delete: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/HomeScreen.kt`
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt`

- [ ] **Step 1: Create `AppRoute.kt`**

```kotlin
package se.birdy.app.ui.scaffold

import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    @Serializable data object Scan : AppRoute
    @Serializable data object Encyclopedia : AppRoute
    @Serializable data object EncyclopediaList : AppRoute // start dest of nested graph
    @Serializable data class SpeciesProfile(val speciesId: String) : AppRoute
    @Serializable data object Diary : AppRoute
    @Serializable data object Badges : AppRoute
}
```

(`Encyclopedia` is the *graph*, `EncyclopediaList` is its start destination — needed because Compose Navigation 2.8 requires a different start type than the parent.)

- [ ] **Step 2: Create stub screens (3 files, identical structure)**

`ScanStubScreen.kt`:

```kotlin
package se.birdy.app.ui.scaffold

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ScanStubScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PhotoCamera,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Text("Skanna", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Den här funktionen kommer i Plan 4 — ML & Camera.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
```

`DiaryStubScreen.kt` — identical pattern with `Icons.Filled.Bookmark`, title `"Dagbok"`, body `"Den här funktionen kommer i Plan 5 — Dagbok."`.

`BadgesStubScreen.kt` — identical pattern with `Icons.Filled.EmojiEvents`, title `"Märken"`, body `"Den här funktionen kommer i Plan 5 — Gamification."`.

- [ ] **Step 3: Create `BottomNavBar.kt`**

```kotlin
package se.birdy.app.ui.scaffold

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

private data class TabSpec(
    val route: AppRoute,
    val label: String,
    val iconResolver: () -> androidx.compose.ui.graphics.vector.ImageVector,
)

private val tabs = listOf(
    TabSpec(AppRoute.Scan, "Skanna") { Icons.Filled.PhotoCamera },
    TabSpec(AppRoute.Encyclopedia, "Uppslagsverk") { Icons.Filled.MenuBook },
    TabSpec(AppRoute.Diary, "Dagbok") { Icons.Filled.Bookmark },
    TabSpec(AppRoute.Badges, "Märken") { Icons.Filled.EmojiEvents },
)

@Composable
fun BottomNavBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    NavigationBar {
        for (tab in tabs) {
            val selected = backStackEntry?.destination?.hierarchy?.any { dest ->
                dest.hasRoute(tab.route::class)
            } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.iconResolver(), contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
    }
}

private val androidx.navigation.NavDestination.hierarchy: Sequence<androidx.navigation.NavDestination>
    get() = generateSequence(this) { it.parent }
```

(Note: `hasRoute(KClass)` accepts any class — for nested-graph destinations, `hierarchy` walks parents so the `Encyclopedia`-tab stays selected when on `SpeciesProfile`.)

- [ ] **Step 4: Create `AppScaffold.kt` (encyclopedia route is a placeholder, replaced in Task 3)**

```kotlin
package se.birdy.app.ui.scaffold

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import se.birdy.app.di.AppGraph

@Composable
fun AppScaffold(graph: AppGraph) {
    val navController = rememberNavController()
    Scaffold(bottomBar = { BottomNavBar(navController) }) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.Encyclopedia,
            modifier = Modifier.padding(padding),
        ) {
            composable<AppRoute.Scan> { ScanStubScreen() }
            navigation<AppRoute.Encyclopedia>(startDestination = AppRoute.EncyclopediaList) {
                composable<AppRoute.EncyclopediaList> {
                    Text("Encyclopedia placeholder — Task 3 wires this") // TASK-3 REPLACE
                }
                composable<AppRoute.SpeciesProfile> { entry ->
                    val route = entry.toRoute<AppRoute.SpeciesProfile>()
                    Text("Profile for ${route.speciesId} — Task 7 wires this") // TASK-7 REPLACE
                }
            }
            composable<AppRoute.Diary> { DiaryStubScreen() }
            composable<AppRoute.Badges> { BadgesStubScreen() }
        }
    }
}
```

(The `// TASK-N REPLACE` markers are the spots where later tasks slot the real screens — keep them so a code search like `grep -n TASK-` shows what's pending.)

- [ ] **Step 5: Update `App.kt` to use `AppScaffold`**

```kotlin
package se.birdy.app

import androidx.compose.runtime.Composable
import se.birdy.app.di.AppGraph
import se.birdy.app.ui.scaffold.AppScaffold
import se.birdy.app.ui.theme.BirdyTheme

@Composable
fun App(graph: AppGraph) {
    BirdyTheme {
        AppScaffold(graph)
    }
}
```

- [ ] **Step 6: Update `MainActivity.kt` to construct AppGraph**

```kotlin
package se.birdy.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import se.birdy.app.App
import se.birdy.app.SpeciesRepositoryProvider
import se.birdy.app.di.AppGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SpeciesRepositoryProvider.init(applicationContext)
        val graph = AppGraph(repository = SpeciesRepositoryProvider.get())
        setContent { App(graph) }
    }
}
```

- [ ] **Step 7: Delete old `HomeScreen.kt`**

```bash
git rm composeApp/src/commonMain/kotlin/se/birdy/app/ui/HomeScreen.kt
```

- [ ] **Step 8: Add Material Icons Extended (needed for PhotoCamera, Bookmark, etc.)**

In `composeApp/build.gradle.kts` `commonMain.dependencies` block, add:

```kotlin
implementation(compose.materialIconsExtended)
```

- [ ] **Step 9: Build + install on device**

```bash
./gradlew :composeApp:assembleDebug
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

Expected: app launches with bottom-nav showing 4 tabs. Default tab is Uppslagsverk and shows "Encyclopedia placeholder — Task 3 wires this". Tapping Skanna → "Skanna" + body. Tapping Dagbok → "Dagbok" + body. Tapping Märken → "Märken" + body. Pressing Uppslagsverk again returns.

- [ ] **Step 10: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/ \
  composeApp/src/commonMain/kotlin/se/birdy/app/App.kt \
  composeApp/build.gradle.kts \
  androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt
git rm -f composeApp/src/commonMain/kotlin/se/birdy/app/ui/HomeScreen.kt 2>/dev/null || true
git commit -m "$(cat <<'EOF'
feat(app): bottom-nav skeleton + nav graph + 3 stub screens

Adds AppScaffold with NavigationBar (4 tabs: Skanna, Uppslagsverk, Dagbok,
Märken) and a Compose Multiplatform Navigation 2.x graph with type-safe
@Serializable routes. Encyclopedia is a nested graph so SpeciesProfile pushes
on its stack — tapping the Encyclopedia tab again returns to the list, not
to other tabs.

Stub screens for Skanna/Dagbok/Märken render Mossbädd-themed placeholders
pointing forward to Plan 4 / Plan 5. The Encyclopedia and SpeciesProfile
destinations contain TASK-N REPLACE placeholders that Tasks 3 and 7 will
swap for the real screens.

App.kt now takes an AppGraph (manual DI container from Task 1).
HomeScreen.kt deleted — superseded by the nav graph.

Plan 3 Task 2 of 11.
EOF
)"
```

---

### Task 3: EncyclopediaScreen list-only with abundance grouping

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/EncyclopediaUiState.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/SpeciesRow.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/EncyclopediaScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/EncyclopediaViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt`

> Task 3 wires Encyclopedia to **`repo.all(locale)`** (no search yet). Search field is added in Task 5 after `repo.search()` is extended in Task 4.

- [ ] **Step 1: Create `EncyclopediaUiState.kt`**

```kotlin
package se.birdy.app.ui.encyclopedia

import se.birdy.content.model.SpeciesSummary

data class GroupedSpecies(
    val common: List<SpeciesSummary>,
    val others: List<SpeciesSummary>,
)

sealed interface EncyclopediaUiState {
    data object Loading : EncyclopediaUiState
    data class Loaded(
        val grouped: GroupedSpecies,
        val sectionCommonHeader: String, // "Allmänna i Sverige" or "Allmänna" depending on region filter
    ) : EncyclopediaUiState
    data object Empty : EncyclopediaUiState
}
```

- [ ] **Step 2: Create `EncyclopediaViewModel.kt` (list-only — no search bar yet)**

```kotlin
package se.birdy.app.ui.encyclopedia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.SpeciesFilter
import se.birdy.content.SpeciesRepository
import se.birdy.content.model.SpeciesSummary

@kotlinx.coroutines.ExperimentalCoroutinesApi
class EncyclopediaViewModel(
    private val repo: SpeciesRepository,
    private val locale: Locale,
) : ViewModel() {
    private val filter = MutableStateFlow(SpeciesFilter())

    val uiState: StateFlow<EncyclopediaUiState> =
        filter
            .flatMapLatest { f -> repo.all(locale).map { list -> applyFilter(list, f) to f } }
            .map { (list, f) -> toUiState(list, f) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), EncyclopediaUiState.Loading)

    private fun applyFilter(
        list: List<SpeciesSummary>,
        f: SpeciesFilter,
    ): List<SpeciesSummary> =
        list.filter { sp ->
            f.abundance.isEmpty() || sp.abundance in f.abundance
        }

    private fun toUiState(list: List<SpeciesSummary>, f: SpeciesFilter): EncyclopediaUiState {
        if (list.isEmpty()) return EncyclopediaUiState.Empty
        val (common, others) = list.partition { it.abundance == Abundance.ALLMÄN }
        val header =
            if (f.regions.isEmpty() || "SE" in f.regions) "Allmänna i Sverige"
            else "Allmänna"
        return EncyclopediaUiState.Loaded(
            grouped = GroupedSpecies(common, others),
            sectionCommonHeader = header,
        )
    }
}
```

- [ ] **Step 3: Create `SpeciesRow.kt`**

```kotlin
package se.birdy.app.ui.encyclopedia

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.HeroMossMid
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.content.Abundance
import se.birdy.content.model.SpeciesSummary

@Composable
fun SpeciesRow(
    summary: SpeciesSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Thumbnail placeholder — Task 9 swaps in real Coil-loaded image with fallback
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(HeroMossMid),
        )
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(summary.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary.scientificName,
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
        if (summary.abundance == Abundance.ALLMÄN) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentCopper)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    "ALLMÄN",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextOnHero,
                )
            }
        }
    }
}
```

- [ ] **Step 4: Create `EncyclopediaScreen.kt` (list-only, no search bar yet)**

```kotlin
package se.birdy.app.ui.encyclopedia

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.content.SpeciesId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EncyclopediaScreen(
    viewModel: EncyclopediaViewModel,
    onSpeciesClick: (SpeciesId) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UPPSLAGSVERK", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HeroMossLight,
                    titleContentColor = TextOnHero,
                ),
            )
        },
    ) { padding ->
        when (val s = state) {
            EncyclopediaUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Laddar…")
                }
            }
            EncyclopediaUiState.Empty -> {
                // Real EmptyState component lives in Task 5 — placeholder text for now
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Inga arter att visa.")
                }
            }
            is EncyclopediaUiState.Loaded -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    if (s.grouped.common.isNotEmpty()) {
                        stickyHeader {
                            SectionHeader("${s.sectionCommonHeader} (${s.grouped.common.size})")
                        }
                        items(s.grouped.common, key = { it.id.raw }) { sum ->
                            SpeciesRow(sum, onClick = { onSpeciesClick(sum.id) })
                        }
                    }
                    if (s.grouped.others.isNotEmpty()) {
                        stickyHeader {
                            SectionHeader("Övriga (${s.grouped.others.size})")
                        }
                        items(s.grouped.others, key = { it.id.raw }) { sum ->
                            SpeciesRow(sum, onClick = { onSpeciesClick(sum.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SandCreme)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
```

(`collectAsStateWithLifecycle` lives in `androidx.lifecycle.compose` — already on the classpath via `lifecycle-viewmodel-compose`.)

- [ ] **Step 5: Add factory methods to `AppGraph.kt`**

Replace the file contents with:

```kotlin
package se.birdy.app.di

import se.birdy.app.ui.encyclopedia.EncyclopediaViewModel
import se.birdy.content.Locale
import se.birdy.content.SpeciesRepository

class AppGraph(
    val repository: SpeciesRepository,
    val defaultLocale: Locale = Locale.SV,
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun encyclopediaViewModel(): EncyclopediaViewModel =
        EncyclopediaViewModel(repository, defaultLocale)
}
```

- [ ] **Step 6: Wire Encyclopedia route in `AppScaffold.kt`**

Replace the placeholder:

```kotlin
composable<AppRoute.EncyclopediaList> {
    Text("Encyclopedia placeholder — Task 3 wires this") // TASK-3 REPLACE
}
```

with:

```kotlin
composable<AppRoute.EncyclopediaList> {
    EncyclopediaScreen(
        viewModel = remember(graph) { graph.encyclopediaViewModel() },
        onSpeciesClick = { id ->
            navController.navigate(AppRoute.SpeciesProfile(id.raw))
        },
    )
}
```

Add the imports:
```kotlin
import androidx.compose.runtime.remember
import se.birdy.app.ui.encyclopedia.EncyclopediaScreen
```

- [ ] **Step 7: Build + install + verify**

```bash
./gradlew :composeApp:assembleDebug
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

Expected: Uppslagsverk-fliken visar nu en list med samtliga ~97 arter, grupperat på "Allmänna i Sverige (X)" + "Övriga (Y)". Tap på en rad → app loggar inget visuellt (SpeciesProfile-routen visar fortfarande TASK-7 placeholder).

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ \
  composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt \
  composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt
git commit -m "$(cat <<'EOF'
feat(app): EncyclopediaScreen list-only — abundance-grouped sections

Wires the Uppslagsverk tab to repo.all(locale=SV) and renders species
grouped by abundance: "Allmänna i Sverige (N)" sticky-header above
"Övriga (M)" sticky-header. Sections with zero items are not rendered.

EncyclopediaViewModel uses StateFlow + abundance-filter (regions/month
filter wiring comes in Task 6). SpeciesRow shows 36dp thumbnail
placeholder (Task 9 adds real Coil image), Swedish + scientific name,
ALLMÄN copper badge.

Tap → navigates to SpeciesProfile(id.raw) — profile screen still a
placeholder until Task 7.

Plan 3 Task 3 of 11.
EOF
)"
```

---

### Task 4: Extend `SqlDelightSpeciesRepository.search()` for full filter + scientific name

**Files:**
- Modify: `shared/content/src/commonMain/sqldelight/se/birdy/content/SpeciesName.sq`
- Modify: `shared/content/src/commonMain/kotlin/se/birdy/content/SqlDelightSpeciesRepository.kt`
- Modify: `shared/content/src/jvmTest/kotlin/se/birdy/content/SpeciesRepositoryTest.kt`

> Strict TDD: write failing tests first, then implement. The current `search()` ignores `regions`, `activeInMonth`, and doesn't match `Species.scientific_name`.

- [ ] **Step 1: Add 4 failing tests to `SpeciesRepositoryTest.kt`**

Append to the test class:

```kotlin
@Test
fun `search matches scientific name`(
    @TempDir tempDir: Path,
) = runTest {
    val driver = newDriverWithFixtures(tempDir)
    val repo = SqlDelightSpeciesRepository(BirdyContent(driver))
    val results = repo.search(query = "Parus", locale = Locale.SV, filters = SpeciesFilter()).first()
    assertTrue(
        results.any { it.id == SpeciesId("Q25485") },
        "expected to find Talgoxe (Parus major) when searching 'Parus', got ${results.map { it.scientificName }}",
    )
    driver.close()
}

@Test
fun `search filter by region restricts results`(
    @TempDir tempDir: Path,
) = runTest {
    val driver = newDriverWithFixtures(tempDir)
    val repo = SqlDelightSpeciesRepository(BirdyContent(driver))
    // Ask for species active in DE only — Talgoxe is active in DE, so it should still be there.
    val results = repo.search(
        query = "Talg",
        locale = Locale.SV,
        filters = SpeciesFilter(regions = setOf("DE")),
    ).first()
    assertTrue(results.any { it.id == SpeciesId("Q25485") })

    // Ask for a region the fixture doesn't have for Talgoxe — should NOT include it.
    val noResults = repo.search(
        query = "Talg",
        locale = Locale.SV,
        filters = SpeciesFilter(regions = setOf("ZZ")),
    ).first()
    assertTrue(noResults.none { it.id == SpeciesId("Q25485") })

    driver.close()
}

@Test
fun `search filter by activeInMonth restricts results`(
    @TempDir tempDir: Path,
) = runTest {
    val driver = newDriverWithFixtures(tempDir)
    val repo = SqlDelightSpeciesRepository(BirdyContent(driver))
    // The walking-skeleton fixtures set every month to "present" — so Talgoxe should match jan.
    val janResults = repo.search(
        query = "Talg",
        locale = Locale.SV,
        filters = SpeciesFilter(activeInMonth = "jan"),
    ).first()
    assertTrue(janResults.any { it.id == SpeciesId("Q25485") })

    // Force-delete the jan row to verify the filter actually excludes — using the driver directly.
    driver.execute(null, "DELETE FROM SpeciesSeason WHERE species_id = 'Q25485' AND month = 'jan'", 0)
    val janResultsAfter = repo.search(
        query = "Talg",
        locale = Locale.SV,
        filters = SpeciesFilter(activeInMonth = "jan"),
    ).first()
    assertTrue(janResultsAfter.none { it.id == SpeciesId("Q25485") })

    driver.close()
}

@Test
fun `search empty query returns all species respecting filters`(
    @TempDir tempDir: Path,
) = runTest {
    val driver = newDriverWithFixtures(tempDir)
    val repo = SqlDelightSpeciesRepository(BirdyContent(driver))
    val all = repo.search(query = "", locale = Locale.SV, filters = SpeciesFilter()).first()
    assertTrue(all.size >= 5, "expected ≥5 walking-skeleton species, got ${all.size}")
    driver.close()
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :shared:content:jvmTest --tests "se.birdy.content.SpeciesRepositoryTest"
```

Expected: 4 new tests FAIL. The first because `Parus` isn't a value of `name`. The second because `regions` is unused. The third because `activeInMonth` is unused. The fourth might pass coincidentally — that's OK, it's a regression-guard.

- [ ] **Step 3: Add new SQL query in `SpeciesName.sq`**

Append after `searchByName`:

```sql
searchByNameOrScientific:
SELECT sn.species_id AS species_id, sn.locale AS locale, sn.name AS name
FROM SpeciesName sn
JOIN Species s ON s.id = sn.species_id
WHERE sn.locale = :locale
  AND (sn.name LIKE ('%' || :query || '%') OR s.scientific_name LIKE ('%' || :query || '%'))
ORDER BY sn.name LIMIT :max;
```

(SQLDelight supports named parameters with `:` prefix.)

- [ ] **Step 4: Update `SqlDelightSpeciesRepository.search()` to use the new query + filter post-fetch**

Replace the existing `override fun search(...)` block with:

```kotlin
override fun search(
    query: String,
    locale: Locale,
    filters: SpeciesFilter,
): Flow<List<SpeciesSummary>> =
    db.speciesNameQueries
        .searchByNameOrScientific(locale = locale.code, query = query, max = 200L)
        .asFlow()
        .mapToList(Dispatchers.Default)
        .map { rows ->
            rows
                .distinctBy { it.species_id }
                .mapNotNull { name ->
                    val sp =
                        db.speciesQueries
                            .selectById(name.species_id)
                            .executeAsOneOrNull() ?: return@mapNotNull null
                    val abundance =
                        Abundance.fromCode(sp.abundance) ?: Abundance.OVANLIG
                    if (filters.abundance.isNotEmpty() && abundance !in filters.abundance) {
                        return@mapNotNull null
                    }
                    if (filters.regions.isNotEmpty()) {
                        val speciesRegions =
                            db.speciesRegionQueries.selectBySpecies(sp.id).executeAsList().toSet()
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
                    SpeciesSummary(
                        id = SpeciesId(sp.id),
                        name = name.name,
                        scientificName = sp.scientific_name,
                        abundance = abundance,
                        heroImagePath =
                            db.speciesImageQueries
                                .selectBySpecies(sp.id)
                                .executeAsList()
                                .firstOrNull { it.role == "hero" }
                                ?.path,
                    )
                }
                .take(50)
        }
```

(Limit raised in SQL to 200 to allow filter post-processing to converge to ≤50; `take(50)` in Kotlin enforces final cap.)

- [ ] **Step 5: Run tests — they should now pass**

```bash
./gradlew :shared:content:jvmTest --tests "se.birdy.content.SpeciesRepositoryTest"
```

Expected: all 8 tests (4 original + 4 new) PASS.

- [ ] **Step 6: Run validation + db build to confirm shared/content stays buildable**

```bash
./gradlew :shared:content:validateSpeciesData :shared:content:buildSpeciesDb
```

Expected: BUILD SUCCESSFUL, `composeApp/.../files/species.db` regenerated.

- [ ] **Step 7: Commit**

```bash
git add shared/content/src/commonMain/sqldelight/se/birdy/content/SpeciesName.sq \
  shared/content/src/commonMain/kotlin/se/birdy/content/SqlDelightSpeciesRepository.kt \
  shared/content/src/jvmTest/kotlin/se/birdy/content/SpeciesRepositoryTest.kt
git commit -m "$(cat <<'EOF'
feat(content): search() honors regions/activeInMonth/scientific name

Adds searchByNameOrScientific to SpeciesName.sq joining Species so we match
both localized name and scientific_name in a single query. Updates
SqlDelightSpeciesRepository.search() to post-filter on regions (intersect
with SpeciesRegion rows) and activeInMonth (SpeciesSeason rows where
status != absent). Increases inner SQL limit to 200 so post-filter still
converges on ≤50 results.

Adds 4 jvmTest cases covering scientific-name match, region-filter,
month-filter, and empty-query baseline.

Plan 3 Task 4 of 11.
EOF
)"
```

---

### Task 5: EncyclopediaScreen search bar (debounced) + ViewModel test

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/EmptyState.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/EncyclopediaViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/EncyclopediaScreen.kt`
- Create: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/encyclopedia/EncyclopediaViewModelTest.kt`

- [ ] **Step 1: Write failing ViewModel test**

```kotlin
package se.birdy.app.ui.encyclopedia

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.model.SpeciesSummary
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class EncyclopediaViewModelTest {
    @BeforeTest
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private val talgoxe = SpeciesSummary(
        id = SpeciesId("Q25485"),
        name = "Talgoxe",
        scientificName = "Parus major",
        abundance = Abundance.ALLMÄN,
        heroImagePath = null,
    )

    private val balkanmes = SpeciesSummary(
        id = SpeciesId("Q574281"),
        name = "Balkanmes",
        scientificName = "Poecile lugubris",
        abundance = Abundance.OVANLIG,
        heroImagePath = null,
    )

    @Test
    fun `default state groups allmänna above övriga`() = runTest {
        val repo = FakeSpeciesRepository().apply {
            allList.value = listOf(talgoxe, balkanmes)
            searchResults.value = listOf(talgoxe, balkanmes)
        }
        val vm = EncyclopediaViewModel(repo, Locale.SV)
        vm.uiState.test {
            // Loading first
            assertEquals(EncyclopediaUiState.Loading, awaitItem())
            val loaded = awaitItem() as EncyclopediaUiState.Loaded
            assertEquals(listOf(talgoxe), loaded.grouped.common)
            assertEquals(listOf(balkanmes), loaded.grouped.others)
            assertEquals("Allmänna i Sverige", loaded.sectionCommonHeader)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `search query debounces 250ms before triggering repo`() = runTest {
        val repo = FakeSpeciesRepository().apply {
            searchResults.value = listOf(talgoxe)
        }
        val vm = EncyclopediaViewModel(repo, Locale.SV)
        vm.uiState.test {
            assertEquals(EncyclopediaUiState.Loading, awaitItem())
            awaitItem() // initial Loaded with no query

            vm.onQueryChanged("T")
            advanceTimeBy(100) // before debounce
            // No new emission yet — repo.search not called for "T"
            assertEquals(null, repo.lastSearchCall?.first?.takeIf { it == "T" })
            advanceTimeBy(200) // past debounce
            // Now lastSearchCall reflects "T"
            assertEquals("T", repo.lastSearchCall?.first)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `empty search result emits Empty state`() = runTest {
        val repo = FakeSpeciesRepository().apply {
            searchResults.value = emptyList()
        }
        val vm = EncyclopediaViewModel(repo, Locale.SV)
        vm.uiState.test {
            assertEquals(EncyclopediaUiState.Loading, awaitItem())
            vm.onQueryChanged("zzz")
            advanceTimeBy(300)
            // After debounce, search returns empty → Empty state.
            // Skip intermediate states until we reach Empty.
            var current = awaitItem()
            while (current !is EncyclopediaUiState.Empty) current = awaitItem()
            assertEquals(EncyclopediaUiState.Empty, current)
            cancelAndConsumeRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :composeApp:jvmTest --tests "se.birdy.app.ui.encyclopedia.EncyclopediaViewModelTest"
```

If `:composeApp:jvmTest` doesn't exist yet (KMP setup), use:

```bash
./gradlew :composeApp:check
```

Expected: tests FAIL because `onQueryChanged` doesn't exist yet, and the ViewModel doesn't wire `searchResults` flow.

- [ ] **Step 3: Update `EncyclopediaViewModel.kt` with search state**

Replace the file with:

```kotlin
package se.birdy.app.ui.encyclopedia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.SpeciesFilter
import se.birdy.content.SpeciesRepository
import se.birdy.content.model.SpeciesSummary

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class EncyclopediaViewModel(
    private val repo: SpeciesRepository,
    private val locale: Locale,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(SpeciesFilter())
    val filter: StateFlow<SpeciesFilter> = _filter.asStateFlow()

    val uiState: StateFlow<EncyclopediaUiState> =
        combine(
            _query.debounce(DEBOUNCE_MS).distinctUntilChanged(),
            _filter,
        ) { q, f -> q to f }
            .flatMapLatest { (q, f) -> repo.search(q, locale, f).map { list -> Triple(list, f, q) } }
            .map { (list, f, _) -> toUiState(list, f) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), EncyclopediaUiState.Loading)

    fun onQueryChanged(q: String) {
        _query.value = q
    }

    fun onFilterChanged(f: SpeciesFilter) {
        _filter.value = f
    }

    private fun toUiState(list: List<SpeciesSummary>, f: SpeciesFilter): EncyclopediaUiState {
        if (list.isEmpty()) return EncyclopediaUiState.Empty
        val (common, others) = list.partition { it.abundance == Abundance.ALLMÄN }
        val header =
            if (f.regions.isEmpty() || "SE" in f.regions) "Allmänna i Sverige"
            else "Allmänna"
        return EncyclopediaUiState.Loaded(
            grouped = GroupedSpecies(common, others),
            sectionCommonHeader = header,
        )
    }

    private companion object {
        const val DEBOUNCE_MS = 250L
    }
}
```

(Note: we now use `repo.search(q, ...)` for everything — `repo.all` was an interim wiring in Task 3. With empty `q`, the extended `search()` from Task 4 returns all species respecting the LIKE-with-empty-string semantics.)

- [ ] **Step 4: Run ViewModel tests — should pass**

```bash
./gradlew :composeApp:check
```

Expected: 3 tests in `EncyclopediaViewModelTest` PASS.

- [ ] **Step 5: Create `EmptyState.kt`**

```kotlin
package se.birdy.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun EmptyState(
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
```

- [ ] **Step 6: Add search bar + EmptyState wiring to `EncyclopediaScreen.kt`**

Replace the existing file with:

```kotlin
package se.birdy.app.ui.encyclopedia

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.birdy.app.ui.components.EmptyState
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.content.SpeciesId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EncyclopediaScreen(
    viewModel: EncyclopediaViewModel,
    onSpeciesClick: (SpeciesId) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UPPSLAGSVERK", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HeroMossLight,
                    titleContentColor = TextOnHero,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text("Sök art, släkte eller familj…") },
            )
            // Filter button slot — Task 6 puts FilterButton + count-pill here.
            // TASK-6 INSERT FILTER BUTTON

            when (val s = state) {
                EncyclopediaUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Laddar…")
                    }
                }
                EncyclopediaUiState.Empty -> {
                    EmptyState(
                        title = "Ingen art matchar",
                        body = "Prova andra filter eller sök på vetenskapligt namn.",
                    )
                }
                is EncyclopediaUiState.Loaded -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (s.grouped.common.isNotEmpty()) {
                            stickyHeader {
                                SectionHeader("${s.sectionCommonHeader} (${s.grouped.common.size})")
                            }
                            items(s.grouped.common, key = { it.id.raw }) { sum ->
                                SpeciesRow(sum, onClick = { onSpeciesClick(sum.id) })
                            }
                        }
                        if (s.grouped.others.isNotEmpty()) {
                            stickyHeader {
                                SectionHeader("Övriga (${s.grouped.others.size})")
                            }
                            items(s.grouped.others, key = { it.id.raw }) { sum ->
                                SpeciesRow(sum, onClick = { onSpeciesClick(sum.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SandCreme)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
```

- [ ] **Step 7: Build + install + verify search**

```bash
./gradlew :composeApp:assembleDebug && ./gradlew :androidApp:installDebug && \
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

Verifiera manuellt på device:
- Skriv "tal" → Talgoxe ska synas (svenskt namn match)
- Skriv "Parus" → Talgoxe ska synas (scientific name match)
- Skriv "zzzz" → "Ingen art matchar"-EmptyState

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ \
  composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/EmptyState.kt \
  composeApp/src/commonTest/kotlin/se/birdy/app/ui/encyclopedia/EncyclopediaViewModelTest.kt
git commit -m "$(cat <<'EOF'
feat(app): EncyclopediaScreen search bar (debounced 250ms) + EmptyState

EncyclopediaViewModel now uses repo.search(query, locale, filter) via
combine(query.debounce(250), filter) → flatMapLatest. onQueryChanged updates
the query StateFlow which Compose observes.

EncyclopediaScreen renders OutlinedTextField above the list and an
EmptyState ("Ingen art matchar — prova andra filter eller sök på
vetenskapligt namn") when results are empty.

Adds 3 ViewModel unit-tests (default grouping, debounce semantics, empty
state) using FakeSpeciesRepository + Turbine.

Plan 3 Task 5 of 11.
EOF
)"
```

---

### Task 6: FilterBottomSheet with chip groups + count pill

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/FilterBottomSheet.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/EncyclopediaScreen.kt`
- Modify: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/encyclopedia/EncyclopediaViewModelTest.kt`

- [ ] **Step 1: Add a failing test for filter changes**

Append to `EncyclopediaViewModelTest`:

```kotlin
@Test
fun `filter changes propagate to repo search call`() = runTest {
    val repo = FakeSpeciesRepository().apply {
        searchResults.value = listOf(talgoxe)
    }
    val vm = EncyclopediaViewModel(repo, Locale.SV)
    vm.uiState.test {
        assertEquals(EncyclopediaUiState.Loading, awaitItem())
        awaitItem() // initial Loaded
        vm.onFilterChanged(
            se.birdy.content.SpeciesFilter(
                abundance = setOf(se.birdy.content.Abundance.ALLMÄN),
                regions = setOf("SE"),
                activeInMonth = "jan",
            ),
        )
        // Allow combine + flatMapLatest to re-run.
        awaitItem()
        assertNotNull(repo.lastSearchCall)
        val call = repo.lastSearchCall!!
        assertEquals(setOf(se.birdy.content.Abundance.ALLMÄN), call.third.abundance)
        assertEquals(setOf("SE"), call.third.regions)
        assertEquals("jan", call.third.activeInMonth)
        cancelAndConsumeRemainingEvents()
    }
}
```

- [ ] **Step 2: Run test to confirm it fails initially**

```bash
./gradlew :composeApp:check --tests "*EncyclopediaViewModelTest*"
```

Expected: test fails (because no UI yet calls `onFilterChanged`). Wait — `onFilterChanged` already exists from Task 5. The test should actually pass already because the VM already wires `_filter` into `combine`. Run it; if it passes, treat this as a regression-guard. If it fails, debug the wiring before proceeding.

- [ ] **Step 3: Create `FilterBottomSheet.kt`**

```kotlin
package se.birdy.app.ui.encyclopedia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.content.Abundance
import se.birdy.content.SpeciesFilter

private val MONTHS = listOf("jan","feb","mar","apr","maj","jun","jul","aug","sep","okt","nov","dec")
private val REGIONS = listOf("SE", "NO", "FI", "DK", "DE")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    initial: SpeciesFilter,
    previewCount: Int,
    onApply: (SpeciesFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Filter", style = MaterialTheme.typography.titleLarge)

            Text("FÖREKOMST", style = MaterialTheme.typography.labelLarge, color = AccentCopper)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Abundance.ALLMÄN, Abundance.OVANLIG).forEach { ab ->
                    FilterChip(
                        selected = ab in draft.abundance,
                        onClick = {
                            draft = draft.copy(
                                abundance = if (ab in draft.abundance) draft.abundance - ab else draft.abundance + ab,
                            )
                        },
                        label = { Text(ab.code.replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            Text("REGION", style = MaterialTheme.typography.labelLarge, color = AccentCopper)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(REGIONS) { r ->
                    FilterChip(
                        selected = r in draft.regions,
                        onClick = {
                            draft = draft.copy(
                                regions = if (r in draft.regions) draft.regions - r else draft.regions + r,
                            )
                        },
                        label = { Text(r) },
                    )
                }
            }

            Text("MÅNAD", style = MaterialTheme.typography.labelLarge, color = AccentCopper)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MONTHS) { m ->
                    FilterChip(
                        selected = draft.activeInMonth == m,
                        onClick = {
                            draft = draft.copy(activeInMonth = if (draft.activeInMonth == m) null else m)
                        },
                        label = { Text(m.replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { draft = SpeciesFilter() },
                    modifier = Modifier.weight(1f),
                ) { Text("Återställ") }
                Button(
                    onClick = { onApply(draft) },
                    modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCopper, contentColor = TextOnHero),
                ) { Text("Visa $previewCount arter") }
            }
        }
    }
}

// Imports for LazyRow.items extension
private fun <T> androidx.compose.foundation.lazy.LazyListScope.items(
    items: List<T>,
    itemContent: @Composable (T) -> Unit,
) = items(items.size) { itemContent(items[it]) }
```

- [ ] **Step 2: Add filter button to `EncyclopediaScreen.kt`**

Replace the `// TASK-6 INSERT FILTER BUTTON` line with:

```kotlin
val filter by viewModel.filter.collectAsStateWithLifecycle()
val activeFilterCount = filter.activeCount()
var showSheet by remember { mutableStateOf(false) }
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    OutlinedButton(onClick = { showSheet = true }) {
        Text("⚙ Filter")
        if (activeFilterCount > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentCopper)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(
                    activeFilterCount.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextOnHero,
                )
            }
        }
    }
}

if (showSheet) {
    val previewCount = (state as? EncyclopediaUiState.Loaded)?.let {
        it.grouped.common.size + it.grouped.others.size
    } ?: 0
    FilterBottomSheet(
        initial = filter,
        previewCount = previewCount,
        onApply = { newFilter ->
            viewModel.onFilterChanged(newFilter)
            showSheet = false
        },
        onDismiss = { showSheet = false },
    )
}
```

Add the imports:
```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import se.birdy.app.ui.theme.AccentCopper
```

And add the helper extension at the bottom of the file:

```kotlin
private fun se.birdy.content.SpeciesFilter.activeCount(): Int {
    var n = 0
    if (abundance.isNotEmpty()) n++
    if (regions.isNotEmpty()) n++
    if (activeInMonth != null) n++
    return n
}
```

- [ ] **Step 3: Build + install + verify on device**

```bash
./gradlew :composeApp:assembleDebug && ./gradlew :androidApp:installDebug && \
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

Verify:
- Tap "Filter" → bottom sheet öppnar med 3 chip-grupper
- Välj "Allmän" + "SE" + "jun" → Visa N arter-knappen uppdaterar (efter Apply)
- Filter-knappen visar `2` count-pill (abundance + regions; månad räknas som tredje fält)
  - actually verify the count: 3 filter groups all active → count = 3
- Tap "Återställ" → chips clear

- [ ] **Step 4: Run tests**

```bash
./gradlew :composeApp:check
```

Expected: all 4 ViewModel tests pass.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/FilterBottomSheet.kt \
  composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/EncyclopediaScreen.kt \
  composeApp/src/commonTest/kotlin/se/birdy/app/ui/encyclopedia/EncyclopediaViewModelTest.kt
git commit -m "$(cat <<'EOF'
feat(app): FilterBottomSheet with abundance/region/month chip groups

ModalBottomSheet exposes 3 filter groups matching SpeciesFilter exactly:
abundance (Allmän, Ovanlig — multi-select), regions (SE/NO/FI/DK/DE —
multi-select), activeInMonth (jan-dec — single-select). "Återställ"
clears the draft. "Visa N arter" applies the draft and dismisses.

EncyclopediaScreen shows a Filter button under the search bar with a
copper count-pill displaying active-filter-count (1-3).

Adds a regression test for filter→repo propagation.

Plan 3 Task 6 of 11.
EOF
)"
```

---

### Task 7: SpeciesProfileScreen with collapsing toolbar

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/profile/SpeciesProfileUiState.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/profile/SpeciesProfileViewModel.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/profile/SpeciesProfileScreen.kt`
- Create: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/profile/SpeciesProfileViewModelTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt`

- [ ] **Step 1: Create `SpeciesProfileUiState.kt`**

```kotlin
package se.birdy.app.ui.profile

import se.birdy.content.model.Species

sealed interface SpeciesProfileUiState {
    data object Loading : SpeciesProfileUiState
    data class Loaded(val species: Species) : SpeciesProfileUiState
    data object NotFound : SpeciesProfileUiState
}
```

- [ ] **Step 2: Write failing ViewModel tests**

```kotlin
package se.birdy.app.ui.profile

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesTaxonomy
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SpeciesProfileViewModelTest {
    @BeforeTest
    fun setup() { kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher()) }

    private val talgoxe = Species(
        id = SpeciesId("Q25485"),
        scientificName = "Parus major",
        taxonomy = SpeciesTaxonomy(family = "Paridae", familySv = "Mesfåglar", genus = "Parus", iocOrder = "Passeriformes"),
        name = "Talgoxe",
        abundance = Abundance.ALLMÄN,
        iucnStatus = "LC",
        regions = listOf("SE", "NO", "FI", "DK", "DE"),
        season = (listOf("jan","feb","mar","apr","maj","jun","jul","aug","sep","okt","nov","dec")).associateWith { "present" },
        description = "Talgoxen är en av Sveriges vanligaste tättingar.",
        migration = "Mestadels stannfågel.",
        images = emptyList(),
    )

    @Test
    fun `loaded state exposes species after repo emits`() = runTest {
        val repo = FakeSpeciesRepository().apply {
            byId.value = mapOf(SpeciesId("Q25485") to talgoxe)
        }
        val vm = SpeciesProfileViewModel(repo, SpeciesId("Q25485"), Locale.SV)
        vm.uiState.test {
            assertEquals(SpeciesProfileUiState.Loading, awaitItem())
            assertEquals(SpeciesProfileUiState.Loaded(talgoxe), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `unknown id keeps NotFound state`() = runTest {
        val repo = FakeSpeciesRepository() // empty byId map → null
        val vm = SpeciesProfileViewModel(repo, SpeciesId("Q99999999"), Locale.SV)
        vm.uiState.test {
            assertEquals(SpeciesProfileUiState.Loading, awaitItem())
            assertEquals(SpeciesProfileUiState.NotFound, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }
}
```

- [ ] **Step 3: Create `SpeciesProfileViewModel.kt` to make tests pass**

```kotlin
package se.birdy.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository

class SpeciesProfileViewModel(
    repo: SpeciesRepository,
    speciesId: SpeciesId,
    locale: Locale,
) : ViewModel() {
    val uiState: StateFlow<SpeciesProfileUiState> =
        repo.getById(speciesId, locale)
            .map { species ->
                if (species == null) SpeciesProfileUiState.NotFound
                else SpeciesProfileUiState.Loaded(species)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), SpeciesProfileUiState.Loading)
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :composeApp:check --tests "*SpeciesProfileViewModelTest*"
```

Expected: 2 tests PASS.

- [ ] **Step 5: Create `SpeciesProfileScreen.kt`**

> SectionBlock and HeroImage are added in Tasks 8 and 9. Task 7 uses inline placeholders so the screen is testable on device.

```kotlin
package se.birdy.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.birdy.app.ui.components.EmptyState
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.HeroMossDeep
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.HeroMossMid
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.content.model.Species

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesProfileScreen(
    viewModel: SpeciesProfileViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        SpeciesProfileUiState.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Laddar…") }
        SpeciesProfileUiState.NotFound ->
            EmptyState(title = "Art saknas.", body = "Tryck tillbaka för att gå till listan.")
        is SpeciesProfileUiState.Loaded -> ProfileContent(s.species, onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileContent(species: Species, onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(species.name, style = MaterialTheme.typography.headlineMedium, color = TextOnHero)
                        Text(
                            species.scientificName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = TextOnHero.copy(alpha = 0.85f),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Tillbaka",
                            tint = TextOnHero,
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = HeroMossLight,
                    scrolledContainerColor = HeroMossLight,
                    navigationIconContentColor = TextOnHero,
                    titleContentColor = TextOnHero,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { FactRow(species) }
            item {
                Text("BESKRIVNING", style = MaterialTheme.typography.labelLarge, color = AccentCopper)
                Text(species.description ?: "Beskrivning kommer i en framtida uppdatering.")
            }
            item {
                Text("FLYTTNING", style = MaterialTheme.typography.labelLarge, color = AccentCopper)
                Text(species.migration ?: "Migrationsdata saknas för denna art.")
            }
            item {
                Text("FOTOGRAFIER", style = MaterialTheme.typography.labelLarge, color = AccentCopper)
                if (species.images.isEmpty()) {
                    Text("Inga foton tillgängliga.")
                } else {
                    Text("${species.images.size} foton (Coil i Task 9).")
                }
            }
        }
    }
}

@Composable
private fun FactRow(species: Species) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(species.abundance.code.uppercase(), accent = true)
        species.taxonomy.familySv?.let { Chip(it) }
        Chip(species.iucnStatus)
    }
}

@Composable
private fun Chip(text: String, accent: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (accent) AccentCopper else SandCreme)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (accent) TextOnHero else MaterialTheme.colorScheme.onBackground,
        )
    }
}
```

- [ ] **Step 6: Add factory in `AppGraph.kt`**

Append the method:

```kotlin
fun speciesProfileViewModel(speciesId: SpeciesId): SpeciesProfileViewModel =
    SpeciesProfileViewModel(repository, speciesId, defaultLocale)
```

Add the imports:

```kotlin
import se.birdy.app.ui.profile.SpeciesProfileViewModel
import se.birdy.content.SpeciesId
```

- [ ] **Step 7: Wire SpeciesProfile route in `AppScaffold.kt`**

Replace the `// TASK-7 REPLACE` placeholder:

```kotlin
composable<AppRoute.SpeciesProfile> { entry ->
    val route = entry.toRoute<AppRoute.SpeciesProfile>()
    SpeciesProfileScreen(
        viewModel = remember(graph, route.speciesId) {
            graph.speciesProfileViewModel(SpeciesId(route.speciesId))
        },
        onBack = { navController.popBackStack() },
    )
}
```

Add imports:

```kotlin
import se.birdy.app.ui.profile.SpeciesProfileScreen
import se.birdy.content.SpeciesId
```

- [ ] **Step 8: Build + install + verify**

```bash
./gradlew :composeApp:assembleDebug && ./gradlew :androidApp:installDebug && \
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

Verify on device:
- Tap a row in encyclopedia (e.g. Talgoxe) → profile öppnar med rubrik "Talgoxe" + scientific name + 3 chips + 3 sektioner
- Scrolla → LargeTopAppBar collapsar från full till 64dp
- Back-pilen → tillbaka till listan, scroll-position bevarad

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/profile/ \
  composeApp/src/commonTest/kotlin/se/birdy/app/ui/profile/ \
  composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt \
  composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt
git commit -m "$(cat <<'EOF'
feat(app): SpeciesProfileScreen with collapsing toolbar

LargeTopAppBar (Material 3, exitUntilCollapsedScrollBehavior) holds the
species name + scientific name. Body is a LazyColumn with FactRow (3 chips:
abundance, family, IUCN) + Beskrivning + Flyttning + Fotografier sections.

Sparse-fallback uses inline strings for now — Task 8 introduces SectionBlock
helper to consolidate. Photo strip is a count-only placeholder; Coil wiring
lands in Task 9.

SpeciesProfileViewModel exposes Loading / Loaded / NotFound StateFlow.
Adds 2 ViewModel unit-tests covering the loaded and not-found paths.

Plan 3 Task 7 of 11.
EOF
)"
```

---

### Task 8: SectionBlock helper + sparse-data inline rendering

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/SectionBlock.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/profile/SpeciesProfileScreen.kt`

- [ ] **Step 1: Create `SectionBlock.kt`**

```kotlin
package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.SandCreme

@Composable
fun SectionBlock(
    label: String,
    isEmpty: Boolean,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = AccentCopper)
        if (isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SandCreme.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    emptyMessage,
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }
        } else {
            content()
        }
    }
}
```

- [ ] **Step 2: Replace inline empties in `SpeciesProfileScreen.kt`**

Replace the three `item { ... }` blocks for BESKRIVNING/FLYTTNING/FOTOGRAFIER with:

```kotlin
item {
    SectionBlock(
        label = "BESKRIVNING",
        isEmpty = species.description.isNullOrBlank(),
        emptyMessage = "Beskrivning kommer i en framtida uppdatering.",
    ) {
        Text(species.description.orEmpty(), style = MaterialTheme.typography.bodyMedium)
    }
}
item {
    SectionBlock(
        label = "FLYTTNING",
        isEmpty = species.migration.isNullOrBlank(),
        emptyMessage = "Migrationsdata saknas för denna art.",
    ) {
        Text(species.migration.orEmpty(), style = MaterialTheme.typography.bodyMedium)
    }
}
item {
    SectionBlock(
        label = "FOTOGRAFIER",
        isEmpty = species.images.isEmpty(),
        emptyMessage = "Inga foton tillgängliga.",
    ) {
        Text("${species.images.size} foton (Coil i Task 9).")
    }
}
```

Add the import:
```kotlin
import se.birdy.app.ui.components.SectionBlock
```

Remove the now-unused `Text("BESKRIVNING", ...)` etc. block + the unused imports (e.g. `AccentCopper` if it's not used elsewhere in the file).

- [ ] **Step 3: Build + install + verify on a sparse-data species**

```bash
./gradlew :composeApp:assembleDebug && ./gradlew :androidApp:installDebug && \
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

Verify:
- Open Talgoxe (full data) → all 3 sections show real text
- Open a sparse alaudidae species (e.g. Q1083050 Sandlärka — search for "sand" or scroll alaudidae) → "Beskrivning kommer i en framtida uppdatering." in italic on sand-creme background

If your DB doesn't have Q1083050 yet, pick any species the validator marks `description-too-short` to verify the empty path. `git grep description_accept_missing shared/content/overrides.yaml` lists candidates.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/SectionBlock.kt \
  composeApp/src/commonMain/kotlin/se/birdy/app/ui/profile/SpeciesProfileScreen.kt
git commit -m "$(cat <<'EOF'
feat(app): SectionBlock helper consolidates sparse-data rendering

Single Composable wraps label + isEmpty branch. Empty renders an italic,
0.7-alpha text on sand-creme rounded box (Mossbädd palette). Profile screen
now uses SectionBlock for BESKRIVNING/FLYTTNING/FOTOGRAFIER, replacing
inline if-else branches.

This is the helper Plan 3's design spec §5.1 specifies. All three labels
now share consistent typography, spacing, and empty-state treatment, so
sparse alaudidae species look intentional rather than buggy.

Plan 3 Task 8 of 11.
EOF
)"
```

---

### Task 9: HeroImage component + Coil wiring + thumbnail fallback

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/HeroImage.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/profile/SpeciesProfileScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/SpeciesRow.kt`

> Coil 3 KMP risk (spec §14): if `Res.getUri()` doesn't resolve, fall back to a `LocalImageLoader` that reads via `Res.readBytes()`. Verify with Talgoxe first.

- [ ] **Step 1: Create `HeroImage.kt`**

```kotlin
package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.Res
import se.birdy.app.ui.theme.HeroMossDeep
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.HeroMossMid
import se.birdy.app.ui.theme.SandCreme

/**
 * Hero or thumbnail with sandig diagonal-pattern fallback when imagePath is null
 * or fails to load. imagePath is the relative path stored in SpeciesImage.path,
 * e.g. "files/images/Q25485/hero.jpg" — Res.getUri() prefixes the right
 * platform-specific URI.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun HeroImage(
    imagePath: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0.dp,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(colors = listOf(HeroMossLight, HeroMossMid, HeroMossDeep)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (imagePath != null) {
            AsyncImage(
                model = Res.getUri(imagePath),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text("📷", style = MaterialTheme.typography.headlineLarge)
        }
    }
}
```

- [ ] **Step 2: Wire `HeroImage` in `SpeciesRow.kt`**

Replace the placeholder thumbnail Box with:

```kotlin
HeroImage(
    imagePath = summary.heroImagePath,
    modifier = Modifier.size(36.dp),
    cornerRadius = 8.dp,
)
```

Add the import + remove now-unused imports:
```kotlin
import se.birdy.app.ui.components.HeroImage
```

- [ ] **Step 3: Wire `HeroImage` in `SpeciesProfileScreen.kt` for the FOTOGRAFIER section**

Replace the inside of `SectionBlock("FOTOGRAFIER", ...) { ... }`:

```kotlin
SectionBlock(
    label = "FOTOGRAFIER",
    isEmpty = species.images.isEmpty(),
    emptyMessage = "Inga foton tillgängliga.",
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (img in species.images.take(3)) {
            HeroImage(
                imagePath = img.path,
                modifier = Modifier.weight(1f).height(64.dp),
                cornerRadius = 8.dp,
            )
        }
    }
}
```

Add the imports:
```kotlin
import se.birdy.app.ui.components.HeroImage
import androidx.compose.foundation.layout.height
```

- [ ] **Step 4: Build + install + verify on device**

```bash
./gradlew :composeApp:assembleDebug && ./gradlew :androidApp:installDebug && \
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

Verify:
- Encyclopedia list — Talgoxe-thumbnailen visar ett riktigt foto, en sparse-art utan bilder visar grön gradient
- Open Talgoxe → FOTOGRAFIER har 3 thumbnails (hero + 2 secondaries)
- Open a `allow_missing_images: true`-art (e.g. ev. Q891376 basrasångare) → "Inga foton tillgängliga."

If Coil fails to load (blank thumbnails everywhere), check the `Res.getUri()` resolution by adding a temporary `Log.d("birdy", Res.getUri(imagePath))` and inspecting via `adb logcat`. The path format may differ from `files/images/...` — adjust accordingly.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/HeroImage.kt \
  composeApp/src/commonMain/kotlin/se/birdy/app/ui/profile/SpeciesProfileScreen.kt \
  composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/SpeciesRow.kt
git commit -m "$(cat <<'EOF'
feat(app): HeroImage component + Coil wiring for list + profile thumbnails

HeroImage renders a Coil AsyncImage with Res.getUri(path) when path is
non-null, falling back to a Mossbädd-green gradient + 📷 glyph when
absent. Reused in SpeciesRow (36dp thumbnail) and the FOTOGRAFIER row in
SpeciesProfileScreen (3×64dp).

This makes sparse-image species (allow_missing_images: true overrides)
visually consistent — they get the gradient instead of blank/broken
images.

Plan 3 Task 9 of 11.
EOF
)"
```

---

### Task 10: i18n — extract all hard-coded strings

**Files:**
- Create: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Create: `composeApp/src/commonMain/composeResources/values-en/strings.xml`
- Modify: every `*Screen.kt` + `BottomNavBar.kt` + stub-screens that reference user-visible strings

- [ ] **Step 1: Create `values/strings.xml` (Swedish defaults)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Bottom-nav -->
    <string name="tab_scan">Skanna</string>
    <string name="tab_encyclopedia">Uppslagsverk</string>
    <string name="tab_diary">Dagbok</string>
    <string name="tab_badges">Märken</string>

    <!-- Top-bar titles -->
    <string name="title_encyclopedia">UPPSLAGSVERK</string>

    <!-- Profile -->
    <string name="profile_label_description">BESKRIVNING</string>
    <string name="profile_label_migration">FLYTTNING</string>
    <string name="profile_label_photos">FOTOGRAFIER</string>
    <string name="profile_back">Tillbaka</string>

    <!-- Filter -->
    <string name="filter_title">Filter</string>
    <string name="filter_button">⚙ Filter</string>
    <string name="filter_label_abundance">FÖREKOMST</string>
    <string name="filter_label_region">REGION</string>
    <string name="filter_label_month">MÅNAD</string>
    <string name="filter_apply">Visa %1$d arter</string>
    <string name="filter_reset">Återställ</string>

    <!-- Badges + abundance presentation -->
    <string name="badge_common">ALLMÄN</string>
    <string name="badge_uncommon">OVANLIG</string>

    <!-- Search -->
    <string name="search_placeholder">Sök art, släkte eller familj…</string>
    <string name="search_empty_title">Ingen art matchar</string>
    <string name="search_empty_body">Prova andra filter eller sök på vetenskapligt namn.</string>

    <!-- Sparse-data empty states -->
    <string name="empty_description">Beskrivning kommer i en framtida uppdatering.</string>
    <string name="empty_migration">Migrationsdata saknas för denna art.</string>
    <string name="empty_photos">Inga foton tillgängliga.</string>

    <!-- SpeciesNotFound -->
    <string name="not_found_title">Art saknas.</string>
    <string name="not_found_body">Tryck tillbaka för att gå till listan.</string>

    <!-- Stub screens -->
    <string name="stub_scan_body">Den här funktionen kommer i Plan 4 — ML &amp; Camera.</string>
    <string name="stub_diary_body">Den här funktionen kommer i Plan 5 — Dagbok.</string>
    <string name="stub_badges_body">Den här funktionen kommer i Plan 5 — Gamification.</string>

    <!-- Loading -->
    <string name="loading">Laddar…</string>
</resources>
```

- [ ] **Step 2: Create `values-en/strings.xml` (English overrides)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="tab_scan">Scan</string>
    <string name="tab_encyclopedia">Encyclopedia</string>
    <string name="tab_diary">Diary</string>
    <string name="tab_badges">Badges</string>

    <string name="title_encyclopedia">ENCYCLOPEDIA</string>

    <string name="profile_label_description">DESCRIPTION</string>
    <string name="profile_label_migration">MIGRATION</string>
    <string name="profile_label_photos">PHOTOS</string>
    <string name="profile_back">Back</string>

    <string name="filter_title">Filter</string>
    <string name="filter_button">⚙ Filter</string>
    <string name="filter_label_abundance">ABUNDANCE</string>
    <string name="filter_label_region">REGION</string>
    <string name="filter_label_month">MONTH</string>
    <string name="filter_apply">Show %1$d species</string>
    <string name="filter_reset">Reset</string>

    <string name="badge_common">COMMON</string>
    <string name="badge_uncommon">UNCOMMON</string>

    <string name="search_placeholder">Search species, genus or family…</string>
    <string name="search_empty_title">No species match</string>
    <string name="search_empty_body">Try other filters or search by scientific name.</string>

    <string name="empty_description">A description will be added in a future update.</string>
    <string name="empty_migration">No migration data available for this species.</string>
    <string name="empty_photos">No photos available.</string>

    <string name="not_found_title">Species not found.</string>
    <string name="not_found_body">Press back to return to the list.</string>

    <string name="stub_scan_body">This feature is coming in Plan 4 — ML &amp; Camera.</string>
    <string name="stub_diary_body">This feature is coming in Plan 5 — Diary.</string>
    <string name="stub_badges_body">This feature is coming in Plan 5 — Gamification.</string>

    <string name="loading">Loading…</string>
</resources>
```

- [ ] **Step 3: Replace hard-coded strings throughout the UI**

For each composable file that has user-visible strings, replace `"literal"` with `stringResource(Res.string.<key>)`. Typical patterns:

```kotlin
// before
Text("UPPSLAGSVERK", ...)
// after
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.title_encyclopedia
import org.jetbrains.compose.resources.stringResource
Text(stringResource(Res.string.title_encyclopedia), ...)
```

For the filter "Visa N arter":

```kotlin
Text(stringResource(Res.string.filter_apply, previewCount))
```

(Compose Multiplatform's `stringResource` takes vararg `Any` for `%1$d` etc.)

Files to update:
- `BottomNavBar.kt` — 4 tab labels
- `ScanStubScreen.kt`, `DiaryStubScreen.kt`, `BadgesStubScreen.kt` — title + body
- `EncyclopediaScreen.kt` — top-bar title, search placeholder, filter button text, EmptyState title/body, "Loading"
- `SpeciesProfileScreen.kt` — chip text uppercase replaced via badge_common/badge_uncommon, section labels, NotFound state
- `FilterBottomSheet.kt` — title, group labels, Återställ, Visa N arter
- `SpeciesRow.kt` — ALLMÄN badge

Use a code-search to make sure no Swedish literal remains:
```bash
grep -rn '"[A-ZÅÄÖa-zåäö ]\+"' composeApp/src/commonMain/kotlin/se/birdy/app/ui/ | grep -v '// '
```

(Some are non-user-visible — e.g. `"hero"`, `"species_id"` as DB keys — leave those.)

- [ ] **Step 4: Build + install + verify Swedish + English**

```bash
./gradlew :composeApp:assembleDebug && ./gradlew :androidApp:installDebug && \
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

Verify Swedish (default): all UI text matches `values/strings.xml`.

Switch device locale to English (Settings → Allmänt → Språk):
- App restarts → bottom-nav: Scan / Encyclopedia / Diary / Badges
- Top-bar: ENCYCLOPEDIA
- Profile: DESCRIPTION / MIGRATION / PHOTOS

(Note: this only switches UI strings — species *names* are still picked from DB by repo's locale fallback, which currently uses Locale.SV via `AppGraph.defaultLocale`. Plan 6 will wire system-locale → repo-locale; Plan 3 keeps it pinned.)

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/composeResources/ \
  composeApp/src/commonMain/kotlin/se/birdy/app/
git commit -m "$(cat <<'EOF'
feat(app): i18n — extract all hard-coded strings to compose resources

Adds composeResources/values/strings.xml (sv defaults) and
values-en/strings.xml (en overrides) covering bottom-nav labels, top-bar
titles, section labels, filter UI, empty states, stub-screen bodies, and
sparse-data fallbacks.

Replaces all hard-coded user-visible strings in EncyclopediaScreen,
SpeciesProfileScreen, FilterBottomSheet, SpeciesRow, BottomNavBar, and
the 3 stub screens with stringResource(Res.string.*).

System-locale switching now toggles UI language; species name fallback
remains pinned to AppGraph.defaultLocale (Locale.SV) — wiring system
locale to repo locale is deferred to Plan 6.

Plan 3 Task 10 of 11.
EOF
)"
```

---

### Task 11: Polish + CI + device-verification + tag

**Files:**
- Modify: any leftover hard-coded strings or visual nits
- Create: `docs/superpowers/screenshots/2026-05-XX-*.png` (7 screenshots)
- Modify: `CLAUDE.md`
- Modify: `.github/workflows/*.yml` if needed
- Create: git tag `v0.3.0-encyclopedia`

- [ ] **Step 1: Run full quality gate**

```bash
./gradlew :composeApp:check ktlintCheck detekt :shared:content:validateSpeciesData :shared:content:buildSpeciesDb :composeApp:assembleDebug
```

Expected: all green. If ktlint complains about generated Compose-resource accessors, the `tasks.withType<BaseKtLintCheckTask>()` exclude in `composeApp/build.gradle.kts` should cover it; bump the rule if needed.

- [ ] **Step 2: Take 7 screenshots on SM-S918B**

```bash
ADB="/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe"
DEST="docs/superpowers/screenshots"
DATE=$(date +%Y-%m-%d)

# 1. bottom-nav (any tab open, snap full screen)
$ADB shell screencap -p /sdcard/snap.png && \
  $ADB pull /sdcard/snap.png "$DEST/$DATE-bottom-nav.png"

# Repeat for the other 6 — manually navigate, then re-run screencap+pull:
# 2. encyclopedia-list   (default view, scrolled to show both sections)
# 3. encyclopedia-search (typed "blå" in search)
# 4. encyclopedia-filter (filter bottom sheet open with 1+ chips selected)
# 5. profile-talgoxe     (Talgoxe profile, expanded hero)
# 6. profile-sparse      (a sparse-data species, e.g. alaudidae rarity)
# 7. profile-collapsed   (any profile, scrolled enough that toolbar is collapsed)
```

- [ ] **Step 3: Update CLAUDE.md status**

Edit the **"Status (2026-05-04)"** line and the Plan-of-plans table to mark Plan 3 ✅ and reference the tag. Append a "Plan 3 status (KLAR)" section similar to Plan 2a's structure with a brief recap of the 11 tasks.

- [ ] **Step 4: Build the release-ready debug APK + APK-size sanity check**

```bash
./gradlew :composeApp:assembleDebug
ls -lh androidApp/build/outputs/apk/debug/*.apk
```

Sanity: APK should be roughly the same size as Plan 2a's (~30-40MB given bundled images + species.db). If it grew >5MB without explanation, investigate before tagging.

- [ ] **Step 5: Commit polish + screenshots + CLAUDE.md**

```bash
git add docs/superpowers/screenshots/ CLAUDE.md
git commit -m "$(cat <<'EOF'
docs(plan-3): screenshots + CLAUDE.md status update for Plan 3

Adds 7 device-verification screenshots from SM-S918B covering bottom-nav,
encyclopedia list, search, filter, profile (full + sparse), and collapsed
toolbar.

Updates CLAUDE.md to mark Plan 3 as ✅ klar with tag v0.3.0-encyclopedia
and adds a "Plan 3 status (KLAR)" recap section. Plan 2b is now the
queued workstream — runbook references stay intact.

Plan 3 Task 11 of 11 (final).
EOF
)"
```

- [ ] **Step 6: Tag and push**

```bash
git tag -a v0.3.0-encyclopedia -m "Plan 3 milestone: Encyclopedia + species profile + bottom-nav skeleton"
git push origin main
git push origin v0.3.0-encyclopedia
```

- [ ] **Step 7: Post-mortem note**

Add a short note to CLAUDE.md "Plan 3 status" with anything surprising during execution (e.g. Coil bug, navigation regression, time spent per task). This feeds Plan 2b resumption + Plan 4 planning.

---

## Self-review notes

**Spec coverage (skim §1–§15 of spec, point to a task):**
- §1 acceptance criteria: Tasks 1-11 cover each bullet (bottom-nav→Task 2, list→Task 3, search→Task 5, filter→Task 6, profile→Task 7, sparse→Task 8, images→Task 9, i18n→Task 10, ViewModel tests→Tasks 5+7, device-verify+tag→Task 11).
- §2 12 låsta beslut: all 12 are encoded in Tasks 1-11.
- §3 architecture + deps: Task 1.
- §3.4 nav graph: Task 2.
- §4.1 Encyclopedia: Tasks 3, 5, 6.
- §4.2 FilterBottomSheet: Task 6.
- §4.3 SpeciesProfile: Tasks 7-9.
- §4.4 stub screens: Task 2.
- §5 sparse-data: Task 8.
- §5.4 fel-scenarier: SpeciesNotFound state (Task 7), HeroImage fallback (Task 9), search-empty (Task 5).
- §6 testing: ViewModel tests in Tasks 5, 6, 7. Repository tests in Task 4. Compose UI tests intentionally deferred (see §6.4 — manual device-verifiering räcker; if a real bug surfaces, write the test then).
- §7 i18n: Task 10.
- §8 image assets: Task 9.
- §9 task-decomposition preview: matches plan tasks 1:1 (with Task 4 inserted as the search-extension).
- §11 datakvalitets-konsekvenser: design built into Tasks 8 (SectionBlock) + 9 (HeroImage fallback).

**Naming consistency:**
- `SpeciesId.raw` everywhere (not `value`). ✅
- `EncyclopediaUiState.Loading/Loaded/Empty` consistent across Tasks 3, 5, 6.
- `SpeciesProfileUiState.Loading/Loaded/NotFound` consistent across Task 7.
- `SectionBlock(label, isEmpty, emptyMessage, content)` signature stable across Tasks 8 + 9.
- `HeroImage(imagePath, modifier, cornerRadius)` signature stable across Task 9.
- `FilterBottomSheet(initial, previewCount, onApply, onDismiss)` matches Task 6 wiring.

**Placeholder scan:** No "TBD"/"TODO" in tasks (only the explicit `// TASK-N REPLACE` markers in Task 2's AppScaffold which Tasks 3 + 7 explicitly remove). All test code is concrete. All commit messages are written out.

**Spec contradictions caught:** Spec §3.4 used `id.value` — actual field is `raw`. Plan uses `raw` consistently and notes this in the "Naming notes" section.

---

## Improvements baked in (vs. raw spec preview §9)

These additions go beyond the spec's preview:

1. **Task 1 establishes commonTest source set** before any feature code — the spec didn't acknowledge that composeApp had no test source set yet.
2. **`FakeSpeciesRepository` test helper in Task 1** — enables ViewModel tests in Tasks 5, 6, 7 without setup-cost duplication.
3. **`AppRoute.EncyclopediaList` start destination of nested graph** — Compose Navigation 2.8 requires distinct start type, not noted in spec.
4. **`searchByNameOrScientific` SQL query** uses named parameters and JOINs Species — cleaner than overloading existing `searchByName`.
5. **Inner SQL limit of 200 + Kotlin `take(50)`** in extended `search()` — ensures post-filter still hits ~50 results.
6. **`distinctBy(species_id)` in search-mapping** — guards against future double-rows from JOIN expansion.
7. **`// TASK-N REPLACE` markers in AppScaffold** — discoverable via grep so half-done states are obvious during execution.
8. **Material Icons Extended dep added in Task 2** — the standard `compose.material` only ships Filled basics, not `MenuBook` / `EmojiEvents` / `Bookmark`.

---

**Plan complete. Two execution options:**

**1. Subagent-Driven (recommended)** — Dispatch a fresh subagent per task, review between tasks, fast iteration via `superpowers:subagent-driven-development`.

**2. Inline Execution** — Execute tasks in this session via `superpowers:executing-plans`, batch execution with checkpoints for review.

Which approach?
