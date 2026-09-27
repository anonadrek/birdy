package se.birdy.app.util

// `iucn_status` in species.db is the GLOBAL IUCN red list, not a national one — any KDoc or UI
// label that names it should say "global rödlista (IUCN)" / "global red list (IUCN)".
private val RED_LISTED_STATUSES = setOf("NT", "VU", "EN", "CR")

/**
 * Whether an IUCN status counts as "red-listed" for badges and UI tags. Shared between
 * [se.birdy.app.badges.RecalculateBadgesUseCase] (the `ObservedRedListed` badge rule) and
 * the Archive screen's red-listed tag, so both agree on exactly which statuses count.
 *
 * T10b fix: `"EN"` (Endangered) was missing from the badge rule's own local status set — a
 * pre-existing bug (not a T10b regression). Extracting one shared predicate closes that gap for
 * both consumers at once instead of fixing it in one place and leaving the other stale.
 */
fun isRedListed(iucnStatus: String?): Boolean = iucnStatus != null && iucnStatus in RED_LISTED_STATUSES
