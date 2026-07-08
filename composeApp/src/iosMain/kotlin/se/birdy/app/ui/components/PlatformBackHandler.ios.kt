package se.birdy.app.ui.components

import androidx.compose.runtime.Composable

/** iOS uses swipe-back/on-screen back buttons; no system back event to intercept. */
@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = Unit
