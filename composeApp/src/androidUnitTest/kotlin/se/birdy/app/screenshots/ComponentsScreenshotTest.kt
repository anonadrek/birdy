package se.birdy.app.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.ui.components.MiniStamp
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.theme.Brass
import se.birdy.app.ui.theme.StampNavy
import se.birdy.app.ui.theme.paperBackground

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ComponentsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(qualifiers = "+sv")
    fun stamps_sv() =
        compose.captureScreen("components_stamps_sv") {
            Column(
                Modifier.fillMaxWidth().paperBackground().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StampSeal(StampSealState.Unlocked(number = 12, glyph = null, name = "Rödhake"), size = 72.dp)
                    StampSeal(
                        StampSealState.Unlocked(number = 5, glyph = null, name = "Premium"),
                        size = 72.dp,
                        accentColor = Brass,
                    )
                    StampSeal(
                        StampSealState.Unlocked(number = 3, glyph = null, name = "Lappmes"),
                        size = 72.dp,
                        accentColor = StampNavy,
                    )
                    StampSeal(StampSealState.InProgress(number = 7, name = "Streak", progressLabel = "2/3"), size = 72.dp)
                    StampSeal(StampSealState.Locked(name = "?"), size = 72.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniStamp(number = 12)
                    MiniStamp(number = 4, size = 28.dp)
                }
            }
        }
}
