# Plan 2b — Content backfill runbook

Plan 2a delivered the pipeline + walking skeleton. Plan 2b is the work of running the fetcher across all ~700 species, reviewing output family-by-family, and committing.

## Status

| Datum | Familj | Arter | Commit |
|---|---|---|---|
| 2026-05-02 | (walking skeleton) | 5 | `d973e31` |
| 2026-05-04 | paridae | +8 (totalt 13) | `f8cc17f` |
| 2026-05-04 | accipitridae | +38 (totalt 51) | `1ed1895` |
| 2026-05-04 | acrocephalidae | +19 (totalt 70) | `3609b98` |
| 2026-05-04 | alaudidae | +27 (totalt 97) | `d945e1f` |
| 2026-05-06 | anatidae | +52 (totalt 149) | `1a99a63` |
| 2026-05-06 | aegithalidae | +1 (totalt 150) | `acb3249` |
| 2026-05-06 | alcedinidae | +6 (totalt 156) | `e193080` |
| 2026-05-06 | alcidae | +7 (totalt 163) | `d296bdd` |
| 2026-05-06 | anhingidae | +1 (totalt 164) | `0461611` |
| 2026-05-06 | apodidae | +9 (totalt 173) | `89f2ca3` |
| 2026-05-07 | ardeidae | +16 (totalt 189) | `103c76c` |
| | _next: bombycillidae eller liknande — kolla species_list.yaml_ | | |

Cumulative species count tracked in `shared/content/expected-species-count.txt`.

**Accipitridae-batch lärdomar (2026-05-04):**

- 5/38 abundance:allmän approved efter visuell hero-check (Sparvhök Q25380, Ormvråk Q25385, Havsörn Q25438, Fjällvråk Q26407, Brun kärrhök Q26431).
- **18/38 (47%) behövde `description_accept_missing`-override** för minst ett språk (svwiki sparse). Räkna med ~30–50% override-rate på rovfågel-/sjöfågel-familjer; lättare i tätt-bevakade tättingfamiljer. Tracker: `docs/superpowers/runbooks/content-gaps.md`.
- **Pipeline partial-rerun-bug fixad i `1bac05d`:** `--field text/images` byggde tidigare SpeciesYamlData från noll → tömde icke-rörda fält (image_refs eller description). `refresh_one` läser nu existerande YAML när `--field` ≠ `all` och bevarar de andra sektionerna + `review_status`/`review_notes`. **Konsekvens för loopen:** sätt `review_status: approved` som SISTA steg innan commit (efter alla `refresh`-anrop), annars riskerar du nästa `--field`-körning skriva över din review.
- **REJECT_PATTERNS utökad** för plural-kategorier ("Bird illustrations", "(museum specimens)", "Taxidermied birds") + historiska tryck (Iconographia, Hardwicke, Wellcome chromolithographs incl. trunkerad form).
- **`commons_search_name`-override i `species_list.yaml`** löser genus-renames där Commons-kategorier inte hängt med (Astur gentilis → search "Accipiter gentilis"). Pattern återanvändbart för Botaurus, Tachyspiza i framtida familjer.

**Acrocephalidae-batch lärdomar (2026-05-04):**

- 3/19 abundance:allmän approved (Q27674 härmsångare, Q27236 sävsångare, Q159080 rörsångare).
- **8/19 (42%) sparse-text-overrides** — liknande rate som accipitridae trots att tättingfamilj. Slutsats: rate driven av rariteter med tunna svwiki-stubbar, inte av familj-ordning.
- **Validator-threshold (80w) > sparse-threshold (20w):** Q1590574 fick sv=72w (Claude körde, men under 80w-gränsen). Lägg `sv` i `description_accept_missing` även när text faktiskt finns men är för kort. Validatorn rapporterar `description-too-short` om missat.
- **`allow_missing_images: true` per art** finns redan som override (`ValidateMain.kt:15`). Använd när Commons saknar foto över `MIN_DIMENSION=2048`. Q891376 basrasångare hade bara 1071×905 → satt allow_missing_images. Bättre än att sänka MIN_DIMENSION globalt.
- Q27674 härmsångare hade bara 1 image_ref (hero, ingen secondary). Validatorn accepterar — minimum är 1 hero. Inte allt är problem.

**Alcidae-batch lärdomar (2026-05-06):**

- 3/7 abundance:allmän approved (Q212055 tobisgrissla, Q27102 tordmule, Q21062 sillgrissla — alla häckar längs SE/Östersjökusten). Övriga 4: Q26685 lunnefågel (västkust-rar), Q26470 alkekung (arktisk), Q728682 spetsbergsgrissla (arktisk), Q189193 garfågel (utdöd 1844 — inte pipeline-fixat, default `ovanlig` räcker).
- **0% sparse-overrides** — alla 7 alcidae har full svwiki + enwiki, inga `description_accept_missing` behövdes. Trots att 4 är arktiska/utdöda har de god wiki-coverage (kulturellt välkända fåglar). Förväntad mönster för "ikoniska" arter.
- **Wikidata SPARQL transient 502 igen:** Q26685 + Q212055 fick `502 Bad Gateway` på första körningen. Retry separat löste båda. Mönstret upprepar sig: vid familj-fetch med flera 502:or, retry den failade SET som en grupp. **Pipeline-förbättring kandidat (icke-blockerande):** retry-with-backoff i `WikidataClient.fetch_taxon` skulle eliminera manuell retry. Inte kritiskt — lärdomen är att 502:or är transient och retry alltid funkar.
- **Gradle UP-TO-DATE-bug observerad:** efter ny alcidae-data var `buildSpeciesDb` UP-TO-DATE trots att YAML-filer var nya. Workaround: `./gradlew :shared:content:buildSpeciesDb --rerun-tasks`. Roten: input-fingerprintet på buildSpeciesDb fångar inte upp nya filer i `shared/content/species/<family>/` korrekt — sannolikt en `@InputDirectory` som inte rekursiverar, eller `@SkipWhenEmpty` på fel nivå. **Icke-blockerande för Plan 2b** (workaround funkar), men **bör fixas före Plan 6 (Play Store-release)** eftersom CI-builds skulle få samma symtom.

**Alcedinidae-batch lärdomar (2026-05-06):**

- 1/6 abundance:allmän approved (Q79915 kungsfiskare — Sveriges enda häckande kungsfiskare). Övriga 5 är afrikanska/asiatiska arter, ovanliga.
- **3/6 (50%) sparse-text-overrides** — alla tre exotiska arter (Q1270808 Halcyon leucocephala, Q21127307 Corythornis cristatus, Q735158 Halcyon smyrnensis) saknar svensk Wikipedia helt (svwiki revision ID:n går vidare men extracten är 0 ord). Förväntat för icke-skandinaviska arter; mönstret kommer återkomma i alla "exotic" familjer (afrikanska/sydostasiatiska).
- **Wikidata SPARQL transient 502:** Q735158 fick `502 Bad Gateway` på första försöket. Retry på enskild art löste det utan kod-ändring. Pipeline-mönster: vid familj-failure med transient-pekare, retry den failade arten separat innan man eskalerar.
- **Inga pipeline-fixar behövdes.**
- Cost: ~$0.011 / 6 arter (4 Claude calls första körningen + 2 retry för Q735158).

**Ardeidae-batch lärdomar (2026-05-07):**

- 2/16 abundance:allmän approved (Q25709 Rördrom — häckar i SE-vassmiljöer, Q25273 Gråhäger — vanlig året runt). Övriga 14 är ovanliga vagrants/exotics.
- **7/16 (44%) sparse-overrides [sv]** — afrikanska/asiatiska hägrar saknar svwiki helt eller har för korta extracts. Q498428 Damrallhäger hade 71w (under 80-tröskel). Inga `[en]`-overrides — enwiki har god coverage av icke-europeiska hägrar.
- **Pipeline-bugg upptäckt + fixad:** `WikidataClient` returnerade `familyLabel = "heron"` istället för `"Ardeidae"` för Ardea cinerea (P171*-traversal genom common-name-label). Resultat: 11 första-batch-YAMLs hamnade i `species/heron/` med `family: heron` istället för `species/ardeidae/` + `family: Ardeidae`. **Fix:** `orchestrator.py:refresh_one` prioriterar nu `listed.get("family")` (IOC-källa från species_list.yaml) över `wd.family` för både `family_dir` och `family`-fältet i YAML. Existerande 11 YAMLs flyttades manuellt + sed-fix på `family:`-fältet. De 5 retry'ade arterna efter fixen skrev till rätt katalog från start. Commit: pipeline-fix + data-batch som separata commits så fixen kan refereras isolerat. **Återanvändbart pattern:** alla `wd.X`-värden där species_list har auktoritativ IOC-data (family, ioc_order) ska gå genom `listed.get("X") or wd.X`.
- **Wikidata 502 igen:** 5/16 första-fetch failed (Q888536, Q132482576, Q191394, Q27074615, Q220578); retry som grupp efter pipeline-fix löste alla 5.
- **Click multi-arg-bug bekräftad igen:** `--species Q1,Q2,Q3` returnerar `No species matched filter`; använd `--species Q1 --species Q2 --species Q3`.
- Cost: ~$0.029 / 16 arter (11 första-batch + 5 retry).

**Apodidae-batch lärdomar (2026-05-06):**

- 1/9 abundance:allmän approved (Q25377 Tornseglare — Sveriges enda allmänna seglare, häckar i hela landet). Övriga 8 är afrikanska/medelhavs/Atlantö-endemics.
- **3/9 (33%) sparse-overrides** — Q772286 (sv), Q1096617 (sv), Q1264567 (sv, en). Mönstret matchar tidigare exotic-familjer.
- **Click multi-arg-bug:** `--species Q1,Q2,Q3` (komma-separerad) returnerade `No species matched filter; nothing to do.` istället för att fetch:a. Workaround: använd flera `--species`-flaggor (`--species Q1 --species Q2 --species Q3`). Det här är en lokal click-version-bug (inte runbook-dokumenterat tidigare). **Pipeline-förbättring kandidat:** verifiera CLI-`--species`-parser — den ska handle:a både komma-separerad och multi-flag enligt runbook §workflow steg 3.
- **Wikidata 502 igen:** Q1264567 + Q1096617 first-fetch failed; retry-as-pair löste båda. Pattern bekräftat över alla familjer hittills.
- Cost: ~$0.013 / 9 arter.

**Anhingidae-batch lärdomar (2026-05-06):**

- 0/1 abundance:allmän — Q387379 Afrikansk ormhalsfågel är en sällsynt afrikansk vagrant; default `ovanlig` är korrekt. Familjen har bara 1 art i species_list.
- **100% sparse-overrides ([sv, en])** — svwiki saknas helt, enwiki-lead är för kort. Förväntat för afrikanska arter; mönstret matchar Q55111925 Rüppellgam (även afrikansk vagrant).
- **Wikidata SPARQL transient 502 igen:** första försöket på Q387379 fick `502 Bad Gateway`. Retry löste det. Pattern: 502:or är genuint transient — retry alltid funkar utan kod-ändring.
- **Single-species + båda-språk-gap-mönster:** snabbaste batch hittills (~2 min inkl 502-retry). Workflow: fetch → validator failar med båda språk → lägg `[sv, en]` i overrides → bumpa count + content-gaps → done.

**Aegithalidae-batch lärdomar (2026-05-06):**

- 1/1 abundance:allmän approved (Q170831 stjärtmes — Europas enda stjärtmes-art, vanlig i löv- och blandskogar). Hela "familjen" är en enda art i SE/Norden.
- **0% sparse-overrides** — fyllig svwiki + enwiki, hero auto-pick OK (klar identifierbar fågel). Snabbaste familjbatch hittills: ~1 minut total tid (1 fetch + 1 approve + 1 commit).
- **Single-species-familj-mönster:** vissa familjer i species_list har bara 1–2 arter i Norden. Workflow är samma men ingen anledning till manuell hero-review HTML — direkt approve efter spot-check av YAML är OK.

**Anatidae-batch lärdomar (2026-05-06):**

- 17/53 abundance:allmän approved (sjöfåglar är väletablerade i SE — knölsvan, sångsvan, gräsand, kricka, gravand, ejder, knipa, vigg, brunand, skedand, snatterand, bläsand, stjärtand, kanadagås, vitkindad gås, grågås, smås/storskrake). Hero-pick bulk-approved efter spot-check av filnamn (alla matchar art-i-naturmiljö, inga museum/illustrations).
- **9/53 (17%) sparse-text-overrides** — sjöfåglar har bättre svwiki/enwiki-coverage än rovfåglar (47% accipitridae) och lärkor (48% alaudidae). Bekräftar förväntan från `project_plan_2b_status.md`.
- **Q28106966 Snatterand (allmän) extremfall:** båda Wikipedia-leadparagrafer = 1 mening. Verifierat direkt mot Wikipedia API (sv=21w, en=15w) — inte pipeline-bug, genuin upstream-glugg trots att det är en vanlig art. `description_accept_missing: [sv, en]` + `(allmän — prioriterad)` i content-gaps.md för manuell text framåt. UI:n hanterar redan detta sparse-fall (Plan 3 fallbacks).
- **Inga pipeline-fixar behövdes** — tidigare lärdomar (ALLOWED_IMAGE_EXTS, gsrlimit=50, REJECT_PATTERNS, refresh_one partial-rerun) räckte. Familj +52 arter levererades utan en enda kod-commit.
- **Inga `commons_search_name`-overrides behövdes** trots flera nyligen omstrukturerade genus (Mareca, Spatula från Anas; Aythya). Commons har hängt med på sub-familjenivå för anatidae.

**Alaudidae-batch lärdomar (2026-05-04):**

- 2/27 abundance:allmän approved (Q25961 sånglärka, Q26969 trädlärka). Andra sångfågel-vana lärkor (berglärka, dvärglärka, korttålärka, tofslärka) bedömda `ovanlig` i SE — sparvfinklärkan/finklärkor och ökenarter är overwhelmingly södra/asiatiska.
- **13/27 (48%) sparse-text-overrides + 8/27 (30%) image-coverage-overrides** — alaudidae är extremfall: många öken/Asien-endemics med tunn coverage på både svwiki och Commons. 5 arter (Q1083050, Q1092087, Q110812143, Q55112126, Q966703) behöver båda overrides — text- och bildgapen korrelerar för sparse-rariteter.
- **Pillow-krasch på .webm:** Q25961 (sånglärka) trasslade pipelinen — Commons-search returnerade `Galerida cristata, South Hebron.webm` som top-2-kandidat (fel art till) och ImageProcessor kraschade på `Pillow.Image.open`. Fixat i `c803ed1` med `ALLOWED_IMAGE_EXTS`-frozenset (Pillow-decodable raster only). SVG distribution-kartor filtreras av samma gate. Återanvändbart för framtida familjer.
- **gsrlimit=20 → 50 i `c803ed1`:** Q26969 (trädlärka, allmän) hittade 0 candidates — top-20 var museum-specimens + sub-MIN_DIMENSION-foton. Bumpning till 50 gav 3 användbara kandidater. Sparse-arter med genuint tunt Commons-utbud (Q890903 Rasolärka, Q31874488 Brunkronad lärka) berörs inte — `allow_missing_images` kvar.
- **Stort flag för datakvalitet:** alaudidae är en utfallsfamilj där upstream-datat saknar svenska/engelska beskrivningar för >50% av arterna. För Plan 3 UI-design måste vi visa "beskrivning kommer" på ett sätt som fungerar för en stor andel arter; det är inte en sällsynt edge case.

## Hur du startar en ny session och tar upp tråden

1. Öppna projektet i Claude Code: `cd /c/Users/abbea/dev/birdy-bird-scanner`.
2. Säg: "Vi fortsätter Plan 2b — nästa familj enligt runbook."
3. Claude läser CLAUDE.md + den här filen automatiskt.
4. Verifiera state:
   ```bash
   git log --oneline -5
   cat shared/content/expected-species-count.txt
   ls shared/content/species/  # vilka familjer som har YAML
   ```
5. Pick nästa familj från tabellen ovan. Jämför mot `tools/content-pipeline/species_list.yaml` för Q-IDs.

## Per-family loop (do this ~25-30 times)

1. Pick the next family (alphabetical from the status table; start `accipitridae`).
2. List Q-IDs in that family from `species_list.yaml`:
   ```bash
   uv run python -c "import yaml,sys; rows=yaml.safe_load(open('species_list.yaml',encoding='utf-8')); print('\n'.join(r['wikidata_id'] for r in rows if r.get('family','').lower()=='accipitridae'))"
   ```
3. Decide which species are genuinely "allmän" (common in Sweden) and add `abundance: allmän` to those rows in `species_list.yaml`. **Default is `ovanlig`** — promote only after deliberate review. (Source of truth: SOF-Birdlife rödlista + Artdatabanken; for Plan 2b we go by Wikipedia/SOF gut-check per species.)
4. Run: `uv run birdy-fetcher refresh --species Q... --species Q... --max-cost 0.30`.
5. Open `tools/content-pipeline/hero_review/{Q-ID}.html` in a browser for any species you want to spot-check (the orchestrator now generates these for every species during refresh). For `abundance: allmän` species the validator requires `review_status: approved`, so visual hero-check is mandatory; for `ovanlig` it's optional.
6. To override the auto-picked hero: copy the chosen filename into `shared/content/overrides.yaml` under `species.{Q-ID}.image_refs[0].commons_filename` (override format TBD — currently only `description_accept_missing` and `allow_missing_images` are supported, see `ValidateMain.kt:8-16`).
7. **Sparse-Wikipedia hantering:** om en art får `description.<lang>: ""` efter refresh (svwiki/enwiki under `SPARSE_WORD_THRESHOLD = 20`) lägg till `description_accept_missing: [<lang>]` i `shared/content/overrides.yaml` och en post i `docs/superpowers/runbooks/content-gaps.md`. Validatorn skippar då 80-ords-regeln för det språket. Manuell text fylls i efter Plan 3+ (UI-utveckling färdig).
8. For approved species (typically `allmän`): edit each YAML and set `review_status: approved` + add a one-line `review_notes`. **Gör detta som SISTA steg efter alla `refresh`-anrop** — partial-rerun bevarar review_status, men en `--field=all`-körning resettar till `auto`.
9. Spot-check 2-3 random YAML files: description reads OK, no hallucinations, image shows the right bird.
10. Update `shared/content/expected-species-count.txt` to current cumulative count.
11. Run: `./gradlew :shared:content:validateSpeciesData :shared:content:buildSpeciesDb :composeApp:assembleDebug` (with `JAVA_HOME` exported per CLAUDE.md).
12. Commit: `data(content): family <name> — N species (M approved, K auto)`. Update the status table in this file in the same commit.
13. Push to `main`. (No PR review for solo dev workflow; user reviews diffs locally before push.)

## Closeout

Once all ~700 species are committed and the validator/build green:

- `expected-species-count.txt` = ~700 (exact number)
- `git tag v0.2.0-content && git push --tags`
- Update CLAUDE.md status to "Plan 2 klar; nästa: Plan 3 Encyclopedia".

## Pre-Plan 2b prerequisites — status

Address (or accept) these before scaling out the backfill:

- ✅ **Wikidata `P1705` gap** — fixed in `237e9a5` (now fetches `rdfs:label@sv` for taxon + family). 8/8 paridae backfill species got Swedish names without manual species_list edits.
- ✅ **Hero_review wiring** — fixed in `deff31d`. Orchestrator now writes `tools/content-pipeline/hero_review/{Q-ID}.html` for every refreshed species.
- ✅ **Abundance heuristic** — fixed in `deff31d`. Old code mapped `vp_status=H` → `allmän` blindly; that wrongly promoted local rarities (Lappmes, Hyrkanmes, …) to allmän. New default is `ovanlig`; promote per-species via `abundance:` field in `species_list.yaml`.
- ✅ **Pipeline partial-rerun preservation** — fixed in `1bac05d`. Tidigare: `--field text/images` rebuildade SpeciesYamlData från noll → tömde icke-rörda fält. Nu: `refresh_one` läser existerande YAML när `--field` ≠ `all` och bevarar de andra sektionerna + `review_status`/`review_notes`.
- ⏳ **Few-shot prompts** — `tools/content-pipeline/prompts/description-v1.md` har placeholder Koltrast + Blåmes-exempel. 13/13 hittills accepterar enstaka Talgoxe-exempel; defer-OK. Fyll i om kvalitet sjunker i en framtida familj.
- ⏳ **Pipeline hardening (Task 8 follow-ups I1, I2, I4, I5)** — see CLAUDE.md. I2 (decompose `refresh_one`) gives debugbarhet at scale; nice-to-have, not blocking.

## Cost watch

Cumulative Claude budget for the full backfill: ~$5. Use `--max-cost` per run, sum tracking via `birdy-fetcher status` after each batch.

**Faktisk cost-data hittills (Haiku 4.5, $0.80/M input + $4.00/M output):**

| Familj | Δ arter | Claude-calls | ~Cost | Per art |
|---|---|---|---|---|
| walking skeleton | 5 | 10 (2 per art) | ~$0.023 | ~$0.005 |
| paridae | 8 | 16 | ~$0.018 | ~$0.002 |
| accipitridae | 38 | ~50 (sparse skip ~26 calls) | ~$0.27 | ~$0.007 |
| acrocephalidae | 19 | 64 (sparse skip ~12 calls) | ~$0.064 | ~$0.003 |
| alaudidae | 27 | ~36 (sparse skip ~18 calls) | ~$0.04 | ~$0.0015 |
| anatidae | 52 | ~95 (sparse skip ~10 calls) | ~$0.10 | ~$0.002 |

Cumulativt: 149 arter / ~$0.52. Vid ~700 arter och 17–48% sparse-rate landar vi på ~$3–4 totalt — väl under budget. Sparse-arter (overrides) bidrar nästan inte till kostnaden eftersom Claude inte anropas alls för det språket.

## Relaterade runbooks

- `docs/superpowers/runbooks/content-gaps.md` — tracker för arter med saknade beskrivningar (sparse Wikipedia). Listan fylls i manuellt **efter** Plan 3+ (UI färdig).
- `docs/superpowers/runbooks/milstolpe-review.md` — review-flödet med 5–6 parallella granskningsagenter när en stor milstolpe ska closeoutas.
