package se.birdy.app.ui.onboarding.scenes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s7_cta
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s7_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s7_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s7_input_helper
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s7_input_placeholder
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s7_sub
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalHeadline
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.rememberCaveat

@Composable
fun SceneName(
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
            label = stringResource(Res.string.onboarding_s7_eyebrow),
            headline = stringResource(Res.string.onboarding_s7_headline),
            sub = stringResource(Res.string.onboarding_s7_sub),
            headlineFontSize = 36.sp,
        )
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            OutlinedTextField(
                value = nameInput,
                onValueChange = onNameChange,
                placeholder = {
                    Text(
                        stringResource(Res.string.onboarding_s7_input_placeholder),
                        fontFamily = caveat,
                        fontSize = 18.sp,
                        color = MarginaliaInk.copy(alpha = 0.6f),
                    )
                },
                textStyle =
                    TextStyle(
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
                text = stringResource(Res.string.onboarding_s7_input_helper),
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
                    text = stringResource(Res.string.onboarding_s7_cta),
                    fontSize = 20.sp,
                    plainColor = OffwhiteWarm,
                    accentColor = OffwhiteWarm,
                )
            }
        }
    }
}
