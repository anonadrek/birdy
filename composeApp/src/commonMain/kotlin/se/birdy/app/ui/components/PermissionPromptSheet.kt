package se.birdy.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.permission_prompt_cta_no
import birdy_bird_scanner.composeapp.generated.resources.permission_prompt_cta_yes
import birdy_bird_scanner.composeapp.generated.resources.permission_prompt_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.permission_prompt_headline
import birdy_bird_scanner.composeapp.generated.resources.permission_prompt_sub
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.PaperTop
import se.birdy.app.ui.theme.rememberCaveat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionPromptSheet(
    onTurnOn: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = PaperTop,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                stringResource(Res.string.permission_prompt_eyebrow),
                color = AccentCopper,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(8.dp))
            JournalHeadline(
                text = stringResource(Res.string.permission_prompt_headline),
                fontSize = 22.sp,
                plainColor = MarginaliaInk,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.permission_prompt_sub),
                color = MarginaliaInk.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontFamily = rememberCaveat(),
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.permission_prompt_cta_no), color = MarginaliaInk)
                }
                Button(
                    onClick = onTurnOn,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCopper, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(Res.string.permission_prompt_cta_yes))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
