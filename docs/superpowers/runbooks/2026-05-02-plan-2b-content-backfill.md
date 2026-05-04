# Plan 2b — Content backfill runbook

Plan 2a delivered the pipeline + walking skeleton. Plan 2b is the work of running the fetcher across all ~700 species, reviewing output family-by-family, and committing.

## Status

| Datum | Familj | Arter | Commit |
|---|---|---|---|
| 2026-05-02 | (walking skeleton) | 5 | `d973e31` |
| 2026-05-04 | paridae | +8 (totalt 13) | `f8cc17f` |
| | _next: accipitridae_ | | |

Cumulative species count tracked in `shared/content/expected-species-count.txt`.

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
7. For approved species (typically `allmän`): edit each YAML and set `review_status: approved` + add a one-line `review_notes`. For `ovanlig` species, `review_status: auto` is fine.
8. Spot-check 2-3 random YAML files: description reads OK, no hallucinations, image shows the right bird.
9. Update `shared/content/expected-species-count.txt` to current cumulative count.
10. Run: `./gradlew :shared:content:validateSpeciesData :shared:content:buildSpeciesDb :composeApp:assembleDebug` (with `JAVA_HOME` exported per CLAUDE.md).
11. Commit: `data(content): family <name> — N species (M approved, K auto)`. Update the status table in this file in the same commit.
12. Push to `main`. (No PR review for solo dev workflow; user reviews diffs locally before push.)

## Closeout

Once all ~700 species are committed and the validator/build green:

- `expected-species-count.txt` = ~700 (exact number)
- `git tag v0.2.0-content && git push --tags`
- Update CLAUDE.md status to "Plan 2 klar; nästa: Plan 3 Encyclopedia".

## Pre-Plan 2b prerequisites — status

Address (or accept) these before scaling out the backfill:

- ✅ **Wikidata `P1705` gap** — fixed in `237e9a5` (now fetches `rdfs:label@sv` for taxon + family). 8/8 paridae backfill species got Swedish names without manual species_list edits.
- ✅ **Hero_review wiring** — fixed in [next commit]. Orchestrator now writes `tools/content-pipeline/hero_review/{Q-ID}.html` for every refreshed species.
- ✅ **Abundance heuristic** — fixed in [next commit]. Old code mapped `vp_status=H` → `allmän` blindly; that wrongly promoted local rarities (Lappmes, Hyrkanmes, …) to allmän. New default is `ovanlig`; promote per-species via `abundance:` field in `species_list.yaml`.
- ⏳ **Few-shot prompts** — `tools/content-pipeline/prompts/description-v1.md` has placeholder Koltrast + Blåmes examples. Plan 2a's 5 + Plan 2b's 8 paridae produced acceptable output with the single Talgoxe example, so this is low-risk to defer. Fill in if quality drops in another family.
- ⏳ **Pipeline hardening (Task 8 follow-ups I1, I2, I4, I5)** — see CLAUDE.md. I2 (decompose `refresh_one`) gives debugbarhet at scale; nice-to-have, not blocking.

## Cost watch

Cumulative Claude budget for the full backfill: ~$5. Use `--max-cost` per run, sum tracking via `birdy-fetcher status` after each batch.
