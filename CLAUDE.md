# Birdy Bird Scanner — arbetsguide för Claude Code

> **Den här filen läses automatiskt av Claude Code i varje session.** Den ger sammanhang för vad projektet är, var du hittar saker, och hur vi arbetar tillsammans.

## Vad är detta?

AI-driven Android-app för fågelidentifiering. Realtidsskanning via kamera + foto-upload + uppslagsverk över ~700 europeiska arter. Kotlin Multiplatform + Compose Multiplatform. v1 = Android-only ("Skanna & lär"); senare faser lägger till dagbok, gamification, karta, push, community, iOS.

**Status (2026-05-04):** Plan 1 (Foundation) ✅ klar — alla 12 tasks committade, CI grönt, milstolpe taggad `v0.1.0-foundation`. Plan 2 är split i Plan 2a (pipeline + walking skeleton, 5 arter) och Plan 2b (family-by-family backfill till ~700 arter). **Plan 2a Tasks 1–10 ✅ klara** — hela content-pipelinen är byggd OCH walking skeleton är committad (5 arter med riktig Claude-content + 15 bilder under `shared/content/`) OCH KMP-sidan har SQLDelight-schemas + kaml YAML-parser med 2/2 jvmTest gröna. **Nästa steg: Plan 2a Task 11 (`SpeciesValidator` + `validateSpeciesData` Gradle-task — verifierar att de 5 art-YAMLs:erna i `shared/content/species/` validerar mot reglerna i schema-specen).**

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
| 2a | Content pipeline + walking skeleton (5 arter) | 🚧 Tasks 1–10 ✅ (pipeline + 5-art-skeleton + KMP-parser); Task 11 nästa |
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

Plan: `docs/superpowers/plans/2026-05-02-v1-02a-content-pipeline.md`. Tasks 1–10 klara, Task 11 är nästa.

| # | Task | Status |
|---|---|---|
| 1 | CLI-scaffold (uv + click + ruff + mypy) | ✅ |
| 2 | species_list-builder (VP11 + IOC + Wikidata) | ✅ |
| 3 | mapping_failures.yaml-review (manuell godkänning) | ✅ — 836 arter, 1 känt gap (`Fringilla moreletti`) |
| 4 | `wikidata.py` strukturerad fetch + `cache.py` | ✅ — commit `6176f71` |
| 5 | `wikipedia.py` REST-klient med revision-keyed cache | ✅ — commits `47ff44f`, `bb1618d` |
| 6 | `claude_summarizer.py` + `cost.py` + 2 prompts | ✅ — commits `7cf7163`, `afff3de` |
| 7 | `images.py` + `hero_review.py` (Commons fetch + selection + HTML) | ✅ — commits `988c7be`, `7420558` |
| 8 | YAML writer + orchestrator + `doctor` + CLI-wiring | ✅ — commits `e820a23` (impl), `b223a63` (review-fixes) |
| 9 | Walking skeleton — fetch 5 arter + commit content | ✅ — commits `8f00fb9` (5 pipeline-buggar fixade under körning), `d973e31` (5 art-YAMLs + 15 bilder, Claude-cost ~$0.023) |
| 10 | SQLDelight-schemas + Kotlin DTOs + kaml YAML-parser | ✅ — commit `2e7099c` (7 `.sq`-filer, `SpeciesYaml.kt`, `SpeciesYamlParser.kt`, 2/2 jvmTest gröna) |
| 11 | Validator + `validateSpeciesData` Gradle-task | ⏳ **Nästa** |
| 12 | `SpeciesDbBuilder` + `buildSpeciesDb` Gradle-task | ⏳ |
| 13 | `SpeciesRepository` interface + SQLDelight-implementation | ⏳ |
| 14 | Wire species.db + bilder i composeApp + verify on device | ⏳ |
| 15 | CI-integration + Plan 2b runbook-stub | ⏳ |

**Pipeline-state att veta om:**

- `tools/content-pipeline/species_list.yaml` är committad och canonical input för Task 9.
- `tools/content-pipeline/mapping_failures.yaml` innehåller bara `Fringilla moreletti` (ingen art-Q-ID på Wikidata; revidera när Wikidata uppdateras).
- `init --resume` re-kör pipelinen och bevarar manuella entries i `species_list.yaml`; lösta failures auto-rensas. Använd alltid `--resume` om du redigerat `species_list.yaml` manuellt.
- `cross_check_with_ioc` är soft (varnar bara, filtrerar inte). 1 kvarvarande varning: Scotocerca inquieta (VP11=Cettiidae, IOC=Scotocercidae — recent split).
- `map_to_wikidata` har en synonym-fallback: misslyckad primär `wdt:P225`-lookup gör en andra fråga mot icke-deprecated `p:P225/ps:P225` så renamed-taxa (Botaurus, Astur, Tachyspiza) hittas trots att IOC v14.1 ännu inte adopterat nya genus.

**Etablerade arkitektur-mönster (alla nya pipeline-moduler):**

- **Constructor-injection för testbarhet:** `WikidataClient`, `WikipediaClient`, `ClaudeSummarizer`, `ImageSelector`, `ImageProcessor` tar alla `cache: Cache` + en injicerbar async callable (`run_sparql`/`http_get`/`http_get_bytes`) i konstruktorn. Default-implementationer använder aiohttp; tester injicerar fakes. `RefreshContext` (orchestrator) följer samma mönster — `build_context()` är produktionspath, `test_orchestrator.py` exercerar injection-sömmen med fakes.
- **Cache-key med content-hash där prompt/innehåll matters:** `claude_summarizer.py` embeddar `sha256(prompt_template)[:8]` i cache-filnamnet så tysta prompt-edits invaliderar cache. Wikipedia-cachen är revision-keyed. Wikidata-cachen är q_id-keyed (rå JSON).
- **Atomic-write-cache:** `Cache.put` skriver till `.tmp` → `os.replace`. Aldrig partial reads. Använd `cache.put_bytes` för binärdata (Commons-bilder).
- **Async-pattern:** `aiohttp.ClientSession` per request (acceptabelt för Plan 2a's 5 arter; Plan 2b kan dela session via injicerad `http_get`).
- **mypy strict + ruff:** alla pipeline-filer måste passera båda. Inga `Any`, inga otypade defs. RUF001 (Unicode multiplikationstecken) använder ASCII `x` istället.

**Task 8 follow-ups (från code-review, ej blockerande för Task 9–10):**

Code-review av Task 8 (commit `e820a23`) gav "Approved with conditions". Två kritiska issues fixades direkt i `b223a63` (Windows cp1252-krasch i `doctor`, silent failure-swallow i `run_refresh`, plus `merge_overrides`-refactor). Följande **Important** + **Minor** är inte fixade än — de blir kvalitetsläckor om de inte adresseras innan Plan 2b's `--all` på ~700 arter:

| Sev | ID | Sak | Var |
|---|---|---|---|
| Important | I1 | `_NoopClient` i orchestrator har svag typning (`**kwargs: Any`, return `Any`) — spegla `FakeClaudeClient`-signaturen från `claude_summarizer.py` | `orchestrator.py` ~line 74 |
| Important | I2 | `refresh_one` är 130 rader / 6 concerns — dekomponera i `_fetch_text_artifacts(ctx, listed, wd)`, `_fetch_image_refs(ctx, q_id, scientific_name)`, `_assemble_species(...)` (pure, no I/O). Underlättar parallel debug i Plan 2b. | `orchestrator.py:refresh_one` |
| Important | I4 | `[accept_missing]`-sentinel-string skrivs som content i YAML-fältet. KMP-konsumenten i Plan 3 kommer rendera den som text. Lös via separat `description_status: missing`-fält eller `description: null` + `review_notes`. | `yaml_writer.py:113` |
| Important | I5 | `wd.family.lower()` saknar sanitizer för tomma/icke-ASCII familjenamn. Family-rename i framtida Wikidata-revisioner skapar orphan-YAMLs i gamla family-mappen — ingen housekeeping. | `orchestrator.py` ~line 245 |
| Minor | M1 | Hard-coded `regions = ["SE", "NO", "FI", "DK", "DE"]` och `_default_season()` (allt = "present") utan `# TODO(plan-2b)`-kommentar | `orchestrator.py` lines 209-210, 253-270 |
| Minor | M2 | Hard-coded modell-ID-strängar i `sources.claude_model` — återanvänd `MODEL_IDS[ctx.options.model]` från `claude_summarizer` istället | `orchestrator.py` ~line 234 |
| Minor | M3 | `dict[str, Any]` för `sources`/`season`/`description`/`migration` har kända shapes — typed alias eller TypedDict skulle fånga fältnamnstypos | flera ställen |
| Minor | M6 | `.cache/`-checken i `doctor` är decoration (`ok=True` alltid). Lägg till riktigt predikat (t.ex. > 30 dagar gammal cache → varning) eller ta bort | `doctor.py:69-80` |
| — | — | Ruff format applicerades på 5 pre-existing filer i `b223a63` (claude_summarizer, cost, images, test_images, test_wikipedia) — scope creep men gjorde quality gate grön | git: `b223a63` |

**Adress innan Plan 2b's `--all`:** I1 + I2 ger debugbarhet vid 700-art-körning; I4 + I5 ger schema-stabilitet för KMP-konsumenten i Plan 3.

**Task 9 follow-ups (5 pipeline-buggar autonomt fixade av subagent under körning, commit `8f00fb9`):**

| Bugg | Var | Fix |
|---|---|---|
| Commons-sökningen returnerade artikel-sidor istället för File:-sidor — 0 bildkandidater | `images.py` | Lade till `gsrnamespace=6` i Commons-search-query |
| Wikipedia REST-summaries (25–87 ord) tystades bort som "sparse" och nådde aldrig Claude | `wikipedia.py` | `SPARSE_WORD_THRESHOLD` 100 → 20 |
| Prompts var hårdkodade till svenska oavsett `lang=`-parameter | `prompts/description-v1.md`, `prompts/migration-v1.md`, `claude_summarizer.py` | Substitutions appliceras nu på system-prompt också; `{lang_name}`-variabel; prompts skrivna språkneutralt |
| `ioc_order` blev "Saurischia" (dinosaur-ancestor) för alla fåglar | `orchestrator.py` | `ioc_order` läses nu från `species_list.yaml` (förlitar inte SPARQL-P171\* traversal) |
| 5 walking-skeleton-arter saknade `common_sv` + `family_sv` i species_list | `species_list.yaml` | Manuella tillägg för Q25485, Q25234, Q25404, Q25402, Q26490 |

**🚩 USER CHECKPOINT plus Plan 2b-blockerare (uppdaterad efter Task 9):**

1. **Plan 2b's `--all` blockare:** Wikidata-stegen `map_to_wikidata` hämtar inte `P1705` (officiellt språk-namn) → 834 av 836 arter saknar `common_sv`. Måste utökas innan family-by-family backfill kan köras autonomt. (Workaround idag: manuell tillägg till `species_list.yaml` per art.)
2. **Few-shot-exempel i `description-v1.md`:** Bara Talgoxe är komplett. Koltrast + Blåmes är platshållare. Räcker för Plan 2a's 5 arter; behövs två svenska 180–250-ords-beskrivningar (eller godkännande att köra med ett enda exempel) innan Plan 2b's `--all`.

**Walking-skeleton-arter (committade, Task 9):** Q25485 Talgoxe (paridae/Parus major), Q25234 Koltrast (turdidae/Turdus merula), Q25404 Blåmes (paridae/Cyanistes caeruleus), Q25402 Knölsvan (anatidae/Cygnus olor), Q26490 Tornfalk (falconidae/Falco tinnunculus). Filer ligger under `shared/content/species/{family}/{Q-ID}.yaml` + `shared/content/images/{Q-ID}/{hero,secondary-1,secondary-2}.jpg`.

**Task 10 deviationer från plan (commit `2e7099c`):**

- Behöll `id("birdy.kmp-android-lib")`-konventionspluggen i stället för att duplicera dess konfiguration. La till `alias(libs.plugins.kotlin.serialization)` + `alias(libs.plugins.sqldelight)` ovanpå.
- Lade till `alias(libs.plugins.kotlin.serialization) apply false` i root `build.gradle.kts` (annars konflikt vid plugin-resolution).
- `.gitignore` fick negation `!**/src/**/kotlin/**/build/` — annars åt root-`build/`-regeln upp paketet `se.birdy.content.build` (kotlin-källkod).
- `.editorconfig` + `afterEvaluate { ktlint.filter { exclude { ... "generated" ... } } }` i `shared/content/build.gradle.kts` — utesluter SQLDelight-genererade källor från ktlint (de använder 2-space indent och bryter våra regler).

**Senaste 5 commits (på main, ännu ej pushade — Tasks 1–7 commits är pushade till origin):**
```
2e7099c feat(content): SQLDelight schemas + kaml YAML parser + DTOs (jvmTest green)
d973e31 data(content): walking skeleton — 5 species (talgoxe, koltrast, blåmes, knölsvan, tornfalk)
8f00fb9 fix(content): fix 4 pipeline bugs found during walking-skeleton run
3ed6a54 docs(claude): mark Plan 2a Task 8 done, log review follow-ups (I1, I2, I4, I5)
b223a63 fix(content): doctor cp1252 crash, run_refresh exit code, merge_overrides simplification
```

**Säg "kör Plan 2a Task 11" så fortsätter jag** — eller "pusha till origin" först om du vill säkra commits, eller "fixa Task 8/9-followups innan vi går vidare" om du vill härda först.
