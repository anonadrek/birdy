package se.birdy.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Den kanoniska "det här är premium"-glöden för gateade ytor som en
 * gratisanvändare möter. ENDA platsen glöd-parametrarna bor — ändra här så slår
 * det igenom på alla gateade kort (PremiumTeaserCard, LockedStatsPreview,
 * MapPremiumTeaser-kortet, PremiumHeroCard).
 *
 * Ett skarpt, smalt ljusband som sveper diagonalt. Till skillnad från
 * [shimmerSweep] ritas bandet BAKOM innehållet ([drawRect] före [drawContent])
 * så texten förblir knivskarp medan glöden glänser bakom den.
 *
 * Lager-kontrakt: applicera EFTER kortets `.clip(...)` (så bandet maskas till de
 * rundade hörnen) och EFTER den ogenomskinliga fyllningen (`.background(...)`),
 * men så att texten/innehållet ritas av `drawContent()` ovanpå. På ytor där
 * fyllningen är en bild eller ett inre lager: lägg glöden på ett eget lager
 * mellan fyllningen och texten (se PremiumHeroCard / LockedStatsPreview).
 */
@Composable
fun Modifier.premiumGlow(
    durationMillis: Int = 3500,
    alpha: Float = 0.85f,
    bandFraction: Float = 0.18f,
): Modifier =
    composed {
        val transition = rememberInfiniteTransition(label = "premiumGlow")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = durationMillis, easing = LinearEasing),
                    // Studsar fram och tillbaka (v→h, sen h→v) i stället för att börja om från vänster.
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "premiumGlowProgress",
        )
        drawWithCache {
            val travel = size.width * 1.8f
            val origin = -size.width * 0.4f
            val bandWidth = size.width * bandFraction
            val x = origin + travel * progress
            val brush =
                Brush.linearGradient(
                    colorStops =
                        arrayOf(
                            0.0f to Color.White.copy(alpha = 0f),
                            0.5f to Color.White.copy(alpha = alpha),
                            1.0f to Color.White.copy(alpha = 0f),
                        ),
                    start = Offset(x, 0f),
                    end = Offset(x + bandWidth, size.height),
                )
            onDrawWithContent {
                drawRect(brush = brush)
                drawContent()
            }
        }
    }
