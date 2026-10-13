package com.benmorrisrains.breakthebricks.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.benmorrisrains.breakthebricks.engine.FreeLevels
import com.benmorrisrains.breakthebricks.platform.KeyValueStore
import com.benmorrisrains.breakthebricks.platform.RewardedAds

/**
 * The paywall after the free levels (Ben, 2026-10-12: 10 free levels, then levels unlock by a
 * one-time purchase or by watching ads). A paid level opens with the [unlock], or for good by
 * watching one rewarded ad for that level (Claude's default).
 *
 * This sits on top of [LevelProgress.isUnlocked], which still asks for the level before to be
 * passed first: a level is playable when both allow it.
 */
class LevelAccess(
    private val store: KeyValueStore,
    private val unlock: FullUnlock,
    private val ads: RewardedAds,
) {
    /** True while the ad for a level is on screen. */
    var adShowing: Boolean by mutableStateOf(false)
        private set

    /** True when the last level ad didn't pay out (none ready, failed, or closed early). */
    var adMissed: Boolean by mutableStateOf(false)
        private set

    fun isPaid(number: Int): Boolean = number > FREE_LEVELS

    fun adUnlocked(number: Int): Boolean = store.getLong(adUnlockKey(number), 0L) == 1L

    /** True when level [number] needs the unlock or an ad before it can be played. */
    fun isPaywalled(number: Int): Boolean = isPaid(number) && !unlock.opensPaidLevels && !adUnlocked(number)

    /**
     * Shows a rewarded ad and, if the player watches it, opens level [number] for good. [onDone]
     * gets whether the level is open now. An owner of the unlock never sees the ad.
     */
    fun watchAdToUnlock(number: Int, onDone: (opened: Boolean) -> Unit = {}) {
        if (!isPaywalled(number)) return onDone(true)
        if (adShowing) return
        adShowing = true
        adMissed = false
        ads.show { rewarded ->
            adShowing = false
            adMissed = !rewarded
            if (rewarded) store.putLong(adUnlockKey(number), 1L)
            onDone(rewarded)
        }
    }

    /** Clears the last ad's miss, for a fresh look at the paywall. */
    fun clearAdMissed() {
        adMissed = false
    }

    companion object {
        /** Levels 1 to 10 are free (Ben, 2026-10-12). */
        const val FREE_LEVELS = FreeLevels.COUNT

        fun adUnlockKey(number: Int) = "level_${number}_ad_unlocked"
    }
}

/** The paywall's ad button: "WATCH AD: PLAY LEVEL 11". */
fun levelAdButtonText(number: Int): String = "WATCH AD: PLAY LEVEL $number"

/** Shown when the last level ad didn't pay out. */
const val LEVEL_AD_MISSED = "NO AD RIGHT NOW. TRY AGAIN SOON."
