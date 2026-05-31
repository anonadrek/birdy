package se.birdy.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.about_back
import birdy_bird_scanner.composeapp.generated.resources.about_credits_body
import birdy_bird_scanner.composeapp.generated.resources.about_credits_label
import birdy_bird_scanner.composeapp.generated.resources.about_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.about_headline_accent_1
import birdy_bird_scanner.composeapp.generated.resources.about_headline_plain_1
import birdy_bird_scanner.composeapp.generated.resources.about_headline_plain_2
import birdy_bird_scanner.composeapp.generated.resources.about_licenses_body
import birdy_bird_scanner.composeapp.generated.resources.about_licenses_label
import birdy_bird_scanner.composeapp.generated.resources.about_subline
import birdy_bird_scanner.composeapp.generated.resources.about_version_prefix
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BackButton
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.JournalScaffold
import se.birdy.app.ui.components.MicroLabel
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    version: String,
) {
    JournalScaffold(
        topBar = { AboutTopBar(onBack) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            val headline =
                "${stringResource(Res.string.about_headline_plain_1)} " +
                    "*${stringResource(Res.string.about_headline_accent_1)}* " +
                    stringResource(Res.string.about_headline_plain_2)
            JournalIntro(
                label = stringResource(Res.string.about_eyebrow),
                headline = headline,
                sub = stringResource(Res.string.about_subline),
                horizontalPadding = 0,
                topPadding = 8,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${stringResource(Res.string.about_version_prefix)} $version",
                fontFamily = rememberDmSerifDisplay(),
                fontStyle = FontStyle.Italic,
                fontSize = 18.sp,
                color = TextOnCreme,
            )
            Spacer(Modifier.height(24.dp))
            MicroLabel(stringResource(Res.string.about_credits_label))
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(Res.string.about_credits_body),
                fontFamily = rememberCaveat(),
                fontSize = 17.sp,
                color = MarginaliaInk,
            )
            Spacer(Modifier.height(20.dp))
            MicroLabel(stringResource(Res.string.about_licenses_label))
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(Res.string.about_licenses_body),
                fontFamily = rememberCaveat(),
                fontSize = 16.sp,
                color = MarginaliaInk,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun AboutTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(
            onClick = onBack,
            contentDescription = stringResource(Res.string.about_back),
        )
        Spacer(Modifier.size(8.dp))
    }
}
