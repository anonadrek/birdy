# Spec — v1.2 Phase B: Weekly Recap ("Veckans uppslag")

> Designdokument. Brainstormat 2026-05-29 (visual companion, tema låst mot publicerade `website/src/styles/tokens.css` + appens `JournalHeadline`). Föregångare: Phase A retention hooks (`2026-05-25-v1-1-phase-a-retention-hooks.md`).

## 1. Bakgrund & mål

Phase A (v1.1) la in **Dagens fågel** + **Streak-risk-notis** som retention-hooks. Phase B var tänkt som två free-tier-hooks — **Migrating now-strip** + **Weekly recap** — defererade 2026-05-25 för att hålla v1.1 smalt. Vid brainstormen 2026-05-29 gjordes en data-genomgång (se §2) som tog bort Migrating-strip:en ur scope. **Phase B blir därför enbart Weekly recap.**

**Mål:** ge användaren en återkommande, känslomässigt positiv anledning att öppna appen i slutet av varje vecka — en söndagskväll-blick tillbaka på veckans fynd, i fältboks-anda (inte en stat-dashboard). Adaptiv: firar aktiva veckor, uppmuntrar mjukt tysta.

## 2. Scope

**I scope:**
- Én skärm: `RecapScreen` ("Veckans uppslag"), adaptiv aktiv-/tyst-vecka.
- Én enad söndags-push (`WeeklyRecapWorker`) som **ersätter** Phase A:s fristående streak-risk-push.
- Entry-point: kort överst på Lifelist-fliken.
- **Gratis** tier. BirdNET-licensen orörd (inget audio bakom premium).

**Utanför scope (med motivering):**
- **Migrating now-strip — BORTTAGEN.** Premissen ("vad som flyttar genom Sverige denna vecka") kräver per-art/per-månad säsongs-/migrationsdata. Datagenomgång 2026-05-29: `season`-statusen i alla 839 species-YAML är uniformt `present` (10 068 månadsrader, noll variation — även gök och ladusvala står "present" året runt). Det finns inget `migration_periods`-fält; fritext-`migration:` är oftast "unavailable". Infrastrukturen finns (`SpeciesSeason(species_id, month, status)`), men datan är en stub. En ärlig strip kräver en **seasonal/phenology-backfill** (eget content-delprojekt, kopplat till geografisk expansion) eller extern datakälla. Spunnen ut till framtida track.
- Beständig recap-historik (journal-issues att bläddra tillbaka i) — framtida möjlighet, ej nu.
- Delbart "vykort" (share-export) — framtida möjlighet (se §11).

## 3. Designbeslut

| # | Beslut |
|---|---|
| 1 | **Ton:** adaptiv — aktiv vecka firar, tyst vecka uppmuntrar utan att skämma. |
| 2 | **Push:** én enad söndags-push. Recap-pushen **subsumerar** streak-risk-pushen (en worker, en push/söndag — inga dubbel-pushar). |
| 3 | **Surface/beständighet:** skärmen visar **innevarande ISO-vecka live**, nollställs måndag 00:00 till en färsk (tyst) sida. Ingen historik. Alltid nåbar (inte bara via pushen). |
| 4 | **Layout (Approach A "Veckans uppslag"):** fältboks-uppslag — PlateFrame-hjälte + handskriven sammanfattning + statistik som marginalia + märken som sigill. INTE en dashboard. |
| 5 | **Veckans fynd (hjälte):** prioritet **ny lifelist-art denna vecka → ovanligast (lägst abundans) → senaste fyndet**. |
| 6 | **Tom-push-regel:** 0 fynd OCH ingen streak att rädda → **ingen push** (nudga inte ny/vilande användare med tomhet). Skärmen finns kvar att öppna själv. |
| 7 | **Entry-point:** "Veckans uppslag →"-kort överst på **Lifelist**-fliken (personliga samlingen). |
| 8 | **Typografi/tema:** `JournalHeadline` (DM Serif Display *italic* + Caveat-accent copper `rotate(-3deg)` 1.15em), Caveat för sammanfattning/marginalia, Inter för UI-text. Exakt som publicerad app + birdy.community. |

## 4. Veckodata & `WeeklyRecapBuilder`

Ren, plattformsoberoende aggregator i `commonMain` (testbar utan Android/Compose).

**Vecka:** ISO-vecka (mån–sön), `TimeZone.currentSystemDefault()`, via Phase A:s `StreakHelpers.weekKey(instant, zone)` / `WeekKey`. "Innevarande vecka" = `weekKey(now)`. Filtrera observationer på `weekKey(Instant.fromEpochMilliseconds(it.captured_at_ms), zone) == currentWeek`.

**Datamodell (förslag):**
```kotlin
data class WeeklyRecap(
    val week: WeekKey,
    val observationCount: Int,
    val newSpeciesCount: Int,          // arter vars FÖRSTA observation föll denna vecka
    val newBadges: List<BadgeId>,      // märken upplåsta denna vecka
    val weeklyStreak: Int,             // NUVARANDE sammanhängande veckostreak (ej all-time-längsta) via StreakHelpers; lägg current-streak-helper om bara longest finns
    val deltaVsLastWeek: Int,          // observationCount(this) - observationCount(prev)
    val hero: HeroFind?,               // null på tyst vecka
    val streakAtRisk: Boolean,         // streak >= 2 && inga fynd denna vecka
)
data class HeroFind(
    val observationId: String,
    val speciesId: String?,
    val photoPath: String,             // obs-foto; tom för audio-obs → plate-fallback
    val captionKey: String,            // "Pl. — {art}, {dag}"
    val isNewSpecies: Boolean,
)
```

**Veckans fynd-urval (§3.5):** bland veckans observationer, välj i ordning: (1) en obs vars art är ny i lifelisten (första observation av `species_id` faller denna vecka), (2) annars obs med ovanligast art (lägst abundans — slås upp via species-content/`Species`-tabellen), (3) annars senaste (`captured_at_ms` max). Hjälte-bild = `photo_path`; om tom (audio-obs, `audio_path != null`) → falla tillbaka på artens plate-/hero-bild. `species_id == null` ("sparad som okänd") kan inte vara ny art och har ingen abundans → kvalar bara via regel 3.

**Ny art-detektion:** en art räknas som ny denna vecka om dess tidigaste observation (`min(captured_at_ms)` över alla obs med samma `species_id`) ligger i innevarande vecka. Beräknas från hela `observationRepository.observeAll()`.

**Edge cases:** se §9.

## 5. `RecapScreen`

Compose (`commonMain`), `paperBackground()`-bas. ViewModel `RecapViewModel` exponerar `WeeklyRecap` via Flow (recompute på resume + på obs-/badge-ändring).

**Eyebrow:** `Fältrapport · Vecka {isoWeek}` (Inter, copper, uppercase).

**Aktivt läge (observationCount ≥ 1):**
- `JournalHeadline("En vecka i *fält*.")`
- `PlateFrame` med hjälte-foto **centrerad** (`object-position: center`, porträtt-vänlig höjd) + Caveat-caption "Pl. — {art}, {dag}".
- Caveat-sammanfattning (genererad: "{n} fynd den här veckan" + ", och en ny bekantskap" om `newSpeciesCount > 0`).
- `OrnamentRule` (❦).
- Statistik-marginalia (Caveat): "{count} fynd · {newSpeciesCount} ny art · {weeklyStreak} v streak".
- Nya märken som `StampSeal`-rad ("Ny stämpel: {namn}") om `newBadges` ej tom.
- Fot (Caveat, dämpad): "vs förra veckan: {±delta} fynd".

**Tyst läge (observationCount == 0):**
- `JournalHeadline("En *lugn* vecka.")`
- Tom, streckad PlateFrame med ❦ + caption "Sidan är tom — inga fynd ännu."
- Caveat-uppmuntran: "Ingen brådska. Tio minuter i parken räcker för veckans första rad."
- `OrnamentRule`.
- Om `streakAtRisk`: nudge-block (copper-tonad ruta) "Din streak: {n} veckor — håll den vid liv innan söndagen är slut. En talgoxe i parken räcker."
- Mjuk CTA: "Öppna kameran →" (navigerar till Identify/Scan).

Alla strängar via `compose-resources` (sv + en). Obs `%`-escape-trappen: passa förformaterade siffror från Kotlin.

## 6. Push — `WeeklyRecapWorker`

Vidareutveckling av `StreakRiskWorker` (`composeApp/src/androidMain/.../notifications/workers/`). **Den fristående streak-risk-schemaläggningen retiras** — recap-pushen tar över söndagskväll-slotten.

- **Schema:** söndag kväll (behåll Phase A:s slot, 18:00 local), default ON, egen toggle i Settings → Aviseringar (ersätter "Streak risk"-toggeln, eller döps om till "Veckans recap"). Reuse `NotificationScheduler` + push-permission-rigg + `CHANNEL`-mönster.
- **Adaptiv copy (vald via `WeeklyRecapBuilder`-resultat):**
  - Aktiv vecka (≥1 fynd): titel "Veckan i fält", body "{n} fynd, {m} ny art — se veckans uppslag."
  - Tyst + `streakAtRisk`: titel "Bara kvällen kvar", body "En talgoxe i parken räcker — fortsätt din streak." (Phase A:s streak-räddnings-copy, nu del av recap-pushen.)
  - Tyst + ingen streak: **ingen push** (§3.6).
- **Deep-link:** `birdy://recap` → `RecapScreen`, via Phase A:s deep-link-bridge (`MutableSharedFlow` i `MainActivity`). Ny route `AppRoute.Recap`.
- DEV-trigger: lägg `WeeklyRecapWorker` till debug-knapparna i Settings (som Phase A:s force-run).

## 7. Entry-point — Lifelist-kort

"Veckans uppslag →"-kort/rad överst på `LifelistScreen.kt` (`ui/diary/`), ovanför samlingen. Visar en kort sammanfattning (t.ex. "Vecka {n} · {count} fynd" eller, tyst vecka, "En lugn vecka hittills") + chevron → `AppRoute.Recap`. Field Journal-stil (paper-kort, copper-accent).

## 8. Arkitektur & komponenter

**Återanvänder (oförändrat):** `JournalHeadline`, `PlateFrame`, `StampSeal`, `OrnamentRule`, `paperBackground` (`ui/components/` + `ui/theme/`); `StreakHelpers` (`shared/domain/.../badge/`); `observationRepository`; badge-unlock-data; `NotificationScheduler` + push-permission-infra; deep-link-bridge (`MainActivity`).

**Nytt:**
- `shared/domain` (eller `composeApp/commonMain`): `WeeklyRecap`, `HeroFind`, `WeeklyRecapBuilder` (+ abundans-lookup mot species-content).
- `composeApp/commonMain`: `RecapViewModel`, `RecapScreen`, `AppRoute.Recap` + route i `AppScaffold`, Lifelist-entry-kort, strängar (sv+en).
- `composeApp/androidMain`: `WeeklyRecapWorker`.
- `AppGraph`: factory för `RecapViewModel` + ev. `weeklyRecapBuilder`.

**Retiras:** fristående streak-risk-worker-schemaläggning (logiken lever vidare i recap-pushen). Behåll bakåtkompatibel avregistrering så befintliga schemalagda streak-risk-jobb inte dubbel-fires efter uppgradering.

**`:androidApp`-trap:** ny shared/library-referens kräver egen `implementation()` i `androidApp/build.gradle.kts` (composeApp använder `implementation`, inte `api`).

## 9. Edge cases

- **Audio-only obs** (`photo_path` tom, `audio_path != null`): hjälte-bild → artens plate/hero-bild.
- **`species_id == null`** ("sparad som okänd"): ej ny art, ingen abundans → bara via "senaste"-regeln; caption utan artnamn.
- **Brand-ny användare / vecka utan historik:** tyst läge, ingen streak, ingen push (§3.6).
- **DST/årsskifte:** hanteras av `StreakHelpers` ISO-veckologik (jan4-ankare).
- **Notiser avstängda:** ingen push; skärmen + Lifelist-kortet fungerar ändå.
- **Vecka spänner årsskifte:** `WeekKey(isoYear, isoWeek)` korrekt via Thursday-regeln.

## 10. Test

**Unit (`commonTest`/`jvmTest`):**
- `WeeklyRecapBuilder`: veckogräns (obs precis innan/efter mån 00:00), ny-art-detektion (första obs i veckan vs tidigare), veckans fynd-urvalsprecedens (ny > ovanligast > senaste), audio-fallback, `species_id == null`, streak (StreakHelpers), delta vs förra veckan, tyst-detektion + `streakAtRisk`.
- Adaptivt push-copy-val (aktiv / streak-risk / ingen-push).

**Device-verify (SM-S918B) — se [[feedback_personal_device_verify]]:** aktiv-vecka-skärm, tyst-vecka-skärm, push (aktiv + streak-nudge), deep-link-landning, Lifelist-entry-kort.

## 11. Framtida uppföljningar (ej i denna spec)

- **Migrating now-strip** — kräver seasonal/phenology-content-backfill (eget delprojekt). Knyts till geografisk expansion.
- **Beständiga journal-issues** — bläddringsbart veckoarkiv.
- **Delbart vykort** — "Approach C"-export (oskarp hjälte + handskriven hälsning) för organisk spridning.

## 12. Visuell referens

- Tema-tokens: `website/src/styles/tokens.css` (paper `#EFE7D6`/`#E8E2D2`/`#E5DCC7`, copper `#A8552D`, moss `#5C6E48`/`#3F4F30`, marginalia-ink `#3F4F30`, stamp-navy `#1F3A5F`; fonts DM Serif Display / Caveat / Inter).
- `JournalHeadline`-accent: Caveat italic 700, copper, `rotate(-3deg)`, 1.15em.
- Brainstorm-mockups (gitignored): `.superpowers/brainstorm/.../recap-a-states.html` (Approach A, aktiv + tyst).
