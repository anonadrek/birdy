package se.birdy.ml

actual fun normalize(pcm: ShortArray): FloatArray {
    val out = FloatArray(pcm.size)
    for (i in pcm.indices) {
        out[i] = pcm[i] / 32768f
    }
    return out
}
