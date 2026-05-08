package se.birdy.app.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p1_body
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p1_breadcrumb
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p1_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p1_sub
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_archive_desc
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_archive_name
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_badges_desc
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_badges_name
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_breadcrumb
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_lifelist_desc
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_lifelist_name
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_listen_desc
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p2_listen_name
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p3_breadcrumb
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p3_cta
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p3_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p3_input_helper
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p3_input_placeholder
import birdy_bird_scanner.composeapp.generated.resources.onboarding_skip
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.HeroMossDeep
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.HeroMossMid
import se.birdy.app.ui.theme.ItalicMixedText
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.SandCreme

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

    val gradient = remember { Brush.verticalGradient(listOf(HeroMossLight, HeroMossMid, HeroMossDeep)) }

    Box(modifier = Modifier.fillMaxSize().background(gradient)) {
        // Skip-link top-right (alla sidor utom sista — eller även där, hoppa = complete med tomt namn)
        TextButton(
            onClick = {
                if (state.pageIndex == PAGE_COUNT - 1) {
                    onComplete()
                } else {
                    onPageChange(PAGE_COUNT - 1)
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp),
        ) {
            Text(
                text = stringResource(Res.string.onboarding_skip),
                color = OffwhiteWarm.copy(alpha = 0.65f),
                fontSize = 13.sp,
            )
        }

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

        // Pager dots
        PagerDots(
            currentPage = pagerState.currentPage,
            pageCount = PAGE_COUNT,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (pagerState.currentPage == PAGE_COUNT - 1) 148.dp else 28.dp),
        )
    }
}

@Composable
private fun Page1Brand() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = stringResource(Res.string.onboarding_p1_breadcrumb),
            color = AccentCopperLight,
            fontSize = 12.sp,
            letterSpacing = 4.8.sp,
            fontWeight = FontWeight.W600,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.onboarding_p1_headline),
            color = AccentCopperLight,
            fontStyle = FontStyle.Italic,
            fontSize = 68.sp,
            fontWeight = FontWeight.W700,
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(Modifier.height(28.dp))
        ItalicMixedText(
            text = stringResource(Res.string.onboarding_p1_body),
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    color = OffwhiteWarm,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W600,
                ),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.onboarding_p1_sub),
            color = OffwhiteWarm.copy(alpha = 0.75f),
            fontSize = 16.sp,
            fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
private fun Page2Overview() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.onboarding_p2_breadcrumb),
            color = AccentCopperLight,
            fontSize = 12.sp,
            letterSpacing = 4.8.sp,
            fontWeight = FontWeight.W600,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(16.dp))
        ItalicMixedText(
            text = stringResource(Res.string.onboarding_p2_headline),
            style =
                MaterialTheme.typography.displaySmall.copy(
                    color = OffwhiteWarm,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.W700,
                ),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        FeatureRow(
            name = stringResource(Res.string.onboarding_p2_listen_name),
            description = stringResource(Res.string.onboarding_p2_listen_desc),
        )
        FeatureRow(
            name = stringResource(Res.string.onboarding_p2_archive_name),
            description = stringResource(Res.string.onboarding_p2_archive_desc),
        )
        FeatureRow(
            name = stringResource(Res.string.onboarding_p2_lifelist_name),
            description = stringResource(Res.string.onboarding_p2_lifelist_desc),
        )
        FeatureRow(
            name = stringResource(Res.string.onboarding_p2_badges_name),
            description = stringResource(Res.string.onboarding_p2_badges_desc),
        )
    }
}

@Composable
private fun FeatureRow(
    name: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentCopper.copy(alpha = 0.18f)),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = name,
                color = OffwhiteWarm,
                fontWeight = FontWeight.W700,
                fontSize = 19.sp,
            )
            Spacer(Modifier.height(2.dp))
            ItalicMixedText(
                text = description,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        color = OffwhiteWarm.copy(alpha = 0.78f),
                        fontSize = 14.sp,
                    ),
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
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Res.string.onboarding_p3_breadcrumb),
            color = AccentCopperLight,
            fontSize = 12.sp,
            letterSpacing = 4.8.sp,
            fontWeight = FontWeight.W600,
        )
        Spacer(Modifier.height(16.dp))
        ItalicMixedText(
            text = stringResource(Res.string.onboarding_p3_headline),
            style =
                MaterialTheme.typography.displaySmall.copy(
                    color = OffwhiteWarm,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.W700,
                ),
        )
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = nameInput,
            onValueChange = onNameChange,
            placeholder = { Text(stringResource(Res.string.onboarding_p3_input_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = SandCreme,
                    unfocusedContainerColor = SandCreme,
                ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.onboarding_p3_input_helper),
            color = OffwhiteWarm.copy(alpha = 0.6f),
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
        ) {
            Text(
                text = stringResource(Res.string.onboarding_p3_cta),
                fontWeight = FontWeight.W600,
                fontSize = 17.sp,
            )
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
                        .background(if (i == currentPage) AccentCopperLight else OffwhiteWarm.copy(alpha = 0.3f)),
            )
        }
    }
}
