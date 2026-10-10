# Fältrapport och Troférum länkar till varandra (beslut 2026-10-09)

Albin ville att veckans fältrapport och troférummet ska hänvisa till varandra (2026-10-09: "fältrapporten och troferummet ska hänvisa till varandra"). `index.html` är skissen med tre alternativ, ritade på skärmarna som de ser ut i 1.3.1 (troférummet som Märken 1b:s hyllor, en per månad). Publicerad som https://claude.ai/artifact/KVQcy7YKif95ypzWSDcd1p.

**Beslut: alternativ A, "Stämpeln som bro"** (Albin 2026-10-09: "I dokumentet så tycker jag samma som dig").

- Kortet "Ny stämpel" i fältrapporten får raden "Se den i troférummet". Den öppnar troférummet på stämpelns hylla med stämpeln ringad en stund.
- Varje stämpel på hyllorna får sin vecka bredvid datumet ("v. 41"; ett annat år skrivs "v. 41, 2025"). Den öppnar den veckans fältrapport, även för gamla veckor: rapporten byggs ur sparade fynd när den öppnas.
- En vecka utan ny stämpel: kortet behåller "Ingen ny stämpel den här veckan." och nämner den stämpel som är närmast ("Närmast: Familjespanare, 18 av 20 familjer", med den befintliga strängen `badge_progress_counted`; framsteg räknas i arter, familjer eller veckor, inte i fynd). Bara i innevarande veckas rapport, eftersom en gammal rapport annars skulle visa dagens framsteg.
- En stämpels vecka är när appen delade ut den (samma regel som rapporten använder för att lista den), så länken landar alltid på en rapport som visar stämpeln. Fältmedlem kan hamna i en vecka utan fynd; den rapporten heter "En lugn vecka." och visar stämpeln, som i dag.

**Vad koden behöver** (`release/1.3.0` vid 7359f24e): `AppRoute.TrophyRoom` får ett argument (stämpeln som ska ringas), rapportens stämpelkort blir en knapp, rapportens viewmodel får märkesframsteg, och länkarna öppnas ovanpå skärmen så att Back går tillbaka (stega bakåt i stället för att stapla en slinga när målet redan ligger under). Detaljer med radnummer står i skissens sista avsnitt och i 1.3.1-planen, punkt 2.

**Öppet:** Märken 1b:s hyllor visar inget "Nära att låsa upp". Tas det bort ur troférummet ska raden "Närmast" i stället öppna fliken Märken. Märken 1a står inte i 1.3.1-tabellen, fast överlämningen för 1.3.0 sa att den flyttas till 1.3.1; bekräftas med Albin.
