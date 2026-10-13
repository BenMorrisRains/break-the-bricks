package com.benmorrisrains.breakthebricks.platform

import com.revenuecat.purchases.kmp.PurchasesConfiguration
import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.Purchases as RevenueCat

/**
 * The one-time unlock through RevenueCat's Kotlin Multiplatform SDK, over StoreKit here and
 * Google Play Billing on Android. androidMain has the same file; keep the two alike.
 *
 * RevenueCat setup this expects: an entitlement with the id [ENTITLEMENT], a non-consumable
 * product attached to it, and that product as the lifetime package of the current offering.
 */
internal class RevenueCatPurchases(apiKey: String) : Purchases {
    init {
        if (!RevenueCat.isConfigured) RevenueCat.configure(PurchasesConfiguration(apiKey))
    }

    private val revenueCat get() = RevenueCat.sharedInstance

    override fun refresh(onResult: (StoreStatus) -> Unit) {
        revenueCat.getCustomerInfo(
            onError = { loadPrice { price -> onResult(StoreStatus(owned = null, price = price)) } },
            onSuccess = { info -> loadPrice { price -> onResult(StoreStatus(owned = info.ownsUnlock, price = price)) } },
        )
    }

    override fun buyUnlock(onResult: (PurchaseResult) -> Unit) {
        loadPackage { unlock ->
            if (unlock == null) return@loadPackage onResult(PurchaseResult.Failed)
            revenueCat.purchase(
                packageToPurchase = unlock,
                onError = { _, userCancelled ->
                    onResult(if (userCancelled) PurchaseResult.Cancelled else PurchaseResult.Failed)
                },
                onSuccess = { _, info ->
                    onResult(if (info.ownsUnlock) PurchaseResult.Owned else PurchaseResult.Failed)
                },
            )
        }
    }

    override fun restore(onResult: (PurchaseResult) -> Unit) {
        revenueCat.restorePurchases(
            onError = { onResult(PurchaseResult.Failed) },
            onSuccess = { info -> onResult(if (info.ownsUnlock) PurchaseResult.Owned else PurchaseResult.NotOwned) },
        )
    }

    private fun loadPrice(onResult: (String?) -> Unit) = loadPackage { onResult(it?.storeProduct?.price?.formatted) }

    /** The unlock's package in the current offering, or null when the store or the offering isn't set up. */
    private fun loadPackage(onResult: (Package?) -> Unit) {
        revenueCat.getOfferings(
            onError = { onResult(null) },
            onSuccess = { offerings ->
                val offering = offerings.current
                onResult(offering?.lifetime ?: offering?.availablePackages?.firstOrNull())
            },
        )
    }

    private val CustomerInfo.ownsUnlock: Boolean
        get() = entitlements[ENTITLEMENT]?.isActive == true

    companion object {
        /** The RevenueCat entitlement the one-time purchase grants. */
        const val ENTITLEMENT = "full_unlock"
    }
}
