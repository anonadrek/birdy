package se.birdy.domain.badge

data class BadgeCatalog(
    val version: Int,
    val badges: List<Badge>,
) {
    private val byId: Map<String, Badge> = badges.associateBy { it.id }

    fun findById(id: String): Badge? = byId[id]
}
