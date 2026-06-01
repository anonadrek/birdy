package se.birdy.app.ui.encyclopedia

/**
 * DP E — Arkivets ekologiska grupp-chips. `key` == content-grupp-id
 * (SpeciesSummary.group, materialiserad ur family_groups.yaml). ALL = inget filter.
 */
enum class ArchiveChip(
    val key: String,
) {
    ALL(""),
    SONGBIRDS("songbirds"),
    WATERFOWL("waterfowl"),
    WADERS("waders"),
    GULLS_TERNS("gulls_terns"),
    AUKS("auks"),
    SEABIRDS("seabirds"),
    GREBES_DIVERS("grebes_divers"),
    HERONS_STORKS("herons_storks"),
    RAPTORS("raptors"),
    OWLS("owls"),
    GAMEBIRDS("gamebirds"),
    DOVES("doves"),
    WOODPECKERS("woodpeckers"),
    CRANES_RAILS("cranes_rails"),
    OTHER("other"),
    ;

    /** Tom ALL = inget filter; annars matchar arten sin ekologiska grupp. */
    fun matches(group: String): Boolean = this == ALL || group == key
}
