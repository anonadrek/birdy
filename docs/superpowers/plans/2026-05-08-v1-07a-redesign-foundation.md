# Plan 7a — Redesign Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lägg foundationen för redesign:en — nya färg-tokens, italic-mixed-headline-helper, HeroZone-composable, SQLDelight-migration (stamp_number), `:shared:datastore`-modul, UserPreferences, Onboarding (3 sidor), Settings-skärm, byt bottom-bar-namn till `Listen · Archive · Lifelist · Badges`. **Ingen IA-omdaning här** — flikarna behåller sina existerande target-skärmar (`ScanScreen`, `EncyclopediaScreen`, `DiaryScreen`, `BadgesScreen`); bara namn + ikoner ändras. Tag `v0.7.0a-foundation`.

**Architecture:** Tema-tokens utökas i existerande `composeApp/.../ui/theme/Color.kt`. `ItalicMixedText` parsar syntax `Birdy *of* Sweden` till `AnnotatedString` med `SpanStyle(fontStyle = Italic, color = AccentCopperLight)` på `*...*`-segment. `HeroZone` är en slot-baserad composable med `Brush.verticalGradient(HeroMossLight → HeroMossDeep)` + 24dp rundade botten-hörn. Ny `:shared:datastore`-modul wrappar `androidx.datastore.preferences` (Android-only i v1; KMP-stub för iOS-skelett). `UserPreferences`-interface i `:shared:domain`, impl i `:shared:datastore`. `OnboardingScreen` använder `HorizontalPager` (foundation-pager). Migration: SQLDelight `1.sqm` lägger `stamp_number INTEGER` + chronologisk backfill via `ROW_NUMBER()`-equivalent (SQLite-portable variant via correlated subquery). Bottom-bar-rename byter bara `tab_*`-strängar i `strings.xml` + `BottomNavBar.kt`-ikoner; routes/destinations rörs inte i 7a (sker i 7b).

**Tech Stack:** Kotlin Multiplatform 2.1.20 · Compose Multiplatform 1.7.3 · SQLDelight 2.0.2 · androidx.datastore-preferences 1.1.1 · okio 3.9.0 · kotlinx.coroutines 1.9.0 · kotlinx.serialization 1.7.3 · Compose Foundation Pager 1.7.3 · JUnit 5 · Turbine 1.1.0 · ktlint 12.1.2 · detekt 1.23.7.

**Spec:** `docs/superpowers/specs/2026-05-08-birdy-bird-scanner-redesign-design.md` (commit `232f528`).

---

## Avvikelser från spec

### 1. `match_percent`-kolumn skippas — använd existerande `confidence`

Spec §11 föreslår `ALTER TABLE observation ADD COLUMN match_percent INTEGER`. Men `shared/data/.../Observation.sq` har redan `confidence REAL NOT NULL` (0.0–1.0) som lagrar samma data. Match-skärmen i Plan 7c kommer visa `(confidence * 100).toInt()` — ingen ny kolumn behövs.

**Beslut:** Migration lägger bara `stamp_number INTEGER`. `confidence` återanvänds.

### 2. DataStore via `androidx.datastore-preferences` (Android-only) — inte okio-KMP-variant

Spec §11 nämner "DataStore via okio (KMP)". Men `okio.Path`-baserad DataStore kräver `multiplatform-settings`-wrapper eller manuell impl per plattform, vilket är 1+ task extra. v1 är Android-only; iOS-skelettet kompilerar utan att behöva DataStore. Lösning: `androidx.datastore-preferences` direkt i `androidMain`, `expect class UserPreferencesStore` i `commonMain` med iOS-stub som kastar `NotImplementedError` (samma mönster som existerande `DatabaseFactory.kt` i `:shared:data`).

**Beslut:** Android-first DataStore. iOS-port delegeras till v1.5 där KMP-DataStore alternativ utvärderas riktigt.

### 3. Bottom-bar-rename behåller existerande target-skärmar i 7a

Spec §3 byter både namn OCH target-skärmar (Listen → ny ListenLauncher, Archive → ny ArchiveScreen, etc). Plan 7a byter bara **namn + ikoner** — `Listen` pekar på existerande `ScanScreen`, `Archive` → `EncyclopediaScreen`, `Lifelist` → `DiaryScreen`, `Badges` → existerande `BadgesScreen`. De nya skärmarna byggs i Plan 7b.

**Beslut:** Foundation-tag (`v0.7.0a-foundation`) innehåller bara visuell + struktur-foundation. Användaren kommer se nya tab-namn + onboarding men resten av appen oförändrad.

---

## File Structure

### Skapas

| Fil | Ansvar |
|---|---|
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/ItalicMixedText.kt` | Parsar `*...*`-syntax → `AnnotatedString` med italic copper-spans |
| `composeApp/src/commonTest/kotlin/se/birdy/app/ui/theme/ItalicMixedTextTest.kt` | Tabell-driven test för parsing |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/HeroZone.kt` | Slot-baserad gradient-composable med rundade botten-hörn |
| `shared/data/src/commonMain/sqldelight/databases/BirdyData.db` | (Genereras automatiskt av SQLDelight `verifyMigrations`) |
| `shared/data/src/commonMain/sqldelight/migrations/1.sqm` | Migration: ADD COLUMN `stamp_number` + chronologisk backfill |
| `shared/data/src/jvmTest/kotlin/se/birdy/data/observation/StampNumberMigrationTest.kt` | Migration-test: lägg 5 obs på v1-schema → migrera → assert stamp_number 1..5 chronologiskt |
| `shared/datastore/build.gradle.kts` | Ny KMP-modul |
| `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/UserPreferences.kt` | Interface med 6 properties + suspend-setters |
| `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/UserPreferencesStore.kt` | `expect class` med `factory(context: Any?): UserPreferences` |
| `shared/datastore/src/androidMain/kotlin/se/birdy/datastore/UserPreferencesStore.android.kt` | `actual` med `androidx.datastore-preferences` (Context-baserad) |
| `shared/datastore/src/iosMain/kotlin/se/birdy/datastore/UserPreferencesStore.ios.kt` | `actual`-stub som kastar `NotImplementedError` |
| `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/InMemoryUserPreferences.kt` | In-memory `MutableStateFlow`-impl — i commonMain så composeApp commonTest kan använda den för VM-tester |
| `shared/datastore/src/jvmMain/kotlin/se/birdy/datastore/UserPreferencesStore.jvm.kt` | `actual` som returnerar `InMemoryUserPreferences()` |
| `shared/datastore/src/jvmTest/kotlin/se/birdy/datastore/InMemoryUserPreferencesTest.kt` | Verifierar att alla keys round-trip:ar |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/OnboardingUiState.kt` | Sealed: `Loading` / `Visible(page: Int)` / `Done` |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/OnboardingViewModel.kt` | `pageIndex: StateFlow<Int>`, `nameInput`, `complete()` skriver till DataStore |
| `composeApp/src/commonTest/kotlin/se/birdy/app/ui/onboarding/OnboardingViewModelTest.kt` | Tester: tom name → fallback `Min`/`My`; complete sätter `has_seen_onboarding = true` |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/OnboardingScreen.kt` | `HorizontalPager(pageCount = 3)` + sida 1/2/3 composables |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsUiState.kt` | `Loaded(name: String, language: AppLanguage)` |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/AppLanguage.kt` | `enum { SV, EN, SYSTEM }` |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsViewModel.kt` | Wrappar `UserPreferences` |
| `composeApp/src/commonTest/kotlin/se/birdy/app/ui/settings/SettingsViewModelTest.kt` | Test name-edit + language-toggle |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsScreen.kt` | Lista med Name + Language + About-rader |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppGate.kt` | Composable som läser `has_seen_onboarding` → visar `OnboardingScreen` eller `AppScaffold` |
| `docs/superpowers/screenshots/v0.7.0a-foundation/` | (Skapas; tomma initialt — fylls efter device-verify) |

### Modifieras

| Fil | Ändring |
|---|---|
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/Color.kt` | Lägg `OffwhiteWarm`, `AccentCopperLight`, `PremiumGold*` |
| `shared/data/src/commonMain/sqldelight/se/birdy/data/db/Observation.sq` | Lägg `stamp_number INTEGER` i CREATE TABLE; ny `insert`-query med `MAX+1` |
| `shared/data/build.gradle.kts` | Aktivera `schemaOutputDirectory` + `verifyMigrations = true` på `BirdyData` |
| `shared/domain/src/commonMain/kotlin/se/birdy/domain/observation/Observation.kt` | Lägg `stampNumber: Int` (nullable för bakåtkompat under migration) |
| `shared/domain/src/commonMain/kotlin/se/birdy/domain/observation/ObservationRepository.kt` | Inga signatur-ändringar; impl uppdateras |
| `shared/data/src/commonMain/kotlin/se/birdy/data/observation/SqlDelightObservationRepository.kt` | Mappa nya kolumner, anpassa `insert` |
| `shared/data/src/jvmTest/kotlin/se/birdy/data/observation/SqlDelightObservationRepositoryTest.kt` | Lägg test för `stamp_number` auto-increment |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | Byt `tab_scan/encyclopedia/diary/badges` till `Listen/Archive/Lifelist/Badges` (svenska behåller — se §Task 10); lägg onboarding/settings-strängar |
| `composeApp/src/commonMain/composeResources/values-en/strings.xml` | Mirror EN |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BottomNavBar.kt` | Byt ikoner: Hearing (Listen), Book (Archive), CollectionsBookmark (Lifelist), Stars (Badges) |
| `composeApp/src/commonMain/kotlin/se/birdy/app/App.kt` | Wrappa `AppScaffold` i `AppGate` |
| `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt` | Lägg `userPreferences: UserPreferences` constructor-param + `onboardingViewModel()` + `settingsViewModel()` factories |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt` | Lägg `@Serializable data object Settings : AppRoute` |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt` | Lägg `composable<AppRoute.Settings>` |
| `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt` | Skapa `UserPreferencesStore.factory(applicationContext)` → passa till AppGraph |
| `androidApp/build.gradle.kts` | `implementation(project(":shared:datastore"))` |
| `composeApp/build.gradle.kts` | `implementation(project(":shared:datastore"))` |
| `settings.gradle.kts` | `include(":shared:datastore")` |
| `gradle/libs.versions.toml` | Lägg `androidx-datastore-preferences = "1.1.1"` + plugin/lib refs |

---

## Task 1: Tema-tokens

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/Color.kt`

- [ ] **Step 1: Lägg nya tokens i Color.kt**

Öppna `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/Color.kt` och lägg till efter `LabelOnCreme`:

```kotlin
// ===== Plan 7 redesign tokens (locked 2026-05-08) =====

// Warmer offwhite for hero text — replaces TextOnHero on redesigned screens.
// Slightly more white-toned than #F0EAD8 for pop on mossgrön gradient.
val OffwhiteWarm = Color(0xFFFFFCF0)

// Lighter copper for italic accents within hero headlines (e.g. *fånga* in
// "Tre sätt att *fånga*."). Brighter than AccentCopper so italic segments pop.
val AccentCopperLight = Color(0xFFE0A47C)

// Premium-tier visual gold gradient — used only on the premium-sparkle marker
// in onboarding (Page 3) and Listen launcher (Audio-locked card). NOT a CTA.
val PremiumGoldLight = Color(0xFFFFE8B5)
val PremiumGoldMid = Color(0xFFE8C374)
val PremiumGoldDeep = Color(0xFFB88944)
```

- [ ] **Step 2: Verifiera kompilering**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/Color.kt
git commit -m "feat(ui-theme): Plan 7a Task 1 — add OffwhiteWarm + AccentCopperLight + PremiumGold tokens"
```

---

## Task 2: ItalicMixedText composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/ItalicMixedText.kt`
- Create: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/theme/ItalicMixedTextTest.kt`

`ItalicMixedText` är hjärtat i den nya headline-stilen. Användning:
```kotlin
ItalicMixedText("Three ways to *catch*.", style = MaterialTheme.typography.headlineLarge)
// renders: "Three ways to " (regular) + "catch" (italic copper) + "." (regular)
```

- [ ] **Step 1: Skriv failing test**

Skapa `composeApp/src/commonTest/kotlin/se/birdy/app/ui/theme/ItalicMixedTextTest.kt`:

```kotlin
package se.birdy.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class ItalicMixedTextTest {
    private val accent = Color(0xFFE0A47C)

    @Test
    fun `parses single italic segment`() {
        val result = parseItalicMixed("Three ways to *catch*.", accent)
        assertEquals("Three ways to catch.", result.text)
        val spans = result.spanStyles
        assertEquals(1, spans.size)
        assertEquals(14, spans[0].start) // "Three ways to "
        assertEquals(19, spans[0].end)   // "catch"
        assertEquals(FontStyle.Italic, spans[0].item.fontStyle)
        assertEquals(accent, spans[0].item.color)
    }

    @Test
    fun `parses two italic segments`() {
        val result = parseItalicMixed("*Albin's* secret *list*.", accent)
        assertEquals("Albin's secret list.", result.text)
        assertEquals(2, result.spanStyles.size)
    }

    @Test
    fun `no asterisks returns plain text with no spans`() {
        val result = parseItalicMixed("Plain text only.", accent)
        assertEquals("Plain text only.", result.text)
        assertEquals(0, result.spanStyles.size)
    }

    @Test
    fun `unmatched asterisk is treated as literal`() {
        val result = parseItalicMixed("Half-italic *open", accent)
        assertEquals("Half-italic *open", result.text)
        assertEquals(0, result.spanStyles.size)
    }

    @Test
    fun `empty asterisk pair produces no span`() {
        val result = parseItalicMixed("Hello ** world.", accent)
        assertEquals("Hello  world.", result.text)
        assertEquals(0, result.spanStyles.size)
    }

    @Test
    fun `escaped asterisk via backslash is literal`() {
        val result = parseItalicMixed("5 \\* 3 = 15", accent)
        assertEquals("5 * 3 = 15", result.text)
        assertEquals(0, result.spanStyles.size)
    }
}
```

- [ ] **Step 2: Run test — verify FAIL**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ItalicMixedTextTest*"`
Expected: FAIL — `parseItalicMixed` not defined.

- [ ] **Step 3: Skapa `ItalicMixedText.kt`**

```kotlin
package se.birdy.app.ui.theme

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign

/**
 * Parses inline italic-mixed syntax: `Birdy *of* Sweden` → AnnotatedString where
 * `*of*` is rendered with [SpanStyle(fontStyle = Italic, color = accent)].
 *
 * Escape literal asterisks with `\*`. Unmatched single asterisk is left as-is.
 * Empty pairs `**` collapse to nothing.
 */
internal fun parseItalicMixed(input: String, accent: Color): AnnotatedString =
    AnnotatedString.Builder().apply {
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == '\\' && i + 1 < input.length && input[i + 1] == '*' -> {
                    append('*')
                    i += 2
                }
                c == '*' -> {
                    val end = input.indexOf('*', startIndex = i + 1)
                    if (end < 0) {
                        // Unmatched — treat as literal
                        append('*')
                        i += 1
                    } else if (end == i + 1) {
                        // Empty pair — drop both
                        i += 2
                    } else {
                        val segment = input.substring(i + 1, end)
                        val start = length
                        append(segment)
                        addStyle(
                            SpanStyle(fontStyle = FontStyle.Italic, color = accent),
                            start = start,
                            end = start + segment.length,
                        )
                        i = end + 1
                    }
                }
                else -> {
                    append(c)
                    i += 1
                }
            }
        }
    }.toAnnotatedString()

@Composable
fun ItalicMixedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = style.color,
    italicAccent: Color = AccentCopperLight,
    textAlign: TextAlign? = null,
) {
    val annotated = parseItalicMixed(text, italicAccent)
    BasicText(
        text = annotated,
        modifier = modifier,
        style = style.copy(color = color, textAlign = textAlign ?: style.textAlign),
    )
}
```

- [ ] **Step 4: Run test — verify PASS**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ItalicMixedTextTest*"`
Expected: PASS — 6 tests.

- [ ] **Step 5: ktlint + detekt**

Run: `./gradlew :composeApp:ktlintCheck :composeApp:detekt`
Expected: SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/ItalicMixedText.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/ui/theme/ItalicMixedTextTest.kt
git commit -m "feat(ui-theme): Plan 7a Task 2 — ItalicMixedText composable + 6 tests"
```

---

## Task 3: HeroZone composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/HeroZone.kt`

`HeroZone` används på Listen/Archive/Lifelist/Badges/Onboarding/Match. Slot-baserad: konsumenter lägger sitt content (breadcrumb + headline + sub) inuti.

- [ ] **Step 1: Skapa `HeroZone.kt`**

```kotlin
package se.birdy.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Mossgrön gradient-zon med rundade botten-hörn.
 *
 * Används som hero på alla redesignade skärmar (Listen / Archive / Lifelist /
 * Badges / Match / Settings). Innehåll (breadcrumb, headline, sub, optional
 * stats) sätts i `content`-slot:en. Padding inuti default = 24dp horisontellt,
 * 28dp vertikalt — anpassa per skärm via `contentPadding`.
 *
 * Bottom-corner-radius 24dp släpper hero från innehållet under utan att vara
 * en kant-till-kant-banner.
 */
@Composable
fun HeroZone(
    modifier: Modifier = Modifier,
    bottomCornerRadius: Dp = 24.dp,
    contentPaddingHorizontal: Dp = 24.dp,
    contentPaddingTop: Dp = 28.dp,
    contentPaddingBottom: Dp = 28.dp,
    content: @Composable () -> Unit,
) {
    val gradient =
        Brush.verticalGradient(
            colors = listOf(HeroMossLight, HeroMossMid, HeroMossDeep),
        )
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = bottomCornerRadius, bottomEnd = bottomCornerRadius))
                .background(gradient)
                .padding(
                    start = contentPaddingHorizontal,
                    end = contentPaddingHorizontal,
                    top = contentPaddingTop,
                    bottom = contentPaddingBottom,
                ),
    ) {
        content()
    }
}
```

- [ ] **Step 2: Verifiera kompilering**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: ktlint + detekt**

Run: `./gradlew :composeApp:ktlintCheck :composeApp:detekt`
Expected: SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/HeroZone.kt
git commit -m "feat(ui-theme): Plan 7a Task 3 — HeroZone composable (gradient + rounded bottom corners)"
```

---

## Task 4: SQLDelight migration — stamp_number

**Files:**
- Modify: `shared/data/src/commonMain/sqldelight/se/birdy/data/db/Observation.sq`
- Create: `shared/data/src/commonMain/sqldelight/migrations/1.sqm`
- Modify: `shared/data/build.gradle.kts`
- Modify: `shared/domain/src/commonMain/kotlin/se/birdy/domain/observation/Observation.kt`
- Modify: `shared/data/src/commonMain/kotlin/se/birdy/data/observation/SqlDelightObservationRepository.kt`
- Create: `shared/data/src/jvmTest/kotlin/se/birdy/data/observation/StampNumberMigrationTest.kt`
- Modify: `shared/data/src/jvmTest/kotlin/se/birdy/data/observation/SqlDelightObservationRepositoryTest.kt`

### Bakgrund: SQLDelight verifyMigrations

`verifyMigrations = true` kräver att `schemaOutputDirectory` finns (`databases/BirdyData.db`). Genereras automatiskt vid first build efter konfig — ska committas.

Migration `1.sqm` ligger i `src/commonMain/sqldelight/migrations/`. SQLDelight tillämpar den när db-version i runtime-databasen är < 1.

**Backfill-strategi:** För befintliga rader sätter vi `stamp_number = (SELECT COUNT(*) FROM observation o2 WHERE o2.captured_at_ms <= observation.captured_at_ms)`. Detta ger äldsta obs `#1`, näst äldsta `#2`, etc.

- [ ] **Step 1: Bumpa `Observation.sq` schema + ändra insert**

Öppna `shared/data/src/commonMain/sqldelight/se/birdy/data/db/Observation.sq` och ersätt hela filen:

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
    location_label  TEXT,
    stamp_number    INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX observation_captured_at_idx ON observation(captured_at_ms DESC);
CREATE INDEX observation_stamp_number_idx ON observation(stamp_number DESC);

selectAll:
SELECT * FROM observation ORDER BY captured_at_ms DESC;

selectAllByStampNumber:
SELECT * FROM observation ORDER BY stamp_number DESC;

selectById:
SELECT * FROM observation WHERE id = ?;

nextStampNumber:
SELECT COALESCE(MAX(stamp_number), 0) + 1 FROM observation;

insert:
INSERT INTO observation(
    id, species_id, captured_at_ms, saved_at_ms,
    photo_path, note, confidence,
    latitude, longitude, location_label,
    stamp_number
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
    (SELECT COALESCE(MAX(stamp_number), 0) + 1 FROM observation)
);

updateNote:
UPDATE observation SET note = ? WHERE id = ?;

deleteById:
DELETE FROM observation WHERE id = ?;
```

> **Why DEFAULT 0?** Migration sätter sant värde via UPDATE direkt efter ALTER. `0` blir bara ett kort övergångs-tillstånd inuti samma transaction — aldrig synligt i runtime.

- [ ] **Step 2: Skapa migration `1.sqm`**

Skapa `shared/data/src/commonMain/sqldelight/migrations/1.sqm`:

```sql
-- Plan 7a — add stamp_number with chronological backfill.
-- Existing observations get sequential numbers ordered by captured_at_ms ASC
-- (tie-break by id for determinism), so oldest obs becomes #1.

ALTER TABLE observation ADD COLUMN stamp_number INTEGER NOT NULL DEFAULT 0;

UPDATE observation
SET stamp_number = 1 + (
    SELECT COUNT(*)
    FROM observation o2
    WHERE o2.captured_at_ms < observation.captured_at_ms
       OR (o2.captured_at_ms = observation.captured_at_ms AND o2.id < observation.id)
);

CREATE INDEX observation_stamp_number_idx ON observation(stamp_number DESC);
```

> **Tie-break:** Om två obs har identisk `captured_at_ms` (osannolikt men möjligt vid burst-save) använder vi `id` (UUID-string) som secondary sort så ordningen är deterministisk.

- [ ] **Step 3: Aktivera `schemaOutputDirectory` + `verifyMigrations`**

Öppna `shared/data/build.gradle.kts` och uppdatera `sqldelight`-block:

```kotlin
sqldelight {
    databases {
        create("BirdyData") {
            packageName.set("se.birdy.data.db")
            srcDirs.setFrom("src/commonMain/sqldelight")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
```

- [ ] **Step 4: Uppdatera `Observation`-domän-modell**

Öppna `shared/domain/src/commonMain/kotlin/se/birdy/domain/observation/Observation.kt`:

```kotlin
package se.birdy.domain.observation

import kotlinx.datetime.Instant

data class Observation(
    val id: String,
    val speciesId: String,
    val capturedAt: Instant,
    val savedAt: Instant,
    val photoPath: String,
    val note: String,
    val confidence: Float,
    val latitude: Double?,
    val longitude: Double?,
    val locationLabel: String?,
    val stampNumber: Int,
)
```

- [ ] **Step 5: Uppdatera `SqlDelightObservationRepository`**

Öppna `shared/data/src/commonMain/kotlin/se/birdy/data/observation/SqlDelightObservationRepository.kt` och uppdatera mappnings-funktionen + insert-call.

Hitta mapper (typiskt en privat extension `SqldelightObservation.toDomain()`):

```kotlin
private fun se.birdy.data.db.Observation.toDomain(): Observation =
    Observation(
        id = id,
        speciesId = species_id,
        capturedAt = Instant.fromEpochMilliseconds(captured_at_ms),
        savedAt = Instant.fromEpochMilliseconds(saved_at_ms),
        photoPath = photo_path,
        note = note,
        confidence = confidence.toFloat(),
        latitude = latitude,
        longitude = longitude,
        locationLabel = location_label,
        stampNumber = stamp_number.toInt(),
    )
```

Insert-funktionen i samma fil — den anropar `queries.insert(...)` som genereras från `.sq`. SQLDelight genererar nu `insert` med 10 parameters (utan `stamp_number` — det beräknas i SQL). Så call-site är oförändrad förutom att signaturen passar.

- [ ] **Step 6: Skriv migration-test**

Skapa `shared/data/src/jvmTest/kotlin/se/birdy/data/observation/StampNumberMigrationTest.kt`:

```kotlin
package se.birdy.data.observation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import se.birdy.data.db.BirdyData

class StampNumberMigrationTest {
    @Test
    fun `migration backfills stamp_number chronologically`() {
        // 1. Create v0 schema (pre-migration) manually
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, V0_SCHEMA, 0)
        // Insert 5 observations with non-monotonic timestamps
        val rows = listOf(
            // (id, captured_at_ms)
            "obs-c" to 3000L,
            "obs-a" to 1000L,
            "obs-e" to 5000L,
            "obs-b" to 2000L,
            "obs-d" to 4000L,
        )
        rows.forEach { (id, captured) ->
            driver.execute(
                identifier = null,
                sql = """
                    INSERT INTO observation (id, species_id, captured_at_ms, saved_at_ms,
                        photo_path, note, confidence)
                    VALUES (?, 'Q1', ?, ?, '/tmp/x.jpg', '', 0.9)
                """.trimIndent(),
                parameters = 3,
            ) {
                bindString(0, id)
                bindLong(1, captured)
                bindLong(2, captured)
            }
        }

        // 2. Run migration to v1
        BirdyData.Schema.migrate(driver, oldVersion = 0, newVersion = 1)

        // 3. Assert stamp_number is sequential by captured_at_ms ASC
        val cursor = driver.executeQuery(
            identifier = null,
            sql = "SELECT id, stamp_number FROM observation ORDER BY stamp_number",
            mapper = { c ->
                val results = mutableListOf<Pair<String, Long>>()
                while (c.next().value) {
                    results.add(c.getString(0)!! to c.getLong(1)!!)
                }
                app.cash.sqldelight.db.QueryResult.Value(results.toList())
            },
            parameters = 0,
        ).value

        assertEquals(
            listOf(
                "obs-a" to 1L,
                "obs-b" to 2L,
                "obs-c" to 3L,
                "obs-d" to 4L,
                "obs-e" to 5L,
            ),
            cursor,
        )
        driver.close()
    }

    private companion object {
        // Snapshot of Observation.sq BEFORE Plan 7a — must match what was deployed
        // in v0.5.0a-diary. Hardcoded to detect accidental schema drift.
        private const val V0_SCHEMA = """
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
        """
    }
}
```

- [ ] **Step 7: Uppdatera SqlDelightObservationRepositoryTest för stampNumber**

Hitta existerande tester i `shared/data/src/jvmTest/kotlin/se/birdy/data/observation/SqlDelightObservationRepositoryTest.kt` och lägg till:

```kotlin
@Test
fun `insert assigns sequential stamp_number starting at 1`() = runTest {
    val repo = createRepo()
    repo.insert(testObservation(id = "a"))
    repo.insert(testObservation(id = "b"))
    repo.insert(testObservation(id = "c"))
    val all = repo.observeAll().first()
    assertEquals(listOf(3, 2, 1), all.map { it.stampNumber }) // selectAll is DESC by captured_at
}

@Test
fun `delete then insert reuses stamp_number sequence (no gap fill)`() = runTest {
    val repo = createRepo()
    repo.insert(testObservation(id = "a"))   // #1
    repo.insert(testObservation(id = "b"))   // #2
    repo.delete("a")
    repo.insert(testObservation(id = "c"))   // #3 (not #2)
    val byStamp = repo.observeAllByStampNumber().first()
    assertEquals(listOf(3, 2), byStamp.map { it.stampNumber })
}
```

(Justera `testObservation`-helper så den inte längre tar `stampNumber`-arg — det beräknas i SQL.)

- [ ] **Step 8: Run migration-test**

Run: `./gradlew :shared:data:jvmTest --tests "*StampNumberMigrationTest*"`
Expected: PASS.

- [ ] **Step 9: Run hela `:shared:data:jvmTest`**

Run: `./gradlew :shared:data:jvmTest`
Expected: PASS — alla obs-tester inkl. nya stamp_number-tester gröna.

- [ ] **Step 10: Bygga + verifiera schema-fil committas**

Run: `./gradlew :shared:data:build`
Expected: BUILD SUCCESSFUL. Verifiera att `shared/data/src/commonMain/sqldelight/databases/1.db` skapats (filnamn varierar).

```bash
ls shared/data/src/commonMain/sqldelight/databases/
# expected: 1.db
```

- [ ] **Step 11: ktlint + detekt**

Run: `./gradlew :shared:data:ktlintCheck :shared:data:detekt :shared:domain:ktlintCheck :shared:domain:detekt`
Expected: SUCCESS.

- [ ] **Step 12: Commit**

```bash
git add shared/data/src/commonMain/sqldelight/se/birdy/data/db/Observation.sq \
        shared/data/src/commonMain/sqldelight/migrations/1.sqm \
        shared/data/src/commonMain/sqldelight/databases/ \
        shared/data/build.gradle.kts \
        shared/domain/src/commonMain/kotlin/se/birdy/domain/observation/Observation.kt \
        shared/data/src/commonMain/kotlin/se/birdy/data/observation/SqlDelightObservationRepository.kt \
        shared/data/src/jvmTest/kotlin/se/birdy/data/observation/StampNumberMigrationTest.kt \
        shared/data/src/jvmTest/kotlin/se/birdy/data/observation/SqlDelightObservationRepositoryTest.kt
git commit -m "feat(data): Plan 7a Task 4 — SQLDelight migration adds stamp_number with chronological backfill"
```

---

## Task 5: Skapa `:shared:datastore`-modul

**Files:**
- Create: `shared/datastore/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Lägg till lib-versioner**

Öppna `gradle/libs.versions.toml`. Under `[versions]`:

```toml
androidx-datastore = "1.1.1"
```

Under `[libraries]`:

```toml
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "androidx-datastore" }
```

- [ ] **Step 2: Inkludera modul i settings.gradle.kts**

Öppna `settings.gradle.kts` och lägg till `:shared:datastore` i `include`-listan:

```kotlin
include(
    ":composeApp",
    ":androidApp",
    ":shared:domain",
    ":shared:data",
    ":shared:datastore",
    ":shared:ml",
    ":shared:content",
)
```

- [ ] **Step 3: Skapa `shared/datastore/build.gradle.kts`**

```kotlin
plugins {
    id("birdy.kmp-android-lib")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.datastore.preferences)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.junit.jupiter)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}

android {
    namespace = "se.birdy.datastore"
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
```

- [ ] **Step 4: Verifiera Gradle-sync**

Run: `./gradlew :shared:datastore:tasks`
Expected: SUCCESS — modul registrerad.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml shared/datastore/build.gradle.kts
git commit -m "build(datastore): Plan 7a Task 5 — add :shared:datastore module + datastore-preferences dep"
```

---

## Task 6: UserPreferences interface + impl

**Files:**
- Create: `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/UserPreferences.kt`
- Create: `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/InMemoryUserPreferences.kt`
- Create: `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/UserPreferencesStore.kt`
- Create: `shared/datastore/src/androidMain/kotlin/se/birdy/datastore/UserPreferencesStore.android.kt`
- Create: `shared/datastore/src/iosMain/kotlin/se/birdy/datastore/UserPreferencesStore.ios.kt`
- Create: `shared/datastore/src/jvmMain/kotlin/se/birdy/datastore/UserPreferencesStore.jvm.kt`
- Create: `shared/datastore/src/jvmTest/kotlin/se/birdy/datastore/InMemoryUserPreferencesTest.kt`

> **Varför `InMemoryUserPreferences` i commonMain:** den används av VM-tester i `composeApp/src/commonTest` (Onboarding + Settings). KMP commonTest kan bara se commonMain-symboler från sina dependencies — om impl ligger i jvmMain kompilerar inte test-importerna. Klassen är ren `MutableStateFlow`-wrapper utan platform-deps.

- [ ] **Step 1: Skapa enums för värdekategorier**

Skapa `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/UserPreferences.kt`:

```kotlin
package se.birdy.datastore

import kotlinx.coroutines.flow.Flow

enum class AppLanguage { SV, EN, SYSTEM }

enum class LifelistStat3Choice {
    STREAK,
    SPECIES_THIS_YEAR,
    SPECIES_THIS_MONTH,
    LONGEST_STREAK,
}

enum class ArchiveSort { ALPHA, FAMILY, RECENT }

enum class LifelistSort { RECENT, STAMP_NUMBER, SPECIES }

interface UserPreferences {
    val userName: Flow<String>
    val hasSeenOnboarding: Flow<Boolean>
    val appLanguage: Flow<AppLanguage>
    val lifelistStat3: Flow<LifelistStat3Choice>
    val archiveSort: Flow<ArchiveSort>
    val lifelistSort: Flow<LifelistSort>

    suspend fun setUserName(name: String)
    suspend fun setHasSeenOnboarding(value: Boolean)
    suspend fun setAppLanguage(value: AppLanguage)
    suspend fun setLifelistStat3(value: LifelistStat3Choice)
    suspend fun setArchiveSort(value: ArchiveSort)
    suspend fun setLifelistSort(value: LifelistSort)
}
```

- [ ] **Step 2: Skapa `expect class UserPreferencesStore`**

Skapa `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/UserPreferencesStore.kt`:

```kotlin
package se.birdy.datastore

/**
 * Platform factory. Constructor-arg är opaque (`Any?`) eftersom Android behöver
 * Context men iOS/JVM inte gör det. Mönstret matchar `DatabaseFactory` i :shared:data.
 *
 * Android: pass `applicationContext`.
 * iOS: pass null (kastar NotImplementedError i v1).
 * JVM (test): pass null (in-memory impl).
 */
expect class UserPreferencesStore(platformContext: Any?) {
    fun preferences(): UserPreferences
}
```

- [ ] **Step 3: Android `actual` med datastore-preferences**

Skapa `shared/datastore/src/androidMain/kotlin/se/birdy/datastore/UserPreferencesStore.android.kt`:

```kotlin
package se.birdy.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "birdy_user_prefs")

actual class UserPreferencesStore actual constructor(platformContext: Any?) {
    private val context: Context = (platformContext as? Context)
        ?: error("Android UserPreferencesStore requires Context, got: $platformContext")

    actual fun preferences(): UserPreferences = AndroidUserPreferences(context.userPrefsDataStore)
}

private class AndroidUserPreferences(private val store: DataStore<Preferences>) : UserPreferences {
    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val LIFELIST_STAT3 = stringPreferencesKey("lifelist_stat3_choice")
        val ARCHIVE_SORT = stringPreferencesKey("archive_sort")
        val LIFELIST_SORT = stringPreferencesKey("lifelist_sort")
    }

    override val userName: Flow<String> = store.data.map { it[Keys.USER_NAME] ?: "" }
    override val hasSeenOnboarding: Flow<Boolean> = store.data.map { it[Keys.HAS_SEEN_ONBOARDING] ?: false }

    override val appLanguage: Flow<AppLanguage> =
        store.data.map { prefs ->
            AppLanguage.entries.firstOrNull { it.name == prefs[Keys.APP_LANGUAGE] } ?: AppLanguage.SYSTEM
        }
    override val lifelistStat3: Flow<LifelistStat3Choice> =
        store.data.map { prefs ->
            LifelistStat3Choice.entries.firstOrNull { it.name == prefs[Keys.LIFELIST_STAT3] }
                ?: LifelistStat3Choice.STREAK
        }
    override val archiveSort: Flow<ArchiveSort> =
        store.data.map { prefs ->
            ArchiveSort.entries.firstOrNull { it.name == prefs[Keys.ARCHIVE_SORT] } ?: ArchiveSort.ALPHA
        }
    override val lifelistSort: Flow<LifelistSort> =
        store.data.map { prefs ->
            LifelistSort.entries.firstOrNull { it.name == prefs[Keys.LIFELIST_SORT] } ?: LifelistSort.RECENT
        }

    override suspend fun setUserName(name: String) {
        store.edit { it[Keys.USER_NAME] = name }
    }

    override suspend fun setHasSeenOnboarding(value: Boolean) {
        store.edit { it[Keys.HAS_SEEN_ONBOARDING] = value }
    }

    override suspend fun setAppLanguage(value: AppLanguage) {
        store.edit { it[Keys.APP_LANGUAGE] = value.name }
    }

    override suspend fun setLifelistStat3(value: LifelistStat3Choice) {
        store.edit { it[Keys.LIFELIST_STAT3] = value.name }
    }

    override suspend fun setArchiveSort(value: ArchiveSort) {
        store.edit { it[Keys.ARCHIVE_SORT] = value.name }
    }

    override suspend fun setLifelistSort(value: LifelistSort) {
        store.edit { it[Keys.LIFELIST_SORT] = value.name }
    }
}
```

- [ ] **Step 4: iOS `actual`-stub**

Skapa `shared/datastore/src/iosMain/kotlin/se/birdy/datastore/UserPreferencesStore.ios.kt`:

```kotlin
package se.birdy.datastore

actual class UserPreferencesStore actual constructor(platformContext: Any?) {
    actual fun preferences(): UserPreferences =
        throw NotImplementedError(
            "iOS UserPreferencesStore not implemented in v1 — see Plan 7a deviation #2 + spec §13.",
        )
}
```

- [ ] **Step 5: Skapa `InMemoryUserPreferences` i commonMain**

Skapa `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/InMemoryUserPreferences.kt`:

```kotlin
package se.birdy.datastore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory impl för tester. Ligger i commonMain så den är synlig från
 * composeApp:commonTest (KMP-regel: commonTest ser bara commonMain-symboler
 * från dependencies, inte jvmMain).
 */
class InMemoryUserPreferences : UserPreferences {
    private val _userName = MutableStateFlow("")
    private val _hasSeenOnboarding = MutableStateFlow(false)
    private val _appLanguage = MutableStateFlow(AppLanguage.SYSTEM)
    private val _lifelistStat3 = MutableStateFlow(LifelistStat3Choice.STREAK)
    private val _archiveSort = MutableStateFlow(ArchiveSort.ALPHA)
    private val _lifelistSort = MutableStateFlow(LifelistSort.RECENT)

    override val userName: Flow<String> = _userName.asStateFlow()
    override val hasSeenOnboarding: Flow<Boolean> = _hasSeenOnboarding.asStateFlow()
    override val appLanguage: Flow<AppLanguage> = _appLanguage.asStateFlow()
    override val lifelistStat3: Flow<LifelistStat3Choice> = _lifelistStat3.asStateFlow()
    override val archiveSort: Flow<ArchiveSort> = _archiveSort.asStateFlow()
    override val lifelistSort: Flow<LifelistSort> = _lifelistSort.asStateFlow()

    override suspend fun setUserName(name: String) { _userName.value = name }
    override suspend fun setHasSeenOnboarding(value: Boolean) { _hasSeenOnboarding.value = value }
    override suspend fun setAppLanguage(value: AppLanguage) { _appLanguage.value = value }
    override suspend fun setLifelistStat3(value: LifelistStat3Choice) { _lifelistStat3.value = value }
    override suspend fun setArchiveSort(value: ArchiveSort) { _archiveSort.value = value }
    override suspend fun setLifelistSort(value: LifelistSort) { _lifelistSort.value = value }
}
```

- [ ] **Step 6: JVM `actual` returnerar InMemory-impl**

Skapa `shared/datastore/src/jvmMain/kotlin/se/birdy/datastore/UserPreferencesStore.jvm.kt`:

```kotlin
package se.birdy.datastore

actual class UserPreferencesStore actual constructor(platformContext: Any?) {
    actual fun preferences(): UserPreferences = InMemoryUserPreferences()
}
```

- [ ] **Step 7: Skriv test för in-memory impl**

Skapa `shared/datastore/src/jvmTest/kotlin/se/birdy/datastore/InMemoryUserPreferencesTest.kt`:

```kotlin
package se.birdy.datastore

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryUserPreferencesTest {
    @Test
    fun `userName starts empty and updates`() = runTest {
        val prefs = InMemoryUserPreferences()
        prefs.userName.test {
            assertEquals("", awaitItem())
            prefs.setUserName("Albin")
            assertEquals("Albin", awaitItem())
        }
    }

    @Test
    fun `hasSeenOnboarding starts false`() = runTest {
        val prefs = InMemoryUserPreferences()
        prefs.hasSeenOnboarding.test {
            assertEquals(false, awaitItem())
            prefs.setHasSeenOnboarding(true)
            assertEquals(true, awaitItem())
        }
    }

    @Test
    fun `appLanguage default is SYSTEM`() = runTest {
        val prefs = InMemoryUserPreferences()
        prefs.appLanguage.test {
            assertEquals(AppLanguage.SYSTEM, awaitItem())
            prefs.setAppLanguage(AppLanguage.SV)
            assertEquals(AppLanguage.SV, awaitItem())
        }
    }

    @Test
    fun `all enum-backed prefs round-trip`() = runTest {
        val prefs = InMemoryUserPreferences()
        prefs.setLifelistStat3(LifelistStat3Choice.LONGEST_STREAK)
        prefs.setArchiveSort(ArchiveSort.RECENT)
        prefs.setLifelistSort(LifelistSort.STAMP_NUMBER)

        assertEquals(LifelistStat3Choice.LONGEST_STREAK, prefs.lifelistStat3.test { awaitItem() })
        assertEquals(ArchiveSort.RECENT, prefs.archiveSort.test { awaitItem() })
        assertEquals(LifelistSort.STAMP_NUMBER, prefs.lifelistSort.test { awaitItem() })
    }
}
```

- [ ] **Step 8: Run tester**

Run: `./gradlew :shared:datastore:jvmTest`
Expected: PASS — 4 tests.

- [ ] **Step 9: Bygg modul**

Run: `./gradlew :shared:datastore:build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: ktlint + detekt**

Run: `./gradlew :shared:datastore:ktlintCheck :shared:datastore:detekt`
Expected: SUCCESS.

- [ ] **Step 11: Commit**

```bash
git add shared/datastore/
git commit -m "feat(datastore): Plan 7a Task 6 — UserPreferences interface + Android/JVM/iOS impls + 4 tests"
```

---

## Task 7: Wire :shared:datastore i AppGraph

**Files:**
- Modify: `composeApp/build.gradle.kts`
- Modify: `androidApp/build.gradle.kts`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt`

- [ ] **Step 1: Lägg dep i `composeApp/build.gradle.kts`**

Hitta `commonMain.dependencies { ... }` och lägg till:

```kotlin
implementation(project(":shared:datastore"))
```

- [ ] **Step 2: Lägg dep i `androidApp/build.gradle.kts`**

Hitta `dependencies { ... }` och lägg till:

```kotlin
implementation(project(":shared:datastore"))
```

> CLAUDE.md noterar: composeApp använder `implementation()` inte `api()`, så transitiva deps måste explicit läggas i androidApp också.

- [ ] **Step 3: Lägg `userPreferences`-fält i AppGraph**

Öppna `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`. Lägg till constructor-param efter `badgeVersionStore`:

```kotlin
val userPreferences: UserPreferences,
```

Och import:

```kotlin
import se.birdy.datastore.UserPreferences
```

- [ ] **Step 4: Skapa store + pass UserPreferences i MainActivity**

Öppna `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt`. Lägg till efter `val badgeVersionStore = ...`:

```kotlin
val userPreferences = UserPreferencesStore(applicationContext).preferences()
```

Och pass till AppGraph:

```kotlin
val graph = AppGraph(
    // ... existing args ...
    badgeVersionStore = badgeVersionStore,
    userPreferences = userPreferences,
    defaultLocale = Locale.SV,
    // ...
)
```

Imports:

```kotlin
import se.birdy.datastore.UserPreferencesStore
```

- [ ] **Step 5: Verifiera kompilering**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add composeApp/build.gradle.kts \
        androidApp/build.gradle.kts \
        composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt \
        androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt
git commit -m "feat(di): Plan 7a Task 7 — wire UserPreferences into AppGraph + MainActivity"
```

---

## Task 8: Onboarding ViewModel + UiState

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/OnboardingUiState.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/OnboardingViewModel.kt`
- Create: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/onboarding/OnboardingViewModelTest.kt`

- [ ] **Step 1: Skapa `OnboardingUiState.kt`**

```kotlin
package se.birdy.app.ui.onboarding

sealed interface OnboardingUiState {
    data object Loading : OnboardingUiState
    data class Visible(val pageIndex: Int, val nameInput: String) : OnboardingUiState
    data object Done : OnboardingUiState
}
```

- [ ] **Step 2: Skriv failing test**

Skapa `composeApp/src/commonTest/kotlin/se/birdy/app/ui/onboarding/OnboardingViewModelTest.kt`:

```kotlin
package se.birdy.app.ui.onboarding

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import se.birdy.datastore.InMemoryUserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnboardingViewModelTest {
    @Test
    fun `initial state is page 0 with empty name`() = runTest {
        val prefs = InMemoryUserPreferences()
        val vm = OnboardingViewModel(prefs, defaultFallbackName = "Min")
        vm.state.test {
            val first = awaitItem()
            assertTrue(first is OnboardingUiState.Visible)
            assertEquals(0, first.pageIndex)
            assertEquals("", first.nameInput)
        }
    }

    @Test
    fun `setPageIndex moves between pages`() = runTest {
        val vm = OnboardingViewModel(InMemoryUserPreferences(), "Min")
        vm.setPageIndex(1)
        vm.setPageIndex(2)
        vm.state.test {
            val s = awaitItem()
            assertTrue(s is OnboardingUiState.Visible)
            assertEquals(2, s.pageIndex)
        }
    }

    @Test
    fun `complete with empty name uses fallback`() = runTest {
        val prefs = InMemoryUserPreferences()
        val vm = OnboardingViewModel(prefs, defaultFallbackName = "Min")
        vm.onNameChange("")
        vm.complete()
        prefs.userName.test { assertEquals("Min", awaitItem()) }
        prefs.hasSeenOnboarding.test { assertEquals(true, awaitItem()) }
    }

    @Test
    fun `complete with non-empty name stores it`() = runTest {
        val prefs = InMemoryUserPreferences()
        val vm = OnboardingViewModel(prefs, defaultFallbackName = "Min")
        vm.onNameChange("Albin")
        vm.complete()
        prefs.userName.test { assertEquals("Albin", awaitItem()) }
    }

    @Test
    fun `complete trims leading and trailing whitespace`() = runTest {
        val prefs = InMemoryUserPreferences()
        val vm = OnboardingViewModel(prefs, defaultFallbackName = "Min")
        vm.onNameChange("  Albin  ")
        vm.complete()
        prefs.userName.test { assertEquals("Albin", awaitItem()) }
    }
}
```

- [ ] **Step 3: Run test — verify FAIL**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*OnboardingViewModelTest*"`
Expected: FAIL — `OnboardingViewModel` not defined.

- [ ] **Step 4: Skapa `OnboardingViewModel.kt`**

```kotlin
package se.birdy.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import se.birdy.datastore.UserPreferences

class OnboardingViewModel(
    private val prefs: UserPreferences,
    private val defaultFallbackName: String,
) : ViewModel() {
    private val _state = MutableStateFlow<OnboardingUiState>(
        OnboardingUiState.Visible(pageIndex = 0, nameInput = ""),
    )
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun setPageIndex(index: Int) {
        val current = _state.value
        if (current is OnboardingUiState.Visible) {
            _state.value = current.copy(pageIndex = index.coerceIn(0, MAX_PAGE_INDEX))
        }
    }

    fun onNameChange(value: String) {
        val current = _state.value
        if (current is OnboardingUiState.Visible) {
            _state.value = current.copy(nameInput = value)
        }
    }

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

    private companion object {
        const val MAX_PAGE_INDEX = 2 // 3 pages: 0, 1, 2
    }
}
```

- [ ] **Step 5: Run test — verify PASS**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*OnboardingViewModelTest*"`
Expected: PASS — 5 tests.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/ \
        composeApp/src/commonTest/kotlin/se/birdy/app/ui/onboarding/
git commit -m "feat(onboarding): Plan 7a Task 8 — OnboardingViewModel + 5 tests"
```

---

## Task 9: OnboardingScreen — 3 sidor

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/OnboardingScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`

OnboardingScreen visar 3 sidor med `HorizontalPager`. Visuellt mappar mot `.superpowers/brainstorm/162773-1778192055/content/onboarding-v11.html` (mockup). Fonten är `Crimson Pro` som redan satts upp i Plan 1 Task 4.

> **Mockup-referens:** `.superpowers/` är gitignore:ad — om mockuparna behövs igen, läs spec §4 + skärmdumpar i `docs/superpowers/screenshots/v0.7.0a-foundation/` (skapas i Task 12).

- [ ] **Step 1: Lägg onboarding-strängar (sv)**

Öppna `composeApp/src/commonMain/composeResources/values/strings.xml`. Lägg till före `</resources>`:

```xml
<!-- ===== Plan 7a: Onboarding ===== -->
<string name="onboarding_skip">Hoppa över</string>

<!-- Page 1 (Brand) -->
<string name="onboarding_p1_breadcrumb">VÄLKOMMEN</string>
<string name="onboarding_p1_headline">Birdy.</string>
<string name="onboarding_p1_body">Lyssna, känn igen, *samla*.</string>
<string name="onboarding_p1_sub">En fält-följeslagare bland fåglar — en stämpel i taget.</string>
<string name="onboarding_p1_cta">Visa hur</string>

<!-- Page 2 (Overview) -->
<string name="onboarding_p2_breadcrumb">ÖVERBLICK</string>
<string name="onboarding_p2_headline">Fyra ställen att *vara* på.</string>
<string name="onboarding_p2_listen_name">Listen</string>
<string name="onboarding_p2_listen_desc">*Lyssna och fånga* — kamera, ljud eller foto.</string>
<string name="onboarding_p2_archive_name">Archive</string>
<string name="onboarding_p2_archive_desc">*Bläddra och utforska* tusentals fåglar.</string>
<string name="onboarding_p2_lifelist_name">Lifelist</string>
<string name="onboarding_p2_lifelist_desc">*Samla* stämplar — din lista växer för varje fynd.</string>
<string name="onboarding_p2_badges_name">Badges</string>
<string name="onboarding_p2_badges_desc">*Förtjäna märken*, håll sviter levande.</string>
<string name="onboarding_p2_cta">Nästan klar</string>

<!-- Page 3 (Name) -->
<string name="onboarding_p3_breadcrumb">SISTA STEGET</string>
<string name="onboarding_p3_headline">Vad ska vi kalla din *samling*?</string>
<string name="onboarding_p3_input_placeholder">Albin</string>
<string name="onboarding_p3_input_helper">Du kan ändra detta senare i inställningarna.</string>
<string name="onboarding_p3_premium_label">PREMIUM</string>
<string name="onboarding_p3_cta">Börja samla →</string>
<string name="onboarding_p3_fallback_name">Min</string>
```

- [ ] **Step 2: Lägg onboarding-strängar (en)**

Öppna `composeApp/src/commonMain/composeResources/values-en/strings.xml` (skapa om saknas — kolla först med `ls composeApp/src/commonMain/composeResources/values-en/`). Spegla samma keys på engelska:

```xml
<string name="onboarding_skip">Skip</string>
<string name="onboarding_p1_breadcrumb">WELCOME</string>
<string name="onboarding_p1_headline">Birdy.</string>
<string name="onboarding_p1_body">Listen, recognize, *collect*.</string>
<string name="onboarding_p1_sub">A field companion among birds — one stamp at a time.</string>
<string name="onboarding_p1_cta">Show me how</string>
<string name="onboarding_p2_breadcrumb">OVERVIEW</string>
<string name="onboarding_p2_headline">Four places to *be*.</string>
<string name="onboarding_p2_listen_name">Listen</string>
<string name="onboarding_p2_listen_desc">*Listen and capture* — camera, audio or photo.</string>
<string name="onboarding_p2_archive_name">Archive</string>
<string name="onboarding_p2_archive_desc">*Browse and explore* thousands of birds.</string>
<string name="onboarding_p2_lifelist_name">Lifelist</string>
<string name="onboarding_p2_lifelist_desc">*Collect* stamps — your list grows with every find.</string>
<string name="onboarding_p2_badges_name">Badges</string>
<string name="onboarding_p2_badges_desc">*Earn discoveries*, keep your streaks alive.</string>
<string name="onboarding_p2_cta">Almost there</string>
<string name="onboarding_p3_breadcrumb">LAST STEP</string>
<string name="onboarding_p3_headline">What should we call your *collection*?</string>
<string name="onboarding_p3_input_placeholder">Alex</string>
<string name="onboarding_p3_input_helper">You can change this later in settings.</string>
<string name="onboarding_p3_premium_label">PREMIUM</string>
<string name="onboarding_p3_cta">Start collecting →</string>
<string name="onboarding_p3_fallback_name">My</string>
```

- [ ] **Step 3: Skapa `OnboardingScreen.kt`**

```kotlin
package se.birdy.app.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p1_body
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p1_breadcrumb
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p1_cta
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p1_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p1_sub
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_archive_desc
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_archive_name
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_badges_desc
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_badges_name
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_breadcrumb
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_cta
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_lifelist_desc
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_lifelist_name
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_listen_desc
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_listen_name
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p3_breadcrumb
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p3_cta
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p3_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p3_input_helper
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p3_input_placeholder
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p3_premium_label
import birdy_bird_scanner.composeapp.generated.resources.onboarding_skip
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.HeroMossDeep
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.HeroMossMid
import se.birdy.app.ui.theme.ItalicMixedText
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.SandCreme

private const val PAGE_COUNT = 3

@Composable
fun OnboardingScreen(
    state: OnboardingUiState.Visible,
    onPageChange: (Int) -> Unit,
    onNameChange: (String) -> Unit,
    onComplete: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = state.pageIndex, pageCount = { PAGE_COUNT })

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onPageChange(it) }
    }
    LaunchedEffect(state.pageIndex) {
        if (pagerState.currentPage != state.pageIndex) {
            pagerState.animateScrollToPage(state.pageIndex)
        }
    }

    val gradient = Brush.verticalGradient(listOf(HeroMossLight, HeroMossMid, HeroMossDeep))

    Box(modifier = Modifier.fillMaxSize().background(gradient)) {
        // Skip-link top-right (alla sidor utom sista — eller även där, hoppa = complete med tomt namn)
        TextButton(
            onClick = {
                if (state.pageIndex == PAGE_COUNT - 1) {
                    onComplete()
                } else {
                    onPageChange(PAGE_COUNT - 1)
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp),
        ) {
            Text(
                text = stringResource(Res.string.onboarding_skip),
                color = OffwhiteWarm.copy(alpha = 0.65f),
                fontSize = 13.sp,
            )
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> Page1Brand()
                1 -> Page2Overview()
                2 -> Page3Name(
                    nameInput = state.nameInput,
                    onNameChange = onNameChange,
                    onComplete = onComplete,
                )
            }
        }

        // Pager dots
        PagerDots(
            currentPage = pagerState.currentPage,
            pageCount = PAGE_COUNT,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (state.pageIndex == PAGE_COUNT - 1) 148.dp else 28.dp),
        )
    }
}

@Composable
private fun Page1Brand() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = stringResource(Res.string.onboarding_p1_breadcrumb),
            color = AccentCopperLight,
            fontSize = 12.sp,
            letterSpacing = 4.8.sp,
            fontWeight = FontWeight.W600,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.onboarding_p1_headline),
            color = AccentCopperLight,
            fontStyle = FontStyle.Italic,
            fontSize = 68.sp,
            fontWeight = FontWeight.W700,
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(Modifier.height(28.dp))
        ItalicMixedText(
            text = stringResource(Res.string.onboarding_p1_body),
            style = MaterialTheme.typography.headlineSmall.copy(
                color = OffwhiteWarm,
                fontSize = 20.sp,
                fontWeight = FontWeight.W600,
            ),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.onboarding_p1_sub),
            color = OffwhiteWarm.copy(alpha = 0.75f),
            fontSize = 16.sp,
            fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
private fun Page2Overview() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.onboarding_p2_breadcrumb),
            color = AccentCopperLight,
            fontSize = 12.sp,
            letterSpacing = 4.8.sp,
            fontWeight = FontWeight.W600,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(16.dp))
        ItalicMixedText(
            text = stringResource(Res.string.onboarding_p2_headline),
            style = MaterialTheme.typography.displaySmall.copy(
                color = OffwhiteWarm,
                fontSize = 56.sp,
                fontWeight = FontWeight.W700,
            ),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        FeatureRow(
            name = stringResource(Res.string.onboarding_p2_listen_name),
            description = stringResource(Res.string.onboarding_p2_listen_desc),
        )
        FeatureRow(
            name = stringResource(Res.string.onboarding_p2_archive_name),
            description = stringResource(Res.string.onboarding_p2_archive_desc),
        )
        FeatureRow(
            name = stringResource(Res.string.onboarding_p2_lifelist_name),
            description = stringResource(Res.string.onboarding_p2_lifelist_desc),
        )
        FeatureRow(
            name = stringResource(Res.string.onboarding_p2_badges_name),
            description = stringResource(Res.string.onboarding_p2_badges_desc),
        )
    }
}

@Composable
private fun FeatureRow(name: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AccentCopper.copy(alpha = 0.18f)),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = name,
                color = OffwhiteWarm,
                fontWeight = FontWeight.W700,
                fontSize = 19.sp,
            )
            Spacer(Modifier.height(2.dp))
            ItalicMixedText(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = OffwhiteWarm.copy(alpha = 0.78f),
                    fontSize = 14.sp,
                ),
            )
        }
    }
}

@Composable
private fun Page3Name(
    nameInput: String,
    onNameChange: (String) -> Unit,
    onComplete: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Res.string.onboarding_p3_breadcrumb),
            color = AccentCopperLight,
            fontSize = 12.sp,
            letterSpacing = 4.8.sp,
            fontWeight = FontWeight.W600,
        )
        Spacer(Modifier.height(16.dp))
        ItalicMixedText(
            text = stringResource(Res.string.onboarding_p3_headline),
            style = MaterialTheme.typography.displaySmall.copy(
                color = OffwhiteWarm,
                fontSize = 36.sp,
                fontWeight = FontWeight.W700,
            ),
        )
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = nameInput,
            onValueChange = onNameChange,
            placeholder = { Text(stringResource(Res.string.onboarding_p3_input_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SandCreme,
                unfocusedContainerColor = SandCreme,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.onboarding_p3_input_helper),
            color = OffwhiteWarm.copy(alpha = 0.6f),
            fontSize = 13.sp,
            fontStyle = FontStyle.Italic,
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentCopper,
                contentColor = OffwhiteWarm,
            ),
        ) {
            Text(
                text = stringResource(Res.string.onboarding_p3_cta),
                fontWeight = FontWeight.W600,
                fontSize = 17.sp,
            )
        }
    }
}

@Composable
private fun PagerDots(currentPage: Int, pageCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(pageCount) { i ->
            val width by animateDpAsState(if (i == currentPage) 22.dp else 8.dp, label = "dot-width")
            Box(
                modifier = Modifier
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (i == currentPage) AccentCopperLight else OffwhiteWarm.copy(alpha = 0.3f)),
            )
        }
    }
}
```

> **Note:** Layout-detaljer (text-shadow, feature-panel-bakgrund, premium-marker) stryks i denna första iteration för att hålla task hanterbar — kommer som polish i Plan 7b/Plan 6.

- [ ] **Step 4: Verifiera build**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: ktlint + detekt**

Run: `./gradlew :composeApp:ktlintCheck :composeApp:detekt`
Expected: SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/OnboardingScreen.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(onboarding): Plan 7a Task 9 — OnboardingScreen (3 pages, pager, sv+en strings)"
```

---

## Task 10: Bottom-bar — byta tab-namn + ikoner

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BottomNavBar.kt`

### Tab-namn — engelska överallt

Spec §3 säger "Alla tab-namn på engelska oavsett app-språk — editorial-feel, korta." Så även svenska språkfilen får engelska tab-namn.

- [ ] **Step 1: Uppdatera tab-strängar (sv)**

Öppna `composeApp/src/commonMain/composeResources/values/strings.xml` och ändra:

```xml
<!-- Bottom-nav -->
<string name="tab_listen">Listen</string>
<string name="tab_archive">Archive</string>
<string name="tab_lifelist">Lifelist</string>
<string name="tab_badges">Badges</string>
```

(Ta bort gamla `tab_scan`, `tab_encyclopedia`, `tab_diary`, `tab_badges`-rader. Lägg `tab_badges` med samma name som tidigare — ändra bara värdet.)

- [ ] **Step 2: Uppdatera tab-strängar (en)**

Identiskt i `values-en/strings.xml`.

- [ ] **Step 3: Uppdatera `BottomNavBar.kt`**

Öppna `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BottomNavBar.kt`. Ersätt `tabs`-listan + uppdatera imports:

```kotlin
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.outlined.CollectionsBookmark
import birdy_bird_scanner.composeapp.generated.resources.tab_archive
import birdy_bird_scanner.composeapp.generated.resources.tab_badges
import birdy_bird_scanner.composeapp.generated.resources.tab_lifelist
import birdy_bird_scanner.composeapp.generated.resources.tab_listen

private val tabs =
    listOf(
        TabSpec(AppRoute.Scan, Res.string.tab_listen, Icons.Filled.Hearing),
        TabSpec(AppRoute.Encyclopedia, Res.string.tab_archive, Icons.AutoMirrored.Filled.LibraryBooks),
        TabSpec(AppRoute.Diary, Res.string.tab_lifelist, Icons.Outlined.CollectionsBookmark),
        TabSpec(AppRoute.Badges, Res.string.tab_badges, Icons.Filled.Stars),
    )
```

> **Routes oförändrade** — `AppRoute.Scan` mappar nu visuellt till "Listen" men öppnar fortfarande `ScanScreenHost`. Renaming av routes sker i Plan 7b när skärmarna byts.

- [ ] **Step 4: Ta bort gamla tab-strängar i `strings.xml`**

Sök efter `tab_scan`, `tab_encyclopedia`, `tab_diary` i hela projektet:

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`

Hantera ev. compile-errors där gamla `tab_scan`-resourcer importeras. Förmodligen är `BottomNavBar.kt` enda call-site (Plan 1 Task 4 wiring). Om Settings/Compose preview-filer importerar dem — uppdatera.

- [ ] **Step 5: Verifiera build + lint**

Run: `./gradlew :composeApp:assembleDebug :composeApp:ktlintCheck :composeApp:detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/composeResources/values-en/strings.xml \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BottomNavBar.kt
git commit -m "feat(scaffold): Plan 7a Task 10 — bottom-bar tabs renamed to Listen/Archive/Lifelist/Badges"
```

---

## Task 11: Settings-skärm

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsUiState.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsViewModel.kt`
- Create: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/settings/SettingsViewModelTest.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`

### Settings-route från var?

Spec §10 säger "gear-ikon i top-right på Lifelist-skärmen". Men Lifelist redesign:as inte i 7a. Lösning: Settings-route registreras i AppScaffold och navigeras till från Encyclopedia-debug-meny i 7a (samma overflow-menu som debug-benchmark — `EncyclopediaScreen` har redan en debug-menu via `showDebugMenu`-flag). Detta håller 7a självständigt — när Lifelist redesign:as i 7b flyttas entry-pointen.

- [ ] **Step 1: Lägg settings-strängar**

`values/strings.xml`:

```xml
<!-- ===== Plan 7a: Settings ===== -->
<string name="settings_title">Inställningar</string>
<string name="settings_label_name">Namn</string>
<string name="settings_label_language">Språk</string>
<string name="settings_label_about">Om appen</string>
<string name="settings_language_sv">Svenska</string>
<string name="settings_language_en">English</string>
<string name="settings_language_system">System</string>
<string name="settings_name_dialog_title">Ändra namn</string>
<string name="settings_name_dialog_save">Spara</string>
<string name="settings_name_dialog_cancel">Avbryt</string>
<string name="settings_back">Tillbaka</string>
```

`values-en/strings.xml`:

```xml
<string name="settings_title">Settings</string>
<string name="settings_label_name">Name</string>
<string name="settings_label_language">Language</string>
<string name="settings_label_about">About</string>
<string name="settings_language_sv">Svenska</string>
<string name="settings_language_en">English</string>
<string name="settings_language_system">System</string>
<string name="settings_name_dialog_title">Change name</string>
<string name="settings_name_dialog_save">Save</string>
<string name="settings_name_dialog_cancel">Cancel</string>
<string name="settings_back">Back</string>
```

- [ ] **Step 2: Skapa `SettingsUiState.kt`**

```kotlin
package se.birdy.app.ui.settings

import se.birdy.datastore.AppLanguage

data class SettingsUiState(
    val userName: String,
    val language: AppLanguage,
)
```

- [ ] **Step 3: Skriv failing test**

`composeApp/src/commonTest/kotlin/se/birdy/app/ui/settings/SettingsViewModelTest.kt`:

```kotlin
package se.birdy.app.ui.settings

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import se.birdy.datastore.AppLanguage
import se.birdy.datastore.InMemoryUserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsViewModelTest {
    @Test
    fun `initial state reflects datastore`() = runTest {
        val prefs = InMemoryUserPreferences()
        prefs.setUserName("Albin")
        prefs.setAppLanguage(AppLanguage.SV)
        val vm = SettingsViewModel(prefs)
        vm.state.test {
            val s = awaitItem()
            assertEquals("Albin", s.userName)
            assertEquals(AppLanguage.SV, s.language)
        }
    }

    @Test
    fun `saveName updates datastore`() = runTest {
        val prefs = InMemoryUserPreferences()
        val vm = SettingsViewModel(prefs)
        vm.saveName("Bjorn")
        prefs.userName.test { assertEquals("Bjorn", awaitItem()) }
    }

    @Test
    fun `saveLanguage updates datastore`() = runTest {
        val prefs = InMemoryUserPreferences()
        val vm = SettingsViewModel(prefs)
        vm.saveLanguage(AppLanguage.EN)
        prefs.appLanguage.test { assertEquals(AppLanguage.EN, awaitItem()) }
    }
}
```

- [ ] **Step 4: Skapa `SettingsViewModel.kt`**

```kotlin
package se.birdy.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.birdy.datastore.AppLanguage
import se.birdy.datastore.UserPreferences

class SettingsViewModel(
    private val prefs: UserPreferences,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> =
        combine(prefs.userName, prefs.appLanguage) { name, lang ->
            SettingsUiState(userName = name, language = lang)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SettingsUiState(userName = "", language = AppLanguage.SYSTEM),
        )

    fun saveName(name: String) {
        viewModelScope.launch { prefs.setUserName(name.trim()) }
    }

    fun saveLanguage(language: AppLanguage) {
        viewModelScope.launch { prefs.setAppLanguage(language) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
```

- [ ] **Step 5: Run test — verify PASS**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*SettingsViewModelTest*"`
Expected: PASS — 3 tests.

- [ ] **Step 6: Skapa `SettingsScreen.kt`**

```kotlin
package se.birdy.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.settings_back
import birdy_bird_scanner.composeapp.generated.resources.settings_label_about
import birdy_bird_scanner.composeapp.generated.resources.settings_label_language
import birdy_bird_scanner.composeapp.generated.resources.settings_label_name
import birdy_bird_scanner.composeapp.generated.resources.settings_language_en
import birdy_bird_scanner.composeapp.generated.resources.settings_language_sv
import birdy_bird_scanner.composeapp.generated.resources.settings_language_system
import birdy_bird_scanner.composeapp.generated.resources.settings_name_dialog_cancel
import birdy_bird_scanner.composeapp.generated.resources.settings_name_dialog_save
import birdy_bird_scanner.composeapp.generated.resources.settings_name_dialog_title
import birdy_bird_scanner.composeapp.generated.resources.settings_title
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MossCreme
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.datastore.AppLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var showNameDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MossCreme,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_title), fontWeight = FontWeight.W700) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.settings_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MossCreme),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsRow(
                label = stringResource(Res.string.settings_label_name),
                value = state.userName.ifEmpty { "—" },
                onClick = { showNameDialog = true },
            )
            LanguageSection(
                current = state.language,
                onSelect = { viewModel.saveLanguage(it) },
            )
            SettingsRow(
                label = stringResource(Res.string.settings_label_about),
                value = "v0.7.0a", // bumpa när tag sätts
                onClick = { /* about-dialog kommer i Plan 7b — visa version i 7a */ },
            )
        }
    }

    if (showNameDialog) {
        NameEditDialog(
            initial = state.userName,
            onSave = {
                viewModel.saveName(it)
                showNameDialog = false
            },
            onDismiss = { showNameDialog = false },
        )
    }
}

@Composable
private fun SettingsRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SandCreme)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextOnCreme, fontWeight = FontWeight.W600, fontSize = 16.sp)
        Text(value, color = AccentCopper, fontWeight = FontWeight.W600, fontSize = 16.sp)
    }
}

@Composable
private fun LanguageSection(current: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SandCreme)
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.settings_label_language),
            color = TextOnCreme,
            fontWeight = FontWeight.W600,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(8.dp))
        listOf(
            AppLanguage.SV to Res.string.settings_language_sv,
            AppLanguage.EN to Res.string.settings_language_en,
            AppLanguage.SYSTEM to Res.string.settings_language_system,
        ).forEach { (lang, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { onSelect(lang) })
                    .padding(vertical = 6.dp),
            ) {
                RadioButton(selected = current == lang, onClick = { onSelect(lang) })
                Spacer(Modifier.height(0.dp))
                Text(stringResource(label), color = TextOnCreme, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun NameEditDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_name_dialog_title)) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                Text(stringResource(Res.string.settings_name_dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.settings_name_dialog_cancel))
            }
        },
        containerColor = MossCreme,
    )
}
```

- [ ] **Step 7: Lägg `Settings`-route i `AppRoute.kt`**

Öppna `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt`. Lägg före `DebugBenchmark`:

```kotlin
@Serializable data object Settings : AppRoute
```

- [ ] **Step 8: Wire factory i AppGraph**

Öppna `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`. Lägg till:

```kotlin
import se.birdy.app.ui.settings.SettingsViewModel

// ... klass-body ...

fun settingsViewModel(): SettingsViewModel = SettingsViewModel(userPreferences)
```

- [ ] **Step 9: Wire route i AppScaffold**

Öppna `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt`. Lägg till efter `composable<AppRoute.Badges>`:

```kotlin
composable<AppRoute.Settings> {
    se.birdy.app.ui.settings.SettingsScreen(
        viewModel = remember(graph) { graph.settingsViewModel() },
        onBack = { navController.popBackStack() },
    )
}
```

Lägg också entry-point från Encyclopedia debug-menu — öppna existerande wiring för `EncyclopediaScreen`:

Hitta `onDebugBenchmarkClick = { navController.navigate(AppRoute.DebugBenchmark) }`. Lägg en ny prop `onSettingsClick = { navController.navigate(AppRoute.Settings) }` om EncyclopediaScreen redan stödjer det. Annars: lägg `Settings`-tap i `EncyclopediaScreen.kt`'s overflow-menu (sök efter `DropdownMenuItem` eller liknande och lägg till en ny "Inställningar"-rad).

> **Detta är en overflow-meny-edit i existerande Encyclopedia-skärm.** Om EncyclopediaScreen inte har en överflow-meny i Plan 3-koden (kolla källan), placera istället en TextButton "Inställningar" i header (temporärt — flyttas till Lifelist-gear i 7b).

- [ ] **Step 10: Run alla tester**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: PASS — alla nya + existerande tester gröna.

- [ ] **Step 11: Bygg + lint**

Run: `./gradlew :composeApp:assembleDebug :composeApp:ktlintCheck :composeApp:detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 12: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/ \
        composeApp/src/commonTest/kotlin/se/birdy/app/ui/settings/ \
        composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/composeResources/values-en/strings.xml \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/
git commit -m "feat(settings): Plan 7a Task 11 — SettingsScreen (name + language) + 3 VM tests"
```

---

## Task 12: AppGate — visa onboarding första gången

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppGate.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/App.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`

`AppGate` är wrappern som läser `has_seen_onboarding` från DataStore. Om `false` → visar `OnboardingScreen`. Om `true` → visar `AppScaffold`.

- [ ] **Step 1: Lägg `onboardingViewModel()`-factory i AppGraph**

Öppna `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`. Lägg till import:

```kotlin
import se.birdy.app.ui.onboarding.OnboardingViewModel
```

Och i klass-body (efter de andra factory-funktionerna):

```kotlin
fun onboardingViewModel(fallbackName: String): OnboardingViewModel =
    OnboardingViewModel(prefs = userPreferences, defaultFallbackName = fallbackName)
```

> Vi tar `fallbackName` som arg istället för att slå upp resource:n här (det skulle kräva `suspend fun` eftersom `getString` är suspend). `AppGate` läser resource:n via `stringResource(...)` och passar in.

- [ ] **Step 2: Skapa `AppGate.kt`**

```kotlin
package se.birdy.app.ui.scaffold

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p3_fallback_name
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.di.AppGraph
import se.birdy.app.ui.onboarding.OnboardingScreen
import se.birdy.app.ui.onboarding.OnboardingUiState
import se.birdy.app.ui.theme.MossCreme

@Composable
fun AppGate(graph: AppGraph) {
    val hasSeen by graph.userPreferences.hasSeenOnboarding.collectAsState(initial = null)

    when (hasSeen) {
        null -> SplashLoading()
        true -> AppScaffold(graph)
        false -> {
            val fallback = stringResource(Res.string.onboarding_p3_fallback_name)
            val vm = remember(graph) { graph.onboardingViewModel(fallback) }
            val state by vm.state.collectAsState()
            when (val s = state) {
                is OnboardingUiState.Visible -> OnboardingScreen(
                    state = s,
                    onPageChange = vm::setPageIndex,
                    onNameChange = vm::onNameChange,
                    onComplete = vm::complete,
                )
                OnboardingUiState.Done -> AppScaffold(graph)
                OnboardingUiState.Loading -> SplashLoading()
            }
        }
    }
}

@Composable
private fun SplashLoading() {
    Box(modifier = Modifier.fillMaxSize().background(MossCreme), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
```

- [ ] **Step 3: Wire `AppGate` i `App.kt`**

Öppna `composeApp/src/commonMain/kotlin/se/birdy/app/App.kt`. Ersätt `AppScaffold(graph)`:

```kotlin
package se.birdy.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CancellationException
import se.birdy.app.di.AppGraph
import se.birdy.app.ui.scaffold.AppGate
import se.birdy.app.ui.theme.BirdyTheme

@Composable
fun App(graph: AppGraph) {
    LaunchedEffect(Unit) {
        runCatching { graph.badgeBackfill.runIfNeeded() }
            .onFailure { if (it is CancellationException) throw it }
    }
    BirdyTheme {
        AppGate(graph)
    }
}
```

- [ ] **Step 4: Verifiera build**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: ktlint + detekt**

Run: `./gradlew :composeApp:ktlintCheck :composeApp:detekt`
Expected: SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppGate.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/App.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt
git commit -m "feat(scaffold): Plan 7a Task 12 — AppGate gates first-launch onboarding"
```

---

## Task 13: Hela-projekt-build + device-verify + tag

**Files:**
- Create: `docs/superpowers/screenshots/v0.7.0a-foundation/` (committa skärmdumpar)

- [ ] **Step 1: Full build alla moduler**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — alla moduler gröna inkl. `:shared:datastore` (nya) och `:shared:data` (med migration).

- [ ] **Step 2: Full test-suite**

Run: `./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :shared:data:jvmTest :shared:datastore:jvmTest :composeApp:testDebugUnitTest`
Expected: PASS — alla tester gröna.

- [ ] **Step 3: Lint hela projektet**

Run: `./gradlew ktlintCheck detekt`
Expected: SUCCESS.

- [ ] **Step 4: Installera + starta på Galaxy S23 Ultra**

```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

- [ ] **Step 5: Manual device-verify checklist**

På telefonen, verifiera:
- [ ] **Onboarding** visas första gången (om DataStore fresh — annars `adb shell pm clear se.birdy.android` först).
- [ ] Sida 1 visar "Birdy." + body "Lyssna, känn igen, samla."
- [ ] Sida 2 visar "Fyra ställen att vara på." + 4 feature-rader
- [ ] Sida 3 visar input + "Börja samla →"-CTA
- [ ] Skip-länken hoppar till sida 3 (eller completes om redan på sida 3)
- [ ] CTA på sida 3 (med tomt namn) → går till AppScaffold med fallback "Min" — kolla i Settings att namnet är "Min"
- [ ] **Bottom-bar** visar `Listen · Archive · Lifelist · Badges` med nya ikoner (Hearing/LibraryBooks/CollectionsBookmark/Stars)
- [ ] **Existerande screens** öppnas oförändrat (Listen → ScanScreen, Archive → EncyclopediaScreen, Lifelist → DiaryScreen, Badges → BadgesScreen)
- [ ] **Settings** kan nås via Encyclopedia-overflow-menu — namn + språk-toggle fungerar
- [ ] **Migration-test:** om device har existerande observationer från `v0.5.0a-diary`, öppna Lifelist (DiaryScreen) — appen ska inte krascha. Kolla via `adb shell sqlite3` eller logcat att `stamp_number` är populerat. (Om device är fresh: skippas)
- [ ] Stäng + öppna appen igen — onboarding visas INTE andra gången

- [ ] **Step 6: Ta skärmdumpar**

```bash
ADB="/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe"
mkdir -p docs/superpowers/screenshots/v0.7.0a-foundation
"$ADB" shell pm clear se.birdy.android
"$ADB" shell am start -n se.birdy.android/.MainActivity
sleep 2
"$ADB" exec-out screencap -p > docs/superpowers/screenshots/v0.7.0a-foundation/01-onboarding-page1.png
# Swipa till sida 2 manuellt, sen:
"$ADB" exec-out screencap -p > docs/superpowers/screenshots/v0.7.0a-foundation/02-onboarding-page2.png
# Swipa till sida 3:
"$ADB" exec-out screencap -p > docs/superpowers/screenshots/v0.7.0a-foundation/03-onboarding-page3.png
# Tap CTA, sen:
"$ADB" exec-out screencap -p > docs/superpowers/screenshots/v0.7.0a-foundation/04-bottom-bar-listen.png
# Tap Archive:
"$ADB" exec-out screencap -p > docs/superpowers/screenshots/v0.7.0a-foundation/05-bottom-bar-archive.png
# Settings:
"$ADB" exec-out screencap -p > docs/superpowers/screenshots/v0.7.0a-foundation/06-settings.png
```

- [ ] **Step 7: Commit skärmdumpar**

```bash
git add docs/superpowers/screenshots/v0.7.0a-foundation/
git commit -m "screenshots: Plan 7a v0.7.0a-foundation — onboarding 3 pages + bottom-bar + settings"
```

- [ ] **Step 8: Tag + push**

```bash
git tag v0.7.0a-foundation
git push origin main --tags
```

- [ ] **Step 9: Uppdatera CLAUDE.md status**

Öppna `CLAUDE.md`. I `## Plan-of-plans (v1)`-tabellen, lägg rad:

```
| 7a | Redesign Foundation — tokens, ItalicMixed, HeroZone, DataStore, Onboarding, Settings | ✅ `v0.7.0a-foundation` |
```

I `## Status (...):`-raden, lägg `Plan 7a (Redesign Foundation) ✅ v0.7.0a-foundation. **Plan 7b (Skärm-redesigns) är nästa.**`

- [ ] **Step 10: Uppdatera auto-memory**

Skapa `~/.claude/projects/C--Users-abbea-dev-birdy-bird-scanner/memory/project_plan_7a_status.md`:

```markdown
---
name: Plan 7a (Redesign Foundation) — DONE
description: Shipped v0.7.0a-foundation 2026-MM-DD; tokens + ItalicMixed + HeroZone + DataStore + Onboarding + Settings; SQLDelight migration for stamp_number
type: project
---

[fyll i lessons learned från task-execution + post-tag follow-ups]
```

Lägg rad i `MEMORY.md`:

```
- [Plan 7a (Foundation) — DONE](project_plan_7a_status.md) — ...
```

- [ ] **Step 11: Final commit för CLAUDE.md + auto-memory**

```bash
git add CLAUDE.md
git commit -m "docs(plan-7a): mark Plan 7a done; v0.7.0a-foundation tagged"
```

(Auto-memory är inte i git — uppdateras separat.)

---

## Risker

- **SQLDelight verifyMigrations** fångar inte alla case — om en hand-rolled migration fungerar i test men kraschar på riktig device-DB, måste vi testa på Albins existerande DB innan tag. **Mitigation:** Step 5 av Task 13 testar explicit på device.
- **DataStore-init blockar inte cold-start** — `userPreferences.hasSeenOnboarding.collectAsState(initial = null)` visar SplashLoading tills första värdet kommer in. På första launch är detta < 100ms (en disk-read). Om Albin märker flicker, byt initial till `false` (visar onboarding direkt) — risken är att existerande users som har v0.5.0b installerat ser onboarding 1 gång efter uppgradering, vilket är acceptabelt.
- **Crimson Pro italic + variable-font + Compose**: AnnotatedString med italic + color kan ha rendering-quirks. **Mitigation:** Task 2-test verifierar parsing-logik (snabbt feedback). Visuell verifiering på device i Task 13.
- **Tab-namn blandad sv/en känns konstigt**: spec lockar engelska tab-namn även i svenska app — kan väcka friktion hos icke-engelsktalande Albin-användare. **Mitigation:** Plan 7b kommer ha hero-titlar på rätt språk; tab-namn är "brand-words" som engelska "Spotify"/"Discover".
- **Encyclopedia-debug-meny** är inte permanent placering för Settings-entry. När Lifelist redesign:as i 7b flyttas det till Lifelist-hero gear-icon. Denna interim-placering är OK för Albin (single user).

---

## Acceptance criteria (Plan 7a)

- [ ] Alla 13 tasks committade.
- [ ] Build grön: `./gradlew build`.
- [ ] Test-suite grön: `./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :shared:data:jvmTest :shared:datastore:jvmTest :composeApp:testDebugUnitTest`.
- [ ] Lint grön: `./gradlew ktlintCheck detekt`.
- [ ] Onboarding visas första gången, lagrar namn i DataStore, visas inte igen vid restart.
- [ ] Bottom-bar visar `Listen · Archive · Lifelist · Badges` med korrekta ikoner (existerande target-skärmar oförändrade).
- [ ] Settings-skärm öppnas från Encyclopedia debug-meny, kan ändra namn + språk.
- [ ] SQLDelight-migration backfillar stamp_number för existerande observationer (om device-test gjordes med befintlig DB).
- [ ] Tag `v0.7.0a-foundation` pushad till `origin main`.
- [ ] CLAUDE.md + auto-memory uppdaterade.
- [ ] 6 device-skärmdumpar committade i `docs/superpowers/screenshots/v0.7.0a-foundation/`.

---

## Out-of-scope (Plan 7a — implementeras i 7b/7c)

- **Listen-launcher hub** (Plan 7b)
- **Archive-redesign** (Plan 7b)
- **Lifelist-redesign** med stamp-rader (Plan 7b)
- **Badges-redesign** med progress-bar (Plan 7b)
- **Match-flow** med threshold-logik (Plan 7c)
- **Match-skärm + Disambig-skärm** (Plan 7c)
- **Befintliga skärm-restyles** (species-profile, camera, photo, unlock-sheet) — Plan 7b
- **Settings-entry från Lifelist hero gear-icon** — Plan 7b (interim entry från Encyclopedia debug-menu i 7a)
- **Premium-marker shimmer-animation** — Plan 7b
- **Stat 3-toggle på Lifelist** — Plan 7b (DataStore-key finns klar i 7a)
- **`lifelist_stat3_choice` / `archive_sort` / `lifelist_sort` är skrivna och läsbara via UserPreferences i 7a, men ingen UI använder dem ännu — kommer i 7b**
