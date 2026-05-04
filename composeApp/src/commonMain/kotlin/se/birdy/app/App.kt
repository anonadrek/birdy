package se.birdy.app

import androidx.compose.runtime.Composable
import se.birdy.app.di.AppGraph
import se.birdy.app.ui.scaffold.AppScaffold
import se.birdy.app.ui.theme.BirdyTheme

@Composable
fun App(graph: AppGraph) {
    BirdyTheme {
        AppScaffold(graph)
    }
}
