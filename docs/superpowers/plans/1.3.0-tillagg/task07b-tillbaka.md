### Task 7b (tillagd 2026-10-06, Albins önskemål): synlig väg tillbaka på varje skärm

**Varför:** Albin ser att "bakåtpilen fattas i vissa sektioner". En läsgranskning av alla rutter (2026-10-06) fann:

Saknar synlig väg tillbaka (vanligast först):
1. Match efter "Spara" (`ui/match/MatchView.kt` ~199-207): "Avbryt" ersätts av sparad-läget, ingen pil på fototoppen (PhotoHero får ingen topBar, till skillnad från Artprofilen).
2. Veckosammanfattningen (`ui/recap/RecapScreen.kt` ~60-74, rutt `AppScaffold.kt` ~573-583): ingen onBack alls, bara gesten. Nås från Mina arters recap-kort och veckoaviseringen.
3. Pilen scrollar bort på Inställningar (`SettingsScreen.kt` ~169-173, TopBar är första LazyColumn-posten), Artprofilen (`SpeciesProfileScreen.kt` ~137-163, i första posten) och Fynddetaljen (`ObservationDetailScreen.kt` ~177-196, top 24dp mot 8dp på andra skärmar).
4. Disambig: "Avbryt" under kandidatlistan (kräver scroll, `DisambigView.kt` ~212-218); NoBird bara "Försök igen" (`NoBirdView.kt` ~124-128).
5. Tack-skärmen (`PremiumThankYouScreen.kt` ~54, 83-89): inget ✕, bottenmenyn dold, "Fortsätt" sist i scrollen.
6. Fel-/tom-/laddlägen utan väg tillbaka: MatchResult Error (`MatchResultScreen.kt` ~41-50), Artprofil NotFound/Loading (~99-104, nåbar via `birdy://species/<id>`), Fynddetalj NotFound/Error/Loading (~134-147), Skanna/Foto-ID:s laddare medan modellen startar (`ScanScreenHost.android.kt` ~36-42, `PhotoAnalyzeHost.android.kt` ~58-65).
7. Beskärningen: bara "Avbryt" längst ned; Visa introduktion igen: svagt "Stäng" (13sp, 70 % alfa).
8. Debugskärmar (låg prio, bara debugbygget).

Inkonsekvenser att rätta samtidigt:
- Pilen ska ligga fast (inte scrolla bort) på alla pushade skärmar: följ mönstret i TrophyRoom/About/SeasonStats (JournalScaffold topBar) eller kamerans Box-overlay; på foto-toppar (Artprofil, Match) en fast glas-BackButton (`onDark = true`) som ligger kvar ovanpå när man scrollar.
- Samma höjd (8dp under statusraden) överallt; Fynddetaljens 24dp → 8dp.
- Skannas pil ska vara `onDark = true` över kamerabilden (komponentens egen KDoc).
- Pilen och systemgesten ska göra samma sak: Skanna/Foto-ID/Lyssna gör i dag `popBackStack(Listen)` på pilen men gesten poppar ett steg (`AppScaffold.kt` ~327-329, 342-344, 554-556). Gör pilen till `popIfTop` (ett steg tillbaka) som alla andra.
- Titel bredvid pilen: valfritt; rör inte om det inte behövs.
- Recap, Inställningar, Om, Fynddetalj, Statistik, Premium ägs inte av någon flik (`BottomNavBar.kt` ~67-91) → ingen flik markerad. Lägg dem i rätt fliks `ownedRoutes` om det är självklart (Recap → Mina arter, Fynddetalj → Mina arter, Statistik → Mina arter); annars lämna.

**Tester först (Robolectric i riktiga AppScaffold, samma stöd som Task 1–2):** för varje rättad skärm: en synlig bakåtkontroll finns (contentDescription "Tillbaka"/"Stäng") även efter scroll till botten; ett tryck tar exakt ett steg tillbaka (och dubbeltryck ger ingen tom skärm, `popIfTop`). Fel-/tomlägen: kontrollen syns.

**Klart när:** testerna gröna, full gate grön, skärmdumpar på emulatorn (SV) av Match sparad, Recap, Inställningar scrollad, Artprofil scrollad, Fynddetalj scrollad, Tack-skärmen, i `docs/superpowers/screenshots/v1.3/device/tillbaka-*.png`; commit `fix(app): synlig väg tillbaka på varje skärm` + Co-Authored-By; push.
