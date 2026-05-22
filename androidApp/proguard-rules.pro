# TensorFlow Lite — kritisk för classifier-init i release-build.
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.nnapi.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-dontwarn org.tensorflow.lite.**

# kotlinx.serialization — kaml använder reflection mot @Serializable-klasser.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class se.birdy.**$$serializer { *; }
-keepclassmembers class se.birdy.** {
    *** Companion;
}
-keepclasseswithmembers class se.birdy.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# SQLDelight — runtime kräver klasser i app.cash.sqldelight.
-keep class app.cash.sqldelight.** { *; }
-keep class se.birdy.data.db.** { *; }

# Coil — image loader använder reflection för fetcher-discovery.
-keep class coil.** { *; }
-dontwarn coil.**

# AndroidX Lifecycle ViewModel — Compose-integration.
-keep class androidx.lifecycle.** { *; }

# CameraX.
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Compose Multiplatform resources — runtime resource-loading.
-keep class org.jetbrains.compose.resources.** { *; }

# Birdy specific — säkra alla ViewModels och datamodeller.
-keep class se.birdy.app.ui.**ViewModel { *; }
-keep class se.birdy.domain.** { *; }
-keep class se.birdy.content.** { *; }
-keep class se.birdy.ml.** { *; }

# Google Play Billing v8 — IPC + Proxy.newProxyInstance kräver reflection-säkra klasser.
-keep class com.android.billingclient.api.** { *; }
-keep class com.android.vending.billing.** { *; }
-dontwarn com.android.billingclient.api.**
