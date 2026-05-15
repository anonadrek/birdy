package se.birdy.app.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_journal_p1_body1
import birdy_bird_scanner.composeapp.generated.resources.onboarding_journal_p1_body2
import birdy_bird_scanner.composeapp.generated.resources.onboarding_journal_p2_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_journal_p2_label
import birdy_bird_scanner.composeapp.generated.resources.onboarding_journal_p2_sub
import birdy_bird_scanner.composeapp.generated.resources.onboarding_journal_p3_cta
import birdy_bird_scanner.composeapp.generated.resources.onboarding_journal_p3_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_journal_p3_label
import birdy_bird_scanner.composeapp.generated.resources.onboarding_journal_p3_sub
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_archive_desc
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_archive_name
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_badges_desc
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_badges_name
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_lifelist_desc
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_lifelist_name
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_listen_desc
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_listen_name
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p3_input_helper
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p3_input_placeholder
import birdy_bird_scanner.composeapp.generated.resources.onboarding_skip
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BodyTextWithCaveatAccents
import se.birdy.app.ui.components.JournalHeadline
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.PlatformBackHandler
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.paperBackground
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

private const val PAGE_COUNT = 3

@Composable
fun OnboardingScreen(
    state: OnboardingUiState.Visible,
    onPageChange: (Int) -> Unit,
    onNameChange: (String) -> Unit,
    onComplete: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = state.pageIndex, pageCount = { PAGE_COUNT })

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onPageChange(it) }
    }
    LaunchedEffect(state.pageIndex) {
        if (pagerState.currentPage != state.pageIndex) {
            pagerState.animateScrollToPage(state.pageIndex)
        }
    }

    PlatformBackHandler(enabled = state.pageIndex > 0) {
        onPageChange(state.pageIndex - 1)
    }

    Box(modifier = Modifier.fillMaxSize().paperBackground().statusBarsPadding()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> Page1Brand()
                1 -> Page2Overview()
                2 ->
                    Page3Name(
                        nameInput = state.nameInput,
                        onNameChange = onNameChange,
                        onComplete = onComplete,
                    )
            }
        }

        TextButton(
            onClick = onComplete,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp),
        ) {
            Text(
                text = stringResource(Res.string.onboarding_skip),
                color = MarginaliaInk.copy(alpha = 0.7f),
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
            )
        }

        PagerDots(
            currentPage = pagerState.currentPage,
            pageCount = PAGE_COUNT,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (pagerState.currentPage == PAGE_COUNT - 1) 148.dp else 28.dp),
        )
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun Page1Brand() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        AsyncImage(
            model = Res.getUri("files/branding/wordmark.png"),
            contentDescription = "Birdy",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(32.dp))
        BodyTextWithCaveatAccents(
            text = stringResource(Res.string.onboarding_journal_p1_body1),
            modifier = Modifier.padding(horizontal = 24.dp),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        BodyTextWithCaveatAccents(
            text = stringResource(Res.string.onboarding_journal_p1_body2),
            modifier = Modifier.padding(horizontal = 24.dp),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun Page2Overview() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        JournalIntro(
            label = stringResource(Res.string.onboarding_journal_p2_label),
            headline = stringResource(Res.string.onboarding_journal_p2_headline),
            sub = stringResource(Res.string.onboarding_journal_p2_sub),
            headlineFontSize = 36.sp,
        )
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            FeatureRow(
                icon = Icons.Filled.Hearing,
                name = stringResource(Res.string.onboarding_p2_listen_name),
                description = stringResource(Res.string.onboarding_p2_listen_desc),
            )
            FeatureRow(
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                name = stringResource(Res.string.onboarding_p2_archive_name),
                description = stringResource(Res.string.onboarding_p2_archive_desc),
            )
            FeatureRow(
                icon = Icons.Outlined.CollectionsBookmark,
                name = stringResource(Res.string.onboarding_p2_lifelist_name),
                description = stringResource(Res.string.onboarding_p2_lifelist_desc),
            )
            FeatureRow(
                icon = Icons.Filled.Stars,
                name = stringResource(Res.string.onboarding_p2_badges_name),
                description = stringResource(Res.string.onboarding_p2_badges_desc),
            )
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    name: String,
    description: String,
) {
    val serif = rememberDmSerifDisplay()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentCopper.copy(alpha = 0.12f))
                    .border(
                        width = 1.dp,
                        color = AccentCopper.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(10.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentCopper,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = name,
                color = TextOnCreme,
                fontFamily = serif,
                fontStyle = FontStyle.Italic,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(2.dp))
            BodyTextWithCaveatAccents(
                text = description,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun Page3Name(
    nameInput: String,
    onNameChange: (String) -> Unit,
    onComplete: () -> Unit,
) {
    val caveat = rememberCaveat()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        JournalIntro(
            label = stringResource(Res.string.onboarding_journal_p3_label),
            headline = stringResource(Res.string.onboarding_journal_p3_headline),
            sub = stringResource(Res.string.onboarding_journal_p3_sub),
            headlineFontSize = 36.sp,
        )
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            OutlinedTextField(
                value = nameInput,
                onValueChange = onNameChange,
                placeholder = {
                    Text(
                        stringResource(Res.string.onboarding_p3_input_placeholder),
                        fontFamily = caveat,
                        fontSize = 18.sp,
                        color = MarginaliaInk.copy(alpha = 0.6f),
                    )
                },
                textStyle =
                    androidx.compose.ui.text.TextStyle(
                        fontFamily = caveat,
                        fontSize = 18.sp,
                        color = MarginaliaInk,
                    ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCopper.copy(alpha = 0.6f),
                        unfocusedBorderColor = AccentCopper.copy(alpha = 0.3f),
                        focusedContainerColor = Color.White.copy(alpha = 0.4f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.4f),
                    ),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.onboarding_p3_input_helper),
                color = MarginaliaInk.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = AccentCopper,
                        contentColor = OffwhiteWarm,
                    ),
                shape = RoundedCornerShape(12.dp),
            ) {
                JournalHeadline(
                    text = stringResource(Res.string.onboarding_journal_p3_cta),
                    fontSize = 20.sp,
                    plainColor = OffwhiteWarm,
                    accentColor = OffwhiteWarm,
                )
            }
        }
    }
}

@Composable
private fun PagerDots(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(pageCount) { i ->
            val width by animateDpAsState(if (i == currentPage) 22.dp else 8.dp, label = "dot-width")
            Box(
                modifier =
                    Modifier
                        .size(width = width, height = 8.dp)
                        .clip(CircleShape)
                        .background(if (i == currentPage) AccentCopper else MarginaliaInk.copy(alpha = 0.25f)),
            )
        }
    }
}
