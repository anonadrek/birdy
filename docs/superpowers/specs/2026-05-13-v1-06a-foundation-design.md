# Plan 6a — Foundation (v0.8.0-rc1)

**Datum:** 2026-05-13
**Status:** Godkänd design — väntar på implementations-plan
**Spec-typ:** Sub-plan inom Plan 6 ("v1.0 Play Store launch")
**Föregående plan:** Plan 7e (Premium tier, v0.7.0e)
**Tag vid slut:** `v0.8.0-rc1` (intern milstolpe, ej publicerad)

---

## 1. Bakgrund och syfte

Efter Plan 7e är appen feature-komplett enligt v1-designspecens scope (`docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md`), men en omfattande UX-review 2026-05-13 med fyra parallella research-agenter (first-run/onboarding, kärnflöde, browse-skärmar, a11y/i18n/release-readiness) identifierade ~19 P0-punkter och ~25 P1-punkter som blockerar eller skadar en Play Store-launch. Parallellt har release-dagen flyttats ~2 veckor framåt p.g.a. AB-bolagsbildningen, vilket ger luft till en grundlig polish + reell Premium-leverans.

**Plan 6 = "v1.0 Play Store launch"**-paraplyet dekomponeras i tre sekventiella sub-planer:

| # | Sub-plan | Mål | Tag |
|---|---|---|---|
| 6a | **Foundation** (denna spec) | UX-polish + release-mekanik | `v0.8.0-rc1` |
| 6b | Billing | Google Play Billing v6 + Premium-skärmen flippas från mock till live sandbox | `v0.9.0-rc2` |
| 6c | Premium | Audio + PDF + stats + extra fält-märken + Crashlytics + faktisk Play Store-publicering | `v1.0.0` |

**6a-syfte:** Göra appen **submit-redo som free-only**. Om 6b/6c oväntat fastnar kan 6a fristående publiceras som v1.0-free. Premium-skärmen lever oförändrad (mock-purchase med debug-flag) genom 6a.

## 2. Scope

### 2.1 UX i existerande flow (13 items)

| # | Skärm | Förändring | Källa |
|---|---|---|---|
| A1 | AppGate + MainActivity | Cold-start TFLite-init flyttas från `MainActivity.runBlocking { buildClassifier() }` till suspend `LaunchedEffect` i AppGate. Tre states: `Initializing` (paper-bg + stamp-shape + Caveat "Förbereder fältboken…") / `Ready` (renderar AppScaffold) / `Failed` (fallback med "Försök igen") | P0 Agent 1 |
| A2 | EncyclopediaScreen | (a) Skeleton-loader: 6-8 `SpeciesRow`-rektanglar i `MarginaliaInk.alpha=.1f`; (b) ✕-knapp som trailing icon i sökfältet när `value.isNotEmpty()`; (c) sticky family-headers vid `ArchiveSort.FAMILY`; (d) `Error`-state med retry-CTA | P0 Agent 3 |
| A3 | LifelistScreen (Diary) | Empty-state redesign: `JournalIntro` + locked `StampSeal(name="Första fyndet")` + Caveat-line "Din fältbok är tom — / skanna för att skriva första sidan." + CTA | P0 Agent 3 |
| A4 | BadgesScreen | Locked-badge tap → bottom-sheet med progress (när `BadgeGridState.InProgress`) eller familjebadge-hint istället för "Hemligt — fortsätt skåda". Genuint dolda (Hidden-state) behåller mystik-beteendet | P0 Agent 3 |
| A5 | NoBirdView | Tipsen får actionable sub-text (Inter, 3-5 ord) + top-1-hint från senaste klassificering: "modellen tyckte den såg en *möjlig kråka* — kom närmare?". Behåll Caveat-italic-marginalia som visuell stil | P0 Agent 2 |
| A6 | DisambigView | Tredje val ovanför Cancel: "Spara som okänd" → skapar `Observation` med `species_id = null` (DB-schema-ändring: gör `species_id` nullable) | P0 Agent 2 |
| A7 | MatchView | Inline edit-note ovanför Save-knappen via `rememberSaveable` + IME `Done` triggar save. Använder `diary_note_*`-strängar som redan finns | P0 Agent 2 |
| A8 | ScanScreen DEMO-banner | Tap → `JournalDialog` med "Vad betyder DEMO?" + "Försök ladda modellen igen" (triggar `SessionFailureGuard.reset()` + classifier rebuild) + "Rapportera buggen" (`mailto:`-länk) | P0 Agent 2 |
| A9 | ScanScreen tap-to-freeze | `awaitPointerEvent()` → `detectTapGestures` med `event.changes.any { !it.isConsumed && it.pressed }`-filter | P0 Agent 2 |
| A10 | ScanScreen permission flow | (a) `PermissionRequiredView` får hero-illustration (kamera-ikon i kopparring + Caveat-text "Birdy ser bara fåglar, inga foton sparas utan ditt val"); (b) `PermissionDeniedView` får sekundär outlined-knapp "Analysera ett foto istället →" → `AppRoute.PhotoAnalyze` | P1 Agent 1 |
| A11 | OnboardingScreen | (a) `BackHandler(enabled = pageIndex > 0) { onPageChange(pageIndex - 1) }`; (b) Skip-knapp på sida 1-2 → `onComplete()` direkt (inte hoppa till sida 3); (c) `onboarding_p3_input_helper` uppdaterad till explicit fallback-warning | P1 Agent 1 |
| A12 | MatchView + NoBirdView strings | "GÅNG N · FÖRSTA: DATUM" → "OBS #N · FÖRSTA SÅGS: 12 MAJ" på SV, "OBS #N · FIRST SEEN: 12 MAY" på EN | P1 Agent 2 |
| A13 | ScanScreen TopChip | Throttle-indikator "1.5 fps" tas bort. Visuell jank räcker som signal. `strings.xml:148` deprekerad | P1 Agent 2 |

### 2.2 Delade komponenter (4 nya, 1 utökad)

| Komponent | Var | Användning |
|---|---|---|
| `JournalLoading(modifier, label?)` | `composeApp/.../ui/components/JournalLoading.kt` | Ersätt alla `Text("Laddar")`, nakna `CircularProgressIndicator` (5 call-sites). Default: AccentCopper spinner + Caveat "Bläddrar i fältboken…" |
| `JournalEmpty(title, body, action?)` | utöka existerande `EmptyState.kt` | Lägg `action: (@Composable () -> Unit)? = null` parameter för CTA-button + Caveat-line |
| `JournalDialog(...)` | `composeApp/.../ui/components/JournalDialog.kt` | Wrapper kring `AlertDialog` med `containerColor = PaperTop`, Caveat på title, AccentCopper på buttons. Ersätt 2 M3-AlertDialog-call-sites (Observation delete + Settings name edit redan är OK) |
| `JournalScaffold(topBar?, content)` | `composeApp/.../ui/components/JournalScaffold.kt` | Kapslar in `Scaffold(containerColor = Color.Transparent)` + `Modifier.paperBackground()`. Ersätt 10+ call-sites |
| `MicroLabel(text)` | redan finns | Säkerställ alla section-labels använder denna istället för inline `9.sp + 0.22.em` Text |

### 2.3 A11y + i18n (7 items)

| # | Förändring | Detalj |
|---|---|---|
| I1 | `AppGraph.defaultLocale` härleds dynamiskt | `(prefs.languageOverride ?? LocalConfiguration.locales[0]).toLocaleEnum()`. Locale-flow: `userPreferences.language` → Activity recreates → AppGate boots med ny locale |
| I2 | Hårdkodade månadsnamn → locale-resource-array | `MatchView.kt:306-317` + `NoBirdView.kt:58-69`. Ny `string-array` i `compose-resources` (`Res.array.months_short_uppercase`); helper i `BadgeDateFormatters.kt` |
| I3 | Language-picker wireup | `SettingsViewModel.changeLanguage(AppLanguage)` skriver DataStore + anropar `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))` via `androidApp`-callback (`SettingsActions.SetLanguage`-effect). Activity recreates → ny locale plockas |
| I4 | `<plurals>` för "Visa N arter" | Ersätt `filter_apply "Visa %1$d arter"` med `<plurals name="filter_apply">` med `one`/`other`. Använd `pluralStringResource` |
| I5 | `contentDescription` på 6 AsyncImage-call-sites | `HeroImage.kt:43`, `PremiumHeroCard.kt:61`, `SpeciesProfileScreen.kt:150`, `ObservationDetailScreen.kt:212`, `ArchiveScreen.kt:347`, `CircularThumb.kt:35`. Default = "Foto av <commonName>"; behåll `null` bara där bilden är dekorativ |
| I6 | `Modifier.semantics{}` på custom-componenter | `StampSeal.kt` (`role = Button` + dynamisk contentDescription per state), `PlateFrame.kt`, `JournalHeadline.kt` (mergeDescendants = true), `JournalSubLine.kt`, `BottomNavBar.kt` (icon contentDescription) |
| I7 | MarginaliaInk kontrast-bump | `MarginaliaInk #5C6E48` → `#3F4F30` (HeroMossMid). Kontrast mot PaperBg blir ~6.7:1 (AA-compliant). Verifiera att alla call-sites fortfarande läses som "marginalia" visuellt |

### 2.4 Konsekvensfixar

- **Diary månadsgruppering:** `stickyHeader { JournalSubLine("maj 2026 · 12 fynd") }` i `LifelistScreen.kt:201-207` vid `RECENT`-sort. Plan 5a-spec-skuld inhämtas
- **Lifelist fejk year/month-stats:** Dölj toggle-cyklerna helt — visa bara total `species_count`. Riktiga time-window-stats levereras i 6c
- **Observation Detail edit-note:** `remember` → `rememberSaveable`, lägg "Avbryt"-knapp bredvid "Spara", `KeyboardOptions(imeAction = ImeAction.Done)`

### 2.5 Settings — alla 6 rader (val 5D)

| Rad | Implementation |
|---|---|
| **Name** | (klar i Plan 7e) — `JournalDialog` |
| **Language** | (I3 ovan) |
| **Rate Birdy** | `Intent.ACTION_VIEW` mot `market://details?id=se.birdy.android`, fallback till Play Store-web-URL |
| **Share** | `Intent.ACTION_SEND` med standard-copy från `share_copy_sv/en`-strängar + Play Store-länk |
| **Feedback** | `Intent.ACTION_SENDTO` med `mailto:feedback@birdy.app` (eller `albinviktorlindblom@gmail.com` tills AB-mail finns) + ämnesrad `"Birdy v${BuildConfig.VERSION_NAME} feedback"` |
| **About** | Ny `AboutScreen` (statisk Compose): version + build (från `BuildConfig.VERSION_NAME` injicerat via `AppGraph`) + AIY V1-attribution + DM Serif Italic + Caveat-font-licenser (SIL OFL) + manuell lista av open-source-licenser (kaml, SQLDelight, Coil, AndroidX m.fl.) renderad som scrollbar text |
| **Privacy** | `Intent.ACTION_VIEW` mot `https://anonadrek.github.io/birdy/privacy` |
| **Terms** | `Intent.ACTION_VIEW` mot `https://anonadrek.github.io/birdy/terms` |

### 2.6 Release-mekanik (9 items)

| # | Item | Detalj |
|---|---|---|
| R1 | Adaptive app-ikon | Foreground SVG (vald i T3-koncept-runda) + background color (paper-creme eller koppar) + monokrom-variant för Android 13+ tema-icon. Filer: `mipmap-anydpi-v26/ic_launcher.xml`, `mipmap-mdpi/ic_launcher.png` ... `xxxhdpi`, `mipmap-anydpi-v26/ic_launcher.xml` round-variant |
| R2 | Splash Screen API 31+ | `androidx.core:core-splashscreen:1.0.1`; `values-v31/themes.xml` med `windowSplashScreenBackground = @color/paper_bg` + `windowSplashScreenAnimatedIcon`; `installSplashScreen()` före `super.onCreate()` |
| R3 | Signing-config | Upload-keystore (PKCS12) sparas utanför repo; läses via `gradle.properties` (`BIRDY_KEYSTORE_PATH`, `BIRDY_KEYSTORE_PASSWORD`, `BIRDY_KEY_ALIAS`, `BIRDY_KEY_PASSWORD`); Play App Signing aktiveras i Play Console |
| R4 | ProGuard/R8 + keep-rules | `isMinifyEnabled = true`, `isShrinkResources = true`; `proguard-rules.pro` med keep-rules för TFLite (`-keep class org.tensorflow.** { *; }` + nnapi), kaml/kotlinx.serialization, AndroidX Lifecycle, SQLDelight, Coil. Verifiera med faktisk device-run av signed AAB |
| R5 | `<queries>`-block | `<queries><intent><action android:name="android.media.action.IMAGE_CAPTURE" /></intent><intent><action android:name="android.intent.action.SENDTO" /><data android:scheme="mailto" /></intent></queries>` |
| R6 | Backup-policy | `android:dataExtractionRules="@xml/data_extraction_rules"` + `android:fullBackupContent="@xml/backup_rules"`. Beslut: backup ALLT (DataStore + SQLDelight DB) — användaren förlorar inte fältbok vid telefon-byte |
| R7 | Network | `android:usesCleartextTraffic="false"`; ingen `<network-security-config>` behövs eftersom appen är offline (Coil läser bara `Res.getUri(...)` + `file://`) |
| R8 | versionCode + versionName | `versionCode = 100, versionName = "1.0.0-rc1"`. Rc-bump till `100x` (rc2 = 101, rc3 = 102, ...) inom 6a; faktisk `versionCode = 200, versionName = "1.0.0"` triggas i 6c när Play Store-publicering sker |
| R9 | App-namn på Play Store-listing | Launcher-label: `app_name = "Birdy"` (oförändrad). Play Store-listing: "Birdy — Fågelskanner" (SV), "Birdy — Bird Scanner" (EN). Listing-strängar i `docs/play-store/store-listing-sv.md` + `store-listing-en.md` |

### 2.7 Off-app artefakter

`docs/play-store/`-mapp med:

- `privacy-policy.md` — Birdy-anpassad version av Google's Play Store Privacy template; täcker: kameradata (on-device, sparas inte utan användarval), foto-storage (`filesDir/observations/`), SQLDelight DB-innehåll (observations, badges, premium-state), inga 3:e-parts-trackers
- `terms.md` — kort villkorstext: åldersgräns, licens (personal use), disclaimers (ML-accuracy är ungefärlig, inte ersättning för riktig fältornitolog)
- `store-listing-sv.md` + `store-listing-en.md` — Play Console-kopia: app-namn, kort beskrivning (80 tecken), lång beskrivning (4000 tecken), ASO-keywords, what's new (release notes för rc1)
- `data-safety-form.md` — svaren till Play Console Data Safety-formuläret: "No data collected", "No data shared" (eftersom on-device), explicit `Camera`/`Photos` permission-rationale
- GitHub Pages-setup: `.github/workflows/pages.yml` (om inte redan) som publicerar `docs/play-store/privacy-policy.md` + `terms.md` som HTML på `https://anonadrek.github.io/birdy/`

## 3. Arkitekturöverväganden

### 3.1 Cold-start TFLite-flytt

**Idag:** `MainActivity.onCreate { runBlocking { buildClassifier() }: Triple<BirdClassifier, ClassifierMode, ModelVersion> }` blockerar UI-thread i ~200-400 ms vid varje cold-start medan AIY V1-modellen (3.5 MB) laddas och initieras.

**Plan:** AppGate mountas direkt vid `setContent`. `AppGate`-state-machine får ett extra steg:

```kotlin
sealed interface AppGateState {
    data class Initializing(val task: InitTask) : AppGateState  // ML / DataStore / DB
    data object Ready : AppGateState
    data class Failed(val cause: InitFailure) : AppGateState
}
```

`AppGraph.buildClassifier` returnerar `Flow<ClassifierReady>`. `AppGate` kollektar och rendererar `JournalLoading` med Caveat-text under init. Vid `Failed` visas en `JournalDialog` med "Försök igen"-knapp som restartar pipeline.

**Trade-off:** kompliserar AppGraph (classifier-fältet blir nullable + state-flow). Acceptabel kostnad — det är denna app's första intryck.

### 3.2 Locale-handling

**Idag:** `AppGraph.defaultLocale = Locale.SV` (hårdkodad). UI-strängar plockas via systemets locale, men species-text + match-strängar (innehåll från SQLDelight) filtreras alltid till SV.

**Plan:** `defaultLocale` blir derivat:
```kotlin
defaultLocale: Locale = (userPreferences.languageOverride
    ?: LocalConfiguration.current.locales[0])
    .toBirdyLocale()  // mappa till Locale.SV / EN
```

`SettingsViewModel.changeLanguage(AppLanguage)`:
1. `userPreferences.setLanguage(lang)` → DataStore-skriv
2. Skicka effect `SettingsEffect.RestartForLocale(tag)` upp till `androidApp`-host
3. Host anropar `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))`
4. Activity recreates → AppGate kallar `buildClassifier` på nytt med nya locale från `prefs.languageOverride`

**Trade-off:** Activity-recreate ger 200-300 ms flicker. Acceptabel — användaren förväntar sig något när språk byts.

### 3.3 Disambig "Spara som okänd" — DB-schema-ändring

`Observation`-tabellen behöver `species_id TEXT NULL` (idag NOT NULL). SQLDelight-migration:

```sql
-- 0002-nullable-species-id.sqm
ALTER TABLE Observation RENAME TO Observation_old;
CREATE TABLE Observation (
    id TEXT NOT NULL PRIMARY KEY,
    species_id TEXT,  -- nullable
    -- ... resten oförändrat
);
INSERT INTO Observation SELECT * FROM Observation_old;
DROP TABLE Observation_old;
```

`SqlDelightObservationRepository.observationsForSpecies(qid)` filtrerar bort `null`; ny query `observationsUnclassified()` för "okänd"-bucket i framtida Plan 6c-stats.

### 3.4 ProGuard-keep-rules — kritisk för release

Minify-on utan keep-rules **kraschar** TFLite-init eftersom reflection slår mot nativ-glue. Test-strategi:

1. Lägg keep-rules
2. `./gradlew :androidApp:assembleRelease` (signerad AAB)
3. Installera signerad AAB på SM-S918B
4. Kör genom hela classifier-flowet (scan + photo + benchmark)
5. Kontrollera Logcat för `org.tensorflow`-relaterade `ClassNotFoundException` / `NoSuchMethodError`

Detta är T1's success-criteria.

## 4. Leveransordning (15 tasks)

| # | Task | Beroende | Mål |
|---|---|---|---|
| T1 | ProGuard/R8 + keep-rules | — | `assembleRelease` green, signed AAB classifier-init OK på device |
| T2 | Upload-keystore + signing-config | — | AAB signerad och uppladdningsbar |
| T3 | Adaptive icon-koncept (3 skisser → 1 vald) | — | 1 vald design |
| T4 | Adaptive icon + Splash Screen API 31+ | T3 | Cold-start = paper-stämpel-splash, ingen vit flash |
| T5 | Cold-start TFLite-flytt | T4 | UI mountar i ms; classifier-init parallellt |
| T6 | Locale-handling (AppGraph + month-array + plurals) | — | EN-enhet visar EN-UI |
| T7 | Language-picker wireup | T6 | Settings → SV/EN/System fungerar utan kill |
| T8 | Delade komponenter (`JournalLoading`/`Empty`/`Dialog`/`Scaffold`) | — | 8+ call-sites migrerade |
| T9 | A11y bumps (contentDescription + semantics + kontrast) | T8 | TalkBack walkthrough funktionell |
| T10 | UX-fixar A1-A13 (alla 13 items i §2.1) | T8 | Alla 13 punkter klara, inkl. DB-migration för A6 |
| T11 | Onboarding (BackHandler + Skip-fix) | — | System-back funkar; Skip → onComplete direkt |
| T12 | Manifest (`<queries>` + backup-rules + cleartext) | — | Manifest Play Store-redo |
| T13 | Settings-rader (Privacy/Terms/About/Rate/Share/Feedback) | T12 + privacy-URL | Alla 6 rader funkar; AboutScreen klar |
| T14 | Play Store-artefakter i `docs/play-store/` + GitHub Pages publicering | — | Privacy URL live + listing-copy klar |
| T15 | Device-verify + screenshot-pass + tag `v0.8.0-rc1` | T1-T14 | Allt grönt på SM-S918B; ≥ 8 nya/uppdaterade screenshots |

**Parallellisering:** T1, T2, T3, T6, T8, T11, T12, T14 kan starta omedelbart (oberoende). T4 väntar på T3. T5 väntar på T4. T7 väntar på T6. T9 och T10 väntar på T8. T13 väntar på T12 + privacy-URL från T14. T15 efter allt.

## 5. Success criteria

1. `./gradlew :androidApp:assembleRelease` producerar signerad AAB **utan crash** vid TFLite-init (verifierat på SM-S918B, inte bara build green)
2. `./gradlew ktlintCheck detekt :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest` allt grönt
3. TalkBack-genomgång på SM-S918B: scan → match → save → archive → species profile → badges → settings utan obegripliga element (≥ 1 contentDescription per interaktiv komponent)
4. Locale-switch via Settings (System/SV/EN) ger korrekt UI utan kill-app-krav (Activity-recreate accepterat)
5. Cold-start från icon-tap till Listen-launcher: ingen vit flash, ingen frusen launcher; total tid ≤ 1.5 s på SM-S918B
6. Device-screenshots committade i `docs/superpowers/screenshots/2026-XX-XX-v0.8.0-rc1/` (≥ 8 nya + uppdaterade)
7. `docs/play-store/` innehåller privacy-policy + terms + listing-copy (SV/EN) + data-safety-form klar för Play Console
8. Privacy + Terms hostat live på `https://anonadrek.github.io/birdy/privacy` (+ terms)
9. Premium-skärmen lever oförändrad (mock-purchase med debug-flag); ingen visuell regression
10. Tag `v0.8.0-rc1` skapad och pushad

## 6. Risker

| Risk | Sannolikhet | Mitigering |
|---|---|---|
| ProGuard-rules missar TFLite-symbol → release-crash | Medel | T1 inkluderar device-test av signed AAB; håll keep-rules generösa initialt (`-keep class org.tensorflow.** { *; }`), strama åt senare |
| Icon-iter tar fler ronder än planerat | Medel | T3: jag presenterar 3 koncept, sen max 1 ronde av justering — annars cyklar vi inte |
| `AppCompatDelegate.setApplicationLocales` flicker vid recreate | Hög | Acceptera 200-300 ms flicker; eller add fade-transition i AppGate |
| Privacy policy-copy juridiskt-otillförlitlig | Låg | Använd Google's Play Store template + Birdy-specifika fakta; not "AB-godkänt" men adekvat för v1.0 free-only launch |
| GitHub Pages-URL behöver bytas när AB-domän finns | Hög | Play Console tillåter URL-byte mellan releases — acceptera detta |
| Adaptive icon i monokrom-tema (Android 13+) ser fel ut | Medel | T3 inkluderar mono-variant från start, inte efter-tanke |
| Cold-start TFLite-flytt introducerar racing mellan AppGate-init och classifier-init | Medel | Modellera som strikt StateFlow-pipeline; AppGate.Ready emitteras först när `BirdClassifier` är fullt redo |
| DB-migration för nullable `species_id` (A6) bryter existerande observationer | Låg | SQLDelight-migration testad i jvmTest med fixture-DB innan device-deploy |

## 7. Out of scope (explicit)

- **Premium-skärmen + mock-purchase** — lever oförändrad genom 6a, tas i 6b
- **Per-tab Premium-teasers + cold-start premium-modal** — leva som idag, tas i 6b/6c
- **Audio / PDF / Stats / extra fält-märken** — alla i 6c
- **Crashlytics** — in i 6c-tail (release-build måste fungera utan)
- **ScanViewModel SavedStateHandle survival** — P2, post-v1.0
- **Plan 2b content backfill** — parallellt spår, ej blockerande
- **ML accuracy improvements (re-train, ny modell)** — post-v1.0
- **Audio-ID** — v2.x roadmap
- **GDPR "Radera all data"-funktion** — flyttad till 6b (matchar billing-data-control-tema)
- **`aboutlibraries`-integration** — manuell lista i AboutScreen räcker för 6a

## 8. Decisions log (Bucket 3-utfall, 2026-05-13)

| Beslut | Val | Motivering |
|---|---|---|
| Privacy/Terms-hosting | **A** — GitHub Pages från `anonadrek/birdy` | Gratis, snabb, fungerar idag. Play Console accepterar URL-byte i v1.0.1 när AB-domän finns |
| Play Store-namn | **B** — "Birdy — Fågelskanner" / "Birdy — Bird Scanner" | ASO-vänligare än bara "Birdy"; launcher-label "Birdy" oförändrat |
| Language-picker | **A** — wire upp i 6a | Eftersom locale-buggen (#I1) ändå löses så är pickern ~2h extra arbete |
| App-icon | **B** — jag (Claude) skissar 3 koncept | Behövs för splash också; värd Field Journal-tematik |
| Settings-rader | **D** — alla 6 inkl. Feedback `mailto:` | < 1h jobb totalt; värdefullt för första 100 användare |

## 9. Spec-fil och tag

- **Spec-fil:** `docs/superpowers/specs/2026-05-13-v1-06a-foundation-design.md`
- **Implementations-plan:** kommer att skapas i `docs/superpowers/plans/2026-05-13-v1-06a-foundation.md` via `superpowers:writing-plans`
- **Tag vid slut:** `v0.8.0-rc1`
