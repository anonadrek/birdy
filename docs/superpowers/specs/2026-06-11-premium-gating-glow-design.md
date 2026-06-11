# Glödande premium-gating för gratisanvändare — designspec

> **Datum:** 2026-06-11
> **Status:** Godkänd design, redo för plan
> **Branch:** `feat/premium-gating-glow`

## Mål

När en gratisanvändare (utan premium) rör sig i appen ska de gateade rutorna
*lysa och glänsa* — kännas exklusiva och inbjudande ("åh, vad är det här?")
istället för restriktiva (lås + sudd). Behandlingen ska vara **diskret och
stilren**, konsekvent över alla gateade ytor, och bygga på det shimmer-vokabulär
som redan finns i appen (Troférummet, Dagens fågel, premium-hero i Inställningar).

### Beslut (från brainstorming 2026-06-11)

- **Omfattning:** Alla gateade ytor en gratisanvändare möter.
- **Inramning:** *Locka, inte låsa* — behåll en tjusig förhandsvisning men byt
  "lås + sudd"-känslan mot glöd/shimmer.
- **Intensitet:** *Diskret & stilren* — matchar befintlig Troférum-shimmer.
- **Approach:** En gemensam `Modifier.premiumGlow()` byggd på befintliga
  `shimmerSweep` (Väg A) — ger konsekvens **och** en enda finjusterings-punkt.
- **Mina arter-CTA:** Byts till inbjudande "Se hela din säsong ›".
- **Inställningar-hero:** Routas om genom `premiumGlow()` så alla ytor delar
  samma tuning-punkt.

### Viktig kontext

Just nu är `PREMIUM_OPEN_FOR_LAUNCH=true`, vilket hårdkodar premium aktivt för
alla testare → det gateade läget syns **inte** i nuvarande closed-testing-bygge
utan debug-premium-OFF-toggeln (`DebugPremiumOverrides`). Den här poleringen är
till för gratis-upplevelsen när monetiseringen slås på. Verifiering sker därför
via debug-toggeln, inte via standardbygget.

## Gateade ytor (nuläge)

| # | Yta | Komponent | Fil | Nuläge |
|---|-----|-----------|-----|--------|
| 1 | Artprofil + Arkiv | `PremiumTeaserCard` | `ui/components/PremiumTeaserCard.kt` | Statiskt papperskort, koppar-flagga + koppar-kant |
| 2 | Mina arter (säsongsstat) | `LockedStatsPreview` | `ui/components/LockedStatsPreview.kt` | Suddade staplar + 🔒 + mörk gradient = restriktiv |
| 3 | Karta-tab | `MapPremiumTeaser` | `ui/map/MapPremiumTeaser.kt` | Platt helskärmstext, ingen glöd |
| 4 | Inställningar | `PremiumHeroCard` | `ui/components/PremiumHeroCard.kt` | Glittrar redan (`shimmerSweep` med egna defaults) |

Referenser för befintligt shimmer-språk: `ui/components/ShimmerSweep.kt`,
`ui/components/ShimmerBorder.kt`, `ui/badges/TrophyRoomEntryCard.kt`
(`shimmerSweep(durationMillis = 6000, alpha = 0.20f)`).

## Design

### Kärnkomponent: `Modifier.premiumGlow()`

Ny fil `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/PremiumGlow.kt`.

Tunn wrapper ovanpå befintliga `shimmerSweep` med låsta, stilrena parametrar:

```kotlin
/**
 * Den kanoniska "det här är premium"-glöden för gateade ytor som en
 * gratisanvändare möter. ENDA platsen glöd-parametrarna bor — ändra här så slår
 * det igenom på alla gateade kort (PremiumTeaserCard, LockedStatsPreview,
 * MapPremiumTeaser-kortet, PremiumHeroCard). Bygger på [shimmerSweep] med ett
 * diskret, långsamt svep i linje med Troférummet.
 *
 * Måste appliceras INNANFÖR kortets .clip(...) så svepet maskas till de
 * rundade hörnen.
 */
@Composable
fun Modifier.premiumGlow(
    durationMillis: Int = 6000,
    alpha: Float = 0.18f,
): Modifier = this.shimmerSweep(durationMillis = durationMillis, alpha = alpha)
```

- `durationMillis = 6000` matchar Troférummet (lugnt, inte stressigt).
- `alpha = 0.18f` strax under Troférummets 0.20 — papperskorten är ljusa, så
  ett något svagare svep läser fortfarande tydligt men aldrig skrikigt.
- Parametrarna har defaults så call-sites inte kan driva isär av misstag, men
  förblir override-bara för framtida hero-ytor.

### Yta 1 — `PremiumTeaserCard` (Artprofil + Arkiv)

- Lägg `.premiumGlow()` på den klippta kortytan (innanför
  `.clip(RoundedCornerShape(14.dp))`, över fyllning + text) så det tidigare
  statiska papperskortet får ett långsamt, subtilt glint.
- Koppar-flaggan ("PREMIUM") behålls oförändrad som den uttalade signaturen.
- Export-CTA-läget (när premium är aktivt och kortet visar "Exportera
  fältdagbok") påverkas inte funktionellt — glöden är rent visuell. Eftersom
  kortet bara *visar premium-teasern* för gratisanvändare och export-läget bara
  för premium, känns glöden naturligt rätt: den syns när kortet teasar.

### Yta 2 — `LockedStatsPreview` (Mina arter) — "locka inte låsa"

Den största omramningen. Mildra de restriktiva elementen så rutan blir en
inbjudande förhandsvisning istället för en låst, gråtonad yta:

- **Sudd:** `blur(3.5.dp)` → `blur(1.5.dp)`. Staplarna ska läsas som en mjuk
  förhandsvisning, inte döljas. (Stapeldatan är ändå hårdkodad/fejk — inget
  riktigt döljs av suddet.)
- **Stapel-alpha:** `graphicsLayer(alpha = 0.55f)` → `~0.8f` så kopparstaplarna
  blir levande och inbjudande.
- **Mörk dimnings-gradient bort:** ersätt den mörka `verticalgradient`
  (`PaperTop` 0 → 0.85 alpha) som läser som "disabled" med `premiumGlow()`-svepet
  på kortytan. Behåll ev. en mycket svag ljus topp-vinjett om texten behöver
  kontrast, men inte den mörka dimningen.
- **Degradera hänglåset:** byt det stora centrerade `Icons.Outlined.Lock` (18 dp)
  mot en liten koppar-**"Premium"-pill** (samma pill-stil som används i
  `PremiumHeroCard`/teaser-flaggan) placerad diskret i ett hörn. Inget stort lås
  som barriär.
- **CTA:** byt copy från "Lås upp …" till inbjudande **"Se hela din säsong ›"**.
  Behåll Caveat-typsnittet och kopparfärgen; behåll klickbarheten → navigerar
  till Premium-skärmen. Behåll `contentDescription`/`Role.Button` för a11y, men
  uppdatera content-description till den nya inbjudande texten.

Nettokänsla: "här är en glimt av din vackra säsongsstatistik" som glänser, inte
"låst, suddat, håll dig borta". Komponenten behåller namnet `LockedStatsPreview`
(undvik onödig churn i call-sites).

### Yta 3 — `MapPremiumTeaser` (Karta-tab)

Idag en platt helskärms-`Column` (titel + caption + antal + Button) på
papper-bakgrund. Gör om till ett **centrerat glödande papperskort**, konsekvent
med kort-språket på övriga ytor:

- Behåll papper-bakgrunden som botten (`paperBackground()`), men lägg
  titel/caption/antal/CTA inuti ett centrerat kort: `SandCreme` +
  `RoundedCornerShape` + koppar-kant + koppar-flagga ("PREMIUM"), med
  `.premiumGlow()` på den klippta kortytan.
- CTA-knappen behålls (koppar). Antals-raden (`map_teaser_count`) behålls inuti
  kortet.
- **YAGNI:** inget wax-seal/pin-motiv som dekor i denna omgång — bara text + glöd.

Nettoeffekt: Karta-tabbens gateade läge blir ett inbjudande, glänsande kort
istället för platt text, och visuellt konsekvent med Yta 1.

### Yta 4 — `PremiumHeroCard` (Inställningar)

- Byt det direkta `shimmerSweep()`-anropet (defaults 3600 ms / 0.275 alpha) mot
  `premiumGlow()` så alla fyra ytor delar samma tuning-punkt.
- Konsekvens: svepet blir lite långsammare och diskretare på denna redan-skeppade
  yta. Acceptabel, avsiktlig harmonisering.

### Konsekvens / single source of truth

`premiumGlow()` är den enda platsen glöd-parametrarna bor. Alla fyra ytor anropar
den. Framtida justering av tempo/styrka = en edit.

## Strängar (compose-resources, SV + EN)

- **Ny/ändrad:** Mina arter-CTA → "Se hela din säsong ›" (SV) /
  "See your full season ›" (EN). Återanvänd befintlig nyckel om copyn byts på
  plats, annars ny nyckel. Uppdatera även den string som används för
  `contentDescription`/`overlayCta`.
- **Ev. ny:** "Premium"-pill-text om ingen befintlig nyckel passar (sannolikt
  finns redan en premium-/chip-sträng att återanvända — verifieras i planen).
- Inga andra nya strängar. Map-teaserns befintliga strängar
  (`map_teaser_title/caption/count/cta`) återanvänds oförändrade.

## Verifiering

- **Animation unit-testas inte** (lågt värde). Följer befintligt mönster:
  `ShimmerBorderTest` testar bara ren alpha-matematik. `premiumGlow` delegerar
  till `shimmerSweep` och inför ingen ny matematik → ingen ny unit-test behövs
  för själva glöden.
- **Befintliga tester gröna:** `./gradlew build` + `ktlintCheck detekt`. Om
  `LockedStatsPreview`/CTA-strängar ändras, säkerställ att eventuella tester som
  refererar dem uppdateras.
- **On-device-verifiering (primär)** på SM-S918B med **debug-premium-OFF**
  (`DebugPremiumOverrides`): besök som gratisanvändare
  1. Karta-tab → glödande kort,
  2. Mina arter → säsongsstat-rutan glänser och känns *inte* låst (litet
     Premium-pill istället för stort lås, mjukare sudd, ny CTA),
  3. en artprofil → teaser-kortet glänser,
  4. Arkiv → teaser-kortet glänser,
  5. Inställningar → hero-kortet glänser med samma diskreta takt som övriga.
  Bekräfta att glöden är subtil (inte skrikig) och konsekvent i takt över alla
  ytor.

## YAGNI / ramar

- Ingen guldpalett, inga gnistror/partiklar, ingen ny bild-asset.
- Inga omdöpta komponenter.
- Ingen `shimmerBorder`/kant-glöd i denna omgång (kan toppas senare om mer lyster
  önskas — uttrycklig framtida möjlighet, inte nu).
- Glöden är rent visuell — ingen ändring i gating-logik, navigation eller
  premium-tillstånd.

## Berörda filer (förväntat)

- **Ny:** `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/PremiumGlow.kt`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/PremiumTeaserCard.kt`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/LockedStatsPreview.kt`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapPremiumTeaser.kt`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/PremiumHeroCard.kt`
- `composeApp/src/commonMain/composeResources/values/strings.xml` (+ `values-en`)
  för CTA-strängen.
