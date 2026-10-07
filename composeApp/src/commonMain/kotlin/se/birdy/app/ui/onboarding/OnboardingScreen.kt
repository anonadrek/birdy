package se.birdy.app.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_close_replay
import birdy_bird_scanner.composeapp.generated.resources.onboarding_skip
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.PlatformBackHandler
import se.birdy.app.ui.onboarding.scenes.SceneAudio
import se.birdy.app.ui.onboarding.scenes.SceneBadges
import se.birdy.app.ui.onboarding.scenes.SceneHero
import se.birdy.app.ui.onboarding.scenes.SceneJournal
import se.birdy.app.ui.onboarding.scenes.SceneLanguage
import se.birdy.app.ui.onboarding.scenes.SceneName
import se.birdy.app.ui.onboarding.scenes.ScenePhoto
import se.birdy.app.ui.onboarding.scenes.ScenePrivacy
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.paperBackground
import se.birdy.datastore.AppLanguage

private const val SCENE_COUNT = 8

/**
 * Höjden på det reserverade bandet längst ner där sid-prickarna bor. Pager-innehållet
 * får detta som bottenpadding så att de vertikalt centrerade scenerna aldrig sträcker
 * sig ner i prick-zonen och "smetas ihop" med indikatorn.
 */
private val DOTS_BAND_HEIGHT = 56.dp

@Composable
fun OnboardingScreen(
    state: OnboardingUiState.Visible,
    onPageChange: (Int) -> Unit,
    onNameChange: (String) -> Unit,
    onLanguageSelect: (AppLanguage) -> Unit,
    onComplete: () -> Unit,
    isReplay: Boolean = false,
) {
    val pagerState =
        rememberPagerState(initialPage = state.pageIndex, pageCount = { SCENE_COUNT })

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
        VerticalPager(
            state = pagerState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(bottom = DOTS_BAND_HEIGHT),
        ) { page ->
            val pageOffset =
                (pagerState.currentPage - page).toFloat() + pagerState.currentPageOffsetFraction
            when (page) {
                0 ->
                    SceneLanguage(
                        pageOffset = pageOffset,
                        selected = state.selectedLanguage,
                        onSelect = onLanguageSelect,
                    )
                1 -> SceneHero(pageOffset = pageOffset)
                2 -> ScenePhoto(pageOffset = pageOffset, isActive = pagerState.currentPage == 2)
                3 -> SceneAudio(pageOffset = pageOffset, isActive = pagerState.currentPage == 3)
                4 -> SceneJournal(pageOffset = pageOffset, isActive = pagerState.currentPage == 4)
                5 -> SceneBadges(pageOffset = pageOffset, isActive = pagerState.currentPage == 5)
                6 -> ScenePrivacy(pageOffset = pageOffset, isActive = pagerState.currentPage == 6)
                7 ->
                    SceneName(
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
                    .padding(top = 12.dp, end = 12.dp)
                    // A full touch target: on the replay this is the way out (Task 7b).
                    .heightIn(min = 48.dp),
        ) {
            Text(
                text =
                    stringResource(
                        if (isReplay) Res.string.onboarding_close_replay else Res.string.onboarding_skip,
                    ),
                // The first run keeps its quiet "Hoppa över"; on the replay "Stäng" is the only way
                // back besides the gesture, so it gets full contrast and normal size (release 1.3.0
                // Task 7b: it was 13sp at 70 % alpha).
                color = if (isReplay) MarginaliaInk else MarginaliaInk.copy(alpha = 0.7f),
                fontStyle = FontStyle.Italic,
                fontWeight = if (isReplay) FontWeight.W600 else null,
                fontSize = if (isReplay) 16.sp else 13.sp,
            )
        }

        PagerDots(
            currentPage = pagerState.currentPage,
            pageCount = SCENE_COUNT,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 4.dp),
        )
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(pageCount) { i ->
            val width by animateDpAsState(if (i == currentPage) 18.dp else 6.dp, label = "dot-width")
            Box(
                modifier =
                    Modifier
                        .size(width = width, height = 6.dp)
                        .clip(CircleShape)
                        .background(if (i == currentPage) AccentCopper else MarginaliaInk.copy(alpha = 0.25f)),
            )
        }
    }
}
