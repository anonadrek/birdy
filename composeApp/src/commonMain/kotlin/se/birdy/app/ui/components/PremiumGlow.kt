package se.birdy.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Den kanoniska "det här är premium"-glöden för gateade ytor som en
 * gratisanvändare möter. ENDA platsen glöd-parametrarna bor — ändra här så slår
 * det igenom på alla gateade kort (PremiumTeaserCard, LockedStatsPreview,
 * MapPremiumTeaser-kortet, PremiumHeroCard). Bygger på [shimmerSweep] med ett
 * diskret, långsamt svep i linje med Troférummet.
 *
 * Måste appliceras INNANFÖR kortets .clip(...) så svepet maskas till de rundade
 * hörnen.
 */
@Composable
fun Modifier.premiumGlow(
    durationMillis: Int = 6000,
    alpha: Float = 0.18f,
): Modifier = this.shimmerSweep(durationMillis = durationMillis, alpha = alpha)
