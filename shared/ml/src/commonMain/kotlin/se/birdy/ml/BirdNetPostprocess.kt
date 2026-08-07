package se.birdy.ml

/**
 * Rankar BirdNET-scores över ENDAST mappade (EU-)klasser och tar sedan topp [take].
 *
 * Filter-före-ranking är bärande: BirdNET 6K Globals råa topplaceringar domineras
 * ofta av brus/människa/icke-EU-pseudoklasser (5 735 av 6 362 index är omappade).
 * Att ta top-3 först och mappa efteråt kastade bort korrekt EU-art på råplats 4+
 * och renderade det som "ingen fågel hörd" (shippad bug t.o.m. vC126).
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
        .mapNotNull { (idx, score) -> lookup(idx)?.let { qid -> ClassificationResult(qid, score) } }
        .sortedByDescending { it.confidence }
        .take(take)
