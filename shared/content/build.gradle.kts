plugins {
    id("birdy.kmp-android-lib")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

// Exclude SQLDelight generated sources from ktlint — they use 2-space indent
// and won't pass our style rules.
// Exclude verifyMigration from check — SQLite native library conflict in worker
// process on Windows (pre-existing, not caused by app code).
afterEvaluate {
    tasks.matching { it.name.startsWith("verify") && it.name.contains("Migration") }.configureEach {
        enabled = false
    }
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        filter {
            exclude { element ->
                element.file.path.contains("generated")
            }
        }
    }
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.kaml)
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            implementation(libs.junit.jupiter)
            implementation(libs.kotlinx.coroutines.test)
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

val speciesDir = file("species")
val imagesDir = file("images")
val expectedCountFile = file("expected-species-count.txt")
val overridesFile = file("overrides.yaml")

val validateSpeciesData by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validate committed species YAML against the schema."
    dependsOn("jvmJar")
    classpath =
        files(tasks.named("jvmJar")) +
        configurations.getByName("jvmRuntimeClasspath")
    mainClass.set("se.birdy.content.build.ValidateMain")
    args =
        listOf(
            speciesDir.absolutePath,
            imagesDir.absolutePath,
            expectedCountFile.absolutePath,
            overridesFile.absolutePath,
        )
    val speciesDirExists = speciesDir.exists()
    val expectedCountFileExists = expectedCountFile.exists()
    onlyIf("speciesDir and expectedCountFile exist") {
        speciesDirExists && expectedCountFileExists
    }
}

tasks.named("check") {
    dependsOn(validateSpeciesData)
}

val mlResourcesDir =
    project(":shared:ml").file("src/commonMain/composeResources/files/ml")
val mappingFile = mlResourcesDir.resolve("aiy_to_qid.json")
val metadataFile = mlResourcesDir.resolve("model_metadata.json")

val validateModelMapping by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validate aiy_to_qid.json against model_metadata.json (Plan 4b Task 10)."
    dependsOn("jvmJar")
    classpath =
        files(tasks.named("jvmJar")) +
        configurations.getByName("jvmRuntimeClasspath")
    mainClass.set("se.birdy.content.build.ValidateModelMappingMain")
    args = listOf(mappingFile.absolutePath, metadataFile.absolutePath)
    inputs.file(mappingFile)
    inputs.file(metadataFile)
    val bothExist = mappingFile.exists() && metadataFile.exists()
    onlyIf("aiy_to_qid.json + model_metadata.json exist") { bothExist }
}

tasks.named("check") {
    dependsOn(validateModelMapping)
}

val birdNetMappingFile =
    project(":shared:ml").file("src/commonMain/composeResources/files/ml/birdnet_lite_to_qid.json")
val birdNetSpeciesListYaml = rootProject.file("tools/content-pipeline/species_list.yaml")

val validateBirdNetMapping by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validate birdnet_lite_to_qid.json against species_list.yaml (Plan 6b2 Task T1)."
    dependsOn("jvmJar")
    classpath =
        files(tasks.named("jvmJar")) +
        configurations.getByName("jvmRuntimeClasspath")
    mainClass.set("se.birdy.content.build.ValidateBirdNetMappingMain")
    args = listOf(birdNetMappingFile.absolutePath, birdNetSpeciesListYaml.absolutePath)
    inputs.file(birdNetMappingFile)
    inputs.file(birdNetSpeciesListYaml)
    val bothExist = birdNetMappingFile.exists() && birdNetSpeciesListYaml.exists()
    onlyIf("birdnet_lite_to_qid.json + species_list.yaml exist") { bothExist }
}

tasks.named("check") {
    dependsOn(validateBirdNetMapping)
}

val badgesYamlFile =
    project(":composeApp").file("src/commonMain/composeResources/files/badges.yaml")
val badgeStringsSv =
    project(":composeApp").file("src/commonMain/composeResources/values/strings.xml")
val badgeStringsEn =
    project(":composeApp").file("src/commonMain/composeResources/values-en/strings.xml")

val validateBadgesYaml by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validate badges.yaml structure and rule-type payloads."
    dependsOn("jvmJar")
    classpath =
        files(tasks.named("jvmJar")) +
        configurations.getByName("jvmRuntimeClasspath")
    mainClass.set("se.birdy.content.build.ValidateBadgesYamlMain")
    args = listOf(badgesYamlFile.absolutePath)
    inputs.file(badgesYamlFile)
    val yamlExists = badgesYamlFile.exists()
    onlyIf("badges.yaml exists") { yamlExists }
}

val validateBadgeStrings by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validate badge_name_*/badge_desc_* keys exist in sv+en strings.xml."
    dependsOn("jvmJar")
    classpath =
        files(tasks.named("jvmJar")) +
        configurations.getByName("jvmRuntimeClasspath")
    mainClass.set("se.birdy.content.build.ValidateBadgeStringsMain")
    args =
        listOf(
            badgesYamlFile.absolutePath,
            badgeStringsSv.absolutePath,
            badgeStringsEn.absolutePath,
        )
    inputs.file(badgesYamlFile)
    inputs.file(badgeStringsSv)
    inputs.file(badgeStringsEn)
    val allExist = badgesYamlFile.exists() && badgeStringsSv.exists() && badgeStringsEn.exists()
    onlyIf("badges.yaml + sv + en strings.xml exist") { allExist }
}

tasks.named("check") {
    dependsOn(validateBadgesYaml)
    dependsOn(validateBadgeStrings)
}

val composeAppFilesDir =
    project(":composeApp").file("src/commonMain/composeResources/files")
val targetDb = composeAppFilesDir.resolve("species.db")
// Plan 6b3 T22b: species hero/secondary images live in the :asset-pack install-time
// pack (instead of bundled compose-resources) so the base APK stays under the
// Play Store 150 MB limit. species.db still lives in composeApp resources.
val targetImages =
    project(":asset-pack").file("src/main/assets/images")

val buildSpeciesDb by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Build species.db from committed YAML and copy images to composeApp assets."
    dependsOn(validateSpeciesData)
    dependsOn("jvmJar")
    classpath =
        files(tasks.named("jvmJar")) +
        configurations.getByName("jvmRuntimeClasspath")
    mainClass.set("se.birdy.content.build.BuildMain")
    args =
        listOf(
            speciesDir.absolutePath,
            imagesDir.absolutePath,
            targetDb.absolutePath,
            targetImages.absolutePath,
        )
    inputs.dir(speciesDir)
    inputs.dir(imagesDir)
    outputs.file(targetDb)
    outputs.dir(targetImages)
    val speciesDirExists = speciesDir.exists()
    onlyIf("speciesDir exists") {
        speciesDirExists
    }
}
