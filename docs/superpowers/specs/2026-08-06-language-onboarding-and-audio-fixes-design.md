# Recensions-batchen: språkval i onboarding + ljud-ID-fixar (vC127 / 1.2.2)

**Datum:** 2026-08-06 · **Status:** Godkänd av Albin (båda sektionerna) · **Mål-release:** vC127 / 1.2.2 (Android)

## Bakgrund

Organiska Play Store-recensioner (Android, vC125 live) klagar på två saker:

1. **"Svårt att ändra språk i appen"** — Albin vill dessutom ha språkval som första steg i onboardingen.
2. **Ljudinspelaren "fungerar inte bra alls"** — symptombild enligt Albin: *hittar aldrig något (no match)*, *fel art/konstiga gissningar*, *krånglig/otydlig att använda*. INTE krasch/felmeddelande.

Kodutforskning (2026-08-06, två Explore-agenter + egenverifiering av de två kritiska fynden) visade att båda problemen har konkreta, avgränsade rotorsaker — ingen omdesign behövs, men flera riktiga buggar ska bort. Allt fixas i delad kod där det går, så iOS-spåret (i3/i4) ärver lösningarna.

### Steg 0 (utanför denna spec, före allt annat): ladda upp vC126

vC126/1.2.1 (byggd, ELF-verifierad, device-verifierad 2026-07-18) ligger o-uppladdad. Den fixar first-run-klassificerarfelet + 16 KB-`.so`:erna — på 16 KB-enheter och vid GC-buggen går ljudvägen idag i **tyst fejkläge** ("Koltrast 92 %" på allt, se fynd L2 nedan). Albin laddar upp vC126 i Play Console **innan** vC127-arbetet börjar, så att verifierat och overifierat inte blandas.

## Mål

- Språkbyte SV/EN fungerar på **alla** Android-versioner (API 24–35) via befintlig Settings-dialog, utan omstarts-instruktioner.
- Språkval som **scen 0 i onboardingen**, replay-säkert, delat (renderas på iOS också).
- Ljud-ID: de fyra allvarliga buggarna fixade, audio-egna trösklar, sessions-aggregation, live-feedback under inspelning, ärliga felstates.
- Android förblir shippbar efter varje commit (full gate); commonMain-ändringar gröna på K/N (`:composeApp:iosSimulatorArm64Test`).

## Icke-mål (följdpunkter, se sist)

- Bundel-swap till EN-default, SV-legal, iOS live-språkbyte (i4), xeno-canto-kalibrering, 212-arters mappnings-backlog, Merlin-style kontinuerlig flerartsvy.

---

# Spår A: Språk

## Fynd (verifierade)

- Språkdialogen **finns redan** och är lätt att hitta: `SettingsScreen.kt:189-194` (rad) → dialog `:598-639` (SV/EN/System) → `SettingsViewModel.saveLanguage()` (`SettingsViewModel.kt:73-78`) → pref + `SettingsEffect.RestartForLocale` → `applyLocale(tag)`.
- **A1 — API ≤32 är en no-op:** `LocaleApplier.android.kt` (`AppLocaleApplier.apply:17-35`): API 33+ går via `LocaleManager` (fungerar); <33 faller på `AppCompatDelegate.setApplicationLocales` som är verkningslös eftersom `MainActivity : ComponentActivity` (`MainActivity.kt:80`) — kodkommentarerna `:20-22`/`:28-30` erkänner det. Användare på Android ≤12: väljer språk → inget händer.
- **A2 — två språkaxlar:** UI-strängar (compose-resources, upplöses via aktivitetens Configuration) och artinnehåll (`se.birdy.content.Locale`, upplöses en gång i `buildAppGraph()` via `LocaleResolver`, `MainActivity.kt:323-328`, → `defaultLocale` `:381`). På 33+ råkar recreation synka dem; på ≤32 blir appen halvöversatt.
- **A3 — iOS-innehåll hårdlåst till svenska:** `IosAppGraph.kt:87-100` skickar aldrig `defaultLocale` → `AppGraph.kt:76`-defaulten `Locale.SV` gäller oavsett enhetsspråk. `LocaleApplier.ios.kt` är no-op (planenligt, i4).
- Default-bundeln är **svenska** (`values/` = SV, `values-en/` = EN, 569 strängar vardera) — tysk/norsk telefon får svensk UI. Hanteras i praktiken av fungerande väljare + onboarding-scen; bundel-swap är följdpunkt.
- Redan på plats och återanvänds: pref-nyckeln `app_language` (`AppLanguage { SV, EN, SYSTEM }`, `UserPreferences.kt:5,21,59`, båda plattformarnas stores), `LanguageTag.kt` (tag-konvertering), `LocaleResolver` (`i18n/LocaleResolver.kt:5-17`, 7 tester), manifest-`localeConfig` (`AndroidManifest.xml:35` → `locales_config.xml` en+sv), picker-strängarna (`settings_language_*`).
- `buildAppGraph()` körs i `onCreate` (`MainActivity.kt:212`) — **grafen byggs om per aktivitets-återskapande**, så recreation räcker för att båda axlarna ska följa med (egenverifierat).

## Design

### Motorn

1. **`MainActivity` → `AppCompatActivity`.** `Theme.Birdy` byter parent `android:Theme.Material.Light.NoActionBar` → `Theme.AppCompat.Light.NoActionBar` (behåll `windowBackground=@color/paper_bg`; `Theme.Birdy.Starting` är AndroidX-SplashScreen med `postSplashScreenTheme` och påverkas inte; `enableEdgeToEdge` + activity-result fungerar oförändrat eftersom AppCompatActivity ärver ComponentActivity). `androidx.appcompat` är redan en dependency.
2. **`AppLocaleApplier` förenklas till en väg:** alltid `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))` (tom tagg = följ systemet); appcompat delegerar själv till `LocaleManager` på 33+. **Dessutom, endast API <33:** sätt `Locale.setDefault(...)` explicit när en override är satt — täcker icke-composable-konsumenter (WorkManager-notisers `getString`, datumformat) där appcompat bara garanterar aktivitets-konfigurationen.
3. **Ny `BirdyApplication`** (registreras med `android:name` i manifestet): läser `app_language` ur DataStore vid processtart (`runBlocking { ... .first() }`, samma mönster och ~ms-kostnad som `MainActivity.kt:323`) och applicerar via punkt 2 **innan första aktiviteten** skapas → ingen extra recreate på kallstart, DataStore förblir enda sanningskällan (ingen `autoStoreLocales`-dubbellagring).
4. **`SettingsEffect.RestartForLocale` avvecklas som koncept:** effekten döps om (t.ex. `ApplyLocale`) — recreation sker automatiskt via appcompat; ingen omstarts-hint behövs. Dialog-UI:t är oförändrat.

### Onboarding-scen 0 "Språk"

5. `OnboardingScreen.kt`: `SCENE_COUNT` 7→8 (`:44`), `when`-dispatchen (`:88-101`) får `0 → SceneLanguage`, övriga skiftar +1; `OnboardingViewModel.MAX_PAGE_INDEX` 6→7 (`:51`).
6. **`SceneLanguage`** (ny fil i `scenes/`): tvåspråkig per design — "Svenska" och "English" som två stora val i Field Journal-stil, vardera skriven på sitt eget språk (valen behöver därför inga lokaliserade strängar; kort eyebrow/rubrik dubbleras SV/EN i layouten). Förval = `LocaleResolver.resolve(override = null, systemTag = <system>)`. Följer `IntroSceneScaffold`-mönstret med `pageOffset`/`isActive` som övriga scener.
7. **Persist direkt vid val, inte i `complete()`:** tryck → `setAppLanguage(...)` + `applyLocale(tag)` via VM-callback. Det är replay-säkert per konstruktion (`isReplay`-guarden i `OnboardingViewModel.kt:42-45` rör bara namn + `hasSeenOnboarding`) och avsiktligt verksamt även i replay-läge — samma semantik som Settings-dialogen. Recreation mitt i onboardingen är ofarlig eftersom språket är scen 0: pagern nollställs till sidan man redan står på, nu på valt språk (fungerar som bekräftelse). Inget tidigare inmatat kan tappas (namnet är scen 7).
8. **Regel för framtiden:** flyttas scenen någonsin från index 0 måste recreation-beteendet omprövas (state-förlust i senare scener).

### iOS-delen (nu)

9. **`IosAppGraph`-fixen:** läs `app_language`-prefsen (via befintliga `UserPreferencesStore`) + `NSLocale`-taggen genom `LocaleResolver` och skicka `defaultLocale` — artinnehållet följer valet från nästa appstart. `applyLocale.ios` förblir no-op (i4 tar live-byte + ev. `AppleLanguages`). Scenen renderas på iOS via delad kod och sparar prefsen korrekt redan nu.

### Felhantering & kantfall

- Recreation förlorar transient state per design endast på scen 0 (se punkt 7).
- `SYSTEM`-valet: tom tagg → appcompat rensar override → systemspråk; `LocaleResolver` faller till SV för okända taggar (befintligt, testat beteende).
- Snabba dubbeltryck på språkvalen: andra trycket träffar antingen samma värde (no-op i appcompat, ingen ny recreate) eller överskrivs av recreation — ofarligt.

---

# Spår B: Ljud-ID

## Fynd (L1–L2 egenverifierade rad för rad; övriga agent-kartlagda)

**Verklighetskoll:** CLAUDE.md:s "3s rec" är inaktuell — sedan v1.0.0 är flödet öppen inspelning (60 s-tak) med rullande 3 s-fönster var 1:e sekund och auto-stopp ≥0,65 (`AudioScanViewModel.kt:73-81`). Arkitekturen är rätt; felen sitter i detaljerna. Nästan allt bor i **commonMain** (`composeApp/.../ui/audio/`) → fixarna är i3:s iOS-kod.

- **L1 — Frys vid lyckad identifiering (rökande pistolen):** auto-stopp anropar `finalizeAndNavigate` inline **inifrån** inferens-coroutinen (`:192-194`), som är barn till `inferenceJob` (`:100`, `:177`). Finalize cancellar `inferenceJob` (`:218`) = sin egen förälder → `stopAndFlush`:s `withContext(IO)` kastar `CancellationException` som **sväljs** av `runCatching` (`:221`; mikrofonen stoppas aldrig) → `ensureActive()` (`:243`) avbryter → state fastnar i `Analyzing` för evigt; 60 s-cap-vägen studsar på `as? Recording ?: return` (`:212`). Ingen avbryt-knapp, ingen timeout; Back stoppar inte micken. Manuell-stopp (`:208`) och cap (`:126`) lanserar korrekt på `viewModelScope` och fungerar — därav signaturen: **appen hänger exakt när den lyckas**. Testen `AudioScanViewModelTest.kt:110-115` accepterar `Analyzing` som pass (syskonen `:150/:167/:183` är strikta) — buggen är alltså dokumenterad av sviten.
- **L2 — Tyst fejkläge:** `AudioClassifierFactory.kt:22-30` fångar alla load-fel och returnerar `FakeAudioClassifier` (fast svar `Q25334`/Koltrast @ 0,92 → Match varje gång, `FakeAudioClassifier.kt:16-19`); moden (REAL/DEMO) **kastas bort** i `AudioScanViewModel.kt:118`; enda spåret är `Log.w` (`MainActivity.kt:188`). Kända prod-triggers i vC125: 16 KB-enheter + GC:ad modellbuffert (båda fixade i o-uppladdade vC126). Förklarar "fel art".
- **L3 — Filter efter top-3:** `AndroidTfliteAudioRunner.kt:84-91` sorterar alla 6 362 råklasser, tar 3, **mappar sedan** (627/6 362 index mappade) — brus-/icke-EU-klasser tränger ut korrekt EU-art på råplats 4; tom lista blir `NoBird` (`MatchResultViewModel.kt:78-91`, vars kommentar `:82` felaktigt hävdar att fel aldrig blir tomma listor). 212/839 EU-arter saknar mappning helt (separat backlog). Förklarar "hittar aldrig något".
- **L4 — Okalibrerade trösklar:** 0,65 auto-stopp (`:77`) / 0,50 Match / 0,35 Disambig (`MatchThresholds.kt:8,11`) / 0,15 hint-golv (`MatchResultViewModel.kt:168`) — ärvda från fotovägen; audio-evalen kördes aldrig (xeno-canto-nyckel saknas; `tools/ml-eval/audio_accuracy_report_2026-05-21.md` = BLOCKED). BirdNET:s eget default-golv är 0,1.
- **L5 — Osynliga recorder-fel:** `read() <= 0` → tyst `break` (`AndroidAudioRecorder.kt:72`) → frusen timer, permanent inaktiv stopp-knapp (`AudioScanViewModel.kt:207`, `RecordingMicButton.kt:80`), medan `WaveformBars` pulserar fejkat även i tystnad (`:76-84`). `startRecording`-throw efter start har `finally` utan `catch` (`:65-86`) i egen scope (`:59`) → default-handler-krasch. Buffert = exakt `getMinBufferSize()` (`:38-44`).
- **L6 — VM:en städas aldrig:** skapas med `remember { }` (`AudioScanScreenHost.android.kt:62`), inte ViewModelStore → `onCleared` körs aldrig; Back poppar bara (`AppScaffold.kt:392-394`) → micken kan hållas (OS-indikatorn lyser) upp till 60 s efter att man lämnat.
- **L7 — API<29-krasch:** Opus-encoder + OGG-muxer (`AndroidWaveformRenderer.android.kt:112-120`) kräver API 29; minSdk 24 (`libs.versions.toml:10`) → Android 7–9 kraschar/fryser vid finalize (bare `viewModelScope.launch` utan catch, `:126/:208` → `:251-262`).
- **L8 — `onTrimMemory` stänger klassificeraren** under levande session (`MainActivity.kt:485-495` vs `classifierInstance` `:71`) → `check(!closed)` (`AndroidTfliteAudioRunner.kt:64`) → krasch vid backgrounding (samma mönsterklass som i2c:s VM-singleton-regel).
- **L9 — Disambig får alltid 1 kandidat:** `:264-282` packar bara `results.first()` trots top-3 från runnern.
- **L10 — Noll loggning** på hela ljudvägen; `Error.BootstrapFailed` visar rå exception-text (`AudioScanScreen.kt:98-99`); `bestSoFar` finns i `Recording`-statet men **renderas aldrig**.

## Design

### Buggfixar

1. **L1-fixen:** auto-stopp-grenen lanserar finalize på `viewModelScope` (spegel av `:126`/`:208`) istället för inline-anrop; `runCatching` runt `stopAndFlush` rethrowar `CancellationException` (repo-regeln); testen `:110-115` skärps till strikt `NavigateToMatch`. CAP/AUTO-race hanteras redan av CAS:en (`:214`).
2. **L3-fixen:** runnern rankar **endast mappade index** → `take(3)`; load-guard `require(outputClasses == mapper.totalBirdnetClasses)`. Konsekvens: resultatlistan från riktiga modellen är aldrig tom → NoBird styrs enbart av trösklar; `MatchResultViewModel:82`-kommentaren rättas.
3. **L2-fixen:** `AudioClassifierFactory` får `allowFakeFallback` (true endast i debug/preview): i produktion propagerar load-fel till bootstrap-felstate → `AudioScanScreen` visar lokaliserat fel + "Försök igen" (inte rå exception-text; riktiga felet loggas som breadcrumb). Moden slutar kastas bort (`:118`); när DEMO faktiskt är aktiv (debug) visas en banner.
4. **L5/L6-fixen:** `AudioRecorderApi` får `onError`-callback i det delade kontraktet (iOS-actualen i i3 implementerar samma); Android-recordern signalerar på `read() <= 0` och fångar loop-throws → `Error.RecordingFailed` istället för tyst break/krasch; buffert-headroom ×4. Delade `AudioScanScreen` får `DisposableEffect` → `cancelRecording()` vid dispose (stoppar micken vid Back/navigering; ofarligt efter `NavigateToMatch` tack vare state-guarden `:301-303`). `Analyzing` får avbryt-knapp + ~15 s-timeout → nytt felstate.
5. **L7-fixen:** finalize-IO:t (PNG + Opus) i try/catch — persist-fel blockerar aldrig navigeringen till resultatet (sökvägar degraderar till null; nullability i `ScanSource.Audio` verifieras i plan-fasen); på API <29 hoppas Opus-encoding över helt (journalpost med vågforms-PNG utan uppspelning på Android 7–9 — ärlig degradering).
6. **L8-fixen:** trim-stängningen av audio-klassificeraren tas bort (ägarskapsregeln från i2c); trade-off: 57 MB kvar i bakgrund, LMK dödar processen vid behov — acceptabelt.

### Träffsäkerhet

7. **Sessions-ackumulator (L4/L9):** VM:n håller per-art löpande max över alla fönster (`Map<speciesId, Top1>`); `Recording.bestSoFar` härleds ur ackumulatorns topp; finalize rankar ackumulatorn → top-3 → **ingen om-klassificering** av bästa fönstret (dubbelinferensen bort). Fallback: om ackumulatorn är tom (stopp innan första inferensen hann klart / alla inferenser föll) klassificeras sista fönstret en gång som idag. Alla top-3 packas i `ScanSource.Audio` (lokal nav-serialisering, ingen migrationsrisk) → Disambig blir meningsfull.
8. **Audio-egna trösklar:** ny `AudioMatchThresholds` — MATCH 0,40 / DISAMBIG 0,20 / NoBird-hint-golv 0,10; auto-stopp kvar 0,65. `MatchResultViewModel`-routingen parametriseras per källa (Audio → audio-trösklar, foto → befintliga). Värdena är **interimistiska** tills xeno-canto-evalen körs (följdpunkt; pipeline redo).

### Live-UX

9. **Live-chip under inspelning:** rendera `bestSoFar` som "Hör: {artnamn} {NN} %" (VM berikar med lokaliserat artnamn via injicerad species-uppslagsfunktion). Stopp-knappens 3 s-spärr visualiseras (inaktiv→aktiv med indikation) istället för att bara vara död. Nya strängar SV+EN (~6–8 st: felstates, demo-banner, chip-prefix, avbryt, timeout, stopp-hint).
10. **Breadcrumb-loggning** (println → logcat, i2a-mönstret) på degrade-orsak, load-fel, classify-fel, encode-fel. Ingen telemetri — privacy-löftet står.

### Felhantering, sammanfattad flödesregel

Varje fel på ljudvägen ska landa i exakt ett av: (a) lokaliserat användarsynligt felstate med retry/avbryt, eller (b) medveten degradering (persist-null) med breadcrumb. Inga tysta fallbacks till fejkdata i produktion, inga ofångade throws i `viewModelScope`, inga svalda `CancellationException`.

---

# Teststrategi

- **commonTest (kör även på K/N):** frys-regression (auto-stopp → strikt `NavigateToMatch`); ackumulator-ranking (flera fönster, olika arter, max-semantik, fallback-klassificering vid tom); tröskel-routing per källa; top-3-pass-through till Disambig; recorder-`onError` → `RecordingFailed`; Analyzing-timeout; dispose → `cancelRecording`; onboarding eager-write + replay-semantik.
- **jvmTest (`:shared:ml`):** filter-först med fejk-mapper (omappade toppklasser tränger inte ut mappade), output-size-guard.
- **Befintligt:** ~300 commonTests + `LocaleResolver`-testerna förblir gröna; `AudioScanViewModelTest`-syskonen orörda.
- **Gate per commit:** `:shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt` + `:composeApp:iosSimulatorArm64Test` + `:composeApp:linkDebugFrameworkIosSimulatorArm64`.

# Device-verify (manuell, efter kod-klart)

- **Emulator API ~30 på Macen** (AVD skapas vid behov) — enda sättet att bevisa ≤32-språkvägen (Galaxyn kör API 35): Settings-byte SV↔EN↔System (UI + artnamn byter, ingen halvöversättning), onboarding-scen 0 (förval, byte, recreation-bekräftelse, replay), splash/edge-to-edge efter tema-swappen.
- **Galaxy SM-S918B (nästa Windows-tillfälle, samkörs med köade i2a/i2c-checkarna):** frys-scenariot (tydlig inspelning som triggar auto-stopp → navigerar, hänger inte), Back mitt i inspelning → mic-indikatorn släcks, live-chippet uppdaterar, felstates (flygplansläge-analogt mic-fel svårt att trigga — bäst-effort), Disambig med 3 kandidater, språkbyte på API 35, scan-återinträde (i2c-latenta Android-fyndet) + crop-re-verify (i2b).

# Risker & öppna beslut

- **Tema-swappen** (`Theme.AppCompat`) är största visuella risken — verifieras först i emulator (splash, edge-to-edge, statusbar). Rollback = trivial (en rad).
- **Trösklarna 0,40/0,20 är kvalificerade gissningar** — medvetet beslut att shippa interimistiskt hellre än att blockera på xeno-canto-nyckeln; evalen är följdpunkt med hög prioritet.
- **`Locale.setDefault`-synken på <33** täcker workers/datum; om appcompat-versionen redan gör detta är raden harmlös dubblering.
- **iOS i3-kontraktet:** `AudioRecorderApi.onError` + `allowFakeFallback` ska respekteras av iOS-implementationerna (noteras för i3-planen).

# Dokumentation & synk (ingår i batchen)

- Version-bump till vC127 / 1.2.2 i sista koduppgiften.
- CLAUDE.md: rätta den inaktuella "3s rec"-raden i Tekniska val, ny statusrad för batchen, trap-katalog-kandidater (finalize-inline-från-barn-coroutine = self-cancel; tyst-fejk-fallback i produktion), synk-regeln (committa + pusha).

# Följdpunkter (ej i denna batch)

1. **xeno-canto v3-nyckel (Albin, ~5 min)** → kör audio-evalen → ersätt interimströsklarna med evidens.
2. 212-arters mappnings-backlog i `birdnet_lite_to_qid.json` (data-pipeline-chore).
3. Bundel-swap till EN-default (`values/` = EN, `values-sv/` = SV) — hjälper icke-SV/EN-systemspråk före första valet.
4. iOS i4: live-språkbyte (`applyLocale.ios`) + ev. `AppleLanguages`.
5. Merlin-style kontinuerlig flerartsvy med spektrogram (roadmap, post-i3).
