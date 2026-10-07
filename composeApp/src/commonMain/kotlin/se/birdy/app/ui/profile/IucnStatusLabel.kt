package se.birdy.app.ui.profile

import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.iucn_cr
import birdy_bird_scanner.composeapp.generated.resources.iucn_dd
import birdy_bird_scanner.composeapp.generated.resources.iucn_en
import birdy_bird_scanner.composeapp.generated.resources.iucn_ew
import birdy_bird_scanner.composeapp.generated.resources.iucn_ex
import birdy_bird_scanner.composeapp.generated.resources.iucn_lc
import birdy_bird_scanner.composeapp.generated.resources.iucn_ne
import birdy_bird_scanner.composeapp.generated.resources.iucn_nt
import birdy_bird_scanner.composeapp.generated.resources.iucn_vu
import org.jetbrains.compose.resources.StringResource

/**
 * The word for a global IUCN category ("Livskraftig", "Ej bedömd", "Utdöd", ...), or null for a
 * code the app does not know. Release 1.3.0 Task 7g added NE, EX and EW: the profile showed a bare
 * "NE" for the species IUCN has not assessed.
 */
internal fun iucnStatusLabel(code: String): StringResource? =
    when (code.uppercase()) {
        "LC" -> Res.string.iucn_lc
        "NT" -> Res.string.iucn_nt
        "VU" -> Res.string.iucn_vu
        "EN" -> Res.string.iucn_en
        "CR" -> Res.string.iucn_cr
        "DD" -> Res.string.iucn_dd
        "NE" -> Res.string.iucn_ne
        "EX" -> Res.string.iucn_ex
        "EW" -> Res.string.iucn_ew
        else -> null
    }
