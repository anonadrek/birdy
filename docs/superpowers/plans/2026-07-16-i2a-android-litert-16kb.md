# i2a — Android LiteRT Migration (16 KB) + Classifier First-Run Fix Implementation Plan

> **✅ STATUS: KOD KLAR + AAB-VERIFIERAD (2026-07-18).** Alla 6 tasks körda via subagent-driven-development (två-stegs-review grön per task), commits `c10f557d..b321ff43` på `main`. vC126 / 1.2.1 release-AAB byggd + 16 KB-verifierad (ELF p_align=0x4000 på alla 15 .so, `zipalign -c -P 16` exit 0). Tre review-drivna förbättringar utöver plan-texten: symmetrisk `init`-guard även i `AndroidTfliteRunner`; flex-download-tasken gjord configuration-cache-kompatibel med härledd ABI-lista (en sanningskälla); död `sha256Of`-helper borttagen. KVAR = endast Albins device-verify (foto-ID första försöket + audio node-29 + live-scan) → Play Console-upload av vC126-AAB. Se CLAUDE.md Status.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Android vC126-ready: every `.so` in the release AAB 16 KB-aligned, photo/audio ID regression-free, classifier first-run bug fixed + instrumented.

**Architecture:** Swap the TFLite core Gradle coordinate for LiteRT 1.4.1 (same `org.tensorflow.lite.*` classes, zero source changes) to fix the photo `.so`; replace the select-tf-ops AAR's 4 KB flex `.so` at build time with a SHA-256-pinned 16 KB community build (jniLibs + pickFirsts) to fix the audio `.so`; retain model buffers as fields in both runners (TFLite never copies the model buffer — GC could free it → dangling pointer = the first-run bug); add a committed ELF-alignment gate tool.

**Tech Stack:** Kotlin Multiplatform, AGP/Gradle Kotlin DSL, LiteRT 1.4.1, Python 3 (stdlib only) for the gate tool.

**Spec:** `docs/superpowers/specs/2026-07-16-i2a-android-litert-16kb-design.md`

**Facts pre-verified during planning (2026-07-16, do not re-derive):**
- Flex release `tf-a95156b81d38` (2026-04-30) assets downloaded; SHA-256 computed by the planner; all three ELF-parsed: every PT_LOAD `p_align = 0x4000`. The checksums in Task 4 are authoritative pins — a mismatch at build time means the release changed and MUST fail the build.
- `org.tensorflow.lite.support` has zero imports in the codebase → the support dep can be dropped.
- detekt: `PrintStackTrace` active (use `println(...stackTraceToString())`, never `printStackTrace()`); `UnusedPrivateProperty` active (retained buffers must be *read* somewhere — no `@Suppress`).
- Machine tools: Python 3.13 on PATH as `python`; Android build-tools `37.0.0`; `tools/bundletool.jar` in repo.

**Work protocol:** Directly on `main` (repo sync rule; Mac runs i2b in parallel — `git pull --ff-only` before every push). Never commit `gradle.properties` (holds the local MapTiler key). Repo gate that must be green before every commit:

```bash
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt
```

---

### Task 1: 16 KB alignment gate tool + red baseline

**Files:**
- Create: `tools/check_16kb_alignment.py`

- [ ] **Step 1: Write the tool**

Create `tools/check_16kb_alignment.py` with exactly this content:

```python
#!/usr/bin/env python3
"""Fail if any PT_LOAD segment of any .so inside the given APK/AAB is below 16 KB alignment.

Usage: python tools/check_16kb_alignment.py <path.apk|path.aab> [more ...]
Exit 0 = every .so has all PT_LOAD p_align >= 0x4000. Exit 1 = at least one below, or no .so found.

Background: Google Play requires 16 KB page-size support (targetSdk 35+ with native code).
vC125 shipped on a per-version skip. Diagnosis + fix: docs/superpowers/specs/
2026-07-16-i2a-android-litert-16kb-design.md
"""
import struct
import sys
import zipfile

REQUIRED = 0x4000


def pt_load_aligns(blob):
    """Return list of p_align for all PT_LOAD program headers, or None if not ELF."""
    if blob[:4] != b"\x7fELF":
        return None
    is64 = blob[4] == 2
    if is64:
        (e_phoff,) = struct.unpack_from("<Q", blob, 0x20)
        (e_phentsize,) = struct.unpack_from("<H", blob, 0x36)
        (e_phnum,) = struct.unpack_from("<H", blob, 0x38)
    else:
        (e_phoff,) = struct.unpack_from("<I", blob, 0x1C)
        (e_phentsize,) = struct.unpack_from("<H", blob, 0x2A)
        (e_phnum,) = struct.unpack_from("<H", blob, 0x2C)
    aligns = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        (p_type,) = struct.unpack_from("<I", blob, off)
        if p_type == 1:  # PT_LOAD
            fmt, rel = ("<Q", 0x30) if is64 else ("<I", 0x1C)
            (p_align,) = struct.unpack_from(fmt, blob, off + rel)
            aligns.append(p_align)
    return aligns


def main(paths):
    failures, checked = [], 0
    for archive in paths:
        with zipfile.ZipFile(archive) as z:
            for name in z.namelist():
                if not name.endswith(".so"):
                    continue
                aligns = pt_load_aligns(z.read(name))
                if aligns is None:
                    continue
                checked += 1
                worst = min(aligns) if aligns else 0
                ok = worst >= REQUIRED
                print(f"{'OK  ' if ok else 'FAIL'} {archive}!{name}  p_align={hex(worst)}")
                if not ok:
                    failures.append(name)
    print(f"\n{checked} .so checked, {len(failures)} below 16 KB")
    return 1 if failures or checked == 0 else 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1:]))
```

- [ ] **Step 2: Build the current (pre-migration) debug APK**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the tool — expect RED on exactly the two TFLite libs**

Run: `python tools/check_16kb_alignment.py androidApp/build/outputs/apk/debug/androidApp-debug.apk`
(If the APK name differs, use the single `.apk` in that directory.)

Expected: exit 1. `FAIL` lines for `lib/<abi>/libtensorflowlite_jni.so` and `lib/<abi>/libtensorflowlite_flex_jni.so` (p_align 0x1000); `OK` for every AndroidX lib. This is the measured baseline the migration must flip.

**If anything OTHER than the two TFLite libs fails: STOP — the spec's diagnosis no longer holds; re-open the spec before proceeding.**

- [ ] **Step 4: Commit**

```bash
git add tools/check_16kb_alignment.py
git commit -m "tools: 16 KB ELF alignment gate for AAB/APK (i2a)"
```

---

### Task 2: LiteRT core migration (fixes the photo `.so`)

**Files:**
- Modify: `shared/ml/build.gradle.kts:53-58`
- Modify: `androidApp/build.gradle.kts:25-27`

- [ ] **Step 1: Swap the core dep in `shared/ml/build.gradle.kts`**

Replace lines 53–58 (the three `org.tensorflow` implementation lines and the BirdNET comment):

```kotlin
            // LiteRT (f.d. TFLite) — 16 KB-alignat core, samma org.tensorflow.lite.*-klasser
            // som tensorflow-lite 2.16.1 (ren koordinat-swap, i2a 2026-07-16).
            implementation("com.google.ai.edge.litert:litert:1.4.1")
            // BirdNET-Lite uses FlexRFFT (TF Select op) for on-graph spectrogram
            // computation. Without this dep the audio model fails to prepare
            // node 29 with "Select TensorFlow op(s) not supported".
            // Only the Java FlexDelegate + auto-registration are used from this artifact —
            // its 4 KB native .so is replaced by a 16 KB build in androidApp
            // (see downloadFlex16kJniLibs). The excludes stop it from dragging the legacy
            // (4 KB) core + duplicate org.tensorflow.lite API classes onto the classpath.
            implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1") {
                exclude(group = "org.tensorflow", module = "tensorflow-lite")
                exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
            }
```

Note: `org.tensorflow:tensorflow-lite-support:0.4.4` is deliberately **gone** (zero imports in the codebase, ships no `.so` here; grep-verified in the spec).

- [ ] **Step 2: Swap the core dep in `androidApp/build.gradle.kts`**

Replace lines 25–27 (comment + dep):

```kotlin
            // LiteRT (f.d. TFLite) needed so Kotlin compiler can resolve Interpreter.Options
            // when calling AndroidTfliteRunner(modelBytes, info) with default options param
            // in buildClassifier. LiteRT = 16 KB-aligned core, same org.tensorflow.lite.* API.
            implementation("com.google.ai.edge.litert:litert:1.4.1")
```

- [ ] **Step 3: Run the full repo gate**

Run: `./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt`
Expected: BUILD SUCCESSFUL, zero source changes needed.

Contingency (only if it fails): a `Duplicate class org.tensorflow.lite...` error means a transitive path still pulls the legacy API — run `./gradlew :androidApp:dependencies --configuration debugRuntimeClasspath | grep tensorflow` and add the same two `exclude(...)` lines to whichever remaining `org.tensorflow` dependency drags them in. A missing-class error in `FlexDelegate` means litert 1.4.1 did not provide an API class the flex artifact needs — remove ONLY the `tensorflow-lite-api` exclude and rebuild.

- [ ] **Step 4: Re-run alignment — photo lib must now be green**

Run: `./gradlew :androidApp:assembleDebug` then
`python tools/check_16kb_alignment.py androidApp/build/outputs/apk/debug/androidApp-debug.apk`

Expected: exit 1 still (flex remains 4 KB), **but** `libtensorflowlite_jni.so` now reports `OK` with `p_align=0x4000`. If it still FAILs, litert 1.4.1 is not what the spec assumed — STOP and re-open the spec.

- [ ] **Step 5: Commit**

```bash
git add shared/ml/build.gradle.kts androidApp/build.gradle.kts
git commit -m "build: migrate TFLite core -> LiteRT 1.4.1 (16 KB photo .so, i2a)"
```

---

### Task 3: Buffer-retention fixes + silent-failure instrumentation

**Files:**
- Modify: `shared/ml/src/androidMain/kotlin/se/birdy/ml/AndroidTfliteRunner.kt:42-51`
- Modify: `shared/ml/src/androidMain/kotlin/se/birdy/ml/AndroidTfliteAudioRunner.kt` (constructor + `load`)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeViewModel.kt:32-51`

Background for the engineer: `Interpreter(buffer, options)` does **not** copy the model buffer — the native interpreter points straight into the direct/mapped buffer's memory and requires it to stay alive as long as the interpreter. Today both runners let the buffer become unreachable after construction; under allocation pressure (first run after clean install) GC can free/unmap it → dangling pointer → "The classifier failed." detekt's `UnusedPrivateProperty` is active, so each retained buffer must be genuinely read (no `@Suppress`).

- [ ] **Step 1: Fix `AndroidTfliteRunner`**

Replace the `interpreter` property (lines 42–51):

```kotlin
    // Retained on purpose (do NOT inline into the interpreter initializer): the Interpreter
    // points into this direct buffer's native memory and does not copy it. Dropping the
    // reference lets GC free the model mid-lifetime -> dangling pointer -> "classifier
    // failed" on first run after clean install (fixed 2026-07-16, i2a).
    private val modelBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(modelBytes.size).apply {
            order(ByteOrder.nativeOrder())
            put(modelBytes)
            rewind()
        }

    private val interpreter: Interpreter = Interpreter(modelBuffer, options)
```

(`modelBuffer` is read by the `interpreter` initializer, so `UnusedPrivateProperty` is satisfied.)

- [ ] **Step 2: Fix `AndroidTfliteAudioRunner`**

2a. Add a `modelBuffer` constructor parameter (after `interpreter`):

```kotlin
class AndroidTfliteAudioRunner private constructor(
    private val interpreter: Interpreter,
    // Retained on purpose: the Interpreter points into this mapped buffer's native memory
    // and does not copy it. Dropping the reference lets GC unmap the model mid-lifetime ->
    // dangling pointer (same defect class as the photo first-run bug, fixed 2026-07-16, i2a).
    private val modelBuffer: MappedByteBuffer,
    private val mapper: BirdNetLabelMapper,
    override val info: AudioModelInfo,
    /** Number of float samples the model expects per call — read from inputShape at load time. */
    private val expectedSamples: Int,
) : BirdAudioClassifier {
```

2b. Add an `init` guard directly above `private val mutex = Mutex()` (a real read of the property — keeps detekt happy AND catches an empty mapping at construction instead of at first inference):

```kotlin
    init {
        require(modelBuffer.capacity() > 0) { "Empty model buffer — model file failed to map" }
    }
```

2c. In `load(...)`, update the return to pass the mapped buffer through:

```kotlin
                return AndroidTfliteAudioRunner(interpreter, model, mapper, info, expectedSamples)
```

- [ ] **Step 3: Instrument the silent failures in `PhotoAnalyzeViewModel`**

detekt forbids `printStackTrace()`; `println` is allowed (`ForbiddenMethodCall` inactive) and lands in logcat under `System.out`. Modify the two `onFailure` blocks inside `analyze(...)`:

Classify site (currently `.onFailure { if (it is CancellationException) throw it }` before the `ClassifierFailure` state):

```kotlin
                runCatching { classifier.classify(frame) }
                    .onFailure {
                        if (it is CancellationException) throw it
                        // The UI only shows a generic ClassifierFailure; without this we are
                        // blind to the real exception in the field (first-run bug, i2a).
                        println("PhotoAnalyzeViewModel: classify failed:\n${it.stackTraceToString()}")
                    }
                    .getOrElse {
```

Persist site (same pattern, before the `IoFailure` state):

```kotlin
                runCatching { persist(frame.bytes) }
                    .onFailure {
                        if (it is CancellationException) throw it
                        println("PhotoAnalyzeViewModel: persist failed:\n${it.stackTraceToString()}")
                    }
                    .getOrElse {
```

The rest of each `getOrElse` block is unchanged.

- [ ] **Step 4: Run the full repo gate (includes the existing failure-path tests)**

Run: `./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt`
Expected: BUILD SUCCESSFUL. `PhotoAnalyzeViewModelTest` (commonTest) already asserts the `ClassifierFailure` state mapping and must stay green — the instrumentation only adds logging, no behavior change.

- [ ] **Step 5: Commit**

```bash
git add shared/ml/src/androidMain/kotlin/se/birdy/ml/AndroidTfliteRunner.kt \
        shared/ml/src/androidMain/kotlin/se/birdy/ml/AndroidTfliteAudioRunner.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeViewModel.kt
git commit -m "fix: retain TFLite model buffers as fields + log real classify/persist errors (i2a)"
```

---

### Task 4: Flex 16 KB swap (fixes the audio `.so`)

**Files:**
- Modify: `androidApp/build.gradle.kts` (defaultConfig, packaging, sourceSets, new download task)

The three SHA-256 values below were computed by the planner on 2026-07-16 from freshly downloaded release assets, which were then ELF-parsed: all PT_LOAD `p_align = 0x4000`. They are trust anchors — never "update them to whatever the download gives".

- [ ] **Step 1: Add abiFilters to `defaultConfig`**

In `androidApp/build.gradle.kts`, inside `defaultConfig { ... }` (after the `PREMIUM_OPEN_FOR_LAUNCH` buildConfigField):

```kotlin
        // x86 (32-bit) excluded: no 16 KB flex build exists for it and we never ship a
        // split with a missing or 4 KB flex lib (i2a spec §2). Real devices are arm64/v7a;
        // x86_64 covers emulators.
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
```

- [ ] **Step 2: Add the packaging rule**

In the `android { ... }` block (sibling of `buildTypes`, e.g. right after it):

```kotlin
    packaging {
        jniLibs {
            // Prefer our 16 KB copy (project jniLibs, from downloadFlex16kJniLibs) over
            // the select-tf-ops AAR's 4 KB copy. Verified by tools/check_16kb_alignment.py.
            pickFirsts += "**/libtensorflowlite_flex_jni.so"
        }
    }
```

- [ ] **Step 3: Wire the generated jniLibs dir into the main source set**

In the existing `sourceSets["main"].apply { ... }` block, add one line after `res.srcDirs("src/main/res")`:

```kotlin
        jniLibs.srcDir(flexJniLibsDir)
```

- [ ] **Step 4: Add the pinned download task**

At the bottom of `androidApp/build.gradle.kts` (top level, before the `assetPack...PreBundleTask` block). Note: `flexJniLibsDir` must be declared **above** the `android { }` block since Step 3 references it — put the whole `val` trio there, and the functions/task at the bottom:

Directly above `android {`:

```kotlin
// ---- 16 KB Flex override (BirdNET select-tf-ops) — i2a 2026-07-16 -----------------
// No official 16 KB select-tf-ops exists: TFLite 2.16.1 (4 KB .so) was the final
// org.tensorflow release and LiteRT publishes no select-tf-ops artifact. The Maven AAR's
// libtensorflowlite_flex_jni.so is replaced at build time with a 16 KB-aligned build from
// github.com/arxdeus/tflite_flex_16kb_android (Apache-2.0 TF build, not Google-signed),
// pinned by release tag + SHA-256 (computed + ELF-verified by us 2026-07-16). Provenance,
// decision + fallbacks: docs/superpowers/specs/2026-07-16-i2a-android-litert-16kb-design.md
val flexReleaseTag = "tf-a95156b81d38"
val flexSha256ByAbi =
    mapOf(
        "arm64-v8a" to "bb63006c3cc8b924c1c83749be39ece51a5dbb2ec1e57a4545f6df1e94e38692",
        "armeabi-v7a" to "de7c9c41d1207a4d24e7148438e48a56eda22c91d35f4fd71e60dd97e0189109",
        "x86_64" to "b066a3a9671d06c6d7d54507c5f6f00f167a08b537cafdb3e62fb23c39235446",
    )
val flexJniLibsDir = layout.buildDirectory.dir("flex16k/jniLibs")
```

At the bottom of the file:

```kotlin
fun sha256Of(file: java.io.File): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val downloadFlex16kJniLibs by tasks.registering {
    description = "Downloads the 16 KB libtensorflowlite_flex_jni.so per ABI (SHA-256-pinned)."
    inputs.property("releaseTag", flexReleaseTag)
    inputs.property("checksums", flexSha256ByAbi.toString())
    outputs.dir(flexJniLibsDir)
    doLast {
        flexSha256ByAbi.forEach { (abi, expectedSha) ->
            val target = flexJniLibsDir.get().dir(abi).file("libtensorflowlite_flex_jni.so").asFile
            if (target.exists() && sha256Of(target) == expectedSha) return@forEach
            target.parentFile.mkdirs()
            val url =
                "https://github.com/arxdeus/tflite_flex_16kb_android/releases/download/" +
                    "$flexReleaseTag/libtensorflowlite_flex_jni.so-$abi"
            logger.lifecycle("Downloading 16 KB flex lib for $abi (~100 MB)...")
            java.net.URI(url).toURL().openStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            val actualSha = sha256Of(target)
            if (actualSha != expectedSha) {
                target.delete()
                error("SHA-256 mismatch for $abi: expected $expectedSha, got $actualSha")
            }
        }
    }
}

tasks.matching { it.name.endsWith("JniLibFolders") }.configureEach {
    dependsOn(downloadFlex16kJniLibs)
}
```

- [ ] **Step 5: Build + alignment — everything green now**

Run: `./gradlew :androidApp:assembleDebug` (first run downloads ~340 MB; subsequent runs hit the checksummed cache) then
`python tools/check_16kb_alignment.py androidApp/build/outputs/apk/debug/androidApp-debug.apk`

Expected: exit 0 — every `.so` `OK`, including `libtensorflowlite_flex_jni.so` at `p_align=0x4000` (proves our copy won pickFirsts), and no `lib/x86/` entries at all.

- [ ] **Step 6: Run the full repo gate**

Run: `./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt`
Expected: BUILD SUCCESSFUL (ktlint also checks `.kts` — the snippets above are pre-formatted for it).

- [ ] **Step 7: Commit**

```bash
git add androidApp/build.gradle.kts
git commit -m "build: 16 KB flex .so override (SHA-256-pinned) + abiFilters, audio .so fixed (i2a)"
```

---

### Task 5: vC126 bump + release AAB + full 16 KB verify

**Files:**
- Modify: `androidApp/build.gradle.kts:60-61` (in `defaultConfig`)

- [ ] **Step 1: Bump version**

```kotlin
        versionCode = 126
        versionName = "1.2.1"
```

- [ ] **Step 2: Build the release AAB**

Run: `./gradlew :androidApp:bundleRelease`
Expected: BUILD SUCCESSFUL. (Keystore comes from `BIRDY_KEYSTORE_*` gradle properties; the real `MAPTILER_API_KEY` is already in the local `gradle.properties`.)

- [ ] **Step 3: ELF gate on the AAB**

Run: `python tools/check_16kb_alignment.py androidApp/build/outputs/bundle/release/androidApp-release.aab`
Expected: exit 0, all `.so` `OK`.

- [ ] **Step 4: zipalign check on the universal APK**

```bash
java -jar tools/bundletool.jar build-apks \
  --bundle=androidApp/build/outputs/bundle/release/androidApp-release.aab \
  --output=build/i2a-verify/universal.apks --mode=universal
unzip -o build/i2a-verify/universal.apks universal.apk -d build/i2a-verify/
"$LOCALAPPDATA/Android/Sdk/build-tools/37.0.0/zipalign" -c -P 16 4 build/i2a-verify/universal.apk
```

Expected: zipalign exits 0 ("Verification succes..." output). Do not commit `build/i2a-verify/`.

- [ ] **Step 5: Full repo gate one last time**

Run: `./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add androidApp/build.gradle.kts
git commit -m "chore: bump to vC126 / 1.2.1 — 16 KB-ready release build verified (i2a)"
```

---

### Task 6: CLAUDE.md sync + push + device-verify hand-off

**Files:**
- Modify: `CLAUDE.md` (Status section + Plan-of-plans v2 table row i2a)

- [ ] **Step 1: Update CLAUDE.md**

Add a new top bullet under `## Status (2026-07-13)` (and bump the heading date to 2026-07-16), adapted to the actual outcome of Tasks 1–5:

```markdown
- **i2a Android LiteRT + 16 KB + classifier-fix — KOD KLAR + AAB-verifierad, device-verify kvar (2026-07-16, Windows):** Core: `org.tensorflow:tensorflow-lite:2.16.1` → `com.google.ai.edge.litert:litert:1.4.1` (noll källkodsändringar; `tensorflow-lite-support` borttagen, 0 imports). Audio: select-tf-ops kvar (FlexDelegate-Java + auto-registrering) men dess 4 KB-`.so` ersätts vid bygge av 16 KB-bygget från `arxdeus/tflite_flex_16kb_android` tag `tf-a95156b81d38` (SHA-256-pinnad i `androidApp/build.gradle.kts`, ELF-verifierad p_align=0x4000 alla tre ABI:er; `downloadFlex16kJniLibs` + `pickFirsts`; `abiFilters` = arm64-v8a/armeabi-v7a/x86_64, x86 medvetet borta). Classifier-first-run-buggen: modellbuffert behålls nu som fält i BÅDA runners (`AndroidTfliteRunner`, `AndroidTfliteAudioRunner` — TFLite kopierar aldrig bufferten) + `PhotoAnalyzeViewModel`:s tysta getOrElse loggar riktiga undantaget (println → logcat System.out). Nytt gate-verktyg `tools/check_16kb_alignment.py` (PT_LOAD p_align ≥ 0x4000 på alla .so i AAB/APK): vC125-baseline röd på exakt de två TFLite-libbarna → nu helgrön; vC126-AAB (versionCode 126 / 1.2.1) byggd + `zipalign -c -P 16` grön. **KVAR (Albin, Galaxy S23 Ultra):** rensa debug-appens data → ren install → (1) foto-ID ska funka FÖRSTA försöket (bugg-verify), (2) audio-ID ger resultat (FlexRFFT node-29), (3) live-scan-svep — därefter Play-upload/promote av vC126 (manuellt). Spec: `docs/superpowers/specs/2026-07-16-i2a-android-litert-16kb-design.md`, plan: `docs/superpowers/plans/2026-07-16-i2a-android-litert-16kb.md`.
```

Update the Plan-of-plans (v2 iOS) table row for i2a from `⬜ Windows-maskinen (Galaxy-verify); oberoende av iOS` to:

```markdown
| i2a | Android TFLite→LiteRT (16 KB-fix) | `litert:1.4.1` (foto-`.so` ✅), 16 KB flex-swap (audio-`.so` ✅, SHA-256-pinnad), classifier-first-run-fix, vC126-AAB byggd + alignment-grön | 🔄 kod klar (2026-07-16); kvar: Albin device-verify (foto första-försöket + audio + live-scan) → Play-upload |
```

Also update the `(4) **Classifier-failure-bugg...**` item in the LIVE-production bullet: change `(måste fixas före nästa release, vC126)` to `(FIXAD i i2a 2026-07-16 — buffert-retention + instrumentering; device-verify kvar)` and leave the diagnosis text as history.

- [ ] **Step 2: Commit + push (NOT gradle.properties)**

```bash
git add CLAUDE.md docs/superpowers/plans/2026-07-16-i2a-android-litert-16kb.md
git commit -m "docs: sync CLAUDE.md — i2a code-complete, device-verify + Play-upload remain"
git pull --ff-only
git push
```

If `pull --ff-only` fails (Mac pushed i2b work): `git pull --rebase` instead, resolve any CLAUDE.md status-bullet collision by keeping BOTH bullets (they describe different tracks), then push.

- [ ] **Step 3: Report the hand-off**

Tell Albin exactly what remains (his hands, one pass): clean-install device-verify per the checklist in the CLAUDE.md bullet, then Play Console upload of `androidApp/build/outputs/bundle/release/androidApp-release.aab` + What's new + promote. NOT part of this plan.

---

## Fallbacks (from the spec — do not improvise)

- **Audio fails node-29 on device:** try the older release tags of `arxdeus/tflite_flex_16kb_android` (repin SHA-256 after verifying ELF alignment with `tools/check_16kb_alignment.py` logic); if none works, plan B = self-build the flex AAR (Bazel via WSL/CI). **Never ship without green audio device-verify.**
- **LiteRT 1.4.1 photo regression:** caught by the repo gate + device verify; fallback = a different LiteRT 1.x on the Interpreter path. LiteRT 2.x / CompiledModel API is out of scope.
- **Flex release URL disappears:** binaries are checksummed-cached in `androidApp/build/flex16k/`; vendoring the verified binaries becomes the fallback distribution (decision to revisit only if it happens).
