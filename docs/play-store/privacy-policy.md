# Birdy — Privacy Policy

_Last updated: 2026-05-22_

Birdy is built and operated by **Albin Viktor Lindblom** (Sweden). This
policy explains what data the app handles and how. Birdy is designed to
work fully offline. We do not collect, transmit, or sell personal data.

## 1. What data does Birdy handle?

Birdy stores the following data **locally on your device**:

- **Bird observations** you save: species, timestamp, optional photo,
  optional audio recording, optional handwritten note, optional location
  label.
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
