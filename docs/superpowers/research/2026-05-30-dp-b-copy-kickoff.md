# DP B — Positionering & copy: kickoff för ny session

> Självständig start-pack så DP B kan köras från en **fräsch session** (brainstorm → spec → plan → exekvering). DP B är delprojekt 2 i v1.x-tester-feedback-programmet. Program-spec: `docs/superpowers/specs/2026-05-29-v1-x-tester-feedback-response-design.md` (§5). DP A (sök) är klar + mergad. All kontext nedan är redan utforskad 2026-05-30 — börja med besluten, inte ny utforskning.

## Mål & låst riktning

**Problem (testaren):** "I can't understand what the app is for" — appen kommunicerar stämning (vacker fältdagbok), inte jobb-att-utföras; ingen krok mot Merlin.

**Låst riktning (program-spec §5):** **"keep it, not just ID it"** — Birdy = den vackra, privata fältdagboken där fyndet blir något du äger och återbesöker; identifiera med kamera/ljud, offline. Smalna mot entusiasten. Uttala kroken mot Merlin ("Merlin är bäst på ID; Birdy handlar om vad som händer efter").

**Scope:** app-copy + onboarding-omsekvensering + nav-översättning + store-listing + website. Mestadels copy, ingen ny funktionalitet. (Överväg i brainstormen att sekvensera planen: app-copy först, store/website sen.)

## Nuvarande copy (inventerat 2026-05-30, med fil:rad)

Strängar: `composeApp/src/commonMain/composeResources/values/strings.xml` (SV) + `values-en/strings.xml` (EN). **Alla ändringar i BÅDA filerna.**

**Första skärmen** (`ui/listen/ListenLauncherScreen.kt`, ScanLaunchScreen som Skip-användare landar på):
- `listen_journal_label` = "FÅNGA · NO 0"
- `listen_journal_headline` = "Tre sätt att *fånga.*"  ← säger inget om ID (huvudproblemet)
- `listen_journal_sub` = "En stämpel väntar i varje."
- Korten (bodies nämner redan ID): `listen_card_camera_title`="Kika" / body "Realtidsskanning via kameran."; `listen_card_photo_title`="Leta upp" / "Välj foto från galleri eller ta nytt."; `listen_card_audio_title`="Lyssna" / "Identifiera via 3 sekunders läte."

**Onboarding scen 1 (Hero)** (`ui/onboarding/scenes/SceneHero.kt`):
- `onboarding_s1_eyebrow` = "FÄLT-FÖLJESLAGARE · NO 1"
- `onboarding_s1_headline` = "*Birdy.*"  ← funktionen begravd i sub
- `onboarding_s1_sub` = "Identifiera fåglar i fält — foto eller ljud, på enheten, utan internet." (den enda raka funktionsmeningen)

**Onboarding-scener (7, sekvens)** (`ui/onboarding/scenes/`): Hero · Photo · Audio · Journal · **Badges** · Privacy · Name. Research: funktion/nisch före gamification; nedtona/flytta Badges-scenen (`SceneBadges.kt` säljer streaks → casual-signal); överväg färre scener.

**Nav-labels** (`ui/scaffold/BottomNavBar.kt`, strings): `tab_listen`="Identifiera", `tab_archive`="**Archive**", `tab_lifelist`="**Lifelist**", `tab_badges`="**Badges**" — 3 av 4 SV-labels på engelska. Förslag: Archive→"Uppslagsverk", Badges→"Märken", Lifelist→? (Listan/Min lista/Livslista — beslut i brainstorm). OBS även breadcrumbs `archive_breadcrumb`="ARCHIVE", `lifelist_breadcrumb`="LIFELIST".

**Store-listing** (`docs/play-store/store-listing-{sv,en}.md`):
- Kort beskr SV (max 80): "Skanna fåglar med kameran, lär dig om dem, samla i din fältbok."
- Kort beskr EN: "Identify birds by camera, learn about them, collect them in your journal."
- Lång beskr SV börjar "Birdy är en AI-driven fältbok för fågelskådare…"

**Website** (`website/src/content/copy.{en,sv}.json`):
- hero.headline = "A *field journal* that looks like a field journal"; hero.sub = "Identify birds with your camera. Keep what you see. **Earn the stamps.**" ← research: ta bort "Earn the stamps" ur hero.
- "How it works": Point/Match/Stamp/Browse-loop.
- FAQ-sektion finns (`"faq"`, ~rad 71) men **ingen "How is this different from Merlin?"** → lägg till.

## Öppna beslut för DP B-brainstormen

1. **Scope/sekvens:** app-copy + store + website i en plan, eller app först + store/website som fas 2? (Device-verify gäller bara app.)
2. **Första-skärmens rubrik** (visuellt — mocka upp): t.ex. "Vilken fågel? *Identifiera.*" + funktions-sub. Behåll korten (de funkar).
3. **Hero-behandling:** lyft funktionen till rubriknivå, eller byt eyebrow till t.ex. "FÅGEL-ID + FÄLTDAGBOK" och behåll "Birdy."-rubrik? (visuellt)
4. **Onboarding-omsekvensering:** bara nedtona/flytta Badges-scenen, eller större omordning / färre scener?
5. **Nav-översättningar:** exakta SV-ord (Lifelist→?).
6. **Positionerings-mening + "vs Merlin"-FAQ:** exakt formulering (app/store/website).

## Cross-cutting bivillkor (program-spec §3)

- Alla UI-strängar via compose-resources i **BÅDA** `strings.xml`. `%`-escape: förformatterat från Kotlin; raw `'`/`’` inte `\'`. JournalHeadline-accent = `*ord*` (Caveat-italic copper).
- **Kommunicera ALDRIG accuracy-siffror** (72% top-3) i copy/store/website.
- Tema låst mot publicerade tokens (`website/src/styles/tokens.css`): DM Serif Display italic + Caveat-accent `rotate(-3deg)` + copper `#A8552D`.
- Inget audio bakom Premium (gäller mest DP D, men håll i minnet).

## Visual companion

Rekommenderas för fråga 2 + 3 (första-skärm/hero copy-mockups). Servern: `bash <superpowers>/skills/brainstorming/scripts/start-server.sh --project-dir <repo>` (Windows: `run_in_background: true`, läs `$STATE_DIR/server-info` för URL). Mocka i låst Field Journal-tema (se DP A-/Phase B-mockups i `.superpowers/brainstorm/` för CSS-mall). OBS: servern idle-timeoutar — starta om vid behov.

## Start-prompt för ny session

> Vi kör **DP B (positionering & copy)** i birdy-bird-scanner — delprojekt 2 i v1.x-tester-feedback-programmet. Läs `docs/superpowers/research/2026-05-30-dp-b-copy-kickoff.md` (all kontext + öppna beslut finns där) och program-spec:en. Kör `superpowers:brainstorming` för att låsa exakt copy + de 6 öppna besluten (visuella mockups för första-skärm/hero), skriv DP B-spec + plan, och exekvera sen via `superpowers:subagent-driven-development` på en feature-branch `feat/dp-b-positioning`. Cross-cutting: båda strings.xml, aldrig accuracy-siffror, Field Journal-tema. DP A (sök) är klar + mergad.
