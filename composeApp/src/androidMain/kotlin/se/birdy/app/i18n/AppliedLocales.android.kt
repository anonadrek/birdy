package se.birdy.app.i18n

import android.app.LocaleManager
import android.content.Context
import android.os.Build

/** Android's per-app language list as tags (API 33+), or null below 33; see [reconcileAppLanguage]. */
fun Context.appliedLocaleTags(): String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSystemService(LocaleManager::class.java)?.applicationLocales?.toLanguageTags()
    } else {
        null
    }
