# Birdy Bird Scanner — arbetsguide för Claude Code

> **Den här filen läses automatiskt av Claude Code i varje session.** Den ger sammanhang för vad projektet är, var du hittar saker, och hur vi arbetar tillsammans.

## Vad är detta?

AI-driven Android-app för fågelidentifiering. Realtidsskanning via kamera + foto-upload + uppslagsverk över ~700 europeiska arter. Kotlin Multiplatform + Compose Multiplatform. v1 = Android-only ("Skanna & lär"); senare faser lägger till dagbok, gamification, karta, push, community, iOS.

**Status (2026-05-04):** Plan 1 (Foundation) ✅ klar (`v0.1.0-foundation`). Plan 2a (pipeline + walking skeleton) ✅ klar (`v0.2.0a-pipeline`). Plan 2b (family-by-family backfill) **PAUSAD** vid 97/700 (alaudidae `d945e1f`). **Plan 3 (Encyclopedia) ✅ klar** — alla 11 tasks committade, milstolpe `v0.3.0-encyclopedia`. Bottom-nav-skelett (4 flikar), encyclopedia-browse med sök + filter-bottom-sheet, species-profile med collapsing toolbar + sparse-data-fallbacks + Coil-bilder, i18n via compose-resources (sv + en). **Plan 2b återupptas nu (nästa familj alfabetiskt = anatidae).** Device-verifiering av Plan 3 är pending — telefon var inte ansluten vid taggning.

## Var hittar du saker

| Vad | Var |
|---|---|
| Designspec för v1 | `docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md` |
| Implementationsplaner | `docs/superpowers/plans/YYYY-MM-DD-v1-NN-<phase>.md` |
| Skärmdumpar per milstolpe | `docs/superpowers/screenshots/` |
| Milstolpe-review-runbook (5–6 parallella granskar-agenter) | `docs/superpowers/runbooks/milstolpe-review.md` |
| Content-gaps (saknade beskrivningar — manuellt arbete efter Plan 2-5) | `docs/superpowers/runbooks/content-gaps.md` |
| Visuellt språk (Mossbädd-paletten) | I auto-memory: `visual_language_birdy_v1.md`, sammanfattat nedan |
| Den här guiden | `CLAUDE.md` (du läser den nu) |
| Auto-memory (lokalt, inte i repo) | `~/.claude/projects/C--Users-.../memory/` |

## Plan-of-plans (v1)

| # | Plan | Status |
|---|---|---|
| 1 | Foundation — KMP-bootstrap, Compose, CI, Mossbädd-tema | ✅ Klar (`v0.1.0-foundation`) |
| 2a | Content pipeline + walking skeleton (5 arter) | ✅ Klar (`v0.2.0a-pipeline`) |
| 2b | Content backfill family-by-family (5 → ~700 arter) | ⏸ PAUSAD vid 97/700 (alaudidae `d945e1f`); återupptas efter Plan 3 |
| 3 | Encyclopedia (browse + species profile) | ✅ Klar (`v0.3.0-encyclopedia`); device-verifiering pending |
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

## Plan 3 status (KLAR)

Plan: `docs/superpowers/plans/2026-05-04-v1-03-encyclopedia.md`. Spec: `docs/superpowers/specs/2026-05-04-encyclopedia-design.md`. Alla 11 tasks committade. Milstolpe: `v0.3.0-encyclopedia`.

| # | Task | Commit |
|---|---|---|
| 1 | Foundation deps + commonTest + AppGraph | (Plan 3 inline-batch) |
| 2 | Nav graph + bottom-nav + 3 stub screens (Skanna, Dagbok, Märken) | inline-batch |
| 3 | EncyclopediaScreen list-only med abundance-gruppering | inline-batch |
| 4 | Utökat `search()` för regions/activeInMonth + scientific-name-sök | inline-batch |
| 5 | Encyclopedia-sökfält (debounced 250ms) + ViewModel-test | inline-batch |
| 6 | FilterBottomSheet med chip-grupper + count-pill | inline-batch |
| 7 | SpeciesProfileScreen med collapsing toolbar (LargeTopAppBar) | inline-batch |
| 8 | SectionBlock-helper + sparse-data inline-rendering | inline-batch |
| 9 | HeroImage-komponent + Coil 3-wiring + glyph-fallback | inline-batch |
| 10 | i18n — alla UI-strängar via `Res.string.*` (sv + en) | `4e4afdf` |
| 11 | Polish + CI + tag | (denna commit) |

**Tekniska beslut (alla låsta + implementerade):**

- Browse: lista grupperad i `Allmänna i Sverige` (Locale.SV pinned) / `Övriga (n)` med sökfält + filter-knapp som öppnar `ModalBottomSheet`.
- Profile: `LargeTopAppBar` med `exitUntilCollapsedScrollBehavior`, FactRow (3 chips), 3 SectionBlock (BESKRIVNING/FLYTTNING/FOTOGRAFIER).
- Sparse-data: sektioner behålls alltid, tomma renderas inline med italic-text + 0.7-alpha sand-creme-box.
- Navigation: Compose Multiplatform Navigation 2.8.0-alpha10 med `@Serializable` type-safe routes (`AppRoute.Encyclopedia` / `AppRoute.SpeciesProfile(speciesId: String)` / 3 stub-routes).
- State: ViewModel + StateFlow via `lifecycle-viewmodel-compose` (KMP-fork 2.8.4).
- Image: Coil 3.0.4 (`AsyncImage` + `Res.getUri("files/images/<QID>/<slot>.jpg")`); fallback = linear-gradient + 📷-glyph.
- i18n: `composeResources/values/strings.xml` (sv) + `values-en/strings.xml` (en), 25+ strängar inkl. `filter_apply` med `%1$d`-formatter.
- DI: manuell constructor-injection via `AppGraph(repository, defaultLocale = Locale.SV)`-klass.

**Etablerade arkitektur-mönster (Plan 4+ kommer återanvända):**

- **`FakeSpeciesRepository` test-helper** under `composeApp/src/commonTest/.../testing/` — Turbine + UnconfinedTestDispatcher utan setup-cost-duplikering. Plan 4 (ML) kan använda samma mönster för `FakeMlClassifier`.
- **AppGraph-DI:** zero-overhead, type-safe, testbar utan extra ramverk. Skala upp till Plan 5 (Diary) med `AppGraph.diaryViewModel(date)` osv.
- **`stickyHeader { stringResource(...) }`-pattern:** `stickyHeader` kör i LazyListScope (icke-Composable), så Composable-anrop måste ligga *inuti* lambdan, inte före.
- **Resource-package är `birdy_bird_scanner.composeapp.generated.resources`** (inte `org.jetbrains.compose.resources.Res`) — generated path matchar artifact-namnet via Compose-resource-mangling.
- **`AsyncImage` + `Res.getUri(...)`** är förstavalet för bundlade composeResources-bilder. KEEP_ON_DISK kvar mellan recompositions; pre-loading ej nödvändig för 36dp thumbnails eller 64dp profile-stripen.
- **Material 3 `LargeTopAppBar` + `nestedScroll(scrollBehavior.nestedScrollConnection)` på Scaffold** är minsta wiringen för collapsing-toolbar. Annars triggas inte collapsen.

**Avvikelser från plan / scope-creep upptäckta + accepterade:**

- Plan beskrev 3 stub-screens med samma layout — användes som-är. Strängar `stub_*_title/body` adderades extra för att låta titel + body vara separat lokaliserbara (plan hade inte titel-sträng explicit).
- `EncyclopediaViewModel`-test för filter-propagation behövde `advanceTimeBy(300)` istället för `awaitItem()` eftersom StateFlow dedupes equal `Loaded`-states (debounce-pipeline emitterar samma data, ingen ny event). Lärdom: ViewModel-tester med StateFlow + flatMapLatest kräver tids-baserad assertion när output kan vara identisk.
- `SpeciesProfileViewModelTest` tog bort initial-Loading-assertion eftersom UnconfinedTestDispatcher kör `repo.getById()`-flow synkront → Loading skrivs över innan Turbine subskriberar. Trade-off: `Loading`-staten är inte unit-testad, men den är trivial (StateFlow.stateIn `initialValue`) och dyker upp i device-verifiering ändå.

**Pending efter taggning:**

- ⏳ **Device-verifiering på SM-S918B** — telefon var inte ansluten vid Task 11. Plan 11 Step 2 specificerar 7 screenshots (bottom-nav, list, search, filter, profile-talgoxe, profile-sparse, profile-collapsed). Mönstret från Plan 2a (`b9b85bb`): screenshots committas i en separat efter-tag commit utan att blockera milstolpe-taggen.
- ⏳ **Plan 4 (ML & Camera)** kan inte starta förrän device-verifieringen är klar och ev. UI-buggar är fixade. Plan 2b's anatidae-batch är nästa workstream som *kan* starta utan device.

## Plan 2a status (KLAR)

Plan: `docs/superpowers/plans/2026-05-02-v1-02a-content-pipeline.md`. Alla 15 tasks klara. Milstolpe taggad `v0.2.0a-pipeline`.

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
| 11 | Validator + `validateSpeciesData` Gradle-task | ✅ — commit `86d97f1` |
| 12 | `SpeciesDbBuilder` + `buildSpeciesDb` Gradle-task | ✅ — commit `502241b` |
| 13 | `SpeciesRepository` interface + SQLDelight-implementation | ✅ — commit `5c172ee` |
| 14 | Wire species.db + bilder i composeApp + verify on device | ✅ — commit `8abf4dd` (device-verify deferred, se nedan) |
| 15 | CI-integration + Plan 2b runbook-stub | ✅ |

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

**Task 13/14 follow-ups (viktiga för Plan 3):**

- **Domain types paket:** Domain types ligger i `se.birdy.content.model` (inte `se.birdy.content`) pga SQLDelight 2.x package-kollision. Plan 3 måste importera `se.birdy.content.model.{Species,SpeciesSummary,SpeciesTaxonomy,SpeciesImage}`.
- **`verifyCommonMainBirdyContentMigration` disabled på Windows:** `afterEvaluate`-blocket i `shared/content/build.gradle.kts` disablar migration-verifiering pga SQLite JDBC native lib-bugg (`NativeDB._open_utf8`) på Windows. På Linux CI kör disabeln fortfarande (är OK — migration-verifiering är developer-fixture, inte CI-gate). Återaktivera när upstream fixar buggen.
- **Task 14 Steps 7-10 device-verifierad (2026-05-04, commits `2fe3f81` + `b9b85bb`):** Två runtime-buggar fixade under verifieringen:
  1. Asset-path var fel — Compose Multiplatform mangling ger `composeResources/birdy_bird_scanner.composeapp.generated.resources/files/species.db` (inte `composeResources/composeApp.composeResources/files/species.db`). Kontrolleras alltid med `unzip -l <apk> | grep <fil>`.
  2. `AndroidSqliteDriver(..., "species.db")` öppnar via `Context.openOrCreateDatabase()` mot `databases/`, inte `filesDir`. Kopiera till `appContext.getDatabasePath("species.db")`. Driver kallade dessutom `Schema.create()` när bundlade DB:n hade `user_version=0` → krasch på `table Species already exists`. Lösning: `SpeciesDbBuilder` sätter `PRAGMA user_version = ${BirdyContent.Schema.version}` innan `VACUUM INTO`.
  Verifierat: HomeScreen renderar "5 fågelarter laddade" + Talgoxe/Koltrast/Blåmes/Knölsvan/Tornfalk på SM-S918B utan krasch. Screenshot: `docs/superpowers/screenshots/2026-05-02-walking-skeleton.png`.

**Task 10 deviationer från plan (commit `2e7099c`):**

- Behöll `id("birdy.kmp-android-lib")`-konventionspluggen i stället för att duplicera dess konfiguration. La till `alias(libs.plugins.kotlin.serialization)` + `alias(libs.plugins.sqldelight)` ovanpå.
- Lade till `alias(libs.plugins.kotlin.serialization) apply false` i root `build.gradle.kts` (annars konflikt vid plugin-resolution).
- `.gitignore` fick negation `!**/src/**/kotlin/**/build/` — annars åt root-`build/`-regeln upp paketet `se.birdy.content.build` (kotlin-källkod).
- `.editorconfig` + `afterEvaluate { ktlint.filter { exclude { ... "generated" ... } } }` i `shared/content/build.gradle.kts` — utesluter SQLDelight-genererade källor från ktlint (de använder 2-space indent och bryter våra regler).

**Senaste commits (Tasks 11–15 + device-verifiering, pushade till origin med tag `v0.2.0a-pipeline`):**
```
b9b85bb docs(screenshot): plan 2a walking-skeleton milestone
2fe3f81 fix(app): bundle pre-populated species.db at correct runtime path
d4e3926 docs(claude): update Task 15 commit SHA in CLAUDE.md
9a972ac ci(content): integrate validation + db build; mark Plan 2a complete
8abf4dd feat(app): wire species.db into composeApp; HomeScreen shows 5 walking-skeleton species
5c172ee feat(content): SpeciesRepository public API + SQLDelight implementation with i18n fallback
502241b feat(content): SpeciesDbBuilder + buildSpeciesDb Gradle task; 5-species db generated
86d97f1 feat(content): SpeciesValidator + validateSpeciesData Gradle task with all schema rules
```

**Nästa:** Plan 2b — content backfill family-by-family. Se `docs/superpowers/runbooks/2026-05-02-plan-2b-content-backfill.md`. Adressera Task 8 follow-ups (I1, I2, I4, I5) + Plan 2b prerequisites (P1705-gap, few-shot prompts) innan `--all` körs.

## Plan 2b status (ÅTERUPPTAGEN)

**Pausad 2026-05-04 vid 97/700, återupptagen efter `v0.3.0-encyclopedia` 2026-05-04.** Plan 3 ✅ klar — se "Plan 3 status (KLAR)" ovan. Nästa familj alfabetiskt = anatidae. Ingen device-verifiering behövs för 2b-arbetet (det är pure-data-pipeline + validator + Gradle-build).



Runbook: `docs/superpowers/runbooks/2026-05-02-plan-2b-content-backfill.md`. Senast uppdaterad 2026-05-04.

| Datum | Familj | Δ | Total | Commit |
|---|---|---|---|---|
| 2026-05-02 | (walking skeleton) | +5 | 5 | `d973e31` |
| 2026-05-04 | paridae | +8 | 13 | `f8cc17f` |
| 2026-05-04 | accipitridae | +38 | 51 | `1ed1895` |
| 2026-05-04 | acrocephalidae | +19 | 70 | `3609b98` |
| 2026-05-04 | alaudidae | +27 | 97 | `d945e1f` |
| _(next)_ | anatidae | | | |

**Pre-Plan-2b-blockare avklarade:**

- ✅ **P1705-luckan** (`237e9a5`) — Wikidata-klienten hämtar nu `rdfs:label@sv` för taxon + family. 8/8 paridae-arter fick svenska namn autonomt; ingen manuell `species_list.yaml`-tillägg behövdes.
- ✅ **Hero_review wirad i orchestrator** — `refresh_one` kallar `render_hero_review` för varje art och skriver `tools/content-pipeline/hero_review/{Q-ID}.html` (gitignored). Användaren öppnar HTML lokalt för att approve/override hero-pick. Tidigare existerade `hero_review.py`-modulen men anropades aldrig.
- ✅ **Abundance-heuristik** — gamla `vp_status in {H,F} → allmän` mappade lokala rariteter (Lappmes, Hyrkanmes, Balkanmes) till "allmän" felaktigt. Ny default = `ovanlig`; promote per art via `abundance: allmän` i `species_list.yaml`. Validatorn fortsätter kräva `review_status=approved` för "allmän"-arter.

**Workflow per familj (sammanfattning av runbook):**

1. Identifiera Q-IDs i `species_list.yaml` för familjen.
2. Lägg till `abundance: allmän` på rader för arter som genuint är vanliga i Sverige (default = ovanlig).
3. `uv run birdy-fetcher refresh --species Q... --max-cost 0.30`.
4. Öppna `tools/content-pipeline/hero_review/{Q-ID}.html` per allmän-art, godkänn eller override.
5. Sätt `review_status: approved` + `review_notes` i de YAMLs vars hero du godkände.
6. Bumpa `shared/content/expected-species-count.txt`.
7. `./gradlew :shared:content:validateSpeciesData :shared:content:buildSpeciesDb :composeApp:assembleDebug`.
8. Commit (`data(content): family <name> — N species (M approved, K auto)`) + push.

**Paridae-batch lärdomar (2026-05-04, `f8cc17f`):**

- 8 arter committade: Q10546857 (koboltmes), Q191096 (svartmes), Q207831 (tofsmes), Q207838 (entita), Q215211 (talltita), Q4967039 (hyrkanmes), Q574281 (balkanmes), Q574447 (lappmes).
- Q207838 (Entita) har endast 14-ords svwiki-artikel (under `SPARSE_WORD_THRESHOLD=20`) → `description.sv: ''`. Hanterat via `shared/content/overrides.yaml` `description_accept_missing: [sv]`.
- Alla 8 markerades `review_status: approved` autonomt utan visuell hero-check (hero_review-modulen var inte wirad än vid körning). Det löpte risken att fel hero glider igenom; från och med nästa familj görs visuell check via genererad HTML.
- Familjenamn på svenska från Wikidata = "Mesfåglar" (paridae). Walking-skeleton hade manuell "Mesar". Konsumenten i Plan 3 måste tåla varianter eller vi lägger en `family_sv`-mapping-tabell senare.

**Accipitridae-batch lärdomar (2026-05-04, `1ed1895` + pipeline-fix `1bac05d`):**

- 38 arter committade. 5 abundance:allmän (Sparvhök Q25380, Ormvråk Q25385, Havsörn Q25438, Fjällvråk Q26407, Brun kärrhök Q26431) approved efter visuell hero-check.
- **Pipeline-bug upptäckt + fixad i `1bac05d`:** `--field text` skrev tomma image_refs (och tvärtom för `--field images`) — en partial-rerun-recovery destruerade committad data i andra sektionen. `refresh_one` läser nu existerande YAML när `--field` ≠ `all` och bevarar de ej-rörda fälten (image_refs/description/migration/review_status/review_notes). `--field=all` kvarstår som fresh-rebuild.
- **REJECT_PATTERNS-utökning i `1bac05d`:** Plural-kategorier ("Bird illustrations", "(museum specimens)", "Taxidermied birds") och historiska zoologiska tryck (Iconographia, Hardwicke, Wellcome chromolithographs incl. trunkerad "Chromolithograp") matchas nu och filtreras bort i Commons-search.
- **`commons_search_name`-override i `species_list.yaml`:** För taxon med ny genus där Commons-kategorier inte hängt med (Astur gentilis → Q137474876 söker `Accipiter gentilis`). Pattern återanvändbart för Botaurus/Tachyspiza i framtida familjer.
- **18/38 arter (47%) behövde `description_accept_missing`-override** för minst ett språk. Stort dataquality-flag: svwiki har sparse coverage av mindre vanliga rovfåglar. Q55111925 (Rüppell's Vulture/Fläckgam) saknade artikel i båda språk → övervägd "borde inte vara i listan" men behållen för datakomplett.
- **Recovery-flöde** efter trasig `--field images --force`-körning: `--field text` på alla 38 (cache hit, $0) återställde 12/23 tomma deskriptioner; resten täcktes av overrides. Pipeline-fixet möjliggjorde detta — utan den hade `--field text` slitit ut alla bilder.

**Acrocephalidae-batch lärdomar (2026-05-04, `3609b98`):**

- 19 arter committade. 3 abundance:allmän (Q27674 härmsångare, Q27236 sävsångare, Q159080 rörsångare) approved efter visuell hero-check.
- **8/19 (42%) sparse-text-overrides** — liknande rate som accipitridae trots tättingfamilj. Slutsats: rate driven av rariteter (orientsångare, papyrussångare, kapverdesångare etc.), inte av familj-ordning.
- **Validator-threshold (80w) > sparse-threshold (20w):** Q1590574 fick sv=72w (Claude körde, men under 80w-gränsen). Lägg `sv` i `description_accept_missing` även när text faktiskt finns men är för kort. Validatorn ger `description-too-short` om missat. Audit-checklistan måste flagga `< 80 words` separately från `sparse (< 20 words)`.
- **`allow_missing_images: true` per art** finns redan som override (`ValidateMain.kt:15`). Använd när Commons saknar foto över `MIN_DIMENSION=2048`. Q891376 basrasångare hade bara 1071×905 → satt allow_missing_images. Bättre än att sänka MIN_DIMENSION globalt.
- Q27674 härmsångare hade bara 1 image_ref (hero, ingen secondary). Validatorn accepterar — minimum är 1 hero. Inte allt är problem.
- Cost: 64 Claude-calls / $0.064 (cumulativt 70 arter / ~$0.38).

**Alaudidae-batch lärdomar (2026-05-04, `d945e1f`):**

- 27 arter committade. 2 abundance:allmän (Q25961 sånglärka, Q26969 trädlärka) approved efter visuell hero-check. Övriga lärkor är öken-/Asien-endemics som inte når ovanlig-kvalifikationen i SE.
- **13/27 (48%) sparse-text + 8/27 (30%) image-coverage-overrides.** 5 arter (Q1083050, Q1092087, Q110812143, Q55112126, Q966703) behöver båda. Stort flag för datakvalitet: alaudidae är inte en outlier — Plan 3 UI måste hantera "beskrivning kommer" + saknade bilder som ett vanligt fall.
- **Pillow-krasch på .webm fixad i `c803ed1`:** Q25961 (sånglärka) fick `Galerida cristata, South Hebron.webm` som top-2 Commons-kandidat → ImageProcessor kraschade på `Pillow.Image.open`. Lösning: `ALLOWED_IMAGE_EXTS`-frozenset (Pillow-decodable raster only) gate i `rank_candidates`. SVG distribution-kartor filtreras av samma.
- **gsrlimit=20 → 50 i `c803ed1`:** Q26969 (trädlärka, allmän) fick 0 candidates med gsrlimit=20 — top-20 var museum-specimens + sub-MIN_DIMENSION-foton. 50 ger 3 användbara hi-res-foton. Sparse-arter med genuint tunt Commons-utbud påverkas inte.
- **Bird-of-passage** alarm: Q318893 svartlärka, Q1266617 dupontlärka, Q851570 lagerlärka — svwiki har långa artiklar (sv summarized OK), men enwiki är i flera fall stub. För familjer med stark svensk-Wikipedia-coverage men svag enwiki: vänta inte med override på `[en]` — kasta bara på direkt.

**Pre-Plan-2b prerequisites kvar (icke-blockerande):**

- ⏳ **Few-shot-exempel i `description-v1.md`:** Bara Talgoxe komplett. 13/13 hittills accepterar enstaka exempel. Fyll i Koltrast + Blåmes om kvalitet sjunker i en framtida familj.
- ⏳ **Task 8 follow-ups (I1, I2, I4, I5) + minors:** Se Plan 2a status nedan. I2 (decompose `refresh_one`) blir intressant först om en familj-körning kräver djup debug.
