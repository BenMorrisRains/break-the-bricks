package com.benmorrisrains.breakthebricks.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** The engine with the real [GridPhysics], played from round 1 to game over as the app runs it. */
class FullGameTest {
    private val angles = listOf(1.3f, 0.5f, 2.4f, 1.0f, 1.9f, 0.3f, 2.8f, 1.57f, 0.8f, 2.1f)

    private class Played(val game: BrickGame, val events: List<GameEvent>, val launchXs: List<Float>)

    /** Plays a whole game in 1/60 s frames, checking every frame that flying balls stay in the field. */
    private fun play(seed: Long, frameSeconds: (Int) -> Float = { 1f / 60 }): Played {
        val game = BrickGame(seed = seed, physics = GridPhysics)
        val config = game.config
        val events = mutableListOf<GameEvent>()
        val launchXs = mutableListOf<Float>()
        var frame = 0
        var shot = 0
        while (game.phase != RoundPhase.GameOver) {
            if (game.phase == RoundPhase.Aiming) {
                launchXs += game.launchX
                game.aim(angles[shot++ % angles.size])
                game.fire()
            }
            game.advance(frameSeconds(frame++))
            for (ball in game.balls) {
                if (ball.state != BallState.Flying) continue
                assertTrue(ball.x in 0f..config.columns.toFloat(), "ball left the field at x=${ball.x}")
                assertTrue(ball.y in 0f..config.rows.toFloat(), "ball left the field at y=${ball.y}")
            }
            events += game.drainEvents()
            if (frame > 2_000_000) fail("game never ended (round ${game.round})")
        }
        return Played(game, events, launchXs)
    }

    @Test
    fun aGamePlaysFromRoundOneToGameOver() {
        val played = play(seed = 2026)
        val game = played.game
        assertEquals(RoundPhase.GameOver, game.phase)
        assertTrue(game.round > 1, "the game should last more than one round")
        assertTrue(game.board.bricks().any { it.row == game.config.rows - 1 }, "a brick should be on the floor row")

        val gameOver = played.events.filterIsInstance<GameEvent.GameOver>().single()
        assertEquals(game.round, gameOver.round)
        assertEquals(game.points, gameOver.points)

        // One point per hit, wherever the hit came from.
        val hitsTaken = played.events.filterIsInstance<GameEvent.BrickHit>().size.toLong()
        assertEquals(hitsTaken, game.points)

        // One more ball every round.
        val rounds = played.events.filterIsInstance<GameEvent.RoundStarted>()
        rounds.forEachIndexed { i, started ->
            assertEquals(i + 1, started.round)
            assertEquals(started.round, started.ballCount)
        }
    }

    @Test
    fun eachRoundLaunchesFromWhereTheLastRoundsFirstBallLanded() {
        val played = play(seed = 7)
        val firstLandings = played.events.filterIsInstance<GameEvent.BallLanded>().filter { it.isFirst }.map { it.x }
        // Every round but the last fed the next round's launch point.
        assertEquals(played.launchXs.size, firstLandings.size)
        for (i in 1 until played.launchXs.size) {
            assertEquals(firstLandings[i - 1], played.launchXs[i], 1e-5f)
        }
    }

    @Test
    fun sameSeedAndAnglesGiveTheSameGameWhateverTheFrameRate() {
        val smooth = play(seed = 42) { 1f / 120 }
        val jittery = play(seed = 42) { frame -> listOf(0.016f, 0.004f, 0.05f, 0.0083f, 0.3f)[frame % 5] }
        assertEquals(smooth.game.points, jittery.game.points)
        assertEquals(smooth.game.round, jittery.game.round)
        assertEquals(smooth.game.steps, jittery.game.steps)
        assertEquals(smooth.events, jittery.events)
    }
}
