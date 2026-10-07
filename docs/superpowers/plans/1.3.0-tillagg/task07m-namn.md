### Task 7m (2026-10-07): svenska namn enligt BirdLife Sverige

**Källa:** BirdLife Sveriges taxonomikommitté, "Officiella svenska namn på alla världens fågelarter, version 2025" (fliken "NL v 2025" i `NL20.xlsx`, uppdaterad november 2025, hämtad 2026-10-07 från https://birdlife.se/tk/svenska-namn-pa-varldens-faglar/), plus listans ändringslogg (https://birdlife.se/tk/svenska-namn-pa-varldens-faglar/forandringar-i-listan/). Själva filen ligger inte i repot (ingen licens angiven).

**Metod:** alla 839 arters `names.sv` jämförda med listan på vetenskapligt namn (838 träffar, 1 utan träff), skiftläge ignorerat. Varje avvikelse kontrollerad för hand: samma engelska namn i listan och i Birdy för alla 29 nedan, alltså samma taxon. Listan följer AviList sedan juni 2025 (Birdy följer IOC); sammanslagningar i AviList kontrollerade mot ändringsloggen.

**Resultat:** 29 namn byts (Albin räknade 30 i Task 7g; den 30:e är gråkråkan, se nedan). Det gamla namnet ligger kvar som `names.former_sv`: sökord i appen och raden "Tidigare: …" på den svenska artprofilen. Allt står även som `common_sv` + `former_sv` i `tools/content-pipeline/species_list.yaml`, så en pipelinekörning behåller dem. Låst i `SpeciesContentCorrectionsTest`.

| Vetenskapligt namn | Wikidata | Tidigare i Birdy | Officiellt namn | Not |
|---|---|---|---|---|
| Alaudala heinei | Q110812143 | Turkestandvärglärka | Turkestanlärka | |
| Anser fabalis | Q26452 | Sädgås | Skogsgås | NL 2025: sädgås delas, tajgasädgås = skogsgås |
| Anser serrirostris | Q673280 | Tundrasädgås | Tundragås | NL 2025 |
| Calonectris diomedea | Q216850 | Gulnäbbad lira | Diomedeslira | NL 2025 (scopolilira). Delade namnet med C. borealis (gulnäbbad lira) |
| Calonectris edwardsii | Q181323 | Kapverdelira | Större kapverdelira | NL 2025 |
| Cettia cetti | Q650114 | Cettisångare | Sumpcettia | NL 2025 |
| Chersophilus duponti | Q1266617 | Dupontlärka | Skymningslärka | NL 2025 |
| Colinus virginianus | Q142651 | Vitstrupig vaktel | Virginiavaktel | |
| Curruca mystacea | Q110257505 | Östlig sammetshätta | Tamarisksångare | NL 2025 |
| Curruca subalpina | Q110257513 | Moltonisångare | Rosensångare | NL 2025 |
| Dendropicos goertae | Q946681 | Afrikansk gråspett | Gulbukig askspett | |
| Eremalauda dunni | Q1092087 | Streckig ökenlärka | Saharalärka | |
| Foudia madagascariensis | Q1060466 | Rödfody | Röd fody | |
| Fringilla teydea | Q847168 | Blåfink | Teneriffablåfink | |
| Gyps rueppelli | Q55111925 | Rüppellgam | Fläckgam | NL 2025 |
| Hydrophasianus chirurgus | Q18861 | Fasanjaçana | Fasanjassana | |
| Merops persicus | Q1951359 | Blåkindad biätare | Grön biätare | |
| Mirafra javanica | Q1083050 | Australisk lärka | Drillärka | Singing Bush Lark i båda listorna |
| Pelagodroma marina | Q845982 | Fregattstormsvala | Fregatthavslöpare | |
| Phylloscopus sindianus | Q3729103 | Kashmirgransångare | Berggransångare | |
| Ploceus manyar | Q1306610 | Streckig vävare | Streckad vävare | |
| Psittacula eupatria | Q753746 | Alexanderparakit | Storparakit | NL 2025 |
| Pterodroma feae | Q1261612 | Kap Verdepetrell | Kapverdepetrell | |
| Puffinus boydi | Q3410601 | Boydlira | Mindre kapverdelira | NL 2025 |
| Puffinus yelkouan | Q511566 | Levantlira | Medelhavslira | AviList slår ihop balearisk lira med medelhavslira; Birdy har ingen P. mauretanicus och kallar arten Mediterranean Shearwater |
| Sitta krueperi | Q851556 | Krüpers nötväcka | Turknötväcka | NL 2025 |
| Sitta tephronota | Q928415 | Östlig klippnötväcka | Ravinnötväcka | |
| Strix butleri | Q1272529 | Klippuggla | Östlig klippuggla | Birdy har även S. hadorami = västlig klippuggla |
| Zosterops abyssinicus | Q3178456 | Abessinsk glasögonfågel | Abessinglasögonfågel | |

"NL 2025" = namnjustering i listans version 2025 (ändringsloggen, 10 juni 2025). Övriga skilde sig redan före 2025 eller kom från Wikidatas etikett.

**Inte bytta (för Albin):**
- **Gråkråka (Corvus cornix, Q25405):** finns inte i listan. AviList slår ihop gråkråka med svartkråka till en art, "kråka" (Corvus corone). Birdy (IOC) har kvar två arter, så namnet står kvar.
- **Kråka (Corvus corone, Q26198, Carrion Crow):** samma namn som listan, men listans "kråka" är den sammanslagna arten. För Birdys smalare art var det officiella namnet "svartkråka" fram till juni 2025. Albins beslut: behålla Kråka eller byta till Svartkråka.

**Hur det används:**
- Pipelinen: `former_sv` i `species_list.yaml` → `names.former_sv` i artens YAML (skrivs bara för omdöpta arter).
- Databasen: det gamla namnet läggs till den svenska namnradens `search_text` och sparas som en `SpeciesText`-rad med `kind = former_name`, `locale = sv` (ingen schemaändring). Sökningen rankar det efter de nuvarande namnen ("sädgås" ger Skogsgås först).
- Appen: `Species.formerName` (bara på svenska) → raden "Tidigare: Sädgås" under det vetenskapliga namnet på artprofilen. Engelska användare ser ingen rad.
- Artsidorna (`birdy-fetcher web`) läser `names.sv` ur samma YAML och får de nya namnen; ingen sida är publicerad ännu, så inga adresser ändras.

**Kvar efter 1.3.0 (texterna är inte ändrade i den här tasken):**
- De svenska beskrivnings- och flyttningstexterna för 12 arter använder fortfarande det gamla namnet (t.ex. "Sädgåsen …"): Skogsgås, Tundragås, Diomedeslira, Sumpcettia, Skymningslärka, Virginiavaktel, Tamarisksångare, Rosensångare, Saharalärka, Röd fody, Teneriffablåfink (ordet "blåfink"), Fregatthavslöpare. Raden "Tidigare: …" förklarar det tills texterna skrivs om.
- Diomedesliran (Q216850): den svenska texten handlar om gulnäbbad lira (C. borealis, Atlanten), skriven ur svenska Wikipedias artikel om fel art eftersom arten hette så i Birdy. Den engelska texten är tom. Förslag: töm de svenska texterna (appen visar sin tomtext) eller kör om pipelinen för arten nu när namnet pekar på artikeln "Diomedeslira".
