# Bildkällor

Fotona är appens egna planschfoton (`asset-pack/src/main/assets/images/<QID>/hero.webp`), nedskalade till 1600 px. Metadata finns i `shared/content/species/**/<QID>.yaml`. De är CC0 eller public domain, så ingen namngivning krävs, men källan ska stå här för varje bild som läggs till (även bilder till blogginlägg). Blogginläggens egna bilder i Flock-looken (2026-10-10) är ritade av Birdy i kod med `tools/render-note-art.mjs`, en fil per språk (`-en`, `-sv`): flocken bildar inläggets fågel efter en siluett ur `tools/social/cover/sil/` (PhyloPic, CC0), samma som på omslagen till See the song.

| Fil | Art | QID | Fotograf | Licens | Används i |
|---|---|---|---|---|---|
| `talgoxe-q25485.webp` | Talgoxe | Q25485 | Hobbyfotowiki | CC0 | startsidans polaroid när ingen artsida har ett fritt foto (bara testbygget utan arter) |
| `ladusvala-q25429.webp` | Ladusvala | Q25429 | Аимаина хикари | CC0 | Fältboken (planschen) |
| `skaggmes-q192817.webp` | Skäggmes | Q192817 | Hobbyfotowiki | CC0 | Ta med Birdy ut i fält |
| `rodhake-q25334.webp` | Rödhake | Q25334 | Rob Hille | Public domain | delningsbilderna `public/og-field-{sv,en}.jpg` (tools/generate-og.mjs); förslagen B och C till "Varför Birdy finns" i `tools/render-note-art.mjs` (inläggets foto fram till 2026-10-10) |
| `see-the-song-flock-q25404-{en,sv}.webp` | (omslaget till See the song: flocken bildar en blåmes) | Q25404 | Birdy, ritad i kod (`tools/render-note-art.mjs`); blåmesens siluett av Wouter Koch via [PhyloPic](https://www.phylopic.org/images/069c4833-e1ac-48e7-90d5-f7bd11000588) | Birdys egen; siluetten CC0 | blogginlägget "See the song" (inläggets bild, korten och delningsbilden) |
| `why-birdy-flock-q25334-{en,sv}.webp` | (flocken bildar en rödhake, bredvid raden Know the bird. Keep the moment.) | Q25334 | Birdy, ritad i kod (`tools/render-note-art.mjs`); rödhakens siluett av Anthony Caravaggi via [PhyloPic](https://www.phylopic.org/images/b0d54194-4105-4694-b670-b693df1640cf) | Birdys egen; siluetten CC0 | blogginlägget "Varför Birdy finns" (inläggets bild, korten och delningsbilden) |
| `birdy-x-albit.webp` | (Birdy × AlbIT, ingen art) | - | Birdy, ritad i kod (`tools/render-collab-share.mjs` ur `src/lib/collab-hero.mjs`): Birdys flock och AlbIT:s ordmärke (`src/assets/brand/`, AlbIT AB:s eget) | AlbIT AB | blogginlägget "Birdy × AlbIT" (inläggets bild, korten och delningsbilden; på sidan ritas samma bild direkt i SVG) |

## Bilden och videon i inlägget See the song

`see-the-song-flock-q25404-{en,sv}.webp` (3200×1680, ritad i 1600×840 i dubbel täthet) är omslaget till See the song i bredformat (Albin 2026-10-10: "the new minityr picture"): samma persikopapper, samma flock och samma ledtråd som det stående omslaget till blåmesvideon, med orden på sidans språk. Fram till 2026-10-10 var inläggets bild ljudringen med frågetecknet ur det första omslaget (`see-the-song-blames-q25404.webp`, CC BY-SA 4.0, borttagen; den finns i git-historiken).

Videon, omslaget och textspåren ligger i `public/video/see-the-song/`: `eurasian-blue-tit.mp4` (samma fil som `see-the-song.mp4`, oförändrad), `eurasian-blue-tit-cover.jpg` (affischen: blåmesens flockomslag, samma fil som `src/assets/clips/eurasian-blue-tit.jpg` och miniatyren i kanalerna; siluetten av Wouter Koch, CC0, via PhyloPic) och `eurasian-blue-tit.{sv,en}.vtt` (bara ljudet, utan fågelns namn). Den gamla affischen `eurasian-blue-tit.jpg` (ringen med frågetecknet) togs bort 2026-10-10. Videon är CC BY-SA 4.0 och innehåller fotot [Blaumeise (64) (34633517080).jpg](https://commons.wikimedia.org/wiki/File:Blaumeise_%2864%29_%2834633517080%29.jpg) av Kathy Büscher (CC BY 2.0, beskuret) och inspelningen ovan (bearbetad). Krediten står under videon i inlägget.

Startsidans polaroid (Dagens fågel) visar artsidans eget foto helt, utan något ritat över det, och får därför visa foton under CC0, public domain, CC BY och CC BY-SA. Korten Fåglarna i månaden beskär fotot och visar därför bara CC0, public domain och CC BY, aldrig CC BY-SA (Albins fotoregler, `src/lib/daily-bird.mjs`). Fotograf, licens (länkad) och källa står under polaroiden och korten med samma rad som på artsidan (`src/components/species/PhotoCredit.astro`). Den AI-genererade rödhaken som heron byggde på före 2026-10-08 är borttagen.

Birdy-fågeln (`public/brand/birdy-bird.png`, `public/coverage/seal-bird.png`, favikonerna) och appikonen står i `docs/legal/image-sources.md`, som listar alla bilder i repot.
