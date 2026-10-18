package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.Ability
import com.benmorrisrains.breakthebricks.engine.AbilityInventory
import com.benmorrisrains.breakthebricks.engine.BigBall
import com.benmorrisrains.breakthebricks.engine.FreeLevels
import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.engine.GrenadeBall
import com.benmorrisrains.breakthebricks.engine.PaidLevels
import com.benmorrisrains.breakthebricks.engine.RoundPhase
import com.benmorrisrains.breakthebricks.engine.WagerOutcome
import com.benmorrisrains.breakthebricks.platform.GameSettings
import com.benmorrisrains.breakthebricks.platform.InMemoryStore
import com.benmorrisrains.breakthebricks.platform.PlatformServices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The wager from the home screen's Play to the results, with the inventory saved across restarts. */
class WagerFlowTest {
    private val store = InMemoryStore()
    private val engines = mutableListOf<GameSessionTest.FakeEngine>()
    /** The copies each free-play game started with. */
    private val stakes = mutableListOf<AbilityInventory>()

    /** A fresh app on the same saved [store], as after a restart. */
    private fun launch() = GameShell(
        session = GameSession(
            PlatformServices(store = store),
            newSeed = { 1L },
            newEngine = { _, _, copies -> stakes += copies; GameSessionTest.FakeEngine().also { engines += it } },
            newLevelEngine = { GameSessionTest.FakeEngine().also { engines += it } },
        ),
        settings = GameSettings(PlatformServices(store = store)),
    )

    private val earnRound = GrenadeBall.earnedAtRound
    private val target = GrenadeBall.wagerTargetRound

    private val GameShell.grenades get() = session.bank.inventory.count(GrenadeBall.id)

    private fun GameShell.event(vararg events: GameEvent) = onFrame(0.016f, events.toList())

    private fun GameShell.reachRound(round: Int) {
        engines.last().round = round
        event(GameEvent.RoundStarted(round, round))
    }

    private fun GameShell.endGame() {
        val engine = engines.last().apply { phase = RoundPhase.GameOver }
        event(GameEvent.GameOver(engine.round, engine.points))
        onFrame(GameSession.RESULTS_DELAY_SECONDS, emptyList())
    }

    /** Plays the free first game, earning one grenade at its round. */
    private fun GameShell.playFirstGameEarningAGrenade() {
        playFreePlay()
        assertEquals(Screen.Game, screen, "the first game is free")
        reachRound(earnRound)
        event(GameEvent.AbilityEarned(GrenadeBall.id, earnRound))
        endGame()
    }

    @Test
    fun theFirstGameIsFreeAndBanksWhatItEarns() {
        val shell = launch()
        shell.playFirstGameEarningAGrenade()
        assertEquals(1, shell.grenades)
        val result = assertIs<Screen.Results>(shell.screen).result
        assertEquals(WagerOutcome.Unstaked, result.wagerOutcome)
        assertEquals(listOf<Ability>(GrenadeBall), result.banked)
        assertEquals(listOf(WagerLine("GRENADE BALL BANKED +1", won = true)), wagerLines(result))
        assertEquals(AbilityInventory(), stakes.last(), "nothing in the bank yet")
    }

    @Test
    fun theNextGameNeedsAStakeAndAWinPaysItBackPlusOne() {
        val shell = launch()
        shell.playFirstGameEarningAGrenade()
        shell.play()
        assertEquals(Screen.Wager, shell.screen, "Play again goes to the wager screen")
        assertEquals(listOf<Ability>(GrenadeBall), shell.session.bank.stakeable)

        shell.stake(GrenadeBall)
        assertEquals(Screen.Game, shell.screen)
        assertEquals(0, stakes.last().count(GrenadeBall.id), "the staked grenade sits out the game")
        assertEquals(0, shell.grenades, "the stake leaves the inventory as the game starts")

        shell.reachRound(earnRound)
        shell.event(GameEvent.AbilityEarned(GrenadeBall.id, earnRound))
        assertEquals(1, shell.grenades, "earning the staked ability banks a copy to use")
        shell.reachRound(target)
        assertEquals(3, shell.grenades, "the target round wins: stake back plus 1")
        shell.reachRound(target + 2)
        shell.endGame()

        val result = assertIs<Screen.Results>(shell.screen).result
        assertEquals(WagerOutcome.Won, result.wagerOutcome)
        assertEquals(
            listOf(
                WagerLine("GRENADE BALL KEPT +1  (GRENADE BALL x3)", won = true),
                WagerLine("GRENADE BALL BANKED +1", won = true),
            ),
            wagerLines(result),
            "the earned copy banks too, now that the stake sits out the game",
        )
        assertEquals(3, launch().grenades, "the win is saved")
    }

    @Test
    fun aLossBeforeTheTargetLosesTheStake() {
        val shell = launch()
        shell.playFirstGameEarningAGrenade()
        shell.playFreePlay()
        shell.stake(GrenadeBall)
        shell.reachRound(target - 1)
        shell.endGame()

        val result = assertIs<Screen.Results>(shell.screen).result
        assertEquals(WagerOutcome.Lost, result.wagerOutcome)
        assertEquals(listOf(WagerLine("GRENADE BALL LOST  (GRENADE BALL x0)", won = false)), wagerLines(result))
        assertEquals(0, shell.grenades)

        shell.play()
        assertEquals(Screen.Game, shell.screen, "an empty inventory plays a free, unstaked game")
        assertEquals(AbilityInventory(), stakes.last())
    }

    @Test
    fun quittingMidGameLosesTheStakeEvenAcrossARestart() {
        val shell = launch()
        shell.playFirstGameEarningAGrenade()
        shell.session.bank.save(AbilityInventory().add(GrenadeBall.id, 3))
        shell.playFreePlay()
        shell.stake(GrenadeBall)
        shell.pause()
        assertEquals("QUITTING LOSES YOUR GRENADE BALL", quitWarning(shell.session.wager.stake, shell.session.wager.outcome))
        shell.goHome()
        assertEquals(WagerOutcome.Lost, shell.session.wager.outcome)
        assertEquals(2, shell.grenades)

        // The app is killed mid-game: the stake was saved out as the game started.
        shell.playFreePlay()
        shell.stake(GrenadeBall)
        assertEquals(1, launch().grenades)
    }

    @Test
    fun restartingAWageredGameLosesItAndAsksForANewStake() {
        val shell = launch()
        shell.playFirstGameEarningAGrenade()
        shell.session.bank.save(AbilityInventory().add(GrenadeBall.id, 2))
        shell.playFreePlay()
        shell.stake(GrenadeBall)
        shell.pause()
        shell.play()
        assertEquals(Screen.Wager, shell.screen)
        assertFalse(shell.paused)
        assertEquals(WagerOutcome.Lost, shell.session.wager.outcome)
        assertEquals(1, shell.grenades)
        shell.goHome()
        assertEquals(Screen.Home, shell.screen, "the wager screen can back out to home")
    }

    @Test
    fun theInventoryAndFirstGameSurviveARestart() {
        launch().playFirstGameEarningAGrenade()
        val shell = launch()
        assertEquals(1, shell.grenades)
        assertTrue(shell.session.bank.firstGamePlayed)
        assertTrue(shell.session.bank.stakeRequired)
        shell.playFreePlay()
        assertEquals(Screen.Wager, shell.screen)
    }

    /** Ben, 2026-10-17: power-ups are limited; a copy used on a shot is gone from the bank. */
    @Test
    fun freePlayStartsWithTheBankAndAUsedCopyLeavesIt() {
        val shell = launch()
        shell.playFirstGameEarningAGrenade()
        shell.session.bank.save(AbilityInventory().add(GrenadeBall.id, 3).add(BigBall.id, 2))
        shell.playFreePlay()
        shell.stake(BigBall)
        assertEquals(AbilityInventory().add(GrenadeBall.id, 3).add(BigBall.id, 1), stakes.last(), "every copy but the stake")
        shell.event(GameEvent.PowerUpUsed(GrenadeBall.id))
        assertEquals(2, shell.grenades)
        shell.event(GameEvent.PowerUpUsed(GrenadeBall.id, boosted = true))
        assertEquals(2, shell.grenades, "an ad's copy was never in the bank")
        assertEquals(2, launch().grenades, "the use is saved")
    }

    @Test
    fun aLevelsLentPowerUpIsUsedBeforeTheBank() {
        val shell = launch()
        shell.playFirstGameEarningAGrenade()
        shell.goHome()
        val level = PaidLevels.all.first { it.power == GrenadeBall }
        shell.session.newGame(level)
        shell.event(GameEvent.PowerUpUsed(GrenadeBall.id))
        assertEquals(1, shell.grenades, "the lent copy went first")
    }

    @Test
    fun copiesAreCappedAtNine() {
        val shell = launch()
        shell.session.bank.save(AbilityInventory().add(GrenadeBall.id, 9))
        shell.playFreePlay()
        shell.reachRound(earnRound)
        shell.event(GameEvent.AbilityEarned(GrenadeBall.id, earnRound))
        assertEquals(9, shell.grenades)
        assertEquals(9, launch().grenades)
    }

    @Test
    fun levelsAreNeverStakedButStillBank() {
        val shell = launch()
        shell.playFirstGameEarningAGrenade()
        shell.goHome()
        shell.playLevel(FreeLevels[1])
        assertEquals(Screen.Game, shell.screen)
        assertNull(shell.session.wager.stake)
        assertEquals(1, shell.grenades)
        shell.event(GameEvent.AbilityEarned(GrenadeBall.id, earnRound))
        assertEquals(2, shell.grenades)
    }

    @Test
    fun aStakeOnlyComesFromTheWagerScreenAndTheInventory() {
        val shell = launch()
        shell.playFirstGameEarningAGrenade()
        shell.stake(GrenadeBall)
        assertIs<Screen.Results>(shell.screen, "staking does nothing off the wager screen")
        assertFailsWith<IllegalArgumentException> { shell.session.newGame() }
    }

    @Test
    fun wagerScreenText() {
        assertEquals("GRENADE BALL x3", stakeCardText(GrenadeBall, 3))
        assertEquals("REACH ROUND $target TO KEEP IT, +1", stakeGoalText(GrenadeBall))
        assertNull(quitWarning(null, WagerOutcome.Unstaked))
        assertNull(quitWarning(GrenadeBall, WagerOutcome.Won), "nothing left to lose once won")
    }
}
