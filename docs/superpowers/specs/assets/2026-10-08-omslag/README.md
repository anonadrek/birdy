# Omslagen i flocken (2026-10-08)

Albin 2026-10-08 kväll: alla videor på sociala medier har samma omslag (mörk ruta, frågetecken i en ljudring, "Whose song is this?"), så i profilens rutnät ser nio inlägg ut som ett enda upprepat. Förslaget: varje video får ett eget omslag i Flock-looken, och det som skiljer dem åt är fågeln.

- `omslag.html`: förhandsvisningen, publicerad privat som https://claude.ai/artifact/CmrNT3wNExs1XW5ubjL6ae. Instagram-profilen efter de nio första inläggen (omkring 17 okt: de tre fästa rutorna, sedan nyast först) med dagens omslag och tre förslag. Bilderna ligger i `img/`.
  - **A Polaroiden** (rekommendationen): fågeln som tejpad polaroid på persikopapper, engelska namnet handskrivet och svenska under, flocken flyger förbi bakom. Samma polaroid som Dagens fågel på webben.
  - **B Gissa fågeln**: samma polaroid, kortet frågar "Whose song is this?" i stället för namnet.
  - **C Ledtråden**: inget foto, flocken som Birdys fågel med artens fågel tänd och en handskriven ledtråd (exempeltexter; skarpa ledtrådar ska hämtas ur artsidornas kontrollerade fakta).
- `cover.html`: mallen, 1080 × 1920. `cover.html?d=A&i=0` ger förslag A för art 0 (listan `SPECIES` i filen). Behöver `flock-data.js` från `../2026-10-08-flocken-webben/` och artsidornas hjältefoton som `img/<QID>.webp` bredvid sig (kopior av `website/src/assets/species/<QID>/hero.webp`).
- `render.cjs`: renderar alla 27 omslag med Playwright ur `website/node_modules` (`node render.cjs`, eller `node render.cjs A0` för ett).

**Regler som mallen följer:** fotot visas helt, aldrig beskuret och utan text på (tejpen sitter på kortets kant, inte på fotot); fotograf och licens står på kortet och i bildtexten; rutnätet visar mitten (3:4) av omslaget, så allt viktigt ligger mellan y = 240 och y = 1680.

**Väntar på Albins val** (A, B, C eller Nu). De nya omslagen gäller de omkring 50 inlägg som inte är schemalagda (YouTube och TikTok från 19 okt, Facebook och Instagram från 29 okt); för de 40 schemalagda kollas om omslaget går att byta utan att schemalägga om.
