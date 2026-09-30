# Artsidorna: baslinje före go-live

Tagen 2026-09-30 av agenten via Search Console och Vercel Web Analytics (Albins inloggade webbläsare). Specen (`docs/superpowers/specs/2026-09-25-artsidor-design.md` avsnitt 12) kräver en baslinje före sammanslagningen i fas 2. **Uppdatera siffrorna samma dag som fas 2 slås ihop**; det här är läget innan en enda artsida finns.

## Search Console (egendom `sc-domain:birdy.community`, webbsök)

Senaste data: 2026-09-27.

| Fönster | Klick | Visningar | CTR | Snittposition |
|---|---|---|---|---|
| 3 mån (28 jun till 27 sep) | 3 | 364 | 0,8 % | ca 17 |
| Hela historiken (21 maj till 27 sep) | 6 | 395 | 1,5 % | 17,1 |

**Per halvmånad** (visningar per dag steg från ca 3 till ca 10 i andra halvan av september, men gav inga klick):

| Period | Klick | Visningar | Snittposition |
|---|---|---|---|
| 16 till 31 maj | 2 | 18 | 6,1 |
| 1 till 15 jun | 0 | 6 | 46,5 |
| 16 till 30 jun | 1 | 7 | 2,0 |
| 1 till 15 jul | 0 | 25 | 19,9 |
| 16 till 31 jul | 2 | 53 | 16,8 |
| 1 till 15 aug | 0 | 53 | 20,9 |
| 16 till 31 aug | 1 | 72 | 29,2 |
| 1 till 15 sep | 0 | 39 | 10,4 |
| 16 till 27 sep | 0 | 122 | 11,0 |

**Sidor med visningar (3 mån):** `/` 354 visningar och 3 klick, `/sv/` 10 visningar. Inga andra sidor.

**Frågor:** 36 st, nästan bara varumärket och felstavningar av det. Störst: "birdy app" 42 visningar på position 9,0; "curious birdy" 7; "birdy website" 5. Enda icke-varumärkesfrågorna: "vilken fågel app" (position 41), "bird diary" (34), "bird spotter", alla med en visning.

**Länder (hela historiken, visningar):** USA 75, Storbritannien 35, Sverige 25 (3 klick), Tyskland 25, Vietnam 23, Ryssland 19. **Enheter:** mobil 186 (position 7,7), dator 205 (position 25,8).

**Indexering:** 1 sida indexerad, 3 ej indexerade med skälet "Sida med omdirigering" (väntat: http och www). Sitemapen (`sitemap-0.xml`, läst 29 sep) har 10 upptäckta sidor. `/sv/` har fått visningar, så indexrapporten ligger troligen efter.

**Externa länkar:** 67, alla till `/`. Från google.com 51 (Play-listningen), albit.se 12, appbrain.com 2, birdforum.net 1, chrome-stats.com 1. Alltså cirka fem länkande domäner.

## Vercel Web Analytics (projekt `loop-lead-ab/birdy`, produktion)

| Fönster | Besökare | Sidvisningar | Avvisningsfrekvens |
|---|---|---|---|
| 90 dagar | 334 (+126 %) | 551 (+150 %) | 65 % |
| 30 dagar | 107 (−1 %) | 229 (+43 %) | 55 % |
| 7 dagar | 46 (+156 %) | 132 (+500 %) | 46 % |

**Sidor (90 dagar, besökare):** `/` 184, `/legal/privacy` 152, `/sv` 9, `/blog/why-birdy` 6, `/legal` 4, `/legal/terms` 4, `/blog` 3.

**Hänvisningar (90 dagar):** albit.se 17, birdforum.net 8, cn.bing.com 6, google.com 5, duckduckgo.com 2, m.facebook.com 1. Resten saknar hänvisning.

**Länder (90 dagar):** USA 74 %, Sverige 14 %. **Operativsystem:** GNU/Linux 40 %, Windows 18 %, Android 18 %. Senaste 7 dagarna: Sverige 37 %, Windows 41 %.

**Tolkning:** knappt hälften av trafiken är integritetspolicyn (länkad från Play-listningen och appens inställningar). Den stora andelen USA plus Linux tyder på automatiska besök. Uppgången senaste veckan sammanfaller med de två webbsläppen (25 och 28 sep) och kommer främst från Sverige, alltså till stor del egna och delade besök. Google skickade 5 besökare på 90 dagar. Egna besök filtreras inte bort i dag (`<Analytics />` i `Layout.astro` saknar `beforeSend`).

## Att fylla i efter go-live

| Mätpunkt | Datum | Indexerade artsidor | Visningar/mån | Klick/mån | Anteckning |
|---|---|---|---|---|---|
| Go-live (fas 2) | | | | | |
| +6 veckor | | | | | |
| +12 veckor | | | | | |
