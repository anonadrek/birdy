# Plan 6b1 — Billing & Launch Prep (v0.9.0a-billing)

**Datum:** 2026-05-16
**Status:** Godkänd design — väntar på implementations-plan
**Spec-typ:** Sub-plan inom Plan 6 ("v1.0 Play Store launch")
**Föregående plan:** Plan 6a (Foundation, `v0.8.0-rc1`)
**Tag vid slut:** `v0.9.0a-billing`
**Tidsbudget:** 5–7 dagar
**Efterföljande sub-planer:** 6b2 (Audio-ID, `v0.9.0b-audio`) → 6b3 (Premium-content, `v0.9.0c-premium-content`) → `v1.0.0`

---

## 1. Bakgrund och syfte

Plan 6a stängde appen som **submit-redo som free-only** (`v0.8.0-rc1`, versionCode 100, versionName 1.0.0-rc1). 6b1 är första av tre sub-planer som tillsammans levererar v1.0 till Play Store. Den fokuserar på **launch-blockerare** — saker som måste vara på plats innan Closed Testing-spåret kan startas hos Google och innan vi kan submission:a en signed AAB för Production-review.

Plan 6b dekomponeras i tre sekventiella sub-planer:

| # | Sub-plan | Mål | Tag |
|---|---|---|---|
| **6b1** | **Billing & Launch Prep** (denna spec) | Real Billing v8 + Premium UI-fixar + ML-diagnos + test-infra + ops-prep | `v0.9.0a-billing` |
| 6b2 | Audio-ID | BirdNET-Lite TFLite-modell + audio-classifier-pipeline | `v0.9.0b-audio` |
| 6b3 | Premium-content | PDF-export + säsongs-stats + 10 fält-märken | `v0.9.0c-premium-content` |

**6b1-syfte:** Göra appen **closed-testing-redo**. När 6b1 är klart kan vi (a) ladda upp signed AAB till Play Console Closed Testing-track, (b) starta 14-dagars-kravet på 12 testare, och (c) parallellt börja 6b2-arbete. Om audio eller PDF blir blockerad senare kan 6b1+6a-funktionalitet teoretiskt släppas som `v1.0.0-no-audio` med "audio coming in v1.1"-disclosure.

**Strategisk position i launch-roadmap:** Plan 6b1 levererar Tier 1-items A (Billing & purchase-flow) + B (Premium UI-fixar) + D (Test-infra) + E (carry-overs) från `docs/superpowers/research/2026-05-15-play-store-launch/00-launch-roadmap.md` samt Phase 1 ML-diagnos från `project_plan_scheduling.md`. Tier 1-item C (Premium-feature-leverans) är dekomponerad till 6b2 + 6b3.

## 2. Scope

### 2.1 In scope (10 task-bundles)

| # | Område | Förändring |
|---|---|---|
| T0 | Closed Testing-spår | Skapa Play Console Closed Testing-track med 12 testare opt-in (14-dagars Google-krav). Förbered Internal Testing som backup för device-verify. |
| T1 | Privacy/Terms-hosting | Aktivera GitHub Pages på repo:t; verifiera `https://anonadrek.github.io/birdy/privacy.html` + `/terms.html` returnerar HTTP 200. Pandoc-workflow finns redan från 6a (`.github/workflows/pages.yml`). |
| T2 | ML preprocessing Phase 1 diagnos | Bygg DEBUG-only `DiagnosticsScreen` (gated via `AppGraph`-lambda-mönstret från Plan 4b) som dumpar 224×224 input-tensor + top-5 från device för 3 corpus-bilder; jämför mot desktop `tools/ml-eval/`-output; identifiera root cause. Tidsboxad 1d; **fix-beslut tas efter diagnos** (se T8). |
| T3 | Test-image-infra | `MatchResultViewModel.resolve()` läser `match_override.txt` från `filesDir` i debug-builds; format `qid:confidence`; gated via `AppGraph.matchOverrideReader: (() -> MatchOverride?)?`. Dokumentera adb-push-workflow i `docs/superpowers/runbooks/2026-05-16-test-image-infra.md`. |
| T4 | Google Play Billing v8 | `BillingClient` setup i `:composeApp/androidMain`; produkter `premium_yearly_v1` (SUBS) + `premium_lifetime_v1` (INAPP); purchase-launch-flow; `acknowledgePurchase` synkront i `PurchasesUpdatedListener`; cold-start `queryPurchasesAsync` i `MainActivity` parallellt med `ClassifierBootstrap`; on-device signature-verify mot `BuildConfig.PLAY_LICENSE_KEY`. Ersätter `purchase()`-mock i `PremiumViewModel`. |
| T5 | Restore Purchases | Ny rad i `SettingsScreen` ("Restore purchases" / "Återställ köp") + `SettingsViewModel.restorePurchases()` triggar `queryPurchasesAsync(SUBS)` + `queryPurchasesAsync(INAPP)` + emittar `SettingsEffect.ShowToast(restoredOrEmpty)` → Caveat-toast. Play Policy-krav. |
| T6 | Premium UI-fixar | (a) Ta bort `premium_tier_yearly_stamp`-strängen + `StampLabel`-rendering i `PremiumScreen.kt` (EU Omnibus + dark-pattern-policy). (b) Fixa EN-valuta-bug i `composeResources/values-en/strings.xml` rad 452-453 (säger "199 kr / year"; ändras till `199 SEK / year` som fallback; runtime override via `ProductDetails.getFormattedPrice()` när Billing-state är loaded). (c) Lägg till `premium_auto_renew_disclosure` Caveat-sub under Yearly TierCard: "Auto-renews yearly at 199 SEK · Cancel anytime" / "Förnyas årligen 199 kr · Avbryt när som helst." på respektive locale. |
| T7 | Cold-start-modal throttle | `EntryFlowDecider.shouldShowPremiumModal()` ändras från 1×/dag till **1×/3d + 7d first-install-grace**. Ny DataStore-key `firstInstallTimestamp: Long?` sätts på första boot om null; befintliga `v0.8.0-rc1`-användare som uppdaterar får `now - 8d` så de inte ser modal direkt efter update. |
| T8 | ML preprocessing fix (gated av T2) | Om T2-diagnos visar enkel root cause (rotation, stretch, RGB/BGR) → implementera och device-verifiera. Om non-actionable → välj fallback-spår (a) sänk threshold 0.10 → 0.05 så Disambig blir primary path (USP-narrativ skiftar till "Du och AI:n hjälps åt"). |
| T9 | TalkBack-walkthrough | SM-S918B walkthrough: scan → match → save → archive → species profile → badges → settings → premium → restore. P0 (köp-flow-blockerande) fixas i samma session; P1 fixas om <30 min vardera; P2+ defer:as till `docs/superpowers/research/2026-05-16-talkback-walkthrough.md`. |
| T10 | Device-verify + tag | Hel premium-flow med Play Console test-konto på SM-S918B: launch → modal-grace → settings → premium → purchase yearly → ack → state-flip → pm clear → restore → state-restore. Screenshots committed. Tagga `v0.9.0a-billing`. |

### 2.2 Out of scope (deferrat till 6b2/6b3 eller helt utanför)

| Deferral | Plan | Anledning |
|---|---|---|
| Audio-ID (BirdNET-Lite) | 6b2 | HIGH-risk wildcard (5–7d); separat plan ger flexibilitet om audio failar |
| PDF-export | 6b3 | Premium-feature-leverans, inte launch-blocker |
| Säsongs-statistik (`LockedStatsPreview` → riktig) | 6b3 | Premium-feature-leverans |
| 10 premium fält-märken (content + ikoner + evaluator) | 6b3 | Premium-feature-leverans + kräver illustration |
| Re-ordna premium-features så Audio är överst | 6b3 | Görs när Audio faktiskt finns |
| Skala ner per-tab teasers | — | INTE i scope — du valde "bygg features istället för att skala ner" i Tier-1-besluten (locked decision 5 i launch-roadmap) |
| Pris-bump 199→299 / 499→699 | — | INTE i scope — låst som "introduction price" (locked decision 4) |
| Månads-tier (49–59 kr/mån) | v1.1+ | Subscription fatigue + decision-fatigue UX |
| Free trial (7d) | v1.1+ | Kräver att audio + PDF faktiskt fungerar i prod |
| Server-side receipt validation | v1.x | On-device räcker för 199 kr-app |
| In-App Messaging för grace-period | v1.x | Polling vid cold-start räcker |

### 2.3 Success criteria

1. Real purchase fungerar end-to-end på SM-S918B mot Play Console test-track (yearly + lifetime båda testade)
2. `acknowledgePurchase` triggas inom samma listener-callback som purchase-completion (verifierat via `Purchase.isAcknowledged == true` i nästkommande `queryPurchasesAsync`-resultat)
3. `BillingClient.queryPurchasesAsync` återställer entitlement efter `pm clear se.birdy.android` + relaunch (= Restore Purchases-flow utan användarinput)
4. "Spara 60%"-stämpeln finns inte i någon byggd APK, varken i SV- eller EN-locale (verifierat via apkanalyzer + uiautomator)
5. EN-locale visar `199 SEK / year` (eller Google-formatterad lokal valuta efter Billing-init) — inte `199 kr / year`
6. Cold-start-modal triggas INTE inom 7d efter första install; INTE oftare än 1×/3d därefter
7. `match_override.txt`-flow demonstrerar Disambig + NoBird-rendering med faktisk classifier-pipeline (inte mock)
8. ML diagnos-rapport committad till `docs/superpowers/research/2026-05-16-ml-preprocessing-diagnos.md` med root cause + fix-effort-estimat
9. `privacy.html` + `terms.html` returnerar HTTP 200 från `https://anonadrek.github.io/birdy/`
10. TalkBack-walkthrough genomförd; P0-issues fixade; P1/P2 dokumenterade
11. Closed Testing-spår live i Play Console med ≥12 testare opt-in (eller Internal Testing-backup aktivt)
12. Tag `v0.9.0a-billing` på main; versionCode `110`, versionName `1.0.0-rc2`

## 3. Arkitekturbeslut

### A. Billing-client-placering

`BillingClient` är Android-only API. **Beslut:** `PremiumBillingClient` som `expect class` i `composeApp/commonMain/.../premium/PremiumBillingClient.kt`. Android `actual` wrappar `com.android.billingclient.api.BillingClient` i `composeApp/androidMain`. iOS `actual` = no-op stub (returnerar alltid `PremiumState.Inactive` + no-op `launchPurchase()`).

Ingen ny Gradle-modul — Plan 4b skapade `:shared:ml` för att ML hade flera klasser + assets + build-time validators. Billing-wrapping är en enskild expect/actual-yta och hör hemma direkt i `composeApp`.

**Konsekvens:** `PremiumRepository` (commonMain) konsumerar `PremiumBillingClient` så `PremiumViewModel` förblir platform-agnostic. När iOS-plan startas behöver bara `PremiumBillingClient`-actual swappas mot StoreKit-equivalent.

### B. Premium-state-source-of-truth

`PremiumRepository.state: StateFlow<PremiumState>` läses från:
1. DataStore-cache vid cold-start (instant render, även offline)
2. `BillingClient.queryPurchasesAsync` när BillingClient connected (canonical truth)

Vid query-success skrivs cache → DataStore aldrig är stale > 1 session. Vid query-failure (Google Play services unavailable, network error) faller vi tillbaka på cached state men loggar varning. **Inte fail-loud:** premium-användare ska inte plötsligt få inactive-state för att Google Play hade en hicka.

### C. Receipt validation

On-device only — Birdy har ingen backend.

Play Console → Monetization Setup → Licensing public key bakas in i `BuildConfig.PLAY_LICENSE_KEY` (base64-encoded RSA pub). `PremiumBillingClient`-actual exponerar internt `verifyPurchase(json, signature)` som gör RSA-verify (~50 LOC: BASE64-decode key → `KeyFactory.getInstance("RSA")` → `Signature.getInstance("SHA1withRSA")` → `Signature.verify(signedBytes)`).

Failed verification → reject purchase + log + treat som inactive. Risk: motiverad attacker kan modda APK för att kringgå check, men för 199 kr-app med svensk målgrupp inte värt server-side-skydd.

### D. Acknowledgement-flöde

`PurchasesUpdatedListener.onPurchasesUpdated()` kallar `acknowledgePurchase()` **innan** state-update emittas till `PremiumRepository`. Synkront i listener; ingen separat work-queue.

Misslyckad ack → reject purchase + log + visa user-facing felmeddelande "Köpet kunde inte slutföras. Försök igen eller kontakta support." Annars triggar Google auto-refund efter 72h vilket är värre UX än fail-loud direkt.

### E. Refund-/revoke-polling

Endast vid cold-start (i `MainActivity` parallellt med `ClassifierBootstrap`). Ingen 24h-WorkManager-polling i v1.0 — för komplext vs nyttan.

**Konsekvens:** refund som inträffar mitt under en session reflekteras först vid nästa app-start. Acceptabelt för 199 kr-app; dokumenteras som känd begränsning. Add WorkManager-polling i v1.1 om reports indikerar att användare upplever "betalde men inga premium-features tills jag starta om appen"-bug.

### F. `match_override.txt`-format och gating

**Format:** `qid:confidence` på en rad. Exempel: `Q25356:0.42` (för Disambig-band). Plats: `context.filesDir/match_override.txt`.

**Gating:** `AppGraph.matchOverrideReader: (() -> MatchOverride?)?` — null på release-builds, populated på debug. `MatchResultViewModel.resolve()` kallar `graph.matchOverrideReader?.invoke()` innan threshold-routing; om non-null → ersätter top-1 confidence + binder QID.

**Parse-validering:** split på `:`, verifiera `qid` matchar `^Q\d+$`, `confidence` är `Float in 0.0..1.0`. Malformat → log + ignorera (treat som null).

**Cleanup:** filen läses men tas **inte** bort efter användning. Låter ADB-pipeline pusha en gång och köra Save flera gånger för bulk screenshots. Manuell rm via `adb run-as se.birdy.android rm files/match_override.txt` när override inte längre önskas.

**Workflow-dokumentation:** `docs/superpowers/runbooks/2026-05-16-test-image-infra.md` skapas i T3 med adb-kommandon för push, verify, clear samt example `qid:confidence`-värden för att hitta varje threshold-band.

### G. DataStore-tillägg för modal-throttle

Lägg till en ny key i `UserPreferences`:
- `firstInstallTimestamp: Long?` — set on first boot om null; används för 7-dagars first-install-grace

Befintliga keys används:
- `premiumModalLastShown: Long` (från Plan 7e) — uppdateras vid varje modal-visning
- `premiumActive: Boolean` (från Plan 7e) — never show modal om true

`EntryFlowDecider.shouldShowPremiumModal(now, prefs)` returns `false` om:
1. `prefs.premiumActive == true` — never show to paying users
2. `prefs.firstInstallTimestamp == null` — first boot, vänta till nästa cold-start
3. `now - prefs.firstInstallTimestamp < 7 * 24 * 3600 * 1000` — grace period
4. `now - prefs.premiumModalLastShown < 3 * 24 * 3600 * 1000` — throttle

Annars `true`.

**Migration för befintliga användare:** Om `firstInstallTimestamp == null` AND `premiumModalLastShown > 0` (= existing user som har sett minst en modal i v0.8.0-rc1) → sätt `firstInstallTimestamp = now - 8d`. Net effect: existing users ser modal ~3d efter update (om throttle medger), nya installs ser den ~7d in.

### H. EN-valuta-string

Behåller 199/499-priser. Två-stegs-lösning:

1. **Statisk fallback i `values-en/strings.xml`:** `"199 SEK / year"` + `"499 SEK · one-time"` — clear currency-code, korrekt för base price som Google sedan regional-converterar.
2. **Dynamisk runtime-override:** När Billing v8 har query:at `ProductDetails`, hämta `getFormattedPrice()` och lagra i state. PremiumScreen visar `state.formattedYearlyPrice ?: stringResource(Res.string.premium_tier_yearly_price)`.

**Konsekvens:** En kort flash från fallback → localized price om paywall öppnas inom ~2s av cold-start (Billing init är async). Acceptabelt UX.

### I. Product-IDs i Play Console

- Subscription (`ProductType.SUBS`): **`premium_yearly_v1`**
- One-time (`ProductType.INAPP`): **`premium_lifetime_v1`**

`v1`-suffixet är intentional: Google-policy förbjuder att ändra etablerade products. Om vi vill bumpa priset i v1.x → skapa `premium_yearly_v2` parallellt och migrera nya köp dit; existing v1-subscribers behåller sitt pris.

**Play Console-config för 6b1:**
- Base SEK-pris 199 / 499 (auto-converted till EUR, USD, NOK, DKK, CHF, GBP osv)
- Regional bumps: NO 349 / 799 NOK, CH 35 / 79 CHF, DK 249 / 599 DKK
- Regional dumps: PL/CZ/HU ~50% av base
- Grace period: 7 dagar (configurable i Play Console → Subscriptions → premium_yearly_v1 → Grace period)
- Account hold: 30 dagar (Play Console default, configurable)

### J. ML-diagnos-debug-screen

Ny `DiagnosticsScreen` (DEBUG-only, gated via en ny `AppGraph.diagnosticsScreen: (@Composable () -> Unit)?`-lambda — parallell till befintlig `AppGraph.debugBenchmarkScreen` från Plan 4b). Wirad via `EncyclopediaScreen` overflow-meny precis som BenchmarkScreen.

**Tre tester per körning:**
1. Load 3 corpus-bilder från `composeApp/src/androidMain/assets/benchmark/` (samma som BenchmarkScreen — placeholder-JPEG flagged i Plan 4b post-tag follow-ups; ersätts som bonus-task här om tid finns)
2. Kör genom `ImagePreprocessor` → dump 224×224 uint8-tensor till `filesDir/preprocess_dump_{ts}.json` (RGB-bytes som base64 + dimensions)
3. Kör genom `TfLiteBirdClassifier` → log top-5 + confidences till samma JSON

**Output → research-rapport:** `docs/superpowers/research/2026-05-16-ml-preprocessing-diagnos.md` med:
- Per-bild: device top-5 vs desktop top-5 (jämför `tools/ml-eval/` output)
- Per-bild: input-tensor-diff (sample of pixel values: device vs desktop)
- Root cause-hypotes (rotation, stretch, RGB/BGR, eller annat)
- Fix-effort-estimat (timmar/dagar)
- Recommendation: ship fix in 6b1 (T8) eller defer post-v1.0?

## 4. Risker + mitigering

### Risk 1 — `BuildConfig.DEBUG` finns inte i commonMain

**Trigger:** T3 (`match_override.txt`-läsning) ligger i commonMain men `BuildConfig` är Android-specifikt.

**Mitigering:** Återanvänd `AppGraph.debugBenchmarkScreen != null`-mönstret från Plan 4b. Lägg till `AppGraph.matchOverrideReader: (() -> MatchOverride?)?` — null på release-builds, populated på debug. `MatchResultViewModel` kallar `graph.matchOverrideReader?.invoke()` innan threshold-routing.

### Risk 2 — Play License Key i versionskontroll

**Trigger:** T4 — `BuildConfig.PLAY_LICENSE_KEY` får inte committas till git (säkerhetsrisk + Google ToS-brott).

**Mitigering:** Injecta via `gradle.properties` (lokal, `.gitignored`) eller env var `BIRDY_PLAY_LICENSE_KEY`. `composeApp/build.gradle.kts` läser via `project.findProperty()` med fallback till `""` (release-build fail-fast om tom). Lägg till `BIRDY_PLAY_LICENSE_KEY=` i `gradle.properties.example` (committad) som dokumentation.

### Risk 3 — `firstInstallTimestamp = null` för befintliga användare

**Trigger:** T7 — DataStore-schema har inte `firstInstallTimestamp` förrän nu. Befintliga `v0.8.0-rc1`-användare som uppdaterar får null → utan migration skulle de få cold-start-modal direkt efter update.

**Mitigering:** Vid `firstInstallTimestamp == null` AND `premiumModalLastShown > 0` (= existing user) på första boot efter uppdatering → sätt `firstInstallTimestamp = now - 8d`. Nya installs (där `premiumModalLastShown == 0L`) → sätt `firstInstallTimestamp = now` så de får full 7d grace.

### Risk 4 — ML-diagnos visar non-actionable root cause

**Trigger:** T2 hittar att problemet är något annat än rotation/stretch/RGB-BGR (t.ex. AIY V1-modellen är fundamentalt dålig på svenska arter, eller fält-fotona har lighting-bias som eval-corpus saknar).

**Mitigering — pre-committed fallback-spår:**

- **(a) Sänk threshold ytterligare** (0.10 → 0.05) i `MatchThresholds.routeFor()` så `NoBird` blir extremt ovanligt — låter Disambig-skärmen göra jobbet ("välj rätt fågel manuellt"). USP "ärlig osäkerhet" funkar med Disambig som primary path.
- **(b) Acceptera 10% hit-rate** och fokusera v1.0-marknadsföring på "encyclopedia + diary + gamification" istället för "AI identifies". Risk: 1-star reviews från användare som förväntade sig Merlin-grade ID.

**Beslut vid T8-gating:** Om diagnos är non-actionable → välj (a) (snabb code-change, ~2h) + dokumentera i `project_play_store_launch_research.md` att marknadsförings-narrativet flyttas från "AI identifies" till "Du och AI:n hjälps åt".

### Risk 5 — Compose-resources string-escape regression (3:e gången)

**Trigger:** T6 + T7 lägger till nya strängar (`premium_auto_renew_disclosure`, modal-strängar). Plan 5a-trap (`%%` blir `%%` inte `%`) och Plan 7d-trap (`\'` lämnas literal) bet två gånger redan.

**Mitigering — pre-task-checklist:**
- Alla `%`-tecken: använd `%1$s` + pre-format `"${value}%"` från Kotlin
- Alla apostrofer: använd Unicode `’` (U+2019) direkt, INTE `\'`
- Hardcoded text på TierCards: alltid via `stringResource(...)`, ingen literal i Kotlin

**Strukturell mitigering:** Bonus-task i 6b1 (om tid finns) — lägg till `validateStrings` Gradle-task som scannar `composeResources/values*/strings.xml` för `\'` och `%%` och failar build. Modellerad efter `validateBadgesYaml` från Plan 5b.

### Risk 6 — Closed Testing-spår kräver mer än 14 dagar

**Trigger:** T0 — Google kan kräva "policy review" som tar ytterligare 3–7 dagar utöver 14-dagars test-perioden, särskilt för nya personliga developer-konton.

**Mitigering — två parallella spår i T0:**
- **(a) Closed Testing via Play Console** (standard — krävs för 14d-grace)
- **(b) Internal Testing-track som backup** (ingen 14d-grace; instant access; max 100 testare; kräver bara `internal@birdy.se`-style invites). Internal kan inte avancera till Production direkt MEN det låter oss device-verifiera Billing-flow utan att vänta på Closed Testing-approval.

**Pre-committed contingency:** Om Closed Testing inte godkänts senast 2026-05-29 (T-3 dagar från launch) → flytta launch till 2026-06-08 (per launch-roadmap rad 176-beslut).

### Risk 7 — TalkBack-walkthrough avslöjar 20+ regressions

**Trigger:** T9 — vi har inte testat hela appen med TalkBack sedan Plan 6a §5 (partial, ~7 skärmar). Nya Premium-rad i Settings + Restore Purchases-rad + auto-renew-disclosure är otestade.

**Mitigering — tiered acceptance:**
- **P0 = elementen som blockerar köp-flow** (Restore, Continue/Purchase, Tier-cards, Close-X) — måste fixas i T9
- **P1 = elementen som är core-experience** (Scan, Match, Save, Diary) — fix om <30 min vardera, annars defer
- **P2+ = decorative/marginal** (ornament-rules, stamp-numbers) — defer till v1.0.x

P2-issues dokumenteras i ny `docs/superpowers/research/2026-05-16-talkback-walkthrough.md` för senare cleanup.

## 5. Beroenden och parallellisering

| T | Type | Est | Depends on |
|---|---|---|---|
| T0 | Ops | 0.5d | — |
| T1 | Ops/code | 0.5d | — |
| T2 | Code | 1d | — |
| T3 | Code | 0.5d | — |
| T4 | Code | 2–3d | — |
| T5 | Code | 0.5d | T4 |
| T6 | Code | 0.5d | — |
| T7 | Code | 0.5d | — |
| T8 | Code (conditional) | 0–2d | T2 |
| T9 | Ops/verify | 0.5d | T4, T5, T6, T7 |
| T10 | Ops/verify | 0.5d | alla |

**Parallelliseringsmöjligheter:**
- T0, T1, T2, T3 är helt oberoende → kan köras i parallella sessions eller agent-dispatched i Sonnet
- T4 (Billing) är största enskilda blocket — bör vara primärt focus-arbete
- T6, T7 är små UI-tweaks som kan klämmas in mellan T4-pauser
- T8 är conditional — om T2 visar enkel fix blir det 0.5d; om non-actionable blir det 0d (fallback-spår a) eller dokumenterat-och-deferred

**Föreslagen sessionsordning (5–7 dagar):**
- **Dag 1:** T0 (ops) + T1 (ops) + T2 (ML diagnos i bakgrund)
- **Dag 2:** T2 färdig + T3 (test-infra)
- **Dag 3–4:** T4 (Billing huvudblock)
- **Dag 4 sen:** T5 + T6 + T7 (smådelar)
- **Dag 5:** T8 (om T2 hittat fixbar bug) ELLER buffer för T4
- **Dag 6:** T9 (TalkBack) + T10 (device-verify + tag)
- **Dag 7:** buffer för T4/T8-overflow

## 6. Referenser

| Källa | Användning |
|---|---|
| `docs/superpowers/research/2026-05-15-play-store-launch/00-launch-roadmap.md` | Tier 1-scope, locked decisions, launch-datum |
| `docs/superpowers/research/2026-05-15-play-store-launch/04-monetization-strategy.md` | Billing v8 details, Restore Purchases, pricing-rationale, anti-patterns |
| `docs/superpowers/specs/2026-05-12-premium-design.md` | Plan 7e — `PremiumScreen`, `PremiumViewModel`, `EntryFlowDecider`, DataStore-keys |
| `docs/superpowers/specs/2026-05-13-v1-06a-foundation-design.md` | Plan 6a — Settings-rader, Pages-workflow, signing-config, versionCode-scheme |
| `~/.claude/projects/.../memory/project_plan_4b_status.md` | Reusable patterns: `AppGraph`-lambda för DEBUG-only screens, `validateXxxYaml`-task-mönster |
| `~/.claude/projects/.../memory/project_plan_7e_status.md` | Plan 7e-status — `PremiumState`, `premiumModalLastShown`, `PREMIUM_DEBUG_FORCE_ACTIVE` |
| [Migrate to Billing Library 8](https://developer.android.com/google/play/billing/migrate-gpblv8) | v8-API-reference |
| [Subscription lifecycle](https://developer.android.com/google/play/billing/lifecycle/subscriptions) | Grace period + account hold mekanik |
| EU Omnibus Directive (Article 6a) | "Spara X%"-stämpel-förbud (T6) |

## 7. Versionering

- **versionCode:** 110 (bumpad från 100 i `v0.8.0-rc1`; rc-bumps inom 6b-serien går 110/120/130)
- **versionName:** `1.0.0-rc2`
- **Tag:** `v0.9.0a-billing` på `main` efter T10-godkänd device-verify

`v1.0.0` (versionCode 200, versionName `1.0.0`) triggas i 6b3 när Play Store-publicering sker.
