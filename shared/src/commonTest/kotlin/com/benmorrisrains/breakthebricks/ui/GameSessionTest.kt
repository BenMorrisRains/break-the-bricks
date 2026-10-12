package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.Ability
import com.benmorrisrains.breakthebricks.engine.Ball
import com.benmorrisrains.breakthebricks.engine.Board
import com.benmorrisrains.breakthebricks.engine.BrickGame
import com.benmorrisrains.breakthebricks.engine.GameConfig
import com.benmorrisrains.breakthebricks.engine.GameEngine
import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.engine.RoundPhase
import com.benmorrisrains.breakthebricks.platform.AudioPlayer
import com.benmorrisrains.breakthebricks.platform.HapticStrength
import com.benmorrisrains.breakthebricks.platform.Haptics
import com.benmorrisrains.breakthebricks.platform.InMemoryStore
import com.benmorrisrains.breakthebricks.platform.PlatformServices
import com.benmorrisrains.breakthebricks.platform.SoundEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameSessionTest {
    private class FakeEngine(override var phase: RoundPhase = RoundPhase.Aiming) : GameEngine {
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
        newEngine = { seed -> seeds += seed; FakeEngine().also { engines += it } },
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
}
