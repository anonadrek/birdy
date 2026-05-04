# Content gaps tracker

Species där pipelinen accepterar saknad beskrivning i ett eller flera språk via `shared/content/overrides.yaml`-postern `description_accept_missing`. Cause: Wikipedia-extracten är under `SPARSE_WORD_THRESHOLD` (20 ord) eller artikeln saknas helt, så Claude inte kan summera utan att hallucinera.

**Plan:** denna lista fylls i manuellt **efter** att appen är funktionell (Plan 3+). Tills dess visar UI:n "Beskrivning kommer" eller liknande för dessa språk-gap (UI-beslut tas i Plan 3).

## Hur du fyller i en post

1. Skriv 180–250 ords beskrivning på det saknade språket. Stil: faktabaserad, art-fokus, första meningen ska kunna stå ensam (lead).
2. Lägg in texten i artens YAML under `description.<lang>:`.
3. Sätt `review_status: approved` + `review_notes` med datum + "manual curated description".
4. Ta bort artens post i `shared/content/overrides.yaml` (eller ta bort språket ur listan om båda var tomma).
5. Kör `./gradlew :shared:content:validateSpeciesData :shared:content:buildSpeciesDb`.
6. Commit: `data(content): manual <q-id>-description for <species_sv>`.

**Alternativt: vänta på upstream.** Om svwiki-artikeln har växt över 20 ord kan du bara köra:
```bash
uv run birdy-fetcher refresh --species Q... --field text --force
```
och den nya texten hämtas + summeras. Verifiera ordlängd ≥ 80, ta bort overrides-posten, commit.

## Status

| # | Q-ID | Familj | Art (sv) | Vetenskaplig | Saknas | svwiki rev | enwiki rev | Status |
|---|---|---|---|---|---|---|---|---|
| 1 | Q207838 | paridae | Entita | Poecile palustris | sv | 58519788 | 1325332649 | pending |
| 2 | Q114338 | accipitridae | Balkanhök | Tachyspiza brevipes | sv | 57082324 | 1333542055 | pending |
| 3 | Q170251 | accipitridae | Ormörn | Circaetus gallicus | sv | 57082754 | 1348885479 | pending |
| 4 | Q179359 | accipitridae | Stäppörn | Aquila nipalensis | sv | 57082799 | 1348516976 | pending |
| 5 | Q21090684 | accipitridae | Större skrikörn | Clanga clanga | sv | 57082762 | 1347360339 | pending |
| 6 | Q230837 | accipitridae | Stäpphök | Circus macrourus | sv | 57082258 | 1314555725 | pending |
| 7 | Q233684 | accipitridae | Örnvråk | Buteo rufinus | sv | 57082589 | 1348532973 | pending |
| 8 | Q234722 | accipitridae | Hökörn | Aquila fasciata | en | 58592514 | — | pending |
| 9 | Q25438 | accipitridae | Havsörn | Haliaeetus albicilla | sv | 59205816 | 1343154911 | pending (allmän — prioriterad) |
| 10 | Q26407 | accipitridae | Fjällvråk | Buteo lagopus | sv | 59200674 | 1344375918 | pending (allmän — prioriterad) |
| 11 | Q26574 | accipitridae | Ängshök | Circus pygargus | en | 57082252 | — | pending |
| 12 | Q374141 | accipitridae | Savannörn | Aquila rapax | sv | 57082794 | 1340915346 | pending |
| 13 | Q390328 | accipitridae | Gabarhök | Micronisus gabar | en | 57082558 | 1313789443 | pending |
| 14 | Q499568 | accipitridae | Spansk kejsarörn | Aquila adalberti | sv | 57082798 | 1334780508 | pending |
| 15 | Q55111925 | accipitridae | Rüppellgam | Gyps rueppelli | sv, en | 58696194 | — | pending |
| 16 | Q605431 | accipitridae | Tofsbivråk | Pernis ptilorhynchus | sv | 57082536 | 1344588459 | pending |
| 17 | Q747827 | accipitridae | Klippörn | Aquila verreauxii | en | 57082777 | — | pending |
| 18 | Q838162 | accipitridae | Örongam | Torgos tracheliotos | sv | 57107309 | 1339353678 | pending |
| 19 | Q843278 | accipitridae | Mörk sånghök | Melierax metabates | sv | 58474447 | 1314038057 | pending |
| 20 | Q1590574 | acrocephalidae | Orientsångare | Hippolais languida | sv, en | 56630364 | — | pending |
| 21 | Q312779 | acrocephalidae | Busksångare | Acrocephalus dumetorum | en | 59170214 | — | pending |
| 22 | Q370354 | acrocephalidae | Saxaulsångare | Iduna rama | en | 58506848 | — | pending |
| 23 | Q63835 | acrocephalidae | Olivsångare | Hippolais olivetorum | sv | 56850754 | 1347245687 | pending |
| 24 | Q74100 | acrocephalidae | Papyrussångare | Acrocephalus stentoreus | sv | 55683024 | 1314274805 | pending |
| 25 | Q752539 | acrocephalidae | Polyglottsångare | Hippolais polyglotta | sv | 57738359 | 1330761181 | pending |
| 26 | Q890918 | acrocephalidae | Kapverdesångare | Acrocephalus brevipennis | sv | 55626435 | 1320901520 | pending |
| 27 | Q891376 | acrocephalidae | Basrasångare | Acrocephalus griseldis | sv + image | 59025831 | 1351539333 | pending (saknar även hi-res Commons-foto) |

`svwiki rev` / `enwiki rev` är revisions-ID för den artikel pipelinen senast hämtade. `—` = artikel saknas helt på språket. Om revisionsnumret går upp innebär det att svwiki har vuxit — bra signal att retry:a `refresh --field text --force`.

**Status-värden:**
- `pending` — gap kvar
- `upstream-fixed` — svwiki/enwiki har vuxit, retry bekräftade ≥80 ord
- `curated` — manuell text inlagd, overrides-posten borttagen

## Prioritering

Arter markerade `(allmän — prioriterad)` är `abundance: allmän` i Sverige och syns mycket i UI:n — fyll i dom först om du ändå sätter dig och skriver.

## När planet anropar denna doc

Refererad från CLAUDE.md "Plan 2b status"-sektionen. Nya gap från framtida familj-batches läggs till nederst i tabellen automatiskt — kör samma audit-script som genererade den initiala tabellen (se `git log` på denna fil för formatet).
