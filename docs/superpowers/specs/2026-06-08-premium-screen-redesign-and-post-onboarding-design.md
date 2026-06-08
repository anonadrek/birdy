# Premium-skärm: redesign + visa-en-gång efter onboarding — design

**Datum:** 2026-06-08
**Status:** Godkänd design (visuellt verifierad i brainstorm-companion)
**Berör:** `PremiumScreen` + entry-flow + DataStore + strängar (SV/EN)

## Bakgrund & motiv

`PremiumScreen` (paywall:en) listar idag bara **3 features** (PDF-export, säsongsstatistik, fältmärken) med generiska koppar-bockar. Sedan dess har appen fått flera premium-features — framför allt **Fynd-kartan** — som inte syns på skärmen. Dessutom är märkessiffran **stale**: strängen säger "10 premium-stämplar" trots att DP D-omarbetningen landade på **7** (34 totalt = 27 gratis + 7 premium).

Samtidigt finns ingen naturlig punkt där en ny användare *får se* premium-erbjudandet. Den enda automatiska visningen är `EntryFlowDecider`s **cold-start-modal** med 7 dagars grace — en re-engagement-nudge långt senare, inte en introduktion.

Det här arbetet gör två saker:

1. **Bygger om `PremiumScreen`** till en ärligare, fylligare layout som visar både gratis-substansen och vad Premium tillför.
2. **Visar `PremiumScreen` en gång direkt efter onboarding-introt** (för icke-premium-användare).

## Mål

- Premium-skärmen visar appens **faktiska** premium-features (med riktigt innehåll bakom) + en gratis-summering som ramar in Premium som "allt detta gratis — *plus* detta".
- Rätta den stale märkessiffran 10 → 7 överallt.
- Visa skärmen **exakt en gång** efter introt, gate:at på att användaren inte redan har premium.
- Behåll Field Journal-looken (hero, headline, tier-kort, CTA är oförändrade).
- Inga nya beroenden. SV + EN i paritet.

## Icke-mål (YAGNI)

- Ingen RevenueCat / ändrad billing-backend (separat, parkerad).
- Ingen ny premium-feature byggs här — vi listar bara det som redan finns och levererar.
- Ingen ändring av priser, tier-struktur eller cold-start-modalens 7-dagars-logik.
- iOS lämnas orört (Android-only v1).

## Designbeslut (fattade i brainstorm)

| Fråga | Beslut |
|---|---|
| Feature-presentation | **Ikon + titel + underrad** (C-stil) — tematiska tunna koppar-linjeikoner |
| Gratis-sektion | Ja — kompakt 2×2-ruta högst upp, dämpad grön (sekundär) så premium poppar |
| "Insikter" som premium-rad | **Nej** — det är ett tomt löfte (se nedan), tas bort |
| Visa-en-gång gate | **Bara icke-premium** (Free). Syns inte under launch-open; verifieras via debug-force-Free |
| Hero-foto | Befintligt `great-tit-hero.jpg` (oförändrat) |

### Varför "Fältobservatörens insikter" tas bort

Artprofilen (`SpeciesProfileScreen.kt`) visar en teaser `premium_species_title` = "Fältobservatörens insikter" (sub: "Migrationskarta · läten · djupare fält-anteckningar") för icke-premium. **Men det finns inget premium-innehåll bakom den:** migrations-sektionen renderas för alla (rad 194, ej gated), ljud-ID är gratis (BirdNET-licensbeslutet), och någon "djupare anteckningar"-funktion existerar inte. Att lista det på paywall:en vore ett överlöfte och bryter mot appens ärlighets-/integritetslinje. Tas därför bort. Bonus: de saker teasern låtsades sälja (migration, läten) är *gratis* och stärker gratis-pitchen.

## Del 1 — `PremiumScreen` redesign

Fil: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumScreen.kt`

### Ny layout (uppifrån och ner)

1. **Hero** — oförändrad (foto + hörn-brackets + papper-fade).
2. **OrnamentRule (❦)** — oförändrad.
3. **Headline** ("Hela året som *fältornitolog.*") — oförändrad (behåll SV/EN suffix-mekaniken).
4. **Subline** — oförändrad.
5. **NYTT: Gratis-sektion**
   - Eyebrow (Caveat, grön): "Allt det här är gratis — alltid".
   - Kort/ruta med dämpad grön ram + bakgrund (`MarginaliaInk`-toner), 2×2-grid, varje rad = liten grön ✓-cirkel + kort etikett.
   - 4 punkter: *Skanna foto & ljud · Spara fynd i fältdagboken · Uppslagsverk 839 arter · 27 fältmärken & Troférum*.
   - Visuellt **sekundär** (grön, lågmäld) så premium-delen sticker ut.
6. **NYTT: Avdelare** — "— Med Premium dessutom —" (koppar, Caveat, med tunna linjer på sidorna).
7. **4 premium-features** (C-stil rad): ikon-hållare (rundad fyrkant, koppar-tonad) + DM Serif-italic-titel + liten grön underrad. Tunn avdelarlinje mellan raderna.

   | Ikon | Titel (SV) | Underrad (SV) | Levereras av |
   |---|---|---|---|
   | kartnål | Fynd-kartan | Se var du har sett dina fåglar | `MapScreen` (gated i `AppScaffold` `AppRoute.Map`) |
   | dokument↓ | Exportera fältdagbok | Hela dagboken som delbar PDF | `JournalPdfRenderer` |
   | stapeldiagram | Säsongsstatistik | Mönster & årsöversikt från dina fynd | `SeasonStatsScreen` (gated) |
   | rosett | 7 premium-fältmärken | Exklusiva stämplar att jaga | 7 premium-badges |

8. **Tier-kort** (1 år / För alltid) — oförändrade.
9. **CTA + subtext** — oförändrade.
10. **Stäng-knapp (✕)** — oförändrad.

### Ikoner

4 egna **dependency-fria** vektorikoner i tunn koppar-stroke (`AccentCopper`), byggda som `ImageVector` (path-data) eller `Canvas`-ritade i en liten `PremiumFeatureIcon`-komposable. **Inte** `material-icons-extended` (stort beroende; skärmen importerar idag bara `Icons.Outlined.Close` ur core). Motiv: kartnål, dokument-med-nedåtpil, stapeldiagram, rosett — matchar de godkända mockup-ikonerna.

### Feature-datamodell

Ersätt den hårdkodade `listOf(3 strängar)` med en liten intern lista av `(icon, titleRes, subRes)` som renderas av en ny `FeatureRow(icon, title, sub)`. Den gamla `StampBullet`/enkelrads-`FeatureRow` ersätts.

### Strängändringar

**Nya strängar (SV `values/strings.xml` + EN `values-en/strings.xml`):**

| Nyckel | SV | EN |
|---|---|---|
| `premium_free_eyebrow` | Allt det här är gratis — alltid | All of this is free — always |
| `premium_free_scan` | Skanna foto & ljud | Scan photo & sound |
| `premium_free_save` | Spara fynd i fältdagboken | Save finds to your journal |
| `premium_free_encyclopedia` | Uppslagsverk · 839 arter | Encyclopedia · 839 species |
| `premium_free_badges` | 27 fältmärken & Troférum | 27 field marks & Trophy Room |
| `premium_divider` | Med Premium dessutom | With Premium, also |
| `premium_feature_map_title` | Fynd-kartan | Your finds map |
| `premium_feature_map_sub` | Se var du har sett dina fåglar | See where you’ve spotted your birds |
| `premium_feature_export_title` | Exportera fältdagbok | Export field journal |
| `premium_feature_export_sub` | Hela dagboken som delbar PDF | Your whole journal as a shareable PDF |
| `premium_feature_stats_title` | Säsongsstatistik | Seasonal statistics |
| `premium_feature_stats_sub` | Mönster & årsöversikt från dina fynd | Patterns & yearly overview from your finds |
| `premium_feature_badges_title` | 7 premium-fältmärken | 7 premium field marks |
| `premium_feature_badges_sub` | Exklusiva stämplar att jaga | Exclusive stamps to chase |

**Fixade strängar:**

| Nyckel | Före | Efter |
|---|---|---|
| `premium_badges_cta` (SV) | Lås upp 10 fältmärken → | Lås upp 7 fältmärken → |
| `premium_badges_cta` (EN) | Unlock 10 field marks → | Unlock 7 field marks → |

**Borttagna strängar (ersatta av title/sub-paren):** `premium_feature_export`, `premium_feature_stats`, `premium_feature_badges` (SV+EN). Verifiera att de inte refereras någon annanstans innan borttagning.

**Trap-noteringar:** EN-apostrofer ska vara `’` (U+2019), aldrig `'` (compose-resources unescape-trap). `&`-tecken escapas som `&amp;` i XML.

## Del 2 — Visa en gång efter onboarding

### DataStore-flagga

Ny boolean i `UserPreferences` (interface + alla impl):
- `shared/datastore/src/commonMain/.../UserPreferences.kt`: `val postOnboardingPremiumShown: Flow<Boolean>` + `suspend fun setPostOnboardingPremiumShown(value: Boolean)`.
- `InMemoryUserPreferences.kt`: backing `MutableStateFlow(false)` + setter.
- `androidMain/.../UserPreferencesStore.android.kt`: ny nyckel `booleanPreferencesKey("post_onboarding_premium_shown")`, default `false`, + setter.

### Ren beslutsregel

`composeApp/src/commonMain/kotlin/se/birdy/app/premium/EntryFlowDecider.kt` — ny pure funktion:

```kotlin
fun shouldShowPostOnboardingPremium(
    onboardingComplete: Boolean,
    alreadyShown: Boolean,
    state: PremiumState,
): Boolean {
    if (!onboardingComplete) return false
    if (alreadyShown) return false
    if (state !is PremiumState.Free) return false
    return true
}
```

Ingen grace/throttle — visningen är direkt efter intro. Håll den separat från `shouldShowPremiumModal` (olika syften, olika flaggor).

### Inhakning i `AppScaffold`

`composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt`, i den befintliga `LaunchedEffect(Unit)`:

1. Läs `postOnboardingPremiumShown.first()` och nuvarande premium-state (`graph.premiumOverride ?: graph.premiumRepository.state.value`).
2. Om `shouldShowPostOnboardingPremium(onboardingComplete = true, alreadyShown, state)` → `setPostOnboardingPremiumShown(true)` + `navController.navigate(AppRoute.Premium)`. **Returnera tidigt** så cold-start-modalen inte också navigerar samma session (cold-start triggar ändå inte inom 7 dagar, men gör företrädet explicit).
3. Annars → kör den befintliga cold-start-grenen oförändrad.

**Stäng-beteende:** Återanvänd befintlig `AppRoute.Premium`. `onClose` gör `popBackStack()` → tillbaka till `AppRoute.Listen` (start-destination), `onPurchaseComplete` poppar Premium + welcome-toast. Inget nytt beteende behövs.

### Launch-open-interaktion

Under `PREMIUM_OPEN_FOR_LAUNCH=true` tvingar `MainActivity` `premiumOverride = Active` → `state` är aldrig `Free` → skärmen visas **inte** i closed-testing-bygget (förväntat, per beslut). Utvecklaren verifierar via `BuildConfig.PREMIUM_DEBUG_FORCE_*` (force Free). Efter Billing go-live (`PREMIUM_OPEN_FOR_LAUNCH=false`) fungerar den för riktiga icke-premium-användare.

## Filer som berörs

**Del 1:**
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumScreen.kt` (omstrukturering + ikoner)
- ev. ny `.../ui/premium/PremiumFeatureIcons.kt` (vektorikoner)
- `composeApp/src/commonMain/composeResources/values/strings.xml`
- `composeApp/src/commonMain/composeResources/values-en/strings.xml`

**Del 2:**
- `shared/datastore/src/commonMain/.../UserPreferences.kt`
- `shared/datastore/src/commonMain/.../InMemoryUserPreferences.kt`
- `shared/datastore/src/androidMain/.../UserPreferencesStore.android.kt`
- `composeApp/src/commonMain/.../premium/EntryFlowDecider.kt`
- `composeApp/src/commonTest/.../premium/EntryFlowDeciderTest.kt`
- `composeApp/src/commonMain/.../ui/scaffold/AppScaffold.kt`
- `shared/datastore/src/jvmTest/.../InMemoryUserPreferencesTest.kt`

## Teststrategi

**TDD för ren logik:**
- `EntryFlowDeciderTest`: `shouldShowPostOnboardingPremium` → false när onboarding ej klar; false när redan visad; false när `Active`; true när klar + ej visad + `Free`.
- `InMemoryUserPreferencesTest`: `postOnboardingPremiumShown` default `false`; setter uppdaterar.

**Compose/övrigt:** `PremiumScreen` är till stor del presentational; verifiera bygge + snabb visuell device-check.

**Device-verify (SM-S918B):** Färsk install (eller rensad DataStore) med **debug-force-Free** → kör igenom introt → `PremiumScreen` visas en gång med ny layout (gratis-ruta + 4 features + riktigt foto) → stäng → hamnar på Listen → döda + starta om appen → visas **inte** igen.

## Relaterad städning (noterad, utanför scope)

Två befintliga överlöften som inte byggs här men bör rättas separat:
- `premium_species_*` (artprofilens "Insikter"-teaser) — säljer gratis-innehåll. Omformulera eller ta bort teasern.
- `premium_archive_subtitle` nämner "molnsynk" som inte är byggt (v1.5-roadmap) — formulera om.

## Release-notering

Ändringen ingår i nästa AAB. Vid bygget: bump `versionCode`/`versionName` enligt projektmönster och lägg kart-/paywall-uppdateringen i "What's new". (Sker vid upload, inte i detta kodarbete.)

## Risker

- **Vertikal höjd:** Skärmen är en `LazyColumn` → scrollar; gratis-ruta + 4 rader får plats utan problem.
- **Strängborttagning:** Säkra att `premium_feature_export/stats/badges` inte refereras på annat håll innან de tas bort (grep).
- **Dubbel-trigger:** Tidig retur i `LaunchedEffect` säkrar att post-onboarding och cold-start inte båda navigerar samma session.
- **Locale-paritet:** SV + EN måste hållas i synk (trap-katalogen).
