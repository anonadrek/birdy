package se.birdy.ml

data class AudioModelInfo(
    val modelVersion: String,
    val inputShape: List<Int>,
    val outputShape: List<Int>,
    val coveragePct: Double,
)
