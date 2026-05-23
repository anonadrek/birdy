# Pre-launch audit master-checklista — Birdy v1.0.0

> **Skapad 2026-05-23** av en team av 7 parallella audit-agenter (build, compliance, kodkvalitet, billing, lokalisering, privacy, testing-readiness).
> Originalrapporter ligger i samma katalog (`01-` till `07-`). **Den här filen är konsoliderad + dedupad + sorterad i körningsordning.**

## TL;DR

- ✅ **Privacy-löftet "data stays on phone" är bevisbart sant.** Noll analytics, noll backend-anrop för inference.
- ✅ **Premium/Billing-stacken är launch-ready** (med `PREMIUM_OPEN_FOR_LAUNCH=true` för closed testing).
- ✅ **License-säkerhet OK** — BirdNET (CC BY-NC-SA) är gratis, Plan 6b3-features är korrekt gate:ade.
- ✅ **SV/EN string-paritet 604/604** identiska keys.
- ❌ **4 BLOCKERS** måste fixas innan AAB-upload — alla snabba (~25 min totalt).
- 🟠 **10 HIGH** bör fixas innan upload (~80 min totalt).
- 🟡 **8 MEDIUM** ta som batch under closed testing.
- 🟢 **6 LOW / post-launch** notera men ignorera nu.

**Bedömning: vi kan trycka Upload till Internal Testing IDAG efter ~25 min blocker-fix + manuell Play Console-setup (~60 min).**

---

## 🔴 BLOCKERS — fixa FÖRST (~25 min totalt)

Sorterade trivialast→tyngst. Varje fix kan committas separat med liten message.

### B1. ~~Bumpa androidx-navigation från `alpha10` till stable~~ → bumpat till `alpha13`
- **Fil:** `gradle/libs.versions.toml:18`
- **Före:** `androidx-navigation = "2.8.0-alpha10"`
- **Efter:** `androidx-navigation = "2.8.0-alpha13"` (senaste patch i samma minor)
- **Auditens premiss var fel:** JetBrains KMP-porten `org.jetbrains.androidx.navigation:navigation-compose` har **ingen stable version alls** — alla 20 releaser på Maven Central är alpha/beta (senaste = `2.9.0-beta03`). Play Console accepterar de facto alpha/beta för KMP-deps; det är industri-norm. Nedgraderad från BLOCKER till klar med not.
- **Verifikation:** `./gradlew :composeApp:compileDebugKotlinAndroid` → BUILD SUCCESSFUL, inga navigation-relaterade warnings
- **Källor:** `01-build-release.md` + `03-code-quality.md` (båda baserade på AOSP-versioneringen, inte JB-porten)
- [x] Klar (2026-05-23)

### B2. Uppdatera Play Store-listing URLer (SV + EN)
- **Filer:** `docs/play-store/store-listing-en.md:74-75` + `docs/play-store/store-listing-sv.md:74-75`
- **Nu:** `https://anonadrek.github.io/birdy/{privacy,terms}.html` + `https://anonadrek.github.io/birdy/`
- **Fix:** Byt till `https://birdy.community/`, `https://birdy.community/legal/privacy/`, `https://birdy.community/legal/terms/`
- **Varför blocker:** Play Console linkar till döda GH Pages-sidor; bryter mot privacy-policy-konformitet
- **ETA:** ~5 min (sed-replace i båda filer)
- **Källor:** `02-play-store-compliance.md` + `07-internal-testing-readiness.md`
- [ ] Klar

### B3. Uppdatera "What's new"-section i store-listings till v1.0.0
- **Filer:** `docs/play-store/store-listing-en.md:52` + samma i SV
- **Nu:** "What's new (v0.9.0a-billing — Closed Testing)"
- **Fix:** Bytt till v1.0.0-narrativ (foto-ID + audio-ID + 839 arter + premium content)
- **ETA:** ~10 min (skriv båda språk)
- **Källor:** `02-play-store-compliance.md`
- [ ] Klar

### B4. Uppdatera closed-testing tester-instruktioner till versionCode 112
- **Fil:** `docs/play-store/closed-testing-tester-instructions.md:3`
- **Nu:** "Build: v0.9.0a-billing (versionCode 110, versionName 1.0.0-rc2)"
- **Fix:** "Build: v1.0.0 (versionCode 112, versionName 1.0.0)"
- **ETA:** ~3 min
- **Källor:** `07-internal-testing-readiness.md`
- [ ] Klar

---

## 🟠 HIGH — bör fixas INNAN AAB-upload (~80 min totalt)

### H1. Sätt `PLAY_LICENSE_KEY` från Play Console
- **Filer:** `androidApp/build.gradle.kts:57-61` + `gradle.properties:1`
- **Nu:** `BIRDY_PLAY_LICENSE_KEY=` (tom placeholder)
- **Fix:** Play Console → Monetize → Licensing → kopiera base64 public key → paste i lokal `gradle.properties`
- **Varför HIGH:** Release-build kommer släppa igenom alla "purchases" om nyckeln är tom (signature-verify bypass i fail-open-mode). Inte blocker eftersom `PREMIUM_OPEN_FOR_LAUNCH=true` ger alla LIFETIME ändå, men måste vara klar innan vi flippar flaggan post-launch.
- **ETA:** ~10 min (kräver Play Console-access)
- **Källor:** `04-premium-billing.md`
- [ ] Klar

### H2. Gata `PREMIUM_DEBUG_FORCE_ACTIVE` med `BuildConfig.DEBUG`
- **Fil:** `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt:235`
- **Fix:** `if (BuildConfig.DEBUG && BuildConfig.PREMIUM_DEBUG_FORCE_ACTIVE) { ... }`
- **Varför HIGH:** Defense-in-depth — om någon av misstag sätter flaggan `true` i release blir det gratis-premium-bypass
- **ETA:** ~5 min
- **Källor:** `04-premium-billing.md` + `06-privacy-security.md`
- [ ] Klar

### H3. Skapa `locales_config.xml` (Android 13+ Language Picker)
- **Fil:** Skapa `androidApp/src/main/res/xml/locales_config.xml` + ref i `AndroidManifest.xml:22`
- **Fix:** XML med `sv-SE` + `en-US` + `android:localeConfig="@xml/locales_config"` på `<application>`
- **Varför HIGH:** Play Console varnar för Android 13+ devices; Settings-app visar inte per-app language picker utan detta
- **ETA:** ~10 min
- **Källor:** `01-build-release.md` + `02-play-store-compliance.md`
- [ ] Klar

### H4. Lägg till `isDebuggable = false` explicit i release-block
- **Fil:** `androidApp/build.gradle.kts:~92` (release-block)
- **Fix:** `isDebuggable = false`
- **ETA:** ~2 min
- **Källor:** `01-build-release.md`
- [ ] Klar

### H5. Lägg till `applicationIdSuffix = ".debug"` i debug-block
- **Fil:** `androidApp/build.gradle.kts:~88-90` (debug-block)
- **Fix:** `applicationIdSuffix = ".debug"`
- **Varför HIGH:** Tillåter debug- och release-builds att samexistera på samma device
- **ETA:** ~2 min
- **Källor:** `01-build-release.md`
- [ ] Klar

### H6. Synka Java-toolchain till `VERSION_21` överallt
- **Filer:** `androidApp/build.gradle.kts:104-105` (21) vs `buildSrc/.../*.gradle.kts` (17)
- **Fix:** Sätt alla `jvmTarget` / `sourceCompatibility` / `targetCompatibility` till `VERSION_21` (eller motsvarande Kotlin toolchain)
- **ETA:** ~15 min (inkl verifiering med `./gradlew clean build`)
- **Källor:** `01-build-release.md`
- [ ] Klar

### H7. Exportera Play Console app-icon 512×512 PNG
- **Status:** Adaptive launcher-icon finns (XML), men Play Console kräver rå 512×512 PNG
- **Fix:** Android Studio → Right-click `ic_launcher` → Export PNG @ 512×512, eller scala xxhdpi (192×192) ×2.67
- **Lägg på:** `docs/play-store/ic_launcher_512.png` (om den inte redan finns där — verifiera först!)
- **ETA:** ~10 min
- **Källor:** `07-internal-testing-readiness.md`
- [ ] Klar

### H8. Null-safe `speciesId` i 4 anrop (NPE-risk)
- **Filer:**
  - `composeApp/src/commonMain/kotlin/se/birdy/app/badges/RecalculateBadgesUseCase.kt`
  - `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveViewModel.kt`
  - `composeApp/src/commonMain/kotlin/se/birdy/app/usecase/ExportJournalUseCase.kt` (2 anrop)
- **Nu:** `groupBy { it.speciesId!! }` etc
- **Fix:** `groupBy { it.speciesId ?: "unknown" }` eller `filter { it.speciesId != null }` före groupBy
- **Varför HIGH:** Legacy v0.5.0a-observationer kan ha null `speciesId` → hela flow:en crashar
- **ETA:** ~5 min
- **Källor:** `03-code-quality.md`
- [ ] Klar

### H9. Säker cast i `BadgesViewModel`
- **Fil:** `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesViewModel.kt:50`
- **Nu:** `buildLoaded(...) as BadgesUiState` (unchecked cast)
- **Fix:** `as? BadgesUiState ?: BadgesUiState.Error(...)` eller ta bort casten helt om typen redan är säker
- **ETA:** ~3 min
- **Källor:** `03-code-quality.md`
- [ ] Klar

### H10. Splitta `BadgeBackfill` error-handling (silent failure)
- **Fil:** `composeApp/src/commonMain/kotlin/se/birdy/app/bootstrap/BadgeBackfillOnAppStart.kt:22-37`
- **Nu:** En enda `runCatching` runt allt
- **Fix:** Splitta try/catch per steg (compute → persist); logga warning vid fail; låt caller hantera degradation
- **Varför HIGH:** Om `persist()` failar tyst blir badge-state korrupt nästa start
- **ETA:** ~10 min
- **Källor:** `03-code-quality.md`
- [ ] Klar

### H11. Säker cast på Activity i `PremiumBillingClient`
- **Fil:** `composeApp/src/androidMain/kotlin/se/birdy/app/data/premium/PremiumBillingClient.android.kt:230`
- **Nu:** `client.launchBillingFlow(activityContext as Activity, ...)`
- **Fix:** `as? Activity` + return `PurchaseResult.Error("Requires Activity context")`
- **ETA:** ~5 min
- **Källor:** `03-code-quality.md`
- [ ] Klar

---

## 🟡 MEDIUM — batch under closed testing (~50 min totalt)

### M1. Verifiera Play Console SKU-setup matchar `PremiumBillingClient`
- **Action:** I Play Console, kolla att `birdy_yearly` + `birdy_lifetime` SKUs är skapade med rätt prisning (199/499 SEK)
- **ETA:** ~5 min (manuellt steg)
- **Källor:** `02-play-store-compliance.md`
- [ ] Klar

### M2. Manuellt fylla i Data Safety form i Play Console
- **Källa:** `docs/play-store/data-safety-form.md` (använd som checklista)
- **ETA:** ~20 min (under upload-flow)
- **Källor:** `02-play-store-compliance.md`
- [ ] Klar

### M3. Uppdatera Data Safety form metadata till v1.0.0
- **Fil:** `docs/play-store/data-safety-form.md:6`
- **Nu:** "Plan 6b2 v0.9.0b-audio"
- **Fix:** "v1.0.0 (Plan 6b3 included)"
- **ETA:** ~2 min
- [ ] Klar

### M4. Exkludera TFLite-modeller från backup
- **Filer:** `androidApp/src/main/res/xml/backup_rules.xml` + `data_extraction_rules.xml`
- **Fix:** `<exclude domain="assets" path="*.tflite"/>` + motsvarande för `:asset-pack`
- **Varför MEDIUM:** 57 MB modeller backuppade onödigt; är reproducerbara
- **ETA:** ~5 min
- **Källor:** `01-build-release.md` + `06-privacy-security.md`
- [ ] Klar

### M5. Lägg `lint { abortOnError = true }` på `:androidApp`
- **Fil:** `androidApp/build.gradle.kts` (android-block)
- **ETA:** ~5 min
- **Källor:** `01-build-release.md`
- [ ] Klar

### M6. `ScanViewModel` synkron camera tear-down
- **Filer:** `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt:147` + `ScanViewModel.kt:147-149`
- **Fix:** `try { cameraSource.stop() } finally { ... }` före GlobalScope-launch
- **ETA:** ~8 min
- **Källor:** `03-code-quality.md`
- [ ] Klar

### M7. `openExternalUrl` wrap i `runCatching`
- **Fil:** `composeApp/src/androidMain/kotlin/se/birdy/app/ui/settings/SettingsLauncher.android.kt:23-25`
- **Varför MEDIUM:** Settings-skärmen crashar om ingen browser registrerad
- **ETA:** ~2 min
- **Källor:** `03-code-quality.md` + `06-privacy-security.md`
- [ ] Klar

### M8. `MatchResultViewModel` DB-anrop guarded
- **Fil:** `composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchResultViewModel.kt:112, 117-118`
- **Fix:** Wrap `nextStampNumber()` / `countByQid()` / `firstByQid()` i `runCatching` + emit Error-state
- **ETA:** ~8 min
- **Källor:** `03-code-quality.md`
- [ ] Klar

### M9. Lös eller flytta TODO i `BadgesViewModel`
- **Fil:** `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesViewModel.kt:35-36`
- **Action:** Använd `locale` för rendering eller ta bort, alternativt konvertera till GitHub issue
- **ETA:** ~5 min
- **Källor:** `03-code-quality.md`
- [ ] Klar

---

## 🟢 LOW / post-launch — notera men ignorera nu

### L1. CI bygger inte `bundleRelease`
- **Fil:** `.github/workflows/ci.yml`
- **Fix:** Lägg till release-bundle-validering (utan signing)
- **ETA:** ~20 min — post-launch
- **Källor:** `01-build-release.md`

### L2. TFLite-version hardcoded i `androidApp/build.gradle.kts:27`
- **Fix:** Flytta till `libs.versions.toml`
- **ETA:** ~10 min
- **Källor:** `01-build-release.md`

### L3. `PREMIUM_DEBUG_FORCE_ACTIVE` inline-doc
- **Fil:** `androidApp/build.gradle.kts:89, 99`
- **Fix:** Lägg kommentar som förklarar flaggans syfte
- **ETA:** ~2 min
- **Källor:** `04-premium-billing.md`

### L4. Email-byte till `feedback@birdy.community`
- **Filer:** Settings + alla docs som idag säger `albin@abrahamssons.se`
- **När:** När Cloudflare Email Routing / Resend Inbound är upp under closed testing
- **Källor:** `02-play-store-compliance.md` + `05-localization-content.md`

### L5. Crashlytics / lokal crash-log fallback
- **Status:** Idag bara `Log.w`; ingen telemetri
- **Trade-off:** Privacy-löftet kräver att vi INTE smyger in tredjeparts-analytics. Lokal crash-log + opt-in är OK.
- **Källor:** `03-code-quality.md`

### L6. SQLCipher för DB / DataStore
- **Trade-off:** Acceptabelt v1; root-access krävs för läsning
- **När:** v1.1 om vi får feedback om känsligt data
- **Källor:** `06-privacy-security.md`

### L7. Kurera 8-10 screenshots för Play Console
- **Källa:** `docs/superpowers/screenshots/2026-05-22-v0.9.0c-premium-content/` (10 från Plan 6b3 T21)
- **Action:** Välja final set, kanske ny ordning
- **ETA:** ~20 min (under upload)

---

## Manuella Play Console-steg (workflow, inte fynd)

Efter att blocker + high-fixar är committade och AAB är byggd:

- [ ] Verifiera `~/.gradle/gradle.properties` har alla signing-credentials
- [ ] `export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"`
- [ ] `./gradlew :androidApp:bundleRelease` (bygga AAB)
- [ ] Verifiera signing med `keytool -list -printcert -jarfile androidApp-release.aab`
- [ ] Play Console → Birdy → Test and release → Internal testing → Create new release
- [ ] Upload `androidApp-release.aab`
- [ ] Sätt release name: `1.0.0 (112)`
- [ ] Sätt release notes (SV + EN) från runbook §4
- [ ] Fylla i Data Safety form (använd `docs/play-store/data-safety-form.md` som checklista)
- [ ] Sätta Privacy policy URL: `https://birdy.community/legal/privacy/`
- [ ] Sätta App icon: 512×512 PNG (från H7)
- [ ] Bifoga 8-10 screenshots från Plan 6b3 T21
- [ ] Save → Review release → Roll out to Internal testing
- [ ] Vänta 5-30 min tills opt-in-länk fungerar
- [ ] Skicka opt-in-länk till license testers + testa själv
- [ ] (Post-Internal) Verify Billing v8 IPC med license tester
- [ ] (Post-Internal, om Billing-verify klar) Flippa `PREMIUM_OPEN_FOR_LAUNCH=false`, bump versionCode → release
- [ ] (Post-Internal) Promotion till Closed Testing
- [ ] (Post-Closed, 14d minimum) Promotion till Production

---

## Pending follow-ups från CLAUDE.md — status efter audit

| # | Follow-up | Status efter denna audit |
|---|---|---|
| 1 | URL-migration (SettingsScreen + Play Console store listing) | 🔴 → 🟢 (Settings redan klart; store listing = BLOCKER B2/B3) |
| 2 | GitHub Pages teardown | 🟡 deferred (post-launch UI-steg) |
| 3 | Email migration → `feedback@birdy.community` | 🟡 deferred (L4) |
| 4 | Billing v8 IPC runtime-verify | 🟡 deferred (kräver license testers + live app) |
| 5 | Audio accuracy eval | 🟢 OK deferred (kräver xeno-canto API v3 key) |
| 6 | AB-flytt (Account Transfer) | 🟢 OK deferred (post-launch) |
| 7 | SV legal-översättningar | 🟢 OK deferred (Nordics-first intentional) |
| 8 | Plan 6a T8/T9 device-screenshots | 🟢 OK deferred (kräver test-image-infra) |

---

## Rekommenderad arbetsordning för nästa session

1. **Sweep BLOCKERS (~25 min)** — B1, B2, B3, B4 i den ordningen. Commit per blocker.
2. **Sweep HIGH (~80 min)** — H1-H11. Kan splittas över 1-2 commits per relaterad batch (build-config-fixar tillsammans, kod-null-safety tillsammans, etc).
3. **Bump versionCode** → 113, tagga `v1.0.1-rc` eller inte (beslut: stanna på 112 om allt rör docs/build-config, bump om kod ändras → enkast: bump till 113 efter H8/H9/H10/H11).
4. **Bygg signed AAB** + verifiera signering.
5. **Manuell Play Console-upload** enligt workflow ovan.
6. **MEDIUM-batch** kan landa som separat commit-stack under tiden tester körs i Internal Testing.

---

## Filreferenser till råa rapporter

- `01-build-release.md` — Build, signing, ProGuard, asset pack, manifest, deps
- `02-play-store-compliance.md` — Store listings, privacy, terms, data safety, screenshots
- `03-code-quality.md` — Regressioner, dead code, deprecated APIs, error-handling
- `04-premium-billing.md` — Billing v8, License key, PremiumGate-placering, RSA-verify
- `05-localization-content.md` — SV/EN paritet, BadgeStringMap, 839 YAMLs, settings URLs
- `06-privacy-security.md` — Datautflöden, secrets, permissions, privacy-löfte-verifiering
- `07-internal-testing-readiness.md` — Runbook, manuella steg, Play Console-prep

---

**Audit-team:** 7 parallella Explore-agenter (Haiku), dispatched via `superpowers:dispatching-parallel-agents`.
**Konsoliderad av:** Opus 4.7 main thread.
