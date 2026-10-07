# Birdy

Birdy känner igen fåglar på foto och på läte, direkt i telefonen. Appen har ett uppslagsverk över 839 europeiska arter och en privat fältdagbok för dina fynd. Den görs av AlbIT AB i Sverige.

- **Webb:** [birdy.community](https://birdy.community)
- **Android:** [Birdy på Google Play](https://play.google.com/store/apps/details?id=se.birdy.android)
- **Juridik:** [integritetspolicy](https://birdy.community/legal/privacy/), [villkor](https://birdy.community/legal/terms/), [datasäkerhet](https://birdy.community/legal/data-safety/)

> **Läge (2026-10-07):** Android-appen är live på Google Play sedan 2026-06-17 (1.2.0). **Release 1.3.0** är i slutlig test och släpps i oktober 2026 tillsammans med de första artsidorna på birdy.community. Den ger ett nytt utseende, köp av Premium i Google Play och krediter för varje foto och text. **iPhone-appen** byggs från samma kod och är inte släppt än.

## Vad appen gör

- **Foto-ID:** skanna live med kameran (zoom 1× till 10×), eller välj ett foto ur galleriet eller ta ett nytt, med beskärning och vridning.
- **Ljud-ID, alltid gratis:** lyssnar i upp till 60 sekunder, slutar när den är säker och visar upp till tre förslag. Modellen BirdNET-Lite har en ickekommersiell licens, så ljud-ID ligger aldrig bakom Premium.
- **Ärlig osäkerhet:** en säker träff får en stämpel, en osäker visar flera förslag, och utan fågel säger appen det.
- **Uppslagsverk:** 839 arter i 15 grupper, med foto, beskrivning, förekomst och kategori i IUCN:s globala rödlista. Sök på svenska, engelska eller vetenskapligt namn.
- **Fältdagbok:** foto och anteckning för varje fynd, plats om du vill, Dagens fågel och Veckans uppslag.
- **Märken:** 27 gratis märken och 7 till med Premium, i Troférummet.
- **Premium, ett tillval:** en privat karta över dina fynd, dagboken som PDF och säsongsstatistik. Köps i Google Play per år eller en gång för alltid. Priserna kommer alltid från Google Play.
- **Krediter:** varje foto visar fotograf och licens, varje arttext anger Wikipedia-artikeln den bygger på, och Inställningar, Om, listar alla bildkällor och licenser.
- **Svenska och engelska.**

## Integritet

All identifiering sker i telefonen. Det finns inga konton, ingen analys och ingen reklam i appen. Fynd, foton och inspelningar sparas bara i telefonen. Kartan hämtar kartbilder från MapTiler, och köp går via Google Play. Detaljer i [integritetspolicyn](https://birdy.community/legal/privacy/).

## Artsidor på birdy.community

En sida per art på svenska och engelska (`/sv/arter/` och `/species/`), byggd ur samma innehåll som appen: foto, kännetecken, när arten syns i Sverige, en inspelning där det finns en, och jämförelser för arter som är lätta att blanda ihop. Fakta kontrolleras mot källorna innan en sida publiceras. Sidorna publiceras en i taget från oktober 2026. Koden finns i `website/` och `tools/content-pipeline/`.

## Vägkarta

- **iPhone:** samma app från samma kod. Foto-ID, ljud-ID, karta, notiser och PDF finns redan i koden; köp via App Store och lanseringen på App Store återstår.
- **Fler länder och språk:** modellerna känner redan igen fåglar från hela världen. Arbetet ligger i innehållet: arttexter, foton, säsongsdata och översättningar.

## Kodbas

Kotlin Multiplatform och Compose Multiplatform: affärslogik och UI delas mellan Android och iOS. Det som skiljer plattformarna (kamera, ML, ljud, karta, köp, PDF, notiser) ligger bakom `expect`/`actual`.

| Mapp | Innehåll |
|---|---|
| `composeApp` | Delad UI för Android och iOS |
| `shared/domain` | Use cases och affärsregler (ren Kotlin) |
| `shared/data` | SQLDelight-databasen för fynden |
| `shared/datastore` | Inställningar och Premium-läge |
| `shared/ml` | Foto-ID (LiteRT på Android, TensorFlow Lite C på iOS) och ljud-ID |
| `shared/pdf` | PDF-exporten av dagboken |
| `shared/content` | Artdata, artfoton och märkesregler |
| `androidApp` | Android-appen |
| `asset-pack` | Artfotona (2 066 WebP-filer) som Play Asset Delivery-paket |
| `iosApp` | iPhone-appen (Xcode-projektet genereras med xcodegen ur `project.yml`) |
| `tools/content-pipeline` | `birdy-fetcher` (Python): artlistan, Wikidata, Wikipedia, foton och artsidornas texter |
| `tools/ml-eval` | Utvärdering av modellerna |
| `tools/licenses` | Licenslistan som appen visar |
| `website` | birdy.community (Astro 7, Tailwind 4), publiceras av Vercel |
| `docs` | Specar, planer, juridik och butikstexter |

## Bygga

**Krav:** JDK 21 och Android SDK 36. Kartan behöver `MAPTILER_API_KEY` i `~/.gradle/gradle.properties` (aldrig i repot). iOS kräver en Mac med Xcode och xcodegen. Webben kräver Node.js 22.12 eller senare.

```bash
# Android
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug          # med telefon eller emulator ansluten

# Tester och lint
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :shared:datastore:jvmTest :composeApp:testDebugUnitTest
./gradlew ktlintCheck detekt

# iOS (Mac)
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
cd iosApp && xcodegen generate              # öppna sedan Birdy.xcodeproj i Xcode

# Webben (från website/)
npm ci && npm run dev
npm run build

# Innehållspipelinen (från tools/content-pipeline/)
uv sync && uv run pytest
```

## Licens

**Appens kod är proprietär**, © 2026 AlbIT AB (org.nr 559593-7607). Du får läsa koden här men inte använda, kopiera, ändra eller sprida den. Se [`LICENSE`](LICENSE).

Innehåll och komponenter från andra har sina egna licenser:

| Vad | Licens |
|---|---|
| Arttexterna (AI-sammanfattningar av Wikipedia) i appen och på artsidorna | CC BY-SA 4.0, se [`shared/content/species/LICENSE.md`](shared/content/species/LICENSE.md) och `LICENSE.md` i `website/src/data/` |
| Artfotona från Wikimedia Commons | CC0, public domain, CC BY eller CC BY-SA enligt krediten för varje foto |
| Ljudmodellen BirdNET-Lite (K. Lisa Yang Center for Conservation Bioacoustics, Cornell Lab of Ornithology) | CC BY-NC-SA 4.0, används oförändrad; därför är ljud-ID alltid gratis |
| Fotomodellen AIY Birds V1 (Google) | Apache 2.0 |
| Typsnitten Caveat, DM Serif Display och Inter | SIL Open Font License 1.1 |
| Kartan | © MapTiler, © OpenStreetMap contributors (ODbL) |
| Artlistan och namnen | BirdLife Sveriges Västpalearktis-lista, IOC World Bird List (CC BY 3.0) och Wikidata (CC0) |
| Öppen källkod (Kotlin, Compose, SQLDelight, LiteRT, osmdroid med flera) | Mest Apache 2.0; hela listan visas i appen |

Var varje bild, modell och datafil i repot kommer ifrån står i [`docs/legal/image-sources.md`](docs/legal/image-sources.md).

## Säkerhet

Hittar du en säkerhetsbrist? Se [`SECURITY.md`](SECURITY.md).

## Hur projektet drivs

Specar och planer i [`docs/superpowers/`](docs/superpowers/) styr arbetet: först en spec, sedan en plan, sedan kod, med granskning i två steg för varje uppgift. Koden skrivs med Claude Code. Löpande status och arbetsregler finns i [`CLAUDE.md`](CLAUDE.md).

## Tidslinje i korthet

| Datum | Händelse |
|---|---|
| 2026-04-30 | Första commit |
| 2026-05-08 | Foto-ID på telefonen |
| 2026-05-21 | Ljud-ID med BirdNET-Lite |
| 2026-05-23 | Version 1.0.0 |
| 2026-06-08 | Privat fyndkarta |
| 2026-06-17 | Live på Google Play (1.2.0) |
| 2026-07-07 | Arbetet med iPhone-appen börjar |
| 2026-09-24 | Release 1.3.0 börjar: Android 16, köp av Premium, nytt utseende |
| 2026-09-28 | birdy.community i fältbokens färger |
| Oktober 2026 | 1.3.0 och de första artsidorna |
