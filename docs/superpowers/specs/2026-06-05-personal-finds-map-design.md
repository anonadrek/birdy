# Karta över mina fynd (Feature A) — designspec

> **Status:** Godkänd design (brainstorming klar 2026-06-05). Nästa steg: implementationsplan via `writing-plans`.
> **Release-strategi:** Post-launch **fast-follow** — skeppas i ett bygge *efter* v1.1-GA (vC123+), inte in i det pågående lanseringståget (vC122). Ny plats-permission + tile-SDK ska soak:a i closed testing.

## Bakgrund & mål

Användaren vill kunna se sina egna fågelfynd på en karta. `observation`-tabellen har redan
`latitude REAL`, `longitude REAL`, `location_label TEXT` (alla nullable, sedan Plan 5a) samt motsvarande
fält i domänmodellen `Observation` — men `SaveObservationUseCase` hårdkodar dem till `null` idag
(rad 54–56), så ingenting fyller dem. Lagringen är alltså i princip gratis; jobbet sitter i platsfångst,
kart-rendering, UI-placering och privacy-formulering.

**Detta är Feature A: en privat, on-device karta över *mina egna* fynd.** Den är uttryckligen **inte**
delade hotspots / community-karta (= "Feature B", som kräver backend + konton + moderering + en helt annan
privacy-/naturvårdsanalys, och ligger kvar i v1.5-spåret "Karta & moln").

Integritetslöftet — *"Almost nothing collected, data stays on phone"* — **hålls intakt**. Fynden lämnar
aldrig telefonen.

## Beslutslogg (från brainstorming 2026-06-05)

| # | Fråga | Beslut |
|---|---|---|
| 1 | Release-timing | **Post-launch fast-follow** (vC123+), inte in i vC122-tåget |
| 2 | Kart-renderare | **osmdroid** (OpenStreetMap-renderare, ingen Google-dep, MIT) |
| 3 | Tile-källa | **Kommersiell leverantör + aggressiv cache** (förslag: MapTiler); osmdroid är bara renderaren — OSM:s publika tiles får ej användas kommersiellt |
| 4 | Platsfångst | **Global toggle i Settings, default AV, auto-fångst vid spar** för live-fynd; gratis för alla |
| 5 | UI-placering | **5:e bottennav-flik "Karta"** (egen route, samma ägar-mönster som Troférummet ägs av Märken) |
| 6 | Premium-gräns | **Kart-vyn = Premium; platsfångst = gratis** → "N fynd väntar på din karta"-krok |

## Scope

### Ingår i v1 av featuren
- Settings-toggle för platsfångst (opt-in, default av) + runtime-permission-flöde.
- One-shot enhetsposition vid spar av **live-fynd** (live foto-scan + audio), on-device, ingen Play Services.
- Populering av `latitude`/`longitude` i `SaveObservationUseCase`.
- Kart-flik med osmdroid + kommersiella tiles + disk-cache.
- Species-thumbnail-pins + clustering; tap → befintlig `ObservationDetail`.
- OSM-/leverantörs-attribution på kartan.
- Premium-gate på kart-vyn + teaser för gratisanvändare (med live-räknare på platsförsedda fynd).
- Privacy policy + Data Safety-form + in-app-formulering uppdaterad.

### Medvetet UTANFÖR scope (YAGNI)
- Galleri-EXIF-plats (uppladdade bilder får ingen auto-plats i v1).
- Manuell pin-placering/justering.
- Offline-regionnedladdning ("exkursionsläge").
- Reverse-geocode-etiketter (`location_label` lämnas null i v1 — drar in Google/nät i onödan).
- **Delade hotspots / community-karta (Feature B, v1.5).**

## Arkitektur & komponenter

### Platsfångst
- **`LocationProvider`** — `expect`/`actual`-interface (samma mönster som `CameraSource`/`PremiumBillingClient`).
  - `commonMain`: `interface LocationProvider { suspend fun current(): GeoPoint? }` där `GeoPoint(lat, lng)`.
  - Android `actual`: one-shot via `LocationManager`.
    - API 30+: `LocationManager.getCurrentLocation(...)`.
    - API 24–29: `requestSingleUpdate(...)` / one-shot `requestLocationUpdates` med timeout-fallback.
    - Returnerar `null` vid saknad permission, avstängd GPS, eller timeout — aldrig kasta.
    - **Inget `play-services-location`.** Håller appen Google-fri.
  - Default no-op `actual` för test/iOS-skelett (returnerar `null`) så inget annat påverkas.
- **Settings-toggle** "Spara plats med mina fynd" i DataStore (default `false`). Copy: *"Lagras bara på din telefon. Behövs för kartan."* Slå på → begär `ACCESS_FINE_LOCATION`.
- **`SaveObservationUseCase`** får injicerad `locationProvider: LocationProvider?` + en `locationEnabled: suspend () -> Boolean`-grind. Rad 54–56 byts:
  `latitude/longitude = if (locationEnabled()) locationProvider?.current() else null`.
  Endast live-fynd (live scan + audio); galleri-upload passerar `null` som idag.

### Datalager
Ingen schema-ändring behövs — kolumnerna finns. Nya queries:
- `selectAllWithLocation: SELECT * FROM observation WHERE latitude IS NOT NULL AND longitude IS NOT NULL;`
- `countWithLocation: SELECT COUNT(*) FROM observation WHERE latitude IS NOT NULL;` (för teaser-räknaren).

### Kartskärm
- **`AppRoute.Map`** (ny route) + 5:e `TabSpec` i `BottomNavBar` (ikon `Place`/`Map`).
- **`MapScreen`** (composeApp): osmdroid `MapView` via `AndroidView`.
  - Tile-källa: `XYTileSource` mot kommersiell leverantör; API-nyckel i `BuildConfig` (för KARTBILDER, ej användardata). Disk-cache på (osmdroid default `SqlTileWriter`) → återbesök offline.
  - **`MapPinMapper`** (ren JVM): `List<Observation>` → `List<MapPin>` (qid, lat, lng, thumbnailPath, stampNumber). Testbar utan Android.
  - Markörer med species-thumbnail; **clustering** via osmdroid `RadiusMarkerClusterer`. Tap → `AppRoute.ObservationDetail(id)`.
  - Attribution-overlay (OSM + leverantör) — synligt krav.
  - Default-kamera: zooma till fyndens bounding-box; tomt läge → vänligt "inga platsförsedda fynd än".
  - Valfri polish: vintage/papper-tile-stil (MapTiler custom style) eller osmdroid-färgfilter mot Fältdagboks-paletten.

### Premium-gate
- Kart-vyn gate:as via befintlig `effectivePremiumActive` (samma som `SeasonStats`).
- Gratisanvändare: tapp på Karta-fliken → **teaser** ("Se dina **N** fynd på kartan — Premium") i Plan 7e:s per-flik-teaser-stil, där **N** = `countWithLocation`. Sunk-cost-krok.
- Under `PREMIUM_OPEN_FOR_LAUNCH=true` ser alla testare kartan; gating biter först när Billing flippas.

## Privacy / Data Safety / villkor

För Feature A är detta **lätt** (ingen insamling):
- Plats **används on-device, samlas INTE in, delas INTE**. Data Safety-formuläret: deklarera platsanvändning men markera "not collected / not shared".
- `docs/play-store/data-safety-form.md` + privacy policy: en mening om valfri, lokalt lagrad plats samt att kartrutor hämtas från [leverantör] medan fynden aldrig lämnar telefonen.
- In-app: toggle-copy + ev. rad i About/privacy-sektion.
- Manifest: `ACCESS_FINE_LOCATION` (+ `ACCESS_COARSE_LOCATION` som följd). Ingen bakgrundsplats.

## Testning
Rena JVM-testbara enheter (filosofi som `CropGeometry`):
- Platsfångst-beslutslogik: toggle av → `null`; toggle på + permission nekad → `null`; toggle på + position → koordinater.
- `MapPinMapper`: filtrerar bort null-plats, mappar thumbnail/stamp korrekt.
- Premium-gate-/teaser-räknar-logik.
- osmdroid-`MapView` (tiles, pins, clustering, tap-nav) verifieras via **device-verify på SM-S918B** (som zoom/crop).

## Tekniska följdval (låses i implementationsplanen)
- **Tile-leverantör slutval:** rekommenderar **MapTiler** (gratisnivå ~100k tiles/mån, custom vintage-stil, raster+vector). Alternativ: Stadia, Thunderforest.
- **Modulplacering:** håll `MapScreen` + osmdroid-dep i `composeApp` (android) / `:androidApp` likt övriga skärmar, hellre än egen `:map`-modul. Lägg transitiva deps i `:androidApp/build.gradle.kts` (känd trap: composeApp använder `implementation`, inte `api`).
- **versionCode/versionName-bump** vid bygget (fast-follow efter vC122).
- **Location API-split:** `getCurrentLocation` (30+) vs `requestSingleUpdate` (24–29), med timeout → `null`.

## Berörda filer (grov karta)
- `shared/domain/.../observation/` — ev. `LocationProvider` + `GeoPoint` (eller i composeApp).
- `composeApp/.../usecase/SaveObservationUseCase.kt` — populera lat/lng.
- `shared/data/.../sqldelight/.../Observation.sq` — `selectAllWithLocation`, `countWithLocation`.
- `composeApp/.../ui/scaffold/AppRoute.kt` + `BottomNavBar.kt` — ny route + flik.
- `composeApp/.../ui/map/` (ny) — `MapScreen`, `MapViewModel`, `MapPinMapper`.
- Settings-skärm + DataStore — toggle.
- `androidApp/src/main/AndroidManifest.xml` — location-permission.
- `androidApp/build.gradle.kts` + `gradle/libs.versions.toml` — osmdroid-dep, tile-key i `BuildConfig`.
- `docs/play-store/data-safety-form.md` + privacy policy + website legal.
