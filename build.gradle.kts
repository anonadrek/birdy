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
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

allprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)
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

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}
