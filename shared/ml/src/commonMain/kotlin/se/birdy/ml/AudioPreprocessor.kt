package se.birdy.ml

/**
 * Normalizes signed 16-bit PCM samples to float32 in range [-1f, 1f].
 *
 * BirdNET-Lite expects float32 waveform input; this is the simplest possible
 * conversion (divide by 32768f). No FFT/spectrogram is computed in Kotlin —
 * the TFLite graph contains the audio-spec ops.
 *
 * JVM target throws because audio capture is Android-only; tests on JVM should
 * inject [FakeAudioClassifier] instead of going through this path.
 */
expect fun normalize(pcm: ShortArray): FloatArray
