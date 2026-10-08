# Bildkällor

Fotona är appens egna planschfoton (`asset-pack/src/main/assets/images/<QID>/hero.webp`), nedskalade till 1600 px. Metadata finns i `shared/content/species/**/<QID>.yaml`. De är CC0 eller public domain, så ingen namngivning krävs, men källan ska stå här för varje bild som läggs till (även bilder till blogginlägg). Undantaget är bilden till inlägget om See the song, som är CC BY-SA 4.0 och krediteras på sidan (se nedan).

| Fil | Art | QID | Fotograf | Licens | Används i |
|---|---|---|---|---|---|
| `talgoxe-q25485.webp` | Talgoxe | Q25485 | Hobbyfotowiki | CC0 | startsidans plansch när ingen artsida har ett fritt foto (bara testbygget utan arter) |
| `ladusvala-q25429.webp` | Ladusvala | Q25429 | Аимаина хикари | CC0 | Fältboken (planschen) |
| `skaggmes-q192817.webp` | Skäggmes | Q192817 | Hobbyfotowiki | CC0 | Ta med Birdy ut i fält |
| `rodhake-q25334.webp` | Rödhake | Q25334 | Rob Hille | Public domain | blogginlägget "Varför Birdy finns" (inläggets foto, korten och delningsbilden); delningsbilderna `public/og-field-{sv,en}.jpg` (tools/generate-og.mjs) |
| `see-the-song-blames-q25404.webp` | (ljudringen ur blåmesvideon, ingen fågel) | Q25404 | Birdy, ringen ritad ur Benoît Van Heckes inspelning | CC BY-SA 4.0 | blogginlägget "See the song" (inläggets bild, korten och delningsbilden) |

## Bilden och videon i inlägget See the song

`see-the-song-blames-q25404.webp` (1600×840) är raderna 340 till 1180 ur omslagsbilden till See the song-videon om blåmesen (renderad med `tools/social/see-the-song.mjs` på grenen `social/see-the-song`, till `tools/social/out/week1/eurasian-blue-tit/cover.jpg`, som inte ligger i git; kopian här och i `public/video/see-the-song/` är de som finns i git): ljudringen och frågetecknet, utan texten "Sound on" och utan raden birdy.community, med kortets espressobruna kant förlängd åt vänster. Ringen är ritad ur inspelningen [Cyanistes caeruleus - Eurasian Blue Tit XC538220.mp3](https://commons.wikimedia.org/wiki/File:Cyanistes_caeruleus_-_Eurasian_Blue_Tit_XC538220.mp3) av Benoît Van Hecke (CC BY-SA 4.0), så bilden har samma licens som videon, CC BY-SA 4.0.

Videon, omslaget och textspåren ligger i `public/video/see-the-song/`: `eurasian-blue-tit.mp4` (samma fil som `see-the-song.mp4`, oförändrad), `eurasian-blue-tit.jpg` (omslaget, oförändrat, används som affisch) och `eurasian-blue-tit.{sv,en}.vtt` (bara ljudet, utan fågelns namn). Videon är CC BY-SA 4.0 och innehåller fotot [Blaumeise (64) (34633517080).jpg](https://commons.wikimedia.org/wiki/File:Blaumeise_%2864%29_%2834633517080%29.jpg) av Kathy Büscher (CC BY 2.0, beskuret) och inspelningen ovan (bearbetad). Krediten står under videon i inlägget.

Startsidans plansch (Dagens fågel) visar artsidans eget foto helt, utan något ritat över det, och får därför visa foton under CC0, public domain, CC BY och CC BY-SA. Korten Fåglarna i månaden beskär fotot och visar därför bara CC0, public domain och CC BY, aldrig CC BY-SA (Albins fotoregler, `src/lib/daily-bird.mjs`). Fotograf, licens (länkad) och källa står under planschen och korten med samma rad som på artsidan (`src/components/species/PhotoCredit.astro`). Den AI-genererade rödhaken som heron byggde på före 2026-10-08 är borttagen.
