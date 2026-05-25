# v1.1 Phase A — Retention hooks design

> **Status:** Spec — brainstormad 2026-05-25, väntar review av Albin innan writing-plans.
> **Target:** Nästa Closed Testing-upplaga efter v1.0.2 (onboarding v2).
> **Scope:** Dagens fågel + Streak-risk-notis + 3 nya premium-badges. Phase B (Migrating now-strip + söndag-veckorecap) deferrad till v1.2.

## Bakgrund

12 testare gav feedback på v1.0.0 som klustrar i två trådar:

1. **Låg återbesöks-pull** — användare öppnar inte appen utan trigger.
2. **Premium känns tunt** — value-prop pratar inte tillräckligt starkt för månadspris.

Phase A adresserar båda i ett släpp: två gratis-feature-hooks som drar tillbaka användare (in-app + push) + en liten premium-gest som ger befintliga Premium-tier-features fler aktiverings-anledningar.

## Mål

- **Primärt:** Höja DAU/WAU-kvoten genom återbesöks-hooks i fritier.
- **Sekundärt:** Ge premium-tier 3 nya aktiverings-events utan att bryta BirdNET-Lite-licens-restriktionen (audio är gratis).
- **Constraint:** Hela paketet ska in i nästa Closed Testing-AAB efter v1.0.2. ~4-5 utvecklingsdagar.

## Icke-mål

- Migrating now-strip på Listen-tab (Phase B / v1.2).
- Söndag-veckorecap-notis (Phase B / v1.2).
- Backend för push (vi använder enbart on-device WorkManager).
- Rich notifications med plate-bild (plate-WebPs ligger i `:asset-pack` install-time module → ej tillgängliga i debug-buildverify-flödet).
- iOS-support (`commonMain` får bara `expect`; alla `actual` är `androidMain`).
- Per-användare region-pref (pan-Nordic bucket räcker för v1.1).

## Låsta designbeslut

| # | Beslut |
|---|---|
| 1 | **Yta:** Paper-card "Dagens fågel" på Identifiera-tab, mellan `JournalIntro` och launch-cards. |
| 2 | **Algoritm:** Abundans-viktad deterministisk slump. 75% vikt på `abundance ∈ {allmän, vanlig}`, 25% på övriga. Seed = hash(ISO-datum + "NORDIC"). |
| 3 | **Region:** Pan-Nordic bucket — filter på `regions ∩ {SE, NO, FI, DK} ≠ ∅`. Ingen användar-pref. |
| 4 | **Cadence:** Deterministisk per ISO-datum i `TimeZone.currentSystemDefault()`. Kortet byts vid midnatt (effektivt nästa app-resume). |
| 5 | **Tap-beteende:** Tap på kortet → `navController.navigate(AppRoute.SpeciesProfile(speciesId))` — existerande skärm, ingen ny ruta. |
| 6 | **Push-permission:** `PermissionPromptSheet` (paper-bg ModalBottomSheet, Field Journal-stil) triggas efter första lyckade `SaveObservationUseCase`. "Inte nu" sätter `userPreferences.pushPermissionAsked = true`. Settings-toggle som backup. Endast Android ≥ 13 visar sheet:en. |
| 7 | **Streak-risk-push:** Söndag 18:00 local. Fires om ingen observation i nuvarande ISO-vecka OCH `longestWeeklyStreak() ≥ 2 veckor`. Default ON. Text-only. Copy: titel "Bara kvällen kvar", body "En talgoxe i parken räcker — fortsätt din streak." Deep-länkar till Listen-tab. |
| 8 | **Dagens fågel-push:** 08:00 local daglig. Default ON. Text-only. Copy: titel "Dagens fågel: {namn}", body = säsongs-eyebrow ("Häckar nu i Sverige" / "Här just nu" / "På sträck"). Deep-länkar till `SpeciesProfile`. |
| 9 | **Premium-gest:** 3 nya premium-badges kopplade till Phase A-beteenden: `early_pilgrim`, `sunday_birder`, `daily_bird_hunter`. |

## Arkitektur

Allt landar i existerande moduler — inget nytt modul.

| Komponent | Modul | Motivation |
|---|---|---|
| `DailyBirdSelector` (rena domänregler) | `:shared:domain` | Granne med `StreakHelpers` + `BadgeCatalog`. Testbart på JVM. |
| `DailyBird`-datamodell | `:shared:domain` | Lättviktig record: `speciesId`, `nameKey`, `eyebrowKey`. |
| `DailyBirdHistory` (SQLDelight-table) | `:shared:domain` | Per-dag log för `daily_bird_hunter`-badge-rule. |
| `DailyBirdCard` (Compose-UI) | `composeApp/.../ui/components/` | Granne med `JournalIntro`, `StampSeal`, `PlateFrame`. |
| `PermissionPromptSheet` (Compose-UI) | `composeApp/.../ui/components/` | Återanvänder ModalBottomSheet-mönster från PremiumModal. |
| `NotificationScheduler` (`expect`) | `:shared:domain` | Expect/actual som `PremiumBillingClient`. |
| `NotificationScheduler` (Android `actual`) | `composeApp/androidMain/.../notifications/` | WorkManager + channels + manifest POST_NOTIFICATIONS. |
| `DailyBirdWorker` + `StreakRiskWorker` | `composeApp/androidMain/.../notifications/workers/` | `androidx.work.CoroutineWorker`. |
| Nya `BadgeRule`-entries | `:shared:domain` `BadgeCatalog.kt` | Inline-tillägg, samma mönster som Plan 6b3. |
| Badge-strängar (sv+en) | `composeApp/composeResources/values{,-en}/strings.xml` + `BadgeStringMap` | 6 nya rader (3 namn + 3 descriptions × 2 språk = 12). |
| Settings-toggles | `composeApp/.../ui/settings/SettingsScreen.kt` | Ny "Aviseringar"-sektion. |
| `UserPreferences`-keys | `:shared:domain` `UserPreferences.kt` | `pushPermissionAsked`, `dailyBirdPushEnabled`, `streakRiskPushEnabled`. |

**DI-koppling:** `AppGraph` får 2 nya lambdas — `selectDailyBird: suspend (LocalDate) -> DailyBird?` och `notificationScheduler: NotificationScheduler`. Båda injiceras från `MainActivity` enligt befintligt lambda-DI-mönster (Plan 6b1).

**Inget berör:** `:shared:content`, `:shared:ml`, `:asset-pack`, iOS-skelettet.

## Komponenter

### 1. `DailyBirdSelector`

```kotlin
class DailyBirdSelector(
    private val speciesRepo: SpeciesRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend fun selectFor(date: LocalDate): DailyBird?
}
```

**Algoritm:**
1. Filtrera arter: `regions ∩ {SE, NO, FI, DK} ≠ ∅` AND `season[currentMonth] ∈ {present, breeding, migrating}`.
2. Partitionera på `abundance`: common-bucket = `{allmän, vanlig}`; rare-bucket = övriga.
3. `seed = "$date-NORDIC".hashCode().toLong()`, `random = Random(seed)`.
4. Med 75% sannolikhet välj från common-bucket; annars rare-bucket. Om vald bucket är tom, fall back till andra.
5. Plocka uniform random ur bucketen.
6. Bygg `DailyBird(speciesId, nameKey, eyebrowKey)`.
7. Returnera `null` om båda bucketsen tomma (extremt osannolikt).

### 2. `DailyBird`-datamodell

```kotlin
data class DailyBird(
    val speciesId: String,
    val nameKey: SpeciesNameKey,
    val eyebrowKey: StringResource,
)
```

Strängar resolvas via `stringResource()` på render — vi packar inte lokaliserad text i värdet.

### 3. `DailyBirdCard` (Compose)

- `Modifier.fillMaxWidth().padding(horizontal = 16.dp)`, höjd ~88dp.
- Bakgrund: `HeroMoss`-gradient (`#5C6E48` → `#3F4F30`).
- Layout: vänster StampSeal-cirkel (#1, copper-patina); höger eyebrow (AccentCopper, letterSpacing 1.5sp), headline (DM Serif Display Italic, vit ~22sp), sub-line (Caveat, vit @ 0.85 opacity).
- `onClick` → `navController.navigate(AppRoute.SpeciesProfile(speciesId))`.
- `contentDescription = "Dagens fågel: {namn}. {eyebrow}. Tryck för att läsa mer."`
- Om `bird == null` renderar ingenting (`Spacer(0.dp)`).

### 4. `PermissionPromptSheet`

- ModalBottomSheet med paper-bg + JournalIntro-stil.
- Eyebrow: "AVISERINGAR".
- Headline (DM Serif Italic, parsar `*ord*` → Caveat-italic): *"Vill du få en *påminnelse* när vi tycker fågeln är värd att möta?"*
- Sub (Caveat): "Max en kort knuff om dagen. Stäng av när du vill."
- Knappar: "Slå på" (AccentCopper-fill) → triggar `ActivityCompat.requestPermissions(POST_NOTIFICATIONS)`. "Inte nu" (text-only) → stänger sheet.
- Båda valen sätter `pushPermissionAsked = true`. "Slå på" + permission granted → schemalägger workers.

### 5. `NotificationScheduler`

```kotlin
// :shared:domain commonMain
interface NotificationScheduler {
    fun scheduleDailyBird()
    fun scheduleStreakRiskCheck()
    fun cancelDailyBird()
    fun cancelStreakRiskCheck()
}
```

Android-actual använder `WorkManager.enqueueUniquePeriodicWork()` med `ExistingPeriodicWorkPolicy.KEEP`. `DailyBirdWorker` = 24h periodic; `StreakRiskWorker` = 7d periodic med `setInitialDelay()` till nästa söndag 18:00.

### 6. `DailyBirdWorker`

`doWork()`:
1. `if (!userPreferences.dailyBirdPushEnabled.first()) return Result.success()`.
2. `val today = LocalDate.now(systemTZ)`.
3. `val bird = dailyBirdSelector.selectFor(today) ?: return Result.success()`.
4. Bygg `NotificationCompat.Builder(ctx, CHANNEL_DAILY_BIRD)` med titel "Dagens fågel: {namn}", body = eyebrow-stäng.
5. `PendingIntent` → `birdy://species/{speciesId}`.
6. `notify(NOTIF_ID_DAILY_BIRD = 1001, notif)`.

### 7. `StreakRiskWorker`

`doWork()`:
1. `if (!userPreferences.streakRiskPushEnabled.first()) return success()`.
2. Hämta `observations` från repo.
3. `currentWeek = WeekKey.fromDate(today)`. Om någon obs har `WeekKey.fromInstant(it.savedAt) == currentWeek` → `return success()` (streak inte i risk).
4. `streak = longestWeeklyStreak(observations.map { it.savedAt })`. Om `streak < 2` → `return success()` (för tidig nudge).
5. Bygg notification: titel "Bara kvällen kvar", body "En talgoxe i parken räcker — fortsätt din streak."
6. `PendingIntent` → `birdy://identify` (Listen-tab).
7. `notify(NOTIF_ID_STREAK_RISK = 1002, notif)`.

### 8. Nya `BadgeRule`-entries

| ID | Namn (sv) | Description (sv) | Rule |
|---|---|---|---|
| `early_pilgrim` | Tidig pilgrim | Spara en observation mellan 05:00 och 07:00. | `obs.savedAt` lokal-tid mellan 05:00-07:00 (en gång räcker). |
| `sunday_birder` | Söndagsskådare | Skåda 4 söndagar i rad. | Minst 1 obs på 4 påföljande söndagar (ISO-vecka). |
| `daily_bird_hunter` | Dagens fågel-jägare | Spara dagens kuraterade fågel på 3 olika dagar. | `dailyBirdHistory.totalMatchCount() >= 3` (räknas som DISTINCT date). |

`daily_bird_hunter` kräver `DailyBirdHistory` SQLDelight-tabell:
```sql
CREATE TABLE DailyBirdHistory (
    date TEXT PRIMARY KEY NOT NULL,    -- ISO YYYY-MM-DD
    speciesId TEXT NOT NULL
);
```
`SaveObservationUseCase` slår upp dagens row efter save; om `obs.speciesId == row.speciesId` → incrementeras matchCount (vi räknar unika dagar genom DISTINCT `date`).

### 9. `UserPreferences`-tillägg

```kotlin
val pushPermissionAsked: Flow<Boolean>      // default false
val dailyBirdPushEnabled: Flow<Boolean>     // default true (gated av permission)
val streakRiskPushEnabled: Flow<Boolean>    // default true (gated av permission)
```

Default ON för båda push-toggles betyder: när permission ges → båda workers schemaläggs direkt. User kan stänga av individuellt via Settings.

**Permission-gating:** Default-ON-flagga ensam räcker inte — vi schemalägger workers ENBART om `NotificationManagerCompat.from(ctx).areNotificationsEnabled() == true`. Det betyder: utan beviljad permission visas inga notifications även om DataStore-flaggan är `true`. Konsekvensen: när user senare ger permission via systeminställningar + öppnar appen → `MainActivity.onCreate()` (Flow G) re-schedulerar workers.

### 10. Settings-sektion "Aviseringar"

Ny sektion mellan "Premium" och "Språk" i `SettingsScreen.kt`:
- Toggle: "Dagens fågel — daglig påminnelse" (bunden till `dailyBirdPushEnabled`).
- Toggle: "Streak-risk — söndag kväll" (bunden till `streakRiskPushEnabled`).
- Helpline om `NotificationManagerCompat.from(ctx).areNotificationsEnabled() == false`: "Aviseringar avstängda i Androids systeminställningar →" (tap → `Settings.ACTION_APP_NOTIFICATION_SETTINGS`).

### 11. Deep-link-handler

`MainActivity.onNewIntent()` parsar `birdy://species/{id}` + `birdy://identify`. Registreras via `<intent-filter>` i `AndroidManifest.xml` med `<data android:scheme="birdy"/>`. MainActivity är redan `singleTop`.

## Data flow

### Flow A — Dagens fågel på Identifiera-tab

```
ListenLauncherScreen
  ↓ LaunchedEffect(today) { dailyBird = appGraph.selectDailyBird(today) }
DailyBirdCard(bird = dailyBird)
  ↓ tap
navController.navigate(AppRoute.SpeciesProfile(bird.speciesId))
```

### Flow B — Permission-prompt efter första obs

```
SaveObservationUseCase.invoke(obs) → success
  ↓ AppGraph.onObservationSaved(obs)
if (!userPreferences.pushPermissionAsked.first() && Build.VERSION.SDK_INT >= 33)
  showPermissionPromptSheet = true
    ↓ user tap "Slå på"
ActivityCompat.requestPermissions(POST_NOTIFICATIONS)
  ↓ granted
notificationScheduler.scheduleDailyBird()
notificationScheduler.scheduleStreakRiskCheck()
userPreferences.setPushPermissionAsked(true)
```

### Flow C — DailyBirdWorker (24h periodic)

Beskrivet i komponentlistan ovan.

### Flow D — StreakRiskWorker (7d periodic, söndag 18:00 init-delay)

Beskrivet i komponentlistan ovan.

### Flow E — Deep-link från notification-tap

```
Notification tap → PendingIntent → MainActivity.onNewIntent(intent)
  ↓ uri = intent.data
when (uri.host) {
  "species" -> navController.navigate(AppRoute.SpeciesProfile(uri.lastPathSegment))
  "identify" -> navController.popBackStack(AppRoute.Listen, inclusive = false)
}
```

### Flow F — Badge-unlock: daily_bird_hunter

```
SaveObservationUseCase.invoke(obs) → success
  ↓
dailyBirdHistory.recordMatchIfAny(obs.speciesId, obs.savedAt.toLocalDate())
  ↓ om dagens row matchar
val count = dailyBirdHistory.totalMatchCount()
badgeUnlockEvaluator.evaluate(MATCH_DAILY_BIRD, count)
  ↓ count >= 3 och inte redan unlocked
UnlockQueue.enqueue(BadgeId.DAILY_BIRD_HUNTER)
```

### Flow G — App-start re-schedulering

```
MainActivity.onCreate()
  ↓
if (userPreferences.dailyBirdPushEnabled.first() &&
    NotificationManagerCompat.from(ctx).areNotificationsEnabled())
  notificationScheduler.scheduleDailyBird()   // KEEP-policy
// samma för streakRisk
```

## Error handling

**Filosofi:** Phase A är frivilliga retention-hooks. Allt failar tyst med fallback till "rendera ingenting" eller "logga + skip". Aldrig error-toast till user.

| Fall | Beteende |
|---|---|
| `DailyBirdSelector` returnerar `null` | `DailyBirdCard(null)` renderar inget. Tab ser ut som idag. Logga warning. |
| WorkManager-worker kastar exception | `Result.failure()` → exponential backoff retry (default 30s→5min). Inget notification visas. Logga warning. |
| POST_NOTIFICATIONS-permission nekas | `pushPermissionAsked = true` sätts. Workers schemaläggs inte. Settings visar helpline "Aviseringar avstängda →". |
| User ändrar system-tid manuellt | Acceptera. `LocalDate.now()` följer ny TZ. Worst-case: en notification fire fel tid nästa dag. |
| Deep-link med okänt `speciesId` | `SpeciesProfileScreen` har redan `null`-fallback (Plan 3). Återanvänds. |
| DataStore-läsning failar | Default-värden (push-toggles = false) → aviseringar pausas. Inget krasch. |
| `DailyBirdHistory`-table korrupt | `daily_bird_hunter` unlock:ar inte. Logga warning. Acceptabelt (retention-bonus, inte kärn-feature). |
| Notification-tap när app kör | `singleTop` + `onNewIntent` hanterar. Befintlig NavController. |
| WorkManager-doze/battery-fördröjning | Acceptera. Streak-risk-notisen är "påminnelse om söndag kväll", inte timing-kritisk. |
| Race: kort visas exakt vid midnatt | Gårdagens fågel visas tills nästa nav-event. 1-sekund's fönster, acceptabelt. |

## Testing

### Unit-tests (`:shared:domain` `jvmTest`)

- **`DailyBirdSelectorTest`:**
  - Deterministisk per datum (100 calls samma datum → samma resultat).
  - Olika datum → olika fågel (≥90% varians över 30 datum).
  - Abundance-viktning: 1000 datum → 70-80% common-bucket-andel.
  - Säsongsfiltrering (fixtur med arter i `breeding`/`absent`/`migrating`).
  - Region-filtrering (fixtur med art utanför Nordic).
  - `null`-fallback när inga arter passar månaden.

- **`StreakRiskEvaluatorTest`** (extraherad pure function ur `StreakRiskWorker`):
  - Inga obs denna vecka + streak ≥ 2 → fires.
  - Obs denna vecka → fires inte.
  - Streak < 2 veckor → fires inte.

- **`DailyBirdHistoryTest`** (SQLDelight in-memory driver):
  - `recordMatchIfAny()` increment:ar bara på match.
  - `totalMatchCount()` räknar unika dagar.

- **`BadgeCatalogTest`** (tillägg till befintligt test):
  - `early_pilgrim` triggar på obs sparad 06:00.
  - `sunday_birder` triggar efter 4 påföljande söndagar.
  - `daily_bird_hunter` triggar vid 3 matchade dagar.

### Compose UI-tests

Inga. Vi följer Plan 7-serien — device-verify är sanningskällan.

### Build-time validators

- `BadgeStringMapValidator` täcker automatiskt 6 nya rader (3 badges × {name, description}).

### Device-verify (SM-S918B)

10-12 canonical screenshots:

1. Identifiera-tab med `DailyBirdCard` renderat.
2. Tap på DailyBirdCard → `SpeciesProfileScreen`.
3. Spara obs → `PermissionPromptSheet` visas (första gången).
4. Tap "Slå på" → Android POST_NOTIFICATIONS-prompt.
5. Settings → "Aviseringar"-sektion (båda toggles ON).
6. Toggle OFF → screenshot.
7. Settings med permission-disabled-state + helpline.
8. DailyBird-notification i pull-down shade.
9. StreakRisk-notification i pull-down shade.
10. Tap på notification → deep-link landar på `SpeciesProfile` / Listen.
11. `early_pilgrim` unlocked efter manipulerad clock + obs.
12. BadgesScreen visar 3 nya premium-badges (låsta + 1 unlocked).

**Force-run-trick:** Debug-only menu-item under Settings ("DEV: Trigga Dagens fågel-push") gated bakom `BuildConfig.DEBUG` — anropar `WorkManager.getInstance(ctx).enqueue(OneTimeWorkRequestBuilder<DailyBirdWorker>().build())`. Enda realistiska vägen att verifiera notification-flödet utan 24h-väntan.

### Manuell QA-checklist

- [ ] Cold-start utan permission → ingen krasch, inga notifications.
- [ ] Permission given mid-session → workers schemaläggs (`adb shell dumpsys jobscheduler | grep birdy`).
- [ ] Force-stop → next launch re-schedulerar (KEEP-policy).
- [ ] Toggle OFF + force-run worker → ingen notification.
- [ ] `adb shell am start -d birdy://species/Q25485` → SpeciesProfile öppnas.
- [ ] DailyBirdCard tap → SpeciesProfile, back-knapp tillbaka till Identifiera.
- [ ] `PermissionPromptSheet` visas EN gång även efter app-restart.
- [ ] TalkBack läser `contentDescription` korrekt.

### CI

Inga nya steg. `:shared:domain:jvmTest` + `:composeApp:testDebugUnitTest` + `ktlintCheck` + `detekt` täcker.

## Open questions

Inga öppna frågor — alla 10 designbeslut är låsta. Spec är komplett.

## Klart-kriterier

- [ ] `DailyBirdCard` renderas på Identifiera-tab när det finns en kandidat.
- [ ] Tap på kortet → existerande `SpeciesProfileScreen`.
- [ ] `PermissionPromptSheet` triggas efter första lyckade obs (Android ≥ 13).
- [ ] `DailyBirdWorker` schedulerad 24h periodic, schemalägger notification 08:00 local.
- [ ] `StreakRiskWorker` schedulerad 7d periodic, schemalägger notification söndag 18:00 om streak i risk.
- [ ] 3 nya `BadgeRule`-entries triggar korrekt unlock-events.
- [ ] Settings "Aviseringar"-sektion med 2 toggles + helpline-state.
- [ ] Deep-links `birdy://species/{id}` + `birdy://identify` fungerar via `am start`.
- [ ] Alla nya strängar finns i sv + en.
- [ ] `:shared:domain:jvmTest` + `:composeApp:testDebugUnitTest` grönt.
- [ ] 10-12 device-screenshots tagna och committade till `docs/superpowers/screenshots/v1.1-phase-a/`.

## Relaterade dokument

- v1-design-spec: `docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md`
- Plan 5b (Gamification): `docs/superpowers/plans/2026-05-06-v1-05b-gamification.md` (BadgeCatalog + UnlockQueue + StreakHelpers)
- Plan 6b1 (Billing + launch-prep): `docs/superpowers/plans/2026-05-16-v1-06b1-billing-launch-prep.md` (lambda-DI + expect/actual mönster)
- Plan 6b3 (Premium content): `docs/superpowers/plans/2026-05-21-v1-06b3-premium-content.md` (10 nya badges referens)
- Onboarding v2 (v1.0.2): `docs/superpowers/plans/2026-05-25-onboarding-v2-scroll-story.md`
- Phase B deferral memory: `~/.claude/projects/.../memory/project_v1_2_phase_b_hooks.md`
- v1.1 workflow memory: `~/.claude/projects/.../memory/project_v1_1_workflow.md`
