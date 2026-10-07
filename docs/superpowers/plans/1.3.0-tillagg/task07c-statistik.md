### Task 7c (tillagd 2026-10-06, Albins val "Season statistics: B"): Säsongsstatistik som årsring och fyndtidslinje

**Design (godkänd av Albin 2026-10-06):** option B, "The year ring and a journal of firsts", i förhandsvisningen `docs/superpowers/specs/assets/2026-10-06-1.3-val/birdy-val.html` (sektionen Season statistics, kolumnen "Option B"; läs HTML/CSS för exakta mått, färger och texter; publicerad på https://claude.ai/artifact/V3YRKWD1ascX26WjY8JmRg). Nuvarande skärm: `docs/superpowers/screenshots/v1.3/stats_sv.png`.

Innehåll i B (verifiera mot mockupen):
- Sidhuvud som i dag (bakåtpil fast i topBar, "Säsongsstatistik", PREMIUM-märket) och intro med en mening ur datan (t.ex. "Femton fynd. Maj var din bästa månad." / EN motsvarighet), räknad, inte hårdkodad.
- **Årsringen:** 12 månadssegment runt en cirkel med januari överst, segmentets längd/tjocklek efter antal fynd, totalsumman i mitten, aktuell månad i mässing (mässing betyder BARA aktuell månad; säsongerna markeras med etiketter eller hårlinjer, inte med mässing), månadsbokstäver runt ringen. Ersätter både stapeldiagrammet och munken (den marinblå säsongsfärgen försvinner).
- **"Nya arter i år":** tidslinje över årets första fynd per art (datum + artnamn + liten rund bild), ersätter linjediagrammet över ackumulerade arter.
- **Mest sedda:** topp tre som präglade sigill med foto (befintlig `StampSeal`-stil), antal fynd under.
- Tomt läge (inga fynd i år) och få fynd (1–2) ska se avsiktliga ut.

**Krav:**
- Delad Compose (commonMain), Canvas utan nya beroenden, palettens tokens (`ui/theme`), WCAG-låsen (`ContrastTest`/`PaletteMirrorTest`) gröna, inga hårdkodade strängar (SV + EN i `values/strings.xml` och `values-en/strings.xml`, `StringsParityTest`), inga tankstreck eller utropstecken i texterna.
- Tillgänglighet: ringen och tidslinjen får contentDescription med datan i klartext (t.ex. "Maj: 4 fynd"), inte bara "diagram" (detta stänger oktober-uppföljaren om diagrammens contentDescription).
- Stor text: vid fontskala 1.3/1.5/2.0 ingen klippt text, månadsetiketterna krymper till en bokstav som i dag (befintliga vakttester mot mitt-i-ord-brytning ska fortsätta gälla eller ersättas likvärdigt).
- Datan: återanvänd befintliga use cases/ViewModel för statistiken; lägg till det som saknas (första fynd per art i år, fynd per månad) i domänlagret med commonTest-tester.
- Premiumgrinden oförändrad.

**Tester:** commonTest för nya beräkningar (månadsfördelning, årets första fynd, bästa månad, tomt år); Robolectric-rendering SV + EN med Roborazzi-riggen (`./gradlew :composeApp:recordRoborazziDebug -Pbirdy.screenshots=true --tests "se.birdy.app.screenshots.*"`), nya bilder i `docs/superpowers/screenshots/v1.3/stats_*`; contentDescription-test.

**Klart när:** full gate grön (`:shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :shared:datastore:jvmTest :androidApp:assembleDebug ktlintCheck detekt`), iOS-länken kompilerar om möjligt lokalt annars via CI, skärmbilderna jämförda mot mockupen, commit(s) `feat(stats): säsongsstatistiken som årsring och årets första fynd` + Co-Authored-By, push av grenen.
