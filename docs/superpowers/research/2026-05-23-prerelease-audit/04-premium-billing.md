# Premium & Billing-audit — 2026-05-23

## Sammanfattning

Birdy Bird Scanner v1.0.0 är **ready för launch med Billing v8 IPC-verify deferred**. `PREMIUM_OPEN_FOR_LAUNCH=true` ger all users lifetime-premium under closed testing (avsiktligt). RSA SHA1-signature-verify implementerad + `acknowledgePurchase()` kallas. BirdNET-Lite (CC BY-NC-SA) är licens-säker — zero Premium gates på audio-ID. PDF-export, Season Statistics & Plan 6b3-badges är korrekt gate:ade bakom Premium. Restore Purchases-flow wired i Settings. Public key-slot finns men är tom (placeholder) — måste fyllas från Play Console pre-launch.

## Findings

### BLOCKER
Inga blockers. Billing-stacken är launch-ready.

### HIGH

**1. PLAY_LICENSE_KEY är blank placeholder**
- **Path:** `androidApp/build.gradle.kts:57-61`, `gradle.properties:1`
- **Issue:** `BIRDY_PLAY_LICENSE_KEY=` (tom) → RSA-verify alltid passerar i DEBUG, men i RELEASE blir signature-verify strikt. Pre-launch måste Public Key från Play Console RSA-sign certificate importeras.
- **Fix:** Extrahera public key från Play Console → base64-encode → set `BIRDY_PLAY_LICENSE_KEY=<base64>` i `gradle.properties` innan release-build.
- **Timeline:** Pre-launch (måste göras innan Upload till Play Console).

**2. Debug-flag `PREMIUM_DEBUG_FORCE_ACTIVE` existerar men ej dokumenterad för devs**
- **Path:** `androidApp/build.gradle.kts:89, 99`
- **Issue:** Finns tillgängligt för DEBUG-builds men ingen inline-doc förklarar det.
- **Fix:** Lägg till inline-kommentar i gradle-filen.
- **Timeline:** Nice-to-have (låg prio).

### MEDIUM

**1. Billing setup könner fail gracefully men loggar inte till Crashlytics**
- **Path:** `composeApp/src/androidMain/kotlin/se/birdy/app/data/premium/PremiumBillingClient.android.kt:106-112`
- **Issue:** Om `billingClient.startConnection()` failar → bara warn-log, state blir `Free`. No retry.
- **Fix:** Defer till Plan 6b3 polish.
- **Timeline:** Post-launch.

**2. `queryPurchases()` inte auto-triggered på app-foreground (resumé)**
- **Path:** `MainActivity.kt:219-221` — connect + queryPurchases körs endast på onCreate.
- **Issue:** Om user ger premium → startar om appen → purchasen kanske inte syncs direkt.
- **Fix:** LaunchEffect i App.kt eller MainActivity.onResume() för att callå `billingClient.queryPurchases()` igen.
- **Timeline:** Post-launch.

### LOW / Nice-to-have

**1. `PurchaseResult` typ-hierarki är verbose**
- **Path:** `composeApp/src/commonMain/kotlin/se/birdy/app/data/premium/PremiumBillingClient.kt:36-44`
- **Issue:** OK för launch, men kunde ha unified error-handling i future.
- **Timeline:** Future refactor.

**2. No in-app messaging för connectivity errors (offline, Play Services unavailable)**
- **Path:** `PremiumBillingClient.android.kt:110` — log varning, men UI får inget feedback.
- **Fix:** Future: show SnackBar i PremiumScreen om billingClient ej ansluten.
- **Timeline:** Post-launch polish.

## License-säkerhet — verifierat

### BirdNET-Lite Audio-ID: ✅ GRATIS FÖR ALLA (CC BY-NC-SA)
- ✅ **ListenLauncherScreen** — ZERO Premium-gates. Audio-card alltid clickable.
- ✅ **AudioScanViewModel** — Kräver `audioClassifierProvider` (nullable), ingen `isPremium`-check.
- ✅ Revs från ListenLauncher 2026-05-22 per memory.

### Plan 6b3 Premium-features — ✅ GATED KORREKT
1. **PDF-Export** — Gatead via `effectivePremiumActive` check i PremiumTeaserCard
2. **Season Statistics** — Markerad Premium-tier, accessad via tab-gate
3. **10 Plan 6b3-badges** — `premiumBadgeDefs` filter + `premiumActive` check
4. **Match Disambiguation pro** — Ej implementerad ännu (deferred)

### Signature-verify — ✅ IMPLEMENTERAD STRIKT
- ✅ RSA SHA1 med base64 decoding, aldrig grant access utan verify-pass
- ✅ Failed verify → log error, state blir `Free`
- ✅ Public key från `BuildConfig.PLAY_LICENSE_KEY` (tom nu, måste fyllas pre-launch)

## PREMIUM_OPEN_FOR_LAUNCH-flagga — status

**Var sätts den:**
- `androidApp/build.gradle.kts:67` — `buildConfigField("Boolean", "PREMIUM_OPEN_FOR_LAUNCH", "true")`

**Var används den:**
- `MainActivity.kt:232-239` — Om true → `premiumOverride = PremiumState.Active(LIFETIME)`

**Kommentar kring flip:**
```
// PREMIUM_OPEN_FOR_LAUNCH (defaultConfig=true) forces every user to Active(LIFETIME)
// through the closed-testing + initial production window. Toggle off in
// androidApp/build.gradle.kts when Billing v8 monetization goes live.
```

**Flip-process:** Ändra `true` → `false`, bump versionCode, test, upload.

---

## Billing v8 IPC Runtime-Verify — deferred till post-launch

Play Console kräver Live app + license-tester-grupp + public key konfigurerad. **Denna audit täcker STATIC code-review.**
