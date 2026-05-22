# Birdy — Google Play Data Safety form answers

This document captures the answers we will provide in the Google Play
Console **Data Safety** section. Keep in sync with code reality.

_Last reviewed: 2026-05-22 (Plan 6b2 v0.9.0b-audio + BirdNET license decision) — audio-ID shipped as free feature; RECORD_AUDIO permission added; no new data collection — recordings stay on-device_

## Data collection and security

### Does your app collect or share any of the required user data types?
**No.**

Birdy stores user data only on the device (SQLDelight database in the
app's private files dir, DataStore preferences, photos and audio
recordings in `filesDir/observations/`). The app makes no network
requests in v1 — neither for analytics, nor for image- or
audio-classification (the AIY Birds V1 + BirdNET-Lite TFLite models run
entirely on-device), nor for content fetches (encyclopedia text +
photos are bundled at build time).

The only data that ever leaves the device is via:
- Google Play Billing (purchase token; handled entirely by Google's
  SDK; we receive only purchase state, never PII).
- Android Auto-Backup to Google Drive (opt-in via system settings;
  data is encrypted in transit and at rest by Google).
- System share sheet (user-initiated; user picks the recipient).

None of these are "collection" or "sharing" by the Play Console
definition because the app does not transmit data to **our** servers
(we don't have any).

### Is all of the user data collected by your app encrypted in transit?
**N/A** — no data is collected.

### Do you provide a way for users to request that their data be deleted?
**Yes.** Uninstalling the app removes all device-local data. Inside the
app, users can long-press an observation to delete it individually.

## Data types (none selected)

We answer "No" to every "Does your app collect or share..." sub-question
in the form:

- Personal info: **No**
- Financial info: **No** (Google Play Billing handles purchases; we
  don't see or store financial data)
- Health and fitness: **No**
- Messages: **No**
- Photos and videos: **No** (user-supplied photos stay in app-private
  storage; not "collected" per Play Console definition)
- Audio files: **No** (user-recorded 3-second audio clips stay in
  app-private storage when attached to an observation; otherwise
  discarded after on-device classification; never transmitted)
- Files and docs: **No**
- Calendar: **No**
- Contacts: **No**
- App activity: **No** (no analytics, no crash logs sent anywhere)
- Web browsing: **No**
- App info and performance: **No**
- Device or other IDs: **No** (no advertising ID, no install ID, no
  device fingerprinting)

## Security practices

- Data encrypted in transit: **N/A** (no transit)
- Data deletion: users can request deletion via uninstall or
  in-app long-press delete
- Independent security review: **No**
- Committed to Google Play's Families Policy: **No** (target audience
  is 13+, not "directed to children")

## Permissions disclosed

- `android.permission.CAMERA` (foreground only) — used for
  on-device bird ID; frames discarded after classification.
- `android.permission.RECORD_AUDIO` (foreground only) — used for
  on-device bird-call ID (BirdNET-Lite, 3-second clips); audio is
  saved to your observation only if you tap save, otherwise discarded
  after classification. Never uploaded.

We do **not** declare:
- Photos permission (`READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE`):
  we use Android 13+'s `PickVisualMedia` which requires no permission
- Location permission: location_label is a text field, not GPS
- Network permission: not declared because we don't make network calls

## App access

If Google Play asks for "App access instructions" to test premium
features, provide:
- Debug build with `PREMIUM_DEBUG_FORCE_ACTIVE=true` to test premium
  UI without going through real billing
- Or use a test account configured as a Play Console license tester

## Diff log

- **2026-05-15** — initial form drafted alongside v0.8.0-rc1 prep.
  Status: app makes zero network calls, collects zero data.
- **2026-05-17** — re-reviewed for v0.9.0a-billing (Plan 6b1 T4 Google
  Play Billing v8 integration). No form changes needed: billing traffic
  is handled by Google's BillingClient SDK, not by Birdy — purchase
  tokens never reach our code (we only see acknowledged-entitlement
  state). No new permissions, no new data types, no new sharing.
- **2026-05-22** — re-reviewed for v0.9.0b-audio (Plan 6b2 audio-ID via
  BirdNET-Lite) + Option-A BirdNET-license decision. Added
  `RECORD_AUDIO` permission disclosure. Audio recordings are
  3-second clips classified on-device by BirdNET-Lite (CC BY-NC-SA
  4.0); saved to app-private storage only when user attaches to an
  observation, otherwise discarded. Zero network calls remain.
  Audio-ID ships as a free feature for all users, not Premium-gated
  (the BirdNET license forbids commercial gating of the model).
