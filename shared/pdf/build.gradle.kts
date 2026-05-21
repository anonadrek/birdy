plugins {
    id("birdy.kmp-android-lib")
    alias(libs.plugins.kotlin.serialization)
}

tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    exclude { it.file.invariantSeparatorsPath.contains("/build/generated/") }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:domain"))
            implementation(project(":shared:content"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidUnitTest.dependencies {
            implementation("org.robolectric:robolectric:4.13")
            implementation("junit:junit:4.13.2")
        }
    }
}

android {
    namespace = "se.birdy.pdf"
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}
