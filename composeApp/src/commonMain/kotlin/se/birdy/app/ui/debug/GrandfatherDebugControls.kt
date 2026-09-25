package se.birdy.app.ui.debug

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * DEBUG-only QA controls for the release-1.3.0 grandfather-thanks flow (spec §5.2), grouped
 * into one parameter so [DiagnosticsScreen]'s own signature doesn't grow past detekt's
 * LongParameterList threshold every time another QA toggle is added.
 */
data class GrandfatherDebugControls(
    val forceGrandfathered: Flow<Boolean> = flowOf(false),
    val onSetForceGrandfathered: suspend (Boolean) -> Unit = {},
    val onResetGrandfatherThanks: suspend () -> Unit = {},
)
