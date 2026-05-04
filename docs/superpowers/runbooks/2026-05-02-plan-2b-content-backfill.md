# Plan 2b — Content backfill runbook

Plan 2a delivered the pipeline + walking skeleton. Plan 2b is the work of running the fetcher across all ~700 species, reviewing output family-by-family, and committing.

## Per-family loop (do this ~25-30 times)

1. Pick the next family (start with `paridae` extended, then alphabetical).
2. List Q-IDs in that family from `species_list.yaml`.
3. Run: `uv run birdy-fetcher refresh --species Q... --species Q... --max-cost 0.30`
4. For `abundance: allmän` species in this family: open generated `tools/content-pipeline/hero_review/{Q-ID}.html` per species, approve hero or override.
5. Spot-check 2-3 random YAML files: description reads OK, no hallucinations, image shows the right bird.
6. Update `shared/content/expected-species-count.txt` to current cumulative count.
7. Run `./gradlew :shared:content:validateSpeciesData :shared:content:buildSpeciesDb :composeApp:assembleDebug`.
8. Commit: `data(content): family <name> — N species (M approved, K auto)`.
9. Push as a small PR for review.

## Closeout

Once all ~700 species are committed and the validator/build green:

- `expected-species-count.txt` = ~700 (exact number)
- `git tag v0.2.0-content && git push --tags`
- Update CLAUDE.md status to "Plan 2 klar; nästa: Plan 3 Encyclopedia".

## Pre-Plan 2b prerequisites (from Task 8/13 follow-ups)

Before kicking off `--all` on ~700 species, address these or accept the risks:

- **Few-shot prompts** — `tools/content-pipeline/prompts/description-v1.md` has placeholder Koltrast + Blåmes examples. Either fill them in (180-250 ord, samma struktur som Talgoxe) or accept single-example prompts.
- **Wikidata `P1705` (common_sv) gap** — 834/836 species lack Swedish common name in Wikidata. Decide on fallback strategy (use Swedish wikipedia title? defer to manual review?).
- **Pipeline hardening** — Task 8 follow-ups I1, I2, I4, I5 + minors M1-M6 (see CLAUDE.md). I1+I2 give debugbarhet at scale, I4+I5 give schema stability for Plan 3.

## Cost watch

Cumulative Claude budget for the full backfill: ~$5. Use `--max-cost` per run, sum tracking via `birdy-fetcher status` after each batch.
