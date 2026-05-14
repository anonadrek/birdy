package se.birdy.app.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object AppLocaleApplier {
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    internal fun apply(tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+: LocaleManager triggers Activity recreate + persists the override.
            // AppCompatDelegate.setApplicationLocales requires an AppCompatActivity to be
            // registered in its delegate list; MainActivity extends ComponentActivity so
            // that path is a silent no-op for us.
            val ctx = appContext ?: return
            val lm = ctx.getSystemService(LocaleManager::class.java) ?: return
            lm.applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            // API 24–32: DataStore write (caller) is the source of truth; LocaleResolver
            // applies it on next cold start. AppCompatDelegate call kept for activities that
            // happen to be AppCompat-based; otherwise no-op until relaunch.
            val locales =
                if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}

actual fun applyLocale(tag: String) = AppLocaleApplier.apply(tag)
