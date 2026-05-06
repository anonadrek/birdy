package se.birdy.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kotlinx.coroutines.runBlocking
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
import se.birdy.ml.FakeBirdClassifier
import se.birdy.ml.camera.AndroidCameraSource
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
        val graph =
            AppGraph(
                repository = SpeciesRepositoryProvider.get(),
                classifier = FakeBirdClassifier(),
                cameraSourceFactory = {
                    AndroidCameraSource(applicationContext, this@MainActivity)
                },
                observationRepository = observationRepo,
                photoStorage = PhotoStorageProvider.get(),
                badgeRepository = badgeRepo,
                badgeCatalog = badgeCatalog,
                badgeVersionStore = badgeVersionStore,
                defaultLocale = Locale.SV,
            )
        setContent { App(graph) }
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
