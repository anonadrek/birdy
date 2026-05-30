# DP B — Positionering & copy: design

**Datum:** 2026-05-30
**Delprojekt:** 2 av v1.x-tester-feedback-programmet (program-spec `docs/superpowers/specs/2026-05-29-v1-x-tester-feedback-response-design.md` §5)
**Föregående:** DP A (sök-fix) klar + mergad. Kontext + öppna beslut: `docs/superpowers/research/2026-05-30-dp-b-copy-kickoff.md`.
**Status:** Design låst i brainstorm 2026-05-30 (visuella mockups för första-skärm + hero via visual companion).

---

## Problem

Hardcore-testare (erfaren skådare): *"I can't understand what the app is for."* Appen kommunicerar **stämning** (vacker fältdagbok) i stället för **jobb-att-utföras** (identifiera arten), och uttalar ingen krok mot Merlin. Funktionen är begravd under poetisk copy; entusiasten ser gamification → "leksak", nybörjaren ser "FÄLT-FÖLJESLAGARE" → exkluderad. Full analys: `docs/superpowers/research/2026-05-29-hardcore-tester-feedback.md` (Problem 4).

## Låst riktning

**"Keep it, not just ID it."** Birdy = den vackra, privata fältdagboken där fyndet blir något du äger och återbesöker. Funktionen (identifiera arten — kamera/ljud, offline) ska möta användaren på varje arbetsskärm; differentieringen (behåll fyndet) bär positioneringen. Smalna mot entusiasten/seriösa hobbyisten. Uttala kroken mot Merlin ("Merlin är bäst på ID; Birdy handlar om vad som händer efter").

## Princip för ingreppen

**Minimala ändringar.** Endast text + en bild-swap. Ingen layout-, flödes- eller komponent-ändring. Inga nya features. Behåll den befintliga (gillade) designen exakt — vi byter ord, inte utseende.

## Cross-cutting bivillkor (program-spec §3)

- Alla UI-strängar via compose-resources i **BÅDA** `strings.xml` (`values/` = SV default, `values-en/` = EN) — utom nav/breadcrumbs som avsiktligt bara rör SV-filen (EN är redan engelsk).
- **Kommunicera ALDRIG accuracy-siffror** (72% top-3) i copy/store/website. *(DP B åtgärdar dessutom en befintlig läcka — se 2d.)*
- Tema låst mot publicerade tokens: DM Serif Display italic + Caveat-accent `rotate(-3deg)` + copper `#A8552D`. `JournalHeadline`-accent = `*ord*`.
- `%`-escape: förformatterat från Kotlin; raw `'`/`’` (U+2019), aldrig `\'`. (Ingen av DP B:s strängar innehåller `%` eller apostrof, men regeln gäller.)
- Inget audio bakom Premium (BirdNET CC BY-NC-SA) — ej berört av DP B men hålls i minnet.

## Sekvens (en spec, en plan, två faser)

- **Fas 1 — App** (kräver device-verify-gate på SM-S918B). Strings ×2 + en kod-swap i `SceneHero`.
- **Fas 2 — Store + website** (ingen device-verify; store laddas manuellt i Play Console, website auto-deployas via Vercel).

Positioneringen härleds en gång och propageras till båda faserna — en enda källa.

---

## FAS 1 — App

### 1a. Första-skärmen (`ListenLauncherScreen`)

Skärmen Skip-användaren landar direkt på. **Behåll:** label "FÅNGA · NO 0", headline "Tre sätt att *fånga.*", kort-titlar (Kika / Leta upp / Lyssna), ljud-kortets body, all layout/ikoner. **Ändra endast:**

| Nyckel | SV (`values/strings.xml`) | EN (`values-en/strings.xml`) |
|---|---|---|
| `listen_journal_sub` | `Kamera, foto eller läte — arten dyker upp i din fältbok.` | `Camera, photo or call — the bird turns up in your field book.` |
| `listen_card_camera_body` | `Identifiera live genom kameran.` | `Identify live through the camera.` |
| `listen_card_photo_body` | `Identifiera från ett foto i galleriet.` | `Identify from a photo in your gallery.` |

Effekt: ordet *arten* + *fältbok* + *identifiera* möter användaren direkt; alla tre kort säger funktionen (idag gör bara ljud-kortet det).

### 1b. Hero-scenen (`SceneHero` / onboarding scen 1)

**Behåll:** headline `*Birdy.*`, sub (`onboarding_s1_sub` — redan appens enda raka funktionsmening), all scaffold/animation.

**Ändra eyebrow:**

| Nyckel | SV | EN |
|---|---|---|
| `onboarding_s1_eyebrow` | `FÅGEL-ID + FÄLTDAGBOK · NO 1` | `BIRD ID + FIELD JOURNAL · NO 1` |

**Kod-swap (visuellt):** Den nedre `wordmark.png` (`AsyncImage` i `SceneHero`) ritar "Birdy." en andra gång — redundant mot Caveat-headline-rubriken högst upp. Ersätt wordmark-bilden med den **fristående koppar-fågeln** (samma fågel som app-ikonens foreground, `androidApp/.../drawable-nodpi/ic_launcher_foreground.png`). Fågeln ska in i `composeApp/src/commonMain/composeResources/files/branding/` (t.ex. `hero_bird.png`) och laddas via `Res.getUri(...)` på samma sätt som idag. Behåll `contentScale = Fit` + horisontell padding så proportionerna känns rätt; `contentDescription` byts från "Birdy" till t.ex. "Birdy-fågeln" / "Birdy bird".

> Detta är den enda icke-rena-copy-ändringen i DP B. Motiverad av att den fixar en faktisk dubblering ("Birdy" två gånger på samma skärm), beslutad visuellt i brainstorm.

### 1c. Onboarding scen 5 — Märken (`SceneBadges`)

**Behåll:** ordning (ingen omnumrering av `· NO N`-eyebrows), headline `*Förtjäna* märken`, all layout. **Mjuka endast subraden** så märken framställs som riktiga skådar-milstolpar, inte streak-grind:

| Nyckel | SV | EN |
|---|---|---|
| `onboarding_s5_sub` | `Livslista, familjer, rariteter — milstolpar värda att nå.` | `Life list, families, rarities — milestones worth reaching.` |

### 1d. Nav-flikar + breadcrumbs (endast SV-filen)

3 av 4 SV-flikar är engelska ord i fel fil. EN-filen är redan korrekt och **rörs ej**. `tab_listen` är redan "Identifiera"/"Identify".

| Nyckel | SV idag | SV ny |
|---|---|---|
| `tab_archive` | `Archive` | `Uppslagsverk` |
| `tab_lifelist` | `Lifelist` | `Mina arter` |
| `tab_badges` | `Badges` | `Märken` |
| `archive_breadcrumb` | `ARCHIVE` | `UPPSLAGSVERK` |
| `lifelist_breadcrumb` | `LIFELIST` | `MINA ARTER` |

Språk-separationen är redan automatisk (Android väljer `values/` vs `values-en/` efter enhetens språk); buggen var bara engelska ord i den svenska filen.

---

## FAS 2 — Store + website

### 2a. Play Store-listning (`docs/play-store/store-listing-{sv,en}.md`)

**Kort beskrivning (max 80 tecken):**

| | Ny |
|---|---|
| SV | `Identifiera fåglar med kamera & ljud. Behåll varje fynd i en fältbok som är din.` (79) |
| EN | `Identify birds by camera & sound. Keep every find in a field book that's yours.` (78) |

**Lång beskrivning — första stycket** (resten av SKANNA/LÄR/SAMLA-sektionerna oförändrade):

| | Nytt första stycke |
|---|---|
| SV | `Birdy är den vackra, privata fältdagboken för fågelskådare. Identifiera fåglar med kamera eller ljud — offline, utan konto — och behåll varje fynd som ett uppslag du äger och vill bläddra tillbaka till.` |
| EN | `Birdy is the beautiful, private field journal for birders. Identify birds with your camera or sound — offline, no account — and keep every find as a page you own and want to flip back to.` |

Ingen accuracy-siffra läggs till (finns ej i store-docs idag).

### 2b. Website hero (`website/src/content/copy.{en,sv}.json` → `hero.sub`)

Behåll `hero.headline` ("A *field journal*…" / "En *fältdagbok*…") + `hero.eyebrow`. Ta bort "Earn the stamps" / "Samla stämplarna" ur hero.

| | Ny `hero.sub` |
|---|---|
| EN | `Identify birds with your camera or mic. Keep every find in a journal that's yours.` |
| SV | `Identifiera fåglar med kamera eller ljud. Behåll varje fynd i en dagbok som är din.` |

### 2c. Website FAQ — ny "vs Merlin"-post (först i `faq.items`)

| | Q | A |
|---|---|---|
| EN | `How is this different from Merlin?` | `Merlin is brilliant at identifying birds — use it, it's great. Birdy is about what happens after: keeping each find in a field journal that's yours, offline, with no account. Many birders use both.` |
| SV | `Hur skiljer sig Birdy från Merlin?` | `Merlin är fantastiskt på att identifiera fåglar — använd det, det är bra. Birdy handlar om vad som händer efter: att behålla varje fynd i en fältdagbok som är din, offline, utan konto. Många skådare använder båda.` |

### 2d. Website FAQ — accuracy-läcka (cross-cutting-tvång)

FAQ:n läcker idag siffran "Top-3 accuracy ~72%" / "Top-3-träffsäkerhet ~72%" — bryter mot regeln att aldrig kommunicera accuracy-siffror. Behåll frågan, ta bort talet:

| | "How accurate is the AI?" / "Hur exakt är AI:n?" → ny `a` |
|---|---|
| EN | `Good enough to learn from, honest when it's unsure. Every match shows a confidence level, and you always confirm before it's saved.` |
| SV | `Tillräckligt bra för att lära sig av, ärlig när den är osäker. Varje träff visar en säkerhetsnivå, och du bekräftar alltid innan något sparas.` |

### 2e. Website premium-body (`copy.sv.json` → `premium.body`)

"obsessiva samlare" skaver på svenska. Byt ordvalet (EN `premium.body` "obsessive collectors" oförändrad):

| | premium.body slut |
|---|---|
| SV | `…säsongsstatistik och 10 fältmärken för de mest hängivna.` |

---

## Acceptanskriterier

**Fas 1 (app):**
1. Alla strängändringar i 1a–1d ligger i rätt fil(er); SV i `values/strings.xml`, EN i `values-en/strings.xml`; nav/breadcrumbs endast SV.
2. `SceneHero` visar inte längre "Birdy." två gånger — nedre wordmark ersatt av fristående koppar-fågel; övre Caveat-rubrik kvar.
3. `./gradlew :composeApp:testDebugUnitTest` grönt; `./gradlew ktlintCheck detekt` grönt.
4. `./gradlew :androidApp:installDebug` + device-verify på SM-S918B: första-skärm, hero, onboarding scen 5, alla fyra nav-flikar (SV-läge) + breadcrumbs. Screenshots i `docs/superpowers/screenshots/`.
5. EN-läge oförändrat på nav (flikar fortfarande engelska).

**Fas 2 (store + website):**
6. Store-docs uppdaterade (kort + långt) i båda språk; korta beskrivningar ≤80 tecken.
7. Website hero/FAQ/premium uppdaterade i `copy.{en,sv}.json`; vs-Merlin-FAQ tillagd; accuracy-siffra borttagen ur BÅDA språk.
8. `cd website && npm run build` grönt; `npm run test:i18n` (SV/EN parity) grönt; `npm run test:smoke` grönt.
9. Ingen accuracy-siffra kvar någonstans i website/store-copy (grep `72`).

## Icke-mål (YAGNI)

- Ingen kategori-/sök-/märken-logikändring (DP A/C/andra delprojekt).
- Ingen onboarding-omsekvensering eller scen-borttagning (bara scen 5-copy mjukas).
- Ingen ny website-sektion/komponent; bara textfält i JSON + en ny FAQ-item.
- Ingen ändring av EN-nav, EN-premium-ord, eller wordmark-bilden i splash/övriga ytor.
- Ingen versionCode-bump beslutas här (hör till release-batchning, program-spec).

## Filer som rörs

**Fas 1:**
- `composeApp/src/commonMain/composeResources/values/strings.xml`
- `composeApp/src/commonMain/composeResources/values-en/strings.xml`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneHero.kt`
- `composeApp/src/commonMain/composeResources/files/branding/hero_bird.png` (ny; kopia av fågel-foreground)

**Fas 2:**
- `docs/play-store/store-listing-sv.md`, `docs/play-store/store-listing-en.md`
- `website/src/content/copy.en.json`, `website/src/content/copy.sv.json`
