package se.birdy.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import se.birdy.app.App
import se.birdy.app.SpeciesRepositoryProvider
import se.birdy.app.di.AppGraph
import se.birdy.content.Locale
import se.birdy.ml.FakeBirdClassifier
import se.birdy.ml.camera.AndroidCameraSource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SpeciesRepositoryProvider.init(applicationContext)
        val activity = this@MainActivity
        val graph =
            AppGraph(
                repository = SpeciesRepositoryProvider.get(),
                classifier = FakeBirdClassifier(),
                cameraSourceFactory = {
                    AndroidCameraSource(applicationContext, activity)
                },
                defaultLocale = Locale.SV,
            )
        setContent { App(graph) }
    }
}
