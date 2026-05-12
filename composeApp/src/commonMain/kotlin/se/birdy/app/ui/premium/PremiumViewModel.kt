package se.birdy.app.ui.premium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import se.birdy.domain.premium.PremiumRepository
import se.birdy.domain.premium.PremiumTier

class PremiumViewModel(
    private val repository: PremiumRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PremiumUiState(backendState = repository.state.value))
    val state: StateFlow<PremiumUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.state.collect { backend ->
                _state.update { it.copy(backendState = backend) }
            }
        }
    }

    fun selectTier(tier: PremiumTier) {
        _state.update { it.copy(selectedTier = tier) }
    }

    fun purchase() {
        if (_state.value.purchaseInFlight) return
        _state.update { it.copy(purchaseInFlight = true) }
        viewModelScope.launch {
            try {
                repository.markPurchased(_state.value.selectedTier)
            } finally {
                _state.update { it.copy(purchaseInFlight = false) }
            }
        }
    }
}
