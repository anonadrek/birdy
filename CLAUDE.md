# Birdy Bird Scanner — arbetsguide för Claude Code

> **Den här filen läses automatiskt av Claude Code i varje session.** Den ger sammanhang för vad projektet är, var du hittar saker, och hur vi arbetar tillsammans.

## Vad är detta?

AI-driven Android-app för fågelidentifiering. Realtidsskanning via kamera + foto-upload + uppslagsverk över ~700 europeiska arter. Kotlin Multiplatform + Compose Multiplatform. v1 = Android-only ("Skanna & lär"); senare faser lägger till dagbok, gamification, karta, push, community, iOS.

**Status (2026-05-03):** Plan 1 (Foundation) ✅ klar — alla 12 tasks committade, CI grönt, milstolpe taggad `v0.1.0-foundation`. Plan 2 är split i Plan 2a (pipeline + walking skeleton, 5 arter) och Plan 2b (family-by-family backfill till ~700 arter). Plan 2a Tasks 1–3 (CLI-scaffold, species_list-builder, mapping-failures-review) ✅ klara — `species_list.yaml` committad med 836 arter, 1 känt gap (Fringilla moreletti). **Nästa steg: Plan 2a Task 4 (Wikidata-berikning av svenska namn + bilder).**

## Var hittar du saker

| Vad | Var |
|---|---|
| Designspec för v1 | `docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md` |
| Implementationsplaner | `docs/superpowers/plans/YYYY-MM-DD-v1-NN-<phase>.md` |
| Skärmdumpar per milstolpe | `docs/superpowers/screenshots/` |
| Milstolpe-review-runbook (5–6 parallella granskar-agenter) | `docs/superpowers/runbooks/milstolpe-review.md` |
| Visuellt språk (Mossbädd-paletten) | I auto-memory: `visual_language_birdy_v1.md`, sammanfattat nedan |
| Den här guiden | `CLAUDE.md` (du läser den nu) |
| Auto-memory (lokalt, inte i repo) | `~/.claude/projects/C--Users-.../memory/` |

## Plan-of-plans (v1)

| # | Plan | Status |
|---|---|---|
| 1 | Foundation — KMP-bootstrap, Compose, CI, Mossbädd-tema | ✅ Klar (`v0.1.0-foundation`) |
| 2a | Content pipeline + walking skeleton (5 arter) | 🚧 Tasks 1–3 ✅ (836-arts species_list klar); Task 4+ pågår |
| 2b | Content backfill family-by-family (5 → ~700 arter) | Runbook stub i Plan 2a Task 15 |
| 3 | Encyclopedia (browse + species profile) | |
| 4 | ML & Camera (TFLite + CameraX) | |
| 5 | Diary & Gamification | |
| 6 | i18n, polish, Play Store-release | |

Varje plan ska lämna projektet i ett byggbart, testbart tillstånd. Plan 1-12 är alltid sant: `./gradlew build` ska gå grönt.

## Hur vi jobbar (Claude Code-flödet)

### När du börjar en ny session

1. Säg "Vi fortsätter med birdy-bird-scanner" eller liknande.
2. Claude läser denna fil + auto-memory automatiskt.
3. Be om en statusöversikt: "Var står vi?" → Claude kollar git log, senaste commit, vilken plan/task som är aktiv.
4. Bestäm nästa steg utifrån status.

### Behövs superpowers?

**Ja, för dessa moment:**
- **Brainstorming av nya features eller större ändringar** → invoke `superpowers:brainstorming`
- **Skriva ny implementationsplan från en spec** → invoke `superpowers:writing-plans`
- **Exekvera en plan task-by-task med subagents + review** → invoke `superpowers:subagent-driven-development` (rekommenderat)
- **Exekvera en plan inline (utan subagents)** → invoke `superpowers:executing-plans`

**Nej, för dessa moment:**
- Vanliga frågor om kod ("vad gör den här filen?")
- Snabba bugfixar eller justeringar
- Att läsa specs/planer
- Mindre refactoring där hela kontexten ryms i ett samtal

Tumregeln: om du startar något som tar mer än ett samtal eller kräver disciplin (TDD-cykel, plan-tracking), använd skills. Annars bara prata.

### Modell-strategi (vilken Claude för vad)

| Uppgift | Modell |
|---|---|
| Brainstorming, designbeslut, arkitektur | **Opus 4.7** (denna session är ett exempel) |
| Skriva implementationsplaner | Opus 4.7 |
| Code review mellan tasks | Opus 4.7 |
| Exekvera enskilda plan-tasks (subagents) | **Sonnet 4.6** (snabb + tillräckligt smart för TDD-cykler) |
| Snabba lookups, formattering | Haiku 4.5 |

`subagent-driven-development` skickar normalt subagents till Sonnet 4.6 för execution och låter huvud-tråden (Opus 4.7) granska resultatet mellan tasks. Det ger snabb iteration utan att tappa noggrannhet.

### Subagent-driven execution-flöde

När en plan körs med `subagent-driven-development`:

1. Huvudtråden (Opus 4.7) dispatchar en subagent (Sonnet 4.6) för **en task** ur planen.
2. Subagenten implementerar tasken: skriver test → kör test → implementerar → kör test igen → committar.
3. Huvudtråden granskar diffen och plan-progressionen.
4. Du (användaren) kan stoppa, ändra riktning, eller låta nästa task dispatchas.
5. Repetera tills planen är klar.

**Vid avbrott:** all progress är committad i git. Nästa session kan fortsätta från senaste commit utan tappad kontext.

## Visuellt språk (Mossbädd)

Färgpalett (locked 2026-04-30):

| Token | Hex | Roll |
|---|---|---|
| Background | `#E8E2D2` | Pale moss-creme |
| Hero top | `#5C6E48` | Mossgrön |
| Hero deep | `#3F4F30` | Djup moss |
| Hero shadow | `#2A3520` | Skuggmoss |
| Accent | `#8C5A3C` | Koppar (CTA, aktiv flik, stat-siffror) |
| Stat surface | `#D8D0BC` | Sand-creme |
| Text primary | `#2A3525` | Djup skog |
| Text on hero/accent | `#F0EAD8` | Varm offwhite |

**Typografi:** Crimson Pro (serif) för rubriker/siffror; system sans för UI. UPPERCASE etiketter med spärr.

**Layout-principer:**
- Hero är en *zon*, inte ett kort — fade-out vertikal gradient mot bg.
- CTA i koppar plockar upp samma accent som ekar i siffror och aktiv flik.
- Bottom-bar 72dp med ikon + textetikett per flik.

Tema-tokens implementeras i `composeApp/.../ui/theme/Color.kt` och `Type.kt` (Plan 1 Task 6).

## Tekniska val (en rad var)

- **Stack:** KMP + Compose Multiplatform (Android första, iOS-skelett)
- **DB:** SQLDelight 2.x med Flow-baserade queries
- **ML:** TensorFlow Lite (on-device, MobileNetV3-Large eller EfficientNet-Lite0/1)
- **Kamera:** CameraX (Android), 3 fps streaming, confidence threshold 0.35
- **Språk:** SV + EN, Sverige först
- **Distribution:** Play Asset Delivery för stora bundles
- **CI:** GitHub Actions (ktlint, detekt, unit tests, assembleDebug, APK-artefakt)
- **Statisk analys:** ktlint 12.1.2 + detekt 1.23.7

## Lokal utvecklingsmiljö (Windows + Galaxy S23 Ultra)

Allt verktyg är installerat och konfigurerat:

| Vad | Var |
|---|---|
| JDK 21 (Temurin) | `C:\Java\OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10\jdk-21.0.11+10\` |
| Android SDK (platform-35, build-tools 34/36/37) | `C:\Users\abbea\AppData\Local\Android\Sdk` |
| ADB | `C:\Users\abbea\AppData\Local\Android\Sdk\platform-tools\adb.exe` |
| `local.properties` (gitignored) | redan satt med `sdk.dir` |
| Telefon | SM-S918B (Galaxy S23 Ultra), USB-felsökning på, RSA-auktoriserad |

**Standard-prefix för alla `./gradlew`-kommandon i bash:**

```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
```

(Annars hittar Gradle inte Java på vägen.)

## Vanliga kommandon

```bash
# Bygga + installera på ansluten enhet
./gradlew :androidApp:installDebug

# Starta appen efter installation
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity

# Verifiera ADB ser enheten
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" devices

# Tester (snabba — bara delade moduler på JVM)
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest

# Lint + statisk analys
./gradlew ktlintCheck detekt
./gradlew ktlintFormat   # autofix
```

## Repo

GitHub: https://github.com/anonadrek/birdy

Branches: `main` är default. Plan-arbete sker på `main` med små commits per task; tagga milstolpar (`v0.1.0-foundation` osv).

## Beslut & ramar

- **Scope:** v1 = "Skanna & lär" + uppslagsverk. Inget mer (dagbok, mål, etc kommer i Plan 5+).
- **Geografi:** Norden/Europa, ~700 arter.
- **Användare:** Bred två-lager (nybörjare som vill lära sig + entusiaster som vill scanna i fält).
- **AI:** On-device, ingen backend för inference. Migrationsdata och sannolikhet är art-nivå statisk i v1.
- **Solo-utvecklare:** användaren bygger detta själv via Claude Code. Granskning sker av användaren mellan tasks.

## Frågor under arbetet

Om en plan-task är otydlig eller kräver beslut, **stoppa och fråga** istället för att gissa. Bättre att pausa fem minuter än att rulla med fel antagande i tre tasks.

Om du upptäcker en spec-motsägelse, lyft det direkt — vi uppdaterar specen tillsammans.

## Autonomi-direktiv (gäller löpande)

Användaren har sagt: **"Don't ask me for permission to run anything."** Det betyder:

- Kör operationer som planen specar utan att be om bekräftelse — commits, push, gradle-kommandon, file-edits enligt task.
- Vid scope-creep eller spec-avvikelse upptäckta i review: fixa autonomt (t.ex. soft-reset + re-commit för att splitta scope-creepiga commits).
- Det betyder INTE att ignorera blockerare som kräver fysisk åtkomst (telefon, emulator) eller tredjepartsbeslut (CI-resultat) — där rapporterar man status och väntar.
- Det betyder INTE heller att hoppa över granskning. Två-stegs-review (spec → kvalitet) körs alltid mellan tasks per `subagent-driven-development`.

## Plan 2a status

Plan: `docs/superpowers/plans/2026-05-02-v1-02a-content-pipeline.md`. Tasks 1–3 klara, Task 4+ kvarstår.

| # | Task | Status |
|---|---|---|
| 1 | CLI-scaffold (uv + click + ruff + mypy) | ✅ |
| 2 | species_list-builder (VP11 + IOC + Wikidata) | ✅ |
| 3 | mapping_failures.yaml-review (manuell godkänning) | ✅ — 836 arter, 1 känt gap (`Fringilla moreletti`) |
| 4 | Wikidata-berikning (svenska namn, hero-bild Q-IDs) | ⏳ Nästa |
| 5–7 | Wikipedia-sammanfattning, Commons-bilder, ranges | ⏳ |
| 8 | Claude-prompt + walking-skeleton 5 arter | ⏳ Behöver few-shot-godkännande av användaren |
| 9–14 | YAML-skrivning, KMP-loader, polish | ⏳ |
| 15 | Plan 2b runbook-stub | ⏳ |

**Pipeline-state att veta om:**

- `tools/content-pipeline/species_list.yaml` är committad och canonical input för Tasks 4–8.
- `tools/content-pipeline/mapping_failures.yaml` innehåller bara `Fringilla moreletti` (ingen art-Q-ID på Wikidata; revidera när Wikidata uppdateras).
- `init --resume` re-kör pipelinen och bevarar manuella entries i `species_list.yaml`; lösta failures auto-rensas. Använd alltid `--resume` om du redigerat `species_list.yaml` manuellt.
- `cross_check_with_ioc` är soft (varnar bara, filtrerar inte). 1 kvarvarande varning: Scotocerca inquieta (VP11=Cettiidae, IOC=Scotocercidae — recent split).
- `map_to_wikidata` har en synonym-fallback: misslyckad primär `wdt:P225`-lookup gör en andra fråga mot icke-deprecated `p:P225/ps:P225` så renamed-taxa (Botaurus, Astur, Tachyspiza) hittas trots att IOC v14.1 ännu inte adopterat nya genus.

**Walking-skeleton-arter (5 st, för Task 8):** Q25485 Talgoxe (Parus major), Q25234 Koltrast (Turdus merula), Q25404 Blåmes (Cyanistes caeruleus), Q25402 Knölsvan (Cygnus olor), Q26490 Tornfalk (Falco tinnunculus).

**Säg "kör Plan 2a Task 4" så fortsätter jag.**
