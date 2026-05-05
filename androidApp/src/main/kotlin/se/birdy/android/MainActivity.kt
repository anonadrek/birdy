package se.birdy.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import se.birdy.app.App
import se.birdy.app.SpeciesRepositoryProvider
import se.birdy.app.di.AppGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SpeciesRepositoryProvider.init(applicationContext)
        val graph = AppGraph(
            repository = SpeciesRepositoryProvider.get(),
            classifier = se.birdy.ml.FakeBirdClassifier(),
            cameraSourceFactory = {
                object : se.birdy.ml.CameraSource {
                    override fun frames() = kotlinx.coroutines.flow.emptyFlow<se.birdy.ml.ImageInput>()
                    override suspend fun start() = error("CameraSource not implemented yet (Task 7)")
                    override suspend fun stop() = Unit
                }
            },
            defaultLocale = se.birdy.content.Locale.SV,
        )
        setContent { App(graph) }
    }
}
