package se.birdy.app.ui.photoanalyze

/** Heltalsrektangel i källbildens pixel-koordinater. */
data class CropRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

enum class CropHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** Ren crop-rect-matematik (inga Android-typer → JVM-testbar). */
object CropGeometry {
    fun fullRect(
        width: Int,
        height: Int,
    ): CropRect = CropRect(0, 0, width, height)

    /**
     * Flytta ett hörn till (x, y) i käll-px, klampat så hörnet stannar inom bilden
     * och varje sida förblir >= minSide.
     */
    fun resizeToCorner(
        rect: CropRect,
        handle: CropHandle,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        minSide: Int,
    ): CropRect {
        val cx = x.coerceIn(0, width)
        val cy = y.coerceIn(0, height)
        return when (handle) {
            CropHandle.TOP_LEFT ->
                rect.copy(
                    left = cx.coerceAtMost(rect.right - minSide),
                    top = cy.coerceAtMost(rect.bottom - minSide),
                )
            CropHandle.TOP_RIGHT ->
                rect.copy(
                    right = cx.coerceAtLeast(rect.left + minSide),
                    top = cy.coerceAtMost(rect.bottom - minSide),
                )
            CropHandle.BOTTOM_LEFT ->
                rect.copy(
                    left = cx.coerceAtMost(rect.right - minSide),
                    bottom = cy.coerceAtLeast(rect.top + minSide),
                )
            CropHandle.BOTTOM_RIGHT ->
                rect.copy(
                    right = cx.coerceAtLeast(rect.left + minSide),
                    bottom = cy.coerceAtLeast(rect.top + minSide),
                )
        }
    }

    /** Translatera hela rektangeln, klampad så den stannar inom bilden (storlek bevaras). */
    fun move(
        rect: CropRect,
        dx: Int,
        dy: Int,
        width: Int,
        height: Int,
    ): CropRect {
        val w = rect.width
        val h = rect.height
        val newLeft = (rect.left + dx).coerceIn(0, width - w)
        val newTop = (rect.top + dy).coerceIn(0, height - h)
        return CropRect(newLeft, newTop, newLeft + w, newTop + h)
    }
}
