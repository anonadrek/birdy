package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.HeroMossDeep
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.HeroMossMid
import se.birdy.app.ui.theme.MossCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.rememberDmSerifDisplay

/**
 * Full-bleed photo header (spec 2026-09-24 §4.3): photo, dark-moss scrim from the bottom,
 * kicker + serif title (+ optional apricot subtitle and a hairline meta row).
 *
 * [drawBehindStatusBar]: false (default) = the app's outer Scaffold already pads the status
 * bar, so the hero starts below it. true = only for routes that let it draw behind the
 * status bar (a later, device-verified task); then [topBar] gets statusBarsPadding().
 *
 * @param image caller-supplied photo (e.g. an AsyncImage with ContentScale.Crop and
 *   Modifier.fillMaxSize()). Null → moss gradient only.
 * @param bottomPadding extra space under the text, e.g. for a [PaperSheet] overlapping it.
 */
@Suppress("LongParameterList") // shared header for 5 screens (spec §4.3); the wide slot count is deliberate.
@Composable
fun PhotoHero(
    kicker: String,
    title: String,
    modifier: Modifier = Modifier,
    titleAccent: String? = null,
    subtitle: String? = null,
    metaStart: String? = null,
    metaEnd: String? = null,
    height: Dp = 300.dp,
    bottomPadding: Dp = 18.dp,
    drawBehindStatusBar: Boolean = false,
    image: (@Composable BoxScope.() -> Unit)? = null,
    topBar: (@Composable BoxScope.() -> Unit)? = null,
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val serif = rememberDmSerifDisplay()
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .background(Brush.verticalGradient(listOf(HeroMossLight, HeroMossMid, HeroMossDeep))),
    ) {
        image?.invoke(this)
        HeroScrim()
        topBar?.let { bar ->
            val barModifier = if (drawBehindStatusBar) Modifier.statusBarsPadding() else Modifier.padding(top = 8.dp)
            Box(Modifier.fillMaxWidth().then(barModifier)) { bar() }
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, bottom = bottomPadding),
        ) {
            MicroLabel(kicker, color = AccentCopperLight)
            Spacer(Modifier.height(8.dp))
            HeroTitle(title = title, titleAccent = titleAccent, serif = serif)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = AccentCopperLight,
                    fontFamily = serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (metaStart != null || metaEnd != null) {
                HeroMetaRow(metaStart = metaStart, metaEnd = metaEnd)
            }
            bottomContent?.invoke(this)
        }
    }
}

// Gradient stop fractions for the scrim (spec §4.3): dark enough at the bottom for
// TextOnHero to stay readable, almost clear near the top so the photo still reads as a photo.
private const val SCRIM_UPPER_MID_STOP = 0.2f
private const val SCRIM_LOWER_MID_STOP = 0.6f

@Composable
private fun HeroScrim() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.25f),
                SCRIM_UPPER_MID_STOP to HeroMossDeep.copy(alpha = 0.1f),
                SCRIM_LOWER_MID_STOP to HeroMossDeep.copy(alpha = 0.72f),
                1f to HeroMossDeep.copy(alpha = 0.97f),
            ),
        ),
    )
}

@Composable
private fun HeroTitle(
    title: String,
    titleAccent: String?,
    serif: FontFamily,
) {
    Text(
        text =
            buildAnnotatedString {
                append(title)
                if (titleAccent != null) {
                    append(" ")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = AccentCopperLight)) {
                        append(titleAccent)
                    }
                }
            },
        color = TextOnHero,
        fontFamily = serif,
        fontSize = if (titleAccent == null) 42.sp else 32.sp,
        lineHeight = if (titleAccent == null) 44.sp else 36.sp,
        letterSpacing = (-0.02).em,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun HeroMetaRow(
    metaStart: String?,
    metaEnd: String?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .drawBehind {
                    val line = TextOnHero.copy(alpha = 0.3f)
                    drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                }.padding(top = 8.dp),
    ) {
        MetaText(metaStart.orEmpty(), Modifier.weight(1f))
        MetaText(metaEnd.orEmpty())
    }
}

@Composable
private fun MetaText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        color = TextOnHero.copy(alpha = 0.75f),
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W600,
        fontSize = 9.5.sp,
        // Explicit, tight line height — otherwise this inherits the theme's bodyLarge 22sp
        // line height, making the hero's text block ~20dp taller than the mockup.
        lineHeight = 12.sp,
        letterSpacing = 0.12.em,
        modifier = modifier,
    )
}

/**
 * Paper sheet that slides up over a [PhotoHero] (24dp rounded top). Place it directly after
 * the hero; [overlap] pulls it up over the photo. Spec §4.3 "Arksida".
 */
@Composable
fun PaperSheet(
    modifier: Modifier = Modifier,
    overlap: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .offset(y = -overlap)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MossCreme)
                .padding(horizontal = 18.dp, vertical = 18.dp),
        content = content,
    )
}
