package com.notifilter.billing

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.notifilter.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SubscriptionOfferInfo(
    val offerToken: String,
    val offerId: String?,
    val basePlanId: String,
    val formattedPrice: String,
    val billingPeriod: String,
    val trialPeriod: String?,
    val trialFormattedPrice: String?
)

class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    sealed class EntitlementState {
        data object Unknown : EntitlementState()
        data object Active : EntitlementState()
        data object Inactive : EntitlementState()
        data class Error(val message: String) : EntitlementState()
    }

    private val _entitlement = MutableStateFlow<EntitlementState>(EntitlementState.Unknown)
    val entitlement: StateFlow<EntitlementState> = _entitlement

    private val _subscriptionInfo = MutableStateFlow<SubscriptionOfferInfo?>(null)
    val subscriptionInfo: StateFlow<SubscriptionOfferInfo?> = _subscriptionInfo

    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null

    fun start() {
        if (BuildConfig.BILLING_BYPASS) {
            _entitlement.value = EntitlementState.Active
            EntitlementStore.setEntitled(context, true)
            return
        }
        if (BuildConfig.BILLING_PRODUCT_ID.isBlank()) {
            _entitlement.value = EntitlementState.Active
            EntitlementStore.setEntitled(context, true)
            return
        }
        if (billingClient != null) return

        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(pendingPurchasesParams)
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    refreshEntitlement()
                    queryProductDetails()
                } else {
                    _entitlement.value = EntitlementState.Error("Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // Will retry on next start/refresh
            }
        })
    }

    fun stop() {
        billingClient?.endConnection()
        billingClient = null
    }

    fun refreshEntitlement() {
        if (BuildConfig.BILLING_BYPASS) {
            _entitlement.value = EntitlementState.Active
            EntitlementStore.setEntitled(context, true)
            return
        }
        val client = billingClient ?: run {
            _entitlement.value = EntitlementState.Unknown
            return
        }

        val productId = BuildConfig.BILLING_PRODUCT_ID
        if (productId.isBlank()) {
            _entitlement.value = EntitlementState.Active
            EntitlementStore.setEntitled(context, true)
            return
        }

        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _entitlement.value = EntitlementState.Error("Query purchases failed: ${billingResult.debugMessage}")
                return@queryPurchasesAsync
            }

            val active = purchases.any { p ->
                p.products.contains(productId) &&
                    p.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            _entitlement.value = if (active) EntitlementState.Active else EntitlementState.Inactive
            EntitlementStore.setEntitled(context, active)
        }
    }

    private fun queryProductDetails() {
        val client = billingClient ?: return

        val productId = BuildConfig.BILLING_PRODUCT_ID
        if (productId.isBlank()) return

        val query = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        client.queryProductDetailsAsync(query) { billingResult, details ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val found = details.firstOrNull { it.productId == productId }
            productDetails = found
            _subscriptionInfo.value = found?.let { parseSubscriptionInfo(it) }
        }
    }

    private fun parseSubscriptionInfo(details: ProductDetails): SubscriptionOfferInfo? {
        val configuredBasePlanId = BuildConfig.BILLING_BASE_PLAN_ID
        val configuredOfferId = BuildConfig.BILLING_OFFER_ID

        val offer = details.subscriptionOfferDetails
            ?.firstOrNull { offer ->
                (configuredBasePlanId.isBlank() || offer.basePlanId == configuredBasePlanId) &&
                    (configuredOfferId.isBlank() || offer.offerId == configuredOfferId)
            }
            ?: details.subscriptionOfferDetails?.firstOrNull()
            ?: return null

        val phases = offer.pricingPhases.pricingPhaseList
        val trialPhase = phases.firstOrNull { it.priceAmountMicros == 0L }
        val pricePhase = phases.lastOrNull { it.priceAmountMicros > 0 } ?: phases.lastOrNull()

        return SubscriptionOfferInfo(
            offerToken = offer.offerToken,
            offerId = offer.offerId,
            basePlanId = offer.basePlanId,
            formattedPrice = pricePhase?.formattedPrice ?: "",
            billingPeriod = pricePhase?.billingPeriod ?: "",
            trialPeriod = trialPhase?.billingPeriod,
            trialFormattedPrice = trialPhase?.formattedPrice
        )
    }

    fun launchPurchaseFlow(activity: Activity) {
        val details = productDetails ?: run {
            queryProductDetails()
            return
        }

        val basePlanId = BuildConfig.BILLING_BASE_PLAN_ID
        val offerId = BuildConfig.BILLING_OFFER_ID

        val offerToken = details.subscriptionOfferDetails
            ?.firstOrNull { offer ->
                offer.basePlanId == basePlanId && (offerId.isBlank() || offer.offerId == offerId)
            }
            ?.offerToken
            ?: details.subscriptionOfferDetails
                ?.firstOrNull { it.basePlanId == basePlanId }
                ?.offerToken
            ?: details.subscriptionOfferDetails
                ?.firstOrNull()
                ?.offerToken
            ?: return

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        billingClient?.launchBillingFlow(activity, flowParams)
    }

    fun openManageSubscription(activity: Activity) {
        val pkg = context.packageName
        val sku = BuildConfig.BILLING_PRODUCT_ID
        val uri = Uri.parse("https://play.google.com/store/account/subscriptions?sku=$sku&package=$pkg")
        activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            // For subscriptions we typically don't need to acknowledge, but we refresh state.
            refreshEntitlement()
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            refreshEntitlement()
        } else {
            _entitlement.value = EntitlementState.Error("Purchase failed: ${billingResult.debugMessage}")
        }
    }
}
