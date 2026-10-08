plugins {
    id("birdy.kmp-android-lib")
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(project(":shared:content"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            // DailyBirdGoldenGenerator reads the app's species.db (the website's golden file).
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

android {
    namespace = "se.birdy.domain"
}
