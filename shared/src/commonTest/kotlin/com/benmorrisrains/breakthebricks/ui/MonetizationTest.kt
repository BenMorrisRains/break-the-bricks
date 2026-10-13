package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.Abilities
import com.benmorrisrains.breakthebricks.engine.BigBall
import com.benmorrisrains.breakthebricks.engine.BrickGame
import com.benmorrisrains.breakthebricks.engine.FreeLevels
import com.benmorrisrains.breakthebricks.engine.GridPhysics
import com.benmorrisrains.breakthebricks.engine.PaidLevels
import com.benmorrisrains.breakthebricks.platform.FakePurchases
import com.benmorrisrains.breakthebricks.platform.GameSettings
import com.benmorrisrains.breakthebricks.platform.InMemoryStore
import com.benmorrisrains.breakthebricks.platform.PlatformServices
import com.benmorrisrains.breakthebricks.platform.PurchaseResult
import com.benmorrisrains.breakthebricks.platform.Purchases
import com.benmorrisrains.breakthebricks.platform.RewardedAds
import com.benmorrisrains.breakthebricks.platform.StoreStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The paywall after level 10, the one-time unlock and the per-level ad (T12). */
class MonetizationTest {
    /** An ad the test finishes by hand. */
    private class ManualAds : RewardedAds {
        var pending: ((Boolean) -> Unit)? = null
        var shown = 0
        override fun show(onFinished: (rewarded: Boolean) -> Unit) {
            shown++
            pending = onFinished
        }

        fun finish(rewarded: Boolean) {
            val onFinished = pending!!
            pending = null
            onFinished(rewarded)
        }
    }

    private val store = InMemoryStore()
    private val ads = ManualAds()
    private val purchases = FakePurchases(InMemoryStore())

    private fun shell(services: PlatformServices = PlatformServices(store = store, ads = ads, purchases = purchases)) =
        GameShell(
            session = GameSession(
                services,
                newSeed = { 3L },
                newEngine = { seed, _, abilities -> BrickGame(seed = seed, physics = GridPhysics, abilities = abilities) },
            ),
            settings = GameSettings(services),
        )

    /** The first paid level. */
    private val level11 = PaidLevels[11]

    private fun passLevel10() = store.putLong(LevelProgress.starsKey(LevelAccess.FREE_LEVELS), 1L)

    @Test
    fun theFreeLevelsAreNeverPaywalled() {
        val access = shell().session.access
        for (level in FreeLevels.all) assertFalse(access.isPaywalled(level.number), "level ${level.number}")
        assertTrue(access.isPaywalled(11))
        assertTrue(access.isPaywalled(30))
    }

    @Test
    fun aPaidLevelOpensThePaywallInsteadOfTheGame() {
        passLevel10()
        val shell = shell()
        shell.playLevel(level11)
        assertEquals(Screen.Paywall(level11), shell.screen)
    }

    @Test
    fun aPaidLevelStillNeedsTheLevelBeforeItPassed() {
        val shell = shell()
        shell.playLevel(level11)
        assertEquals(Screen.Home, shell.screen, "level 10 isn't passed, so nothing happens")
    }

    @Test
    fun buyingTheUnlockStartsTheLevelAndOpensEveryPaidLevel() {
        passLevel10()
        val shell = shell()
        shell.playLevel(level11)
        shell.buyUnlock()
        assertEquals(Screen.Game, shell.screen)
        assertEquals(level11, shell.session.level)
        assertTrue(shell.session.unlock.owned)
        assertFalse(shell.session.access.isPaywalled(25))
        assertEquals(0, ads.shown, "buying shows no ad")
    }

    @Test
    fun theUnlockIsRememberedAfterARestart() {
        shell().session.unlock.buy()
        assertTrue(shell().session.unlock.owned)
    }

    @Test
    fun aCancelledPurchaseStaysOnThePaywallQuietly() {
        passLevel10()
        purchases.nextBuy = PurchaseResult.Cancelled
        val shell = shell()
        shell.playLevel(level11)
        shell.buyUnlock()
        assertEquals(Screen.Paywall(level11), shell.screen)
        assertFalse(shell.session.unlock.owned)
        assertNull(shell.session.unlock.notice)
    }

    @Test
    fun aFailedPurchaseSaysSo() {
        passLevel10()
        purchases.nextBuy = PurchaseResult.Failed
        val shell = shell()
        shell.playLevel(level11)
        shell.buyUnlock()
        assertEquals(Screen.Paywall(level11), shell.screen)
        assertEquals(FullUnlock.STORE_FAILED, shell.session.unlock.notice)
    }

    @Test
    fun restoreFindsAnEarlierPurchaseAndStartsTheLevel() {
        passLevel10()
        purchases.owned = true // bought on another device; this one doesn't know yet
        store.putLong(FullUnlock.OWNED_KEY, 0L)
        val shell = shell(PlatformServices(store = store, ads = ads, purchases = OfflineStore(purchases)))
        assertFalse(shell.session.unlock.owned)
        shell.playLevel(level11)
        shell.restorePurchases()
        assertEquals(Screen.Game, shell.screen)
        assertTrue(shell.session.unlock.owned)
    }

    @Test
    fun restoreWithNothingToRestoreSaysSo() {
        val unlock = shell().session.unlock
        unlock.restore()
        assertFalse(unlock.owned)
        assertEquals(FullUnlock.NOTHING_TO_RESTORE, unlock.notice)
    }

    @Test
    fun launchRechecksTheStore() {
        store.putLong(FullUnlock.OWNED_KEY, 1L)
        assertFalse(shell().session.unlock.owned, "the store says it isn't owned (refunded)")

        store.putLong(FullUnlock.OWNED_KEY, 1L)
        val offline = shell(PlatformServices(store = store, ads = ads, purchases = OfflineStore(purchases)))
        assertTrue(offline.session.unlock.owned, "an unreachable store keeps the saved answer")
    }

    @Test
    fun thePriceComesFromTheStore() {
        val unlock = shell().session.unlock
        assertEquals("$2.99", unlock.price)
        assertEquals("UNLOCK ALL  $2.99", unlockButtonText(unlock.price))
        assertEquals("UNLOCK ALL", unlockButtonText(null))
    }

    @Test
    fun anAdOpensJustThatLevelForGood() {
        passLevel10()
        val shell = shell()
        shell.playLevel(level11)
        shell.watchAdForLevel()
        assertTrue(shell.session.access.adShowing)
        ads.finish(rewarded = true)
        assertEquals(Screen.Game, shell.screen)
        assertEquals(level11, shell.session.level)
        assertFalse(shell.session.access.isPaywalled(11))
        assertTrue(shell.session.access.isPaywalled(12), "the next paid level needs its own ad")
        assertFalse(shell.session.unlock.owned)

        shell.goHome()
        shell.playLevel(level11)
        assertEquals(Screen.Game, shell.screen, "no second ad for the same level")
        assertEquals(1, ads.shown)
    }

    @Test
    fun anAdClosedEarlyKeepsTheLevelLocked() {
        passLevel10()
        val shell = shell()
        shell.playLevel(level11)
        shell.watchAdForLevel()
        ads.finish(rewarded = false)
        assertEquals(Screen.Paywall(level11), shell.screen)
        assertTrue(shell.session.access.adMissed)
        assertTrue(shell.session.access.isPaywalled(11))
    }

    @Test
    fun anOwnerGetsTheAdPowerUpWithoutTheAd() {
        val shell = shell()
        shell.session.unlock.buy()
        shell.playFreePlay()
        shell.pause()
        assertEquals(Abilities.earnable, shell.session.boostOffers)
        shell.watchAdForBoost(BigBall)
        assertEquals(0, ads.shown)
        assertFalse(shell.paused)
        assertTrue(shell.session.boostUsed)
        shell.session.engine.fire()
        assertEquals(listOf(BigBall.id), shell.session.engine.activeAbilities.map { it.id })
        assertEquals("FREE: BIG BALL", boostButtonText(BigBall, adFree = true))
    }

    @Test
    fun abilityPreviewsWaitUntilTheFreeLevelsArePassed() {
        assertFalse(shell().session.showsAbilityPreviews)
        passLevel10()
        assertTrue(shell().session.showsAbilityPreviews)
    }

    /** [purchases] for buying and restoring, but its launch check can't reach the store. */
    private class OfflineStore(private val purchases: Purchases) : Purchases by purchases {
        override fun refresh(onResult: (StoreStatus) -> Unit) = onResult(StoreStatus(owned = null, price = null))
    }
}
