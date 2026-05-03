plugins {
    id("birdy.kmp-android-lib")
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(project(":shared:domain"))
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
    }
}

android {
    namespace = "se.birdy.data"
}

sqldelight {
    databases {
        // Placeholder — Plan 2 will register the BirdyDatabase here with .sq files.
    }
}
