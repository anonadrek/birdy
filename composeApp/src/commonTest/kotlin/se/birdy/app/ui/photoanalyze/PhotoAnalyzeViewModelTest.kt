package se.birdy.app.ui.photoanalyze

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import se.birdy.ml.BirdClassifier
import se.birdy.ml.Classification
import se.birdy.ml.FakeBirdClassifier
import se.birdy.ml.ImageInput
import se.birdy.ml.ImageOrigin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoAnalyzeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun before() = Dispatchers.setMain(dispatcher)

    @AfterTest fun after() = Dispatchers.resetMain()

    private val acceptableFrame =
        ImageInput(
            bytes = ByteArray(8),
            widthPx = 1024,
            heightPx = 768,
            format = se.birdy.ml.FrameFormat.JPEG,
        )

    @Test
    fun analyze_emits_loaded_with_predictions_and_path() =
        runTest(dispatcher) {
            val vm =
                PhotoAnalyzeViewModel(
                    classifier = FakeBirdClassifier(),
                    persist = { _ -> "/cache/photo-input/abc.jpg" },
                )
            vm.state.test {
                assertEquals(PhotoAnalyzeUiState.Idle, awaitItem())
                vm.analyze(acceptableFrame)
                assertEquals(PhotoAnalyzeUiState.Analyzing, awaitItem())
                val loaded = awaitItem()
                assertIs<PhotoAnalyzeUiState.Loaded>(loaded)
                assertEquals("Q25485", loaded.predictions.first().speciesId)
                assertEquals("/cache/photo-input/abc.jpg", loaded.frameJpegPath)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun analyze_carries_origin_and_exif_into_loaded() =
        runTest(dispatcher) {
            val vm =
                PhotoAnalyzeViewModel(
                    classifier = FakeBirdClassifier(),
                    persist = { _ -> "/cache/photo-input/abc.jpg" },
                )
            vm.state.test {
                assertEquals(PhotoAnalyzeUiState.Idle, awaitItem())
                vm.analyze(acceptableFrame, origin = ImageOrigin.Gallery, exifLatitude = 59.3, exifLongitude = 18.0)
                assertEquals(PhotoAnalyzeUiState.Analyzing, awaitItem())
                val loaded = awaitItem()
                assertIs<PhotoAnalyzeUiState.Loaded>(loaded)
                assertEquals(ImageOrigin.Gallery, loaded.origin)
                assertEquals(59.3, loaded.exifLatitude)
                assertEquals(18.0, loaded.exifLongitude)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun analyze_with_too_small_image_emits_too_small_error() =
        runTest(dispatcher) {
            val tiny = ImageInput(bytes = ByteArray(8), widthPx = 100, heightPx = 100)
            val vm =
                PhotoAnalyzeViewModel(
                    classifier = FakeBirdClassifier(),
                    persist = { _ -> "ignored" },
                    minShortSide = 224,
                )
            vm.state.test {
                assertEquals(PhotoAnalyzeUiState.Idle, awaitItem())
                vm.analyze(tiny)
                val err = awaitItem()
                assertIs<PhotoAnalyzeUiState.Error>(err)
                assertEquals(PhotoAnalyzeUiState.Error.Kind.TooSmall, err.kind)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun analyze_with_throwing_classifier_emits_classifier_failure() =
        runTest(dispatcher) {
            val throwing =
                object : BirdClassifier {
                    override suspend fun classify(image: ImageInput): Classification = error("boom")
                }
            val vm =
                PhotoAnalyzeViewModel(
                    classifier = throwing,
                    persist = { _ -> "/cache/photo-input/x.jpg" },
                )
            vm.state.test {
                assertEquals(PhotoAnalyzeUiState.Idle, awaitItem())
                vm.analyze(acceptableFrame)
                assertEquals(PhotoAnalyzeUiState.Analyzing, awaitItem())
                val err = awaitItem()
                assertIs<PhotoAnalyzeUiState.Error>(err)
                assertEquals(PhotoAnalyzeUiState.Error.Kind.ClassifierFailure, err.kind)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun analyze_with_persist_failure_emits_io_failure() =
        runTest(dispatcher) {
            val vm =
                PhotoAnalyzeViewModel(
                    classifier = FakeBirdClassifier(),
                    persist = { _ -> error("disk full") },
                )
            vm.state.test {
                assertEquals(PhotoAnalyzeUiState.Idle, awaitItem())
                vm.analyze(acceptableFrame)
                assertEquals(PhotoAnalyzeUiState.Analyzing, awaitItem())
                val err = awaitItem()
                assertIs<PhotoAnalyzeUiState.Error>(err)
                assertEquals(PhotoAnalyzeUiState.Error.Kind.IoFailure, err.kind)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun decode_failed_emits_decode_failure_error() =
        runTest(dispatcher) {
            val vm =
                PhotoAnalyzeViewModel(
                    classifier = FakeBirdClassifier(),
                    persist = { _ -> "ignored" },
                )
            vm.state.test {
                assertEquals(PhotoAnalyzeUiState.Idle, awaitItem())
                vm.decodeFailed()
                val err = awaitItem()
                assertIs<PhotoAnalyzeUiState.Error>(err)
                assertEquals(PhotoAnalyzeUiState.Error.Kind.DecodeFailure, err.kind)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
