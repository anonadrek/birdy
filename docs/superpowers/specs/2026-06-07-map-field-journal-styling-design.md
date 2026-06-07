# Field Journal-karta — tema & fynd-pin (designspec)

> **Status:** Godkänd design (brainstorming klar 2026-06-07). Nästa steg: implementationsplan via `writing-plans`.
> **Tillhör:** map-polish-v2 (branch `feat/map-polish-v2`, backlog `docs/superpowers/plans/2026-06-07-map-polish-v2-backlog.md`). Detta är backlog **item 2** (Field Journal-kartstil) + pin-delen av **item 3**.
> **Förutsätter:** item 1 (HiDPI @2x/512-tiles + 8 nedladdningstrådar) är redan landad på branchen (`ccec35c2`). Detta bygger vidare på samma host-fil.

## Bakgrund & mål

Den personliga fynd-kartan (Feature A, mergad till main via PR #4) renderar idag MapTilers generiska
`outdoor-v2`-stil med vanliga osmdroid-`Marker`-pins (koppar-droppar). Det fungerar men har ingen koppling
till appens **Field Journal**-estetik (papper `#EFE7D6`, sepia-bläck, koppar, DM Serif). Kartan ser ut som
vilken utomhuskarta som helst.

**Mål:** byt kart-temat mot ett *bläck-på-papper*-utseende som matchar fältdagboken, och gör fynd-pinsen
till **Birdy-fågeln** (appens signatur-silhuett) i en **vax-sigill**-stämpel. Inga nya beroenden, ingen
schema-ändring, integritetslöftet orört (endast tile-viewport egress:ar, precis som idag).

## Beslutslogg (från brainstorming 2026-06-07)

| # | Fråga | Beslut |
|---|---|---|
| 1 | Estetisk riktning | **C — vintage papperskarta** (bläck-på-papper), inte generisk outdoor och inte bara lätt tint |
| 2 | Hur looken byggs | **Kod-väg (C-hybrid):** `toner-v2`-bas + runtime-`ColorMatrix` duotone-tint. **Inte** en custom MapTiler-stil — den looken du gillade *är* `toner-v2` + sepia, vilket en färgmatris återger exakt |
| 3 | Bas-stil | **`toner-v2`** (MapTiler hostar Stamen Toner). Probad 200 på @2x; `toner-lite` testades men `toner-v2`:s tyngre bläck ger rikare sepia |
| 4 | Tint-mål | **Duotone:** vit → papper `#EFE7D6`, svart → varm sepia-bläck. Exakta konstanter finjusteras på enhet |
| 5 | Fynd-pin | **Vax-sigill** (förslag A): gräddstämpel + kopparring + **navy** Birdy-fågel + spets nedtill som pekar på fyndet |
| 6 | Custom MapTiler-stil | **Skjuts upp** som valfri framtida uppgradering (serif-etiketter, färg per lager). Inte nödvändig för looken |

## Scope

### Ingår
- Byt tile-källa `outdoor-v2` → `toner-v2` (behåller @2x/512 + 8 trådar från item 1; nytt källnamn så cachen
  inte blandar in gamla outdoor-tiles).
- Ren `duotoneMatrix(ink, paper): FloatArray`-funktion (commonMain) + applicering via osmdroids
  tiles-overlay-färgfilter i `MapScreenHost.android.kt`.
- Vax-sigill-markör som ersätter default-`Marker`-ikonen: Canvas-komponerad bitmap (gräddcirkel, kopparring,
  navy-tintad Birdy-fågel, kopparspets), ankrad i spetsen mot fyndets koordinat.
- Uppdaterad attribution om `toner-v2` kräver annan formulering än `outdoor-v2`.

### Utanför scope (medvetet, noteras som framtida)
- **Clustering** av täta pins (osmbonuspack `RadiusMarkerClusterer` = JitPack-dep — krockar med appens
  minimal-deps/privacy-etik; användaren bad inte om det).
- **Info-fönster** vid pin-tap (idag: tap → `ObservationDetail`, oförändrat).
- **Custom MapTiler-stil** (serif-etiketter, per-lager-färg).
- **Art-thumbnail i pinsen** (förslag B/C förkastades till förmån för enhetlig sigill).

## Arkitektur & komponenter

### 1. Tile-källa: `toner-v2` @2x
`mapTilerSource()` i `MapScreenHost.android.kt` pekar om från `outdoor-v2` till `toner-v2`, behåller
512px-`@2x.png`-formen + tileSize 512. Källnamnet byts (t.ex. `MapTiler-Toner-Retina`) så osmdroids
disk-cache får en egen mapp och inte återanvänder outdoor-tiles. URL-form verifierad: `toner-v2/{z}/{x}/{y}@2x.png?key=` → 200.

### 2. Duotone-ColorMatrix (papper ↔ sepia)
Ny **ren** funktion i commonMain (t.ex. `ui/map/MapTileTheme.kt`):

```
fun duotoneMatrix(ink: Int, paper: Int): FloatArray
```

Den mappar luminans → tvåtonsskala: en pixel med luminans `L∈[0,1]` blir `ink + L·(paper − ink)` per kanal.
Implementeras som en 4×5-färgmatris där varje utkanal = luminans-viktning (0.299/0.587/0.114) av in-RGB,
skalad med `(paper − ink)` plus offset `ink`. Ren Kotlin (bit-skift för kanal-extraktion, inga
`android.graphics`-typer) → JVM-testbar.

I `MapScreenHost.android.kt` lindas resultatet i `ColorMatrixColorFilter` och sätts på tiles-overlayn:
`mapView.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(duotoneMatrix(INK, PAPER)))`.

Startvärden: `PAPER = 0xEFE7D6`, `INK ≈ 0x2E2417` (varm mörk sepia). De finjusteras på enhet (looken
matchar `sepia(.7) saturate(.6) brightness(1.05) contrast(.95)` + papper-multiply från browser-previewen).

### 3. Vax-sigill-markör
Ny hjälpare i androidMain som bygger en `Bitmap`/`BitmapDrawable` en gång och sätts som `marker.icon`:

- Gräddcirkel: radiell gradient `#F4EDDC → #E5DBC4`.
- Kopparring: 3 dp stroke `#A8552D` (`AccentCopper`).
- Birdy-fågel: `branding/hero_bird.png` (redan i composeApp `commonMain/composeResources/files`) avkodad
  till bitmap, **navy-tintad** (`#1F3A5F` `StampNavy`) via `PorterDuff.Mode.SRC_IN`, centrerad i cirkeln.
- Spets nedtill: kopparfylld triangel.
- Mjuk skugga för läsbarhet mot kartan.

Ankras `setAnchor(ANCHOR_CENTER, ANCHOR_BOTTOM)` så spetsens tipp sitter på koordinaten. Byggs i den
DPI-skala enheten har (sigill-bitmap skapas från `context.resources.displayMetrics.density`).

### 4. Oförändrat
Premium-gate, `MapScreen` empty-state + paper-bakgrund, attribution-position, zoom-to-pins-logiken,
pin-tap → `onPinClick` → `ObservationDetail`.

## Testning

- **Enhetstest (ren JVM, composeApp unit-tests):** `duotoneMatrix(ink, paper)` — verifiera att matrisen
  applicerad på svart `(0,0,0)` ger `ink` och på vit `(255,255,255)` ger `paper` (inom tolerans), samt att
  en mellangrå luminans hamnar mittemellan. Detta är featurens TDD-kärna.
- **Device-verify (SM-S918B, DND först):** bygg `:androidApp:installDebug`, injicera en test-pin (se
  backlog-runbooken), öppna Karta → (a) tiles skarpa + sepia/papper-tonade, (b) gator/kust/etiketter läsbara,
  (c) vax-sigill-pins med navy-fågel pekar rätt och läser tydligt mot kartan. Jämför mot outdoor-baslinjen.
- **Statisk analys:** `:composeApp:ktlintAndroidMainSourceSetCheck` + `:androidApp:assembleDebug` grönt.

## Tekniska följdval (låses i implementationsplanen)

- Exakt placering/namn på `duotoneMatrix` + sigill-hjälparen (commonMain vs androidMain-fil).
- Slutgiltiga `INK`-hex + ev. extra kontrast-/ljus-justering efter device-verify.
- Sigillets pixel-storlek + spets-proportion (finjusteras på enhet).
- Attribution-sträng för `toner-v2` (kontrollera MapTilers/Stamens krav vid bygget; ev. lägg "Tiles: Stamen").
- Om navy-fågeln läser dåligt i små storlekar: fallback till koppar-fågel (beslut på enhet).

## Berörda filer (grov karta)

| Fil | Ändring |
|---|---|
| `composeApp/.../ui/map/MapScreenHost.android.kt` | Tile-källa → toner-v2; sätt tiles-färgfilter; sigill-markör istället för default-ikon |
| `composeApp/.../ui/map/MapTileTheme.kt` (ny) | Ren `duotoneMatrix(ink, paper)` + tema-konstanter (INK/PAPER) |
| `composeApp/.../ui/map/MapTileThemeTest.kt` (ny) | Enhetstest för duotone-matrisen |
| `composeApp/.../ui/map/*` (sigill-hjälpare, ny androidMain-fil) | Canvas-komponerad vax-sigill-bitmap från hero_bird.png |
| ev. `MapScreen.kt` / strings | Endast om attribution-strängen behöver ändras |
