plugins {
    id("birdy.kmp-android-lib")
    alias(libs.plugins.kotlin.serialization)
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
            implementation(libs.kotlinx.serialization.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.kaml)
            implementation(libs.sqldelight.sqlite.driver)
        }
        jvmTest.dependencies {
            implementation(libs.junit.jupiter)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
    }
}

android {
    namespace = "se.birdy.content"
}

sqldelight {
    databases {
        create("BirdyContent") {
            packageName.set("se.birdy.content.db")
            srcDirs.setFrom("src/commonMain/sqldelight")
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
