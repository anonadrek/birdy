package se.birdy.app

import androidx.compose.runtime.Composable
import se.birdy.app.ui.HomeScreen
import se.birdy.app.ui.theme.BirdyTheme

@Composable
fun App() {
    BirdyTheme {
        HomeScreen()
    }
}
