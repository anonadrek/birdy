# Build & release-audit — 2026-05-23

## Sammanfattning
Versioning och signering är produktionsfärdigt (`1.0.0`, versionCode 112, keystore-setup OK). ProGuard/R8 och asset pack har uppdaterats sedan maj-20-auditen. **Två HIGH-level fynd kvarstår från tidigare audit:** androidx-navigation på alpha10 och isDebuggable/applicationIdSuffix saknas. PREMIUM_OPEN_FOR_LAUNCH är dokumenterat och intentionell. ~5-10 blockerare från maj-20-auditen ligger kvar (runBlocking, Java-version-mismatch, localeConfig).

## Findings

### BLOCKER

- **androidx-navigation fortfarande på alpha10** — `gradle/libs.versions.toml:18` — `-alpha10` är osupportad i produktion. **Fix:** Uppdatera till `2.8.x` stable (om släppt) eller `2.7.x` LTS. **ETA:** 10 min (test efter bump).

### HIGH

- **Java version mismatch mellan moduler** — `androidApp/build.gradle.kts:104-105` (21) vs `buildSrc/.../gradle.kts` (17). **Fix:** Sätt alla till `VERSION_21`. **ETA:** 15 min + verifiering.
  
- **Google Play Billing ProGuard-keep redan tillagd** — `androidApp/proguard-rules.pro:49` har `-keep class com.android.billingclient.api.** { *; }`. ✅ **FIXAT** sedan maj-20.

- **`isDebuggable = false` inte explicit deklarerad** — `androidApp/build.gradle.kts` release-block saknar explicit `isDebuggable = false`. **Fix:** Lägg till rad 92. **ETA:** 2 min.

- **`applicationIdSuffix = ".debug"` saknas** — Debug-builds kan inte sam-existera med release på samma device. **Fix:** Lägg till i debug-blocket (rad 88-90). **ETA:** 2 min.

### MEDIUM

- **`localeConfig` saknas för Android 13+** — `androidApp/src/main/AndroidManifest.xml:22` har ingen `android:localeConfig`-referens. Play Console varnar om detta. **Fix:** Skapa `res/xml/locales_config.xml` med SV + EN. **ETA:** 10 min.

- **TFLite-modellen följer med i molnbackup** — `backup_rules.xml` behöver exkludera `.tflite`-filer. **Fix:** Verifiera exclude-regel finns. **ETA:** 5 min.

- **`PREMIUM_OPEN_FOR_LAUNCH=true` dokumentering OK** — Kommentarer i `androidApp/build.gradle.kts:62-66` + `MainActivity.kt:229-231` förklarar intentionen. ✅ Ingen fix behövs.

- **Lint-block på androidApp saknas** — Ingen `lint { abortOnError = true; checkReleaseBuilds = true }`. **Fix:** Lägg till i android-blocket. **ETA:** 5 min.

### LOW / Nice-to-have

- **TFLite-version hårdkodad** — `androidApp/build.gradle.kts:27` har "2.16.1" direkt. **Fix:** Flytta till `libs.versions.toml`. **ETA:** 10 min.

- **CI bygger bara debug-APK** — `.github/workflows/ci.yml` saknar `bundleRelease`-steg. **Fix:** Lägg till release-bundle-validering. **ETA:** 20 min.

## Vad har fixats sedan 2026-05-20-auditen

- ✅ versionName = "1.0.0" (från RC2)
- ✅ Google Play Billing keep-regler tillagda (proguard-rules.pro:49)
- ✅ PREMIUM_OPEN_FOR_LAUNCH dokumenterat + commented
- ✅ Asset pack för WebP-migration (APK: 300 MB → 136 MB)
- ✅ Premium override-bugfix (ArchiveViewModel + AppGraph.effectivePremiumActive)

**Kvarvarande från maj-20:** runBlocking×3, Java-version-mismatch, localeConfig, TFLite backup-exclude, error-handling i ViewModels/PhotoAnalyzeHost.
