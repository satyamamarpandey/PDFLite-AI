package com.pdfliteai.billing

import android.app.Activity
import com.pdfliteai.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PremiumRepository(
    private val settingsRepo: SettingsRepository,
    private val billingManager: BillingManager,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(PremiumState())
    val state: StateFlow<PremiumState> = _state.asStateFlow()

    init {
        // 1) Keep local state in sync with DataStore cache
        scope.launch {
            settingsRepo.premiumStateFlow.collect { cached ->
                _state.value = cached
            }
        }

        // 2) Listen to BillingManager entitlement updates -> persist + enforce plan
        billingManager.setEntitlementListener { newState ->
            scope.launch {
                settingsRepo.setPremiumCache(newState)
                settingsRepo.enforcePlanRules(newState.isPremium)
            }
            _state.value = newState
        }
    }

    fun refresh() {
        billingManager.refreshEntitlement()
    }

    fun restore() {
        billingManager.restorePurchases()
    }

    fun purchase(activity: Activity, productId: String) {
        val candidates = listOf(
            "launchSubscriptionPurchase",
            "purchaseSubscription",
            "launchPurchase",
            "purchase",
            "startPurchase"
        )

        for (name in candidates) {
            val ok = runCatching {
                val m = billingManager::class.java.getMethod(
                    name,
                    Activity::class.java,
                    String::class.java
                )
                m.invoke(billingManager, activity, productId)
            }.isSuccess
            if (ok) return
        }

        throw IllegalStateException("No compatible purchase method found in BillingManager")
    }
}