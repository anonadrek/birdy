# Birdy Bird Scanner — Mac bootstrap

> **Vad det här är:** Ett självständigt bootstrap-dokument att ta med till Claude Code på Mac, så att en ny session där har allt sammanhang Windows-sessionen byggt upp. Klistra in hela filen som första meddelande i nya Mac-sessionen, eller spara den i repot och säg "läs `docs/mac-bootstrap.md`".
>
> **Förutsatt mål:** Bygga vidare på *exakt samma* Birdy-app som ligger i `https://github.com/anonadrek/birdy` (branch `main`), nu från Mac-miljön istället för Windows. Inget ska skrivas om — koden, planerna, designen, allt är samma. Det enda som ändras är dev-miljön.

---

## 0. Innan du läser vidare

Det här dokumentet är skrivet 2026-05-26 från en Windows-session. **Repo-state vid skrivning:**

- Senaste tag: `v1.1.0-rc1` (versionCode 116). Det finns inte en `v1.0.3` ännu — versionssekvensen är v1.0.0 → v1.0.1 → v1.0.2 → v1.1.0-rc1. "v1.0.3" som användaren refererar till motsvarar troligtvis den nu aktuella `main`-tippen (`67ddf9e`).
- Branch `main` är pushad till origin. Working tree var ren när dokumentet skrevs.
- AAB för v1.1.0-rc1 är byggd lokalt (`androidApp/build/outputs/bundle/release/`, 479 MB) men inte uppladdad till Play Console.

**Verifiera direkt på Mac efter clone:**
```bash
cd birdy-bird-scanner
git log --oneline -5
git describe --tags
```
Resultatet ska matcha `67ddf9e` + `v1.1.0-rc1` eller senare.

---

## 1. Vad är Birdy?

AI-driven Android-app för fågelidentifiering. Tre kärnflöden:

1. **Skanna med kamera** — realtidsklassificering i live preview (foto, 3 fps) + 3-sekunders inspelning (audio).
2. **Uppslagsverk** — 839 europeiska arter med plate-illustrationer, fakta, säsongs- och migrationsdata.
3. **Fältdagbok** — sparar fynd, bygger life list, delar ut 35 badges, exporterar PDF.

**Brand:** "Birdy" — intim, kunnig följeslagare i fält. Inte "BirdNet+++". Visuellt språk = pappersbaserad **Field Journal** med kursiv DM Serif Display + handskriven Caveat-marginalia, mossgrön accent.

**v1-scope:** Android-only, Norden/Europa, on-device ML. Karta + moln-sync + community + iOS-launch = v2/v3 (post-launch).

**Användare:** Bred två-lager — nybörjare som vill lära sig OCH entusiaster som vill ha hjälp i fält. Inte sliten "expertapp" — varm pedagogisk inramning runt seriös ML.

---

## 2. Mac dev-miljö — installation från grunden

Förutsätter macOS 14+ (Sonoma eller senare) och Homebrew installerat (`brew --version` ska funka).

### 2.1 JDK 21 (Temurin)

```bash
brew install --cask temurin@21
```

Verifiera:
```bash
/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/java -version
# Expected: openjdk version "21.x.x"
```

Sätt `JAVA_HOME` permanent i `~/.zshrc`:
```bash
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.zshrc
source ~/.zshrc
```

På Mac slipper du Windows-prefix:et `export JAVA_HOME="C:/Java/..."` — `java_home -v 21` resolvar automatiskt.

### 2.2 Android Studio + SDK

1. Ladda ned Android Studio Koala+: https://developer.android.com/studio
2. Vid första-uppstart: välj "Standard" setup → låt den installera SDK + emulator.
3. SDK landar default på: `~/Library/Android/sdk`
4. Lägg till i `~/.zshrc`:
   ```bash
   echo 'export ANDROID_HOME=$HOME/Library/Android/sdk' >> ~/.zshrc
   echo 'export PATH=$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH' >> ~/.zshrc
   source ~/.zshrc
   ```
5. Verifiera ADB:
   ```bash
   adb --version
   # Expected: Android Debug Bridge version 1.0.41+
   ```

Acceptera SDK-licenser:
```bash
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
```
(Tryck `y` på alla.)

### 2.3 Xcode + CocoaPods (för KMP iOS-target)

Repo:t har redan iOS-skelett. Även om v1 är Android-only behövs Xcode för att Gradle ska kunna konfigurera KMP-modulerna utan att klaga.

```bash
xcode-select --install     # CLI tools
# Sen från App Store: installera "Xcode" (15+) → kör en gång → acceptera licens
sudo xcodebuild -license accept
brew install cocoapods
```

Verifiera:
```bash
xcodebuild -version
pod --version
```

### 2.4 Git + GitHub CLI

```bash
brew install git gh
gh auth login                # logga in mot anonadrek-kontot
```

### 2.5 Klona repot

```bash
mkdir -p ~/dev
cd ~/dev
git clone git@github.com:anonadrek/birdy.git birdy-bird-scanner
cd birdy-bird-scanner
git checkout main
git pull
git describe --tags    # ska visa v1.1.0-rc1 eller senare
```

### 2.6 Första bygget

```bash
./gradlew :composeApp:testDebugUnitTest :shared:domain:jvmTest :shared:ml:jvmTest
./gradlew :androidApp:assembleDebug
```

Båda ska vara gröna. Första bygget tar 5–10 minuter (Gradle drar ned alla dependencies + tar genom KMP-targets). Efterföljande bygg är snabba.

Förväntade varningar (ofarliga):
- `PremiumActivationListenerTest` har `@OptIn`-saknad-varningar (kvarstående).
- Gradle 8.11 deprecation-varningar för Gradle 9.0-kompatibilitet (kvarstående).

### 2.7 Physical device

På Mac används Android-telefonen exakt som på Windows. Användaren har testat på SM-S918B (Galaxy S23 Ultra, API 35). På Mac:

```bash
# Sätt telefonen i USB-felsökning, anslut, godkänn RSA-prompt på enheten
adb devices              # ska visa serial + "device"
adb -s <serial> install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb -s <serial> shell am start -n se.birdy.android/.MainActivity
```

På debug-bygget heter paketet `se.birdy.android.debug` (inte `se.birdy.android`) — viktig trap från Onboarding v2-verify.

### 2.8 Memory-katalog för Claude Code

På Mac ligger Claude Code:s memory här:
```
~/.claude/projects/<repo-id>/memory/
```

`<repo-id>` är en URL-kodad version av repots absoluta path. För `~/dev/birdy-bird-scanner` blir det troligtvis `-Users-<dittnamn>-dev-birdy-bird-scanner`. Claude Code skapar katalogen automatiskt vid första memory-write.

**Auto-memories från Windows-sessionen följer INTE med automatiskt** — de ligger lokalt i `C:\Users\abbea\.claude\projects\C--Users-abbea-dev-birdy-bird-scanner\memory\` och är inte i git. Det här dokumentet + repo:ts `CLAUDE.md` är vad du tar med dig som "frozen snapshot". Mac-sessionen bygger upp ny memory över tid.

---

## 3. Status (vid skrivning 2026-05-26)

Detaljerad status finns i `CLAUDE.md` ("Status"-sektionen). Sammanfattat:

- **v1.0.0 — taggad, AAB uppladdad till Play Console Internal Testing** under granskning.
- **v1.0.1 — taggad** (versionCode 114). Audit-fixes (Identify-tab + back-pil + tab-tap-pop). Ingen AAB byggd.
- **v1.0.2 — taggad** (versionCode 115). Onboarding v2 (7-scens VerticalPager scroll-story).
- **v1.1.0-rc1 — taggad** (versionCode 116). Phase A retention-hooks (daily-bird-card med blurred hero, shimmer-sweep, notifikations-workers, DB migration 4). AAB byggd lokalt (479 MB), inte uppladdad.
- **Branch `main` clean och pushad** till origin.

**Nästa milstolpe:** Manuell Play Console-upload av v1.1.0-rc1 AAB → Closed Testing 14d → Production launch → flippa `PREMIUM_OPEN_FOR_LAUNCH=false`.

**Production-launch blocker:** `docs/superpowers/runbooks/2026-05-26-billing-verify-and-go-live.md` — fyra-stegs runbook (debug-toggle, Billing v8-verify, BirdNET-licensguard DONE, conversion-monitoring). MÅSTE köras innan flippen. Detaljerad i CLAUDE.md "Pending follow-ups #3".

---

## 4. Plan-of-plans

Hela appen har byggts som sekventiella planer med plan-doc i `docs/superpowers/plans/`, taggad milstolpe, device-screenshots per fas.

| # | Plan | Tag | Plan-doc |
|---|---|---|---|
| 1 | Foundation — KMP-bootstrap, Compose, CI | `v0.1.0-foundation` | `2026-04-30-v1-01-foundation.md` |
| 2a | Content pipeline + walking skeleton (5 arter) | `v0.2.0a-pipeline` | `2026-05-02-v1-02a-content-pipeline.md` |
| 2b | Content backfill (5 → 839 arter) | `v0.2.0-content` | runbook `2026-05-02-plan-2b-content-backfill.md` |
| 3 | Encyclopedia (browse + species profile) | `v0.3.0-encyclopedia` | `2026-05-04-v1-03-encyclopedia.md` |
| 4a | ML & Camera UI (FakeClassifier + 3 fps CameraX) | `v0.4.0a-camera-ui` | `2026-05-05-v1-04a-camera-ui.md` |
| 4b | Real TFLite (AIY Birds V1, 965 klasser, ~14ms) | `v0.4.0b-real-tflite` | `2026-05-07-v1-04b-real-tflite.md` |
| 5a | Diary (browse + detail + save flow) | `v0.5.0a-diary` | `2026-05-05-v1-05a-diary.md` |
| 5b | Gamification (25 badges, streaks, unlock-queue) | `v0.5.0b-gamification` | `2026-05-06-v1-05b-gamification.md` |
| 7a | Redesign Foundation — tokens, DataStore, Onboarding, Settings | `v0.7.0a-foundation` | `2026-05-08-v1-07a-redesign-foundation.md` |
| 7b | Redesign Skärmar — Listen/Archive/Lifelist/Badges | `v0.7.0b-screens` | `2026-05-09-v1-07b-redesign-screens.md` |
| 7c | Field Journal redesign — DM Serif + Caveat + paper-bg + StampSeal | `v0.7.0c-field-journal` | `2026-05-09-v1-07c-field-journal.md` |
| 7d | Match-flow — threshold-logik, Match/Disambig/NoBird-screens | `v0.7.0d-match-flow` | `2026-05-12-v1-07d-match-flow.md` |
| 7e | Premium tier — PremiumScreen + per-tab teasers + cold-start modal | `v0.7.0e-premium` | `2026-05-12-v1-07e-premium-tier.md` |
| 6a | Release foundation — UX-polish + R8/signing/icon/a11y | `v0.8.0-rc1` | `2026-05-13-v1-06a-foundation.md` |
| 6b1 | Billing v8 + launch-prep (PremiumBillingClient + Restore Purchases) | `v0.9.0a-billing` | `2026-05-16-v1-06b1-billing-launch-prep.md` |
| 6b2 | Audio-ID via BirdNET-Lite (3s rec + FlexRFFT TF Select op) — *free-tier* | `v0.9.0b-audio` | `2026-05-20-v1-06b2-audio-id.md` |
| 6b3 | Premium content (PDF-export + season-statistics + 10 fält-märken) | `v0.9.0c-premium-content` + `v1.0.0` | `2026-05-21-v1-06b3-premium-content.md` |
| W | Marketing-website (Astro + Vercel + birdy.community + /legal/) | live | `2026-05-21-website-vercel-legal.md` |
| v1.0.2 | Onboarding v2 — 7-scens scroll-story (VerticalPager) | `v1.0.2` | `2026-05-25-onboarding-v2-scroll-story.md` |
| v1.1 Phase A | Retention-hooks (daily-bird-card + notifications + DB migration) | `v1.1.0-rc1` | `2026-05-25-v1-1-phase-a-retention.md` |
| v1.2 Phase B | Migrating now-strip + söndag-veckorecap | *deferred — brainstorma efter Phase A landat* |

**Konvention:** Varje plan ska lämna projektet i ett byggbart, testbart tillstånd. `./gradlew build` ska gå grönt på `main` mellan planer.

---

## 5. Tekniska val

### App-stack
- **Kotlin Multiplatform + Compose Multiplatform.** Android primär; iOS-skelett finns för v2-launch.
- **DB:** SQLDelight 2.x med Flow-baserade queries. Migrationsschema i `shared/data/src/commonMain/sqldelight/` — nu 4 migrations.
- **Kamera:** CameraX 3 fps `ImageAnalysis` + auto-throttle till 1.5 fps vid p95 > 333 ms inference.
- **Audio:** 48 kHz mono PCM_16 via `MediaRecorder.AudioSource.UNPROCESSED` → `VOICE_RECOGNITION` graceful fallback. 3-sekunders push-to-record → OGG/Opus output.
- **Billing:** Google Play Billing v8. `PremiumBillingClient` är expect/actual så core-koden är platform-agnostic. RSA SHA1-signature-verify via Play Licensing public key embeddad i BuildConfig.
- **Distribution:** AAB via Play Asset Delivery. `:asset-pack` install-time-modul för TFLite-vikter (~150 MB) håller base APK på 136 MB.
- **Språk:** SV + EN, Sverige först. Alla UI-strängar via `compose-resources` (Res.string-API).
- **CI:** GitHub Actions (ktlint 12.1.2, detekt 1.23.7, unit tests, assembleDebug).

### ML-stack
- **Foto:** TensorFlow Lite + AIY Birds V1 (uint8-quantized MobileNetV2, 965 klasser). På Galaxy S23 Ultra ≈ 14 ms/inference. Filtrerat till 839 europeiska arter via `species_list.yaml`.
- **Audio:** BirdNET-Lite v2 + `tensorflow-lite-select-tf-ops:2.16.1` (FlexRFFT TF Select op — utan denna failar node 29 "Failed to prepare"). **CC BY-NC-SA 4.0 — får ALDRIG gate:as bakom Premium.**
- **Match-flow:** confidence-thresholds routar till `Match` / `Disambig` (top-3 nära) / `NoBird`. Implementerat i Plan 7d.
- **Migrations- och säsongsdata:** statisk per art i v1 (YAML i `shared/content/`). Geografi-baserad filtrering kommer post-v1.

### Webb
- Live på `https://birdy.community` (Vercel auto-deploy från `main`).
- Astro 5 + Tailwind v4 + `@astrojs/sitemap` + `marked` + Playwright smoke-tests (7 tester) + i18n parity check (SV/EN).
- **Setup-gotcha:** Vercel **Root Directory MÅSTE vara `website`** — annars ENOENT på `package.json`.

---

## 6. Var saker ligger i repot

| Vad | Var |
|---|---|
| Projektguide (canonical, läses av Claude Code automatiskt) | `CLAUDE.md` |
| Detaljerad projektöversikt (för web-Claude) | `docs/project-overview.md` |
| Den här bootstrapen (för Mac-sessioner) | `docs/mac-bootstrap.md` |
| Designspec för v1 | `docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md` |
| Implementationsplaner | `docs/superpowers/plans/YYYY-MM-DD-v1-NN-<phase>.md` |
| Skärmdumpar per milstolpe | `docs/superpowers/screenshots/` |
| Milstolpe-review-runbook | `docs/superpowers/runbooks/milstolpe-review.md` |
| Billing-verify + go-live runbook (production-launch blocker) | `docs/superpowers/runbooks/2026-05-26-billing-verify-and-go-live.md` |
| Play Store-artefakter (markdown) | `docs/play-store/{privacy-policy,terms,store-listing-{sv,en},data-safety-form}.md` |
| Website källkod | `website/` (Astro 5 + Tailwind v4 + Playwright) |
| Launch-research | `docs/superpowers/research/2026-05-15-play-store-launch/` + `2026-05-20-play-store-audit.md` |
| Android-app-modul | `androidApp/` |
| Compose Multiplatform UI | `composeApp/` |
| Shared domain/ml/data | `shared/domain/`, `shared/ml/`, `shared/data/`, `shared/content/` |
| TFLite-modeller (asset pack) | `asset-pack/src/main/assets/` |

---

## 7. Visuellt språk (Field Journal — canonical app-wide från Plan 7c)

**Färgtokens** (i `composeApp/src/commonMain/.../ui/theme/Color.kt`):

| Token | Hex | Roll |
|---|---|---|
| PaperBg / PaperEdge | `#EFE7D6` / `#E5DCC7` | Pappersbakgrund + texture |
| MarginaliaInk | `#3F4F30` | Caveat-text, sub-lines (WCAG AA-bumpad i Plan 6a) |
| AccentCopper | `#A8552D` | CTA, aktiv tab, stat-siffror, copper-pills |
| StampNavy | `#1F3A5F` | StampSeal-states |
| HeroMossMid / Deep / Shadow | `#5C6E48` / `#3F4F30` / `#2A3520` | Mossgrön gradient (Listen/Premium hero) |

**Typografi:**
- `DM Serif Display Italic` för rubriker. Komponent `JournalHeadline` parsar `*ord*` → renderar segmentet i Caveat-italic med lätt rotation (handskriven accent).
- `Caveat` för marginalia + sub-lines.
- `Inter` (system sans) för body.
- Fonts bundlade via `compose-resources` (`rememberDmSerifDisplay()` / `rememberCaveat()` i `Type.kt`).

**Layout-element:** `Modifier.paperBackground()` (dot-texture som default-bas), `JournalIntro` (eyebrow + JournalHeadline + ornament + sub-line), `StampSeal` (locked/in-progress/unlocked-states), `PlateFrame` (naturalist-foto-frame), `OrnamentRule` (❦ + horisontellt streck).

---

## 8. Viktiga beslut & ramar — bryt INTE utan diskussion

1. **Scope v1.0 är låst.** Skanna (foto + audio) + uppslagsverk + dagbok + gamification + premium tier. Karta + cloud-sync = v1.5 / v2. Inte expanderas mitt i closed testing.
2. **Geografi:** Norden/Europa, 839 arter. ML-modellerna är globala men content filtrerar.
3. **Privacy-löfte:** "Almost nothing collected, data stays on phone." Verifierat i 2026-05-20 fältrevision (6 agents). All data ligger lokalt i SQLDelight-DB; ingen cloud-backend för inference. **Lägg ALDRIG in in-app analytics-events utan diskussion.**
4. **BirdNET-Lite-modellen är CC BY-NC-SA 4.0 (NonCommercial).** Audio-ID är gratis-feature i v1.0. **Om något BirdNET-relaterat hamnar bakom Premium → licensbrott.** Premium = endast Plan 6b3-features (PDF/stats/badges) som vi byggt själva. Unit test `BirdNetLicenseGuardTest` failar bygget om någon försöker återinföra gaten — kör vid varje build.
5. **`PREMIUM_OPEN_FOR_LAUNCH=true`** (i `androidApp/build.gradle.kts`) under closed testing → alla användare får LIFETIME-premium via override i MainActivity. Flippas till `false` ENDAST efter att Billing-verify-runbooken är körd (`docs/superpowers/runbooks/2026-05-26-billing-verify-and-go-live.md`).
6. **Solo-utvecklare:** En person bygger via Claude Code. Granskning sker av användaren mellan tasks. **Tvåstegs-review (spec → kvalitet) körs alltid mellan tasks.**

---

## 9. Arbetsflöde med Claude Code

### Kommunikationsspråk
Användaren skriver och får svar på **svenska**. Kod, commit-meddelanden, plan-docs och tekniska identifierare på engelska enligt etablerad konvention.

### Autonomi-direktiv
**"Don't ask me for permission to run anything."** Commits, push, gradle-kommandon, file-edits enligt plan körs utan bekräftelse. Vid scope-creep i review: fixa autonomt (soft-reset + re-commit). 

Undantag — rapportera status och vänta:
- Blockerare som kräver fysisk åtkomst (telefon, emulator)
- Tredjepartsbeslut (Play Console-UI, Vercel-UI, Linear etc.)
- Otydlig task eller spec-motsägelse → **stoppa och fråga**, inte gissa.

### När superpowers (skills) används
- **Brainstorming, ny plan, plan-execution med review** → `superpowers:brainstorming` → `:writing-plans` → `:subagent-driven-development`.
- **Code-review** → `superpowers:requesting-code-review` (mellan tasks i en plan).
- **Vanliga frågor, snabba bugfixar, mindre refactoring** → bara prata, ingen skill.

Tumregeln: större än ett samtal eller kräver disciplin (TDD, plan-tracking) → skill. Annars inte.

### Modell-strategi
| Uppgift | Modell |
|---|---|
| Brainstorming, design, arkitektur, code review | Opus 4.7 (eller senaste tillgängliga) |
| Implementer-subagents i subagent-driven-development | Sonnet 4.6 |
| Snabba lookups | Haiku 4.5 |

### Två-stegs-review mellan tasks
1. **Spec-konformitet:** Gör koden det plan-task:en kräver? Inga skipade krav?
2. **Kvalitet/säkerhet:** Hanteras edge cases? Inga regressions? Tester passar? Inga sårbarheter?

Båda körs alltid innan task markeras klar och commit pushas.

---

## 10. Vanliga kommandon (Mac)

```bash
# Android: bygga + installera + starta på ansluten enhet
./gradlew :androidApp:installDebug
adb shell am start -n se.birdy.android/.MainActivity

# OBS: debug-bygget heter se.birdy.android.debug på enheten — inte se.birdy.android
adb shell am start -n se.birdy.android.debug/se.birdy.android.MainActivity

# Snabba unit-tests (delade moduler på JVM)
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest

# Specifikt test (t.ex. license guard)
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.listen.BirdNetLicenseGuardTest"

# Lint + statisk analys
./gradlew ktlintCheck detekt
./gradlew ktlintFormat   # autofix

# Signed release-AAB
./gradlew :androidApp:bundleRelease

# Lokalt test av release-AAB med asset-pack-splits (enda korrekta sättet)
# (kräver bundletool — brew install bundletool på Mac)
bundletool build-apks --bundle=androidApp/build/outputs/bundle/release/androidApp-release.aab \
  --output=birdy.apks --connected-device --ks=<keystore> --ks-pass=pass:<password> \
  --ks-key-alias=<alias> --key-pass=pass:<password>
bundletool install-apks --apks=birdy.apks

# Website (kör från website/)
cd website && npm install
cd website && npm run dev      # localhost:4321
cd website && npm run build
cd website && npm run test:smoke    # Playwright (7 tester)
cd website && npm run test:i18n     # SV/EN parity check
```

---

## 11. Trap-katalog — vanliga repeterande buggar

Saker som har bitit oss mer än en gång. Kolla först här om något konstigt händer.

- **`:androidApp` saknar transitiva deps från `:composeApp`.** composeApp använder `implementation()` inte `api()`, så varje ny shared/library-referens måste få egen `implementation()` i `androidApp/build.gradle.kts`.
- **compose-resources unescape:ar inte Android `\'`** — använd raw `'` eller Unicode `'` (U+2019) direkt i strings.xml.
- **compose-resources processar inte `%%` som `%`-escape** — använd `%1$s` och passa pre-formatterad `"${value}%"` från Kotlin call-site.
- **`ImageProxy.imageInfo.timestamp` returnerar nanos sedan boot, inte Unix-epoch** — använd `System.currentTimeMillis()` i CameraX-analyzer.
- **Hardcoded localized strings bryter andra locale** — alltid `stringResource(Res.string.xxx)`, aldrig `"spara 60%"` direkt i Kotlin.
- **ADB-tap y < 300 kan trigga notification-drawer** istället för UI-element — använd `KEYCODE_BACK` för recovery + `adb shell uiautomator dump` för exact bounds.
- **Quality-review måste köra `:androidApp:installDebug` + device-test** — `:composeApp:assembleDebug` ensam missar manifest/dep-trap för Android-screens.
- **FlexRFFT-crash i TFLite-audio** — kräver `tensorflow-lite-select-tf-ops:2.16.1` dep, annars failar node 29 "Failed to prepare". Diagnostisk logging > catch-all `Throwable`-swallow.
- **Vercel `npm install` ENOENT** — Root Directory måste vara `website` (inte `/website`, inte tomt).
- **BirdNET-Lite-modellen är CC BY-NC-SA (NonCommercial)** — får ALDRIG gate:as bakom Premium. Unit test `BirdNetLicenseGuardTest` fångar regression.
- **Compose tap-miss debug protocol:** när ett element renderas men tap missar, kör `adb shell uiautomator dump` FÖRST. Missing node ⇒ z-order. Node present ⇒ inset/overlay. Inte pixel-scanna.
- **`installDebug` lägger ut till `se.birdy.android.debug`** (inte `se.birdy.android`) — applicationIdSuffix `.debug` är aktiverat på debug-buildtypen. Använd rätt package vid `am start`.
- **`installRelease` missar `:asset-pack`-modulen** — använd bundletool's `build-apks --connected-device` + `install-apks` för att testa release-AAB lokalt med asset-pack-splits korrekt.
- **Verify audit-rapporter mot kod innan fix.** 4 av 21 audit-fynd 2026-05-23 var fel-premiss. Alltid grep/Read före edit.
- **StampSeal-Column shiftar cirkel 9dp uppåt från Box-center** (Onboarding v2-lärdom) — om du behöver "stämpel inuti ram" med crosshair-justering, lägg crosshair + stämpel i samma image-slot och sätt `name = null` på StampSeal så bara cirkeln renderas.

---

## 12. iOS-möjligheten (Mac-only-unlock — men inte v1-scope)

Eftersom du nu är på Mac kan du teoretiskt jobba med iOS-targeten i KMP-projektet. Men:

- **v1-scope är låst till Android.** Inte börja iOS-utveckling utan att brainstorma + skriva plan först.
- **iOS-launch är v2.** Detaljerad scope i `CLAUDE.md` "Roadmap post-v1.0".
- **Compose Multiplatform-iOS-target** finns redan i build-konfigen (men inte aktiverat i releases). SwiftUI-shim behöver byggas för plattforms-API:er: kamera, audio, billing (→ StoreKit istället för Play Billing), share-sheet, file-export.

Om iOS-arbete blir aktuellt: starta med `superpowers:brainstorming` → spec → plan, precis som alla andra större initiativ.

---

## 13. Pending follow-ups (post-launch)

Från `CLAUDE.md`-sektionen "Pending follow-ups":

1. **GitHub Pages teardown.** In-app + store-listing URL:er är redan migrerade till `birdy.community/legal/`. I repo Settings → Pages, sätt Source till "None". Bekräfta också Play Console-UI:ns Privacy/Terms-fält när nästa AAB laddas upp.
2. **Email migration.** Sätt upp `feedback@birdy.community` (Cloudflare Email Routing eller Resend Inbound) under closed testing. Bridge nu = `albin@abrahamssons.se`. När birdy.community-mailen är live: byt tillbaka i `website/src/content/copy.{en,sv}.json` (FAQ + footer) + alla markdown-filer i `docs/play-store/`.
3. **Billing v8 IPC runtime-verify + go-live** — **MÅSTE köras innan `PREMIUM_OPEN_FOR_LAUNCH=false` och innan officiell production-release.** Full plan i `docs/superpowers/runbooks/2026-05-26-billing-verify-and-go-live.md`.
4. **Audio accuracy eval.** Kräver xeno-canto API v3 key. Pipeline klar i `tools/ml-eval/audio_accuracy_report_2026-05-21.md`.
5. **AB-flytt.** Account Transfer av Play Console till AB-bolaget när det är registrerat (post-launch).
6. **SV legal-översättningar.** Om Sverige-trafik växer, mirror `/sv/legal/...` med översatta markdown-filer.
7. **Plan 6a T8/T9 device-screenshots saknas:** `08-match-with-inline-note` + `09-disambig-save-as-unknown` (kräver deterministisk match-flow ej driveable via ADB — kan adresseras via test-image-infra i framtida sprint).

---

## 14. Roadmap post-v1.0

### Geografisk expansion (lågsiktig huvudtrack)

ML-modellerna är redan globalt tränade (AIY V1 ≈ 965 klasser, BirdNET-Lite ≈ 6000) — vi har bara filtrerat till EU. Expansionsjobbet sitter i **content-pipeline** (en YAML + plate-foto per art), **regional migrations-/säsongsdata**, **on-demand asset packs** (APK växer snabbt — bortom v1.0:s 136 MB-base) och **fler språk**.

- **v1.0 — Norden/Europa (839 arter)** ← current
- **v2 — "Asien + hela Europa" + iOS-launch.** Östasien/Indien först + Compose Multiplatform-iOS-target. *iOS aktiveras enklast nu när dev-miljön är Mac.*
- **v3 — "Hela världen."** Alla återstående kontinenter; full content-skalning + språkstöd.

### Parallella feature-spår (inte version-bundna)

- **"Karta & moln":** Konton, molnsynk av dagboken, karta med fynd från publika datakällor, push-notiser om sällsynta arter nära användaren. `Observation`-schemat har redan nullable `latitude` / `longitude` / `location_label` från Plan 5a — ingen migration behövs.
- **"Community":** Delning av fynd, kommentarer, flöde, moderering.
- **Övrigt:** Quiz/utbildningsläge, fullt offline-läge för längre exkursioner.

---

## 15. Snabbreferens — vad Claude (Mac-sessionen) ska tänka på

- Svara på **svenska**.
- **Inte bryt privacy-löftet** utan diskussion. Ingen in-app telemetri.
- **Inte gate:a BirdNET-relaterade audio-features bakom Premium** (licensbrott). `BirdNetLicenseGuardTest` fångar regression i CI.
- **Inte expandera v1-scope** mitt i closed testing.
- **Inte flippa `PREMIUM_OPEN_FOR_LAUNCH=false`** utan att först ha kört Billing-verify-runbooken till grön status.
- Stora ändringar → använd `superpowers:brainstorming` → `:writing-plans` → `:subagent-driven-development`. Småfix → bara prata och kör.
- Code-review är tvåstegs: spec-konformitet först, kvalitet/säkerhet sen.
- Vid otydlig task: **stoppa och fråga**, inte gissa.
- Annars: autonom. Push, tag, gradle, file-edits enligt plan utan att fråga om lov.
- Memory bygger upp över tid lokalt — auto-memories från Windows följer INTE med. CLAUDE.md + den här filen + alla `docs/`-filer i repot är den frusna snapshoten du startar från.

---

## 16. Första-sessions-prompt (mall)

När du öppnar Claude Code på Mac i `~/dev/birdy-bird-scanner`, kör något i stil med:

```
Vi fortsätter med birdy-bird-scanner — jag har migrerat dev-miljön från Windows till Mac.

Läs först:
1. docs/mac-bootstrap.md  (det här dokumentet — komplett kontext)
2. CLAUDE.md  (canonical projektguide)
3. Senaste 5 commits + senaste tag

Bekräfta att du har laddat allt och summera status i 5–10 punkter.
Sen väntar vi på nästa konkreta task.
```

Claude Code laddar `CLAUDE.md` automatiskt i varje session. Men `docs/mac-bootstrap.md` är ditt portable kontext-paket — det är skrivet just för att bridg:a kunskap mellan maskiner.

---

## 17. Vad som INTE följer med från Windows

För transparens — saker som bara existerade lokalt på Windows-maskinen och måste återskapas eller godkännas att vara borta:

1. **Auto-memory-filer** i `~/.claude/projects/C--Users-abbea-dev-birdy-bird-scanner/memory/` (35+ filer per `MEMORY.md`-index). Innehåller status-snapshots per plan, autonomy-direktiv, debug-protokoll, beslutshistorik. **Allt viktigt har lyfts upp till CLAUDE.md eller den här filen.** Eventuellt något specifikt minne skulle kunna manuellt kopieras över om det behövs (de är vanliga `.md`-filer).
2. **Lokala Play Console-credentials / signing keystore.** Keystore för release-signing ligger inte i repo (security). Du behöver antingen migrera `*.jks`-filen från Windows-maskinen ELLER skapa en ny upload-key och rotera via Play Console (gh-secret om CI-signering). Sökväg på Windows: kollas i `androidApp/build.gradle.kts` `signingConfigs.release`. Var försiktig — release-key får ALDRIG committas.
3. **Lokal gradle-cache** (`~/.gradle/caches/`). Ofarligt — bygger upp på nytt vid första `./gradlew build`.
4. **Vercel CLI auth.** Om du vill deploya manuellt från Mac: `npm i -g vercel && vercel login`. (Default-flow är auto-deploy från `main`-push, så manuell deploy behövs sällan.)
5. **gh CLI auth.** Kör `gh auth login` på nya maskinen.
6. **Android Studio-konfiguration** (IDE-preferenser, run-configs, scratch-files). Återskapas efter eget tycke.

Allt annat — kod, plan-docs, beslut, designtokens, screenshots, runbooks, website — ligger i git och följer med automatiskt vid clone.

---

**Lycka till på Mac:en. Säg "Vi fortsätter med birdy-bird-scanner" och så kör vi.**
