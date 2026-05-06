package se.birdy.domain.badge

data class Badge(
    val id: String,
    val category: BadgeCategory,
    val rule: BadgeRule,
)
