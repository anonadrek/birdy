package se.birdy.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import se.birdy.app.App
import se.birdy.app.SpeciesRepositoryProvider
import se.birdy.app.badges.BadgeCatalogLoader
import se.birdy.app.bootstrap.SharedPrefsBadgeVersionStore
import se.birdy.app.di.AppGraph
import se.birdy.app.i18n.LocaleResolver
import se.birdy.app.i18n.toLocaleTagOrNull
import se.birdy.app.photo.PhotoStorageProvider
import se.birdy.app.ui.debug.DiagnosticsRunner
import se.birdy.app.ui.debug.DiagnosticsScreen
import se.birdy.app.ui.settings.AppLocaleApplier
import se.birdy.app.ui.settings.SettingsLauncherSetup
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
import se.birdy.ml.ClassifierBootstrap
import se.birdy.ml.ClassifierBootstrapState
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
    private lateinit var appGraph: AppGraph

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
        AppLocaleApplier.init(applicationContext)
        SettingsLauncherSetup.init(applicationContext)
        // Build graph before setContent so recomposition cannot orphan
        // ClassifierBootstrap or leak the TFLite Interpreter.
        appGraph = buildAppGraph()
        setContent { App(appGraph) }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel in-flight init coroutine and free TFLite native handle.
        // Rotation destroys+recreates Activity so the classifier re-loads
        // (~14ms p95); singleton-lift to Application scope is a follow-up.
        appGraph.classifierBootstrap.close()
    }

    private fun buildAppGraph(): AppGraph {
        val birdyData = BirdyData(DatabaseFactory(applicationContext).createDriver())
        val observationRepo = SqlDelightObservationRepository(birdyData.observationQueries)
        val badgeRepo = BadgeRepositoryImpl(birdyData.badgeUnlockQueries)
        // Catalog is small (25 badges from YAML); runBlocking ~10ms during onCreate is acceptable.
        val badgeCatalog = runBlocking { BadgeCatalogLoader.loadFromResources() }
        val badgeVersionStore = SharedPrefsBadgeVersionStore(applicationContext)
        val userPreferences = UserPreferencesStore(applicationContext).preferences()
        val premiumRepository = PremiumStateStore(applicationContext).repository()
        val classifierBootstrap = ClassifierBootstrap(buildClassifier = { buildClassifier() })
        val premiumOverride: PremiumState? =
            if (BuildConfig.PREMIUM_DEBUG_FORCE_ACTIVE) {
                PremiumState.Active(PremiumTier.YEARLY, Clock.System.now())
            } else {
                null
            }
        val overrideTag = runBlocking { userPreferences.appLanguage.first() }.toLocaleTagOrNull()
        val resolvedLocale =
            LocaleResolver.resolve(
                override = overrideTag,
                systemTag = resources.configuration.locales[0].toLanguageTag(),
            )
        return AppGraph(
            repository = SpeciesRepositoryProvider.get(),
            classifierBootstrap = classifierBootstrap,
            cameraSourceFactory = { AndroidCameraSource(applicationContext, this@MainActivity) },
            observationRepository = observationRepo,
            photoStorage = PhotoStorageProvider.get(),
            badgeRepository = badgeRepo,
            badgeCatalog = badgeCatalog,
            badgeVersionStore = badgeVersionStore,
            userPreferences = userPreferences,
            premiumRepository = premiumRepository,
            premiumOverride = premiumOverride,
            versionName = BuildConfig.VERSION_NAME,
            defaultLocale = resolvedLocale,
            benchmarkScreen = buildBenchmarkScreen(classifierBootstrap),
            diagnosticsScreen = buildDiagnosticsScreen(classifierBootstrap),
        )
    }

    private fun buildBenchmarkScreen(bootstrap: ClassifierBootstrap): (@Composable () -> Unit)? =
        if (BuildConfig.DEBUG) {
            @Composable {
                val ready = bootstrap.state.collectAsState().value as? ClassifierBootstrapState.Ready
                val version = ready?.modelVersion
                if (ready != null && version != null) {
                    se.birdy.app.debug.BenchmarkScreen(
                        classifier = ready.classifier,
                        modelVersion = version,
                    )
                }
            }
        } else {
            null
        }

    private fun buildDiagnosticsScreen(bootstrap: ClassifierBootstrap): (@Composable () -> Unit)? =
        if (BuildConfig.DEBUG) {
            @Composable {
                val ready = bootstrap.state.collectAsState().value as? ClassifierBootstrapState.Ready
                if (ready != null) {
                    val classifier = ready.classifier
                    val runner =
                        androidx.compose.runtime.remember(classifier) {
                            DiagnosticsRunner(context = applicationContext, classifier = classifier)
                        }
                    DiagnosticsScreen(runDiagnostic = { runner.run() })
                }
            }
        } else {
            null
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
