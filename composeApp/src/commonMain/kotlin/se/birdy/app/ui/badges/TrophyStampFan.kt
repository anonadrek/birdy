package se.birdy.app.ui.badges

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.Brass

/** Test tag on each fanned seal, followed by `:<stamp number>`. */
internal const val TROPHY_FAN_SEAL_TAG = "trophy_fan_seal"

/** Test tag on the single dashed seal of the empty card. */
internal const val TROPHY_FAN_EMPTY_TAG = "trophy_fan_empty"

private const val MAX_FAN_STAMPS = 3

// The number on a seal is part of the drawing, sized to the seal (StampSeal sets it in sp), so at
// 2.0× text "№17" outgrew a 50dp seal and was cut off. It may grow a little, no further; the
// card's own text lines, which carry the same facts, scale fully.
private const val MAX_SEAL_FONT_SCALE = 1.3f

/**
 * The stamps the Troférum entry card fans out, in drawing order: the up to three most recently
 * earned ([NewestStampFirst], the same order as the rest of Märken), oldest first, so the newest
 * is drawn last (on top, in the largest slot). Sorts itself, so the card never depends on the
 * caller's order.
 */
internal fun trophyFanStamps(recent: List<BadgeWithUnlock>): List<BadgeWithUnlock> =
    recent.sortedWith(NewestStampFirst).take(MAX_FAN_STAMPS).reversed()

private class FanSlot(
    val x: Dp,
    val y: Dp,
    val size: Dp,
    val angle: Float,
)

// Design "Bild 2" (docs/superpowers/specs/assets/2026-10-06-1.3-val/birdy-uppslag-marken.html, B):
// seals of 44/44/52 px at left 0/34/66 and top 14/4/12, turned -12°/7°/-5°, scaled 1.15× to dp.
// StampSeal adds its own -3° tilt to an unlocked seal on top of these.
private val FanSlots =
    listOf(
        FanSlot(x = 0.dp, y = 16.dp, size = 50.dp, angle = -12f),
        FanSlot(x = 39.dp, y = 5.dp, size = 50.dp, angle = 7f),
        FanSlot(x = 76.dp, y = 14.dp, size = 60.dp, angle = -5f),
    )
private val FanHeight = 84.dp
private val EmptySealSize = 52.dp

/** How wide the fan of [count] stamps is ([TrophyStampFan]), or the empty card's waiting seal when 0. */
internal fun trophyFanWidth(count: Int): Dp {
    val slots = FanSlots.takeLast(count.coerceAtMost(MAX_FAN_STAMPS))
    val first = slots.firstOrNull() ?: return EmptySealSize
    val last = slots.last()
    return last.x + last.size - first.x
}

/**
 * Up to three earned stamps fanned like stamps on a desk ([stamps] in drawing order, see
 * [trophyFanStamps]; given more, only the last three, the newest, are drawn). Fewer than three
 * take the last slots, so the newest is always the large one on the right. Each seal keeps the ink
 * it has everywhere else: brass for Premium, rust otherwise. Decorative only: the card reads the
 * stamps out in its own description.
 */
@Composable
internal fun TrophyStampFan(
    stamps: List<BadgeWithUnlock>,
    modifier: Modifier = Modifier,
) {
    val shown = stamps.takeLast(MAX_FAN_STAMPS)
    val slots = FanSlots.takeLast(shown.size)
    val shift = slots.firstOrNull()?.x ?: 0.dp
    Box(modifier = modifier.size(width = trophyFanWidth(shown.size), height = FanHeight)) {
        SealTextScale {
            shown.zip(slots).forEach { (stamp, slot) ->
                Box(
                    modifier =
                        Modifier
                            .offset(x = slot.x - shift, y = slot.y)
                            .rotate(slot.angle)
                            .clearAndSetSemantics { testTag = "$TROPHY_FAN_SEAL_TAG:${stamp.stampNumber}" },
                ) {
                    StampSeal(
                        state = StampSealState.Unlocked(number = stamp.stampNumber, glyph = null, name = null),
                        size = slot.size,
                        accentColor = if (stamp.badge.isPremium) Brass else AccentCopper,
                    )
                }
            }
        }
    }
}

/** The empty card's single dashed seal: the first stamp, still to come. Decorative only. */
@Composable
internal fun TrophyStampWaiting(modifier: Modifier = Modifier) {
    Box(modifier = modifier.clearAndSetSemantics { testTag = TROPHY_FAN_EMPTY_TAG }) {
        SealTextScale { StampSeal(state = StampSealState.Locked(name = null), size = EmptySealSize) }
    }
}

/** Caps the text size inside the seals at [MAX_SEAL_FONT_SCALE]; see there. */
@Composable
private fun SealTextScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val capped = Density(density.density, density.fontScale.coerceAtMost(MAX_SEAL_FONT_SCALE))
    CompositionLocalProvider(LocalDensity provides capped, content = content)
}
