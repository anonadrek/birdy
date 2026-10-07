package se.birdy.app.ui.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.diary_detail_unknown_species
import birdy_bird_scanner.composeapp.generated.resources.recap_new_a11y_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_new_lifelist_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_new_tag
import birdy_bird_scanner.composeapp.generated.resources.recap_section_new
import birdy_bird_scanner.composeapp.generated.resources.recap_stamp_a11y_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_stamp_label
import birdy_bird_scanner.composeapp.generated.resources.recap_stamp_none
import birdy_bird_scanner.composeapp.generated.resources.recap_stamp_none_label
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.MicroLabel
import se.birdy.app.ui.components.SectionCard
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.components.hairlineBottom
import se.birdy.app.ui.dailybird.dailyBirdDateA11yLabel
import se.birdy.app.ui.dailybird.dailyBirdDateLabel
import se.birdy.app.ui.stats.FittedSpeciesName
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.Brass
import se.birdy.app.ui.theme.BrassText
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.rememberDmSerifDisplay

private val NewSpeciesPhotoSize = 42.dp
private val StampSize = 52.dp

/**
 * "Nya i livslistan" (release 1.3.0 Task 7j): each species first found this week, with its place in
 * the life list ("№ 31 i livslistan · lör 10 okt"), latest first. A row opens the find that put the
 * species on the list.
 */
@Composable
internal fun NewSpeciesSection(
    items: List<RecapNewSpeciesItem>,
    onObservationClick: (String) -> Unit,
) {
    MicroLabel(
        text = stringResource(Res.string.recap_section_new),
        modifier = Modifier.semantics(mergeDescendants = true) { heading() },
    )
    Spacer(Modifier.height(4.dp))
    items.forEachIndexed { index, item ->
        NewSpeciesRow(
            item = item,
            isLast = index == items.lastIndex,
            onClick = { onObservationClick(item.find.observationId) },
        )
    }
}

@Composable
private fun NewSpeciesRow(
    item: RecapNewSpeciesItem,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val name = item.find.speciesName ?: stringResource(Res.string.diary_detail_unknown_species)
    val description =
        stringResource(Res.string.recap_new_a11y_fmt, name, item.lifeListNumber, dailyBirdDateA11yLabel(item.find.date))
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (isLast) Modifier else Modifier.hairlineBottom())
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecapPhoto(find = item.find, shape = CircleShape, modifier = Modifier.size(NewSpeciesPhotoSize))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            FittedSpeciesName(
                text = name,
                style = TextStyle(fontFamily = rememberDmSerifDisplay(), color = TextOnCreme, lineHeight = 1.15.em),
                fontSize = 17.sp,
                // "Trädgårdssångare" beside the photo and the tag at 2.0x on a 320dp phone.
                minFontSize = 10.sp,
            )
            Text(
                text =
                    stringResource(
                        Res.string.recap_new_lifelist_fmt,
                        item.lifeListNumber,
                        dailyBirdDateLabel(item.find.date),
                    ),
                color = InkMuted,
                fontSize = 12.sp,
                lineHeight = 1.3.em,
            )
        }
        Spacer(Modifier.width(8.dp))
        NewTag()
    }
}

/** The small rust "NY" tag at the end of a new species row. */
@Composable
private fun NewTag() {
    Text(
        text = stringResource(Res.string.recap_new_tag).uppercase(),
        color = TextOnHero,
        fontSize = 9.sp,
        lineHeight = 1.2.em,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.14.em,
        maxLines = 1,
        softWrap = false,
        modifier =
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(AccentCopper)
                .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/**
 * The week's stamp (release 1.3.0 Task 7j): the real seal with its number, rust or brass as on
 * Märken, its name and what it was for. A week without a new stamp says so quietly with an open
 * dashed seal, so the card is always in the same place on the page.
 */
@Composable
internal fun StampSection(stamps: List<RecapStampItem>) {
    SectionCard {
        if (stamps.isEmpty()) {
            NoStampRow()
        } else {
            stamps.forEachIndexed { index, stamp ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                StampRow(stamp)
            }
        }
    }
}

@Composable
private fun StampRow(stamp: RecapStampItem) {
    val description = stringResource(Res.string.recap_stamp_a11y_fmt, stamp.name, stamp.stampNumber, stamp.description)
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The seal announces itself ("Månads-rytm, upplåst märke."); the row's sentence says it all once.
        Box(Modifier.clearAndSetSemantics {}) {
            StampSeal(
                state = StampSealState.Unlocked(number = stamp.stampNumber, glyph = null, name = null),
                size = StampSize,
                accentColor = if (stamp.isPremium) Brass else AccentCopper,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            MicroLabel(
                text = stringResource(Res.string.recap_stamp_label),
                showRule = false,
                color = if (stamp.isPremium) BrassText else AccentCopper,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stamp.name,
                fontFamily = rememberDmSerifDisplay(),
                fontSize = 17.sp,
                lineHeight = 1.15.em,
                color = TextOnCreme,
            )
            if (stamp.description.isNotBlank()) {
                Text(text = stamp.description, color = InkMuted, fontSize = 12.sp, lineHeight = 1.3.em)
            }
        }
    }
}

@Composable
private fun NoStampRow() {
    val text = stringResource(Res.string.recap_stamp_none)
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clearAndSetSemantics {}) {
            StampSeal(state = StampSealState.Locked(name = null), size = StampSize)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            MicroLabel(text = stringResource(Res.string.recap_stamp_none_label), showRule = false, color = InkMuted)
            Spacer(Modifier.height(2.dp))
            Text(text = text, color = InkMuted, fontSize = 13.sp, lineHeight = 1.3.em)
        }
    }
}
