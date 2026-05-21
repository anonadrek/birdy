package se.birdy.ml

data class AudioInput(
    val waveform: FloatArray,
    val sampleRate: Int,
    val durationMs: Int,
    val rawPcm: ShortArray? = null,
) {
    init {
        require(waveform.isNotEmpty()) { "waveform must not be empty" }
        require(sampleRate > 0) { "sampleRate must be positive" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioInput) return false
        return waveform.contentEquals(other.waveform) &&
            sampleRate == other.sampleRate &&
            durationMs == other.durationMs &&
            (rawPcm?.contentEquals(other.rawPcm) ?: (other.rawPcm == null))
    }

    override fun hashCode(): Int {
        var result = waveform.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + durationMs
        result = 31 * result + (rawPcm?.contentHashCode() ?: 0)
        return result
    }
}
