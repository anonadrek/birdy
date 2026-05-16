# Test Image Infra — match_override.txt

DEBUG-only mechanism for deterministic Match/Disambig/NoBird screenshots
when real TFLite confidences don't naturally land in the desired band.

## Format

Single line, `qid:confidence`. Examples:
- `Q25356:0.42` → Disambig band (talgoxe, conf inside 0.35–0.50)
- `Q25356:0.65` → Match band (high confidence)
- `Q25356:0.05` → NoBird band (very low)

## Push override

```bash
echo "Q25356:0.42" > /tmp/override.txt
adb push /tmp/override.txt /data/local/tmp/match_override.txt
adb shell run-as se.birdy.android cp /data/local/tmp/match_override.txt files/match_override.txt
```

(`/data/data/.../files/` is not directly writable by adb without run-as on
API 30+; staging via `/data/local/tmp/` and using `run-as cp` is the
standard pattern.)

## Verify it's read

Trigger Scan → MatchResult flow. The result screen should render in the
band corresponding to the overridden confidence (Disambig for 0.35–0.50,
Match for ≥ 0.50, NoBird for < 0.35).

The override is read on every `MatchResultViewModel.resolve()` (i.e.
every navigation to MatchResult). The file is NOT deleted after read —
the same override applies to repeated scans until removed.

## Clear override

```bash
adb shell run-as se.birdy.android rm files/match_override.txt
```

## Common QIDs for testing

| qid | species |
|---|---|
| Q25356 | Parus major (talgoxe) |
| Q133348 | Turdus merula (koltrast) |
| Q133376 | Cyanistes caeruleus (blames) |
| Q183670 | Erithacus rubecula (rödhake) |

## Release-build behavior

`AppGraph.matchOverrideReader == null` on release builds. The lambda
is never instantiated; `MatchResultViewModel` falls through to real
classifier output. Safe to ship — no test-only behavior leaks into
production.
