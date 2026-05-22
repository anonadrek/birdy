plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget()
    jvm()
}

android {
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        // Plan 6b3 T20: align with :composeApp / :androidApp (both VERSION_21) since the
        // project's JDK is 21 across the board — keeps shared android-lib bytecode and the
        // app bytecode on the same target.
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
