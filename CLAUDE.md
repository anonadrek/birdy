# Birdy Bird Scanner — arbetsguide för Claude Code

> **Den här filen läses automatiskt av Claude Code i varje session.** Den ger sammanhang för projektet, var saker ligger, och hur vi arbetar.

## Vad är detta?

AI-driven app för fågelidentifiering. Realtidsskanning via kamera + foto-upload + audio-ID + uppslagsverk över 839 europeiska arter. Kotlin Multiplatform + Compose Multiplatform. **v1.2 (Android) är live på Google Play sedan 2026-06-17** (inkl. privat fynd-karta + premium-tier). **v2 = iOS från samma kodbas — pågår** (plan i0–i6). Senare faser: moln-synk, community, geografisk expansion.

## Status (2026-10-10 kväll)

**⏭️ NÄSTA SESSION BÖRJAR HÄR:** läs `docs/superpowers/plans/OVERLAMNING-2026-10-10.md` (lör 10 okt cirka 14.00, med en kvällsuppdatering överst). Allt ligger på `main`: inga andra grenar, inga worktrees, inga öppna PR:er, inga stashar. Statusposterna från 30 april till 10 okt (cirka 146 000 tecken) ligger ordagrant i `docs/superpowers/status-arkiv/2026-10-10-claude-md-status.md`; läs där när en gammal detalj behövs. Skriv nya statusposter kort här, nyast överst, och flytta dem till arkivet när de är inaktuella.

### Läget i korthet

- **Android i produktion:** 1.2 (vC125, 1.2.0-rc3) sedan 2026-06-17. **1.3.1 (vC130) är byggd, emulatortestad (API 36 och API 30) och redo att skickas in:** gratis (Premium öppet för alla, `PREMIUM_OPEN_FOR_LAUNCH=true`, brytpunkt 0, ingen tack-skärm), tagg `v1.3.1` (`ca9c5927`). **Inskickad till granskning lör 10 okt 22.15** (Albins OK): fullständig lansering 100 %, AAB:n tillagd från biblioteket (Albin laddade upp den), versionsnamn "130 (1.3.1)", What's new en-US och sv-SE. Varningar: 1 telefonmodell av 12 275 tappar stödet (3 ABI:er mot 4 i 1.2), inga felsökningssymboler; inga fel. Hanterad publicering är av, så versionen går ut av sig själv när Google godkänt (oftast en till sju dagar). Butikstexten på tio språk och butiksbilderna i Flock-looken ligger redan i Play (Albin: "The store looks perfect").
- **Webben (birdy.community):** Flock-looken med flocken som lyfter på startsidan, ljus meny och ljust galleri (ordmärket rost), klippsidan `/clips/` och `/sv/klipp/` (nattbygget lägger till dagens klipp 00.05), 100 av 180 artsidor (paus till tidigast tors 15 okt), Premium-texten för gratis 1.3.1, bildmetadata (licens + copyrightNotice) på varje artfoto. Bloggen 10 okt: "See the song", "Birdy × AlbIT: en rädsla för fåglar blev en app" (collab-hjälte där AlbIT:s svarta fält möter Birdys persikopapper) och omskrivna "Varför Birdy finns" med fältet `updated`; läsvyn har läslinje, anfang, skribentruta och två fler fältanteckningar. **10 okt kväll (Albins OK):** See the song har flockomslaget (kort, bild, delningsbild, videons affisch), "Varför Birdy finns" har flocken som rödhake (val A), bloggen ett enhetligt kortsystem, och flockbilderna flyger åt samma håll (`website/tools/render-note-art.mjs`); Premium ligger i ljuset med flocken som flyger ett år runt sigillet (P3, `website/src/lib/year-flock.mjs`, dagens fågel flyttas av nattbygget). **Samma kväll (Albins val):** menyn säger **Blogg** och **Möt fåglarna** (Blog, Meet the birds), titeln "Birdy: Känn igen, samla och lär känna fåglarna" och ingressen nämner fältdagboken, livslistan och märkena. **Flygvägen mellan bloggens bilder** (fåglarna flyger från en bild in i inlägget ovanför) byggs på grenen `website/flocken-flyger-vidare` (worktree `C:/w/birdy-flyg`, spec och plan 2026-10-10) och visas för Albin som förhandsvisning före sammanslagningen.
- **Sociala medier:** Facebook-sidan "Birdy: Bird ID", Instagram @app.birdy, TikTok @birdy.app, YouTube @birdy.community, sloganen "Know the bird. Keep the moment." En video om dagen 08.00 (serien See the song med flockomslag och loop) schemalagd på **alla fyra kanaler till och med 7 nov**; YouTube 29 okt till 7 nov laddades upp lör 10 okt kväll (klart 19.53, kontrollerat i listan: en Short per dag, inga utkast). Nästa schemaläggning gäller serien från 8 nov.
- **iOS (v2):** koden klar till och med i4 (foto-ID, kamera, ljud-ID, karta, notiser, PDF). Kvar: Albins sim-check och device-verify, Apple Developer-enrollment, i5 StoreKit 2, i6 App Store. `docs/ios-release-checklist.md` (listar nu även tre Cursor-fixar för iOS att granska i i5).

### Nästa steg i ordning

1. **1.3.1 när Google godkänt** (inskickad 10 okt 22.15): datumet som reserv i `APP_1_3_LIVE_FROM` (`website/src/lib/release.mjs`), webbgrinden, push, live-koll; en rad i #birdy-bygge. Kontrollera status i Play Console, Publiceringsöversikt ("Ändringarna granskas").
2. ~~**YouTube 29 okt till 7 nov**~~ ✅ klart 10 okt 19.53 (receptet för skymt fönster uppdaterat i runbooken `docs/superpowers/runbooks/2026-10-08-sociala-schemalaggning.md`).
3. **Siffrorna per kanal varje måndag från 12 okt** (YouTube Studio, TikTok Studio, Business Suite, Search Console, Vercel Analytics, Play Console): en rad per kanal i #birdy-marknad och i tabellen i Albins dokument "Birdy: läget, varumärket och 12–24 månader framåt".
4. **Den betalda releasen = v1.3.2, Premium-uppdateringen** (Albin 10 okt kväll): `docs/superpowers/plans/2026-10-08-1.3.1-samlad-release.md`, nu med **kalibrering av ljud- och kamera-ID** (punkt 15) och **faktagranskning av appens 839 arter** (punkt 16) före produktionsbygget. Grindar: BirdNET:s svar, köptestet med vC129, MapTiler Flex + ny nyckel, Resend, och **brytpunkten = 1.3.1:s go-live + 48 h** (bygget stoppar annars). Varje del med egen plan och SDD i egen worktree under `C:/w/`.
5. **Artsidorna tidigast tors 15 okt:** Search Console först, sedan återstart enligt överlämningen (worktreen för loopen återskapas, `website/reports/STOP` i huvudklonen tas bort), cirka 3 arter om dagen; våg 3 när API-budgeten räcker.
6. **Nästa sociala serie (från 8 nov):** en liten förloppsmätare överst i klippen (Albins OK 10 okt kväll, sammanslagen; rendera med `node cover/title-card.mjs <set> --progress`), omslagen med flocken som flyger åt samma håll, nya hookar och längre bildtexter; detaljerna överst i runbooken.

### Väntar på Albin

- Två osända ändringar i Play för det stängda testet Alpha (lägg till e-postlistan "Testing Group #1", ta bort Google-gruppen birdy-testers@googlegroups.com): skickades inte med 1.3.1; skicka eller kasta dem i Publiceringsöversikten.
- Beslut: löftet "alla som laddar ner innan Premium börjar kosta får Premium gratis för alltid" (kräver brytpunkten, install referrer för Android 12 och äldre, ny webbtext); Märken 1a i den betalda releasen (rekommendation: ja, ihop med 1b); Cockpit-planen för måndagssiffrorna; undantagsarket för våg 2 före 15 okt.
- Köptestet med vC129, MapTiler Flex, Resend-kontot, BirdNET-brevet, Slack Pro.
- Klippsidan i bion (Instagram; TikTok efter företagskontot i appen), plattformsegendomarna TikTok och Instagram i Search Console, adresserna till de nya blogginläggen till albit.se-sessionen.
- iOS: sim-check, device-verify, enrollment.

### Bestående fakta (detaljerna i arkivet)

- **Språk:** chatten med Albin på engelska; repot, Slack och commit-meddelanden på svenska. Inga tankstreck i texter (webbens vakt `test:no-dashes`, appens `NoDashesInAppTextTest`).
- **Publik text** får aldrig nämna "full auto", att något körs utan tillåtelse, eller citera interna regler ur den här filen (Albin 2026-10-10).
- **Flockkonst flyger ihop** (Albin 2026-10-10, "så alla små fåglar alltid samlas eller är riktade mot samma håll"): i varje bild eller video med flocken pekar alla små fåglar åt samma håll som fågeln de bildar eller dit flocken rör sig, med högst några graders variation, och de som lämnar flyger samma väg. Silhuetter som tittar åt vänster speglas. Kontrollera i ett närutsnitt innan något visas.
- **Slack (AlbIT AB, gratisplan):** navet #birdy plus #birdy-status, #birdy-bygge, #birdy-marknad, #birdy-webb, #birdy-tidslinje, #birdy-readme. Efter varje arbetspass en kort rapport i #birdy-status (klart, pågår, nästa, väntar på Albin), på svenska.
- **Betalning (Play, AlbIT AB):** `premium_lifetime_v1` 499 kr, `premium_yearly_v1` 199 kr, `premium_monthly_v1` 49 kr (appen känner inte månaden än). Priser anges exkl. moms i Console (499 kr = 399,20). Licenstestarlistan "Birdy licenstestare", serviceavgiften 15 % registrerad, utbetalningsuppgifterna saknas (Albin). Tidig användare avgörs av **bevis** (gammalt `firstInstallTimestamp` eller nätverkstid från API 33), aldrig enhetsklockan; brytpunkten får aldrig ändras efter en release.
- **BirdNET-modellen är CC BY-NC-SA:** ljud-ID får aldrig ligga bakom Premium.
- **MapTiler:** den läckta Default key är låst till user agent `se.birdy.` (appen skickar paketnamnet, iOS `se.birdy.ios`); bygget släpper bara igenom den med `-Pbirdy.leakedMapTilerKeyLocked=true`. Byts mot en ny nyckel med den betalda releasen när Flex är köpt. Webbens nyckel är låst till `birdy.community`.
- **Vercel:** projektet `birdy` i teamet `albtab` (Pro), Root Directory `website`, Agent Code Review avstängd. Nattbygget `.github/workflows/daily-site-build.yml` (deploy hook) bygger om produktion; kör det manuellt när en driftsättning fallerat. IndexNow varje måndag (`weekly-indexnow.yml`).
- **Artsidornas pipeline:** `uv run birdy-fetcher web facts|verify|sheet|import|write` i `tools/content-pipeline`, publiceringen via `website/scripts/publish-loop.sh` i en egen worktree (aldrig mot samma poster på `main` medan loopen går). API-budget 100 USD i månaden.
- **Maskinen:** högst ett Gradle-tungt jobb och en emulator åt gången (`pg-api36`, `pg-api30`), stoppa daemonerna efteråt. Sammanslagningar till `main` gör huvudagenten själv.

## Plan-of-plans (v1)

| # | Plan | Status |
|---|---|---|
| 1 | Foundation — KMP-bootstrap, Compose, CI | ✅ `v0.1.0-foundation` |
| 2a | Content pipeline + walking skeleton (5 arter) | ✅ `v0.2.0a-pipeline` |
| 2b | Content backfill (5 → 839 arter) | ✅ `v0.2.0-content` |
| 3 | Encyclopedia (browse + species profile) | ✅ `v0.3.0-encyclopedia` |
| 4a | ML & Camera UI (FakeClassifier + 3 fps CameraX) | ✅ `v0.4.0a-camera-ui` |
| 4b | Real TFLite (AIY Birds V1, 965 klasser, ~14ms) | ✅ `v0.4.0b-real-tflite` |
| 5a | Diary (browse + detail + save flow) | ✅ `v0.5.0a-diary` |
| 5b | Gamification (25 badges, streaks, unlock-queue) | ✅ `v0.5.0b-gamification` |
| 7a | Redesign Foundation — tokens, DataStore, Onboarding, Settings | ✅ `v0.7.0a-foundation` |
| 7b | Redesign Skärmar — Listen/Archive/Lifelist/Badges | ✅ `v0.7.0b-screens` |
| 7c | Field Journal redesign — DM Serif + Caveat + paper-bg + StampSeal | ✅ `v0.7.0c-field-journal` |
| 7d | Match-flow — threshold-logik, Match/Disambig/NoBird-screens | ✅ `v0.7.0d-match-flow` |
| 7e | Premium tier — PremiumScreen + per-tab teasers + cold-start modal | ✅ `v0.7.0e-premium` |
| 6a | Foundation — UX-polish + release-mekanik (R8, signing, icon, a11y) | ✅ `v0.8.0-rc1` |
| 6b1 | Billing v8 + launch-prep (PremiumBillingClient + Restore Purchases) | ✅ `v0.9.0a-billing` |
| 6b2 | Audio-ID via BirdNET-Lite (3s rec + FlexRFFT TF Select op) — **free-tier** | ✅ `v0.9.0b-audio` (PremiumGate rivet 2026-05-22) |
| 6b3 | Premium content (PDF-export + season-statistics + 10 fält-märken) | ✅ `v0.9.0c-premium-content` + `v1.0.0` |
| W | Marketing-website (Astro + Vercel + birdy.community + /legal/) | ✅ Live |

**Föreslagen ordning:** ~~Plan 6b3 → tag v1.0~~ ✅ → ~~Internal Testing → Closed Testing (14d) → Play Store-launch~~ ✅ (live 2026-06-17).

## Plan-of-plans (v2 iOS)

Mål: feature-identisk Birdy (Android v1.2-parity, inga nya features) på App Store, från samma repo/KMP-kod. StoreKit-paywall aktiv från dag 1 (beslut 2026-07-07). Spec: `docs/superpowers/specs/2026-07-07-birdy-ios-v2-design.md`.

| # | Plan | Scope | Status |
|---|---|---|---|
| i0 | Environment + iOS ignition | Mac-toolchain, iOS-targets på alla moduler, xcodegen-Xcode-projekt, simulator-boot | ✅ task 1–9 klara (bootar i simulatorn; CI:s macOS-jobb sedan `96fabbd0`, sim-build sedan 2026-08-17); kvar: Apple Developer-enrollment (behövs först för device-sandbox i i5 och i6) |
| i1 | Encyclopedia + journal på fysisk iPhone | SQLDelight native driver, artbilder, lätta actuals, device-install | 🔄 kod klar + **sim-verifierad grön** (WebP-dekod ✅, prefs-persistens ✅, browse ✅); kvar = endast fysisk iPhone-install (Albin, senare — free-Apple-ID signing) |
| i2a | Android TFLite→LiteRT (16 KB-fix) | `litert:1.4.1` (foto-`.so` ✅), 16 KB flex-swap (audio-`.so` ✅, SHA-256-pinnad), classifier-first-run-fix, vC126-AAB byggd + alignment-grön | ✅ kod klar + **device-verify PASSERAD** (SM-S918B 2026-07-18: classifier + audio node-29 agent-verifierade); vC126 laddades aldrig upp, fixen når användarna med 1.3.0 (vC129) |
| i2b | iOS foto-ID (ML-runtime + galleri-scan) | Vendored TensorFlowLiteC-cinterop-runner + ImagePreprocessor.ios + PHPicker-scan + delad crop | 🔄 kod klar + **fullt review:ad** (`7ab14c32..782af6db`): alla 7 tasks Approved, T6-review + slutreview av hela grenen körda, båda fix-vågorna re-review:ade gröna; agent-verify grön (iOS-sim-tester + Android-gate + boot). KVAR: endast Albins interaktiva galleri→ID (48 MP HEIC + snabb rotate-dubbeltryck) + Android crop-re-verify |
| i2c | iOS live-kamera + ta-foto | AVCaptureSession + UIKitView-preview + zoom + ta-foto = **Milestone 1** (live scan på iPhone) | 🔄 kod klar + **fullt review:ad** (`f602af61..4507f979`): alla 9 tasks + T4a-prerequisite + 2 slutreview-Criticals fixade + re-review gröna; agent-verify grön (iOS-sim-tester inkl. ~300 commonTest på K/N + Android-gate + boot). KVAR: Albins sim-check (permission-flow + scan-återinträde) + Milestone 1 device-verify (kräver iPhone) |
| i3 | Audio-ID | BirdNET Select-TF-ops på iOS, AVAudioEngine-capture | 🔄 kod klar + **fullt review:ad** (`8c351b4c..a13c2cd3`): 9 tasks + slutvåg, alla Approved; storleksgate PASS (shippat delta ≤ 88 MB); sim kan aldrig köra riktig inferens (Flex = device-only) → sim visar felstate/DEMO. KVAR: Albins sim-check + device-verify (kräver iPhone) |
| i4 | Parity sweep | Karta (MapKit iOS), notiser, PDF-export, rest | 🔄 kod klar + **fullt review:ad** (`ce88e44a..e7b1a679`): 14 tasks + NSLog-mikrofix + slutreview-fixvåg, alla Approved; parity-bonus: Dagens fågel wire:ad på iOS (var aldrig kopplad förut). KVAR: Albins sim-check + device-verify (kräver iPhone) |
| i5 | StoreKit 2 | `PremiumBillingClient`-actual: köp + restore + entitlement | ⬜ |
| i6 | App Store-release | Ikoner, TestFlight, listing (återbruk SV/EN-texter), privacy labels, review | ⬜ |

Varje plan ska lämna projektet i ett byggbart, testbart tillstånd: `./gradlew build` ska gå grönt — **och Android ska förbli shippbar efter varje commit** (`:shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug` gröna).

## Var hittar du saker

| Vad | Var |
|---|---|
| Designspec för v1 | `docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md` |
| Designspec för v2 (iOS) | `docs/superpowers/specs/2026-07-07-birdy-ios-v2-design.md` |
| Implementationsplaner | `docs/superpowers/plans/YYYY-MM-DD-v1-NN-<phase>.md` (iOS: `...-ios-iN-<phase>.md`) |
| Mac-bootstrap-guide | `docs/mac-bootstrap.md` |
| Skärmdumpar per milstolpe | `docs/superpowers/screenshots/` |
| Milstolpe-review-runbook | `docs/superpowers/runbooks/milstolpe-review.md` |
| Internal Testing hand-off runbook | `docs/superpowers/runbooks/2026-05-22-v1.0.0-internal-testing.md` |
| Play Store-artefakter (markdown) | `docs/play-store/{privacy-policy,terms,store-listing-{sv,en},data-safety-form}.md` |
| Website källkod | `website/` (Astro 7 + Tailwind v4 + Playwright) |
| Visuellt språk (Mossbädd + Field Journal) | sammanfattat nedan + auto-memories `visual_language_birdy_v1.md`, `project_plan_7c_status.md` |
| Auto-memory (lokalt, inte i repo, en per maskin) | Windows: `~/.claude/projects/C--Users-abbea-dev-1-mina-projekt-birdy/memory/` (före repoflytten: `...-dev-birdy-bird-scanner`) |
| Äldre statusposter (30 april till 10 okt 2026) | `docs/superpowers/status-arkiv/2026-10-10-claude-md-status.md` |
| Projekthistorik (tidslinje, privat) | "The Birdy Logbook" i Albins artefaktlista på claude.ai |
| Launch-research | `docs/superpowers/research/2026-05-15-play-store-launch/` + `2026-05-20-play-store-audit.md` |

## Hur vi jobbar

### När du börjar en ny session
1. Säg "Vi fortsätter med birdy-bird-scanner" eller liknande.
2. **`git pull` först** — den andra maskinen kan ha pushat sedan sist (två-maskiners-setup, se Repo & deploy).
3. Be om statusöversikt: "Var står vi?" → kolla git log + senaste commit.
4. Bestäm nästa steg utifrån status.

### SYNK-REGEL (obligatorisk — två maskiner, ett repo)

**Vi synkar ALLTID CLAUDE.md.** Albin jobbar växelvis på Windows-maskinen (Android) och Mac:en (iOS); repot är enda kanalen mellan dem. Därför:

1. **Vid slutet av varje arbetssession** (eller efter varje avslutad milstolpe/plan-task): uppdatera CLAUDE.md:s Status-sektion med vad som hänt → committa → **pusha**. En session är inte klar förrän CLAUDE.md speglar läget och är pushad.
2. **Placeringsregel för kunskap:** behöver *båda* maskinerna veta det → repot (CLAUDE.md eller `docs/`). Bara *den här* maskinen → auto-memory. Auto-minnen synkas INTE mellan maskinerna — `[[minnes-länkar]]` i den här filen är bara läsbara på maskinen som skrev dem, så lyft in innehållet i repot om det är beslutskritiskt.
3. **Innan maskinbyte:** allt committat + pushat. Okommitterade experiment (t.ex. pågående version-bump-test) noteras i Status-sektionen så den andra maskinen vet att de finns.

### Behövs superpowers?
- **Brainstorming, ny plan, plan-execution med review** → `superpowers:brainstorming` / `:writing-plans` / `:subagent-driven-development`.
- **Vanliga frågor, snabba bugfixar, mindre refactoring** → bara prata; ingen skill.

Tumregeln: större än ett samtal eller kräver disciplin (TDD, plan-tracking) → skill. Annars inte.

### Modell-strategi
| Uppgift | Modell |
|---|---|
| Brainstorming, design, arkitektur, code review | Opus 4.7 |
| Implementer-subagents i `subagent-driven-development` | Sonnet 4.6 |
| Snabba lookups | Haiku 4.5 |

Vid avbrott: all progress är committad i git. Nästa session fortsätter från senaste commit utan tappad kontext.

## Visuellt språk

**Field Journal-tema (Plan 7c, locked 2026-05-10) är canonical app-wide.** Mossbädd-paletten under är legacy-tokens som fortfarande används punktvis (HeroMoss-gradient, AccentCopper).

**Field Journal tokens** (i `composeApp/.../ui/theme/Color.kt`):

| Token | Hex | Roll |
|---|---|---|
| PaperBg / PaperEdge | `#EFE7D6` / `#E5DCC7` | Pappersbakgrund + texture |
| MarginaliaInk | `#3F4F30` | Caveat-text, sub-lines (WCAG AA-bumpad i Plan 6a T9) |
| AccentCopper | `#A8552D` | CTA, aktiv tab, stat-siffror, copper-pills |
| StampNavy | `#1F3A5F` | StampSeal-states |
| HeroMossMid / Deep / Shadow | `#5C6E48` / `#3F4F30` / `#2A3520` | Mossgrön gradient (Listen/Premium hero) |

**Typografi:** `DM Serif Display Italic` för rubriker (`JournalHeadline` parsar `*ord*` → Caveat-italic accent-segment med rotation), `Caveat` för marginalia/sub-lines, `Inter` (system sans) för body. Fonts bundlade via `compose-resources` (`rememberDmSerifDisplay()` / `rememberCaveat()` i `Type.kt`).

**Layout-element:** `Modifier.paperBackground()` med dot-texture som default-bas; `JournalIntro` (eyebrow + JournalHeadline + ornament + sub-line); `StampSeal` (locked/in-progress/unlocked-states); `PlateFrame` (naturalist-foto-frame); `OrnamentRule` (❦ + horisontellt streck).

## Tekniska val

- **Android-stack:** KMP + Compose Multiplatform (Android primär)
- **iOS-stack (v2, pågår):** samma KMP-kod med `iosArm64` + `iosSimulatorArm64` på alla moduler; statiskt `ComposeApp`-umbrella-framework via `embedAndSignAppleFrameworkForXcode`; Xcode-projekt genererat med xcodegen (`iosApp/project.yml` är källan); SQLDelight `native-driver`; min iOS 16.0; bundle-id `se.birdy.ios`; Swift-lagret hålls minimalt (~10 små filer)
- **DB:** SQLDelight 2.x med Flow-baserade queries
- **ML (foto):** TensorFlow Lite + AIY Birds V1 (uint8-quantized MobileNetV2, ~14ms/inference)
- **ML (audio):** BirdNET-Lite v2 + `tensorflow-lite-select-tf-ops:2.16.1` (FlexRFFT TF Select op — utan denna failar node 29); **iOS (i3):** samma modell + Googles `TensorFlowLiteSelectTfOps` 2.17.0 vendrad via SHA-pinnad fetch (`tools/fetch_ios_selectops.sh`, körs automatiskt som xcodegen-preBuildScript), `-force_load` ENDAST device-SDK:n — simulatorn kan aldrig köra riktig audio-inferens (visar ärligt felstate/DEMO)
- **Kamera:** CameraX 3 fps `ImageAnalysis` + auto-throttle till 1.5 fps vid p95 > 333ms
- **Audio:** 48kHz mono PCM_16 via UNPROCESSED → VOICE_RECOGNITION graceful fallback; öppen inspelning (60s tak) med rullande 3s-fönster/1s stride, auto-stopp ≥0.65, per-art sessions-ackumulator → top-3; OGG/Opus-encode endast API 29+ (annars ingen uppspelningsfil)
- **Billing:** Google Play Billing v8 (`PremiumBillingClient` expect/actual) + RSA SHA1-signature-verify via Play Licensing public key embeddad i BuildConfig
- **Språk:** SV + EN, Sverige först; alla UI-strängar via `compose-resources`
- **Distribution:** AAB via Play Asset Delivery
- **CI:** GitHub Actions (ktlint 12.1.2, detekt 1.23.7, unit tests, assembleDebug)
- **Website:** Astro 7 + Tailwind v4 + `@astrojs/sitemap` + `marked` + Playwright smoke tests, hostat på Vercel (auto-deploy från `main`, root dir = `website`)

## Lokal utvecklingsmiljö — Windows (Android-maskinen, Galaxy S23 Ultra)

| Vad | Var |
|---|---|
| JDK 21 (Temurin) | `C:\Java\OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10\jdk-21.0.11+10\` |
| Android SDK | `C:\Users\abbea\AppData\Local\Android\Sdk` |
| ADB | `C:\Users\abbea\AppData\Local\Android\Sdk\platform-tools\adb.exe` |
| Telefon | SM-S918B (Galaxy S23 Ultra, API 35), USB-felsökning på, RSA-auktoriserad |

**Standard-prefix för bash-`./gradlew`-kommandon** (annars hittar Gradle inte Java):

```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
```

## Lokal utvecklingsmiljö — macOS (iOS-spåret, från 2026-07-07)

| Vad | Var |
|---|---|
| Repo | `~/dev/birdy` |
| JDK 21 (Temurin) | `~/.local/java21/Contents/Home` (JAVA_HOME satt i `~/.zshrc`) |
| Android SDK | `~/Library/Android/sdk` (platform-tools, android-35, build-tools 35.0.0) |
| Xcode | 26.6 (App Store-installerad); simulator-runtime via `xcodebuild -downloadPlatform iOS` |
| xcodegen | `~/.local/bin/xcodegen` — `iosApp/project.yml` är källan, `.xcodeproj` genereras |
| gh CLI | `~/.local/bin/gh` (inloggad som anonadrek) |

Inga Windows-prefix behövs — `./gradlew` funkar direkt (JAVA_HOME kommer från `~/.zshrc`; OBS: Claude Codes Bash-skal ärver den inte alltid — kör `export JAVA_HOME="$HOME/.local/java21/Contents/Home"` i kommandot om Gradle klagar på Java). Samma sak för `xcodebuild`/`xcrun`: `xcode-select -p` pekar på CommandLineTools på den här maskinen — exportera `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` i kommandot (varje nytt skal/subagent drabbas; simctl failar annars med "unable to find utility"). **Konan-gotcha (LÖST 2026-07-11):** cache-bygget för CMP 1.7.3-klibs under Kotlin 2.1.20 kraschade vid `linkDebugFrameworkIosSimulatorArm64`; fixat genom bump till CMP 1.8.2 (`a12cf41f`) — workarounden `kotlin.native.cacheKind=none` är borttagen. Om kraschen någonsin återkommer: se NOTE-blocket i `gradle.properties`.

## Vanliga kommandon

```bash
# Android: bygga + installera + starta på ansluten enhet
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity

# Android: snabba unit-tests (delade moduler på JVM)
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest

# Android: lint + statisk analys
./gradlew ktlintCheck detekt
./gradlew ktlintFormat   # autofix

# Android: signed release-AAB
./gradlew :androidApp:bundleRelease

# iOS (Mac): snabbcheck att Kotlin-sidan kompilerar + länkar
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64

# iOS: unit-tests för shared-moduler på simulator-target
./gradlew :shared:content:iosSimulatorArm64Test :shared:domain:iosSimulatorArm64Test :shared:data:iosSimulatorArm64Test :shared:ml:iosSimulatorArm64Test

# iOS: regenerera Xcode-projektet (bara efter ändring av iosApp/project.yml)
cd iosApp && xcodegen generate
# Kör appen: öppna iosApp/Birdy.xcodeproj i Xcode, scheme "Birdy", iOS 16+-simulator.
# Build-fasen "Compile Kotlin Framework" kör embedAndSignAppleFrameworkForXcode via Gradle.

# Website (kör från website/)
cd website && npm run test:i18n && npm run test:no-accuracy && npm run test:contrast && npm run test:no-dashes && npm run test:palette && npm run build
cd website && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4741 npm run test:smoke   # Playwright (113 tester); egen port, 4321 kan vara en annan sessions server
```

## Repo & deploy

- **GitHub:** https://github.com/anonadrek/birdy. Branch `main` är default. Plan-arbete sker på `main` med små commits per task; tagga milstolpar (`v0.1.0-foundation` osv).
- **Två dev-maskiner, ETT repo:** Windows-maskinen (Android-primär, all v1-historik) + Mac:en `~/dev/birdy` (iOS-spåret sedan 2026-07-07). GitHub `main` är sanningskällan mellan maskinerna — pusha innan man byter maskin. (Per 2026-07-11 är i0-commitsen + CMP-bumpen pushade; endast CI-workflow-committen väntar på `workflow`-scope, se Status.)
- **Website:** Auto-deploy till `birdy.community` via Vercel vid push till `main`. **Root Directory MÅSTE vara `website`** i Vercel project settings — annars failar `npm install` med ENOENT på `/vercel/path0/package.json`.
- **Play Console:** Personligt konto (Albin), approved 2026-05-20. App entry + in-app products + license testers behövs innan Billing v8 IPC kan runtime-verifieras.

## Beslut & ramar

- **Scope v1.0:** Skanna (foto + audio) + uppslagsverk + dagbok + gamification + premium tier. Map/cloud sync = v1.5.
- **Geografi:** Norden/Europa, 839 arter.
- **Användare:** Bred två-lager (nybörjare som vill lära sig + entusiaster i fält).
- **AI:** On-device, ingen backend för inference. Migrationsdata + sannolikhet är art-nivå statisk i v1.
- **Solo-utvecklare:** användaren bygger via Claude Code; granskning sker av användaren mellan tasks.
- **Privacy-löfte:** "Almost nothing collected, data stays on phone" — verifierat i 2026-05-20 fältrevision. INTE bryt detta utan diskussion.

## Frågor + autonomi

- Otydlig task eller spec-motsägelse? **Stoppa och fråga / lyft upp** istället för att gissa.
- Annars: **"Don't ask me for permission to run anything"** — kör commits, push, gradle, file-edits enligt plan utan bekräftelse. Vid scope-creep i review: fixa autonomt (soft-reset + re-commit). Undantag: blockerare som kräver fysisk åtkomst (telefon, emulator) eller tredjepartsbeslut (Play Console, Vercel UI) — där rapporterar man status. Två-stegs-review (spec → kvalitet) körs alltid mellan tasks.

## Pending follow-ups (post-launch)

1. **GitHub Pages teardown:** I repo Settings → Pages, sätt Source till "None". `pages.yml` är redan borttagen (Plan W T2). **OBS:** in-app + store-listing URL:er är redan migrerade till `birdy.community/legal/` (verifierat 2026-05-24); enda kvar är att bekräfta Play Console-UI:ns Privacy/Terms-fält pekar på samma — manuell Console-grej när nästa AAB laddas upp.
2. **Email migration:** Sätt upp `feedback@birdy.community` (Cloudflare Email Routing eller Resend Inbound) under closed testing. **Bridge nu = `albin@abrahamssons.se`** — bytt in i alla legal-docs 2026-05-22. När birdy.community-mailen är live: byt i `website/src/lib/links.ts` (`CONTACT_EMAIL`, enda stället på webben efter att `website/1.3-lyft` slagits ihop; FAQ:n har inte längre adressen, före ihopslagningen ligger den i `website/src/content/copy.{en,sv}.json`) + alla markdown-filer i `docs/play-store/`.
3. **Billing v8 IPC runtime-verify + go-live** (deferred från 6b1) — **MÅSTE köras innan `PREMIUM_OPEN_FOR_LAUNCH=false` och innan officiell production-release.** **(2026-09-25: flippen + grandfather ligger i 1.3.0-koden; köptestet körs med vC128 enligt runbookens uppdatering överst, inklusive den hårda licensnyckelgrinden.)** Full plan i `docs/superpowers/runbooks/2026-05-26-billing-verify-and-go-live.md`. Innehåller: debug-toggle som skippar override:n per-device, full Billing v8 verifieringschecklista (purchase / restore / YEARLY vs LIFETIME / refund / signature-fail), BirdNET-licensguard (unit test redan på plats: `BirdNetLicenseGuardTest`), conversion-monitoring-plan +7d/+14d post-launch. **OBS:** under closed testing kan `PREMIUM_OPEN_FOR_LAUNCH=true` stå kvar — testarna ska ha hela v1.0-upplevelsen gratis. Flippen sker först efter att runbook-punkt 1+2 är gröna.
4. **Audio accuracy eval** (deferred från 6b2): kräver xeno-canto API v3 key. Pipeline klar i `tools/ml-eval/audio_accuracy_report_2026-05-21.md`.
5. **AB-flytt:** Account Transfer av Play Console till AB-bolaget när det är registrerat (post-launch).
6. **SV legal-översättningar:** Om Sverige-trafik växer, mirror `/sv/legal/...` med översatta markdown-filer. Idag cross-linkar SV-footer till EN-only `/legal/`-routes (intentionellt — Nordics/EU first launch).
7. **Plan 6a T8/T9 device-screenshots saknas:** `08-match-with-inline-note` + `09-disambig-save-as-unknown` (kräver deterministisk match-flow ej driveable via ADB — kan adresseras via test-image-infra i framtida sprint).
8. **Store-listning + release-notes vid nästa AAB-upload (v1.2, vC123 / 1.2.0-rc1):** **(Ersatt 2026-10-10: 1.3.1:s butikstext på tio språk och What's new ligger i Play-utkastet, se `docs/play-store/console-paste-v1.3.1.md`.)** Repo-texten i `docs/play-store/store-listing-{en,sv}.md` är nu **uppdaterad till v1.2** (2026-06-08, commit `0b2ed2a5` + versionName-PR): kartan (Premium) + gratis opt-in-plats, omgjorda märken (rödlistat-spår, livslista 500, audio/säsong gratis), ekologiska uppslagsgrupper, märkesantal **34** (27 gratis + 7 premium), och "What's new" v1.0.0→v1.2. **Kvar = bara den manuella Play Console-inmatningen** av samma text + "What's new"-fältet vid upload. (Täcker DP A–E + Phase B + DP B-positionering + post-vC122 + kartan.) Detaljerad batch-state i auto-memory `project_v1_1_release_train.md`.
9. **Bättre app-bilder före release (release-gate):** **(✅ Klart 2026-10-10: butiksbilderna i Flock-looken för 1.3.1, sv och en, i Play-utkastet; webbgalleriet med 1.3-skärmar live sedan 10 okt.)** Innan production-release ska vi ta fram bättre bilder som *visar och beskriver* applikationen — färska, snygga skärmdumpar/marknadsbilder som speglar v1.1-läget (omgjorda märken i två sektioner, ekologiska grupper, Troférummet, Veckans uppslag). Återanvänds på **två ställen**: (1) Play Store-listningens feature graphic + phone screenshots, (2) webbplatsens Glimpse-carousel (`website/src/assets/screens/` + `slides/`). Dagens sajt-skärmdumpar är från v1.0-eran (t.ex. 5×5-märkesrutnät stämmer inte längre — alt-texten är redan avsiffrad). Koppla ihop med #8 (samma upload-tillfälle).
10. **targetSdk 36-bump (vC128) — Play-policy-krav, deadline-styrd (✅ kod klar i Plan 1 2026-09-25: targetSdk/compileSdk 36, AGP 8.9.1, lintRelease 0 fel; varningen i Console släcks när vC129 nått produktion):** Play kräver targetSdk 36 (Android 16); API 35-uploads blockeras från 31 aug 2026. Ordning: vC127 (API 35) laddas upp FÖRE 31 aug, därefter vC128 = `android-targetSdk`/`android-compileSdk` 35→36 i `gradle/libs.versions.toml` som egen uppgift med eget verify-varv (API 36-emulator; predictive back default-on, edge-to-edge-opt-outen borttagen, AGP-stöd för compileSdk 36). vC128 kan laddas upp efter 31 aug utan problem. Console skickar bekräftelsemejl när en 36-version nått produktion — först då är varningen släckt. Se 🚨-posten i Status (2026-08-20).
11. **Sociala medier när artsidorna börjar publiceras (Albins beslut 2026-10-06, PÅMINNELSE):** **(✅ Igång sedan 9 okt: serien See the song på fyra kanaler till 7 nov, se runbooken `docs/superpowers/runbooks/2026-10-08-sociala-schemalaggning.md`.)** när de första artsidorna går live på birdy.community startar ett dagligt inlägg på Facebook + Instagram (TikTok senare) om en publicerad artsida, samma mall för alla arter, schemalagt en månad i taget eller automatiskt via Meta Graph API. Albin skapar kontona själv (agenten får inte skapa konton). Bara bilder med CC0/PD/CC BY/CC BY-SA, kredit i bildtexten. Detaljer: spec `docs/superpowers/specs/2026-09-25-artsidor-design.md` §15.1. Ta upp det med Albin så fort fas 2:s publiceringsloop körs.

## Roadmap post-v1.0 (referens)

Tagits in från v1-design-spec så vi inte tappar bort dem. Inget byggs här innan v1.0 är ute.

### Geografisk expansion (lågsiktig huvudtrack)

ML-modellerna är redan globalt tränade (AIY V1 ≈ 965 klasser, BirdNET-Lite ≈ 6000) — vi har bara filtrerat till EU. Expansionsjobbet sitter i **content-pipeline** (en YAML + plate-foto per art), **regional migrations-/säsongsdata**, **on-demand asset packs** (APK växer snabbt — bortom v1.0:s 136 MB-base) och **fler språk**.

- **v1.0–v1.2 — Norden/Europa (839 arter)** ← live på Google Play
- **v2 — "Asien + hela Europa" + iOS-launch:** Utöka content till delar av Asien (lämpligen Östasien/Indien först) **och** släpp samtidigt på App Store. **iOS-delen har startat 2026-07-07** — eget plan-spår i0–i6, se "Plan-of-plans (v2 iOS)" ovan (Compose Multiplatform-iOS-target + minimal Swift-shim för plattforms-API:er; kamera, audio, billing → StoreKit istället för Play Billing, share-sheet, file-export). Content-expansionen (Asien) är ännu inte påbörjad. Webb: ny `/regions/`-sida med coverage-status (✅ supportat / 🟡 snart / ⬜ planerat) — byggs först när content-delen faktiskt är igång, annars står den tom.
- **v3 — "Hela världen":** Alla återstående kontinenter; full content-skalning + språkstöd.

### Parallella feature-spår (inte version-bundna)

Kan landa när som helst längs geografi-tracken; placering bestäms när vi närmar oss.

- **"Karta & moln":** Konton, molnsynk av dagboken, karta med fynd från publika datakällor, push-notiser om sällsynta arter nära användaren. `Observation`-schemat har nullable `latitude` / `longitude` / `location_label` från Plan 5a så vi bara fyller i nya rader (ingen migration behövs).
- **"Community" → Birdy-forumet:** Delning av fynd, kommentarer, flöde, diskussioner, moderering.
- **Övrigt:** Quiz/utbildningsläge, fullt offline-läge för längre exkursioner.

### Det slutliga målet: ett riktigt Birdy-forum (Albin 2026-10-10)

En plats där människor som tycker om fåglar träffas, pratar, delar sina fynd och diskuterar det de har sett. Allt annat i roadmapen leder hit. Ramar: **opt-in** (konto och server bara för den som går med), fältdagboken stannar i telefonen tills användaren själv delar ett fynd, och privacy-löftet ("nästan inget samlas in") gäller oförändrat för alla som inte går med. Kräver moderering och en backend, så det byggs ovanpå "Karta & moln" (konton, molnsynk) och "Community" ovan. Står även i `/goal` (`.claude/commands/goal.md`) och berättas publikt i fältanteckningen "Birdy × AlbIT".

## Avslutade planer (referens)

Detaljerade lärdomar + återanvändbara mönster finns i auto-memory (`project_plan_<NN>_status.md`). Här bara one-liners + tagg + spec-pointer.

| Plan | Tag | Plan-doc | Auto-memory |
|---|---|---|---|
| 1 Foundation | `v0.1.0-foundation` | `2026-04-30-v1-01-foundation.md` | — |
| 2a Pipeline | `v0.2.0a-pipeline` | `2026-05-02-v1-02a-content-pipeline.md` | `project_plan_2b_status.md` (delad) |
| 2b Content backfill | `v0.2.0-content` | runbook `2026-05-02-plan-2b-content-backfill.md` | `project_plan_2b_status.md` |
| 3 Encyclopedia | `v0.3.0-encyclopedia` | `2026-05-04-v1-03-encyclopedia.md` | `project_plan_3_strategy.md` |
| 4a Camera UI | `v0.4.0a-camera-ui` | `2026-05-05-v1-04a-camera-ui.md` | `project_plan_4a_status.md` |
| 4b Real TFLite | `v0.4.0b-real-tflite` | `2026-05-07-v1-04b-real-tflite.md` | `project_plan_4b_status.md` |
| 5a Diary | `v0.5.0a-diary` | `2026-05-05-v1-05a-diary.md` | `project_plan_5a_status.md` |
| 5b Gamification | `v0.5.0b-gamification` | `2026-05-06-v1-05b-gamification.md` | `project_plan_5b_status.md` |
| 7a Redesign Foundation | `v0.7.0a-foundation` | `2026-05-08-v1-07a-redesign-foundation.md` | `project_plan_7a_status.md` |
| 7b Redesign Skärmar | `v0.7.0b-screens` | `2026-05-09-v1-07b-redesign-screens.md` | — |
| 7c Field Journal | `v0.7.0c-field-journal` | `2026-05-09-v1-07c-field-journal.md` | `project_plan_7c_status.md` |
| 7d Match-flow | `v0.7.0d-match-flow` | `2026-05-12-v1-07d-match-flow.md` | `project_plan_7d_status.md` |
| 7e Premium tier | `v0.7.0e-premium` | `2026-05-12-v1-07e-premium-tier.md` | `project_plan_7e_status.md` |
| 6a Release foundation | `v0.8.0-rc1` | `2026-05-13-v1-06a-foundation.md` | `project_plan_6a_status.md` |
| 6b1 Billing + launch-prep | `v0.9.0a-billing` | `2026-05-16-v1-06b1-billing-launch-prep.md` | `project_plan_6b1_status.md` |
| 6b2 Audio-ID | `v0.9.0b-audio` | `2026-05-20-v1-06b2-audio-id.md` | `project_plan_6b2_status.md` |
| 6b3 Premium content | `v0.9.0c-premium-content` + `v1.0.0` | `2026-05-21-v1-06b3-premium-content.md` | `feedback_plan_6b3_doc_traps.md` |
| W Website (Vercel + /legal/) | — (live) | `2026-05-21-website-vercel-legal.md` | — |

## Trap-katalog (vanliga repeterande buggar)

Saker som har bitit oss mer än en gång — kolla först här om något konstigt händer:

- **`:androidApp` saknar transitiva deps från `:composeApp`** (Plan 5a T12) — composeApp använder `implementation()` inte `api()`, så varje ny shared/library-referens måste få egen `implementation()` i `:androidApp/build.gradle.kts`.
- **compose-resources unescape:ar inte Android `\'`** — använd raw `'` eller Unicode `’` (U+2019) direkt i strings.xml.
- **compose-resources processar inte `%%` som `%`-escape** — använd `%1$s` och passa pre-formatterad `"${value}%"` från Kotlin-call-site. Regression i Plan 5a → 7d.
- **`ImageProxy.imageInfo.timestamp` returnerar nanos sedan boot, inte Unix-epoch** — använd `System.currentTimeMillis()` i CameraX-analyzer för wall-clock timestamps.
- **Hardcoded localized strings bryter andra locale** — alltid `stringResource(Res.string.xxx)`, aldrig `"spara 60%"` direkt i Kotlin.
- **ADB-tap y < 300 kan trigga notification-drawer** i stället för UI-element — använd `KEYCODE_BACK` för recovery + `uiautomator dump` för exact bounds.
- **Quality-review måste köra `:androidApp:installDebug` + device-test** (Plan 5a process-lärdom) — `:composeApp:assembleDebug` ensam missar manifest/dep-trap för Android-screens.
- **FlexRFFT-crash i TFLite-audio** — kräver `tensorflow-lite-select-tf-ops:2.16.1` dep, annars failar node 29 "Failed to prepare". Diagnostisk logging > catch-all `Throwable`-swallow.
- **Vercel `npm install` ENOENT** — Root Directory måste vara `website` i project settings (inte `/website`, inte tomt).
- **BirdNET-Lite-modellen är CC BY-NC-SA (NonCommercial)** — får INTE gate:as bakom Premium. Audio-ID är gratis-feature i v1.0. Om vi någonsin lägger något bakom Premium som rör BirdNET → licensbrott. Premium = endast Plan 6b3-features (PDF/stats/badges) som vi byggt själva.
- **Compose test-`composeResources` når INTE Kotlin/Native-testbinärer** (i2b) — iOS iosTest kan inte `Res.readBytes` från commonTest-resurser. Embedda små bytes i koden, eller lägg fixturen i **commonMain** composeResources (som modellen). Fullständig iOS-ML-gotcha-lista i [[reference_ios_ml_runtime_and_parity]].
- **iOS `TfLiteModelCreate` kopierar INTE FlatBuffern** (i2b) — modell-bytes måste **lifetime-pinnas** (`modelBytes.pin()`-fält + `unpin()` i `close()`), inte scoped `usePinned`. Och `.def` måste vara header-form (`modules =` failar på Xcode 26.6-SDK).
- **Back i delade composables: använd `PlatformBackHandler`, inte hosten** (i2b — RÄTTAD 2026-07-26). CMP 1.8.2:s multiplatform `androidx.compose.ui.backhandler.BackHandler` är visserligen runtime-scope och kan inte refereras från commonMain — **men det är inget skäl att skjuta back-hanteringen till hosten**, för repot har redan en egen expect/actual: `composeApp/src/commonMain/.../ui/components/PlatformBackHandler.kt` (Android-actual = `BackHandler`, iOS-actual = no-op), använd från commonMain av `OnboardingScreen` sedan `cdaa06f9` (2026-05-15). i2b T5 lyfte först `CropAdjustScreen` med back kvar i Android-hosten på den felaktiga premissen att inget delat alternativ fanns; i2b:s slutreview fångade det och `782af6db` rättade det. **Varje ny delad composable som behöver back ska anropa `PlatformBackHandler` själv** — annars tappar nästa host tyst back-to-cancel.
- **PHPicker `.delegate` är weak** → delegaten måste strong-retainas annars fyras callbacken aldrig. Och `UIApplication.sharedApplication.keyWindow` är deprecated för scene-baserade appar (Birdy **är** scene-baserad, SwiftUI `@main App` + `WindowGroup`) → lös root-VC via `connectedScenes` → `UIWindowScene.windows.firstOrNull { it.isKeyWindow() }` (`isKeyWindow` bryggas som **funktion**, inte property).
- **`./gradlew detekt` analyserade INGEN KMP-modul före 2026-08-17** (upptäckt i i2b:s slutreview 2026-07-26; ✅ **FIXAD i follow-up-passet 2026-08-17**). Rotorsak: `allprojects { apply(detekt) }` applicerade plugin:et men `detekt {}`-blocket låg på **rot-projektet** och konfigurerar inte subprojekten → varje modul körde detekts defaults `src/main/{java,kotlin}` (obefintliga i KMP-moduler) med DEFAULT-config — dvs. även `androidApp` skannades utan vår `detekt.yml`. **Fix:** `DetektExtension` konfigureras nu per projekt i `allprojects` (explicit produktions-källista `main|commonMain|androidMain|iosMain|jvmMain`; testkällor medvetet utanför, paritet med detekts default-hållning) + `ignoreAnnotated: ['Composable']` på FunctionNaming (Composables ÄR PascalCase — 199 falska positiver annars) + **committade `detekt-baseline.xml` per modul** (783 historiska stilfynd bokförda; korrekthetsgranskade före baseline: alla 7 SwallowedException är avsiktliga reviewade mönster — per-frame-drops i kamerakällorna, CE-rethrow-först-degraderingar i AudioScanViewModel, fail-closed signaturverify i billing). NY kod gate:as på riktigt — kanarie-verifierat red-green i commonMain. **Historiska "detekt grön"-claims före 2026-08-17 för delad kod förblir tomma** (koden har ktlint + tester + reviews + produktion bakom sig). Nytt avsiktligt fynd i ny kod: typad multi-catch eller `@Suppress` med motivering (T11-precedenten) — utöka INTE baselinen.
- **K/N failable ObjC-initializers kastar rå NPE, inte en nullable retur** (i2c T7/slutreview, 2026-07-27). Kotlin/Native mappar en Objective-C failable-init (`init?`, t.ex. `UIImage(data:)` på odekodbara bytes) till en Kotlin-**konstruktor**, och en konstruktor kan aldrig returnera `null` — Kotlin/Native löser detta genom att kasta `kotlin.NullPointerException` istället. Ett `UIImage(data = ...) ?: return null`-mönster ser ut som en null-check men är **dödkod**: elvisen körs aldrig, för konstruktorn antingen lyckas eller kastar. `IosImageDecode.kt` hade detta på fyra ställen innan `4507f979` fixade det med en delad `internal fun uiImageFromDataOrNull(data: NSData): UIImage? = try { UIImage(data = data) } catch (_: NullPointerException) { null }`. **Regel:** varje gång en K/N-actual anropar en Kotlin-konstruktor som speglar en failable ObjC/Swift-initializer, fånga `NullPointerException` explicit runt konstruktoranropet — lita aldrig på en elvis efter konstruktorn.
- **Delade ViewModels får INTE stänga bootstrap-ägda singletons i `onCleared()`** (i2c-slutreview, 2026-07-27). `ScanViewModel.onCleared()` kallade `classifier.close()`, men `classifier` kommer från `AppGraph.scanViewModel()` som ger `(classifierBootstrap.state.value as? Ready)?.classifier` — den app-livstids-singleton som `ClassifierBootstrap` äger, inte något VM:en själv skapade. i2c gjorde Scan till en push:ad nav-destination, så varje back-out clear:ar VM:en → stängde singletonen på FÖRSTA back-out → brickade `classify()` permanent (runnerns `check(!closed)`-guard) för både live-scan och galleri-foto-ID tills app-omstart. Fixad i `602caf1b`: VM:n stänger aldrig klassificeraren; bara sina egna per-VM-resurser (`cameraSource.stop()` är fortfarande korrekt eftersom varje `ScanViewModel` äger sin egen `CameraSource`-instans). **Mönstret är latent i Android-prod också** (samma `onCleared`-kod, samma `AppGraph`-wiring) — inte tidigare upptäckt eftersom Android-Scan sannolikt inte push:ats/clear:ats på samma sätt i testad väg; verifiera scan-återinträde på Galaxy vid nästa device-tillfälle. **Regel:** en VM får bara stänga resurser den själv instansierade via sin egen factory — allt som kommer in via `AppGraph`/bootstrap som en delad singleton är INTE VM:ens att stänga.
- **Suspend-`finalize`/`cleanup` får ALDRIG anropas inline från en coroutine den själv cancellar** (recensions-batchen T5/L1, 2026-08-07). Auto-stoppets `finalize`-anrop kördes som barn till sin egen `inferenceJob` och cancellade sin förälder-coroutine när den slutförde → `CancellationException` svaldes tyst av ett omslutande `runCatching` → state fastnade permanent i `Analyzing` (ingen krasch, inget resultat, ingen retry-väg synlig för användaren). Fixad i `74c48d64`. **Regel:** terminala state-övergångar (finalize/cleanup/close) lanseras alltid på ägar-scopen (t.ex. `viewModelScope.launch { ... }`), aldrig som ett anrop inifrån den coroutine de själva avslutar — och varje `runCatching` som omsluter ett suspend-anrop måste rethrowa `CancellationException`, annars äter den tyst cancellation-signaler den inte äger.
- **Tysta fejk-fallbacks i produktion är recensions-gift** (recensions-batchen T11, 2026-08-07). `AudioClassifierFactory` svarade en hårdkodad "Koltrast 92%" på ALL audio när den riktiga modellen inte kunde laddas (samma klass av 16 KB-enhets-/GC-bugg som i2a:s classifier-first-run-fynd) — helt tyst, ingen banner, ingen logg; användaren fick ett falskt positivt resultat och ingen anledning att misstänka att något var fel. Fixad i `167042bf` + `347444f0`: produktion visar nu ett lokaliserat felstate + retry och degrade-orsaken loggas, medan en synlig DEMO-banner gate:ad på `BuildConfig.DEBUG` markerar när fejk-data faktiskt används avsiktligt. **Regel:** fejk/DEMO-fallbacks gate:as ALLTID på `BuildConfig.DEBUG` — produktion visar aldrig en tyst påhittad utdata; en degradering ska alltid synas (state + logg) även när den känns "harmless" i det enskilda fallet.
- **K/N cinterop: opaka C-struct-typer resolvar under `cnames.structs.*`, INTE cinterop-paketet** (i3 T3, 2026-08-16). När en forward-deklarerad C-struct utan synlig layout (t.ex. TFLite:s handle-typer `TfLiteModel`/`TfLiteInterpreter`) ska namnges explicit i Kotlin — t.ex. som fälttyp — finns typen INTE i ditt cinterop-paket (`tflitec.TfLiteModel` failar trots att namnet syns i klib-metadatan); K/N lägger alla opaka struct-typer i det syntetiska paketet `cnames.structs`. Importera därifrån: `import cnames.structs.TfLiteModel`.
- **`NSLog("%@", kotlinString)` kraschar i K/N** (i4 T4, 2026-08-16). Vararg-marshaling av en rå Kotlin `String` till en ObjC-vararg (`NSLog`s `format, ...`) ger en ogiltig objektpekare → `EXC_BAD_ACCESS` djupt inne i `CFStringCreateWithFormat` (bevisat i i4 T4-verifyn via crash-report + klib-dump-metadata — inte en teori). **Regel:** använd ALLTID 1-args-formen `NSLog(msg)`; om `msg` interpolerar fri text som kan innehålla `%`, escapa själv (`msg.replace("%", "%%")`) innan anropet. Gäller alla C-vararg-ObjC-funktioner anropade från Kotlin, inte bara `NSLog`. Träffade tre sajter i i4 (inkl. en latent i `IosAppGraph.onDegrade` som fanns sedan i3 och annars hade sänkt i3:s sim-check) — grep efter `NSLog(` med mer än ett argument vid varje ny iOS-actual.
- **K/N-bindningar kan göra ObjC-override-punkter `final`, trots att Swift kan subklassa dem** (i4 T4-riskgrind, 2026-08-16). `MKTileOverlay`s båda tile-load-metoder (`loadTileAtPath`/`URLForTilePath`) är `final` i K/N 2.1.20:s plattformsbindning av MapKit, så en Kotlin-subklass kan inte överrida dem — trots att Apples egen dokumentation (och Swift-communityn) visar precis den subklassningen som standardmönster. Diagnos: `klib dump-metadata` på platform-klib:en (t.ex. `~/.konan/kotlin-native-prebuilt-<host>/klib/platform/ios_simulator_arm64/platform.MapKit/...`) visar `final` på metoden direkt i signaturen — snabbare att verifiera än att gissa utifrån ett kompileringsfel. Husmönster när det händer: en minimal Swift-shim äger subklassen och vidarebefordrar varje anrop till ett Kotlin-object med all den faktiska logiken (se `BirdyTileOverlay.swift` → `IosTileFetcher`), factory-registrerad in i Kotlin-sidan via ett bridge-object exponerat från `iOSApp.swift`. Samma mönster återanvändbart för nästa K/N↔UIKit/MapKit-yta som visar sig vara oöverridbar.
- **Filer som flyttas mellan källmängder ger falskt resultat i inkrementella byggen** (1.3.0, 2026-10-07). När benchmarkbilderna flyttades från `composeApp/src/androidMain/assets` till `androidDebug/assets` fanns de kvar i ett worktree:s gamla `build/intermediates/assets` (testet grönt fast det föll på en ren checkout) och saknades i ett annat (återanvänd konfigurationscache, `benchmark/` tom även i debug-APK:n). **Regel:** efter en merge som flyttar/lägger till assets eller källmängder: `rm -rf composeApp/build/intermediates/assets composeApp/build/intermediates/library_assets androidApp/build/intermediates/assets` och kör grinden med `--no-configuration-cache`; pusha aldrig utan att ha kontrollerat grindens exit-kod. Unit-tester som behöver en fil ska läsa den från disk, inte via Robolectrics AssetManager.
- **`git worktree remove --force` raderar gitignorerade filer utan varning** (2026-10-10). De renderade videorna i `tools/social/out/` (gitignorerat) försvann med worktreen `C:/w/birdy-social` och fick renderas om. **Regel:** kör `git status --ignored --short` i worktreen innan den tas bort och flytta det som behövs (renderingar, rapporter, `reports/STOP`) till huvudklonen först.
- **En Vercel-förhandsvisning kan visa förra versionen utan att det syns** (2026-10-10). Bygget av `b3e7ee4f` på `website/galleri` föll på "Git information retrieval failed for this deployment" (Vercels sida, inte koden), så grenens adress visade fortfarande förra lyckade bygget och Albin såg inget av ändringarna. **Regel:** innan en förhandsvisningslänk skickas, kontrollera `gh api repos/anonadrek/birdy/commits/<sha>/status` (Vercel ska vara success) och leta efter en markör som bara finns i nya versionen; vid fel bygger en tom commit om. **Samma fel slår också mot produktion** (10 okt 10.35: `3758c402` föll direkt och sajten låg kvar på förra versionen utan att något syntes): kontrollera produktionsdeployen efter varje push till `main` (`gh api repos/anonadrek/birdy/deployments` + statuses) och bygg om med deploy hooken, `gh workflow run daily-site-build.yml --ref main` (ingen tom commit behövs). **Polla inte birdy.community med curl i en tät loop:** efter ett 40-tal anrop på sju minuter svarade Vercel 403 "Security Checkpoint" till maskinens IP (webbläsare klarar utmaningen); kontrollera live i Chrome eller via deployens status.
