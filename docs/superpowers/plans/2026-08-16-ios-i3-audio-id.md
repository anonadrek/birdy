# i3 — Ljud-ID på iOS: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** BirdNET-ljud-ID på iOS med Android v1.2-paritet — runner + capture + actuals + Flex-länkning; all sessionslogik är redan commonMain.

**Architecture:** Väg A ur spec:en: Googles officiella `TensorFlowLiteSelectTfOps` 2.17.0 hämtas SHA-pinnat och `-force_load`:as in i app-binären (endast device-SDK — sim-slice existerar inte; sim visar ärligt felstate/DEMO). `IosTfliteAudioRunner` speglar `AndroidTfliteAudioRunner` på befintlig tflitec-cinterop; `IosAudioRecorder` (AVAudioEngine→AVAudioConverter→48 kHz mono Int16) speglar `AndroidAudioRecorder`s callback-kontrakt; composeApp-actuals (adapter, waveform-renderer, permission-controller, host) speglar sina Android-motsvarigheter; `IosAppGraph` speglar MainActivitys `Deferred`-CAS-cache inkl. eviction-fixen.

**Tech Stack:** Kotlin/Native + cinterop (tflitec), AVFAudio (AVAudioEngine/AVAudioSession/AVAudioConverter), CoreGraphics/UIKit (waveform-PNG), xcodegen, bash-fetch-script, uv + full TensorFlow-pip (desktop-referens).

**Spec:** `docs/superpowers/specs/2026-08-16-ios-i3-audio-id-design.md` (läs den + research-docen `docs/superpowers/research/2026-08-15-ios-i3-flex-select-ops-research.md` innan task 1).

## Global Constraints

- **Full gate per task-commit (Android + iOS):**
  ```bash
  ./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt
  ./gradlew :shared:content:iosSimulatorArm64Test :shared:domain:iosSimulatorArm64Test :shared:data:iosSimulatorArm64Test :shared:ml:iosSimulatorArm64Test :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosSimulatorArm64
  ```
  (Tasks som bara rör `iosApp/`/`tools/`/docs kör ändå båda raderna — de är billiga när inget Kotlin ändrats.)
- **Skal-miljö:** `export JAVA_HOME="$HOME/.local/java21/Contents/Home"` före varje `./gradlew`; `export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` före varje `xcrun`/`xcodebuild`.
- **species.db-brus:** varje iOS-bygge regenererar `composeApp/src/commonMain/composeResources/files/species.db` med identiskt innehåll — kör `git restore composeApp/src/commonMain/composeResources/files/species.db` före varje commit (om du inte medvetet ändrat art-YAML, vilket ingen task här gör).
- **Noll Android-beteendeändring** utanför task 2:s `flatSigmoid`-lyft (behavior-preserving).
- **K/N-traps (repo-CLAUDE.md trap-katalogen gäller):** failable ObjC-init via Kotlin-konstruktor kastar rå NPE — fånga `NullPointerException` runt sådana konstruktoranrop, lita aldrig på elvis efteråt; delade composables använder `PlatformBackHandler` (ej aktuellt här — `AudioScanScreen` är redan delad och klar); VM:er stänger aldrig bootstrap-ägda singletons.
- **ktlint:** kör `./gradlew ktlintFormat` innan gate om formatfel; detekt-varningar åtgärdas eller motiveras med `@Suppress` + kommentar (mönster: `audioProviders`-catchen).
- **Commit-stil:** `feat(ios)|fix(ios)|chore(ios)|docs(ios): ...` på `main`, en commit per task (Co-Authored-By-trailern enligt sessionens standard).
- **STORLEKSGATE (task 1):** växer device-appbinären > 150 MB okomprimerat av force_load ⇒ **STOPPA HELA PLANEN**, rapportera siffrorna till Albin (fallback = väg B i spec:en). Fortsätt inte till task 2.

---

### Task 1: Flex-artefakt — fetch-script, länkning, STORLEKSGATE-spike

**Files:**
- Create: `tools/fetch_ios_selectops.sh`
- Create: `iosApp/TensorFlowLiteSelectTfOps.bundle/PrivacyInfo.xcprivacy`
- Modify: `.gitignore` (lägg till en rad)
- Modify: `iosApp/project.yml`

**Interfaces:**
- Consumes: inget från andra tasks.
- Produces: `iosApp/Frameworks/TensorFlowLiteSelectTfOps.xcframework` på disk (gitignorerad) + device-länk med force_load + modellresursen `birdnet_lite_v2.tflite` i app-bundlen (task 3/7 läser den via `NSBundle.mainBundle.pathForResource("birdnet_lite_v2", "tflite")`).

- [ ] **Step 1: Skriv fetch-scriptet**

```bash
#!/usr/bin/env bash
# Hämtar Googles officiella TensorFlowLiteSelectTfOps 2.17.0 (Flex-delegaten för
# BirdNET:s FlexRFFT) och packar upp till iosApp/Frameworks/. SHA-256-pinnad —
# vid mismatch avbryts bygget (samma princip som i2a:s downloadFlex16kJniLibs).
# Arkivet är 1,1 GB uppackat och gitignoreras; detta script är enda källan.
# Se docs/superpowers/research/2026-08-15-ios-i3-flex-select-ops-research.md.
set -euo pipefail

VERSION="2.17.0"
URL="https://dl.google.com/tflite-release/ios/prod/tensorflow/lite/release/ios/release/32/20240729-115310/TensorFlowLiteSelectTfOps/${VERSION}/224693067351224e/TensorFlowLiteSelectTfOps-${VERSION}.tar.gz"
SHA256="bc152ec8ceb1987e78d924d90e1e537b20e8594719c93c951595f33949fe9f85"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEST="${SCRIPT_DIR}/../iosApp/Frameworks/TensorFlowLiteSelectTfOps.xcframework"
MARKER="${DEST}/.fetched-sha256"

if [[ -f "${MARKER}" && "$(cat "${MARKER}")" == "${SHA256}" ]]; then
  exit 0
fi

CACHE_DIR="${HOME}/Library/Caches/birdy"
mkdir -p "${CACHE_DIR}"
TARBALL="${CACHE_DIR}/TensorFlowLiteSelectTfOps-${VERSION}.tar.gz"

if [[ ! -f "${TARBALL}" ]] || ! echo "${SHA256}  ${TARBALL}" | shasum -a 256 -c - >/dev/null 2>&1; then
  echo "Fetching TensorFlowLiteSelectTfOps ${VERSION} (266 MB)..."
  curl -fL --retry 3 -o "${TARBALL}" "${URL}"
fi

echo "${SHA256}  ${TARBALL}" | shasum -a 256 -c - || {
  echo "FEL: SHA-256-mismatch på ${TARBALL} — avbryter." >&2
  rm -f "${TARBALL}"
  exit 1
}

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT
tar -xzf "${TARBALL}" -C "${TMP}"
rm -rf "${DEST}"
mkdir -p "$(dirname "${DEST}")"
mv "${TMP}/TensorFlowLiteSelectTfOps-${VERSION}/Frameworks/TensorFlowLiteSelectTfOps.xcframework" "${DEST}"
echo "${SHA256}" > "${MARKER}"
echo "TensorFlowLiteSelectTfOps ${VERSION} klar: ${DEST}"
```

- [ ] **Step 2: Gör scriptet körbart + kör det**

Run: `chmod +x tools/fetch_ios_selectops.sh && tools/fetch_ios_selectops.sh`
Expected: slutar med `... klar: .../iosApp/Frameworks/TensorFlowLiteSelectTfOps.xcframework`. Verifiera: `ls iosApp/Frameworks/TensorFlowLiteSelectTfOps.xcframework/ios-arm64/` visar `TensorFlowLiteSelectTfOps.framework`. (Tarballen kan redan ligga i `~/Library/Caches/birdy/` eller scratchpad från researchen — scriptet laddar bara ner vid behov.)

- [ ] **Step 3: `.gitignore`**

Lägg till sist i `.gitignore`:

```gitignore
# TensorFlowLiteSelectTfOps — 1,1 GB uppackat, hämtas av tools/fetch_ios_selectops.sh (SHA-pinnad)
iosApp/Frameworks/TensorFlowLiteSelectTfOps.xcframework/
```

Run: `git status --short` → xcframeworken syns INTE som untracked.

- [ ] **Step 4: BASELINE-mätning av device-binären (FÖRE länkändringen)**

```bash
export JAVA_HOME="$HOME/.local/java21/Contents/Home"
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
xcodebuild -project iosApp/Birdy.xcodeproj -scheme Birdy -configuration Debug \
  -destination "generic/platform=iOS" -derivedDataPath iosApp/build/dd-device \
  CODE_SIGNING_ALLOWED=NO build 2>&1 | tail -3
BASELINE=$(stat -f%z iosApp/build/dd-device/Build/Products/Debug-iphoneos/Birdy.app/Birdy)
echo "${BASELINE}" > /tmp/i3-baseline-bytes.txt   # överlever till step 7 (separata bash-anrop delar inte variabler)
echo "BASELINE device-binär: ${BASELINE} bytes"
```

Expected: `BUILD SUCCEEDED`; anteckna siffran (förvänta ~tiotal MB). Om device-bygget failar av annan orsak (signing etc.): felsök INNAN länkflaggorna läggs på — baseline måste vara grön.

- [ ] **Step 5: Skapa privacy-bundlen**

Skapa `iosApp/TensorFlowLiteSelectTfOps.bundle/PrivacyInfo.xcprivacy` med exakt detta innehåll (kopia av manifestet ur arkivet; committas eftersom arkivet är gitignorerat):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>NSPrivacyTracking</key>
	<false/>
	<key>NSPrivacyCollectedDataTypes</key>
	<array/>
	<key>NSPrivacyTrackingDomains</key>
	<array/>
	<key>NSPrivacyAccessedAPITypes</key>
	<array>
		<dict>
			<key>NSPrivacyAccessedAPIType</key>
			<string>NSPrivacyAccessedAPICategoryFileTimestamp</string>
			<key>NSPrivacyAccessedAPITypeReasons</key>
			<array>
				<string>C617.1</string>
			</array>
		</dict>
	</array>
</dict>
</plist>
```

- [ ] **Step 6: Uppdatera `iosApp/project.yml`**

Ersätt hela filen med (diff mot nuvarande: fetch-script först i preBuildScripts, modellresurs + privacy-bundle i sources, device-villkorad OTHER_LDFLAGS, weak CoreML):

```yaml
name: Birdy
options:
  deploymentTarget:
    iOS: "16.0"
targets:
  Birdy:
    type: application
    platform: iOS
    sources:
      - path: iosApp
      - path: ../shared/content/images
        type: folder
        buildPhase: resources
      # BirdNET-modellen delas med Android (androidMain/assets är dess hemvist —
      # medvetet beslut B3 i i3-spec:en; flytt hade bytt Androids mmap mot heap-kopia).
      - path: ../composeApp/src/androidMain/assets/models/birdnet_lite_v2.tflite
        buildPhase: resources
      # Privacy-manifest för den statiskt länkade SelectTfOps (arkivet är gitignorerat,
      # så manifestet committas som egen bundle — speglar podspec:ens resource_bundles).
      - path: TensorFlowLiteSelectTfOps.bundle
        type: folder
        buildPhase: resources
    dependencies:
      - framework: Frameworks/TensorFlowLiteC.xcframework
        embed: false   # static — link only
      - sdk: CoreML.framework
        weak: true     # SelectTfOps-podspec:ens weak_frameworks
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: se.birdy.ios
        INFOPLIST_FILE: iosApp/Info.plist
        FRAMEWORK_SEARCH_PATHS: "$(inherited) $(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)"
        OTHER_LDFLAGS: "$(inherited) -framework ComposeApp -lsqlite3"
        # Flex-delegaten (FlexRFFT för BirdNET) finns ENDAST som ios-arm64 — sim-SDK:n
        # länkar den inte (sim visar ärligt felstate/DEMO, se i3-spec §A). force_load
        # krävs för att registrerings-objekten inte ska strippas (podspec + ops_select-docs).
        "OTHER_LDFLAGS[sdk=iphoneos*]": "$(inherited) -framework ComposeApp -lsqlite3 -force_load $(SRCROOT)/Frameworks/TensorFlowLiteSelectTfOps.xcframework/ios-arm64/TensorFlowLiteSelectTfOps.framework/TensorFlowLiteSelectTfOps -lc++"
        ENABLE_USER_SCRIPT_SANDBOXING: "NO"
        CODE_SIGN_STYLE: Automatic
    preBuildScripts:
      - name: Fetch SelectTfOps
        script: |
          "$SRCROOT/../tools/fetch_ios_selectops.sh"
        basedOnDependencyAnalysis: false
      - name: Compile Kotlin Framework
        script: |
          export JAVA_HOME="$HOME/.local/java21/Contents/Home"
          cd "$SRCROOT/.."
          ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
        basedOnDependencyAnalysis: false
```

- [ ] **Step 7: Regenerera Xcode-projektet + device-bygg MED flex**

```bash
cd iosApp && ~/.local/bin/xcodegen generate && cd ..
xcodebuild -project iosApp/Birdy.xcodeproj -scheme Birdy -configuration Debug \
  -destination "generic/platform=iOS" -derivedDataPath iosApp/build/dd-device \
  CODE_SIGNING_ALLOWED=NO build 2>&1 | tail -3
WITHFLEX=$(stat -f%z iosApp/build/dd-device/Build/Products/Debug-iphoneos/Birdy.app/Birdy)
BASELINE=$(cat /tmp/i3-baseline-bytes.txt)
echo "MED FLEX: ${WITHFLEX} bytes (delta: $((WITHFLEX - BASELINE)) bytes)"
ls -l iosApp/build/dd-device/Build/Products/Debug-iphoneos/Birdy.app/birdnet_lite_v2.tflite
```

Expected: `BUILD SUCCEEDED`; modellfilen ligger i .app-roten; delta-siffran skriven.
**⛔ STORLEKSGATE: är binär-deltat > 150 000 000 bytes ⇒ STOPPA HELA PLANEN och rapportera (baseline, med-flex, delta) — väg B omprövas per spec. Committa i så fall ändå detta task-arbete (det är korrekt oavsett väg) men markera planen stoppad.**

- [ ] **Step 8: Sim-bygget är opåverkat**

```bash
xcodebuild -project iosApp/Birdy.xcodeproj -scheme Birdy -configuration Debug \
  -destination "id=183DD149-45ED-49B8-A2C1-70317698B383" -derivedDataPath iosApp/build/dd \
  build 2>&1 | tail -3
```

Expected: `BUILD SUCCEEDED` (sim-länken har inga force_load-flaggor). Kör sedan full gate (båda raderna i Global Constraints) + `git restore composeApp/src/commonMain/composeResources/files/species.db`.

- [ ] **Step 9: Commit**

```bash
git add tools/fetch_ios_selectops.sh .gitignore iosApp/project.yml iosApp/TensorFlowLiteSelectTfOps.bundle
git commit -m "feat(ios): i3 T1 — SelectTfOps 2.17.0 SHA-pinnad fetch + force_load på device-länken + BirdNET-modell i bundlen"
```

(Committa INTE `iosApp/Birdy.xcodeproj` om det redan är versionerat med genererade diffar — kolla `git status`; xcodeproj:en ÄR versionerad i repot, så ta med dess diff.)

---

### Task 2: `flatSigmoid` → commonMain (TDD, behavior-preserving)

**Files:**
- Modify: `shared/ml/src/commonMain/kotlin/se/birdy/ml/BirdNetPostprocess.kt`
- Modify: `shared/ml/src/androidMain/kotlin/se/birdy/ml/AndroidTfliteAudioRunner.kt` (ta bort privata `flatSigmoid`, använd delade)
- Test: `shared/ml/src/commonTest/kotlin/se/birdy/ml/BirdNetPostprocessTest.kt` (utöka befintlig — finns den inte: skapa)

**Interfaces:**
- Produces: `fun flatSigmoid(logit: Float): Float` i `se.birdy.ml` (commonMain) — task 3:s iOS-runner anropar den.
- Consumes: inget.

- [ ] **Step 1: Skriv failande test**

Lägg i `BirdNetPostprocessTest.kt` (skapa filen med `package se.birdy.ml` + imports `kotlin.test.Test/assertEquals/assertTrue` om den saknas):

```kotlin
@Test
fun flatSigmoidMapsLogitsToConfidences() {
    assertEquals(0.5f, flatSigmoid(0f), 1e-6f)
    // Klipp vid ±15: värden utanför ger exakt samma resultat som gränsen.
    assertEquals(flatSigmoid(15f), flatSigmoid(99f), 0f)
    assertEquals(flatSigmoid(-15f), flatSigmoid(-99f), 0f)
    // Monotont stigande + rimliga ändpunkter.
    assertTrue(flatSigmoid(-15f) < 1e-6f)
    assertTrue(flatSigmoid(15f) > 0.999999f)
    assertTrue(flatSigmoid(1f) > flatSigmoid(0f))
}
```

- [ ] **Step 2: Kör testet — ska faila**

Run: `./gradlew :shared:ml:jvmTest --tests "se.birdy.ml.BirdNetPostprocessTest" 2>&1 | tail -5`
Expected: FAIL/kompileringsfel — `flatSigmoid` finns inte i commonMain.

- [ ] **Step 3: Implementera i `BirdNetPostprocess.kt`**

```kotlin
import kotlin.math.exp

/**
 * BirdNET-Lite emitterar pre-sigmoid-logits; flat sigmoid mappar till [0, 1]
 * (klipp ±15 mot overflow). Speglar BirdNET-Analyzers `flat_sigmoid`.
 * commonMain så Android- och iOS-runnern delar exakt samma formel (i3 T2).
 */
fun flatSigmoid(logit: Float): Float {
    val clipped = logit.coerceIn(-15f, 15f)
    return 1f / (1f + exp(-clipped))
}
```

- [ ] **Step 4: Peka om Android-runnern**

I `AndroidTfliteAudioRunner.kt`: ta bort `private fun flatSigmoid(...)` ur companion (rad ~149–152) och `import kotlin.math.exp` om oanvänd; call-siten `flatSigmoid(outputBuf.float)` löser nu commonMain-funktionen (samma paket — ingen import behövs).

- [ ] **Step 5: Kör tester — ska passera**

Run: `./gradlew :shared:ml:jvmTest :shared:ml:iosSimulatorArm64Test 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL (befintliga AndroidTfliteAudioRunner-relaterade JVM-tester gröna = behavior-preserving bevisat).

- [ ] **Step 6: Full gate + commit**

Kör båda gate-raderna; species.db-restore; sedan:

```bash
git add shared/ml/src
git commit -m "refactor(ml): i3 T2 — flatSigmoid lyft till commonMain, delas av Android- och iOS-audio-runnern"
```

---

### Task 3: `IosTfliteAudioRunner`

**Files:**
- Create: `shared/ml/src/iosMain/kotlin/se/birdy/ml/IosTfliteAudioRunner.kt`
- Test: `shared/ml/src/iosTest/kotlin/se/birdy/ml/IosTfliteAudioRunnerTest.kt`

**Interfaces:**
- Consumes: `flatSigmoid` (task 2), `rankMappedScores` + `BirdNetLabelMapper`/`loadBirdNetLabelMapper()` + `AudioInput`/`AudioClassification`/`AudioModelInfo`/`BirdAudioClassifier` (befintliga commonMain), tflitec-cinteropen (befintlig).
- Produces: `class IosTfliteAudioRunner : BirdAudioClassifier` med `companion object { suspend fun load(modelPath: String): IosTfliteAudioRunner }` — task 7 anropar `IosTfliteAudioRunner.load(path)`.

- [ ] **Step 1: Skriv failande tester**

```kotlin
package se.birdy.ml

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class IosTfliteAudioRunnerTest {
    // Sim-slicen saknar Flex, så en LYCKAD load kan inte testas här (i3-spec §Test).
    // Dessa tester pinnar att felvägen är ett kastat undantag — inte en K/N-krasch
    // (samma ärlighetskrav som AudioClassifierFactory bygger på).

    @Test
    fun garbageModelBytesThrowInsteadOfCrashing() = runTest {
        val mapper = loadBirdNetLabelMapper()
        assertFailsWith<IllegalStateException> {
            IosTfliteAudioRunner(ByteArray(64) { 0x42 }, mapper)
        }
    }

    @Test
    fun emptyModelBytesThrowRequire() = runTest {
        val mapper = loadBirdNetLabelMapper()
        assertFailsWith<IllegalArgumentException> {
            IosTfliteAudioRunner(ByteArray(0), mapper)
        }
    }

    @Test
    fun loadWithMissingFileThrows() = runTest {
        assertFailsWith<IllegalStateException> {
            IosTfliteAudioRunner.load("/nonexistent/birdnet.tflite")
        }
    }
}
```

- [ ] **Step 2: Kör — ska faila (klassen finns inte)**

Run: `./gradlew :shared:ml:iosSimulatorArm64Test --tests "se.birdy.ml.IosTfliteAudioRunnerTest" 2>&1 | tail -5`
Expected: kompileringsfel.

- [ ] **Step 3: Implementera**

```kotlin
package se.birdy.ml

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.pin
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy
import tflitec.TfLiteInterpreterAllocateTensors
import tflitec.TfLiteInterpreterCreate
import tflitec.TfLiteInterpreterDelete
import tflitec.TfLiteInterpreterGetInputTensor
import tflitec.TfLiteInterpreterGetInputTensorCount
import tflitec.TfLiteInterpreterGetOutputTensor
import tflitec.TfLiteInterpreterInvoke
import tflitec.TfLiteInterpreterOptionsCreate
import tflitec.TfLiteInterpreterOptionsDelete
import tflitec.TfLiteInterpreterOptionsSetNumThreads
import tflitec.TfLiteModelCreate
import tflitec.TfLiteModelDelete
import tflitec.TfLiteTensorByteSize
import tflitec.TfLiteTensorCopyFromBuffer
import tflitec.TfLiteTensorCopyToBuffer
import tflitec.TfLiteTensorDim
import tflitec.TfLiteTensorNumDims
import tflitec.kTfLiteOk
import kotlin.system.getTimeMillis

/**
 * iOS-spegel av [AndroidTfliteAudioRunner] för BirdNET-Lite (float32 in/ut, INGEN
 * kvantisering — till skillnad från [IosTfliteRunner]/AIY). Kör på den vendrade
 * TensorFlowLiteC-cinteropen (i2b); FlexRFFT-op:en (node 29) löses av den
 * force_load:ade SelectTfOps-arkiveringen på DEVICE (i3 T1). På SIMULATOR saknas
 * Flex-slicen → create/allocate/invoke failar → kastat fel → ärligt felstate/DEMO
 * via [AudioClassifierFactory] (i3-spec B6).
 *
 * Paritetsregler mot Android-runnern:
 * - Adaptiv [expectedSamples] från inputShape `[1, N]` (guard mot fel modellfil).
 * - Output-guard mot [BirdNetLabelMapper.totalBirdnetClasses] (model/mapping-mismatch).
 * - [flatSigmoid] + [rankMappedScores] ur commonMain — identisk postprocess.
 * - METADATA_INPUT (tensor 1) nollfylls EXPLICIT (i3-spec B4): Android lämnar den
 *   omatad via Interpreter.run(input, output); C-API:t garanterar inte arena-innehåll,
 *   så determinismen görs synlig här. Ingen beteendeskillnad avsedd.
 * - Modellbytes LIFETIME-pinnas ([pinnedModel]-fält, unpin i [close]) —
 *   TfLiteModelCreate kopierar inte FlatBuffern (trap-katalogen).
 * - Mutex-serialisering + idempotent [close], som Android.
 */
@OptIn(ExperimentalForeignApi::class)
class IosTfliteAudioRunner(
    modelBytes: ByteArray,
    private val mapper: BirdNetLabelMapper,
) : BirdAudioClassifier {
    init {
        require(modelBytes.isNotEmpty()) { "Empty model bytes — model file failed to load" }
    }

    private val pinnedModel = modelBytes.pin()

    private val model =
        checkNotNull(TfLiteModelCreate(pinnedModel.addressOf(0), modelBytes.size.convert())) {
            "TfLiteModelCreate returned null — korrupt/ogiltig modellfil"
        }

    private val options =
        checkNotNull(TfLiteInterpreterOptionsCreate()) {
            "TfLiteInterpreterOptionsCreate returned null"
        }.also { TfLiteInterpreterOptionsSetNumThreads(it, NUM_THREADS) }

    private val interpreter =
        checkNotNull(TfLiteInterpreterCreate(model, options)) {
            "TfLiteInterpreterCreate returned null"
        }.also {
            check(TfLiteInterpreterAllocateTensors(it) == kTfLiteOk) {
                "TfLiteInterpreterAllocateTensors failed"
            }
        }

    private val expectedSamples: Int
    private val outputClasses: Int
    override val info: AudioModelInfo

    init {
        val inputTensor =
            checkNotNull(TfLiteInterpreterGetInputTensor(interpreter, 0)) { "input tensor was null" }
        val numDims = TfLiteTensorNumDims(inputTensor)
        val inputShape = List(numDims) { TfLiteTensorDim(inputTensor, it) }
        check(numDims == 2 && inputShape[0] == 1) {
            "Unexpected inputShape $inputShape — expected [1, N] waveform tensor. " +
                "This may indicate the wrong model file was bundled (T1 regression)."
        }
        expectedSamples = inputShape[1]

        val outputTensor =
            checkNotNull(TfLiteInterpreterGetOutputTensor(interpreter, 0)) { "output tensor was null" }
        val outDims = TfLiteTensorNumDims(outputTensor)
        val outputShape = List(outDims) { TfLiteTensorDim(outputTensor, it) }
        outputClasses = outputShape.last()
        check(outputClasses == mapper.totalBirdnetClasses) {
            "Model emits $outputClasses classes but birdnet_lite_to_qid.json " +
                "maps ${mapper.totalBirdnetClasses} — model/mapping mismatch would mis-index species."
        }

        // Nollfyll METADATA_INPUT (tensor 1) en gång — se KDoc.
        if (TfLiteInterpreterGetInputTensorCount(interpreter) >= 2) {
            val meta =
                checkNotNull(TfLiteInterpreterGetInputTensor(interpreter, 1)) { "metadata tensor was null" }
            val byteSize = TfLiteTensorByteSize(meta).toInt()
            if (byteSize > 0) {
                ByteArray(byteSize).usePinned { pinned ->
                    check(
                        TfLiteTensorCopyFromBuffer(meta, pinned.addressOf(0), byteSize.convert()) == kTfLiteOk,
                    ) { "zero-fill of METADATA_INPUT failed" }
                }
            }
        }

        info =
            AudioModelInfo(
                modelVersion = mapper.modelVersion,
                inputShape = inputShape,
                outputShape = outputShape,
                coveragePct = mapper.coveragePct,
            )
    }

    private val mutex = Mutex()
    private var closed = false
    private val logits = FloatArray(outputClasses)

    override suspend fun classify(input: AudioInput): AudioClassification =
        mutex.withLock {
            check(!closed) { "IosTfliteAudioRunner closed" }
            require(input.waveform.size == expectedSamples) {
                "Expected $expectedSamples samples (from model inputShape), got ${input.waveform.size}"
            }

            val t0 = getTimeMillis()
            val inputTensor =
                checkNotNull(TfLiteInterpreterGetInputTensor(interpreter, 0)) { "input tensor was null" }
            input.waveform.usePinned { pinned ->
                check(
                    TfLiteTensorCopyFromBuffer(
                        inputTensor,
                        pinned.addressOf(0),
                        (input.waveform.size * Float.SIZE_BYTES).convert(),
                    ) == kTfLiteOk,
                ) { "TfLiteTensorCopyFromBuffer failed" }
            }

            check(TfLiteInterpreterInvoke(interpreter) == kTfLiteOk) { "TfLiteInterpreterInvoke failed" }

            val outputTensor =
                checkNotNull(TfLiteInterpreterGetOutputTensor(interpreter, 0)) { "output tensor was null" }
            logits.usePinned { pinned ->
                check(
                    TfLiteTensorCopyToBuffer(
                        outputTensor,
                        pinned.addressOf(0),
                        (logits.size * Float.SIZE_BYTES).convert(),
                    ) == kTfLiteOk,
                ) { "TfLiteTensorCopyToBuffer failed" }
            }
            val inferenceMs = getTimeMillis() - t0

            val scores = FloatArray(outputClasses) { flatSigmoid(logits[it]) }
            AudioClassification(
                results = rankMappedScores(scores, mapper::lookup),
                inferenceMs = inferenceMs,
                modelVersion = info.modelVersion,
            )
        }

    override fun close() {
        if (closed) return
        closed = true
        TfLiteInterpreterDelete(interpreter)
        TfLiteInterpreterOptionsDelete(options)
        TfLiteModelDelete(model)
        pinnedModel.unpin()
    }

    companion object {
        private const val NUM_THREADS = 4

        /** Läser modellen från [modelPath] (NSBundle-path, wire:as i IosAppGraph i3 T7). */
        suspend fun load(modelPath: String): IosTfliteAudioRunner {
            val bytes = readFileBytes(modelPath)
            val mapper = loadBirdNetLabelMapper()
            return IosTfliteAudioRunner(bytes, mapper)
        }

        private fun readFileBytes(path: String): ByteArray {
            val data =
                NSData.dataWithContentsOfFile(path)
                    ?: error("Kunde inte läsa modellfil: $path")
            val size = data.length.toInt()
            check(size > 0) { "Modellfilen är tom: $path" }
            val bytes = ByteArray(size)
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
            return bytes
        }
    }
}
```

Notera: om något `TfLiteTensor*`-symbolnamn saknas i `tflitec`-paketet vid kompilering, kolla `shared/ml/src/nativeInterop/cinterop/TensorFlowLiteC.def` — alla funktioner ovan ligger i `c_api.h` som cinteropen redan läser (header-form).

- [ ] **Step 4: Kör testerna — ska passera**

Run: `./gradlew :shared:ml:iosSimulatorArm64Test --tests "se.birdy.ml.IosTfliteAudioRunnerTest" 2>&1 | tail -5`
Expected: PASS ×3.

- [ ] **Step 5: Full gate + commit**

```bash
git add shared/ml/src
git commit -m "feat(ios): i3 T3 — IosTfliteAudioRunner (BirdNET float32-spegel av Android-runnern, metadata-nollfyll, lifetime-pin)"
```

---

### Task 4: `PcmChunker` (TDD) + `IosAudioRecorder` (AVAudioEngine-skal)

**Files:**
- Create: `shared/ml/src/iosMain/kotlin/se/birdy/ml/PcmChunker.kt`
- Create: `shared/ml/src/iosMain/kotlin/se/birdy/ml/IosAudioRecorder.kt`
- Test: `shared/ml/src/iosTest/kotlin/se/birdy/ml/PcmChunkerTest.kt`

**Interfaces:**
- Consumes: inget från andra tasks.
- Produces: `class IosAudioRecorder(val sampleRate: Int = 48_000)` med `fun start(onChunk: (ShortArray, Float, Int) -> Unit, onCapReached: () -> Unit, onError: (Throwable) -> Unit, maxDurationMs: Long): IosRecorderHandle`; `class IosRecorderHandle` med `suspend fun stopAndFlush(): ShortArray` + `fun cancel()`. Task 7:s adapter wrappar dessa till `AudioRecorderApi`/`RecorderHandle`.

- [ ] **Step 1: Failande PcmChunker-tester**

```kotlin
package se.birdy.ml

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PcmChunkerTest {
    private class Sink {
        val chunks = mutableListOf<ShortArray>()
        val rms = mutableListOf<Float>()
        val totals = mutableListOf<Int>()
        var caps = 0
    }

    private fun chunker(
        sink: Sink,
        chunkSize: Int = 4,
        maxSamples: Int = 20,
    ) = PcmChunker(
        chunkSize = chunkSize,
        maxSamples = maxSamples,
        onChunk = { s, r, t ->
            sink.chunks += s
            sink.rms += r
            sink.totals += t
        },
        onCapReached = { sink.caps++ },
    )

    @Test
    fun emitsFixedSizeChunksAcrossUnevenBuffers() {
        val sink = Sink()
        val c = chunker(sink)
        c.accept(shortArrayOf(1, 2, 3))          // 3 pending — inget chunk än
        c.accept(shortArrayOf(4, 5, 6, 7, 8, 9)) // 9 totalt → två 4-chunks, 1 pending
        assertEquals(2, sink.chunks.size)
        assertContentEquals(shortArrayOf(1, 2, 3, 4), sink.chunks[0])
        assertContentEquals(shortArrayOf(5, 6, 7, 8), sink.chunks[1])
        assertEquals(listOf(4, 8), sink.totals)
    }

    @Test
    fun rmsMatchesAndroidFormula() {
        val sink = Sink()
        val c = chunker(sink, chunkSize = 2, maxSamples = 100)
        c.accept(shortArrayOf(16384, -16384)) // |0.5| → rms = 0.5
        assertEquals(1, sink.chunks.size)
        val expected = sqrt((0.5 * 0.5 + 0.5 * 0.5) / 2).toFloat()
        assertEquals(expected, sink.rms[0], 1e-4f)
    }

    @Test
    fun capFiresOnceAndStopsAccumulating() {
        val sink = Sink()
        val c = chunker(sink, chunkSize = 4, maxSamples = 8)
        c.accept(ShortArray(6) { 1 })
        c.accept(ShortArray(6) { 2 }) // passerar cap vid 8
        c.accept(ShortArray(6) { 3 }) // efter cap — ignoreras
        assertEquals(1, sink.caps)
        assertEquals(8, c.snapshot().size)
        assertTrue(sink.totals.all { it <= 8 })
    }

    @Test
    fun snapshotReturnsExactlyCapturedSamples() {
        val sink = Sink()
        val c = chunker(sink, chunkSize = 4, maxSamples = 100)
        c.accept(shortArrayOf(7, 8, 9))
        assertContentEquals(shortArrayOf(7, 8, 9), c.snapshot())
    }
}
```

- [ ] **Step 2: Kör — ska faila**

Run: `./gradlew :shared:ml:iosSimulatorArm64Test --tests "se.birdy.ml.PcmChunkerTest" 2>&1 | tail -5`
Expected: kompileringsfel.

- [ ] **Step 3: Implementera `PcmChunker`**

```kotlin
package se.birdy.ml

import kotlin.math.sqrt

/**
 * Ren, trådlös ackumulator för [IosAudioRecorder]: samlar inkommande PCM-buffertar
 * (godtycklig längd från AVAudioConverter) och emitterar fasta [chunkSize]-chunks med
 * rms + ackumulerad total — samma kadens/format som [AndroidAudioRecorder]s capture-loop
 * (~33 ms à sampleRate/30). Slutar ackumulera vid [maxSamples] och fyrar [onCapReached]
 * EN gång. Trådsäkerhet ägs av anroparen ([IosAudioRecorder] serialiserar via lås).
 */
internal class PcmChunker(
    private val chunkSize: Int,
    private val maxSamples: Int,
    private val onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
    private val onCapReached: () -> Unit,
) {
    private val captured = ShortArray(maxSamples)
    private var total = 0
    private var pendingStart = 0 // index i captured där nästa oemittade chunk börjar
    private var capFired = false

    fun accept(samples: ShortArray) {
        if (capFired) return
        val toCopy = minOf(samples.size, maxSamples - total)
        if (toCopy > 0) {
            samples.copyInto(captured, destinationOffset = total, startIndex = 0, endIndex = toCopy)
            total += toCopy
        }
        while (total - pendingStart >= chunkSize) {
            val chunk = captured.copyOfRange(pendingStart, pendingStart + chunkSize)
            pendingStart += chunkSize
            onChunk(chunk, computeRms(chunk), pendingStart)
        }
        if (total >= maxSamples && !capFired) {
            capFired = true
            onCapReached()
        }
    }

    fun snapshot(): ShortArray = captured.copyOf(total)

    private fun computeRms(buffer: ShortArray): Float {
        if (buffer.isEmpty()) return 0f
        var sum = 0.0
        for (s in buffer) {
            val v = s / 32768.0
            sum += v * v
        }
        return sqrt(sum / buffer.size).toFloat().coerceIn(0f, 1f)
    }
}
```

- [ ] **Step 4: Kör — ska passera**

Run: `./gradlew :shared:ml:iosSimulatorArm64Test --tests "se.birdy.ml.PcmChunkerTest" 2>&1 | tail -5`
Expected: PASS ×4.

- [ ] **Step 5: Implementera `IosAudioRecorder` (AVAudioEngine-skalet — kompileras, device-verifieras i grind 2)**

```kotlin
package se.birdy.ml

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioConverter
import platform.AVFAudio.AVAudioConverterInputStatus_HaveData
import platform.AVFAudio.AVAudioConverterInputStatus_NoDataNow
import platform.AVFAudio.AVAudioConverterOutputStatus_Error
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSLock
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import kotlin.concurrent.Volatile
import kotlin.math.ceil

/**
 * iOS-spegel av [AndroidAudioRecorder]: öppen 48 kHz mono PCM_16-capture via
 * AVAudioEngine-tap + AVAudioConverter (hårdvaruformat → 48k/mono/Int16).
 * Identiskt callback-kontrakt (se [AndroidAudioRecorder] + AudioRecorderApi-KDoc):
 * onChunk på recorderns egen tråd (~33 ms-chunks), onCapReached vid [maxDurationMs],
 * onError HÖGST EN GÅNG (session-avbrott/mic-förlust/start-fel) och aldrig efter
 * stop/cancel. AVAudioSession .record + .measurement ≈ Androids UNPROCESSED.
 *
 * OBS failable-init-trapen (CLAUDE.md): AVAudioFormat/AVAudioConverter är `init?` —
 * konstruktoranropen wrappas i [orNullOnNpe], aldrig elvis direkt på konstruktorn.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAudioRecorder(
    val sampleRate: Int = 48_000,
) {
    fun start(
        onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
        onCapReached: () -> Unit,
        onError: (Throwable) -> Unit = {},
        maxDurationMs: Long = 60_000L,
    ): IosRecorderHandle {
        val maxSamples = (sampleRate * maxDurationMs / 1000L).toInt()
        val session = AVAudioSession.sharedInstance()
        val engine = AVAudioEngine()
        val handle = IosRecorderHandle(engine, session)

        val chunker =
            PcmChunker(
                chunkSize = sampleRate / 30, // ~33 ms — samma kadens som Android
                maxSamples = maxSamples,
                onChunk = { s, r, t -> if (!handle.terminated) onChunk(s, r, t) },
                onCapReached = {
                    // Spegel av Android: capture slutar vid cap; VM:en kör stopAndFlush.
                    handle.stopCaptureOnly()
                    onCapReached()
                },
            )
        handle.chunker = chunker
        handle.onErrorOnce = { t -> onError(t) }

        try {
            memScoped {
                val err = alloc<kotlinx.cinterop.ObjCObjectVar<NSError?>>()
                check(
                    session.setCategory(
                        AVAudioSessionCategoryRecord,
                        mode = AVAudioSessionModeMeasurement,
                        options = 0u,
                        error = err.ptr,
                    ),
                ) { "AVAudioSession.setCategory failed: ${err.value?.localizedDescription}" }
                check(session.setActive(true, error = err.ptr)) {
                    "AVAudioSession.setActive(true) failed: ${err.value?.localizedDescription}"
                }
            }

            val input = engine.inputNode
            val hwFormat = input.inputFormatForBus(0u)
            check(hwFormat.sampleRate > 0.0) { "Ingen mikrofon-input tillgänglig (sampleRate=0)" }

            val targetFormat =
                orNullOnNpe {
                    AVAudioFormat(
                        commonFormat = AVAudioPCMFormatInt16,
                        sampleRate = sampleRate.toDouble(),
                        channels = 1u,
                        interleaved = true,
                    )
                } ?: error("AVAudioFormat(Int16/48k/mono) kunde inte skapas")
            val converter =
                orNullOnNpe { AVAudioConverter(fromFormat = hwFormat, toFormat = targetFormat) }
                    ?: error("AVAudioConverter $hwFormat -> $targetFormat kunde inte skapas")

            // Avbrott (samtal/Siri) = mic stulen → onError en gång (Androids read<=0-motsvarighet).
            handle.interruptionObserver =
                NSNotificationCenter.defaultCenter.addObserverForName(
                    name = AVAudioSessionInterruptionNotification,
                    `object` = null,
                    queue = NSOperationQueue.mainQueue,
                ) { _ ->
                    handle.fireError(IllegalStateException("AVAudioSession interrupted"))
                }

            input.installTapOnBus(0u, bufferSize = 4800u, format = hwFormat) { buffer, _ ->
                val inBuf = buffer ?: return@installTapOnBus
                if (handle.terminated) return@installTapOnBus
                try {
                    val inFrames = inBuf.frameLength.toInt()
                    if (inFrames == 0) return@installTapOnBus
                    val capacity =
                        ceil(inFrames * sampleRate.toDouble() / hwFormat.sampleRate).toInt() + 16
                    val outBuf =
                        orNullOnNpe { AVAudioPCMBuffer(pCMFormat = targetFormat, frameCapacity = capacity.toUInt()) }
                            ?: error("AVAudioPCMBuffer kunde inte skapas")
                    var consumed = false
                    memScoped {
                        val convErr = alloc<kotlinx.cinterop.ObjCObjectVar<NSError?>>()
                        val status =
                            converter.convertToBuffer(
                                outBuf,
                                error = convErr.ptr,
                                withInputFromBlock = { _, outStatus ->
                                    if (consumed) {
                                        outStatus?.pointed?.value = AVAudioConverterInputStatus_NoDataNow
                                        null
                                    } else {
                                        consumed = true
                                        outStatus?.pointed?.value = AVAudioConverterInputStatus_HaveData
                                        inBuf
                                    }
                                },
                            )
                        check(status != AVAudioConverterOutputStatus_Error) {
                            "AVAudioConverter failed: ${convErr.value?.localizedDescription}"
                        }
                    }
                    val outFrames = outBuf.frameLength.toInt()
                    if (outFrames > 0) {
                        val channel =
                            outBuf.int16ChannelData?.get(0)
                                ?: error("int16ChannelData was null")
                        val shorts = ShortArray(outFrames) { channel[it] }
                        handle.withLock { chunker.accept(shorts) }
                    }
                } catch (t: Throwable) {
                    handle.fireError(t)
                }
            }

            memScoped {
                val err = alloc<kotlinx.cinterop.ObjCObjectVar<NSError?>>()
                check(engine.startAndReturnError(err.ptr)) {
                    "AVAudioEngine.start failed: ${err.value?.localizedDescription}"
                }
            }
        } catch (t: Throwable) {
            handle.teardown()
            throw t
        }
        return handle
    }

    private inline fun <T : Any> orNullOnNpe(block: () -> T): T? =
        try {
            block()
        } catch (_: NullPointerException) {
            // K/N mappar failable ObjC-init till konstruktor som kastar NPE vid nil.
            null
        }
}

@OptIn(ExperimentalForeignApi::class)
class IosRecorderHandle internal constructor(
    private val engine: AVAudioEngine,
    private val session: AVAudioSession,
) {
    internal lateinit var chunker: PcmChunker
    internal var onErrorOnce: ((Throwable) -> Unit)? = null
    internal var interruptionObserver: Any? = null

    private val lock = NSLock()

    @Volatile internal var terminated = false
        private set

    @Volatile private var cancelled = false

    @Volatile private var errorFired = false

    internal fun withLock(block: () -> Unit) {
        lock.lock()
        try {
            block()
        } finally {
            lock.unlock()
        }
    }

    internal fun fireError(t: Throwable) {
        if (terminated || errorFired) return
        errorFired = true
        stopCaptureOnly()
        onErrorOnce?.invoke(t)
    }

    /** Stoppar tap + engine (mic-indikatorn släcks) utan att markera handle som stängd för flush. */
    internal fun stopCaptureOnly() {
        runCatching { engine.inputNode.removeTapOnBus(0u) }
        runCatching { engine.stop() }
        runCatching { interruptionObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) } }
        interruptionObserver = null
    }

    internal fun teardown() {
        terminated = true
        stopCaptureOnly()
        runCatching {
            memScoped {
                val err = alloc<kotlinx.cinterop.ObjCObjectVar<NSError?>>()
                session.setActive(false, error = err.ptr)
            }
        }
    }

    suspend fun stopAndFlush(): ShortArray =
        withContext(Dispatchers.Default) {
            teardown()
            if (cancelled) ShortArray(0) else withLockReturning { chunker.snapshot() }
        }

    fun cancel() {
        cancelled = true
        teardown()
    }

    private fun <T> withLockReturning(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }
}
```

Kompilerings-notiser för implementeraren: (a) `int16ChannelData` bryggas som `CPointer<CPointerVar<ShortVar>>?` — `channel[it]` indexerar `CPointer<ShortVar>`; om typen kräver det, använd `channel.get(it)`. (b) `convertToBuffer`-blockets parametertyper dikteras av K/N-bryggan (`AVAudioConverterInputBlock`) — låt kompilatorn visa exakt signatur och anpassa lambda-parametrarna, semantiken ovan är den rätta (leverera `inBuf` EN gång, därefter `NoDataNow`). (c) `session.setCategory`-överlagringen med `mode:`-parameter kräver iOS 10+ — OK (min 16).

- [ ] **Step 6: Kompilera + full gate**

Run: `./gradlew :shared:ml:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosSimulatorArm64 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL. Kör därefter båda gate-raderna.

- [ ] **Step 7: Commit**

```bash
git add shared/ml/src
git commit -m "feat(ios): i3 T4 — IosAudioRecorder (AVAudioEngine 48k mono Int16) + PcmChunker med Android-paritetskadens"
```

---

### Task 5: `IosWaveformRenderer`

**Files:**
- Create: `composeApp/src/iosMain/kotlin/se/birdy/app/ui/audio/IosWaveformRenderer.kt`
- Test: `composeApp/src/iosTest/kotlin/se/birdy/app/ui/audio/IosWaveformRendererTest.kt`

**Interfaces:**
- Consumes: `WaveformRendererApi` (commonMain, `AudioScanViewModel.kt:443`), `uiImageFromDataOrNull` (befintlig internal i `IosImageDecode.kt`, samma modul).
- Produces: `class IosWaveformRenderer : WaveformRendererApi` — task 7 wire:ar `waveformRendererFactory = { IosWaveformRenderer() }`.

- [ ] **Step 1: Failande test**

```kotlin
package se.birdy.app.ui.audio

import kotlinx.coroutines.test.runTest
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.dataWithContentsOfFile
import se.birdy.app.ui.photoanalyze.uiImageFromDataOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosWaveformRendererTest {
    @Test
    fun rendersA600x200PngToDisk() = runTest {
        val out = NSTemporaryDirectory() + "i3-waveform-test.png"
        NSFileManager.defaultManager.removeItemAtPath(out, error = null)
        val pcm = ShortArray(48_000) { ((it % 100) * 300 - 15_000).toShort() }

        val path = IosWaveformRenderer().renderWaveformPng(pcm, out)

        assertEquals(out, path)
        val data = NSData.dataWithContentsOfFile(out)
        assertNotNull(data)
        val image = uiImageFromDataOrNull(data)
        assertNotNull(image)
        image.size.let { /* CGSize via useContents */ }
        // Dimensioner verifieras via UIImage.size (points, scale 1.0 för fil-PNG).
        assertTrue(imageWidth(image) == 600.0 && imageHeight(image) == 200.0)
    }

    @Test
    fun encodeOpusIsDocumentedNullDegrade() = runTest {
        assertNull(IosWaveformRenderer().encodeOpus(ShortArray(4), NSTemporaryDirectory() + "x.opus"))
    }
}
```

Hjälparna `imageWidth`/`imageHeight` läggs överst i testfilen:

```kotlin
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIImage

@OptIn(ExperimentalForeignApi::class)
private fun imageWidth(image: UIImage): Double = image.size.useContents { width }

@OptIn(ExperimentalForeignApi::class)
private fun imageHeight(image: UIImage): Double = image.size.useContents { height }
```

(Är `uiImageFromDataOrNull` `internal` i annat paket och inte når testet — gör den `internal` + samma modul räcker; justera importvägen efter dess faktiska paket i `IosImageDecode.kt`.)

- [ ] **Step 2: Kör — ska faila**

Run: `./gradlew :composeApp:iosSimulatorArm64Test --tests "se.birdy.app.ui.audio.IosWaveformRendererTest" 2>&1 | tail -5`
Expected: kompileringsfel.

- [ ] **Step 3: Implementera**

```kotlin
package se.birdy.app.ui.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.stringByDeletingLastPathComponent
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.Foundation.writeToFile
import kotlin.math.abs

/**
 * iOS-spegel av [AndroidWaveformRenderer]s PNG-rendering: 600×200, 120 buckets,
 * 3-tap-utjämning, Mossbädd-paletten (PaperBg-bakgrund #EFE7D6, MarginaliaInk-staplar
 * #3F4F30, AccentCopper-underlinje #A8552D vid y = height-6, staplar ±40 % av höjden,
 * min-stapel 2 px). [encodeOpus] returnerar null — den dokumenterade degrade-vägen
 * (i3-spec B1; iOS har ingen system-Opus-encoder och .opus kan ändå inte spelas nativt).
 */
class IosWaveformRenderer : WaveformRendererApi {
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun renderWaveformPng(
        pcm: ShortArray,
        outPath: String,
    ): String =
        withContext(Dispatchers.Default) {
            val width = 600
            val height = 200
            val buckets = 120
            val samplesPerBucket = if (pcm.isNotEmpty()) pcm.size / buckets else 1

            val peaks =
                FloatArray(buckets) { b ->
                    var max = 0
                    val start = b * samplesPerBucket
                    val end = (start + samplesPerBucket).coerceAtMost(pcm.size)
                    for (i in start until end) {
                        val v = abs(pcm[i].toInt())
                        if (v > max) max = v
                    }
                    max / 32768f
                }
            val smoothed =
                FloatArray(buckets) { i ->
                    val prev = peaks[(i - 1).coerceAtLeast(0)]
                    val cur = peaks[i]
                    val next = peaks[(i + 1).coerceAtMost(buckets - 1)]
                    (prev + cur + next) / 3f
                }

            val colorSpace = CGColorSpaceCreateDeviceRGB()
            val ctx =
                CGBitmapContextCreate(
                    data = null,
                    width = width.toULong(),
                    height = height.toULong(),
                    bitsPerComponent = 8u,
                    bytesPerRow = 0u,
                    space = colorSpace,
                    bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
                ) ?: error("CGBitmapContextCreate failed")

            // OBS: CG har origo nere-vänster; Android-mallen räknar uppifrån. Vi speglar
            // y-koordinaterna (yCg = height - yAndroid - rectHeight) så PNG:n blir identisk.
            // PaperBg #EFE7D6
            CGContextSetRGBFillColor(ctx, 0xEF / 255.0, 0xE7 / 255.0, 0xD6 / 255.0, 1.0)
            CGContextFillRect(ctx, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))

            // MarginaliaInk #3F4F30 — staplar kring mittlinjen (symmetriska → ingen y-spegling behövs)
            CGContextSetRGBFillColor(ctx, 0x3F / 255.0, 0x4F / 255.0, 0x30 / 255.0, 1.0)
            val barWidth = width.toDouble() / buckets
            val centerY = height / 2.0
            val maxHalfHeight = height * 0.4
            smoothed.forEachIndexed { i, level ->
                val h = (maxHalfHeight * level).coerceAtLeast(2.0)
                val x = i * barWidth
                CGContextFillRect(ctx, CGRectMake(x + 1.0, centerY - h, barWidth - 2.0, h * 2.0))
            }

            // AccentCopper #A8552D — underlinje vid Android-y (height-6), CG-y = 6-2/2 → fyll rect 2 px hög vid y=5
            CGContextSetRGBFillColor(ctx, 0xA8 / 255.0, 0x55 / 255.0, 0x2D / 255.0, 1.0)
            CGContextFillRect(ctx, CGRectMake(0.0, 5.0, width.toDouble(), 2.0))

            val cgImage = CGBitmapContextCreateImage(ctx) ?: error("CGBitmapContextCreateImage failed")
            val image = UIImage(cGImage = cgImage)
            val png = UIImagePNGRepresentation(image) ?: error("UIImagePNGRepresentation failed")

            @Suppress("CAST_NEVER_SUCCEEDS")
            val parent = (outPath as NSString).stringByDeletingLastPathComponent
            NSFileManager.defaultManager.createDirectoryAtPath(
                parent,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
            check(png.writeToFile(outPath, atomically = true)) { "PNG write failed: $outPath" }
            outPath
        }

    override suspend fun encodeOpus(
        pcm: ShortArray,
        outPath: String,
    ): String? = null
}
```

(CG-minnesregler: `CGBitmapContextCreate`/`CGBitmapContextCreateImage`/`CGColorSpaceCreateDeviceRGB` returnerar +1-referenser — K/N:s CF-brygga hanterar release automatiskt för returnerade `CFTypeRef`-managed objekt i moderna Kotlin; om memory-lint klagar, spegla `IosScanFramePersist.kt`s hantering.)

- [ ] **Step 4: Kör — ska passera**

Run: `./gradlew :composeApp:iosSimulatorArm64Test --tests "se.birdy.app.ui.audio.IosWaveformRendererTest" 2>&1 | tail -5`
Expected: PASS ×2.

- [ ] **Step 5: Full gate + commit**

```bash
git add composeApp/src
git commit -m "feat(ios): i3 T5 — IosWaveformRenderer (CoreGraphics-PNG-spegel av Android-renderern; encodeOpus = null-degrade)"
```

---

### Task 6: `IosAudioPermissionController`

**Files:**
- Create: `composeApp/src/iosMain/kotlin/se/birdy/app/ui/audio/IosAudioPermissionController.kt`
- Test: `composeApp/src/iosTest/kotlin/se/birdy/app/ui/audio/IosAudioPermissionControllerTest.kt`

**Interfaces:**
- Consumes: `actual interface AudioPermissionController` + `enum PermissionState { Unknown, Granted, Denied, PermanentlyDenied }` (befintliga).
- Produces: `class IosAudioPermissionController : AudioPermissionController` — task 8:s host instansierar den; `internal fun mapRecordPermission(raw: AVAudioSessionRecordPermission): PermissionState`.

- [ ] **Step 1: Failande test av mappningen**

```kotlin
package se.birdy.app.ui.audio

import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVAudioSessionRecordPermissionUndetermined
import kotlin.test.Test
import kotlin.test.assertEquals

class IosAudioPermissionControllerTest {
    @Test
    fun mapsRecordPermissionToCommonStates() {
        assertEquals(PermissionState.Unknown, mapRecordPermission(AVAudioSessionRecordPermissionUndetermined))
        assertEquals(PermissionState.Granted, mapRecordPermission(AVAudioSessionRecordPermissionGranted))
        // iOS har ingen "fråga igen"-nivå: denied ⇒ endast Inställningar hjälper.
        assertEquals(PermissionState.PermanentlyDenied, mapRecordPermission(AVAudioSessionRecordPermissionDenied))
    }
}
```

- [ ] **Step 2: Kör — ska faila**

Run: `./gradlew :composeApp:iosSimulatorArm64Test --tests "se.birdy.app.ui.audio.IosAudioPermissionControllerTest" 2>&1 | tail -5`

- [ ] **Step 3: Implementera**

```kotlin
package se.birdy.app.ui.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermission
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS-actual-implementation av [AudioPermissionController] via AVAudioSession
 * (INTE AVAudioApplication — den är iOS 17+, deployment target är 16.0).
 * Spegel av [se.birdy.app.permissions.rememberIosCameraPermissionState]s mönster:
 * request → systemdialog (endast första gången), därefter är denied permanent
 * på iOS → [PermissionState.PermanentlyDenied] + openSettings. recheck() anropas
 * av hosten på UIApplicationDidBecomeActive (fångar toggle i Inställningar).
 */
class IosAudioPermissionController : AudioPermissionController {
    private val _state = MutableStateFlow(currentState())
    override val state: StateFlow<PermissionState> = _state.asStateFlow()

    override fun request() {
        AVAudioSession.sharedInstance().requestRecordPermission { granted ->
            dispatch_async(dispatch_get_main_queue()) {
                _state.value = if (granted) PermissionState.Granted else PermissionState.PermanentlyDenied
            }
        }
    }

    override fun openSettings() {
        val url = NSURL(string = UIApplicationOpenSettingsURLString)
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }

    override fun recheck() {
        _state.value = currentState()
    }

    private fun currentState(): PermissionState =
        mapRecordPermission(AVAudioSession.sharedInstance().recordPermission)
}

internal fun mapRecordPermission(raw: AVAudioSessionRecordPermission): PermissionState =
    when (raw) {
        AVAudioSessionRecordPermissionGranted -> PermissionState.Granted
        AVAudioSessionRecordPermissionDenied -> PermissionState.PermanentlyDenied
        else -> PermissionState.Unknown
    }
```

- [ ] **Step 4: Kör — ska passera**

Run: `./gradlew :composeApp:iosSimulatorArm64Test --tests "se.birdy.app.ui.audio.IosAudioPermissionControllerTest" 2>&1 | tail -5`
Expected: PASS.

- [ ] **Step 5: Full gate + commit**

```bash
git add composeApp/src
git commit -m "feat(ios): i3 T6 — IosAudioPermissionController (AVAudioSession, denied=permanent + openSettings)"
```

---

### Task 7: Adapter + IosAppGraph-wiring (CAS-spegel) + versionName

**Files:**
- Create: `composeApp/src/iosMain/kotlin/se/birdy/app/ui/audio/IosAudioRecorderAdapter.kt`
- Modify: `composeApp/src/iosMain/kotlin/se/birdy/app/IosAppGraph.kt`

**Interfaces:**
- Consumes: `IosAudioRecorder`/`IosRecorderHandle` (task 4), `IosTfliteAudioRunner.load` (task 3), `IosWaveformRenderer` (task 5), `AudioClassifierFactory`/`FakeAudioClassifier`/`AudioClassifierMode` (befintliga), `AppGraph`-parametrarna `audioClassifierProvider`/`audioStorageDir`/`audioRecorderFactory`/`waveformRendererFactory` (befintliga, idag ovirade på iOS).
- Produces: fullt wirad audio-graf — task 8:s host kan anropa `graph.audioScanViewModel()` utan `error(...)`.

- [ ] **Step 1: Adaptern**

```kotlin
package se.birdy.app.ui.audio

import se.birdy.ml.IosAudioRecorder

/** iOS-spegel av [AndroidAudioRecorderAdapter]: bryggar [IosAudioRecorder] till [AudioRecorderApi]. */
class IosAudioRecorderAdapter(
    private val recorder: IosAudioRecorder = IosAudioRecorder(),
) : AudioRecorderApi {
    override fun start(
        onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
        onCapReached: () -> Unit,
        onError: (Throwable) -> Unit,
        maxDurationMs: Long,
    ): RecorderHandle {
        val iosHandle = recorder.start(onChunk, onCapReached, onError, maxDurationMs)
        return object : RecorderHandle {
            override suspend fun stopAndFlush(): ShortArray = iosHandle.stopAndFlush()

            override fun cancel() = iosHandle.cancel()
        }
    }
}
```

- [ ] **Step 2: IosAppGraph — audio-bootstrap-objekt (trogen MainActivity-spegel)**

Lägg i `IosAppGraph.kt` (efter `buildIosAppGraph`, före `NsUserDefaultsBadgeVersionStore`), plus imports (`kotlinx.coroutines.*`, `platform.Foundation.*` för NSBundle/NSFileManager/NSSearchPath, `se.birdy.app.ui.audio.IosAudioRecorderAdapter`, `se.birdy.app.ui.audio.IosWaveformRenderer`, `se.birdy.ml.AudioClassifierFactory`, `se.birdy.ml.AudioClassifierMode`, `se.birdy.ml.BirdAudioClassifier`, `se.birdy.ml.FakeAudioClassifier`, `se.birdy.ml.IosTfliteAudioRunner`, `kotlin.concurrent.AtomicReference`, `kotlin.experimental.ExperimentalNativeApi`):

```kotlin
/**
 * iOS-spegel av MainActivitys audio-bootstrap (Deferred-CAS-cache): modellen (54 MB)
 * laddas högst en gång, LAZY så förlorar-grenens Deferred aldrig startar, och en
 * FAILAD Deferred evictas före rethrow så "Försök igen" gör ett RIKTIGT nytt försök
 * (vC127-fix #8). Skillnader mot Android: scope är app-livstid (grafen dör aldrig på
 * iOS — ingen onDestroy-close behövs), BuildConfig.DEBUG → Platform.isDebugBinary.
 */
internal object IosAudioBootstrap {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cache =
        AtomicReference<Deferred<Pair<BirdAudioClassifier, AudioClassifierMode>>?>(null)

    @Suppress("TooGenericExceptionCaught")
    val provider: suspend () -> Pair<BirdAudioClassifier, AudioClassifierMode> =
        provider@{
            while (true) {
                val cached = cache.value
                val deferred: Deferred<Pair<BirdAudioClassifier, AudioClassifierMode>> =
                    if (cached != null) {
                        cached
                    } else {
                        val newDeferred =
                            scope.async(start = CoroutineStart.LAZY) { build() }
                        if (cache.compareAndSet(null, newDeferred)) {
                            newDeferred
                        } else {
                            cache.value ?: continue
                        }
                    }
                val result =
                    try {
                        deferred.await()
                    } catch (t: Throwable) {
                        // Generisk catch avsiktlig (spegel av MainActivity): native-laddfel
                        // kan vara Errors. Evicta ENDAST när deferred SJÄLV failade — en
                        // caller-cancel får inte evicta en frisk in-flight-load.
                        @OptIn(ExperimentalCoroutinesApi::class)
                        val deferredFailed =
                            deferred.isCompleted && deferred.getCompletionExceptionOrNull() != null
                        if (deferredFailed) {
                            cache.compareAndSet(deferred, null)
                        }
                        throw t
                    }
                if (cache.value === deferred) {
                    return@provider result
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("audioProvider loop exited unexpectedly")
        }

    @OptIn(ExperimentalNativeApi::class)
    private suspend fun build(): Pair<BirdAudioClassifier, AudioClassifierMode> =
        AudioClassifierFactory(
            createReal = { IosTfliteAudioRunner.load(bundledBirdnetPath()) },
            createFallback = { FakeAudioClassifier() },
            onDegrade = { t -> println("Birdy/audio: classifier degrade: ${t.message}") },
            allowFallback = Platform.isDebugBinary,
        ).create()

    private fun bundledBirdnetPath(): String =
        NSBundle.mainBundle.pathForResource("birdnet_lite_v2", ofType = "tflite")
            ?: error("birdnet_lite_v2.tflite saknas i app-bundlen — kontrollera project.yml-resursen (i3 T1)")
}

internal fun audioStorageDirPath(): String {
    val docs =
        NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .first() as String
    val dir = "$docs/audio"
    NSFileManager.defaultManager.createDirectoryAtPath(
        dir,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return dir
}
```

(`kotlin.concurrent.AtomicReference` på K/N: `.value`/`.compareAndSet` som ovan. `Platform` = `kotlin.native.Platform`.)

- [ ] **Step 3: Wire:a in i `AppGraph(...)`-anropet + versionName**

I `buildIosAppGraph()`s `return AppGraph(...)`: lägg till fyra rader och bumpa versionName:

```kotlin
        audioClassifierProvider = IosAudioBootstrap.provider,
        audioStorageDir = ::audioStorageDirPath,
        audioRecorderFactory = { IosAudioRecorderAdapter() },
        waveformRendererFactory = { IosWaveformRenderer() },
        versionName = "1.2.0-ios-i3",
```

Uppdatera även KDoc-blocket överst i filen: lägg till raden `* i3 resolved: audio-ID wirad (IosTfliteAudioRunner + IosAudioRecorder; Flex endast device — sim visar felstate/DEMO).`

- [ ] **Step 4: Full gate**

Run: båda gate-raderna. Expected: gröna (ingen ny test — wiring bevisas av att `:composeApp:iosSimulatorArm64Test` + länken kompilerar och att befintliga VM-tester är orörda).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src
git commit -m "feat(ios): i3 T7 — IosAppGraph wirar audio (CAS-Deferred-spegel av MainActivity + eviction-fix); versionName 1.2.0-ios-i3"
```

---

### Task 8: `AudioScanScreenHost.ios` + mic-usage-strings

**Files:**
- Modify: `composeApp/src/iosMain/kotlin/se/birdy/app/ui/audio/AudioScanScreenHost.ios.kt` (ersätt ComingSoon-stubben)
- Modify: `iosApp/iosApp/Info.plist`, `iosApp/iosApp/en.lproj/InfoPlist.strings`, `iosApp/iosApp/sv.lproj/InfoPlist.strings`

**Interfaces:**
- Consumes: `IosAudioPermissionController` (task 6), wirad graf (task 7), `AudioScanScreen`/`AudioScanState`/`AudioScanViewModel` (befintliga commonMain — host-anropet speglar `AudioScanScreenHost.android.kt:84-95`).
- Produces: fungerande Lyssna-flöde på iOS (sim: t.o.m. ärligt felstate/DEMO; device: hela vägen).

- [ ] **Step 1: Ersätt hosten**

```kotlin
package se.birdy.app.ui.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import se.birdy.app.di.AppGraph

@Composable
actual fun AudioScanScreenHost(
    graph: AppGraph,
    onNavigateToMatch: (sourceJson: String, capturedAtMs: Long) -> Unit,
    onBack: () -> Unit,
) {
    val permissionController = remember { IosAudioPermissionController() }

    // Omkontroll när appen blir aktiv igen — fångar toggle i Inställningar
    // (spegel av Android-hostens ON_RESUME-observer / i2c-kameramönstret).
    DisposableEffect(Unit) {
        val observer =
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = UIApplicationDidBecomeActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ -> permissionController.recheck() }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }

    val permissionState by permissionController.state.collectAsState()

    val vm = remember(graph) { graph.audioScanViewModel() }

    // KONTRAKT (AudioScanScreenHost-expecten): stoppa inspelningen när skärmen
    // lämnar kompositionen — annars läcker mikrofonen upp till 60 s.
    DisposableEffect(vm) {
        onDispose { vm.cancelRecording() }
    }

    val state by vm.state.collectAsState()
    val demoMode by vm.demoMode.collectAsState()

    LaunchedEffect(permissionState) {
        vm.onPermissionState(permissionState)
    }

    LaunchedEffect(state) {
        val s = state
        if (s is AudioScanState.NavigateToMatch) onNavigateToMatch(s.sourceJson, s.capturedAtMs)
    }

    AudioScanScreen(
        state = state,
        permissionState = permissionState,
        demoMode = demoMode,
        onStartRecording = vm::startRecording,
        onStopRecording = vm::stopRecording,
        onCancelAnalyzing = vm::cancelRecording,
        onRequestPermission = permissionController::request,
        onOpenSettings = permissionController::openSettings,
        onRetry = vm::cancelRecording,
        onBack = onBack,
    )
}
```

(Ta bort `IosComingSoonPanel`-importen; panelen används fortfarande av kartan — radera INTE komponenten.)

- [ ] **Step 2: Mic-usage-strings**

`iosApp/iosApp/Info.plist` — lägg till efter `NSCameraUsageDescription`-paret:

```xml
    <key>NSMicrophoneUsageDescription</key>
    <string>Birdy uses the microphone to identify birds by their song. Audio is analyzed on your device and never leaves it.</string>
```

`iosApp/iosApp/en.lproj/InfoPlist.strings` — lägg till rad:

```text
"NSMicrophoneUsageDescription" = "Birdy uses the microphone to identify birds by their song. Audio is analyzed on your device and never leaves it.";
```

`iosApp/iosApp/sv.lproj/InfoPlist.strings` — lägg till rad:

```text
"NSMicrophoneUsageDescription" = "Birdy använder mikrofonen för att identifiera fåglar via deras läten. Ljudet analyseras på din enhet och lämnar den aldrig.";
```

- [ ] **Step 3: Sim-bygg + boot-verify**

```bash
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
cd iosApp && ~/.local/bin/xcodegen generate && cd ..
xcodebuild -project iosApp/Birdy.xcodeproj -scheme Birdy -configuration Debug \
  -destination "id=183DD149-45ED-49B8-A2C1-70317698B383" -derivedDataPath iosApp/build/dd build 2>&1 | tail -3
xcrun simctl boot 183DD149-45ED-49B8-A2C1-70317698B383 2>/dev/null || true
APP=$(find iosApp/build/dd/Build/Products -name Birdy.app -maxdepth 3 | head -1)
xcrun simctl install 183DD149-45ED-49B8-A2C1-70317698B383 "$APP"
xcrun simctl launch 183DD149-45ED-49B8-A2C1-70317698B383 se.birdy.ios
sleep 6
xcrun simctl io 183DD149-45ED-49B8-A2C1-70317698B383 screenshot docs/superpowers/screenshots/i3-01-ios-boot.png
```

Expected: BUILD SUCCEEDED + appen bootar (screenshotten visar Identifiera-fliken, inte svart/krasch). Läs screenshotten och verifiera. (Tap-interaktion är inte agent-möjlig — Lyssna-flödet är Albins sim-check, grind 1.)

- [ ] **Step 4: Full gate + commit**

Kör båda gate-raderna + species.db-restore.

```bash
git add composeApp/src iosApp/iosApp iosApp/Birdy.xcodeproj docs/superpowers/screenshots/i3-01-ios-boot.png
git commit -m "feat(ios): i3 T8 — AudioScanScreenHost.ios (Lyssna-flödet live) + mic-usage-strings SV+EN"
```

---

### Task 9: Desktop-referens (full-TF flexref) + fixtur

**Files:**
- Create: `tools/ml-eval/flexref/pyproject.toml`
- Create: `tools/ml-eval/flexref/gen_fixture.py`
- Create: `tools/ml-eval/flexref/reference.py`
- Create: `tools/ml-eval/flexref/fixtures/chirp_3s_48k.wav` (genereras av gen_fixture.py, committas)
- Create: `docs/superpowers/research/2026-08-16-i3-audio-reference-facit.md` (genereras av reference.py, committas)

**Interfaces:**
- Consumes: modellfilen + `composeApp/src/commonMain/composeResources/files/ml/birdnet_lite_to_qid.json` (läses direkt från repo-sökvägar).
- Produces: facit-dokumentet som device-verifyn (grind 2) jämför mot; `reference.py <wav>` är återanvändbar för godtyckliga klipp Albin vill facit-sätta senare.

- [ ] **Step 1: pyproject**

```toml
[project]
name = "birdy-flexref"
version = "0.1.0"
description = "Full-TF-referens för BirdNET-audio (ai-edge-litert saknar Flex — verifierat 2026-08-15)"
requires-python = ">=3.12"
dependencies = [
    "tensorflow==2.19.0",
]
```

- [ ] **Step 2: gen_fixture.py**

```python
"""Genererar en deterministisk 3s/48kHz mono-chirp som paritetsfixtur.

Syntetisk (ingen licensfråga, till skillnad från xeno-canto-material). Facit är
inte "rätt art" utan EXAKT vilka top-3 den delade pipelinen ger — samma bytes in
ska ge samma svar på desktop-referensen och (via mic-luft-gapet approximativt)
på device. Determinism: ren matte, ingen slump.
"""
import math
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 48_000
SECONDS = 3
OUT = Path(__file__).parent / "fixtures" / "chirp_3s_48k.wav"


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    n = SAMPLE_RATE * SECONDS
    samples = []
    for i in range(n):
        t = i / SAMPLE_RATE
        f = 2000.0 + (6000.0 * t / SECONDS)  # svep 2->8 kHz (fågelsångs-registret)
        v = 0.5 * math.sin(2 * math.pi * f * t) + 0.2 * math.sin(2 * math.pi * 2 * f * t)
        env = math.sin(math.pi * t / SECONDS)  # fade in/ut
        samples.append(int(max(-1.0, min(1.0, v * env)) * 32767))
    with wave.open(str(OUT), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        w.writeframes(struct.pack(f"<{n}h", *samples))
    print(f"Skrev {OUT} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
```

- [ ] **Step 3: reference.py**

```python
"""Kör BirdNET-Lite med full TF (Flex ingår) och skriver facit-top-3.

Pipeline-spegel av appens delade postprocess: normalize (/32768) ->
flat_sigmoid (klipp +-15) -> filtrera till mappade EU-klasser FÖRE ranking ->
top-3. Se shared/ml BirdNetPostprocess.kt + AudioScanViewModel.

Användning:
    uv run python reference.py                # chirp-fixturen + nolltest
    uv run python reference.py path/to.wav    # godtyckligt 48k mono 16-bit wav
"""
import json
import struct
import sys
import wave
from pathlib import Path

import numpy as np
import tensorflow as tf

REPO = Path(__file__).resolve().parents[3]
MODEL = REPO / "composeApp/src/androidMain/assets/models/birdnet_lite_v2.tflite"
MAPPING = REPO / "composeApp/src/commonMain/composeResources/files/ml/birdnet_lite_to_qid.json"
FACIT = REPO / "docs/superpowers/research/2026-08-16-i3-audio-reference-facit.md"
FIXTURE = Path(__file__).parent / "fixtures" / "chirp_3s_48k.wav"


def load_wav(path: Path) -> np.ndarray:
    with wave.open(str(path), "rb") as w:
        assert w.getframerate() == 48_000 and w.getnchannels() == 1 and w.getsampwidth() == 2, (
            "kräver 48 kHz mono 16-bit"
        )
        raw = w.readframes(w.getnframes())
    pcm = np.array(struct.unpack(f"<{len(raw) // 2}h", raw), dtype=np.float32)
    return pcm / 32768.0


def top3(waveform: np.ndarray, interp, lookup) -> list[tuple[str, float]]:
    n = 144_000
    x = np.zeros(n, dtype=np.float32)
    x[: min(n, len(waveform))] = waveform[:n]
    inputs = interp.get_input_details()
    interp.set_tensor(inputs[0]["index"], x.reshape(1, n))
    if len(inputs) > 1:
        interp.set_tensor(inputs[1]["index"], np.zeros((1, 6), dtype=np.float32))
    interp.invoke()
    logits = interp.get_tensor(interp.get_output_details()[0]["index"])[0]
    scores = 1.0 / (1.0 + np.exp(-np.clip(logits, -15.0, 15.0)))
    mapped = [(lookup[i], float(s)) for i, s in enumerate(scores) if lookup.get(i)]
    return sorted(mapped, key=lambda p: -p[1])[:3]


def main() -> None:
    mapping = json.loads(MAPPING.read_text())
    # birdnet_lite_to_qid.json:s struktur: kontrollera nyckeln som bär index->qid
    # (samma data som BirdNetLabelMapper.parse läser) och bygg lookup därefter.
    entries = mapping["classes"] if "classes" in mapping else mapping
    lookup = {int(k): v for k, v in entries.items()} if isinstance(entries, dict) else {
        i: e.get("qid") for i, e in enumerate(entries)
    }
    interp = tf.lite.Interpreter(model_path=str(MODEL))
    interp.allocate_tensors()

    targets = [Path(sys.argv[1])] if len(sys.argv) > 1 else [FIXTURE]
    lines = [
        "# i3 audio — desktop-referensfacit (full TF, Flex inkluderad)",
        "",
        f"Modell: `{MODEL.relative_to(REPO)}` · TF {tf.__version__} · pipeline = normalize → flat_sigmoid(±15) → filter-före-ranking → top-3.",
        "",
    ]
    for wav in targets:
        result = top3(load_wav(wav), interp, lookup)
        lines.append(f"## {wav.name}")
        lines += [f"- {qid}: {conf:.6f}" for qid, conf in result] or ["- (tomt)"]
        lines.append("")
    zeros = top3(np.zeros(144_000, dtype=np.float32), interp, lookup)
    lines.append("## tystnad (144000 nollor)")
    lines += [f"- {qid}: {conf:.6f}" for qid, conf in zeros]
    lines.append("")
    FACIT.write_text("\n".join(lines))
    print(FACIT)
    print("\n".join(lines[4:]))


if __name__ == "__main__":
    main()
```

**OBS till implementeraren:** mappnings-parsningen ovan är skriven defensivt eftersom `birdnet_lite_to_qid.json`:s exakta struktur inte är fastslagen i planen — öppna filen först (`head -c 400 composeApp/src/commonMain/composeResources/files/ml/birdnet_lite_to_qid.json`) och anpassa `lookup`-bygget så det speglar `BirdNetLabelMapper.parse` exakt (index → qid, omappade = None). Sanity-korset: antalet mappade poster ska vara `6362 - 5735 = 627`-ish (se BirdNetPostprocess-KDoc:en).

- [ ] **Step 4: Kör + committa artefakterna**

```bash
cd tools/ml-eval/flexref
~/.local/bin/uv sync
~/.local/bin/uv run python gen_fixture.py
~/.local/bin/uv run python reference.py
cd ../../..
```

Expected: facit-filen skriven med top-3 för chirp + tystnad (confidences förväntas LÅGA — under Match-tröskeln 0.50; det är facit, inte ett fel). Verifiera att tystnads-top-3 inte innehåller någon hög confidence (>0.5) — gör den det, flagga i facit-docen.

- [ ] **Step 5: Commit**

```bash
git add tools/ml-eval/flexref docs/superpowers/research/2026-08-16-i3-audio-reference-facit.md
git commit -m "feat(tools): i3 T9 — full-TF desktop-referens för BirdNET-audio + deterministisk chirp-fixtur + facit"
```

(`.venv/` under flexref ska INTE committas — kolla att `tools/ml-eval/.gitignore` eller rotens `.gitignore` täcker `**/.venv/`; annars lägg till `tools/ml-eval/flexref/.venv/` i rotens `.gitignore` i samma commit.)

---

### Task 10: CLAUDE.md-synk + slutgate

**Files:**
- Modify: `CLAUDE.md` (Status-sektionen + i3-raden i Plan-of-plans (v2))
- Modify: `docs/superpowers/plans/2026-08-16-ios-i3-audio-id.md` (bocka av)

**Interfaces:** inga.

- [ ] **Step 1: Full slutgate — båda raderna + device-länken**

Kör båda gate-raderna PLUS device-bygget ur T1 step 7 (utan mätning — bara BUILD SUCCEEDED) så force_load-länken bevisas grön på slutläget.

- [ ] **Step 2: CLAUDE.md**

- Ny Status-post överst (under NÄSTA ARBETSPASS-posten): "**i3 (iOS ljud-ID) KODKLAR — Albins sim-check + device-verify kvar (2026-08-XX, Mac):** kört SDD på plan `docs/superpowers/plans/2026-08-16-ios-i3-audio-id.md` (spec `...-ios-i3-audio-id-design.md`), tasks 1–10, storleksgatens uppmätta delta = <SIFFRA> MB. KVAR: grind 1 (sim-check: Lyssna → permission → felstate/DEMO) + grind 2 (device-verify på iPhone, jämför `docs/superpowers/research/2026-08-16-i3-audio-reference-facit.md`)." — fyll i faktiskt datum, delta-siffran från T1 och ev. avvikelser/fixrundor.
- i3-raden i Plan-of-plans (v2): `🔄 kod klar + review:ad; kvar: Albins sim-check + device-verify (kräver iPhone)`.

- [ ] **Step 3: Commit + push**

```bash
git add CLAUDE.md docs/superpowers/plans/2026-08-16-ios-i3-audio-id.md
git commit -m "chore(ios): i3 T10 — CLAUDE.md-synk; ljud-ID kodklar, sim-check + device-verify kvar"
git push
```
