package se.birdy.app.util

// Returns a Coil-loadable URI for a species image. On Android the images
// live in the install-time asset pack `:asset-pack` (see Plan 6b3 T22b),
// reached via `file:///android_asset/images/...`.
expect fun speciesImageUri(relativePath: String): String
