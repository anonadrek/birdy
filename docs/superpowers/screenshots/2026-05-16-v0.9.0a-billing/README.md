# Device screenshots — v0.9.0a-billing (Plan 6b1)

**Device:** SM-S918B (Galaxy S23 Ultra), Android 14 (API 35)
**Build:** signed release AAB → bundletool 1.18.1 install
**Date:** 2026-05-16

Numbered checkpoints from Plan 6b1 Task T10 (`docs/superpowers/plans/2026-05-16-v1-06b1-billing-launch-prep.md` lines 2087-2185). Full step-by-step ADB session: `docs/superpowers/runbooks/2026-05-16-v0.9.0a-billing-device-verify.md`.

## Canonical shots

| # | Filename | Source step |
|---|---|---|
| 01 | `01-purchase-yearly-complete.png` | T10.4 — Yearly purchase succeeds, app shows Active(YEARLY) |
| 02 | `02-restore-purchases-success.png` | T10.5 — after `pm clear`, Settings → Restore restores entitlement |
| 03 | `03-cold-start-no-modal-fresh.png` | T10.6 — fresh install, cold-start 3× shows no Premium modal (7d grace) |

## Optional / supplementary

| # | Filename | Purpose |
|---|---|---|
| 04 | `04-settings-restore-row.png` | T5 row visible in Settings ABOUT BIRDY group |
| 05 | `05-premium-no-savings-stamp.png` | T6 — "spara 60%" stamp confirmed removed; auto-renew disclosure visible under Yearly |
| 06 | `06-premium-en-currency-fix.png` | T6 — EN locale shows "199 SEK / year" (not "199 kr") |
| 07 | `07-talkback-premium-walkthrough.png` | T9 — TalkBack focus indicator on Premium Continue button |

Grace-period progression (Cold-start modal at >7d post-install) can't be device-verified without time-travel — note that in commit message instead of capturing.
