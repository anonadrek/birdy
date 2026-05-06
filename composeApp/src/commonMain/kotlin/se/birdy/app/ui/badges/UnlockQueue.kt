package se.birdy.app.ui.badges

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import se.birdy.domain.badge.BadgeUnlock

class UnlockQueue {
    private val _queue = MutableStateFlow<List<BadgeUnlock>>(emptyList())
    val queue = _queue.asStateFlow()

    val current: Flow<BadgeUnlock?> = _queue.map { it.firstOrNull() }
    val size: Flow<Int> = _queue.map { it.size }

    fun enqueue(unlocks: List<BadgeUnlock>) {
        if (unlocks.isEmpty()) return
        _queue.update { it + unlocks }
    }

    fun pop() {
        _queue.update { if (it.isEmpty()) it else it.drop(1) }
    }
}
