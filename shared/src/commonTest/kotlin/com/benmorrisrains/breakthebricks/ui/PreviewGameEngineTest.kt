package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.BallState
import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.engine.RoundPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewGameEngineTest {
    /** Advances in 1/60 s frames until the round is back to aiming (or gives up). */
    private fun PreviewGameEngine.playOut(maxSeconds: Float = 120f): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        var t = 0f
        while (phase != RoundPhase.Aiming && phase != RoundPhase.GameOver && t < maxSeconds) {
            advance(1f / 60)
            t += 1f / 60
            balls.filter { it.state == BallState.Flying }.forEach {
                assertTrue(it.x in 0f..config.columns.toFloat() && it.y in 0f..config.rows.toFloat())
            }
            events += drainEvents()
        }
        return events
    }

    @Test
    fun aRoundEndsWithOneMoreBallAndTheLaunchPointWhereTheFirstBallLanded() {
        val engine = PreviewGameEngine()
        engine.drainEvents()
        val round = engine.round
        engine.aim(1.0f)
        engine.fire()
        val events = engine.playOut()
        assertEquals(RoundPhase.Aiming, engine.phase)
        assertEquals(round + 1, engine.round)
        assertEquals(round + 1, engine.ballCount)
        val first = events.filterIsInstance<GameEvent.BallLanded>().first { it.isFirst }
        assertEquals(first.x, engine.launchX, 1e-4f)
        assertEquals(events.count { it is GameEvent.BrickHit }.toLong(), engine.points)
    }

    @Test
    fun sameSeedAndInputsGiveTheSameGame() {
        fun play(): Pair<Long, Float> {
            val engine = PreviewGameEngine(seed = 7)
            repeat(3) { i ->
                engine.aim(0.6f + i * 0.5f)
                engine.fire()
                engine.playOut()
            }
            return engine.points to engine.launchX
        }
        assertEquals(play(), play())
    }

    @Test
    fun aimIsClampedAboveTheFloor() {
        val engine = PreviewGameEngine()
        engine.aim(0f)
        assertEquals(engine.config.minAimAngle, engine.aimAngle)
    }

    @Test
    fun fireIsIgnoredOutsideAiming() {
        val engine = PreviewGameEngine()
        engine.fire()
        engine.advance(0.5f)
        val launched = engine.balls.count { it.state != BallState.Waiting }
        engine.fire()
        assertEquals(launched, engine.balls.count { it.state != BallState.Waiting })
    }
}
