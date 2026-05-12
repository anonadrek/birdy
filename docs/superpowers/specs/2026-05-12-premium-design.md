# Premium Tier — Design Spec

> Introducerar en premium-prenumeration i Birdy v1 med en dedikerad Premium-skärm, ny Settings-flik (åtkomlig via gear-ikon), cold-start-modal, och konsekventa per-tab markörer. Allt i Field Journal-estetik. Billing-integration är **utanför scope** för denna spec — UI:t kallar ett stub-`PremiumRepository` som vid release-build sätter premium-state lokalt.

**Datum:** 2026-05-12
**Status:** Brainstormad och godkänd. Implementationsplan skrivs härnäst.
**Pipeline-position:** Efter Plan 7c (Field Journal redesign) och Plan 7d (Match-flow). Numreras som **Plan 7e**.

---

## 1. Varför

Birdy v1 är planerad som "Skanna & lär + uppslagsverk". För att vara en livskraftig produkt över tid behöver appen en monetiseringsmodell. Användaren har inspirerats av en annan app ("Bird Sound Identify") där:

1. Premium känns lättåtkomligt utan att blockera kärnfunktioner.
2. Settings är en naturlig hub för konto, info, och premium-upsell.
3. Premium-skärmen är en visuell milstolpe — bild + tydlig värdeproposition + två tiers.

Mål med denna spec: ta in samma struktur men översätta den till Field Journal-estetiken så att den känns som *Birdy*, inte som en generisk SaaS-funnel. Premium ska kännas som *medlemskap i fältornitologernas sällskap*, inte som *Pro Tier Unlocked*.

## 2. Goal

Lägg till en komplett premium-yta i appen som:

- **Auto-presenterar** sig vid cold-start (efter onboarding, max 1×/dag).
- **Är åtkomlig** från en gear-ikon i Listen-launcher + Badges-hero som öppnar en helt ny Settings-skärm.
- **Markeras** konsekvent över alla flikar (Lyssna / Arkiv / Lifelist / Märken / Art-profil) via en återanvändbar `PremiumTeaserCard`-komponent.
- **Är visuellt distinkt** — paper-bg, DM Serif Italic + Caveat, AccentCopper accenter, stämpel-motiv. Inga banner-ad-skrik.
- **Förbereder** för billing utan att implementera den. Release-build kallar ett stub-`PremiumRepository` som markerar premium lokalt; verklig billing levereras i separat plan.

## 3. Premium-skärm (`PremiumScreen`)

### 3.1 Layout

Layout-riktning: **A Plate Frame**. Lockad efter visuell brainstorm i `.superpowers/brainstorm/.../premium-a-v3-sv-en.html`.

```
[Status bar]
[← top-left close]                            [✕ top-right close]
─── ❦ ───

[PlateFrame med naturalist-foto av talgoxe (Wikimedia Commons)]
   ┌──❦────────────────❦──┐
   │  [bird photo]         │
   │              Pl. 042  │
   └───────────────────────┘

[Headline · DM Serif Display Italic 30sp + Caveat-accent]
Hela året som *fältornitolog.*               (SV)
A *field birder's* year.                       (EN)

[Sub-line · Caveat Regular 16sp · MarginaliaInk]
— Stöd Birdy och få fler verktyg i fält. —

─── ❦ ───

[4 features · ✓-stamp + label]
  ✓  Lyssna på fågelsång
  ✓  Exportera fältdagbok som PDF
  ✓  Säsongs-statistik & årsöversikt
  ✓  Fältmärken (10 premium-stämplar)

─── ❦ ───

[Tier 1 · selected by default · 2px copper border + paper-edge bg]
  1 år                  199 kr / år
  Spara 60%             ~17 kr / mån

[Tier 2 · outline]
  För alltid            499 kr · engångsköp

[Primary CTA · AccentCopper-pill]
  Fortsätt                                       (SV)
  Continue                                       (EN)

[Sub-CTA caveat under pill]
  Avbryt när som helst.
```

### 3.2 Innehåll

| Element | Svenska | Engelska |
|---|---|---|
| Headline | `Hela året som *fältornitolog.*` | `A *field birder's* year.` |
| Sub-line | `Stöd Birdy och få fler verktyg i fält.` | `Support Birdy and get more tools in the field.` |
| Feature 1 | `Lyssna på fågelsång` | `Listen to bird song` |
| Feature 2 | `Exportera fältdagbok som PDF` | `Export field journal as PDF` |
| Feature 3 | `Säsongs-statistik & årsöversikt` | `Seasonal statistics & yearly overview` |
| Feature 4 | `Fältmärken (10 premium-stämplar)` | `Field marks (10 premium stamps)` |
| Tier 1 title | `1 år` | `1 year` |
| Tier 1 price | `199 kr / år` | `199 kr / year` |
| Tier 1 sub | `Spara 60% · ~17 kr / mån` | `Save 60% · ~17 kr / mo` |
| Tier 2 title | `För alltid` | `Lifetime` |
| Tier 2 price | `499 kr · engångsköp` | `499 kr · one-time` |
| Primary CTA | `Fortsätt` | `Continue` |
| Sub-CTA | `Avbryt när som helst.` | `Cancel anytime.` |
| Top-right close | (✕) | (✕) |

### 3.3 Bild-källa

Hero-fotot använder en talgoxe (`Q25485` / `Parus major`) från Wikimedia Commons. För v1 paketeras bilden lokalt under `composeApp/src/commonMain/composeResources/files/premium/great-tit-hero.jpg` (~120 KB JPEG, 800px bred). Källans `licence`, `licence_url` och `author` läses från befintlig hero-image-metadata i `shared/content/species/paridae/Q25485.yaml` (content-pipeline har redan dessa fält). En liten Caveat-attribution renderas under bilden i formen `Foto: Wikimedia / {author} · {licence}` med faktisk data hämtad från Q25485-yamlen vid build time.

### 3.4 Entry-points

`PremiumScreen` kan nås från fyra håll:

1. **Cold-start-modal** (post-onboarding, 1×/dag) — se §5.
2. **Settings-hero-card** — tap någonstans på heron öppnar skärmen.
3. **Per-tab `PremiumTeaserCard`** — tap "→ Lås upp" öppnar skärmen.
4. **Listen-launcher locked Audio-ID-card** — befintlig "PREMIUM"-pill leder hit.

Skärmen är en `AppRoute.Premium` route (ej modal-overlay) så att navigation-back fungerar konsekvent. När den **presenteras som auto-popup vid cold-start** är den fortfarande en route, men presenteras ovanpå Listen-launcher (peek-bg via dimmad backdrop, se §5).

## 4. Settings-skärm (`SettingsScreen` — total rewrite)

### 4.1 Layout

Layout-riktning lockad efter `.superpowers/brainstorm/.../settings-v1.html`. Helt ny mot nuvarande Mossbädd-version.

```
[← Top-bar med DM Serif Italic-titel "Inställningar" / "Settings"]

[Premium-hero-card · 16:9 talgoxe-foto + dark gradient overlay]
   ┌─────────────────────────────────────┐
   │  [bird photo · darkened]            │
   │                                     │
   │  Bli *fältmedlem.*                  │ (DM Serif Italic + Caveat)
   │  Stöd Birdy & lås upp alla verktyg. │ (Caveat)
   │                       [Premium →]   │ (vit pill, copper text)
   └─────────────────────────────────────┘

─── ❦ ───

[MicroLabel · KONTO]
┌──── paper-card ────┐
│  ⊙  Namn      Albin     ›  │
│  ─── dashed divider ───   │
│  ⊙  Språk     Svenska   ›  │
└────────────────────┘

[MicroLabel · OM BIRDY]
┌──── paper-card ────┐
│  ⊙  Betygsätt Birdy        ›  │
│  ⊙  Dela appen             ›  │
│  ⊙  Feedback               ›  │
│  ⊙  Om · v0.7.0e           ›  │
└────────────────────┘

[MicroLabel · JURIDISKT]
┌──── paper-card ────┐
│  ⊙  Integritetspolicy      ›  │
│  ⊙  Användarvillkor        ›  │
└────────────────────┘

— Birdy är ett fält-prospekt. —    (Caveat-footer, centered)
```

### 4.2 Rader

| Sektion | Rad | Action |
|---|---|---|
| Konto | Namn | Open inline-edit-bottom-sheet (befintlig pattern från Plan 7a Onboarding) |
| Konto | Språk | Open language-picker-bottom-sheet (Svenska / English) |
| Om Birdy | Betygsätt Birdy | Open Play Store-intent (`market://details?id=se.birdy.android`) |
| Om Birdy | Dela appen | Share-intent med deeplink-stub |
| Om Birdy | Feedback | Mailto-intent till user-konfigurerad address (default: `feedback@birdy.se`-placeholder, **dokumentera som "kommer i Plan 6"** om address inte är klar) |
| Om Birdy | Om · vX.Y.Z | Open `AboutScreen` (befintlig — version + credits + open-source-licenser) |
| Juridiskt | Integritetspolicy | Open `LegalScreen` med markdown från `composeResources/files/legal/privacy_<locale>.md` |
| Juridiskt | Användarvillkor | Open `LegalScreen` med markdown från `composeResources/files/legal/terms_<locale>.md` |

**Notera:** "Betygsätt Birdy" / "Dela appen" / "Feedback" är **nya** rader som inte fanns i den gamla Mossbädd-settings. Mailto-feedback använder `feedback@birdy.se` som platshållare — implementation-planen flaggar detta för uppdatering före Play Store-release.

### 4.3 Visuella detaljer

- **Bakgrund:** `Modifier.paperBackground()` (befintlig).
- **Hero-card:** rounded 18dp, height 160dp, foto med `ContentScale.Crop` + svart gradient `0→0.7 alpha` bottom 60%, text-stack bottom-aligned med 16dp padding. Vit pill med `AccentCopper`-text, Caveat 18sp.
- **MicroLabel-headers:** Inter 10sp, weight 600, letterSpacing 0.16em, uppercase, `MarginaliaInkSoft` (#7A6E55).
- **Paper-cards:** `PaperEdge` bg (#E5DCC7), rounded 14dp, internal padding 0dp (raderna styr sin egen padding), 1px border `MarginaliaInk` @ 10%.
- **Rader:** height 56dp, padding 14dp horisontellt. Vänster: round 32dp icon-circle med 1.5px AccentCopper-outline (transparent fill). Mitten: label (Inter 14sp) + optional value (Inter 13sp `MarginaliaInkSoft`). Höger: chevron `›` AccentCopper 18sp.
- **Dashed divider:** 1px dashed `MarginaliaInk` @ 18%, full bredd.
- **Caveat-footer:** centered, 14sp, `MarginaliaInkSoft`, padding-top 24dp.

### 4.4 Top-bar

`Settings` titel i DM Serif Italic 22sp på paper-bg. Vänster: `←` AccentCopper. Inga övriga actions (gear öppnar redan denna skärm).

## 5. Entry-flow & cold-start-modal

### 5.1 Triggers

`PremiumScreen` presenteras automatiskt vid app-start om **alla** följande är sant:

1. Användaren har slutfört första-gångs-onboardingen (befintlig DataStore-flag `onboarding_complete: Boolean`).
2. Premium-state är inte aktiv (`premium_state.tier == FREE`).
3. Senaste auto-visning var **inte** dagens datum (`premium_modal_last_shown_date != today`).

Om alla tre stämmer: presentera `PremiumScreen` ovanpå Listen-launcher (peek-bg, se §5.2). Vid presentation skrivs `premium_modal_last_shown_date = today` i DataStore.

### 5.2 Presentationsdetaljer

- **Bakgrund:** Listen-launcher renderas under, med ett `Modifier.background(Color.Black.copy(alpha=0.35f))`-lager mellan. Detta är "peek-bg"-effekten — användaren ser kanten av sin app, vilket signalerar att premium är overlay, inte tvångsroute.
- **Animation:** `slideInVertically` från botten (300ms) + `fadeIn` (200ms) för premium-skärmen. Backdrop fadar in samtidigt.
- **Dismiss:** ✕ top-right stänger modalen och visar en **Caveat-toast** (snackbar med custom style) i 4 sek:
  - SV: `Hittas i Inställningar →`
  - EN: `Find it in Settings →`
- Toasten har AccentCopper-text på paper-bg, Caveat 16sp, ingen action-knapp. Den hjälper användaren förstå var de hittar premium igen.

### 5.3 Onboarding-ordning

Premium-modalen visas **efter** att första-gångs-onboardingen (3 sidor) är klar. Konkret flöde vid first-ever cold-start:

```
App-start
  → AppGate splash (befintlig)
  → OnboardingScreen (3 sidor, sätter onboarding_complete=true)
  → ListenLauncherScreen (cold-start landing)
  → 300ms efter Listen visas: PremiumScreen pop-up med peek-bg
```

På efterföljande cold-starts hoppas onboarding över, men premium-modalen kan fortfarande dyka upp (1×/dag).

### 5.4 DataStore-nycklar

Lägg till i existerande `PreferencesDataStore` (eller motsvarande):

```kotlin
val PREMIUM_STATE = stringPreferencesKey("premium_state")           // "FREE" | "YEARLY" | "LIFETIME"
val PREMIUM_PURCHASED_AT = longPreferencesKey("premium_purchased_at") // epoch millis, 0 om FREE
val PREMIUM_MODAL_LAST_SHOWN = stringPreferencesKey("premium_modal_last_shown_date") // ISO LocalDate
```

`premium_state` är en sträng (inte enum-int) så att framtida tiers (`TRIAL`, `LIFETIME_FAMILY`, …) inte kräver migration.

**Mappning DataStore → `PremiumState` sealed class:**
- `"FREE"` (eller saknat värde) → `PremiumState.Free`
- `"YEARLY"` + `purchasedAt > 0` → `PremiumState.Active(YEARLY, Instant.fromEpochMillis(purchasedAt))`
- `"LIFETIME"` + `purchasedAt > 0` → `PremiumState.Active(LIFETIME, Instant.fromEpochMillis(purchasedAt))`
- Okänd sträng → `PremiumState.Free` (defensiv fallback för forward-compat)

## 6. Gear-ikon för Settings-åtkomst

Settings nås via en **gear-ikon** uppe till höger i två skärmar:

- **ListenLauncherScreen** (top-right, samma rad som titeln)
- **BadgesScreen** (top-right, samma rad som "Märken"-titeln)

Gear-ikonen är **inte** placerad i:

- Bottom-nav (4-tab-strukturen behålls oförändrad)
- Lifelist / Arkiv / Skanna / Art-profil (för att hålla dessa skärmar fokuserade)

### Visuella detaljer

- 32dp ⊙-cirkel med 1.5px AccentCopper-outline (transparent fill).
- Inuti: ⚙-ikon, AccentCopper, 16dp.
- Tap → `AppRoute.Settings`.
- Position: end-aligned i Row, vertikal centrerad mot titeln. 18dp padding-end.

## 7. Per-tab Premium-markörer

Lockade efter `.superpowers/brainstorm/.../per-tab-markers-v1.html`. Alla markörer använder **samma 3 element**: paper-edge-bg + 1px AccentCopper-ram + "PREMIUM"-corner-flag + Caveat-italic *"→ Lås upp"*.

### 7.1 Återanvändbar komponent

```kotlin
@Composable
fun PremiumTeaserCard(
    title: String,              // DM Serif Italic, 15sp
    subtitle: String,           // Inter 11sp, MarginaliaInkSoft, line-height 1.5
    cornerLabel: String = "PREMIUM",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Renderar:
- Paper-edge-bg (rounded 14dp)
- 1px AccentCopperSoft-border
- Corner-flag uppe till vänster (-9dp top, 14dp left): AccentCopper-bg + vit text 9sp 600 letterSpacing 0.2em
- Inre padding 14dp
- Bottom-row: Caveat *"→ Lås upp"* (vänster) + chevron `›` (höger)
- Tap = onClick → `navigateTo(AppRoute.Premium)`

### 7.2 Per-tab markörer

| Tab | Markör | Plats |
|---|---|---|
| **Lyssna** | Befintlig Audio-ID locked launch-card med "PREMIUM"-pill — **oförändrad** | Listen-launcher tredje card (locked-variant) |
| **Arkiv** | `PremiumTeaserCard("Exportera & säkerhetskopiera", "PDF-fältdagbok · molnsynk · flera foton per fynd")` | Överst i ArchiveScreen, ovanför månadsgrupperna |
| **Lifelist** | Blurred "Säsongs-statistik"-sektion under entry-listan med 🔒-overlay | LifelistScreen, efter recent-entries |
| **Märken** | Dashed "Fältmärken · PREMIUM"-rad (5×1 grid av dashed stamps) + copper-pill CTA "Lås upp 10 fältmärken →" | BadgesScreen, efter "TO DISCOVER"-grid |
| **Art-profil** | `PremiumTeaserCard("Fältobservatörens insikter", "Migrationskarta · läten · djupare fält-anteckningar")` | SpeciesProfileScreen, efter description-stycket |

### 7.3 Lifelist blurred-preview-detaljer

Den enda markören som **inte** är `PremiumTeaserCard`. Renderar:

```kotlin
Box {
    Column(modifier = Modifier
        .blur(3.5.dp)
        .graphicsLayer(alpha = 0.55f)
    ) {
        Text("Vår 2026 · 23 nya arter", style = ...)
        // 6 staplar med height 30/55/80/65/45/75% — mockad data
        Row { repeat(6) { Spacer(height = it.height) } }
    }
    Column(  // overlay
        modifier = Modifier.matchParentSize()
            .background(verticalGradient(0f→0.85f PaperBg)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(🔒, AccentCopper)
        Text("Lås upp säsongs-stats", style = Caveat 18sp AccentCopper)
        Text("PREMIUM", style = MicroLabel AccentCopper)
    }
}
```

Stapeldatan är **hard-coded** i v1 — ingen real säsongs-stats-pipeline behövs ännu. När premium aktiveras visas en placeholder-skärm: "Säsongs-stats kommer i nästa uppdatering" (Caveat-text). Faktisk implementation följer i framtida plan.

### 7.4 Märken-tab-detaljer

Efter befintlig "TO DISCOVER · N LEFT"-grid lägg till:

```
[MicroLabel · FÄLTMÄRKEN · PREMIUM]

[5×1 grid av dashed-circle-stamps med ★-glyph, alla locked]

[Centered copper-pill]
   Lås upp 10 fältmärken →
```

De 10 premium-stämplarna är **inte** i ordinarie `badges.yaml` — de placeras i ny fil `composeApp/src/commonMain/composeResources/files/premium_badges.yaml` med samma schema. De renderas alltid som locked i v1; unlock-logiken är gated bakom `premium_state != FREE` men **stämplarna själva har ingen `BadgeRule`-evaluator** — de unlocks via en framtida plan (när premium-features faktiskt levereras).

Detta är medvetet: vi visar visualiseringen utan att committa till stamps som inte ännu har content.

## 8. Premium-state-arkitektur

### 8.1 `PremiumRepository`

Nytt interface i `:shared:domain`:

```kotlin
interface PremiumRepository {
    val state: StateFlow<PremiumState>
    suspend fun markPurchased(tier: PremiumTier)   // stub i v1 — sätter state lokalt
    suspend fun restore()                           // stub i v1 — re-läser från DataStore
}

sealed interface PremiumState {
    data object Free : PremiumState
    data class Active(val tier: PremiumTier, val purchasedAt: Instant) : PremiumState
}

enum class PremiumTier { YEARLY, LIFETIME }
```

Implementation i `:composeApp` (eller `:shared:data` om vi vill kunna mocka i shared-tests):

```kotlin
class DataStorePremiumRepository(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : PremiumRepository { ... }
```

### 8.2 `AppGraph`-tillägg

Lägg till i befintlig `AppGraph`-data-class:

```kotlin
data class AppGraph(
    // ... befintliga fält
    val premiumRepository: PremiumRepository,
    val premiumOverride: PremiumState? = null,   // debug-only; om != null override:ar state
)
```

`premiumOverride` är **bara** wirad i debug-builds (`MainActivity.kt` läser från en BuildConfig-flagga eller debug-meny). Release-builds passerar alltid `null`.

### 8.3 Stub `markPurchased`

I release-build kallar CTA-knappen *"Fortsätt"* så här:

```kotlin
onClick = {
    scope.launch {
        premiumRepository.markPurchased(selectedTier)  // sätter state lokalt
        navigator.popBackStack(AppRoute.Premium, inclusive = true)
        // optional: visa Caveat-toast "Välkommen, fältmedlem."
    }
}
```

Detta är **medvetet inte en riktig köp-flow**. Spec'n flaggar billing-integration som separat framtida arbete; v1-leveransen testar visuell + state-pipeline utan att kräva Google Play Billing.

## 9. Komponentinventarium

Nya komponenter (i `ui/components/`):

| Komponent | Användning |
|---|---|
| `PremiumTeaserCard` | Arkiv + Art-profil + (eventuellt) Settings (om hero-card-design bryts ut) |
| `PremiumHeroCard` | Settings-skärmens stora hero (16:9 foto + gradient + pill) |
| `LockedStatsPreview` | Lifelist blurred-säsongs-stats |
| `CaveatToast` (om inte befintlig) | Cold-start-modal-dismiss-toast |
| `GearButton` | 32dp copper-outline-cirkel med ⚙-glyph; återanvänds i Listen + Badges |

Nya skärmar (i `ui/`):

| Skärm | Route | Plats |
|---|---|---|
| `PremiumScreen` | `AppRoute.Premium` | `ui/premium/PremiumScreen.kt` |
| `SettingsScreen` (rewrite) | `AppRoute.Settings` | `ui/settings/SettingsScreen.kt` |

Modifierade skärmar:

- `ListenLauncherScreen` — gear top-right
- `BadgesScreen` — gear top-right + premium-rad
- `ArchiveScreen` — premium-card överst
- `LifelistScreen` — locked-stats-section efter entries
- `SpeciesProfileScreen` — premium-card efter description
- `OnboardingScreen` — hook för att trigga premium-modal post-completion

Data:

- `PremiumRepository` + `DataStorePremiumRepository`
- `PremiumState` / `PremiumTier` sealed types i `:shared:domain`
- DataStore-nycklar (§5.4)
- `premium_badges.yaml` (10 placeholder-stämplar, inga evaluators)

## 10. i18n & strings

Alla nya strings läggs till i `composeApp/src/commonMain/composeResources/values/strings.xml` (svenska) + `values-en/strings.xml` (engelska). Konvention: prefix `premium_*`, `settings_*`, `gear_*`.

Cirka **40 nya strings** totalt (Premium-skärm headlines/features/CTAs + Settings-sektionsetiketter + Caveat-toasts + gear-content-description). Detaljerad lista i implementations-planen.

## 11. Testning

- **UI-tests:** Snapshot-tests för `PremiumScreen` (SV + EN), `SettingsScreen` (SV + EN), `PremiumTeaserCard` (locked + active), `LockedStatsPreview`.
- **State-tests:** `DataStorePremiumRepository`-tester (markPurchased → Active, restore → läser DataStore, state-flow emits).
- **Entry-flow-test:** `EntryFlowDeciderTest` — kollar att `shouldShowPremiumModal(now, lastShown, premiumState, onboardingComplete)` returnerar rätt för alla 8 boolean-kombinationer.
- **Device-verify:** 6 screenshots på SM-S918B:
  1. Premium-skärm (SV) cold-start över Listen-launcher
  2. Premium-skärm (EN) cold-start
  3. Settings-skärm med hero
  4. Listen-launcher med gear-ikon
  5. Arkiv med premium-card överst
  6. Märken med premium-rad efter ordinarie grid

## 12. YAGNI / out of scope

Följande är **medvetet** ute för denna spec:

- **Google Play Billing-integration.** Stubb-`markPurchased` räcker för v1. Verklig billing kräver merchant-konto + Play Console-setup + edge-cases — separat plan.
- **Premium-features (faktisk funktionalitet bakom paywallen).** Säsongs-stats, PDF-export, molnsynk, Audio-ID, premium-stämplar — alla visas som locked-teasers men implementeras i framtida planer.
- **Trial / promo-koder.** Endast YEARLY + LIFETIME tiers i v1.
- **Server-side validation.** All state är lokal (DataStore). Framtida cloud-sync (v1.5) lägger till server-state.
- **A/B-testning av pricing.** Hardcoded 199 / 499 kr.
- **iOS-anpassning.** Spec gäller Android. iOS täcks när KMP-iOS-targetet lyfts (post-v1).

## 13. Beslut

| Beslut | Värde | Lockad av |
|---|---|---|
| Layout-riktning Premium-skärm | A Plate Frame | Användare |
| Pricing-struktur | Yearly + Lifetime (199 / 499 kr) | Användare |
| Cold-start-frekvens | 1×/kalenderdag | Användare (alt B) |
| Cold-start vs onboarding-ordning | Efter onboarding | Användare |
| Modal-bakgrund | Peek av Listen-launcher | Användare |
| ✕-dismiss-feedback | Caveat-toast "Hittas i Inställningar →" | Användare |
| Gear-placering | Listen-launcher + Badges top-right (inte i bottom-nav) | Användare (alt A+C) |
| Per-tab markörer | 5 markörer (Lyssna / Arkiv / Lifelist / Märken / Art-profil) | Användare |
| Extra markörer (nav-prick, onboarding-marginalia) | Skippas | Användare ("räcker mycket väl") |
| Billing-integration | Stub `markPurchased`, ingen Play Billing | Spec-författare (YAGNI) |

## 14. Referenser

- Visuella mockups: `.superpowers/brainstorm/245965-1778582509/content/`
  - `premium-a-v3-sv-en.html` — Premium-skärm slutgiltig
  - `settings-v1.html` — Settings-skärm slutgiltig
  - `entry-flow-v1.html` — Cold-start-flow
  - `per-tab-markers-v1.html` — Tab-markörer
- Design-system: `docs/superpowers/specs/2026-05-09-field-journal-refresh-design.md`
- Plan 7c-status: `~/.claude/projects/.../memory/project_plan_7c_status.md`
- Wikimedia-foto-källa: `Great_tit_(Parus_major),_North_Rhine-Westphalia.jpg` (CC-BY-SA)
