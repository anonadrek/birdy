package se.birdy.app.ui.map

import platform.Foundation.NSBundle
import platform.Foundation.NSLog

/**
 * MapTiler-nyckeln på iOS läses ur app-bundlens Info.plist, dit xcodegen expanderar
 * den från gitignorerade iosApp/Local.xcconfig (spec-beslut 5). Saknad nyckel ⇒ tom
 * sträng ⇒ 403-tiles — samma tysta degrade som Androids BuildConfig-default, men loggad.
 */
internal object MapTilerKey {
    fun value(): String {
        val key = NSBundle.mainBundle.objectForInfoDictionaryKey("MAPTILER_API_KEY") as? String ?: ""
        if (key.isBlank()) {
            // Enda arg-formen (ingen "%@" + vararg): NSLog är deklarerad
            // `NSLog(format: String, vararg variadicArguments: Any?)` (klib-verifierat) — att
            // skicka en rå Kotlin String som vararg-elementet kraschar (EXC_BAD_ACCESS djupt inne
            // i CFStringCreateWithFormat/objc_opt_respondsToSelector när "%@" försöker meddela den
            // som ett ObjC-objekt; verifierat via sim-krasch — se task-4-rapporten, i4 T4). Den
            // enkla format-only-formen bridgar korrekt eftersom `format` INTE är ett vararg-element.
            NSLog("Birdy/map: MAPTILER_API_KEY saknas — kopiera iosApp/Local.xcconfig.sample till Local.xcconfig och fyll i nyckeln")
        }
        return key
    }
}
