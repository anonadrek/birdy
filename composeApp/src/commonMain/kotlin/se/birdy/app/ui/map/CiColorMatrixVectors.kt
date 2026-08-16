package se.birdy.app.ui.map

/**
 * Androids 4×5-ColorMatrix (rad-major; rad = [→R, →G, →B, →A, offset i 0..255])
 * omuttryckt i Core Images CIColorMatrix-konvention (fyra 4-vektorer + bias, allt 0..1).
 * Ren matte så färgpariteten mellan osmdroids ColorMatrixColorFilter och iOS-tintningen
 * bevisas i test i stället för med ögonmått.
 */
class CiColorMatrixVectors(
    val r: FloatArray,
    val g: FloatArray,
    val b: FloatArray,
    val a: FloatArray,
    val bias: FloatArray,
)

fun ciVectorsFrom(colorMatrix: FloatArray): CiColorMatrixVectors {
    require(colorMatrix.size == 20) { "expected 4x5 row-major ColorMatrix, got ${colorMatrix.size}" }

    fun row(i: Int) = floatArrayOf(colorMatrix[i * 5], colorMatrix[i * 5 + 1], colorMatrix[i * 5 + 2], colorMatrix[i * 5 + 3])
    return CiColorMatrixVectors(
        r = row(0),
        g = row(1),
        b = row(2),
        a = row(3),
        bias =
            floatArrayOf(
                colorMatrix[4] / 255f,
                colorMatrix[9] / 255f,
                colorMatrix[14] / 255f,
                colorMatrix[19] / 255f,
            ),
    )
}
