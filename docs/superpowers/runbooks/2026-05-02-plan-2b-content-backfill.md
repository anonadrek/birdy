# Plan 2b — Content backfill runbook

Plan 2a delivered the pipeline + walking skeleton. Plan 2b is the work of running the fetcher across all ~700 species, reviewing output family-by-family, and committing.

## Status

| Datum | Familj | Arter | Commit |
|---|---|---|---|
| 2026-05-02 | (walking skeleton) | 5 | `d973e31` |
| 2026-05-04 | paridae | +8 (totalt 13) | `f8cc17f` |
| 2026-05-04 | accipitridae | +38 (totalt 51) | `1ed1895` |
| | _next: acrocephalidae_ | | |

Cumulative species count tracked in `shared/content/expected-species-count.txt`.

**Accipitridae-batch lärdomar (2026-05-04):**

- 5/38 abundance:allmän approved efter visuell hero-check (Sparvhök Q25380, Ormvråk Q25385, Havsörn Q25438, Fjällvråk Q26407, Brun kärrhök Q26431).
- **18/38 (47%) behövde `description_accept_missing`-override** för minst ett språk (svwiki sparse). Räkna med ~30–50% override-rate på rovfågel-/sjöfågel-familjer; lättare i tätt-bevakade tättingfamiljer. Tracker: `docs/superpowers/runbooks/content-gaps.md`.
- **Pipeline partial-rerun-bug fixad i `1bac05d`:** `--field text/images` byggde tidigare SpeciesYamlData från noll → tömde icke-rörda fält (image_refs eller description). `refresh_one` läser nu existerande YAML när `--field` ≠ `all` och bevarar de andra sektionerna + `review_status`/`review_notes`. **Konsekvens för loopen:** sätt `review_status: approved` som SISTA steg innan commit (efter alla `refresh`-anrop), annars riskerar du nästa `--field`-körning skriva över din review.
- **REJECT_PATTERNS utökad** för plural-kategorier ("Bird illustrations", "(museum specimens)", "Taxidermied birds") + historiska tryck (Iconographia, Hardwicke, Wellcome chromolithographs incl. trunkerad form).
- **`commons_search_name`-override i `species_list.yaml`** löser genus-renames där Commons-kategorier inte hängt med (Astur gentilis → search "Accipiter gentilis"). Pattern återanvändbart för Botaurus, Tachyspiza i framtida familjer.

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

Vid ~700 arter och 30–40% sparse-rate landar vi på ~$3–4 totalt — väl under budget. Sparse-arter (overrides) bidrar nästan inte till kostnaden eftersom Claude inte anropas alls för det språket.

## Relaterade runbooks

- `docs/superpowers/runbooks/content-gaps.md` — tracker för arter med saknade beskrivningar (sparse Wikipedia). Listan fylls i manuellt **efter** Plan 3+ (UI färdig).
- `docs/superpowers/runbooks/milstolpe-review.md` — review-flödet med 5–6 parallella granskningsagenter när en stor milstolpe ska closeoutas.
