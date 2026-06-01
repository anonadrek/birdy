# Spec — v1.x Hardcore-tester feedback-respons (program)

> Programdesign för att arbeta igenom alla 6 punkter i `docs/superpowers/research/2026-05-29-hardcore-tester-feedback.md`. Brainstormat 2026-05-29. Dekomponerat i 5 oberoende, var-för-sig-shippbara delprojekt (A–E). **Endast DP A detaljeras fullt här** (får impl-plan direkt); B–E låses på design-nivå och brainstormas var för sig (med copy-/chip-/märkes-mockups) innan sin egen plan.

## 1. Bakgrund

En erfaren skådare (extern testare) gav brutal, korrekt feedback: kategorierna är godtyckliga + inkonsekventa (alkor i "vadare"), söket hittar inte "Eleonora's Falcon", märkena känns poänglösa, och appens syfte är otydligt vs Merlin. Alla fyra punkter är verifierade mot koden (fil:rad i research-docen; nyckelpåståenden om-verifierade 2026-05-29: apostrof = U+2019, `ArchiveChip` filtrerar `ioc_order`, `SpeciesSummary.family` finns, content-DB-fingeravtryck hashar bara `id+generated_at`).

## 2. Programstruktur

| DP | Delprojekt | Täcker (research-prio) | Subsystem | Status |
|---|---|---|---|---|
| **A** | Sök-fix | Problem 2 (Prio 1) | DB/sök | **Detaljerad här → impl-plan nu** |
| **B** | Positionering & copy | Problem 4 (Prio 2 + 5) | Copy (app/onboarding/store/website/nav) | Design-nivå; egen brainstorm sen |
| **C** | Kategori-hotfix | Problem 1 Option B (Prio 3) | Encyclopedia-chips | Design-nivå; egen brainstorm sen |
| **D** | Märken-omarbetning | Problem 3 (Prio 4) | Badges | ✅ **DONE** — spec `2026-05-30-v1-x-dp-d-badges-rework-design.md` + plan `2026-05-30-v1-x-dp-d-badges-rework.md`; 16 TDD-tasks, device-verifierad SM-S918B (vC119/rc4) |
| **E** | Kategori `group`-axel | Problem 1 Option A (Prio 6) | Content-pipeline + schema | Design-nivå; content-spår, sist |

**Sekvens:** A → B → C → D → E. Varje DP är oberoende shippbar (egen versionCode-bump). A+B ger störst trovärdighetseffekt mot recensent-entusiasten; E är ett långsiktigt content-spår som ersätter C:s provisoriska chips.

## 3. Cross-cutting bivillkor (gäller ALLA delprojekt)

Hämtade ur trap-katalogen + research-docen — varje DP-plan måste hålla dem:
1. **Alla UI-strängar via `compose-resources` i BÅDA `strings.xml`** (`values/` SV-default + `values-en/`). Aldrig hårdkoda lokaliserad text. `%`-escape: passa förformaterade strängar från Kotlin (inte `%%`); använd raw `'`/`’` (inte `\'`).
2. **Kommunicera ALDRIG accuracy-siffror** (72 % top-3) i copy/store/website.
3. **Inget audio-relaterat bakom Premium** (BirdNET CC BY-NC-SA). Gäller särskilt DP D (märken) — `premium_song_scholar` (`audio_observation_count`) ska flyttas till gratis om audio-märken byggs ut.
4. **Content-DB-schemaändring MÅSTE flippa fingeravtrycket.** `SpeciesDbBuilder.contentHash` hashar bara `id+generated_at` → en ren schemaändring uppdaterar inte `application_id` → uppgraderade enheter behåller gammal DB utan ny kolumn → **krasch**. (VERIFIERAT 2026-05-29: `BirdyContent.Schema.version` är hårdkodad `1` — ingen migrations-katalog/`deriveSchemaFromMigrations` — så user_version-strategin fungerar INTE.) Lös genom att folda en manuell `SCHEMA_REV`-konstant in i `contentHash` så `application_id` flippar; befintliga `needsCopy` (`SpeciesRepositoryProvider.android.kt`) jämför redan `application_id` → ingen provider-ändring. Gäller DP A och DP E.
5. **`:androidApp` saknar transitiva deps** — varje ny shared/library-referens kräver egen `implementation()` i `androidApp/build.gradle.kts`.
6. **Verifiera audit-påståenden mot koden innan fix** (research-docen är grund, men fil:rad kan ha drivit). Default-värden på nya summary-/modellfält så test-fixturer inte bryts.

---

## 4. DP A — Sök-fix (full design)

### Mål
Söket ska hitta arter oavsett apostroftyp, diakriter och aktivt språk; dessutom familj-/genus-sök + prefix-boost.

### Rotorsaker (verifierade)
- **A.** Namn lagras med U+2019 (`Eleonora’s Falcon`, `shared/content/species/falconidae/Q212243.yaml:10`); sök-`LIKE` (`SpeciesName.sq`) matchar exakt → rak `'` ger noll träff. 74 arter drabbade.
- **B.** `SpeciesName.sq` filtrerar `WHERE sn.locale = :locale` → bara aktivt språks namn söks. Default `Locale.SV` → engelskt namn osökbart i SV-läge (och vice versa). 100 % cross-locale-miss.
- **C.** `LIKE` skiftlägesokänsligt bara för ASCII → "ruppell" hittar inte "Rüppell's" (5 arter med `ü`).

### Designbeslut (låsta 2026-05-29)
| # | Beslut |
|---|---|
| 1 | **Normalisering: `expect/actual normalizeSearch(String): String`.** Android-actual = `java.text.Normalizer` (NFD → strippa combining marks) + apostrof-folding (`’ ʼ \`` → `'`, alt. strippa) + lowercase. commonMain-`expect` deklareras; ev. iOS-actual senare. Build-tid (jvmMain) använder samma logik via `java.text.Normalizer`. |
| 2 | **Normaliserad `search_text`-kolumn** på `SpeciesName`, beräknad vid DB-build. Sök matchar `search_text LIKE %normalize(query)%`. Löser A + C. |
| 3 | **Cross-locale:** släpp `sn.locale = :locale` ur WHERE; sök båda språkens namn; dedupa på `species_id` (`distinctBy`); visningsnamn via befintligt locale-fallback. Löser B. |
| 4 | **Plus extras:** sök även familj (latin + `family_sv`) + genus → "falk"/"falcon" ger familjeträffar. Prefix-boost i `ORDER BY` (`CASE WHEN search_text LIKE :q||'%' THEN 0 ELSE 1 END`). |
| 5 | **Fingeravtryck-fix:** folda en manuell `SCHEMA_REV`-konstant in i `contentHash` (i `SpeciesDbBuilder`) så `application_id` flippar vid schemaändring → befintliga `needsCopy` ersätter cachad DB på uppgradering. (Schema.version är hårdkodad 1 → user_version duger inte; verifierat 2026-05-29.) Ingen provider-ändring. |

### Komponenter (filer)
- `shared/content/.../sqldelight/.../SpeciesName.sq` — ny kolumn `search_text TEXT NOT NULL`, index, uppdaterad `INSERT`, omskriven `searchByNameOrScientific` (matcha `search_text`/`scientific_name`/familj/genus, ta bort locale-filter, prefix-boost-`ORDER BY`).
- `shared/content/.../build/SpeciesDbBuilder.kt` (jvmMain) — beräkna `search_text = normalizeSearch(name)` vid insert. (Fingeravtryck-fixen görs i providern, se nedan — inte här.)
- `shared/content/.../build/SpeciesDbBuilder.kt` — `SCHEMA_REV`-konstant foldad in i `contentHash` (provider `SpeciesRepositoryProvider.android.kt` orörd — `needsCopy` jämför redan `application_id`).
- `shared/content/.../normalize/SearchNormalize.kt` — `expect fun normalizeSearch(input: String): String` (commonMain) + `actual` (androidMain, `java.text.Normalizer`). jvmMain-build återanvänder samma actual eller en delad jvm-impl.
- `SqlDelightSpeciesRepository.kt` — normalisera query med `normalizeSearch()` före `LIKE`; dedupa cross-locale-träffar.

### Tester
- `SpeciesRepositoryTest.kt`: "eleonora's" (rak apostrof) mot U+2019-fixtur → träff; cross-locale (engelskt namn i `Locale.SV`) → träff; "ruppell" → "Rüppell's"; familj-sök ("falk" → Falconidae-arter); prefix-boost-ordning (exakt prefix först).
- `SpeciesDbBuilderTest.kt`: `search_text` normaliseras korrekt vid build (apostrof + diakrit strippade, lowercase).
- Fingeravtrycks-test: `SCHEMA_REV`-ändring flippar `application_id` (regressionsskydd så stale DB inte behålls på uppgradering).

### Edge cases
- Tom query → alla arter (befintligt beteende, behåll).
- `scientific_name`-sök fortsätter fungera ("Falco eleonorae").
- Diakriter i query OCH i data normaliseras symmetriskt.

---

## 5. DP B — Positionering & copy (design-nivå)

**Riktning (låst):** "**keep it, not just ID it**" — Birdy = den vackra, privata fältdagboken där fyndet blir något du äger och återbesöker; identifiera med kamera/ljud, offline. Smalna målgruppen mot entusiasten/seriösa hobbyisten (recenserar + sprider). Krok mot Merlin uttalas ("Merlin är bäst på ID; Birdy handlar om vad som händer efter — använd båda").

**Design-nivå-ändringar (exakt copy nailas i DP B:s egen brainstorm med visuella mockups):**
- Första skärmen (`ListenLauncherScreen.kt`, `listen_*`-strängar): funktionell rubrik istället för "Tre sätt att fånga" — riktning "Vilken fågel? *Identifiera.*" + funktions-sub + kort-bodies som nämner "identifiera/art".
- Hero/onboarding scen 1 (`SceneHero.kt`, `onboarding_s1_*`): lyft funktions-/nisch-löftet till rubriknivå; eyebrow-riktning "FÅGEL-ID + FÄLTDAGBOK".
- Onboarding-omsekvensering (`OnboardingScreen.kt`): funktion + nisch före gamification; nedtona/flytta märken-scenen; överväg färre scener.
- Nav-översättning (`values/strings.xml`): "Archive" → "Uppslagsverk", "Badges" → "Märken" (engelska SV-etiketter signalerar slarv).
- Store-listing (`docs/play-store/store-listing-{sv,en}.md`) + Website (`website/src/content/copy.{en,sv}.json`): kort beskrivning enligt riktningen; FAQ-post "How is this different from Merlin?". Ingen accuracy-siffra.

**Scope:** app-copy + onboarding + nav + store-listing + website i samma DP (sammanhållen positionerings-berättelse). Mestadels copy, ingen ny funktionalitet.

## 6. DP C — Kategori-hotfix (design-nivå)

**Riktning (Option B, låst):** byt chip-filtrering från `ioc_order` till `family` (finns redan på `SpeciesSummary`). Begränsa WADERS till äkta vadar-familjer; lägg `AUKS` (Alcidae) och `GULLS` (Laridae + Stercorariidae); hantera tidigare okategoriserade (20,5 %) bättre (fler chips eller "Övrigt"). Tar bort auk-/mås-pinsamheten direkt utan schema-ändring.

**Berörda filer:** `ArchiveChip.kt` (nya enum-värden + `familySets`), `ArchiveViewModel.kt` (filtrera `it.family in families`), `ArchiveScreen.kt` (chip-listan), strängar ×2. **Exakt slutgiltig chip-taxonomi (vilka familjer, hur många chips, "Övrigt"-hantering) bestäms i DP C:s egen brainstorm** (visuell chip-row-mockup).

## 7. DP D — Märken-omarbetning (design-nivå)

**Riktning (låst):** bygg om mot riktig skådar-progression (datan finns: livslista via `Observation.speciesId`, familj/ordning via `taxonomy`, 4 abundance-nivåer, fenologi via `season`):
- Höj/förläng skalan 5→25→100→**250→500**.
- Familje-completion: byt `target: 1` mot "se N olika arter ur familjen".
- Avslöja sällsynt-spåret (ta bort `RARE → Hidden` i `BadgesViewModel`).
- Utnyttja alla 4 abundance-nivåer.
- Separera "Vanor" (streaks, opt-in/nedtonat) vs "Skådar-milstolpar".
- Retona onboarding scen 5; överväg att degradera Badges från huvudflik.

**Licens-spärr:** inga audio-märken bakom Premium. **Berörda filer:** `badges.yaml`, `premium_badges.yaml`, `BadgeRule.kt`, `RecalculateBadgesUseCase.kt`, `BadgeCatalogLoader.kt`, `BadgesViewModel.kt`, `BadgeStringMap.kt`, `SceneBadges.kt`, strängar ×2, `BadgeRuleTest.kt`. **Exakt märkes-set bestäms i DP D:s egen brainstorm.**

## 8. DP E — Kategori `group`-axel (design-nivå, content-spår, sist)

**Riktning (Option A, låst):** inför en kurerad ekologisk `group`-nivå (oberoende av IOC-ordning), ~14 grupper (Tättingar, Änder & gäss, Vadare, Måsar & tärnor, Alkor, Havsfåglar, Doppingar & lommar, Hägrar & storkar, Rovfåglar, Ugglor, Hönsfåglar, Duvor, Hackspettar, Tranor & rallar + "Övriga") som täcker ~alla 839; ev. valbar undergrupp "Sångare". **Ersätter DP C:s provisoriska chips.**

**Mekanik:** `taxonomy.group` i YAML (genereras från familj via pipeline-script, ingen handredigering av 839 filer), kolumn i `SpeciesTaxonomy.sq`, fält i `Species.kt` + `SpeciesSummary`, propagera i `SqlDelightSpeciesRepository.kt`, importer i `SpeciesDbBuilder.kt`. **Schema-ändring → DB-rebuild + fingeravtryck-bump (cross-cutting #4, samma som DP A).** Stort; detaljplaneras sist, lämpligen ihop med nästa content-pipeline-iteration / geografisk expansion.

## 9. Release-strategi

A, B, C, D är var för sig shippbara v1.x-feedback-respons-steg (versionCode-bump per release; kan även buntas). E är ett separat content-spår. Inget av detta blockerar v1.1-utgåvan; det är en v1.x-respons efter v1.1 landat. Device-verify (SM-S918B) per DP enligt [[feedback_personal_device_verify]].

## 10. Testning

Varje DP: TDD för ren logik (DP A: normalisering + sök-query; DP C: family-mappning; DP D: badge-rules), unit-tester i `commonTest`/`jvmTest`, device-verify-screenshots på SM-S918B. DP A + E: dessutom DB-fingeravtrycks-test (schemaändring tvingar rebuild).

## 11. Vad som brainstormas vidare per DP

DP A → impl-plan nu (writing-plans). DP B, C, D, E får var sin **egen brainstorm** (lås exakt copy / chip-lista / märkes-set / grupp-taxonomi, med visuella mockups där det är UI) → spec/plan-tillägg → impl-plan, i tur och ordning när vi når dem. Program-spec:en uppdateras med pekare till varje DP:s plan allteftersom.
