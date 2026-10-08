# Flocken på webben: utkast 1 (2026-10-08)

Förhandsvisningen av birdy.community i de sociala profilernas Flock-look, som Albin gillade ("Ser mycket, mycket bra ut"). Publicerad privat som artefakten https://claude.ai/artifact/P39PewN7n4vVoURz5vbDFi (version 3 = utkast 1, återställd efter att utkast 2 avvisades).

- `flocken-pa-webben.html`: sidan. Bilderna den visar (`img/ringduva.webp`, `img/blames.webp`, `img/rodhake.webp`) är kopior av `website/src/assets/species/Q26026/hero.webp`, `website/src/assets/species/Q25404/hero.webp` och `website/src/assets/photos/rodhake-q25334.webp`; lägg dem i en `img/`-mapp bredvid för att öppna sidan lokalt.
- `flock-data.js`: fågelmärkets bana och tre flockar (839 fåglar var: `cover`, `grid`, `round`), genererad ur `docs/superpowers/specs/assets/2026-10-08-sociala-profiler/kalla/data.js` på grenen `social/see-the-song`. Varje fågel är `[x, y, storlek, rotation, färgindex, opacitet]`; de första `edge` fåglarna ritar märkets kontur.

## Beslut hittills (brainstorm, ingen spec än)

1. **Omfattning:** hela sajten, i steg: blogginläggen, sedan artsidorna, sedan startsidan, menyn och sidfoten.
2. **Riktning:** "tyst flock överallt" (inte en levande, klickbar flock, och inte bara färgerna).
3. **Färger:** samma som de sociala profilerna (persika, espresso, rost, koppar, mässing, aprikos); Albin: flocken är appens "lore och kärna".
4. **Albins återkoppling på utkast 1:** mer engagemang och djup, sticka ut, en baktanke och en röd tråd, och alla sidor behöver inte ha fågeln av alla små fåglar.
5. **Utkast 2 avvisades** ("kladdigt som tusan"): en bokstavlig röd linje dragen genom varje sida, en sånghalo som rörde sig med fågelns sång, stämplar och fler handskrivna anteckningar. Nästa försök börjar från utkast 1 och söker djupet i idén och kompositionen, inte i fler dekorationer.
6. **Rörelseprototyp (Albin: "Yessir" på en animation i kod):** `flocken-lyfter.html`, publicerad som https://claude.ai/artifact/Y9PWpnzdTSkPrgyGoYtgY1. Startsidans enda stora rörelse: de 839 fåglarna flyger in nerifrån vänster längs strömmen (under texten), virvlar kort och landar som Birdys fågel; dagens fågel landar sist och tänds; sedan lyfter Dagens fågel-fotot ur just den fågeln, flyger till sin plats, framkallas som en polaroid och tejpas fast, och "dagens fågel" skrivs fram med en pil. Albin 2026-10-08: "Ser kanon ut" och, med fotot efter flocken, "Sen är vi nöjda" (godkänd). Canvas med en bitmap av märket per färg, en ram var 7:e ms i testet; minskade rörelser ger den färdiga flocken direkt. Behöver samma `flock-data.js` och `img/ringduva.webp` bredvid sig.
