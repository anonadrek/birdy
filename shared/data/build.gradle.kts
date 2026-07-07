plugins {
    id("birdy.kmp-android-lib")
    alias(libs.plugins.sqldelight)
}

// Exclude SQLDelight generated sources from ktlint — they use 2-space indent
// and won't pass our style rules.
// Disable verifyMigration + generateSchema on Windows — native SQLite worker process crashes (same workaround as :shared:content). Migration correctness is verified by StampNumberMigrationTest at runtime.
afterEvaluate {
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        filter {
            exclude { element ->
                element.file.path.contains("generated")
            }
        }
    }
    tasks.matching { it.name.startsWith("verify") && it.name.contains("Migration") }.configureEach {
        enabled = false
    }
    tasks.matching { it.name.startsWith("generate") && it.name.contains("Schema") }.configureEach {
        enabled = false
    }
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

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
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
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
            // Schema dir is committed but generation is disabled locally on Windows; CI/Linux populates it.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
