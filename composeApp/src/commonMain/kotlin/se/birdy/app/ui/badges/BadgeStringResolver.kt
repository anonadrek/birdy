package se.birdy.app.ui.badges

import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.StringResource
import se.birdy.app.i18n.AppStrings

/**
 * Slår upp en badge-sträng utanför komposition (notis-payloads, PDF-export).
 * [BadgeStringMap.nameFor]/[descriptionFor] kastar för badge-id:n utan strängar
 * (t.ex. framtida badges efter en katalog-bump) — fall då tillbaka på
 * [humanizeBadgeId] istället för att låta hela exporten/notisen misslyckas.
 * Strängen läses på appens språk ([strings]), inte telefonens.
 */
suspend fun resolveBadgeString(
    badgeId: String,
    strings: AppStrings,
    resourceFor: () -> StringResource,
): String =
    runCatching { strings.get(resourceFor()) }
        .onFailure { if (it is CancellationException) throw it }
        .getOrElse { humanizeBadgeId(badgeId) }

/** Läsbar nödfallstitel ur ett badge-id: "premium_year_lister" → "Year Lister". */
fun humanizeBadgeId(badgeId: String): String =
    badgeId
        .removePrefix("premium_")
        .split('_')
        .joinToString(" ") { part ->
            part.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
        }
