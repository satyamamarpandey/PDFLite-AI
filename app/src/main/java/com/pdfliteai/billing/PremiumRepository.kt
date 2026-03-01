package com.pdfliteai.billing

import com.pdfliteai.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PremiumRepository(
    private val prefs: Prefs,
    private val billingManager: BillingManager
) {
    private val _state = MutableStateFlow(
        PremiumState(
            isPremium = prefs.getPremiumCached(),
            activeProductId = prefs.getPremiumProductId(),
            lastCheckedAt = prefs.getPremiumLastChecked()
        )
    )
    val state: StateFlow<PremiumState> = _state.asStateFlow()

    init {
        billingManager.setEntitlementListener { newState ->
            // Cache to prefs
            prefs.setPremiumCached(newState.isPremium)
            prefs.setPremiumProductId(newState.activeProductId)
            prefs.setPremiumLastChecked(newState.lastCheckedAt)

            _state.value = newState
        }
    }

    fun refresh() {
        billingManager.refreshEntitlement()
    }

    fun restore() {
        billingManager.restorePurchases()
    }
}