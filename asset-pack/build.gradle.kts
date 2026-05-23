// Plan 6b3 T22b: Install-time asset pack for species images (WebP).
// Moves ~320 MB of bundled hero/secondary images out of the base APK so the
// Play Store base-APK download stays under the 150 MB limit. Install-time
// packs install with the app and are accessible via AssetManager just like
// regular assets, via `file:///android_asset/images/...` URIs.
plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("birdy_images")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
