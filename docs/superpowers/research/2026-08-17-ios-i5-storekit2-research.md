# i5-research: StoreKit 2 på Kotlin/Native — arkitektur, enrollment-avkoppling, spike

> **Syfte:** de-riska i5 (StoreKit 2) före brainstorm/spec — samma mönster som
> `2026-08-15-ios-i3-flex-select-ops-research.md` gjorde för i3. Skriven 2026-08-17 (Mac).
> Huvudfynden är **empiriskt bevisade lokalt** (klib-dump + kodläsning), inte antaganden.

## TL;DR

1. **StoreKit 2 är osynligt för Kotlin/Native** — bevisat via `klib dump-metadata` på
   `platform.StoreKit` (K/N 2.1.20, ios_arm64): `Product`, `Transaction`, `AppStore`,
   `VerificationResult` finns INTE i bindningen (SK2 är ett rent Swift-API, inte @objc).
   Hela StoreKit 1-ytan finns (`SKProduct`, `SKPaymentQueue`, `SKProductsRequest`, …) men
   SK1 är deprecated och kräver DIY-kvittovalidering → fel väg 2026.
2. **⇒ Swift-bro är obligatorisk** — exakt samma husmönster som i4:s kart-tile-bro
   (`BirdyTileOverlay.swift` → Kotlin `IosTileFetcher`, factory-registrerad via
   `IosMapOverlayBridge` från `iOSApp.swift`). Swift-inventariet växer från 2 till ~3 filer
   — väl inom "minimalt Swift-lager"-doktrinen.
3. **Enrollment blockerar INTE i5-utvecklingen** — StoreKit Testing i Xcode
   (`.storekit`-konfigfil + scheme-inställning) kör riktiga köpflöden **i simulatorn utan
   Apple Developer-konto och utan App Store Connect**. Enrollment (checklista #1) behövs
   först för sandbox-på-device + Console-produkterna + TestFlight (i6). Kalenderrisken
   ligger kvar på enrollment-ledtiden, inte på kodfasen.

## 1. Empirin: vad K/N exponerar av StoreKit

Kommando (samma teknik som i4:s `MKTileOverlay`-final-bevis):

```bash
~/.konan/kotlin-native-prebuilt-macos-aarch64-2.1.20/bin/klib dump-metadata \
  ~/.konan/kotlin-native-prebuilt-macos-aarch64-2.1.20/klib/platform/ios_arm64/org.jetbrains.kotlin.native.platform.StoreKit
```

Resultat (2026-08-17): bindningen innehåller **43 SK*-klasser** — komplett StoreKit 1
(`SKProduct`, `SKPaymentQueue`, `SKPayment`, `SKPaymentTransaction`, `SKProductsRequest`,
`SKReceiptRefreshRequest`, `SKStorefront`, `SKStoreReviewController`, …) men **noll**
StoreKit 2-typer (`Product`: NEJ, `Transaction`: NEJ, `AppStore`: NEJ,
`VerificationResult`: NEJ). SK2:s API är Swift-only (async/await, generics) och kan aldrig
dyka upp i en ObjC-baserad cinterop-bindning — detta ändras inte av K/N-bumpar.

**Varför inte SK1 från Kotlin (ingen Swift alls)?** Tekniskt möjligt men fel:
SKPaymentQueue-familjen är formellt deprecated (iOS 18-erans SDK:er), kvittovalidering blir
DIY (lokal PKCS7-parsning eller server — servern bryter privacy-löftet/no-backend), och
entitlement-hanteringen är manuell. SK2 ger on-device JWS-verifiering gratis
(`VerificationResult.verified`), `Transaction.currentEntitlements` (fungerar offline,
StoreKit cachar), och `Product.displayPrice` (lokaliserad prissträng — ersätter Plays
`formattedPrices`-bygge). Beslutet "StoreKit 2" i plan-of-plans står sig.

## 2. Ytan som ska fyllas (kodläsning 2026-08-17)

`composeApp/src/commonMain/.../premium/PremiumBillingClient.kt` (expect):

```
val state: StateFlow<PremiumState>            // Free | Active(tier, since)
val formattedPrices: StateFlow<FormattedPrices>  // yearly/lifetime som lokaliserade strängar
suspend fun connect()
suspend fun queryPurchases()
suspend fun launchPurchase(activityContext: Any, tier: PremiumTier): PurchaseResult
fun dispose()
```

- iOS-actualen är idag en stub (`PremiumBillingClient.ios.kt`: Free + Error på köp).
- **iOS-grafen kör `premiumOverride = Active(LIFETIME)`** (`IosAppGraph.kt:222`) för
  Android-launch-paritet. **i5 ska ta BORT overriden på iOS** — beslutet 2026-07-07 är
  paywall aktiv dag 1 på iOS (ingen open-for-launch, ingen grandfather — de är
  Play-specifika).
- Produkt-id-paritet (Android, `PremiumBillingClient.android.kt:35-36`):
  `premium_yearly_v1` = **subscription** (SUBS), `premium_lifetime_v1` = **engångsköp**
  (INAPP). iOS-spegeln: auto-renewable subscription (kräver Subscription Group i Console)
  + non-consumable. Samma id:n kan återanvändas i App Store Connect (id:n är per butik).
- `activityContext: Any` är Android-ism (Activity för köparket) — iOS-actualen ignorerar
  den (SK2:s `purchase()` ankrar själv i aktiv scen på iOS; `confirmIn:` är visionOS/macOS).
- Persistens/graf-wiring finns redan delat: `PremiumStateStore` (datastore-modulen) +
  `PremiumActivationListener`/`effectivePremiumActive` (Plan 6b3 T19) — iOS-actualen ska
  bara mata `state`, resten är byggt.

## 3. Föreslagen arkitektur (spegel av tile-bron)

**Swift-sidan** (`iosApp/iosApp/BirdyStoreKit.swift`, ny — tredje Swift-filen):
@objc-synligt bridge-objekt som wrappar SK2:s async-API i completion-handlers:

- `loadProducts(ids:completion:)` → `Product.products(for:)` → (id, displayPrice, type)
- `purchase(id:completion:)` → `product.purchase()` → utfall-enum (success/userCancelled/
  pending/failed) + endast `.verified`-transaktioner räknas (JWS-checken)
- `currentEntitlements(completion:)` → itererar `Transaction.currentEntitlements`,
  returnerar aktiva produkt-id:n
- `startTransactionUpdates(onEntitlementChange:)` → `Transaction.updates`-lyssnare
  (måste starta tidigt vid launch — missade köp levereras här; `await transaction.finish()`)
- `restore(completion:)` → `AppStore.sync()` (explicit användar-action, kan trigga inlogg)

Registreras från `iOSApp.swift` init (samma plats som kart-bron) via ett Kotlin-object
(`IosStoreKitBridge` i iosMain, spegel av `IosMapOverlayBridge`).

**Kotlin-sidan** (`PremiumBillingClient.ios.kt`, ersätter stubben):
suspendCancellableCoroutine-wrappers runt bridge-callbacks; mappar utfall →
`PurchaseResult`; `connect()` = ladda produkter + starta updates-lyssnaren + läs
`currentEntitlements` → `state`; `queryPurchases()` = re-läs entitlements.

**Öppen mappningsfråga till brainstormen:** SK2-köp kan bli **`.pending`** (Ask to Buy /
familjegodkännande) — `PurchaseResult` (delad kod) har bara Success/UserCancelled/Error.
Alternativ: mappa pending → Error med egen sträng (enklast, ingen common-ändring) eller
utöka sealed-interfacet (Android-sidan berörs). Beslut i spec:en.

## 4. Enrollment-avkoppling + testväg (viktigast för kalendern)

| Miljö | Kräver enrollment? | Vad som kan verifieras |
|---|---|---|
| **StoreKit Testing i Xcode** (`.storekit`-fil, simulator) | **NEJ** | Hela köpflödet: produkter, köp-ark, pending (Ask-to-Buy simuleras), refund, subscription-förnyelse i snabbspolning, restore — utan Console, utan nätverk |
| Sandbox på fysisk device | JA (sandbox-testare i Console) | Riktiga StoreKit-servrar, riktig Apple-ID-inlogg |
| TestFlight/App Store (i6) | JA | Produktion |

`.storekit`-filen definierar produkterna lokalt (`premium_yearly_v1` som årsprenumeration i
en subscription group + `premium_lifetime_v1` som non-consumable) och committas i repot.
Koppling: scheme-inställningen *StoreKit Configuration* — xcodegen stödjer
`storeKitConfiguration` under schemets `run`-sektion (verifieras i spiken; fallback är att
sätta den i den delade schemen manuellt/via projekt-yml-options). **⇒ i5:s kodfas + Albins
sim-check kan köras KLART före enrollment** — endast device-sandbox-punkten och i6 väntar
på Apple.

## 5. Föreslagen spike (i2b-T1/i3-spegel, första plan-tasken)

Walking skeleton som bevisar hela kedjan innan resten byggs:

1. `.storekit`-fil med båda produkterna + xcodegen-scheme-wiring.
2. Minimal `BirdyStoreKit.swift` med `loadProducts` + `purchase(lifetime)`.
3. Kotlin-bridge + temporär debug-knapp (eller test) som köper lifetime i simulatorn →
   `state` flippar till `Active(LIFETIME)`.
4. Gate: köpet syns i Xcodes Transactions-inspektör; app-omstart → entitlement kvarstår
   (currentEntitlements-vägen).

Risker att bevaka i spiken: (a) `Transaction.updates`-lyssnarens livscykel vs Kotlin-skopet
(får inte dö med en VM — ska ligga i grafen/bootstrap, jfr i2c:s
delad-singleton-trap); (b) xcodegen-scheme-stödet för StoreKit-config; (c) `purchase()`
kräver foreground-scen — anropas från main-tråd (jfr share-sheet-ankaret i i4).

## 6. Vad som INTE ingår i i5 (avgränsning att bekräfta i brainstorm)

- Ingen server-side receipt-validering (privacy-löftet + no-backend står).
- Ingen grandfather-mekanik på iOS (Play-specifik; dag-1-paywall-beslutet 2026-07-07).
- Prisexperiment/intro-offers — inget sådant finns på Android-sidan; paritet först.
- `manage subscriptions`-deep-link + refund-request-UI — nice-to-have, kan bli
  checklist-punkt i i6 istället.
