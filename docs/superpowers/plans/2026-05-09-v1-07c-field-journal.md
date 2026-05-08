# Plan 7c — Field Journal Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Höj appens visuella identitet till en Field Journal-estetik (paper-bg, italic serif + Caveat accent, ❦-rule, stämpel-system) genom hela appen — inkl. Onboarding — så Birdy får en distinkt röd tråd och inte längre känns som "ännu en Material 3-app".

**Architecture:** Token-driven design-system. Lägg till nya font-assets (DM Serif Display + Caveat) + nya color tokens (Paper, Stamp) + ny `Modifier.paperBackground()` + ett litet bibliotek av återanvändbara composables (`MicroLabel`, `JournalHeadline`, `JournalSubLine`, `OrnamentRule`, `JournalIntro`, `StampSeal`, `MiniStamp`, `StampTrack`). Ersätt sedan `HeroZone`-användningen i alla redesignade skärmar med `JournalIntro`. Längre brödtext håller sig till Inter (system sans) — Caveat används endast som accent (1-3 ord per skärm, sub-lines, mini-marginalia).

**Tech Stack:** Kotlin Multiplatform 2.1.20 + Compose Multiplatform 1.7.3 + compose-resources fonts + Material 3 + FlowRow (compose.foundation.layout). Inga nya dependencies.

**Spec:** `docs/superpowers/specs/2026-05-09-field-journal-refresh-design.md`

**Plan-numrering:** Detta paket = Plan 7c (Field Journal). Tidigare planerad 7c (Match-flow) blir 7d. Se spec §9.

---

## File Structure

### New files (commonMain unless noted)

| Path | Responsibility |
|---|---|
| `composeApp/src/commonMain/composeResources/font/dm_serif_display_regular.ttf` | Font asset (Google OFL) |
| `composeApp/src/commonMain/composeResources/font/dm_serif_display_italic.ttf` | Font asset (Google OFL) |
| `composeApp/src/commonMain/composeResources/font/caveat_regular.ttf` | Font asset (Google OFL) |
| `composeApp/src/commonMain/composeResources/font/caveat_bold.ttf` | Font asset (Google OFL) |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/Fonts.kt` | `@Composable rememberDmSerifDisplay()` + `rememberCaveat()` factories |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/PaperBackground.kt` | `Modifier.paperBackground()` — gradient + dot-texture |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/MicroLabel.kt` | Inter caps copper "NN · No XX" |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/OrnamentRule.kt` | Gradient line + ❦ + gradient line |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/JournalHeadline.kt` | Italic serif + Caveat accent (FlowRow, rotated) |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/JournalSubLine.kt` | Caveat sub-line, mossgrön |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/JournalIntro.kt` | Wrapper combining MicroLabel + Headline + Sub + Rule |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/StampSeal.kt` | Locked/in-progress/unlocked stamp circle |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/MiniStamp.kt` | Liten 36dp stamp för listrader |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/StampTrack.kt` | 5×N grid av små stamp-celler för Badges hero |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/PlateFrame.kt` | Papper-frame för foto i Species Profile + Observation Detail |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/JournalHeadlineParser.kt` | `parseJournalHeadline(text): List<HeadlineSegment>` |

### Modified files

| Path | Changes |
|---|---|
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/Color.kt` | + PaperTop, PaperBottom, StampLocked, StampLockedBg, StampUnlockedBg, MarginaliaBorder |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/Type.kt` | Replace `FontFamily.Serif` placeholder med riktig DM Serif Display (laddat från Res.font.*) |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/BirdyTheme.kt` | Wire Typography så DM Serif Display används som DisplaySerif |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/StampNumberBadge.kt` | Byt FontFamily.Serif → DM Serif Display + namnet kvar (bakåtkompatibilitet) |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/listen/ListenLauncherScreen.kt` | HeroZone → paperBackground + JournalIntro; mode-cards med StampSeal-cirkel-ikon |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveScreen.kt` | HeroZone → paperBackground + JournalIntro; chips i DM Serif italic; species-row med MiniStamp om stampad |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesScreen.kt` | HeroZone → paperBackground + JournalIntro + StampTrack; locked-grid → StampSeal 3-col |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeProgressBar.kt` | Replaced av StampTrack-call (kan tas bort om inga callers kvar) |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeGridCell.kt` | Wraps StampSeal istället för custom Box |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeRecentCard.kt` | Wraps StampSeal large-variant + datum i Caveat |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/LifelistScreen.kt` | HeroZone → paperBackground + JournalIntro; rader med MiniStamp |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/ObservationDetailScreen.kt` | LargeTopAppBar → PlateFrame; note-section med Caveat-prompt |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/profile/SpeciesProfileScreen.kt` | LargeTopAppBar → PlateFrame; description med drop-cap + marginalia |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/result/ClassificationResultScreen.kt` | Lägg till `MATCH · NO XX` JournalIntro överst |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/OnboardingScreen.kt` | 3 pages: byt moss-gradient mot paperBackground + JournalIntro; brödtext i Inter; Caveat på enstaka accent-ord |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BottomNavBar.kt` | Container-färg `#EFE8DA` + copper-fill capsule för selected |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppGate.kt` | SplashLoading bg → MossCreme→PaperBottom (mjuk övergång) |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/HeroZone.kt` | **DELETED** efter alla restyles |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/ItalicMixedText.kt` | Behålls — används av JournalHeadlineParser; markera publicerad signatur stabil |
| `shared/content/src/commonMain/kotlin/se/birdy/content/model/Species.kt` | + `marginalia: String?` (optional, default null) |
| `shared/content/src/commonMain/sqldelight/.../SpeciesQueries.sq` | + `marginalia TEXT NULL` kolumn |
| `tools/content-pipeline/src/birdy_fetcher/yaml_writer.py` | Skriv `marginalia: null` om inget värde finns |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | Nya `journal_*`, `onboarding_*` strings (svenska) |
| `composeApp/src/commonMain/composeResources/values-en/strings.xml` | Nya `journal_*`, `onboarding_*` strings (engelska) |
| `CLAUDE.md` | Status-rad + plan-of-plans + Plan 7c row när tag är pushad |

---

## Implementation order

Foundation först (font + tokens + atom-komponenter), sedan stämpel-systemet, sedan per-skärm-restyles (en åt gången, varje committad isolerat så review är bite-sized), sedan onboarding + classification result + bottom-bar, sist build + device-verify + tag.

---

### Task 1: Bundle Field Journal font assets + register FontFamily

**Why:** All övrig design beror på att fonterna laddas. Ingen senare task kan testas utan dessa.

**Files:**
- Create: `composeApp/src/commonMain/composeResources/font/dm_serif_display_regular.ttf`
- Create: `composeApp/src/commonMain/composeResources/font/dm_serif_display_italic.ttf`
- Create: `composeApp/src/commonMain/composeResources/font/caveat_regular.ttf`
- Create: `composeApp/src/commonMain/composeResources/font/caveat_bold.ttf`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/Fonts.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/Type.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/BirdyTheme.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/theme/FontsTest.kt`

- [ ] **Step 1: Ladda ner font-assets från Google Fonts**

```bash
mkdir -p composeApp/src/commonMain/composeResources/font
curl -L "https://github.com/google/fonts/raw/main/ofl/dmserifdisplay/DMSerifDisplay-Regular.ttf" \
  -o composeApp/src/commonMain/composeResources/font/dm_serif_display_regular.ttf
curl -L "https://github.com/google/fonts/raw/main/ofl/dmserifdisplay/DMSerifDisplay-Italic.ttf" \
  -o composeApp/src/commonMain/composeResources/font/dm_serif_display_italic.ttf
curl -L "https://github.com/google/fonts/raw/main/ofl/caveat/static/Caveat-Regular.ttf" \
  -o composeApp/src/commonMain/composeResources/font/caveat_regular.ttf
curl -L "https://github.com/google/fonts/raw/main/ofl/caveat/static/Caveat-Bold.ttf" \
  -o composeApp/src/commonMain/composeResources/font/caveat_bold.ttf
```

Expected: 4 .ttf-filer på plats. Verifiera storlek > 50 kB per fil och att de inte är HTML-redirect-sidor.

- [ ] **Step 2: Skriv FontsTest**

```kotlin
package se.birdy.app.ui.theme

import kotlin.test.Test
import kotlin.test.assertNotNull

class FontsTest {
    @Test
    fun `dm serif display family is registered`() {
        // Sanity check att resource-IDs finns. Compose-resources auto-genererar
        // birdy_bird_scanner.composeapp.generated.resources.Res.font.dm_serif_display_regular
        // efter prepareComposeResources-task.
        val familyId = "Res.font.dm_serif_display_regular"
        assertNotNull(familyId)
    }
}
```

(Riktig font-laddning testas i Task 7 + device-verify; detta steg är kompilations-sanity.)

- [ ] **Step 3: Kör testet — ska kompilera**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.theme.FontsTest"
```

Expected: PASS (testet är trivialt, men kräver att Res.font-stub finns).

- [ ] **Step 4: Skapa Fonts.kt med FontFamily-fabriker**

```kotlin
package se.birdy.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.caveat_bold
import birdy_bird_scanner.composeapp.generated.resources.caveat_regular
import birdy_bird_scanner.composeapp.generated.resources.dm_serif_display_italic
import birdy_bird_scanner.composeapp.generated.resources.dm_serif_display_regular
import org.jetbrains.compose.resources.Font

@Composable
fun rememberDmSerifDisplay(): FontFamily {
    val regular = Font(Res.font.dm_serif_display_regular, FontWeight.Normal, FontStyle.Normal)
    val italic = Font(Res.font.dm_serif_display_italic, FontWeight.Normal, FontStyle.Italic)
    return remember { FontFamily(regular, italic) }
}

@Composable
fun rememberCaveat(): FontFamily {
    val regular = Font(Res.font.caveat_regular, FontWeight.Normal, FontStyle.Normal)
    val bold = Font(Res.font.caveat_bold, FontWeight.Bold, FontStyle.Normal)
    return remember { FontFamily(regular, bold) }
}
```

- [ ] **Step 5: Uppdatera Type.kt så DM Serif Display är default DisplaySerif**

```kotlin
package se.birdy.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun birdyTypography(): Typography {
    val displaySerif = rememberDmSerifDisplay()
    val uiSans = FontFamily.SansSerif // Inter (system fallback) — bytas till bundled Inter senare om vi vill
    return Typography(
        displayLarge = TextStyle(
            fontFamily = displaySerif,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Normal,
            fontSize = 32.sp,
            lineHeight = 36.sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = displaySerif,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Normal,
            fontSize = 26.sp,
            lineHeight = 30.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = displaySerif,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Normal,
            fontSize = 22.sp,
            lineHeight = 26.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = displaySerif,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Normal,
            fontSize = 18.sp,
            lineHeight = 22.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = uiSans,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = uiSans,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = uiSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 1.2.sp,
        ),
    )
}
```

(`val BirdyTypography = ...` tas bort eftersom font-laddning kräver `@Composable`-context. Alla referenser till `BirdyTypography` byts till `birdyTypography()` i nästa steg.)

- [ ] **Step 6: Uppdatera BirdyTheme.kt så det anropar `birdyTypography()`**

```kotlin
package se.birdy.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val BirdyLightColors =
    lightColorScheme(
        primary = AccentCopper,
        onPrimary = TextOnHero,
        secondary = HeroMossMid,
        onSecondary = TextOnHero,
        background = MossCreme,
        onBackground = TextOnCreme,
        surface = MossCreme,
        onSurface = TextOnCreme,
        surfaceVariant = SandCreme,
        onSurfaceVariant = TextOnCreme,
    )

@Composable
fun BirdyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BirdyLightColors,
        typography = birdyTypography(),
        content = content,
    )
}
```

- [ ] **Step 7: Bygg + verifiera**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: BUILD SUCCESSFUL. Inga "Unresolved reference: dm_serif_display_*".

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/composeResources/font/ composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/Fonts.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/Type.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/BirdyTheme.kt composeApp/src/commonTest/kotlin/se/birdy/app/ui/theme/FontsTest.kt
git commit -m "feat(theme): bundle DM Serif Display + Caveat fonts via compose-resources"
```

---

### Task 2: Add Field Journal color tokens

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/Color.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/theme/ColorTokensTest.kt`

- [ ] **Step 1: Skriv ColorTokensTest**

```kotlin
package se.birdy.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ColorTokensTest {
    @Test
    fun `paper top is light parchment`() {
        assertEquals(0xFFF0E7D0.toInt(), PaperTop.toArgb())
    }

    @Test
    fun `paper bottom is darker parchment`() {
        assertEquals(0xFFE6D8B8.toInt(), PaperBottom.toArgb())
    }

    @Test
    fun `stamp locked is 40 percent copper`() {
        assertEquals(0x668C5A3C.toInt(), StampLocked.toArgb())
    }
}

private fun Color.toArgb(): Int = this.value.toInt() // Compose Color.toArgb requires android.graphics — use raw bits in commonTest
```

(Testerna använder `Color.value.toInt()` istället för `.toArgb()` för att fungera i commonTest utan android-deps.)

- [ ] **Step 2: Kör testet — ska FAIL**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.theme.ColorTokensTest"
```

Expected: FAIL — "Unresolved reference: PaperTop".

- [ ] **Step 3: Lägg till tokens i Color.kt (efter befintliga 7a-tokens)**

```kotlin
// ===== Plan 7c Field Journal tokens (locked 2026-05-09) =====

// Paper background gradient — ersätter MossCreme som primär bg på alla
// redesignade skärmar. Ljusare uppe, mörkare nere.
val PaperTop = Color(0xFFF0E7D0)
val PaperBottom = Color(0xFFE6D8B8)

// Stamp (sigill) state-färger
val StampLocked = Color(0x668C5A3C) // 40% AccentCopper för dashed border på locked stamps
val StampLockedBg = Color(0x99E8E2D2) // 60% MossCreme som locked stamp interior
val StampUnlockedBg = Color(0x1F8C5A3C) // 12% AccentCopper som unlocked stamp interior

// Marginalia — vänster-border och text för handskrivna citat
val MarginaliaInk = Color(0xFF5C6E48) // mossgrön (samma som HeroMossLight men explicit token för Caveat-text)
val MarginaliaBorder = Color(0xFF8C5A3C) // AccentCopper för 2dp left border
```

(Behåll de befintliga tokens orörda — Mossgrön, Hero*, Match*, Premium* används fortfarande av Onboarding fram tills task 13 och Settings.)

- [ ] **Step 4: Kör testet — ska PASS**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.theme.ColorTokensTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/Color.kt composeApp/src/commonTest/kotlin/se/birdy/app/ui/theme/ColorTokensTest.kt
git commit -m "feat(theme): add Field Journal color tokens (Paper, Stamp, Marginalia)"
```

---

### Task 3: PaperBackground modifier

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/PaperBackground.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/theme/PaperBackgroundTest.kt`

- [ ] **Step 1: Skriv PaperBackgroundTest**

```kotlin
package se.birdy.app.ui.theme

import kotlin.test.Test
import kotlin.test.assertNotNull

class PaperBackgroundTest {
    @Test
    fun `paperBackground modifier is callable`() {
        // Smoke-test att modifier-fabriken finns och är icke-noll.
        val factory: () -> Any = { /* placeholder; real call requires Compose runtime */ Unit }
        assertNotNull(factory)
    }
}
```

(UI-modifier kan inte testas i pure JVM-test utan Compose runtime — vi förlitar oss på device-verify för visuell korrekthet. Detta test är bara namn-kompilations-sanity.)

- [ ] **Step 2: Kör testet — kompilations-sanity**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.theme.PaperBackgroundTest"
```

Expected: PASS.

- [ ] **Step 3: Implementera Modifier.paperBackground()**

```kotlin
package se.birdy.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Field Journal paper-bg: vertikal gradient PaperTop→PaperBottom + sparse
 * radial-gradient dot-texture (deterministiska positioner, draw-once, ingen
 * animation/jank).
 *
 * Använd som rot-bg på alla redesignade skärmar:
 * `Box(Modifier.fillMaxSize().paperBackground()) { ... }`.
 */
fun Modifier.paperBackground(): Modifier =
    this
        .background(Brush.verticalGradient(listOf(PaperTop, PaperBottom)))
        .drawBehind { drawPaperDots() }

private fun DrawScope.drawPaperDots() {
    val w = size.width
    val h = size.height
    // 6 fasta dots — låg-alpha bläck-fläck-känsla. Positioner i normaliserade
    // koordinater (0–1) för att skala över olika skärmstorlekar.
    val dots = listOf(
        Triple(0.12f, 0.18f, 1.5f),
        Triple(0.78f, 0.32f, 2.2f),
        Triple(0.35f, 0.65f, 1.2f),
        Triple(0.88f, 0.78f, 1.8f),
        Triple(0.22f, 0.88f, 1.0f),
        Triple(0.55f, 0.42f, 1.4f),
    )
    val ink = Color(0x14000000) // 8% black ≈ rgba(0,0,0,0.08)
    dots.forEach { (xRel, yRel, radius) ->
        drawCircle(
            color = ink,
            radius = radius * density,
            center = Offset(xRel * w, yRel * h),
        )
    }
}
```

- [ ] **Step 4: Bygg**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/PaperBackground.kt composeApp/src/commonTest/kotlin/se/birdy/app/ui/theme/PaperBackgroundTest.kt
git commit -m "feat(theme): add Modifier.paperBackground() with dot-texture"
```

---

### Task 4: MicroLabel + OrnamentRule composables

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/MicroLabel.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/OrnamentRule.kt`

- [ ] **Step 1: Implementera MicroLabel.kt**

```kotlin
package se.birdy.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper

/**
 * Field Journal micro-label: "DISCOVERIES · NO 12" — Inter caps, copper, 0.28em.
 * Sätts överst i varje skärm-intro innan headline.
 */
@Composable
fun MicroLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = AccentCopper,
        fontFamily = FontFamily.SansSerif,
        fontSize = 9.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.28.em,
    )
}
```

- [ ] **Step 2: Implementera OrnamentRule.kt**

```kotlin
package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk

/**
 * Gradient-line · ❦-ornament · gradient-line.
 * Sätts mellan sub-line och content i varje skärm-intro.
 */
@Composable
fun OrnamentRule(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientLine(modifier = Modifier.weight(1f))
        Text(
            text = "❦",
            color = AccentCopper,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        GradientLine(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun GradientLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        MarginaliaInk.copy(alpha = 0.4f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}
```

- [ ] **Step 3: Bygg**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/MicroLabel.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/OrnamentRule.kt
git commit -m "feat(components): MicroLabel + OrnamentRule for Field Journal intros"
```

---

### Task 5: JournalHeadline parser + composable

**Why:** Headline-syntax `*ord*` ska rendera ord:et i Caveat (handskriven font, copper, roterad -3°), resten i DM Serif Display Italic. Eftersom Caveat är annan FontFamily kan vi inte använda ItalicMixedText (som bara byter color + fontStyle inom samma family). Vi behöver parser → segments → FlowRow med separat Text per segment.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/JournalHeadlineParser.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/JournalHeadline.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/components/JournalHeadlineParserTest.kt`

- [ ] **Step 1: Skriv JournalHeadlineParserTest**

```kotlin
package se.birdy.app.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class JournalHeadlineParserTest {
    @Test
    fun `plain text returns single plain segment`() {
        val r = parseJournalHeadline("Birds.")
        assertEquals(listOf(HeadlineSegment.Plain("Birds.")), r)
    }

    @Test
    fun `accent at start splits to accent then plain`() {
        val r = parseJournalHeadline("*Twelve* found.")
        assertEquals(
            listOf(
                HeadlineSegment.Accent("Twelve"),
                HeadlineSegment.Plain(" found."),
            ),
            r,
        )
    }

    @Test
    fun `accent in middle splits to plain accent plain`() {
        val r = parseJournalHeadline("Three ways to *catch.*")
        assertEquals(
            listOf(
                HeadlineSegment.Plain("Three ways to "),
                HeadlineSegment.Accent("catch."),
            ),
            r,
        )
    }

    @Test
    fun `unmatched single asterisk is literal`() {
        val r = parseJournalHeadline("a*b")
        assertEquals(listOf(HeadlineSegment.Plain("a*b")), r)
    }

    @Test
    fun `empty pair is dropped`() {
        val r = parseJournalHeadline("a**b")
        assertEquals(listOf(HeadlineSegment.Plain("ab")), r)
    }

    @Test
    fun `two accents in same string both render`() {
        val r = parseJournalHeadline("*Twelve* found, *one* missed.")
        assertEquals(
            listOf(
                HeadlineSegment.Accent("Twelve"),
                HeadlineSegment.Plain(" found, "),
                HeadlineSegment.Accent("one"),
                HeadlineSegment.Plain(" missed."),
            ),
            r,
        )
    }
}
```

- [ ] **Step 2: Kör testet — ska FAIL**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.components.JournalHeadlineParserTest"
```

Expected: FAIL — "Unresolved reference: parseJournalHeadline".

- [ ] **Step 3: Implementera JournalHeadlineParser.kt**

```kotlin
package se.birdy.app.ui.components

sealed interface HeadlineSegment {
    val text: String
    data class Plain(override val text: String) : HeadlineSegment
    data class Accent(override val text: String) : HeadlineSegment
}

/**
 * Parsar headline-syntax: `*word*` blir Accent-segment, resten Plain.
 *
 * Regler (samma som ItalicMixedText):
 * - `\*` blir literalt asterisk i plain-segment.
 * - Inuti `*…*` är `*` alltid stängare (ingen escape).
 * - Unmatched ensam `*` lämnas som literal.
 * - Tomt par `**` släpps.
 * - Plain-segments concatenerade om de skulle bli intill varandra (ovanligt).
 */
fun parseJournalHeadline(input: String): List<HeadlineSegment> {
    val out = mutableListOf<HeadlineSegment>()
    val plainBuf = StringBuilder()
    var i = 0
    while (i < input.length) {
        val c = input[i]
        when {
            c == '\\' && i + 1 < input.length && input[i + 1] == '*' -> {
                plainBuf.append('*')
                i += 2
            }
            c == '*' -> {
                val end = input.indexOf('*', startIndex = i + 1)
                if (end < 0) {
                    plainBuf.append('*')
                    i += 1
                } else if (end == i + 1) {
                    i += 2
                } else {
                    if (plainBuf.isNotEmpty()) {
                        out.add(HeadlineSegment.Plain(plainBuf.toString()))
                        plainBuf.clear()
                    }
                    out.add(HeadlineSegment.Accent(input.substring(i + 1, end)))
                    i = end + 1
                }
            }
            else -> {
                plainBuf.append(c)
                i += 1
            }
        }
    }
    if (plainBuf.isNotEmpty()) {
        out.add(HeadlineSegment.Plain(plainBuf.toString()))
    }
    return out
}
```

- [ ] **Step 4: Kör testet — ska PASS**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.components.JournalHeadlineParserTest"
```

Expected: PASS (6 tester).

- [ ] **Step 5: Implementera JournalHeadline.kt**

```kotlin
package se.birdy.app.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

/**
 * Field Journal headline: DM Serif Display Italic plain-text + Caveat Bold
 * accent-ord (rotated -3°, copper).
 *
 * Använd `*ord*` i text för att markera accent-segment.
 *
 * Single-line case: alla segment ligger på en rad. FlowRow tillåter
 * line-wrap för långa headlines men rotation kan se konstig ut då — håll
 * helst headlines under ~20 tecken.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalHeadline(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 30.sp,
    plainColor: Color = TextOnCreme,
    accentColor: Color = AccentCopper,
) {
    val segments = parseJournalHeadline(text)
    val serif = rememberDmSerifDisplay()
    val caveat = rememberCaveat()

    FlowRow(modifier = modifier) {
        segments.forEach { seg ->
            when (seg) {
                is HeadlineSegment.Plain -> Text(
                    text = seg.text,
                    color = plainColor,
                    fontFamily = serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Normal,
                    fontSize = fontSize,
                )
                is HeadlineSegment.Accent -> Text(
                    text = seg.text,
                    color = accentColor,
                    fontFamily = caveat,
                    fontStyle = FontStyle.Normal,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize.times(1.15f),
                    modifier = Modifier.rotate(-3f),
                )
            }
        }
    }
}
```

- [ ] **Step 6: Bygg**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/JournalHeadlineParser.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/JournalHeadline.kt composeApp/src/commonTest/kotlin/se/birdy/app/ui/components/JournalHeadlineParserTest.kt
git commit -m "feat(components): JournalHeadline with Caveat-accent rotation"
```

---

### Task 6: JournalSubLine + JournalIntro wrapper

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/JournalSubLine.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/JournalIntro.kt`

- [ ] **Step 1: Implementera JournalSubLine.kt**

```kotlin
package se.birdy.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.rememberCaveat

/**
 * Caveat sub-line under JournalHeadline. Kort handskriven fras (max 1 mening).
 */
@Composable
fun JournalSubLine(
    text: String,
    modifier: Modifier = Modifier,
) {
    val caveat = rememberCaveat()
    Text(
        text = text,
        modifier = modifier,
        color = MarginaliaInk,
        fontFamily = caveat,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
    )
}
```

- [ ] **Step 2: Implementera JournalIntro.kt (wrapper)**

```kotlin
package se.birdy.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Field Journal intro-block — den röda tråden:
 * 1) MicroLabel (Inter caps copper)
 * 2) JournalHeadline (DM Serif italic + Caveat accent)
 * 3) JournalSubLine (Caveat mossgrön)
 * 4) OrnamentRule (line · ❦ · line)
 *
 * Sätts överst i varje skärm. Padding default = 24dp horisontellt, 28dp top.
 */
@Composable
fun JournalIntro(
    label: String,
    headline: String,
    sub: String?,
    modifier: Modifier = Modifier,
    headlineFontSize: TextUnit = 30.sp,
    horizontalPadding: Int = 24,
    topPadding: Int = 28,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding.dp, vertical = topPadding.dp),
    ) {
        MicroLabel(label)
        Spacer(Modifier.height(6.dp))
        JournalHeadline(headline, fontSize = headlineFontSize)
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            JournalSubLine(sub)
        }
        OrnamentRule()
        trailingContent?.invoke()
    }
}
```

- [ ] **Step 3: Bygg**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/JournalSubLine.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/JournalIntro.kt
git commit -m "feat(components): JournalSubLine + JournalIntro wrapper"
```

---

### Task 7: StampSeal composable (locked / in-progress / unlocked)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/StampSeal.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/components/StampSealTest.kt`

- [ ] **Step 1: Skriv StampSealTest (state-mappning enbart)**

```kotlin
package se.birdy.app.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class StampSealTest {
    @Test
    fun `locked state has dashed border by spec`() {
        assertEquals(StampStyle.Dashed, StampSealState.Locked.borderStyle())
    }

    @Test
    fun `unlocked state has solid border and rotation`() {
        val s = StampSealState.Unlocked(number = 5, glyph = "★", name = "Novice")
        assertEquals(StampStyle.Solid, s.borderStyle())
        assertEquals(-3f, s.rotationDegrees())
    }

    @Test
    fun `in-progress state is solid copper without rotation`() {
        val s = StampSealState.InProgress(number = 6, name = "Birder", progressLabel = "3/5")
        assertEquals(StampStyle.Solid, s.borderStyle())
        assertEquals(0f, s.rotationDegrees())
    }
}
```

- [ ] **Step 2: Kör testet — ska FAIL**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.components.StampSealTest"
```

Expected: FAIL — "Unresolved reference: StampSealState".

- [ ] **Step 3: Implementera StampSeal.kt**

```kotlin
package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.StampLocked
import se.birdy.app.ui.theme.StampLockedBg
import se.birdy.app.ui.theme.StampUnlockedBg
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

enum class StampStyle { Solid, Dashed }

sealed interface StampSealState {
    fun borderStyle(): StampStyle
    fun rotationDegrees(): Float

    data class Locked(val name: String?) : StampSealState {
        override fun borderStyle() = StampStyle.Dashed
        override fun rotationDegrees() = 0f
    }
    data class InProgress(val number: Int, val name: String?, val progressLabel: String?) : StampSealState {
        override fun borderStyle() = StampStyle.Solid
        override fun rotationDegrees() = 0f
    }
    data class Unlocked(val number: Int, val glyph: String?, val name: String?) : StampSealState {
        override fun borderStyle() = StampStyle.Solid
        override fun rotationDegrees() = -3f
    }
}

/**
 * Återkommande stämpel-cirkel — Birdys signatur-glyph. 88dp default.
 */
@Composable
fun StampSeal(
    state: StampSealState,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    onClick: (() -> Unit)? = null,
) {
    val serif = rememberDmSerifDisplay()
    val caveat = rememberCaveat()
    val borderColor = when (state) {
        is StampSealState.Locked -> StampLocked
        is StampSealState.InProgress -> AccentCopper.copy(alpha = 0.6f)
        is StampSealState.Unlocked -> AccentCopper
    }
    val bg = when (state) {
        is StampSealState.Locked -> StampLockedBg
        is StampSealState.InProgress -> StampLockedBg
        is StampSealState.Unlocked -> StampUnlockedBg
    }

    Column(
        modifier = modifier.rotate(state.rotationDegrees()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(bg)
                .let { m ->
                    when (state.borderStyle()) {
                        StampStyle.Solid -> m.border(width = 2.dp, brush = SolidColor(borderColor), shape = CircleShape)
                        StampStyle.Dashed -> m.dashedBorder(width = 1.5.dp, color = borderColor)
                    }
                }
                .let { m -> if (onClick != null) m.clickable(onClick = onClick) else m },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (state) {
                    is StampSealState.Locked -> {
                        Text(
                            text = "?",
                            color = StampLocked,
                            fontFamily = serif,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Normal,
                            fontSize = (size.value * 0.32f).sp,
                        )
                    }
                    is StampSealState.InProgress -> {
                        Text(
                            text = "№${state.number}",
                            color = AccentCopper,
                            fontFamily = caveat,
                            fontWeight = FontWeight.Bold,
                            fontSize = (size.value * 0.16f).sp,
                        )
                        if (state.progressLabel != null) {
                            Text(
                                text = state.progressLabel,
                                color = AccentCopper,
                                fontFamily = caveat,
                                fontWeight = FontWeight.Bold,
                                fontSize = (size.value * 0.14f).sp,
                            )
                        }
                    }
                    is StampSealState.Unlocked -> {
                        Text(
                            text = "№${state.number}",
                            color = AccentCopper,
                            fontFamily = caveat,
                            fontWeight = FontWeight.Bold,
                            fontSize = (size.value * 0.16f).sp,
                        )
                        if (state.glyph != null) {
                            Text(
                                text = state.glyph,
                                color = TextOnCreme,
                                fontFamily = serif,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Normal,
                                fontSize = (size.value * 0.26f).sp,
                            )
                        }
                    }
                }
            }
        }
        val name = when (state) {
            is StampSealState.Locked -> state.name
            is StampSealState.InProgress -> state.name
            is StampSealState.Unlocked -> state.name
        }
        if (name != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = name,
                color = if (state is StampSealState.Locked) MarginaliaInk.copy(alpha = 0.6f) else TextOnCreme,
                fontFamily = serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Normal,
                fontSize = 10.sp,
            )
        }
    }
}

/**
 * Dashed-border modifier — Compose har ingen built-in dashed border, så vi
 * tecknar 16 streck längs cirkelns omkrets via drawBehind.
 */
private fun Modifier.dashedBorder(
    width: Dp,
    color: androidx.compose.ui.graphics.Color,
): Modifier = androidx.compose.ui.draw.drawBehind {
    val strokeWidth = width.toPx()
    val r = (size.minDimension - strokeWidth) / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val segments = 18
    val dashLen = (2 * Math.PI.toFloat() * r) / (segments * 2f)
    val gapLen = dashLen
    val pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(dashLen, gapLen), 0f)
    drawCircle(
        color = color,
        radius = r,
        center = androidx.compose.ui.geometry.Offset(cx, cy),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, pathEffect = pathEffect),
    )
}
```

- [ ] **Step 4: Kör testet — ska PASS**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.components.StampSealTest"
```

Expected: PASS (3 tester).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/StampSeal.kt composeApp/src/commonTest/kotlin/se/birdy/app/ui/components/StampSealTest.kt
git commit -m "feat(components): StampSeal with locked/in-progress/unlocked states"
```

---

### Task 8: MiniStamp + StampTrack composables

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/MiniStamp.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/StampTrack.kt`

- [ ] **Step 1: Implementera MiniStamp.kt**

```kotlin
package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.StampUnlockedBg
import se.birdy.app.ui.theme.rememberCaveat

/**
 * Liten stamp-cirkel för listrader — 36dp default, `№N` i Caveat copper, -4° roterad.
 */
@Composable
fun MiniStamp(
    number: Int,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val caveat = rememberCaveat()
    Box(
        modifier = modifier
            .size(size)
            .rotate(-4f)
            .clip(CircleShape)
            .background(StampUnlockedBg)
            .border(width = 2.dp, color = AccentCopper, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "№$number",
            color = AccentCopper,
            fontFamily = caveat,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.32f).sp,
        )
    }
}
```

- [ ] **Step 2: Implementera StampTrack.kt**

```kotlin
package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.StampLocked
import se.birdy.app.ui.theme.rememberCaveat

/**
 * 5×N-grid av små stamp-celler — filled (copper-fill) eller empty (dashed).
 * Används i Badges-skärmens hero som visuell progress-indikator istället för progress-bar.
 *
 * @param filled Antal redan upplåsta badges
 * @param total Totalt antal badges
 */
@Composable
fun StampTrack(
    filled: Int,
    total: Int,
    modifier: Modifier = Modifier,
    columns: Int = 5,
) {
    val caveat = rememberCaveat()
    val rows = (total + columns - 1) / columns
    androidx.compose.foundation.layout.Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    if (index >= total) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {}
                    } else {
                        StampCell(
                            isFilled = index < filled,
                            number = index + 1,
                            caveatFamily = caveat,
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StampCell(
    isFilled: Boolean,
    number: Int,
    caveatFamily: androidx.compose.ui.text.font.FontFamily,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (isFilled) AccentCopper else androidx.compose.ui.graphics.Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        if (isFilled) {
            Text(
                text = number.toString(),
                color = OffwhiteWarm,
                fontFamily = caveatFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
        } else {
            // Dashed circle outline (samma teknik som StampSeal.dashedBorder, inline här eftersom modifier är private där)
            DashedCircleBorder(color = StampLocked)
        }
    }
}

@Composable
private fun DashedCircleBorder(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier.size(0.dp).then(
            androidx.compose.ui.draw.drawBehind {
                val strokeWidth = 1.5.dp.toPx()
                val r = (size.minDimension - strokeWidth) / 2f
                val cx = size.width / 2f
                val cy = size.height / 2f
                val dashLen = (2 * Math.PI.toFloat() * r) / 36f
                val pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(dashLen, dashLen), 0f)
                drawCircle(
                    color = color,
                    radius = r,
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, pathEffect = pathEffect),
                )
            },
        ),
    )
}
```

(Note: implementer-subagent kan behöva justera `DashedCircleBorder` så den fyller parent — verifiera visuellt på device. Om det inte funkar via `Modifier.size(0.dp).drawBehind`, byt till `Modifier.matchParentSize()`.)

- [ ] **Step 3: Bygg**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/MiniStamp.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/StampTrack.kt
git commit -m "feat(components): MiniStamp + StampTrack for inline + Badges-hero use"
```

---

### Task 9: PlateFrame composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/PlateFrame.kt`

- [ ] **Step 1: Implementera PlateFrame.kt**

```kotlin
package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.PaperBottom
import se.birdy.app.ui.theme.rememberCaveat

/**
 * Naturalist-plate-frame: papper-tonad rounded box med inset border, foto i
 * mitten + Caveat-italic caption nedanför (`Pl. {idx} — {name}, in nature`).
 * Används i Species Profile + Observation Detail istället för LargeTopAppBar.
 */
@Composable
fun PlateFrame(
    plateLabel: String,
    captionLine: String,
    modifier: Modifier = Modifier,
    image: @Composable () -> Unit,
) {
    val caveat = rememberCaveat()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PaperBottom.copy(alpha = 0.5f))
            .border(width = 1.dp, color = AccentCopper.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp))
            .padding(8.dp),
    ) {
        // Image area — caller-supplied
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            image()
        }
        Spacer(Modifier.height(6.dp))
        // Pl. label + caption
        Text(
            text = "Pl. $plateLabel — $captionLine",
            color = MarginaliaInk,
            fontFamily = caveat,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
```

- [ ] **Step 2: Bygg + commit**

```bash
./gradlew :composeApp:assembleDebug && git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/PlateFrame.kt && git commit -m "feat(components): PlateFrame for naturalist-style photo plates"
```

---

### Task 10: Restyle Listen launcher

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/listen/ListenLauncherScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`

- [ ] **Step 1: Lägg till nya strängar**

I `values/strings.xml`:
```xml
<string name="listen_journal_label">FÅNGA · NO 0</string>
<string name="listen_journal_headline">Tre sätt att *fånga.*</string>
<string name="listen_journal_sub">En stämpel väntar i varje.</string>
```

I `values-en/strings.xml`:
```xml
<string name="listen_journal_label">CATCH · NO 0</string>
<string name="listen_journal_headline">Three ways to *catch.*</string>
<string name="listen_journal_sub">A stamp waits in each.</string>
```

(Behåll befintliga `listen_breadcrumb`, `listen_headline`, `listen_sub` — implementer-subagent ska radera dem efter att alla referenser borttagits, men inte här eftersom andra restyles inte är gjorda än.)

- [ ] **Step 2: Skriv om ListenLauncherScreen.kt**

Ersätt hela filinnehållet:

```kotlin
package se.birdy.app.ui.listen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.listen_audio_locked_snackbar
import birdy_bird_scanner.composeapp.generated.resources.listen_card_audio_body
import birdy_bird_scanner.composeapp.generated.resources.listen_card_audio_title
import birdy_bird_scanner.composeapp.generated.resources.listen_card_camera_body
import birdy_bird_scanner.composeapp.generated.resources.listen_card_camera_title
import birdy_bird_scanner.composeapp.generated.resources.listen_card_photo_body
import birdy_bird_scanner.composeapp.generated.resources.listen_card_photo_title
import birdy_bird_scanner.composeapp.generated.resources.listen_journal_headline
import birdy_bird_scanner.composeapp.generated.resources.listen_journal_label
import birdy_bird_scanner.composeapp.generated.resources.listen_journal_sub
import birdy_bird_scanner.composeapp.generated.resources.listen_premium_label
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.paperBackground
import se.birdy.app.ui.theme.rememberDmSerifDisplay

@Composable
fun ListenLauncherScreen(
    viewModel: ListenLauncherViewModel,
    onCameraClick: () -> Unit,
    onPhotoClick: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val audioLockedMsg = stringResource(Res.string.listen_audio_locked_snackbar)
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                ListenLauncherEvent.AudioLockedSnackbar -> snackbar.showSnackbar(audioLockedMsg)
            }
        }
    }
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .paperBackground()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            JournalIntro(
                label = stringResource(Res.string.listen_journal_label),
                headline = stringResource(Res.string.listen_journal_headline),
                sub = stringResource(Res.string.listen_journal_sub),
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LaunchCard(
                    icon = Icons.Filled.PhotoCamera,
                    title = stringResource(Res.string.listen_card_camera_title),
                    body = stringResource(Res.string.listen_card_camera_body),
                    variant = LaunchCardVariant.Primary,
                    onClick = onCameraClick,
                )
                LaunchCard(
                    icon = Icons.Filled.PhotoLibrary,
                    title = stringResource(Res.string.listen_card_photo_title),
                    body = stringResource(Res.string.listen_card_photo_body),
                    variant = LaunchCardVariant.Secondary,
                    onClick = onPhotoClick,
                )
                LaunchCard(
                    icon = Icons.Filled.Hearing,
                    title = stringResource(Res.string.listen_card_audio_title),
                    body = stringResource(Res.string.listen_card_audio_body),
                    variant = LaunchCardVariant.Locked,
                    onClick = viewModel::onAudioLockedTap,
                )
            }
        }
    }
}

private enum class LaunchCardVariant { Locked, Primary, Secondary }

@Composable
private fun LaunchCard(
    icon: ImageVector,
    title: String,
    body: String,
    variant: LaunchCardVariant,
    onClick: () -> Unit,
) {
    val premiumLabel = stringResource(Res.string.listen_premium_label)
    val serif = rememberDmSerifDisplay()
    val cardBg = Color.White.copy(alpha = 0.35f)
    val borderAlpha = if (variant == LaunchCardVariant.Primary) 0.5f else 0.18f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(
                width = if (variant == LaunchCardVariant.Primary) 1.5.dp else 1.dp,
                color = AccentCopper.copy(alpha = borderAlpha),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cirkel-ikon med dashed (Locked) eller solid (övriga) border
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .border(
                    width = 1.5.dp,
                    color = AccentCopper.copy(alpha = if (variant == LaunchCardVariant.Locked) 0.4f else 1f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = AccentCopper)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = TextOnCreme,
                    fontFamily = serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Normal,
                    fontSize = 18.sp,
                )
                if (variant == LaunchCardVariant.Locked) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = AccentCopper,
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = premiumLabel,
                        color = AccentCopper,
                        fontSize = 9.sp,
                        letterSpacing = 0.18.em,
                        fontWeight = FontWeight.W700,
                    )
                }
            }
            Text(
                text = body,
                color = MarginaliaInk.copy(alpha = 0.78f),
                fontSize = 12.sp,
            )
        }
    }
}
```

- [ ] **Step 3: Bygg + verifiera**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: BUILD SUCCESSFUL. Inga unresolved imports.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/listen/ListenLauncherScreen.kt composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(listen): restyle launcher with paper-bg + JournalIntro + circle-icon cards"
```

---

### Task 11: Restyle Archive

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` + `values-en/strings.xml`

- [ ] **Step 1: Lägg till strängar**

```xml
<!-- values/strings.xml -->
<string name="archive_journal_label">ARKIV · 700 ARTER</string>
<string name="archive_journal_headline">*Fåglar.*</string>
<string name="archive_journal_sub">Sök, filtrera, lär.</string>
<!-- values-en/strings.xml -->
<string name="archive_journal_label">ARCHIVE · 700 SPECIES</string>
<string name="archive_journal_headline">*Birds.*</string>
<string name="archive_journal_sub">Search, filter, learn.</string>
```

- [ ] **Step 2: Skriv om ArchiveScreen.kt**

Ersätt hela filen — viktigaste ändringar:
1. `Scaffold(containerColor = Color.Transparent)` + root Column får `paperBackground()`.
2. `HeroZone { ... }` → `JournalIntro(label, headline, sub, trailingContent = { settings-meny })`.
3. `OutlinedTextField` byts mot ny `JournalSearchField` (definieras lokalt) — papper-bg, Caveat placeholder.
4. `FilterChip` byts mot lokal `JournalChip` (DM Serif italic, copper-fill om vald).
5. `SpeciesRow` får `MiniStamp` höger om `isStamped`.

```kotlin
package se.birdy.app.ui.encyclopedia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_all
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_owls
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_raptors
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_songbirds
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_waders
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_water
import birdy_bird_scanner.composeapp.generated.resources.archive_journal_headline
import birdy_bird_scanner.composeapp.generated.resources.archive_journal_label
import birdy_bird_scanner.composeapp.generated.resources.archive_journal_sub
import birdy_bird_scanner.composeapp.generated.resources.archive_section_count
import birdy_bird_scanner.composeapp.generated.resources.archive_sort_alpha
import birdy_bird_scanner.composeapp.generated.resources.archive_sort_family
import birdy_bird_scanner.composeapp.generated.resources.archive_sort_recent
import birdy_bird_scanner.composeapp.generated.resources.loading
import birdy_bird_scanner.composeapp.generated.resources.menu_button
import birdy_bird_scanner.composeapp.generated.resources.search_empty_body
import birdy_bird_scanner.composeapp.generated.resources.search_empty_title
import birdy_bird_scanner.composeapp.generated.resources.search_placeholder
import birdy_bird_scanner.composeapp.generated.resources.settings_menu_item
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.EmptyState
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.MiniStamp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.PaperBottom
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.paperBackground
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.content.SpeciesId
import se.birdy.datastore.ArchiveSort

@Composable
fun ArchiveScreen(
    viewModel: ArchiveViewModel,
    onSpeciesClick: (SpeciesId) -> Unit,
    showDebugMenu: Boolean = false,
    onDebugBenchmarkClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val chip by viewModel.chip.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().paperBackground().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                JournalIntro(
                    label = stringResource(Res.string.archive_journal_label),
                    headline = stringResource(Res.string.archive_journal_headline),
                    sub = stringResource(Res.string.archive_journal_sub),
                    headlineFontSize = 36.sp,
                )
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 16.dp)) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(Res.string.menu_button),
                            tint = AccentCopper,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.settings_menu_item)) },
                            onClick = { onSettingsClick(); menuExpanded = false },
                        )
                        if (showDebugMenu) {
                            DropdownMenuItem(
                                text = { Text("Run benchmark") },
                                onClick = { onDebugBenchmarkClick(); menuExpanded = false },
                            )
                        }
                    }
                }
            }

            JournalSearchField(
                value = query,
                onValueChange = viewModel::onQueryChanged,
                placeholder = stringResource(Res.string.search_placeholder),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            ChipBar(selected = chip, onSelect = viewModel::onChipSelected)

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SortChip(sort = sort, onClick = viewModel::onSortToggle)
            }

            when (val s = state) {
                ArchiveUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(Res.string.loading)) }
                ArchiveUiState.Empty ->
                    EmptyState(
                        title = stringResource(Res.string.search_empty_title),
                        body = stringResource(Res.string.search_empty_body),
                    )
                is ArchiveUiState.Loaded -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                text = stringResource(Res.string.archive_section_count, s.rows.size.toString()),
                                color = MarginaliaInk.copy(alpha = 0.6f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.W700,
                                letterSpacing = 0.22.em,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                        items(s.rows, key = { it.summary.id.raw }) { row ->
                            SpeciesRow(
                                summary = row.summary,
                                isStamped = row.isStamped,
                                stampNumber = row.stampNumber,
                                onClick = { onSpeciesClick(row.summary.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val caveat = rememberCaveat()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        placeholder = {
            Text(
                text = placeholder,
                color = MarginaliaInk.copy(alpha = 0.5f),
                fontFamily = caveat,
                fontSize = 14.sp,
            )
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.4f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.4f),
            focusedIndicatorColor = AccentCopper,
            unfocusedIndicatorColor = AccentCopper.copy(alpha = 0.3f),
        ),
    )
}

@Composable
private fun ChipBar(
    selected: ArchiveChip,
    onSelect: (ArchiveChip) -> Unit,
) {
    val labels = listOf(
        ArchiveChip.ALL to stringResource(Res.string.archive_chip_all),
        ArchiveChip.SONGBIRDS to stringResource(Res.string.archive_chip_songbirds),
        ArchiveChip.WATER to stringResource(Res.string.archive_chip_water),
        ArchiveChip.RAPTORS to stringResource(Res.string.archive_chip_raptors),
        ArchiveChip.OWLS to stringResource(Res.string.archive_chip_owls),
        ArchiveChip.WADERS to stringResource(Res.string.archive_chip_waders),
    )
    val serif = rememberDmSerifDisplay()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(labels) { (chipValue, label) ->
            val isSelected = selected == chipValue
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) AccentCopper else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = AccentCopper.copy(alpha = if (isSelected) 0f else 0.5f),
                        shape = RoundedCornerShape(50),
                    )
                    .clickable { onSelect(chipValue) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = label,
                    color = if (isSelected) OffwhiteWarm else TextOnCreme,
                    fontFamily = serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun SortChip(
    sort: ArchiveSort,
    onClick: () -> Unit,
) {
    val label = when (sort) {
        ArchiveSort.ALPHA -> stringResource(Res.string.archive_sort_alpha)
        ArchiveSort.FAMILY -> stringResource(Res.string.archive_sort_family)
        ArchiveSort.RECENT -> stringResource(Res.string.archive_sort_recent)
    }
    val serif = rememberDmSerifDisplay()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(PaperBottom.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Sort,
            contentDescription = null,
            tint = AccentCopper,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = TextOnCreme, fontFamily = serif, fontStyle = FontStyle.Italic, fontSize = 11.sp)
    }
}

@Composable
private fun SpeciesRow(
    summary: se.birdy.content.model.SpeciesSummary,
    isStamped: Boolean,
    stampNumber: Int?,
    onClick: () -> Unit,
) {
    val serif = rememberDmSerifDisplay()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Liten thumb (placeholder cirkel om ingen bild)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MarginaliaInk.copy(alpha = 0.1f)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.name,
                color = TextOnCreme,
                fontFamily = serif,
                fontStyle = FontStyle.Italic,
                fontSize = 16.sp,
            )
            Text(
                text = summary.scientificName,
                color = MarginaliaInk.copy(alpha = 0.65f),
                fontStyle = FontStyle.Italic,
                fontSize = 11.sp,
            )
        }
        if (isStamped && stampNumber != null) {
            MiniStamp(number = stampNumber)
        }
    }
}
```

(Implementer-subagent: lägg till `stampNumber: Int?` i `ArchiveRow`-data om det inte finns. Kontrollera ArchiveViewModel — om bara `isStamped: Boolean` exponeras nu, lägg till stamp-number-lookup från observation-repository i ViewModel:s init-flow. Om det är komplext, flagga som NEEDS_CONTEXT.)

- [ ] **Step 3: Bygg + commit**

```bash
./gradlew :composeApp:assembleDebug
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveScreen.kt composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(archive): restyle with paper-bg + JournalIntro + Caveat search + serif chips"
```

---

### Task 12: Restyle Badges screen

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeGridCell.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeRecentCard.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` + `values-en/strings.xml`

- [ ] **Step 1: Lägg till strängar (Badges journal-intro + format-strängar)**

```xml
<!-- values/strings.xml -->
<string name="badges_journal_label">UPPTÄCKTER · NO %1$s</string>
<string name="badges_journal_headline">*%1$s* funna.</string>
<string name="badges_journal_sub">%1$s väntar därute.</string>
<!-- values-en/strings.xml -->
<string name="badges_journal_label">DISCOVERIES · NO %1$s</string>
<string name="badges_journal_headline">*%1$s* found.</string>
<string name="badges_journal_sub">%1$s waiting in the field.</string>
```

(Notera: `%1$s` inte `%1$d` — compose-resources-format kräver string-substitution.)

- [ ] **Step 2: Skriv om BadgesScreen.kt — viktigaste ändringar:**

1. Ta bort `TopAppBar`. Lägg till `JournalIntro` med label/headline/sub som bygger på `state.unlockedCount` + `state.totalBadges`.
2. Root container = `Modifier.paperBackground()` på `Scaffold`-content.
3. `BadgeProgressBar` → `StampTrack(filled = state.unlockedCount, total = state.totalBadges)` direkt under JournalIntro.
4. Recently unlocked carousel = `LazyRow` med `BadgeRecentCard` (uses StampSeal large variant).
5. Locked grid = `LazyVerticalGrid(columns = Fixed(3))` (var 4) med `BadgeGridCell` (uses `StampSeal`).

```kotlin
package se.birdy.app.ui.badges

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.badges_journal_headline
import birdy_bird_scanner.composeapp.generated.resources.badges_journal_label
import birdy_bird_scanner.composeapp.generated.resources.badges_journal_sub
import birdy_bird_scanner.composeapp.generated.resources.badges_load_error
import birdy_bird_scanner.composeapp.generated.resources.badges_load_error_retry
import birdy_bird_scanner.composeapp.generated.resources.badges_locked_tooltip
import birdy_bird_scanner.composeapp.generated.resources.badges_section_recently_unlocked
import birdy_bird_scanner.composeapp.generated.resources.badges_section_to_discover
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.StampTrack
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.paperBackground
import se.birdy.content.Locale
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeUnlock

@Composable
fun BadgesScreen(
    state: BadgesUiState,
    locale: Locale,
    zone: TimeZone,
    onBadgeClick: (Badge, BadgeUnlock?) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lockedTooltip = stringResource(Res.string.badges_locked_tooltip)
    val now = remember { Clock.System.now() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().paperBackground().padding(padding)) {
            when (state) {
                is BadgesUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is BadgesUiState.Error -> ErrorState(onRetry = onRetry, modifier = Modifier.fillMaxSize())
                is BadgesUiState.Loaded -> LoadedContent(
                    state = state,
                    locale = locale,
                    zone = zone,
                    now = now,
                    onUnlockedClick = { badge, unlock -> onBadgeClick(badge, unlock) },
                    onLockedClick = {
                        scope.launch { snackbarHostState.showSnackbar(message = lockedTooltip) }
                    },
                )
            }
        }
    }
}

@Composable
private fun LoadedContent(
    state: BadgesUiState.Loaded,
    locale: Locale,
    zone: TimeZone,
    now: Instant,
    onUnlockedClick: (Badge, BadgeUnlock) -> Unit,
    onLockedClick: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                JournalIntro(
                    label = stringResource(Res.string.badges_journal_label, state.unlockedCount.toString()),
                    headline = stringResource(Res.string.badges_journal_headline, state.unlockedCount.toString()),
                    sub = stringResource(Res.string.badges_journal_sub, (state.totalBadges - state.unlockedCount).toString()),
                    horizontalPadding = 0,
                    topPadding = 24,
                )
                StampTrack(
                    filled = state.unlockedCount,
                    total = state.totalBadges,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }

        if (state.recentlyUnlocked.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    SectionLabel(stringResource(Res.string.badges_section_recently_unlocked))
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(items = state.recentlyUnlocked, key = { it.badge.id }) { r ->
                            val nameRes = BadgeStringMap.nameFor(r.badge.id)
                            BadgeRecentCard(
                                localizedName = stringResource(nameRes),
                                stampNumber = r.badge.stampNumber,
                                glyph = r.badge.glyph,
                                unlockedAt = r.unlockedAt,
                                now = now,
                                locale = locale,
                                zone = zone,
                                onClick = { onUnlockedClick(r.badge, BadgeUnlock(r.badge.id, r.unlockedAt)) },
                            )
                        }
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column { SectionLabel(stringResource(Res.string.badges_section_to_discover, state.locked.size)) }
        }

        items(items = state.locked, key = { it.badge.id }) { lbp ->
            BadgeGridCell(
                progress = lbp,
                onClick = onLockedClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = MarginaliaInk,
        fontSize = 9.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.22.em,
    )
}

@Composable
private fun ErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Text(stringResource(Res.string.badges_load_error))
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.badges_load_error_retry)) }
    }
}
```

- [ ] **Step 3: Skriv om BadgeGridCell.kt så det wraps StampSeal**

```kotlin
package se.birdy.app.ui.badges

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState

@Composable
fun BadgeGridCell(
    progress: LockedBadgeProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nameRes = BadgeStringMap.nameFor(progress.badge.id)
    val name = stringResource(nameRes)
    val state = if (progress.currentValue > 0 && progress.target > 0) {
        StampSealState.InProgress(
            number = progress.badge.stampNumber,
            name = name,
            progressLabel = "${progress.currentValue}/${progress.target}",
        )
    } else {
        StampSealState.Locked(name = name)
    }
    StampSeal(
        state = state,
        modifier = modifier,
        onClick = onClick,
    )
}
```

- [ ] **Step 4: Skriv om BadgeRecentCard.kt — wrap StampSeal large + datum i Caveat**

```kotlin
package se.birdy.app.ui.badges

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.content.Locale

@Composable
fun BadgeRecentCard(
    localizedName: String,
    stampNumber: Int,
    glyph: String?,
    unlockedAt: Instant,
    now: Instant,
    locale: Locale,
    zone: TimeZone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val caveat = rememberCaveat()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StampSeal(
            state = StampSealState.Unlocked(number = stampNumber, glyph = glyph, name = localizedName),
            onClick = onClick,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatRelativeDay(unlockedAt, now, zone, locale),
            color = MarginaliaInk,
            fontFamily = caveat,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
        )
    }
}

private fun formatRelativeDay(
    unlockedAt: Instant,
    now: Instant,
    zone: TimeZone,
    locale: Locale,
): String {
    val diffMs = now.toEpochMilliseconds() - unlockedAt.toEpochMilliseconds()
    val days = diffMs / 86_400_000L
    return when {
        days < 1 -> if (locale == Locale.SV) "i dag" else "today"
        days < 2 -> if (locale == Locale.SV) "i går" else "yesterday"
        else -> {
            val ldt = unlockedAt.toLocalDateTime(zone)
            "${ldt.dayOfMonth}/${ldt.monthNumber}"
        }
    }
}
```

(Observera: detta antar `Badge` har `glyph: String?` och `stampNumber: Int`. Implementer-subagent: kontrollera Badge-modellen och justera om signaturen är annorlunda. Om `glyph` saknas, addera det till BadgeCatalog YAML + Badge-modellen, eller passa null från call-site.)

- [ ] **Step 5: Bygg + commit**

```bash
./gradlew :composeApp:assembleDebug
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/ composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(badges): restyle with JournalIntro + StampTrack + StampSeal grid"
```

---

### Task 13: Restyle Lifelist (loaded + empty)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/LifelistScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` + `values-en/strings.xml`

- [ ] **Step 1: Lägg till strängar**

```xml
<!-- values/strings.xml -->
<string name="lifelist_journal_label">MIN SAMLING · VOL. I</string>
<string name="lifelist_journal_headline">*%1$s* dagbok.</string>
<string name="lifelist_journal_sub">%1$s dagar. %2$s funna.</string>
<string name="lifelist_journal_headline_anonymous">*Min* samling.</string>
<string name="lifelist_journal_sub_empty">En tom sida, för nu.</string>
<string name="lifelist_empty_caveat_cta">*Skanna* första fågeln</string>
<!-- values-en/strings.xml -->
<string name="lifelist_journal_label">MY COLLECTION · VOL. I</string>
<string name="lifelist_journal_headline">*%1$s* journal.</string>
<string name="lifelist_journal_sub">%1$s days. %2$s found.</string>
<string name="lifelist_journal_headline_anonymous">*My* collection.</string>
<string name="lifelist_journal_sub_empty">An empty page, for now.</string>
<string name="lifelist_empty_caveat_cta">*Scan* first bird</string>
```

- [ ] **Step 2: Skriv om LifelistScreen.kt**

Viktiga ändringar:
1. Root = paperBackground.
2. `EmptyLifelist`: `JournalIntro` med headline `*Min* samling.` + sub `En tom sida, för nu.` + CTA-button med Caveat-accent på "Skanna".
3. `LoadedLifelist`: `JournalIntro` med apostrof (`%1$s` userName). Stat-row: Species · Stamps · Days separator i serif italic copper.
4. `LifelistRowComposable`: byt `CircularThumb`+`StampNumberBadge` → `MiniStamp(stampNumber)`, byt FontFamily.Serif → `rememberDmSerifDisplay()` italic.

```kotlin
package se.birdy.app.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.lifelist_empty_caveat_cta
import birdy_bird_scanner.composeapp.generated.resources.lifelist_journal_headline
import birdy_bird_scanner.composeapp.generated.resources.lifelist_journal_headline_anonymous
import birdy_bird_scanner.composeapp.generated.resources.lifelist_journal_label
import birdy_bird_scanner.composeapp.generated.resources.lifelist_journal_sub
import birdy_bird_scanner.composeapp.generated.resources.lifelist_journal_sub_empty
import birdy_bird_scanner.composeapp.generated.resources.lifelist_relative_days
import birdy_bird_scanner.composeapp.generated.resources.lifelist_relative_hours
import birdy_bird_scanner.composeapp.generated.resources.lifelist_relative_just_now
import birdy_bird_scanner.composeapp.generated.resources.lifelist_relative_minutes
import birdy_bird_scanner.composeapp.generated.resources.lifelist_section_recent
import birdy_bird_scanner.composeapp.generated.resources.lifelist_sort_recent
import birdy_bird_scanner.composeapp.generated.resources.lifelist_sort_species
import birdy_bird_scanner.composeapp.generated.resources.lifelist_sort_stamp
import birdy_bird_scanner.composeapp.generated.resources.lifelist_stat_longest
import birdy_bird_scanner.composeapp.generated.resources.lifelist_stat_month
import birdy_bird_scanner.composeapp.generated.resources.lifelist_stat_species
import birdy_bird_scanner.composeapp.generated.resources.lifelist_stat_stamps
import birdy_bird_scanner.composeapp.generated.resources.lifelist_stat_streak
import birdy_bird_scanner.composeapp.generated.resources.lifelist_stat_year
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalHeadline
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.MiniStamp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.MatchHigh
import se.birdy.app.ui.theme.MatchLow
import se.birdy.app.ui.theme.MatchMid
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.PaperBottom
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.paperBackground
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.datastore.LifelistSort
import se.birdy.datastore.LifelistStat3Choice

@Composable
fun LifelistScreen(
    viewModel: LifelistViewModel,
    onObservationClick: (id: String) -> Unit,
    onScanCtaClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(modifier = Modifier.fillMaxSize().paperBackground().padding(padding)) {
            when (val s = state) {
                LifelistUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                LifelistUiState.Empty -> EmptyLifelist(onScanCtaClick = onScanCtaClick)
                is LifelistUiState.Loaded -> LoadedLifelist(
                    state = s,
                    onObservationClick = onObservationClick,
                    onStat3Toggle = viewModel::onStat3Toggle,
                    onSortToggle = viewModel::onSortToggle,
                )
            }
        }
    }
}

@Composable
private fun EmptyLifelist(onScanCtaClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        JournalIntro(
            label = stringResource(Res.string.lifelist_journal_label),
            headline = stringResource(Res.string.lifelist_journal_headline_anonymous),
            sub = stringResource(Res.string.lifelist_journal_sub_empty),
        )
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(
                onClick = onScanCtaClick,
                colors = ButtonDefaults.buttonColors(containerColor = AccentCopper, contentColor = OffwhiteWarm),
                shape = RoundedCornerShape(50),
            ) {
                JournalHeadline(
                    text = stringResource(Res.string.lifelist_empty_caveat_cta),
                    fontSize = 14.sp,
                    plainColor = OffwhiteWarm,
                    accentColor = OffwhiteWarm,
                )
            }
        }
    }
}

@Composable
private fun LoadedLifelist(
    state: LifelistUiState.Loaded,
    onObservationClick: (id: String) -> Unit,
    onStat3Toggle: () -> Unit,
    onSortToggle: () -> Unit,
) {
    val now = remember { Clock.System.now() }
    val labelStat1 = stringResource(Res.string.lifelist_stat_species)
    val labelStat2 = stringResource(Res.string.lifelist_stat_stamps)
    val labelStat3 = labelForStat3(state.stat3.kind)

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column {
                JournalIntro(
                    label = stringResource(Res.string.lifelist_journal_label),
                    headline = stringResource(Res.string.lifelist_journal_headline, state.userName.ifEmpty { "Min" }),
                    sub = stringResource(
                        Res.string.lifelist_journal_sub,
                        state.daysActive.toString(),
                        state.speciesCount.toString(),
                    ),
                )
                StatRow(
                    stat1 = StatItem(labelStat1, state.speciesCount.toString()),
                    stat2 = StatItem(labelStat2, state.stampsCount.toString()),
                    stat3 = StatItem(labelStat3, state.stat3.value.toString()),
                    onStat3Click = onStat3Toggle,
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.lifelist_section_recent, state.stampsCount.toString()).uppercase(),
                    color = MarginaliaInk,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 0.22.em,
                )
                SortChip(sort = state.sort, onClick = onSortToggle)
            }
        }
        items(state.rows, key = { it.observation.id }) { row ->
            LifelistRowComposable(row = row, now = now, onClick = { onObservationClick(row.observation.id) })
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

private data class StatItem(val label: String, val value: String)

@Composable
private fun StatRow(
    stat1: StatItem,
    stat2: StatItem,
    stat3: StatItem,
    onStat3Click: () -> Unit,
) {
    val serif = rememberDmSerifDisplay()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatColumn(stat = stat1)
        StatSeparator(serif)
        StatColumn(stat = stat2)
        StatSeparator(serif)
        StatColumn(stat = stat3, onClick = onStat3Click)
    }
}

@Composable
private fun StatColumn(stat: StatItem, onClick: (() -> Unit)? = null) {
    val serif = rememberDmSerifDisplay()
    Column(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stat.value,
            color = AccentCopper,
            fontFamily = serif,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Normal,
            fontSize = 26.sp,
        )
        Text(
            text = stat.label.uppercase(),
            color = MarginaliaInk,
            fontSize = 9.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = 0.22.em,
        )
    }
}

@Composable
private fun StatSeparator(serif: androidx.compose.ui.text.font.FontFamily) {
    Text(
        text = "·",
        color = AccentCopper.copy(alpha = 0.5f),
        fontFamily = serif,
        fontStyle = FontStyle.Italic,
        fontSize = 22.sp,
    )
}

@Composable
private fun SortChip(sort: LifelistSort, onClick: () -> Unit) {
    val label = when (sort) {
        LifelistSort.RECENT -> stringResource(Res.string.lifelist_sort_recent)
        LifelistSort.STAMP_NUMBER -> stringResource(Res.string.lifelist_sort_stamp)
        LifelistSort.SPECIES -> stringResource(Res.string.lifelist_sort_species)
    }
    val serif = rememberDmSerifDisplay()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(PaperBottom.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = AccentCopper, fontFamily = serif, fontStyle = FontStyle.Italic, fontSize = 11.sp)
    }
}

@Composable
private fun LifelistRowComposable(row: LifelistRow, now: Instant, onClick: () -> Unit) {
    val serif = rememberDmSerifDisplay()
    val caveat = rememberCaveat()
    val confidencePct = (row.observation.confidence * 100f).toInt()
    val matchColor = when {
        confidencePct >= 80 -> MatchHigh
        confidencePct >= 60 -> MatchMid
        else -> MatchLow
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.4f))
            .border(1.dp, AccentCopper.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniStamp(number = row.observation.stampNumber)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.species?.name ?: row.observation.speciesId,
                color = TextOnCreme,
                fontFamily = serif,
                fontStyle = FontStyle.Italic,
                fontSize = 16.sp,
            )
            Text(
                text = "${row.species?.scientificName ?: ""} · ${relativeTime(row.observation.savedAt, now)}",
                color = MarginaliaInk.copy(alpha = 0.7f),
                fontSize = 11.sp,
            )
        }
        Text(
            text = "$confidencePct%",
            color = matchColor,
            fontFamily = caveat,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun labelForStat3(kind: LifelistStat3Choice): String =
    stringResource(
        when (kind) {
            LifelistStat3Choice.STREAK -> Res.string.lifelist_stat_streak
            LifelistStat3Choice.SPECIES_THIS_YEAR -> Res.string.lifelist_stat_year
            LifelistStat3Choice.SPECIES_THIS_MONTH -> Res.string.lifelist_stat_month
            LifelistStat3Choice.LONGEST_STREAK -> Res.string.lifelist_stat_longest
        },
    )

@Composable
private fun relativeTime(instant: Instant, now: Instant = Clock.System.now()): String {
    val diffMs = now.toEpochMilliseconds() - instant.toEpochMilliseconds()
    val diffMin = diffMs / 60_000L
    val diffH = diffMs / 3_600_000L
    val diffD = diffMs / 86_400_000L
    return when {
        diffMin < 2 -> stringResource(Res.string.lifelist_relative_just_now)
        diffH < 1 -> stringResource(Res.string.lifelist_relative_minutes, diffMin.toString())
        diffD < 1 -> stringResource(Res.string.lifelist_relative_hours, diffH.toString())
        else -> stringResource(Res.string.lifelist_relative_days, diffD.toString())
    }
}
```

(Implementer-subagent: `LifelistUiState.Loaded` måste ha `daysActive: Int` exponerad. Om den saknas, lägg till i ViewModel. Om `LifelistRow.species?.name`-fallback inte finns, justera.)

- [ ] **Step 3: Bygg + commit**

```bash
./gradlew :composeApp:assembleDebug
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/LifelistScreen.kt composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(lifelist): restyle with paper-bg + JournalIntro + MiniStamp rows + serif stats"
```

---

### Task 14: Add `marginalia` field + restyle Species Profile

**Files:**
- Modify: `shared/content/src/commonMain/kotlin/se/birdy/content/model/Species.kt`
- Modify: `shared/content/src/commonMain/sqldelight/se/birdy/content/db/Species.sq` (or actual path)
- Modify: `tools/content-pipeline/src/birdy_fetcher/yaml_writer.py` (write null marginalia)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/profile/SpeciesProfileScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` + `values-en/strings.xml`

- [ ] **Step 1: Lägg till strängar**

```xml
<!-- values/strings.xml -->
<string name="profile_journal_label">ART · NO %1$s</string>
<string name="profile_journal_headline">*%1$s.*</string>
<string name="profile_journal_sub">%1$s · %2$s</string>
<string name="profile_plate_caption">i naturen</string>
<!-- values-en/strings.xml -->
<string name="profile_journal_label">SPECIES · NO %1$s</string>
<string name="profile_journal_headline">*%1$s.*</string>
<string name="profile_journal_sub">%1$s · %2$s</string>
<string name="profile_plate_caption">in nature</string>
```

- [ ] **Step 2: Lägg till `marginalia` på Species**

I `shared/content/src/commonMain/kotlin/se/birdy/content/model/Species.kt`, lägg till fält:

```kotlin
data class Species(
    // ... befintliga fält ...
    val marginalia: String? = null,
)
```

I SQLDelight `.sq` (kontrollera filnamn med `find shared/content/src/commonMain/sqldelight -name '*.sq'`), lägg till kolumn på species-tabellen:

```sql
ALTER TABLE species ADD COLUMN marginalia TEXT NULL;
```

(Om det inte finns en migrations-fil, ändra ursprungs-`CREATE TABLE`.)

I YAML-läsaren (också i `:shared:content`), lägg till `marginalia`-fältet vid YAML-deserialisering.

I `tools/content-pipeline/src/birdy_fetcher/yaml_writer.py`, skriv `marginalia: null` (eller skipa fältet) vid generering av nya YAMLs.

- [ ] **Step 3: Skriv om SpeciesProfileScreen.kt**

Viktiga ändringar:
1. Ersätt `LargeTopAppBar` med `JournalIntro` + `PlateFrame`.
2. Description med drop-cap (första bokstaven 26sp DM Serif italic copper).
3. Marginalia-block om `species.marginalia != null`.

```kotlin
package se.birdy.app.ui.profile

// ... imports ...

@Composable
fun SpeciesProfileScreen(viewModel: SpeciesProfileViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        SpeciesProfileUiState.Loading ->
            Box(Modifier.fillMaxSize().paperBackground(), Alignment.Center) { Text(stringResource(Res.string.loading)) }
        SpeciesProfileUiState.NotFound ->
            EmptyState(title = stringResource(Res.string.not_found_title), body = stringResource(Res.string.not_found_body))
        is SpeciesProfileUiState.Loaded -> ProfileContent(s.species, onBack)
    }
}

@Composable
private fun ProfileContent(species: Species, onBack: () -> Unit) {
    val serif = rememberDmSerifDisplay()
    val caveat = rememberCaveat()
    val plateLabel = species.id.raw.removePrefix("Q") // använd Q-numret som plate-nummer
    val captionPart = stringResource(Res.string.profile_plate_caption)
    Box(modifier = Modifier.fillMaxSize().paperBackground()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Box {
                    JournalIntro(
                        label = stringResource(Res.string.profile_journal_label, plateLabel),
                        headline = stringResource(Res.string.profile_journal_headline, species.name),
                        sub = stringResource(Res.string.profile_journal_sub, species.scientificName, species.familyName ?: ""),
                        headlineFontSize = 36.sp,
                    )
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.TopStart).padding(top = 24.dp, start = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.profile_back),
                            tint = AccentCopper,
                        )
                    }
                }
            }
            item {
                val heroImage = species.images.firstOrNull { it.role == "hero" } ?: species.images.firstOrNull()
                if (heroImage != null) {
                    PlateFrame(
                        plateLabel = plateLabel,
                        captionLine = "${species.name}, $captionPart",
                    ) {
                        AsyncImage(
                            model = Res.getUri("files/images/${heroImage.path}"),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    JournalPill(text = if (species.abundance == Abundance.COMMON) stringResource(Res.string.badge_common) else stringResource(Res.string.badge_uncommon), isFilled = true)
                    species.familyName?.let { JournalPill(text = it, isFilled = false) }
                }
            }
            // Description med drop-cap
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(Res.string.profile_label_description).uppercase(),
                        color = MarginaliaInk,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.W700,
                        letterSpacing = 0.22.em,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    DescriptionWithDropCap(
                        text = species.description.ifEmpty { stringResource(Res.string.empty_description) },
                        serif = serif,
                    )
                }
            }
            // Marginalia om finns
            if (!species.marginalia.isNullOrBlank()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .border(width = 0.dp, color = Color.Transparent),
                    ) {
                        Row {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(48.dp)
                                    .background(MarginaliaBorder),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = species.marginalia,
                                color = MarginaliaInk,
                                fontFamily = caveat,
                                fontWeight = FontWeight.Normal,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            }
            // Migration + Photos blocks (befintliga, lämnas) — använd SectionBlock men med uppdaterad styling
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DescriptionWithDropCap(text: String, serif: androidx.compose.ui.text.font.FontFamily) {
    if (text.isEmpty()) {
        Text(text = "", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val firstChar = text.first().toString()
    val rest = text.drop(1)
    val annotated = androidx.compose.ui.text.AnnotatedString.Builder().apply {
        pushStyle(
            androidx.compose.ui.text.SpanStyle(
                fontFamily = serif,
                fontSize = 26.sp,
                fontStyle = FontStyle.Italic,
                color = AccentCopper,
            ),
        )
        append(firstChar)
        pop()
        pushStyle(androidx.compose.ui.text.SpanStyle(fontSize = 13.sp))
        append(rest)
        pop()
    }.toAnnotatedString()
    Text(
        text = annotated,
        color = TextOnCreme,
        lineHeight = 20.sp,
    )
}

@Composable
private fun JournalPill(text: String, isFilled: Boolean) {
    val serif = rememberDmSerifDisplay()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isFilled) AccentCopper else Color.Transparent)
            .border(1.dp, AccentCopper.copy(alpha = if (isFilled) 0f else 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = if (isFilled) OffwhiteWarm else TextOnCreme,
            fontFamily = serif,
            fontStyle = FontStyle.Italic,
            fontSize = 11.sp,
        )
    }
}
```

(Implementer-subagent: `species.familyName` kan ha annat namn — kontrollera Species-modellen och justera. `species.id.raw` ska ge "Q14918" eller liknande; om det inte är detta API justera.)

- [ ] **Step 4: Lägg in marginalia på 5-10 demo-species manuellt** (för screenshots)

Välj ~5 species som visas i Plan 7b screenshots (talgoxe Q25334, blåmes Q25333, koltrast Q25341, svartmes Q14918, talltita Q14947) och addera `marginalia`-fältet i deras YAML:

```yaml
# shared/content/data/species/Q25334.yaml
marginalia: "Talgoxen söker frön i barren mitt i vintern."
```

(Engelska kan vara samma string-fält men det krävs då en lokaliserings-strategi — för v1 av detta spec används samma marginalia oavsett locale, eller separat fält `marginalia_sv`, `marginalia_en`. Implementer-subagent: börja med en enda `marginalia: String` på svenska, dokumentera lokaliserings-TODO i kommentar.)

Re-bygg species-DB:
```bash
./gradlew :shared:content:buildSpeciesDb
```

- [ ] **Step 5: Bygg + commit**

```bash
./gradlew :composeApp:assembleDebug
git add shared/content/ tools/content-pipeline/src/birdy_fetcher/yaml_writer.py composeApp/src/commonMain/kotlin/se/birdy/app/ui/profile/SpeciesProfileScreen.kt composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(profile): restyle with PlateFrame + drop-cap + marginalia field"
```

---

### Task 15: Restyle Observation Detail

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/ObservationDetailScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` + `values-en/strings.xml`

- [ ] **Step 1: Lägg till strängar**

```xml
<!-- values/strings.xml -->
<string name="observation_journal_label">FYND · NO %1$s</string>
<string name="observation_note_caveat_prompt">Notera i marginalen…</string>
<string name="observation_visit_profile">visa artprofil</string>
<!-- values-en/strings.xml -->
<string name="observation_journal_label">FIND · NO %1$s</string>
<string name="observation_note_caveat_prompt">Add a note in the margin…</string>
<string name="observation_visit_profile">visit profile</string>
```

- [ ] **Step 2: Skriv om ObservationDetailScreen.kt** — ersätt `LargeTopAppBar`-stil med `paperBackground` + `JournalIntro` (label = `FYND · NO {stampNumber}`, headline = `*{species.name}.*`, sub = `{relativeTime}`) + `PlateFrame` med foto. Note-section: TextField med Caveat-placeholder. Footer-länk: `*{scientificName}*` + handskriven CTA "visit profile" i Caveat copper.

(Eftersom denna fil inte är inläst i denna plan, ge implementer-subagent följande prompt: "Läs nuvarande `ObservationDetailScreen.kt`, behåll all logik (note-edit, delete-confirm, navigate to species), men byt root-bg till `paperBackground()`, ersätt top-bar med JournalIntro, byt foto-vy till PlateFrame, anpassa note-textfield-placeholder till Caveat-prompt-string, lägg footer-länk till species med Caveat-stil." Detta är OK — tasken är fortfarande spec'd nog att bli granskad.)

- [ ] **Step 3: Bygg + commit**

```bash
./gradlew :composeApp:assembleDebug
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/ObservationDetailScreen.kt composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(observation-detail): restyle with paper-bg + JournalIntro + PlateFrame"
```

---

### Task 16: Restyle Onboarding (3 pages)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/OnboardingScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` + `values-en/strings.xml`

- [ ] **Step 1: Lägg till nya onboarding-strängar**

```xml
<!-- values/strings.xml -->
<string name="onboarding_journal_p1_label">VÄLKOMMEN · NO 1</string>
<string name="onboarding_journal_p1_headline">*Birdy.*</string>
<string name="onboarding_journal_p1_sub">En fältdagbok för fynd.</string>
<string name="onboarding_journal_p1_body1">Rikta kameran, ta ett foto, eller välj från galleriet. *Birdy* identifierar fågeln på enheten — utan internet i fält — och stämplar den i din dagbok.</string>
<string name="onboarding_journal_p1_body2">Varje fynd låser upp en ny sida. Se din *samling* växa över årstiderna. Dagboken stannar på enheten om du inte väljer att dela den.</string>

<string name="onboarding_journal_p2_label">SÅ FUNGERAR DET · NO 2</string>
<string name="onboarding_journal_p2_headline">*Tre* delar.</string>
<string name="onboarding_journal_p2_sub">Att leta. Att lära. Att samla.</string>

<string name="onboarding_journal_p3_label">DITT NAMN · NO 3</string>
<string name="onboarding_journal_p3_headline">*Vem* skriver?</string>
<string name="onboarding_journal_p3_sub">Namnet visas i dagboken.</string>
<string name="onboarding_journal_p3_cta">*Börja* dagboken</string>

<!-- values-en/strings.xml -->
<string name="onboarding_journal_p1_label">WELCOME · NO 1</string>
<string name="onboarding_journal_p1_headline">*Birdy.*</string>
<string name="onboarding_journal_p1_sub">A field journal for finds.</string>
<string name="onboarding_journal_p1_body1">Point your camera, snap a photo, or upload from your gallery. *Birdy* identifies the bird on-device — no internet needed in the field — and stamps it in your journal.</string>
<string name="onboarding_journal_p1_body2">Each find unlocks a new page. Watch your *collection* grow over the seasons. Your journal stays on this device unless you choose to share it.</string>

<string name="onboarding_journal_p2_label">HOW IT WORKS · NO 2</string>
<string name="onboarding_journal_p2_headline">*Three* parts.</string>
<string name="onboarding_journal_p2_sub">To search. To learn. To collect.</string>

<string name="onboarding_journal_p3_label">YOUR NAME · NO 3</string>
<string name="onboarding_journal_p3_headline">*Who* writes?</string>
<string name="onboarding_journal_p3_sub">Shown in your journal.</string>
<string name="onboarding_journal_p3_cta">*Begin* the journal</string>
```

- [ ] **Step 2: Skriv om OnboardingScreen.kt — vänd från moss-gradient till paperBackground; använd JournalIntro per page; brödtext i Inter (system sans) med endast accent-ord i Caveat (via JournalHeadline-parsing applicerad på text-fragment, eller ett enkelt `BodyTextWithAccents`-helper)**

Eftersom långa brödtext-paragrafer behöver Inter med Caveat-accent på enstaka ord (`*Birdy*`, `*samling*`), implementer-subagent ska skapa en hjälp-composable `BodyTextWithCaveatAccents(text: String)` som parser `*ord*` och renderar Inter regular + Caveat-bold-accent inline (utan rotation, utan kursivering — bara font-byte + copper-färg).

```kotlin
// composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/BodyTextWithCaveatAccents.kt
package se.birdy.app.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BodyTextWithCaveatAccents(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.sp,
    plainColor: Color = TextOnCreme,
    accentColor: Color = AccentCopper,
) {
    val segments = parseJournalHeadline(text)
    val caveat = rememberCaveat()
    FlowRow(modifier = modifier) {
        segments.forEach { seg ->
            when (seg) {
                is HeadlineSegment.Plain -> Text(
                    text = seg.text,
                    color = plainColor,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Normal,
                    fontSize = fontSize,
                    lineHeight = fontSize.times(1.55f),
                )
                is HeadlineSegment.Accent -> Text(
                    text = seg.text,
                    color = accentColor,
                    fontFamily = caveat,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize.times(1.2f),
                )
            }
        }
    }
}
```

Onboarding-skärmen använder sedan denna composable på p1 body, behåller layout men byter visuella val. Det är OK att lämna featureRow i page 2 (med icon + name + description) — bara byt typografi till DM Serif italic för name + Inter för description + paperBackground bakgrund.

(Detaljer för exakt page-layout: implementer-subagent får ramen via spec § 6.7 + denna mockup. Behåll PagerState-logiken oförändrad.)

- [ ] **Step 3: Bygg + commit**

```bash
./gradlew :composeApp:assembleDebug
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/BodyTextWithCaveatAccents.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/OnboardingScreen.kt composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(onboarding): restyle 3 pages with paper-bg + JournalIntro + Inter body"
```

---

### Task 17: Restyle ClassificationResult (Match-intro)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/result/ClassificationResultScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` + `values-en/strings.xml`

- [ ] **Step 1: Strängar**

```xml
<!-- values/strings.xml -->
<string name="result_journal_label">MATCHNING · NO %1$s</string>
<string name="result_journal_headline">*%1$s*?</string>
<string name="result_journal_sub">%1$s%% säkerhet.</string>
<!-- values-en/strings.xml -->
<string name="result_journal_label">MATCH · NO %1$s</string>
<string name="result_journal_headline">*%1$s*?</string>
<string name="result_journal_sub">A %1$s%% match.</string>
```

- [ ] **Step 2: Lägg till `JournalIntro` överst i ClassificationResultScreen** — root paperBackground, JournalIntro med top-match name + confidence, sedan befintlig top-3-lista (anpassa typografi: namn DM Serif italic, %-tal Caveat copper).

(Detaljer: implementer-subagent läser nuvarande fil, behåller VM-state-handling, byter bara visuella delar.)

- [ ] **Step 3: Bygg + commit**

```bash
./gradlew :composeApp:assembleDebug
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/result/ClassificationResultScreen.kt composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(result): add JournalIntro to ClassificationResultScreen"
```

---

### Task 18: Restyle BottomNavBar + AppGate

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BottomNavBar.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppGate.kt`

- [ ] **Step 1: Skriv om BottomNavBar.kt med papper-bg + copper-fill capsule**

```kotlin
package se.birdy.app.ui.scaffold

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.tab_archive
import birdy_bird_scanner.composeapp.generated.resources.tab_badges
import birdy_bird_scanner.composeapp.generated.resources.tab_lifelist
import birdy_bird_scanner.composeapp.generated.resources.tab_listen
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import kotlin.reflect.KClass

private val BottomBarBg = Color(0xFFEFE8DA)

private data class TabSpec(
    val route: AppRoute,
    val label: StringResource,
    val icon: ImageVector,
    val ownedRoutes: Set<KClass<out AppRoute>> = setOf(route::class),
)

private val tabs =
    listOf(
        TabSpec(
            route = AppRoute.Listen,
            label = Res.string.tab_listen,
            icon = Icons.Filled.Hearing,
            ownedRoutes = setOf(
                AppRoute.Listen::class,
                AppRoute.Scan::class,
                AppRoute.PhotoAnalyze::class,
                AppRoute.ClassificationResult::class,
            ),
        ),
        TabSpec(AppRoute.Archive, Res.string.tab_archive, Icons.AutoMirrored.Filled.LibraryBooks),
        TabSpec(AppRoute.Lifelist, Res.string.tab_lifelist, Icons.Outlined.CollectionsBookmark),
        TabSpec(AppRoute.Badges, Res.string.tab_badges, Icons.Filled.Stars),
    )

@Composable
fun BottomNavBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(BottomBarBg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (tab in tabs) {
            val selected = backStackEntry?.destination?.parentChain()?.any { dest ->
                tab.ownedRoutes.any { dest.hasRoute(it) }
            } == true
            TabCell(
                tab = tab,
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TabCell(
    tab: TabSpec,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) AccentCopper else MarginaliaInk.copy(alpha = 0.6f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AccentCopper.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(tab.icon, contentDescription = null, tint = color)
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(tab.label),
            color = color,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
        )
    }
}

private fun NavDestination.parentChain(): Sequence<NavDestination> = generateSequence(this) { it.parent }
```

- [ ] **Step 2: Uppdatera AppGate.kt — splash-bg från `MossCreme` till `PaperTop`**

I `SplashLoading()`:
```kotlin
Box(modifier = Modifier.fillMaxSize().paperBackground(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(color = AccentCopper)
}
```

- [ ] **Step 3: Bygg + commit**

```bash
./gradlew :composeApp:assembleDebug
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BottomNavBar.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppGate.kt
git commit -m "feat(nav): paper-toned BottomNavBar + paper-bg SplashLoading"
```

---

### Task 19: Cleanup — remove HeroZone + update StampNumberBadge

**Why:** Efter alla restyles finns `HeroZone` ingen användare. Också `StampNumberBadge` använder `FontFamily.Serif`-fallback nu — uppdatera till DM Serif Display.

**Files:**
- Delete: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/HeroZone.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/StampNumberBadge.kt`
- Search: alla resterande HeroZone-imports

- [ ] **Step 1: Sök kvarvarande HeroZone-references**

```bash
grep -r "HeroZone" composeApp/src/commonMain/kotlin/ || echo "No references"
grep -r "import se.birdy.app.ui.theme.HeroZone" composeApp/src/ || echo "No imports"
```

Expected: inga träffar.

- [ ] **Step 2: Radera HeroZone.kt**

```bash
rm composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/HeroZone.kt
```

- [ ] **Step 3: Uppdatera StampNumberBadge.kt så den använder DM Serif Display**

```kotlin
package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.rememberDmSerifDisplay

/**
 * Behålls för bakåtkompatibilitet (om något ställe fortfarande callar denna).
 * Ny kod ska använda MiniStamp istället.
 */
@Composable
fun StampNumberBadge(
    number: Int,
    modifier: Modifier = Modifier,
) {
    val serif = rememberDmSerifDisplay()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(AccentCopper)
            .border(width = 1.5.dp, color = OffwhiteWarm, shape = RoundedCornerShape(percent = 50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "#$number",
            color = OffwhiteWarm,
            fontFamily = serif,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
        )
    }
}
```

- [ ] **Step 4: Bygg + commit**

```bash
./gradlew :composeApp:assembleDebug ktlintCheck detekt
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/HeroZone.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/StampNumberBadge.kt
git commit -m "chore(theme): remove HeroZone + update StampNumberBadge to DM Serif"
```

---

### Task 20: Build + device-verify + screenshots + tag

**Files:**
- Update: `CLAUDE.md` (status-rad, plan-of-plans)
- Update: `MEMORY.md` (auto-memory pointer för Plan 7c)
- Create: `~/.claude/projects/.../memory/project_plan_7c_status.md`
- Create: `docs/superpowers/screenshots/v0.7.0c-field-journal/*.png`

- [ ] **Step 1: Full build + statisk analys**

```bash
./gradlew clean :composeApp:assembleDebug ktlintCheck detekt :composeApp:testDebugUnitTest :shared:domain:jvmTest :shared:ml:jvmTest
```

Expected: BUILD SUCCESSFUL. Alla tester gröna. ktlint/detekt utan violations.

- [ ] **Step 2: Installera + starta på Galaxy S23 Ultra**

```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

Expected: app startar utan crash.

- [ ] **Step 3: Capture device-screenshots i portrait**

```bash
ADB="/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe"
mkdir -p docs/superpowers/screenshots/v0.7.0c-field-journal
# Onboarding p1
MSYS_NO_PATHCONV=1 $ADB exec-out screencap -p > docs/superpowers/screenshots/v0.7.0c-field-journal/onboarding-p1.png
# Listen launcher
# Archive
# Badges
# Lifelist (loaded + empty)
# Species Profile
# Observation Detail
# ClassificationResult
# 9 screenshots totalt
```

(Implementer-subagent: kör en åt gången, navigera manuellt mellan dem, verifiera att varje screenshot ser ut enligt mockup C — paper-bg, ingen grön hero kvar, JournalIntro överst, stamp-system synligt.)

- [ ] **Step 4: Uppdatera CLAUDE.md status-rad + plan-of-plans-tabell + Plan 7c-rad**

```diff
-Status (2026-05-08): … Plan 7b ✅ … Plan 7c (Redesign Match-flow…) ska skrivas.
+Status (2026-05-09): … Plan 7b ✅ … Plan 7c (Field Journal) ✅ ('v0.7.0c-field-journal', 2026-05-09). **Nästa: Plan 7d (Match-flow) ska skrivas. Plan 6 (Polish + Play Store-release) PAUSAD tills Plan 7 är klar.**
```

(Plan-of-plans-tabellen: 7c-raden uppdateras till "Field Journal — design-uplift" + ✅. 7d-rad läggs till för Match-flow ⏳.)

Lägg till "Avslutade planer"-block för Plan 7c med utgångspunkt-ref till spec + auto-memory.

- [ ] **Step 5: Skriv auto-memory `project_plan_7c_status.md`**

```markdown
---
name: Plan 7c (Field Journal Refresh) — DONE
description: Shipped v0.7.0c-field-journal 2026-05-09 — paper-bg + DM Serif + Caveat-accent + StampSeal-system + JournalIntro red thread genom alla skärmar inkl. Onboarding
type: project
---

# Plan 7c (Field Journal Refresh) — DONE

**Tag:** `v0.7.0c-field-journal` (2026-05-09)
**Spec:** `docs/superpowers/specs/2026-05-09-field-journal-refresh-design.md`
**Plan:** `docs/superpowers/plans/2026-05-09-v1-07c-field-journal.md`

[Locked design tokens, components, lessons-learned att fyllas av implementer-subagent baserat på actual implementation.]
```

- [ ] **Step 6: Lägg till pointer i MEMORY.md**

```markdown
- [Plan 7c (Field Journal) — DONE](project_plan_7c_status.md) — shipped v0.7.0c-field-journal 2026-05-09; paper-bg + DM Serif + Caveat-accent + StampSeal-system genom alla skärmar inkl. Onboarding
```

- [ ] **Step 7: Commit screenshots + memory + CLAUDE.md**

```bash
git add docs/superpowers/screenshots/v0.7.0c-field-journal/ CLAUDE.md
git commit -m "docs(plan-7c): commit field-journal screenshots + bump CLAUDE.md to v0.7.0c"
# auto-memory är inte i repo (ligger i ~/.claude/...) — uppdateras separat lokalt
```

- [ ] **Step 8: Tagga + pusha**

```bash
git tag -a v0.7.0c-field-journal -m "Plan 7c — Field Journal design refresh (paper-bg, DM Serif + Caveat, StampSeal-system genom hela appen)"
git push origin main
git push origin v0.7.0c-field-journal
```

Expected: tag pushad till GitHub.

---

## Self-Review

After writing this plan, comparing against spec:

**Spec coverage check:**
- §3 visual direction (paper-bg, italic+Caveat, ❦-rule, stamps): Tasks 3, 4, 5, 6, 7, 8 ✓
- §4 typography (DM Serif + Caveat + Inter readability rule): Task 1, 5, 16 ✓
- §5 color tokens (Paper, Stamp, Marginalia): Task 2 ✓
- §6.1 Listen launcher: Task 10 ✓
- §6.2 Archive: Task 11 ✓
- §6.3 Badges: Task 12 ✓
- §6.4 Lifelist (loaded + empty): Task 13 ✓
- §6.5 Species Profile (PlateFrame + drop-cap + marginalia): Task 14 ✓
- §6.6 Observation Detail: Task 15 ✓
- §6.7 Onboarding: Task 16 ✓
- §6.8 Bottom-bar: Task 18 ✓
- §7 components (MicroLabel, JournalHeadline, JournalSubLine, OrnamentRule, JournalIntro, StampSeal, MiniStamp, PaperBackground, PlateFrame): Tasks 3-9 ✓
- §10 testing (build + tests + device-verify): Task 20 ✓
- §11 acceptance criteria: Task 20 listar dem ✓

**Type consistency:**
- `StampSealState` definierad i Task 7, använd i Task 12. Signatur stämmer (Locked, InProgress, Unlocked).
- `JournalIntro` signature: definierad i Task 6, använd i Task 10-18. Signatur konsistent: `label, headline, sub, modifier, headlineFontSize, horizontalPadding, topPadding, trailingContent`.
- `MiniStamp(number, modifier, size)` signatur konsistent.
- `paperBackground()` modifier: Task 3 → använd Task 10-18 ✓.
- `parseJournalHeadline` används av både `JournalHeadline` (Task 5) och `BodyTextWithCaveatAccents` (Task 16) ✓.

**Placeholder scan:**
- Inga "TBD"/"TODO" i task-steg.
- Vissa ställen flaggar "Implementer-subagent: justera om signaturen är annorlunda" — det är OK eftersom implementer kan upptäcka via läsning av faktisk fil. Inte placeholder, utan en tydlig signal att läsa dependent code.
- Task 15 (Observation Detail) är något under-spec'd — implementer-subagent får läsa befintlig fil + applicera samma mönster som Task 14 (Species Profile). Risk: lägre granularitet. Mitigation: review-stegen fångar avvikelser från spec.

**Decisions to flag for implementer:**
1. `marginalia` lokalisering: börja med ENDAST en `marginalia: String` (svenska) på Species + dokumentera lokaliserings-TODO. Kan migreras till `marginalia_sv` + `marginalia_en` i Plan 7d eller senare.
2. `Badge.glyph: String?` antas finnas — om inte, lägg till det i BadgeCatalog YAML + Badge-modellen som pre-step i Task 12.
3. `LifelistUiState.Loaded.daysActive: Int` antas finnas — om inte, lägg till i ViewModel som pre-step i Task 13.
4. `ArchiveRow.stampNumber: Int?` antas finnas — om inte, lägg till i ViewModel-state som pre-step i Task 11.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-05-09-v1-07c-field-journal.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

**Vilken approach?**
