package com.benmorrisrains.breakthebricks.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.benmorrisrains.breakthebricks.platform.KeyValueStore
import com.benmorrisrains.breakthebricks.platform.PurchaseResult
import com.benmorrisrains.breakthebricks.platform.Purchases

/**
 * The one-time unlock: whether the player owns it, its price, and buying and restoring it.
 * Ownership is saved in [store] so the game knows at once on launch, then [refresh] re-checks
 * it with the store (Ben, 2026-10-12: rewarded ads plus a one-time purchase that removes ads).
 *
 * The purchase removes ads, opens every paid level, and gives the ad rewards without the ad
 * (Ben, 2026-10-13). The game's only ads are opt-in rewarded ones, so removing them means
 * their rewards come free. Each perk reads its own property so a split is a one-line change.
 */
class FullUnlock(private val purchases: Purchases, private val store: KeyValueStore) {
    var owned: Boolean by mutableStateOf(store.getLong(OWNED_KEY, 0L) == 1L)
        private set

    /** The store's price, once it has answered; null shows a plain "UNLOCK" button. */
    var price: String? by mutableStateOf(null)
        private set

    /** True while the store's purchase or restore flow is running. */
    var busy: Boolean by mutableStateOf(false)
        private set

    /** One line on how the last buy or restore went, or null. */
    var notice: String? by mutableStateOf(null)
        private set

    /** Levels after the free ones are open to an owner. */
    val opensPaidLevels: Boolean get() = owned

    /** An owner gets whatever an ad would have paid out, without the ad. */
    val adRewardsFree: Boolean get() = owned

    /** Asks the store again; a refunded purchase is taken away, an unreachable store changes nothing. */
    fun refresh() {
        purchases.refresh { status ->
            status.owned?.let(::save)
            if (status.price != null) price = status.price
        }
    }

    /** Runs the store's purchase flow; [onDone] gets whether the player owns the unlock after it. */
    fun buy(onDone: (owned: Boolean) -> Unit = {}) {
        if (owned || busy) return onDone(owned)
        busy = true
        notice = null
        purchases.buyUnlock { result ->
            busy = false
            apply(result)
            onDone(owned)
        }
    }

    /** Asks the store for an earlier purchase; [onDone] gets whether the player owns the unlock after it. */
    fun restore(onDone: (owned: Boolean) -> Unit = {}) {
        if (busy) return onDone(owned)
        busy = true
        notice = null
        purchases.restore { result ->
            busy = false
            apply(result)
            onDone(owned)
        }
    }

    private fun apply(result: PurchaseResult) {
        notice = when (result) {
            PurchaseResult.Owned -> {
                save(true)
                UNLOCK_THANKS
            }
            PurchaseResult.NotOwned -> NOTHING_TO_RESTORE
            PurchaseResult.Cancelled -> null
            PurchaseResult.Failed -> STORE_FAILED
        }
    }

    private fun save(owned: Boolean) {
        this.owned = owned
        store.putLong(OWNED_KEY, if (owned) 1L else 0L)
    }

    companion object {
        const val OWNED_KEY = "full_unlock_owned"
        const val UNLOCK_THANKS = "UNLOCKED. THANK YOU!"
        const val NOTHING_TO_RESTORE = "NO PURCHASE TO RESTORE"
        const val STORE_FAILED = "STORE UNAVAILABLE. TRY AGAIN LATER."
    }
}

/** The buy button: "UNLOCK ALL  $2.99", or without a price until the store answers. */
fun unlockButtonText(price: String?): String = if (price == null) "UNLOCK ALL" else "UNLOCK ALL  $price"

/** Settings, once the unlock is owned. */
const val UNLOCK_OWNED = "FULL GAME UNLOCKED"

/** What the unlock gives, under the buy button. */
const val UNLOCK_TERMS = "EVERY LEVEL. NO ADS. AD REWARDS FREE."
