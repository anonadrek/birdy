# Birdy Bird Scanner — Plan 2: Content Pipeline Design Spec

**Datum:** 2026-05-02
**Status:** Utkast — väntar på användargranskning
**Spec-typ:** Plan 2 av 6 (content pipeline för v1)
**Föregående:** Plan 1 (Foundation) klar — `v0.1.0-foundation`
**Författare:** Albin Lindblom + Claude Code (brainstormingsession 2026-05-02)

---

## 1. Bakgrund och syfte

Plan 1 lämnade Birdy med en KMP-bootstrap, Mossbädd-temat och en placeholder-`HomeScreen`. Plan 2 fyller appen med innehåll: hela uppslagsverket av ~700 västpaleartiska fågelarter, levererat som en bundlad SQLite-databas (`species.db`) plus referensbilder. Det här dokumentet specar både *innehållet* (schema, fält, kvalitetskrav) och *pipelinen* som producerar det (fetcher-CLI, byggprocess, validering).

**Plan 2 är klar när:**

- 700 art-YAML-filer är committade i `shared/content/species/`
- En hero- och 1-2 secondary-bilder per art finns i `shared/content/images/`
- Gradle-task `:shared:content:buildSpeciesDb` producerar `species.db` på <5 sekunder
- `:shared:content:validateSpeciesData` är grön i CI
- `SpeciesRepository`-interface finns och har integrationstester
- `composeApp` debug-APK innehåller `species.db` + bilder och installerar utan fel
- Tag `v0.2.0-content` är pushad

Plan 3 (Encyclopedia) plockar upp `SpeciesRepository` och bygger UI mot riktig data. Plan 4 (ML & Camera) lägger till TFLite-modellen vars output-vokabulär byggs *från* `species.db`.

---

## 2. Låsta strategiska beslut

Dessa togs i brainstormingen 2026-05-02 och är fundament för resten av specen.

| # | Beslut | Motivering |
|---|---|---|
| 1 | **Scope: alla ~700 arter** med full data vid Plan 2-merge | Halvfärdig DB i mainline = teknisk skuld; full bredd från start ger Plan 3 + Plan 4 stabil konsumtions-yta |
| 2 | **Hybrid pipeline-arkitektur:** committade YAML-källor + separat Python fetcher CLI + snabb Gradle-task som bygger SQLite | Källdata granskningsbar i klartext, CI deterministisk, Claude-anrop sker bara när användaren själv kör fetchern |
| 3 | **Tiered review:** arter med `abundance: allmän` (~120 st) handgranskas, övriga (~580 st) markeras `review_status: auto` | Realistisk arbetsbörda för solo-utvecklare; granskning där den syns mest i appen |
| 4 | **Artlista:** IOC World Bird List 2024 (taxonomi-standard) + BirdLife Sverige checklist (svenska arter) → mappad till Wikidata Q-ID via SPARQL | Kanoniska källor; Wikidata-mappningen ger sedan integration med all annan strukturerad data |
| 5 | **Bilder ingår i Plan 2.** Hero-bild ≥2048px per art. Hämtas från Wikimedia Commons via Wikidata `P18` + Commons-sökning. Bundlade i AAB i v1; PAD-migration är en Plan 6-fråga | Bilder är kärnupplevelse (resultat-skärm, artprofil). Plan 3 kan börja bygga mot riktig content |
| 6 | **Fetcher: Python** under `tools/content-pipeline/` med `uv` dep-management | Python är gold standard för data-engineering; alla bibliotek mogna; en extra toolchain men låg onboarding-kostnad |
| 7 | **Licenser: PD / CC0 / CC-BY / CC-BY-SA** alla accepterade. Full metadata fångas per bild (license, author, source_url) | Maximal täckning från Wikimedia Commons; attribution renderas i Plan 3 |
| 8 | **`top-N`-koncept ersatt av `abundance: allmän`** som review-trigger | Återanvänder fält vi ändå behöver för app-filter och badge-regler |

---

## 3. Pipeline-arkitektur (övergripande)

```
┌──────────────────────────────────────────────────────────────────────┐
│                  Engångskällor (ej i Birdy-repot)                    │
│                                                                      │
│   IOC World Bird List 2024.csv          BirdLife Sverige checklist   │
│         (taxonomi-standard)                  (svenska arter)         │
└────────────────────┬─────────────────────────────────┬───────────────┘
                     │                                 │
                     ▼                                 ▼
        ┌─────────────────────────────────────────────────────┐
        │  tools/content-pipeline/  (Python, uv-managed)      │
        │                                                     │
        │   ┌─────────────────────────────────────────────┐   │
        │   │  birdy-fetcher CLI                          │   │
        │   │  ─────────────────                          │   │
        │   │   1. species_list.py     ← IOC + BirdLife   │   │
        │   │   2. wikidata.py         ← Wikidata SPARQL  │   │
        │   │   3. wikipedia.py        ← Wikipedia REST   │   │
        │   │   4. claude_summarizer.py← Anthropic SDK    │   │
        │   │   5. images.py           ← Wikimedia Commons│   │
        │   │   6. yaml_writer.py                         │   │
        │   └─────────────────────────────────────────────┘   │
        │                                                     │
        │   .cache/  (gitignored, deterministisk per art+steg)│
        └────────────────────────┬────────────────────────────┘
                                 │
                                 ▼ (committat klartext, granskningsbart i PR)
        ┌─────────────────────────────────────────────────────┐
        │  shared/content/species/                            │
        │     paridae/                                        │
        │       Q25485.yaml   (Parus major, talgoxe)          │
        │       Q25404.yaml   (Cyanistes caeruleus, blåmes)   │
        │     anatidae/                                       │
        │       Q25402.yaml   (Cygnus olor, knölsvan)         │
        │     ...                                             │
        │  shared/content/images/                             │
        │     Q25485/hero.jpg                                 │
        │     Q25485/secondary-1.jpg                          │
        │     ...                                             │
        │  shared/content/overrides.yaml  (manuella patches)  │
        └────────────────────────┬────────────────────────────┘
                                 │
                                 ▼ (build-time, snabbt — Kotlin/JVM, ingen nätåtkomst)
        ┌─────────────────────────────────────────────────────┐
        │  shared/content/build.gradle.kts                    │
        │     ├── task validateSpeciesData                    │
        │     ├── task buildSpeciesDb                         │
        │     │     YAML → SQLite via SQLDelight scheman      │
        │     │     → composeApp/.../files/species.db         │
        │     │     → composeApp/.../files/images/*           │
        │     └── (validateModelMapping kommer i Plan 4)      │
        └─────────────────────────────────────────────────────┘
```

### 3.1 Två separerade processer

**Refresh-fasen (manuell, körs när content behöver uppdateras):**

Användaren kör `uv run birdy-fetcher refresh ...`. Hämtar från externa källor, anropar Claude, skriver/uppdaterar YAML+bilder. Cachen säkerställer att API-anrop inte upprepas i onödan. Resultatet är en serie diff:ar i klartext som granskas i PR.

**Build-fasen (varje Gradle-build, snabb):**

`:shared:content:buildSpeciesDb` läser YAML+bilder, validerar, genererar `species.db` och kopierar bilder till assets. Inga externa API-anrop, helt deterministiskt, körs på CI utan extra credentials.

### 3.2 Inkrementellt som default

Fetchern checkar cachen och re-fetchar bara stale eller saknad data. Explicita flaggor (`--force`, `--field=images`, `--species=Q123`, `--stale`) för riktade uppdateringar.

### 3.3 Claude API-användning

Fetchern använder Anthropic Python SDK direkt (inte Claude Code CLI). Modellval: **Haiku 4.5** (`claude-haiku-4-5-20251001`) för summarisering. Per-art-uppgradering till Sonnet 4.6 möjlig via `--model sonnet` på enskilda arter.

**Kostnadsuppskattning (full refresh):**
- ~700 arter × 2 språk = 1400 summariserings-anrop
- ~5K input tokens + ~250 output tokens per anrop
- Haiku 4.5: ~$2.50 totalt för en full körning
- Migration-prompts adderar ~$1
- **Total full-refresh-kostnad: ~$4** (engångs när hela content regenereras)
- Inkrementella körningar (cache-träff på det mesta): typiskt **<$0.10**

Appen själv har ingen Claude-koppling — alla API-kostnader är utvecklarens lokala fetcher-körningar.

---

## 4. YAML-schema per art

En art = en YAML-fil under `shared/content/species/{family}/{wikidata_id}.yaml`. Familjegrupperade kataloger gör att 700 filer förblir navigerbara (~30 familjer i västpaleartisk fauna).

### 4.1 Konkret exempel

```yaml
# shared/content/species/paridae/Q25485.yaml
# Talgoxe — generated 2026-05-02, last refreshed 2026-05-02
id: Q25485                              # Wikidata Q-ID — primärnyckel överallt
scientific_name: Parus major            # IOC binomial
taxonomy:
  family: Paridae
  family_sv: Mesar
  genus: Parus
  ioc_order: Passeriformes
names:
  sv: Talgoxe
  en: Great Tit

abundance: allmän                       # allmän | mindre allmän | ovanlig | sällsynt
iucn_status: LC                         # LC | NT | VU | EN | CR | DD | NE
season:                                 # årshjul av närvaro i Sverige
  jan: present
  feb: present
  mar: present
  apr: breeding
  may: breeding
  jun: breeding
  jul: breeding
  aug: present
  sep: present
  oct: present
  nov: present
  dec: present
regions:                                # ISO 3166-1 alpha-2
  - SE
  - NO
  - FI
  - DK
  - DE
  # ... resten av västpaleartiska området

description:
  sv: |
    Talgoxen är en av Sveriges vanligaste fåglar och kan ses året
    runt i trädgårdar, parker och skogsbryn. Den är lätt att känna
    igen på sin gula buk med svart "slips" som löper från hakan
    ner över bröstet — slipsen är bredare hos hannen.

    Talgoxen är en stark och utmärkt anpassad till människans
    miljöer; den utnyttjar fågelmatningar villigt och bygger
    gärna bo i fågelholkar.
  en: |
    The Great Tit is among Sweden's most familiar birds, ...

migration:
  sv: |
    Stationär i Sverige året runt. Vissa individer från
    nordliga populationer flyttar söderut under hårda vintrar.
  en: |
    Resident year-round in Sweden. ...

image_refs:
  - role: hero
    path: Q25485/hero.jpg
    width: 2400
    height: 1800
    license: CC-BY-SA-4.0
    author: "Pierre Dalous"
    source_url: https://commons.wikimedia.org/wiki/File:Parus_major_-..._.jpg
    commons_filename: Parus_major_-_garden_-_Sweden.jpg
  - role: secondary
    path: Q25485/secondary-1.jpg
    width: 1800
    height: 1200
    license: CC-BY-2.0
    author: "Andreas Trepte"
    source_url: https://commons.wikimedia.org/wiki/File:Parus_major_2.jpg
    commons_filename: Parus_major_2.jpg

# Pipeline-metadata (inte exponerat i appen)
review_status: approved                 # approved | auto | needs_review
review_notes: ""                        # fri text när du granskar
generated_at: 2026-05-02T14:30:00Z
sources:
  wikipedia_sv_revision: 12345678       # konkret revisions-ID för repro
  wikipedia_en_revision: 87654321
  wikidata_revision: 1234567
  claude_model: claude-haiku-4-5-20251001
```

### 4.2 Designprinciper

- **Wikidata Q-ID är primärnyckel överallt** (i YAML, i SQLite, i image-paths). Stabil, internationell, ändras aldrig.
- **Familje-grupperade kataloger** för human navigability. Filnamn = Q-ID (stabilt), inte arstnamn (kan ändras vid taxonomi-revisioner).
- **Heredoc-strings (`|`)** för all flerstycks-text — bevarar styckeindelning, undviker quote-helvete.
- **`review_status`** är pipeline-state, inte content. Build-validering kräver att alla `abundance: allmän`-arter har `review_status: approved`. `needs_review` blockar build oavsett abundance.
- **`sources.*_revision`** ger fullständig reproducerbarhet — vi vet exakt vilken Wikipedia-version Claude summariserade.

### 4.3 `overrides.yaml`

Separat fil i `shared/content/overrides.yaml` tillåter manuell override av enskilda fält per art utan att blockera fetcher-re-runs. Användning:

```yaml
# shared/content/overrides.yaml
Q25485:
  description:
    sv: |
      Manuellt skriven text som ersätter Wikipedia-summariseringen.
      Används om Claude-output inte håller måttet eller Wikipedia är fel.

Q123456:
  image_refs:
    - role: hero
      path: Q123456/hero.jpg
      # ... ersätter automatisk hero-pick

Q789012:
  bundle: hero_only                     # hoppa över secondary-bilder från APK
                                        # (för att hålla totalstorlek nere)

Q654321:
  description:
    sv:
      accept_missing: true              # explicit OK att svensk text saknas
                                        # (Wikipedia har inte artikel)
```

Fetchern läser overrides och slår ihop dem ovanpå auto-genererade YAML innan den skriver — overrides tar alltid precedens. Build-validation läser också overrides.

### 4.4 SQLite-schema

`.sq`-filerna i `shared/content/src/commonMain/sqldelight/se/birdy/content/`:

- **`Species`** — alla skalärfält (id PK, scientific_name, abundance, iucn_status, generated_at, sources.* metadata)
- **`SpeciesName`** — (species_id, locale, name) — namn per språk
- **`SpeciesText`** — (species_id, locale, kind ['description'|'migration'], text) — översättbar text per språk + sort
- **`SpeciesRegion`** — (species_id, region_iso) — många-till-många
- **`SpeciesSeason`** — (species_id, month, status ['present'|'breeding'|'absent'])
- **`SpeciesImage`** — (species_id, role, path, width, height, license, author, source_url, commons_filename)
- **`SpeciesTaxonomy`** — (species_id, family, family_sv, genus, ioc_order)

Indexes: `Species(abundance)`, `SpeciesName(locale, name)` (för fritext-sök), `SpeciesRegion(region_iso)`, `SpeciesTaxonomy(family)`.

---

## 5. Fetcher-CLI och refresh-flöde

### 5.1 CLI-yta

```
uv run birdy-fetcher init                    # första gången: bygger artlistan från
                                              #   IOC + BirdLife → species_list.yaml +
                                              #   mapping_failures.yaml
uv run birdy-fetcher init --resume           # fortsätt efter manuella patches
uv run birdy-fetcher refresh --all           # full refresh av alla arter (alla fält)
uv run birdy-fetcher refresh --species Q25485  # bara talgoxe, alla fält
uv run birdy-fetcher refresh --species Q25485 \
                              --field text   # bara text, behåll bilder
uv run birdy-fetcher refresh --field images  # alla arter, bara bilder
uv run birdy-fetcher refresh --stale         # bara arter där cachen är >30 dagar
                                              #   eller källrevisioner ändrats
uv run birdy-fetcher refresh --resume        # fortsätt avbruten körning
uv run birdy-fetcher status                  # rapport: hur många approved/auto,
                                              #   var saknas data, cache-hälsa
uv run birdy-fetcher eval-prompts            # generera 10 stickprov för manuell
                                              #   prompt-tonkalibrering
```

### 5.2 Refresh-pipeline för en art (intern flödesordning)

```
species Q-ID
   ▼
[wikidata.fetch_structured]
   ├─ cache hit? skippa om ej --force
   ├─ SPARQL: taxonomy, names, IUCN, P18 image filename
   ▼
[wikipedia.fetch_articles(sv, en)]
   ├─ cache hit per revision-id? skippa
   ├─ Wikipedia REST API: extract intro section
   ▼
[claude.summarize(sv) + claude.summarize(en)]
   ├─ cache key = (revision_id, model, prompt_version)
   ├─ Anthropic SDK call, default Haiku 4.5
   ▼
[images.fetch(P18 + topplista från Wikimedia Commons)]
   ├─ ranka kandidater (resolution, license, "in nature"-flagga via categories)
   ├─ ladda hem, EXIF-strip, validera dimensioner
   ├─ resize hero → 2400px max-side, JPEG q=88
   ├─ resize secondary → 1800px max-side, JPEG q=85
   ▼
[merge_with_overrides → write yaml + images]
   ├─ läs overrides.yaml för manuella fält-patches
   ├─ skriv shared/content/species/{family}/Q25485.yaml
   ├─ skriv shared/content/images/Q25485/{hero,secondary-N}.jpg
```

### 5.3 Cache-design

`tools/content-pipeline/.cache/` (gitignored). Per art och steg:

```
.cache/
  Q25485/
    wikidata.json                      ← rådata + retrieval timestamp
    wikipedia-sv-r12345678.html
    wikipedia-en-r87654321.html
    claude-sv-haiku-prompt-v3.txt
    claude-en-haiku-prompt-v3.txt
    image-candidates.json
    images/                            ← downloadade originals före processing
```

Cache-nyckel inkluderar **prompt-version** (incrementeras manuellt i `prompts/` när vi ändrar Claude-instruktioner — säkrar att text re-genereras när vi förbättrat prompten utan att vi måste tvinga med `--force`).

**Atomicitet:** varje cache-entry skrivs först till `.tmp` och sen renamea:s — abrupt avbrott (Ctrl-C, krasch) lämnar aldrig korrupta cache-filer.

### 5.4 Claude-prompt (skiss)

```
System: Du är en svensk fågelguide. Skriv 2-3 koncisa stycken (180-250 ord)
        om följande fågelart, riktat till en intresserad amatör. Fokusera på
        utseende, beteende och var arten ses. Undvik anekdoter och specifika
        platser. Använd "den" inte "han/hon".

        Om källtexten är < 200 ord, returnera en kortare beskrivning på
        80-120 ord. Hitta inte på fakta utöver källan.

Tonprov: [3 kuraterade exempel-stycken om talgoxe, koltrast, blåmes]

User:   Art: Parus major (Talgoxe, Great Tit)
        Familj: Mesar (Paridae)
        Källtext (Wikipedia sv, intro):
        {wikipedia_intro_text}
```

Few-shot med 2-3 tonprov ger konsekvent output utan finetuning. Migration-text är en separat prompt med samma struktur (kortare, ~80-120 ord).

Slutgiltigt promptinnehåll och tonprov-arter beslutas i implementations-tasken som skriver `claude_summarizer.py`. Användaren granskar och godkänner prompten innan första `--all`-körning.

### 5.5 Concurrency

Parallelliserar per art (asyncio + aiohttp) med konfigurerbar `--workers N` (default 4 — väl under Anthropics rate limits). Claude-anrop seriealiseras per språk för att respektera per-second limits — Anthropic SDK hanterar 429-retries internt.

---

## 6. Härdade designval för identifierade risker

Brainstormingen identifierade sju risker. Var och en är adresserad i designen istället för att lämnas som "vi får hantera om det dyker upp".

### 6.1 IOC ↔ Wikidata-mappning fallerar

**Härdning:** Q-ID-mappning är ett separat pipelinesteg med egen artefakt.

```
uv run birdy-fetcher init
   → läser IOC-CSV + BirdLife-checklist
   → SPARQL-query per art: hitta Wikidata Q-ID via P225 (taxon name)
   → producerar:
       tools/content-pipeline/species_list.yaml      (alla mappade arter)
       tools/content-pipeline/mapping_failures.yaml  (arter utan match)
   → exit code != 0 om mapping_failures.yaml inte är tom
```

`mapping_failures.yaml` har för-ifyllda placeholder-Q-ID:n och kommentarsfält. Användaren patchar manuellt, committar, kör `init --resume`. Build fail:ar permanent om någon `Q-ID = null` finns kvar i `species_list.yaml`.

### 6.2 Wikidata distribution-data ojämn

**Eliminerad genom designval.** Wikidata används *inte* för "är arten relevant för västpaleartisk fauna" — den frågan besvaras av IOC-listan filtrerad mot BirdLife Sveriges checklist (utbyggbar med fler nationella checklists i `tools/content-pipeline/checklists/`). Wikidata levererar bara Q-ID och strukturerad metadata för arter vi redan vet ska ingå.

### 6.3 Wikipedia-intro för kort

**Härdning, tre lager:**

1. Claude-prompten instrueras explicit: "Om källtexten är < 200 ord, returnera kortare beskrivning på 80-120 ord. Hitta inte på fakta utöver källan."
2. Detektion i fetchern: om Wikipedia-artikel saknas helt eller `len(intro) < 100 words` → markera `description.{lang}: null` automatiskt och logga till `tools/content-pipeline/sparse_content.yaml`.
3. Build-validation: arter med `description.{lang}: null` *måste* ha en motsvarande post i `overrides.yaml` (handskriven text eller explicit `accept_missing: true`-flagga). Annars build fail.

Resultat: alla luckor är medvetna val per art, inte tysta kvalitetsförluster i 700-arts-batch.

### 6.4 Wikimedia Commons-bild dålig

**Härdning:** kraftigt förstärkt selection-pipeline + visuell granskningshjälp för granskade arter.

Selection-algorithm i `images.py`:

```python
def select_hero(q_id: str) -> ImageCandidate:
    candidates = []
    candidates += fetch_p18(q_id)                       # Wikidata-kanonisk
    candidates += commons_search(scientific_name, limit=10)
    candidates += commons_category_search(scientific_name, in_nature_only=True)

    return rank(candidates, criteria=[
        REJECT if filename matches /illustration|drawing|painting|specimen|skeleton|egg|nest only/i,
        PREFER if commons_categories includes "Photographs of Aves",
        PREFER if commons_categories includes "Birds in nature",
        REQUIRE width >= 2048 OR height >= 2048,
        PREFER landscape orientation (för bakgrundsbruk),
        PREFER PD/CC0 > CC-BY > CC-BY-SA,
        PREFER author_name matches known wildlife photographers (curated allowlist, växer över tid),
    ])
```

För `abundance: allmän`-arter genererar fetchern dessutom `tools/content-pipeline/hero_review/{Q-ID}.html` med top-5 kandidater visuellt + metadata. Användaren öppnar lokalt i webbläsaren, klickar "godkänn" eller väljer annan kandidat — valet skrivs till `overrides.yaml`. Ingen UI-server behövs, bara statiska HTML-filer.

För övriga ~580 arter trustar vi rangordningen; manuell override via `overrides.yaml` möjlig när som helst.

### 6.5 APK för stor

**Härdning:** hårdkodade gränser i `validateAssets`, byggar fail:ar ovanför gräns.

```kotlin
// shared/content/build.gradle.kts validateAssets
val maxBundledAssetSizeMb = 130    // lämnar ~80 MB headroom för TFLite-modell i Plan 4
val maxHeroSizeKb = 600
val maxSecondarySizeKb = 400

if (totalImagesMb > maxBundledAssetSizeMb) {
    error("""
        Bundled image size $totalImagesMb MB exceeds budget $maxBundledAssetSizeMb MB.
        Options:
          1. Lower JPEG quality in tools/content-pipeline/images.py
          2. Add 'bundle: hero_only' to specific Q-IDs in overrides.yaml
          3. Trigger PAD migration (see Plan 6 for distribution split)
    """)
}
```

`bundle: hero_only`-flaggan stöds i schemat från dag ett — om en art får denna i overrides skippas dess secondary-bilder från `species.db`-bygget och flaggas för PAD-distribution senare. Plan 2 implementerar inte PAD; hooken finns för Plan 6.

### 6.6 Claude API rate limits

**Härdning:** resilient resume-flöde inbyggt.

- Default `--workers 4` (väl under Anthropics 50 RPM tier 1)
- Anthropic SDK retry+backoff: 429 → exponential backoff 1s → 60s, max 5 försök, sen abort
- Cache är atomisk per art-och-steg — om körningen abortar mitt i bevaras alla färdiga arter
- `birdy-fetcher refresh --resume` fortsätter från avbrottspunkten
- Telemetri: progress-bar visar `537/700, ~12 min återstår, kostnad hittills $1.83`

### 6.7 Mega-commit (700 filer i en PR)

**Härdning:** inbäddat i implementations-planens task-struktur (se sek 9). Plan 2 levereras som ~25-30 batch-PR:s per familj efter en walking-skeleton-PR med 5 arter. Varje familj-PR är independent och granskningsbar (~20-30 arter, ~50-100 filer inkl. bilder).

### 6.8 Kvarvarande risker (post-härdning)

| Risk | Sannolikhet | Påverkan | Status |
|---|---|---|---|
| Externa API:er (Wikipedia REST, Wikimedia Commons, Anthropic) tillgänglighet | Låg | Låg | Cache-systemet absorberar tillfälliga avbrott; resume-flödet hanterar längre outages |
| Claude-output kvalitet faller pga modelluppgradering | Låg | Medel | Few-shot-tonprov + spot-checks på sällsynta arter; prompt-version-bumpen tvingar regenerering vid prompt-iteration |
| Wikipedia-information faktiskt fel | Låg | Låg | `overrides.yaml` med manuell text; Plan 6 / v1.5 kan lägga till "rapportera fel"-funktion i appen |
| IOC-taxonomi uppdateras (årligen) och vissa arter byter Q-ID | Medel | Låg | Wikidata Q-ID:n är stabila även vid taxonomi-revisioner; uppdatering är en cron-task för senare. Plan 2 låser till IOC 2024 |

---

## 7. Build-time-validering och Gradle-tasks

### 7.1 `:shared:content:buildSpeciesDb`

Primär byggar-task. Kotlin/JVM. Konsumerar YAML+bilder, producerar:

- `composeApp/src/commonMain/composeResources/files/species.db` (SQLite, ~5-15 MB)
- `composeApp/src/commonMain/composeResources/files/images/{Q-ID}/{hero,secondary-N}.jpg` (~50-130 MB)

Implementation: läser YAML via `kaml` (Kotlin-native YAML, evalueras mot Jackson YAML i implementations-task), validerar varje fil, instanserar SQLDelight `BirdyContent` databas i in-memory-läge, kör `INSERT`-statements, exporterar `.db`-filen via `VACUUM INTO`. Bilder kopieras 1:1 från `shared/content/images/`. **Mål: <5 sekunder för 700 arter.**

### 7.2 `:shared:content:validateSpeciesData`

Separat task, körs innan `buildSpeciesDb` och i CI som egen step. Felar bygget vid:

| Regel | Felmeddelande |
|---|---|
| YAML parsar inte | `species/{family}/{Q-ID}.yaml: invalid YAML at line N` |
| Saknad obligatorisk fält | `Q-ID: missing required field 'X'` |
| Q-ID i filnamn ≠ Q-ID i fält | `Q-ID: filename/id mismatch` |
| Beskrivning < 80 ord på något språk (utom där `description.{lang}: null` är explicit + `accept_missing: true` i overrides) | `Q-ID: description.{lang} is too short (N words)` |
| `abundance: allmän` men `review_status != approved` | `Q-ID: common species requires manual review` |
| `image_refs` tom utan `placeholder: true` i overrides | `Q-ID: missing hero image; add to overrides.yaml or re-run fetcher` |
| Bildfil refererad i YAML men saknas på disk | `Q-ID: image file not found at {path}` |
| Bildfil finns men dimensioner < 2048px på hero | `Q-ID: hero image too small ({W}×{H})` |
| Saknad licens-metadata på bild | `Q-ID: image missing license/author/source_url` |
| Dubbletter av Q-ID över alla filer | `Duplicate species id: Q-ID in {file1} and {file2}` |
| `regions` innehåller okänd ISO-kod | `Q-ID: invalid region code 'X'` |

Build-felet är ett enda strukturerat block med alla problem listade — användaren fixar batch, inte en-i-taget.

### 7.3 `:shared:content:validateAssets`

Körs som del av `validateSpeciesData`. Kollar att image_refs och faktiska filer matchar, summerar total bildstorlek + APK-impact-prognos. Hård gräns 130 MB (se 6.5).

### 7.4 Inte i Plan 2

`validateModelMapping` (jämför TFLite output-vokabulär mot species-tabellen) tas i Plan 4 när modellen kommer in.

---

## 8. Testning

### 8.1 Python-fetchern (`tools/content-pipeline/tests/`)

`pytest`-suite med ~30 tester. Inga tester anropar riktiga externa API:er — alla externa kall mockas via fixtures.

| Lager | Vad testas | Approx antal |
|---|---|---|
| Pure functions | YAML-skrivare/läsare (round-trip), region-kod-validering, abundance-mappning, prompt-template-rendering | ~10 |
| Wikidata-modulen | SPARQL-resultat-parsing från fixture-JSON, P18-extraction, taxonomi-mappning | ~5 |
| Wikipedia-modulen | Intro-section-extraction från fixture-HTML, revision-id-detektion, fallback när artikel saknas | ~5 |
| Image-processing | Resize-logik, EXIF-strip, license-extraction från Commons-API-fixture, hero/secondary-rangordning | ~5 |
| End-to-end (fake APIs) | En art (talgoxe) går genom hela pipelinen med mockade svar och producerar förväntad YAML | ~3 |
| Cache | Cache-key-stabilitet, invalidation vid prompt-version-bump, --force-flag, atomicitet | ~3 |

Anthropic-anrop mockas genom `FakeClaudeClient` som returnerar deterministisk text. För tonprov-validering finns `birdy-fetcher eval-prompts` (10 stickprov för manuell granskning), inte CI-test.

### 8.2 Kotlin build-pipeline (`shared/content/src/jvmTest/`)

| Test | Syfte |
|---|---|
| `SpeciesDataValidatorTest` | Varje validation-regel har minst ett pos- + ett neg-test (~15 tester) |
| `SpeciesDbBuilderTest` | Givet fixtur-YAML + fixtur-bilder → producerar förväntad SQLite-databas |
| `SpeciesRepositoryTest` | Använder genererad `species.db` (in-memory SQLDelight), läser ut talgoxe via Q-ID, verifierar alla fält + image_refs. Testar i18n-fallback (hämta sv när bara en finns). |

**Fixture-data:** kuraterad mini-uppsättning av 5 arter (talgoxe, koltrast, blåmes, tornfalk, sångsvan) i `shared/content/src/jvmTest/resources/fixtures/`. Räcker för att täcka alla code paths utan produktions-content i tester.

### 8.3 SpeciesRepository — publikt API för Plan 3

```kotlin
// shared/content/src/commonMain/kotlin/se/birdy/content/SpeciesRepository.kt
interface SpeciesRepository {
    fun getById(id: SpeciesId): Flow<Species?>
    fun search(query: String, locale: Locale, filters: SpeciesFilter): Flow<List<SpeciesSummary>>
    fun listByFamily(familyKey: String, locale: Locale): Flow<List<SpeciesSummary>>
    fun all(locale: Locale): Flow<List<SpeciesSummary>>
}

data class SpeciesFilter(
    val abundance: Set<Abundance> = emptySet(),
    val regions: Set<RegionIso> = emptySet(),
    val activeInMonth: Month? = null,
)
```

Plan 2 implementerar interfacet med SQLDelight. Plan 3 konsumerar det utan att veta om YAML-byggprocessen.

### 8.4 CI-pipeline (tillägg till Plan 1:s setup)

```yaml
# .github/workflows/ci.yml (tillägg)
- name: Validate species data
  run: ./gradlew :shared:content:validateSpeciesData
- name: Run shared:content tests
  run: ./gradlew :shared:content:jvmTest
- name: Build species.db
  run: ./gradlew :shared:content:buildSpeciesDb
- name: Verify species.db reachable from app
  run: ./gradlew :composeApp:assembleDebug
- name: Verify total image size < 130 MB
  run: ./gradlew :shared:content:validateAssets

# .github/workflows/content-pipeline.yml (ny, körs vid tools/** ändringar)
- uv sync
- uv run pytest
- uv run ruff check
- uv run ruff format --check
- uv run mypy
```

CI behöver ingen Anthropic-key och ingen utgående nätverksåtkomst — fetchern körs aldrig på CI; CI bara konsumerar committat content.

### 8.5 Inte i Plan 2 (medvetet)

- UI-tester av Encyclopedia-skärmen — Plan 3
- ML-utvärdering / model-vs-species-vokabulär — Plan 4
- Performance-test av sök på 700 arter — accepterar att SQLDelight med rätt index hanterar detta utan benchmark; revisit om Plan 3 visar problem
- E2E-test av riktiga Wikipedia/Commons-API-anrop — manuell `birdy-fetcher refresh --species Q25485 --force` används som smoke-test

---

## 9. Plan 2 implementations-task-struktur (skiss)

Detaljerad implementations-plan skrivs i separat dokument via `superpowers:writing-plans` efter att den här specen är godkänd. Skissen nedan visar task-uppdelningen så att review-bördan blir hanterbar.

```
Task 1:  Setup tools/content-pipeline/ scaffold (uv, ruff, mypy, pytest, CLI-skelett)
Task 2:  Implement init: IOC + BirdLife → species_list.yaml + mapping_failures.yaml
Task 3:  User runs init, patches mapping_failures.yaml, commits species_list.yaml
Task 4:  Implement wikidata.py + tests (mocked SPARQL)
Task 5:  Implement wikipedia.py + tests (mocked REST)
Task 6:  Implement claude_summarizer.py + tests (mocked Anthropic) +
         user-approval av prompt + tonprov
Task 7:  Implement images.py + tests (mocked Commons) + hero_review HTML-generator
Task 8:  Implement YAML writer + cache + main fetcher CLI orchestration
Task 9:  Walking skeleton PR — kör fetchern för 5 arter (talgoxe, koltrast, blåmes,
         sångsvan, tornfalk), commit YAML + bilder, granska end-to-end-output
Task 10: Implement Kotlin: validateSpeciesData task + tests
Task 11: Implement Kotlin: SpeciesDbBuilder task + tests
Task 12: Implement Kotlin: SpeciesRepository + SQLDelight queries + tests
Task 13: Wire species.db + images into composeApp assets, verify accessible
Task 14-N: Familjevisa batch-PR:s (~25-30 PR:s, ~20-30 arter per familj)
Task N+1: Final validation — alla 700 arter approved/auto, build grönt,
         manuell smoke test av app med riktig data
Task N+2: Tag v0.2.0-content
```

Familjebatcherna kan köras parallellt med varandra (independent PR:s). subagent-driven-development från Plan 1 fungerar oförändrat.

---

## 10. Beroenden, secrets och setup

### 10.1 Lokalt utvecklarsetup (utöver Plan 1:s krav)

| Verktyg | Version | Källa |
|---|---|---|
| Python | 3.12+ | https://www.python.org/downloads/ eller `winget install Python.Python.3.12` |
| `uv` | senaste | `pip install uv` eller `winget install --id=astral-sh.uv` |

### 10.2 Secrets

| Secret | Var | Hur sätts |
|---|---|---|
| `ANTHROPIC_API_KEY` | Lokal `.env` i `tools/content-pipeline/` (gitignored) | Användaren genererar key på console.anthropic.com |

CI behöver inte denna key — fetchern körs bara lokalt.

### 10.3 Engångskällor som behöver laddas ner

`tools/content-pipeline/sources/`:

- IOC World Bird List 2024 (CSV) — hämtas från https://www.worldbirdnames.org/new/ioc-lists/master-list-2/
- BirdLife Sverige checklist (CSV/PDF) — hämtas från BirdLife Sverige

Dessa committas i repot (CC-licens / fri användning) så att `birdy-fetcher init` kan köras utan ytterligare nedladdning.

### 10.4 Repotillägg sammanfattat

```
birdy-bird-scanner/
├── shared/content/                     # befintlig modul, fylls ut
│   ├── build.gradle.kts                # uppdateras med buildSpeciesDb + validateSpeciesData
│   ├── species/                        # NEW: 700 YAML-filer i familje-underkataloger
│   ├── images/                         # NEW: 700 hero + ~1400 secondary bilder
│   ├── overrides.yaml                  # NEW: manuella patches
│   ├── src/commonMain/sqldelight/se/birdy/content/
│   │   └── *.sq                        # NEW: schema-filer
│   ├── src/commonMain/kotlin/se/birdy/content/
│   │   ├── SpeciesRepository.kt        # NEW
│   │   └── ...                         # data-modeller, queries
│   └── src/jvmTest/                    # NEW: tester
│       └── resources/fixtures/         # NEW: mini-set för tester
├── tools/                              # NEW
│   └── content-pipeline/
│       ├── pyproject.toml              # uv-managed
│       ├── README.md                   # quickstart
│       ├── .env.example
│       ├── sources/                    # IOC + BirdLife rådata
│       │   ├── ioc-2024.csv
│       │   └── birdlife-se.csv
│       ├── prompts/
│       │   ├── description-v1.md       # versionerade prompts
│       │   └── migration-v1.md
│       ├── checklists/
│       │   └── western-palearctic.yaml
│       ├── src/
│       │   └── birdy_fetcher/
│       │       ├── __init__.py
│       │       ├── cli.py
│       │       ├── species_list.py
│       │       ├── wikidata.py
│       │       ├── wikipedia.py
│       │       ├── claude_summarizer.py
│       │       ├── images.py
│       │       ├── yaml_writer.py
│       │       └── cache.py
│       ├── tests/
│       │   ├── fixtures/
│       │   └── test_*.py
│       └── species_list.yaml           # genererad, committad (eller mapping_failures.yaml)
└── .github/workflows/
    ├── ci.yml                          # tillägg: validation + build species.db
    └── content-pipeline.yml            # NEW: pytest + ruff + mypy
```

---

## 11. Öppna frågor (tas i implementations-planen)

- **Exakt YAML-bibliotek på Kotlin-sidan:** `kaml` (Kotlin-native) eller Jackson YAML — POC av båda i Task 11 innan beslut
- **Cache-format:** rena filer per cache-key (enklast, första valet) eller SQLite (snabbare lookup) — startar med filer, omvärderar om performance blir issue
- **Few-shot-tonprovens exakta innehåll:** vilka 3 arter och vilken ton — drafs i Task 6, användaren godkänner prompten innan första `--all`
- **`season_summary`-textsträng:** komplement till månads-arrayen — beslutas när Plan 3 designar artprofil-skärmen
- **Cron-schema för `refresh --stale`:** veckovis automatisering — inte i Plan 2; börjar manuellt

---

## 12. Bilaga: terminologi (utöver v1-specens)

- **YAML** — YAML Ain't Markup Language. Källformat för committed art-data
- **SPARQL** — Wikidata's queryspråk
- **`uv`** — modern Python package + project manager (Astral)
- **PD / CC0** — public domain / Creative Commons noll-rättigheter — ingen attribution krävs
- **CC-BY** — Creative Commons Attribution — attribution krävs
- **CC-BY-SA** — CC Attribution + ShareAlike — attribution + derivativ måste delas under samma licens
- **PAD** — Play Asset Delivery, Googles mekanism för att leverera stora assets utanför grund-APK:n
- **IOC** — International Ornithological Congress; kanonisk taxonomi-standard för fåglar
- **Wikidata Q-ID** — Wikidatas stabila identifierare per entitet (t.ex. Q25485 för Parus major)

---

**Nästa steg:**

1. Användargranskning av denna spec
2. Skriva implementations-plan via `superpowers:writing-plans` skill
3. Exekvera plan via `superpowers:subagent-driven-development`
