# Spec — DP C: Kategori-hotfix (launch-grade ekologiska chips)

> Delprojekt **C** i v1.x tester-feedback-responsen. Program-spec: `docs/superpowers/specs/2026-05-29-v1-x-tester-feedback-response-design.md`. Underliggande research: `docs/superpowers/research/2026-05-29-hardcore-tester-feedback.md` (Problem 1). Brainstormat + låst 2026-05-30.

## 1. Bakgrund & problem

En erfaren skådare (extern testare) sågade Arkivets kategorier som **godtyckliga och inkonsekventa**:
- Chiparna filtrerar på **en enda axel — IOC-ordning** (`ArchiveChip.orderSets`), godtyckligt blandade nivåer: `SONGBIRDS` = hela Passeriformes (378 arter) men `OWLS` = Strigiformes (23).
- `WADERS` = hela ordningen Charadriiformes → en **soptunna** på 118 arter ur 13 familjer: äkta vadare **+ måsar/tärnor (Laridae) + alkor (Alcidae) + labbar (Stercorariidae)**. Alkorna hamnar i "Vadare" — testarens explicita exempel.
- **20,5 % av alla 839 arter** (hönsfåglar, hackspettar, duvor, tranor/rallar…) täcks inte av någon chip — de syns bara under "Alla".

Rotorsak i kod: `ArchiveChip.kt:13-21` (`orderSets`) + `ArchiveViewModel.kt:120-126` (`list.filter { it.iocOrder in orders }`). `SpeciesSummary` (`Species.kt:38-46`) exponerar redan `family` (latin) + `iocOrder` — ingen ekologisk gruppnivå finns däremellan.

## 2. Mål & icke-mål

**Mål:** Ett **launch-grade ekologiskt chip-set** (~10 chips) som en nordisk skådare känner igen, är konsekvent, och fixar testarens konkreta klagomål — **utan schema-/content-ändring** (ren UI-/mappnings-fix i `composeApp`).

**Icke-mål (medvetet uppskjutet):**
- **DP E** (kurerad `group`-axel i content + DB-kolumn, ~14 grupper, ev. passerin-undergrupper) är den riktiga lösningen och **ersätter DP C senare** (post-launch content-spår). DP C är därför provisorisk men ska duga vid launch.
- Att dela Tättingarna (378) i sångare/finkar/trastar → DP E.
- Propagera `family_sv` till `SpeciesSummary` så familj-sorteringens rubriker visar svenska familjenamn (idag latin, t.ex. "Anatidae") → vikas in i **DP E** (rör ändå `SpeciesSummary` + repository).

## 3. Designbeslut (låsta 2026-05-30)

| # | Beslut |
|---|---|
| 1 | **Ambition: launch-grade ~10 ekologiska chips** (inte minimal stopgap, inte full ~14). Det är DP C testarna/launch-användarna ser. |
| 2 | **Mekanik: family-baserad mappning i kod** (ingen schema/content-ändring). Chip → set av latinska familjenamn (matchar `SpeciesSummary.family`). |
| 3 | **`SONGBIRDS` keyas på ordning** (`iocOrder == "Passeriformes"`), inte på en 42-familjers-lista. Passeriformes är den ENDA ordning som mappar 1:1 mot en ekologisk grupp → robust: en framtida ny passerin-familj hamnar automatiskt rätt. (Det var inte ordnings-filtrering i sig som var fel i gamla koden — det var att *heterogena* ordningar som Charadriiformes klumpades.) |
| 4 | **`OTHER` (Övrigt) = komplementet** — inget fast set; `iocOrder != "Passeriformes" && family !in categorizedFamilies`. Självunderhållande: nya arter/familjer ramlar ner i Övrigt tills DP E, ingen faller mellan stolarna. |
| 5 | **Alkor (Alcidae) slås ihop** i "Måsar, tärnor & alkor" (egen chip vore bara 7 arter). Löser auk-buggen direkt. |
| 6 | **Hackspettar (17) + Duvor (17) ligger kvar i Övrigt** för launch (DP E gör den långa svansen). |

## 4. Chip-taxonomi (komplett mappning, täcker alla 839)

Enum-id (engelska, kod-konvention) → svensk/engelsk etikett → familjer → artantal (regressions-ankare):

| Enum | Etikett SV / EN | Nyckel | Familjer | Arter |
|---|---|---|---|---:|
| `ALL` | Alla / All | *(inget filter)* | — | 839 |
| `SONGBIRDS` | Tättingar / Songbirds | `iocOrder == "Passeriformes"` | (alla 42 passerin-familjer) | 378 |
| `WATERFOWL` | Änder & gäss / Ducks & geese | family | Anatidae | 53 |
| `RAPTORS` | Rovfåglar / Birds of prey | family | Accipitridae, Falconidae, Pandionidae | 51 |
| `WADERS` | Vadare / Waders | family | Scolopacidae, Charadriidae, Glareolidae, Burhinidae, Recurvirostridae, Haematopodidae, Rostratulidae, Jacanidae, Dromadidae | 66 |
| `GULLS` | Måsar, tärnor & alkor / Gulls, terns & auks | family | Laridae, Stercorariidae, Alcidae | 51 |
| `SEABIRDS` | Havsfåglar / Seabirds | family | Procellariidae, Hydrobatidae, Oceanitidae, Sulidae, Phalacrocoracidae, Anhingidae, Fregatidae, Phaethontidae | 37 |
| `HERONS` | Hägrar & storkar / Herons & storks | family | Ardeidae, Ciconiidae, Threskiornithidae, Pelecanidae, Phoenicopteridae, Scopidae | 31 |
| `GREBES_DIVERS` | Doppingar & lommar / Grebes & divers | family | Podicipedidae, Gaviidae | 9 |
| `GAMEBIRDS` | Hönsfåglar / Gamebirds | family | Phasianidae, Odontophoridae, Numididae | 31 |
| `OWLS` | Ugglor / Owls | family | Strigidae, Tytonidae | 23 |
| `OTHER` | Övrigt / Other | komplement | hackspettar, duvor, rallar/tranor, gökar, seglare, nattskärror, flyghöns, kungsfiskare, biätare, trappar, papegojor m.fl. | 109 |

Summa kategoriserade: 378+53+51+66+51+37+31+9+31+23 = **730**; Övrigt = 839−730 = **109**. ✔

**Chip-ordning i raden** (lätt att justera): Alla · Tättingar · Rovfåglar · Ugglor · Hönsfåglar · Änder & gäss · Doppingar & lommar · Hägrar & storkar · Vadare · Måsar, tärnor & alkor · Havsfåglar · Övrigt.

## 5. Komponenter (filer)

1. **`composeApp/.../ui/encyclopedia/ArchiveChip.kt`**
   - Byt enum-värdena till: `ALL, SONGBIRDS, WATERFOWL, RAPTORS, WADERS, GULLS, SEABIRDS, HERONS, GREBES_DIVERS, GAMEBIRDS, OWLS, OTHER`.
   - Byt `orderSets` mot `familySets: Map<ArchiveChip, Set<String>>` (familjer enligt §4) — `SONGBIRDS`, `ALL`, `OTHER` har inget set.
   - Lägg `companion`-konstant `SONGBIRD_ORDER = "Passeriformes"` + `categorizedFamilies: Set<String> = familySets.values.flatten().toSet()`.

2. **`composeApp/.../ui/encyclopedia/ArchiveViewModel.kt`** (`toUiState`, ~rad 120-126) — ersätt filtret:
   ```
   when (c) {
     ALL -> list
     SONGBIRDS -> list.filter { it.iocOrder == ArchiveChip.SONGBIRD_ORDER }
     OTHER -> list.filter { it.iocOrder != ArchiveChip.SONGBIRD_ORDER && it.family !in ArchiveChip.categorizedFamilies }
     else -> list.filter { it.family in ArchiveChip.familySets[c].orEmpty() }
   }
   ```

3. **`composeApp/.../ui/encyclopedia/ArchiveScreen.kt`** (`ChipBar`, ~rad 453-461) — uppdatera `labels`-listan till de nya chiparna + deras `stringResource` i §4-ordningen. Importera de nya `archive_chip_*`-resurserna.

4. **Strängar ×2** — `composeApp/src/commonMain/composeResources/values/strings.xml` (SV, default) + `values-en/strings.xml` (EN):
   - Sätt SV+EN-värde för **varje** chip-nyckel enligt §4. Återanvänd de befintliga nyckelnamnen där de finns (`archive_chip_all/songbirds/raptors/owls/waders`) och **uppdatera deras värden** till §4-etiketterna (t.ex. `songbirds` → "Tättingar"/"Songbirds", `waders` → "Vadare"/"Waders").
   - **Lägg till** nya nycklar: `archive_chip_{waterfowl, gulls, seabirds, herons, grebes_divers, gamebirds, other}`.
   - **Ta bort** `archive_chip_water` (chip:en finns inte längre).
   - Raw `'`/`’` (inte `\'`); ingen `%`-användning här.

## 6. Gratis preferens-migrering

Sparad chip-preferens lagras som enum-namn och läses redan via `runCatching { ArchiveChip.valueOf(it) }.getOrDefault(ArchiveChip.ALL)` (`ArchiveViewModel.kt:49`). En användare som hade `WATER` sparat → `valueOf` kastar → faller tillbaka till `ALL`. Borttagningen av `WATER` kräver alltså **ingen migrering**.

## 7. Tester

**`ArchiveChipMappingTest`** (ren `commonTest`, ingen DB):
1. **Inga överlapp:** ingen familj förekommer i mer än ett `familySets`-värde (annars dubbelräkning).
2. **Passeriformes inte i något `familySets`** (annars dubbelräknas tättingar mot en annan chip).
3. **Typo-vakt + täckning + regressions-ankare:** mot en **kanonisk familjelista** (de 97 familjerna från content, lagrad som testresurs/konstant): varje mappad familj finns i listan (fångar felstavning); `categorizedFamilies ∪ Passeriformes-familjer ∪ OTHER` = hela listan; förväntade artantal per chip (§4) hålls. *(Exakt källa för "alla familjer + antal" — testresurs-fil genererad från content vs query mot bundlad species.db i composeApp-jvmTest — bestäms i planen. Intentionen: en framtida ny familj får inte tyst försvinna.)*
4. **OTHER ≠ tom.**

**Device-verify (SM-S918B)** — per [[feedback_personal_device_verify]] (Albins dagliga telefon, "händerna borta" först, screencap-verifiera, radera ev. privat innehåll):
- Scrolla chip-raden; tappa nyckel-chips:
  - **Vadare** → inga måsar/tärnor/alkor i listan.
  - **Måsar, tärnor & alkor** → alkor (t.ex. sillgrissla/tordmule) finns med.
  - **Övrigt** → hackspettar + duvor finns med.
- Tom-state om någon chip skulle bli tom (ska inte hända givet ankar-antalen).
- Screenshot på den nya chip-raden till `docs/superpowers/screenshots/`.

## 8. Cross-cutting bivillkor (från program-spec §3)

1. **Alla UI-strängar via `compose-resources` i BÅDA `strings.xml`** (SV-default + EN). Aldrig hårdkoda lokaliserad text. Raw `'`/`’`.
2. **Verifiera mot koden innan fix** (fil:rad i denna spec är grund men kan ha drivit; jfr [[feedback_audit_verify_before_fix]]). Default-värden på ev. nya fält så test-fixturer inte bryts.
3. **Inga nya beroenden**; ingen schema-/content-ändring → **ingen DB-rebuild/fingeravtryck-bump** (till skillnad från DP A/E).
4. `:androidApp` transitiva deps: ej relevant (inga nya modul-referenser).
5. Accuracy-siffror: ej relevant (ingen ML-copy i DP C).

## 9. Release

Egen versionCode-bump (eller buntas i den samlade v1.x-AAB:n enligt [[project_v1_1_phase_a_ready]]:s "starta klockan tidigt, batcha innehållet"). Inget blockerar; shippbar var för sig. DP E ersätter senare DP C:s provisoriska chips.

## 10. Vad som händer sen

Spec → `superpowers:writing-plans` → bite-sized TDD-plan (ren logik först: `ArchiveChip.familySets` + `ArchiveChipMappingTest`; sen ViewModel-filter; sen ChipBar + strängar; sen device-verify).
