package com.benmorrisrains.breakthebricks.platform

/**
 * Rewarded video ads: the player chooses to watch one for a reward (Ben, 2026-10-13: an ad
 * buys a one-game power-up). The real ad network SDK plugs in per platform behind this; common
 * code only ever asks for an ad and waits for the answer.
 */
interface RewardedAds {
    /**
     * Shows one rewarded ad. [onFinished] runs once, on the main thread, with true only when the
     * player watched far enough to earn the reward; false when no ad was ready, it failed, or
     * the player closed it early.
     */
    fun show(onFinished: (rewarded: Boolean) -> Unit)
}

/** Stand-in until an ad network is chosen: every "ad" finishes at once and pays out. */
object FakeRewardedAds : RewardedAds {
    override fun show(onFinished: (rewarded: Boolean) -> Unit) = onFinished(true)
}
