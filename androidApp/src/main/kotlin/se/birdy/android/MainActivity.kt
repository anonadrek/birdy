package se.birdy.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import se.birdy.app.App
import se.birdy.app.SpeciesRepositoryProvider
import se.birdy.app.badges.BadgeCatalogLoader
import se.birdy.app.bootstrap.SharedPrefsBadgeVersionStore
import se.birdy.app.di.AppGraph
import se.birdy.app.photo.PhotoStorageProvider
import se.birdy.content.Locale
import se.birdy.data.DatabaseFactory
import se.birdy.data.badge.BadgeRepositoryImpl
import se.birdy.data.db.BirdyData
import se.birdy.data.observation.SqlDelightObservationRepository
import se.birdy.datastore.PremiumStateStore
import se.birdy.datastore.UserPreferencesStore
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import se.birdy.ml.AndroidTfliteRunner
import se.birdy.ml.BirdClassifier
import se.birdy.ml.BirdClassifierFactory
import se.birdy.ml.ClassifierMode
import se.birdy.ml.FakeBirdClassifier
import se.birdy.ml.ImagePreprocessor
import se.birdy.ml.ModelArtifactProvider
import se.birdy.ml.TfLiteBirdClassifier
import se.birdy.ml.camera.AndroidCameraSource
import se.birdy.ml.loadAiyLabelMapper
import se.birdy.ml.loadModelMetadata
import java.io.File
import android.graphics.Color as AndroidColor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        // Edge-to-edge: BottomNavBar's paper background flows beneath the
        // system nav bar so the two surfaces fuse into a single strip.
        // BottomNavBar applies windowInsetsPadding(navigationBars) to keep
        // its tab icons above the system nav.
        enableEdgeToEdge(
            navigationBarStyle =
                SystemBarStyle.light(
                    AndroidColor.TRANSPARENT,
                    AndroidColor.TRANSPARENT,
                ),
        )
        super.onCreate(savedInstanceState)
        cleanOldCacheFrames()
        SpeciesRepositoryProvider.init(applicationContext)
        PhotoStorageProvider.init(applicationContext)
        val birdyData = BirdyData(DatabaseFactory(applicationContext).createDriver())
        val observationRepo = SqlDelightObservationRepository(birdyData.observationQueries)
        val badgeRepo = BadgeRepositoryImpl(birdyData.badgeUnlockQueries)
        // Catalog is small (25 badges from YAML); runBlocking ~10ms during onCreate is acceptable.
        // Async-loading would require state machine in AppGraph (catalog is required by SaveObservationUseCase
        // + BadgesViewModel constructors). Revisit post-v1.0 if cold-start budget tightens.
        val badgeCatalog = runBlocking { BadgeCatalogLoader.loadFromResources() }
        val badgeVersionStore = SharedPrefsBadgeVersionStore(applicationContext)
        val userPreferences = UserPreferencesStore(applicationContext).preferences()
        val premiumRepository = PremiumStateStore(applicationContext).repository()
        // BirdClassifierFactory.create() is suspend — we run it blocking here for v1 simplicity,
        // following the same precedent as BadgeCatalogLoader above. TFLite init + 3.5 MB model
        // read can take longer than the badge catalog (~10ms), but avoids a loading screen and
        // keeps App(graph) signature unchanged. Post-v1.0 this can move to an async splash flow
        // if cold-start budget tightens.
        val (classifier, classifierMode, modelVersion) = runBlocking { buildClassifier() }
        val premiumOverride: PremiumState? =
            if (BuildConfig.PREMIUM_DEBUG_FORCE_ACTIVE) {
                PremiumState.Active(PremiumTier.YEARLY, Clock.System.now())
            } else {
                null
            }
        val graph =
            AppGraph(
                repository = SpeciesRepositoryProvider.get(),
                classifier = classifier,
                classifierMode = classifierMode,
                cameraSourceFactory = {
                    AndroidCameraSource(applicationContext, this@MainActivity)
                },
                observationRepository = observationRepo,
                photoStorage = PhotoStorageProvider.get(),
                badgeRepository = badgeRepo,
                badgeCatalog = badgeCatalog,
                badgeVersionStore = badgeVersionStore,
                userPreferences = userPreferences,
                premiumRepository = premiumRepository,
                premiumOverride = premiumOverride,
                defaultLocale = Locale.SV,
                modelVersion = modelVersion,
                benchmarkScreen =
                    if (BuildConfig.DEBUG && modelVersion != null) {
                        {
                            se.birdy.app.debug
                                .BenchmarkScreen(classifier = classifier, modelVersion = modelVersion)
                        }
                    } else {
                        null
                    },
            )
        setContent { App(graph) }
    }

    private suspend fun buildClassifier(): Triple<BirdClassifier, ClassifierMode, String?> {
        val artifactProvider = ModelArtifactProvider()
        var capturedModelVersion: String? = null
        val factory =
            BirdClassifierFactory(
                createReal = {
                    val info = loadModelMetadata()
                    capturedModelVersion = info.modelVersion
                    val mapper = loadAiyLabelMapper()
                    val modelBytes = artifactProvider.loadModelBytes(info)
                    val runner = AndroidTfliteRunner(modelBytes, info)
                    val preprocessor = ImagePreprocessor()
                    TfLiteBirdClassifier(
                        info = info,
                        runner = runner,
                        preprocess = { input, modelInfo ->
                            preprocessor.preprocess(
                                input = input,
                                outHeight = modelInfo.inputHeightPx,
                                outWidth = modelInfo.inputWidthPx,
                                normalizationMean = modelInfo.normalizationMean.toFloatArray(),
                                normalizationStd = modelInfo.normalizationStd.toFloatArray(),
                            )
                        },
                        mapper = mapper,
                    )
                },
                // MUST be cheap + non-throwing — BirdClassifierFactory does not guard the DEMO-path fallback.
                createFallback = { FakeBirdClassifier() },
                onCrashlytics = { t ->
                    android.util.Log.e("Birdy", "TFLite init failed, falling back to Fake", t)
                    // FirebaseCrashlytics integration deferred — Plan 6 polish.
                },
            )
        val (classifier, mode) = factory.create()
        // capturedModelVersion is null when createReal threw and we fell back to DEMO.
        return Triple(classifier, mode, capturedModelVersion)
    }

    private fun cleanOldCacheFrames() {
        val cutoff = System.currentTimeMillis() - ONE_HOUR_MS
        listOf("scan-frames", "photo-input").forEach { sub ->
            val dir = File(cacheDir, sub)
            if (!dir.exists()) return@forEach
            dir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) file.delete()
            }
        }
    }

    private companion object {
        private const val ONE_HOUR_MS = 60L * 60L * 1000L
    }
}
