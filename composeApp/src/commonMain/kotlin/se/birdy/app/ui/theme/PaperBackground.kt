package se.birdy.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush

/**
 * Field Journal paper: a quiet vertical gradient PaperTop → PaperBottom. The 1.2 ink-dot
 * texture was removed in 1.3.0 (spec §4.1: clean paper, no dots).
 *
 * Use as root bg on every screen: `Box(Modifier.fillMaxSize().paperBackground()) { ... }`.
 */
fun Modifier.paperBackground(): Modifier = this.background(Brush.verticalGradient(listOf(PaperTop, PaperBottom)))
