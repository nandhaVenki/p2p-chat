package com.example.p2pchat.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchasesUpdatedListener {

    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed

    private val _isLifetimeActive = MutableStateFlow(false)
    val isLifetimeActive: StateFlow<Boolean> = _isLifetimeActive

    private lateinit var billingClient: BillingClient
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        setupBillingClient()
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        connectToGooglePlay()
    }

    private fun connectToGooglePlay() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                connectToGooglePlay()
            }
        })
    }

    fun queryPurchases() {
        if (!billingClient.isReady) return

        // Query Subscriptions
        val subParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(subParams) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _isSubscribed.value = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.contains("premium_privacy_sub") }
                purchases.forEach { acknowledgePurchase(it) }
            }
        }

        // Query One-time Purchases (Lifetime)
        val inAppParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(inAppParams) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _isLifetimeActive.value = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.contains("lifetime_access_premium") }
                purchases.forEach { acknowledgePurchase(it) }
            }
        }
    }

    fun startPurchaseFlow(activity: Activity, productId: String, isSubscription: Boolean) {
        val productType = if (isSubscription) BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(productType)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()

        billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]
                val flowParamsBuilder = BillingFlowParams.newBuilder()
                
                if (isSubscription) {
                    val offerToken = productDetails.subscriptionOfferDetails?.get(0)?.offerToken ?: ""
                    flowParamsBuilder.setProductDetailsParamsList(listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .setOfferToken(offerToken)
                            .build()
                    ))
                } else {
                    flowParamsBuilder.setProductDetailsParamsList(listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .build()
                    ))
                }

                billingClient.launchBillingFlow(activity, flowParamsBuilder.build())
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { acknowledgePurchase(it) }
            queryPurchases()
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { _ -> queryPurchases() }
        }
    }
}
