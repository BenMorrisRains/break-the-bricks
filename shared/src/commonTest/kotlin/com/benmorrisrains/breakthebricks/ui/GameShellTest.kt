package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.engine.RoundPhase
import com.benmorrisrains.breakthebricks.platform.GameSettings
import com.benmorrisrains.breakthebricks.platform.PlatformServices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameShellTest {
    private val engines = mutableListOf<GameSessionTest.FakeEngine>()
    private val services = PlatformServices()
    private var seed = 0L
    private val shell = GameShell(
        session = GameSession(
            services,
            newSeed = { seed++ },
            newEngine = { GameSessionTest.FakeEngine().also { engines += it } },
        ),
        settings = GameSettings(services),
    )

    private fun endGame(points: Long = 0L) {
        engines.last().apply { phase = RoundPhase.GameOver; this.points = points }
        shell.onFrame(0.016f, listOf(GameEvent.GameOver(round = 1, points = points)))
    }

    @Test
    fun opensOnHomeAndPlayStartsAFreshGame() {
        assertEquals(Screen.Home, shell.screen)
        val before = shell.session.engine
        shell.play()
        assertEquals(Screen.Game, shell.screen)
        assertNotSame(before, shell.session.engine)
        assertFalse(shell.paused)
    }

    @Test
    fun pauseAndResumeKeepTheSameGame() {
        shell.play()
        val engine = shell.session.engine
        shell.pause()
        assertTrue(shell.paused)
        shell.resume()
        assertFalse(shell.paused)
        assertSame(engine, shell.session.engine)
    }

    @Test
    fun settingsFromThePauseMenuReturnToThePausedGame() {
        shell.play()
        val engine = shell.session.engine
        shell.pause()
        shell.openSettings()
        assertEquals(Screen.Settings(returnTo = Screen.Game), shell.screen)
        shell.closeSettings()
        assertEquals(Screen.Game, shell.screen)
        assertTrue(shell.paused, "still paused until Resume")
        assertSame(engine, shell.session.engine)
    }

    @Test
    fun settingsFromHomeReturnHome() {
        shell.openSettings()
        shell.openSettings()
        shell.closeSettings()
        assertEquals(Screen.Home, shell.screen)
    }

    @Test
    fun restartFromPauseStartsANewUnpausedGame() {
        shell.play()
        val engine = shell.session.engine
        shell.pause()
        shell.play()
        assertFalse(shell.paused)
        assertNotSame(engine, shell.session.engine)
        assertEquals(Screen.Game, shell.screen)
    }

    @Test
    fun homeFromPauseDropsTheGame() {
        shell.play()
        shell.pause()
        shell.goHome()
        assertEquals(Screen.Home, shell.screen)
        assertFalse(shell.paused)
    }

    @Test
    fun gameOverShowsResultsAfterTheFinalBoardHasHadItsMoment() {
        shell.play()
        endGame(points = 42)
        shell.pause()
        assertFalse(shell.paused, "nothing to pause once the game is over")
        assertEquals(Screen.Game, shell.screen, "the final board stays up first")
        shell.onFrame(GameSession.RESULTS_DELAY_SECONDS, emptyList())
        val results = assertIs<Screen.Results>(shell.screen)
        assertEquals(42, results.result.points)
        assertTrue(results.result.isNewBest)

        shell.play()
        assertEquals(Screen.Game, shell.screen, "Play again")
    }

    @Test
    fun aTapSkipsToResultsOnlyAfterTheGuard() {
        shell.play()
        endGame()
        shell.onGameOverTap()
        assertEquals(Screen.Game, shell.screen, "a late tap from the last shot doesn't skip")
        shell.onFrame(GameSession.SKIP_DELAY_SECONDS, emptyList())
        shell.onGameOverTap()
        assertIs<Screen.Results>(shell.screen)
    }

    @Test
    fun aTapMidGameDoesNothing() {
        shell.play()
        shell.onGameOverTap()
        assertEquals(Screen.Game, shell.screen)
    }

    @Test
    fun rankIsLeftOutUntilItExists() {
        assertNull(shell.rank())
    }

    @Test
    fun rankProgressFractionAndPointsToGo() {
        val rank = RankProgress(rank = 3, pointsIntoRank = 30, pointsForNextRank = 150)
        assertEquals(0.2f, rank.fraction)
        assertEquals(120, rank.pointsToGo)
        assertEquals(1f, RankProgress(3, 200, 150).fraction)
        assertEquals(0, RankProgress(3, 200, 150).pointsToGo)
        assertEquals(0f, RankProgress(1, 0, 0).fraction)
    }
}
