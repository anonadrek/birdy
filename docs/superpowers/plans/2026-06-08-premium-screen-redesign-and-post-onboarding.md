# Premium-skärm: redesign + visa-en-gång efter onboarding — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bygg om `PremiumScreen` (gratis-summering + "Med Premium dessutom"-avdelare + 4 äkta features i ikon+titel+underrad-stil, rättad märkessiffra 10→7) och visa skärmen en gång direkt efter onboarding för icke-premium-användare.

**Architecture:** Del 1 är ren Compose-UI i `PremiumScreen.kt` + en ny ikon-fil + nya strängar (SV/EN). Del 2 lägger en boolean DataStore-flagga (`postOnboardingPremiumShown`) + en ren beslutsfunktion i `EntryFlowDecider` + inhakning i `AppScaffold`s befintliga `LaunchedEffect`. Inga nya beroenden.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, compose-resources (strängar), androidx.datastore (Android-prefs), kotlin.test + turbine (tester).

**Spec:** `docs/superpowers/specs/2026-06-08-premium-screen-redesign-and-post-onboarding-design.md`

**Worktree:** Allt arbete sker i `C:\Users\abbea\dev\birdy-bird-scanner\.worktrees\premium-screen-redesign` på branch `feat/premium-screen-redesign`.

**Gradle-prefix (bash):** Alla `./gradlew`-kommandon nedan kräver:
```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
```

---

## File Structure

| Fil | Ansvar | Åtgärd |
|---|---|---|
| `composeApp/src/commonMain/composeResources/values/strings.xml` | SV-strängar | Modify |
| `composeApp/src/commonMain/composeResources/values-en/strings.xml` | EN-strängar | Modify |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumFeatureIcons.kt` | 4 dependency-fria koppar-ikoner | Create |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumScreen.kt` | Paywall-UI | Modify |
| `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/UserPreferences.kt` | Prefs-interface | Modify |
| `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/InMemoryUserPreferences.kt` | In-memory-impl | Modify |
| `shared/datastore/src/androidMain/kotlin/se/birdy/datastore/UserPreferencesStore.android.kt` | Android-impl | Modify |
| `composeApp/src/commonTest/kotlin/se/birdy/app/testing/FakeUserPreferences.kt` | Test-fake-impl | Modify |
| `shared/datastore/src/jvmTest/kotlin/se/birdy/datastore/InMemoryUserPreferencesTest.kt` | Prefs-test | Modify |
| `composeApp/src/commonMain/kotlin/se/birdy/app/premium/EntryFlowDecider.kt` | Entry-beslut | Modify |
| `composeApp/src/commonTest/kotlin/se/birdy/app/premium/EntryFlowDeciderTest.kt` | Decider-test | Modify |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt` | Entry-wiring | Modify |

---

## Task 1: Lägg till nya strängar + rätta märkessiffran (SV + EN)

Lägger till alla nya strängar och rättar `10→7`. Tar **inte** bort de gamla `premium_feature_*` ännu (skärmen refererar dem fortfarande → bygget måste hålla grönt). compose-resources regenererar `Res`-accessorer automatiskt vid build.

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`

- [ ] **Step 1: Lägg till nya SV-strängar**

I `values/strings.xml`, direkt efter raden `<string name="premium_feature_badges">Fältmärken (10 premium-stämplar)</string>` (rad 498), lägg till:

```xml
    <!-- Premium-skärm v2: gratis-summering + 4 feature-rader -->
    <string name="premium_free_eyebrow">Allt det här är gratis — alltid</string>
    <string name="premium_free_scan">Skanna foto &amp; ljud</string>
    <string name="premium_free_save">Spara fynd i fältdagboken</string>
    <string name="premium_free_encyclopedia">Uppslagsverk · 839 arter</string>
    <string name="premium_free_badges">27 fältmärken &amp; Troférum</string>
    <string name="premium_divider">Med Premium dessutom</string>
    <string name="premium_feature_map_title">Fynd-kartan</string>
    <string name="premium_feature_map_sub">Se var du har sett dina fåglar</string>
    <string name="premium_feature_export_title">Exportera fältdagbok</string>
    <string name="premium_feature_export_sub">Hela dagboken som delbar PDF</string>
    <string name="premium_feature_stats_title">Säsongsstatistik</string>
    <string name="premium_feature_stats_sub">Mönster &amp; årsöversikt från dina fynd</string>
    <string name="premium_feature_badges_title">7 premium-fältmärken</string>
    <string name="premium_feature_badges_sub">Exklusiva stämplar att jaga</string>
```

- [ ] **Step 2: Rätta SV märkessiffra 10→7**

I `values/strings.xml`, ändra raden:
```xml
    <string name="premium_badges_cta">Lås upp 10 fältmärken →</string>
```
till:
```xml
    <string name="premium_badges_cta">Lås upp 7 fältmärken →</string>
```

- [ ] **Step 3: Lägg till nya EN-strängar**

I `values-en/strings.xml`, direkt efter raden `<string name="premium_feature_badges">Field marks (10 premium stamps)</string>` (rad 484), lägg till:

```xml
    <!-- Premium screen v2: free summary + 4 feature rows -->
    <string name="premium_free_eyebrow">All of this is free — always</string>
    <string name="premium_free_scan">Scan photo &amp; sound</string>
    <string name="premium_free_save">Save finds to your journal</string>
    <string name="premium_free_encyclopedia">Encyclopedia · 839 species</string>
    <string name="premium_free_badges">27 field marks &amp; Trophy Room</string>
    <string name="premium_divider">With Premium, also</string>
    <string name="premium_feature_map_title">Your finds map</string>
    <string name="premium_feature_map_sub">See where you’ve spotted your birds</string>
    <string name="premium_feature_export_title">Export field journal</string>
    <string name="premium_feature_export_sub">Your whole journal as a shareable PDF</string>
    <string name="premium_feature_stats_title">Seasonal statistics</string>
    <string name="premium_feature_stats_sub">Patterns &amp; yearly overview from your finds</string>
    <string name="premium_feature_badges_title">7 premium field marks</string>
    <string name="premium_feature_badges_sub">Exclusive stamps to chase</string>
```

**Trap:** apostrofen i `you’ve` MÅSTE vara `’` (U+2019), inte `'` (compose-resources unescape-trap). `&` skrivs `&amp;`.

- [ ] **Step 4: Rätta EN märkessiffra 10→7**

I `values-en/strings.xml`, ändra:
```xml
    <string name="premium_badges_cta">Unlock 10 field marks →</string>
```
till:
```xml
    <string name="premium_badges_cta">Unlock 7 field marks →</string>
```

- [ ] **Step 5: Verifiera att projektet fortfarande bygger (genererar Res-accessorer)**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL (gamla `premium_feature_*` finns kvar, så `PremiumScreen.kt` kompilerar; nya strängar genererade).

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(premium): add free-summary + 4-feature strings, fix 10->7 badge count (SV+EN)"
```

---

## Task 2: Skapa de 4 premium-feature-ikonerna

Dependency-fria Canvas-ritade koppar-ikoner (karta-nål, dokument+pil, stapeldiagram, rosett). Ingen `material-icons-extended`.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumFeatureIcons.kt`

- [ ] **Step 1: Skapa ikon-filen**

```kotlin
package se.birdy.app.ui.premium

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.theme.AccentCopper

/** De fyra premium-feature-ikonerna, ritade dependency-fritt i tunn koppar-stroke. */
enum class PremiumFeatureIcon { MAP, EXPORT, STATS, BADGE }

@Composable
fun PremiumFeatureGlyph(
    icon: PremiumFeatureIcon,
    modifier: Modifier = Modifier,
    tint: Color = AccentCopper,
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val sw = w * 0.09f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (icon) {
            PremiumFeatureIcon.MAP -> {
                val cx = w * 0.5f
                val headCy = h * 0.36f
                val r = w * 0.28f
                drawCircle(tint, radius = r, center = Offset(cx, headCy), style = stroke)
                val tail =
                    Path().apply {
                        moveTo(cx - r * 0.72f, headCy + r * 0.72f)
                        lineTo(cx, h * 0.92f)
                        lineTo(cx + r * 0.72f, headCy + r * 0.72f)
                    }
                drawPath(tail, tint, style = stroke)
                drawCircle(tint, radius = w * 0.09f, center = Offset(cx, headCy))
            }
            PremiumFeatureIcon.EXPORT -> {
                val left = w * 0.26f
                val right = w * 0.74f
                val top = h * 0.12f
                val bottom = h * 0.88f
                val page =
                    Path().apply {
                        moveTo(left, top)
                        lineTo(right, top)
                        lineTo(right, bottom)
                        lineTo(left, bottom)
                        close()
                    }
                drawPath(page, tint, style = stroke)
                val ax = w * 0.5f
                drawLine(tint, Offset(ax, h * 0.34f), Offset(ax, h * 0.66f), strokeWidth = sw, cap = StrokeCap.Round)
                val chevron =
                    Path().apply {
                        moveTo(ax - w * 0.12f, h * 0.54f)
                        lineTo(ax, h * 0.68f)
                        lineTo(ax + w * 0.12f, h * 0.54f)
                    }
                drawPath(chevron, tint, style = stroke)
            }
            PremiumFeatureIcon.STATS -> {
                val base = h * 0.82f
                drawLine(tint, Offset(w * 0.16f, base), Offset(w * 0.84f, base), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.30f, base), Offset(w * 0.30f, h * 0.58f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.50f, base), Offset(w * 0.50f, h * 0.34f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.70f, base), Offset(w * 0.70f, h * 0.46f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            PremiumFeatureIcon.BADGE -> {
                val cx = w * 0.5f
                val cy = h * 0.38f
                val r = w * 0.26f
                drawCircle(tint, radius = r, center = Offset(cx, cy), style = stroke)
                val ribbons =
                    Path().apply {
                        moveTo(cx - r * 0.55f, cy + r * 0.8f)
                        lineTo(w * 0.34f, h * 0.92f)
                        lineTo(cx, h * 0.74f)
                        lineTo(w * 0.66f, h * 0.92f)
                        lineTo(cx + r * 0.55f, cy + r * 0.8f)
                    }
                drawPath(ribbons, tint, style = stroke)
            }
        }
    }
}
```

- [ ] **Step 2: Verifiera kompilering**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL (ny fil, oanvänd men kompilerar).

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumFeatureIcons.kt
git commit -m "feat(premium): add dependency-free copper feature icons (map/export/stats/badge)"
```

---

## Task 3: Bygg om `PremiumScreen` feature-området

Ersätt 3-radslistan (`features` + `items(features)` + `FeatureRow`/`StampBullet`) med: gratis-summering → avdelare → 4 ikon-rader.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumScreen.kt`

- [ ] **Step 1: Uppdatera importerna**

Ta bort dessa tre rader (de gamla feature-strängarna):
```kotlin
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_badges
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_export
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_stats
```

Lägg till (alfabetisk ordning bland de andra `birdy_bird_scanner...`-importerna):
```kotlin
import birdy_bird_scanner.composeapp.generated.resources.premium_divider
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_badges_sub
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_badges_title
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_export_sub
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_export_title
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_map_sub
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_map_title
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_stats_sub
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_stats_title
import birdy_bird_scanner.composeapp.generated.resources.premium_free_badges
import birdy_bird_scanner.composeapp.generated.resources.premium_free_encyclopedia
import birdy_bird_scanner.composeapp.generated.resources.premium_free_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.premium_free_save
import birdy_bird_scanner.composeapp.generated.resources.premium_free_scan
```

Lägg även till (för `StringResource`-typen):
```kotlin
import org.jetbrains.compose.resources.StringResource
```

- [ ] **Step 2: Ersätt `features`-listan med en top-level feature-modell**

Ta bort raderna i `PremiumScreen(...)` som bygger den gamla listan:
```kotlin
    val features =
        listOf(
            stringResource(Res.string.premium_feature_export),
            stringResource(Res.string.premium_feature_stats),
            stringResource(Res.string.premium_feature_badges),
        )
```

Lägg till, längst ner i filen (top-level), feature-modellen + listan:
```kotlin
private data class PremiumFeatureItem(
    val icon: PremiumFeatureIcon,
    val title: StringResource,
    val sub: StringResource,
)

private val premiumFeatures =
    listOf(
        PremiumFeatureItem(PremiumFeatureIcon.MAP, Res.string.premium_feature_map_title, Res.string.premium_feature_map_sub),
        PremiumFeatureItem(PremiumFeatureIcon.EXPORT, Res.string.premium_feature_export_title, Res.string.premium_feature_export_sub),
        PremiumFeatureItem(PremiumFeatureIcon.STATS, Res.string.premium_feature_stats_title, Res.string.premium_feature_stats_sub),
        PremiumFeatureItem(PremiumFeatureIcon.BADGE, Res.string.premium_feature_badges_title, Res.string.premium_feature_badges_sub),
    )
```

- [ ] **Step 3: Byt ut LazyColumn-innehållet (subline → feature-rader)**

Ersätt detta block i `LazyColumn`:
```kotlin
            item { PremiumSubline() }
            item { Spacer(Modifier.height(6.dp)) }
            items(features) { feature -> FeatureRow(feature) }
            item { Spacer(Modifier.height(8.dp)) }
```
med:
```kotlin
            item { PremiumSubline() }
            item { Spacer(Modifier.height(10.dp)) }
            item { FreeSummarySection() }
            item { Spacer(Modifier.height(8.dp)) }
            item { PremiumDivider() }
            item { Spacer(Modifier.height(2.dp)) }
            items(premiumFeatures) { f ->
                FeatureRowC(
                    icon = f.icon,
                    title = stringResource(f.title),
                    sub = stringResource(f.sub),
                )
            }
            item { Spacer(Modifier.height(10.dp)) }
```

- [ ] **Step 4: Ersätt `FeatureRow` + `StampBullet` med de nya composables**

Ta bort de gamla:
```kotlin
@Composable
private fun FeatureRow(feature: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StampBullet()
        Spacer(Modifier.size(12.dp))
        Text(
            text = feature,
            fontSize = 14.sp,
            color = TextOnCreme,
        )
    }
}

@Composable
private fun StampBullet() {
    Box(
        modifier =
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(AccentCopper.copy(alpha = 0.08f))
                .border(1.5.dp, AccentCopper, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "✓",
            fontFamily = rememberCaveat(),
            fontWeight = FontWeight.W600,
            fontSize = 16.sp,
            color = AccentCopper,
        )
    }
}
```

Lägg till i stället:
```kotlin
@Composable
private fun FreeSummarySection() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(
            text = stringResource(Res.string.premium_free_eyebrow),
            fontFamily = rememberCaveat(),
            fontWeight = FontWeight.W600,
            fontSize = 15.sp,
            color = MarginaliaInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MarginaliaInk.copy(alpha = 0.05f))
                    .border(1.dp, MarginaliaInk.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FreeItem(stringResource(Res.string.premium_free_scan), Modifier.weight(1f))
                FreeItem(stringResource(Res.string.premium_free_save), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FreeItem(stringResource(Res.string.premium_free_encyclopedia), Modifier.weight(1f))
                FreeItem(stringResource(Res.string.premium_free_badges), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RowScope.FreeItem(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(16.dp).clip(CircleShape).background(MarginaliaInk),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", color = PaperTop, fontSize = 10.sp)
        }
        Spacer(Modifier.size(7.dp))
        Text(text, fontSize = 11.sp, color = MarginaliaInk, lineHeight = 13.sp)
    }
}

@Composable
private fun PremiumDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(AccentCopper.copy(alpha = 0.4f)))
        Text(
            text = stringResource(Res.string.premium_divider),
            fontFamily = rememberCaveat(),
            fontWeight = FontWeight.W700,
            fontSize = 15.sp,
            color = AccentCopper,
        )
        Box(Modifier.weight(1f).height(1.dp).background(AccentCopper.copy(alpha = 0.4f)))
    }
}

@Composable
private fun FeatureRowC(
    icon: PremiumFeatureIcon,
    title: String,
    sub: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(AccentCopper.copy(alpha = 0.08f))
                    .border(1.3.dp, AccentCopper.copy(alpha = 0.5f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            PremiumFeatureGlyph(icon, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = rememberDmSerifDisplay(),
                fontStyle = FontStyle.Italic,
                fontSize = 15.sp,
                color = TextOnCreme,
            )
            Text(
                text = sub,
                fontSize = 11.sp,
                color = MarginaliaInk,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}
```

**Notera:** `FreeItem` deklareras `RowScope.` så `Modifier.weight(1f)` är giltig. Importera `androidx.compose.foundation.layout.RowScope` om den inte redan finns.

- [ ] **Step 5: Kör ktlintFormat + bygg**

Run: `./gradlew ktlintFormat :composeApp:compileDebugKotlinAndroid 2>&1 | tail -25`
Expected: BUILD SUCCESSFUL. (Gamla `premium_feature_*`-strängarna refereras inte längre, men finns kvar — tas bort i Task 4.)

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumScreen.kt
git commit -m "feat(premium): redesign feature list with free-summary + divider + 4 icon rows"
```

---

## Task 4: Ta bort de obsoleta `premium_feature_*`-strängarna

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`

- [ ] **Step 1: Bekräfta att de inte refereras någonstans**

Run: `git grep -n "premium_feature_export\b\|premium_feature_stats\b\|premium_feature_badges\b" -- "*.kt"`
Expected: Inga träffar (de nya nycklarna har `_title`/`_sub`-suffix, så `\b` exkluderar dem). Om någon `.kt`-träff finns — STOPPA och åtgärda referensen först.

- [ ] **Step 2: Ta bort de tre SV-raderna**

I `values/strings.xml`, ta bort:
```xml
    <string name="premium_feature_export">Exportera fältdagbok som PDF</string>
    <string name="premium_feature_stats">Säsongs-statistik &amp; årsöversikt</string>
    <string name="premium_feature_badges">Fältmärken (10 premium-stämplar)</string>
```

- [ ] **Step 3: Ta bort de tre EN-raderna**

I `values-en/strings.xml`, ta bort:
```xml
    <string name="premium_feature_export">Export field journal as PDF</string>
    <string name="premium_feature_stats">Seasonal statistics &amp; yearly overview</string>
    <string name="premium_feature_badges">Field marks (10 premium stamps)</string>
```

- [ ] **Step 4: Bygg**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "chore(premium): remove obsolete single-line feature strings (replaced by title/sub)"
```

---

## Task 5: DataStore-flagga `postOnboardingPremiumShown` (TDD)

**Files:**
- Modify: `shared/datastore/src/jvmTest/kotlin/se/birdy/datastore/InMemoryUserPreferencesTest.kt`
- Modify: `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/UserPreferences.kt`
- Modify: `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/InMemoryUserPreferences.kt`
- Modify: `shared/datastore/src/androidMain/kotlin/se/birdy/datastore/UserPreferencesStore.android.kt`
- Modify: `composeApp/src/commonTest/kotlin/se/birdy/app/testing/FakeUserPreferences.kt`

- [ ] **Step 1: Skriv det failande testet**

I `InMemoryUserPreferencesTest.kt`, lägg till (efter `appLanguage`-testet):
```kotlin
    @Test
    fun `postOnboardingPremiumShown starts false and updates`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            prefs.postOnboardingPremiumShown.test {
                assertEquals(false, awaitItem())
                prefs.setPostOnboardingPremiumShown(true)
                assertEquals(true, awaitItem())
            }
        }
```

- [ ] **Step 2: Kör — bekräfta att det INTE kompilerar/failar**

Run: `./gradlew :shared:datastore:jvmTest 2>&1 | tail -20`
Expected: FAIL — `unresolved reference: postOnboardingPremiumShown` (metoden finns inte än).

- [ ] **Step 3: Lägg till i interfacet**

I `UserPreferences.kt`, efter raden `val premiumModalLastShownAt: Flow<Long?>` (rad 31), lägg till:
```kotlin

    /** True när post-onboarding-premium-skärmen visats en gång (visa aldrig igen). */
    val postOnboardingPremiumShown: Flow<Boolean>
```
Och efter `suspend fun setPremiumModalLastShownAt(ms: Long)` (rad 56), lägg till:
```kotlin

    suspend fun setPostOnboardingPremiumShown(value: Boolean)
```

- [ ] **Step 4: Implementera i `InMemoryUserPreferences`**

I `InMemoryUserPreferences.kt`:
- Efter `private val _premiumModalLastShownAt = MutableStateFlow<Long?>(null)`:
```kotlin
    private val _postOnboardingPremiumShown = MutableStateFlow(false)
```
- Efter `override val premiumModalLastShownAt: Flow<Long?> = _premiumModalLastShownAt.asStateFlow()`:
```kotlin
    override val postOnboardingPremiumShown: Flow<Boolean> = _postOnboardingPremiumShown.asStateFlow()
```
- Efter `setPremiumModalLastShownAt`-funktionen:
```kotlin

    override suspend fun setPostOnboardingPremiumShown(value: Boolean) {
        _postOnboardingPremiumShown.value = value
    }
```

- [ ] **Step 5: Implementera i `AndroidUserPreferences`**

I `UserPreferencesStore.android.kt`:
- I `object Keys`, efter `PREMIUM_MODAL_LAST_SHOWN_AT`:
```kotlin
        val POST_ONBOARDING_PREMIUM_SHOWN = booleanPreferencesKey("post_onboarding_premium_shown")
```
- Efter `override val premiumModalLastShownAt: ...`-blocket:
```kotlin
    override val postOnboardingPremiumShown: Flow<Boolean> =
        safeData.map { it[Keys.POST_ONBOARDING_PREMIUM_SHOWN] ?: false }
```
- Efter `setPremiumModalLastShownAt`-funktionen:
```kotlin

    override suspend fun setPostOnboardingPremiumShown(value: Boolean) {
        store.edit { it[Keys.POST_ONBOARDING_PREMIUM_SHOWN] = value }
    }
```

- [ ] **Step 6: Implementera i `FakeUserPreferences`**

I `composeApp/src/commonTest/kotlin/se/birdy/app/testing/FakeUserPreferences.kt`:
- Efter `private val _premiumModalLastShownAt = MutableStateFlow<Long?>(null)`:
```kotlin
    private val _postOnboardingPremiumShown = MutableStateFlow(false)
```
- Efter `override val premiumModalLastShownAt: ...`:
```kotlin
    override val postOnboardingPremiumShown: Flow<Boolean> = _postOnboardingPremiumShown.asStateFlow()
```
- Efter `setPremiumModalLastShownAt`-funktionen:
```kotlin

    override suspend fun setPostOnboardingPremiumShown(value: Boolean) {
        _postOnboardingPremiumShown.value = value
    }
```

- [ ] **Step 7: Kör testet — grönt**

Run: `./gradlew :shared:datastore:jvmTest 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL (nya testet passerar).

- [ ] **Step 8: ktlintFormat + commit**

```bash
./gradlew ktlintFormat
git add shared/datastore composeApp/src/commonTest/kotlin/se/birdy/app/testing/FakeUserPreferences.kt
git commit -m "feat(datastore): add postOnboardingPremiumShown flag across all UserPreferences impls"
```

---

## Task 6: `EntryFlowDecider.shouldShowPostOnboardingPremium` (TDD)

**Files:**
- Modify: `composeApp/src/commonTest/kotlin/se/birdy/app/premium/EntryFlowDeciderTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/premium/EntryFlowDecider.kt`

- [ ] **Step 1: Skriv de failande testerna**

I `EntryFlowDeciderTest.kt`, lägg till före den avslutande `}`:
```kotlin

    @Test fun `post-onboarding false when onboarding incomplete`() {
        assertFalse(
            EntryFlowDecider.shouldShowPostOnboardingPremium(
                onboardingComplete = false,
                alreadyShown = false,
                state = PremiumState.Free,
            ),
        )
    }

    @Test fun `post-onboarding false when already shown`() {
        assertFalse(
            EntryFlowDecider.shouldShowPostOnboardingPremium(
                onboardingComplete = true,
                alreadyShown = true,
                state = PremiumState.Free,
            ),
        )
    }

    @Test fun `post-onboarding false when premium active`() {
        assertFalse(
            EntryFlowDecider.shouldShowPostOnboardingPremium(
                onboardingComplete = true,
                alreadyShown = false,
                state = PremiumState.Active(se.birdy.domain.premium.PremiumTier.LIFETIME, now),
            ),
        )
    }

    @Test fun `post-onboarding true when complete, not shown, free`() {
        assertTrue(
            EntryFlowDecider.shouldShowPostOnboardingPremium(
                onboardingComplete = true,
                alreadyShown = false,
                state = PremiumState.Free,
            ),
        )
    }
```

- [ ] **Step 2: Kör — bekräfta fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.premium.EntryFlowDeciderTest" 2>&1 | tail -20`
Expected: FAIL — `unresolved reference: shouldShowPostOnboardingPremium`.

- [ ] **Step 3: Implementera regeln**

I `EntryFlowDecider.kt`, lägg till inuti `object EntryFlowDecider`, efter `shouldShowPremiumModal(...)`-funktionen:
```kotlin

    /**
     * Show the premium screen once, immediately after onboarding, iff:
     *  1. Onboarding completed
     *  2. Not shown before
     *  3. Premium is Free (not Active)
     *
     * No grace/throttle — this is the day-0 introduction, distinct from the
     * 7-day cold-start modal above.
     */
    fun shouldShowPostOnboardingPremium(
        onboardingComplete: Boolean,
        alreadyShown: Boolean,
        state: PremiumState,
    ): Boolean {
        if (!onboardingComplete) return false
        if (alreadyShown) return false
        if (state !is PremiumState.Free) return false
        return true
    }
```

- [ ] **Step 4: Kör testerna — grönt**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.premium.EntryFlowDeciderTest" 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL (alla EntryFlowDecider-tester passerar).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/premium/EntryFlowDecider.kt composeApp/src/commonTest/kotlin/se/birdy/app/premium/EntryFlowDeciderTest.kt
git commit -m "feat(premium): add shouldShowPostOnboardingPremium decision rule + tests"
```

---

## Task 7: Haka in i `AppScaffold`s entry-flow

Visa post-onboarding-skärmen före cold-start-modalen i den befintliga `LaunchedEffect(Unit)`.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt`

- [ ] **Step 1: Ersätt `LaunchedEffect(Unit)`-blocket**

Hitta detta block (ca rad 60–77):
```kotlin
    LaunchedEffect(Unit) {
        val now = graph.clock.now()
        val firstInstallMs = graph.userPreferences.firstInstallTimestamp.first()
        val lastShownMs = graph.userPreferences.premiumModalLastShownAt.first()
        val premiumState = graph.premiumOverride ?: graph.premiumRepository.state.value
        val shouldShow =
            EntryFlowDecider.shouldShowPremiumModal(
                now = now,
                firstInstallAt = firstInstallMs?.let { Instant.fromEpochMilliseconds(it) },
                lastShownAt = lastShownMs?.let { Instant.fromEpochMilliseconds(it) },
                state = premiumState,
                onboardingComplete = true,
            )
        if (shouldShow) {
            graph.userPreferences.setPremiumModalLastShownAt(now.toEpochMilliseconds())
            navController.navigate(AppRoute.Premium)
        }
    }
```

Ersätt med:
```kotlin
    LaunchedEffect(Unit) {
        val now = graph.clock.now()
        val premiumState = graph.premiumOverride ?: graph.premiumRepository.state.value

        // Day-0: show the premium screen once right after onboarding (non-premium only).
        val postOnboardingShown = graph.userPreferences.postOnboardingPremiumShown.first()
        if (EntryFlowDecider.shouldShowPostOnboardingPremium(
                onboardingComplete = true,
                alreadyShown = postOnboardingShown,
                state = premiumState,
            )
        ) {
            graph.userPreferences.setPostOnboardingPremiumShown(true)
            navController.navigate(AppRoute.Premium)
            return@LaunchedEffect
        }

        // Otherwise: the 7-day cold-start re-engagement modal.
        val firstInstallMs = graph.userPreferences.firstInstallTimestamp.first()
        val lastShownMs = graph.userPreferences.premiumModalLastShownAt.first()
        val shouldShow =
            EntryFlowDecider.shouldShowPremiumModal(
                now = now,
                firstInstallAt = firstInstallMs?.let { Instant.fromEpochMilliseconds(it) },
                lastShownAt = lastShownMs?.let { Instant.fromEpochMilliseconds(it) },
                state = premiumState,
                onboardingComplete = true,
            )
        if (shouldShow) {
            graph.userPreferences.setPremiumModalLastShownAt(now.toEpochMilliseconds())
            navController.navigate(AppRoute.Premium)
        }
    }
```

- [ ] **Step 2: ktlintFormat + bygg**

Run: `./gradlew ktlintFormat :composeApp:compileDebugKotlinAndroid 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt
git commit -m "feat(premium): show premium screen once after onboarding (non-premium, day-0)"
```

---

## Task 8: Full verifiering (bygg + lint + tester)

**Files:** Inga (verifieringssteg).

- [ ] **Step 1: ktlint + detekt**

Run: `./gradlew ktlintCheck detekt 2>&1 | tail -25`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Relevanta unit-tester**

Run: `./gradlew :shared:datastore:jvmTest :composeApp:testDebugUnitTest 2>&1 | tail -25`
Expected: BUILD SUCCESSFUL (inkl. `InMemoryUserPreferencesTest`, `EntryFlowDeciderTest`).

- [ ] **Step 3: Android-app bygger**

Run: `./gradlew :androidApp:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit (om ktlintFormat ändrade något)**

```bash
git add -A
git commit -m "chore(premium): ktlint/detekt clean-up" || echo "nothing to commit"
```

---

## Task 9: Device-verify (manuellt, SM-S918B)

**Files:** Inga.

> Kräver fysisk enhet (rapporteras, körs inte av subagent). Be Albin om "händerna borta" innan ADB-driving (personlig enhet).

- [ ] **Step 1: Säkerställ icke-premium-läge**

Eftersom `PREMIUM_OPEN_FOR_LAUNCH=true` tvingar Active måste du för verifiering antingen (a) använda debug-bygget med `BuildConfig.PREMIUM_DEBUG_FORCE_*` satt till Free, eller (b) tillfälligt sätta `PREMIUM_OPEN_FOR_LAUNCH=false` i `androidApp/build.gradle.kts` lokalt (committa INTE den ändringen).

- [ ] **Step 2: Installera + nollställ**

```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell pm clear se.birdy.android.debug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android.debug/se.birdy.android.MainActivity
```
(`pm clear` nollställer DataStore → `hasSeenOnboarding=false` + `postOnboardingPremiumShown=false`.)

- [ ] **Step 3: Verifiera flödet**

Förväntat:
1. Onboarding-introt körs.
2. Vid avslut visas `PremiumScreen` automatiskt **en gång** — ny layout: hero-foto, gratis-2×2-ruta, "Med Premium dessutom"-avdelare, 4 ikon-rader (karta/PDF/statistik/rosett), tier-kort, CTA.
3. Stäng (✕) → hamnar på Listen-fliken.
4. Döda appen + starta om → premium-skärmen visas **inte** igen.

Ta skärmdump av den nya premium-skärmen för dokumentation.

---

## Self-Review (ifylld av plan-författaren)

- **Spec-täckning:** Gratis-sektion ✅ (T1/T3) · avdelare ✅ (T1/T3) · 4 features + ikoner ✅ (T2/T3) · 10→7 ✅ (T1) · Insikter borttagen ✅ (aldrig tillagd) · DataStore-flagga ✅ (T5) · decider-regel ✅ (T6) · AppScaffold-wiring ✅ (T7) · Free-gate/launch-open ✅ (T6-regel + T9-not) · SV+EN ✅ (T1/T4). Relaterad städning (species/molnsynk) är medvetet utanför scope per spec.
- **Placeholders:** Inga — all kod är fullständig.
- **Typkonsistens:** `PremiumFeatureIcon` (T2) används i `PremiumFeatureItem`/`FeatureRowC` (T3); `shouldShowPostOnboardingPremium(onboardingComplete, alreadyShown, state)` (T6) matchar anropet i T7; `postOnboardingPremiumShown`/`setPostOnboardingPremiumShown` (T5) matchar T7-anropen.
- **Build-grönt per task:** T1–T4 ordnade så gamla strängar lever tills skärmen slutat referera dem; T5/T6 TDD; T8 helhetsverifiering.

## Release-notering (vid AAB-upload, inte i kod-arbetet)

Bumpa `versionCode`/`versionName` och lägg paywall-/kart-uppdateringen i "What's new". Pusha branchen + öppna PR mot `main` (separat från coverage-map-spåret).
