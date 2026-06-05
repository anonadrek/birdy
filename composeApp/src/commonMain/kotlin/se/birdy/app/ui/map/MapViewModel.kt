package se.birdy.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import se.birdy.domain.observation.ObservationRepository

data class MapUiState(
    val pins: List<MapPin> = emptyList(),
    val locatedCount: Int = 0,
)

class MapViewModel(
    observationRepo: ObservationRepository,
) : ViewModel() {
    val state: StateFlow<MapUiState> =
        observationRepo
            .observeAll()
            .map { all ->
                val pins = MapPinMapper.toPins(all)
                MapUiState(pins = pins, locatedCount = pins.size)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())
}
