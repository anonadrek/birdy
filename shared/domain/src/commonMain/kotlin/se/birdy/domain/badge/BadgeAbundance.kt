package se.birdy.domain.badge

/**
 * Mirror av se.birdy.content.Abundance. shared/domain har medvetet
 * inget beroende på shared/content; mappning sker i composeApp/badges/
 * RecalculateBadgesUseCase vid evaluator-anropet.
 */
enum class BadgeAbundance {
    ALLMÄN,
    MINDRE_ALLMÄN,
    OVANLIG,
    SÄLLSYNT,
}
