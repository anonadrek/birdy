# R3 trial run: Opus 5 vs Opus 5.5 on four species

Run 2026-10-07 on Talgoxe, Bofink, Råka and Koboltmes, with the same Wikipedia articles and the same starting records in both variants.

- **A (plan defaults, committed as `70504baf` on `data/artsidor`):** facts and text by Claude Opus 5 (`high`); the V1 fact check and the text check by Claude Sonnet 5.
- **B (not committed):** facts and text by Claude Opus 5.5 (`claude-opus-5-5`, `high`); V1 and the text check unchanged (Sonnet 5).

All four species came through every step in both variants, with no failures, retries or refusals. This is four species and one run each, so read the quality verdict as a strong hint, not a measurement.

## Cost

| Step | A per species | B per species | A, 180 species | B, 180 species |
|---|---|---|---|---|
| Fact sheet (`web facts`) | $0.32 | $0.21 | $57.60 | $36.90 |
| V1 check (`web verify`, Sonnet) | $0.07 | $0.06 | $13.00 | $11.30 |
| Text + text check (`web write`) | $0.32 | $0.23 | $56.70 (wave 1, 40 species: $12.60) | $41.90 (wave 1, 40 species: $9.30) |
| **Total** | **$0.71** | **$0.50** | **$127.40** | **$90.00** |

Run totals from the reports: A $1.28 + $0.29 + $1.26 = **$2.83**; B $0.82 + $0.25 + $0.93 = **$2.00**. The reports give one total per run, so per species = run total / 4 (Koboltmes has shorter articles than the other three). B is about 30 % cheaper. That is more than the 20 % price difference because Opus 5 wrote more than the prompt asked for (see below). B was also faster: 1 min 22 s for the fact sheets vs 2 min 20 s.

Both variants come in well under the plan's estimates (about $75 for R4, about $25 for R6 wave 1).

## Talgoxe (Q25485)

### What V1 struck

- **A:** `f20` (partial). The fact says Talgoxen "kan vara mycket vanlig i städer, parker och trädgårdar", but the quote mentions only cities, not parks or gardens. Correct strike. V1 also struck the status `s01` *Stannfågel* (unsupported); see the next point.
- **B:** only `s01` *Stannfågel* (unsupported), for the same reason.
- The status strike is a pipeline bug, not a model problem. V1 is shown only the **first** quote of each fact (`render_facts_for_check` uses `sources[0]`). Both models gave the right second quote ("Par stannar vanligen nära eller inom sitt revir hela året runt ..."), but V1 saw only "Talgoxen finns i hela Sverige ...", which says nothing about migration. Every struck status becomes a flag that Albin has to decide on.
- V4 (BirdNET) struck Talgoxe's recording in both variants. Great Tit was the top guess in 2 of 7 windows, but at only 2.3 % confidence against a 10 % threshold, and no window reached 3 % for any species. Either the clip (XC165660) is faint, or the 10 % threshold is too strict for these clips. Worth checking in R4b before many recordings are lost.

### Fact sheet A (Opus 5): 30 facts kept, plus the status and 3 data facts

Opus 5 returned 43 facts. The prompt asks for 10 to 30, and the code keeps the first 30 *in the order the model gave them*, so 13 facts were dropped without being checked: 2 on breeding, **all 4 on food and all 7 on behaviour**. That is why A's page says nothing about what a Great Tit eats or how it behaves.

- `f01` *appearance*: Talgoxen känns igen på svart huvud och hals, framträdande vita kinder, olivgrön ovansida och gul undersida.
  - sv: “Talgoxen är lätt igenkännlig med svart huvud och hals, framträdande vita kinder, olivgrön ovansida och gul undersida”
- `f02` *appearance*: Bröstet är klart citrongult med en svart längsgående rand på mitten som löper från haklappen till kloaköppningen.
  - sv: “Bröstet är klart citrongult med en svart längsgående rand mitt på, som löper från haklappen till kloaköppningen.”
- `f03` *appearance*: Vingtäckarna är gröna medan resten av vingen är blågrå med ett vitt vingband.
  - sv: “Vingtäckarna är gröna, resten av vingen är blågrå med ett vitt vingband.”
- `f04` *appearance*: Stjärten är blåaktigt grå med vita ytterkanter.
  - sv: “Stjärten är blåaktigt grå med vita ytterkanter.”
- `f05` *appearance*: Näbben är konisk och kullrig, ungefär dubbelt så lång som hög.
  - sv: “Näbben är konisk, kullrig och ungefär dubbelt så lång som hög.”
- `f06` *appearance*: Ben och fötter är blågrå till skiffergrå.
  - de: “Die Beine und Füße sind blaugrau bis schiefergrau.”
- `f07` *sex_age*: Honan liknar hanen men är blekare, med mindre intensivt svart haklapp och smalare, ibland avbruten buksträng.
  - sv: “Honans fjäderdräkt liknar hanens förutom att färgerna på det hela taget är blekare; haklappen är mindre intensivt svart, liksom randen som löper nedför magen, vilken också är smalare och ibland avbruten.”
- `f08` *sex_age*: Ungfåglar liknar honan men har blekt olivbruna nackar och halsar, gråaktig gump och gråare stjärt.
  - sv: “Ungfåglar liknar honan, förutom att de har blekt olivbruna nackar och halsar, gråaktig gump, och gråare stjärtar med mindre markerade vita spetsar.”
- `f09` *size*: Talgoxen är en stor mes som mäter 12,5–14,0 centimeter och väger 16–21 gram.
  - sv: “Talgoxen är en stor mes som mäter 12,5–14,0 cm och den har ett distinkt utseende som gör arten lätt att känna igen. Den väger 16–21 gram.”
- `f10` *size*: Med 13–15 centimeters kroppslängd är talgoxen den största mesen i Europa.
  - de: “Die Kohlmeise zählt mit 13–15 cm Körperlänge zu den größeren Meisenarten und ist die größte Meise in Europa.”
- `f11` *voice*: Talgoxen är en ljudlig fågel med upp till 40 olika sorters läten och sånger.
  - sv: “Talgoxen är, liksom andra mesar, en ljudlig fågel, och har upp till 40 sorters läten och sånger.”
- `f12` *voice*: Ett av de mest kända lätena är ett "tí-ta, tí-ta" som liknas vid ett gnissligt skottkärrehjul och markerar revir.
  - sv: “Ett av de mest välkända lätena är ett "tí-ta, tí-ta", som ofta liknas vid ett gnissligt skottkärrehjul, vilket används för att hävda revirägande.”
- `f13` *voice*: Ett bofinkaktigt "ping, ping" hörs ofta från talgoxen.
  - sv: “Ett bofinkaktigt "ping, ping" hörs ofta.”
- `f14` *voice*: Mjuka enstaka toner som "pit", "spick" eller "tjitt" används som kontaktläten.
  - sv: “Mjuka enstaka toner som "pit", "spick" eller "tjitt" används som kontaktläten.”
- `f15` *voice*: Ett högljutt "tink" används av vuxna hanar som larm eller vid revirstrider.
  - sv: “Ett högljutt "tink"används av vuxna hanar som larm eller i revirstrider.”
- `f16` *voice*: Den metalliskt ljusa sången hörs från tidig vår och delvis redan på vintern.
  - de: “Ab dem zeitigen Frühjahr und teilweise auch schon im Winter ist der recht auffällige, metallisch-helle Gesang zu vernehmen”
- `f17` *habitat*: Talgoxen återfinns oftast i öppna lövskogslandskap, blandskog och skogsbryn.
  - sv: “Den återfinns oftast i öppna lövskogslandskap, blandskog och skogsbryn.”
- `f18` *habitat*: I täta skogar, inklusive barrskogar, håller den vanligen till i gläntor.
  - sv: “I täta skogar, däribland barrskogar, återfinns den vanligen i gläntor.”
- `f19` *habitat*: Den häckar främst i löv- och blandskogar med minst 60 år gamla träd som ger tillräckligt med bohål.
  - de: “Die Kohlmeise brütet primär in Laub- und Mischwäldern, deren Baumbestand mit 60 oder mehr Jahren alt genug ist, um ein genügendes Angebot an Nisthöhlen zu gewährleisten”
- `f20` *habitat*: Talgoxen har anpassat sig till människans miljöer och kan vara mycket vanlig i städer, parker och trädgårdar. **(struck by V1)**
  - sv: “men den har anpassat sig till människors miljöer, inklusive städer, där den kan vara mycket vanlig”
- `f21` *sweden*: Talgoxen finns i hela Sverige och är i mellersta och södra Sverige en av de vanligaste mesarna.
  - sv: “Talgoxen finns i hela Sverige och är i mellersta och södra Sverige en av de vanligaste mesarna.”
- `f22` *sweden*: Med 709 884 ringmärkta individer åren 1911–2008 är talgoxen en av Sveriges vanligast ringmärkta fågelarter.
  - sv: “Med 709 884 individer ringmärkta åren 1911–2008 utgör talgoxen en av de vanligast ringmärkta fågelarterna i Sverige.”
- `f23` *sweden*: Arten saknas på Island och i norra Skandinavien men finns annars över hela Europa.
  - sv: “Den återfinns över hela Europa förutom Island och norra Skandinavien, inklusive flera öar i Medelhavet.”
- `f24` *sweden*: Under hårda vintrar kan grupper på upp till tusen fåglar oväntat flytta från norra Europa till Östersjöområdet och längre söderut.
  - sv: “Populationer kan bli invasionsflyttare under hårda vintrar, vilket innebär att grupper på upp till tusen fåglar utan förvarning kan flytta från norra Europa till Östersjöområdet, Nederländerna, Storbritannien och till och med till södra Balkan.”
- `f25` *sweden*: Talgoxen har utvidgat sitt utbredningsområde norrut till Skandinavien och Skottland.
  - sv: “Talgoxen har utvidgat sitt utbredningsområde norrut till Skandinavien och Skottland och söderut till Israel och Egypten.”
- `f26` *breeding*: Talgoxen är hålhäckare som oftast häckar i träd, ibland i husväggar eller bergväggar, och använder gärna holkar.
  - sv: “Talgoxar är hålhäckare som vanligen häckar i träd, men ibland i husväggar eller bergväggar, och använder gärna holkar.”
- `f27` *breeding*: Kullen kan vara så stor som 18 ungar, men fem till tolv är vanligare.
  - sv: “Antalet ungar i en kull är ofta mycket stort, så många som 18, men fem till tolv är vanligare.”
- `f28` *breeding*: Äggen är cirka 18 millimeter stora och vita med rödaktiga fläckar.
  - sv: “Äggen är cirka 18 millimeter stora och vita med rödaktiga fläckar.”
- `f29` *breeding*: Honan ruvar ensam och matas av hanen, och ruvningen varar i 12 till 15 dagar.
  - sv: “Honan sköter hela ruvningen och utfodras då av hanen.”
  - sv: “Ruvningen varar i 12 till 15 dagar.”
- `f30` *breeding*: Ungarna stannar i boet i 16 till 22 dagar och blir oberoende åtta dagar efter att de blivit flygga.
  - sv: “Ungarna stannar i boet i 16 till 22 dagar och blir oberoende av föräldrarna åtta dagar efter att ha blivit flygga.”
- `s01` *status* = `resident`: Stannfågel **(struck by V1)**
  - sv: “Talgoxen finns i hela Sverige och är i mellersta och södra Sverige en av de vanligaste mesarna.”
  - sv: “Par stannar vanligen nära eller inom sitt revir hela året runt, till och med i de norra delarna av utbredningsområdet.”
  - de: “In Europa harren sogar nördlich des Polarkreises noch viele Kohlmeisen in den Brutgebieten aus.”
- `d01` *data (Artportalen/red list, not checked by V1)*: Rapporteras mest i oktober till februari.
- `d02` *data (Artportalen/red list, not checked by V1)*: Vanligast i rapporterna från Norrbotten, Västerbotten och Västernorrland.
- `d03` *data (Artportalen/red list, not checked by V1)*: Inte rödlistad i Svenska rödlistan 2025.

### Fact sheet B (Opus 5.5): 30 facts, plus the status and 3 data facts

Opus 5.5 stayed within 30, so nothing was cut. Its coverage is more even (3 facts on food, 3 on behaviour, and the song). Its quotes are tighter, split at sentence boundaries instead of one long quote, and its Swedish uses proper ”…” quotation marks.

- `f01` *appearance*: Talgoxen har svart huvud och hals, framträdande vita kinder, olivgrön ovansida och gul undersida.
  - sv: “Talgoxen är lätt igenkännlig med svart huvud och hals, framträdande vita kinder, olivgrön ovansida och gul undersida”
- `f02` *appearance*: Bröstet är klart citrongult med en svart längsgående rand som löper från haklappen till kloaköppningen.
  - sv: “Bröstet är klart citrongult med en svart längsgående rand mitt på, som löper från haklappen till kloaköppningen.”
- `f03` *appearance*: Vingtäckarna är gröna och resten av vingen är blågrå med ett vitt vingband.
  - sv: “Vingtäckarna är gröna, resten av vingen är blågrå med ett vitt vingband.”
- `f04` *appearance*: Stjärten är blåaktigt grå med vita ytterkanter.
  - sv: “Stjärten är blåaktigt grå med vita ytterkanter.”
- `f05` *appearance*: Benen och fötterna är blågrå till skiffergrå.
  - de: “Die Beine und Füße sind blaugrau bis schiefergrau.”
- `f06` *size*: Talgoxen är en stor mes som mäter 12,5–14,0 cm och väger 16–21 gram.
  - sv: “Talgoxen är en stor mes som mäter 12,5–14,0 cm”
  - sv: “Den väger 16–21 gram.”
- `f07` *sex_age*: Honan liknar hanen men är blekare, med mindre intensivt svart haklapp och en smalare, ibland avbruten bukrand.
  - sv: “Honans fjäderdräkt liknar hanens förutom att färgerna på det hela taget är blekare; haklappen är mindre intensivt svart, liksom randen som löper nedför magen, vilken också är smalare och ibland avbruten.”
- `f08` *sex_age*: Ungfåglar liknar honan men har blekt olivbruna nackar och halsar, gråaktig gump och gråare stjärt med mindre markerade vita spetsar.
  - sv: “Ungfåglar liknar honan, förutom att de har blekt olivbruna nackar och halsar, gråaktig gump, och gråare stjärtar med mindre markerade vita spetsar.”
- `f09` *voice*: Ett av de mest välkända lätena är ett ”tí-ta, tí-ta” som ofta liknas vid ett gnissligt skottkärrehjul.
  - sv: “Ett av de mest välkända lätena är ett "tí-ta, tí-ta", som ofta liknas vid ett gnissligt skottkärrehjul”
- `f10` *voice*: Ett bofinkaktigt ”ping, ping” hörs ofta.
  - sv: “Ett bofinkaktigt "ping, ping" hörs ofta.”
- `f11` *voice*: Mjuka enstaka toner som ”pit”, ”spick” eller ”tjitt” används som kontaktläten.
  - sv: “Mjuka enstaka toner som "pit", "spick" eller "tjitt" används som kontaktläten.”
- `f12` *voice*: Vuxna hanar använder ett högljutt ”tink” som larm eller i revirstrider.
  - sv: “Ett högljutt "tink"används av vuxna hanar som larm eller i revirstrider.”
- `f13` *voice*: Hanens revirsång är en serie metalliskt rena, höga och ljudliga motiv.
  - de: “Der Reviergesang der Männchen ist eine Reihe metallisch reiner, hoher und lauter Motive”
- `f14` *habitat*: Talgoxen finns oftast i öppna lövskogslandskap, blandskog och skogsbryn.
  - sv: “Den återfinns oftast i öppna lövskogslandskap, blandskog och skogsbryn.”
- `f15` *habitat*: I täta skogar, däribland barrskogar, finns den vanligen i gläntor.
  - sv: “I täta skogar, däribland barrskogar, återfinns den vanligen i gläntor.”
- `f16` *habitat*: Talgoxen är en vanlig fågel i stadsparker och trädgårdar.
  - sv: “är en vanlig fågel i stadsparker och trädgårdar”
- `f17` *sweden*: Talgoxen finns i hela Sverige och är i mellersta och södra Sverige en av de vanligaste mesarna.
  - sv: “Talgoxen finns i hela Sverige och är i mellersta och södra Sverige en av de vanligaste mesarna.”
- `f18` *sweden*: I Fennoskandien sker den huvudsakliga äggläggningen mellan slutet av april och mitten av maj.
  - de: “in Fennoskandien und Russland liegt sie zwischen Ende April und Mitte Mai”
- `f19` *breeding*: Talgoxen häckar i hål, vanligen i träd men ibland i husväggar eller bergväggar, och använder gärna holkar.
  - sv: “Talgoxar är hålhäckare som vanligen häckar i träd, men ibland i husväggar eller bergväggar, och använder gärna holkar.”
- `f20` *breeding*: Kullen kan omfatta upp till 18 ägg, men fem till tolv är vanligare.
  - en: “The number in the clutch is often very large, as many as 18, but five to twelve is more common.”
- `f21` *breeding*: Äggen är cirka 18 millimeter stora och vita med rödaktiga fläckar.
  - sv: “Äggen är cirka 18 millimeter stora och vita med rödaktiga fläckar.”
- `f22` *breeding*: Honan sköter hela ruvningen, som varar 12 till 15 dagar, och utfodras då av hanen.
  - sv: “Honan sköter hela ruvningen och utfodras då av hanen.”
  - sv: “Ruvningen varar i 12 till 15 dagar.”
- `f23` *breeding*: Ungarna stannar i boet i 16 till 22 dagar.
  - sv: “Ungarna stannar i boet i 16 till 22 dagar”
- `f24` *breeding*: De flesta år föder paret upp två kullar.
  - sv: “De flesta år föder paret upp två kullar.”
- `f25` *food*: På sommaren äter talgoxen främst insekter och spindlar som den plockar från löv.
  - sv: “På sommaren äter talgoxar främst insekter och spindlar som de fångar genom att plocka dem från löv.”
- `f26` *food*: Under höst och vinter, när insekterna blir färre, kompletterar talgoxen födan med bär och frön.
  - sv: “Under höst och vinter på norra halvklotet, då färre insekter finns tillgängliga, utökar talgoxen sin föda med bär och frön.”
- `f27` *food*: Vid fågelbord tar den gärna matrester, jordnötter och solrosfrön.
  - sv: “När det finns tillgängligt tar de gärna matrester, jordnötter och solrosfrön från fågelbord.”
- `f28` *behaviour*: På vintern födosöker talgoxar tillsammans med andra mesar i artblandade grupper, så kallade meståg.
  - sv: “Talgoxar och andra mesar bildar artblandade grupper, så kallade meståg, som födosöker tillsammans under vintern.”
- `f29` *behaviour*: Stora frön eller byten håller den med en eller båda fötterna och hackar på med näbben.
  - sv: “Stora matbitar, som stora frön eller byten, hanteras genom att fågeln håller föremålet med ena foten eller båda fötterna och slår på det med näbben”
- `f30` *behaviour*: Särskilt på vintern ses talgoxen ofta söka föda på marken.
  - de: “vor allem im Winter ist die Art bei der Nahrungssuche viel am Boden anzutreffen”
- `s01` *status* = `resident`: Stannfågel **(struck by V1)**
  - sv: “Talgoxen finns i hela Sverige och är i mellersta och södra Sverige en av de vanligaste mesarna.”
  - sv: “Par stannar vanligen nära eller inom sitt revir hela året runt, till och med i de norra delarna av utbredningsområdet.”
- `d01` *data (Artportalen/red list, not checked by V1)*: Rapporteras mest i oktober till februari.
- `d02` *data (Artportalen/red list, not checked by V1)*: Vanligast i rapporterna från Norrbotten, Västerbotten och Västernorrland.
- `d03` *data (Artportalen/red list, not checked by V1)*: Inte rödlistad i Svenska rödlistan 2025.

### Page text A (Opus 5), with fact ids

#### Swedish
- *lead*: Talgoxen är en stor mes med svart huvud, vita kinder och gul undersida. [f09,f01] Den ses oftast i öppna lövskogslandskap, blandskog och skogsbryn, och i tätare skog i gläntor. [f17,f18]
- *fieldMarks*: Svart huvud och hals med framträdande vita kinder och olivgrön ovansida [f01] Klart citrongult bröst med svart längsrand från haklappen till kloaköppningen [f02] Gröna vingtäckare, i övrigt blågrå vinge med ett vitt vingband [f03] Honan blekare än hanen, med smalare och ibland avbruten buksträng [f07]
- *voice*: Talgoxen är ljudlig och har upp till 40 olika läten och sånger. [f11] Mest känt är ett "tí-ta, tí-ta" som markerar revir. [f12] Ett bofinkaktigt "ping, ping" hörs ofta, och mjuka toner som "pit" är kontaktläten. [f13,f14] Ett högljutt "tink" används av vuxna hanar vid larm. [f15] Sången hörs från tidig vår och delvis redan på vintern. [f16]
- *whereWhen*: Talgoxen finns i hela Sverige och är i mellersta och södra Sverige en av de vanligaste mesarna. [f21] Den rapporteras mest i oktober till februari och är vanligast i rapporterna från Norrbotten, Västerbotten och Västernorrland. [d01,d02] Arten är inte rödlistad i Svenska rödlistan 2025. [d03]
- *behaviour*: Talgoxen är hålhäckare och häckar oftast i träd, ibland i husväggar eller bergväggar, och använder gärna holkar. [f26] Kullen kan vara så stor som 18 ungar, men fem till tolv är vanligare. [f27] Honan ruvar ensam i 12 till 15 dagar och matas av hanen. [f29] Ungarna stannar i boet i 16 till 22 dagar. [f30]
- *metaDescription*: Talgoxe: lär dig känna igen den på dräkt och vingband, hör hur lätena låter och läs var och när arten syns i Sverige under året.
- *facts*: {"size": {"value": "12,5 till 14,0 cm", "factIds": ["f09"]}, "swedenStatus": null}

#### English
- *lead*: The Great Tit is a large tit with a black head, white cheeks and a yellow underside. [f09,f01] It is usually seen in open broadleaf country, mixed woodland and woodland edges, and in clearings in denser forest. [f17,f18]
- *fieldMarks*: Black head and throat with bold white cheeks and olive green upperparts [f01] Bright lemon yellow breast with a black stripe running down the middle [f02] Green wing coverts, the rest of the wing blue grey with a white wing bar [f03] Female paler than the male, with a narrower, sometimes broken belly stripe [f07]
- *voice*: The Great Tit is noisy, with up to 40 different calls and songs. [f11] Best known is a "tee-ta, tee-ta" that marks territory. [f12] A chaffinch-like "ping, ping" is common, and soft notes like "pit" are contact calls. [f13,f14] A loud "tink" serves as an alarm from adult males. [f15] The song is heard from early spring and partly in winter. [f16]
- *whereWhen*: The Great Tit occurs throughout Sweden and is one of the commonest tits in central and southern Sweden. [f21] It is reported most from October to February, and most often from Norrbotten, Västerbotten and Västernorrland. [d01,d02] The species is not red-listed in the Swedish Red List 2025. [d03]
- *behaviour*: It is a hole nester, usually breeding in trees, sometimes in house walls or rock faces, and readily uses nest boxes. [f26] A brood can be as large as 18 young, but five to twelve is more usual. [f27] The female incubates alone for 12 to 15 days and is fed by the male. [f29] The young stay in the nest for 16 to 22 days. [f30]
- *metaDescription*: Great Tit: how to recognise it by plumage and wing bar, what its calls sound like, and where and when it is seen in Sweden.
- *facts*: {"size": {"value": "12.5 to 14.0 cm", "factIds": ["f09"]}, "swedenStatus": null}


### Page text B (Opus 5.5), with fact ids

#### Swedish
- *lead*: Talgoxen är en stor mes med svart huvud, vita kinder och gul undersida. [f06,f01] Den finns i hela Sverige och är en vanlig fågel i stadsparker och trädgårdar. [f17,f16]
- *fieldMarks*: Svart huvud och hals med framträdande vita kinder och olivgrön ovansida [f01] Citrongult bröst med en svart längsgående rand från haklappen till kloaköppningen [f02] Blågrå vinge med gröna täckare och vitt vingband, blåaktigt grå stjärt med vita kanter [f03,f04] Honan är blekare än hanen, med mattare svart haklapp och smalare, ibland avbruten bukrand [f07]
- *voice*: Ett välkänt läte är ett ”tí-ta, tí-ta”, som ofta liknas vid ett gnissligt skottkärrehjul. [f09] Ett bofinkaktigt ”ping, ping” hörs ofta, och som kontaktläte används mjuka enstaka toner som ”pit”, ”spick” eller ”tjitt”. [f10,f11] Vuxna hanar använder ett högljutt ”tink” som larm och i revirstrider. [f12] Hanens revirsång är en serie metalliskt rena, höga och ljudliga motiv. [f13]
- *whereWhen*: Talgoxen finns i hela Sverige och är i mellersta och södra Sverige en av de vanligaste mesarna. [f17] Den håller till i öppna lövskogslandskap, blandskog och skogsbryn, i täta skogar oftast i gläntor, och är vanlig i stadsparker och trädgårdar. [f14,f15,f16] Den rapporteras mest i oktober till februari. [d01] I Fennoskandien sker äggläggningen främst mellan slutet av april och mitten av maj. [f18]
- *behaviour*: På sommaren äter talgoxen främst insekter och spindlar som den plockar från löv, och på hösten och vintern kompletterar den med bär och frön. [f25,f26] Vid fågelbord tar den gärna matrester, jordnötter och solrosfrön. [f27] På vintern söker den föda i meståg tillsammans med andra mesar och ses då ofta på marken. [f28,f30] Stora frön och byten håller den med en eller båda fötterna och hackar på med näbben. [f29]
- *metaDescription*: Talgoxe: så känner du igen den på dräkten, hur sången och lätena låter, vad den äter, var den håller till och när den syns.
- *facts*: {"size": {"value": "12,5 till 14,0 cm", "factIds": ["f06"]}, "swedenStatus": null}

#### English
- *lead*: The Great Tit is a large tit with a black head, white cheeks and yellow underparts. [f06,f01] It occurs throughout Sweden and is a common bird in city parks and gardens. [f17,f16]
- *fieldMarks*: Black head and neck with prominent white cheeks and olive-green upperparts [f01] Lemon-yellow breast with a black stripe running from the bib to the vent [f02] Blue-grey wing with green coverts and white wing bar, bluish grey tail with white edges [f03,f04] Female paler than male, with duller black bib and narrower, sometimes broken belly stripe [f07]
- *voice*: A well-known call is "tí-ta, tí-ta", often likened to a squeaky wheelbarrow wheel. [f09] A chaffinch-like "ping, ping" is often heard, and soft single notes such as "pit", "spick" or "tjitt" are contact calls. [f10,f11] Adult males give a loud "tink" in alarm or territorial disputes. [f12] The male's territorial song is a series of clear, high, loud metallic phrases. [f13]
- *whereWhen*: The Great Tit occurs throughout Sweden and is one of the most common tits in central and southern Sweden. [f17] It lives in open deciduous woodland, mixed forest and forest edges, in dense forest mostly in clearings, and is common in city parks and gardens. [f14,f15,f16] It is reported most often from October to February. [d01] In Fennoscandia most eggs are laid between late April and mid May. [f18]
- *behaviour*: In summer it mainly eats insects and spiders picked from leaves, and in autumn and winter it adds berries and seeds. [f25,f26] At feeders it readily takes food scraps, peanuts and sunflower seeds. [f27] In winter it forages in mixed flocks with other tits and is often seen feeding on the ground. [f28,f30] It holds large seeds or prey with one or both feet and hammers at them with its bill. [f29]
- *metaDescription*: Great Tit: how to recognise it by its plumage, what its song and calls sound like, what it eats, where it lives and when it is seen.
- *facts*: {"size": {"value": "12.5 to 14.0 cm", "factIds": ["f06"]}, "swedenStatus": null}


### Sentences removed by the text checker

Talgoxe: none in A or B. In both, `swedenStatus` is empty because V1 struck the status.

Removed in the other species:
- A, Bofink: the English look-alike box (Lövsångare), because "Lövsångare is not among the look-alike facts". Opus 5 used a voice fact as a look-alike.
- A, Koboltmes: the English look-alike box, because it was over 35 words. The Swedish box stayed, so the two languages differ.
- B, Råka: the look-alike box in **both** languages, because it was over 35 words. Råka's page in B therefore has no "Kan förväxlas med" section, even though B's fact sheet had four good look-alike facts against Svartkråka: the bill, the flight, calls given singly vs in series of three or four, and walking vs hopping.

## The other three species

- **Bofink:** fine in both. V1 struck the status in both: A quoted the German "Nord- und Osteuropa ... Zugvögel", B quoted a population count (a weaker choice). A's text turned the data fact "Vanligast i rapporterna från Norrbotten, Västerbotten och Västernorrland" (the county where the species makes up the largest *share* of reports) into "flest rapporter kommer från Norrbotten ...", which says the most reports come from Norrland. That is false, and the text check let it through. B kept the original wording and added food, the double white wing bars and the northern limit of the range in Lappland.
- **Råka:** fine in both, and V1 struck the status in both. The quote says "stannfågel i Skåne", and V1 is right that this does not cover all of Sweden. A kept 2 look-alike facts and its look-alike box; B had 4 better ones but lost the box because it was too long. The look-alike species is written "C. corone" (A) or "C. corone corone" (B), because that is how the article abbreviates it, so it gets no QID and the box can't link to Svartkråka.
- **Koboltmes (does not occur in Sweden):** neither variant makes a false claim about Sweden. Neither article mentions Sweden, so both models correctly gave no status. But the page never says the bird does not occur in Sweden, and both texts end with "Arten är inte rödlistad i Svenska rödlistan 2025", which reads as if the species had been assessed in Sweden. V1 struck nothing in A, and one fact in B that dropped a hedge ("tydligen begränsad till tallskog" stated as certain). A's page has a look-alike box only in Swedish; B's has it in both languages.

## Assessment

**B (Opus 5.5) reads better and is at least as accurate, at 30 % lower cost.**

- **Accuracy:** V1 struck 1 of about 120 checked facts in each variant, not counting the three status facts (the pipeline bug above). A's strike was an overstated claim (parks and gardens); B's was a dropped hedge. The one clear factual error in a page text was A's ("flest rapporter ... Norrbotten" for Bofink).
- **Completeness:** A went over the 30-fact limit in 3 of 4 species, and the cut always hits the topics that come last: food, behaviour and look-alikes. B never went over. B's Talgoxe page covers the song, the food and the winter flocks; A's has none of that.
- **Language:** both write clean, natural Swedish and English. B's sentences are a little more concrete ("Titta på ryggen: koboltmesens är gråblå utan grönt").
- **B's weak spots:** the Råka look-alike box was too long and was removed. B also used the Artportalen data sentences less: no red-list line for Talgoxe and no data sentence at all for Råka. The page shows the data charts anyway.

**Recommendation:**
- Fact sheet: **Opus 5.5, `high`**.
- V1 fact check: **Sonnet 5, `high`** (unchanged).
- Text: **Opus 5.5, `high`**.
- Text check: **Sonnet 5** (unchanged).
- Projected cost for all 180 species: about $90 (facts $37, V1 $11, text $42), plus the comparison pages.

Switching needs a small code change that has to be committed:
- an `opus55` model key in `web/llm.py` (`MODELS` and `COST_KEYS`);
- its price in `cost.py` ($4 / $20 per million tokens);
- the model choices in `cli.py`;
- the defaults: `FACTS_MODEL_KEY` in `web/defaults.py` and the `web write --model` default.

Opus 5.5 defaults to `medium` effort, but the pipeline always sends the effort explicitly, so `high` holds. The plan says to rerun the trial after a change. Variant B already is that rerun, unless the fixes below change the prompts.

**Fix before R4, whichever model you choose (these are pipeline problems, not model problems):**
1. V1 sees only the first quote of each fact, so all three Swedish species lost their status and got a flag. Across 180 species, Albin would have to decide nearly every status by hand. Fix: pass all of a fact's quotes to V1.
2. The 30-fact cap cuts by position, so food, behaviour and look-alikes are dropped first. This matters less with Opus 5.5, but a cut per topic or a retry would be safer.
3. Species that don't occur in Sweden: the page should say so, and the red-list line should be hidden or worded differently.
4. The county data sentence ("Vanligast i rapporterna från Norrbotten ...") means the largest *share* of each county's reports, but it reads as "the most reports". The same three Norrland counties came out for both Talgoxe and Bofink. Reword it, or don't give it to the writer.
5. Look-alike species with abbreviated scientific names ("C. corone") don't match a QID.
6. The V4 threshold (see Talgoxe above).
