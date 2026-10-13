package com.benmorrisrains.breakthebricks.platform

/**
 * The one in-app purchase: a one-time unlock (Ben, 2026-10-12: rewarded ads plus a one-time
 * purchase that removes ads). The store SDK (RevenueCat on Android and iOS) plugs in per
 * platform behind this; common code only asks and waits for the answer. Power-ups are never
 * sold (Ben, 2026-10-12), so this is the only thing the store offers.
 *
 * Every callback runs once, on the main thread.
 */
interface Purchases {
    /** Asks the store whether the player owns the unlock, and its price to show. */
    fun refresh(onResult: (StoreStatus) -> Unit)

    /** Starts the store's purchase flow for the unlock. */
    fun buyUnlock(onResult: (PurchaseResult) -> Unit)

    /** Asks the store for purchases made before, on this or another device. */
    fun restore(onResult: (PurchaseResult) -> Unit)
}

/**
 * What the store said: [owned] is null when it couldn't be reached, so the saved answer
 * stands. [price] is the store's own formatted price ("$2.99"), or null when unknown.
 */
data class StoreStatus(val owned: Boolean?, val price: String?)

enum class PurchaseResult {
    /** The player owns the unlock now. */
    Owned,

    /** Restore found nothing to restore. */
    NotOwned,

    /** The player backed out of the store's sheet. */
    Cancelled,

    /** The store failed or isn't set up. */
    Failed,
}

/**
 * Stand-in store for desktop, tests and debug builds without a RevenueCat key: buying and
 * restoring succeed at once. Ownership is saved in [store] under its own key, so restarting
 * the app finds it again like a real store would. [nextBuy] lets a test pick the outcome.
 */
class FakePurchases(
    private val store: KeyValueStore = InMemoryStore(),
    private val price: String = "$2.99",
) : Purchases {
    var nextBuy: PurchaseResult = PurchaseResult.Owned

    var owned: Boolean
        get() = store.getLong(OWNED_KEY, 0L) == 1L
        set(value) = store.putLong(OWNED_KEY, if (value) 1L else 0L)

    override fun refresh(onResult: (StoreStatus) -> Unit) = onResult(StoreStatus(owned, price))

    override fun buyUnlock(onResult: (PurchaseResult) -> Unit) {
        if (nextBuy == PurchaseResult.Owned) owned = true
        onResult(nextBuy)
    }

    override fun restore(onResult: (PurchaseResult) -> Unit) =
        onResult(if (owned) PurchaseResult.Owned else PurchaseResult.NotOwned)

    companion object {
        const val OWNED_KEY = "fake_store_unlock_owned"
    }
}

/** For a release build with no store key configured: nothing can be bought, and the game says so. */
object UnavailablePurchases : Purchases {
    override fun refresh(onResult: (StoreStatus) -> Unit) = onResult(StoreStatus(owned = null, price = null))
    override fun buyUnlock(onResult: (PurchaseResult) -> Unit) = onResult(PurchaseResult.Failed)
    override fun restore(onResult: (PurchaseResult) -> Unit) = onResult(PurchaseResult.Failed)
}

/**
 * The ad network and store IDs for one platform build, from its build config so no key lives
 * in the code. A blank [rewardedAdUnitId] uses Google's test ads. A blank [revenueCatApiKey]
 * uses [FakePurchases] in a [debug] build, so the paywall can be tried before the store is set
 * up, and [UnavailablePurchases] in a release build, so nothing is ever unlocked for free.
 */
data class MonetizationConfig(
    val revenueCatApiKey: String = "",
    val rewardedAdUnitId: String = "",
    val debug: Boolean = false,
) {
    /** The store to use, made by [real] from the API key once one is set. */
    fun purchases(store: KeyValueStore, real: (apiKey: String) -> Purchases): Purchases = when {
        revenueCatApiKey.isNotBlank() -> real(revenueCatApiKey)
        debug -> FakePurchases(store)
        else -> UnavailablePurchases
    }
}
