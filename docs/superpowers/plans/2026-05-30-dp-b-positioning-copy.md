# DP B — Positionering & copy: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Birdy's purpose ("identify the species, then keep it in a private field journal") legible across the app, store and website — addressing the hardcore tester's "I can't understand what the app is for."

**Architecture:** Pure copy changes in two `strings.xml` files (SV `values/`, EN `values-en/`), JSON copy decks (`copy.{en,sv}.json`), and store markdown — plus one visual swap in `SceneHero.kt` (the duplicated lower wordmark "Birdy" → standalone copper bird). No layout, flow, or feature changes. Two phases: Fas 1 (app, device-verify-gated), Fas 2 (store + website, no device-verify).

**Tech Stack:** Kotlin Multiplatform + Compose Multiplatform (compose-resources for strings), Astro/JSON i18n decks, Playwright tests, Gradle.

**Spec:** `docs/superpowers/specs/2026-05-30-dp-b-positioning-copy-design.md` (commit `958374c`).

**Cross-cutting rules (apply to every task):**
- SV strings go in `composeApp/src/commonMain/composeResources/values/strings.xml`; EN in `…/values-en/strings.xml`. Nav/breadcrumb changes touch **only SV** (EN already English).
- **Never** state accuracy numbers (no "72%", no "~72") anywhere in copy/store/website. Fas 2 actively removes an existing leak.
- Field Journal theme is locked: DM Serif Display italic + Caveat accent + copper `#A8552D`; `JournalHeadline` accent uses `*word*`.
- Use raw `'` / `'` (U+2019), never `\'`. None of DP B's new strings contain `%` or apostrophes — keep it that way.
- Edits match on **exact current string content** (shown per task), not line numbers — line numbers in this repo's `strings.xml` drift.

**Bash prefix for Gradle (Windows):**
```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
```

---

## Task 0: Create feature branch

**Files:** none (git only)

- [ ] **Step 1: Verify clean tree on main**

Run: `git -C "C:/Users/abbea/dev/birdy-bird-scanner" status -s`
Expected: empty (no output). If dirty, stop and surface.

- [ ] **Step 2: Create + switch to branch**

```bash
git checkout -b feat/dp-b-positioning
```

- [ ] **Step 3: Confirm branch**

Run: `git branch --show-current`
Expected: `feat/dp-b-positioning`

---

# FAS 1 — App (device-verify-gated)

## Task 1: First screen copy (SV + EN)

The screen a Skip-user lands on (`ListenLauncherScreen`). Keep label, headline, card titles, audio body. Change only the sub-line + camera/photo bodies so every card states the function.

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`

- [ ] **Step 1: Edit SV sub-line**

In `values/strings.xml`, replace:
```xml
    <string name="listen_journal_sub">En stämpel väntar i varje.</string>
```
with:
```xml
    <string name="listen_journal_sub">Kamera, foto eller läte — arten dyker upp i din fältbok.</string>
```

- [ ] **Step 2: Edit SV camera + photo bodies**

In `values/strings.xml`, replace:
```xml
    <string name="listen_card_camera_body">Realtidsskanning via kameran.</string>
```
with:
```xml
    <string name="listen_card_camera_body">Identifiera live genom kameran.</string>
```
and replace:
```xml
    <string name="listen_card_photo_body">Välj foto från galleri eller ta nytt.</string>
```
with:
```xml
    <string name="listen_card_photo_body">Identifiera från ett foto i galleriet.</string>
```

- [ ] **Step 3: Edit EN sub-line**

In `values-en/strings.xml`, replace:
```xml
    <string name="listen_journal_sub">A stamp waits in each.</string>
```
with:
```xml
    <string name="listen_journal_sub">Camera, photo or call — the bird turns up in your field book.</string>
```

- [ ] **Step 4: Edit EN camera + photo bodies**

In `values-en/strings.xml`, replace:
```xml
    <string name="listen_card_camera_body">Real-time camera scanning.</string>
```
with:
```xml
    <string name="listen_card_camera_body">Identify live through the camera.</string>
```
and replace:
```xml
    <string name="listen_card_photo_body">Pick from gallery or take new.</string>
```
with:
```xml
    <string name="listen_card_photo_body">Identify from a photo in your gallery.</string>
```

- [ ] **Step 5: Verify no accidental sibling changes**

Run: `git diff --stat`
Expected: only the two `strings.xml` files changed.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(copy): DP B — första-skärmen säger identifiera/arten/fältbok (SV+EN)"
```

---

## Task 2: Hero eyebrow copy (SV + EN)

`SceneHero` / onboarding scene 1. Keep headline `*Birdy.*` + sub (already the one straight function sentence). Change only the eyebrow.

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`

- [ ] **Step 1: Edit SV eyebrow**

In `values/strings.xml`, replace:
```xml
    <string name="onboarding_s1_eyebrow">FÄLT-FÖLJESLAGARE · NO 1</string>
```
with:
```xml
    <string name="onboarding_s1_eyebrow">FÅGEL-ID + FÄLTDAGBOK · NO 1</string>
```

- [ ] **Step 2: Edit EN eyebrow**

In `values-en/strings.xml`, replace:
```xml
    <string name="onboarding_s1_eyebrow">FIELD COMPANION · NO 1</string>
```
with:
```xml
    <string name="onboarding_s1_eyebrow">BIRD ID + FIELD JOURNAL · NO 1</string>
```

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(copy): DP B — hero-eyebrow säger fågel-ID + fältdagbok (SV+EN)"
```

---

## Task 3: Hero image swap — standalone copper bird replaces duplicated wordmark

The lower `wordmark.png` renders "Birdy." a second time, duplicating the Caveat headline at the top. Replace it with the standalone copper bird (the app-icon foreground).

**Files:**
- Create: `composeApp/src/commonMain/composeResources/files/branding/hero_bird.png` (copy of `androidApp/src/main/res/drawable-nodpi/ic_launcher_foreground.png`)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneHero.kt`

- [ ] **Step 1: Copy the bird asset**

```bash
cp androidApp/src/main/res/drawable-nodpi/ic_launcher_foreground.png \
   composeApp/src/commonMain/composeResources/files/branding/hero_bird.png
```

- [ ] **Step 2: Verify the asset exists + is a PNG**

Run: `file composeApp/src/commonMain/composeResources/files/branding/hero_bird.png`
Expected: `PNG image data, 432 x 432, 8-bit/color RGBA, non-interlaced`

- [ ] **Step 3: Point SceneHero at the new asset + fix contentDescription**

In `SceneHero.kt`, the `AsyncImage` block currently reads:
```kotlin
        AsyncImage(
            model = Res.getUri("files/branding/wordmark.png"),
            contentDescription = "Birdy",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 64.dp),
            contentScale = ContentScale.Fit,
        )
```
Replace it with:
```kotlin
        AsyncImage(
            model = Res.getUri("files/branding/hero_bird.png"),
            contentDescription = "Birdy",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 96.dp),
            contentScale = ContentScale.Fit,
        )
```
(Bird is square 432×432 vs the wide wordmark; tighter `horizontal = 96.dp` keeps it from dominating. `contentDescription = "Birdy"` stays — it labels the brand visual.)

- [ ] **Step 4: Confirm no stray wordmark reference remains in SceneHero**

Run: `grep -n "wordmark" composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneHero.kt`
Expected: no output (empty).

> Note: `wordmark.png` stays in the repo — it is still used by the splash/branding elsewhere. We only stopped using it in `SceneHero`.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/composeResources/files/branding/hero_bird.png composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneHero.kt
git commit -m "feat(onboarding): hero visar fristående fågel istället för dubblerad wordmark"
```

---

## Task 4: Onboarding scene 5 (Märken) sub copy (SV + EN)

Soften the badges scene so it reads as real birder milestones, not a streak grind. Keep order + headline.

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`

- [ ] **Step 1: Edit SV sub**

In `values/strings.xml`, replace:
```xml
    <string name="onboarding_s5_sub">Mängder av milstolpar att jaga. Håll svit levande.</string>
```
with:
```xml
    <string name="onboarding_s5_sub">Livslista, familjer, rariteter — milstolpar värda att nå.</string>
```

- [ ] **Step 2: Edit EN sub**

In `values-en/strings.xml`, replace:
```xml
    <string name="onboarding_s5_sub">Plenty of milestones to chase. Keep your streak alive.</string>
```
with:
```xml
    <string name="onboarding_s5_sub">Life list, families, rarities — milestones worth reaching.</string>
```

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(copy): DP B — onboarding scen 5 säljer skådar-milstolpar, inte streaks (SV+EN)"
```

---

## Task 5: Nav tabs + breadcrumbs (SV only)

Three of four SV tabs are English words in the wrong file. EN file is already correct — **do not touch it**.

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` (SV only)

- [ ] **Step 1: Edit the three SV tab labels**

In `values/strings.xml`, replace:
```xml
    <string name="tab_archive">Archive</string>
```
with:
```xml
    <string name="tab_archive">Uppslagsverk</string>
```
replace:
```xml
    <string name="tab_lifelist">Lifelist</string>
```
with:
```xml
    <string name="tab_lifelist">Mina arter</string>
```
replace:
```xml
    <string name="tab_badges">Badges</string>
```
with:
```xml
    <string name="tab_badges">Märken</string>
```

- [ ] **Step 2: Edit the two SV breadcrumbs**

In `values/strings.xml`, replace:
```xml
    <string name="archive_breadcrumb">ARCHIVE</string>
```
with:
```xml
    <string name="archive_breadcrumb">UPPSLAGSVERK</string>
```
replace:
```xml
    <string name="lifelist_breadcrumb">LIFELIST</string>
```
with:
```xml
    <string name="lifelist_breadcrumb">MINA ARTER</string>
```

- [ ] **Step 3: Confirm EN nav untouched**

Run: `git diff --name-only`
Expected: only `composeApp/src/commonMain/composeResources/values/strings.xml`.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(copy): DP B — översätt SV nav-flikar + breadcrumbs (Uppslagsverk/Mina arter/Märken)"
```

---

## Task 6: App build + lint + unit-test gate

**Files:** none (verification)

- [ ] **Step 1: Unit tests (compose module)**

Run (with Gradle bash prefix):
```bash
./gradlew :composeApp:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`. (Confirms compose-resources still generate; new `hero_bird` URI resolves at resource-gen time.)

- [ ] **Step 2: Lint + static analysis**

Run:
```bash
./gradlew ktlintCheck detekt
```
Expected: `BUILD SUCCESSFUL`. If ktlint flags `SceneHero.kt`, run `./gradlew ktlintFormat`, re-run, and commit the format fix with message `style: ktlintFormat DP B hero-swap`.

- [ ] **Step 3: Confirm no accuracy number entered app copy**

Run: `grep -rnE "72|accuracy|träffsäker" composeApp/src/commonMain/composeResources/values*/strings.xml`
Expected: no output.

- [ ] **Step 4: Commit (only if ktlintFormat changed files in Step 2; otherwise skip)**

---

## Task 7: Device-verify on SM-S918B + screenshots

**Files:** Create screenshots under `docs/superpowers/screenshots/`

> Personal-device hazard: SM-S918B is Albin's daily phone. Ask for "hands off" before ADB-driving; verify via screencap; delete any private content captured. (memory: personal-device-verify-hazard)

- [ ] **Step 1: Build + install debug**

Run:
```bash
./gradlew :androidApp:installDebug
```
Expected: `BUILD SUCCESSFUL`. (Debug package is `se.birdy.android.debug` — memory: onboarding-v2-status.)

- [ ] **Step 2: Launch app**

Run:
```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android.debug/se.birdy.android.MainActivity
```
(If the activity path errors, resolve with `adb shell cmd package resolve-activity --brief se.birdy.android.debug`.)

- [ ] **Step 3: Capture first screen (SV)**

Confirm device language is Swedish. Screencap the launcher: new sub-line + all three card bodies say "Identifiera…". Save `docs/superpowers/screenshots/dp-b-01-first-screen-sv.png`.

- [ ] **Step 4: Capture hero scene**

Trigger onboarding replay (Settings → "Visa introduktion igen", per memory onboarding-v2-status) and screencap scene 1: eyebrow "FÅGEL-ID + FÄLTDAGBOK", headline "Birdy.", standalone copper bird below (NOT the wordmark, no duplicated "Birdy"). Save `dp-b-02-hero.png`.

- [ ] **Step 5: Capture onboarding scene 5**

Scroll to scene 5; screencap the softened sub-line. Save `dp-b-03-onboarding-badges.png`.

- [ ] **Step 6: Capture nav tabs + a breadcrumb (SV)**

Screencap the bottom nav showing "Identifiera / Uppslagsverk / Mina arter / Märken", and one of the Archive/Lifelist screens showing the translated breadcrumb. Save `dp-b-04-nav-sv.png` and `dp-b-05-breadcrumb-sv.png`.

- [ ] **Step 7: Commit screenshots**

```bash
git add docs/superpowers/screenshots/dp-b-*.png
git commit -m "docs(screenshots): DP B Fas 1 device-verify på SM-S918B"
```

---

# FAS 2 — Store + website (no device-verify)

## Task 8: Play Store listing (SV + EN)

**Files:**
- Modify: `docs/play-store/store-listing-sv.md`
- Modify: `docs/play-store/store-listing-en.md`

- [ ] **Step 1: SV short description**

In `store-listing-sv.md`, under `## Kort beskrivning (max 80 tecken)`, replace:
```
Skanna fåglar med kameran, lär dig om dem, samla i din fältbok.
```
with:
```
Identifiera fåglar med kamera & ljud. Behåll varje fynd i en fältbok som är din.
```

- [ ] **Step 2: Verify SV short ≤ 80 chars (code-point count, not bytes)**

`wc -m` miscounts here (it counts UTF-8 bytes for å/ä/ö/— in a non-UTF-8 shell). Use code-point counting, which is what Play Console measures:
```bash
node -e "console.log([...'Identifiera fåglar med kamera & ljud. Behåll varje fynd i en fältbok som är din.'].length)"
```
Expected: `80` (exactly at the Play limit). If Play Console later rejects it, fall back to `Identifiera fåglar med kamera & ljud. Behåll dina fynd i en fältbok som är din.` = 79 code points.

- [ ] **Step 3: SV long description first paragraph**

In `store-listing-sv.md`, replace the opening paragraph:
```
Birdy är en AI-driven fältbok för fågelskådare — gjord för dig som
vill **lära dig** känna igen fåglar i fält, **samla** dina fynd, och
**växa** som fältornitolog.
```
with:
```
Birdy är den vackra, privata fältdagboken för fågelskådare.
Identifiera fåglar med kamera eller ljud — offline, utan konto — och
behåll varje fynd som ett uppslag du äger och vill bläddra tillbaka till.
```

- [ ] **Step 4: EN short description**

In `store-listing-en.md`, under `## Short description (max 80 chars)`, replace:
```
Identify birds by camera, learn about them, collect them in your journal.
```
with:
```
Identify birds by camera & sound. Keep every find in a field book that's yours.
```

- [ ] **Step 5: EN long description first paragraph**

In `store-listing-en.md`, replace the opening paragraph:
```
Birdy is an AI-powered field journal for birders — built for people who
want to **learn** to recognise birds in the wild, **collect** their
sightings, and **grow** as field birders.
```
with:
```
Birdy is the beautiful, private field journal for birders. Identify
birds with your camera or sound — offline, no account — and keep every
find as a page you own and want to flip back to.
```

- [ ] **Step 6: Confirm no accuracy number in store docs**

Run: `grep -nE "72|accuracy|träffsäker" docs/play-store/store-listing-*.md`
Expected: no output.

- [ ] **Step 7: Commit**

```bash
git add docs/play-store/store-listing-sv.md docs/play-store/store-listing-en.md
git commit -m "docs(store): DP B — keep-it-positionering i kort+lång beskrivning (SV+EN)"
```

---

## Task 9: Website hero sub (EN + SV)

**Files:**
- Modify: `website/src/content/copy.en.json`
- Modify: `website/src/content/copy.sv.json`

- [ ] **Step 1: EN hero.sub**

In `copy.en.json`, replace:
```json
    "sub": "Identify birds with your camera. Keep what you see. Earn the stamps.",
```
with:
```json
    "sub": "Identify birds with your camera or mic. Keep every find in a journal that's yours.",
```
(This is inside the `"hero"` object — confirm by the preceding `"headline": "A *field journal*…"`.)

- [ ] **Step 2: SV hero.sub**

In `copy.sv.json`, replace:
```json
    "sub": "Identifiera fåglar med kameran. Spara det du ser. Samla stämplarna.",
```
with:
```json
    "sub": "Identifiera fåglar med kamera eller ljud. Behåll varje fynd i en dagbok som är din.",
```

- [ ] **Step 3: Validate JSON parses (both)**

Run:
```bash
cd website && node -e "JSON.parse(require('fs').readFileSync('src/content/copy.en.json','utf8')); JSON.parse(require('fs').readFileSync('src/content/copy.sv.json','utf8')); console.log('OK')"
```
Expected: `OK`.

- [ ] **Step 4: Commit**

```bash
git add website/src/content/copy.en.json website/src/content/copy.sv.json
git commit -m "feat(website): DP B — hero-sub keep-it, ta bort Earn the stamps (EN+SV)"
```

---

## Task 10: Website FAQ — add vs-Merlin item + remove accuracy leak (EN + SV)

The i18n-parity test compares key structure (including array indices), so the new FAQ item must be added to **both** decks. The accuracy answer leaks "~72%" — replace it in both.

**Files:**
- Modify: `website/src/content/copy.en.json`
- Modify: `website/src/content/copy.sv.json`
- Create: `website/scripts/check-no-accuracy.mjs`
- Modify: `website/package.json` (add `test:no-accuracy` script)

> Why a node script, not a Playwright spec: Playwright's config boots a `preview` webServer (needs a prior `npm run build`), so a `.spec.ts` would hang on server startup mid-task. A standalone node script matches the existing `check-i18n-parity.mjs` convention — fast, no server, just reads the JSON.

- [ ] **Step 1: Write the failing guard script**

Create `website/scripts/check-no-accuracy.mjs`:
```js
#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

// Cross-cutting rule (DP B): never communicate accuracy numbers.
// Guards both copy decks against the "~72%" regression.
const __dirname = dirname(fileURLToPath(import.meta.url));
const root = resolve(__dirname, '..');
const banned = [/\b72\b/, /accuracy/i, /träffsäker/i];

let failed = false;
for (const lang of ['en', 'sv']) {
  const raw = readFileSync(resolve(root, `src/content/copy.${lang}.json`), 'utf8');
  for (const re of banned) {
    if (re.test(raw)) {
      console.error(`accuracy-guard FAILED: copy.${lang}.json matches ${re}`);
      failed = true;
    }
  }
}
if (failed) process.exit(1);
console.log('accuracy-guard OK (no accuracy number in either deck)');
```

- [ ] **Step 2: Add the npm script**

In `website/package.json`, in `"scripts"`, add after the `"test:i18n"` line:
```json
    "test:no-accuracy": "node scripts/check-no-accuracy.mjs",
```
(Remember to add the trailing comma to the previous line if needed so the JSON stays valid.)

- [ ] **Step 3: Run it — expect FAIL (leak still present)**

Run: `cd website && npm run test:no-accuracy`
Expected: exit 1, `accuracy-guard FAILED` for both decks (matches "72" / "accuracy" / "träffsäker").

- [ ] **Step 3: Fix EN accuracy answer**

In `copy.en.json`, replace:
```json
      { "q": "How accurate is the AI?", "a": "Top-3 accuracy ~72% on European species. Field-verified on a Galaxy S23 Ultra. Confidence shown for every match." },
```
with:
```json
      { "q": "How accurate is the AI?", "a": "Good enough to learn from, honest when it's unsure. Every match shows a confidence level, and you always confirm before it's saved." },
```

- [ ] **Step 4: Fix SV accuracy answer**

In `copy.sv.json`, replace:
```json
      { "q": "Hur exakt är AI:n?", "a": "Top-3-träffsäkerhet ~72% på europeiska arter. Fältverifierat på en Galaxy S23 Ultra. Säkerhet visas för varje träff." },
```
with:
```json
      { "q": "Hur exakt är AI:n?", "a": "Tillräckligt bra för att lära sig av, ärlig när den är osäker. Varje träff visar en säkerhetsnivå, och du bekräftar alltid innan något sparas." },
```

- [ ] **Step 5: Run guard — expect PASS**

Run: `cd website && npm run test:no-accuracy`
Expected: `accuracy-guard OK` (exit 0).

- [ ] **Step 6: Add vs-Merlin item as first FAQ item — EN**

In `copy.en.json`, the `"faq"` object has `"items": [` followed by `{ "q": "Does it really work offline?" …`. Insert a new first item so it reads:
```json
    "items": [
      { "q": "How is this different from Merlin?", "a": "Merlin is brilliant at identifying birds — use it, it's great. Birdy is about what happens after: keeping each find in a field journal that's yours, offline, with no account. Many birders use both." },
      { "q": "Does it really work offline?", "a": "Yes. The AI runs entirely on your phone — both for camera and audio. No data leaves the device." },
```

- [ ] **Step 7: Add vs-Merlin item as first FAQ item — SV**

In `copy.sv.json`, insert the new first item so it reads:
```json
    "items": [
      { "q": "Hur skiljer sig Birdy från Merlin?", "a": "Merlin är fantastiskt på att identifiera fåglar — använd det, det är bra. Birdy handlar om vad som händer efter: att behålla varje fynd i en fältdagbok som är din, offline, utan konto. Många skådare använder båda." },
      { "q": "Funkar den verkligen offline?", "a": "Ja. AI:n körs helt på din telefon — både för kamera och ljud. Ingen data lämnar enheten." },
```

- [ ] **Step 8: Run i18n parity + guard together**

Run: `cd website && npm run test:i18n && npm run test:no-accuracy`
Expected: `i18n parity OK (N keys)` then `accuracy-guard OK`. Parity holds because the new FAQ item was added to **both** decks, so `faq.items[len=…]` matches.

- [ ] **Step 9: Commit**

```bash
git add website/src/content/copy.en.json website/src/content/copy.sv.json website/scripts/check-no-accuracy.mjs website/package.json
git commit -m "feat(website): DP B — vs-Merlin-FAQ + ta bort accuracy-siffra + guard-script (EN+SV)"
```

---

## Task 11: Website premium body word (SV only)

"obsessiva samlare" reads negatively in Swedish. EN "obsessive collectors" stays.

**Files:**
- Modify: `website/src/content/copy.sv.json`

- [ ] **Step 1: Edit SV premium.body**

In `copy.sv.json`, replace:
```json
    "body": "Appen är gratis. Kärnupplevelsen kommer alltid att vara det. Premium lägger till PDF-export, säsongsstatistik och 10 fältmärken för obsessiva samlare.",
```
with:
```json
    "body": "Appen är gratis. Kärnupplevelsen kommer alltid att vara det. Premium lägger till PDF-export, säsongsstatistik och 10 fältmärken för hängivna samlare.",
```

- [ ] **Step 2: Validate JSON parses**

Run:
```bash
cd website && node -e "JSON.parse(require('fs').readFileSync('src/content/copy.sv.json','utf8')); console.log('OK')"
```
Expected: `OK`.

- [ ] **Step 3: Commit**

```bash
git add website/src/content/copy.sv.json
git commit -m "feat(website): DP B — premium-copy 'obsessiva' → 'hängivna samlare' (SV)"
```

---

## Task 12: Website build + full test gate

**Files:** none (verification)

- [ ] **Step 1: Build**

Run: `cd website && npm run build`
Expected: build completes, no errors.

- [ ] **Step 2: i18n parity**

Run: `cd website && npm run test:i18n`
Expected: PASS.

- [ ] **Step 3: Smoke tests**

Run: `cd website && npm run test:smoke`
Expected: PASS (7 tests). (Boots a `preview` server — Step 1's build must have run first.)

- [ ] **Step 4: Accuracy guard**

Run: `cd website && npm run test:no-accuracy`
Expected: `accuracy-guard OK`.

- [ ] **Step 5: Final repo-wide accuracy sweep**

Run: `grep -rnE "~?72%|72 ?%" website/src docs/play-store composeApp/src/commonMain/composeResources/values*/strings.xml`
Expected: no output.

---

## Finishing

After all tasks: use superpowers:finishing-a-development-branch to decide merge/PR. Note in the merge summary that Fas 2 (store + website) lands copy in the repo, but the **Play Console upload of the new store text is a manual step** (not done by this plan), and the website auto-deploys via Vercel on merge to `main`.

---

## Self-Review (completed by author)

**Spec coverage:**
- 1a first screen → Task 1 ✓
- 1b hero eyebrow + image swap → Tasks 2 + 3 ✓
- 1c onboarding scene 5 → Task 4 ✓
- 1d nav + breadcrumbs (SV only) → Task 5 ✓
- 2a store listing → Task 8 ✓
- 2b website hero → Task 9 ✓
- 2c vs-Merlin FAQ → Task 10 ✓
- 2d accuracy leak → Task 10 (+ guard test) ✓
- 2e premium word → Task 11 ✓
- Acceptance criteria 1–9 → Tasks 6, 7, 10, 12 ✓

**Placeholder scan:** No TBD/TODO; every edit shows exact before/after strings.

**Consistency:** Asset name `hero_bird.png` used identically in Task 3 steps 1, 2, 3, 4 and the "files touched" list. String keys match the spec and the verified current file contents. The vs-Merlin FAQ is added to both decks (i18n parity preserved). The accuracy guard (`scripts/check-no-accuracy.mjs`, a node script matching the existing `check-i18n-parity.mjs` convention — not a Playwright spec, which would boot a preview server) is written before the fix (TDD red→green).

**Grounding corrections (vs first draft):** EN scene-5 sub current value is "Keep **your** streak alive" (not "the"); SV short-description verified at 80 code points via `node`, not `wc -m` (which counts bytes); `test:i18n` is a node script, accuracy guard is now also a node script (not Playwright). All exact-match strings re-verified against working tree at commit `958374c`.
