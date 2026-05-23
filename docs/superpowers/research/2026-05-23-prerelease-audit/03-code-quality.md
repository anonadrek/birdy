# Kodkvalitets-audit — 2026-05-23

## Sammanfattning

Kodbasen är **väl framstaplad efter PremiumGate-rivningen** — Listen-screenen är helt ren, och ingen dead code kvarstår från licensöppningen. Fyra allvarliga regression-risker identifierades: alpha-navigation fortfarande pinnad (B2 från föregående audit), `speciesId!!`-assertions (NPE-risk i ArchiveViewModel), osäker cast i BadgesViewModel, och swallowed error-handling i BadgeBackfill (split try/catch krävs). Övriga fynd är låga-medium hygien. Versionsnumret är rätt (1.0.0), Billing ProGuard-regler är på plats.

## Findings

### BLOCKER

**[Navigation alpha-version fortfarande pinnad]** — `gradle/libs.versions.toml:18` — `androidx-navigation = "2.8.0-alpha10"`. **Denna var identifierad i föregående audit (B2) och måste fixas före AAB-upload.** Alpha-versioner accepteras inte på Production-track i Play Console. Bumpa till senaste stable (2.8.x final eller 2.7.x LTS).
- **Tid:** ~2 min (byt version + verifiera CI)

---

### HIGH

**[speciesId!! assertion NPE-risk]** — `composeApp/src/commonMain/kotlin/se/birdy/app/badges/RecalculateBadgesUseCase.kt` + `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveViewModel.kt` + `composeApp/src/commonMain/kotlin/se/birdy/app/usecase/ExportJournalUseCase.kt` (4 anrop totalt) — Legacy v0.5.0a-observationer kan ha null `speciesId`; `!!` crashar den hela flow:en istället för att degradera gracefully.
- **Fix:** `groupBy { it.speciesId ?: "unknown" }` eller `filter { it.speciesId != null }` före groupBy.
- **Tid:** ~5 min

**[Osäker smart-cast i BadgesViewModel]** — `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesViewModel.kt:50` — `buildLoaded(...) as BadgesUiState` castas utan `as?` guard. Om upstream emission ändras blir det `ClassCastException` på nästa state-uppdatering.
- **Fix:** Använd `as?` med fallback till Error-state, eller ta bort casten (type-safe flow).
- **Tid:** ~3 min

**[BadgeBackfill error-handling sväljer fel helt]** — `composeApp/src/commonMain/kotlin/se/birdy/app/bootstrap/BadgeBackfillOnAppStart.kt:22-37` — En enda `runCatching { ... }` runt allt; om `persist()` failar på rad 32 är badge-staten korrupt nästa start. Silentfailure utan warning.
- **Fix:** Splitta try/catch per operation; emit warning log på fail; låt caller (`AppGate` / `MainActivity`) hantera degradation.
- **Tid:** ~10 min

**[PremiumBillingClient unchecked cast]** — `composeApp/src/androidMain/kotlin/se/birdy/app/data/premium/PremiumBillingClient.android.kt:230` — `client.launchBillingFlow(activityContext as Activity, ...)` utan `as?` guard. Future-refactor kan passa non-Activity → omedelbar `ClassCastException`.
- **Fix:** Byt till `as? Activity` + return `PurchaseResult.Error(...)` eller kräv Activity i signaturen.
- **Tid:** ~5 min

---

### MEDIUM

**[ScanViewModel GlobalScope + NonCancellable tear-down pattern]** — `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt:147` + samme mönster i `ScanViewModel.kt:147-149` — CameraX native-resurser kan läcka om low-memory-kill träffar innan `GlobalScope.launch` slutför. Synkron `cameraSource.stop()` först är säkrare.
- **Fix:** `try { cameraSource.stop() } finally { ... }` före `GlobalScope` launch, eller använd `lifecycleScope` med explicit timeout.
- **Tid:** ~8 min

**[openExternalUrl saknar runCatching]** — `composeApp/src/androidMain/kotlin/se/birdy/app/ui/settings/SettingsLauncher.android.kt:23-25` — Om ingen browser registrerad för URL crashar Settings-skärmen. Övriga launcher-metoder (mailto, share, Play Store) är wrappade.
- **Fix:** Wrap i `runCatching { ctx.startNewTaskActivity(...) }`.
- **Tid:** ~2 min

**[MatchResultViewModel DB-anrop oguardade]** — `composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchResultViewModel.kt:112, 117-118` — `nextStampNumber()`, `countByQid(qid)`, `firstByQid(qid)` kan kasta om SQLite är låst/korrupt.
- **Fix:** Wrap i `runCatching` + emit `MatchResultUiState.Error(...)`.
- **Tid:** ~8 min

**[AudioScanViewModel classifierProvider error-handling bra, men test-coverage saknas]** — Happy-path är well-protected (`runCatching` + emit Error-state), men edge-case recovery under `onTrimMemory` är inte testbar.
- **Note:** Inte kritisk för v1.0; kan vänta till v1.1 test-pass.

**[TODO-kommentar kvarstår i BadgesViewModel]** — `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesViewModel.kt:35-36` — `// TODO Plan 5b Task 12: passed through for badge title/description rendering.` + `@Suppress("UnusedPrivateMember") private val locale: Locale`. Denna ska ha lösts eller konverterats till issue.
- **Fix:** Ta bort kommentar + använd `locale` för rendering, eller dokumentera i issue varför den passar in.
- **Tid:** ~5 min

---

### LOW / Nice-to-have

**[Log.w på Audio TFLite fallback är bra, men saknar Crashlytics]** — `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt:142` — `Log.w("Birdy", "Audio TFLite init failed, falling back to Fake", t)`. Kommentar från föregående audit: _"FirebaseCrashlytics integration deferred — Plan 6b2-trap"_. Användaren får bara DEMO-banner utan telemetri. Inte blocker för v1.0, men ett senare patch kan tiera denna.

**[Hardcoded string format i ArchiveViewModel]** — `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveViewModel.kt` + tests — Gruppering-logik härdkodad utan i18n-layer. Låg prio för v1 (SV + EN funkar), men internationalisering post-launch kräver refactor.

**[distinctUntilChanged deprecated på StateFlow]** — Ingen use i codebase; redan migrerat. ✓

**[Material 1 imports helt borttagna]** — Ingen lingering `androidx.compose.material.*` (M1) imports. ✓

---

## Vad har fixats sedan 2026-05-20-auditen

✓ **PremiumGate-rivning (2026-05-22)** — Listen-screenen är helt ren, ListenLauncher är minimalistisk (3 kort + effekt), ingen premium-logik kvar.
✓ **versionName** — Redan uppdaterad till `1.0.0` (inte rc2).
✓ **Billing ProGuard-keep-regler** — `com.android.billingclient.** { *; }` på plats i `proguard-rules.pro:49`.
✓ **Audio classifier bootstrap** — Robust async-init med `AtomicReference` CAS + lazy-loading + retry-loop vid cache-clear.
✓ **PhotoAnalyzeHost Bitmap-cleanup** — Intermediate bitmaps recyclas korrekt (#3-märkering).
✓ **BadgeBackfill migration-run** — Körs i `AppGate` post-render, inte på MainActivity-huvudtråd.

## Dead code från PremiumGate-rivningen

**Ingen dead code detekterad.** Listen-screenen har ingen kvarvarande premium-logik, inga oanvända imports, inga kommenterad-ut UI-element. Rivningen var ren.

---

## Rekommenderad åtgärdsordning

1. **OMEDELBAR (innan AAB till Internal Testing):**
   - Navigation alpha-version → stable (B2-fix)
   
2. **DENNA VECKA:**
   - speciesId!! → nullable-safe groupBy/filter
   - BadgesViewModel cast → as? with guard
   - BadgeBackfill → split try/catch per operation
   - PremiumBillingClient launchBillingFlow → as? Activity
   
3. **INNAN PRODUCTION-PUBLISH:**
   - openExternalUrl runCatching
   - MatchResultViewModel DB-guard
   - ScanViewModel async tear-down pattern
   - TODO Plan 5b Task 12 → resolve eller issue

4. **FÖRSTA PATCH-CYKEL:**
   - FirebaseCrashlytics eller lokal crash-log fallback
   - ArchiveViewModel i18n-layer för gruppering

---

*Audit utförd 2026-05-23 av Code Quality Agent (Haiku). Fokus: Regressions efter PremiumGate-rivning, deprecated APIs, error-handling, resource leaks. Inget kodändrande gjort — observation + recommendation.*
