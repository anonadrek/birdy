# Hardcore-testare: feedback → problem + lösningar

**Datum:** 2026-05-29
**Källa:** Oombedd feedback från en erfaren fågelskådare (closed/external testare)
**Metod:** 4 parallella utrednings-agenter mot kodbasen (kategorier, sök, märken, värdeerbjudande) + syntes
**Status:** Endast utredning — **ingen kod ändrad**. Detta är underlag inför nästa plan.

> Hela rapporten är grundad i koden. Varje fynd har fil:rad-referens så det går att verifiera innan något fixas (jfr trap-lärdom `feedback_audit_verify_before_fix`).

---

## Testarens meddelande (verbatim)

> "I think your app has a long way to go. The categorisations (songbirds, water, raptors, owls, waders) are odd (eg why lump all songbirds together but separate owls as a single category when warblers for example would be a larger category?) and inconsistent (eg auks are in waders, not in water), the search doesn't always work (won't find Eleonora's Falcon, for example), and I can't see the point of the badges. Overall I can't at the moment understand what the app is for. I'll have another look when it's updated, but at present I think you'll find it hard to get a user base for it. Sorry to be so blunt."

---

## Sammanfattning

Testaren har rätt på **alla fyra punkter**, och tre av dem är reella, reproducerbara fel i koden — inte tyckande. Det allvarligaste (#4) är ett positioneringsproblem som hotar hela användarbasen.

| # | Klagomål | Typ | Allvar | Rotorsak (kort) |
|---|---|---|---|---|
| 1 | Kategorierna är godtyckliga + inkonsekventa (alkor i "vadare") | Bug + design | 🔴 Hög | Filtrerar på en enda axel (IOC-ordning); 20,5 % av arterna saknar kategori; Charadriiformes-soptunna |
| 2 | Sök hittar inte "Eleonora's Falcon" | Bug | 🔴 Hög | Typografisk apostrof `’` (U+2019) ≠ tangentbordets `'`; dessutom bara aktivt språks namn söks |
| 3 | "Kan inte se poängen med märkena" | Produktdesign | 🟡 Medel | 60 % av märkena belönar app-vana, inte skådning; trösklar för låga; sällsynt-spåret dolt |
| 4 | "Förstår inte vad appen är till för" | Positionering | 🔴 Kritisk | Funktionen begravd under poesi; ingen krok mot Merlin; spretig för-bred målgrupp |

**Röd tråd:** #1 och #2 är snabba, konkreta kodfixar med stor trovärdighetseffekt på exakt den entusiast-grupp som skriver recensioner. #4 är till 90 % copy/sekvens, inte ny funktionalitet. #3 kan delvis lösas i samma veva som #1 (datan finns redan).

---

## Problem 1 — Kategorierna (godtyckliga nivåer + alkor i "vadare")

### Problem (bekräftat)
Alla tre delklagomål stämmer:
1. **Godtyckliga grupperingsnivåer.** `SONGBIRDS` = hela ordningen Passeriformes (**378 arter = 45 % av appen**) medan `OWLS` = ordningen Strigiformes (**23 arter**). Ingen princip förklarar varför sångfåglar klumpas men ugglor särskiljs. Testarens "warbler"-poäng: sångare (Phylloscopidae + Acrocephalidae + Sylviidae + Locustellidae + Cettiidae + Cisticolidae) är **~70 arter** — fler än ugglorna — men göms inuti den odifferentierade 378-massan.
2. **Inkonsekvens (auk-buggen).** Alkor (`Alcidae`) har `ioc_order: Charadriiformes` → hamnar under chip:en **WADERS / "Vadare"**, inte WATER, trots att de är dykande havsfåglar.
3. **"Vadare" är en soptunna.** Chip:en `WADERS` = hela Charadriiformes = **118 arter ur 13 familjer**: äkta vadare **+ måsar & tärnor (Laridae, 40) + labbar (Stercorariidae, 4) + alkor (Alcidae, 7)**. Minst **43 % av "Vadare" är inte vadare.**

### Rotorsak (fil:rad)
Hela kategorimodellen filtrerar på **en enda taxonomisk axel — IOC-ordning** — och ordningarna handplockades utan att täcka alla arter eller respektera ekologiska gränser inom en ordning.

- `composeApp/.../ui/encyclopedia/ArchiveChip.kt:13-21` — `orderSets` mappar 5 chips → set av `ioc_order`. Rad 20 `WADERS to setOf("Charadriiformes")` är auk-buggens ursprung.
- `composeApp/.../ui/encyclopedia/ArchiveViewModel.kt:120-126` — filtret `list.filter { it.iocOrder in orders }`; allt utanför de utvalda ordningarna faller bara under `ALL`, ingen "övrigt"-hantering.
- `shared/content/.../model/Species.kt:38-46` — `SpeciesSummary` exponerar bara `iocOrder` + `family` (latin). **Ingen ekologisk grupp-nivå** finns mellan ordning och familj. (`family_sv` finns i `SpeciesTaxonomy` men propageras inte till summary.)

Det är alltså en **datamodell-begränsning**, inte bara felmappning.

### Omfattning (siffror, över 839 arter)
- **Täckta av någon chip:** 667 arter (79,5 %).
- **Faller bara under "Alla":** **172 arter (20,5 %)** — inkl. hönsfåglar (31), liror/petreller (26), hackspettar (17), duvor (17), tranor/rallar (13), kungsfiskare/biätare (13), gökar (9), seglare (9), nattskärror (8), trappar (5), storkar (4), flamingo (2).
- **Inuti "Vadare" (118):** Laridae 40 (måsar/tärnor), Scolopacidae 34, Charadriidae 17, Alcidae 7 (alkor), Stercorariidae 4 (labbar) m.fl.

### Domänkunskap (varför en skådare reagerar)
Fältguider och appar bläddrar **ekologiskt/morfologiskt på rätt nivå**: Collins Bird Guide / svenska guider har "Lommar och doppingar", "Stormfåglar och liror", "Änder/gäss/svanar", "Vadare", "Måsar, trutar och tärnor", "Alkor", "Ugglor", "Tättingar" (som bryts ner i sångare/trastar/finkar). Merlin/eBird bläddrar i taxonomisk ordning men **på familjenivå** (gulls, terns, auks, sandpipers separata). Alkor är *aldrig* samma grupp som snäppor.

### Lösningsförslag (rangordnade)
- **Option B (hotfix, ingen DB-migration) — gör nu:** byt filtrering från `ioc_order` till **`family`** (finns redan på `SpeciesSummary`). Begränsa `WADERS` till äkta vadar-familjer, lägg `AUKS to setOf("Alcidae")`, `GULLS to setOf("Laridae","Stercorariidae")`. Tar bort de pinsamma felen direkt.
- **Option A (riktig lösning) — planera:** inför en kurerad **ekologisk grupp-axel** (ny `group`-nivå i content-YAML/DB), oberoende av IOC-ordning, mappad per familj. Förslag på ~14 grupper (Tättingar, Änder & gäss, Vadare, Måsar & tärnor, Alkor, Havsfåglar, Doppingar & lommar, Hägrar & storkar, Rovfåglar, Ugglor, Hönsfåglar, Duvor, Hackspettar, Tranor & rallar + "Övriga") som täcker ~alla 839. Ev. valbar undergrupp "Sångare" inom tättingarna.
- **Option C (entusiast-läge):** valbar full taxonomisk bläddring ordning→familj *vid sidan av* Option A, inte istället för.

**Rekommendation:** Option B som hotfix nu, Option A som riktig lösning i nästa content-plan.

### Kodändringar
- **Option B:** `ArchiveChip.kt:4-21` (nya enum-värden + `familySets`), `ArchiveViewModel.kt:124-125` (`it.family in families`), `ArchiveScreen.kt` chip-listan, + nya strängar i **båda** `strings.xml` (SV default + `values-en/`). Aldrig hårdkoda etiketter (trap-katalog).
- **Option A:** lägg `taxonomy.group` i YAML (genereras från familj via pipeline-script, ingen handredigering av 839 filer), kolumn i `SpeciesTaxonomy.sq`, fält i `Species.kt` (`SpeciesTaxonomy` + `SpeciesSummary`), propagera i `SqlDelightSpeciesRepository.kt`, uppdatera importern i `SpeciesDbBuilder.kt`. → schemaändring kräver DB-rebuild + fingeravtrycks-bump (se Problem 2, samma mekanik).
- Default-fältvärden på nya summary-fält så test-fixturer (`FakeSpeciesRepository.kt` m.fl.) inte bryts.

---

## Problem 2 — Sök hittar inte "Eleonora's Falcon"

### Problem (bekräftat)
Reellt, reproducerbart fel som drabbar en **hel kategori arter**, inte ett enstaka dataglapp. Den faktiska orsaken till just "Eleonora's Falcon" är **apostrof-teckenkonflikt**, med locale-buggen som förstärkande andra orsak.

### Rotorsak(er) (fil:rad + bevis)
**A (primär) — typografisk apostrof.** `shared/content/species/falconidae/Q212243.yaml:10` lagrar `en: Eleonora’s Falcon` med **U+2019** (RIGHT SINGLE QUOTATION MARK), inte rak ASCII-apostrof. Sök-SQL `shared/content/.../SpeciesName.sq:20-26` använder `sn.name LIKE ('%' || :query || '%')`; SQLite `LIKE` matchar tecken exakt. Driver är `AndroidSqliteDriver` utan ICU/normalisering.

| Söksträng | Matchar? | Varför |
|---|---|---|
| `Eleonora` | ✅ | delsträng utan apostrof |
| `Eleonora's` (rak `'`) | ❌ | `'` ≠ lagrat `’` |
| `Eleonora’s` (U+2019) | ✅ | men telefon-tangentbord ger rak apostrof |
| `falk` / `falcon` | ❌ | endast namn + scientific_name söks, inte familj |

→ Skriver man hela namnet med vanlig apostrof: **noll träffar**. Skriver man bara "Eleonora": träff. Därav "doesn't *always* work".

**B (sekundär) — single-locale.** `SpeciesName.sq:24` `WHERE sn.locale = :locale` → bara aktivt språks namn söks. Default-locale är `Locale.SV` (`AppGraph.kt:74`), och `LocaleResolver.kt:11-15` mappar allt utom `sv`/`se`/`en` → SV. En användare i SV-läge som söker det engelska namnet får **garanterat noll träffar** (och tvärtom). Korsspråklig sökning är omöjlig idag.

**C (mindre) — diakriter.** `LIKE` är skiftlägesokänslig bara för ASCII. "Ruppell" hittar inte "Rüppell's"; "rüppell" matchar inte "Rüppell".

**Avfärdat:** scientific_name-sök fungerar ("Falco eleonorae" hittar arten); tom query returnerar alla i aktiv locale (OK).

### Omfattning (över 839 arter)
- **74 arter (8,8 %)** har EN-namn med U+2019 → osökbara på fullständigt namn med vanlig apostrof. Ex: Bonelli's, Montagu's, Temminck's, Cetti's, Steller's, Pallas's, Rüppell's, Verreaux's, Barrow's, Kittlitz's …
- **5 arter** har diakriter i EN-namn (alla `ü`: Rüppell's Vulture/Weaver/Warbler, Güldenstädt's Redstart, Krüper's Nuthatch — 4 dubbelt drabbade).
- **839 (100 %)** kan ej hittas på "fel" språks namn (cross-locale).

> Not: ett tidigt diakrit-svep gav falskt 529 (PowerShell `-match` skiftlägesokänsligt + brett intervall); korrekt siffra är 5. Apostrof-siffran 74 är robust.

### Lösningsförslag (rangordnade)
1. **(Måste) Normaliserad sökkolumn + normaliserad query.** Lägg `search_text` på `SpeciesName`; vid DB-build: NFD-dekomponera, strippa combining marks (diakriter), vik `’`/`ʼ`/`` ` ``→`'` (eller strippa apostrofer), lowercase. Applicera **samma** funktion på query före `LIKE`. Löser A + C.
2. **(Bör) Cross-locale-sök.** Släpp `sn.locale = :locale` ur WHERE → sök båda språkens namn, dedupa på `species_id` (`distinctBy` finns redan), välj visningsnamn via befintligt fallback-mönster. Löser B.
3. **(Trevligt)** sök även familj/genus/`family_sv` ("falk"/"falcon" → familjeträffar).
4. **(Polish)** prefix-boost i `ORDER BY` (`CASE WHEN name LIKE :query||'%' THEN 0 ELSE 1 END`).

### Kodändringar
- `SpeciesName.sq` — ny kolumn `search_text`, index, uppdaterad `INSERT`, omskriven `searchByNameOrScientific` (match mot `search_text`, ta bort locale-filter för cross-locale).
- `SpeciesDbBuilder.kt` — beräkna `search_text = normalize(name)` vid insert; JVM `java.text.Normalizer` OK här (build-tid).
- `SqlDelightSpeciesRepository.kt:93-152` — normalisera query med samma funktion. `commonMain` saknar `java.text.Normalizer` → `expect/actual normalizeSearch()` (Android actual = `Normalizer`) **eller** enkel common-impl (apostrof-folding + lowercase räcker för 74/79 fall, diakrit-map för resten).
- **Viktig spärr (DB-rebuild):** content-DB byggs från YAML och buntas i APK; `contentHash` hashar bara `id + generated_at` (`SpeciesDbBuilder.kt:119-124`) → en ren schemaändring flippar **inte** fingeravtrycket → uppgraderade enheter behåller gammal DB utan kolumn → krasch. **Måste** samtidigt baka in `BirdyContent.Schema.version` i fingeravtrycket (`SpeciesRepositoryProvider.android.kt:41-52`).
- Regressionstest i `SpeciesRepositoryTest.kt`: sök "eleonora's" (rak apostrof) mot U+2019-fixtur → träff; cross-locale (engelskt namn i `Locale.SV`) → träff.

---

## Problem 3 — "Kan inte se poängen med märkena"

### Problem (tolkning)
Inte ett render-fel — märkena syns och funkar. Klagomålet: **belöningen korrelerar inte med vad en skådare upplever som prestation.** Systemet mäter app-engagemang och triviala trösklar, inte skådar-skicklighet eller list-progression. För en erfaren skådare är "sett en fink" eller "öppnat appen 4 veckor i rad" inte en bedrift.

### Nuläge (vad märkena faktiskt gör)
35 märken: 25 gratis (`badges.yaml`) + 13 premium (`premium_badges.yaml`), utvärderas i `RecalculateBadgesUseCase.kt`.

| Kategori | Antal | Mäter | Belägg |
|---|---:|---|---|
| Progression | 3 | unika arter (5/25/100) | `count_unique_species` |
| Streak vecka | 4 | öppna+spara N veckor i rad | `weekly_streak` |
| Streak månad | 3 | obs N månader i rad | `monthly_streak` |
| Säsong | 4 | antal obs i en årstid | `observed_in_season` |
| **Familj** | **8** | **se *en enda* art (`target: 1`)** | `observed_in_family` |
| Sällsynt | 3 | se 1/5/10 `sällsynt`-arter | `observed_with_abundance` |

Konkreta brister:
- **Familje-märkena kräver `target: 1`** — "Mes-vän: du har sett en mes." En svensk har talgoxe dag 1. 8 av 25 gratis-märken dör i sekunden de blir relevanta.
- **15 av 25 gratis-märken (60 %)** belönar app-vana (7 streak + 8 trivial-familj), inte fynd. "Deltagardiplom".
- **Den enda skicklighets-kategorin är dold.** `BadgesViewModel.computeLockedState()` (~rad 137) returnerar `Hidden` för hela RARE-kategorin → "se 10 sällsynta" syns aldrig som *uppsatt mål*, bara i efterhand.
- **Trösklarna är nybörjar-satta:** taket 100 arter ("börjar bli kunnig") — i skådar-skala är 100 *startpunkten* (svensk medelskådare 150–250+).
- **Onboarding scen 5** (`SceneBadges.kt`) säljer "Mängder av milstolpar att jaga. Håll svit levande." → signalerar casual/Duolingo-gamification, stöter bort den seriösa skådaren.

### Varför det inte landar
Skådar-kulturen kretsar kring **listor och svåra, glesa, stigande milstolpar**: livslista, year list, patch list, first-of-year, sällsyntheter, taxonomisk bredd (jfr eBird-milstolpar 100/200/500). Birdys system kolliderar: ribborna är för låga, engagemang ≠ skicklighet, och det enda riktiga skicklighets-spåret är gömt tills för sent. → **en blandning**, tyngdpunkt på trivialt/grindy + frikopplat från skicklighet.

### Lösningsförslag (rangordnade)
1. **(Rekommenderad) Bygg om mot riktig progression — datan finns redan** (livslista via `Observation.speciesId`, familj/ordning via `taxonomy`, sällsynthet via `abundance` (4 nivåer!), fenologi via `season`):
   - Höj/förläng skalan: 5→25→100→**250→500**.
   - Familje-completion: byt `target: 1` mot "se N olika arter ur familjen" (samlar-utmaning).
   - Avslöja sällsynt-spåret (ta bort RARE→`Hidden`).
   - Utnyttja alla 4 abundance-nivåerna, inte bara binärt `sällsynt`.
2. **(Komplement)** gör streak/engagemang opt-in & nedtonat — separera "Vanor" vs "Skådar-milstolpar"; ev. flytta streaks till valbar "Årsutmaning".
3. **(Minst ingrepp)** degradera Badges från huvudflik till sektion i Lifelist/Arkiv; gör onboarding-scen 5 opt-out.

**Konkreta nya/omgjorda märken** (alla med befintlig data): Century/250/500-klubben; Familje-mästare (5 finkar); Ordnings-samlare (10 ordningar); First of Year; Raritets-jägare (set av abundance-nivåer); Vårmottagaren (fenologi); Regional completion (% av `regions: SE`); Patch-trogen (om GPS finns). De tre översta = billigast + störst effekt.

### Kod-/data-ändringar
- Snabba vinster: `badges.yaml` (targets), `BadgesViewModel.kt:~137` (visa RARE), `strings.xml` ×2 + `BadgeStringMap.kt` (copy + onboarding-scen 5).
- Nya villkor: `BadgeRule.kt` (nya subtyper), `RecalculateBadgesUseCase.kt` (`rawValue` `when`-gren; har redan `speciesByQid` + `zone`), `BadgeCatalogLoader.kt` (YAML→rule), `BadgeRuleTest.kt`.
- **Licensspärr (trap-katalog):** inget *audio*-relaterat märke får ligga bakom Premium (BirdNET CC BY-NC-SA). `premium_song_scholar` (`audio_observation_count`) ligger i premium idag — flytta audio-märken till gratis om de byggs ut.

---

## Problem 4 — "Förstår inte vad appen är till för" (kritisk)

### Problem (tolkning)
Ett kategori- och kontrast-problem. Efter onboarding + första skärmen kan testaren inte besvara: *"Vad är det här, och varför använda det istället för Merlin (gratis, Cornell, bäst på ID)?"* Appen kommunicerar **stämning** (vacker fältdagbok) i stället för **jobb-att-utföras**. Den estetiska differentiatorn presenteras som dekoration, inte funktion.

### Nuläge (med citat)
- **Onboarding scen 1** är den enda raka funktionsmeningen i hela appen: *"Identifiera fåglar i fält — foto eller ljud, på enheten, utan internet."* — men den är begravd som sub under ett ensamt `*Birdy.*`.
- **Första skärmen** (`ListenLauncherScreen.kt`, som Skip-användare landar direkt på): rubrik **"Tre sätt att *fånga.*"** / "En stämpel väntar i varje." + kort "Kika"/"Leta upp"/"Lyssna". **Inget om "identifiera", "art" eller "vilken fågel".**
- **Nav-flikar** (`BottomNavBar.kt`): "Identifiera", **"Archive"**, **"Lifelist"**, **"Badges"** — 3 av 4 SV-etiketter är på engelska (`values/strings.xml`).
- **Märken-scenen** säljer streaks → signalerar nybörjar-leksak.

### Varför syftet är otydligt
- **(a)** Verb-poesin (`Fånga`, `Kika`, `Förtjäna`, `Stämpla`) gömmer funktionen; ord som "identifiera/art" möter aldrig användaren på en arbetsskärm.
- **(b)** Sex jämbördiga löften i rad (foto/ljud/dagbok/PDF/märken/privacy) utan hierarki → "är detta ID-app? samlar-app? streak-app?".
- **(c)** Ingen krok mot Merlin uttalas. "Offline/on-device" säljs som teknik-faktoid, inte som kontrast.
- **(d)** Field Journal-temat (den faktiska försvarbara nischen, jfr `03-product-differentiation.md`) presenteras som *yta*, inte som *funktion/behållning*.

Nettoeffekt: appen tjänar varken nybörjaren eller entusiasten tydligt. Entusiasten ser gamification → "leksak"; nybörjaren ser "FÄLT-FÖLJESLAGARE" → exkluderad.

### Föreslagen positionering
> **Birdy är den vackra fältdagboken för fågelskådare som vill *behålla* sina fynd — identifiera med kamera eller ljud, offline, och se samlingen växa till något värt att bläddra tillbaka i.**

Huvudkrok: **"keep it", inte bara "ID it".** Merlin identifierar och glömmer; eBird loggar kliniskt för forskning. Birdy = där fyndet *blir något du äger och vill återbesöka*. Stöd: radikalt privat/offline; ärlig om osäkerhet (Match/Disambig/NoBird). **Smalna målgruppen till entusiasten/seriösa hobbyisten** — det är den som recenserar och sprider.

### Konkreta ändringar (rangordnat efter effekt/insats — till 90 % copy)
1. **Gör första skärmen funktionell** (störst vinst — Skip-användaren ser den): `listen_journal_*`/`listen_card_*` i `strings.xml` ×2. "Tre sätt att *fånga.*" → **"Vilken fågel? *Identifiera.*"**; sub → "Foto, galleri eller läte — arten dyker upp i din fältbok."; kort-bodies "Identifiera live genom kameran" / "...från ett foto".
2. **Lyft hero-löftet till rubrik**, inte sub (`SceneHero.kt`, `onboarding_s1_*`): eyebrow → **"FÅGEL-ID + FÄLTDAGBOK"**.
3. **Store-listing** (`store-listing-{sv,en}.md`): kort beskrivning → "Identifiera fåglar med kamera & ljud. Behåll varje fynd i en fältdagbok som är din — offline."
4. **Website** (`copy.{en,sv}.json`): ta bort "Earn the stamps" ur hero; lägg FAQ-post **"How is this different from Merlin?"** ("Merlin is brilliant at ID. Birdy is about what happens after... Use both.").
5. **Omsekvensera onboarding** (`OnboardingScreen.kt`): funktion + nisch före gamification; flytta/nedtona Märken-scenen; överväg färre scener.
6. **Översätt nav-flikarna** (`values/strings.xml`): "Archive"→"Uppslagsverk", "Badges"→"Märken" (litet, men signalerar slarv för entusiast).

### Differentiering mot Merlin/eBird
| Dimension | Merlin | eBird | **Birdy** |
|---|---|---|---|
| Primärt jobb | Identifiera (best in class) | Logga listor för forskning | **Behålla & återbesöka dina fynd vackert** |
| Estetik | Modern Material | Kliniskt forskar-UI | **Fältdagbok: papper, serif, stämplar — kategori-unik** |
| Privacy | Opt-in moln-upload | Konto + moln krävs | **Inga konton, inget moln, offline i fält** |
| Målgrupp | Alla | Hardcore listförare | Entusiast som vill ha en *personlig, vacker, privat* logg |

**Strategisk kärna:** sluta tävla med Merlin på "vem identifierar bäst" (förlorat krig); positionera på **"vad händer efter identifieringen" — behållningen.** Fixen är till 90 % copy & sekvens, inte ny funktionalitet. *Kommunicera aldrig accuracy-siffror (72 % top-3).*

---

## Föreslagen åtgärdsordning

| Prio | Åtgärd | Insats | Effekt |
|---|---|---|---|
| 1 | **Sök-normalisering (apostrof + diakriter) + cross-locale** (Problem 2, lösn. 1+2) | M (schema + DB-rebuild-bump) | 🔴 Hög — 74 + alla arter blir hittbara |
| 2 | **Första-skärm + hero-copy** (Problem 4, ändr. 1+2) | S (copy) | 🔴 Hög — adresserar "vad är detta" direkt |
| 3 | **Kategori-hotfix: family-filter + dela Charadriiformes** (Problem 1, Option B) | S–M | 🔴 Hög — tar bort auk/mås-pinsamheten |
| 4 | **Märken: höj targets + avslöja sällsynt-spåret + familje-completion** (Problem 3, lösn. 1) | S–M | 🟡 Medel |
| 5 | **Store/website-positionering + FAQ "vs Merlin" + nav-översättning** (Problem 4, ändr. 3–6) | S | 🟡 Medel |
| 6 | **Kategori-riktig lösning: `group`-axel** (Problem 1, Option A) | L (content-pipeline + schema) | 🟢 Långsiktig |

Prio 1–3 är en rimlig **v1.x-feedback-respons** att brainstorma som egen plan. Prio 6 hör hemma i nästa content-pipeline-iteration.

---

## Källor / agent-spår
- Kategori-utredning: `ArchiveChip.kt`, `ArchiveViewModel.kt`, `Species.kt`, art-YAML-distribution.
- Sök-utredning: `SpeciesName.sq`, `SqlDelightSpeciesRepository.kt`, `Q212243.yaml`, `LocaleResolver.kt`, `AppGraph.kt`.
- Märken-utredning: `badges.yaml`, `premium_badges.yaml`, `RecalculateBadgesUseCase.kt`, `BadgesViewModel.kt`, `SceneBadges.kt`; eBird-gamification-referenser.
- Positionering: onboarding-scener, `ListenLauncherScreen.kt`, `BottomNavBar.kt`, `store-listing-{sv,en}.md`, `copy.{en,sv}.json`, `03-product-differentiation.md`, `01-competitor-analysis.md`.
