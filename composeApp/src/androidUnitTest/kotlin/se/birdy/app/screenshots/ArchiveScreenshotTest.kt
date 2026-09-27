package se.birdy.app.screenshots

import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.app.ui.encyclopedia.ArchiveScreen
import se.birdy.app.ui.encyclopedia.ArchiveViewModel
import se.birdy.app.usecase.JournalExportResult
import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.model.SpeciesSummary
import se.birdy.datastore.ArchiveSort
import se.birdy.domain.observation.Observation
import java.util.concurrent.TimeUnit

/**
 * Uppslagsverket (spec 2026-09-24 §4.5): sökfält, ekologiska grupp-piller, ett präglat MiniStamp
 * per fångad art och lugna foto-rader med hårlinje. Sex arter i två familjer (Mesfåglar/Paridae,
 * Trastar/Turdidae) — Talgoxe är fångad (stämplad) för att öva [se.birdy.app.ui.components.MiniStamp]
 * i radens 26dp-storlek. Coil laddar inga bilder under Robolectric, så tumnaglarna renderar sin
 * `SandCreme`-platshållarfyllning — förväntat (samma gotcha som [LifelistScreenshotTest]).
 *
 * [ArchiveViewModel.uiState] går via en 250ms `debounce()` på sökfrågan innan combine-kedjan ger
 * sitt första `Loaded`-värde. Robolectric kör med PAUSAD huvud-Looper, så `Dispatchers.Main`s
 * `delay()` löser sig aldrig av sig själv inom testets synkrona `setContent`+capture — [advanceMainLooper]
 * knuffar fram Loopern förbi debouncen (ges till [captureScreen] som `settle`, en hook byggd för
 * just detta i denna task).
 *
 * T10b minor 14: varje test asserterar att "Talgoxe" faktiskt finns i trädet EFTER capture — ett
 * bevis, inte en gissning, att debouncen verkligen hann lösa sig och att skärmdumpen inte tyst
 * fångade skelett-laddningsläget (en längre debounce framöver skulle annars misslyckas tyst).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ArchiveScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * Advances Robolectric's paused main Looper well past [ArchiveViewModel]'s 250ms search
     * debounce so `uiState` has settled to `Loaded` by the time the screenshot is captured.
     */
    private fun advanceMainLooper() {
        shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)
    }

    private fun summary(
        id: String,
        name: String,
        scientificName: String,
        family: String,
        familySv: String,
        iucnStatus: String = "",
    ) = SpeciesSummary(
        id = SpeciesId(id),
        name = name,
        scientificName = scientificName,
        abundance = Abundance.ALLMÄN,
        heroImagePath = null,
        family = family,
        familySv = familySv,
        group = "songbirds",
        iucnStatus = iucnStatus,
    )

    private fun sixSpeciesRepo(): FakeSpeciesRepository =
        FakeSpeciesRepository().apply {
            searchResults.value =
                listOf(
                    summary("Q25485", "Talgoxe", "Parus major", "Paridae", "Mesfåglar"),
                    // Red-listed (T10b spec gap C) so the Archive's tag renders in these
                    // screenshots — Blåmes isn't really NT in species.db, this is fixture-only.
                    summary("Q25404", "Blåmes", "Cyanistes caeruleus", "Paridae", "Mesfåglar", iucnStatus = "NT"),
                    summary("Q25406", "Svartmes", "Periparus ater", "Paridae", "Mesfåglar"),
                    summary("Q25234", "Koltrast", "Turdus merula", "Turdidae", "Trastar"),
                    summary("Q25233", "Björktrast", "Turdus pilaris", "Turdidae", "Trastar"),
                    summary("Q25236", "Rödvingetrast", "Turdus iliacus", "Turdidae", "Trastar"),
                )
        }

    /** Talgoxe (Q25485) är den enda fångade arten i uppsättningen — nummer 7. */
    private fun stampedObservationRepo(): FakeObservationRepository =
        FakeObservationRepository().apply {
            seed(
                listOf(
                    Observation(
                        id = "o1",
                        speciesId = "Q25485",
                        capturedAt = Instant.parse("2026-08-01T09:00:00Z"),
                        savedAt = Instant.parse("2026-08-01T09:00:05Z"),
                        photoPath = "/fake/o1.jpg",
                        note = "",
                        confidence = 0.93f,
                        latitude = null,
                        longitude = null,
                        locationLabel = null,
                        stampNumber = 7,
                    ),
                ),
            )
        }

    private fun viewModel(
        locale: Locale,
        sort: ArchiveSort = ArchiveSort.ALPHA,
        premiumActive: Boolean = false,
    ) = ArchiveViewModel(
        repo = sixSpeciesRepo(),
        observationRepo = stampedObservationRepo(),
        prefs = FakeUserPreferences().apply { archiveSortValue = sort },
        locale = locale,
        premiumActiveFlow = flowOf(premiumActive),
    )

    @Composable
    private fun screen(
        locale: Locale,
        sort: ArchiveSort = ArchiveSort.ALPHA,
        premiumActive: Boolean = false,
        onJournalExport: (suspend () -> JournalExportResult)? = null,
    ) {
        val vm = remember { viewModel(locale, sort, premiumActive) }
        ArchiveScreen(
            viewModel = vm,
            locale = locale,
            onSpeciesClick = {},
            onPremiumClick = {},
            onJournalExport = onJournalExport,
        )
    }

    @Test
    @Config(qualifiers = "+sv")
    fun archive_sv() {
        compose.captureScreen("archive_sv", settle = ::advanceMainLooper) { screen(Locale.SV) }
        compose.onNodeWithText("Talgoxe").assertExists()
    }

    @Test
    @Config(qualifiers = "+en")
    fun archive_en() {
        compose.captureScreen("archive_en", settle = ::advanceMainLooper) { screen(Locale.EN) }
        compose.onNodeWithText("Talgoxe").assertExists()
    }

    /**
     * Sort = FAMILY: sticky [se.birdy.app.ui.encyclopedia.ArchiveScreen]'s `FamilyHeader`s render
     * (Mesfåglar/Trastar), the row this task restyles.
     */
    @Test
    @Config(qualifiers = "+sv")
    fun archive_family_sv() {
        compose.captureScreen("archive_family_sv", settle = ::advanceMainLooper) {
            screen(Locale.SV, sort = ArchiveSort.FAMILY)
        }
        compose.onNodeWithText("Talgoxe").assertExists()
    }

    /**
     * Premium active + an export lambda: [se.birdy.app.ui.components.PremiumTeaserCard] swaps its
     * "Lås upp" unlock CTA for the "Exportera fältdagbok" export variant (T10b minor 14).
     */
    @Test
    @Config(qualifiers = "+sv")
    fun archive_premium_sv() {
        compose.captureScreen("archive_premium_sv", settle = ::advanceMainLooper) {
            screen(
                Locale.SV,
                premiumActive = true,
                onJournalExport = { JournalExportResult.Success("/fake/export.pdf", pageCount = 4, sizeBytes = 245_000L) },
            )
        }
        compose.onNodeWithText("Talgoxe").assertExists()
        compose.onNodeWithText("Exportera fältdagbok").assertExists()
    }
}
