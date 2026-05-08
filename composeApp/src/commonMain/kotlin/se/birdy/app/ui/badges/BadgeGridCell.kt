package se.birdy.app.ui.badges

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState

@Composable
fun BadgeGridCell(
    progress: LockedBadgeProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nameRes = BadgeStringMap.nameFor(progress.badge.id)
    val name = stringResource(nameRes)
    val state = when (val s = progress.state) {
        is BadgeGridState.InProgress ->
            StampSealState.InProgress(
                number = progress.stampNumber,
                name = name,
                progressLabel = "${s.current}/${s.target}",
            )
        BadgeGridState.Hidden -> StampSealState.Locked(name = null)
        BadgeGridState.Locked -> StampSealState.Locked(name = name)
    }
    StampSeal(
        state = state,
        modifier = modifier,
        onClick = onClick,
    )
}
