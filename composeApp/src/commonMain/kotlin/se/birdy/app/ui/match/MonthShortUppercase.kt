package se.birdy.app.ui.match

import androidx.compose.runtime.Composable
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.months_short_uppercase
import org.jetbrains.compose.resources.stringArrayResource

@Composable
internal fun monthShortUppercase(month: Int): String {
    val months = stringArrayResource(Res.array.months_short_uppercase)
    return months[(month - 1).coerceIn(0, 11)]
}
