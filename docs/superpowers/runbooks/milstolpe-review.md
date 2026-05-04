# Milstolpe-review — runbook

> **När du läser denna:** detta är en checklista du följer (eller säger till Claude att följa) varje gång en plan i `docs/superpowers/plans/` är 100 % klar och CI är grön. Den driver en parallell granskning innan nästa plan startas.

## Varför vi gör detta

Plan-task-flödet (subagent-driven-development) ger kvalitet *per task* — varje task har sin TDD-cykel och passerar CI innan den committas. Men sådant glider ändå genom:

- **Mönsterglidning mellan tasks.** Modul A löser ett problem på sätt 1; modul B möter samma problem två tasks senare och löser det på sätt 2. Ingen review fångar det när man bara tittar en task i taget.
- **Spec-glidning.** En task var "klar" men en rad i specen missades, eller acceptanskriterierna tolkades lite generöst.
- **A11y och visuell drift.** Tillgänglighet och designspec-efterlevnad har inga unit-tester. AI-genererad UI tappar lätt content descriptions, hamnar utanför Mossbädd-paletten, eller bryter touch-target-storlek.
- **Kumulativ säkerhetsyta.** Varje task lägger till lite — en permission, en filehandler, en network-call. Ingen stannar upp och frågar "har vi nu en angreppsyta vi inte tänkt på?".

Att granska *efter varje milstolpe* (varje plan klar) fångar detta medan koden är färsk i hjärnan. Slut-reviewen inför v1.0-release blir då en smal release-config-kontroll, inte ett gigantiskt audit av hela appen.

## När du kör detta

Triggerläge — ALLA dessa måste vara sanna:

- [ ] Alla tasks i den aktuella planen är committade
- [ ] CI är grön på `main` (eller relevant branch)
- [ ] Milstolpe-tagg är satt (t.ex. `v0.1.0-foundation`)
- [ ] Nästa plan har **inte** startats än

Om ovanstående är sant: tid för review.

## Hur du startar reviewen

Säg till Claude (i en ny eller pågående Opus 4.7-session):

```
kör milstolpe-review för plan <N>, från tagg <prev>..HEAD
```

Exempel:

```
kör milstolpe-review för plan 2a, från tagg v0.1.0-foundation..HEAD
```

Vad Claude gör då (huvudtråden, Opus 4.7):

1. Kör `git diff --stat <prev>..HEAD` för att lista ändrade filer.
2. Identifierar planfilen och specfilen för milstolpen.
3. Dispatchar de **5 agenterna nedan i ett enda meddelande** så de kör parallellt.
4. Väntar in alla rapporter, syntetiserar fynd-listan.
5. Visar dig en prioriterad åtgärdslista. Du beslutar vad som fixas.

## De 5 standard-agenterna

Varje agent har **ett snävt scope**. Det är medvetet: överlappande agenter slösar tokens, breda agenter ger ytliga rapporter.

### Agent 1 — Kodkvalitet

**Subagent-typ:** `superpowers:code-reviewer` (eller `code-review:code-review` slash-skill om huvudtråden vill)

**Varför:** AI-genererad kod tenderar lägga till oanvänd komplexitet, döda kodvägar, generiska namn, och kommentarer som förklarar VAD istället för VARFÖR. En extern blick fångar det.

**Prompt-mall:**

```
Granska all kod som ändrats sedan föregående milstolpe i birdy-bird-scanner.

Diff att granska: `git diff <PREV-TAG>..HEAD`
Plan som drev milstolpen: docs/superpowers/plans/<PLAN-FIL>
Designspec: docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md
Projekt-konventioner: CLAUDE.md (läs avsnittet "Doing tasks")

Fokus:
- Idiomatisk Kotlin (KMP/Compose) — inga Java-ismer
- Onödig komplexitet, döda kodvägar, dubblettlogik mellan moduler
- Felhantering på fel ställen — ska bara finnas vid systemgränser
- Namngivning som är otydlig eller inkonsekvent
- Kommentarer som beskriver VAD koden gör (ska bort) vs VARFÖR (får finnas om icke-uppenbart)
- Backwards-compat-skräp som kan tas bort

Rapport: prioriterad lista (HÖG/MEDIUM/LÅG), filename:line för varje fynd,
max 10 sidor, ingen sammanfattning av vad koden gör — bara fynd.
```

### Agent 2 — Säkerhet

**Subagent-typ:** `general-purpose` med `security-review` slash-skill, eller direkt `Explore` om du vill spara tokens

**Varför:** Mobilappar har specifika angrepsytor — permissions, on-device storage, ML-modell-handling, leakage till nätverket. Lätt att missa när man är fokuserad på feature-arbete.

**Prompt-mall:**

```
Säkerhetsgranska birdy-bird-scanner-koden som ändrats sedan föregående milstolpe.

Diff: `git diff <PREV-TAG>..HEAD`
Spec: docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md

Specifika frågor:
1. AndroidManifest.xml — vilka permissions begärs? Är någon bredare än
   nödvändig (CAMERA, LOCATION, READ_MEDIA_IMAGES, INTERNET)?
2. ML-pipeline — hur laddas TFLite-modellen? Möjlighet till modell-swap
   (assetpack-supply-chain)? Verifieras checksum?
3. Bilddata — stannar foton on-device? Loggas EXIF? Skickas något
   till tredje part (analytics, crash-reporting, telemetri)?
4. SQLDelight — parametriserade queries överallt? Inga string-konkatenerade
   där användardata blandas in?
5. API-nycklar — ingen hårdkodad nyckel i Kotlin/Compose-kod, BuildConfig,
   eller resources/strings (kolla även generated)?
6. ProGuard/R8 — körs vid release? Kontrollera androidApp/build.gradle.kts.
7. Backup-flagga — android:allowBackup, dataExtractionRules — kan känslig
   data backupas till Google Drive utan användarens vetskap?
8. Network security config — finns? Tillåter den klartext-HTTP?

Rapport: prioriterad lista (KRITISK/HÖG/MEDIUM/LÅG), CWE-id där relevant,
max 5 sidor.
```

### Agent 3 — Spec-compliance

**Subagent-typ:** `Explore` (medium thoroughness)

**Varför:** Plan-task-flow committar per task men saker glider — en task kan vara "grön" och ändå sakna en rad i specen. Detta är den enda agenten som faktiskt verifierar **att planen levererades som spec'ad**.

**Prompt-mall:**

```
Verifiera att birdy-bird-scanner-implementationen efter milstolpe <N>
matchar planens leveranser.

Plan: docs/superpowers/plans/<PLAN-FIL>
Spec: docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md
(och spec-varianten för planen om den finns i docs/superpowers/specs/)

Metod:
1. Läs planen task-för-task.
2. För varje task: hitta motsvarande commit (`git log --oneline --grep`
   eller `git log <PREV-TAG>..HEAD --oneline`).
3. Verifiera att leveransen finns i koden — inte bara att en commit finns,
   utan att de filer/funktioner/tester planen specat faktiskt finns.
4. Kontrollera "Acceptance criteria" eller "Definition of done"-sektionen
   per task om sådan finns.
5. Flagga varje glidning: "task X säger Y men koden gör Z", eller
   "task X listar fil A men A saknas / har annat innehåll".

Rapport: tabell med kolumner: task-id | plan-krav | faktisk leverans |
gap. Inga generaliseringar — citat ur plan + fil:rad i koden för bevis.
Max 6 sidor.
```

### Agent 4 — Tillgänglighet (a11y)

**Subagent-typ:** `Explore`

**Varför:** Tillgänglighet glöms ofta i AI-genererad UI. Lätt att fånga i en separat granskning eftersom kontrollerna är mekaniska.

**Prompt-mall:**

```
Tillgänglighetsgranska Compose-UI:n i birdy-bird-scanner.

Källkod: composeApp/src/, androidApp/src/
Visuell spec: CLAUDE.md "Visuellt språk (Mossbädd)"-sektionen
Tema-tokens: composeApp/src/commonMain/kotlin/.../ui/theme/Color.kt + Type.kt

Kontroller:

1. **Content descriptions**: Varje Image, Icon, IconButton — har contentDescription?
   Är texten meningsfull (inte "ikon" eller "image")? Är dekorativa element
   markerade med contentDescription = null?

2. **Touch targets**: Klickbara element ≥ 48dp x 48dp?
   Speciellt i bottom-bar och kort-listor.

3. **Färg-kontrast** (WCAG AA, ≥4.5:1 för normaltext, ≥3:1 för stor text):
   - Text primary `#2A3525` på Background `#E8E2D2` — beräkna kontrast
   - Text on hero `#F0EAD8` på Hero deep `#3F4F30`
   - Accent `#8C5A3C` text på Background
   - Accent på Stat surface `#D8D0BC`
   Rapportera varje par + kontrast-värde + pass/fail.

4. **TalkBack-flöde**: Logisk fokus-ordning? Inga osynliga element som
   fångar fokus? Modal-dialoger som behåller fokus inom sig?

5. **Text-skalning**: Klarar layouten Settings → Display size: largest
   och Font size: largest (≈200%)? Trunkerad text? Överlappande element?

6. **Indikering bara på färg**: Aktiv flik i bottom-bar — är det bara
   koppar-färgen som visar aktivitet, eller finns en andra ledtråd
   (bold-text, ikon-fyllning, underline)?

7. **Heading-semantik**: Stora rubriker har Modifier.semantics { heading() }?

Rapport: per-skärm fynd, prioriterad (HÖG/MEDIUM/LÅG), fil:rad, max 4 sidor.
```

### Agent 5 — Visuell efterlevnad (Mossbädd)

**Subagent-typ:** `Explore`

**Varför:** Mossbädd-paletten är låst. AI tenderar att avvika från designspec under iterationer (Material defaults smyger sig in, hex-värden hamnar inline i UI-kod). En diff-fokuserad agent fångar drift.

**Prompt-mall:**

```
Granska birdy-bird-scanner Compose-UI:n mot Mossbädd-designspecen.

Auktoritativ källa:
- composeApp/src/commonMain/kotlin/.../ui/theme/Color.kt (tokens)
- composeApp/src/commonMain/kotlin/.../ui/theme/Type.kt
- CLAUDE.md "Visuellt språk (Mossbädd)"-sektionen
- docs/superpowers/screenshots/ för referens från tidigare milstolpar

Kontroller:

1. **Färg-tokens**: Används ENBART tokens från Color.kt? Inga inline
   `Color(0xFF...)` i UI-koden? Inga MaterialTheme-defaults som inte
   mappats till Mossbädd?

2. **Typografi**:
   - Crimson Pro (serif) på rubriker och stat-siffror?
   - System sans (default) på UI-element?
   - UPPERCASE-etiketter med spärr (letterSpacing) där spec säger?

3. **Hero som zon, inte kort**:
   - Inga hörn-radier på hero-områden?
   - Inga shadow/border?
   - Vertikal gradient mot Background `#E8E2D2` med fade-out?

4. **CTA-konsekvens**: Koppar `#8C5A3C` används bara på:
   - Primär-CTA-knappar
   - Aktiv flik i bottom-bar
   - Stat-siffror
   Inte på sekundära ytor eller dekorativa element.

5. **Bottom-bar**: 72dp höjd, ikon + textetikett per flik, koppar-fyllning
   på aktiv flik?

6. **Stat-surface `#D8D0BC`**: Används bara som stat-bakgrund, inte som
   primär yta?

7. **Konsistens mellan skärmar**: Samma padding/spacing-skala genom appen?
   Samma rubrik-storlek på samma rubrik-typ?

Rapport: avvikelse-lista per skärm med fil:rad och förslag på korrigering
(t.ex. "ersätt Color(0xFF8C5A3C) med MossTheme.colors.accent").
Max 4 sidor.
```

## Synthesis (vad jag gör efter rapporterna)

När alla 5 rapporter är inne:

1. **Konsoliderad fynd-lista** — alla fynd från alla agenter, sorterade KRITISK → HÖG → MEDIUM → LÅG.
2. **Per-fynd**: agent-källa, fil:rad, rekommenderad åtgärd, uppskattad fix-storlek (S/M/L).
3. **Mönster-flagga**: om två agenter rapporterar samma rotorsak (t.ex. "ingen contentDescription någonstans" + "låg kontrast på samma element" → systemiskt UI-kvalitetsproblem), lyfter jag det separat — det är värt en egen åtgärd, inte 20 individuella patches.

## Fix-loop

För varje fynd som ska åtgärdas:

| Prio | Hantering |
|---|---|
| **KRITISK** | Stoppa allt. Fix dispatchas till en Sonnet 4.6-subagent med tight scope, inkl. test-skrivning där relevant. CI måste bli grön igen. |
| **HÖG** | Fixas innan nästa plan startas. Subagent eller inline beroende på storlek. |
| **MEDIUM** | Fixa inline i huvudtråden om scope ryms i samma session. Annars → followups. |
| **LÅG** | Loggas i `docs/superpowers/runbooks/<milstolpe>-followups.md` för senare. Får inte blockera nästa plan. |

När KRITISK + HÖG är åtgärdade, committade och CI grön → **milstolpen är granskad-och-klar**. Skriv en sista commit `chore(review): milstolpe <N> reviewed and signed off` och fortsätt med nästa plan.

## Bonus — Agent 6 (bara inför v1.0 / Play Store)

När du närmar dig publicering, lägg till:

### Agent 6 — Release-config

**Subagent-typ:** `Explore`

**Varför:** Sista check på signing, ProGuard, store-metadata innan submission. Misslyckanden här kostar dagar i Play Store-review-cykler.

**Prompt-mall:**

```
Granska release-konfiguration i birdy-bird-scanner inför Play Store-submission.

Filer: androidApp/build.gradle.kts, androidApp/src/main/AndroidManifest.xml,
androidApp/proguard-rules.pro, gradle.properties, Play Console-metadata
(om committat någonstans).

Kontroller:

1. **Signing**:
   - signingConfigs.release deklarerad?
   - Keystore-fil utanför git (.gitignore-ad)?
   - Keystore-credentials utanför git (env/secrets, inte hårdkodade)?

2. **Minify/ProGuard**:
   - androidApp/build.gradle.kts: `isMinifyEnabled = true` för release?
   - proguard-rules.pro finns och innehåller regler för:
     Compose, KMP-metadata, SQLDelight-genererat, TFLite-interfaces,
     kotlinx.serialization (om använd)?

3. **versionCode och versionName**:
   - versionCode > föregående release och unikt?
   - versionName matchar git-tagg (semver)?

4. **applicationId**: produktion = `se.birdy.android` — inga `.debug`-suffix
   för release-build?

5. **AndroidManifest**:
   - android:debuggable inte satt på release?
   - android:allowBackup = false ELLER dataExtractionRules satt?
   - Network security config — tillåter klartext bara om absolut nödvändigt?

6. **Store-metadata**:
   - Integritetspolicy-länk finns?
   - Skärmdumpar: telefon, 7" tablet, 10" tablet?
   - Beskrivning på SV och EN?
   - Innehållsklassificering ifylld?

7. **Asset Pack**:
   - Stora bundles via Play Asset Delivery konfigurerade?
   - Manifest deklarerar asset packs?
   - Testat med bundletool eller internal testing track?

Rapport: checklista PASS/MISSING/WARN per punkt, åtgärds-prio, max 3 sidor.
```

## Vanliga frågor

**Måste vi köra alla 5 varje gång?**
Nej. För en intern alpha eller en mycket smal milstolpe (t.ex. en pipeline-förändring som inte rör UI alls) räcker 2–3 (kod, säkerhet, spec). 5 är default; du kan be Claude att hoppa över specifika.

**Vad händer om en agent rapporterar dåligt / hallucinerat?**
Säg till. Jag dispatchar om med ett mer specifikt scope. Agenter blir vagare ju bredare prompten är — om en rapport känns yttlig, snäva scope och kör om.

**Kan jag triggra en enskild agent utan att köra hela paketet?**
Ja: säg "kör bara säkerhets-agenten för plan 2a". Användbart när du gjort en specifik ändring (t.ex. lagt till en permission) och bara vill ha en fokuserad granskning.

**Hur lång tid tar det?**
Med parallellitet: 5–15 minuter wallclock för dispatch + rapporter, plus läsning + syntes. Lägg på fix-tid beroende på fynd. Räkna 30–60 min totalt för en typisk milstolpe utan kritiska fynd.

**Token-kostnad?**
5 agenter × ~30k tokens var = ~150k tokens för själva reviewen. Plus huvudtrådens kontext. Inte gratis — men billigt jämfört med en post-release-bugg som måste fixas via en Play Store-update.

## Möjlig uppgradering: slash-skill

Om du tröttnar på att skriva "kör milstolpe-review för plan X" varje gång, kan denna runbook paketeras som en slash-skill (`.claude/skills/milstolpe-review/SKILL.md`) så att `/milstolpe-review plan-2a` startar hela flödet. Bara säg till så bygger jag det.
