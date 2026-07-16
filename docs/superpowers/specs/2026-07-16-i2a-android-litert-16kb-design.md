# i2a — Android LiteRT migration (16 KB page size) + classifier first-run fix

**Date:** 2026-07-16 · **Machine:** Windows (Galaxy S23 Ultra device-verify) · **Track:** independent of iOS (i2b/i2c) — shares only the `.tflite` model files.

## Context

Google Play requires 16 KB page-size support for apps targeting API 35+ that ship native code. vC125 shipped via a per-version skip in Play Console; the fix is mandatory before the next update (vC126) and for the app to run on 16 KB-page devices. Measured diagnosis (2026-06-17, ELF `p_align` on the vC125 AAB): **only the two TFLite `.so` are 4 KB-aligned** — `libtensorflowlite_jni.so` (org.tensorflow:tensorflow-lite:2.16.1, photo path) and `libtensorflowlite_flex_jni.so` (tensorflow-lite-select-tf-ops:2.16.1, BirdNET audio's FlexRFFT/node-29 dependency). All AndroidX `.so` are already 16 KB.

Bundled into this plan (decision 2026-07-16): the **classifier first-run bug** — photo ID shows `ClassifierFailure` the first time after a clean install; restart "fixes" it. Prime suspect: `AndroidTfliteRunner` builds its `Interpreter` from a local direct `ByteBuffer` that is not retained as a field; TFLite does not copy the model buffer, so GC can free the native memory → dangling pointer. The same TFLite-runner file is rewritten by this migration, so the fix and the migration belong together.

Ecosystem status (re-verified 2026-07-16): TFLite 2.16.1 remains the final `org.tensorflow` release; LiteRT has **no select-tf-ops artifact** (checked Maven + LiteRT docs). The only prebuilt 16 KB Flex option is the community repo `arxdeus/tflite_flex_16kb_android` (latest release tag `tf-a95156b81d38`, 2026-04-30; arm64-v8a, armeabi-v7a, x86_64).

## Goal

Android is **vC126-ready**: every `.so` in the release AAB is 16 KB-aligned (`p_align ≥ 0x4000`), photo ID and audio ID are regression-free on device, and the classifier first-run bug is fixed and instrumented. Play Console upload/promote stays a manual step (Albin).

## Decisions made during brainstorming

1. **Classifier bug: instrument + fix directly** (not repro-first). The buffer-retention fix is correct TFLite usage regardless of root cause; instrumentation stays so a different root cause would surface in logcat if the symptom recurs. Albin's device time is bundled into one final verify pass. Trade-off accepted: we never capture the "before" stacktrace.
2. **Flex strategy: community prebuilt** (not self-build, not off-graph re-architecture). Fastest route to vC126; gated by ELF verify + SHA-256 pinning + on-device FlexRFFT verify. Off-graph RFFT (which would also shed the ~68 MB flex `.so`) remains a future option and may fall out of i3's iOS-audio work naturally.

## Design

### 1. Dependency migration (fixes the photo `.so`)

- `org.tensorflow:tensorflow-lite:2.16.1` → `com.google.ai.edge.litert:litert:1.4.1` in `shared/ml/build.gradle.kts` and `androidApp/build.gradle.kts`. LiteRT 1.x keeps the `org.tensorflow.lite.*` classes (`Interpreter`, `Interpreter.Options`) — **zero source changes**, Gradle coordinates only. (LiteRT 2.x introduces the new CompiledModel API — explicitly out of scope; 1.4.1 is the drop-in line and matches the version pinned in the i2b spec.)
- `org.tensorflow:tensorflow-lite-support:0.4.4` is **removed** — zero imports of `org.tensorflow.lite.support` in the codebase (grep-verified 2026-07-16); compilation confirms.
- `org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1` is **kept** — it provides the `FlexDelegate` Java class and the auto-registration that BirdNET's node-29 (FlexRFFT) requires. Only its native `.so` is replaced (next section).

### 2. Flex 16 KB swap (fixes the audio `.so`)

- Source: pinned release tag of `arxdeus/tflite_flex_16kb_android` (start with `tf-a95156b81d38`; the exact tag + per-file SHA-256 are pinned in the Gradle task when implemented and recorded in the plan doc).
- ABIs: **arm64-v8a + x86_64** (the ABIs Play's 16 KB check covers), plus armeabi-v7a if present in the release. If the Maven AAR ships an ABI we cannot fully replace (e.g. x86), that **entire ABI is excluded from the app** (`ndk.abiFilters` limited to the replaceable set) — never a split with a missing or 4 KB flex lib.
- Delivery: a Gradle task in `androidApp` downloads the `.so` files from the pinned release URL at build time, **verifies SHA-256** against hardcoded checksums, and places them under a generated `jniLibs` directory wired into the android source set. The ~68 MB+ binaries are **not committed to git**; downloads are cached locally so offline rebuilds work after first fetch.
- `packaging { jniLibs.pickFirsts += "**/libtensorflowlite_flex_jni.so" }` resolves the duplicate in favour of our 16 KB copy over the Maven AAR's 4 KB copy.
- Provenance (source repo, tag, checksums, license: Apache-2.0 TF build, not Google-signed) is documented in the build file next to the task.

### 3. Classifier fix + instrumentation

- `AndroidTfliteRunner` (`shared/ml/src/androidMain/.../AndroidTfliteRunner.kt`): retain the model `ByteBuffer` as a `private val` field so it lives exactly as long as the `Interpreter`. This is required TFLite API usage (the interpreter points into the buffer's native memory; it does not copy).
- `AndroidTfliteAudioRunner`: **same latent defect** — the `MappedByteBuffer` from `loadModelFile` is passed to the `Interpreter` constructor but not retained; if GC collects it, the mapping is unmapped. Retain it as a field too.
- `PhotoAnalyzeViewModel` (commonMain): the two silent `getOrElse` sites (classify at ~line 35, persist at ~line 45) log the real throwable with stacktrace before mapping to UI error state — `printStackTrace()` (lands in logcat on Android via System.err; commonMain-safe; no new logging infrastructure).

### 4. Verification gates

1. **Per commit** (repo standard, Android stays shippable): `:shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug` + ktlint + detekt green.
2. **New committed tool** `tools/check-16kb-alignment` (replaces the ad-hoc June parser, so the gate is repeatable): parses PT_LOAD `p_align` for **every** `.so` in a built AAB/APK, fails if any `< 0x4000`; plus `zipalign -c -P 16` on the universal APK. Runs against the vC126 release AAB.
3. **vC126 build**: versionCode 125 → 126, versionName `1.2.0-rc3` → `1.2.1`; `:androidApp:bundleRelease` + bundletool device-smoke (`tools/bundletool.jar`).
4. **Device verify (Albin, one pass at the end, Galaxy S23 Ultra)**: clean install of the debug app (own package `se.birdy.android.debug`, wipe its data first) → photo ID succeeds **on the first attempt** (bug verify), audio ID returns results (FlexRFFT/node-29 verify), live scan regression. 16 KB-aligned libs run fine on 4 KB devices — alignment is backward-compatible.
5. Optional extra (not a gate): a 16 KB x86_64 emulator image run, if available on the machine.

### 5. Error handling / fallbacks

- **Audio fails node-29 on device** (flex/core version mismatch): try another release tag from the flex repo matching LiteRT 1.4.1's TF baseline; if none works, plan B is self-building the flex AAR (Bazel via WSL/CI). **We never ship without green audio device-verify.**
- **LiteRT 1.4.1 photo regression** (unlikely — same API, same model): caught by existing unit gates + device photo-ID verify; fallback is staying on the interpreter path with a different 1.x LiteRT version.
- **Download task offline/URL rot**: checksummed cache means only first fetch needs network; if GitHub release disappears, vendoring the verified binaries becomes the fallback distribution.

## Out of scope

- Play Console upload, What's new, promote (manual, Albin).
- Monetization flip / billing verify (own runbook, own decision — vC126 here means "16 KB + bugfix ready", not the billing release).
- LiteRT 2.x / CompiledModel API, GPU/NNAPI delegates.
- Off-graph RFFT re-architecture (future; possibly via i3).
- iOS — i2b/i2c own the iOS runtime; this plan must not touch iosMain or `iosApp/`.

## Work protocol

On `main` per the repo sync rule (Mac runs i2b in parallel; only CLAUDE.md status collides — pull before push). Every commit leaves Android shippable (gate 1). CLAUDE.md status + push at session end.
