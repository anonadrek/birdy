# Internal Testing hand-off-audit — 2026-05-23

## Sammanfattning

**Kan vi trycka Upload idag? ✋ NEJ — två BLOCKER måste fixas först:**

1. **Play Store-listings pekar på gamla GitHub Pages-URLer** (ska peka på birdy.community)
2. **closed-testing-tester-instructions.md refererar till föråldrad versionCode** (110 istället för 112)

Runbook är bra dokumenterad; AAB-build-process är klar; release notes finns; skärmdumpar är tillgängliga (10 från v0.9.0c-premium-content). När vi fixar URLerna kan vi trycka upload samma dag.

---

## Findings

### BLOCKER

#### #1: Play Store-listings har gamla anonadrek.github.io-URLer

- **Filer:** `docs/play-store/store-listing-{sv,en}.md:74-75`
- **Problem:** Båda filerna (SV + EN) refererar fortfarande till `https://anonadrek.github.io/birdy/{privacy,terms}.html`
- **Bör vara:** `https://birdy.community/legal/{privacy,terms}/` (migrerat 2026-05-22, se commit `92c40b3`)
- **Impact:** Play Console-listings kommer att länka till döda GH Pages-sidor tills de uppgraderas
- **Fix:** Uppdatera båda MD-filer + kopiera text till Play Console web UI vid upload
- **Tid:** ~5 min

#### #2: closed-testing-tester-instructions.md refererar till versionCode 110

- **Fil:** `docs/play-store/closed-testing-tester-instructions.md:3`
- **Problem:** "Build: v0.9.0a-billing (versionCode 110, versionName 1.0.0-rc2)" — men v1.0.0 är versionCode 112
- **Bör vara:** "Build: v1.0.0 (versionCode 112, versionName 1.0.0)"
- **Impact:** Testers får förvirrande instruktioner; bygget vi uploadar matchar inte instruktionerna
- **Fix:** Uppdatera rubrik + version-references
- **Tid:** ~3 min

---

### HIGH

#### #3: Launcher-app-icon i rå 512x512-format saknas för Play Console

- **Status:** App har adaptive launcher-icon (XML + drawables), men Play Console kräver en rå PNG-fil 512×512
- **Tillgängliga:** 180×180 apple-touch-icon.png, 1200×630 og.png (OpenGraph) — varken är rätt format
- **Impact:** Play Console-upload kan fallera eller kräver manuell hand-resizing
- **Workaround:** Exporteras från Android Studio eller scal xxhdpi launcher-png (192×192) upp 2.67x
- **Tid:** ~10 min

#### #4: Website store-listing-copy kan referera till gamla URLer

- **Status:** Runbook steg 8 säger "Uppdatera hemsidan" för opt-in-länken (redan gjord)
- **Potentiell risk:** Settings-länk om den fortfarande pekar till anonadrek.github.io
- **Tid:** Redan inkluderad i #1-fixningen

---

### MEDIUM

#### #5: Release notes för v1.0.0 färdiga

- **Status:** Runbook §4 innehåller två färdiga mallar (SV + EN, 250–280 tecken vardera — under 500-limit ✓)
- **Content är bra:** Nämner foto-ID, audio-ID, 839 arter, dagbok, märken, premium
- **Action:** Kopiera direkt från runbook till Play Console vid upload
- **Tid:** 0 min (redan färdig)

#### #6: In-app products laggas in post-Internal Testing

- **Status:** Runbook förkrav säger SKUs ska konfigureras, men CLAUDE.md follow-up #4 säger deferred
- **Faktiskt:** `PREMIUM_OPEN_FOR_LAUNCH=true` betyder closed testing-users får Premium GRATIS
- **Plan:** Lägg in SKUs senare; innan verklig monetization måste `PREMIUM_OPEN_FOR_LAUNCH=false` flippa
- **Tid:** Inte blockerande för today's upload

#### #7: Signed keystore credentials måste vara lokalt konfigurerade

- **Krav:** `~/.gradle/gradle.properties` måste ha signing-credentials (4 env vars)
- **Status:** Runbook dokumenterar detta (§Förkrav); kan inte verifiera från read-only audit
- **Check:** Användaren måste manuellt verifiera före build
- **Tid:** ~2 min (per användare, manual verification)

---

### LOW / Nice-to-have

#### #8: Privacy policy + terms gamla GH Pages-länkar

- **Action:** Verifiera att dessa markdown-filer inte länkar tillbaka till anonadrek.github.io
- **Tid:** ~5 min (review)

#### #9: Backup av keystore inte dokumenterat

- **Rekommendation:** Lägg keystore-backup-steg i runbook eller separat security-runbook
- **Tid:** Nice-to-have, ingen blockerare

#### #10: Rollback-plan saknas

- **Scenario:** Om något kritiskt upptäcks efter Internal Testing-upload
- **Notering:** Play Console låter dag unpublish från tracks innan release goes live
- **Tid:** Nice-to-have dokumentation

---

## Manuella steg som väntar (checklist)

Förutsatt att vi fixar BLOCKER #1 + #2 ovan:

- [ ] **FIX: Uppdatera Play Store-listings (SV + EN) till birdy.community-URLer**
- [ ] **FIX: Uppdatera closed-testing-instructions versionCode 110 → 112**
- [ ] **NICE: Exportera app-icon 512×512 för Play Console upload**
- [ ] Verifiera `~/.gradle/gradle.properties` har alla signing-credentials
- [ ] Set JAVA_HOME (se runbook §Build signed AAB)
- [ ] `./gradlew :androidApp:bundleRelease` (bygga AAB)
- [ ] Verifiera signing via keytool
- [ ] Logga in på Play Console
- [ ] Navigate to Birdy > Test and release > Internal testing > Create new release
- [ ] Upload `androidApp-release.aab`
- [ ] Sätt release name: `1.0.0 (112)`
- [ ] Sätt release notes (SV + EN) från runbook
- [ ] Fylla i Data Safety form
- [ ] Sätta Privacy policy URL: `https://birdy.community/legal/privacy/`
- [ ] Sätta App icon: 512×512 PNG
- [ ] Bifoga screenshots (10 från `docs/superpowers/screenshots/2026-05-22-v0.9.0c-premium-content/`)
- [ ] Save > Review release
- [ ] Roll out to Internal testing
- [ ] Vänta 5–30 min tills opt-in-länk fungerar
- [ ] Skicka opt-in-länk till license testers + testa själv
- [ ] (Post-Internal) Promotion till Closed Testing
- [ ] (Post-Closed, 14d) Promotion till Production

---

## Pending follow-ups från CLAUDE.md — nuläges-snapshot

- #1: **URL-migration** — 🔴 BLOCKER, denna audit identifierade det (Play Store listings)
- #2: **GitHub Pages teardown** — 🟡 Deferred post-launch
- #3: **Email migration** — 🟡 Deferred post-launch
- #4: **Billing v8 IPC verify** — 🟢 OK deferred; `PREMIUM_OPEN_FOR_LAUNCH=true` bypasses
- #5: **Audio accuracy eval** — 🟢 OK deferred
- #6: **AB-flytt** — 🟢 OK deferred (post-launch Account Transfer)
- #7: **SV legal-translations** — 🟢 OK deferred (Nordics-first intentional)
- #8: **Plan 6a T8/T9 screenshots** — 🟢 OK deferred (test-image-infra)

