# Release 1.3.0: sammanslagningskö och uppföljare (2026-10-06)

Sammanslaget i release/1.3.0: stats (674ae69a), dagens-fagel (cbd7fdb8).
Pågår på release (Task 7-agenten): fixar från granskningen av Task 7 + merge av foto-klar (b8b898d9).
Väntar på merge efter det: feature/1.3-puts (godkänd, bc695924; byt Color.Black mot PhotoScrim i PremiumHeroCard efter merge), feature/1.3-bilder (granskning pågår). species.db: bygg om efter varje merge, välj aldrig en sida.
Sedan: 7b tillbakapilar, 7e-2 fotokrediter, 7i upphovsrätts- och juridikgenomgång, Task 8 R8, Task 9 butiksbilder, Task 10 vC130.

Uppföljare (små, före vC130 om tid finns):
- Dagens fågel kan välja en utdöd art (Garfågel, Kanariestrandskata, Smalnäbbad spov): exkludera isExtinct i DailyBirdSelector (M4 från 7g-granskningen).
- Dagens fågel midnatt: coerceAtLeast(MidnightMargin) + test "gårdagens fågel sparad efter midnatt räknas inte".
- Pipeline (efter release): manuellt iucn_status vinner alltid (M1), okänd status → NE tyst (logga, M2), svenska namn-fallback kan ta gammalt binomen/ej deterministisk (M3).
- Artsidorna: kör om web sources för Kaja (NE→LC) och för alla godkända arter vars foton byttes (62 st) innan deras sidor genereras.
- 30 svenska namn skiljer sig från BirdLife Sveriges lista (Albins beslut).

Artsidans textkredit (7i-fix A, granskningen 2026-10-07), till 1.3.1:
- Fyra engelska texter kan beskriva ett annat taxon än arten, eftersom den sparade revisionen är en artikel om ett annat Wikidata-objekt (inte en förgreningssida, så revisionen står kvar och krediten länkar den): Kricka Q25700 (en "Green-winged teal" rev 1347153361, Q704074 = Anas carolinensis, amerikansk kricka), Drillärka Q1083050 Mirafra javanica (en "Singing bush lark" rev 1315354773, Q2743822 = M. cantillans), Härfågel Q25247 (en "Hoopoe" rev 1350762486, Q20977 = familjen/släktet) och Gråsiska Q20754771 (en "Redpoll" rev 1315349490, Q2822501 = släktet). Hämta om rätt artikel i 1.3.1 och skriv om texterna, eller töm revisionen som för förgreningssidorna (dadf90e5).
