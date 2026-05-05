package se.birdy.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import se.birdy.app.App
import se.birdy.app.SpeciesRepositoryProvider
import se.birdy.app.di.AppGraph
import se.birdy.content.Locale
import se.birdy.ml.CameraSource
import se.birdy.ml.FakeBirdClassifier
import se.birdy.ml.ImageInput

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SpeciesRepositoryProvider.init(applicationContext)
        val graph =
            AppGraph(
                repository = SpeciesRepositoryProvider.get(),
                classifier = FakeBirdClassifier(),
                cameraSourceFactory = {
                    object : CameraSource {
                        override fun frames(): Flow<ImageInput> = emptyFlow()

                        override suspend fun start() = error("CameraSource not implemented yet (Task 7)")

                        override suspend fun stop() = Unit
                    }
                },
                defaultLocale = Locale.SV,
            )
        setContent { App(graph) }
    }
}
