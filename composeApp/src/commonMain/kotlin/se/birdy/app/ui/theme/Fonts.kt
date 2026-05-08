package se.birdy.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.caveat_bold
import birdy_bird_scanner.composeapp.generated.resources.caveat_regular
import birdy_bird_scanner.composeapp.generated.resources.dm_serif_display_italic
import birdy_bird_scanner.composeapp.generated.resources.dm_serif_display_regular
import org.jetbrains.compose.resources.Font

@Composable
fun rememberDmSerifDisplay(): FontFamily {
    val regular = Font(Res.font.dm_serif_display_regular, FontWeight.Normal, FontStyle.Normal)
    val italic = Font(Res.font.dm_serif_display_italic, FontWeight.Normal, FontStyle.Italic)
    return remember { FontFamily(regular, italic) }
}

@Composable
fun rememberCaveat(): FontFamily {
    val regular = Font(Res.font.caveat_regular, FontWeight.Normal, FontStyle.Normal)
    val bold = Font(Res.font.caveat_bold, FontWeight.Bold, FontStyle.Normal)
    return remember { FontFamily(regular, bold) }
}
