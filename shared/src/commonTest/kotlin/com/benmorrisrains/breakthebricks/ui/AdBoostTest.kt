package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.Abilities
import com.benmorrisrains.breakthebricks.engine.AbilityInventory
import com.benmorrisrains.breakthebricks.engine.BigBall
import com.benmorrisrains.breakthebricks.engine.BrickGame
import com.benmorrisrains.breakthebricks.engine.Drill
import com.benmorrisrains.breakthebricks.engine.GridPhysics
import com.benmorrisrains.breakthebricks.engine.Splitter
import com.benmorrisrains.breakthebricks.platform.GameSettings
import com.benmorrisrains.breakthebricks.platform.InMemoryStore
import com.benmorrisrains.breakthebricks.platform.PlatformServices
import com.benmorrisrains.breakthebricks.platform.RewardedAds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A free-play engine with limited power-ups, as the app makes, on the default config. */
internal fun limitedEngine(seed: Long, @Suppress("UNUSED_PARAMETER") rank: Int, copies: AbilityInventory) = BrickGame(
    seed = seed,
    physics = GridPhysics,
    abilities = Abilities.earnable.filter { copies.count(it.id) > 0 },
    singleUsePowerUps = true,
    copies = copies.copies,
)

/** Watching an ad mid-game for a one-shot power-up (Ben, 2026-10-13). */
class AdBoostTest {
    /** An ad the test finishes by hand. */
    private class ManualAds : RewardedAds {
        var pending: ((Boolean) -> Unit)? = null
        override fun show(onFinished: (rewarded: Boolean) -> Unit) { pending = onFinished }
        fun finish(rewarded: Boolean) {
            val onFinished = pending!!
            pending = null
            onFinished(rewarded)
        }
    }

    private val ads = ManualAds()
    private val store = InMemoryStore()
    private val services = PlatformServices(store = store, ads = ads)
    private val shell = GameShell(
        session = GameSession(
            services,
            newSeed = { 3L },
            newEngine = ::limitedEngine,
        ),
        settings = GameSettings(services),
    )
    private val session get() = shell.session

    private fun pausedGame() {
        shell.playFreePlay()
        shell.pause()
    }

    @Test
    fun everyPowerUpIsOfferedAtTheStart() {
        pausedGame()
        assertEquals(Abilities.earnable, session.boostOffers)
    }

    @Test
    fun aWatchedAdTurnsThePowerUpOnFromTheNextShotAndResumes() {
        pausedGame()
        shell.watchAdForBoost(BigBall)
        assertTrue(session.adShowing)
        assertTrue(session.boostOffers.isEmpty(), "no second ad while one is showing")
        ads.finish(rewarded = true)
        assertFalse(shell.paused, "play picks up after the ad")
        assertTrue(session.boostUsed)
        session.engine.fire()
        assertEquals(listOf(BigBall.id), session.engine.activeAbilities.map { it.id })
    }

    @Test
    fun oneAdPowerUpPerGame() {
        pausedGame()
        shell.watchAdForBoost(BigBall)
        ads.finish(rewarded = true)
        shell.pause()
        assertTrue(session.boostOffers.isEmpty())
        shell.watchAdForBoost(Drill)
        assertEquals(null, ads.pending, "no ad is shown")
        shell.play()
        assertEquals(Abilities.earnable, session.boostOffers, "a new game gets its own")
    }

    @Test
    fun anAdClosedEarlyGrantsNothing() {
        pausedGame()
        shell.watchAdForBoost(Splitter)
        ads.finish(rewarded = false)
        assertTrue(shell.paused)
        assertTrue(session.adMissed)
        assertFalse(session.boostUsed)
        session.engine.fire()
        assertTrue(session.engine.activeAbilities.isEmpty())
        assertEquals(Abilities.earnable, session.boostOffers, "the player can try again")
    }

    @Test
    fun theAdPowerUpNeverReachesTheInventory() {
        pausedGame()
        val before = session.bank.inventory
        shell.watchAdForBoost(BigBall)
        ads.finish(rewarded = true)
        session.engine.fire()
        shell.onFrame(0.016f, session.engine.drainEvents())
        assertEquals(before, session.bank.inventory)
        assertTrue(session.bank.stakeable.isEmpty(), "nothing to stake from an ad")
    }

    @Test
    fun anAdThatEndsAfterTheGameWasLeftGrantsNothing() {
        pausedGame()
        val first = session.engine
        shell.watchAdForBoost(Drill)
        shell.goHome()
        shell.playFreePlay()
        ads.finish(rewarded = true)
        assertFalse(session.boostUsed)
        first.fire()
        assertTrue(first.activeAbilities.isEmpty())
    }

    @Test
    fun freePlayOffersACopyOfOneTheGameAlreadyHas() {
        val bank = AbilityBank(store)
        bank.save(bank.inventory.add(Splitter.id))
        bank.markFirstGamePlayed()
        val shell = GameShell(
            session = GameSession(
                services,
                newSeed = { 3L },
                newEngine = ::limitedEngine,
            ),
            settings = GameSettings(services),
        )
        shell.playFreePlay()
        shell.stake(Splitter)
        assertTrue(Splitter in shell.session.boostOffers, "power-ups are limited, so another copy helps")
    }

    @Test
    fun buttonText() {
        assertEquals("AD: BIG BALL", boostButtonText(BigBall))
    }
}
