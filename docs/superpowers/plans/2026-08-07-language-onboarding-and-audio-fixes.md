# Recensions-batchen vC127: språk-onboarding + ljud-ID-fixar — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fixa de två Play-recensionsdrivna problemen: (A) språkbyte som faktiskt fungerar på alla Android-versioner + språkval som scen 0 i onboardingen, (B) ljud-ID:ts fyra allvarliga buggar + audio-egna trösklar + sessions-ackumulator + live-UX.

**Architecture:** Spår A byter `MainActivity` till `AppCompatActivity` så den redan byggda Settings-språkväljaren fungerar på API 24–32 (idag no-op där), lägger en ny delad onboarding-scen 0 med eager-persist, och fixar iOS-grafens hårdlåsta svenska artinnehåll. Spår B fixar `AudioScanViewModel` (commonMain — blir i3:s iOS-kod): self-cancel-frysen, per-art sessions-ackumulator som ersätter dubbelinferensen, felhärdning av finalize (timeout/avbryt/degradering), recorder-felsignal, filter-före-ranking i BirdNET-postprocess, ärligt felläge istället för tyst fejk-klassificerare, samt audio-egna Match-trösklar.

**Tech Stack:** Kotlin Multiplatform + Compose Multiplatform 1.8.2, androidx.appcompat 1.7.0 (redan dependency), DataStore/NSUserDefaults via `shared/datastore`, TFLite/BirdNET-Lite via `shared/ml`, kotlinx-coroutines-test + Turbine i commonTest.

**Spec:** `docs/superpowers/specs/2026-08-06-language-onboarding-and-audio-fixes-design.md` (fynd L1–L10 med fil:rad-referenser)

## Global Constraints

- **Android shippbar efter varje commit.** Gate per task: `./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt` (kör `export JAVA_HOME="$HOME/.local/java21/Contents/Home"` först om Gradle klagar på Java).
- **K/N-gate** på task 4, 9 och 13: `./gradlew :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosSimulatorArm64`.
- **commonTest-testnamn måste vara giltiga ObjC-selektorer** (i2c T4a-trapen): backticks med ord/mellanslag är OK, men ALDRIG `(`, `)` eller `,` i testnamn. Använd `kotlin.test`, aldrig `java.*` i commonTest.
- **`CancellationException` ska ALLTID rethrowas**, aldrig sväljas av `runCatching`/`catch (Throwable)`.
- **Alla UI-strängar via `stringResource`** — enda undantaget är `SceneLanguage` som är avsiktligt tvåspråkig med hårdkodade literaler (dokumenterat i KDoc). Inga `%%`-escapes i strings.xml — procent pre-formatteras i Kotlin (`"${v}%"`) och skickas som `%1$s`-arg.
- **Trösklar (exakta värden):** PHOTO 0.50/0.35/0.15 (oförändrade), AUDIO 0.40/0.20/0.10 (nya, interimistiska), `AUTO_STOP_THRESHOLD` 0.65f (oförändrad), `ANALYZE_TIMEOUT_MS` 15_000L (ny).
- **Inga nya dependencies.** Inga versionsbumpar utom versionCode 126→127 / versionName "1.2.1"→"1.2.2" i task 13.
- **Loggning = `println("Birdy/audio: …")`** (i2a-mönstret; System.out → logcat). Ingen telemetri.
- **Steg 0 (utanför planen, Albin manuellt):** vC126-AAB:n laddas upp i Play Console FÖRE denna batch släpps.

---

# Spår A: Språk

### Task 1: AppCompat-motorn — språkbyte som fungerar på API 24–32

**Files:**
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt:80` (klassdeklaration + import)
- Create: `androidApp/src/main/kotlin/se/birdy/android/BirdyApplication.kt`
- Modify: `androidApp/src/main/AndroidManifest.xml` (application-elementet, ~rad 27)
- Modify: `androidApp/src/main/res/values/themes.xml` (Theme.Birdy parent)
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/ui/settings/LocaleApplier.android.kt` (hela `apply`)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsEffect.kt:6-8` (rename)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsViewModel.kt:76`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsScreen.kt:145`

**Interfaces:**
- Consumes: `UserPreferencesStore(context).preferences().appLanguage: Flow<AppLanguage>` (befintlig; DataStore-instansen är en top-level `Context.userPrefsDataStore`-delegat = process-singleton, så en andra `UserPreferencesStore` i Application är säker), `AppLanguage.toLocaleTagOrEmpty()` från `se.birdy.app.i18n.LanguageTag`.
- Produces: `SettingsEffect.ApplyLocale(tag)` (ersätter `RestartForLocale`); `AppLocaleApplier.apply(tag)` som fungerar på alla API-nivåer. Task 2/3 använder `applyLocale(tag)` (oförändrad expect-signatur).

- [ ] **Step 1: Migrera MainActivity till AppCompatActivity**

I `MainActivity.kt`: byt bassklass på rad 80 och lägg till import.

```kotlin
// FÖRE:
class MainActivity : ComponentActivity() {
// EFTER:
class MainActivity : AppCompatActivity() {
```

Import att lägga till: `import androidx.appcompat.app.AppCompatActivity`. Ta INTE bort `androidx.activity.ComponentActivity`-importen om den används på andra ställen i filen (kör `rg -n "ComponentActivity" androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt` — om enda träffen var klassdeklarationen, ta bort importen). `AppCompatActivity` ärver `ComponentActivity`, så `enableEdgeToEdge`, activity-result-API:erna och `AudioScanScreenHost.android.kt:26`:s cast `LocalContext.current as ComponentActivity` fortsätter fungera.

- [ ] **Step 2: Byt Theme.Birdy-parent till AppCompat**

I `androidApp/src/main/res/values/themes.xml`:

```xml
<!-- FÖRE: -->
    <style name="Theme.Birdy" parent="android:Theme.Material.Light.NoActionBar">
<!-- EFTER: -->
    <style name="Theme.Birdy" parent="Theme.AppCompat.Light.NoActionBar">
```

`windowBackground`-raden och hela `Theme.Birdy.Starting` (SplashScreen-temat med `postSplashScreenTheme`) lämnas orörda. Utan denna ändring kraschar appen vid start med "You need to use a Theme.AppCompat theme".

- [ ] **Step 3: Skriv om AppLocaleApplier**

MEDVETEN AVVIKELSE från specens formulering "en enda väg via AppCompatDelegate": 33+-grenen behåller den redan produktionsbevisade `LocaleManager`-vägen (AppCompatDelegate-anrop från `Application.onCreate` på 33+ har en odokumenterad context-init-beroende; LocaleManager-vägen är riskfri och beteendemässigt identisk). Specens MÅL — fungerande byte på alla API-nivåer + `Locale.setDefault`-synk på <33 — uppfylls exakt. Ersätt hela `apply`-metoden i `LocaleApplier.android.kt` (behåll `object AppLocaleApplier`, `init`, `appContext` och `actual fun applyLocale` som de är):

```kotlin
    internal fun apply(tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+: LocaleManager persistar overriden + recreatar aktiviteter (bevisat fungerande väg).
            val ctx = appContext ?: return
            val lm = ctx.getSystemService(LocaleManager::class.java) ?: return
            lm.applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            // API 24–32: AppCompatDelegate fungerar nu eftersom MainActivity är AppCompatActivity —
            // recreatar aktiva AppCompat-aktiviteter och applicerar via deras konfiguration.
            // Locale.setDefault synkas explicit: AppCompat garanterar bara aktivitets-resurser,
            // men WorkManager-notisers getString + datumformat läser processens default-locale.
            val target =
                if (tag.isEmpty()) {
                    Resources.getSystem().configuration.locales[0]
                } else {
                    Locale.forLanguageTag(tag)
                }
            Locale.setDefault(target)
            AppCompatDelegate.setApplicationLocales(
                if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
            )
        }
    }
```

Nya imports: `import android.content.res.Resources` och `import java.util.Locale`. Befintliga imports (`LocaleManager`, `LocaleList`, `AppCompatDelegate`, `LocaleListCompat`, `Build`) behålls.

- [ ] **Step 4: Skapa BirdyApplication + registrera i manifestet**

Ny fil `androidApp/src/main/kotlin/se/birdy/android/BirdyApplication.kt`:

```kotlin
package se.birdy.android

import android.app.Application
import android.os.Build
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import se.birdy.app.i18n.toLocaleTagOrEmpty
import se.birdy.app.ui.settings.AppLocaleApplier
import se.birdy.datastore.UserPreferencesStore

/**
 * Återapplicerar det persistade app-språket före första aktiviteten.
 *
 * Endast API < 33: på 33+ persistar systemets LocaleManager overriden själv.
 * DataStore är enda sanningskällan (ingen appcompat autoStoreLocales-dubbellagring);
 * applicering här — innan någon aktivitet finns — undviker en extra recreate på kallstart.
 * runBlocking på en enda DataStore-läsning är ~ms (samma mönster som MainActivity.buildAppGraph).
 */
class BirdyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLocaleApplier.init(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val stored =
                runBlocking { UserPreferencesStore(this@BirdyApplication).preferences().appLanguage.first() }
            val tag = stored.toLocaleTagOrEmpty()
            if (tag.isNotEmpty()) {
                AppLocaleApplier.apply(tag)
            }
        }
    }
}
```

OBS: `AppLocaleApplier.apply` är `internal` i composeApp-modulen — androidApp är en annan modul. Ändra synligheten i `LocaleApplier.android.kt` från `internal fun apply` till `fun apply` (och lägg en KDoc-rad: "Publik för BirdyApplication (androidApp-modulen) — appen ska annars gå via [applyLocale]."). `MainActivity.kt:208`:s befintliga `AppLocaleApplier.init(applicationContext)` behålls (harmlös dubbel-init).

I `AndroidManifest.xml`, lägg `android:name` först i application-elementet:

```xml
    <application
        android:name=".BirdyApplication"
        android:label="@string/app_name"
```

- [ ] **Step 5: Döp om SettingsEffect.RestartForLocale → ApplyLocale**

`SettingsEffect.kt`:

```kotlin
    /** Applicerar valt språk. AppCompat/LocaleManager recreatar aktiviteten automatiskt — ingen omstart behövs. */
    data class ApplyLocale(
        val tag: String,
    ) : SettingsEffect
```

`SettingsViewModel.kt:76`: `_effects.send(SettingsEffect.ApplyLocale(value.toLocaleTagOrEmpty()))`
`SettingsScreen.kt:145`: `is SettingsEffect.ApplyLocale -> applyLocale(effect.tag)`

Kör `rg -n "RestartForLocale" composeApp/src` — fixa varje kvarvarande träff (t.ex. tester) till `ApplyLocale`.

- [ ] **Step 6: Kör gaten**

Run: `export JAVA_HOME="$HOME/.local/java21/Contents/Home"; ./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt`
Expected: BUILD SUCCESSFUL (inga nya tester i denna task — plattformsglue; beteendet verifieras i emulator-verify).

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat(lang): AppCompatActivity-migrering — språkväljaren fungerar nu på API 24-32

MainActivity -> AppCompatActivity + Theme.AppCompat-parent; AppLocaleApplier
<33-grenen går via AppCompatDelegate (var dokumenterad no-op med ComponentActivity)
+ Locale.setDefault-synk för icke-Compose-konsumenter; ny BirdyApplication
återapplicerar sparat språk före första aktiviteten (ingen dubbel-recreate);
RestartForLocale -> ApplyLocale (omstarts-semantiken är död)."
```

---

### Task 2: OnboardingViewModel — språkval med eager persist (TDD)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/OnboardingUiState.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/OnboardingViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/onboarding/OnboardingViewModelTest.kt`

**Interfaces:**
- Consumes: `UserPreferences.appLanguage`/`setAppLanguage` (shared/datastore), `AppLanguage` enum, `AppLanguage.toLocaleTagOrEmpty()` (se.birdy.app.i18n), `applyLocale(tag)` (se.birdy.app.ui.settings, expect/actual).
- Produces: `OnboardingUiState.Visible.selectedLanguage: AppLanguage?`; `OnboardingViewModel.selectLanguage(value: AppLanguage)`; ctor-param `applyLocaleFn: (String) -> Unit = ::applyLocale`; `MAX_PAGE_INDEX = 7`. Task 3 konsumerar alla tre.

- [ ] **Step 1: Skriv failing tests**

I `OnboardingViewModelTest.kt` — ersätt de två sidindex-testerna och lägg tre nya (imports att lägga till: `se.birdy.datastore.AppLanguage`):

```kotlin
    @Test
    fun `setPageIndex moves to page 7`() =
        runTest {
            val vm = OnboardingViewModel(InMemoryUserPreferences(), "Min")
            vm.setPageIndex(7)
            vm.state.test {
                val s = awaitItem()
                assertTrue(s is OnboardingUiState.Visible)
                assertEquals(7, s.pageIndex)
            }
        }

    @Test
    fun `setPageIndex coerces 8 to 7 MAX_PAGE_INDEX`() =
        runTest {
            val vm = OnboardingViewModel(InMemoryUserPreferences(), "Min")
            vm.setPageIndex(8)
            vm.state.test {
                val s = awaitItem()
                assertTrue(s is OnboardingUiState.Visible)
                assertEquals(7, s.pageIndex)
            }
        }

    @Test
    fun `selectLanguage persists immediately and applies locale`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val applied = mutableListOf<String>()
            val vm = OnboardingViewModel(prefs, "Min", applyLocaleFn = { applied += it })
            vm.selectLanguage(AppLanguage.EN)
            prefs.appLanguage.test { assertEquals(AppLanguage.EN, awaitItem()) }
            assertEquals(listOf("en"), applied)
            val s = vm.state.value
            assertTrue(s is OnboardingUiState.Visible)
            assertEquals(AppLanguage.EN, s.selectedLanguage)
        }

    @Test
    fun `selectLanguage persists in replay mode too`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val vm = OnboardingViewModel(prefs, "Min", isReplay = true, applyLocaleFn = {})
            vm.selectLanguage(AppLanguage.SV)
            prefs.appLanguage.test { assertEquals(AppLanguage.SV, awaitItem()) }
        }

    @Test
    fun `init reads stored non-system language into state`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            prefs.setAppLanguage(AppLanguage.EN)
            val vm = OnboardingViewModel(prefs, "Min", applyLocaleFn = {})
            vm.state.test {
                val s = awaitItem()
                assertTrue(s is OnboardingUiState.Visible)
                assertEquals(AppLanguage.EN, s.selectedLanguage)
            }
        }
```

De gamla testerna `setPageIndex moves to page 6` och `setPageIndex coerces 7 to 6 MAX_PAGE_INDEX` TAS BORT (ersätts av 7/8-varianterna ovan). Befintliga `OnboardingViewModel(...)`-konstruktioner i övriga tester kompilerar oförändrat (nya parametern har default). OBS: `applyLocaleFn = {}` behövs i testerna som anropar `selectLanguage`/init-läsning så inte den riktiga `applyLocale`-actualen körs på JVM (Android-actualen kräver initierad appContext — defaulten får inte röras i test).

- [ ] **Step 2: Kör testerna, verifiera att de failar**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.onboarding.OnboardingViewModelTest"`
Expected: FAIL — `selectedLanguage`/`selectLanguage`/`applyLocaleFn` finns inte; kompileringsfel.

- [ ] **Step 3: Implementera**

`OnboardingUiState.kt`:

```kotlin
package se.birdy.app.ui.onboarding

import se.birdy.datastore.AppLanguage

sealed interface OnboardingUiState {
    data object Loading : OnboardingUiState

    data class Visible(
        val pageIndex: Int,
        val nameInput: String,
        val selectedLanguage: AppLanguage? = null,
    ) : OnboardingUiState

    data object Done : OnboardingUiState
}
```

`OnboardingViewModel.kt` — ny ctor-param, init-block, `selectLanguage`, bumpad `MAX_PAGE_INDEX`:

```kotlin
package se.birdy.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import se.birdy.app.i18n.toLocaleTagOrEmpty
import se.birdy.app.ui.settings.applyLocale
import se.birdy.datastore.AppLanguage
import se.birdy.datastore.UserPreferences

class OnboardingViewModel(
    private val prefs: UserPreferences,
    private val defaultFallbackName: String,
    private val isReplay: Boolean = false,
    private val applyLocaleFn: (String) -> Unit = ::applyLocale,
) : ViewModel() {
    private val _state =
        MutableStateFlow<OnboardingUiState>(
            OnboardingUiState.Visible(pageIndex = 0, nameInput = ""),
        )
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = prefs.appLanguage.first()
            if (stored != AppLanguage.SYSTEM) {
                val current = _state.value
                if (current is OnboardingUiState.Visible && current.selectedLanguage == null) {
                    _state.value = current.copy(selectedLanguage = stored)
                }
            }
        }
    }

    fun setPageIndex(index: Int) {
        val current = _state.value
        if (current is OnboardingUiState.Visible) {
            _state.value = current.copy(pageIndex = index.coerceIn(0, MAX_PAGE_INDEX))
        }
    }

    fun onNameChange(value: String) {
        val current = _state.value
        if (current is OnboardingUiState.Visible) {
            _state.value = current.copy(nameInput = value)
        }
    }

    /**
     * Eager persist BY DESIGN (inte i [complete]): (1) replay-lägets write-skip gäller
     * bara namn + hasSeenOnboarding — språket är en riktig inställning även i replay,
     * (2) värdet måste vara på disk INNAN AppCompat-recreaten läser om det.
     */
    fun selectLanguage(value: AppLanguage) {
        val current = _state.value as? OnboardingUiState.Visible ?: return
        _state.value = current.copy(selectedLanguage = value)
        viewModelScope.launch {
            prefs.setAppLanguage(value)
            applyLocaleFn(value.toLocaleTagOrEmpty())
        }
    }

    fun complete() {
        val current = _state.value
        if (current !is OnboardingUiState.Visible) return
        val trimmed = current.nameInput.trim()
        val resolvedName = trimmed.ifEmpty { defaultFallbackName }
        viewModelScope.launch {
            if (!isReplay) {
                prefs.setUserName(resolvedName)
                prefs.setHasSeenOnboarding(true)
            }
            _state.value = OnboardingUiState.Done
        }
    }

    private companion object {
        const val MAX_PAGE_INDEX = 7 // 8 pages: 0 (language), 1..7
    }
}
```

- [ ] **Step 4: Kör testerna, verifiera gröna**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.onboarding.OnboardingViewModelTest"`
Expected: PASS (alla, inkl. de 4 orörda).

- [ ] **Step 5: Kör gaten + commit**

Run: gaten från Global Constraints.
Expected: BUILD SUCCESSFUL.

```bash
git add -A && git commit -m "feat(lang): OnboardingViewModel språkval — eager persist + applyLocale, MAX_PAGE_INDEX 7

selectLanguage skriver setAppLanguage DIREKT vid val (replay-säkert per
konstruktion — isReplay-guarden rör bara namn+hasSeenOnboarding) och
applicerar locale via injicerbar applyLocaleFn; init förifyller från sparad
icke-SYSTEM-pref."
```

---

### Task 3: SceneLanguage + OnboardingScreen-wiring (scen 0)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/scenes/SceneLanguage.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/OnboardingScreen.kt` (SCENE_COUNT, dispatch, ny param)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppGate.kt:46-51`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt:352-358`

**Interfaces:**
- Consumes: `OnboardingUiState.Visible.selectedLanguage` + `vm::selectLanguage` (task 2), `IntroSceneScaffold(eyebrow, headline, sub, pageOffset, visual)`, `LocaleResolver.resolve(override, systemTag)` (se.birdy.app.i18n), `androidx.compose.ui.text.intl.Locale.current`.
- Produces: `SceneLanguage(pageOffset: Float, selected: AppLanguage?, onSelect: (AppLanguage) -> Unit)`; `OnboardingScreen(..., onLanguageSelect: (AppLanguage) -> Unit, ...)`.

- [ ] **Step 1: Skapa SceneLanguage**

```kotlin
package se.birdy.app.ui.onboarding.scenes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.i18n.LocaleResolver
import se.birdy.app.ui.onboarding.components.IntroSceneScaffold
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.content.Locale as ContentLocale
import se.birdy.datastore.AppLanguage

/**
 * Scen 0 — språkval. AVSIKTLIGT tvåspråkig med hårdkodade literaler: skärmen
 * visas INNAN användaren valt språk, så båda språken renderas samtidigt
 * (etablerad konvention för språkväljare). Dokumenterat undantag från
 * stringResource-regeln i trap-katalogen.
 *
 * Val persisteras direkt (task 2) → AppCompat recreatar aktiviteten → pagern
 * nollställs till sida 0 = den här scenen, nu på valt språk. Flyttas scenen
 * någonsin från index 0 måste recreate-beteendet omprövas (state-förlust).
 */
@Composable
fun SceneLanguage(
    pageOffset: Float,
    selected: AppLanguage?,
    onSelect: (AppLanguage) -> Unit,
) {
    val fallback =
        when (LocaleResolver.resolve(override = null, systemTag = Locale.current.toLanguageTag())) {
            ContentLocale.SV -> AppLanguage.SV
            ContentLocale.EN -> AppLanguage.EN
        }
    val effective = selected ?: fallback

    IntroSceneScaffold(
        eyebrow = "SPRÅK · LANGUAGE · NO 0",
        headline = "Välj språk · *Choose language*",
        sub = "Kan ändras när som helst i Inställningar · Change anytime in Settings",
        pageOffset = pageOffset,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LanguageChoice(
                label = "Svenska",
                isSelected = effective == AppLanguage.SV,
                onClick = { onSelect(AppLanguage.SV) },
            )
            Spacer(Modifier.height(12.dp))
            LanguageChoice(
                label = "English",
                isSelected = effective == AppLanguage.EN,
                onClick = { onSelect(AppLanguage.EN) },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LanguageChoice(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) AccentCopper else MarginaliaInk.copy(alpha = 0.3f)
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier =
            Modifier
                .width(240.dp)
                .clip(shape)
                .border(width = if (isSelected) 2.dp else 1.dp, color = borderColor, shape = shape)
                .background(if (isSelected) AccentCopper.copy(alpha = 0.08f) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 18.sp, color = MarginaliaInk, modifier = Modifier.weight(1f))
        if (isSelected) {
            Text(text = "✓", fontSize = 18.sp, color = AccentCopper)
        }
    }
}
```

- [ ] **Step 2: Wire:a OnboardingScreen**

I `OnboardingScreen.kt`: `SCENE_COUNT` 7→8; ny param + dispatch (alla index skiftar +1, `isActive`-jämförelserna följer med):

```kotlin
private const val SCENE_COUNT = 8
```

```kotlin
fun OnboardingScreen(
    state: OnboardingUiState.Visible,
    onPageChange: (Int) -> Unit,
    onNameChange: (String) -> Unit,
    onLanguageSelect: (AppLanguage) -> Unit,
    onComplete: () -> Unit,
    isReplay: Boolean = false,
) {
```

```kotlin
            when (page) {
                0 ->
                    SceneLanguage(
                        pageOffset = pageOffset,
                        selected = state.selectedLanguage,
                        onSelect = onLanguageSelect,
                    )
                1 -> SceneHero(pageOffset = pageOffset)
                2 -> ScenePhoto(pageOffset = pageOffset, isActive = pagerState.currentPage == 2)
                3 -> SceneAudio(pageOffset = pageOffset, isActive = pagerState.currentPage == 3)
                4 -> SceneJournal(pageOffset = pageOffset, isActive = pagerState.currentPage == 4)
                5 -> SceneBadges(pageOffset = pageOffset, isActive = pagerState.currentPage == 5)
                6 -> ScenePrivacy(pageOffset = pageOffset, isActive = pagerState.currentPage == 6)
                7 ->
                    SceneName(
                        nameInput = state.nameInput,
                        onNameChange = onNameChange,
                        onComplete = onComplete,
                    )
            }
```

Imports att lägga till: `se.birdy.app.ui.onboarding.scenes.SceneLanguage`, `se.birdy.datastore.AppLanguage`.

- [ ] **Step 3: Uppdatera call-sites**

`AppGate.kt:46-51`:

```kotlin
                        is OnboardingUiState.Visible ->
                            OnboardingScreen(
                                state = s,
                                onPageChange = vm::setPageIndex,
                                onNameChange = vm::onNameChange,
                                onLanguageSelect = vm::selectLanguage,
                                onComplete = vm::complete,
                            )
```

`AppScaffold.kt:352-358` (replay-routen):

```kotlin
                        se.birdy.app.ui.onboarding.OnboardingScreen(
                            state = s,
                            onPageChange = vm::setPageIndex,
                            onNameChange = vm::onNameChange,
                            onLanguageSelect = vm::selectLanguage,
                            onComplete = { navController.popBackStack() },
                            isReplay = true,
                        )
```

Kör `rg -n "OnboardingScreen\(" composeApp/src` — fixa ev. ytterligare call-sites (t.ex. previews) på samma sätt.

- [ ] **Step 4: Kör gaten**

Run: gaten från Global Constraints.
Expected: BUILD SUCCESSFUL (scenen är UI utan egen unit-test; VM-logiken täcktes i task 2; visuell verify sker i emulator-rundan).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(lang): SceneLanguage som onboarding-scen 0 (tvåspråkig, eager select)

SCENE_COUNT 7->8; förval härleds från systemlocale via LocaleResolver;
val -> selectLanguage (persist + applyLocale) -> AppCompat-recreate landar
tillbaka på scen 0 i valt språk som bekräftelse."
```

---

### Task 4: IosAppGraph — artinnehåll följer språkvalet (fixar hårdlåst SV)

**Files:**
- Modify: `composeApp/src/iosMain/kotlin/se/birdy/app/IosAppGraph.kt:87-100`

**Interfaces:**
- Consumes: `LocaleResolver.resolve` + `AppLanguage.toLocaleTagOrNull()` (commonMain, samma som Android-vägen i MainActivity.kt:323-328), `UserPreferencesStore(null).preferences()`.
- Produces: `AppGraph.defaultLocale` satt korrekt på iOS (idag utelämnad → default `Locale.SV`).

- [ ] **Step 1: Implementera**

I `buildIosAppGraph()`: lyft ut prefs till en variabel, resolva locale, skicka båda:

```kotlin
    val userPreferences = UserPreferencesStore(null).preferences()
    // Spegel av MainActivity.buildAppGraph():s locale-resolution (rad 323-328).
    // Utan detta är artinnehållet hårdlåst till AppGraph-defaulten Locale.SV
    // oavsett enhetsspråk och app_language-pref. Ny graf byggs per appstart,
    // så språkbyten slår igenom vid nästa launch (live-byte = i4).
    val storedLanguage = runBlocking { userPreferences.appLanguage.first() }
    val resolvedLocale =
        LocaleResolver.resolve(
            override = storedLanguage.toLocaleTagOrNull(),
            systemTag = (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "en",
        )
    return AppGraph(
        ...befintliga argument oförändrade...
        userPreferences = userPreferences,
        ...
        versionName = "1.2.0-ios-i2c",
        defaultLocale = resolvedLocale,
    )
```

Imports att lägga till: `kotlinx.coroutines.flow.first`, `platform.Foundation.NSLocale`, `se.birdy.app.i18n.LocaleResolver`, `se.birdy.app.i18n.toLocaleTagOrNull`. (`runBlocking` är redan importerad.) Om K/N-bindningen för `NSLocale.preferredLanguages` inte kompilerar som skrivet, använd `platform.Foundation.NSLocale.Companion.preferredLanguages` eller fallbacken `NSLocale.currentLocale.languageCode` — kompilatorn avgör; semantiken (första föredragna språktaggen) ska bevaras.

- [ ] **Step 2: Kör K/N-gaten + Android-gaten**

Run: `./gradlew :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosSimulatorArm64` + Android-gaten.
Expected: BUILD SUCCESSFUL båda.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "fix(ios): IosAppGraph resolvar defaultLocale ur app_language + NSLocale

Artinnehållet på iOS var hårdlåst till Locale.SV (defaultLocale skickades
aldrig). Nu samma LocaleResolver-väg som Android; slår igenom per appstart."
```

---

# Spår B: Ljud-ID

### Task 5: L1 — frys-vid-träff-fixen (TDD)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanViewModel.kt:192-194, 220-222`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/audio/AudioScanViewModelTest.kt:101-119`

**Interfaces:**
- Consumes: befintlig VM-struktur (inferenceJob-parent på rad 100/177, `finalizeAndNavigate`).
- Produces: auto-stopp som alltid når `NavigateToMatch`. Ingen API-ändring.

- [ ] **Step 1: Skärp testet till failing**

Ersätt assertionen i `autoStops_whenConfidenceReachesThreshold_after3s` (rad 113-117) — den accepterar idag `Analyzing` som pass och dokumenterar därmed buggen:

```kotlin
            assertTrue(
                vm.state.value is AudioScanState.NavigateToMatch,
                "auto-stop must reach NavigateToMatch (Analyzing-hang = L1 self-cancel bug), got ${vm.state.value}",
            )
```

- [ ] **Step 2: Kör testet, verifiera FAIL**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.audio.AudioScanViewModelTest"`
Expected: FAIL på `autoStops_...` med "got Analyzing" — beviset för self-cancel-kedjan (finalize körs som barn till inferenceJob → `inferenceJob?.cancel()` på rad 218 cancellar sig själv → `ensureActive()` på rad 243 avbryter → state fastnar).

- [ ] **Step 3: Fixa — lansera finalize på viewModelScope + rethrowa CE**

Rad 192-195, auto-stopp-grenen (spegel av MANUAL rad 208 och CAP rad 126):

```kotlin
                    if (top.confidence >= AUTO_STOP_THRESHOLD) {
                        // Finalize FÅR INTE köras inline här: denna coroutine är barn till
                        // inferenceJob, och finalizeAndNavigate cancellar inferenceJob →
                        // self-cancel → permanent Analyzing-häng (i produktion t.o.m. vC126).
                        viewModelScope.launch { finalizeAndNavigate(reason = StopReason.AUTO) }
                        return@launch
                    }
```

Rad 220-222, stopAndFlush-hämtningen — `runCatching` svalde `CancellationException` (repo-anti-mönstret):

```kotlin
        val fullPcm =
            try {
                handle?.stopAndFlush() ?: ShortArray(bufferEnd)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                fullBuffer.copyOf(bufferEnd)
            }
```

- [ ] **Step 4: Kör testerna, verifiera gröna**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.audio.AudioScanViewModelTest"`
Expected: PASS — alla 9, inkl. det skärpta.

- [ ] **Step 5: Kör gaten + commit**

```bash
git add -A && git commit -m "fix(audio): auto-stopp cancellade sin egen coroutine -> permanent Analyzing-frys

finalizeAndNavigate kördes inline i inferens-barnet och cancellade sin
förälder (inferenceJob) -> stopAndFlush CE svaldes av runCatching (mic
stoppades aldrig) -> ensureActive avbröt -> state fast i Analyzing för
evigt, exakt när en fågel identifierades säkert. Nu lanseras finalize på
viewModelScope som MANUAL/CAP-vägarna; CE rethrowas. Testet som accepterade
Analyzing som pass är skärpt till strikt NavigateToMatch."
```

---

### Task 6: Sessions-ackumulator + top-3 hela vägen (TDD)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanViewModel.kt` (streaming-uppdatering, finalize, analyzeAndNavigate)
- Modify: `shared/ml/src/commonMain/kotlin/se/birdy/ml/ScanSource.kt:19` (nullable audioWavPath)
- Modify: `shared/ml/src/commonMain/kotlin/se/birdy/ml/ScanSourceSerialization.kt:57`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/audio/AudioScanViewModelTest.kt`

**Interfaces:**
- Consumes: `Top1(speciesId, confidence, pcmOffset, pcmEnd)` (befintlig), `ClassificationResult`, `ScanSourceSerialization`.
- Produces: `ScanSource.Audio.audioWavPath: String?` (var `String`); serialiserad `classification.results` innehåller upp till 3 arter rankade på sessions-max (var alltid exakt 1). `MatchResultViewModel` konsumerar redan listan generiskt — Disambig får nu riktiga kandidater.

- [ ] **Step 1: Skriv failing tests**

Lägg i `AudioScanViewModelTest.kt` (imports att lägga till: `kotlinx.serialization.json.Json`, `se.birdy.ml.ScanSourceSerialization`):

```kotlin
    /** Klassificerare med olika resultatlistor per anrop — för ackumulator-tester. */
    private class MultiResultClassifier(
        private val resultsPerCall: List<List<ClassificationResult>>,
    ) : BirdAudioClassifier {
        override val info =
            AudioModelInfo(
                modelVersion = "multi",
                inputShape = listOf(1, 144_000),
                outputShape = listOf(1, 1),
                coveragePct = 100.0,
            )
        var calls = 0
            private set

        override suspend fun classify(input: AudioInput): AudioClassification {
            val results = resultsPerCall.getOrNull(calls) ?: emptyList()
            calls++
            return AudioClassification(results = results, inferenceMs = 5L, modelVersion = "multi")
        }

        override fun close() {}
    }

    @Test
    fun finalize_ranksSessionAccumulatorAcrossWindows_top3InSource() =
        runTest {
            // Fönster 1: A=0.30, Fönster 2: B=0.45 + A=0.20, Fönster 3: C=0.10.
            // Sessionsmax: B=0.45, A=0.30, C=0.10 — i den ordningen i källan.
            val classifier =
                MultiResultClassifier(
                    listOf(
                        listOf(ClassificationResult("QA", 0.30f)),
                        listOf(ClassificationResult("QB", 0.45f), ClassificationResult("QA", 0.20f)),
                        listOf(ClassificationResult("QC", 0.10f)),
                    ),
                )
            val recorder = FakeStreamingRecorder()
            val vm2 =
                AudioScanViewModel(
                    classifierProvider = { Pair(classifier, AudioClassifierMode.REAL) },
                    recorder = recorder,
                    waveformRenderer = FakeWaveformRenderer(),
                    audioStorageDir = { "/tmp/audio" },
                    clock = { 0L },
                    normalizer = stubNormalizer,
                    ioDispatcher = Dispatchers.Unconfined,
                    inferenceDispatcher = Dispatchers.Unconfined,
                )
            vm2.onPermissionState(PermissionState.Granted)
            vm2.startRecording()
            recorder.emitChunks(200) // ≥3 fönster (144k + 2×48k = 240k samples = 150 chunks)
            advanceUntilIdle()
            vm2.stopRecording()
            advanceUntilIdle()

            val state = vm2.state.value
            assertTrue(state is AudioScanState.NavigateToMatch, "got $state")
            val source = Json.decodeFromString<ScanSourceSerialization>(state.sourceJson)
            val results = source.classification.results
            assertEquals(listOf("QB", "QA", "QC"), results.map { it.speciesId })
            assertEquals(0.45f, results[0].confidence)
            assertEquals(0.30f, results[1].confidence)
            // Ingen om-klassificering av bästa fönstret: exakt de 3 streaming-anropen.
            assertEquals(3, classifier.calls)
        }

    @Test
    fun finalize_withEmptyAccumulator_runsFallbackClassifyOnLastWindow() =
        runTest {
            // Alla streaming-fönster ger tomma resultat -> ackumulatorn är tom ->
            // finalize kör EN fallback-klassificering på sista fönstret.
            val classifier = MultiResultClassifier(List(10) { emptyList() })
            val recorder = FakeStreamingRecorder()
            val vm =
                AudioScanViewModel(
                    classifierProvider = { Pair(classifier, AudioClassifierMode.REAL) },
                    recorder = recorder,
                    waveformRenderer = FakeWaveformRenderer(),
                    audioStorageDir = { "/tmp/audio" },
                    clock = { 0L },
                    normalizer = stubNormalizer,
                    ioDispatcher = Dispatchers.Unconfined,
                    inferenceDispatcher = Dispatchers.Unconfined,
                )
            vm.onPermissionState(PermissionState.Granted)
            vm.startRecording()
            recorder.emitChunks(120)
            advanceUntilIdle()
            val streamingCalls = classifier.calls
            vm.stopRecording()
            advanceUntilIdle()

            assertTrue(vm.state.value is AudioScanState.NavigateToMatch, "got ${vm.state.value}")
            assertEquals(streamingCalls + 1, classifier.calls)
        }
```

- [ ] **Step 2: Kör, verifiera FAIL**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.audio.AudioScanViewModelTest"`
Expected: FAIL — `finalize_ranksSessionAccumulator...` får 1 resultat (bara `results.first()` packas idag, rad 264-282) och `calls == 4` (om-klassificeringen), `finalize_withEmptyAccumulator...` kan ge annan call-count.

- [ ] **Step 3: Implementera ackumulatorn**

I `AudioScanViewModel.kt`:

(a) Ersätt fältet `bestSoFar` (rad 63) med en immutable-map-swap (volatile referens; single-flight-inferensen är enda skrivaren, finalize läser senaste referensen):

```kotlin
    /** Per-art sessionsmax över alla fönster: speciesId -> bästa observation. */
    @Volatile private var sessionScores: Map<String, Top1> = emptyMap()
```

`startRecording()`-nollställningen (rad 104) blir `sessionScores = emptyMap()`, och i `cancelRecording()` (rad 297) ersätts `bestSoFar = null` med `sessionScores = emptyMap()`.

(b) Streaming-uppdateringen (rad 182-196) — ackumulera ALLA resultat i fönstret, härled bestSoFar ur mappen, auto-stoppa på fönstrets top-1 som förut:

```kotlin
                val top = result.results.firstOrNull()
                result.results.forEach { r ->
                    val existing = sessionScores[r.speciesId]
                    if (existing == null || r.confidence > existing.confidence) {
                        sessionScores = sessionScores +
                            (r.speciesId to Top1(r.speciesId, r.confidence, windowStart, windowEnd))
                    }
                }
                if (result.results.isNotEmpty()) {
                    val best = sessionScores.values.maxByOrNull { it.confidence }
                    _state.update { s ->
                        if (s is AudioScanState.Recording) s.copy(bestSoFar = best) else s
                    }
                }
                if (top != null && top.confidence >= AUTO_STOP_THRESHOLD) {
                    viewModelScope.launch { finalizeAndNavigate(reason = StopReason.AUTO) }
                    return@launch
                }
```

(OBS: task 5:s viewModelScope-launch behålls exakt; `Recording.bestSoFar` i statet behåller typen `Top1?`.)

(c) `finalizeAndNavigate` (rad 224-236): best-fönster-extraktionen försvinner — window behövs bara för fallbacken:

```kotlin
        val fullPcm = /* task 5:s try/catch, oförändrad */

        val windowEnd = fullPcm.size
        val windowStart = (windowEnd - WINDOW_SAMPLES).coerceAtLeast(0)
        val window = fullPcm.copyOfRange(windowStart, windowEnd)

        analyzeAndNavigate(fullPcm = fullPcm, window = window)
```

(d) `analyzeAndNavigate` (rad 239-288): ranka ackumulatorn; klassificera bara som fallback; packa hela listan:

```kotlin
    private suspend fun analyzeAndNavigate(
        fullPcm: ShortArray,
        window: ShortArray,
    ) {
        coroutineContext.ensureActive()
        val ranked = sessionScores.values.sortedByDescending { it.confidence }.take(3)
        val results: List<ClassificationResult> =
            if (ranked.isNotEmpty()) {
                // Sessions-ackumulatorn ersätter om-klassificeringen av bästa fönstret —
                // en inferens mindre, och Disambig får riktiga kandidater.
                ranked.map { ClassificationResult(it.speciesId, it.confidence) }
            } else {
                val classifier =
                    classifierInstance ?: run {
                        _state.value = AudioScanState.Error.BootstrapFailed("classifier unavailable")
                        return
                    }
                val waveform = normalizer(window)
                classifier.classify(AudioInput(waveform, SAMPLE_RATE, 3_000, rawPcm = window)).results
            }

        val ts = clock()
        val pngPath =
            withContext(ioDispatcher) {
                waveformRenderer.renderWaveformPng(fullPcm, "${audioStorageDir()}/$ts.png")
            }
        coroutineContext.ensureActive()
        val audioPath =
            withContext(ioDispatcher) {
                waveformRenderer.encodeOpus(fullPcm, "${audioStorageDir()}/$ts.opus")
            }
        coroutineContext.ensureActive()

        val source =
            ScanSource.Audio(
                frameJpegPath = pngPath,
                classification = Classification(results = results, frameTimestampMillis = ts),
                audioWavPath = audioPath,
            )
        val json = Json.encodeToString(ScanSourceSerialization.serializer(), source.toSerial())
        _state.update { s ->
            if (s is AudioScanState.Analyzing) AudioScanState.NavigateToMatch(json, ts) else s
        }
    }
```

(e) `ScanSource.kt:19`: `val audioWavPath: String?` — och i `ScanSourceSerialization.kt:57` ersätt error-kastet:

```kotlin
                audioWavPath = audioWavPath,
```

(`MatchResultViewModel`:s båda läsare använder redan `(source as? ScanSource.Audio)?.audioWavPath` → typen blir `String?` utan följdändringar; `SaveObservationUseCase.save(audioPath: String?)` tar redan null från fotovägen.) Kör `rg -n "audioWavPath" composeApp/src shared` och verifiera att inga andra ställen antar non-null.

- [ ] **Step 4: Kör testerna, verifiera gröna**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.audio.AudioScanViewModelTest"`
Expected: PASS — alla, inkl. de två nya och de befintliga (t.ex. `bestSoFar_tracksHighestConfidenceAcrossWindows` asserterar ≥3 anrop: 3 streaming utan final-klassificering uppfyller det).

- [ ] **Step 5: Kör gaten + commit**

```bash
git add -A && git commit -m "feat(audio): per-art sessions-ackumulator ersätter dubbelinferensen; top-3 till Disambig

sessionScores (speciesId -> sessionsmax över alla fönster) rankas vid
finalize -> upp till 3 riktiga kandidater i ScanSource (var: alltid bara
results.first()) och ingen om-klassificering av bästa fönstret. Fallback-
klassificering av sista fönstret kvar när ackumulatorn är tom.
ScanSource.Audio.audioWavPath nullable (förberedelse för persist-degradering)."
```

---

### Task 7: Finalize-härdning — timeout, avbryt, persist-degradering, AnalyzeFailed (TDD)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanState.kt` (nytt Error-state)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanViewModel.kt` (finalize-wrap, persist-try/catch, cancelRecording-guard, logAudio)
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/ui/audio/AndroidWaveformRenderer.android.kt:102-106` (API<29-gate)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanViewModel.kt:352-356` (`WaveformRendererApi.encodeOpus` → `String?`)
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/audio/AudioScanViewModelTest.kt`

**Interfaces:**
- Consumes: task 6:s `analyzeAndNavigate`-form.
- Produces: `AudioScanState.Error.AnalyzeFailed` (data object); `WaveformRendererApi.encodeOpus(pcm, outPath): String?`; `ANALYZE_TIMEOUT_MS = 15_000L`; `cancelRecording()` som även lämnar `Analyzing`; `logAudio(msg)`. Task 12 renderar AnalyzeFailed + avbryt-knappen.

- [ ] **Step 1: Skriv failing tests**

```kotlin
    @Test
    fun analyzeTimeout_emitsAnalyzeFailed_notHang() =
        runTest {
            val slowClassifier =
                object : BirdAudioClassifier {
                    override val info =
                        AudioModelInfo("slow", listOf(1, 144_000), listOf(1, 1), 100.0)

                    override suspend fun classify(input: AudioInput): AudioClassification {
                        kotlinx.coroutines.delay(60_000) // långt över ANALYZE_TIMEOUT_MS
                        return AudioClassification(emptyList(), 5L, "slow")
                    }

                    override fun close() {}
                }
            val recorder = FakeStreamingRecorder()
            val vm =
                AudioScanViewModel(
                    classifierProvider = { Pair(slowClassifier, AudioClassifierMode.REAL) },
                    recorder = recorder,
                    waveformRenderer = FakeWaveformRenderer(),
                    audioStorageDir = { "/tmp/audio" },
                    clock = { 0L },
                    normalizer = stubNormalizer,
                    ioDispatcher = Dispatchers.Unconfined,
                    inferenceDispatcher = Dispatchers.Unconfined,
                )
            vm.onPermissionState(PermissionState.Granted)
            vm.startRecording()
            recorder.emitChunks(120)
            // OBS: streaming-inferensen hänger också på slowClassifier — men den är
            // single-flight (inflight-guard) och blockerar inte stopRecording-vägen.
            vm.stopRecording()
            advanceUntilIdle()

            assertEquals(AudioScanState.Error.AnalyzeFailed, vm.state.value)
        }

    @Test
    fun encodeFailure_stillNavigatesWithNullAudioPath() =
        runTest {
            val failingRenderer =
                object : WaveformRendererApi {
                    override suspend fun renderWaveformPng(pcm: ShortArray, outPath: String) = outPath

                    override suspend fun encodeOpus(pcm: ShortArray, outPath: String): String? =
                        throw RuntimeException("disk full")
                }
            val classifier = ScriptedClassifier(confidencesPerCall = listOf(0.45f))
            val recorder = FakeStreamingRecorder()
            val vm =
                AudioScanViewModel(
                    classifierProvider = { Pair(classifier, AudioClassifierMode.REAL) },
                    recorder = recorder,
                    waveformRenderer = failingRenderer,
                    audioStorageDir = { "/tmp/audio" },
                    clock = { 0L },
                    normalizer = stubNormalizer,
                    ioDispatcher = Dispatchers.Unconfined,
                    inferenceDispatcher = Dispatchers.Unconfined,
                )
            vm.onPermissionState(PermissionState.Granted)
            vm.startRecording()
            recorder.emitChunks(120)
            advanceUntilIdle()
            vm.stopRecording()
            advanceUntilIdle()

            val state = vm.state.value
            assertTrue(state is AudioScanState.NavigateToMatch, "persist-fel får inte blockera ID:t, got $state")
            val source = Json.decodeFromString<ScanSourceSerialization>(state.sourceJson)
            assertEquals(null, source.audioWavPath)
        }

    @Test
    fun cancelFromAnalyzing_returnsToIdle_andStaleTimeoutDoesNotClobber() =
        runTest {
            // Renderer som hänger för evigt -> finalize fastnar i Analyzing på PNG-steget.
            val hangingRenderer =
                object : WaveformRendererApi {
                    override suspend fun renderWaveformPng(pcm: ShortArray, outPath: String): String {
                        kotlinx.coroutines.awaitCancellation()
                    }

                    override suspend fun encodeOpus(pcm: ShortArray, outPath: String): String? = outPath
                }
            val recorder = FakeStreamingRecorder()
            val vm =
                AudioScanViewModel(
                    classifierProvider = { Pair(ScriptedClassifier(listOf(0.45f)), AudioClassifierMode.REAL) },
                    recorder = recorder,
                    waveformRenderer = hangingRenderer,
                    audioStorageDir = { "/tmp/audio" },
                    clock = { 0L },
                    normalizer = stubNormalizer,
                    ioDispatcher = Dispatchers.Unconfined,
                    inferenceDispatcher = Dispatchers.Unconfined,
                )
            vm.onPermissionState(PermissionState.Granted)
            vm.startRecording()
            recorder.emitChunks(120)
            advanceUntilIdle()
            vm.stopRecording()
            // OBS: ingen advanceUntilIdle här — finalize hänger i renderern -> Analyzing.
            assertTrue(vm.state.value is AudioScanState.Analyzing, "got ${vm.state.value}")

            vm.cancelRecording()
            assertEquals(AudioScanState.Idle, vm.state.value)

            // Låt virtuell tid passera 15s-timeouten: det cancellade finalize-jobbet
            // får INTE klobba Idle med Error.AnalyzeFailed i efterhand.
            advanceUntilIdle()
            assertEquals(AudioScanState.Idle, vm.state.value)
        }
```

- [ ] **Step 2: Kör, verifiera FAIL**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.audio.AudioScanViewModelTest"`
Expected: FAIL — `Error.AnalyzeFailed` finns inte (kompileringsfel), `encodeOpus`-signaturen matchar inte.

- [ ] **Step 3: Implementera**

(a) `AudioScanState.kt`, i `sealed interface Error`:

```kotlin
        data object AnalyzeFailed : Error
```

(b) `WaveformRendererApi` (AudioScanViewModel.kt:352-356): `encodeOpus` returnerar `String?` med KDoc: "Returnerar null när encoding inte stöds på plattformen (Android API < 29 saknar Opus-encoder + OGG-muxer). Fel kastas; anroparen degraderar." Uppdatera `FakeWaveformRenderer` i testfilen (returtyp `String?`).

(c) `AudioScanViewModel.kt` — companion får `const val ANALYZE_TIMEOUT_MS = 15_000L`; ny hjälpare + finalize-job-spårning + timeout-wrap:

```kotlin
    private fun logAudio(msg: String) = println("Birdy/audio: $msg")
```

Nytt fält bredvid `sessionJob`/`inferenceJob`:

```kotlin
    private var finalizeJob: Job? = null
```

ALLA TRE finalize-lanseringssajterna (auto-stopp från task 5, `onCapReached` rad 126, `stopRecording` rad 208) ändras från `viewModelScope.launch { finalizeAndNavigate(...) }` till:

```kotlin
        finalizeJob = viewModelScope.launch { finalizeAndNavigate(reason = StopReason.XXX) }
```

och `cancelRecording()` cancellar det (lägg intill `inferenceJob?.cancel()`):

```kotlin
        finalizeJob?.cancel()
        finalizeJob = null
```

— utan detta kan avbryt-knappen i Analyzing lämna ett levande finalize-jobb som senare skriver över Idle. I `finalizeAndNavigate`, ersätt det direkta `analyzeAndNavigate(...)`-anropet (task 6 step c) med:

```kotlin
        try {
            withTimeout(ANALYZE_TIMEOUT_MS) {
                analyzeAndNavigate(fullPcm = fullPcm, window = window)
            }
        } catch (t: TimeoutCancellationException) {
            logAudio("analyze timed out after ${ANALYZE_TIMEOUT_MS}ms")
            _state.update { s -> if (s is AudioScanState.Analyzing) AudioScanState.Error.AnalyzeFailed else s }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            logAudio("analyze failed: ${t.message}")
            _state.update { s -> if (s is AudioScanState.Analyzing) AudioScanState.Error.AnalyzeFailed else s }
        }
```

(imports: `kotlinx.coroutines.withTimeout`, `kotlinx.coroutines.TimeoutCancellationException`. Felövergångarna är Analyzing-guardade så ett stale jobb aldrig klobbar Idle/en ny session — testet ovan pinnar exakt det.)

I `analyzeAndNavigate`, ersätt png/opus-anropen med degraderande varianter:

```kotlin
        val ts = clock()
        val pngPath: String =
            try {
                withContext(ioDispatcher) {
                    waveformRenderer.renderWaveformPng(fullPcm, "${audioStorageDir()}/$ts.png")
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                logAudio("waveform png failed: ${t.message}")
                "" // ""-konventionen: MatchResult ifBlank{null}-ar redan frameJpegPath
            }
        coroutineContext.ensureActive()
        val audioPath: String? =
            try {
                withContext(ioDispatcher) {
                    waveformRenderer.encodeOpus(fullPcm, "${audioStorageDir()}/$ts.opus")
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                logAudio("opus encode failed: ${t.message}")
                null
            }
        coroutineContext.ensureActive()
```

(d) `cancelRecording()`-guarden (rad 301-303) utökas med Analyzing:

```kotlin
        if (_state.value is AudioScanState.Recording ||
            _state.value is AudioScanState.Error ||
            _state.value is AudioScanState.Analyzing
        ) {
            _state.value = AudioScanState.Idle
        }
```

(e) `AndroidWaveformRenderer.android.kt` — API-gate överst i `encodeOpus` (returtyp → `String?`; import `android.os.Build`):

```kotlin
    override suspend fun encodeOpus(
        pcm: ShortArray,
        outPath: String,
    ): String? =
        withContext(Dispatchers.Default) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                // MIMETYPE_AUDIO_OPUS-encodern + MUXER_OUTPUT_OGG kräver API 29;
                // minSdk är 24. Android 7-9 får journalpost utan uppspelning.
                return@withContext null
            }
            ...befintlig kropp oförändrad...
        }
```

- [ ] **Step 4: Kör testerna, verifiera gröna**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.audio.AudioScanViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Kör gaten + commit**

```bash
git add -A && git commit -m "fix(audio): finalize-härdning — 15s-timeout, AnalyzeFailed-state, persist-degradering, API<29-Opus-gate

Analyzing kan inte längre hänga (withTimeout) eller krascha appen (try/catch
-> Error.AnalyzeFailed); PNG/Opus-fel blockerar aldrig ID-resultatet (\"\"/null-
degradering); Opus hoppar över på API<29 (encodern kräver 29, minSdk är 24 —
kraschade/frös på Android 7-9); cancelRecording tar sig ur Analyzing."
```

---

### Task 8: Audio-egna Match-trösklar + routing per källa (TDD)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchThresholds.kt` (objekt → data class med PHOTO/AUDIO)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchResultViewModel.kt:137, 156, 168`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/match/MatchThresholdsTest.kt`

**Interfaces:**
- Consumes: `ScanSource` (för källval).
- Produces: `MatchThresholds(matchConfidence, disambigConfidence, noBirdHintFloor)` med `routeFor(topConfidence): MatchRoute`, `MatchThresholds.PHOTO` (0.50/0.35/0.15), `MatchThresholds.AUDIO` (0.40/0.20/0.10), `MatchThresholds.forSource(source)`.

- [ ] **Step 1: Skriv om testfilen till failing**

Ersätt hela `MatchThresholdsTest.kt`:

```kotlin
package se.birdy.app.ui.match

import se.birdy.ml.Classification
import se.birdy.ml.ScanSource
import kotlin.test.Test
import kotlin.test.assertEquals

class MatchThresholdsTest {
    @Test
    fun photo_thresholds_are_0_50_0_35_0_15() {
        assertEquals(0.50f, MatchThresholds.PHOTO.matchConfidence)
        assertEquals(0.35f, MatchThresholds.PHOTO.disambigConfidence)
        assertEquals(0.15f, MatchThresholds.PHOTO.noBirdHintFloor)
    }

    @Test
    fun audio_thresholds_are_0_40_0_20_0_10() {
        assertEquals(0.40f, MatchThresholds.AUDIO.matchConfidence)
        assertEquals(0.20f, MatchThresholds.AUDIO.disambigConfidence)
        assertEquals(0.10f, MatchThresholds.AUDIO.noBirdHintFloor)
    }

    @Test
    fun forSource_picksAudioForAudioSource() {
        val source =
            ScanSource.Audio(
                frameJpegPath = "",
                classification = Classification(emptyList(), 0L),
                audioWavPath = null,
            )
        assertEquals(MatchThresholds.AUDIO, MatchThresholds.forSource(source))
    }

    @Test
    fun forSource_picksPhotoForImageSource() {
        val source =
            ScanSource.Image(frameJpegPath = "", classification = Classification(emptyList(), 0L))
        assertEquals(MatchThresholds.PHOTO, MatchThresholds.forSource(source))
    }

    @Test
    fun photo_routeFor_at_exactly_match_threshold_routes_to_match() {
        assertEquals(MatchRoute.MATCH, MatchThresholds.PHOTO.routeFor(0.50f))
    }

    @Test
    fun photo_routeFor_just_below_match_threshold_routes_to_disambig() {
        assertEquals(MatchRoute.DISAMBIG, MatchThresholds.PHOTO.routeFor(0.4999f))
    }

    @Test
    fun photo_routeFor_just_below_disambig_threshold_routes_to_nobird() {
        assertEquals(MatchRoute.NOBIRD, MatchThresholds.PHOTO.routeFor(0.3499f))
    }

    @Test
    fun audio_routeFor_between_thresholds_routes_to_disambig() {
        assertEquals(MatchRoute.DISAMBIG, MatchThresholds.AUDIO.routeFor(0.30f))
        assertEquals(MatchRoute.MATCH, MatchThresholds.AUDIO.routeFor(0.40f))
        assertEquals(MatchRoute.NOBIRD, MatchThresholds.AUDIO.routeFor(0.19f))
    }
}
```

- [ ] **Step 2: Kör, verifiera FAIL** (kompileringsfel — objektet har konstanter, inte instanser)

- [ ] **Step 3: Implementera**

`MatchThresholds.kt`:

```kotlin
package se.birdy.app.ui.match

import se.birdy.ml.ScanSource

/** Vilken UiState top-1-confidence ska route:a till. */
internal enum class MatchRoute { MATCH, DISAMBIG, NOBIRD }

/**
 * Källspecifika confidence-trösklar. PHOTO är de ursprungliga (Plan 7d).
 * AUDIO är sänkta INTERIMISTISKT (spec 2026-08-06): fotovärdena var aldrig
 * kalibrerade för ljud, och fält-inspelningar landar systematiskt lägre —
 * BirdNET:s eget default-golv är 0.1. Ersätts med evidens när xeno-canto-
 * evalen körs (follow-up).
 */
internal data class MatchThresholds(
    val matchConfidence: Float,
    val disambigConfidence: Float,
    val noBirdHintFloor: Float,
) {
    fun routeFor(topConfidence: Float): MatchRoute =
        when {
            topConfidence >= matchConfidence -> MatchRoute.MATCH
            topConfidence >= disambigConfidence -> MatchRoute.DISAMBIG
            else -> MatchRoute.NOBIRD
        }

    companion object {
        val PHOTO = MatchThresholds(matchConfidence = 0.50f, disambigConfidence = 0.35f, noBirdHintFloor = 0.15f)
        val AUDIO = MatchThresholds(matchConfidence = 0.40f, disambigConfidence = 0.20f, noBirdHintFloor = 0.10f)

        fun forSource(source: ScanSource): MatchThresholds =
            if (source is ScanSource.Audio) AUDIO else PHOTO
    }
}
```

`MatchResultViewModel.kt` — fält + tre call-sites:

```kotlin
    private val thresholds = MatchThresholds.forSource(source)
```

Rad 137: `when (thresholds.routeFor(top1.confidence)) {`
Rad 156: `.filter { it.confidence >= thresholds.disambigConfidence }`
Rad 168: `topPrediction = top1.takeIf { it.confidence >= thresholds.noBirdHintFloor },`

Kör `rg -n "MatchThresholds\.(MATCH_CONFIDENCE|DISAMBIG_CONFIDENCE|routeFor)" composeApp/src` — fixa alla kvarvarande referenser (t.ex. `MatchResultViewModelTest` i androidUnitTest om den refererar konstanterna).

- [ ] **Step 4: Kör alla tester + gaten** — Expected: PASS/BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(audio): audio-egna Match-trösklar 0.40/0.20/0.10 (interim) + routing per källa

Fotovägens 0.50/0.35/0.15 var aldrig kalibrerade för ljud (evalen blockerad
på xeno-canto-nyckel). MatchThresholds är nu data class med PHOTO/AUDIO-
instanser; MatchResultViewModel väljer per ScanSource."
```

---

### Task 9: BirdNET-postprocess — filtrera FÖRE ranking + output-guard (TDD)

**Files:**
- Create: `shared/ml/src/commonMain/kotlin/se/birdy/ml/BirdNetPostprocess.kt`
- Create: `shared/ml/src/commonTest/kotlin/se/birdy/ml/BirdNetPostprocessTest.kt`
- Modify: `shared/ml/src/commonMain/kotlin/se/birdy/ml/BirdNetLabelMapper.kt` (exponera totalBirdnetClasses)
- Modify: `shared/ml/src/androidMain/kotlin/se/birdy/ml/AndroidTfliteAudioRunner.kt:84-91` (använd hjälparen), `:126-134` (guard)

**Interfaces:**
- Consumes: `ClassificationResult`, `BirdNetLabelMapper.lookup`.
- Produces: `fun rankMappedScores(scores: FloatArray, lookup: (Int) -> String?, take: Int = 3): List<ClassificationResult>` (commonMain — iOS i3 återanvänder); `BirdNetLabelMapper.totalBirdnetClasses: Int`.

- [ ] **Step 1: Skriv failing test**

`shared/ml/src/commonTest/kotlin/se/birdy/ml/BirdNetPostprocessTest.kt`:

```kotlin
package se.birdy.ml

import kotlin.test.Test
import kotlin.test.assertEquals

class BirdNetPostprocessTest {
    @Test
    fun unmappedTopClassesDoNotCrowdOutMappedSpecies() {
        // Index 0-2 (omappade brusklasser) har högst score; den mappade arten
        // på råplats 4 ska ändå vinna. Detta var den shippade buggen:
        // take(3)-före-filter gav tom lista här.
        val scores = floatArrayOf(0.9f, 0.8f, 0.7f, 0.6f, 0.3f, 0.2f)
        val mapping = mapOf(3 to "QA", 4 to "QB", 5 to "QC")
        val result = rankMappedScores(scores, lookup = { mapping[it] })
        assertEquals(listOf("QA", "QB", "QC"), result.map { it.speciesId })
        assertEquals(0.6f, result[0].confidence)
    }

    @Test
    fun takesAtMostThreeByDefault() {
        val scores = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f)
        val result = rankMappedScores(scores, lookup = { "Q$it" })
        assertEquals(3, result.size)
        assertEquals(listOf("Q4", "Q3", "Q2"), result.map { it.speciesId })
    }

    @Test
    fun allUnmappedGivesEmptyList() {
        val result = rankMappedScores(floatArrayOf(0.9f, 0.8f), lookup = { null })
        assertEquals(emptyList(), result)
    }
}
```

- [ ] **Step 2: Kör, verifiera FAIL**

Run: `./gradlew :shared:ml:jvmTest --tests "se.birdy.ml.BirdNetPostprocessTest"`
Expected: FAIL — `rankMappedScores` finns inte.

- [ ] **Step 3: Implementera**

`BirdNetPostprocess.kt`:

```kotlin
package se.birdy.ml

/**
 * Rankar BirdNET-scores över ENDAST mappade (EU-)klasser och tar sedan topp [take].
 *
 * Filter-före-ranking är bärande: BirdNET 6K Globals råa topplaceringar domineras
 * ofta av brus/människa/icke-EU-pseudoklasser (5 735 av 6 362 index är omappade).
 * Att ta top-3 först och mappa efteråt kastade bort korrekt EU-art på råplats 4+
 * och renderade det som "ingen fågel hörd" (shippad bug t.o.m. vC126).
 *
 * commonMain så att iOS-runnern (i3) återanvänder exakt samma postprocess.
 */
fun rankMappedScores(
    scores: FloatArray,
    lookup: (Int) -> String?,
    take: Int = 3,
): List<ClassificationResult> =
    scores
        .mapIndexed { idx, score -> idx to score }
        .mapNotNull { (idx, score) -> lookup(idx)?.let { qid -> ClassificationResult(qid, score) } }
        .sortedByDescending { it.confidence }
        .take(take)
```

`BirdNetLabelMapper.kt` — ny publik ctor-param + parse-wiring:

```kotlin
class BirdNetLabelMapper internal constructor(
    private val indexToQid: Map<Int, String>,
    val modelVersion: String,
    val coveragePct: Double,
    val totalBirdnetClasses: Int,
) {
```

och i `parse(...)`:s retur: `totalBirdnetClasses = dto.meta.totalBirdnetClasses,`

`AndroidTfliteAudioRunner.kt:84-91` — ersätt top3-blocket:

```kotlin
            val top3 = rankMappedScores(scores, mapper::lookup)
```

och i `load(...)` efter `outputShape`-läsningen (rad ~126):

```kotlin
                check(outputShape.last() == mapper.totalBirdnetClasses) {
                    "Model emits ${outputShape.last()} classes but birdnet_lite_to_qid.json " +
                        "maps ${mapper.totalBirdnetClasses} — model/mapping mismatch would mis-index species."
                }
```

OBS: `mapper` laddas på rad 134 EFTER shape-checkarna — flytta `val mapper = loadBirdNetLabelMapper()` upp före den nya checken. Kör `rg -n "BirdNetLabelMapper(" shared` och fixa ev. testkonstruktioner av mappern (ny ctor-param; internal ctor → tester i samma modul kan skicka `totalBirdnetClasses = <antal>`).

- [ ] **Step 4: Kör alla ml-tester + K/N**

Run: `./gradlew :shared:ml:jvmTest :shared:ml:iosSimulatorArm64Test`
Expected: PASS (commonTest kör på båda targets — testnamnen ovan är ObjC-säkra).

- [ ] **Step 5: Kör gaten + commit**

```bash
git add -A && git commit -m "fix(audio): filtrera till mappade EU-klasser FÖRE top-3-ranking + model/mapping-guard

Rå top-3 domineras i fält av brus/icke-EU-klasser (5735/6362 omappade) ->
korrekt art på råplats 4 kastades och visades som NoBird. rankMappedScores
i commonMain (iOS i3 återanvänder); load() verifierar outputShape mot
mapperns totalBirdnetClasses."
```

---

### Task 10: Recorder-felsignal + mic-stopp vid Back (TDD)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanViewModel.kt:309-327` (`AudioRecorderApi.start` + VM-wiring)
- Modify: `shared/ml/src/androidMain/kotlin/se/birdy/ml/AndroidAudioRecorder.kt`
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/ui/audio/AndroidAudioRecorderAdapter.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/ui/audio/AudioScanScreenHost.android.kt` (DisposableEffect)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanScreenHost.kt` (KDoc-kontrakt)
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/audio/FakeStreamingRecorder.kt` + `AudioScanViewModelTest.kt`

**Interfaces:**
- Consumes: task 7:s `logAudio`.
- Produces: `AudioRecorderApi.start(onChunk, onCapReached, onError: (Throwable) -> Unit = {}, maxDurationMs)` — iOS-recordern (i3) MÅSTE implementera samma kontrakt; `FakeStreamingRecorder.emitError(t)`.

- [ ] **Step 1: Skriv failing test**

I `FakeStreamingRecorder.kt`: lägg fält + metod och uppdatera `start`-signaturen:

```kotlin
    private var onError: ((Throwable) -> Unit)? = null

    override fun start(
        onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
        onCapReached: () -> Unit,
        onError: (Throwable) -> Unit,
        maxDurationMs: Long,
    ): RecorderHandle {
        this.onChunk = onChunk
        this.onCap = onCapReached
        this.onError = onError
        ...oförändrad retur...
    }

    /** Simulerar recorder-haveri mitt i sessionen (read()<=0 / startRecording-throw). */
    fun emitError(t: Throwable) {
        onError?.invoke(t)
    }
```

I `AudioScanViewModelTest.kt`:

```kotlin
    @Test
    fun recorderError_midSession_emitsRecordingFailed() =
        runTest {
            val recorder = FakeStreamingRecorder()
            val (vm, _) = makeVm(recorder = recorder)
            vm.onPermissionState(PermissionState.Granted)
            vm.startRecording()
            recorder.emitChunks(30)
            recorder.emitError(IllegalStateException("AudioRecord.read returned 0"))
            advanceUntilIdle()

            assertEquals(AudioScanState.Error.RecordingFailed, vm.state.value)
        }
```

- [ ] **Step 2: Kör, verifiera FAIL** (kompileringsfel — `onError` finns inte i interfacet)

- [ ] **Step 3: Implementera**

(a) `AudioRecorderApi` (AudioScanViewModel.kt:322-326):

```kotlin
    fun start(
        onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
        onCapReached: () -> Unit,
        onError: (Throwable) -> Unit = {},
        maxDurationMs: Long = 60_000L,
    ): RecorderHandle
```

KDoc-tillägg: "[onError] fyras högst en gång per session när capture havererar mitt i (read <= 0 = mic stulen/privacy-toggle/backgrounding, eller throw i capture-loopen). Fyras INTE vid normal stop/cancel. Fyras på recorderns IO-tråd."

(b) VM `startRecording()` (rad 120-129) + ny privat metod:

```kotlin
                    handle =
                        recorder.start(
                            onChunk = { samples, rms, totalSoFar ->
                                onChunkReceived(samples, rms, totalSoFar)
                            },
                            onCapReached = {
                                viewModelScope.launch { finalizeAndNavigate(reason = StopReason.CAP) }
                            },
                            onError = { t -> onRecorderError(t) },
                            maxDurationMs = MAX_RECORD_MS,
                        )
```

```kotlin
    /** Recorder-haveri mitt i sessionen: städa och visa fel istället för tyst frusen timer. */
    private fun onRecorderError(cause: Throwable) {
        logAudio("recorder failed mid-session: ${cause.message}")
        viewModelScope.launch {
            inferenceJob?.cancel()
            handle?.cancel()
            handle = null
            if (_state.value is AudioScanState.Recording) {
                _state.value = AudioScanState.Error.RecordingFailed
            }
        }
    }
```

(c) `AndroidAudioRecorder.kt` — signatur + felvägar + buffert-headroom:

```kotlin
    fun start(
        onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
        onCapReached: () -> Unit,
        onError: (Throwable) -> Unit = {},
        maxDurationMs: Long = 60_000L,
    ): AndroidRecorderHandle {
```

Buffert (rad 46/49): `buildRecorder(MediaRecorder.AudioSource.UNPROCESSED, minBuf * 4)` resp. `VOICE_RECOGNITION, minBuf * 4` — headroom ×4 mot overrun när konsumenten är långsam.

Capture-loopen (rad 63-87):

```kotlin
        val job: Job =
            scope.launch {
                try {
                    recorder.startRecording()
                    val chunkSize = sampleRate / 30 // ~33ms
                    val chunkBuf = ShortArray(chunkSize)
                    while (total < maxSamples && !stopRequested.isCompleted && !cancelRequested.isCompleted) {
                        val toRead = minOf(chunkSize, maxSamples - total)
                        val read = recorder.read(chunkBuf, 0, toRead)
                        if (read <= 0) {
                            // Mic stulen (samtal/privacy-toggle) eller backgrounding: Android 11+
                            // ger TYSTNAD eller 0-reads till bakgrundsappar — utan signal här
                            // frös UI:t med död timer och permanent låst stopp-knapp.
                            if (!stopRequested.isCompleted && !cancelRequested.isCompleted) {
                                onError(IllegalStateException("AudioRecord.read returned $read"))
                            }
                            break
                        }
                        chunkBuf.copyInto(captured, destinationOffset = total, startIndex = 0, endIndex = read)
                        val rms = computeRms(chunkBuf, 0, read)
                        total += read
                        if (!cancelRequested.isCompleted) {
                            onChunk(chunkBuf.copyOf(read), rms, total)
                        }
                    }
                    if (total >= maxSamples && !stopRequested.isCompleted && !cancelRequested.isCompleted) {
                        onCapReached()
                    }
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    // startRecording()/read()-throw hamnade förut i SupervisorJob-scopens
                    // default-handler = app-krasch.
                    if (!stopRequested.isCompleted && !cancelRequested.isCompleted) onError(t)
                } finally {
                    runCatching { recorder.stop() }
                    recorder.release()
                }
            }
```

(d) `AndroidAudioRecorderAdapter.android.kt`:

```kotlin
    override fun start(
        onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
        onCapReached: () -> Unit,
        onError: (Throwable) -> Unit,
        maxDurationMs: Long,
    ): RecorderHandle {
        val androidHandle = recorder.start(onChunk, onCapReached, onError, maxDurationMs)
        ...oförändrad retur...
    }
```

(e) `AudioScanScreenHost.android.kt` — efter `val vm = remember(graph) { ... }` (rad 62):

```kotlin
    // Stoppa mikrofonen när skärmen lämnas (Back/navigering). VM:en skapas med
    // remember — inte ViewModelStore — så onCleared körs aldrig; utan detta höll
    // AudioRecord micken (OS-indikatorn lyste) i upp till 60s efter utnavigering.
    // Ofarligt efter NavigateToMatch: cancelRecording rör inte det statet.
    DisposableEffect(vm) {
        onDispose { vm.cancelRecording() }
    }
```

(f) `AudioScanScreenHost.kt` (expect) — KDoc-tillägg: "KONTRAKT för varje plattforms-actual (iOS i3 inkluderad): hosten MÅSTE stoppa inspelningen när skärmen lämnar kompositionen (DisposableEffect → `vm.cancelRecording()`), annars läcker mikrofonen upp till 60s."

- [ ] **Step 4: Kör testerna + gaten** — Expected: PASS/BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "fix(audio): recorder-felsignal (read<=0/throw) + mic-stopp vid Back + buffert-headroom x4

AudioRecorderApi.start får onError; död mic gav förut tyst break -> frusen
timer + permanent låst stopp-knapp bakom fejk-pulserande vågform, och
capture-throws kraschade appen via default-handlern. Hosten stoppar nu
inspelningen på dispose (VM:en är remember-baserad — onCleared körs aldrig)."
```

---

### Task 11: Ärligt felläge istället för tyst Koltrast + DEMO-surfacing + onTrimMemory-fixen

**Files:**
- Modify: `shared/ml/src/commonMain/kotlin/se/birdy/ml/AudioClassifierFactory.kt` (allowFallback)
- Test: `shared/ml/src/commonTest` — leta upp befintlig `AudioClassifierFactoryTest` (`rg -l "AudioClassifierFactory" shared/ml/src/commonTest`); lägg nya fall där
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt:184-189` (allowFallback + logg), `:485-495` (ta bort trim-blocket)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanViewModel.kt:112-118, 177-200` (mode-surfacing + streaming-catch)
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` + `values-en/strings.xml` (2 nya strängar)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanScreen.kt:98-99` (lokaliserat BootstrapFailed)

**Interfaces:**
- Consumes: `AudioClassifierMode`, `BuildConfig.DEBUG` (androidApp).
- Produces: `AudioClassifierFactory(createReal, createFallback, onDegrade, sessionFailureThreshold, allowFallback: Boolean = true)`; `AudioScanViewModel.demoMode: StateFlow<Boolean>` (task 12 renderar); strängnycklar `audio_scan_bootstrap_failed`, `audio_scan_demo_banner`.

- [ ] **Step 1: Skriv failing test (TDD på factoryn)**

I den befintliga factory-testfilen (eller ny `AudioClassifierFactoryTest.kt` i `shared/ml/src/commonTest/kotlin/se/birdy/ml/` om ingen finns):

```kotlin
    @Test
    fun allowFallbackFalse_rethrowsRealLoadFailure() =
        runTest {
            val boom = IllegalStateException("native lib missing")
            var degraded: Throwable? = null
            val factory =
                AudioClassifierFactory(
                    createReal = { throw boom },
                    createFallback = { FakeAudioClassifier() },
                    onDegrade = { degraded = it },
                    allowFallback = false,
                )
            val thrown = kotlin.runCatching { factory.create() }.exceptionOrNull()
            assertEquals(boom, thrown)
            assertEquals(boom, degraded)
        }

    @Test
    fun allowFallbackFalse_successReturnsRealWithoutGuardWrap() =
        runTest {
            val real = FakeAudioClassifier()
            val factory =
                AudioClassifierFactory(
                    createReal = { real },
                    createFallback = { FakeAudioClassifier() },
                    onDegrade = {},
                    allowFallback = false,
                )
            val (clf, mode) = factory.create()
            assertEquals(AudioClassifierMode.REAL, mode)
            // Utan guard-wrap är det exakt real-instansen som returneras:
            assertEquals(real, clf)
        }
```

- [ ] **Step 2: Kör, verifiera FAIL** — Run: `./gradlew :shared:ml:jvmTest`; Expected: kompileringsfel (`allowFallback` finns inte).

- [ ] **Step 3: Implementera**

(a) `AudioClassifierFactory.kt`:

```kotlin
class AudioClassifierFactory(
    private val createReal: suspend () -> BirdAudioClassifier,
    private val createFallback: () -> BirdAudioClassifier,
    private val onDegrade: (Throwable) -> Unit,
    private val sessionFailureThreshold: Int = 3,
    private val allowFallback: Boolean = true,
) {
    suspend fun create(): Pair<BirdAudioClassifier, AudioClassifierMode> {
        val real =
            try {
                createReal()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                onDegrade(t)
                // Produktion (allowFallback=false): propagera ärligt fel istället för
                // att tyst svara "Koltrast 92%" på allt via FakeAudioClassifier.
                if (!allowFallback) throw t
                return createFallback() to AudioClassifierMode.DEMO
            }

        if (!allowFallback) return real to AudioClassifierMode.REAL

        val fallback = ...befintlig kod oförändrad (guard-wrap-vägen)...
    }
}
```

(b) `MainActivity.kt:184-189`:

```kotlin
    private suspend fun buildAudioClassifier(): Pair<BirdAudioClassifier, AudioClassifierMode> =
        AudioClassifierFactory(
            createReal = { AndroidTfliteAudioRunner.load(applicationContext) },
            createFallback = { FakeAudioClassifier() },
            onDegrade = { t ->
                android.util.Log.w("Birdy", "Audio TFLite init failed", t)
                println("Birdy/audio: classifier degrade: ${t.message}")
            },
            allowFallback = BuildConfig.DEBUG,
        ).create()
```

(c) `MainActivity.kt:485-495`: ta bort HELA `onTrimMemory`-overriden (i2c-ägarskapsregeln: aktiviteten får inte stänga en klassificerare som en levande `AudioScanViewModel` fortfarande håller via `classifierInstance` — `check(!closed)` kraschade vid backgrounding mitt i session). Trade-off: 57 MB ligger kvar i bakgrund; LMK dödar processen vid tryck, vilket är rätt mekanism. Ta också bort `audioBootstrapCache`-KDoc-raderna om onTrimMemory (rad 128-129) och "Fix #5"-loop-kommentaren kan förenklas men LÅT retry-loopen stå kvar (harmlös, skyddar mot framtida cache-clears).

(d) `AudioScanViewModel.kt` — mode-surfacing (rad 112-118) + streaming-catch (rad 177-200):

```kotlin
    private val _demoMode = MutableStateFlow(false)

    /** True när fejk-klassificeraren är aktiv (endast debug-byggen) — UI visar banner. */
    val demoMode: StateFlow<Boolean> = _demoMode
```

```kotlin
                    val (clf, mode) =
                        runCatching { classifierProvider() }
                            .getOrElse { throwable ->
                                coroutineContext.ensureActive()
                                logAudio("classifier bootstrap failed: ${throwable.message}")
                                _state.value =
                                    AudioScanState.Error.BootstrapFailed(throwable.message ?: "bootstrap failed")
                                return@launch
                            }
                    classifierInstance = clf
                    _demoMode.value = mode == AudioClassifierMode.DEMO
```

Streaming-inferensen får catch (klassificeringsfel per fönster ska logga och släppa fönstret, inte krascha — `AudioSessionFailureGuard` rethrowade in i en ofångad coroutine):

```kotlin
        viewModelScope.launch(parent + inferenceDispatcher) {
            try {
                ...befintlig kropp...
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                logAudio("streaming classify failed: ${t.message}")
            } finally {
                inflight = false
            }
        }
```

(e) Strängar — `values/strings.xml` (intill `audio_scan_recording_failed`):

```xml
    <string name="audio_scan_bootstrap_failed">Ljud-identifieringen kunde inte starta på den här enheten.</string>
    <string name="audio_scan_demo_banner">Demoläge — riktiga modellen kunde inte laddas</string>
```

`values-en/strings.xml`:

```xml
    <string name="audio_scan_bootstrap_failed">Audio identification could not start on this device.</string>
    <string name="audio_scan_demo_banner">Demo mode — the real model could not load</string>
```

(f) `AudioScanScreen.kt:98-99` — sluta rendera rå exception-text:

```kotlin
                    is AudioScanState.Error.BootstrapFailed ->
                        ErrorRetry(
                            message = stringResource(Res.string.audio_scan_bootstrap_failed),
                            onRetry = onRetry,
                        )
```

(import: `audio_scan_bootstrap_failed`; `state.cause` loggas redan i VM:n.)

- [ ] **Step 4: Kör alla tester + gaten** — Expected: PASS/BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "fix(audio): ärligt felstate i prod istället för tyst Koltrast-fejk; DEMO surfacas; onTrimMemory-close bort

allowFallback=BuildConfig.DEBUG: prod-load-fel ger lokaliserat BootstrapFailed
+ retry (16KB-enheter/GC-buggen gav förut 'Koltrast 92%' på ALLT, för alltid,
utan spår). Moden kastas inte längre bort (demoMode-flow för banner);
streaming-classify-fel loggas per fönster istället för att krascha; trim-
stängningen bröt i2c-ägarskapsregeln (VM:ens classifierInstance -> check(!closed)-krasch)."
```

---

### Task 12: Live-UX — hör-chip, Analyzing-avbryt, stopp-nedräkning, DEMO-banner

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanState.kt` (Recording.bestSoFarName)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanViewModel.kt` (namn-cache + lookup-param)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt:460-479` (wiring)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanScreen.kt` (chip, avbryt, nedräknings-CTA, banner, AnalyzeFailed-rendering)
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/ui/audio/AudioScanScreenHost.android.kt` (nya params)
- Modify: strings.xml SV+EN (4 nya strängar)
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/audio/AudioScanViewModelTest.kt`

**Interfaces:**
- Consumes: task 6:s `sessionScores`/`bestSoFar`, task 7:s `Error.AnalyzeFailed` + `cancelRecording`-Analyzing-guard, task 11:s `demoMode`.
- Produces: `AudioScanViewModel(speciesNameLookup: suspend (String) -> String? = { null }, ...)`; `Recording.bestSoFarName: String?`; `AudioScanScreen(..., demoMode: Boolean, onCancelAnalyzing: () -> Unit, ...)`; strängnycklar `audio_scan_hearing_chip`, `audio_scan_analyze_failed`, `audio_scan_cancel`, `audio_scan_cta_recording_locked`.

- [ ] **Step 1: Skriv failing test**

```kotlin
    @Test
    fun bestSoFarName_resolvedViaLookupAndExposedInRecordingState() =
        runTest {
            val classifier = ScriptedClassifier(confidencesPerCall = listOf(0.45f))
            val recorder = FakeStreamingRecorder()
            val vm =
                AudioScanViewModel(
                    classifierProvider = { Pair(classifier, AudioClassifierMode.REAL) },
                    recorder = recorder,
                    waveformRenderer = FakeWaveformRenderer(),
                    audioStorageDir = { "/tmp/audio" },
                    clock = { 0L },
                    normalizer = stubNormalizer,
                    ioDispatcher = Dispatchers.Unconfined,
                    inferenceDispatcher = Dispatchers.Unconfined,
                    speciesNameLookup = { qid -> if (qid == "Q25334") "Koltrast" else null },
                )
            vm.onPermissionState(PermissionState.Granted)
            vm.startRecording()
            recorder.emitChunks(120)
            advanceUntilIdle()

            val s = vm.state.value
            assertTrue(s is AudioScanState.Recording, "got $s")
            assertEquals("Q25334", s.bestSoFar?.speciesId)
            assertEquals("Koltrast", s.bestSoFarName)
        }
```

- [ ] **Step 2: Kör, verifiera FAIL** (kompileringsfel — parametern/fältet finns inte)

- [ ] **Step 3: Implementera**

(a) `AudioScanState.kt`:

```kotlin
    data class Recording(
        val rms: Float,
        val elapsedMs: Long,
        val bestSoFar: Top1? = null,
        val bestSoFarName: String? = null,
    ) : AudioScanState
```

(b) VM — ctor-param + namn-cache; i streaming-uppdateringen (task 6 step b) ersätt state-uppdateringen:

```kotlin
    private val speciesNameLookup: suspend (String) -> String? = { null },
```

```kotlin
    private val nameCache = mutableMapOf<String, String?>()

    private suspend fun resolveName(speciesId: String): String? =
        if (nameCache.containsKey(speciesId)) {
            nameCache[speciesId]
        } else {
            val name =
                try {
                    speciesNameLookup(speciesId)
                } catch (t: CancellationException) {
                    throw t
                } catch (t: Throwable) {
                    null
                }
            nameCache[speciesId] = name
            name
        }
```

```kotlin
                if (result.results.isNotEmpty()) {
                    val best = sessionScores.values.maxByOrNull { it.confidence }
                    val bestName = best?.let { resolveName(it.speciesId) }
                    _state.update { s ->
                        if (s is AudioScanState.Recording) {
                            s.copy(bestSoFar = best, bestSoFarName = bestName)
                        } else {
                            s
                        }
                    }
                }
```

`startRecording()`-nollställningen får `nameCache.clear()`. OBS: `onChunkReceived`:s Recording-rekonstruktion (rad 151-157) bygger `AudioScanState.Recording(rms, elapsed, bestSoFar)` — uppdatera till att bevara namnet: `s.copy(rms = rms, elapsedMs = elapsed)`.

(c) `AppGraph.audioScanViewModel()` (rad 473-478):

```kotlin
        return AudioScanViewModel(
            classifierProvider = provider,
            recorder = recorder,
            waveformRenderer = renderer,
            audioStorageDir = dir,
            speciesNameLookup = { qid ->
                repository.getById(SpeciesId(qid), defaultLocale).first()?.name
            },
        )
```

(imports i AppGraph: `SpeciesId` + `first` finns redan — verifiera med `rg -n "SpeciesId|flow.first" composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`.)

(d) Strängar — SV:

```xml
    <string name="audio_scan_hearing_chip">Hör: %1$s · %2$s</string>
    <string name="audio_scan_analyze_failed">Analysen misslyckades. Försök igen.</string>
    <string name="audio_scan_cancel">Avbryt</string>
    <string name="audio_scan_cta_recording_locked">Lyssnar — stopp möjligt om %1$s s</string>
```

EN:

```xml
    <string name="audio_scan_hearing_chip">Hearing: %1$s · %2$s</string>
    <string name="audio_scan_analyze_failed">Analysis failed. Please try again.</string>
    <string name="audio_scan_cancel">Cancel</string>
    <string name="audio_scan_cta_recording_locked">Listening — stop available in %1$s s</string>
```

(e) `AudioScanScreen.kt` — nya params + rendering:

```kotlin
fun AudioScanScreen(
    state: AudioScanState,
    permissionState: PermissionState,
    demoMode: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelAnalyzing: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
```

I when-blocket: `is AudioScanState.Error.AnalyzeFailed -> ErrorRetry(message = stringResource(Res.string.audio_scan_analyze_failed), onRetry = onRetry)` och `is AudioScanState.Analyzing -> AnalyzingView(state = state, onCancel = onCancelAnalyzing)`. Före when-blocket (ovanför alla states):

```kotlin
                if (demoMode) {
                    Text(
                        text = stringResource(Res.string.audio_scan_demo_banner),
                        color = AccentCopper,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                }
```

`RecordingView` — chip + nedräknings-CTA (procenten pre-formatteras i Kotlin — trap-katalogen: inga %%-escapes):

```kotlin
        state.bestSoFar?.let { best ->
            Spacer(Modifier.height(8.dp))
            Text(
                text =
                    stringResource(
                        Res.string.audio_scan_hearing_chip,
                        state.bestSoFarName ?: best.speciesId,
                        "${(best.confidence * 100).toInt()}%",
                    ),
                color = MarginaliaInk,
                fontFamily = rememberCaveat(),
                fontSize = 15.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        val ctaText =
            if (micState == MicButtonState.RecordingDisabled) {
                val secondsLeft =
                    (((AudioScanViewModel.MIN_RECORD_MS - state.elapsedMs).coerceAtLeast(0L) / 1000L) + 1)
                        .toString()
                stringResource(Res.string.audio_scan_cta_recording_locked, secondsLeft)
            } else {
                stringResource(Res.string.audio_scan_cta_recording)
            }
        Text(text = ctaText, color = AccentCopper, fontFamily = rememberCaveat(), fontSize = 16.sp)
```

(den gamla statiska CTA-texten tas bort). `AnalyzingView` — avbryt-knapp:

```kotlin
@Composable
private fun AnalyzingView(
    state: AudioScanState.Analyzing,
    onCancel: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ...befintligt innehåll...
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onCancel) {
            Text(text = stringResource(Res.string.audio_scan_cancel), color = MarginaliaInk)
        }
    }
}
```

(import `androidx.compose.material3.TextButton` + de fyra nya strängarna + `audio_scan_demo_banner`.)

(f) `AudioScanScreenHost.android.kt`:

```kotlin
    val demoMode by vm.demoMode.collectAsState()
    ...
    AudioScanScreen(
        state = state,
        permissionState = permissionState,
        demoMode = demoMode,
        onStartRecording = vm::startRecording,
        onStopRecording = vm::stopRecording,
        onCancelAnalyzing = vm::cancelRecording,
        onRequestPermission = permissionController::request,
        onOpenSettings = permissionController::openSettings,
        onRetry = vm::cancelRecording,
        onBack = onBack,
    )
```

- [ ] **Step 4: Kör testerna + gaten** — Expected: PASS/BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(audio): live-hör-chip under inspelning + Analyzing-avbryt + stopp-nedräkning + DEMO-banner

bestSoFar fanns i Recording-statet men renderades aldrig — nu chip med
lokaliserat artnamn (speciesNameLookup via AppGraph) + pre-formatterad
procent; 3s-stopplåset visar nedräkning istället för död knapp; Analyzing
har avbryt; AnalyzeFailed renderas lokaliserat."
```

---

### Task 13: Version-bump + CLAUDE.md + fulla gates

**Files:**
- Modify: `androidApp/build.gradle.kts:80-81`
- Modify: `CLAUDE.md` (status, Tekniska val, trap-katalog)

- [ ] **Step 1: Bumpa versionen**

```kotlin
        versionCode = 127
        versionName = "1.2.2"
```

- [ ] **Step 2: Uppdatera CLAUDE.md**

(a) **Tekniska val**-raden om audio: ersätt `"Audio: 48kHz mono PCM_16 via UNPROCESSED → VOICE_RECOGNITION graceful fallback, 3s rec → OGG/Opus"` med `"Audio: 48kHz mono PCM_16 via UNPROCESSED → VOICE_RECOGNITION graceful fallback; öppen inspelning (60s tak) med rullande 3s-fönster/1s stride, auto-stopp ≥0.65, per-art sessions-ackumulator → top-3; OGG/Opus-encode endast API 29+ (annars ingen uppspelningsfil)"`.

(b) Ny **Status**-rad överst (datera, sammanfatta batchen: spår A+B, vC127, verify-läge — kod klar, emulator-verify av språk-≤32 + Galaxy-verify av ljud kvar; vC126-upload är Albins steg 0).

(c) Två nya **trap-katalog**-poster:
- "**Suspend-`finalize`/`cleanup` får ALDRIG anropas inline från en coroutine den själv cancellar**" — L1-mönstret: auto-stoppens finalize kördes som barn till `inferenceJob` och cancellade sin egen förälder → CE svaldes av `runCatching` → permanent `Analyzing`. Regel: terminala övergångar lanseras på ägar-scopen (`viewModelScope.launch { ... }`), och `runCatching` runt suspend-anrop måste rethrowa `CancellationException`.
- "**Tysta fejk-fallbacks i produktion är recensions-gift**" — `AudioClassifierFactory` svarade "Koltrast 92%" på allt när modellen inte kunde laddas (16 KB-enheter/GC-buggen), utan banner/logg. Regel: fejk/DEMO-fallbacks gate:as på `BuildConfig.DEBUG`; produktion visar ett lokaliserat felstate + retry, och degrade-orsaken loggas.

- [ ] **Step 3: Kör FULLA gaten (Android + K/N)**

Run: `export JAVA_HOME="$HOME/.local/java21/Contents/Home"; ./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt :shared:content:iosSimulatorArm64Test :shared:domain:iosSimulatorArm64Test :shared:data:iosSimulatorArm64Test :shared:ml:iosSimulatorArm64Test :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosSimulatorArm64`
Expected: BUILD SUCCESSFUL rakt igenom.

- [ ] **Step 4: Commit + push**

```bash
git add -A && git commit -m "chore: vC127 / 1.2.2 — recensions-batchen (språk + ljud) kodklar; CLAUDE.md-synk

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
git push
```

---

## Efter plan-exekvering (manuellt, utanför tasks)

1. **Emulator-verify (Mac, API ~30-AVD):** Settings-språkbyte SV↔EN↔System slår igenom direkt (UI + artnamn, ingen halvöversättning), onboarding-scen 0 (förval, val→recreate→tillbaka på scen 0 i valt språk, replay), splash/edge-to-edge ser rätt ut efter tema-swappen.
2. **Galaxy-verify (nästa Windows-tillfälle, samkörs med köade i2a/i2c-checkar):** auto-stopp på tydlig inspelning → navigerar (INTE evig "Analyzing"), Back mitt i inspelning → mic-indikatorn släcks direkt, live-chippet uppdaterar, Disambig visar upp till 3 kandidater, AnalyzeFailed/RecordingFailed-states, språkbyte på API 35, scan-återinträde + crop-re-verify.
3. **Albin:** vC126-upload FÖRE release av vC127; xeno-canto-nyckel för tröskelkalibrering (follow-up).
