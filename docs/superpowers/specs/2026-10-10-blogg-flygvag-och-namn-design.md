# Bloggen som en flygväg, nya namn i menyn och "mer än fågel-ID" (design 2026-10-10)

## Albins val (lör 10 okt kväll, i chatten)

Efter "se till att artwork alltid flyger ihop" ville Albin mer än en gemensam riktning: "de fåglarna som flyger från huvudet ... ska vara som att de flyger upp till den ovanför, att de typ är kopplade till varandra", plus "field notes should be something else", "Species should also be something more telling or drawing" och "det är inte bara en bird ID ... det är en gamification". Valen:

1. **Flockens spår: "Into the post above".** Fåglarna som lämnar en bild flyger upp ur den och landar i flocken i inlägget ovanför, så att alla inlägg läses som en enda flygning. Sida vid sida på breda skärmar flyger de vidare in i nästa kort. På ett enskilt inlägg stiger de mot Birdys logga i menyn.
2. **"Field notes / Fältanteckningar" blir "Blog / Blogg"** i toppmenyn, sidfoten och bloggens egna etiketter.
3. **"Species / Arter" blir "Meet the birds / Möt fåglarna".**
4. **Budskapet "Identify, collect, learn":** Birdy är mer än ID; titeln och ingressen säger vad som händer efter ID:t, med appens egna ord (märken, livslista, veckor i rad, fältdagbok).

Två delar med var sin gren: del 1 är liten och tydlig, del 2 kräver bildarbete och en förhandsvisning till Albin före sammanslagningen.

## Del 1: namnen och budskapet (gren `website/namn-och-budskap`)

Adresserna ändras inte (`/blog/`, `/sv/blog/`, `/species/`, `/sv/arter/`), bara texterna. Ordvalet följer appen: "badges/märken", "life list/livslista", "weeks in a row/veckor i rad" (appens svit räknas i veckor, inte dagar).

| Nyckel i `copy.{en,sv}.json` | Engelska | Svenska |
|---|---|---|
| `nav.fieldNotes` | Blog | Blogg |
| `nav.species` | Meet the birds | Möt fåglarna |
| `footer.species` (kolumnrubriken) | Meet the birds | Möt fåglarna |
| `fieldNotes.kicker`, `blog.kicker` | Blog | Blogg |
| `fieldNotes.all`, `blog.allNotes` | All blog posts | Alla blogginlägg |
| `blog.moreNotes` | More from the blog | Mer från bloggen |
| `blog.indexTitle` | Blog: articles about Birdy and birds \| Birdy | Blogg: artiklar om Birdy och fåglar \| Birdy |
| `meta.title` | Birdy: Identify, collect and get to know the birds | Birdy: Känn igen, samla och lär känna fåglarna |
| `meta.description` | Identify 839 European bird species by photo, camera or song, then collect them: a private field journal, a life list and badges to earn. Explore the bird guide offline. Available for Android; coming to iPhone. | Identifiera 839 europeiska fågelarter med foto, kamera eller sång och samla dem: en privat fältdagbok, en livslista och märken att förtjäna. Utforska fågelguiden offline. Finns för Android, kommer till iPhone. |
| `hero.lead` | Point the camera, pick a photo or let the bird sing. Birdy suggests the species right on your phone, and every find goes into your field journal, onto your life list and towards your next badge. | Rikta kameran, välj ett foto eller låt fågeln sjunga. Birdy föreslår arten direkt i telefonen, och varje fynd hamnar i din fältdagbok, på din livslista och närmare nästa märke. |

Rubrikerna "Notes *from the field.*" och "From *the field journal.*" står kvar (de hör till fältdagbokens bildspråk). Testerna som läser etiketterna (`tests/home.spec.ts`, `tests/species.spec.ts`) följer med, och menyraden ska fortfarande rymmas i 1024 px. Grind: vakterna (i18n, no-accuracy, contrast, no-dashes, palette), enhetstesterna, bygget och Playwright mot `build:fixtures`.

Utanför webben, inte i den här grenen: visningsnamnet "Birdy: Bird ID" på de fyra kanalerna (TikTok låst till omkring 16 okt, Facebook låst 60 dagar efter granskningen, Instagram och YouTube har ett byte kvar per 14 dagar) och Play-titeln, där "Bird Identify" bär sökorden. Tas upp med Albin separat.

## Del 2: flygvägen mellan inläggen (gren `website/flocken-flyger-vidare`)

**Idén:** bloggens bilder är inte lösa kort utan en flock på väg. Fåglarna som lämnar en bilds flock flyger vidare till nästa bild och landar i dess flock. Kortens text ligger framför flygningen: fåglarna försvinner bakom texten och kommer fram i nästa bild, vilket ger djup utan dekor.

**Var:** bloggens lista (`FieldNotesIndex`: det stora kortet överst och rutnätet under), startsidans tre anteckningar (`FieldNotesTeaser`) och det enskilda inlägget (`FieldNoteArticle`: bilden överst mot loggan i menyn).

**Vilka par:** korten i listans ordning (nyast först). För varje bild utom den första går en flygning mellan den och föregående bild:
- ligger föregående bild **ovanför** (stapling på telefon, rutnätet under det stora kortet): flygningen går **uppåt** från den här bildens utgång till den föregående bildens landningspunkt;
- ligger föregående bild **till vänster i samma rad** (startsidan och rutnätet på breda skärmar): flygningen går **åt höger** från den föregående bildens utgång till den här bildens landningspunkt.
Så flyger fåglarna alltid uppåt eller åt höger, åt samma håll som flockarna tittar (regeln "flocken flyger ihop" i CLAUDE.md).

**Bildernas data:** varje flockbild anger i bildens egna koordinater (0 till 1) var dess eget spår lämnar bilden (`exit`), var ankommande fåglar landar i flocken (`land`), varifrån de helst kommer in (`enter`: kant och läge) och rutor där inga fåglar får flyga (`avoid`: bildens egna ord). `tools/render-note-art.mjs` skriver ut värdena för sina bilder (de räknas fram ur flocken och ordkolumnen), Birdy × AlbIT-bilden får dem för hand; allt samlas i en datafil som korten läser och skriver som `data-`attribut på bilden.

**Ritningen:** ett litet skript per lista lägger ett SVG-lager över listan (`aria-hidden`, `pointer-events: none`) och ritar fåglarna (samma `MARK` och `COLOURS` som flockarna) längs en mjuk kurva mellan punkterna, cirka 10 till 14 fåglar per flygning, var och en vänd efter kurvan (speglad i stället för upp och ner när kurvan går åt vänster), mindre och ljusare mitt på vägen. En fågel som skulle hamna över en textruta i något kort eller i en bilds `avoid`-ruta ritas inte, så flygningen går bakom texten. Lagret räknas om vid storleksändring och när bilderna laddats. Rörelse: när listan kommer in i bild flyger fåglarna fram längs varje väg från början till slut (cirka 1,2 s) och vilar sedan; med `prefers-reduced-motion` står de stilla från början. Utan JavaScript syns korten som i dag.

**Det enskilda inlägget:** bildens spår fortsätter upp mot Birdys logga i menyn (samma regler, loggan som landningspunkt). Går flygningen inte att rita utan att korsa text eller menyn hoppar sidan över den.

**Kontroll före förhandsvisningen:** skärmbilder av `/blog/`, `/sv/blog/`, startsidans anteckningar och ett inlägg i 1440, 1024, 768 och 390 px bredd; ingen fågel över text eller ord i bilderna, inga fåglar upp och ner, flygningarna börjar och slutar i flockarna. Testerna: enhetstester för par-regeln och kurvan (rena funktioner), Playwright för att lagret finns, är dolt för skärmläsare och inte ritar över text. Albin ser förhandsvisningen (länk och skärmbilder) innan grenen slås ihop.
