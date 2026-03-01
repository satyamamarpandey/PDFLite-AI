package com.pdfliteai.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*

class BillingManager(
    private val context: Context
) : PurchasesUpdatedListener {

    companion object {
        val SUB_IDS = listOf("pdflite_premium_monthly", "pdflite_premium_annual")
    }

    private var billingClient: BillingClient? = null
    private var onEntitlementChanged: ((PremiumState) -> Unit)? = null

    fun setEntitlementListener(listener: (PremiumState) -> Unit) {
        onEntitlementChanged = listener
    }

    fun startConnection(onConnected: (() -> Unit)? = null) {
        if (billingClient?.isReady == true) {
            onConnected?.invoke()
            return
        }

        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    onConnected?.invoke()
                } else {
                    onEntitlementChanged?.invoke(
                        PremiumState(
                            isPremium = false,
                            lastCheckedAt = System.currentTimeMillis(),
                            error = "Billing setup failed: ${result.debugMessage}"
                        )
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                // Billing will retry automatically next time you call startConnection or query.
            }
        })
    }

    /**
     * Call this on app start and on resume (refresh entitlement).
     */
    fun refreshEntitlement() {
        startConnection {
            queryActiveSubscriptions()
        }
    }

    /**
     * Restore purchases = same as refresh entitlement.
     */
    fun restorePurchases() {
        refreshEntitlement()
    }

    /**
     * Launch purchase flow for monthly or annual.
     */
    fun launchPurchase(activity: Activity, productId: String) {
        startConnection {
            queryProductDetailsAndLaunch(activity, productId)
        }
    }

    private fun queryProductDetailsAndLaunch(activity: Activity, productId: String) {
        val client = billingClient ?: return

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                onEntitlementChanged?.invoke(
                    PremiumState(
                        isPremium = false,
                        lastCheckedAt = System.currentTimeMillis(),
                        error = "ProductDetails failed: ${billingResult.debugMessage}"
                    )
                )
                return@queryProductDetailsAsync
            }

            val details = productDetailsList.firstOrNull()
            if (details == null) {
                onEntitlementChanged?.invoke(
                    PremiumState(
                        isPremium = false,
                        lastCheckedAt = System.currentTimeMillis(),
                        error = "Product not found: $productId"
                    )
                )
                return@queryProductDetailsAsync
            }

            // Choose the first available offer token (base plan / trial offer is handled by Play).
            val offerToken = details.subscriptionOfferDetails
                ?.firstOrNull()
                ?.offerToken

            if (offerToken.isNullOrBlank()) {
                onEntitlementChanged?.invoke(
                    PremiumState(
                        isPremium = false,
                        lastCheckedAt = System.currentTimeMillis(),
                        error = "No offer token available for $productId"
                    )
                )
                return@queryProductDetailsAsync
            }

            val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offerToken)
                .build()

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParams))
                .build()

            client.launchBillingFlow(activity, flowParams)
        }
    }

    private fun queryActiveSubscriptions() {
        val client = billingClient ?: return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        client.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                onEntitlementChanged?.invoke(
                    PremiumState(
                        isPremium = false,
                        lastCheckedAt = System.currentTimeMillis(),
                        error = "Query purchases failed: ${billingResult.debugMessage}"
                    )
                )
                return@queryPurchasesAsync
            }

            handlePurchases(purchases)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // ignore - user backed out
        } else {
            onEntitlementChanged?.invoke(
                PremiumState(
                    isPremium = false,
                    lastCheckedAt = System.currentTimeMillis(),
                    error = "Purchase update failed: ${result.debugMessage}"
                )
            )
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        // Premium if ANY active subscription purchase contains our product IDs
        val active = purchases.firstOrNull { p ->
            p.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    p.products.any { it in SUB_IDS }
        }

        if (active != null) {
            // Acknowledge if needed (required for Google Play)
            if (!active.isAcknowledged) {
                acknowledge(active)
            }

            val activeProduct = active.products.firstOrNull { it in SUB_IDS }

            onEntitlementChanged?.invoke(
                PremiumState(
                    isPremium = true,
                    activeProductId = activeProduct,
                    lastCheckedAt = System.currentTimeMillis(),
                    error = null
                )
            )
        } else {
            onEntitlementChanged?.invoke(
                PremiumState(
                    isPremium = false,
                    activeProductId = null,
                    lastCheckedAt = System.currentTimeMillis(),
                    error = null
                )
            )
        }
    }

    private fun acknowledge(purchase: Purchase) {
        val client = billingClient ?: return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        client.acknowledgePurchase(params) { /* result -> ignore; next refresh will pick it up */ }
    }

    fun endConnection() {
        billingClient?.endConnection()
        billingClient = null
    }
}