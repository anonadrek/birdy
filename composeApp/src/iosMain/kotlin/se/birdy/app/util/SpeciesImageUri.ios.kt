package se.birdy.app.util

import platform.Foundation.NSBundle

/**
 * iOS actual. Plate images are NOT bundled in i0 (they live in the Android asset
 * pack); Coil shows its error placeholder. Plan i1 bundles them and this path
 * starts resolving.
 */
actual fun speciesImageUri(relativePath: String): String = "file://${NSBundle.mainBundle.resourcePath}/images/$relativePath"
