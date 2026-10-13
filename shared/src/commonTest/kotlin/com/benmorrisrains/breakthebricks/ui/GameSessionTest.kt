package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.Ability
import com.benmorrisrains.breakthebricks.engine.Ball
import com.benmorrisrains.breakthebricks.engine.Board
import com.benmorrisrains.breakthebricks.engine.BrickGame
import com.benmorrisrains.breakthebricks.engine.Difficulty
import com.benmorrisrains.breakthebricks.engine.FreeLevels
import com.benmorrisrains.breakthebricks.engine.GameConfig
import com.benmorrisrains.breakthebricks.engine.GameEngine
import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.engine.PlayerRank
import com.benmorrisrains.breakthebricks.engine.RoundPhase
import com.benmorrisrains.breakthebricks.platform.AudioPlayer
import com.benmorrisrains.breakthebricks.platform.HapticStrength
import com.benmorrisrains.breakthebricks.platform.Haptics
import com.benmorrisrains.breakthebricks.platform.InMemoryStore
import com.benmorrisrains.breakthebricks.platform.PlatformServices
import com.benmorrisrains.breakthebricks.platform.SoundEffect
import com.benmorrisrains.breakthebricks.progress.RankStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameSessionTest {
    internal class FakeEngine(override var phase: RoundPhase = RoundPhase.Aiming) : GameEngine {
        override val config = GameConfig()
        override val board = Board(config.columns, config.rows)
        override val balls = emptyList<Ball>()
        override var round = 1
        override val ballCount = 1
        override var points = 0L
        override val launchX = 3.5f
        override val aimAngle = 1.57f
        override val activeAbilities = emptyList<Ability>()
        override fun aim(angleRadians: Float) = Unit
        override fun fire() = Unit
        override fun advance(elapsedSeconds: Float) = Unit
        override fun drainEvents() = emptyList<GameEvent>()
    }

    private class Recorder : AudioPlayer, Haptics {
        val sounds = mutableListOf<SoundEffect>()
        val taps = mutableListOf<HapticStrength>()
        override fun play(effect: SoundEffect) { sounds += effect }
        override fun setMusicPlaying(playing: Boolean) = Unit
        override fun setMusicEnabled(enabled: Boolean) = Unit
        override fun setEffectsEnabled(enabled: Boolean) = Unit
        override fun tap(strength: HapticStrength) { taps += strength }
        override fun setEnabled(enabled: Boolean) = Unit
    }

    private val recorder = Recorder()
    private val store = InMemoryStore()
    private val services = PlatformServices(recorder, recorder, store)
    private val seeds = mutableListOf<Long>()
    private val ranks = mutableListOf<Int>()
    private var nextSeed = 10L

    private fun session(engines: MutableList<FakeEngine>) = GameSession(
        services,
        newSeed = { nextSeed++ },
        newEngine = { seed, rank, _ -> seeds += seed; ranks += rank; FakeEngine().also { engines += it } },
    )

    @Test
    fun theDefaultSessionRunsTheRealEngine() {
        assertIs<BrickGame>(GameSession(services).engine)
    }

    @Test
    fun resultsWaitForGameOverAndAShortPause() {
        val engines = mutableListOf<FakeEngine>()
        val session = session(engines)
        assertFalse(session.canSkipToResults, "nothing to skip mid-game")
        assertNull(session.result)

        engines.last().phase = RoundPhase.GameOver
        session.onFrame(0.3f, emptyList())
        assertFalse(session.canSkipToResults, "too soon after game over")
        session.onFrame(GameSession.SKIP_DELAY_SECONDS, emptyList())
        assertTrue(session.canSkipToResults)
        assertFalse(session.resultsDue)
        session.onFrame(GameSession.RESULTS_DELAY_SECONDS, emptyList())
        assertTrue(session.resultsDue)
        assertEquals(GameResult(round = 1, points = 0, bestPoints = 0, isNewBest = false), session.result)
    }

    @Test
    fun newGameStartsOnAFreshSeedAtAnyTime() {
        val engines = mutableListOf<FakeEngine>()
        val session = session(engines)
        val first = session.engine
        session.newGame()
        assertNotSame(first, session.engine, "a game in progress can be dropped")
        engines.last().phase = RoundPhase.GameOver
        session.onFrame(GameSession.RESULTS_DELAY_SECONDS, emptyList())
        session.newGame()
        assertEquals(RoundPhase.Aiming, session.engine.phase)
        assertFalse(session.resultsDue, "the game over timer resets")
        assertEquals(listOf(10L, 11L, 12L), seeds, "each game gets a fresh seed")
    }

    @Test
    fun resultCallsOutANewBestOnlyAgainstTheBestBeforeThatGame() {
        store.putLong(GameSession.BEST_POINTS_KEY, 100)
        val engines = mutableListOf<FakeEngine>()
        val session = session(engines)
        engines.last().apply { points = 250; round = 12; phase = RoundPhase.GameOver }
        session.onFrame(0.016f, listOf(GameEvent.GameOver(round = 12, points = 250)))
        assertEquals(
            GameResult(round = 12, points = 250, bestPoints = 250, isNewBest = true),
            session.result,
        )

        session.newGame()
        engines.last().apply { points = 250; phase = RoundPhase.GameOver }
        session.onFrame(0.016f, listOf(GameEvent.GameOver(round = 1, points = 250)))
        assertEquals(false, session.result?.isNewBest, "tying the best isn't a new best")
    }

    @Test
    fun bestPointsAreSavedAndOnlyGoUp() {
        store.putLong(GameSession.BEST_POINTS_KEY, 120)
        val session = session(mutableListOf())
        assertEquals(120, session.bestPoints)
        session.onFrame(0.016f, listOf(GameEvent.GameOver(round = 9, points = 80)))
        assertEquals(120, session.bestPoints)
        session.onFrame(0.016f, listOf(GameEvent.GameOver(round = 14, points = 450)))
        assertEquals(450, session.bestPoints)
        assertEquals(450, store.getLong(GameSession.BEST_POINTS_KEY, 0))
    }

    @Test
    fun eventsAndFiringReachSoundAndHaptics() {
        val session = session(mutableListOf())
        val engine = session.engine
        session.onFired()
        session.onFrame(0.016f, listOf(GameEvent.BrickBroken(2, 3, 1)))
        assertEquals(listOf(SoundEffect.Fire, SoundEffect.BrickBreak), recorder.sounds)
        assertEquals(listOf(HapticStrength.Light), recorder.taps, "the break tap is rate-limited after the fire tap")
        assertSame(engine, session.engine)
    }

    @Test
    fun aLevelPlaysItsOwnSeedAndSavesItsOwnResult() {
        val engines = mutableListOf<FakeEngine>()
        val levelsPlayed = mutableListOf<Int>()
        val session = GameSession(
            services,
            newSeed = { nextSeed++ },
            newEngine = { _, _, _ -> FakeEngine().also { engines += it } },
            newLevelEngine = { level -> levelsPlayed += level.number; FakeEngine().also { engines += it } },
        )
        store.putLong(GameSession.BEST_POINTS_KEY, 500)
        val level = FreeLevels[1]
        session.newGame(level)
        assertEquals(listOf(1), levelsPlayed)
        assertEquals(level, session.level)
        assertEquals(0, session.bestPoints, "a level has its own best, not free play's")

        engines.last().apply { points = 40; round = level.threeStarRounds; phase = RoundPhase.LevelComplete }
        session.onFrame(0.016f, listOf(GameEvent.LevelCompleted(level.threeStarRounds, 40)))
        val result = session.result
        assertEquals(true, result?.passed)
        assertEquals(3, result?.stars)
        assertEquals(level, result?.level)
        assertEquals(3, session.progress.stars(1))
        assertTrue(session.progress.isUnlocked(2))
        assertEquals(40, store.getLong(LevelProgress.bestPointsKey(1), 0))
        assertEquals(500, store.getLong(GameSession.BEST_POINTS_KEY, 0), "free play's best is untouched")

        session.onFrame(GameSession.RESULTS_DELAY_SECONDS, emptyList())
        assertTrue(session.resultsDue, "a cleared level moves on to the results too")

        session.newGame()
        assertNull(session.level)
        assertEquals(500, session.bestPoints)
    }

    @Test
    fun aFailedLevelEarnsNoStars() {
        val engines = mutableListOf<FakeEngine>()
        val session = GameSession(services, newEngine = { _, _, _ -> FakeEngine() }, newLevelEngine = { FakeEngine().also { engines += it } })
        session.newGame(FreeLevels[1])
        engines.last().apply { points = 999; phase = RoundPhase.GameOver }
        session.onFrame(0.016f, listOf(GameEvent.GameOver(round = 3, points = 999)))
        assertEquals(false, session.result?.passed)
        assertEquals(0, session.result?.stars)
        assertEquals(0, session.progress.stars(1))
    }

    private fun assertHud(rank: Int, progress: Float, actual: HudRank, message: String? = null) {
        assertEquals(rank, actual.rank, message)
        assertEquals(progress, actual.progress, 1e-6f, message)
    }

    @Test
    fun theDefaultSessionBuildsFreePlayForTheSavedRank() {
        RankStore(store).addPoints(PlayerRank.pointsToReach(50))
        val config = GameSession(services).engine.config
        val expected = Difficulty.forRank(50)
        assertEquals(expected.startingRows, config.startingRows)
        assertEquals(expected.maxBricksPerRow, config.maxBricksPerRow)
        assertEquals(expected.rowValues.valueFor(1), config.rowValues.valueFor(1))
    }

    @Test
    fun gamePointsCountTowardRankAndTheNextGameUsesTheNewRank() {
        store.putLong(RankStore.POINTS_KEY, 300)
        val engines = mutableListOf<FakeEngine>()
        val session = session(engines)
        assertEquals(listOf(1), ranks)
        assertHud(1, 300f / 500f, session.hudRank(0))
        assertHud(1, 400f / 500f, session.hudRank(100), "the bar fills live")
        assertHud(1, 1f, session.hudRank(2000), "the rank waits for the game to end")

        engines.last().apply { points = 2000; phase = RoundPhase.GameOver }
        session.onFrame(0.016f, listOf(GameEvent.GameOver(round = 8, points = 2000)))
        session.onFrame(0.016f, listOf(GameEvent.GameOver(round = 8, points = 2000)))
        assertEquals(2300, store.getLong(RankStore.POINTS_KEY, 0), "a game's points count once")
        assertEquals(3, store.getLong(RankStore.RANK_KEY, 0))
        assertEquals(RankUp(from = 1, to = 3), session.rankUp)
        assertEquals(RankUp(from = 1, to = 3), session.result?.rankUp)
        assertEquals(RankProgress(rank = 3, pointsIntoRank = 800, pointsForNextRank = 1500), session.rankProgress())
        assertHud(3, 800f / 1500f, session.hudRank(2000))

        session.newGame()
        assertEquals(listOf(1, 3), ranks)
        assertEquals(3, session.gameRank)
        assertNull(session.rankUp)
    }

    @Test
    fun levelPointsCountTowardRankButLevelsKeepTheirOwnBoards() {
        val engines = mutableListOf<FakeEngine>()
        val session = GameSession(services, newEngine = { _, _, _ -> FakeEngine() }, newLevelEngine = { FakeEngine().also { engines += it } })
        session.newGame(FreeLevels[1])
        engines.last().apply { points = 600; phase = RoundPhase.GameOver }
        session.onFrame(0.016f, listOf(GameEvent.GameOver(round = 3, points = 600)))
        assertEquals(PlayerRank(600), session.playerRank)
        assertEquals(RankUp(from = 1, to = 2), session.result?.rankUp)
    }

    @Test
    fun noBannerWithoutARankUp() {
        val engines = mutableListOf<FakeEngine>()
        val session = session(engines)
        engines.last().phase = RoundPhase.GameOver
        session.onFrame(0.016f, listOf(GameEvent.GameOver(round = 3, points = 200)))
        assertNull(session.rankUp)
        assertEquals(PlayerRank(200), session.playerRank)
    }
}
