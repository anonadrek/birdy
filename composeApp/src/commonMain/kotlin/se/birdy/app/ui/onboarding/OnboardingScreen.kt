package se.birdy.app.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import se.birdy.app.ui.onboarding.scenes.SceneName
import se.birdy.app.ui.onboarding.scenes.ScenePhoto
import se.birdy.app.ui.onboarding.scenes.ScenePrivacy
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.paperBackground

private const val SCENE_COUNT = 7

@Composable
fun OnboardingScreen(
    state: OnboardingUiState.Visible,
    onPageChange: (Int) -> Unit,
    onNameChange: (String) -> Unit,
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
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val pageOffset =
                (pagerState.currentPage - page).toFloat() + pagerState.currentPageOffsetFraction
            when (page) {
                0 -> SceneHero(pageOffset = pageOffset)
                1 -> ScenePhoto(pageOffset = pageOffset, isActive = pagerState.currentPage == 1)
                2 -> SceneAudio(pageOffset = pageOffset, isActive = pagerState.currentPage == 2)
                3 -> SceneJournal(pageOffset = pageOffset, isActive = pagerState.currentPage == 3)
                4 -> SceneBadges(pageOffset = pageOffset, isActive = pagerState.currentPage == 4)
                5 -> ScenePrivacy(pageOffset = pageOffset, isActive = pagerState.currentPage == 5)
                6 ->
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
                    .padding(top = 12.dp, end = 12.dp),
        ) {
            Text(
                text =
                    stringResource(
                        if (isReplay) Res.string.onboarding_close_replay else Res.string.onboarding_skip,
                    ),
                color = MarginaliaInk.copy(alpha = 0.7f),
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
            )
        }

        PagerDots(
            currentPage = pagerState.currentPage,
            pageCount = SCENE_COUNT,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        bottom = if (pagerState.currentPage == SCENE_COUNT - 1) 148.dp else 28.dp,
                    ),
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
