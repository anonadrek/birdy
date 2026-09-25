# Billing-verify + go-live runbook (v1.0)

> **Uppdatering 2026-09-25 (release 1.3.0):** Appen ligger nu på AB:s utvecklarkonto. `PREMIUM_OPEN_FOR_LAUNCH=false` och grandfather-regeln (§5) är implementerade i 1.3.0 (`GrandfatherPolicy`, brytpunkt `GRANDFATHER_CUTOFF_MS` = 2026-10-02 00:00 Stockholm, tack-skärm en gång för tidiga användare). Köptestet körs med **vC128 byggt med `-Pbirdy.grandfatherCutoffMs=0 -Pbirdy.billingTestBuild=true`** (versionsnamnet blir `1.3.0-koptest`; flaggorna gäller bara på kommandoraden, aldrig i gradle.properties): ingen är grandfathered, så betalväggen syns även på Albins gamla installation. **vC128 får ALDRIG befordras till produktion.** Produktionsbygget blir vC129 med standardbrytpunkten; bygget skriver ut `Birdy release config: … GRANDFATHER_CUTOFF_MS=1790892000000 billingTestBuild=false`, kontrollera den raden.
>
> **Förberedelser i Play Console (AB):**
> 1. Skapa `premium_yearly_v1` (prenumeration med EN basplan: 1 år, förnyas automatiskt, eftersom appen säger "Förnyas årligen till <pris>") och `premium_lifetime_v1` (engångsköp). Sätt priser och aktivera båda.
> 2. Kontrollera att licensnyckeln under Monetization setup → Licensing är samma som `BIRDY_PLAY_LICENSE_KEY` i `~/.gradle/gradle.properties`. Klistra in den på nytt om du är osäker, och bygg om. **Fel nyckel betyder att varje riktigt köp misslyckas signaturkontrollen: kunden debiteras, ser "Köpet gick inte igenom", köpet kvitteras aldrig och Play återbetalar automatiskt efter 3 dagar.**
> 3. Lägg till ditt Google-konto som licenstestare och logga in med det på Galaxyn.
> 4. Ladda upp `birdy-1.3.0-vc128-KOPTEST-EJ-PRODUKTION.aab` (skrivbordet) till **Intern testning** och installera från Play.
>
> **HÅRD GRIND före vC129:** ett riktigt köp i vC128 ska ge Premium (skärmen stänger med "Välkommen, fältmedlem.") och `adb logcat -s PremiumBilling` får INTE visa `Signature verification failed`. Visas raden: stoppa, rätta licensnyckeln och bygg om. Produktionsbygget kräver också en NY MapTiler-nyckel (bygget stoppar med den läckta) och signeringsnyckeln.
>
> **Extra rutor för 1.3.0** (på svenska OCH engelska, Inställningar → Språk):
> - **Grund:** [ ] Premium-skärmen visar Plays priser (inte "Hämtar pris…" efter några sekunder); [ ] köpknappen är grå tills priset syns; [ ] texten under knappen visar rätt årspris respektive "Engångsköp. Ingen prenumeration."; [ ] avbrutet köp: skärmen står kvar, ingen välkomsttext; [ ] genomfört köp: skärmen stänger och "Välkommen, fältmedlem." visas; [ ] köp, avbryt, köp igen.
> - **Kvittering och återställning:** [ ] köp och döda appen direkt (`adb shell am force-stop se.birdy.android`), starta igen: köpet ska bli kvitterat (Play Console → Beställningar, ingen automatisk återbetalning efter 3 dagar); [ ] flygplansläge → Inställningar → Återställ köp → "Kunde inte nå Google Play"; [ ] Återställ köp under och efter ett köp.
> - **Väntande köp** (Plays testkort "Slow test card, approves after a few minutes" och "declines after a few minutes"): [ ] beskedet "Betalningen väntar…" visas; [ ] vänta kvar på skärmen: välkomst när kortet godkänns; [ ] lägg appen i bakgrunden under väntan, låt det godkännas, öppna igen: Premium på och exakt en välkomst; [ ] tryck på köpknappen igen under väntan: fortfarande "väntar", inte "gick inte igenom"; [ ] avböjt kort: förblir gratis, ingen välkomst.
> - **Redan ägt:** [ ] köp livstid igen när du redan äger det: skärmen stänger som klart; med ett väntande livstidsköp: "väntar".
> - **Nät:** [ ] kallstart i flygplansläge, slå på nätet: priserna syns inom ungefär 20 sekunder, annars efter att appen varit i bakgrunden en stund.
> - **3-D Secure / nytt betalsätt:** [ ] en välkomst, inget Premium-flimmer efteråt.
> - **Språkbyte:** [ ] byt språk med Premium aktivt: Premium kvar, ingen gammal aviseringslänk öppnas igen.
> - **Tidig användare** (debugbygge: Diagnostics → "Simulate early user", starta om): [ ] tack-skärmen visas en gång, dubbeltryck på Fortsätt ger ingen tom skärm, nästa start visar den inte; [ ] med "Skip premium override" på visas betalväggen i stället.
> - **Felsökning:** om priserna aldrig syns, kör `adb logcat -s PremiumBilling` och leta efter `unfetched=` (produkten saknas eller är inaktiv i Console) eller `responseCode=`.

> **När:** Innan vi flippar `PREMIUM_OPEN_FOR_LAUNCH=false` och släpper Birdy i produktion på Google Play.
> **Varför:** Override:n `premiumOverride = Active(LIFETIME)` som ligger på under closed testing maskerar hela "no premium → köpflöde → state-flip till Active"-vägen. Den vägen måste verifieras isär från overriden innan den möter riktiga betalande användare.
> **Status:** Item 1 (debug-toggle) **DONE 2026-06-17** (commit `c027a6f6`). Item 3 (BirdNET-licensguard) **DONE 2026-05-26**. Item 5 (grandfather) **implementerad i 1.3.0** (Plan 1, 2026-09-24/25: `GrandfatherPolicy` med båda källorna `firstInstallTimestamp` + `PackageInfo.firstInstallTime`, så även "rensa data" täcks; tack-skärm; samma bygge som `PREMIUM_OPEN_FOR_LAUNCH=false`). **Kvar:** item 2 (köptestet med vC128 på AB-kontot, se uppdateringen överst) och item 4 (uppföljning efter release).

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

## 5. Grandfather launch-period-användare (HARD GATE på flippen)

> **Implementerad i 1.3.0 (2026-09-24/25).** Texten nedan är det ursprungliga beslutet. Skillnader i det som byggdes: båda källorna används (DataStore-tiden OCH `PackageInfo.firstInstallTime`, som överlever "rensa data"), den tidigaste kända tiden sparas tillbaka vid varje start, brytpunkten är 2026-10-02 00:00 Stockholm (go-live + 48 h), och en tack-skärm visas en gång. Verifieras på enhet i Plan 3 (debugbygge: Diagnostics → "Simulate early user").

**Beslut (Albin 2026-06-17):** Alla som laddar ner appen *innan* monetiseringen slås på (`PREMIUM_OPEN_FOR_LAUNCH=false`) ska behålla full Premium **för alltid** — "inget snack om saken". Tidiga användare straffas aldrig av att vi börjar ta betalt.

**Varför hard gate:** Idag beräknas `premiumOverride = Active(LIFETIME)` *enbart* från `BuildConfig.PREMIUM_OPEN_FOR_LAUNCH` vid runtime (MainActivity). Flippas flaggan till `false` förlorar **alla** — inklusive tidiga användare — premium vid nästa uppdatering. Ingen kvarstående markering finns. Att flippa utan grandfather-logik = bryter beslutet.

**Mekanism (on-device, ingen backend — respekterar privacy-löftet):** Bygget som introducerar billing måste, *innan* det förlitar sig på billing-state, ge permanent `Active(LIFETIME)` till varje användare vars `firstInstallTimestamp` (redan persisterad i DataStore för ALLA användare sedan Plan 6a) ligger före en cutoff = billing-byggets rollout-datum. Nya installationer efter cutoff får gratis-tier + paywall.

```
effectivePremium =
    if (firstInstallTimestamp != null && firstInstallTimestamp < BILLING_LAUNCH_CUTOFF_MS)
        Active(LIFETIME)        // grandfathered launch-period user
    else
        <billing-state>          // new user: free tier + paywall
```

- Cutoff bakas in som konstant i billing-bygget.
- **Kräver NOLL kodändring nu** och NOLL ändring i den redan byggda vC125-AAB:n — `firstInstallTimestamp` sätts redan vid första uppstart för alla användare (MainActivity, Plan 6a), så alla nuvarande + framtida open-period-installationer täcks retroaktivt.
- Accepterad edge: rensar användaren app-datan nollställs `firstInstallTimestamp` → tappar grandfather. Oundvikligt utan backend; acceptabelt (sällsynt).

**Klart-kriterium:** Grandfather-logiken ligger i SAMMA build som flippar `PREMIUM_OPEN_FOR_LAUNCH=false`. Device-verify: en "gammal" install (firstInstall < cutoff) behåller premium efter uppdatering till billing-bygget; en fräsch install (efter cutoff) möter paywall.

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
1. Debug-toggle ✅  ──┐
                      ├─►  2. Billing-verify-checklista  ──►  5. Grandfather-logik + flippa PREMIUM_OPEN_FOR_LAUNCH=false (SAMMA build)  ──►  Production launch  ──►  4. Conversion-monitoring
3. License guard ✅ ──┘                                                                                                                                            (+7d, +14d checkpoints)
```

Punkt 1 (debug-toggle) och 3 (license guard) är redan på plats. Punkt 2 körs på AB-kontot efter account-transfer. **Punkt 5 (grandfather) MÅSTE ligga i samma build som flippar `PREMIUM_OPEN_FOR_LAUNCH=false`** — annars tappar tidiga användare premium, vilket bryter beslutet i §5. Punkt 4 startar dagen vi går live.

---

## Filer som rörs när detta körs

- ✅ `shared/datastore/.../UserPreferences.kt` (+ InMemory/Fake/Android-impl) — `skipPremiumOverride` tillagd (commit `c027a6f6`).
- ✅ `androidApp/.../MainActivity.kt` — läser flaggan vid uppstart, kortsluter override:n när satt (DEBUG-only).
- ✅ `composeApp/.../ui/debug/DiagnosticsScreen.kt` — toggle:n hamnade här (inte SettingsScreen) eftersom debug-skärmen är `null` i release → renare release-isolering i KMP-commonMain. Nås via Arkiv → Diagnostics.
- ⬜ **Grandfather (§5):** `androidApp/.../MainActivity.kt` (eller premium-resolution) — ge `Active(LIFETIME)` när `firstInstallTimestamp < BILLING_LAUNCH_CUTOFF_MS`; lägg cutoff-konstanten. MÅSTE i samma build som flippen.
- ⬜ `androidApp/build.gradle.kts` — flippa `PREMIUM_OPEN_FOR_LAUNCH=false` *efter* punkt 2 är grön OCH §5 ligger i bygget.
- `docs/superpowers/screenshots/v1.0-billing-verify/` — skärmdumpar (skapas vid verify).

## Referenser

- BirdNET-licensbeslut: auto-memory `project_birdnet_license_decision.md`.
- Billing v8 grunddesign: Plan 6b1 (`docs/superpowers/plans/2026-05-16-v1-06b1-billing-launch-prep.md`).
- Premium-feature gates: Plan 6b3 (`docs/superpowers/plans/2026-05-21-v1-06b3-premium-content.md`).
- Privacy-löfte: `docs/play-store/privacy-policy.md` + audit `2026-05-20-play-store-audit.md`.
