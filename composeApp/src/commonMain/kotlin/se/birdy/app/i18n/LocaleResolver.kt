package se.birdy.app.i18n

import se.birdy.content.Locale

object LocaleResolver {
    fun resolve(
        override: String?,
        systemTag: String,
    ): Locale {
        val candidate = override ?: systemTag.substringBefore('-').lowercase()
        return when (candidate.substringBefore('-').lowercase()) {
            "sv", "se" -> Locale.SV
            "en" -> Locale.EN
            else -> Locale.SV
        }
    }
}
