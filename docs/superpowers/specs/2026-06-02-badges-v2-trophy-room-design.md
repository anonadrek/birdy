# Badges v2 — Skådar-profil / Troférummet (designspec)

> Status: **DESIGN — godkänd i brainstorm 2026-06-02, väntar spec-review.**
> Föregående: DP D (märken-omarbetning, `2026-05-30-v1-x-dp-d-badges-rework-design.md`) lämnade märken-skärmen mer sammanhållen men valde *medvetet bort* en rikare showcase/profil (§2, §15.1). Den här specen bygger den.

## 1. Bakgrund & mål

Märken finns men "visar inte upp sig". Hardcore-testaren sa *"I can't see the point of the badges"* ([[project_hardcore_tester_feedback]]). DP D gav skådar-ton-copy och tvåsektions-rutnät, men troférummet — en plats där man **stolt bläddrar i det man vunnit** — saknas.

**Mål:** en rikare **troférum-vy** som firar upplåsta märken och lockar vidare-jakten, helt lokalt, i Field Journal-språket. Kärnkänsla (låst i brainstorm): **troférum / visa upp** — stolthet och samling, *inte* ett analytiskt stats-dashboard.

**Framgångskriterium:** från Märken-fliken kan användaren öppna ett troférum som (a) lyfter senaste trofén stort och firat, (b) visar senast upplåsta + sällsynta fynd + nära-att-låsa-upp, (c) känns omedelbart som Birdy (papper, koppar, stämplar, DM Serif/Caveat), och (d) inte kräver konto/molnsynk eller någon ny datamodell.

## 2. Icke-mål / scope-gränser

- **Ingen export/delning** den här omgången (in-app only — beslut i brainstorm). Vyn ska dock byggas så att en bild-export *kan* adderas senare utan omarbete, men flödet byggs inte nu.
- **Ingen schemaändring, ingen ny DB-kolumn, ingen ny domänregel.** Allt härleds ur befintlig märkesdata.
- **Inget dagens-fågel-kort i troférummet.** "Dagens fågel" nämndes som *exempel* på shimmer-effekten — bara beteendet/effekten återanvänds, inte själva DailyBird-kortet (beslut i brainstorm).
- **Ingen ny visuell idiom.** Återanvänder Field Journal-komponenterna. Enda tillägget är en navy-variant av stämpeln (`StampNavy` är redan en token) + en nedtonad shimmer (befintlig `Modifier.shimmerSweep`).
- **Rör inte "Alla märken"-rutnätet.** DP D:s `BadgesScreen`-rutnät (milstolpar/vanor/premium-sektion + bottom-sheets) lämnas oförändrat.
- **Säsong/fenologi används inte** ([[project_season_data_is_stub]] — säsongsdata är uniform stub).

## 3. Navigationsmodell (vald: "klick öppnar vy", som Dagens fågel)

Tre modeller utvärderades i brainstorm (flik-växel / sektion-i-scroll / klick-öppnar-vy). **Vald: klick öppnar egen vy.**

1. **Märken-fliken** (`BadgesScreen`) behålls i stort. Ett nytt **shimrande entrékort "Ditt troférum"** läggs överst — direkt under intro + `StampTrack`, ovanför `Senast upplåsta`/sektionerna. Idiomet är exakt `DailyBirdCard` (mossgrönt kort, `shimmerSweep`, klickbart).
2. Klick på entrékortet → navigerar till **`AppRoute.TrophyRoom`** — en egen fullskärmsvy med back-pil i toppen (samma mönster som `SeasonStatsScreen`).
3. `TrophyRoom` är en **flat `composable<AppRoute.TrophyRoom>`** i `AppScaffold` (syskon till `AppRoute.Badges`). För att Märken-tabben ska förbli markerad läggs `AppRoute.TrophyRoom::class` i **Badges-tabbens `ownedRoutes`** i `BottomNavBar` (samma teknik som Listen äger Scan/PhotoAnalyze/AudioScan/MatchResult). Tab-tap på Märken medan man är i TrophyRoom poppar tillbaka till Badges (befintlig `popBackStack(tab.route)`-logik).
4. Back-pil / system-back → `navController.popBackStack()` tillbaka till Märken-fliken.

**Varför inte flik-växel/sektion?** Modell 3 matchar användarens mentala bild ("som dagens fågel — något du klickar och så kommer in till kortet"), håller rutnätet orört, och ger troférummet andrum som egen yta.

## 4. Datamodell & härledning (inga nya repos/queries)

All data finns redan i `BadgesViewModel.buildLoaded(...)` → `BadgesUiState.Loaded`. Vi lägger till **ett härlett fält** och **en ren funktion**.

### 4.1 Nytt fält på `BadgesUiState.Loaded`

```kotlin
val trophyShowcase: TrophyShowcase
```

```kotlin
data class TrophyShowcase(
    val hero: BadgeWithUnlock?,                 // senaste trofén
    val recentlyUnlocked: List<BadgeWithUnlock>, // exkl. hero (för att inte dubblera)
    val rareFinds: List<BadgeWithUnlock>,        // alla upplåsta i REDLISTED-spåret, DESC
    val closeToUnlock: List<LockedBadgeProgress>,// 2–3 närmaste in-progress
)
```

### 4.2 Ren, unit-testbar härledningsfunktion

`buildTrophyShowcase(...)` (i `BadgesViewModel` eller egen fil i `ui/badges/`), anropas i `buildLoaded` med data som redan finns där (`unlocks`, `recentlyUnlocked`, `locked`, `catalog`, `stampNumbersById`):

- **hero** = `recentlyUnlocked.firstOrNull()`.
- **recentlyUnlocked** (band) = `Loaded.recentlyUnlocked.drop(1)` (hjälten visas inte två gånger).
- **rareFinds** = alla `unlocks` vars `catalog.findById(id)?.category == BadgeCategory.REDLISTED`, mappade till `BadgeWithUnlock` (med stampNumber), sorterade DESC på `unlockedAt`. *(Inte kapad till 5 — `recentlyUnlocked` är top-5, sällsynta fynd ska visa alla.)*
- **closeToUnlock** = `locked` där `state is BadgeGridState.InProgress`, sorterade DESC på `current.toFloat() / target` (target > 0), `take(3)`.

`BadgeCategory.REDLISTED` finns redan (`shared/domain/.../BadgeCategory.kt`, order 3, MILESTONE). Rödliste-spåret bekräftat i DP D (`redlisted_*` i `badges.yaml`, regel `observed_red_listed`).

### 4.3 Delad ViewModel

Både entrékortet (på Märken) och TrophyRoom-vyn läser **samma `BadgesUiState.Loaded`** via `graph.badgesViewModel()`. `TrophyRoomRoute` instansierar sin egen VM (`remember(graph) { graph.badgesViewModel() }`) precis som övriga routes — samma repo-backade hot flow, ingen delad mutbar state.

## 5. Komponenter (alla i `composeApp/.../ui/badges/`, återanvänder Field Journal)

| Komponent | Roll |
|---|---|
| `TrophyRoomEntryCard` | Shimrande mossgrönt entrékort på `BadgesScreen` (idiom = `DailyBirdCard`). Visar hjälte-№ + namn + `"{unlockedCount} troféer ›"`. Klickbart → `onOpenTrophyRoom()`. Tomt läge: "Ditt troférum väntar" + låst stämpel. |
| `TrophyRoomScreen` | Fullskärmsvyn. `JournalScaffold` + `topBar` med `BackButton` (som `SeasonStatsScreen`). `LazyColumn` med intro + de fyra banden. Tar `BadgesUiState` + callbacks. |
| `TrophyHero` | Stor `StampSeal(Unlocked)` (bar, "del av stämpeln") med **nedtonad, långsam `shimmerSweep`**. Namn + "SENAST VUNNEN" + datum. Klick → `onHeroClick(badge, unlock)` → `UnlockBottomSheet`. Tomt läge: `StampSeal(Locked)` + "din första stämpel väntar". |
| `TrophyBand` | `SectionLabel` + `LazyRow` av stämplar. Används av Senast upplåsta, Sällsynta fynd (navy), Nära att låsa upp ("X kvar"-caption). Döljs när tom. |
| `StampSeal` (utökas) | Ny param `accentColor: Color = AccentCopper`. Default = alla befintliga call sites orörda. Sällsynta fynd renderas med `StampNavy`. |
| `TrophyRoomRoute` (scaffold) | Wirar VM + `UnlockBottomSheet` (kopia av `BadgesRoute`-mönstret: hero-klick sätter `bottomSheetUnlock`, samma `UnlockBottomSheet(isCelebration=false, …)`). |

`BadgesScreen` får en ny param `onOpenTrophyRoom: () -> Unit` och renderar `TrophyRoomEntryCard` i `LoadedContent` (ny `item` överst, span = maxLineSpan). Rutnätet i övrigt orört.

## 6. Visuellt språk

- **Field Journal-tema** rakt igenom (papper-gradient, `AccentCopper`, `MarginaliaInk`, DM Serif Display Italic-rubriker via `JournalHeadline`/`JournalIntro`, Caveat-marginalia, `OrnamentRule` ❦). Hjälte-rubrik = `JournalHeadline("Ditt *troférum*.")` (accent-segmentet renderas redan i Caveat Bold, koppar, roterat −3°).
- **Stämplar:** `StampSeal` — upplåst = fylld koppar-disc med cream `№N` (Caveat), roterad −3°; sällsynt = samma men `StampNavy`; in-progress = koppar-rand + `current/target`.
- **Shimmer:** befintlig `Modifier.shimmerSweep(durationMillis, alpha, bandFraction)` — **oförändrad fil**. Anropas nedtonad och långsam: riktvärde `shimmerSweep(durationMillis = 6000, alpha = 0.20f)` (default är 3600 / 0.275) så den läser som en del av stämpeln/kortet, inte ett blänk ovanpå. Exakt värde finjusteras i device-verify. Precedens: `DailyBirdCard` + `PremiumHeroCard`.
- **Bottennav:** oförändrat. Märken-tabben förblir markerad i TrophyRoom (via `ownedRoutes`).

## 7. Tomt/tidigt läge

- **0 troféer:** hjälten = `StampSeal(Locked)` + "din första stämpel väntar"; **Nära att låsa upp** lyfts överst som lockbete; Senast/Sällsynta döljs. Entrékortet på Märken visar "Ditt troférum väntar".
- **Inga rödlistade fynd:** Sällsynta fynd-bandet döljs (ingen tom rad).
- **Inga in-progress:** Nära-bandet döljs.
- **1 trofé:** hjälte visas, Senast upplåsta (drop(1)) blir tomt → döljs.

## 8. Språk (SV + EN, `compose-resources`)

Alla nya etiketter i `values/strings.xml` + `values-en/strings.xml`. Vanliga UI-strängar (ej `badges.yaml`-bundna) → **inte** build-validerade av `ValidateBadgesYamlMain`. Nyckel-lista (namn kan finjusteras i plan):

`trophy_room_entry_eyebrow`, `trophy_room_entry_count` (`%1$s`), `trophy_room_entry_empty`, `trophy_room_entry_a11y`, `trophy_room_title`, `trophy_room_back`, `trophy_room_headline` (`Ditt *troférum*.`), `trophy_room_sub` (`%1$s av %2$s · %3$s väntar i fält`), `trophy_hero_recent_label`, `trophy_hero_empty_name`, `trophy_section_recent`, `trophy_section_rare`, `trophy_section_close`, `trophy_close_remaining` (`%1$s`), `trophy_hero_a11y`.

(Procent-escape: använd `%1$s` och passa förformaterade värden från Kotlin — undvik `%%`-regression, trap-katalogen.)

## 9. Felhantering / edge cases

- `BadgesUiState.Loading/Error` i TrophyRoom → `JournalLoading` / enkel feltext med samma mönster som `BadgesScreen`.
- Hero-klick när `unlock == null` (kan inte hända för hero eftersom hero = upplåst) → no-op-guard.
- `target == 0` i closeToUnlock-sorteringen → filtrera bort (division by zero-skydd).
- `recentlyUnlocked` tomt → hero null → tomt-läge.
- Premium-troféer: upplåsta premium-märken kan dyka upp i hero/recent om de är senast upplåsta (de finns i `unlocks`); det är OK (de är vunna troféer). Rare/close rör bara REDLISTED resp. regular locked — premium-teaser bor kvar i "Alla märken".

## 10. Testning

**Unit (`:composeApp:testDebugUnitTest`):** `buildTrophyShowcase` opererar på UI-lagrets typer (`BadgeWithUnlock`/`LockedBadgeProgress` bor i `composeApp/.../ui/badges/BadgesUiState.kt`), så funktion + test ligger i `:composeApp` — inte i domänen.

`buildTrophyShowcase`:
- hero = senaste unlock; null vid 0 unlocks.
- recentlyUnlocked-band exkluderar hero (drop(1)).
- rareFinds = endast REDLISTED, DESC på unlockedAt, ej kapad till 5.
- closeToUnlock = endast InProgress, sorterat på ratio, `take(3)`, target==0 exkluderad.
- tomma fall: 0 unlocks / 0 in-progress / 0 redlisted.

**Device-verify SM-S918B** (CLAUDE.md-process: `:androidApp:installDebug` + ADB):
- Entrékort syns överst på Märken, shimrar nedtonat, är klickbart.
- TrophyRoom öppnas som egen vy; Märken-tabben förblir markerad; back funkar.
- Alla fyra band renderar; navy-stämplar för sällsynta; "X kvar" på närbandet.
- Hero-klick → `UnlockBottomSheet`.
- Tomt läge (om driveable) eller åtminstone 1-trofé-läge.
- "Alla märken"-rutnätet oförändrat.
- Skärmdumpar till `docs/superpowers/screenshots/v1.x-badges-v2-trophy-room/`.

## 11. Risker & fällor

- **`StampSeal accentColor`-param:** default måste hålla *alla* befintliga call sites byte-identiska — verifiera genom att inga befintliga anrop ändras.
- **`ownedRoutes`-glömska:** glöms `TrophyRoom` i Badges-tabbens `ownedRoutes` tappar tabben markering i vyn. Explicit test/verify.
- **`:androidApp` transitive deps** (trap-katalog): inga nya moduler/deps införs → låg risk, men nya `composable` kräver inga nya `implementation()` (allt redan tillgängligt).
- **compose-resources `\'` / `%%`** (trap-katalog): använd raka `'`/`’` och `%1$s`.
- **Subagent detached-HEAD** ([[feedback_subagent_git_detached_head]]) om planen körs via subagent-driven-development: controllern verifierar branch-tip efter varje commit.
- Ingen `ShimmerSweep.kt`-ändring → ingen regressionsrisk för DailyBird/Premium-kort.

## 12. Filer som rörs (touch-lista)

**Nya:**
- `ui/badges/TrophyRoomScreen.kt`, `TrophyRoomEntryCard.kt`, `TrophyHero.kt`, `TrophyBand.kt`
- `ui/scaffold/TrophyRoomRoute.kt`
- `ui/badges/TrophyShowcase.kt` (data class + `buildTrophyShowcase`) — eller i `BadgesUiState.kt`/`BadgesViewModel.kt`
- Tester: `TrophyShowcaseTest.kt`

**Ändrade:**
- `ui/scaffold/AppRoute.kt` (+`TrophyRoom`)
- `ui/scaffold/BottomNavBar.kt` (Badges `ownedRoutes` +`TrophyRoom`)
- `ui/scaffold/AppScaffold.kt` (+`composable<AppRoute.TrophyRoom>`)
- `ui/scaffold/BadgesRoute.kt` (+`onOpenTrophyRoom`-navigering)
- `ui/badges/BadgesScreen.kt` (+`onOpenTrophyRoom`-param + entrékort i `LoadedContent`)
- `ui/badges/BadgesUiState.kt` (+`trophyShowcase`-fält)
- `ui/badges/BadgesViewModel.kt` (bygg `trophyShowcase`)
- `ui/components/StampSeal.kt` (+`accentColor`-param)
- `composeResources/values/strings.xml` + `values-en/strings.xml` (nya nycklar)
- `androidApp/build.gradle.kts` (versionCode +1, versionName nästa rc i v1.1-batchen)

## 13. Release-noteringar

- Landar i den outgivna **v1.1-batchen** ([[project_v1_1_release_train]]). Bumpa versionCode/name (nästa rc).
- **Store-listning + "What's new"** (CLAUDE.md follow-up #8): lägg till troférummet i listan över v1.1-ändringar vid AAB-upload.

## 14. Öppna frågor

Inga blockerande. Mindre finjusteringar (exakt shimmer-alpha/duration, exakt strängformuleringar, ev. ornament mellan band) avgörs i implementation/device-verify.
