package se.birdy.app.ui.map

/**
 * The map's own paper. MapTiler's tiles are shown unmodified (terms §4.4, see MapTilerUrls.kt), so
 * the only colour the app adds under the map is the paper osmdroid draws where a tile hasn't loaded
 * yet, instead of its grey loading grid.
 */
object MapTileTheme {
    /**
     * Field Journal paper (MossCreme #F6EFE2, the app's primary background), so a tile that is
     * still loading reads as the same paper as the screen around the map (B7).
     */
    const val PAPER: Int = 0xF6EFE2
}
