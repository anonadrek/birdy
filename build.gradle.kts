plugins {
    // KMP + Android plugins are provided by buildSrc's classpath
    // (kotlin-gradle-plugin + android-tools-gradle), so no version specifier here —
    // declaring a version would conflict with the classpath-supplied "unknown version".
    id("org.jetbrains.kotlin.multiplatform") apply false
    id("com.android.application") apply false
    id("com.android.library") apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

allprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

    // Trap-katalogen "detekt analyserar INGEN KMP-modul": extensionen måste
    // konfigureras PER projekt (ett rot-scope:at detekt {}-block når inte
    // subprojekten, som då kör default-config mot default-källorna
    // src/main/{java,kotlin} — obefintliga i KMP-modulerna). Källistan är
    // explicit: produktions-source-sets; testkällor utelämnas medvetet
    // (paritet med detekts default-hållning). Baseline per modul fångar
    // backloggen från åren utan skanning — NY kod gate:as på riktigt.
    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
        source.setFrom(
            files(
                "src/main/kotlin",
                "src/main/java",
                "src/commonMain/kotlin",
                "src/androidMain/kotlin",
                "src/iosMain/kotlin",
                "src/jvmMain/kotlin",
            ),
        )
    }
}

subprojects {
    afterEvaluate {
        extensions.findByType<org.jlleitschuh.gradle.ktlint.KtlintExtension>()?.apply {
            version.set("1.4.1")
            android.set(true)
            ignoreFailures.set(false)
        }
    }
}
