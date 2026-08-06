package se.birdy.app.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object AppLocaleApplier {
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Publik för BirdyApplication (androidApp-modulen) — appen ska annars gå via [applyLocale]. */
    fun apply(tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+: LocaleManager persistar overriden + recreatar aktiviteter (bevisat fungerande väg).
            val ctx = appContext ?: return
            val lm = ctx.getSystemService(LocaleManager::class.java) ?: return
            lm.applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            // API 24–32: AppCompatDelegate fungerar nu eftersom MainActivity är AppCompatActivity —
            // recreatar aktiva AppCompat-aktiviteter och applicerar via deras konfiguration.
            // Locale.setDefault synkas explicit: AppCompat garanterar bara aktivitets-resurser,
            // men WorkManager-notisers getString + datumformat läser processens default-locale.
            val target =
                if (tag.isEmpty()) {
                    Resources.getSystem().configuration.locales[0]
                } else {
                    Locale.forLanguageTag(tag)
                }
            Locale.setDefault(target)
            AppCompatDelegate.setApplicationLocales(
                if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
            )
        }
    }
}

actual fun applyLocale(tag: String) = AppLocaleApplier.apply(tag)
