package se.birdy.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import se.birdy.app.App
import se.birdy.app.SpeciesRepositoryProvider
import se.birdy.app.di.AppGraph
import se.birdy.app.photo.PhotoStorageProvider
import se.birdy.content.Locale
import se.birdy.ml.FakeBirdClassifier
import se.birdy.ml.camera.AndroidCameraSource
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cleanOldCacheFrames()
        SpeciesRepositoryProvider.init(applicationContext)
        PhotoStorageProvider.init(applicationContext)
        val graph =
            AppGraph(
                repository = SpeciesRepositoryProvider.get(),
                classifier = FakeBirdClassifier(),
                cameraSourceFactory = {
                    AndroidCameraSource(applicationContext, this@MainActivity)
                },
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
