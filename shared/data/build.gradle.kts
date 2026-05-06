plugins {
    id("birdy.kmp-android-lib")
    alias(libs.plugins.sqldelight)
}

// Exclude SQLDelight generated sources from ktlint — they use 2-space indent
// and won't pass our style rules.
afterEvaluate {
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        filter {
            exclude { element ->
                element.file.path.contains("generated")
            }
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:domain"))
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.junit.jupiter)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}

android {
    namespace = "se.birdy.data"
}

sqldelight {
    databases {
        create("BirdyData") {
            packageName.set("se.birdy.data.db")
            srcDirs.setFrom("src/commonMain/sqldelight")
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
