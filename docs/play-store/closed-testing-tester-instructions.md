# Birdy — Closed Testing Instructions

**Build:** v1.2.0-rc1 (versionCode 123)
**Track:** Closed Testing
**Testing window:** 14 days (Google requirement before production)

Thanks for testing Birdy — an AI-powered, privacy-first field journal for
birders. Everything runs on your device. Below is what to focus on, with the
**new v1.2 features first**.

## How to install / update

1. Accept the tester invite via the email link → opt-in URL → "Become a tester"
2. Install or update Birdy from Google Play (it may take a few minutes to appear)
3. Open it and scroll through the onboarding story (swipe up through the scenes)

## New in v1.2 — please test these first

### 🗺️ Personal finds map (the headline feature)
- [ ] **Settings (gear, top-right) → turn on "Save location with my finds"** →
      grant the location permission when asked
- [ ] Make a few finds with location on: **Look** (live camera), **Find**
      (take a photo OR pick one from your gallery), **Listen** (3-sec audio)
- [ ] Open the **Map** tab (bottom-right) → your finds should appear as
      wax-seal pins on a warm, sepia "field journal" map
- [ ] Tap a pin → it opens that find
- [ ] Notes: the map needs internet to load its tiles (it's the one place the
      app goes online, besides Google Play). Location capture is **off by
      default** and the coordinates **stay on your phone**.

### Other v1.2 changes
- [ ] **Reworked stamps** (Badges tab) — 34 in total, including a red-listed
      track and a life list up to 500 species; audio + seasonal stamps are now
      free. The **Trophy Room** card sits at the top.
- [ ] **Encyclopedia by ecological group** (Archive tab) — filter by group
      (auks, woodpeckers, doves, cranes & rails, …)
- [ ] **Weekly Recap** + **Chase the Bird of the Day** — the daily-bird card
      shows your progress; catch it for a free stamp.

## Core flows (regression)

- [ ] **Photo scan** — Identify → **Look** (live camera) or **Find**
      (gallery/photo). Try the **zoom** presets in live scan; for uploads you
      can **crop + rotate** before analysing.
- [ ] **Audio scan** — Identify → **Listen** → record a 3-second clip (e.g. a
      song from a speaker).
- [ ] **Save a find** → it appears in your **Lifelist / field journal**; a
      stamp may unlock (bottom-sheet appears).
- [ ] **Encyclopedia** — Archive → tap a species → photo + text + field-mark
      marginalia render.
- [ ] **Search** — try a species whose name has an apostrophe or accent — it
      should be found.

## Premium — note for this build

Premium is **open and free for every tester** in this build, so you'll see all
premium features unlocked: the **map view**, PDF export of your journal,
seasonal statistics, and the premium stamps. You don't need to buy anything.
(The purchase flow itself is verified separately before the public launch.)

## Edge cases

- [ ] **Camera / location permission denied** → a sensible hint is shown;
      "Open settings" works.
- [ ] **Force-quit during a scan** → no crash on relaunch.
- [ ] **Airplane mode** → scanning, the encyclopedia and the journal still work
      fully offline (only the map's tiles won't load).

## Known limitations / out of scope

- **Cloud sync & accounts** — not yet; the map is **private and on-device only**.
- **Community / sharing** — not yet.
- **iOS** — Android only for now.

## How to report a bug

Use **Settings → Feedback** (it emails albin@abrahamssons.se), or just email
that address directly. Please include:
- What you were doing
- What you expected to happen
- What actually happened
- Your device + Android version (Settings → About → device info)
- A screenshot if anything is visible (Power + Volume Down)

## Privacy

Birdy is privacy-first: the AI model, encyclopedia, photos and journal all live
on your device, and almost nothing is collected. The **only** network calls are
Google Play Billing (Google-handled) and — when you open the **Map** — fetching
map tiles from MapTiler. Your finds and their locations **never leave your
phone**. Uninstalling deletes everything.

— Albin (solo developer)
