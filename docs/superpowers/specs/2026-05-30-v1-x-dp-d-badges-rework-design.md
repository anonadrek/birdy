# Spec — DP D: Märken-omarbetning (full design)

> Detaljerad design för delprojekt **D** i programmet `docs/superpowers/specs/2026-05-29-v1-x-tester-feedback-response-design.md` (avsnitt 7). Brainstormat 2026-05-30 (med visuell companion + appreferens). Adresserar Problem 3 i `docs/superpowers/research/2026-05-29-hardcore-tester-feedback.md`: *"I can't see the point of the badges."*

## 1. Bakgrund

En erfaren skådare (extern testare) klagade att märkena känns poänglösa. Kodgrundad diagnos: 60 % av gratis-märkena belönar app-vana (streaks + trivial-familj), de 8 familje-märkena har `target: 1` (dör direkt), det enda skicklighets-spåret (sällsynt) är dolt, taket är 100 arter (nybörjar-nivå), och ett audio-märke ligger felaktigt bakom Premium (BirdNET CC BY-NC-SA).

Vald ambitionsnivå (brainstorm 2026-05-30): **omdesign inom befintlig motor** — strukturera om i två nivåer, bygg om setet på data som redan finns på `Species`, balansera om gratis/premium. Inga riskabla nya data-beroenden.

## 2. Mål / icke-mål

**Mål:**
- Flytta tyngdpunkten från app-vana → skådar-prestation (mål: ~80 % av märkena belönar skådning, var ~40 %).
- Inga döda eller omöjliga märken.
- Avslöja special-art-spåret som ett synligt, jagat mål.
- Förläng progression för entusiasten (tak 100 → 500).
- Städa premium (ta bort krockar/döda; audio till gratis).
- Fixa "sne"-etiketten under upplåsta märken.

**Icke-mål:**
- Ingen ny content-pipeline, ingen schemaändring, inget DB-rebuild (allt finns redan på `Species`).
- Ingen nav-omstrukturering — Märken **kvar** som huvudflik (beslut N1).
- Ingen ny visuell idiom — återanvänd Field Journal-komponenterna.
- Ingen GPS-/fenologi-/regional-baserad mekanik (stub-data, se §3).

## 3. Dataverklighet (verifierad 2026-05-30 mot 839 art-YAML)

Tre signaler ur den ursprungliga research-riktningen är stubbar och får INTE användas som skicklighets-axlar:

| Signal | Verklighet | Konsekvens |
|---|---|---|
| `abundance` | **Binär:** 180 `allmän`, 659 `ovanlig`. **0 `mindre allmän`, 0 `sällsynt`.** | Dagens `rare_*` + `premium_rare_seeker` (kräver `sällsynt`) är **omöjliga att låsa upp sedan launch**. "Använd alla 4 nivåer" är ogenomförbart. |
| `regions` | Uniform: alla 839 = `[SE, NO, FI, DK, DE]`. | "Regional completion" meningslöst. |
| `season` (per art) | Stub: uniformt `present` alla arter × 12 mån (se [[project_season_data_is_stub]]). | Fenologi-märken ("vårens första") ogenomförbara. Säsong-**av-observation** (enhetens klocka) är OK. |

**Riktig, distribuerad data på `Species`** (modellen som badge-evaluatorn redan får via `speciesByQid`):
- `taxonomy.family` (97 familjer), `taxonomy.iocOrder` (27 ordningar), `taxonomy.genus`
- `iucnStatus: String` — **697 LC · 51 NE · 46 NT · 39 VU · 6 CR** → **91 rödlistade (NT/VU/CR, 11 %)** = den enda äkta "special-art"-axeln. Rödlistade arter är något svenska skådare faktiskt jagar.
- Användardata: `Observation.speciesId` (livslista), `capturedAt` (säsong-av-obs, streaks, timme), `sourceType` (audio), `note`, daily-bird-match.

**Viktigt:** `Species.kt:12` har redan `iucnStatus` och `Species.sq:5` har kolumnen → IUCN-spåret kräver **ingen** schemaändring. DP D rör enbart badge-lagret.

## 4. Designbeslut (låsta 2026-05-30)

| # | Beslut |
|---|---|
| 1 | **Två nivåer, struktur A:** "⭐ Skådar-milstolpar" (prominent, överst) + "🔁 Vanor" (nedtonad sektion längst ner). Nedtoning via **ordning + en svagare `SectionLabel`** — INGET nytt hopfäll-element (max konsekvens med befintligt UI). |
| 2 | **Livslista:** förläng 5 → 25 → 100 → **250 → 500** unika arter. |
| 3 | **Familje-mästare:** byt `target: 1` mot "se N **olika arter** ur familjen" (evaluator räknar distinkta arter, ej observationer). 6 familjer, mål skalat till familjestorlek. |
| 4 | **Sångare:** nytt multi-familj-märke (testaren klagade att sångarna göms i 378-Passeriformes-massan). |
| 5 | **Taxonomisk bredd:** nya regler — N olika familjer / N olika ordningar. |
| 6 | **Special-spåret = Rödlistade (R2):** ersätt de döda abundance-märkena med IUCN-baserade (NT/VU/CR). **Avslöja** spåret (ta bort `RARE → Hidden`). |
| 7 | **Säsong:** flytta `Året runt` (alla 4 säsonger) + `Trogen följeslagare` (samma art i 4 säsonger) från Premium → gratis. Ta bort de 4 volym-baserade `season_*`-märkena. |
| 8 | **Ljud-skådare:** flytta `song_scholar` (audio) Premium → **gratis** (licens-tvång, BirdNET CC BY-NC-SA). |
| 9 | **Premium = bara flair/hängivenhet (P1):** all progression & samling är gratis. Inga skådar-mål bakom betalvägg. Premium 13 → 7. |
| 10 | **Märken kvar som huvudflik (N1).** |
| 11 | **Onboarding scen 5:** byt `StreakCounter` → `StampTrack` (samlings-känsla, ej svit). Copy redan retonad. |
| 12 | **"Sne"-fix:** rotera bara stämpel-cirkeln i `StampSeal`, inte namn-etiketten. |

## 5. Märkes-set

### 5.1 Gratis — ⭐ Skådar-milstolpar (21 märken)

| Kategori | Märke | Regel | Mål |
|---|---|---|---|
| PROGRESSION | Livslista ×5 | `count_unique_species` | 5, 25, 100, 250, 500 |
| FAMILY | Änder & gäss | `observed_in_family: anatidae` (distinkt) | 15 / 53 |
| FAMILY | Vadare | `observed_in_family: scolopacidae` | 12 / 34 |
| FAMILY | Rovfåglar | `observed_in_family: accipitridae` | 12 / 38 |
| FAMILY | Finkar | `observed_in_family: fringillidae` | 12 / 41 |
| FAMILY | Mesar | `observed_in_family: paridae` | 6 / 10 |
| FAMILY | Ugglor | `observed_in_family: strigidae` | 8 / 22 |
| FAMILY | Sångare | `observed_in_family_group` (phylloscopidae, acrocephalidae, sylviidae, cettiidae, locustellidae, cisticolidae) | 15 / 70 |
| BREADTH | Familjespanare ×2 | `count_distinct_families` | 20, 50 / 97 |
| BREADTH | Ordnings-resenär | `count_distinct_orders` | 20 / 27 |
| REDLISTED | Rödlistad ×3 | `observed_red_listed` (NT/VU/CR, distinkt) | 1, 5, 15 / 91 |
| SEASON | Året runt | `observed_in_all_seasons` | 1 |
| SEASON | Trogen följeslagare | `species_across_seasons: 4` | 1 |
| AUDIO | Ljud-skådare | `audio_observation_count` | 5 |

### 5.2 Gratis — 🔁 Vanor (5 märken, nedtonat)

| Kategori | Märke | Regel | Mål |
|---|---|---|---|
| STREAK_WEEKLY | Vecko-svit ×3 | `weekly_streak` | 4, 12, 52 |
| STREAK_MONTHLY | Månads-svit ×2 | `monthly_streak` | 3, 12 |

### 5.3 Premium — flair/hängivenhet (7 märken)

Behålls (riktig data, ej audio): `premium_field_member` (manuell), `premium_dawn_chorus` (5 fynd före kl 6), `premium_early_pilgrim` (fynd 05–07), `premium_field_journalist` (25 anteckningar ≥30 tecken), `premium_sunday_birder` (4 söndagar), `premium_daily_bird_hunter` (dagens fågel ×3), `premium_winter_wanderer` (10 vinterfynd).

**Tas bort:** `premium_archive_curator` (=gratis 100), `premium_lifelist_legend` (=gratis 250), `premium_migration_mapper` (=gratis Trogen följeslagare), `premium_seasonal_steward` (=gratis Året runt), `premium_rare_seeker` (dött, 0 sällsynt-arter). `premium_song_scholar` flyttas till gratis (§5.1, Ljud-skådare).

> `premium_dawn_chorus` + `premium_early_pilgrim` överlappar (tidiga morgnar) — valfri sammanslagning till en stege finaliseras i planen.

**Totalt:** 26 gratis + 7 premium = **33 märken** (var 25 + 13 = 38).

## 6. BadgeRule — ändringar

**Ändrad semantik (befintlig typ):**
- `ObservedInFamily` — evaluator räknar nu **distinkta arter** (`observations.mapNotNull{speciesId}.distinct()` filtrerat på familj), ej observationer.

**Nya typer (`shared/domain/.../BadgeRule.kt`):**
- `ObservedInFamilyGroup(families: Set<String>, target)` — distinkta arter vars `taxonomy.family ∈ families`.
- `CountDistinctFamilies(target)` — antal distinkta `taxonomy.family` bland observerade arter.
- `CountDistinctOrders(target)` — antal distinkta `taxonomy.iocOrder`.
- `ObservedRedListed(target)` — distinkta arter med `iucnStatus ∈ {"NT","VU","CR"}`.

**Återanvänds oförändrat:** `CountUniqueSpecies`, `WeeklyStreak`, `MonthlyStreak`, `ObservedInAllSeasons`, `SpeciesAcrossSeasons`, `AudioObservationCount`, `Manual` + premium-flair-typerna (`ObservedBeforeHour`, `ObservedInHourRange`, `SundayStreak`, `DailyBirdMatches`, `ObservationsWithNote`, `ObservedInSeason`).

**Blir oanvänd (städa):** `ObservedWithAbundance` + `BadgeAbundance` + `mapAbundance()` — inget märke använder dem efter R2. Ta bort rule-typ, evaluator-gren, enum och YAML-nyckeln `observed_with_abundance`. (Verifiera inga andra referenser innan borttag.)

Evaluator-grenar (`RecalculateBadgesUseCase.rawValue`) läggs till för de 4 nya/ändrade reglerna. `BadgeCatalogLoader` får parsing för nya YAML-typerna (`observed_in_family_group` med `families: [...]`, `count_distinct_families`, `count_distinct_orders`, `observed_red_listed`).

## 7. BadgeCategory + tier-struktur

Lägg till **`tier: BadgeTier`** (`MILESTONE` / `HABIT`) på `BadgeCategory` så `BadgesScreen` kan gruppera i två sektioner utan ny logik i datalagret.

| Kategori | tier | order |
|---|---|---|
| PROGRESSION | MILESTONE | 0 |
| FAMILY | MILESTONE | 1 |
| BREADTH (ny) | MILESTONE | 2 |
| REDLISTED (ersätter RARE) | MILESTONE | 3 |
| SEASON | MILESTONE | 4 |
| AUDIO (ny) | MILESTONE | 5 |
| STREAK_WEEKLY | HABIT | 6 |
| STREAK_MONTHLY | HABIT | 7 |

`RARE` byts till `REDLISTED` (eller behålls som namn men semantiken = rödlistad). `AUDIO` = en-märkes-kategori för Ljud-skådare (acceptabelt; alternativt fold in i PROGRESSION — finaliseras i plan).

## 8. UI-ändringar

- **`BadgesScreen.kt` (`LoadedContent`):** dela den lockade listan i två grupper på `badge.category.tier`. Rendera en `SectionLabel("SKÅDAR-MILSTOLPAR")` före MILESTONE-rutnätet och en svagare `SectionLabel("VANOR")` (t.ex. `MarginaliaInk.copy(alpha=0.55f)`) före HABIT-rutnätet, längst ner. Återanvänd befintligt 3-kol `LazyVerticalGrid` + `BadgeGridCell`. Premium-sektionen oförändrad i botten.
- **`StampSeal.kt` ("sne"-fix):** `-3°`-rotationen ligger idag på ytter-`Column`:en (rad ~127) som rymmer både cirkeln och namn-`Text`:en → texten lutar. Flytta `.rotate(state.rotationDegrees())` till **endast stämpel-`Box`:en** (`sealModifier`), så cirkeln lutar (avsiktligt handstämplat) men namnet står rakt. Verifiera att InProgress/Locked (rotation 0f) är oförändrade. Semantics-rotation påverkar inte a11y.
- **`SceneBadges.kt`:** ersätt `StreakCounter(target = 7, …)` med en `StampTrack`-rad (några fyllda + tomma stämplar) — samlings-känsla i stället för svit. `StampSeal`-flip-animationen kan behållas. Importera bort `StreakCounter` om oanvänd.
- **Nav:** ingen ändring (N1).

## 9. Strängar (compose-resources, BÅDA `strings.xml`)

- Nya namn + beskrivningar för alla nya/ändrade märken i `values/strings.xml` (SV-default) **och** `values-en/strings.xml`, plus `BadgeStringMap.kt`-mappning (id → `StringResource`).
- Ta bort strängar för borttagna märken (`rare_first/5/10`, `season_*`, borttagna premium-ids, borttagna familjer corvidae/turdidae/sylviidae(enskild)/picidae).
- Aldrig hårdkoda lokaliserad text. `%`-escape: passa förformaterade strängar från Kotlin (ej `%%`); raw `'`/`’` (ej `\'`).
- Nya sektionsrubriker: `badges_section_milestones`, `badges_section_habits` ×2 locale.

## 10. Migration / unlock-persistens

- **Borttagna märken** (dött + krock + borttagna familjer): deras `BadgeUnlock`-rader blir föräldralösa. `catalog.findById` → null → filtreras redan bort i `recentlyUnlocked` (`mapNotNull`) och `locked` (saknas i katalogen). **Ingen migration behövs.**
- **`unlockedCount`** måste filtreras till katalog-ids så föräldralösa rader inte blåser upp räknaren/`StampTrack` — ändra i `BadgesViewModel.buildLoaded` (`unlocks.count { catalog.findById(it.badgeId) != null }`).
- **Ändrade familje-märken** (samma id, nytt mål + distinkt-semantik): redan upplåsta rader behålls (vi re-lockar aldrig). En användare som låste `family_paridae` på `target:1` behåller märket. Accepterat — förtjänade märken förblir förtjänade.
- **Stamp-nummer** (`№`) härleds ur katalog-ordning → kan flytta sig kosmetiskt. OK.

## 11. Cross-cutting bivillkor (från program-spec §3)

1. Alla UI-strängar via compose-resources i båda `strings.xml`.
2. Kommunicera aldrig accuracy-siffror.
3. Inget audio bakom Premium (löses här: Ljud-skådare → gratis).
4. **Ingen content-DB-schemaändring** → ingen fingeravtryck-risk (gäller ej DP D).
5. `:androidApp` transitiva deps: inga nya shared-referenser förväntas (allt i `:composeApp` + `:shared:domain`).
6. Default-värden på ev. nya summary-/modellfält så test-fixturer inte bryts. Verifiera audit-/research-påståenden mot koden före edit.

## 12. Komponenter (filer)

- `composeApp/.../composeResources/files/badges.yaml` — bygg om gratis-setet (§5.1–5.2).
- `composeApp/.../composeResources/files/premium_badges.yaml` — trimma till 7 (§5.3).
- `shared/domain/.../badge/BadgeRule.kt` — 4 nya/ändrade typer (§6).
- `shared/domain/.../badge/BadgeCategory.kt` — `tier` + BREADTH/REDLISTED/AUDIO (§7).
- `composeApp/.../badges/RecalculateBadgesUseCase.kt` — evaluator-grenar; familj = distinkta arter; ta bort abundance-gren.
- `composeApp/.../badges/BadgeCatalogLoader.kt` — parsa nya YAML-typer.
- `composeApp/.../ui/badges/BadgesViewModel.kt` — ta bort `RARE → Hidden`; filtrera `unlockedCount` mot katalog; tier-medveten gruppering (eller exponera tier i state).
- `composeApp/.../ui/badges/BadgesScreen.kt` — två sektioner via tier.
- `composeApp/.../ui/badges/BadgeStringMap.kt` — id → strängar.
- `composeApp/.../ui/components/StampSeal.kt` — "sne"-fix.
- `composeApp/.../ui/onboarding/scenes/SceneBadges.kt` — StampTrack i st.f. StreakCounter.
- `composeApp/.../composeResources/values/strings.xml` + `values-en/strings.xml` — namn/beskrivningar/sektioner.
- Tester: `BadgeRuleTest.kt` + use-case-tester (§13).

## 13. Tester (TDD på ren logik)

- **`RecalculateBadgesUseCase` / `BadgeRuleTest`:**
  - `ObservedInFamily` räknar distinkta arter (2 obs av samma art → 1).
  - `ObservedInFamilyGroup` summerar distinkta arter över familje-setet.
  - `CountDistinctFamilies` / `CountDistinctOrders` räknar distinkt taxonomi.
  - `ObservedRedListed` räknar distinkta arter med `iucnStatus ∈ {NT,VU,CR}`; LC/NE räknas ej.
  - `currentValue` klampas mot target; `newUnlocks` triggar vid `>= target`.
- **Katalog:** `BadgeCatalogLoader` parsar alla nya YAML-typer; gratis-/premium-yaml laddar utan fel; inga dubbletter av id.
- **ViewModel:** REDLISTED visar progress (ej Hidden); `unlockedCount` ignorerar föräldralösa unlocks; tier-gruppering ger rätt sektioner.
- **Device-verify (SM-S918B):** märkes-skärm (två sektioner), in-progress-stämpel, upplåst stämpel med **rak** etikett, onboarding scen 5, premium-sektion. Screenshots enligt [[feedback_personal_device_verify]].

## 14. Öppna finaliserings-detaljer (för planen, ej blockerande)

- Exakta familje-/bredd-/rödlist-mål är justerbara (siffror ovan = utgångspunkt ur faktisk fördelning).
- Sammanslagning `dawn_chorus`+`early_pilgrim`.
- `AUDIO` egen kategori vs fold-in i PROGRESSION.
- Behåll enum-namnet `RARE` (semantik=rödlistad) vs döp om till `REDLISTED` (renare; kräver att hitta alla referenser).
- Exakt copy för nya namn/beskrivningar (skådar-ton, inte Duolingo).

## 15. Release

Egen versionCode-bump; shippbar v1.x-feedback-respons (DP D). Ingen DB-rebuild. Device-verify på SM-S918B. Batchas in i closed-testing-lyftet enligt [[project_v1_1_phase_a_ready]] release-strategi. Uppdatera program-spec:en med pekare till DP D:s plan.
