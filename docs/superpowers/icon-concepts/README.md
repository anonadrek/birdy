# Birdy app-ikon-koncept

Adaptive icon foreground SVGs (432×432, Asset Studio-standard) — bakgrund hanteras
separat (`PaperBg #EFE7D6` paper-creme) + monokrom-variant för Android 13+ tema-icon.

**Same image goes to Play Store:** Den valda ikonen exporteras i 512×512 PNG ("Hi-res
icon" — krav för Play Console store-listing) och som adaptive launcher icon för enheten.

## Round 1 (2026-05-13) — typografi/symbol — AVVISAD

Användaren ville ha tecknad fågel istället för bokstavs-/fjäder-/stämpel-koncept.

- `2026-05-13-concept-A.svg` — "Stämpel-B" (versal B i kopparcirkel)
- `2026-05-13-concept-B.svg` — "Fjäder-monogram" (fjäder i mossgrönt + koppar)
- `2026-05-13-concept-C.svg` — "Fältboks-stämpel" (cirkulär stämpel + fågelsilhuett)

## Round 2 (2026-05-14) — tecknad fågel

### Concept D — "Talgoxe"
`2026-05-14-concept-D-talgoxe.svg`

Kawaii-stil tecknad talgoxe — Sveriges vanligaste trädgårdsfågel och appens flaggart
(första arten i pipeline'n). Ikonisk svart-vit-gul färgskala med svart "slips" på bröstet
gör arten direkt igenkännbar. **Pros:** Stark svensk identitet, omedelbar "fågel-app"-signal,
specifik art (ekar appens identifierings-syfte). **Cons:** Kan kännas barnsligt för vissa
användare, mycket detalj kan tappa läsbarhet på 48dp launcher.

### Concept E — "Sketchad fågel"
`2026-05-14-concept-E-sketch.svg`

Naturalist-skiss av en bröstad fågel på gren, i Field Journal-temat. Bläck-och-akvarell-känsla,
med "B"-stämpel i hörnet som ekar in-app StampSeal. **Pros:** Visuell kontinuitet från ikon
till app-tema, premium-känsla, "fältbok"-signal är tydlig. **Cons:** Komplex komposition kan
bli grötig på små storlekar, "B"-stämpeln blir oläslig vid 48dp.

### Concept F — "Birdy-maskot"
`2026-05-14-concept-F-mascot.svg`

Vänlig tecknad fågel-karaktär med stort öga och rund kropp — Duolingo-vibes men med varmare
färgpalett (teal + koppar + creme). **Pros:** Tydlig läsbarhet på alla storlekar,
"app-mascot"-potential för marketing, känns inbjudande för nybörjare. **Cons:** Generisk "söt
fågel" — saknar Field Journal-tema-koppling, ingen specifik art-identitet.

## Val (2026-05-14)

Runda 2 (D/E/F) avvisad också — användaren designade själv ett komplett set i Field Journal-stil.
Slutgiltigt set finns i `final/`:

- `final/ic_launcher_512.svg` — Play Store hi-res icon (512×512), stiliserad fågel-siluett i koppar
  (`#A8552D`) på paper-creme (`#EBDEC2`) med warmth-gradient + grain-filter (SVG-only — bakas
  in i PNG-rasterisering för Play Store; strippas i Android VectorDrawable för launcher).
- `final/feature_graphic.svg` — Play Store feature graphic (1024×500): fågel + "Birdy." Caveat-cursive
  + tagline "A field journal for finds." + monospace eyebrow "IDENTIFY · LEARN · COLLECT" + stamp-seal.
- `final/ic_notification.svg` — 24×24 vit silhuett för Android notification small icon (krav: monochrome
  vit på transparent).
- `final/ic_launcher_monochrome.svg` — 108×108 svart silhuett för Android 13+ "themed icons".

Färgpaletten har en lätt drift mot varmare/mer mättat koppar jämfört med appens token (`AccentCopper
#8C5A3C`). T4 hanterar:
- Konvertering SVG → Android VectorDrawable (strippa filter, bevara path-data)
- Rasterisering 512×512 PNG med grain bevarat (för Play Store)
- 1024×500 feature graphic PNG-rasterisering
- Mipmap fallbacks (mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi)
- Adaptive icon XML (`mipmap-anydpi-v26/ic_launcher.xml`)
- Eventuell uppdatering av in-app `AccentCopper`-token för palett-konsistens

## Refresh (2026-05-22)

Användaren har designat om launcher-ikonen externt — ny flat fågel-i-flykt-silhuett i koppar
`#A8552D` på paper `#EFE7D6`. Källan ligger i `final/ic_launcher_512.png` (512×512 PNG).
SVG-källan (`ic_launcher_512.svg` / `ic_launcher_monochrome.svg` / `feature_graphic.svg`) speglar
**inte längre** den faktiska launcher-ikonen som finns på enheten — PNG:n är canonical source.

Pipeline för regenerering (`tools/update-launcher-icon.py`):
- Legacy mipmap-* PNGs (mdpi 48 / hdpi 72 / xhdpi 96 / xxhdpi 144 / xxxhdpi 192) — composed (bg+bird) som-är.
- Adaptive foreground (`drawable-nodpi/ic_launcher_foreground.png` 432×432) — bird-only på transparent, ~78% av canvas (i safe zone).
- Adaptive monochrome (`drawable-nodpi/ic_launcher_monochrome.png` 432×432) — vit silhuett på transparent (Android tintar via system theme).
- Adaptive background — `colors.xml` → `ic_launcher_background = #EFE7D6` (samma som `paper_bg`).
- Play Store hi-res — `docs/play-store/ic_launcher_512.png` (composed 512×512 PNG).

Splash screen använder samma `ic_launcher_foreground` så den får nya fågeln automatiskt.

