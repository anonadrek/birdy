# i4 — Paritets-svep på iOS: karta + notiser + PDF (design-spec)

> Brainstormad + godkänd 2026-08-16 (Mac). Bygger på i0–i3 (hela appkärnan kodklar). Mål: varje v1.2-feature fungerar på iPhone. Byggs **sim-verifierbart** under iPhone-väntetiden (leverans ~2026-08-26); device-punkterna läggs på den samlade device-sessionen.

## Mål

Stänga de tre återstående feature-luckorna mot Android v1.2 — personlig fynd-karta, lokala notiser (dagens fågel / veckorecap / trofé), PDF-export av fältdagboken — plus rest-städ (NotifyOthersOnDeactivation, i3-minor-triage, stale-doc-städ). Efter i4 återstår endast i5 (StoreKit) + i6 (release-mekanik) före App Store.

## Beslut (låsta i brainstorm)

1. **En samlad i4-spec/plan** för alla tre delsystemen (inte i4a/i4b-split).
2. **Kart-stack: MapKit + `MKTileOverlay`** — INTE MapLibre (checklistans rad var shorthand, omprövad med kodfakta). Systemramverk = noll nya beroenden, ingen cinterop/SPM/Swift; samma MapTiler raster-tiles (toner-v2 @2x) + samma duotone-matris som Android ⇒ pixelnära paritet. MapLibre förblir dokumenterad fallback om MapKit-vägen mot förmodan faller.
3. **Notis-färskhet: omschemaläggning vid foreground** (ingen BGAppRefreshTask, inga bakgrundsjobb — i linje med privacy-löftet). Dagens fågel är deterministisk per datum → förschemaläggs ~7 dagar med exakt innehåll; recap/trofé får färskt innehåll per foreground-pass och kan bli något stale om appen legat orörd — accepterad avvägning. **Uttalad följd:** när en schemalagd förekomst avfyrats och appen inte öppnas igen schemaläggs ingen ny förrän nästa foreground (Android/WorkManager fortsätter fyra veckovis) — accepterat: färre notiser vid inaktivitet är anti-spam, inte ett fel. BGAppRefreshTask = ev. follow-up.
4. **AAC-uppspelningsfil: NEJ i v1** — v1-avvägningen står (ingen uppspelningsfil för ljudfynd på iOS, samma degrade som Android API<29). AAC + spelare = dokumenterad follow-up. Detta beslut ska inte omprövas i planen.
5. **MapTiler-nyckel på iOS:** runtime-läsning ur `Info.plist`, injicerad av xcodegen från git-ignorerad `iosApp/Local.xcconfig` (iOS-spegel av Androids `gradle.properties`-väg). Albin lägger in nyckeln manuellt (checklistans punkt 9).
6. **Två delade refaktoreringar ingår** (betalar sig direkt, Android shippbar efter varje commit): notis-payload-bygget hoistas till commonMain; PDF-layoutens geometri/palett extraheras till commonMain-metrics.

## Arkitektur

### A. Karta — `MapScreenHost.ios` (composeApp iosMain)

Allt ovanför sömmen är redan delat (`MapViewModel`, `MapPinMapper`, `MapTileTheme.duotoneMatrix`, empty state, attribution, premium-teaser, nav-gate i `AppScaffold`). Nytt:

- Actualen ersätter `IosComingSoonPanel`-stubben med `UIKitView` runt en `MKMapView`, byggd i Kotlin mot `platform.MapKit` (i2c:s AVFoundation-mönster).
- Kotlin-subklass av `MKTileOverlay` med `canReplaceMapContent = true`: hämtar `https://api.maptiler.com/maps/toner-v2/{z}/{x}/{y}@2x.png?key=…` (512 px, zoom 0–20) via egen `NSURLSession` (disk-cache + user-agent), tintar tile-datan med duotone-matrisen via Core Image (`CIColorMatrix`) i `loadTileAtPath` innan den lämnas vidare.
- Ren funktion konverterar Androids 4×5-ColorMatrix (rad-major) → CIColorMatrix-vektorer (r/g/b/a/bias) — unit-testad så färgparitet bevisas i test.
- Vaxsigill-pin: geometri-/färgkonstanter extraheras ur `MapMarkerIcon.android.kt` till commonMain; iOS ritar om med CoreGraphics/`UIGraphicsImageRenderer`; `MKAnnotationView` ankras bottom-center (`centerOffset`). Kameralogik speglas: 1 pin → zoom ~13, flera → bounding box + padding 96.
- `MKMapViewDelegate` (Kotlin-NSObject) för annotation-vyer + pin-tap → samma `onPinClick`-callback som Android.
- Städ: stale kommentaren `MapScreen.kt:37` ("one actual suffices") bort; `IosComingSoonPanel` + `ios_coming_soon_*`-strängarna raderas (enda call-siten försvinner, copyn stale).

### B. Plats — `IosLocationProvider` (composeApp iosMain)

- Speglar `AndroidLocationProvider`-kontraktet: `suspend fun current(): LatLng?`, one-shot (`requestLocation`), 8 s-timeout, `lastKnown`-fallback, returnerar null vid nekad permission/timeout — **kastar aldrig**.
- `CLLocationManager`-delegaten strong-retainas (PHPicker-fällan gäller CoreLocation också).
- `requestLocationPermission`-lambda i `buildIosAppGraph` → `requestWhenInUseAuthorization()` (avsiktligt ingen result-hantering — capture degraderar graciöst, som Android).
- `NSLocationWhenInUseUsageDescription` i `iosApp/iosApp/Info.plist` + `en.lproj`/`sv.lproj` `InfoPlist.strings` (kamera/mic-mönstret).
- `SaveObservationUseCase`/`shouldAttachLocation` orörda (redan delade; regeln "både Audio och Image geotaggar" gäller, per 2026-06-08-planen).

### C. Nyckel-injektion

- iOS: `MAPTILER_API_KEY` läses i runtime ur `NSBundle.mainBundle` Info.plist; `project.yml` expanderar `$(MAPTILER_API_KEY)` från git-ignorerad `iosApp/Local.xcconfig`. Saknad nyckel ⇒ tom sträng ⇒ 403-tiles + NSLog-varning (samma tysta degrade som Android, men loggad).
- Android orörd (`buildConfigField`-vägen behålls).

### D. Notiser — delad payload + `IosNotificationScheduler`

- **Refaktor (commonMain):** en suspend-byggare per notistyp — läser prefs, snapshotar repos, kör `DailyBirdSelector`/`WeeklyRecapBuilder`/`TrophyProgress`, löser compose-resources-strängar → `NotificationContent(title, body, deepLink)` eller null (avstängd/tyst vecka). `millisUntilNext*`-datummatten hoistas från androidMain och testas. Android-workersarna blir tunna skal (payload-byggare → `NotificationCompat` → `notify`); `AndroidAppGraphHolder`-åtkomsten stannar i skalet.
- **`IosNotificationScheduler : NotificationScheduler`** (composeApp iosMain) via `UNUserNotificationCenter` + `UNCalendarNotificationTrigger` (repeats=false): dagens fågel förschemaläggs ~7 dagar (id `daily_bird_<datum>`, 08:00); recap nästa sön 18:00; trofé nästa ons 09:00. Omschemaläggningspass vid `UIApplicationDidBecomeActive` + vid toggle-ändring (schedulern anropas redan där). Cancels via `removePendingNotificationRequests(withIdentifiers:)`. Max ~9 pending — långt under iOS 64-tak.
- **`IosPlatformNotificationsApi`:** cachad auth-status (`getNotificationSettings` är async, interfacet sync — cachen uppdateras vid foreground + efter permission-request), `needsRuntimePermission() = true`, `openAppNotificationSettings()` → `UIApplicationOpenSettingsURLString`. `requestPostNotificationsPermission` → `requestAuthorization(alert|sound|badge)`. Därmed: delade pre-prompt-sheeten visas på iOS, togglarna gör riktigt jobb, "notiser är av"-hjälplinjen fungerar — dagens tysta-lögn-UI försvinner.
- **Deep links:** `UNUserNotificationCenterDelegate` (Kotlin) parsar `birdy://species/{qid}` / `birdy://recap` / `birdy://trophy` ur notisens userInfo → matar `AppGraph.deepLinkFlow` (wire:as i `IosAppGraph`, null i dag). `willPresent` → banner+sound.
- `devTrigger*`-lambdorna wire:as på iOS för sim-verify utan att vänta på söndag.

### E. PDF — metrics-extraktion + `JournalPdfRenderer.ios`

- **Refaktor (commonMain):** `JournalPdfMetrics` — A4 595×842 + marginaler, Field Journal-paletten (PAPER_BG/INK/COPPER/NAVY), typstorlekar/radavstånd, sidordning, datumformattering. Rit-koden förblir per plattform (Canvas/`StaticLayout` vs CoreGraphics/`NSAttributedString` delas inte lönsamt) men båda läser samma konstanter. Androids `JournalPdfLayout` uppdateras till metrics-objektet; behavior-preserving.
- **iOS-actualen** byter `Failed`-stubben mot `UIGraphicsPDFRenderer`: fem sid-funktioner speglar `drawTitlePage`/`drawStatsPage`/`drawSpeciesPage`/`drawBadgesPage`/`drawColophonPage`; paginering via redan delade `JournalPageAggregator`; fel → `Failed` per kontraktet.
- **Typsnitt:** DM Serif Display Italic + Caveat som `project.yml`-resurser; registreras vid första render via `CTFontManagerRegisterFontsForURL` → `UIFont` per namn. Misslyckad registrering ⇒ systemtypsnitt + logg, rendera ändå (aldrig hårt fel för typsnitt).
- **Wiring:** `outputPathFactory` under `NSCachesDirectory/journal-exports` (`audioStorageDirPath()`-mönstret); `journalExport` i `buildIosAppGraph` → export-CTA:n i Arkiv-fliken blir synlig. Share-sheeten finns redan (`SettingsLauncher.ios.kt`); dess stale expect-KDoc ("no-ops på iOS") rättas.

### F. Rest-städ

- `IosAudioRecorder.teardown()`: `setActive(false)` får `AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation` så duckad musik/podd återupptas efter inspelning.
- i3:s deferred-minor-triage: egen task — billiga fynd fixas, resten re-deferras uttryckligen med motivering.
- `versionName` → `1.2.0-ios-i4` som sista task.

## Felhantering (sammanfattning — allt återanvänder befintliga vägar)

- Karta: saknad nyckel/403 ⇒ tomma tiles + NSLog (Android-paritet, men loggad); nekad plats ⇒ `current()` = null ⇒ fynd utan koordinater ⇒ delade empty staten (ingen krasch, inget felstate — som Android).
- Notiser: nekad permission ⇒ toggles persistar men inget schemaläggs; hjälplinjen i Inställningar visar av-läget + länkar till systeminställningar. Payload-byggare som returnerar null ⇒ ingen notis schemaläggs (tyst vecka-beslutet delas med Android).
- PDF: render-fel ⇒ `Failed` ⇒ befintlig snackbar (`premium_teaser_export_failed`); typsnittsfel ⇒ degradera + logga, inte faila.

## Test & verifiering

- **Full gate per commit** (i3-disciplinen): Android-raden (`:shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt`) + iOS-raden (shared-modulernas + `:composeApp:iosSimulatorArm64Test` + `linkDebugFrameworkIosSimulatorArm64`). Android shippbar efter varje commit.
- **Nya unit-tester:** ColorMatrix→CIColorMatrix-konverteringen, tile-URL-byggaren, delade markör-geometrin, notis-payload-byggarna + hoistad datummatte (commonTest), `JournalPdfRendererContractTest` börjar köra på iOS automatiskt.
- **Sim-check (Albins händer, ~15 min):** kart-fliken med `simctl location` + nyckel i `Local.xcconfig` → duotone-tiles + vaxsigill-pin efter sparat fynd + pin-tap; notis-pre-prompt → systemdialog → devTrigger-avfyrad notis (banner + deep link); PDF-export → share-sheet → ögna fem sidorna.
- **Device-verify (samlade iPhone-sessionen ~2026-08-26):** riktig GPS-capture → pin på kartan, notis som avfyras på riktigt (kalender-trigger), PDF-share till Filer/AirDrop, musik-resume-checken (Spotify → inspelning → stopp → musiken återupptas).

## Grindar (i4 gör inte anspråk på "klar" förrän dessa körts)

1. Albins sim-check enligt ovan (kräver MapTiler-nyckeln i `Local.xcconfig` — enda förberedande handgreppet).
2. Device-punkterna på den samlade iPhone-sessionen.

## Risker & gates

- **MapKit-vägen är omprövningen av checklistans MapLibre-rad** — riskerna (tile-tint-prestanda i `loadTileAtPath`, `canReplaceMapContent`-beteende) attackeras i planens FÖRSTA task som en i2b-T1-stil spike: tiles + duotone + en pin i simulatorn innan resten byggs. Faller den ⇒ MapLibre-fallback (research-läge finns: website-kartans vektor-stil-omfärgning).
- **Foreground-modellens staleness** (recap/trofé) är ett medvetet beslut — omprövas inte i planen; BGAppRefreshTask är follow-up om det skaver i praktiken.
- MapTiler-kvoten: iOS-trafiken går mot samma konto som Android — bevakas som del av befintlig launch-data-punkt.

## Utanför scope (uttalat)

- AAC-uppspelningsfil + spelare för ljudfynd (beslut 4 — follow-up).
- BGAppRefreshTask / bakgrundsuppdatering av notisinnehåll (follow-up).
- MapLibre-integration (endast dokumenterad fallback).
- StoreKit/paywall-ändringar (i5) och release-mekanik (i6). Premium-gaten på iOS fortsätter köra `premiumOverride = Active(LIFETIME)` tills i5.
- detekt-KMP-hålet (repo-brett, egen chore per trap-katalogen).
