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
| 28 | Q1584282 | alaudidae | Svartkronad finklärka | Eremopterix nigriceps | sv | 56466598 | 1314034501 | pending |
| 29 | Q525871 | alaudidae | Mindre sånglärka | Alauda gulgula | sv | 57963556 | 1327118317 | pending |
| 30 | Q890990 | alaudidae | Stenökenlärka | Ammomanes deserti | sv | 56600163 | 1348838390 | pending |
| 31 | Q1193299 | alaudidae | Ökenberglärka | Eremophila bilopha | en | 56773261 | — | pending |
| 32 | Q1266617 | alaudidae | Dupontlärka | Chersophilus duponti | en | 57589435 | — | pending |
| 33 | Q1589837 | alaudidae | Tjocknäbbad lärka | Ramphocoris clotbey | en | 56454789 | 1315213392 | pending (enwiki finns men för kort) |
| 34 | Q318893 | alaudidae | Svartlärka | Melanocorypha yeltoniensis | en | 55739591 | 1314461620 | pending |
| 35 | Q851570 | alaudidae | Lagerlärka | Galerida theklae | en | 56929571 | — | pending |
| 36 | Q27075426 | alaudidae | Sandlärka | Alaudala raytal | image | 56454796 | 1315368429 | pending (saknar hi-res Commons-foto) |
| 37 | Q31874488 | alaudidae | Brunkronad lärka | Calandrella eremica | image | 59025589 | 1337431158 | pending (saknar hi-res Commons-foto) |
| 38 | Q890903 | alaudidae | Rasolärka | Alauda razae | image | 55711832 | 1337275433 | pending (saknar hi-res Commons-foto, kritiskt hotad) |
| 39 | Q1083050 | alaudidae | Australisk lärka | Mirafra javanica | sv + image | 57591443 | 1315354773 | pending |
| 40 | Q1092087 | alaudidae | Streckig ökenlärka | Eremalauda dunni | en + image | 58389660 | — | pending |
| 41 | Q110812143 | alaudidae | Turkestandvärglärka | Alaudala heinei | sv + image | 58537309 | 1314771867 | pending |
| 42 | Q55112126 | alaudidae | Arablärka | Eremalauda eremodites | sv + image | 58321237 | 1314534185 | pending |
| 43 | Q966703 | alaudidae | Tibetansk korttålärka | Calandrella acutirostris | en + image | 56454762 | — | pending |
| 44 | Q210954 | anatidae | Alförrädare | Polysticta stelleri | en | 58985729 | — | pending |
| 45 | Q241399 | anatidae | Kopparand | Oxyura leucocephala | sv | 58818108 | 1318890801 | pending |
| 46 | Q26603 | anatidae | Mindre sångsvan | Cygnus columbianus | en | 58510822 | 1351593407 | pending |
| 47 | Q27074540 | anatidae | Snögås | Anser caerulescens | sv | 55729875 | 1346622087 | pending |
| 48 | Q28106778 | anatidae | Blåvingad årta | Spatula discors | sv | 55900784 | 1347137223 | pending |
| 49 | Q28106902 | anatidae | Årta | Spatula querquedula | sv | 58510758 | 1347137232 | pending |
| 50 | Q28106966 | anatidae | Snatterand | Mareca strepera | sv, en | 58662283 | 1351192999 | pending (allmän — prioriterad) |
| 51 | Q331447 | anatidae | Marmorand | Marmaronetta angustirostris | sv | 55648211 | 1314728993 | pending |
| 52 | Q369767 | anatidae | Islandsknipa | Bucephala islandica | sv | 55623245 | 1324201424 | pending |
| 53 | Q1270808 | alcedinidae | Halcyon leucocephala | Grey-headed Kingfisher | sv | 56466565 | 1315068381 | pending |
| 54 | Q21127307 | alcedinidae | Corythornis cristatus | Malachite Kingfisher | sv | 56775753 | 1314595144 | pending |
| 55 | Q735158 | alcedinidae | Halcyon smyrnensis | White-throated Kingfisher | sv | 55885523 | 1315217781 | pending |
| 56 | Q387379 | anhingidae | Afrikansk ormhalsfågel | Anhinga rufa | sv, en | 56594862 | 1352193487 | pending (afrikansk vagrant; ingen svwiki, en lead för kort) |
| 57 | Q772286 | apodidae | Palmseglare | Cypsiurus parvus | sv | — | — | pending (afrikansk, ingen svwiki) |
| 58 | Q1096617 | apodidae | Madeiraseglare | Apus unicolor | sv | — | — | pending (Madeira/Kanarie endemic, ingen svwiki) |
| 59 | Q1264567 | apodidae | Forbes-Watsonseglare | Apus berliozi | sv, en | — | — | pending (afrikansk, båda wikis sparse) |
| 60 | Q118608 | ardeidae | Goliathhäger | Ardea goliath | sv | — | — | pending (afrikansk) |
| 61 | Q126216 | ardeidae | Natthäger | Nycticorax nycticorax | sv | — | — | pending (sällsynt i SE) |
| 62 | Q130730 | ardeidae | Ägretthäger | Ardea alba | sv | — | — | pending (växer söderut i SE men svwiki sparse) |
| 63 | Q132482576 | ardeidae | Strandhäger | Butorides atricapilla | sv | — | — | pending (afrikansk) |
| 64 | Q392570 | ardeidae | Revhäger | Egretta gularis | sv | — | — | pending (atlantkust Afrika) |
| 65 | Q498428 | ardeidae | Damrallhäger | Ardeola grayii | sv | — | — | pending (sv summary 71w under 80-tröskeln) |
| 66 | Q888536 | ardeidae | Kinesisk dvärgrördrom | Botaurus sinensis | sv | — | — | pending (asiatisk) |
| 67 | Q27074817 | bucerotidae | Gråtoko | Lophoceros nasutus | sv | — | — | pending (afrikansk vagrant, ingen svwiki) |
| 68 | Q1002588 | burhinidae | Fläcktjockfot | Burhinus capensis | sv | 57974200 | 1318547566 | pending (afrikansk, ingen svwiki) |
| 69 | Q1260062 | burhinidae | Strandtjockfot | Esacus recurvirostris | sv | 55735322 | 1314148858 | pending (sydostasiatisk, ingen svwiki) |
| 70 | Q184834 | burhinidae | Tjockfot | Burhinus oedicnemus | en | 57857788 | 1313792943 | pending (enwiki lead 20w — exakt SPARSE_WORD_THRESHOLD trots välkänd EU-art) |
| 71 | Q922125 | burhinidae | Senegaltjockfot | Burhinus senegalensis | sv | 58603103 | 1326547544 | pending (afrikansk, ingen svwiki) |
| 72 | Q1137192 | caprimulgidae | Rödhalsad nattskärra | Caprimulgus ruficollis | sv | — | — | pending (sydeuropeisk, ingen svwiki) |
| 73 | Q1264019 | caprimulgidae | Guldnattskärra | Caprimulgus eximius | sv + image | — | — | pending (sahelisk; saknar hi-res Commons-foto) |
| 74 | Q1265442 | caprimulgidae | Bergnattskärra | Caprimulgus poliocephalus | sv | — | — | pending (afrikansk, ingen svwiki) |
| 75 | Q1269353 | caprimulgidae | Nubisk nattskärra | Caprimulgus nubicus | sv | — | — | pending (afrikansk, ingen svwiki) |
| 76 | Q1270252 | caprimulgidae | Sahelnattskärra | Caprimulgus inornatus | sv | — | — | pending (afrikansk, ingen svwiki) |
| 77 | Q2752089 | caprimulgidae | Sindnattskärra | Caprimulgus mahrattensis | en | 56773477 | — | pending (asiatisk; enwiki lead för kort) |

`svwiki rev` / `enwiki rev` är revisions-ID för den artikel pipelinen senast hämtade. `—` = artikel saknas helt på språket. Om revisionsnumret går upp innebär det att svwiki har vuxit — bra signal att retry:a `refresh --field text --force`.

**Status-värden:**
- `pending` — gap kvar
- `upstream-fixed` — svwiki/enwiki har vuxit, retry bekräftade ≥80 ord
- `curated` — manuell text inlagd, overrides-posten borttagen

## Prioritering

Arter markerade `(allmän — prioriterad)` är `abundance: allmän` i Sverige och syns mycket i UI:n — fyll i dom först om du ändå sätter dig och skriver.

## När planet anropar denna doc

Refererad från CLAUDE.md "Plan 2b status"-sektionen. Nya gap från framtida familj-batches läggs till nederst i tabellen automatiskt — kör samma audit-script som genererade den initiala tabellen (se `git log` på denna fil för formatet).
