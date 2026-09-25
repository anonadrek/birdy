package se.birdy.app.ui.premium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import se.birdy.app.data.premium.FormattedPrices
import se.birdy.app.data.premium.PurchaseResult
import se.birdy.domain.premium.PremiumRepository
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier

class PremiumViewModel(
    private val repository: PremiumRepository,
    private val launchPurchase: suspend (PremiumTier) -> PurchaseResult = {
        error("launchPurchase not wired — provide via AppGraph.launchPurchase or test stub")
    },
    private val formattedPricesFlow: StateFlow<FormattedPrices> = MutableStateFlow(FormattedPrices()),
) : ViewModel() {
    private val _state = MutableStateFlow(PremiumUiState(backendState = repository.state.value))
    val state: StateFlow<PremiumUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.state.collect { backend ->
                _state.update {
                    val justActivated = it.awaitingActivation && backend is PremiumState.Active
                    it.copy(
                        backendState = backend,
                        purchaseCompleted = it.purchaseCompleted || justActivated,
                        awaitingActivation = it.awaitingActivation && !justActivated,
                    )
                }
            }
        }
        viewModelScope.launch {
            formattedPricesFlow.collect { prices ->
                _state.update {
                    it.copy(
                        formattedYearlyPrice = prices.yearly,
                        formattedLifetimePrice = prices.lifetime,
                    )
                }
            }
        }
    }

    fun selectTier(tier: PremiumTier) {
        _state.update { it.copy(selectedTier = tier) }
    }

    fun purchase() {
        if (_state.value.purchaseInFlight) return
        _state.update {
            it.copy(purchaseInFlight = true, awaitingActivation = true, purchaseNotice = null)
        }
        viewModelScope.launch {
            // launchPurchase is expected to report failures as PurchaseResult.Error, not throw —
            // but a real exception here would otherwise crash the app, so any other Exception is
            // caught and turned into the same FAILED notice a reported error would produce.
            @Suppress("TooGenericExceptionCaught")
            try {
                val result = launchPurchase(_state.value.selectedTier)
                val notice =
                    when (result) {
                        PurchaseResult.Pending -> PurchaseNotice.PENDING
                        is PurchaseResult.Error -> {
                            println("PremiumViewModel: purchase failed: ${result.message}")
                            PurchaseNotice.FAILED
                        }
                        PurchaseResult.Success, PurchaseResult.UserCancelled -> null
                    }
                _state.update { it.copy(purchaseNotice = notice) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("PremiumViewModel: launchPurchase threw:\n${e.stackTraceToString()}")
                _state.update { it.copy(purchaseNotice = PurchaseNotice.FAILED) }
            } finally {
                _state.update { it.copy(purchaseInFlight = false) }
            }
        }
    }
}
