# Billing-verify + go-live runbook (v1.0)

> **När:** Innan vi flippar `PREMIUM_OPEN_FOR_LAUNCH=false` och släpper Birdy i produktion på Google Play.
> **Varför:** Override:n `premiumOverride = Active(LIFETIME)` som ligger på under closed testing maskerar hela "no premium → köpflöde → state-flip till Active"-vägen. Den vägen måste verifieras isär från overriden innan den möter riktiga betalande användare.
> **Status:** Items 1, 2, 4 nedan är **pending** — ska köras innan production-launch. Item 3 (BirdNET-licensguard) är **DONE 2026-05-26** (commit pushas tillsammans med denna fil).

---

## 1. Separat testväg för Billing-flowet (debug-toggle)

**Beslut:** Lägg en hidden debug-toggle i Settings som låter override:n stängas av per-device utan rebuild. Återanvänder mönstret från commit `afd976f` ("debug-only force-run buttons for both workers").

**Implementation (utför precis innan Billing-verify):**

1. Lägg `skipPremiumOverride: Boolean = false` i `UserPreferences` (DataStore).
2. I `MainActivity` (eller där `premiumOverride` injectas), läs flaggan vid app-start. Om `BuildConfig.DEBUG && skipPremiumOverride` → hoppa över hela override-injektionen.
3. I `SettingsScreen` (Developer-sektionen, redan gated bakom `BuildConfig.DEBUG`), lägg en toggle: "Skip premium override (Billing test)".
4. Verifiera att toggle:n inte renderas i release-bygget (kompilera och inspektera).
5. Bonus före go-live: kör en lokal `installRelease`-build med `PREMIUM_OPEN_FOR_LAUNCH=false` direkt i `defaultConfig` på en testenhet, för att fånga ev. R8/proguard-strip av Billing-klasser som debug-bygget döljer.

**Klart-kriterium:** Med debug-bygget installerat och toggle:n på → cold-start visar "Unlock Premium"-banner, Identify-tab visar premium-teaser, PDF/Stats-features gated. State motsvarar `NotActive`.

---

## 2. Billing v8 verifieringschecklista

**Förutsättningar:**
- Internal Testing-app entry i Play Console med signed AAB uppladdad.
- In-app products skapade och aktiva (YEARLY-sub + LIFETIME-one-time).
- License testers konfigurerade (din egen Gmail som testkonto).
- Galaxy S23 Ultra (SM-S918B) inloggad med license tester-kontot.
- Debug-toggle (punkt 1) PÅ → override avstängd.

### Per-tillstånd

- [ ] **Fresh install, ingen tidigare purchase** → `effectivePremiumActive = false`. Premium-skärm visar erbjudande. Per-tab teasers visas. Cold-start modal triggar enligt regel.
- [ ] **YEARLY purchase pågående** → Google purchase-sheet öppnas. Return till app utan crash. Loading-state visas under pending.
- [ ] **YEARLY purchase OK** → State-flip till `Active(YEARLY)`. Alla gates släpper *omedelbart* (PDF-export, Season Statistics, 10 premium-badges). Teasers försvinner från alla tabbar.
- [ ] **LIFETIME purchase OK** → Samma, men `Active(LIFETIME)`. Precedens: LIFETIME vinner över YEARLY om båda finns.
- [ ] **Cold start med tidigare YEARLY** → State restoreas automatiskt via `queryPurchasesAsync`. Ingen ny purchase-sheet visas.
- [ ] **"Restore Purchases"-knapp** (i Settings/PremiumScreen) → Funkar även om automatisk restore failat (test: offline cold-start, sen online och tap restore).
- [ ] **Cancellation / refund** (Play Console license-tester refund-flow) → State-flip tillbaka till `NotActive` vid nästa cold start. Gates re-engagerar.
- [ ] **Nätverksfel under purchase** (flygplansläge mitt i flow) → Graceful error. Ingen half-state. Retry möjlig.
- [ ] **Signatur-verify fail** (manipulera Play Licensing public key i BuildConfig tillfälligt) → Reject + log. Ingen state-flip.

### Per-feature gate-verify (i `NotActive`-tillstånd)

- [ ] PDF-export → gated (upsell visas, ingen export).
- [ ] Season Statistics → gated.
- [ ] 10 premium-badges → låsta. När Active → de blir "in-progress" enligt rule.
- [ ] **Audio-ID → ALDRIG gated.** Se punkt 3 + unit test `BirdNetLicenseGuardTest`.

### Klart-kriterium
Alla rutor ovan ticked. Skärmdumpar på 3–4 nyckelstates (NotActive, mid-purchase, Active(YEARLY), Active(LIFETIME)) sparade i `docs/superpowers/screenshots/v1.0-billing-verify/`.

---

## 3. BirdNET-licensguard (DONE 2026-05-26)

**Implementerat:** Unit-test `BirdNetLicenseGuardTest` i `composeApp/src/androidUnitTest/kotlin/se/birdy/app/ui/listen/`.

Testet:
- Walkar alla `.kt`-filer under `ui/listen/` och `ui/audio/`.
- Failar om någon fil innehåller `PremiumState`, `effectivePremiumActive`, eller `isPremiumActive`.
- Asserterar att antal scannade filer > 0 så path-resolution inte tyst breaks.

Kör via:
```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.listen.BirdNetLicenseGuardTest"
```

**Manuell licens-check (komplement) under Billing-verify:**
Fresh install, debug-toggle PÅ (`NotActive`). Banner "Unlock Premium" syns på Identify-tab. Navigera till Listen → push-to-record 3 sek → få species-resultat. Screenshot. Om audio-resultatet visas utan paywall → ✅ licens-OK.

**Varför:** BirdNET-Lite-modellen är CC BY-NC-SA 4.0 (NonCommercial). Att gate:a audio-ID bakom Premium = licensbrott. Premium-tier står helt på Plan 6b3-features (PDF, stats, badges) som vi byggt själva.

---

## 4. Conversion-monitoring post-launch

**Plan:** Conversion är otestat innan production-launch. Closed-testarna ser allt gratis, så premium-screen / teasers / pris möter inte folk som faktiskt står inför betalvägg. Första produktionsdagarna = första signalen, inte en bekräftelse.

**Mätning utan att bryta privacy-löftet:**
- Använd Play Console-data (gratis, ingen in-app instrumentation). Konvertering per akvisitionskanal, retention, uninstall rate.
- Reddit + email-feedback som kvalitativ signal.
- **Lägg INTE in analytics-events i appen.** Bryter "almost nothing collected, data stays on phone."

**Review-checkpoints (sätt kalender-påminnelser vid go-live):**
- **+7 dagar:** Första baseline. Notera install-volym, conversion-rate (free → premium), eventuella refunds, uninstall-trend, Play Console rating-distribution.
- **+14 dagar:** Andra datapunkt. Om conversion < ~1% av första-skanning-users → börja iterera.

**Iterations-kandidater, ordnade billigast → dyrast att ändra:**

1. **Copy** på PremiumScreen + per-tab teasers (compose-resources string-change, en commit + ny AAB).
2. **Pris** (Play Console UI-ändring, kräver ingen ny AAB).
3. **Teaser-placering / -frekvens** (kodändring + AAB).
4. **Cold-start modal-regel** (frekvens, trigger-condition).
5. **Feature-mix bakom Premium** — *sista resort*. Enkelriktat: tidiga köpare har redan betalat för befintlig mix.

**Klart-kriterium:** +14d-checkpoint körd med Play Console-data sammanställd i `docs/superpowers/research/YYYY-MM-DD-conversion-baseline.md`. Beslut: iterate (vilka kandidater) eller låt ligga.

---

## Beroenden + ordning

```
1. Implementera debug-toggle  ──┐
                                ├─►  2. Billing-verify-checklista  ──►  Flippa PREMIUM_OPEN_FOR_LAUNCH=false  ──►  Production launch  ──►  4. Conversion-monitoring
3. License guard test ✅ ──────┘                                                                                          (+7d, +14d checkpoints)
```

Punkt 3 är redan på plats — fångar regression om någon framöver försöker återinföra premium-gating i audio/listen-koden. Punkt 1 och 2 måste köras i sekvens innan flippen. Punkt 4 startar dagen vi går live.

---

## Filer som rörs när detta körs

- `composeApp/.../UserPreferences*.kt` — lägg `skipPremiumOverride`.
- `androidApp/.../MainActivity.kt` — läs flaggan, hoppa över override när satt.
- `composeApp/.../SettingsScreen.kt` — debug-only toggle i Developer-sektionen.
- `androidApp/build.gradle.kts` — flippa `PREMIUM_OPEN_FOR_LAUNCH=false` *efter* punkt 2 är grön.
- `docs/superpowers/screenshots/v1.0-billing-verify/` — skärmdumpar (skapas vid verify).

## Referenser

- BirdNET-licensbeslut: auto-memory `project_birdnet_license_decision.md`.
- Billing v8 grunddesign: Plan 6b1 (`docs/superpowers/plans/2026-05-16-v1-06b1-billing-launch-prep.md`).
- Premium-feature gates: Plan 6b3 (`docs/superpowers/plans/2026-05-21-v1-06b3-premium-content.md`).
- Privacy-löfte: `docs/play-store/privacy-policy.md` + audit `2026-05-20-play-store-audit.md`.
