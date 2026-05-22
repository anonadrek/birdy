plugins {
    id("birdy.kmp-android-lib")
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
            implementation(libs.kotlinx.coroutines.test)
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
