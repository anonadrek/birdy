package se.birdy.ml

/** Loads model binary bytes from the configured distribution channel. */
expect class ModelArtifactProvider() {
    suspend fun loadModelBytes(info: BirdClassifierModelInfo): ByteArray
}
