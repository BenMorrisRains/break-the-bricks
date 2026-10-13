package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.FreeLevels
import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.engine.PaidLevels
import com.benmorrisrains.breakthebricks.engine.RoundPhase
import com.benmorrisrains.breakthebricks.platform.AudioPlayer
import com.benmorrisrains.breakthebricks.platform.GameSettings
import com.benmorrisrains.breakthebricks.platform.PlatformServices
import com.benmorrisrains.breakthebricks.platform.SoundEffect
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
    private class MusicRecorder : AudioPlayer {
        var playing = false
        override fun play(effect: SoundEffect) = Unit
        override fun setMusicPlaying(playing: Boolean) { this.playing = playing }
        override fun setMusicEnabled(enabled: Boolean) = Unit
        override fun setEffectsEnabled(enabled: Boolean) = Unit
    }

    private val music = MusicRecorder()
    private val services = PlatformServices(audio = music)
    private var seed = 0L
    private val shell = GameShell(
        session = GameSession(
            services,
            newSeed = { seed++ },
            newEngine = { _, _, _ -> GameSessionTest.FakeEngine().also { engines += it } },
            newLevelEngine = { GameSessionTest.FakeEngine().also { engines += it } },
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

    @Test
    fun levelsOpenFromHomeAndOnlyUnlockedOnesPlay() {
        shell.openLevels()
        assertEquals(Screen.Levels, shell.screen)
        shell.playLevel(FreeLevels[2])
        assertEquals(Screen.Levels, shell.screen, "level 2 is locked until level 1 is passed")
        shell.playLevel(FreeLevels[1])
        assertEquals(Screen.Game, shell.screen)
        assertEquals(FreeLevels[1], shell.session.level)
    }

    @Test
    fun aPassedLevelOffersTheNextAndPlayAgainRetriesTheSameLevel() {
        shell.playLevel(FreeLevels[1])
        assertNull(shell.nextLevel())
        engines.last().apply { phase = RoundPhase.LevelComplete; points = 50 }
        shell.onFrame(0.016f, listOf(GameEvent.LevelCompleted(round = 5, points = 50)))
        shell.onFrame(GameSession.RESULTS_DELAY_SECONDS, emptyList())
        val results = assertIs<Screen.Results>(shell.screen)
        assertEquals(true, results.result.passed)
        assertEquals(FreeLevels[2], shell.nextLevel())

        shell.play()
        assertEquals(FreeLevels[1], shell.session.level, "play again replays the same level")
        shell.goHome()
        shell.playFreePlay()
        assertNull(shell.session.level)
    }

    @Test
    fun passingLevelTenOffersNoNextLevelWhileThePaidLevelsAreLocked() {
        for (number in 1..9) shell.session.progress.record(FreeLevels[number], passed = true, round = 1, points = 1)
        shell.playLevel(FreeLevels[10])
        assertEquals(FreeLevels[10], shell.session.level)
        engines.last().apply { phase = RoundPhase.LevelComplete; points = 50 }
        shell.onFrame(0.016f, listOf(GameEvent.LevelCompleted(round = 5, points = 50)))
        shell.onFrame(GameSession.RESULTS_DELAY_SECONDS, emptyList())
        assertIs<Screen.Results>(shell.screen)
        assertNull(shell.nextLevel(), "level 11 is paid")
        shell.playNextLevel()
        assertIs<Screen.Results>(shell.screen)
        shell.playLevel(PaidLevels[11])
        assertIs<Screen.Results>(shell.screen, "a locked paid level doesn't start")
    }

    @Test
    fun musicPlaysDuringAGameAndStopsForPauseMenusAndTheEnd() {
        assertFalse(music.playing, "no music on home")
        shell.play()
        assertTrue(music.playing)
        shell.pause()
        assertFalse(music.playing, "paused")
        shell.openSettings()
        assertFalse(music.playing)
        shell.closeSettings()
        assertFalse(music.playing, "still paused")
        shell.resume()
        assertTrue(music.playing)
        endGame()
        assertFalse(music.playing, "the game-over sound plays alone")
        shell.play()
        assertTrue(music.playing)
        shell.goHome()
        assertFalse(music.playing)
        shell.playFreePlay()
        shell.openLevels()
        assertFalse(music.playing)
    }

    @Test
    fun aFinishedLevelCannotBePaused() {
        shell.playLevel(FreeLevels[1])
        engines.last().phase = RoundPhase.LevelComplete
        shell.pause()
        assertFalse(shell.paused)
    }
}
