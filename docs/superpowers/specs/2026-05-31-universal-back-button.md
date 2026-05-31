# Universell bakåtpil — problemidentifiering + fix

> **Status:** Förslag (ej implementerat). 2026-05-31.
> **Författare:** Claude Code, på begäran av Albin.
> **Scope:** Bakåt-affordancen uppe till vänster på *alla* skärmar. Ingen funktionsändring — bara visuell enhetlighet + synlighet.

---

## TL;DR

Bakåtpilen är **inte en komponent** utan tre olika hand-rullade varianter spridda över skärmarna, och på pappers­skärmarna är den en **naken kopparfärgad glyf utan behållare** som tonalt ligger i samma varma jordfärgs­familj som pappret (`#A8552D` på `#EFE7D6`). Resultat: den läser som *dekoration/marginalia* snarare än en tryckbar knapp och "smälter in".

**Fixen:** gör om `BackButton` till **en enda universell komponent** med en *fylld* cirkel­behållare (inte bara outline) och rulla ut den på alla 8 skärmar som idag har egen pil. En knapp, ett utseende, en position — överallt.

---

## Vad användaren sa

> "Vi vill att pilen bakåt i det vänstra hörnet på ALLA sidor ska vara universell och den ska inte smälta in i designen som den gör just nu."

Två separata krav:
1. **Universell** → samma pil, samma utseende, samma position på *varje* skärm.
2. **Ska inte smälta in** → den måste ha tillräcklig kontrast + en tydlig knapp-affordance så att den läser som "tryck här för att gå tillbaka", inte som en del av Field Journal-ornamentiken.

---

## Nuläge — inventering av varje bakåt-affordance

Det finns redan en central komponent (`ui/components/BackButton.kt`), men den används bara på **3 av 8** skärmar. Resten rullar sin egen pil för hand.

| Skärm | Fil | Hur pilen byggs idag | Ikon | Behållare | Tint | Position / padding |
|---|---|---|---|---|---|---|
| Scan (live-kamera) | `ui/scan/ScanScreen.kt:191` | `BackButton(variant = OnDark)` | `AutoMirrored.Filled.ArrowBack` | **Fylld cirkel** (OffwhiteWarm) + copper-ring | Copper | `TopStart`, statusBars-inset, start=12 top=8 |
| Foto-analys | `ui/photoanalyze/PhotoAnalyzeScreen.kt:136` | `BackButton(variant = OnPaper)` | Filled.ArrowBack | Transparent + copper-ring | Copper | `TopStart`, statusBars-inset, start=12 top=8 |
| Audio-scan | `ui/audio/AudioScanScreen.kt:103` | `BackButton(variant = OnPaper)` | Filled.ArrowBack | Transparent + copper-ring | Copper | `TopStart`, statusBars-inset, start=12 top=8 |
| Artprofil | `ui/profile/SpeciesProfileScreen.kt:122` | naken `IconButton` | Filled.ArrowBack | **ingen** | Copper | `TopStart`, top=24 start=12 |
| Observationsdetalj | `ui/diary/ObservationDetailScreen.kt:188` | naken `IconButton` | Filled.ArrowBack | **ingen** | Copper | `TopStart`, top=24 start=12 |
| Settings | `ui/settings/SettingsScreen.kt:369` | naken `IconButton` i `Row` | **Outlined**.ArrowBack | **ingen** | Copper | `Row`, start=4 top=8 |
| About | `ui/settings/AboutScreen.kt:108` | naken `IconButton` i `Row` | **Outlined**.ArrowBack | **ingen** | Copper | `Row`, start=4 top=8 |
| Säsongsstatistik | `ui/stats/SeasonStatsScreen.kt:101` | naken `IconButton` i `Row` | **Outlined**.ArrowBack | **ingen** | Copper | `Row`, start=4 top=8 |

**Närliggande men separat metafor** (stänga, inte gå bakåt — utanför detta scope men noteras):

| Skärm | Kontroll | Ikon | Tint |
|---|---|---|---|
| Premium | `PremiumScreen.kt:161` `onClose` | `Outlined.Close` (X) | MarginaliaInk |
| Archive (meny) | `ArchiveScreen.kt:152` | `MoreVert` (kebab, inte back) | Copper |

### Vad inventeringen avslöjar

Tre oberoende inkonsekvenser, ovanpå synlighetsproblemet:

1. **Ikon-mismatch:** 5 skärmar använder `Filled.ArrowBack`, 3 använder `Outlined.ArrowBack`. Outline-pilen är tunnare → syns ännu sämre.
2. **Behållar-mismatch:** 1 skärm har fylld cirkel (Scan/OnDark), 2 har tom ring (OnPaper), 5 har *ingen* behållare alls.
3. **Position/storlek-mismatch:** tre olika top-paddings (`8`, `24`, + statusBars-inset) och två storlekar (`BackButton` = 26dp box/14dp ikon; naken `IconButton` = 48dp touch/24dp ikon). Pilen "hoppar" alltså i position och storlek när man navigerar mellan skärmar.

---

## Root-cause: varför den smälter in

Det är **två problem**, inte ett:

### Problem A — låg *kromatisk* separation (inte bara luminans)
- `AccentCopper` = `#A8552D` (varm, jordig orange-brun, medel­mättnad).
- `PaperBg` = `#EFE7D6` (varm gräddvit).
- Luminanskontrast ≈ **4.3:1** — det klarar precis WCAG 1.4.11 (3:1 för icke-text/grafiska objekt), så det är *inte* primärt ett ren-kontrast-fel.
- Men båda färgerna ligger i **samma varma hue-familj**. Ögat får ingen färg-"pop"; pilen läser som en ton-på-ton-detalj. Det är exakt känslan av "smälter in".

### Problem B — ingen behållare = ingen knapp-affordance
- På de 5 pappersskärmarna är pilen en **fristående glyf** utan ram, fyllning eller skugga.
- I Field Journal-språket finns redan massor av kopparfärgade fristående element (`OrnamentRule` ❦, copper-pills, StampSeal-ringar, marginalia). En ensam kopparpil ser ut som **ännu ett ornament**, inte en kontroll.
- Även `BackButton`-komponentens `OnPaper`-variant har `Color.Transparent` som fyllning → bara en ring → fortfarande svag affordance på papper.

> Kärnan: en *fristående lågkromatisk glyf* i ett tema fullt av dekorativa kopparglyfer kan inte annat än smälta in. Lösningen är en **fylld, avgränsad behållare** som bryter mot pappret + **enhetlig form överallt**.

---

## Fixen

### 1. Gör `BackButton` till EN universell komponent med fylld behållare

En knapp som funkar lika bra på papper som på mörk kamera-bakgrund. Nyckeländringen: `OnPaper`-varianten får en **solid fyllning** (varm offwhite) i stället för transparent, så cirkeln alltid bryter av mot bakgrunden — precis som `OnDark` redan gör.

```kotlin
// ui/components/BackButton.kt  (förslag)
@Composable
fun BackButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    variant: BackButtonVariant = BackButtonVariant.OnPaper,
) {
    val (fill, ring, icon) = when (variant) {
        // Papper: solid offwhite-bricka + copper-ring + copper-pil.
        // Bryter av mot pappret → läser som knapp, inte ornament.
        BackButtonVariant.OnPaper -> Triple(OffwhiteWarm, AccentCopper, AccentCopper)
        // Mörk kamera-bakgrund: oförändrad (redan hög kontrast).
        BackButtonVariant.OnDark  -> Triple(OffwhiteWarm, AccentCopper, AccentCopper)
    }
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)                         // ≥40dp touch-target (a11y)
            .clip(CircleShape)
            .background(fill)
            .border(1.5.dp, ring, CircleShape),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,  // EN ikon överallt
            contentDescription = contentDescription,
            tint = icon,
            modifier = Modifier.size(20.dp),
        )
    }
}
```

**Fyllningsfärg — LÅST 2026-05-31: (a) `OffwhiteWarm` (`#FFFCF0`).** Diskret "bricka" som håller sig i den ljusa paletten men ger en tydlig behållare. (Förkastat alternativ: (b) `StampNavy`-blå med offwhite pil — djärvare, drog för mycket fokus.) Koden i §1 ovan reflekterar redan (a).

### 2. Standardisera storlek + position överallt

| Egenskap | Värde |
|---|---|
| Touch-target | **40dp** (cirkel) |
| Ikon | **20dp**, `AutoMirrored.Filled.ArrowBack` |
| Hörn | `TopStart` |
| Padding | `start = 12dp`, `top = 8dp` |
| Inset | `windowInsetsPadding(WindowInsets.statusBars)` på full-screen-skärmar (Scan/Foto/Audio/Profil/Detalj); i `Row`-topbars (Settings/About/Stats) ärver den befintliga top-bar-insetten |

### 3. Ersätt alla 5 nakna pilar med komponenten

| Fil | Före | Efter |
|---|---|---|
| `SpeciesProfileScreen.kt` | naken `IconButton` + Filled.ArrowBack | `BackButton(variant = OnPaper, …)` |
| `ObservationDetailScreen.kt` | naken `IconButton` + Filled.ArrowBack | `BackButton(variant = OnPaper, …)` |
| `SettingsScreen.kt` (TopBar) | naken `IconButton` + Outlined.ArrowBack | `BackButton(variant = OnPaper, …)` i samma `Row` |
| `AboutScreen.kt` (TopBar) | naken `IconButton` + Outlined.ArrowBack | `BackButton(variant = OnPaper, …)` i samma `Row` |
| `SeasonStatsScreen.kt` (TopBar) | naken `IconButton` + Outlined.ArrowBack | `BackButton(variant = OnPaper, …)` i samma `Row` |

`PhotoAnalyzeScreen` och `AudioScanScreen` använder redan `BackButton(OnPaper)` → de får den fyllda behållaren **gratis** av ändring (1), ingen call-site-edit behövs. `ScanScreen` (`OnDark`) är oförändrad.

> **Topbar-detalj:** I `Row`-topbarsen (Settings/About/Stats) sitter pilen bredvid en titel. Den fyllda 40dp-cirkeln är något "tyngre" än dagens nakna pil — kontrollera att `Spacer(4.dp)` + titel-baseline fortfarande ser balanserade ut; ev. justera spacer till 8dp. Verifieras på enhet.

---

## Avgränsningar & öppna frågor

1. **Premium (`onClose`) + Archive (kebab)** är *inte* bakåt-pilar — de är stäng-/meny-metaforer. **Förslag: lämna utanför** detta scope. (Premium-X:en kan ärva samma behållar-tänk i en separat liten följd-fix om vi vill, men det är en annan komponent.) → *Bekräfta: rör vi dem nu eller inte?*
2. ~~**Fyllningsfärg (a) offwhite vs (b) navy**~~ → **LÅST: (a) offwhite.** Se §1.
3. **Tab-rotskärmar** (Lifelist/Listen/Badges/Identify-flikarna) har ingen bakåtpil eftersom de är top-level i bottom-nav — korrekt, de ska *inte* ha en. Detta scope rör bara push:ade del-skärmar.
4. **`BackButtonVariant`** blir i praktiken identisk för OnPaper/OnDark efter fixen (båda fyllda offwhite). Vi kan behålla enum:en för framtida flexibilitet, eller förenkla bort den. → mindre städ-beslut vid implementation.

---

## Acceptanskriterier

- [ ] Exakt **en** komponent (`BackButton`) renderar bakåtpilen på samtliga 8 del-skärmar.
- [ ] Samma ikon (`Filled.ArrowBack`), storlek (40dp/20dp), behållare (fylld cirkel + ring) och `TopStart`-position överallt.
- [ ] Inga nakna `Icons.*.ArrowBack` + `IconButton` kvar i skärm-koden (grep ger 0 träffar utanför `BackButton.kt`).
- [ ] Pilen läser tydligt som en knapp mot pappersbakgrunden — verifierat på SM-S918B med skärmdumpar från Profil, Observationsdetalj, Settings, About, Säsongsstatistik.
- [ ] Ingen regression på de mörka kamera-skärmarna (Scan/Foto/Audio).
- [ ] `./gradlew :composeApp:testDebugUnitTest ktlintCheck detekt` grönt.

---

## Implementationsskiss (om/när vi kör)

Litet, mekaniskt, inga nya beroenden:

1. Uppdatera `BackButton.kt` enligt §1 (fylld OnPaper + 40dp/20dp).
2. Byt ut de 5 nakna pilarna mot `BackButton(...)` + ta bort nu oanvända `ArrowBack`/`IconButton`-imports.
3. `ktlintFormat` + bygg + `installDebug` + device-verify-skärmdumpar.
4. Liten version-bump enligt projektets vana (versionCode++/versionName-suffix) + commit på nuvarande branch `feat/v1.2-phase-b-weekly-recap` eller egen liten branch — *Albin avgör om det batchas in i pågående v1.2-arbete eller står själv.*

Uppskattning: ~1 komponentändring + 5 call-site-swaps + device-verify. En kort session.
