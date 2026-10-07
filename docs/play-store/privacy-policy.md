# Birdy: Privacy Policy

_Last updated: 2026-10-07_

Birdy is built and operated by **AlbIT AB** (org. no. 559593-7607,
Solna, Sweden), which is responsible for the app and for the website
birdy.community. This policy explains what data the app handles and
how; section 8 covers the website, where this policy is also
published. Bird identification, the encyclopedia and your field
journal work offline. The personal finds map needs an internet
connection to load map imagery, and purchases go through Google Play.
We do not collect, transmit, or sell personal data through the app.

## 1. What data does Birdy handle?

Birdy stores the following data **locally on your device**:

- **Bird observations** you save: species, timestamp, optional photo,
  optional audio recording, optional handwritten note, optional location
  label, and (if you have enabled "Save location with my finds")
  optional GPS coordinates (see section 2a below).
- **Photos** you choose to associate with an observation. Stored in the
  app's private files directory (`filesDir/observations/`).
- **Audio recordings** of up to 60 seconds, captured when you use
  bird-call ID. They are kept only in the app's private storage on
  your device, are never uploaded, and are removed when you uninstall
  the app.
- **App preferences**: your display name (optional), preferred language,
  notification choices, premium state, and when you first installed
  and first opened Birdy (used only on your device, to recognise early
  users who keep Premium for free).
- **Badge unlocks** and progress counters.

This data **never leaves your device** unless:

- You export your journal as a PDF and share it, or share a link to
  the app, via the system share sheet (e.g., email, message), in which
  case the receiver decides where it goes.
- You enable Google's Android Auto-Backup to Google Drive (Settings →
  Backup), which encrypts and uploads app data to your Google account.

## 2. Permissions Birdy uses

- **Camera** (`android.permission.CAMERA`): required to identify birds
  via the live viewfinder. Frames are processed on-device by the AI
  model and discarded after classification. No frame is uploaded.
  Camera permission is foreground-only.
- **Microphone** (`android.permission.RECORD_AUDIO`): required to
  identify birds by their call. Birdy listens for up to 60 seconds
  and stops earlier once it is confident. The audio is processed
  on-device by the BirdNET-Lite model and kept only in the app's
  private storage. No audio is uploaded.
- **Photo picker** (Android 13+ `PickVisualMedia`): no permission
  required; you choose which photo to share with Birdy per pick.
- **Location** (`ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`):
  used **only** when you have enabled "Save location with my finds"
  in Settings (this toggle is **off by default**). When on, Birdy
  records your device's GPS coordinates at the moment you save a field
  observation, so you can see it on your personal finds map. Location
  is stored only in the device-local database. It is never transmitted,
  synced to a server, or shared with anyone.
- **Internet** (`INTERNET` + `ACCESS_NETWORK_STATE`): used to fetch
  map tile imagery from MapTiler when you view the personal finds map.
  See section 2a.
- **Notifications** (`POST_NOTIFICATIONS`): used for local reminders,
  namely the daily Bird of the day around 08:00 (with a photo of the
  species), a weekly recap on Sunday evening and a weekly note on your
  badge progress. On Android 13 and later Birdy asks for permission
  after your first saved find, and you can say no; on earlier versions
  Android allows them by default. Each kind can be turned off in
  Settings → Notifications. The notifications are created and
  scheduled on your device with Android's WorkManager library (the
  library also declares `RECEIVE_BOOT_COMPLETED`, so the schedule
  survives a restart, and `WAKE_LOCK` and `FOREGROUND_SERVICE`, which
  it can use while running scheduled work). There is no push server,
  and nothing is sent.

## 2a. Location and the personal finds map

If you turn on **"Save location with my finds"** (off by default),
Birdy records the GPS location of observations you make in the field
and stores it only on your device. This lets you see your finds on
your personal map. The location data never leaves your phone.

When you view the map, the map imagery (tiles) is loaded from
**MapTiler** over a secure HTTPS connection. Only the area of the map
you are viewing is sent to MapTiler in order to fetch the correct
tiles. Your bird finds and their coordinates are never included in
these requests and are never transmitted anywhere. MapTiler's own
privacy policy applies to these tile requests.

If you keep "Save location with my finds" off (the default), no
location data is ever captured, stored, or requested.

## 3. AI / machine learning

Birdy uses two on-device classifiers, both running entirely via
TensorFlow Lite. No image or audio data is sent to any server for
inference:

- **AIY Birds V1** (image classifier) from Google, distributed under
  the Apache 2.0 license.
- **BirdNET-Lite** (audio classifier) from the K. Lisa Yang Center
  for Conservation Bioacoustics, Cornell Lab of Ornithology, Cornell
  University. The model is distributed under **CC BY-NC-SA 4.0**
  (NonCommercial), and Birdy uses it unchanged. Sound identification
  is free for all users, with no paywall.

## 4. Third-party services

Birdy does **not** include analytics, advertising SDKs, crash
reporting, or tracking. We do not have a backend.

The species texts (based on Wikipedia) and the reference photos (from
Wikimedia Commons) are bundled with the app at build time; the app
does not fetch them at runtime. Links to sources and licenses open in
your browser only when you tap them.

**Google Play**: purchases go through Google Play Billing, and the
prompt that asks you to rate Birdy is Google Play's In-App Review.
Both are provided by Google, which handles the data they use under
[Google's Privacy Policy](https://policies.google.com/privacy).

**MapTiler**: when the personal finds map is displayed, map tile
imagery is fetched from MapTiler over HTTPS. MapTiler receives the
map viewport (the geographic area being displayed) in order to serve
the correct tiles. Your bird finds and their coordinates are never
sent. See [MapTiler's Privacy Policy](https://www.maptiler.com/privacy-policy/)
for details on how MapTiler handles tile requests.

## 5. Premium purchases

Premium is sold as a yearly subscription or as a one-time lifetime
purchase. Both are handled by **Google Play Billing**. Birdy receives
only the purchase token from Google, not your name, email, or payment
details. See [Google Play's Privacy Policy](https://policies.google.com/privacy)
for details on how Google handles billing data.

If you installed Birdy before paid Premium launched, Premium is
unlocked for free. The app decides this on your device, from when you
first installed or opened it (using the time an earlier version
stored, or the phone's network time), and nothing is sent anywhere.

## 6. Children

Birdy is intended for users aged **13 and older**. We do not knowingly
collect data from users under 13.

## 7. Your rights

Since Birdy stores data only on your device, your rights under GDPR
(right to access, right to erasure, etc.) are satisfied by:

- **Accessing your data**: open the Lifelist tab in Birdy.
- **Deleting your data**: open an observation and tap Delete, or
  uninstall the app to clear all data.

## 8. The website birdy.community

This policy is also published on the website, at
birdy.community/legal/privacy/. The website has no accounts, no sign-up
forms and no advertising, and it does not set cookies.

- **Visitor statistics**: the website uses **Vercel Web Analytics** to
  count page views, without cookies. For each page view it records
  the time, the page address (including query parameters), the
  referring site, an approximate
  location (country, region and city) and the type of device,
  operating system and browser. Visitors are told
  apart by a hash of the request that is discarded after 24 hours, and
  we only see aggregated statistics. See
  [Vercel's description](https://vercel.com/docs/analytics/privacy-policy).
- **Hosting**: the website is hosted by **Vercel**, which handles the
  technical data that every web request carries (such as your IP
  address) in order to deliver the pages.
- **Map**: the coverage map on the start page loads the map (style,
  map data and label fonts) from **MapTiler** when you scroll near it,
  so MapTiler receives those requests. See
  [MapTiler's Privacy Policy](https://www.maptiler.com/privacy-policy/).
- **Fonts**: the website's own typefaces are served from the website
  itself, not from a font service.

## 9. Changes to this policy

If we change this policy materially, we will note the change in the
app's release notes. The "Last updated" date above always reflects the
current version.

## 10. Contact

Questions? Email **albin@abrahamssons.se**.
