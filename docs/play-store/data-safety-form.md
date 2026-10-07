# Birdy: Google Play Data Safety form answers

This document captures the answers we will provide in the Google Play
Console **Data Safety** section. Keep in sync with code reality.

Birdy is developed and operated by **AlbIT AB** (org. no. 559593-7607,
Solna, Sweden).

_Last reviewed: 2026-10-07 (v1.3.0): Premium sold through Google Play Billing (yearly subscription or lifetime purchase), sound ID records up to 60 seconds, early users recognised on the device from the install time or the phone's network time, local notifications listed under the permissions. No form answer changes. See Diff log entries 2026-10-05 and 2026-10-07._

## Data collection and security

### Does your app collect or share any of the required user data types?
**No**, with one nuance: the app now makes network requests to fetch
map tile imagery from MapTiler (a third-party tile provider), but
**user observations and their coordinates are never sent**.

Birdy stores user data only on the device (SQLDelight database in the
app's private files dir, DataStore preferences, photos and audio
recordings in `filesDir/observations/`). The AI classifiers (AIY Birds
V1 + BirdNET-Lite TFLite) run entirely on-device. Encyclopedia text
and photos are bundled at build time.

The only data that ever leaves the device is via:
- **Map tile requests** (new in v1.2): when a user views the personal
  finds map, the app fetches map imagery (tiles) from MapTiler. These
  requests reveal the **map viewport** (approximate geographic area
  being viewed) to MapTiler, which is standard for any map SDK. The
  user's bird observations and their stored coordinates are **never
  included** in tile requests and are **never transmitted** to
  MapTiler or anyone else.
- Google Play Billing (purchase token; handled entirely by Google's
  SDK; we receive only purchase state, never PII).
- Google Play In-App Review (the prompt that asks the user to rate
  Birdy; handled entirely by Google).
- Android Auto-Backup to Google Drive (opt-in via system settings;
  data is encrypted in transit and at rest by Google).
- System share sheet (user-initiated; user picks the recipient).

None of these constitute "collection" or "sharing" by the Play Console
definition because the app does not transmit user data to **our**
servers (we don't have any). Tile requests to MapTiler convey map
viewport only, no user-identifiable observation data.

### Is all of the user data collected by your app encrypted in transit?
**Not asked.** Play Console shows the security questions only when an
app collects or shares user data, and Birdy answers **No** above. For
the record: map tile requests (HTTPS to MapTiler) are encrypted in
transit and carry only the map viewport, no user data.

### Do you provide a way for users to request that their data be deleted?
**Not asked** either, for the same reason. For the record: uninstalling
the app removes all device-local data, and users can delete a single
observation with Delete on its page.

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
- Audio files: **No** (user-recorded audio of up to 60 seconds is
  classified on-device and kept only in app-private storage; never
  transmitted)
- **Location: No**. The app reads the device location (approximate +
  precise) only when the user has enabled "Save location with my finds"
  (Settings toggle, **off by default**). When enabled, the coordinates
  of a saved observation are stored **only in the device-local
  database**. Location data is **never transmitted** to our servers
  or to any third party. Map tile requests go to MapTiler over HTTPS
  but carry only the map viewport, never the stored observation
  coordinates. Per Play Console definitions this is **not
  "collection"** (data is not shared with or accessible by us or our
  servers). Answer on the form: **No**.
- Files and docs: **No**
- Calendar: **No**
- Contacts: **No**
- App activity: **No** (no analytics, no crash logs sent anywhere)
- Web browsing: **No**
- App info and performance: **No**
- Device or other IDs: **No** (no advertising ID, no install ID, no
  device fingerprinting)

## Security practices

- Data encrypted in transit: **not asked** in the form (see "Data
  collection and security" above). For the record: map tile requests
  use HTTPS, no user data is transmitted, and observation data
  (including optional location) never leaves the device.
- Data deletion: **not asked** in the form. For the record: uninstall,
  or Delete on an observation's page.
- Independent security review: **No** (optional MASA declaration, not
  done)
- Families Policy badge: **not opted in** (set under Target audience
  and content; the app is 13+, not "directed to children")

## Permissions disclosed

- `android.permission.CAMERA` (foreground only): used for
  on-device bird ID; frames discarded after classification.
- `android.permission.RECORD_AUDIO` (foreground only): used for
  on-device bird-call ID (BirdNET-Lite, recordings of up to 60
  seconds); audio is kept only in app-private storage and never
  uploaded.
- `android.permission.INTERNET` (new in v1.2): used to fetch map
  tile imagery from MapTiler when the user views the personal finds
  map. No user data is transmitted; tile requests carry only the map
  viewport.
- `android.permission.ACCESS_NETWORK_STATE` (new in v1.2): used to
  check connectivity before attempting to load map tiles; avoids
  unnecessary tile requests when offline.
- `android.permission.ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`
  (new in v1.2, opt-in, off by default): used to capture the
  device's GPS location at observation save-time, when the user has
  enabled "Save location with my finds" in Settings. Location is
  stored only on the device; never transmitted.
- `android.permission.POST_NOTIFICATIONS`: used to show local
  notifications (the daily Bird of the day around 08:00 with a photo of
  the species, the weekly recap and the weekly badge progress note). On
  Android 13 and later the user is asked after the first saved find and
  can decline; each kind can be turned off in Settings. Scheduled on the
  device with AndroidX WorkManager; there is no push server and nothing
  is sent.
- Added by libraries: WorkManager declares `RECEIVE_BOOT_COMPLETED`, so
  the schedule survives a restart, and `WAKE_LOCK` and
  `FOREGROUND_SERVICE`, which it can use while running scheduled work;
  Google Play Billing declares `com.android.vending.BILLING`.

We do **not** declare:
- Photos permission (`READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE`):
  we use Android 13+'s `PickVisualMedia` which requires no permission

## App access

If Google Play asks for "App access instructions" to test premium
features, provide:
- Debug build with `PREMIUM_DEBUG_FORCE_ACTIVE=true` to test premium
  UI without going through real billing
- Or use a test account configured as a Play Console license tester

## Diff log

- **2026-05-15**: initial form drafted alongside v0.8.0-rc1 prep.
  Status: app makes zero network calls, collects zero data.
- **2026-05-17**: re-reviewed for v0.9.0a-billing (Plan 6b1 T4 Google
  Play Billing v8 integration). No form changes needed: billing traffic
  is handled by Google's BillingClient SDK, not by Birdy; purchase
  tokens never reach our code (we only see acknowledged-entitlement
  state). No new permissions, no new data types, no new sharing.
- **2026-05-22**: re-reviewed for v0.9.0b-audio (Plan 6b2 audio-ID via
  BirdNET-Lite) + Option-A BirdNET-license decision. Added
  `RECORD_AUDIO` permission disclosure. Audio recordings are
  3-second clips classified on-device by BirdNET-Lite (CC BY-NC-SA
  4.0); saved to app-private storage only when user attaches to an
  observation, otherwise discarded. Zero network calls remain.
  Audio-ID ships as a free feature for all users, not Premium-gated
  (the BirdNET license forbids commercial gating of the model).
- **2026-06-06**: re-reviewed for personal-finds-map feature (v1.2).
  Added INTERNET + ACCESS_NETWORK_STATE (map tile fetching from
  MapTiler) and ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION (opt-in,
  off by default; GPS captured at save-time for personal map). Location
  data stored on-device only, never transmitted. Core privacy promise
  unchanged: observations (including optional location) never leave
  the device. Tile requests convey map viewport to MapTiler only.
  Data Safety form answer for Location remains **No** (no collection /
  no sharing per Play Console definitions). Encryption-in-transit
  answer updated to **Yes** (HTTPS tile requests; no user data in
  transit).
- **2026-09-28**: developer changed from a private person to
  **AlbIT AB** (the Play Console account moved to the company). No
  change to any form answer.
- **2026-10-05**: re-reviewed for v1.3.0 (vC129). Premium is now sold
  through Google Play Billing as a yearly subscription or a one-time
  lifetime purchase; Birdy still only sees purchase state, never
  payment details. Sound ID now records for up to 60 seconds instead
  of fixed short clips; recordings stay in app-private storage and are
  never uploaded. Early users are recognised on the device from the install
  time an earlier version stored and from the phone's network time at the
  first start; nothing is transmitted. No change to any form answer.
- **2026-10-07**: legal review for v1.3.0 (vC130). Added
  `POST_NOTIFICATIONS` (local notifications, in the manifest since the
  notification features shipped) and the permissions that libraries add
  to the permission list. The encryption and deletion questions are not
  asked when no data is collected or shared; the places that answered
  them now say so, and deletion is described as it works (Delete on an
  observation's page, not long-press). No change to any form answer.
