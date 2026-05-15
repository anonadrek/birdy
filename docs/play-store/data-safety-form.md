# Birdy — Google Play Data Safety form answers

This document captures the answers we will provide in the Google Play
Console **Data Safety** section. Keep in sync with code reality.

_Last reviewed: 2026-05-15 (Plan 6a v0.8.0-rc1)_

## Data collection and security

### Does your app collect or share any of the required user data types?
**No.**

Birdy stores user data only on the device (SQLDelight database in the
app's private files dir, DataStore preferences, and photos in
`filesDir/observations/`). The app makes no network requests in v1 —
neither for analytics, nor for image-classification (the AIY Birds V1
TFLite model runs entirely on-device), nor for content fetches
(encyclopedia text + photos are bundled at build time).

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
- Audio files: **No**
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

We do **not** declare:
- Photos permission (`READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE`):
  we use Android 13+'s `PickVisualMedia` which requires no permission
- Location permission: location_label is a text field, not GPS
- Microphone permission: deferred to a future audio-ID release
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
