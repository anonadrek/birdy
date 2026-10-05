package se.birdy.app.notifications

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import se.birdy.app.SpeciesRepositoryProvider
import se.birdy.app.badges.BadgeCatalogLoader
import se.birdy.app.i18n.LocaleResolver
import se.birdy.app.i18n.toLocaleTagOrNull
import se.birdy.content.SpeciesId
import se.birdy.data.DatabaseFactory
import se.birdy.data.badge.BadgeRepositoryImpl
import se.birdy.data.dailybird.DailyBirdHistoryRepositoryImpl
import se.birdy.data.db.BirdyData
import se.birdy.data.observation.SqlDelightObservationRepository
import se.birdy.datastore.UserPreferencesStore
import se.birdy.domain.dailybird.DailyBirdSelector

/**
 * Builds [NotificationPayloads] for WorkManager when [se.birdy.app.AndroidAppGraphHolder]
 * is empty. That is the common overnight case: WorkManager starts a fresh process
 * without [android.app.Activity.onCreate], so the holder was never set.
 *
 * Only data-layer collaborators — no TFLite, billing, or camera.
 */
internal object AndroidNotificationPayloads {
    /**
     * Builds a standalone [NotificationPayloads] and hands it to [use], then closes the
     * one-off SQLDelight driver backing it (try/finally). The driver was never closed
     * before — SQLDelight's `AndroidSqliteDriver` caches its `SupportSQLiteDatabase` behind
     * a `Lazy`, so it can't transparently reopen after close() the way a bare
     * `SQLiteOpenHelper` would; the close must happen only once [use] (i.e. the caller's
     * single `dailyBird`/`weeklyRecap`/`trophyProgress` query) has actually finished with it.
     * The live-graph path (`NotificationPayloads.from(graph)`) never calls this function, so
     * it is unaffected.
     */
    suspend fun <T> fromContext(
        context: Context,
        use: suspend (NotificationPayloads) -> T,
    ): T {
        val appContext = context.applicationContext
        SpeciesRepositoryProvider.init(appContext)
        val driver = DatabaseFactory(appContext).createDriver()
        try {
            val birdyData = BirdyData(driver)
            val userPreferences = UserPreferencesStore(appContext).preferences()
            val overrideTag = userPreferences.appLanguage.first().toLocaleTagOrNull()
            val resolvedLocale =
                LocaleResolver.resolve(
                    override = overrideTag,
                    systemTag =
                        appContext.resources.configuration.locales[0]
                            .toLanguageTag(),
                )
            val dailyBirdHistory = DailyBirdHistoryRepositoryImpl(birdyData)
            val dailyBirdSelector =
                DailyBirdSelector(
                    speciesProvider = { SpeciesRepositoryProvider.get().allByQid(resolvedLocale) },
                )
            val payloads =
                NotificationPayloads(
                    prefs = userPreferences,
                    observationRepo = SqlDelightObservationRepository(birdyData.observationQueries),
                    badgeRepo = BadgeRepositoryImpl(birdyData.badgeUnlockQueries),
                    badgeCatalog = BadgeCatalogLoader.loadFromResources(),
                    speciesByQid = { SpeciesRepositoryProvider.get().allByQid(resolvedLocale) },
                    speciesNameFor = { qid ->
                        SpeciesRepositoryProvider
                            .get()
                            .getById(SpeciesId(qid), resolvedLocale)
                            .first()
                            ?.name
                    },
                    selectDailyBird = { date -> dailyBirdSelector.selectFor(date) },
                    dailyBirdMatchCount = { dailyBirdHistory.totalMatchCount() },
                    timeZone = TimeZone.currentSystemDefault(),
                    clock = Clock.System,
                )
            return use(payloads)
        } finally {
            driver.close()
        }
    }
}
