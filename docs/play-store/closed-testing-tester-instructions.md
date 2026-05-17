# Birdy — Closed Testing Instructions

**Build:** v0.9.0a-billing (versionCode 110, versionName 1.0.0-rc2)
**Track:** Closed Testing — "Birdy launch testing"
**Testing window:** 14 days (Google requirement before production)

Thank you for testing Birdy! This is an AI-powered field journal for
birders. Below is what to focus on — both happy paths and the things
that are most likely to break.

## How to install

1. Accept the tester invite via the email link
2. Visit the opt-in URL and click "Become a tester"
3. Install Birdy from Google Play (it will appear in your Play store
   after opt-in — may take a few minutes to propagate)
4. Open and walk through the 3 onboarding pages

## What to test (10–15 min — short version)

### Core flow (priority 1)
- [ ] **Onboarding** — three pages, paper background, "Skip" works
- [ ] **Camera scan** — grant camera permission; point at a bird photo
      on a screen or printed image; tap-to-freeze; confidence chip
      appears top-right
- [ ] **Save a sighting** — after a match, tap Save → snackbar +
      observation appears under "Field Journal" tab
- [ ] **Browse encyclopedia** — "Atlas" tab → tap a species → photo +
      text + marginalia render
- [ ] **Badges** — "Stamps" tab → first save should unlock at least
      one stamp; bottom-sheet appears

### Premium purchase flow (priority 1 — new in this build)
- [ ] Open **Settings** (gear icon top-right on Listen launcher)
- [ ] Tap **Premium →** banner → Premium screen opens
- [ ] Pick **Yearly** tier → tap **Continue** → Google Play purchase
      sheet opens
- [ ] Use a **test payment method** (your tester account is configured
      as a Play Console license tester — purchases are FREE; no real
      money charged)
- [ ] After purchase: app returns to Premium screen, then dismisses;
      Settings now shows "Premium" badge
- [ ] **Quit and relaunch** — Premium state should persist
- [ ] **Settings → Restore purchases** — should re-fetch entitlement
      and show "Purchase restored." toast

### Language switching (priority 2)
- [ ] System language SV → app text is Swedish; switch system to EN →
      app text is English on next relaunch
- [ ] In Settings → Language picker is a placeholder ("TODO Plan 6")
      — known limitation, don't worry about it

### Edge cases (priority 3)
- [ ] **Camera permission denied** → settings hint shown; tap "Open
      settings" should open Android settings
- [ ] **Force-quit during scan** → no crash on relaunch
- [ ] **Airplane mode** → app still works fully (offline-first)
- [ ] **Disable accessibility/TalkBack** → enable TalkBack →
      Premium → all buttons announced sensibly

## What's known-broken / out of scope

- **Audio bird ID** — not in this build; will appear in a later release
- **Cloud sync / accounts** — never in v1; deferred to v1.5
- **Maps / location** — deferred to v1.5
- **Premium badges** — placeholders; full set ships in 6b2/6b3
- **PDF export + season statistics** — placeholders; full implementation
  ships in 6b2/6b3 (you'll see "Unlock" rows for now)

## How to report a bug

Use **Settings → Feedback** (opens mailto: to feedback@birdy.app).
Include:
- What you were doing
- What you expected to happen
- What actually happened
- Device + Android version (Settings → About → device info)
- Screenshot if visible (Power + Volume Down)

For Google Play feedback (publicly visible to other testers), use the
"Send feedback" option in the Play Store listing — but please ALSO
email so I can follow up.

## Privacy reminder

Birdy makes **zero network calls** (other than Google Play Billing,
which Google handles). Everything else — model, encyclopedia, photos,
journal — lives on your device. Uninstalling deletes everything.

— Albin (solo developer)
