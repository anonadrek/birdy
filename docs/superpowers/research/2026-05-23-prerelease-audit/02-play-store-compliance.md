# Play Store compliance-audit — 2026-05-23

## Sammanfattning

Birdy v1.0.0 är **nästan redo för Play Store-upload** men har två kritiska URL-mismatchar som måste fixas före Closed Testing: store-listningarnas gamla nonadrek.github.io-URLer passar inte med appens irdy.community-URLer eller webbsitets live legal-routes. Privacy/Terms-dokumenten är uppdaterade och överensstämmer med Data Safety-formuläret. Screenshots, app-icon och feature-graphic är på plats. Lokaliseringen (SV+EN) är konsistent i listings. Tre HIGH-prioriterade fynd behöver åtgärd före upload.

## Findings

### BLOCKER

#### **Store-listning pekar på gammal nonadrek.github.io — app pekar på irdy.community**
- docs/play-store/store-listing-en.md:74-75 — kontakt-URL: https://anonadrek.github.io/birdy/ + privacy-URL: https://anonadrek.github.io/birdy/privacy.html
- docs/play-store/store-listing-sv.md:74-75 — samma gamla URLer
- **App reality:** SettingsScreen.kt:130-132 pekar på https://birdy.community/legal/privacy/, https://birdy.community/legal/terms/, https://birdy.community/
- **Website reality:** website/src/pages/legal/[slug].astro renderar rutter för /legal/privacy/, /legal/terms/, /legal/data-safety/ — alla live på birdy.community
- **Fix:** Uppdatera store-listings (båda språk) med:
  - Website: https://birdy.community/
  - Privacy policy: https://birdy.community/legal/privacy/
- **Tid:** 5 min per fil

#### **Version-mismatch mellan store-listing och appens versionCode/versionName**
- docs/play-store/store-listing-en.md:52 — "What's new (v0.9.0a-billing — Closed Testing)"
- ndroidApp/build.gradle.kts:56 — ersionName = "1.0.0", ersionCode = 112
- **Fix:** Uppdatera "What's new" i båda store-listings till v1.0.0
- **Tid:** 10 min båda språk

### HIGH

#### **Android 13+ ndroid:localeConfig saknas (från tidigare audit)**
- ndroidApp/src/main/AndroidManifest.xml — saknar ndroid:localeConfig för Language Picker
- **Fix:** Skapa ndroidApp/src/main/res/xml/locales_config.xml med sv-SE + en-US
- **Status:** Blockerar inte upload men Play Console kan flagga avvikelse för Android 13+ devices
- **Tid:** 10 min

#### **Play Console Data Safety form kräver manuell ifyllning**
- docs/play-store/data-safety-form.md är dokumenterad men måste infogas i Play Console UI
- **Action:** Använd dokumentet som checklista vid Console-upload
- **Tid:** 15-20 min vid upload

#### **Monetize SKU-setup måste verifiera**
- Play Console → Monetize ska redan ha: irdy_yearly (199 SEK/år) + irdy_lifetime (499 SEK)
- **Action:** Verifiera att SKUs matchar PremiumBillingClient.kt före upload
- **Tid:** 5 min

### MEDIUM

#### **Data Safety form metadata föråldrad**
- docs/play-store/data-safety-form.md:6 refererar till "Plan 6b2 v0.9.0b-audio"
- Bör uppdateras till v1.0.0-metadata för tydlighet
- **Tid:** 2 min

#### **Email-kontakt är personlig, inte domain**
- lbin@abrahamssons.se istället för eedback@birdy.community
- Accepterat för v1.0.0; post-launch fix
- **Tid:** 0 min (post-launch)

### LOW

#### **Screenshots: 131 finns, kurera 8-10 för Console**
- Play Store rekommenderar 8-10 per locale
- **Tid:** 20 min för selection

#### **Privacy policy + Terms ligger korrekt på webbsiten**
- website/src/pages/legal/[slug].astro mappar korrekt
- Routes /legal/privacy/, /legal/terms/ är live ✓

#### **Feature graphic + app icon på plats**
- eature-graphic-1024x500.png ✓
- ic_launcher_512.png ✓

## Vad har fixats sedan 2026-05-20-auditen

1. **SettingsScreen.kt URLer redan uppdaterade**
   - Pekar på irdy.community routes (inte gamla github.io)
   - **ISSUE:** Store-listings inte uppdaterade än

2. **Privacy policy + Terms uppdaterad (2026-05-22)**
   - Inkluderas i webbsitets legal-routing
   - Kontakt-email + datum korrekt

3. **Data Safety form uppdaterad för audio-ID**
   - BirdNET-Lite disclosure + RECORD_AUDIO permission

4. **Internal Testing runbook skapad (2026-05-22)**
   - Stegvis upload-instruktioner + Billing-verifiering

---

_Audit utförd 2026-05-23 av Play Store-compliance-agent._
