package se.birdy.ml

import kotlin.math.exp

/**
 * BirdNET-Lite emitterar pre-sigmoid-logits; flat sigmoid mappar till [0, 1]
 * (klipp ±15 mot overflow). Speglar BirdNET-Analyzers `flat_sigmoid`.
 * commonMain så Android- och iOS-runnern delar exakt samma formel (i3 T2).
 */
fun flatSigmoid(logit: Float): Float {
    val clipped = logit.coerceIn(-15f, 15f)
    return 1f / (1f + exp(-clipped))
}

/**
 * Rankar BirdNET-scores över ENDAST mappade (EU-)klasser och tar sedan topp [take].
 *
 * Filter-före-ranking är bärande: BirdNET 6K Globals råa topplaceringar domineras
 * ofta av brus/människa/icke-EU-pseudoklasser (5 735 av 6 362 index är omappade).
 * Att ta top-3 först och mappa efteråt kastade bort korrekt EU-art på råplats 4+
 * och renderade det som "ingen fågel hörd" (shippad bug t.o.m. vC126).
 *
 * Icke-ändliga scores (NaN) kastas: digital tystnad (exakta nollor, t.ex. när systemet
 * stängt av mikrofonen eller en annan app håller den) ger NaN i alla BirdNET-logits. NaN
 * sorterades överst, visades som "Hör: Berguv · 0%" och fällde resultatets JSON så att
 * sessionen slutade i "Kunde inte identifiera ljudet." (1.3.0 Plan 3 Task 7). Utan scores
 * blir fönstret tomt och sessionen landar i NoBird.
 *
 * commonMain så att iOS-runnern (i3) återanvänder exakt samma postprocess.
 */
fun rankMappedScores(
    scores: FloatArray,
    lookup: (Int) -> String?,
    take: Int = 3,
): List<ClassificationResult> =
    scores
        .mapIndexed { idx, score -> idx to score }
        .filter { (_, score) -> score.isFinite() }
        .mapNotNull { (idx, score) -> lookup(idx)?.let { qid -> ClassificationResult(qid, score) } }
        .sortedByDescending { it.confidence }
        .take(take)

/**
 * True when every sample is exactly zero (or there are none): digital silence, which a real
 * microphone never produces on its own. It means the system or another app silenced the mic
 * (privacy toggle, a call, another recorder; the emulator's virtual mic always does). BirdNET
 * turns it into NaN for every class, so it is a recording fault, not "no bird heard"
 * (Release 1.3.0 Plan 3 Task 7 review).
 */
fun ShortArray.isDigitalSilence(): Boolean = all { it == 0.toShort() }

/**
 * A log line when [scores] holds non-finite values although [input] was not digital silence:
 * that points to a model or runtime fault, which [rankMappedScores] would otherwise turn into a
 * silent "no bird". Null when the scores are all finite or the input was silence (the caller
 * reports silence as a recording fault).
 */
fun nonFiniteScoreWarning(
    scores: FloatArray,
    input: AudioInput,
): String? {
    val nonFinite = scores.count { !it.isFinite() }
    val silence = input.rawPcm?.isDigitalSilence() ?: input.waveform.all { it == 0f }
    return if (nonFinite == 0 || silence) {
        null
    } else {
        "BirdNET gave $nonFinite of ${scores.size} non-finite scores for non-silent input"
    }
}
