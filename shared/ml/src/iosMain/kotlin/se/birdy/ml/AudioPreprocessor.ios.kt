package se.birdy.ml

actual fun normalize(pcm: ShortArray): FloatArray = FloatArray(pcm.size) { i -> pcm[i] / 32768f }
