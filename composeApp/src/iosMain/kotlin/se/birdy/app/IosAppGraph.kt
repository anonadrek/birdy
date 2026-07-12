package se.birdy.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import platform.Foundation.NSUserDefaults
import se.birdy.app.badges.BadgeCatalogLoader
import se.birdy.app.bootstrap.BadgeVersionStore
import se.birdy.app.di.AppGraph
import se.birdy.app.photo.PhotoStorageProvider
import se.birdy.app.ui.scan.IosNoopCameraSource
import se.birdy.data.DatabaseFactory
import se.birdy.data.badge.BadgeRepositoryImpl
import se.birdy.data.db.BirdyData
import se.birdy.data.observation.SqlDelightObservationRepository
import se.birdy.datastore.UserPreferencesStore
import se.birdy.domain.premium.PremiumRepository
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import se.birdy.ml.ClassifierBootstrap
import se.birdy.ml.ClassifierMode
import se.birdy.ml.FakeBirdClassifier

/**
 * iOS composition root — the iOS counterpart of MainActivity.buildAppGraph().
 *
 * Remaining stubs (each lifted by its owning plan):
 * - FakeBirdClassifier in DEMO mode: scanning is stubbed (i2).
 * - premiumOverride Active(LIFETIME): launch-parity with Android's
 *   PREMIUM_OPEN_FOR_LAUNCH; real StoreKit gating lands in i5.
 *
 * i1 resolved: UserPreferences + BadgeVersionStore now persist (NSUserDefaults).
 */
fun buildIosAppGraph(): AppGraph {
    val birdyData = BirdyData(DatabaseFactory().createDriver())
    val observationRepo = SqlDelightObservationRepository(birdyData.observationQueries)
    val badgeRepo = BadgeRepositoryImpl(birdyData.badgeUnlockQueries)
    val badgeCatalog = runBlocking { BadgeCatalogLoader.loadFromResources() }
    val classifierBootstrap =
        ClassifierBootstrap(
            buildClassifier = { Triple(FakeBirdClassifier(), ClassifierMode.DEMO, null) },
        )
    return AppGraph(
        repository = SpeciesRepositoryProvider.get(),
        classifierBootstrap = classifierBootstrap,
        cameraSourceFactory = { IosNoopCameraSource() },
        observationRepository = observationRepo,
        photoStorage = PhotoStorageProvider.get(),
        badgeRepository = badgeRepo,
        badgeCatalog = badgeCatalog,
        badgeVersionStore = NsUserDefaultsBadgeVersionStore(),
        userPreferences = UserPreferencesStore(null).preferences(),
        premiumRepository = IosStubPremiumRepository(),
        premiumOverride = PremiumState.Active(PremiumTier.LIFETIME, Clock.System.now()),
        versionName = "1.2.0-ios-i1",
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
