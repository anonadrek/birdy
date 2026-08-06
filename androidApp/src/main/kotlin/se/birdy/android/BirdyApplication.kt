package se.birdy.android

import android.app.Application
import android.os.Build
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import se.birdy.app.i18n.toLocaleTagOrEmpty
import se.birdy.app.ui.settings.AppLocaleApplier
import se.birdy.datastore.UserPreferencesStore

/**
 * Återapplicerar det persistade app-språket före första aktiviteten.
 *
 * Endast API < 33: på 33+ persistar systemets LocaleManager overriden själv.
 * DataStore är enda sanningskällan (ingen appcompat autoStoreLocales-dubbellagring);
 * applicering här — innan någon aktivitet finns — undviker en extra recreate på kallstart.
 * runBlocking på en enda DataStore-läsning är ~ms (samma mönster som MainActivity.buildAppGraph).
 */
class BirdyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLocaleApplier.init(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val stored =
                runBlocking { UserPreferencesStore(this@BirdyApplication).preferences().appLanguage.first() }
            val tag = stored.toLocaleTagOrEmpty()
            if (tag.isNotEmpty()) {
                AppLocaleApplier.apply(tag)
            }
        }
    }
}
