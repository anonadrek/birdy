# birdy-fetcher

Content pipeline CLI for Birdy Bird Scanner. Generates `shared/content/species/**/*.yaml` and `shared/content/images/**/*.jpg` from IOC, BirdLife, Wikidata, Wikipedia, Wikimedia Commons, and Anthropic Claude.

## Setup (one-off)

1. Install Python 3.12 and uv (`winget install Python.Python.3.12`, `winget install --id=astral-sh.uv`).
2. Copy `.env.example` to `.env`, paste your Anthropic API key.
3. Source files at `sources/ioc-14.1.xlsx` (IOC World Bird List v14.1) and `sources/vp11.pdf` (BirdLife Sverige TK Västpalearktis-lista v11) — both committed to repo.
4. From this directory: `uv sync`.

## Common commands

```bash
uv run birdy-fetcher doctor                       # verify env + sources
uv run birdy-fetcher init                         # build species_list.yaml
uv run birdy-fetcher refresh --species Q25485     # one species end-to-end
uv run birdy-fetcher refresh --all --max-cost 5   # full refresh with cost cap
uv run birdy-fetcher refresh --dry-run --species Q25485  # plan only
uv run birdy-fetcher status                       # coverage report
```

## Dev loop

```bash
uv run pytest               # tests
uv run ruff check           # lint
uv run ruff format          # format
uv run mypy                 # type check
```
