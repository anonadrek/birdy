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
