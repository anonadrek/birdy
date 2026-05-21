# Fältrevision inför Play Store-launch

> **Datum:** 2026-05-20 · **Mål:** `v1.0.0` · **Launch:** 2026-06-01 · **Status pre-audit:** `v0.9.0a-billing`
>
> Sex agenter har gått igenom kodbasen i parallell. Varje agent hade en avgränsad domän och rapporterade fynd med fil-radhänvisning och rekommenderad åtgärd. Den här filen är konsolideringen — sorterad efter hur fort det blöder.
>
> Ingen kod är ändrad i den här passningen. Allt här är **observationer**.

---

## Sammanfattning

Birdy är **nästan klar** för Play Store. Privacy-löftet håller, koden är ovanligt ren för en v1, och release-mekaniken är på plats sedan Plan 6a. Tre saker måste fixas innan AAB:n laddas upp till Closed Testing; sju saker bör fixas innan v1.0 publiceras på Production; resten är hygien som kan rinna in under första veckans patch-cykel.

| Domän | Verdikt | Blockers | High |
|---|---|---:|---:|
| Play Store policy | Redo med två justeringar | 0 | 3 |
| Build & release | 3 kritiska fixar behövs | 3 | 5 |
| Kod-skräp | Ovanligt rent | 0 | 6 |
| Resurser & i18n | i18n-redo | 0 | 4 |
| Krash & ANR | **Inte redo** | 3 | 4 |
| Privacy & data safety | **Löftet håller** | 0 | 0 |
| **Totalt** | | **6** | **22** |

---

## Blockers — fixa före AAB-upload till Closed Testing

Sex fynd som antingen orsakar rejection, krash på första kalla starten, eller tysta säkerhetshål.

### B1. `versionName` är fortfarande pre-release
`androidApp/build.gradle.kts:48` — `versionName = "1.0.0-rc2"`. Play Console accepterar det men review-teamet flaggar gärna pre-release-strängar på Production-track.
**Åtgärd:** Sätt till `"1.0.0"` när du bygger AAB för Production. Behåll RC-namnet på Closed Testing-spåret om du vill — det är två separata uploads.

### B2. `androidx-navigation` är pinnad på alpha
`gradle/libs.versions.toml:18` — `androidx-navigation = "2.8.0-alpha10"`. Alpha-versioner är osupportade i prod, har kända regressions-historik, och syns i Play Console's API-skanning.
**Åtgärd:** Bumpa till senaste stable (`2.8.x` final om släppt, annars `2.7.x` LTS).

### B3. ProGuard saknar `-keep`-regler för Google Play Billing
`androidApp/proguard-rules.pro` — Billing v8 (wirad i Plan 6b1) använder reflection internt. R8 strippar klasser → `ClassNotFoundException` på första riktiga köp.
**Åtgärd:** Lägg till:
```
-keep class com.android.billingclient.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
```

### B4. Tre `runBlocking { }`-anrop på main-tråden i `MainActivity.onCreate`
`MainActivity.kt:94, 101, 136` — `BadgeCatalogLoader.loadFromResources()`, `firstInstallTimestamp`-migration, locale-override. Var och en ~10 ms i bästa fall; på en kall SD-kort eller ett långsamt SQLite-handle bryter du 5-sekunders-ANR-tröskeln.
**Åtgärd:** Flytta till `ClassifierBootstrap` eller `AppGate` post-render. Visa splash medan de laddar. Sista locale-overriden kan vara `LaunchedEffect(Unit)` i App-rooten.

### B5. Ocheckad cast i `PremiumBillingClient.launchBillingFlow`
`PremiumBillingClient.android.kt:230` — `client.launchBillingFlow(activityContext as Activity, flowParams)`. Om någon någonsin passar en non-Activity-kontext (lätt hänt vid future-refactor) → omedelbar `ClassCastException`.
**Åtgärd:** Antingen byt signaturen till att kräva `Activity`, eller använd `as?` med graceful `PurchaseResult.Error`-retur.

### B6. Java-version-mismatch mellan moduler
`buildSrc/src/main/kotlin/birdy.kmp-android-lib.gradle.kts:16-18` deklarerar `JavaVersion.VERSION_17`. `androidApp/build.gradle.kts:90-92` och `composeApp` använder `VERSION_21`. Klassp-konflikter och bytecode-verification-failures är fältdiagnos när detta händer.
**Åtgärd:** Bestäm en version (rekommendation: 21, eftersom det är vad app-modulen redan kör). Bumpa shared-libs till matchande. Verifiera mot AGP 8.7.3.

### B7. `PREMIUM_DEBUG_FORCE_ACTIVE` BuildConfig-flagga utan synlig debug-gate
`androidApp/build.gradle.kts:75, 85` — Sätter flaggan till `"false"` i båda buildTypes. Om koden som läser den (`AppGraph.premiumOverride`) **inte** är `BuildConfig.DEBUG`-omsluten, finns risk att en framtida ändring sätter `true` i release-builden av misstag.
**Åtgärd:** Verifiera att alla användningar av flaggan är `if (BuildConfig.DEBUG)`-gatade. Lägg en kommentar i build-skriptet som låser detta som invariant.

---

## High — fixa innan v1.0 publiceras på Production

22 fynd som syns för användare, höjer krash-raten, eller hamnar i Play Console's varnings-flöde.

### Play Store-policy

- **`android:localeConfig` saknas** för Android 13+ per-app språkväljare. `androidApp/src/main/AndroidManifest.xml`. → Skapa `res/xml/locales_config.xml` med `sv-SE` + `en-US` och referera från `<application>`. Detta wirar också Plan 7e:s Language-picker-stub som idag är placeholder.
- **TFLite-modellen följer med i molnbackup** (3.5 MB). `data_extraction_rules.xml:4-6` + `backup_rules.xml`. → Exkludera `assets/*.tflite` eller den specifika modell-pathen. Modellen är återställningsbar; observationer är inte.
- **`versionName "1.0.0-rc2"`** — se B1.

### Build & release

- **`isDebuggable = false` deklareras inte explicit på release** (`androidApp/build.gradle.kts:77-87`). Defaultar till false, men explicit deklaration förhindrar olyckor och Play rejection.
- **Inget `applicationIdSuffix = ".debug"` för debug-builds** — debug + release kan inte sam-existera på samma device. Lägg till och spara framtida frustration.
- **TFLite-versioner hårdkodade** i två filer (`androidApp/build.gradle.kts:23`, `shared/ml/build.gradle.kts:33-34`). → Flytta till `libs.versions.toml` som `tensorflow-lite = "2.16.1"`.
- **`androidApp` saknar lint-block.** `abortOnError = true` + `checkReleaseBuilds = true` rekommenderas, annars går release-builds förbi lint helt.
- **CI bygger bara debug-APK.** `.github/workflows/ci.yml:52` kör `assembleDebug`. Release-specifika ProGuard-regler valideras aldrig pre-merge. → Lägg till `bundleRelease`-steg med signing-secrets i CI.

### Kod-skräp & WIP-markörer

- **`FirebaseCrashlytics` deferred-kommentar.** `MainActivity.kt:248` — `// FirebaseCrashlytics integration deferred — Plan 6 polish.` Produktions-krasch utan telemetri = blind. → Antingen wira Crashlytics (eller alternativ utan tracking, t.ex. lokal logging till `filesDir/crash-log/` som användaren kan skicka via Feedback-mail), eller acceptera blindheten och ta bort kommentaren.
- **Tyst fallback till `FakeBirdClassifier`** vid TFLite-init-fel. `MainActivity.kt:244-249` loggar bara `Log.e()`. Användaren får DEMO-banner som är subtilare än "din AI fungerar inte". → Höj banner-prominensen, eller crash:a för att tvinga fram bug-report.
- **Unused `private val locale` i `BadgesViewModel`** med `@Suppress("UnusedPrivateMember")` (`BadgesViewModel.kt:35-36`). Kvarvarande artefakt från Plan 5b. → Använd eller ta bort.
- **TODO-kommentar (Plan 5b Task 12)** i samma fil. → Konvertera till issue eller radera.
- **`as BadgesUiState`** osäker cast i `BadgesViewModel.kt:48`. → Använd `as?` med Loaded-guard (mönstret är redan etablerat på andra ställen).
- **`speciesId!!`** i `ArchiveViewModel.kt:53` `.groupBy { it.speciesId!! }`. Legacy v0.5.0a-observationer kan ha null speciesId. → `groupBy { it.speciesId ?: "unknown" }` eller filtrera innan.

### Resurser & i18n

- **`AsyncImage` saknar `contentDescription`** i `SpeciesProfileScreen.kt:146, 225-230`. Två foton som är osynliga för TalkBack. → Pass `stringResource(Res.string.species_photo_label, species.name)`.
- **Format-strängar SV vs EN ordning.** `diary_relative_date_full` + `diary_full_date_format` har olika argument-ordning mellan locales (`%1$d %2$s, %3$s` SV vs `%2$s %1$d, %3$s` EN). Korrekt mönster, men verifiera att call-sites i `DiaryDetailScreen` passar args i samma ordning på båda — annars renderar EN fel datum.
- **Hardcoded debug-Text** i `DiagnosticsScreen.kt:48`, `ArchiveScreen.kt:151, 159`. "Run benchmark" / "ML diagnos". Debug-only skärmar men bör ändå antingen gömmas bakom `BuildConfig.DEBUG` eller flyttas till debug-resources.
- **Hardcoded symboler** `Text("📷")`, `Text("★")`, `Text("›")` i flera screens. Emoji + chevron renderar inkonsekvent mellan Android-versioner. → Använd Material Icons vector-drawables.

### Krash & ANR

- **`openExternalUrl` line 23 saknar `runCatching`** i `SettingsLauncher.android.kt`. Övriga launcher-metoder (mailto, share, Play Store) är wrappad. Om ingen browser finns för Privacy/Terms-URL crashar Settings-skärmen. → Wrap line 23 i `runCatching { }`.
- **`MatchResultViewModel.resolve()` DB-anrop oguardade.** Lines 96, 102 — `nextStampNumber()` och `firstByQid()` kan kasta om SQLite är låst/korrupt. → `runCatching` + emit `MatchResultUiState.Error`.
- **`BadgeBackfillOnAppStart.runIfNeeded()`** sväljer DB-fel helt (`BadgeBackfillOnAppStart.kt:22-37`). En enda runCatching runt allt — om persist() failar är badge-staten korrupt nästa start. → Splitta try/catch per operation.
- **`PhotoAnalyzeHost.decodeByteArray`** kan returnera null + downstream `Bitmap.createBitmap()` kan kasta `OutOfMemoryError`. `PhotoAnalyzeHost.android.kt:127-144`. → Try/catch + recycle på exception.
- **`GlobalScope.launch(NonCancellable)` i `ScanViewModel.onCleared`** (`ScanViewModel.kt:147`). Om appen low-memory-killas innan teardown firat → CameraX native-resurser läcker. → `try/finally` med synkron `cameraSource.stop()` först.

---

## Medium — hygien för första patch-cykeln

Nio fynd som inte bryter launch men förtjänar uppmärksamhet inom 1-2 veckor post-launch.

- **`POST_NOTIFICATIONS` deklareras inte.** Inga notifikationer i v1.0, så icke-blocker. Men förbered manifest-permissionen för v1.5 push-roadmap.
- **`BillingClient.connect()` resolverar `resume(Unit)` oavsett success/failure** (`PremiumBillingClient.android.kt:102-122`). `queryProducts()` körs sedan på null-state. → Log warning + delay query tills connected.
- **`signature verification` returnerar true om license-key är blank i DEBUG** (`PremiumBillingClient.android.kt:52-54`). Förbättring: gate med separat `BuildConfig.DEBUG_SIGNATURE_BYPASS_ENABLED`-flagga.
- **CameraX permission-omcheck saknas i `ScanViewModel.startPipeline`.** Om användaren revokerar Camera-permission mid-session hänger pipelinen. → Permission-guard innan `cameraSource.start()`.
- **`enableJetifier = false`** är inte explicit i `gradle.properties:10`. Defaultar till false på AGP 8+ men explicit deklaration räddar förvirring vid framtida AGP-bumpar.
- **`Robolectric 4.13`** är pinnad (`shared/ml/build.gradle.kts:37`). Inte kritiskt, men 4.14+ har modernare device-emulation.
- **Hidden `SPECIES_THIS_YEAR/MONTH`** enum-värden i `LifelistViewModel.kt:50-51, 118` — "fake time-window stats tills Plan 6c". Användaren ser toggle-options som inte fungerar. → Antingen rendera eller dölj från enum.
- **`backup_rules.xml` inkluderar `observations/`-mappen.** Användar-kontrollerat, krypterat av Google. Men Data Safety-formuläret bör vara explicitare: "användare kan inaktivera molnbackup i Settings → Google Account."
- **Compose-resources vs Android `strings.xml`-duplikation.** `androidApp/values/strings.xml` håller bara `app_name`, resten är compose-resources. Ren split, men dokumentera i CONTRIBUTING.md.

---

## Low — polish, framtida polerings-pass

Polish som inte är synlig för users och inte blockerar något.

- **Themed icon-deklaration** verifiera i manifest (Android 13+).
- **SplashScreen API**-migration (`androidx.core.splashscreen`) — `Theme.Birdy.Starting` är custom; en native splash-API skulle bli renare.
- **`-dontnote kotlinx.serialization.AnnotationsKt`** kan grupperas med övriga kotlinx-ProGuard-regler.
- **Scan-frame cleanup efter classification** — idag rensas via hourly TTL i `cleanOldCacheFrames()`. Direkt cleanup efter klass-resultat är snålare.
- **Privacy policy på GitHub Pages.** Funkar — men en custom-domain (`privacy.birdy.app`) känns mer professionellt och låser inte hosting-historiken till `anonadrek`-handelet.
- **"Rensa all data"-knapp i Settings** — idag måste användaren gå via Android-Settings → Apps → Birdy → Storage. Inte GDPR-blocker men hövlighet.
- **`Plan N`-referenser** i kod-kommentarer — flera filer pekar på `Plan 5b`, `6c`, `7c`. Inte fel, men post-launch-städ kan ta bort dem.
- **iOS-stubs** — `UserPreferencesStore.ios.kt:5` + `PremiumStateStore.ios.kt:10, 13` kastar `NotImplementedError`. Dokumenterat som v1-Android-only. Verifiera att manifest + Play-listning aldrig erbjuder iOS-build.

---

## Privacy-löftet — vad agenten faktiskt verifierade

Privacy-agentens viktigaste fynd är att löftet **håller** — på följande grunder:

| Påstående | Verifierat | Bevis |
|---|---|---|
| Foton lämnar aldrig telefonen | ✅ Ja | Endast `filesDir/observations/` + `cacheDir/scan-frames` (1h TTL). Inga HTTP-uploads i hela kodbasen. |
| Ingen position samlas in | ✅ Ja | `Observation.latitude/longitude/location_label` är alltid `null` i v1.0. Inga GPS-API-anrop i koden. |
| Inga analytics/tracking-SDKs | ✅ Ja | Zero Firebase, Crashlytics, GA, AppsFlyer, Mixpanel, Amplitude, Sentry. Endast Coil/CameraX/Compose/SQLDelight/AndroidX. |
| Inga Birdy-backend-anrop | ✅ Ja | Enda nätverks-anrop är Google Play Billing (handlas av Googles SDK; Birdy ser bara entitlement-state). |

Data Safety-formuläret (`docs/play-store/data-safety-form.md`) **stämmer överens** med faktiskt beteende. Privacy policy stämmer.

---

## Per-agent-rapportstatistik

| Domän | Filer scannade | Fynd totalt | Tidigare missade i tidigare reviews |
|---|---:|---:|---|
| Play Store-policy | Manifest + build-config + Data Safety | 11 | versionName, localeConfig, backup-TFLite |
| Build & release | 8 build.gradle.kts + ProGuard + libs.versions | 14 | Java-version-mismatch, alpha-nav, Billing-keep-rules |
| Kod-skräp | ~120 Kotlin-filer | 12 | runBlocking×3, GlobalScope-teardown |
| Resurser & i18n | 533 SV-keys + 520 EN-keys + drawables | 10 | hardcoded emoji/chevron, contentDescription-luckor |
| Krash & ANR | ViewModels + Activity + native interop | 13 | unchecked cast i Billing, swallowed BackupBackfill-fel |
| Privacy | Hela kodbasen + deps + Data Safety-doc | 0 blockers, 6 låga | — (löftet håller) |

---

## Rekommenderad ordning för åtgärd

**Idag (innan AAB-upload till Closed Testing 2026-05-18):**
1. B1 — versionName fix
2. B2 — navigation alpha → stable
3. B3 — Billing ProGuard-keep
4. B5 — Billing unchecked cast
5. B7 — verifiera `PREMIUM_DEBUG_FORCE_ACTIVE`-gating

**Denna vecka (innan v1.0 Production-publish 2026-06-01):**
6. B4 — runBlocking-flytt från onCreate
7. B6 — Java-version-mismatch
8. localeConfig + locales_config.xml
9. TFLite backup-exclude
10. isDebuggable=false + applicationIdSuffix=.debug
11. Lint-block på androidApp
12. AsyncImage contentDescription i SpeciesProfile
13. openExternalUrl runCatching
14. MatchResultViewModel + BadgeBackfill error-handling
15. PhotoAnalyzeHost OOM-guard
16. ScanViewModel synkron CameraX-cleanup

**Första patch-cykeln (1-2 veckor post-launch):**
17. Hela Medium-listan
18. FirebaseCrashlytics-beslut (wira eller ta bort kommentar)
19. CI release-bundle-byggsteg

**Senare polerings-pass:**
20. Hela Low-listan
21. Custom privacy-domain
22. Rensa all data-knapp i Settings

---

*Fältrevisionen utförd 2026-05-20 av sex parallella agenter. Inga ändringar gjorda i koden — det här är observation, inte ingrepp. Konsoliderat av huvud-Claude i Birdys ton: saklig, naturalist, lugn.*
