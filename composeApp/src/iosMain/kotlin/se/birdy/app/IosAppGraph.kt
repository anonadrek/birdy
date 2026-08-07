package se.birdy.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages
import se.birdy.app.badges.BadgeCatalogLoader
import se.birdy.app.bootstrap.BadgeVersionStore
import se.birdy.app.di.AppGraph
import se.birdy.app.i18n.LocaleResolver
import se.birdy.app.i18n.toLocaleTagOrNull
import se.birdy.app.photo.PhotoStorageProvider
import se.birdy.data.DatabaseFactory
import se.birdy.data.badge.BadgeRepositoryImpl
import se.birdy.data.db.BirdyData
import se.birdy.data.observation.SqlDelightObservationRepository
import se.birdy.datastore.UserPreferencesStore
import se.birdy.domain.premium.PremiumRepository
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import se.birdy.ml.BirdClassifierFactory
import se.birdy.ml.ClassifierBootstrap
import se.birdy.ml.FakeBirdClassifier
import se.birdy.ml.ImagePreprocessor
import se.birdy.ml.IosTfliteRunner
import se.birdy.ml.ModelArtifactProvider
import se.birdy.ml.TfLiteBirdClassifier
import se.birdy.ml.camera.IosCameraSource
import se.birdy.ml.loadAiyLabelMapper
import se.birdy.ml.loadModelMetadata

/**
 * iOS composition root — the iOS counterpart of MainActivity.buildAppGraph().
 *
 * Remaining stubs (each lifted by its owning plan):
 * - premiumOverride Active(LIFETIME): launch-parity with Android's
 *   PREMIUM_OPEN_FOR_LAUNCH; real StoreKit gating lands in i5.
 *
 * i1 resolved: UserPreferences + BadgeVersionStore now persist (NSUserDefaults).
 * i2b resolved: buildClassifier() mirrors Android's real TFLite classifier wiring.
 * i2c resolved: live-camera scan is now REAL (IosCameraSource, AVFoundation) — only the
 *   premium override remains.
 */
fun buildIosAppGraph(): AppGraph {
    val birdyData = BirdyData(DatabaseFactory().createDriver())
    val observationRepo = SqlDelightObservationRepository(birdyData.observationQueries)
    val badgeRepo = BadgeRepositoryImpl(birdyData.badgeUnlockQueries)
    val badgeCatalog = runBlocking { BadgeCatalogLoader.loadFromResources() }
    val classifierBootstrap =
        ClassifierBootstrap(
            buildClassifier = {
                val artifactProvider = ModelArtifactProvider()
                var capturedModelVersion: String? = null
                val factory =
                    BirdClassifierFactory(
                        createReal = {
                            val info = loadModelMetadata()
                            capturedModelVersion = info.modelVersion
                            val mapper = loadAiyLabelMapper()
                            val modelBytes = artifactProvider.loadModelBytes(info)
                            val runner = IosTfliteRunner(modelBytes, info)
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
                        onCrashlytics = { /* no Crashlytics on iOS yet — swallow; factory falls back to FakeBirdClassifier + DEMO */ },
                    )
                val (classifier, mode) = factory.create()
                // capturedModelVersion is null when createReal threw and we fell back to DEMO.
                Triple(classifier, mode, capturedModelVersion)
            },
        )
    val userPreferences = UserPreferencesStore(null).preferences()
    // Mirror of MainActivity.buildAppGraph()'s locale resolution (line 323-328).
    // Without this, species content is hard-locked to the AppGraph default Locale.SV
    // regardless of device language and the app_language pref. A fresh graph is built
    // per app launch, so language changes take effect on next launch (live-switch = i4).
    val storedLanguage = runBlocking { userPreferences.appLanguage.first() }
    val resolvedLocale =
        LocaleResolver.resolve(
            override = storedLanguage.toLocaleTagOrNull(),
            systemTag = (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "en",
        )
    return AppGraph(
        repository = SpeciesRepositoryProvider.get(),
        classifierBootstrap = classifierBootstrap,
        cameraSourceFactory = { IosCameraSource() },
        observationRepository = observationRepo,
        photoStorage = PhotoStorageProvider.get(),
        badgeRepository = badgeRepo,
        badgeCatalog = badgeCatalog,
        badgeVersionStore = NsUserDefaultsBadgeVersionStore(),
        userPreferences = userPreferences,
        premiumRepository = IosStubPremiumRepository(),
        premiumOverride = PremiumState.Active(PremiumTier.LIFETIME, Clock.System.now()),
        versionName = "1.2.0-ios-i2c",
        defaultLocale = resolvedLocale,
    )
}

/** Persists the last badge-catalog version we backfilled, so it does not re-run each launch. */
internal class NsUserDefaultsBadgeVersionStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : BadgeVersionStore {
    override var lastSeen: Int
        get() = defaults.integerForKey(KEY).toInt()
        set(value) {
            defaults.setInteger(value.toLong(), forKey = KEY)
        }

    private companion object {
        const val KEY = "birdy_badges.catalog_version_last_seen"
    }
}

/** Local premium state only; real StoreKit repository lands in plan i5. */
internal class IosStubPremiumRepository : PremiumRepository {
    private val _state = MutableStateFlow<PremiumState>(PremiumState.Free)
    override val state: StateFlow<PremiumState> = _state.asStateFlow()

    override suspend fun markPurchased(tier: PremiumTier) {
        _state.value = PremiumState.Active(tier, Clock.System.now())
    }

    override suspend fun restore() = Unit
}
