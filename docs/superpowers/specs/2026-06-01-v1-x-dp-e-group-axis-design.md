# Spec — DP E: Ekologisk `group`-axel (content + DB-kolumn)

> Delprojekt **E** i v1.x tester-feedback-responsen. Program-spec: `docs/superpowers/specs/2026-05-29-v1-x-tester-feedback-response-design.md` (§8). Underliggande research: `docs/superpowers/research/2026-05-29-hardcore-tester-feedback.md` (Problem 1, Option A). DP C (provisoriska UI-chips) som DP E ersätter: `docs/superpowers/specs/2026-05-30-dp-c-category-hotfix-design.md`. Brainstormat + låst 2026-06-01.

## 1. Bakgrund & problem

En erfaren skådare (extern testare) sågade Arkivets kategorier som **godtyckliga och inkonsekventa** (alkor i "Vadare" m.m.). **DP C** (DONE, mergad `fa133cc`) löste detta provisoriskt genom att byta IOC-ordnings-chips mot **12 ekologiska, family-mappade chips + Övrigt** — men mappningen ligger i **UI-lagret** (`ArchiveChip.kt`, Kotlin `familySets`-map), inte i datan. Det betyder:

- Grupperingen är inte en frågbar content-axel — andra features (browse-by-group, grupp-badges, stats per grupp, framtida geografisk expansion) kan inte återanvända den utan att re-implementera familj→grupp-mappningen.
- "Övrigt" är en stor uppsamling (109 arter) som klumpar hackspettar, duvor, tranor/rallar m.fl. som egentligen är egna naturliga grupper.

**DP E** är "den riktiga lösningen" från program-spec §8: en **kurerad ekologisk `group`-nivå** (oberoende av IOC-ordning), materialiserad som en **DB-kolumn** med en enda kurerad sanningskälla, som **ersätter DP C:s provisoriska UI-chips**. Plus en inlånad uppstädning: propagera `family_sv` så Arkivets familje-sortrubriker visar svenska familjenamn (idag latin).

Verifierat mot koden 2026-06-01: `family_sv` finns redan i varje art-YAML + i `SpeciesTaxonomy`-tabellen (kolumn) men bärs **inte** av `SpeciesSummary` → `FamilyHeader` (`ArchiveScreen.kt:358`) renderar latinskt familjenamn. `SCHEMA_REV = 2` i `SpeciesDbBuilder.kt:12` (bumpad av DP A för `search_text`). `BirdyContent.Schema.version` hårdkodad `1`.

## 2. Mål & icke-mål

**Mål:**
- En kurerad ekologisk **`group`-axel som DB-kolumn** på `SpeciesTaxonomy`, genererad vid DB-bygget ur en **enda** kurerad `family → group`-map. Noll handredigering av de 839 art-YAML:erna.
- **15 grupper** (14 namngivna + `other`) som täcker alla 839, en platt axel, som en nordisk skådare känner igen.
- Arkivets chips konsumerar `group`-kolumnen i stället för Kotlin-familjemappen; DP C:s `familySets`/`categoryOf`/`ArchiveChipMappingTest` pensioneras.
- `family_sv` propageras till `SpeciesSummary` → Arkivets FAMILY-sort visar svenska familjerubriker (SV-locale; EN faller tillbaka till latin).

**Icke-mål (medvetet uppskjutet):**
- **Passerin-undergrupp ("Sångare")** — att dela de 378 tättingarna vidare. Skulle införa hierarki/andra-axel-frågor i en idag platt chip-modell + "Sångare" är en luddig folk-kategori. **Egen uppföljning om vi vill.**
- **`family_en`-content** (engelska familjenamn) — finns inte; att lägga till = 94 översättningar. EN-locale behåller latinska familjerubriker.
- Nya sorteringslägen / egen "browse-by-group"-vy / grupp-badges / stats-per-grupp. `group`-kolumnen **möjliggör** dem men DP E bygger dem inte.
- Att eliminera `other` — den behålls som ärlig uppsamling (62 heterogena arter, ofta rara gäster, bildar ingen naturlig nordisk grupp).

## 3. Designbeslut (låsta 2026-06-01)

| # | Beslut |
|---|---|
| 1 | **Granularitet: platt 15-gruppsaxel** (14 namngivna + `other`), en enda axel. Tättingar förblir én grupp (378). Sångare-split = uppskjuten uppföljning. |
| 2 | **Mekanik: bygg-tids-map.** EN kurerad `family_groups.yaml`-resurs under `shared/content/`; `SpeciesDbBuilder` läser den och stämplar `group` in i en ny `SpeciesTaxonomy.group`-kolumn vid DB-bygget. Noll YAML-churn, en sanningskälla, ingen drift. (Förkastat: skriva `taxonomy.group` in i 839 YAML — brusig diff, redundant data, måste köras om vid content-refresh.) |
| 3 | **`songbirds` keyas på `ioc_order == "Passeriformes"`**, inte på en 42-familjers-lista — robust mot framtida nya passerinfamiljer (samma beslut som DP C §3). Övriga 14 grupper keyas på familj. |
| 4 | **`other` = komplementets familjer** (explicit lista i `family_groups.yaml`), inte ett tyst fallthrough. Builder loggar en **bygg-tids-varning** om en content-familj saknas i mappen (då hamnar den i `other` som säkerhetsnät) — så en ny familj inte tyst försvinner. |
| 5 | **Fingerprint-fix:** `SCHEMA_REV 2→3`. Ny kolumn = schemaändring → `contentFingerprint` flippar `application_id` → `needsCopy` ersätter cachad DB på uppgradering. (Samma mekanik som DP A; provider orörd.) |
| 6 | **`family_sv`-rubriker** vikas in (program-spec §6). `SpeciesSummary` får `familySv`; FAMILY-sort-rubrik = svenska (fallback latin för EN). Ingen schemaändring för detta (kolumnen finns). |
| 7 | **Curatoriska kantfall** (behållna från DP C för kontinuitet): Stercorariidae (labbar)→`gulls_terns`; Pelecanidae (pelikaner)+Phoenicopteridae (flamingor)→`herons_storks`. |

## 4. Grupp-taxonomi (komplett mappning, täcker alla 839)

Grupp-id (kod-nyckel, lagras i `group`-kolumnen) → SV/EN-etikett → familjer → artantal (regressions-ankare, snapshot 2026-05-30 / 2026-06-01, 839 arter):

| Grupp-id | SV / EN | Nyckel | Familjer | Arter |
|---|---|---|---|---:|
| `songbirds` | Tättingar / Songbirds | `ioc_order == "Passeriformes"` | (alla 42 passerinfamiljer) | 378 |
| `waterfowl` | Änder & gäss / Ducks & geese | family | Anatidae | 53 |
| `waders` | Vadare / Waders | family | Scolopacidae, Charadriidae, Glareolidae, Burhinidae, Recurvirostridae, Haematopodidae, Rostratulidae, Jacanidae, Dromadidae | 66 |
| `gulls_terns` | Måsar & tärnor / Gulls & terns | family | Laridae, Stercorariidae | 44 |
| `auks` | Alkor / Auks | family | Alcidae | 7 |
| `seabirds` | Havsfåglar / Seabirds | family | Procellariidae, Hydrobatidae, Oceanitidae, Sulidae, Phalacrocoracidae, Anhingidae, Fregatidae, Phaethontidae | 37 |
| `grebes_divers` | Doppingar & lommar / Grebes & divers | family | Podicipedidae, Gaviidae | 9 |
| `herons_storks` | Hägrar & storkar / Herons & storks | family | Ardeidae, Ciconiidae, Threskiornithidae, Pelecanidae, Phoenicopteridae, Scopidae | 31 |
| `raptors` | Rovfåglar / Birds of prey | family | Accipitridae, Falconidae, Pandionidae | 51 |
| `owls` | Ugglor / Owls | family | Strigidae, Tytonidae | 23 |
| `gamebirds` | Hönsfåglar / Gamebirds | family | Phasianidae, Odontophoridae, Numididae | 31 |
| `doves` | Duvor / Doves & pigeons | family | Columbidae | 17 |
| `woodpeckers` | Hackspettar / Woodpeckers | family | Picidae | 17 |
| `cranes_rails` | Tranor & rallar / Cranes & rails | family | Rallidae, Gruidae | 13 |
| `other` | Övriga / Other | komplement | Cuculidae, Apodidae, Caprimulgidae, Pteroclidae, Alcedinidae, Psittacidae, Otididae, Meropidae, Coraciidae, Psittaculidae, Turnicidae, Upupidae, Struthionidae, Bucerotidae | 62 |

**Summa kategoriserade (exkl. `other`):** 378+53+66+44+7+37+9+31+51+23+31+17+17+13 = **777**; `other` = 839−777 = **62**. ✔

**Chip-ordning i raden** (Alla först, sen tabellordningen): Alla · Tättingar · Änder & gäss · Vadare · Måsar & tärnor · Alkor · Havsfåglar · Doppingar & lommar · Hägrar & storkar · Rovfåglar · Ugglor · Hönsfåglar · Duvor · Hackspettar · Tranor & rallar · Övriga. (16 chips totalt; scrollar i `LazyRow` som idag.)

> **Kantfall (beslut #7):** Stercorariidae→`gulls_terns` (alt: `seabirds`); Pelecanidae+Phoenicopteridae→`herons_storks` (alt: `other`). Behållna från DP C för kontinuitet; lätta att flytta i `family_groups.yaml` om vi ändrar oss (ankar-antalen i testet måste då uppdateras i samma commit).

## 5. Arkitektur & dataflöde

```
shared/content/family_groups.yaml            ← kurerad family→group-map + grupp-ordning (~94 familjer)
        │  (parsas av builder vid DB-bygget, jvmMain)
        ▼
SpeciesDbBuilder.insertSpecies → group = FamilyGroups.groupFor(family, iocOrder)
        │                          (Passeriformes→songbirds; family-lookup; okänd→other + varning)
        ▼
SpeciesTaxonomy.group  (TEXT NOT NULL, index species_taxonomy_group)   ← SCHEMA_REV 2→3 + fingerprint-flip
        ▼
SqlDelightSpeciesRepository → SpeciesSummary.group + .familySv
        ▼
ArchiveViewModel.toUiState → list.filter { chip == ALL || it.group == chip.key }
        ▼
ArchiveScreen ChipBar (Alla + 15 grupper) + FamilyHeader på svenska (familySv, fallback latin)
```

**`group` är en äkta frågbar content-axel** — inte UI-logik. Det är hela skillnaden mot DP C.

## 6. Komponenter (filer)

### Content/schema (`shared/content`)

1. **`shared/content/family_groups.yaml`** *(ny)* — kurerad källa. Form (skiss):
   ```yaml
   # Ekologisk grupp-axel. Enda sanningskällan för family→group.
   # songbirds keyas på ioc_order==Passeriformes (täcker alla passerinfamiljer).
   order: [songbirds, waterfowl, waders, gulls_terns, auks, seabirds,
           grebes_divers, herons_storks, raptors, owls, gamebirds,
           doves, woodpeckers, cranes_rails, other]
   groups:
     songbirds:    { keyed_by: order, ioc_order: Passeriformes }
     waterfowl:    { families: [Anatidae] }
     waders:       { families: [Scolopacidae, Charadriidae, Glareolidae, Burhinidae,
                                 Recurvirostridae, Haematopodidae, Rostratulidae, Jacanidae, Dromadidae] }
     gulls_terns:  { families: [Laridae, Stercorariidae] }
     auks:         { families: [Alcidae] }
     seabirds:     { families: [Procellariidae, Hydrobatidae, Oceanitidae, Sulidae,
                                 Phalacrocoracidae, Anhingidae, Fregatidae, Phaethontidae] }
     grebes_divers:{ families: [Podicipedidae, Gaviidae] }
     herons_storks:{ families: [Ardeidae, Ciconiidae, Threskiornithidae, Pelecanidae,
                                 Phoenicopteridae, Scopidae] }
     raptors:      { families: [Accipitridae, Falconidae, Pandionidae] }
     owls:         { families: [Strigidae, Tytonidae] }
     gamebirds:    { families: [Phasianidae, Odontophoridae, Numididae] }
     doves:        { families: [Columbidae] }
     woodpeckers:  { families: [Picidae] }
     cranes_rails: { families: [Rallidae, Gruidae] }
     other:        { families: [Cuculidae, Apodidae, Caprimulgidae, Pteroclidae, Alcedinidae,
                                 Psittacidae, Otididae, Meropidae, Coraciidae, Psittaculidae,
                                 Turnicidae, Upupidae, Struthionidae, Bucerotidae] }
   ```
   *(SV/EN-etiketterna bor i `strings.xml`, inte här — denna fil är ren taxonomi-data. Exakt YAML-form finslipas i planen, men en sanningskälla är låst.)*

2. **`FamilyGroups.kt`** *(ny, jvmMain)* — parsar `family_groups.yaml`; bygger en `family → groupId`-uppslagstabell; `fun groupFor(family: String, iocOrder: String): String` (Passeriformes→`songbirds`; family-lookup; okänd familj→`other` + returnerar signal/loggar varning). Återanvänds av builder + validator + test.

3. **`SpeciesTaxonomy.sq`** — ny kolumn `group TEXT NOT NULL`; nytt `CREATE INDEX species_taxonomy_group ON SpeciesTaxonomy(group)`; uppdaterad `insert:` (ny parameter); ev. ny query `selectByGroup` (frivillig, för framtida bruk — tas bara med om planen ser direkt användning).

4. **`SpeciesDbBuilder.kt`** (jvmMain) — `SCHEMA_REV = 3`. Ladda `FamilyGroups` en gång; i `insertSpecies` skicka `group = familyGroups.groupFor(yaml.taxonomy.family, yaml.taxonomy.ioc_order)` till `speciesTaxonomyQueries.insert(...)`.

5. **`Species.kt`** — `SpeciesTaxonomy` får `val group: String`; `SpeciesSummary` får `val group: String = ""` + `val familySv: String = ""` (defaults skyddar befintliga test-fixturer, cross-cutting #6).

6. **`SqlDelightSpeciesRepository.kt`** — propagera `group` + `family_sv` på alla ställen som bygger `SpeciesSummary` (`summaryFor`, `search`) och `SpeciesTaxonomy` (`getById`, `allByQid`). `group` läses ur nya kolumnen; `familySv` ur befintliga `family_sv`-kolumnen.

7. **`ValidateFamilyGroupsMain.kt`** *(ny, jvmMain — frivillig, avgörs i planen)* — körbar validator: varje content-familj är mappad (annars fel), inga dubletter, summa = artantal. Alternativt räcker `FamilyGroupsTest` i jvmTest.

### App (`composeApp`)

8. **`ArchiveChip.kt`** — enum byts till `ALL` + de 15 grupp-id:na (versaler enligt kod-konvention, t.ex. `SONGBIRDS, WATERFOWL, WADERS, GULLS_TERNS, AUKS, SEABIRDS, GREBES_DIVERS, HERONS_STORKS, RAPTORS, OWLS, GAMEBIRDS, DOVES, WOODPECKERS, CRANES_RAILS, OTHER`). Varje värde har `val key: String` = content-grupp-id (t.ex. `GULLS_TERNS.key == "gulls_terns"`). `matches(group: String): Boolean = this == ALL || group == key`. `familySets`/`categorizedFamilies`/`categoryOf`/`PASSERINE_ORDER` **rivs**.

9. **`ArchiveViewModel.kt`** — `toUiState`-filtret: `list.filter { c.matches(it.group) }` (signaturbyte från `(family, iocOrder)`).

10. **`ArchiveScreen.kt`** — `ChipBar` `labels`-lista → 16 chips i §4-ordning med deras `archive_chip_*`-resurser; `FamilyHeader(family, familySv)` använder `familySv.ifBlank { family }` (svenska där det finns, latin annars). `groupBy { it.summary.family }` i FAMILY-sort kan behöva keya på `familySv` för korrekt svensk gruppering/rubrik — avgörs i planen (rubrik-text vs grupperingsnyckel).

11. **Strängar ×2** — `composeApp/src/commonMain/composeResources/values/strings.xml` (SV-default) + `values-en/strings.xml` (EN):
    - SV+EN-värde för **varje** grupp enligt §4. Återanvänd befintliga nyckelnamn (`archive_chip_{all, songbirds, raptors, owls, waders, waterfowl, gulls, seabirds, herons, grebes_divers, gamebirds, other}`) — **uppdatera värden** (t.ex. `gulls`→"Måsar & tärnor"/"Gulls & terns", `herons`→"Hägrar & storkar"/"Herons & storks").
    - **Lägg till** `archive_chip_{auks, doves, woodpeckers, cranes_rails}`.
    - Nyckel-konvention: behåll `archive_chip_gulls` för `gulls_terns` och `archive_chip_herons` för `herons_storks` (id != nyckel ok), ELLER döp om till `archive_chip_gulls_terns`/`herons_storks` — kosmetiskt, avgörs i planen.
    - Raw `'`/`’` (inte `\'`); ingen `%`-användning.

## 7. Schema, fingerprint & migrering (cross-cutting #4 — kritiskt)

- Ny `group`-kolumn = äkta schemaändring → **`SCHEMA_REV 2→3`** i `SpeciesDbBuilder`. `contentFingerprint` prefixar `schema=$schemaRev` → ny `application_id` → `needsCopy` (`SpeciesRepositoryProvider.android.kt`, **orörd**) jämför APK-asset-DB:ns `application_id` mot cachad kopias → re-kopierar bundlad DB på uppgradering. **Utan bumpen:** uppgraderade enheter behåller gammal DB utan `group`-kolumn → krasch vid `SELECT group`. (`Schema.version` hårdkodad 1 → user_version-strategin fungerar inte; verifierat i DP A.)
- `family_sv`-propageringen kräver **ingen** schemaändring (kolumnen finns) men åker med på samma rebuild.
- **Chip-pref-migrering:** gratis. `ArchiveViewModel.kt:49` läser `runCatching { ArchiveChip.valueOf(it) }.getOrDefault(ALL)`. Borttagna/betydelseändrade enum-värden (`GREBES_DIVERS` finns kvar; `WADERS`/`GULLS` ändrar betydelse men namn lever; ev. borttagna) faller säkert tillbaka till `ALL`. Ingen DataStore-migrering.

## 8. Tester (TDD — ren logik först)

| Test | Plats | Vad det vaktar |
|---|---|---|
| `FamilyGroupsTest` | `shared/content` jvmTest | Mot snapshot `CONTENT_FAMILY_COUNTS` (94 familjer/839): varje familj mappad till exakt en grupp; inga dubletter; `groupFor(Passeriformes-familj, "Passeriformes")`→`songbirds`; okänd familj→`other`; **artantal per grupp = §4-ankarna**; summa = 839; `order`-listan matchar grupp-id-mängden. |
| `SpeciesDbBuilderTest` | `shared/content` jvmTest | `group`-kolumnen fylls korrekt vid build (representativa: Alcidae→`auks`, Falconidae→`raptors`, Picidae→`woodpeckers`, en passerin→`songbirds`). |
| Fingerprint-test | `shared/content` jvmTest | `SCHEMA_REV`-ändring flippar `application_id` (utökar/bekräftar befintligt `contentFingerprint`-test). |
| `ArchiveViewModelTest` | `composeApp` commonTest | Filtret matchar på `group`; fixturer uppdaterade med `group`/`familySv`. |
| Chip-drift-test | `composeApp` commonTest | UI-enumens `key`-mängd == content-grupp-id-mängden (`family_groups.yaml`-`order`, ev. via genererad konstant/testresurs) — fångar att en grupp läggs i content men glöms i chip-raden, eller tvärtom. |

**Snapshot-ankarens källa** (testresurs genererad ur content vs query mot bundlad `species.db` i jvmTest) bestäms i planen; intentionen: en framtida ny familj får inte tyst hamna fel/försvinna. `ArchiveChipMappingTest` (DP C) **tas bort** (ersätts av `FamilyGroupsTest` + chip-drift-test).

**Device-verify (SM-S918B)** — per [[feedback_personal_device_verify]] (Albins dagliga telefon: be om "händerna borta", screencap-verifiera mCurrentFocus, radera ev. privat innehåll; snabba ADB-interaktioner, undvik scroll-loopar > ~10s, chip-`&`-etiketter står som `&amp;` i uiautomator-XML):
- Scrolla chip-raden; tappa nyckel-chips:
  - **Vadare** → inga måsar/tärnor/alkor.
  - **Alkor** → egen chip; sillgrissla/tordmule synliga.
  - **Hackspettar / Duvor / Tranor & rallar** → egna chips med innehåll.
  - **Övriga** → gökar/seglare/papegojor m.fl. (ej hackspettar/duvor längre).
- FAMILY-sort → svenska familjerubriker (t.ex. "FALKFÅGLAR" inte "FALCONIDAE") i SV-locale.
- Screenshots till `docs/superpowers/screenshots/`.

## 9. Cross-cutting bivillkor (från program-spec §3)

1. **Alla UI-strängar via `compose-resources` i BÅDA `strings.xml`** (SV-default + EN). Raw `'`/`’`.
2. **Verifiera mot koden innan fix** (fil:rad här är grund men kan ha drivit; jfr [[feedback_audit_verify_before_fix]]). Default-värden på nya `SpeciesSummary`-fält (`group`/`familySv` = `""`) så test-fixturer inte bryts.
3. **Content-DB-schemaändring MÅSTE flippa fingeravtrycket** → `SCHEMA_REV 2→3` (§7). Detta är DP E:s största fälla.
4. **Inga audio/Premium-kopplingar** (BirdNET-licens) — ej relevant i DP E.
5. **`:androidApp` transitiva deps** — ej relevant (inga nya modul-referenser; `group`/`familySv` är fält på befintliga typer).
6. **Accuracy-siffror** — ej relevant (ingen ML-copy).
7. **Inga nya beroenden.** YAML-parsning återanvänder befintlig stack: `SpeciesYamlParser` (jvmMain) använder **kaml** (`com.charleskorn.kaml.Yaml`, byggd på kotlinx-serialization) — `family_groups.yaml` parsas med en egen `@Serializable`-modell via samma `Yaml`-instans/mönster. `libs.kaml` finns redan i `shared/content/build.gradle.kts`.

## 10. Release

Egen versionCode-bump (vC120→121, versionName-suffix enligt projektets vana) ELLER buntas i nästa samlade v1.x-AAB enligt [[project_v1_1_release_train]] / "starta klockan tidigt, batcha innehållet". Inget blockerar; shippbar var för sig. **DP E ersätter DP C:s provisoriska chips** — efter DP E är `ArchiveChip`-familjemappen historik. När v1.1/v1.x-AAB:n laddas upp gäller CLAUDE.md follow-up #8 (store-listning + release-notes) — DP E:s ekologiska kategorier nämns redan där som en av batchens ändringar.

## 11. Vad som händer sen

Spec → `superpowers:writing-plans` → bite-sized TDD-plan. Föreslagen task-ordning (ren logik först, schema/DB i mitten, UI sist, device-verify till slut):
1. `family_groups.yaml` + `FamilyGroups.kt` + `FamilyGroupsTest` (ren logik, ingen DB).
2. `SpeciesTaxonomy.sq` `group`-kolumn + index + `SCHEMA_REV 3` + `SpeciesDbBuilder` + `SpeciesDbBuilderTest` + fingerprint-test.
3. `Species.kt` (`group`/`familySv`-fält) + `SqlDelightSpeciesRepository` propagering.
4. `ArchiveChip` omskrivning + `ArchiveViewModel`-filter + `ArchiveViewModelTest` + chip-drift-test; ta bort `ArchiveChipMappingTest`.
5. `ArchiveScreen` ChipBar + FamilyHeader (familySv) + strängar ×2.
6. Bygg + `installDebug` + device-verify-screenshots; versionCode-bump.

Eventuell **Sångare-undergrupp** och **`browse-by-group`-vy / grupp-badges / stats-per-grupp** är separata uppföljningar som `group`-kolumnen nu möjliggör.
