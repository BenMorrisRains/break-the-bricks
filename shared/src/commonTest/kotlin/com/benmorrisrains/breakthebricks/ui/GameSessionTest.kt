package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.Ability
import com.benmorrisrains.breakthebricks.engine.Ball
import com.benmorrisrains.breakthebricks.engine.Board
import com.benmorrisrains.breakthebricks.engine.BrickGame
import com.benmorrisrains.breakthebricks.engine.Difficulty
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
    private class FakeEngine(val rank: Int = 1, override var phase: RoundPhase = RoundPhase.Aiming) : GameEngine {
        override val config = GameConfig()
        override val board = Board(config.columns, config.rows)
        override val balls = emptyList<Ball>()
        override val round = 1
        override val ballCount = 1
        override val points = 0L
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
        override fun setMusicEnabled(enabled: Boolean) = Unit
        override fun setEffectsEnabled(enabled: Boolean) = Unit
        override fun tap(strength: HapticStrength) { taps += strength }
        override fun setEnabled(enabled: Boolean) = Unit
    }

    private val recorder = Recorder()
    private val store = InMemoryStore()
    private val services = PlatformServices(recorder, recorder, store)
    private val seeds = mutableListOf<Long>()
    private var nextSeed = 10L

    private fun session(engines: MutableList<FakeEngine>) = GameSession(
        services,
        newSeed = { nextSeed++ },
        newEngine = { seed, rank -> seeds += seed; FakeEngine(rank).also { engines += it } },
    )

    @Test
    fun theDefaultSessionRunsTheRealEngine() {
        assertIs<BrickGame>(GameSession(services).engine)
    }

    @Test
    fun restartWaitsForGameOverAndAShortPause() {
        val engines = mutableListOf<FakeEngine>()
        val session = session(engines)
        val first = session.engine
        assertFalse(session.restart(), "no restart mid-game")

        engines.last().phase = RoundPhase.GameOver
        session.onFrame(0.3f, emptyList())
        assertFalse(session.restart(), "too soon after game over")
        session.onFrame(GameSession.RESTART_DELAY_SECONDS, emptyList())
        assertTrue(session.restart())

        assertNotSame(first, session.engine)
        assertEquals(RoundPhase.Aiming, session.engine.phase)
        assertEquals(listOf(10L, 11L), seeds, "each game gets a fresh seed")
        assertFalse(session.canRestart)
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

    private fun assertHud(rank: Int, progress: Float, actual: HudRank, message: String? = null) {
        assertEquals(rank, actual.rank, message)
        assertEquals(progress, actual.progress, 1e-6f, message)
    }

    @Test
    fun theDefaultSessionBuildsTheGameForTheSavedRank() {
        RankStore(store).addPoints(PlayerRank.pointsToReach(50))
        val config = GameSession(services).engine.config
        val expected = Difficulty.forRank(50)
        assertEquals(expected.startingRows, config.startingRows)
        assertEquals(expected.maxBricksPerRow, config.maxBricksPerRow)
        assertEquals(expected.rowValues.valueFor(1), config.rowValues.valueFor(1))
    }

    @Test
    fun gamePointsCountTowardRankAndTheNextGameUsesTheNewRank() {
        store.putLong(RankStore.POINTS_KEY, 30)
        val engines = mutableListOf<FakeEngine>()
        val session = session(engines)
        assertEquals(1, engines.last().rank)
        assertHud(1, 30f / 50f, session.hudRank(0))
        assertHud(1, 40f / 50f, session.hudRank(10), "the bar fills live")
        assertHud(1, 1f, session.hudRank(200), "the rank waits for the game to end")

        engines.last().phase = RoundPhase.GameOver
        session.onFrame(0.016f, listOf(GameEvent.GameOver(round = 8, points = 200)))
        session.onFrame(0.016f, listOf(GameEvent.GameOver(round = 8, points = 200)))
        assertEquals(230, store.getLong(RankStore.POINTS_KEY, 0), "a game's points count once")
        assertEquals(3, store.getLong(RankStore.RANK_KEY, 0))
        assertEquals(RankUp(from = 1, to = 3), session.rankUp)
        assertEquals(PlayerRank(230), session.playerRank)
        assertHud(3, 80f / 150f, session.hudRank(200))

        session.onFrame(GameSession.RESTART_DELAY_SECONDS, emptyList())
        assertTrue(session.restart())
        assertEquals(3, engines.last().rank)
        assertEquals(3, session.gameRank)
        assertNull(session.rankUp)
    }

    @Test
    fun noBannerWithoutARankUp() {
        val engines = mutableListOf<FakeEngine>()
        val session = session(engines)
        engines.last().phase = RoundPhase.GameOver
        session.onFrame(0.016f, listOf(GameEvent.GameOver(round = 3, points = 20)))
        assertNull(session.rankUp)
        assertEquals(PlayerRank(20), session.playerRank)
    }
}
