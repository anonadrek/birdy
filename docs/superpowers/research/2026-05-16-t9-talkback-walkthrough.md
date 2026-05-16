# T9 — TalkBack walkthrough report

**Plan:** 6b1 (Billing v8 + launch-prep), Task T9.
**Date:** 2026-05-16.
**Device:** SM-S918B (Galaxy S23 Ultra), One UI 6 / API 35.
**TalkBack engine:** Samsung TalkBack
(`com.samsung.android.accessibility.talkback/com.samsung.android.marvin.talkback.TalkBackService`).
**Build:** `:androidApp:installDebug` from HEAD `6ab9d66` (T8 Path B + T4 hotfix
both landed).
**Status:** ✅ PASS — no P0/P1/P2 findings.

## Result

User Albin drove the 8-surface walkthrough end-to-end. All surfaces announced
correctly with no observed gaps:

- No unannounced focusable elements.
- No redundant/double-announced labels.
- No focus traps.
- No unexpected focus order.
- No mis-labelled elements.

This is the expected outcome given Plan 6a T9 a11y bumps already shipped
in `v0.8.0-rc1` (`4937ea2`): `MarginaliaInk` bumped to WCAG AA contrast,
`StampSeal` got state-aware `contentDescription` + `Role.Button`, `PlateFrame` /
`JournalHeadline` / `JournalSubLine` / `BottomNavBar` got `mergeDescendants`,
and `AsyncImage` cd-strings populated on Premium/Archive/SpeciesProfile. Plan
6b1 launch-prep only added Settings rows (Restore Purchases, About, Privacy,
Terms) which use standard Material 3 `ListItem` semantics — those passed
without custom a11y plumbing.

## Surfaces walked

| # | Surface | Notes |
|---|---|---|
| 1 | Listen launcher (3 launch cards + gear) | ✅ |
| 2 | Settings (Premium hero card + ACCOUNT + ABOUT BIRDY incl. Restore Purchases) | ✅ |
| 3 | Premium screen (headline + 4 stamp-bullets + Yearly + Lifetime + Continue + close X) | ✅ |
| 4 | Scan (top species chip + crosshair + bottom-nav) | ✅ |
| 5 | Archive / Encyclopedia (search + family chips + species row) | ✅ |
| 6 | Diary (empty state OR month/observation row) | ✅ |
| 7 | Badges (hero stats + Recently discovered carousel + 5×5 stamp grid) | ✅ |
| 8 | Match-flow (optional — Photo → pick → result) | ✅ |

## Process notes

- First ADB command `settings put secure enabled_accessibility_services
  com.google.android.marvin.talkback/…` had no effect because Samsung S23
  Ultra ships **Samsung TalkBack**, not Google TalkBack. The Google package
  isn't even installed (`pm list packages | grep talkback` shows only
  `com.samsung.android.accessibility.talkback`). Correct package path:
  `com.samsung.android.accessibility.talkback/com.samsung.android.marvin.talkback.TalkBackService`
  — class name `com.samsung.android.marvin.talkback.TalkBackService` is the
  same as Google's (Samsung forked the binary).
- Disabling via `settings put secure enabled_accessibility_services ""`
  fails with "Bad arguments" on Samsung; use `settings delete secure
  enabled_accessibility_services` instead, paired with
  `settings put secure accessibility_enabled 0`.

## Follow-ups

None. T9 closed. Proceed to T10 (signed-AAB device-verify + tag).
