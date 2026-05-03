plugins {
    id("birdy.kmp-android-lib")
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "se.birdy.domain"
}
