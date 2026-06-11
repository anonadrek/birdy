# Glödande premium-gating för gratisanvändare — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Få alla premium-gateade rutor en gratisanvändare möter att lysa/glänsa diskret (locka istället för att låsa), via en gemensam `Modifier.premiumGlow()`.

**Architecture:** En tunn delad modifier `premiumGlow()` ovanpå befintliga `shimmerSweep` är enda tuning-punkten för glöden. Den appliceras på `PremiumHeroCard`, `PremiumTeaserCard`, `MapPremiumTeaser` (omgjord till ett centrerat glödande kort) och `LockedStatsPreview` (omramad från lås+sudd till inbjudande förhandsvisning). Rent visuella ändringar — ingen ändring i gating-logik, navigation eller premium-tillstånd.

**Tech Stack:** Kotlin Multiplatform + Compose Multiplatform; compose-resources för strängar (SV + EN); ktlint 12.1.2 + detekt 1.23.7.

**Spec:** `docs/superpowers/specs/2026-06-11-premium-gating-glow-design.md`

---

## Förutsättningar (läs först)

- **Inga unit-tester för glöden.** Den är en animerad modifier utan ny matematik. Projektnormen (se `composeApp/src/commonTest/.../ui/components/ShimmerBorderTest.kt`) testar bara ren alpha-matte, inte animationer. Verifiering per task = **kompilering + ktlint/detekt grönt**; samlad **on-device-verifiering** i Task 5.
- **Bash-prefix för gradle** (annars hittar Gradle inte Java) — använd i varje gradle-kommando:
  ```bash
  export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```
- **Rör inte `gradle.properties`** (lokal MapTiler-nyckel, committas aldrig). Den syns som `M` i git status — lämna den.
- Alla nya/ändrade `.kt`-filer ligger i `composeApp/src/commonMain/kotlin/se/birdy/app/...`. `premiumGlow`, `shimmerSweep`, `PremiumTeaserCard`, `LockedStatsPreview`, `PremiumHeroCard` ligger alla i paketet `se.birdy.app.ui.components` → de når varandra utan import.

---

## File Structure

| Fil | Ansvar | Åtgärd |
|-----|--------|--------|
| `composeApp/.../ui/components/PremiumGlow.kt` | Den delade `Modifier.premiumGlow()` — enda glöd-tuning-punkten | **Skapa** |
| `composeApp/.../ui/components/PremiumHeroCard.kt` | Inställningar-hero, routas genom `premiumGlow()` | Ändra (1 rad) |
| `composeApp/.../ui/components/PremiumTeaserCard.kt` | Artprofil + Arkiv teaser-kort, får glöd när det teasar | Ändra |
| `composeApp/.../ui/components/LockedStatsPreview.kt` | Mina arter säsongsstat — omramad till inbjudande förhandsvisning | **Skriv om** |
| `composeApp/.../ui/map/MapPremiumTeaser.kt` | Karta-tab gateat läge — omgjort till glödande kort | **Skriv om** |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | SV CTA-sträng | Ändra (1 rad) |
| `composeApp/src/commonMain/composeResources/values-en/strings.xml` | EN CTA-sträng | Ändra (1 rad) |

---

## Task 1: Delad `premiumGlow()` + routa om Inställningar-hero

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/PremiumGlow.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/PremiumHeroCard.kt:60`

- [ ] **Step 1: Skapa `PremiumGlow.kt`**

```kotlin
package se.birdy.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Den kanoniska "det här är premium"-glöden för gateade ytor som en
 * gratisanvändare möter. ENDA platsen glöd-parametrarna bor — ändra här så slår
 * det igenom på alla gateade kort (PremiumTeaserCard, LockedStatsPreview,
 * MapPremiumTeaser-kortet, PremiumHeroCard). Bygger på [shimmerSweep] med ett
 * diskret, långsamt svep i linje med Troférummet.
 *
 * Måste appliceras INNANFÖR kortets .clip(...) så svepet maskas till de rundade
 * hörnen.
 */
@Composable
fun Modifier.premiumGlow(
    durationMillis: Int = 6000,
    alpha: Float = 0.18f,
): Modifier = this.shimmerSweep(durationMillis = durationMillis, alpha = alpha)
```

- [ ] **Step 2: Routa om hero genom `premiumGlow()`**

I `PremiumHeroCard.kt`, byt raden `.shimmerSweep(),` (rad 60, i Box-modifier-kedjan efter `.clickable(onClick = onClick)`) mot:

```kotlin
                .premiumGlow(),
```

(`premiumGlow` ligger i samma paket → ingen ny import. `shimmerSweep` användes också utan import; inget import-block att städa.)

- [ ] **Step 3: Kompilera + lint**

Run:
```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"; export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :composeApp:assembleDebug ktlintCheck detekt
```
Expected: BUILD SUCCESSFUL. (Kör `./gradlew ktlintFormat` först om ktlint klagar på formatering.)

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/PremiumGlow.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/PremiumHeroCard.kt
git commit -m "feat(premium): delad premiumGlow()-modifier + routa Inställningar-hero genom den"
```

---

## Task 2: Glöd på `PremiumTeaserCard` (Artprofil + Arkiv)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/PremiumTeaserCard.kt`

Glöden ska bara synas när kortet **teasar** (gratisanvändare), inte i export-läget (premium-användare som ser "Exportera fältdagbok").

- [ ] **Step 1: Beräkna glöd-modifier utifrån läget**

I `PremiumTeaserCard.kt`, direkt efter `val onCardClick: () -> Unit = ...`-blocket (runt rad 71, före `Box(...)`), lägg till:

```kotlin
    val glowModifier = if (showExportCta) Modifier else Modifier.premiumGlow()
```

- [ ] **Step 2: Applicera glöden i kort-kedjan**

I inner-`Column`ens modifier-kedja, lägg in `.then(glowModifier)` mellan `.border(...)` och `.clickable(...)`. Kedjan blir:

```kotlin
                    .clip(RoundedCornerShape(14.dp))
                    .background(SandCreme)
                    .border(1.dp, AccentCopper.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    .then(glowModifier)
                    .clickable(enabled = !(showExportCta && isExporting), onClick = onCardClick)
                    .padding(14.dp),
```

(`premiumGlow` är samma paket → ingen import. `Modifier` är redan importerad.)

- [ ] **Step 3: Kompilera + lint**

Run:
```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"; export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :composeApp:assembleDebug ktlintCheck detekt
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/PremiumTeaserCard.kt
git commit -m "feat(premium): subtil glöd på PremiumTeaserCard i teaser-läget (Artprofil + Arkiv)"
```

---

## Task 3: Omramning av `LockedStatsPreview` (Mina arter) — locka inte låsa

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml:507`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml:494`
- Modify (skriv om): `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/LockedStatsPreview.kt`

- [ ] **Step 1: Byt CTA-strängen till inbjudande copy (SV)**

I `values/strings.xml`, byt rad 507 från:
```xml
    <string name="premium_lifelist_cta">Lås upp säsongs-stats</string>
```
till:
```xml
    <string name="premium_lifelist_cta">Se hela din säsong</string>
```

- [ ] **Step 2: Byt CTA-strängen (EN)**

I `values-en/strings.xml`, byt rad 494 från:
```xml
    <string name="premium_lifelist_cta">Unlock seasonal stats</string>
```
till:
```xml
    <string name="premium_lifelist_cta">See your full season</string>
```

(Pilen `›` renderas separat i komponenten, så den ligger inte i strängen — håller `contentDescription` ren för skärmläsare.)

- [ ] **Step 3: Skriv om `LockedStatsPreview.kt`**

Ersätt HELA filinnehållet med:

```kotlin
package se.birdy.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.rememberCaveat

/**
 * "Locka inte låsa"-förhandsvisning av säsongsstatistik för gratisanvändare:
 * en mjukt suddad glimt av kopparstaplar som glänser ([premiumGlow]), med en
 * liten koppar-"Premium"-pill i hörnet och en inbjudande CTA — inget stort
 * hänglås, ingen mörk dimning. Stapeldatan är hårdkodad i v1.
 */
@Composable
fun LockedStatsPreview(
    title: String,
    overlayCta: String,
    overlayBadge: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val barHeights = listOf(0.30f, 0.55f, 0.80f, 0.65f, 0.45f, 0.75f)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .premiumGlow()
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = overlayCta
                    role = Role.Button
                },
    ) {
        // Mjuk förhandsvisning av säsongsstaplarna — inbjudande, inte dold.
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(SandCreme)
                    .padding(14.dp)
                    .blur(1.5.dp)
                    .graphicsLayer(alpha = 0.8f),
        ) {
            Text(title, fontSize = 11.sp, color = MarginaliaInk)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(70.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                barHeights.forEach { h ->
                    Box(
                        modifier =
                            Modifier
                                .width(20.dp)
                                .height((70 * h).dp)
                                .background(AccentCopper, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                    )
                }
            }
        }
        // Koppar-"Premium"-pill — uppe i hörnet, nedgraderad från det stora låset.
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(AccentCopper)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = overlayBadge,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.W700,
                letterSpacing = 0.2.em,
            )
        }
        // Inbjudande CTA — nere till vänster, ingen lås-barriär.
        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = overlayCta,
                fontFamily = rememberCaveat(),
                fontSize = 18.sp,
                color = AccentCopper,
                fontWeight = FontWeight.W600,
            )
            Spacer(Modifier.width(4.dp))
            Text("›", color = AccentCopper, fontSize = 20.sp, fontWeight = FontWeight.W600)
        }
    }
}
```

Ändringar mot förra versionen: blur 3.5→1.5 dp, stapel-alpha 0.55→0.8, den mörka `Brush.verticalGradient`-overlayen borttagen, det stora `Icons.Outlined.Lock` borttaget → liten koppar-pill (`overlayBadge`) i `TopEnd`, CTA flyttad till `BottomStart` med separat `›`, `.premiumGlow()` på kortytan. Borttagna imports: `Brush`, `Icon`, `Icons`, `Lock`, `PaperTop`, `size`.

- [ ] **Step 4: Kompilera + lint + relevanta unit-tester**

Run:
```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"; export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest ktlintCheck detekt
```
Expected: BUILD SUCCESSFUL, alla tester gröna. (Inget test refererar `premium_lifelist_cta`-värdet eller `LockedStatsPreview`-internals; testkörningen är en regressions-spärr.)

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/LockedStatsPreview.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(premium): omramma Mina arter-stat från lås+sudd till inbjudande glödande förhandsvisning"
```

---

## Task 4: `MapPremiumTeaser` → centrerat glödande kort

**Files:**
- Modify (skriv om): `composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapPremiumTeaser.kt`

- [ ] **Step 1: Skriv om `MapPremiumTeaser.kt`**

Ersätt HELA filinnehållet med:

```kotlin
package se.birdy.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.map_teaser_caption
import birdy_bird_scanner.composeapp.generated.resources.map_teaser_count
import birdy_bird_scanner.composeapp.generated.resources.map_teaser_cta
import birdy_bird_scanner.composeapp.generated.resources.map_teaser_title
import birdy_bird_scanner.composeapp.generated.resources.premium_lifelist_badge
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.premiumGlow
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.paperBackground
import se.birdy.app.ui.theme.rememberDmSerifDisplay

@Composable
fun MapPremiumTeaser(
    viewModel: MapViewModel,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(
        modifier = modifier.fillMaxSize().paperBackground().padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 9.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(SandCreme)
                        .border(1.dp, AccentCopper.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                        .premiumGlow()
                        .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(Res.string.map_teaser_title),
                    textAlign = TextAlign.Center,
                    fontFamily = rememberDmSerifDisplay(),
                    fontStyle = FontStyle.Italic,
                    fontSize = 24.sp,
                    color = TextOnCreme,
                )
                Text(
                    stringResource(Res.string.map_teaser_caption),
                    color = MarginaliaInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    stringResource(Res.string.map_teaser_count, state.locatedCount.toString()),
                    color = MarginaliaInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
                Button(
                    onClick = onUpgrade,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCopper, contentColor = OffwhiteWarm),
                ) { Text(stringResource(Res.string.map_teaser_cta)) }
            }
            // Koppar-"PREMIUM"-flagga, samma språk som PremiumTeaserCard.
            Box(
                modifier =
                    Modifier
                        .padding(start = 18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentCopper)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .height(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.premium_lifelist_badge),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 0.2.em,
                )
            }
        }
    }
}
```

(Återanvänder `premium_lifelist_badge` = "PREMIUM" för flaggan; inga nya strängar. `premiumGlow` importeras från `se.birdy.app.ui.components`.)

- [ ] **Step 2: Kompilera + lint**

Run:
```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"; export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :composeApp:assembleDebug ktlintCheck detekt
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapPremiumTeaser.kt
git commit -m "feat(premium): gör Karta-tabbens gateade läge till ett centrerat glödande kort"
```

---

## Task 5: Full build + on-device-verifiering (checkpoint)

**Files:** inga ändringar — verifiering.

- [ ] **Step 1: Full lokal testsvit + lint**

Run:
```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"; export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest ktlintCheck detekt
```
Expected: BUILD SUCCESSFUL, alla tester gröna.

- [ ] **Step 2: Installera debug-bygget**

Run:
```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"; export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :androidApp:installDebug
```
Expected: BUILD SUCCESSFUL. (Debug-paketet är `se.birdy.android.debug`.)

- [ ] **Step 3: Slå PÅ gratis-läget (debug-premium-OFF)**

Det gateade läget syns bara när premium är AV. Slå av premium-overriden via debug-toggeln (`DebugPremiumOverrides` — nåbar via Inställningar i debug-bygget; bekräfta exakt åtkomst i appen). **Be Albin om "händerna borta från telefonen"** innan ADB-driving (SM-S918B är hans dagliga telefon).

- [ ] **Step 4: Verifiera varje gatead yta som gratisanvändare**

Besök och bekräfta diskret, konsekvent glöd + locka-inte-låsa-känsla:
1. **Karta-tab** → centrerat glödande kort (inte platt text).
2. **Mina arter** (säsongsstat-rutan) → glänser, mjukt sudd, liten koppar-"Premium"-pill (inte stort lås), CTA "Se hela din säsong ›", ingen mörk dimning.
3. **En artprofil** → teaser-kortet glänser.
4. **Arkiv** → teaser-kortet glänser.
5. **Inställningar** → hero-kortet glänser i samma diskreta takt som övriga.

Bekräfta att glöden är subtil (inte skrikig) och att takten är likadan över alla ytor. Ta skärmdumpar för dokumentation. Radera ev. privat innehåll som råkat fångas i screencaps direkt.

- [ ] **Step 5: Slå tillbaka premium-override till PÅ + avinstallera debug-bygget**

Återställ debug-toggeln så standardläget (premium på för launch) gäller, och avinstallera debug-appen efter verify:
```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" uninstall se.birdy.android.debug
```

---

## Self-Review

**Spec coverage:**
- `premiumGlow()` enda tuning-punkt → Task 1. ✅
- Yta 1 `PremiumTeaserCard` glöd → Task 2. ✅
- Yta 2 `LockedStatsPreview` omramning (sudd, alpha, gradient bort, lås→pill, CTA) → Task 3. ✅
- Yta 3 `MapPremiumTeaser` glödande kort → Task 4. ✅
- Yta 4 `PremiumHeroCard` routad genom `premiumGlow()` → Task 1 Step 2. ✅
- CTA-sträng SV+EN → Task 3 Step 1–2. ✅
- On-device-verifiering med debug-premium-OFF → Task 5. ✅
- YAGNI (ingen guldpalett/gnistror/asset/omdöpning) → respekterad i all kod. ✅

**Placeholder scan:** Inga TBD/TODO; all kod fullständig i varje step.

**Type/namn-konsistens:** `premiumGlow(durationMillis, alpha)` definieras i Task 1 och anropas parameterlöst i Task 2/3/4 (defaults gäller) och i Task 1 för hero. `overlayBadge`/`overlayCta`/`title` matchar `LockedStatsPreview`-signaturen som call-site i `LifelistScreen.kt:330` redan skickar (oförändrad). `premium_lifelist_badge` återanvänds i både Task 3 och Task 4.
