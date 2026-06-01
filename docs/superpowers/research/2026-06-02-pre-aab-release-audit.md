# Pre-AAB release-audit — v1.1 closed testing (vC121)

> **Read-only granskningsrapport.** Sammanställd 2026-06-02 inför morgondagens Play Store-AAB.
> 6 parallella granskningsagenter (Opus) + lokal bygg/test/lint-verifiering. Inga projektfiler ändrades.
> Källa: workflow `pre-aab-release-audit` (run `wf_f6c633b7-f38`).

---

## TL;DR — kan vi bygga AAB:n imorgon?

**Ja — men bygg från `main`, inte från den här branchen, och gör listnings-/doc-stegen vid upload.**

Själva binären är i bra skick: versionsnummer, R8/minify, signering, asset-pack, targetSdk 35, desugaring, och den bundlade `species.db` är alla **verifierat korrekta**. Lokala enhetstester + ktlint + detekt går **grönt**. Inget i koden hindrar ett release-bygge.

Det som kräver din uppmärksamhet är **(a) vilken branch du bygger från**, **(b) att release-/minify-vägen aldrig körts i CI** (kör `bundleRelease` + device-smoke lokalt först), och **(c) en rad manuella Play Console-steg** (What's new, märkesantal, data-safety-formulering) som annars publicerar fel info om appen.

| Dimension | Status |
|---|---|
| Bygg / AAB-paketering | 🟡 bygg från `main` |
| Vad som faktiskt shippar (branch-state) | 🟡 badges-v2 är inert, bygg ändå från `main` |
| Test / CI / statisk analys | 🟢 (CI-täckning tunn → kör lokalt) |
| Store-listing & release-notes | 🟡 inaktuell vid v1.0.0 — manuella steg |
| Content / DB-schema | 🟢 verifierat byte-för-byte |
| Legal / privacy / billing | 🟡 2 doc-/string-luckor |

---

## Checklista för imorgon bitti

### A. Innan du bygger (i repo-root `C:\Users\abbea\dev\birdy-bird-scanner`)

1. **`git switch main`** och bekräfta `versionCode = 121` (`git show main:androidApp/build.gradle.kts | grep versionCode`). Lämna `feat/badges-v2-trophy-room` orörd — den fortsätter sen.
2. **Bekräfta att versionCode 121 inte redan är konsumerad** på closed-testing-spåret. Är den det → bumpa versionCode (Play avvisar återupload på samma kod). Är den oanvänd → kör som den är.
3. *(Rekommenderat, kräver en liten string-edit)* Lägg **BirdNET-Lite / Cornell-attribution** i About-strängarna (se H3) innan bygget — annars saknar appen den CC BY-krävda attributionen och det blir ett post-launch-steg.
4. **Kör full lokal testsvit** (CI kör bara 3 av 7 moduler — se H4):
   ```bash
   ./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :shared:content:jvmTest \
             :composeApp:testDebugUnitTest :shared:data:jvmTest \
             :shared:datastore:jvmTest :shared:pdf:testDebugUnitTest \
             :androidApp:lintRelease
   ```

### B. Bygg + verifiera binären

5. **`./gradlew :androidApp:bundleRelease`** (första gången release-/R8-vägen körs sedan vC116 — se M1).
6. **Verifiera signeringen:** `keytool -printcert -jarfile <aab>` → ska vara upload-nyckelns alias (se L1).
7. **Device-smoke via bundletool** (enda sättet att testa asset-pack-splits lokalt — `reference_bundletool_local_release_test`): `build-apks --connected-device` + `install-apks`. Kör igenom: foto-ID (AIY), **audio-ID (BirdNET + FlexRFFT under R8)**, artbläddring (species.db + asset-pack-bilder), PDF-export, DP D/E-märken + grupp-chips.

### C. Vid upload i Play Console (manuellt — `CLAUDE.md` follow-up #8)

8. **"What's new" / Nyheter för vC121** i SV+EN (finns inte draftat någonstans — se H1).
9. **Fixa märkesantal i store-listing:** 33 totalt = **26 gratis + 7 premium** (idag står 35 = 25+10 → fel, se H2). Lägg samtidigt in DP A / DP C-E / Phase B i copyn (se M2).
10. **Omdatera data-safety + privacy:** sluta påstå att INTERNET inte är deklarerad / att appen är "fully offline" — det är falskt mot den **mergade** manifesten (se H5). Lägg POST_NOTIFICATIONS i permission-listan (L4).
11. **Uppdatera tester-instruktionerna** (idag vC112/v1.0.0, "3 onboarding pages", "audio not in this build" — allt fel, se M3).
12. **Bekräfta Privacy/Terms-URL-fälten** i Console pekar på `birdy.community/legal` (follow-up #1).

### D. Post-launch (icke-blockerande)

BirdNET-attribution (om ej gjort i steg 3) · 11 saknade SV-namn · website-footer `v1.1.0` · CI: lägg `:composeApp`-tester + `lintRelease` + `bundleRelease` · ta bort stale `.worktrees/listen-open` · navigation alpha → stable · **uppdatera `CLAUDE.md`: Phase B-lint-blockern är inaktuell (redan fixad)**.

---

## Blockerare / beslut (måste avgöras före bygget)

### ⛔ B1 — Bygg från `main`, inte från `feat/badges-v2-trophy-room`

**Problem:** Branchen ligger nu **2 commits före `main`** (`c958ab6 expose trophyShowcase on BadgesUiState.Loaded` + `d8d379a` pure deriver) — pågående Badges-v2-"Troférummet"-arbete (Task 1 av 10-task-planen). `main` bär redan den **klara v1.1-batchen** (DP A–E + Phase B + zoom/crop + onboarding v2) på versionCode 121 / 1.1.0-rc6.

**Nyans (viktig):** Det extra arbetet är **inert** — agenterna verifierade att det inte finns någon `AppRoute.TrophyRoom`, ingen NavHost-registrering, inget entrékort på Märken-fliken och ingen UI-konsument av fältet. En testare kan alltså varken nå eller krascha något nytt. Att bygga från branchen producerar en *funktionellt identisk* binär. **Men** `main` är det rätta, lägre-överrasknings-valet, och så fort fler Troférum-tasks landar (Task 4/9 wire:ar en riktig route + UI) blir en halvfärdig branch testar-nåbar.

**Lösning:** `git switch main` → bygg där. Lämna badges-v2 på sin branch för en senare rc.

*Evidens: `git log main..HEAD` = 2 inert-commits; `AppRoute.kt` saknar TrophyRoom; `git grep buildTrophyShowcase` utanför sin egen fil = inga callers; `androidApp/build.gradle.kts:57-58` vC121/rc6.*

### ⚠️ B2 — Är versionCode 121 redan konsumerad på spåret?

**Problem:** Branchen bumpade aldrig versionen (Troférum-Task 10 ej körd), så den läser `121` precis som `main`. Om en tidigare closed-testing-upload redan använt 121 avvisar Play en återupload på samma kod.

**Lösning:** Kolla closed-testing-spåret i Console. Konsumerad → bumpa versionCode (+ rc-tag) oavsett branch. Oanvänd → ship as-is.

*Evidens: senast **uppladdade** AAB var vC113 (v1.0.0 Internal Testing); 114–120 byggdes aldrig upp, men bekräfta 121-statusen i Console.*

---

## Högt (blockerar inte binären — men gör vid/innan upload)

### H1 — "What's new" är kvar på v1.0.0; inga vC121-release-notes finns
Båda `store-listing-{en,sv}.md` har ett "What's new (v1.0.0)"-block; ingen whats-new/release-notes-fil för vC121 finns i repot. Console-fältet har inget att klistra in.
**Lösning:** Drafta vC121-noteringar SV+EN: DP A (sök hittar apostrof/diakrit-arter), DP C/E (15 ekologiska uppslagsgrupper), DP D (omgjorda märken: rödlistat-spår, livslista 500, audio/säsong gratis), Phase B (Veckans uppslag-notis), onboarding v2, kamera-zoom + upload-crop. *(`store-listing-en.md:55-61`, `store-listing-sv.md:53-59`)*

### H2 — Märkesantal i listningen är fel efter DP D
Listningen säger "25 + 10 = 35 stamps". Koden har **26 gratis + 7 premium = 33** (`badges.yaml`=26 ids, `premium_badges.yaml`=7 ids).
**Lösning:** Rätta STAMP/STÄMPLA→26, PREMIUM→7, fixa What's-new-raden, nämn DP D-omramningen. *(`store-listing-en.md:33,37,58`; `store-listing-sv.md:32,36,56`)*

### H3 — BirdNET-Lite-attribution saknas i appens About-skärm
Audio-modellen är BirdNET-Lite v2 (CC BY-NC-SA 4.0). **NC** uppfylls (audio är gratis), men **BY** (attribution) gör det inte i UI: About krediterar bara "AIY Birds V1 (Google)". Privacy-policyn nämner BirdNET, men in-app-ytan gör det inte. `grep BirdNET|Cornell` i `composeApp/src` → bara en kodkommentar + guard-testet, noll user-facing-strängar.
**Lösning:** Lägg en BirdNET-rad i `about_credits_body` + `about_licenses_body` i både `values/strings.xml` och `values-en/strings.xml`, t.ex. *"BirdNET-Lite v2 (Cornell Lab of Ornithology) — modellvikter CC BY-NC-SA 4.0"*. Bara strängar, låg risk — **görs bäst före bygget** (steg 3). *(`strings.xml:585,587`; `values-en/strings.xml:571,573`)*

### H4 — CI kör bara 3 av 7 testmoduler; merparten av v1.1-testerna körs aldrig i CI
CI kör `:shared:domain/ml/content:jvmTest`. Den kör **inte** `:composeApp`-testerna eller `:shared:data/datastore/pdf`. Alltså har TrophyShowcaseTest, WeeklyRecapBuilderTest, Archive-grupp-tester (DP E), DP D-märkestester, OnboardingViewModelTest, CropGeometryTest, **BirdNET-license-guarden** och **StampNumberMigrationTest** (DB-migration, release-kritisk) bara gröntestats på din lokala maskin.
**Lösning:** Kör full lokal svit före bygget (checklista steg 4). Post-launch: lägg `:composeApp:testDebugUnitTest` m.fl. i `ci.yml`. *(`.github/workflows/ci.yml:39-46`)*

### H5 — Data-safety + privacy påstår "ingen nätverksbehörighet / fully offline" — falskt mot mergad manifest
Appens egen manifest deklarerar bara CAMERA/RECORD_AUDIO/POST_NOTIFICATIONS, men den **mergade release-manifesten** drar in `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `BILLING` (transitivt via Coil 3 / WorkManager / Play Billing). Play läser den mergade manifesten, så påståendet att INTERNET "inte är deklarerad" är bevisbart fel. *(Runtime-löftet håller ändå: inga analytics-SDK:er, alla `AsyncImage` läser lokala filer/resurser, noll http(s)-URL:er.)*
**Lösning:** (a) I `data-safety-form.md`: sluta påstå att INTERNET ej är deklarerad — skriv att den finns transitivt men att appen initierar ingen användardata-egress (Data Safety "No collection/sharing" är fortf. korrekt). (b) Mjuka upp `privacy-policy.md:7` från "fully offline / does not make network calls" → "transmits none of your data off the device". Doc-only, inget kodbyte. *(`merged_manifest/release/.../AndroidManifest.xml`; `data-safety-form.md:84-86`; `privacy-policy.md:7`)*

---

## Medel

### M1 — Release-/minify-vägen körs aldrig i CI — första valideringen är imorgons lokala bygge
CI kör bara `assembleDebug`. R8-strippning, proguard-keeps, resource-shrink och signering valideras aldrig före merge. All DP A–E / Phase B / märkeskod sedan vC116 (26 maj) har aldrig release-byggts. En R8-överstrippning (t.ex. ny reflektionsklass utanför keeps) syns först vid `bundleRelease`.
**Lösning:** Kör `bundleRelease` + device-smoke imorgon (checklista B). Proguard-keeps täcker idag `org.tensorflow.lite.**` (inkl. FlexRFFT), kotlinx.serialization, SQLDelight, Coil, CameraX, Billing. *(`.github/workflows/ci.yml:52`; `proguard-rules.pro`)*

### M2 — DP A, DP C/E och Phase B saknas helt i listnings-copyn
LEARN-blocket nämner bara "filter by family" — inte de ekologiska grupp-chipsen, inte apostrof/diakrit-söket, inte Veckans uppslag.
**Lösning:** Utöka LEARN/LÄR med ekologisk gruppbläddring (alkor, hackspettar, duvor, tranor & rallar …) + förbättrad sök; lägg en kort Phase B-rad. Spegla SV/EN. *(`store-listing-en.md:22-25`; `store-listing-sv.md:21-24`)*

### M3 — Tester-instruktionerna är kraftigt inaktuella (v1.0.0 / vC112)
Säger Build v1.0.0/vC112, "walk through the 3 onboarding pages" (nu 7-scens scroll-story), "Audio bird ID — not in this build" (audio shippade i v0.9.0b), kallar premium-märken/PDF/säsongsstatistik "placeholders". Klistras det in i Console vägleds testarna fel.
**Lösning:** Skriv om för vC121: rätt version, 7-scens onboarding, audio gratis & på plats, premium fullt shippat, + nya v1.1-saker att testa (diakrit-sök, grupp-chips, omgjord märkesskärm, veckonotis, zoom + crop). *(`closed-testing-tester-instructions.md:3,16,63,65-68`)*

### M4 — BirdNET-license-guard-testet ligger utanför CI
Tripwire-testet (`BirdNetLicenseGuardTest`) som förhindrar att audio någonsin premium-gate:as körs bara via `:composeApp:testDebugUnitTest` — vilket CI inte kör. v1.1 bryter inte mot regeln, men en framtida regression skulle passera CI oupptäckt.
**Lösning:** Kör `:composeApp:testDebugUnitTest` lokalt före bygget (täcks av steg 4); veckla in det i CI post-launch. *(`composeApp/src/androidUnitTest/.../BirdNetLicenseGuardTest.kt`)*

---

## Lågt / info (mestadels verifieringar — ingen åtgärd krävs)

| # | Punkt | Notis |
|---|---|---|
| L1 | Signering är maskin-lokal | Korrekt på **denna** maskin (keystore `C:/Users/abbea/keys/birdy-upload.jks` + alla `BIRDY_*`-props i `~/.gradle/gradle.properties`). Osignerad AAB bara om man bygger på annan maskin. Verifiera alias efter bygget. |
| L2 | navigation-compose `2.8.0-alpha13` | Alpha-dep. Har device-verifierats genom v1.0–v1.1. **Bumpa inte kvällen före** — post-launch-hygien. |
| L3 | 11 arter saknar SV-namn | 839 EN / 828 SV. SV-locale faller graciöst tillbaka på EN-namn (ingen krasch). Backfilla post-launch. |
| L4 | POST_NOTIFICATIONS ej i data-safety-listan | Doc-lucka. Inte en data-collection-behörighet → ändrar inte "No collection". Lägg rad vid upload. |
| L5 | `data-safety-form.md` stämplad vC112 | Omdatera mot vC121 vid Data Safety-submit (svaren "No collection/sharing" är fortf. korrekta). |
| L6 | Website-footer säger `v1.0.0` | `copy.{en,sv}.json:98`. Bumpa till v1.1.0 när batchen går publik. Ej i AAB:n. |
| L7 | `.worktrees/listen-open` stale duplikat | Gitignorerad → kan inte förorena CI eller AAB:n. Bygg från repo-root, inte inifrån `.worktrees/`. Ta bort post-launch. |
| L8 | CI saknar Android Lint-steg | `lintRelease` körs bara lokalt/i Play. Kör `:androidApp:lintRelease` en gång före bygget (steg 4). |

---

## 🟢 Verifierat solitt (ingen åtgärd)

- **Versionsnummer:** vC121 > senast uppladdade vC113 → monotont OK. `1.1.0-rc6` är giltigt Play-versionName.
- **R8/minify/shrink:** `isDebuggable=false`, `isMinifyEnabled=true`, `isShrinkResources=true`. Debug-config (`.debug`-suffix, asset-inlining, `PREMIUM_DEBUG_FORCE_ACTIVE`) är `BuildConfig.DEBUG`-grindad → läcker inte till release. Debug-routes (Benchmark/Diagnostics) likaså.
- **Asset-pack:** install-time-modulen korrekt wire:ad (2062 WebP-bilder); base-APK 136 MB < Plays 150 MB-tak. *(TFLite-vikterna ligger i base-modulen, inte i pack:en — en doc-formulering att rätta post-launch.)*
- **targetSdk 35** uppfyller Plays krav; **minSdk 24 core-library-desugaring** aktivt i **båda** modulerna (Phase B:s `DayOfWeek`/`java.time`).
- **`species.db` är regenererad till SCHEMA_REV 3 och INTE stale** — verifierat byte-för-byte: omräknat `SpeciesDbBuilder`-fingerprint från 839 YAML:er = `0x2959529b` = exakt den `application_id` som ligger i den committade binären (den värdet appen jämför vid varje start). 839 arter, 839 taxonomy-rader, **15 grupper**, 0 null `group_id`, 0 tom `search_text`, `integrity_check = ok`, alla 2062 bild-refs upplöser, 0 saknade. Regenererad i commit `7a4ddfa`.
- **Migrationer:** användardata-DB:n (`birdy-observations.db`) har sekventiella, idempotenta migrationer 1–4 (Phase A:s migration 4 = `daily_bird_history`); content-DB:n ersätts helfil vid fingerprint-skifte (rätt modell för read-only-katalog). Migrationskedjan testas av `StampNumberMigrationTest`.
- **Audio är inte premium-gate:at** (`ListenLauncherViewModel.onAudioCardTap` → direkt till audio-scan, ingen premium-check; `PremiumScreen` listar bara PDF/stats/märken). Skyddat av build-failande `BirdNetLicenseGuardTest`. **CC BY-NC-SA NonCommercial uppfyllt.**
- **Privacy-löftet håller i runtime:** inga analytics-SDK:er (ingen Firebase/OkHttp/Retrofit), alla bildladdningar lokala, klassificerare on-device.
- **`PREMIUM_OPEN_FOR_LAUNCH=true`** bypassar korrekt den overifierade Billing v8-IPC:n (`effectivePremiumActive = (premiumOverride ?: backend)`; override:n kortsluter backend). Rätt config för closed testing; dokumenterad flip-väg.
- **Den dokumenterade "Phase B-lint-blockern" är INAKTUELL** — det var `DayOfWeek.SUNDAY`/`java.time` NewApi-lint, redan fixad via core-library-desugaring (`80858e2` + `f7fe5ee`, båda anfäder till HEAD). `bundleRelease` kör dessutom inte lint i sin task-graf. → Uppdatera `CLAUDE.md`/memory.
- **Min lokala körning:** `:shared:domain:jvmTest`, `:shared:ml:jvmTest`, `:composeApp:testDebugUnitTest`, `ktlintCheck`, `detekt` → **BUILD SUCCESSFUL** (inga baselines/suppressions döljer något; detekt `maxIssues=0`, ktlint `ignoreFailures=false`).

---

## Bilaga — granskningens upplägg

6 read-only Opus-agenter, en per dimension: (A) bygg/AAB-paketering · (B) branch-/release-candidate-state · (C) test/CI/statisk analys · (D) store-listing & release-notes · (E) content/DB-integritet · (F) legal/privacy/licensing/billing. Parallellt körde controllern lokala enhetstester + ktlint + detekt (grönt). Allt grundat i `file:line`-evidens; inga projektfiler ändrades.
