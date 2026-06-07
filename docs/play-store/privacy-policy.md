# Birdy — Privacy Policy

_Last updated: 2026-06-06_

Birdy is built and operated by **Albin Viktor Lindblom** (Sweden). This
policy explains what data the app handles and how. Birdy is designed to
work fully offline. We do not collect, transmit, or sell personal data.

## 1. What data does Birdy handle?

Birdy stores the following data **locally on your device**:

- **Bird observations** you save: species, timestamp, optional photo,
  optional audio recording, optional handwritten note, optional location
  label, and — if you have enabled "Save location with my finds" —
  optional GPS coordinates (see section 2a below).
- **Photos** you choose to associate with an observation. Stored in the
  app's private files directory (`filesDir/observations/`).
- **Audio recordings** (3-second clips) captured for bird-call ID.
  Stored in the app's private files directory
  (`filesDir/observations/`) when you save the resulting observation;
  otherwise discarded after classification.
- **App preferences**: your display name (optional), preferred language,
  premium state.
- **Badge unlocks** and progress counters.

This data **never leaves your device** unless:

- You actively share an observation via the system share sheet (e.g.,
  email, message), in which case the receiver decides where it goes.
- You enable Google's Android Auto-Backup to Google Drive (Settings →
  Backup), which encrypts and uploads app data to your Google account.

## 2. Permissions Birdy uses

- **Camera** (`android.permission.CAMERA`) — required to identify birds
  via the live viewfinder. Frames are processed on-device by the AI
  model and discarded after classification. No frame is uploaded.
  Camera permission is foreground-only.
- **Microphone** (`android.permission.RECORD_AUDIO`) — required to
  identify birds by their call. Audio is captured in 3-second clips,
  processed on-device by the BirdNET-Lite model, and either saved to
  your observation (if you tap save) or discarded immediately after
  classification. No audio is uploaded.
- **Photo picker** (Android 13+ `PickVisualMedia`) — no permission
  required; you choose which photo to share with Birdy per pick.
- **Location** (`ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`) —
  used **only** when you have enabled "Save location with my finds"
  in Settings (this toggle is **off by default**). When on, Birdy
  records your device's GPS coordinates at the moment you save a field
  observation, so you can see it on your personal finds map. Location
  is stored only in the device-local database. It is never transmitted,
  synced to a server, or shared with anyone.
- **Internet** (`INTERNET` + `ACCESS_NETWORK_STATE`) — used to fetch
  map tile imagery from MapTiler when you view the personal finds map.
  See section 2a.

## 2a. Location and the personal finds map

If you turn on **"Save location with my finds"** (off by default),
Birdy records the GPS location of observations you make in the field
and stores it only on your device. This lets you see your finds on
your personal map. The location data never leaves your phone.

When you view the map, the map imagery (tiles) is loaded from
**MapTiler** over a secure HTTPS connection. Only the area of the map
you are viewing is sent to MapTiler in order to fetch the correct
tiles — your bird finds and their coordinates are never included in
these requests and are never transmitted anywhere. MapTiler's own
privacy policy applies to these tile requests.

If you keep "Save location with my finds" off (the default), no
location data is ever captured, stored, or requested.

## 3. AI / machine learning

Birdy uses two on-device classifiers, both running entirely via
TensorFlow Lite — no image or audio data is sent to any server for
inference:

- **AIY Birds V1** (image classifier) from Google, distributed under
  the Apache 2.0 license.
- **BirdNET-Lite v2** (audio classifier) from the Cornell Lab of
  Ornithology. Source code is MIT-licensed; the model weights are
  distributed under **CC BY-NC-SA 4.0** (NonCommercial). Birdy ships
  audio-ID as a free feature for all users, with no paywall, in
  compliance with the model's license.

## 4. Third-party services

Birdy does **not** include analytics, advertising SDKs, crash
reporting, or tracking. We do not have a backend.

Wikipedia and Wikimedia Commons are listed as content sources for
encyclopedia text and reference photos. These are bundled with the app
at build time; the app does not fetch from them at runtime.

**MapTiler** — when the personal finds map is displayed, map tile
imagery is fetched from MapTiler over HTTPS. MapTiler receives the
map viewport (the geographic area being displayed) in order to serve
the correct tiles. Your bird finds and their coordinates are never
sent. See [MapTiler's Privacy Policy](https://www.maptiler.com/privacy-policy/)
for details on how MapTiler handles tile requests.

## 5. Premium purchases

If you purchase a premium subscription, the transaction is handled by
**Google Play Billing**. Birdy receives only the purchase token from
Google — not your name, email, or payment details. See
[Google Play's Privacy Policy](https://policies.google.com/privacy)
for details on how Google handles billing data.

## 6. Children

Birdy is intended for users aged **13 and older**. We do not knowingly
collect data from users under 13.

## 7. Your rights

Since Birdy stores data only on your device, your rights under GDPR
(right to access, right to erasure, etc.) are satisfied by:

- **Accessing your data**: open the Diary / Lifelist tabs in Birdy.
- **Deleting your data**: long-press an observation to delete, or
  uninstall the app to clear all data.

## 8. Changes to this policy

If we change this policy materially, we will note the change in the
app's release notes. The "Last updated" date above always reflects the
current version.

## 9. Contact

Questions? Email **albin@abrahamssons.se**.
