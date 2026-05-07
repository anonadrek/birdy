package se.birdy.ml

import birdy_bird_scanner.shared.ml.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

class ModelArtifactProvider {
    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadModelBytes(info: BirdClassifierModelInfo): ByteArray =
        when (info.distribution) {
            ModelDistribution.COMPOSE_RESOURCES ->
                Res.readBytes("files/ml/aiy_birds_v1.tflite")
        }
}
