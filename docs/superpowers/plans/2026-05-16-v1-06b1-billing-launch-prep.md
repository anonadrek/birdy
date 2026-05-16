# Plan 6b1 — Billing & Launch Prep Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the app closed-testing-ready by replacing the `purchase()` mock with real Google Play Billing v8, fixing EU Omnibus + dark-pattern violations in the Premium screen, throttling the cold-start modal, building ML preprocessing diagnos + test-image-infra, and shipping ops-prep (Pages hosting, TalkBack walkthrough, Closed Testing track).

**Architecture:** `PremiumBillingClient` `expect class` in `composeApp/commonMain` with Android `actual` wrapping `com.android.billingclient.api.BillingClient` v8; iOS `actual` = no-op stub. New `BillingPremiumRepository` implements existing `PremiumRepository` interface, reads DataStore cache + canonical truth from `queryPurchasesAsync`. `match_override.txt` test-infra gated via new `AppGraph.matchOverrideReader` lambda (release builds: null). ML diagnos exposed via new `AppGraph.diagnosticsScreen` lambda parallel to existing `benchmarkScreen` (Plan 4b pattern).

**Tech Stack:** Google Play Billing Library v8.0.0 (`com.android.billingclient:billing-ktx:8.0.0`), Kotlin Multiplatform expect/actual, AppGraph lambda-injection for DEBUG-only features, on-device RSA signature-verify via `java.security.Signature` (`SHA1withRSA`).

**Spec reference:** `docs/superpowers/specs/2026-05-16-v1-06b1-billing-launch-prep-design.md`

**Final tag:** `v0.9.0a-billing` (versionCode 110, versionName 1.0.0-rc2)

---

## File Structure

### New files

| Path | Purpose |
|---|---|
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/debug/DiagnosticsScreen.kt` | DEBUG-only screen: runs 3 corpus images through `ImagePreprocessor` + `TfLiteBirdClassifier`, dumps tensor + top-5 to `filesDir/preprocess_dump_*.json` |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchOverride.kt` | Data class `MatchOverride(qid: String, confidence: Float)` + parser `parseMatchOverride(text: String): MatchOverride?` |
| `composeApp/src/commonTest/kotlin/se/birdy/app/ui/match/MatchOverrideParserTest.kt` | Unit tests for parser (valid, malformed, edge cases) |
| `composeApp/src/commonMain/kotlin/se/birdy/app/data/premium/PremiumBillingClient.kt` | `expect class PremiumBillingClient` — connection lifecycle, `queryProducts`, `launchPurchase`, `queryPurchasesAsync`, `acknowledgePurchase`, signature-verify |
| `composeApp/src/androidMain/kotlin/se/birdy/app/data/premium/PremiumBillingClient.android.kt` | `actual class` wrapping `com.android.billingclient.api.BillingClient` v8 |
| `composeApp/src/iosMain/kotlin/se/birdy/app/data/premium/PremiumBillingClient.ios.kt` | No-op `actual class` — returns `Inactive` + no-op purchase |
| `composeApp/src/commonMain/kotlin/se/birdy/app/data/premium/BillingPremiumRepository.kt` | Implements `PremiumRepository`; reads DataStore-cache + canonical truth from `PremiumBillingClient.queryPurchasesAsync` |
| `composeApp/src/commonTest/kotlin/se/birdy/app/data/premium/BillingPremiumRepositoryTest.kt` | Tests against `FakePremiumBillingClient` for state-flip on purchase, restore, ack-fail |
| `composeApp/src/commonTest/kotlin/se/birdy/app/data/premium/FakePremiumBillingClient.kt` | Test fake exposing `setPurchases(...)` |
| `docs/superpowers/runbooks/2026-05-16-test-image-infra.md` | adb push/clear workflow for `match_override.txt` + example values per threshold band |
| `docs/superpowers/research/2026-05-16-ml-preprocessing-diagnos.md` | Output of T2 — root cause + fix-effort estimate (written during T2) |
| `docs/superpowers/research/2026-05-16-talkback-walkthrough.md` | Output of T9 — P0/P1/P2 issue triage |

### Modified files

| Path | Change |
|---|---|
| `composeApp/build.gradle.kts` | Add `com.android.billingclient:billing-ktx:8.0.0`; add `BuildConfig.PLAY_LICENSE_KEY` field |
| `gradle.properties.example` | Document `BIRDY_PLAY_LICENSE_KEY=` |
| `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt` | Add `diagnosticsScreen` + `matchOverrideReader` lambdas (parallel to existing `benchmarkScreen`) |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchResultViewModel.kt` | Call `matchOverrideReader` before threshold-routing if non-null |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumScreen.kt` | Remove `StampLabel` rendering; add `AutoRenewDisclosure` Caveat-sub under Yearly TierCard; use `state.formattedYearlyPrice` if non-null |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumUiState.kt` | Add `formattedYearlyPrice: String?`, `formattedLifetimePrice: String?` fields |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsScreen.kt` | Add "Restore purchases" / "Återställ köp" row |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsViewModel.kt` | Add `restorePurchases()` method emitting `SettingsEffect.ShowToast` |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsEffect.kt` | Add `ShowToast(text)` variant if not already present |
| `composeApp/src/commonMain/kotlin/se/birdy/app/premium/EntryFlowDecider.kt` | Replace day-based throttle with 3d-throttle + 7d first-install grace |
| `composeApp/src/commonTest/kotlin/se/birdy/app/premium/EntryFlowDeciderTest.kt` | New test cases for grace + throttle boundaries |
| `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/UserPreferences.kt` | Add `firstInstallTimestamp: Flow<Long?>` + `setFirstInstallTimestamp(ms: Long)` |
| `shared/datastore/src/androidMain/kotlin/se/birdy/datastore/UserPreferencesStore.android.kt` | Implement DataStore key for `firstInstallTimestamp` |
| `shared/datastore/src/iosMain/kotlin/se/birdy/datastore/UserPreferencesStore.ios.kt` | Stub implementation (returns null/no-op) |
| `shared/datastore/src/jvmMain/kotlin/se/birdy/datastore/UserPreferencesStore.jvm.kt` | Test stub |
| `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt` | Wire `matchOverrideReader`, `diagnosticsScreen`, cold-start `queryPurchasesAsync`, first-install-timestamp migration |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | Remove `premium_tier_yearly_stamp`; add `premium_auto_renew_disclosure`; add `restore_purchases_row` + `restore_purchases_success` + `restore_purchases_empty` |
| `composeApp/src/commonMain/composeResources/values-en/strings.xml` | Fix `199 kr` → `199 SEK`; remove stamp; add disclosure + restore strings |

---

## Pre-flight: Set up environment

- [ ] **Step 0.1: Verify JDK 21 + Android SDK on PATH**

Run:
```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```
Expected: `openjdk version "21.0.11"`.

- [ ] **Step 0.2: Confirm clean working tree on main**

Run: `git status`
Expected: branch `main`, no staged changes (untracked `shared/content/images/Q*` dirs are OK — they're committed via content pipeline).

- [ ] **Step 0.3: Confirm baseline build is green**

Run: `./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

---

## Task T0: Start Closed Testing track (ops)

**Files:** none (Play Console actions only)

This task happens entirely in Play Console + email. No code touched. Track progress externally.

- [ ] **Step T0.1: Create personal Play Console developer account TODAY**

**Decision locked 2026-05-16 (see `project_dev_account_decision.md`):** Personal account NOW (not org/AB) — AB-bolagsbildning is pending and D-U-N-S/Bolagsverket-route would miss the 2026-05-18 Closed Testing deadline.

User actions (must be done by user, not Claude):
1. Sign up at https://play.google.com/console/signup as **personal** developer.
2. Pay $25 one-time fee.
3. Complete ID verification (passport scan + selfie via Google's flow).
4. Wait for approval email (24-48h typical, max 7d).
5. While waiting → recruit 12 testers for T0.4 in parallel.

Post-launch migration to AB: Google Play Account Transfer ($25 + 1-2v handläggning) flyttar app + IAP-historik, men nollställer recensioner/betyg — gör transferen tidigt efter launch.

- [ ] **Step T0.2: Generate signed AAB from current v0.8.0-rc1 main**

Run:
```bash
./gradlew :androidApp:bundleRelease
```
Expected: output at `androidApp/build/outputs/bundle/release/androidApp-release.aab`. Keep this AAB — Step T0.4 uploads it.

- [ ] **Step T0.3: Create Internal Testing track first (backup)**

In Play Console: Testing → Internal testing → Create track → upload AAB from T0.2 → add `albinviktorlindblom@gmail.com` as tester → enable.
Expected: opt-in URL available; instant access (no Google review delay).

- [ ] **Step T0.4: Create Closed Testing track (primary, 14-day timer)**

In Play Console: Testing → Closed testing → Create new track "Birdy launch testing" → upload AAB → add 12 testers (recruit from `Fåglar inpå knuten` Facebook group, personal contacts, Reddit r/birding). Note: 12 testers MUST opt-in for the 14-day timer to start; Google counts unique opt-ins.

- [ ] **Step T0.5: Record start date in memory**

Update auto-memory `project_play_store_launch_research.md` to add closed-testing-start-date so countdown is trackable across sessions.

- [ ] **Step T0.6: Mark T0 complete**

No commit (no code change). Update plan checkbox.

---

## Task T1: GitHub Pages hosting for Privacy/Terms

**Files:**
- Modify: `.github/workflows/pages.yml` (verify settings)
- Verify in browser: `https://anonadrek.github.io/birdy/privacy.html`

- [ ] **Step T1.1: Inspect existing pages.yml workflow**

Run: `cat .github/workflows/pages.yml`
Expected: pandoc-based markdown→HTML conversion that emits `privacy.html` + `terms.html`. If file missing, escalate — Plan 6a should have created it.

- [ ] **Step T1.2: Enable GitHub Pages in repo settings via gh CLI**

Run:
```bash
gh api repos/anonadrek/birdy/pages -X POST -f source[branch]=gh-pages -f source[path]=/ 2>/dev/null || \
gh api repos/anonadrek/birdy/pages -X POST --input - <<'EOF'
{"build_type":"workflow"}
EOF
```
Expected: 201 Created OR 409 Conflict (already enabled). If 409, that's fine.

- [ ] **Step T1.3: Trigger pages.yml workflow manually**

Run: `gh workflow run pages.yml -R anonadrek/birdy`
Expected: queued. Wait ~60s.

- [ ] **Step T1.4: Verify workflow succeeded**

Run: `gh run list --workflow=pages.yml -R anonadrek/birdy --limit 1`
Expected: status `completed`, conclusion `success`. If failed, read logs via `gh run view <id> --log` and fix pandoc-conversion issue.

- [ ] **Step T1.5: Verify both URLs return 200**

Run:
```bash
curl -sI https://anonadrek.github.io/birdy/privacy.html | head -1
curl -sI https://anonadrek.github.io/birdy/terms.html | head -1
```
Expected: both return `HTTP/2 200`.

- [ ] **Step T1.6: Commit any workflow tweaks needed**

If T1.1–T1.4 required workflow edits:
```bash
git add .github/workflows/pages.yml
git commit -m "$(cat <<'EOF'
ops(pages): enable GitHub Pages workflow + verify privacy/terms URLs return 200

Activates pages.yml workflow that emits privacy.html and terms.html from
docs/play-store markdown sources. Verified via curl that both URLs are
reachable for Play Console submission.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

If no edits were needed (T1.5 already returned 200 without workflow changes), skip the commit and proceed.

---

## Task T2: ML preprocessing Phase 1 diagnos

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/debug/DiagnosticsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/EncyclopediaScreen.kt` (overflow menu)
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt` (lambda wiring)
- Output: `docs/superpowers/research/2026-05-16-ml-preprocessing-diagnos.md`

- [ ] **Step T2.1: Add `diagnosticsScreen` lambda to AppGraph**

Edit `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`. Add new parameter after `benchmarkScreen`:

```kotlin
    /**
     * Non-null = show ML preprocessing-diagnos route + overflow menu item.
     * Null = release builds. Lambda is androidMain-only; defined in MainActivity.
     */
    val diagnosticsScreen: (@Composable () -> Unit)? = null,
```

- [ ] **Step T2.2: Create DiagnosticsScreen**

Create `composeApp/src/commonMain/kotlin/se/birdy/app/ui/debug/DiagnosticsScreen.kt`:

```kotlin
package se.birdy.app.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DiagnosticsScreen(
    runDiagnostic: suspend () -> String,
) {
    var log by remember { mutableStateOf("Tap Run to start.") }
    var running by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("ML Preprocessing Diagnos")
        Button(onClick = { running = true }, enabled = !running) {
            Text(if (running) "Running…" else "Run diagnos")
        }
        Text(log)
    }
    LaunchedEffect(running) {
        if (running) {
            log = "Running…"
            log = runDiagnostic()
            running = false
        }
    }
}
```

The `runDiagnostic` lambda is Android-implemented and dumps per-image: device top-5, input-tensor sample bytes (first 32 pixels), preprocessor metadata (size, mean/std).

- [ ] **Step T2.3: Implement Android-side diagnostic runner**

Create `composeApp/src/androidMain/kotlin/se/birdy/app/ui/debug/DiagnosticsRunner.kt`:

```kotlin
package se.birdy.app.ui.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.birdy.ml.BirdClassifier
import se.birdy.ml.ImageInput
import java.io.File

class DiagnosticsRunner(
    private val context: Context,
    private val classifier: BirdClassifier,
) {
    suspend fun run(): String = withContext(Dispatchers.IO) {
        val report = StringBuilder()
        val assetNames = listOf("benchmark/bird1.jpg", "benchmark/bird2.jpg", "benchmark/bird3.jpg")
        for (name in assetNames) {
            report.appendLine("=== $name ===")
            val bytes = context.assets.open(name).use { it.readBytes() }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            report.appendLine("Decoded ${bitmap.width}×${bitmap.height}")
            val sampledPixels = (0..7).map { idx ->
                val x = (idx * bitmap.width / 8).coerceAtMost(bitmap.width - 1)
                bitmap.getPixel(x, bitmap.height / 2)
            }
            report.appendLine("Mid-row sampled ARGB: $sampledPixels")
            val input = ImageInput.JpegBytes(bytes)
            val result = classifier.classify(input)
            report.appendLine("Top-5: ${result.predictions.take(5).joinToString { "${it.speciesId.raw}:${"%.3f".format(it.confidence)}" }}")
            report.appendLine()
        }
        val outFile = File(context.filesDir, "preprocess_dump_${System.currentTimeMillis()}.txt")
        outFile.writeText(report.toString())
        report.appendLine("Written to: ${outFile.absolutePath}")
        report.toString()
    }
}
```

(Adjust `ImageInput.JpegBytes` constructor signature if it differs — check `shared/ml/.../BirdClassifier.kt` for the actual `ImageInput` API. If `classify()` is suspend, the call works; if it requires different shape, mirror what `BenchmarkScreen` does.)

- [ ] **Step T2.4: Wire `diagnosticsScreen` lambda in MainActivity**

In `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt`, add a sibling to `buildBenchmarkScreen`:

```kotlin
    private fun buildDiagnosticsScreen(bootstrap: ClassifierBootstrap): (@Composable () -> Unit)? =
        if (BuildConfig.DEBUG) {
            @Composable {
                val ready = bootstrap.state.collectAsState().value as? ClassifierBootstrapState.Ready
                if (ready != null) {
                    val runner = remember { se.birdy.app.ui.debug.DiagnosticsRunner(applicationContext, ready.classifier) }
                    se.birdy.app.ui.debug.DiagnosticsScreen(runDiagnostic = { runner.run() })
                }
            }
        } else {
            null
        }
```

Add to `AppGraph(...)` construction:
```kotlin
            diagnosticsScreen = buildDiagnosticsScreen(classifierBootstrap),
```

Add `import androidx.compose.runtime.remember` at top of file.

- [ ] **Step T2.5: Add overflow-menu entry in EncyclopediaScreen**

Find the existing benchmark overflow item in `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/EncyclopediaScreen.kt`. Add sibling:

```kotlin
if (graph.diagnosticsScreen != null) {
    DropdownMenuItem(
        text = { Text("ML diagnos") },
        onClick = { navController.navigate(AppRoute.DebugDiagnostics.route) }
    )
}
```

Add `AppRoute.DebugDiagnostics` route in navigation graph (mirror `AppRoute.DebugBenchmark` setup — search codebase for `DebugBenchmark` and copy pattern).

- [ ] **Step T2.6: Add 3 corpus benchmark images if missing**

Run: `ls composeApp/src/androidMain/assets/benchmark/`
If empty or contains placeholder JPEGs, copy 3 representative bird photos from `tools/ml-eval/corpus/` to `composeApp/src/androidMain/assets/benchmark/bird1.jpg`, `bird2.jpg`, `bird3.jpg`. Use diverse species (e.g. talgoxe, koltrast, blames).

- [ ] **Step T2.7: Build + install + run diagnos**

Run:
```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

Navigate Encyclopedia → overflow → ML diagnos → Run. Wait ~5s.

- [ ] **Step T2.8: Pull diagnos report**

Run:
```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" exec-out run-as se.birdy.android cat 'files/preprocess_dump_*.txt' > docs/superpowers/research/2026-05-16-ml-preprocessing-diagnos-device.txt
```

- [ ] **Step T2.9: Run desktop eval on same 3 images**

Run:
```bash
cd tools/ml-eval
uv run python -c "
from pathlib import Path
from accuracy import classify_image
for name in ['bird1.jpg', 'bird2.jpg', 'bird3.jpg']:
    src = Path('../../composeApp/src/androidMain/assets/benchmark') / name
    print(f'=== {name} ===')
    print(classify_image(src))
"
```
(Adjust function name if `tools/ml-eval/accuracy.py` exposes a different API.)

Save output to `docs/superpowers/research/2026-05-16-ml-preprocessing-diagnos-desktop.txt`.

- [ ] **Step T2.10: Compare device vs desktop + write root-cause analysis**

Create `docs/superpowers/research/2026-05-16-ml-preprocessing-diagnos.md`:

```markdown
# ML Preprocessing Phase 1 Diagnos

**Date:** 2026-05-16
**Spec:** Plan 6b1 T2

## Method
Ran 3 bench images through device (`DiagnosticsScreen`) and desktop (`tools/ml-eval`). Compared top-5 predictions + mid-row ARGB samples.

## Results
[paste device vs desktop output side-by-side]

## Root cause hypothesis
- Hypothesis (a) rotation: [TRUE/FALSE based on data]
- Hypothesis (b) stretch vs center-crop: [TRUE/FALSE]
- Hypothesis (c) RGB/BGR swap: [TRUE/FALSE]
- Other: [if data points to something else]

## Fix effort estimate
[hours/days]

## Recommendation
- [ ] Ship fix in 6b1 T8
- [ ] Defer post-v1.0; use threshold-fallback (a) per Risk 4 mitigation
```

Fill in actual data from T2.8 + T2.9. Document the root cause + effort.

- [ ] **Step T2.11: Commit T2**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/debug/ \
        composeApp/src/androidMain/kotlin/se/birdy/app/ui/debug/ \
        composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/EncyclopediaScreen.kt \
        androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt \
        composeApp/src/androidMain/assets/benchmark/ \
        docs/superpowers/research/2026-05-16-ml-preprocessing-diagnos*.{md,txt}
git commit -m "$(cat <<'EOF'
feat(plan-6b1/t2): ML preprocessing Phase 1 diagnos screen + root-cause report

Adds DEBUG-only DiagnosticsScreen (gated via AppGraph.diagnosticsScreen
lambda, parallel to existing benchmarkScreen from Plan 4b) that runs 3
corpus images through ImagePreprocessor + TfLiteBirdClassifier and dumps
top-5 + mid-row ARGB samples for comparison against desktop eval output.

Root-cause analysis in docs/superpowers/research/2026-05-16-ml-preprocessing-diagnos.md
determines whether to ship a fix in T8 or defer per Risk 4 fallback.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task T3: Test-image-infra (`match_override.txt`)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchOverride.kt`
- Create: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/match/MatchOverrideParserTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchResultViewModel.kt`
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt`
- Create: `docs/superpowers/runbooks/2026-05-16-test-image-infra.md`

- [ ] **Step T3.1: Write failing parser tests**

Create `composeApp/src/commonTest/kotlin/se/birdy/app/ui/match/MatchOverrideParserTest.kt`:

```kotlin
package se.birdy.app.ui.match

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchOverrideParserTest {
    @Test fun `parses valid Q-id and confidence`() {
        val result = parseMatchOverride("Q25356:0.42")
        assertEquals(MatchOverride("Q25356", 0.42f), result)
    }

    @Test fun `returns null on missing colon`() {
        assertNull(parseMatchOverride("Q25356"))
    }

    @Test fun `returns null on non-Q id`() {
        assertNull(parseMatchOverride("X25356:0.42"))
    }

    @Test fun `returns null on non-numeric confidence`() {
        assertNull(parseMatchOverride("Q25356:high"))
    }

    @Test fun `returns null on confidence out of range`() {
        assertNull(parseMatchOverride("Q25356:1.5"))
        assertNull(parseMatchOverride("Q25356:-0.1"))
    }

    @Test fun `trims whitespace`() {
        assertEquals(MatchOverride("Q25356", 0.5f), parseMatchOverride("  Q25356 : 0.5  \n"))
    }

    @Test fun `accepts boundaries`() {
        assertEquals(MatchOverride("Q1", 0.0f), parseMatchOverride("Q1:0.0"))
        assertEquals(MatchOverride("Q1", 1.0f), parseMatchOverride("Q1:1.0"))
    }
}
```

- [ ] **Step T3.2: Run tests — confirm they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*MatchOverrideParserTest*"`
Expected: FAIL — `MatchOverride` and `parseMatchOverride` undefined.

- [ ] **Step T3.3: Implement `MatchOverride` + parser**

Create `composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchOverride.kt`:

```kotlin
package se.birdy.app.ui.match

/**
 * DEBUG-only override for MatchResultViewModel routing. Pushed via:
 *   adb push override.txt /data/data/se.birdy.android/files/match_override.txt
 * See docs/superpowers/runbooks/2026-05-16-test-image-infra.md.
 */
data class MatchOverride(val qid: String, val confidence: Float)

private val QID_REGEX = Regex("^Q\\d+$")

fun parseMatchOverride(text: String): MatchOverride? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val qid = parts[0].trim()
    val confStr = parts[1].trim()
    if (!QID_REGEX.matches(qid)) return null
    val conf = confStr.toFloatOrNull() ?: return null
    if (conf !in 0.0f..1.0f) return null
    return MatchOverride(qid, conf)
}
```

- [ ] **Step T3.4: Run tests — confirm they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*MatchOverrideParserTest*"`
Expected: 7 tests PASS.

- [ ] **Step T3.5: Add `matchOverrideReader` to AppGraph**

In `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`, add parameter after `diagnosticsScreen`:

```kotlin
    /**
     * DEBUG-only hook for deterministic Match/Disambig/NoBird testing.
     * Reads files/match_override.txt; null on release builds.
     * See Plan 6b1 T3 + docs/superpowers/runbooks/2026-05-16-test-image-infra.md.
     */
    val matchOverrideReader: (() -> MatchOverride?)? = null,
```

Add import:
```kotlin
import se.birdy.app.ui.match.MatchOverride
```

Pass through to `matchResultViewModel(...)` factory by adding a `matchOverrideReader` parameter to its constructor (next step).

- [ ] **Step T3.6: Wire override into MatchResultViewModel**

Edit `composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchResultViewModel.kt`. Add constructor parameter:

```kotlin
class MatchResultViewModel(
    private val repository: SpeciesRepository,
    private val observationRepo: ObservationRepository,
    private val saveUseCase: SaveObservationUseCase,
    private val catalog: BadgeCatalog,
    private val predictionsCsv: String,
    private val frameJpegPath: String?,
    private val capturedAtMs: Long,
    private val locale: Locale,
    private val matchOverrideReader: (() -> MatchOverride?)? = null,
) : ViewModel() {
```

In `resolve()`, before threshold-routing, check override. Replace the block starting `val top1 = resolved.first()` with:

```kotlin
        val override = matchOverrideReader?.invoke()
        val effective: List<ResolvedPrediction> = if (override != null) {
            // Find species by qid; if not found, fall through to natural pipeline.
            val species = runCatching { repository.getById(SpeciesId(override.qid), locale).first() }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
            if (species != null) {
                val overridden = ResolvedPrediction(species, override.confidence)
                // Keep runners-up but replace top-1
                listOf(overridden) + resolved.drop(1).take(2)
            } else {
                resolved
            }
        } else {
            resolved
        }
        val top1 = effective.first()
```

Then replace remaining references to `resolved` in the `when` block with `effective` (specifically the `MatchRoute.DISAMBIG` branch's filter).

- [ ] **Step T3.7: Wire factory to pass `matchOverrideReader`**

In `AppGraph.matchResultViewModel(...)`, pass `matchOverrideReader = matchOverrideReader`:

```kotlin
    fun matchResultViewModel(
        predictionsCsv: String,
        frameJpegPath: String?,
        capturedAtMs: Long,
    ): MatchResultViewModel =
        MatchResultViewModel(
            repository = repository,
            observationRepo = observationRepository,
            saveUseCase = saveObservationUseCase,
            catalog = badgeCatalog,
            predictionsCsv = predictionsCsv,
            frameJpegPath = frameJpegPath,
            capturedAtMs = capturedAtMs,
            locale = defaultLocale,
            matchOverrideReader = matchOverrideReader,
        )
```

- [ ] **Step T3.8: Wire DEBUG reader in MainActivity**

In `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt`, add helper:

```kotlin
    private fun buildMatchOverrideReader(): (() -> se.birdy.app.ui.match.MatchOverride?)? =
        if (BuildConfig.DEBUG) {
            {
                val f = File(filesDir, "match_override.txt")
                if (f.exists()) {
                    runCatching { se.birdy.app.ui.match.parseMatchOverride(f.readText()) }
                        .getOrNull()
                } else {
                    null
                }
            }
        } else {
            null
        }
```

Pass to `AppGraph(...)` construction:
```kotlin
            matchOverrideReader = buildMatchOverrideReader(),
```

- [ ] **Step T3.9: Write runbook**

Create `docs/superpowers/runbooks/2026-05-16-test-image-infra.md`:

```markdown
# Test Image Infra — match_override.txt

DEBUG-only mechanism for deterministic Match/Disambig/NoBird screenshots
when real TFLite confidences don't naturally land in the desired band.

## Format

Single line, `qid:confidence`. Examples:
- `Q25356:0.42` → Disambig band (talgoxe, conf inside 0.35–0.50)
- `Q25356:0.65` → Match band (high confidence)
- `Q25356:0.05` → NoBird band (very low)

## Push override

```bash
echo "Q25356:0.42" > /tmp/override.txt
adb push /tmp/override.txt /data/data/se.birdy.android/files/match_override.txt
```

(On Windows: replace `/tmp/` with `%TEMP%` or use forward-slash mingw paths.)

## Verify it's read

After pushing, trigger a Scan → MatchResult flow. The result screen should
render in the band corresponding to the overridden confidence (Disambig for
0.35–0.50, Match for ≥ 0.50, NoBird for < 0.35).

The override is read on every MatchResultViewModel init. The file is NOT
deleted after read so the same override applies to repeated scans until
removed.

## Clear override

```bash
adb shell run-as se.birdy.android rm files/match_override.txt
```

Or rotate device → next scan goes back to real classifier output.

## Common QIDs for testing

| qid | species |
|---|---|
| Q25356 | Parus major (talgoxe) |
| Q133348 | Turdus merula (koltrast) |
| Q133376 | Cyanistes caeruleus (blames) |
| Q183670 | Erithacus rubecula (rödhake) |

## Release-build behavior

`AppGraph.matchOverrideReader == null` on release builds. The file is
never read; MatchResultViewModel falls through to real classifier output.
Safe to ship; no risk of test-only behavior leaking into production.
```

- [ ] **Step T3.10: Run all tests + manual smoke test**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

Then install + adb-push an override + verify on device:
```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
echo "Q25356:0.42" > /tmp/override.txt
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" push /tmp/override.txt /data/local/tmp/match_override.txt
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell run-as se.birdy.android cp /data/local/tmp/match_override.txt files/match_override.txt
```

Take a Scan → verify MatchResultScreen renders in Disambig mode.

- [ ] **Step T3.11: Commit T3**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchOverride.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/ui/match/MatchOverrideParserTest.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchResultViewModel.kt \
        androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt \
        docs/superpowers/runbooks/2026-05-16-test-image-infra.md
git commit -m "$(cat <<'EOF'
feat(plan-6b1/t3): match_override.txt test-image-infra for deterministic Match flows

Adds DEBUG-only AppGraph.matchOverrideReader lambda that reads
files/match_override.txt (qid:confidence) and overrides the top-1
prediction before MatchResultViewModel does threshold-routing. Unlocks
deterministic Disambig/NoBird/Match screenshot driving without needing
specially-crafted physical bird photos.

Release builds: reader is null, file is never read. Safe in production.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task T4: Google Play Billing v8 integration

**Files:**
- Modify: `composeApp/build.gradle.kts`
- Modify: `gradle.properties.example`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/data/premium/PremiumBillingClient.kt` (expect)
- Create: `composeApp/src/androidMain/kotlin/se/birdy/app/data/premium/PremiumBillingClient.android.kt` (actual)
- Create: `composeApp/src/iosMain/kotlin/se/birdy/app/data/premium/PremiumBillingClient.ios.kt` (no-op actual)
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/data/premium/BillingPremiumRepository.kt`
- Create: `composeApp/src/commonTest/kotlin/se/birdy/app/data/premium/FakePremiumBillingClient.kt`
- Create: `composeApp/src/commonTest/kotlin/se/birdy/app/data/premium/BillingPremiumRepositoryTest.kt`
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt`

- [ ] **Step T4.1: Add Billing v8 dependency + BuildConfig key**

Edit `composeApp/build.gradle.kts`. In `androidMain.dependencies`:
```kotlin
            implementation("com.android.billingclient:billing-ktx:8.0.0")
```

In `android { defaultConfig { ... } }`, add `buildConfigField`:
```kotlin
        buildConfigField(
            "String",
            "PLAY_LICENSE_KEY",
            "\"${project.findProperty("BIRDY_PLAY_LICENSE_KEY") ?: ""}\"",
        )
```

Verify `buildFeatures { buildConfig = true }` is already set (from Plan 4b).

- [ ] **Step T4.2: Document gradle.properties.example**

Edit `gradle.properties.example` (create if missing):
```properties
# Google Play Billing v8 — paste from Play Console → Monetization Setup → Licensing public key
# Local-only; never commit actual key value.
BIRDY_PLAY_LICENSE_KEY=
```

Add to `.gitignore` if not already there:
```bash
grep -q "gradle.properties$" .gitignore || echo "gradle.properties" >> .gitignore
```

Create local `gradle.properties` if missing (user must fill key):
```bash
[ -f gradle.properties ] || cp gradle.properties.example gradle.properties
```

User fills `BIRDY_PLAY_LICENSE_KEY=<base64>` from Play Console manually.

- [ ] **Step T4.3: Define `expect class PremiumBillingClient`**

Create `composeApp/src/commonMain/kotlin/se/birdy/app/data/premium/PremiumBillingClient.kt`:

```kotlin
package se.birdy.app.data.premium

import kotlinx.coroutines.flow.StateFlow
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier

/**
 * Thin Android Billing v8 wrapper exposed as expect/actual for KMP.
 * - Android actual: wraps com.android.billingclient.api.BillingClient
 * - iOS actual: no-op stub (returns Inactive, throws on launchPurchase)
 *
 * BillingClient lifecycle (connect/disconnect) is handled internally;
 * call `connect()` once at app start and `dispose()` on Activity destroy.
 */
expect class PremiumBillingClient {
    val state: StateFlow<PremiumState>
    val formattedPrices: StateFlow<FormattedPrices>

    suspend fun connect()
    suspend fun queryPurchases()
    suspend fun launchPurchase(activityContext: Any, tier: PremiumTier): PurchaseResult
    fun dispose()
}

data class FormattedPrices(
    val yearly: String? = null,
    val lifetime: String? = null,
)

sealed interface PurchaseResult {
    data object Success : PurchaseResult
    data object UserCancelled : PurchaseResult
    data class Error(val message: String) : PurchaseResult
}
```

- [ ] **Step T4.4: Implement Android actual**

Create `composeApp/src/androidMain/kotlin/se/birdy/app/data/premium/PremiumBillingClient.android.kt`:

```kotlin
package se.birdy.app.data.premium

import android.app.Activity
import android.content.Context
import android.util.Base64
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Clock
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.coroutines.resume

private const val TAG = "PremiumBilling"
private const val YEARLY_PRODUCT_ID = "premium_yearly_v1"
private const val LIFETIME_PRODUCT_ID = "premium_lifetime_v1"

actual class PremiumBillingClient(
    private val context: Context,
    private val licensePublicKeyBase64: String,
) {
    private val _state = MutableStateFlow<PremiumState>(PremiumState.Free)
    actual val state: StateFlow<PremiumState> = _state.asStateFlow()

    private val _formattedPrices = MutableStateFlow(FormattedPrices())
    actual val formattedPrices: StateFlow<FormattedPrices> = _formattedPrices.asStateFlow()

    private var purchaseDeferred: CompletableDeferred<PurchaseResult>? = null
    private var yearlyDetails: ProductDetails? = null
    private var lifetimeDetails: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases ->
            handlePurchasesUpdate(result, purchases)
        }
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    actual suspend fun connect() {
        suspendCancellableCoroutine<Unit> { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        if (cont.isActive) cont.resume(Unit)
                    } else {
                        Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
                override fun onBillingServiceDisconnected() {
                    Log.w(TAG, "Billing service disconnected")
                }
            })
        }
        queryProducts()
    }

    private suspend fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(YEARLY_PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build(),
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(LIFETIME_PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build(),
            ))
            .build()
        val (result, details) = suspendCancellableCoroutine<Pair<BillingResult, List<ProductDetails>>> { cont ->
            client.queryProductDetailsAsync(params) { r, list ->
                if (cont.isActive) cont.resume(r to list)
            }
        }
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            yearlyDetails = details.firstOrNull { it.productId == YEARLY_PRODUCT_ID }
            lifetimeDetails = details.firstOrNull { it.productId == LIFETIME_PRODUCT_ID }
            _formattedPrices.value = FormattedPrices(
                yearly = yearlyDetails?.subscriptionOfferDetails
                    ?.firstOrNull()?.pricingPhases?.pricingPhaseList
                    ?.firstOrNull()?.formattedPrice,
                lifetime = lifetimeDetails?.oneTimePurchaseOfferDetails?.formattedPrice,
            )
        } else {
            Log.w(TAG, "queryProductDetails failed: ${result.debugMessage}")
        }
    }

    actual suspend fun queryPurchases() {
        val subsResult = suspendCancellableCoroutine<List<Purchase>> { cont ->
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ) { _, list -> if (cont.isActive) cont.resume(list) }
        }
        val inappResult = suspendCancellableCoroutine<List<Purchase>> { cont ->
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            ) { _, list -> if (cont.isActive) cont.resume(list) }
        }
        val active = (subsResult + inappResult).firstOrNull { p ->
            p.purchaseState == Purchase.PurchaseState.PURCHASED && verifySignature(p)
        }
        _state.value = active?.toPremiumState() ?: PremiumState.Free
    }

    actual suspend fun launchPurchase(activityContext: Any, tier: PremiumTier): PurchaseResult {
        val details = when (tier) {
            PremiumTier.YEARLY -> yearlyDetails
            PremiumTier.LIFETIME -> lifetimeDetails
        } ?: return PurchaseResult.Error("Product details not loaded")

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .apply {
                        if (tier == PremiumTier.YEARLY) {
                            val token = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
                            if (token != null) setOfferToken(token)
                        }
                    }
                    .build()
            ))
            .build()

        val deferred = CompletableDeferred<PurchaseResult>()
        purchaseDeferred = deferred
        val launchResult = client.launchBillingFlow(activityContext as Activity, flowParams)
        if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
            purchaseDeferred = null
            return PurchaseResult.Error("launchBillingFlow failed: ${launchResult.debugMessage}")
        }
        return deferred.await()
    }

    private fun handlePurchasesUpdate(result: BillingResult, purchases: List<Purchase>?) {
        val deferred = purchaseDeferred
        purchaseDeferred = null
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val list = purchases.orEmpty()
                val verified = list.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED && verifySignature(it) }
                if (verified != null) {
                    if (!verified.isAcknowledged) {
                        acknowledge(verified) { ackResult ->
                            if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                _state.value = verified.toPremiumState()
                                deferred?.complete(PurchaseResult.Success)
                            } else {
                                deferred?.complete(PurchaseResult.Error("ack failed: ${ackResult.debugMessage}"))
                            }
                        }
                    } else {
                        _state.value = verified.toPremiumState()
                        deferred?.complete(PurchaseResult.Success)
                    }
                } else {
                    deferred?.complete(PurchaseResult.Error("No verified purchase in callback"))
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                deferred?.complete(PurchaseResult.UserCancelled)
            }
            else -> {
                deferred?.complete(PurchaseResult.Error(result.debugMessage))
            }
        }
    }

    private fun acknowledge(purchase: Purchase, callback: (BillingResult) -> Unit) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params) { result -> callback(result) }
    }

    private fun verifySignature(purchase: Purchase): Boolean {
        if (licensePublicKeyBase64.isBlank()) {
            Log.w(TAG, "PLAY_LICENSE_KEY missing; skipping signature verification (debug only)")
            return BuildConfig.DEBUG
        }
        return try {
            val keyBytes = Base64.decode(licensePublicKeyBase64, Base64.DEFAULT)
            val publicKey: PublicKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(keyBytes))
            val sig = Signature.getInstance("SHA1withRSA")
            sig.initVerify(publicKey)
            sig.update(purchase.originalJson.toByteArray())
            val sigBytes = Base64.decode(purchase.signature, Base64.DEFAULT)
            sig.verify(sigBytes)
        } catch (t: Throwable) {
            Log.e(TAG, "Signature verification failed", t)
            false
        }
    }

    private fun Purchase.toPremiumState(): PremiumState {
        val tier = when {
            products.contains(YEARLY_PRODUCT_ID) -> PremiumTier.YEARLY
            products.contains(LIFETIME_PRODUCT_ID) -> PremiumTier.LIFETIME
            else -> return PremiumState.Free
        }
        return PremiumState.Active(tier, Clock.System.now())
    }

    actual fun dispose() {
        client.endConnection()
    }
}
```

Add import in androidMain for `se.birdy.app.BuildConfig` (verify package matches).

- [ ] **Step T4.5: Implement iOS no-op actual**

Create `composeApp/src/iosMain/kotlin/se/birdy/app/data/premium/PremiumBillingClient.ios.kt`:

```kotlin
package se.birdy.app.data.premium

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier

actual class PremiumBillingClient {
    private val _state = MutableStateFlow<PremiumState>(PremiumState.Free)
    actual val state: StateFlow<PremiumState> = _state.asStateFlow()

    private val _formattedPrices = MutableStateFlow(FormattedPrices())
    actual val formattedPrices: StateFlow<FormattedPrices> = _formattedPrices.asStateFlow()

    actual suspend fun connect() = Unit
    actual suspend fun queryPurchases() = Unit
    actual suspend fun launchPurchase(activityContext: Any, tier: PremiumTier): PurchaseResult =
        PurchaseResult.Error("iOS billing not implemented")
    actual fun dispose() = Unit
}
```

- [ ] **Step T4.6: Write `BillingPremiumRepository` tests first**

Create `composeApp/src/commonTest/kotlin/se/birdy/app/data/premium/FakePremiumBillingClient.kt`:

```kotlin
package se.birdy.app.data.premium

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier

class FakePremiumBillingClient {
    private val _state = MutableStateFlow<PremiumState>(PremiumState.Free)
    val state: StateFlow<PremiumState> = _state.asStateFlow()
    val formattedPrices = MutableStateFlow(FormattedPrices("199 SEK / year", "499 SEK"))
    var purchasesQueried = 0
    var purchaseLaunched: PremiumTier? = null
    var nextPurchaseResult: PurchaseResult = PurchaseResult.Success
    var disposed = false

    fun setActive(tier: PremiumTier) {
        _state.value = PremiumState.Active(tier, kotlinx.datetime.Clock.System.now())
    }

    fun setFree() {
        _state.value = PremiumState.Free
    }

    // mimics actual PremiumBillingClient surface for repository wiring
}
```

Create `composeApp/src/commonTest/kotlin/se/birdy/app/data/premium/BillingPremiumRepositoryTest.kt`:

```kotlin
package se.birdy.app.data.premium

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier

class BillingPremiumRepositoryTest {
    @Test fun `initial state is Free`() = runTest {
        val fake = FakePremiumBillingClient()
        val repo = BillingPremiumRepository(fake.state, queryPurchases = { fake.purchasesQueried++ })
        assertIs<PremiumState.Free>(repo.state.value)
    }

    @Test fun `state flips to Active when billing emits Active`() = runTest {
        val fake = FakePremiumBillingClient()
        val repo = BillingPremiumRepository(fake.state, queryPurchases = { fake.purchasesQueried++ })
        fake.setActive(PremiumTier.YEARLY)
        kotlinx.coroutines.yield()
        assertIs<PremiumState.Active>(repo.state.value)
    }

    @Test fun `restore calls queryPurchases`() = runTest {
        val fake = FakePremiumBillingClient()
        val repo = BillingPremiumRepository(fake.state, queryPurchases = { fake.purchasesQueried++ })
        repo.restore()
        assert(fake.purchasesQueried == 1)
    }
}
```

- [ ] **Step T4.7: Run tests — confirm fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*BillingPremiumRepositoryTest*"`
Expected: FAIL — `BillingPremiumRepository` undefined.

- [ ] **Step T4.8: Implement `BillingPremiumRepository`**

Create `composeApp/src/commonMain/kotlin/se/birdy/app/data/premium/BillingPremiumRepository.kt`:

```kotlin
package se.birdy.app.data.premium

import kotlinx.coroutines.flow.StateFlow
import se.birdy.domain.premium.PremiumRepository
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier

/**
 * Plan 6b1: replaces the DataStore-only stub from Plan 7e.
 * - `state` is sourced from the wrapped PremiumBillingClient
 * - `markPurchased` is a no-op here; UI flow drives launchPurchase via PremiumViewModel.purchase()
 *   which sees state changes through `state` flow.
 * - `restore` triggers a fresh queryPurchases.
 */
class BillingPremiumRepository(
    override val state: StateFlow<PremiumState>,
    private val queryPurchases: suspend () -> Unit,
) : PremiumRepository {
    override suspend fun markPurchased(tier: PremiumTier) {
        // No-op: the real purchase flow runs through PremiumBillingClient.launchPurchase,
        // and state propagates via the wrapped StateFlow.
    }

    override suspend fun restore() {
        queryPurchases()
    }
}
```

- [ ] **Step T4.9: Run tests — confirm pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*BillingPremiumRepositoryTest*"`
Expected: 3 tests PASS.

- [ ] **Step T4.10: Wire BillingPremiumRepository in MainActivity**

In `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt`:

Replace `val premiumRepository = PremiumStateStore(applicationContext).repository()` with:

```kotlin
        val billingClient = se.birdy.app.data.premium.PremiumBillingClient(
            context = applicationContext,
            licensePublicKeyBase64 = BuildConfig.PLAY_LICENSE_KEY,
        )
        val premiumRepository = se.birdy.app.data.premium.BillingPremiumRepository(
            state = billingClient.state,
            queryPurchases = { billingClient.queryPurchases() },
        )
        // Connect + cold-start query in parallel with classifier bootstrap
        kotlinx.coroutines.GlobalScope.launch {
            billingClient.connect()
            billingClient.queryPurchases()
        }
```

Add field at class top: `private lateinit var billingClient: se.birdy.app.data.premium.PremiumBillingClient`.

Add to `onDestroy()`:
```kotlin
        billingClient.dispose()
```

(Save `billingClient` to the field before launching the connect coroutine.)

- [ ] **Step T4.11: Update PremiumViewModel.purchase() to launch real flow**

Edit `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumViewModel.kt`. The current `purchase()` calls `repository.markPurchased(...)` which is now a no-op. We need to inject a launch-purchase callback.

Add constructor parameter:
```kotlin
class PremiumViewModel(
    private val repository: PremiumRepository,
    private val launchPurchase: suspend (PremiumTier) -> Unit = { repository.markPurchased(it) },
) : ViewModel() {
```

Update `purchase()` to call `launchPurchase` instead:
```kotlin
    fun purchase() {
        if (_state.value.purchaseInFlight) return
        _state.update { it.copy(purchaseInFlight = true) }
        viewModelScope.launch {
            try {
                launchPurchase(_state.value.selectedTier)
            } finally {
                _state.update { it.copy(purchaseInFlight = false) }
            }
        }
    }
```

In `AppGraph.premiumViewModel(...)`, inject the real launch:
```kotlin
    fun premiumViewModel(): PremiumViewModel = PremiumViewModel(
        repository = premiumRepository,
        launchPurchase = launchPurchase ?: { premiumRepository.markPurchased(it) },
    )
```

Add `AppGraph` parameter:
```kotlin
    val launchPurchase: (suspend (PremiumTier) -> Unit)? = null,
```

In `MainActivity.buildAppGraph()`:
```kotlin
            launchPurchase = { tier ->
                billingClient.launchPurchase(this@MainActivity, tier)
                // Result is reflected via state flow; we don't need to do anything else here.
                Unit
            },
```

- [ ] **Step T4.12: Verify formattedPrices propagate to PremiumUiState**

Edit `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumUiState.kt`. Add fields:
```kotlin
    val formattedYearlyPrice: String? = null,
    val formattedLifetimePrice: String? = null,
```

In `PremiumViewModel`, also subscribe to formattedPrices (need to inject):

Add constructor parameter:
```kotlin
    private val formattedPricesFlow: StateFlow<FormattedPrices> = MutableStateFlow(FormattedPrices()),
```

Add `init` block update:
```kotlin
        viewModelScope.launch {
            formattedPricesFlow.collect { prices ->
                _state.update {
                    it.copy(
                        formattedYearlyPrice = prices.yearly,
                        formattedLifetimePrice = prices.lifetime,
                    )
                }
            }
        }
```

Import `se.birdy.app.data.premium.FormattedPrices` + `kotlinx.coroutines.flow.MutableStateFlow`.

In `AppGraph`, add parameter:
```kotlin
    val formattedPricesFlow: kotlinx.coroutines.flow.StateFlow<se.birdy.app.data.premium.FormattedPrices>? = null,
```

Update `premiumViewModel()` factory:
```kotlin
    fun premiumViewModel(): PremiumViewModel = PremiumViewModel(
        repository = premiumRepository,
        launchPurchase = launchPurchase ?: { premiumRepository.markPurchased(it) },
        formattedPricesFlow = formattedPricesFlow ?: kotlinx.coroutines.flow.MutableStateFlow(se.birdy.app.data.premium.FormattedPrices()),
    )
```

In `MainActivity.buildAppGraph()`:
```kotlin
            formattedPricesFlow = billingClient.formattedPrices,
```

- [ ] **Step T4.13: Build + verify no compile errors**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL. If fail on Billing API mismatch, check Billing v8 docs — class names may differ slightly between v7/v8.

- [ ] **Step T4.14: Commit T4**

```bash
git add composeApp/build.gradle.kts \
        gradle.properties.example \
        .gitignore \
        composeApp/src/commonMain/kotlin/se/birdy/app/data/ \
        composeApp/src/androidMain/kotlin/se/birdy/app/data/ \
        composeApp/src/iosMain/kotlin/se/birdy/app/data/ \
        composeApp/src/commonTest/kotlin/se/birdy/app/data/ \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumViewModel.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumUiState.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt \
        androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt
git commit -m "$(cat <<'EOF'
feat(plan-6b1/t4): Google Play Billing v8 integration replacing purchase() mock

Adds PremiumBillingClient expect/actual (Android wraps BillingClient v8,
iOS = no-op stub) and BillingPremiumRepository implementing existing
PremiumRepository interface. MainActivity connects + queryPurchases at
cold-start parallel to classifier bootstrap; PremiumViewModel.purchase()
now launches the real Google purchase sheet via injected lambda.

Includes on-device RSA signature-verify against Play Licensing public key
(BuildConfig.PLAY_LICENSE_KEY, injected via gradle.properties — never
committed). Acknowledgement runs synchronously in PurchasesUpdatedListener
to avoid 72h auto-refund. Pricing strings sourced from ProductDetails
when available (overrides static SEK fallback in PremiumUiState).

iOS actual is no-op pending future iOS plan.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task T5: Restore Purchases in Settings

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsEffect.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`

- [ ] **Step T5.1: Add Settings strings (SV + EN)**

Edit `composeApp/src/commonMain/composeResources/values/strings.xml`:
```xml
    <string name="settings_restore_purchases">Återställ köp</string>
    <string name="settings_restore_purchases_success">Köpet är återställt.</string>
    <string name="settings_restore_purchases_empty">Inget köp att återställa.</string>
```

Edit `composeApp/src/commonMain/composeResources/values-en/strings.xml`:
```xml
    <string name="settings_restore_purchases">Restore purchases</string>
    <string name="settings_restore_purchases_success">Purchase restored.</string>
    <string name="settings_restore_purchases_empty">Nothing to restore.</string>
```

- [ ] **Step T5.2: Verify SettingsEffect has ShowToast variant**

Read `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsEffect.kt`. If `ShowToast(text: String)` or similar already exists, reuse. Otherwise add:

```kotlin
    data class ShowToast(val text: String) : SettingsEffect
```

(Match the existing sealed-interface/class pattern in the file.)

- [ ] **Step T5.3: Add `restorePurchases()` to SettingsViewModel**

Edit `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsViewModel.kt`. Add method:

```kotlin
    fun restorePurchases() {
        viewModelScope.launch {
            premiumRepository.restore()
            // Check state after restore
            val state = premiumRepository.state.value
            val message = if (state is PremiumState.Active) {
                Res.string.settings_restore_purchases_success
            } else {
                Res.string.settings_restore_purchases_empty
            }
            // Emit via existing effects channel (mirror existing pattern in this VM)
            _effects.emit(SettingsEffect.ShowToast(message))
        }
    }
```

(Adjust imports + match existing `_effects` channel name. If `ShowToast` takes a `StringResource` instead of a `String`, use the resource type.)

- [ ] **Step T5.4: Add Restore Purchases row in SettingsScreen**

Edit `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsScreen.kt`. Find the ABOUT BIRDY group (where Rate / Share / Feedback rows live). Add as last row in that group (or as its own group above ABOUT BIRDY):

```kotlin
SettingsRow(
    title = stringResource(Res.string.settings_restore_purchases),
    onClick = { viewModel.restorePurchases() },
)
```

(Use the same `SettingsRow`-style composable that other rows use; check call-sites for Rate Birdy / Share / Feedback.)

- [ ] **Step T5.5: Verify SettingsScreen LaunchedEffect handles ShowToast → Snackbar**

In `SettingsScreen`, check the existing `LaunchedEffect(Unit) { vm.effects.collect { ... } }` block. Ensure it has a branch for `SettingsEffect.ShowToast` that calls `snackbarHostState.showSnackbar(...)` with the string-resource-resolved text.

If missing, add:
```kotlin
is SettingsEffect.ShowToast -> {
    val resolved = stringResource(effect.text)
    snackbarHostState.showSnackbar(resolved)
}
```

(Note: `stringResource` must be called in composable context; you may need to resolve outside `LaunchedEffect` via a local helper.)

- [ ] **Step T5.6: Build + install**

Run: `./gradlew :androidApp:installDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step T5.7: Commit T5**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/ \
        composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "$(cat <<'EOF'
feat(plan-6b1/t5): Restore Purchases row in Settings

Adds "Återställ köp" / "Restore purchases" row in Settings → triggers
PremiumRepository.restore() (which calls PremiumBillingClient.queryPurchases())
and shows Caveat-toast with success or "nothing to restore" outcome.

Required by Google Play policy for paid apps. Wired via existing
SettingsEffect.ShowToast → snackbarHostState pattern.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task T6: Premium UI fixes (stamp removal + EN-currency + auto-renew disclosure)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`

- [ ] **Step T6.1: Remove "spara 60%" string + add auto-renew disclosure**

Edit `composeApp/src/commonMain/composeResources/values/strings.xml`:

Delete:
```xml
    <string name="premium_tier_yearly_stamp">spara 60%</string>
```

Add:
```xml
    <string name="premium_auto_renew_disclosure">Förnyas årligen 199 kr · Avbryt när som helst.</string>
```

Verify existing `premium_tier_yearly_price` string says `199 kr / år` (or similar — preserve current copy).

Edit `composeApp/src/commonMain/composeResources/values-en/strings.xml`:

Delete:
```xml
    <string name="premium_tier_yearly_stamp">save 60%</string>
```

Change `premium_tier_yearly_price` from `"199 kr / year"` to `"199 SEK / year"`.
Change `premium_tier_lifetime_price` from `"499 kr · one-time"` to `"499 SEK · one-time"`.

Add:
```xml
    <string name="premium_auto_renew_disclosure">Auto-renews yearly at 199 SEK · Cancel anytime.</string>
```

- [ ] **Step T6.2: Remove StampLabel rendering in PremiumScreen**

Edit `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumScreen.kt`. Find `TierCard(...)` call for the YEARLY tier. Remove the `stampLabel = stringResource(Res.string.premium_tier_yearly_stamp)` argument (or `stampLabel = "spara 60%"` if it's hardcoded — per Plan 7e bug-fix-lärdom).

If `TierCard` has a non-nullable `stampLabel` parameter, change its default to `null` in the definition:
```kotlin
@Composable
fun TierCard(
    // ...
    stampLabel: String? = null,
    // ...
)
```

Inside `TierCard`, wrap the stamp rendering in `if (stampLabel != null) { ... }`.

- [ ] **Step T6.3: Add AutoRenewDisclosure Caveat-sub under Yearly TierCard**

In `PremiumScreen.kt`, immediately after the YEARLY `TierCard(...)` invocation (and inside the same parent Column/Box), add:

```kotlin
if (selectedTier == PremiumTier.YEARLY) {
    Text(
        text = stringResource(Res.string.premium_auto_renew_disclosure),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = rememberCaveat()),
        color = MarginaliaInk,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
```

(Match existing Caveat-sub patterns in the file — search for `rememberCaveat()` to find the helper.)

- [ ] **Step T6.4: Wire formattedPrice override from state**

In `PremiumScreen.kt`, find where `premium_tier_yearly_price` is used to render the Yearly price string. Replace:

```kotlin
val priceText = state.formattedYearlyPrice ?: stringResource(Res.string.premium_tier_yearly_price)
```

Same for Lifetime:
```kotlin
val lifetimeText = state.formattedLifetimePrice ?: stringResource(Res.string.premium_tier_lifetime_price)
```

Pass `priceText` / `lifetimeText` to the respective `TierCard(price = ...)` argument.

- [ ] **Step T6.5: Verify no string-escape regressions**

Run: `grep -E "\\\\'|%%" composeApp/src/commonMain/composeResources/values*/strings.xml`
Expected: zero matches. If any, replace `\'` with Unicode `’` (U+2019) and `%%` with `%1$s` + Kotlin-side `"${value}%"`.

- [ ] **Step T6.6: Build + install + visually verify on device**

Run:
```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

Navigate to Settings → Premium →. Confirm:
- No "spara 60%" stamp on Yearly card
- Auto-renew disclosure visible under Yearly card when selected
- EN-locale (`adb shell cmd locale set-app-locales se.birdy.android --locales en` + force-stop + relaunch) shows `199 SEK / year`, not `199 kr / year`

- [ ] **Step T6.7: Commit T6**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumScreen.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "$(cat <<'EOF'
fix(plan-6b1/t6): remove "spara 60%" stamp + fix EN-currency + add auto-renew disclosure

Drops the "spara 60%" / "save 60%" stamp from PremiumScreen — bryter EU
Omnibus Article 6a (no legitimate reference price) and Google Play 2025
dark-pattern policy. App-rejection risk eliminated.

Fixes English locale showing "199 kr / year" (kr is Swedish abbreviation).
Now displays "199 SEK / year" as static fallback; runtime override via
ProductDetails.getFormattedPrice() shows user's local Play Store currency
once Billing has loaded.

Adds Caveat-sub auto-renew disclosure under Yearly TierCard:
"Auto-renews yearly at 199 SEK · Cancel anytime." — required for clarity
per Google Play Developer Policy + Apphud paywall best-practice.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task T7: Cold-start-modal throttle (3d + 7d first-install grace)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/premium/EntryFlowDecider.kt`
- Modify: `composeApp/src/commonTest/kotlin/se/birdy/app/premium/EntryFlowDeciderTest.kt`
- Modify: `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/UserPreferences.kt`
- Modify: `shared/datastore/src/androidMain/kotlin/se/birdy/datastore/UserPreferencesStore.android.kt`
- Modify: `shared/datastore/src/iosMain/kotlin/se/birdy/datastore/UserPreferencesStore.ios.kt`
- Modify: `shared/datastore/src/jvmMain/kotlin/se/birdy/datastore/UserPreferencesStore.jvm.kt`
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt` (migration)
- Modify call-sites in app where the modal is gated

- [ ] **Step T7.1: Write failing tests for new throttle logic**

Edit `composeApp/src/commonTest/kotlin/se/birdy/app/premium/EntryFlowDeciderTest.kt`. Replace existing tests with:

```kotlin
package se.birdy.app.premium

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.Instant
import se.birdy.domain.premium.PremiumState

class EntryFlowDeciderTest {
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val day = 24L * 3600L * 1000L

    @Test fun `returns false if onboarding incomplete`() {
        val r = EntryFlowDecider.shouldShowPremiumModal(
            now = now, firstInstallAt = now - 30 * day, lastShownAt = null,
            state = PremiumState.Free, onboardingComplete = false,
        )
        assertFalse(r)
    }

    @Test fun `returns false if premium active`() {
        val r = EntryFlowDecider.shouldShowPremiumModal(
            now = now, firstInstallAt = now - 30 * day, lastShownAt = null,
            state = PremiumState.Active(se.birdy.domain.premium.PremiumTier.YEARLY, now),
            onboardingComplete = true,
        )
        assertFalse(r)
    }

    @Test fun `returns false if firstInstall is null`() {
        val r = EntryFlowDecider.shouldShowPremiumModal(
            now = now, firstInstallAt = null, lastShownAt = null,
            state = PremiumState.Free, onboardingComplete = true,
        )
        assertFalse(r)
    }

    @Test fun `returns false inside 7-day grace period`() {
        val r = EntryFlowDecider.shouldShowPremiumModal(
            now = now, firstInstallAt = now - 5 * day, lastShownAt = null,
            state = PremiumState.Free, onboardingComplete = true,
        )
        assertFalse(r)
    }

    @Test fun `returns true after 7-day grace + never shown`() {
        val r = EntryFlowDecider.shouldShowPremiumModal(
            now = now, firstInstallAt = now - 8 * day, lastShownAt = null,
            state = PremiumState.Free, onboardingComplete = true,
        )
        assertTrue(r)
    }

    @Test fun `returns false within 3-day throttle`() {
        val r = EntryFlowDecider.shouldShowPremiumModal(
            now = now, firstInstallAt = now - 30 * day, lastShownAt = now - 2 * day,
            state = PremiumState.Free, onboardingComplete = true,
        )
        assertFalse(r)
    }

    @Test fun `returns true after 3-day throttle expires`() {
        val r = EntryFlowDecider.shouldShowPremiumModal(
            now = now, firstInstallAt = now - 30 * day, lastShownAt = now - 4 * day,
            state = PremiumState.Free, onboardingComplete = true,
        )
        assertTrue(r)
    }
}
```

- [ ] **Step T7.2: Rewrite EntryFlowDecider**

Edit `composeApp/src/commonMain/kotlin/se/birdy/app/premium/EntryFlowDecider.kt`:

```kotlin
package se.birdy.app.premium

import kotlinx.datetime.Instant
import se.birdy.domain.premium.PremiumState

object EntryFlowDecider {
    private const val GRACE_DAYS = 7L
    private const val THROTTLE_DAYS = 3L
    private const val DAY_MS = 24L * 3600L * 1000L

    /**
     * Show the cold-start premium modal iff all conditions hold:
     *  1. Onboarding completed
     *  2. Premium is Free (not Active)
     *  3. `firstInstallAt` is set (DataStore migration ran)
     *  4. ≥ 7 days since first install
     *  5. ≥ 3 days since last shown (null counts as "never shown")
     */
    fun shouldShowPremiumModal(
        now: Instant,
        firstInstallAt: Instant?,
        lastShownAt: Instant?,
        state: PremiumState,
        onboardingComplete: Boolean,
    ): Boolean {
        if (!onboardingComplete) return false
        if (state !is PremiumState.Free) return false
        if (firstInstallAt == null) return false
        if ((now - firstInstallAt).inWholeMilliseconds < GRACE_DAYS * DAY_MS) return false
        if (lastShownAt != null && (now - lastShownAt).inWholeMilliseconds < THROTTLE_DAYS * DAY_MS) return false
        return true
    }
}
```

- [ ] **Step T7.3: Run tests — confirm pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*EntryFlowDeciderTest*"`
Expected: 7 tests PASS.

- [ ] **Step T7.4: Add `firstInstallTimestamp` to UserPreferences interface**

Edit `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/UserPreferences.kt`. Add to interface:

```kotlin
    val firstInstallTimestamp: Flow<Long?>
    suspend fun setFirstInstallTimestamp(ms: Long)
```

- [ ] **Step T7.5: Implement on Android DataStore**

Edit `shared/datastore/src/androidMain/kotlin/se/birdy/datastore/UserPreferencesStore.android.kt`. Find existing key declarations and add:

```kotlin
        private val FIRST_INSTALL_TIMESTAMP_KEY = longPreferencesKey("first_install_timestamp")
```

(`Long` not nullable in DataStore — use `0L` as sentinel for null.)

Override:
```kotlin
        override val firstInstallTimestamp: Flow<Long?> = ds.data.map { prefs ->
            prefs[FIRST_INSTALL_TIMESTAMP_KEY]?.takeIf { it > 0L }
        }
        override suspend fun setFirstInstallTimestamp(ms: Long) {
            ds.edit { it[FIRST_INSTALL_TIMESTAMP_KEY] = ms }
        }
```

- [ ] **Step T7.6: Implement no-op on iOS + JVM**

Edit `shared/datastore/src/iosMain/kotlin/se/birdy/datastore/UserPreferencesStore.ios.kt`:
```kotlin
        override val firstInstallTimestamp: Flow<Long?> = flowOf(null)
        override suspend fun setFirstInstallTimestamp(ms: Long) = Unit
```

Edit `shared/datastore/src/jvmMain/kotlin/se/birdy/datastore/UserPreferencesStore.jvm.kt`: same pattern.

- [ ] **Step T7.7: Wire migration + first-install in MainActivity**

In `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt`, after `userPreferences` is obtained, add:

```kotlin
        runBlocking {
            val existing = userPreferences.firstInstallTimestamp.first()
            if (existing == null) {
                // Migration: existing v0.8.0-rc1 users get now-8d so modal can show after 3d throttle.
                // Fresh installs get now → full 7d grace.
                val premiumLastShown = userPreferences.premiumModalLastShown.first()
                val backdated = if (premiumLastShown > 0L) {
                    System.currentTimeMillis() - 8L * 24 * 3600 * 1000
                } else {
                    System.currentTimeMillis()
                }
                userPreferences.setFirstInstallTimestamp(backdated)
            }
        }
```

(Adjust property name `premiumModalLastShown` based on what's in the existing UserPreferences interface — verify by reading the file.)

- [ ] **Step T7.8: Update call-sites that gate the modal**

Find all call-sites of `EntryFlowDecider.shouldShowPremiumModal(...)` (likely in `AppScaffold` or similar):

```bash
grep -rn "shouldShowPremiumModal" composeApp/src --include="*.kt"
```

Update each to pass `firstInstallAt = prefs.firstInstallTimestamp.collectAsState(initial = null).value?.let { Instant.fromEpochMilliseconds(it) }` and `lastShownAt = prefs.premiumModalLastShown...?.let { Instant.fromEpochMilliseconds(it) }`.

(The call-site signature changes from `today: LocalDate, lastShown: LocalDate?` to `now: Instant, firstInstallAt: Instant?, lastShownAt: Instant?` so the type of the stored value also changes. The `premiumModalLastShown` was likely already `Long` in DataStore but exposed as `LocalDate`-derived — if so, also update the existing DataStore key to store millis and surface as `Long?`.)

- [ ] **Step T7.9: Build + verify**

Run: `./gradlew :composeApp:testDebugUnitTest :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step T7.10: Device-verify migration**

Install on device with existing v0.8.0-rc1 data:
```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

Verify modal does NOT appear immediately. Force-stop and relaunch; verify still no modal (still in throttle from "8d ago" + 3d throttle = within 8-3=5d so OK).

- [ ] **Step T7.11: Commit T7**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/premium/EntryFlowDecider.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/premium/EntryFlowDeciderTest.kt \
        shared/datastore/src/ \
        androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/  # any call-site updates
git commit -m "$(cat <<'EOF'
feat(plan-6b1/t7): cold-start premium modal throttle (3d + 7d grace)

Replaces day-based throttle (1×/day) with timestamp-based logic: 7-day
first-install grace + 3-day repeat throttle. New DataStore key
`first_install_timestamp` migrated on first boot — existing v0.8.0-rc1
users (premiumModalLastShown > 0) get backdated `now - 8d` so they
neither see the modal immediately nor wait a full 7d; fresh installs get
`now` for full grace.

Rationale: Cold-start modal daily was too aggressive for privacy-first
field-journal app per launch-roadmap §3 + monetization research §4.2.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task T8: ML preprocessing fix (gated by T2 outcome)

**Files:** depends on T2 root cause

**Decision gate:** Read `docs/superpowers/research/2026-05-16-ml-preprocessing-diagnos.md`. Choose path:

### Path A — T2 found actionable root cause

- [ ] **Step T8.A.1: Implement the fix**

Edit the relevant file in `shared/ml/...` per the diagnos. Common cases:

**(a) Rotation:** Edit `composeApp/src/androidMain/kotlin/se/birdy/ml/camera/AndroidCameraSource.kt` (or wherever `ImageProxy → Bitmap` happens). Apply `Matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())` before passing to preprocessor.

**(b) Stretch vs center-crop:** Edit `shared/ml/src/.../ImagePreprocessor.kt`. Replace `Bitmap.createScaledBitmap(src, w, h, true)` with center-crop logic:
```kotlin
val sx = src.width.toFloat() / w
val sy = src.height.toFloat() / h
val s = minOf(sx, sy)
val cw = (w * s).toInt()
val ch = (h * s).toInt()
val x = (src.width - cw) / 2
val y = (src.height - ch) / 2
val cropped = Bitmap.createBitmap(src, x, y, cw, ch)
val scaled = Bitmap.createScaledBitmap(cropped, w, h, true)
```

**(c) RGB/BGR swap:** Edit YUV→Bitmap conversion path. Verify channel order matches AIY V1 expectation (RGB, MSB-first).

- [ ] **Step T8.A.2: Re-run DiagnosticsScreen + verify improvement**

Install + run DiagnosticsScreen again. Compare new device output to desktop. Expected: top-1/top-3 now match desktop.

- [ ] **Step T8.A.3: Field-test on real bird (if possible) or document improvement**

If user has access to live bird: scan a known species, verify Match-screen displays correct species. Otherwise: document the improvement metric (e.g. "device top-3 now matches desktop on 3/3 corpus images, up from 1/3 before fix").

- [ ] **Step T8.A.4: Commit T8 Path A**

```bash
git add shared/ml/ composeApp/src/androidMain/kotlin/se/birdy/ml/
git commit -m "$(cat <<'EOF'
fix(plan-6b1/t8): ML preprocessing root-cause fix from Phase 1 diagnos

[Describe specific fix: rotation / center-crop / RGB-BGR / other]

Per docs/superpowers/research/2026-05-16-ml-preprocessing-diagnos.md.
Device top-3 now matches desktop on 3/3 corpus images. Field hit-rate
should improve from ~10% to [estimated %].

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

### Path B — T2 was inconclusive (Risk 4 fallback)

- [ ] **Step T8.B.1: Lower NoBird threshold**

Edit `composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchThresholds.kt`. Change `NOBIRD_CONFIDENCE` from `0.10f` to `0.05f`.

- [ ] **Step T8.B.2: Update EntryFlowDeciderTest if any threshold tests reference this**

Run: `grep -rn "0.10\|0\\.10f\\|NOBIRD" composeApp/src --include="*.kt"`
Update assertions.

- [ ] **Step T8.B.3: Update auto-memory with marketing narrative shift**

Update `~/.claude/projects/.../memory/project_play_store_launch_research.md` with note:
> 2026-05-16: ML preprocessing diagnos inconclusive; threshold lowered 0.10→0.05 (Disambig is now primary path). Marketing narrative shifts from "AI identifies" to "Du och AI:n hjälps åt" / "You and the AI work together". Update store-listing copy in T0.4 AAB regen if needed.

- [ ] **Step T8.B.4: Commit T8 Path B**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchThresholds.kt
git commit -m "$(cat <<'EOF'
fix(plan-6b1/t8): lower NoBird threshold 0.10 → 0.05 (Risk 4 fallback)

T2 diagnos inconclusive — no actionable preprocessing root cause found.
Per spec Risk 4 fallback (a): lower threshold so Disambig becomes the
primary path. USP narrative shifts from "AI identifies" to "You and
the AI work together" — captured in auto-memory.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task T9: TalkBack walkthrough

**Files:**
- Create: `docs/superpowers/research/2026-05-16-talkback-walkthrough.md`
- Modify various UI components if P0 issues found

- [ ] **Step T9.1: Enable TalkBack on device**

```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell settings put secure accessibility_enabled 1
```

Verify TalkBack starts narrating via the device speaker.

- [ ] **Step T9.2: Walk through all 8 surfaces**

For each surface, listen to TalkBack annunciation. Note any unannounced elements, redundant readouts, or focus traps.

1. **Onboarding** → all 3 pages → Skip button
2. **Listen launcher** → 3 cards + gear button → Settings
3. **Settings** → all rows including new Restore Purchases
4. **Premium screen** → Tier cards (Yearly + Lifetime) + Continue button + Close X
5. **Scan** → top-chip + crosshair + bottom-nav
6. **Match flow** (use `match_override.txt` to force Disambig → pick → Match)
7. **Save flow** → unlock bottom-sheet
8. **Archive + Diary + Badges** → core browse

- [ ] **Step T9.3: Triage findings**

Create `docs/superpowers/research/2026-05-16-talkback-walkthrough.md`:

```markdown
# TalkBack Walkthrough — 2026-05-16

**Device:** SM-S918B (Galaxy S23 Ultra), Android 14
**Build:** v0.9.0a-billing pre-tag

## P0 — Blocks purchase flow (must fix in T9)
- [list any]

## P1 — Affects core experience (fix if <30 min)
- [list any]

## P2 — Decorative / marginal (defer to v1.0.x)
- [list any]
```

- [ ] **Step T9.4: Fix all P0 issues inline**

For each P0 issue, edit the relevant file. Common fixes:
- Missing `contentDescription` on `Icon`/`AsyncImage` → add string-resource lookup
- Composite element not merged → wrap in `Modifier.semantics(mergeDescendants = true) { contentDescription = "..." }`
- Custom-drawn component (StampSeal etc) unannounced → add `Modifier.semantics { contentDescription = ...; role = Role.Button }`

Commit each fix with descriptive message referencing the P0 item.

- [ ] **Step T9.5: Fix P1 issues if quick**

For each P1 issue with estimated effort <30 min, fix inline. Otherwise note in walkthrough doc and defer.

- [ ] **Step T9.6: Disable TalkBack**

```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell settings put secure accessibility_enabled 0
```

- [ ] **Step T9.7: Commit T9**

```bash
git add docs/superpowers/research/2026-05-16-talkback-walkthrough.md \
        composeApp/src/commonMain/kotlin/se/birdy/app/  # any P0/P1 fixes
git commit -m "$(cat <<'EOF'
fix(plan-6b1/t9): TalkBack walkthrough P0 + P1 fixes

Walkthrough on SM-S918B covered 8 surfaces including new Premium screen
purchase flow + Restore Purchases row added in T4/T5. P0 issues
(purchase-flow-blocking) fixed inline. P1 issues fixed if <30 min;
remaining P1 + all P2 documented in
docs/superpowers/research/2026-05-16-talkback-walkthrough.md for v1.0.x
cleanup.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task T10: Full device-verify + tag v0.9.0a-billing

**Files:**
- Modify: `composeApp/build.gradle.kts` or `androidApp/build.gradle.kts` (versionCode/versionName bump)
- Create: device-screenshot files in `docs/superpowers/screenshots/2026-05-16-v0.9.0a-billing/`

- [ ] **Step T10.1: Bump versionCode + versionName**

Edit `androidApp/build.gradle.kts` (or wherever versionCode lives — check `defaultConfig`):
```kotlin
        versionCode = 110
        versionName = "1.0.0-rc2"
```

- [ ] **Step T10.2: Build signed release AAB**

Run:
```bash
./gradlew :androidApp:bundleRelease
```
Expected: BUILD SUCCESSFUL. Output at `androidApp/build/outputs/bundle/release/androidApp-release.aab`.

- [ ] **Step T10.3: Install signed release via bundletool**

Run:
```bash
java -jar tools/bundletool-all-1.18.1.jar build-apks \
    --bundle=androidApp/build/outputs/bundle/release/androidApp-release.aab \
    --output=/tmp/birdy.apks \
    --ks=keystore/birdy-upload.keystore \
    --ks-key-alias=birdy-upload \
    --ks-pass=pass:$BIRDY_KEYSTORE_PASSWORD \
    --key-pass=pass:$BIRDY_KEY_PASSWORD
java -jar tools/bundletool-all-1.18.1.jar install-apks --apks=/tmp/birdy.apks
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

(Use the same bundletool + keystore paths Plan 6a T15 used.)

- [ ] **Step T10.4: Full purchase verify (yearly)**

On device:
1. Settings → Premium → select Yearly → Continue
2. Google sheet opens → choose test card → complete purchase
3. Return to app → PremiumState should flip to Active(YEARLY)
4. Quit app
5. Relaunch → no Premium screen modal; Settings shows account as Premium

Screenshot: `docs/superpowers/screenshots/2026-05-16-v0.9.0a-billing/01-purchase-yearly-complete.png`

- [ ] **Step T10.5: Restore Purchases verify**

```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell pm clear se.birdy.android
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

Walk through onboarding. Go to Settings → Restore purchases → confirm Caveat-toast "Purchase restored." Verify state flipped to Active.

Screenshot: `02-restore-purchases-success.png`

- [ ] **Step T10.6: Cold-start-modal grace verify**

After `pm clear` (T10.5), confirm no modal appeared. Restart app 3 times — still no modal (7d first-install grace).

Skip the rest of grace verification (would require time-travel). Document in screenshot README.

- [ ] **Step T10.7: Tag the release**

```bash
git tag v0.9.0a-billing
git push origin v0.9.0a-billing
```

- [ ] **Step T10.8: Commit screenshots + final plan status**

```bash
git add docs/superpowers/screenshots/2026-05-16-v0.9.0a-billing/ \
        androidApp/build.gradle.kts
git commit -m "$(cat <<'EOF'
release(plan-6b1): v0.9.0a-billing — versionCode 110, versionName 1.0.0-rc2

Tag-time device-verify on SM-S918B (API 35) with signed release AAB
installed via bundletool 1.18.1:
- Yearly purchase end-to-end via Play Console test track (acknowledge
  confirmed via Purchase.isAcknowledged in next queryPurchasesAsync)
- Restore purchases after pm clear restores entitlement without user
  re-purchase
- Cold-start modal grace verified inside 7d window
- TalkBack P0 issues fixed; P1 partial; P2 logged

Closed Testing track is live with 12 testers opt-in (14-day Google grace
clock started [date]); Internal Testing backup track exists for ongoing
device-verify during 6b2 work.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step T10.9: Update CLAUDE.md status line**

Edit `CLAUDE.md`. Update the **Status** line at the top to reflect v0.9.0a-billing. Add Plan 6b1 row to the plan-of-plans table.

```bash
git add CLAUDE.md
git commit -m "docs: mark Plan 6b1 complete (v0.9.0a-billing) — next is 6b2 Audio-ID"
```

- [ ] **Step T10.10: Update auto-memory**

Save a new memory file at `~/.claude/projects/.../memory/project_plan_6b1_status.md` capturing reusable patterns + post-tag follow-ups. Add pointer in `MEMORY.md`.

---

## Self-Review

After completing all tasks, run this checklist:

**1. Spec coverage:**
- Section 2.1 T0–T10 → mapped to Tasks T0–T10 ✓
- Section 2.3 Success criteria 1–12 → verified in T10 device-verify
- Section 3 Architecture decisions A–J → implemented in T2/T3/T4/T7 + documented in commits
- Section 4 Risks 1–7 → addressed (Risk 1 via AppGraph lambda; Risk 2 via gradle.properties; Risk 3 via T7 migration; Risk 4 via T8 Path B fallback; Risk 5 via T6.5 grep; Risk 6 via T0 dual-track; Risk 7 via T9 tiered acceptance)

**2. Placeholder scan:** All task steps have either complete code, exact commands, or specific decisions. T8 has two branches (A/B) but each is fully spec'd. No "TBD" / "TODO" in plan.

**3. Type consistency:**
- `PremiumBillingClient` constructor signature consistent across expect + Android actual + iOS actual
- `MatchOverride` data class fields (`qid`, `confidence`) consistent across parser, tests, AppGraph usage
- `EntryFlowDecider.shouldShowPremiumModal` new signature (`now: Instant, firstInstallAt: Instant?, lastShownAt: Instant?, ...`) consistent between EntryFlowDecider.kt, tests, and call-sites in T7.8
- `FormattedPrices` shape (`yearly: String?, lifetime: String?`) consistent in PremiumBillingClient + PremiumViewModel + PremiumUiState

**4. Open questions for executor:**
- T2.3: `ImageInput.JpegBytes` constructor — verify actual class name and signature in `shared/ml/.../BirdClassifier.kt`. If different (e.g. `Bytes(jpeg: ByteArray)`), adjust call-site.
- T5.3: `_effects` channel name in SettingsViewModel — match existing pattern in file
- T6.2: `TierCard`-composable's current `stampLabel` parameter shape — verify whether it's required or already nullable
- T6.3: `rememberCaveat()` import path + `MarginaliaInk` token import path — verify both exist (Plan 7c shipped them)
- T7.4–T7.5: `premiumModalLastShown` storage type in DataStore — verify whether it's `Long` or `LocalDate`-derived; may need parallel migration
- T10.1: `versionCode`/`versionName` location — verify in `androidApp/build.gradle.kts` not `composeApp/build.gradle.kts`

If any of these don't match reality, fix the step and continue (don't pause to ask).
