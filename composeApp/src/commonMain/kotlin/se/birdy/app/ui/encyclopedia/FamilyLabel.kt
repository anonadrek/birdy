package se.birdy.app.ui.encyclopedia

import se.birdy.content.Locale

/**
 * Familjens visningsetikett.
 *
 * Trivialnamn för familjer finns bara på svenska i innehållet (taxonomy.family_sv);
 * engelska saknas helt. Visa därför det svenska trivialnamnet i SV-locale och det
 * vetenskapliga (latinska) familjenamnet i EN-locale, så svenska aldrig läcker in i
 * det engelska gränssnittet (t.ex. "Mesar"/"Storkar"/"Hökar" i en EN-build).
 *
 * Faller tillbaka till det latinska namnet om family_sv saknas.
 */
fun localizedFamilyLabel(
    locale: Locale,
    family: String,
    familySv: String?,
): String = if (locale == Locale.SV && !familySv.isNullOrBlank()) familySv else family
