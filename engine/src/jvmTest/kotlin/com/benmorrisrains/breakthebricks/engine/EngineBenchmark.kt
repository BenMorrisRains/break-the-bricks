package com.benmorrisrains.breakthebricks.engine

import java.lang.management.ManagementFactory
import kotlin.test.Test

/**
 * Rough timings for the engine's hot paths, printed to the test log. Skipped unless the
 * environment has BENCH=1, so CI stays fast:
 *
 *     BENCH=1 ./gradlew :engine:jvmTest --tests '*EngineBenchmark*' -i
 */
class EngineBenchmark {
    private val enabled = System.getenv("BENCH") == "1"
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    /** [balls] balls flying through a full board of bricks that never break, one physics step each. */
    @Test
    fun physicsStepsWithManyBalls() {
        if (!enabled) return
        val config = GameConfig()
        val board = Board(config.columns, config.rows)
        // A checkerboard of bricks so balls bounce constantly, with empty floor rows to fall into.
        for (row in 0 until config.rows - 2) for (column in 0 until config.columns) {
            if ((row + column) % 2 == 0) board[column, row] = Brick(column, row, Long.MAX_VALUE)
        }
        val contacts = object : BallContacts {
            override fun brickHit(column: Int, row: Int) = Unit
            override fun wallBounce() = Unit
        }
        for (count in listOf(50, 200)) {
            val random = SeededRandom(count.toLong())
            val balls = List(count) {
                val angle = 0.3f + random.nextFloat() * 2.5f
                Ball(
                    x = 0.5f + random.nextFloat() * 6f,
                    y = config.rows - 1.5f,
                    vx = kotlin.math.cos(angle) * config.ballSpeed,
                    vy = -kotlin.math.sin(angle) * config.ballSpeed,
                )
            }
            measure("physics, $count balls", steps = 20_000) {
                for (ball in balls) {
                    if (GridPhysics.step(ball, board, config, GameConfig.STEP_SECONDS, contacts)) {
                        // Relaunch a landed ball upward so the field stays busy.
                        ball.vx = -ball.vx.coerceAtLeast(1f)
                        ball.vy = -config.ballSpeed * 0.9f
                    }
                }
            }
        }
    }

    /** Whole games from round 1 to game over, the way the app runs them. */
    @Test
    fun fullGames() {
        if (!enabled) return
        val angles = floatArrayOf(1.3f, 0.5f, 2.4f, 1.0f, 1.9f, 0.3f, 2.8f, 1.57f, 0.8f, 2.1f)
        fun playAll(): Long {
            var steps = 0L
            for (seed in 1L..40L) {
                val game = BrickGame(seed = seed, physics = GridPhysics)
                var shot = 0
                while (!game.phase.isFinished) {
                    if (game.phase == RoundPhase.Aiming) {
                        game.aim(angles[shot++ % angles.size])
                        game.fire()
                    }
                    game.advance(1f / 60)
                    game.drainEvents()
                }
                steps += game.steps
            }
            return steps
        }
        playAll() // Warm up the JIT.
        val startBytes = threads.currentThreadAllocatedBytes
        val start = System.nanoTime()
        val steps = playAll()
        val nanos = System.nanoTime() - start
        val bytes = threads.currentThreadAllocatedBytes - startBytes
        println("BENCH full games: $steps fixed steps, ${nanos / steps / 1000.0} us/step, ${bytes / steps} bytes allocated/step")
    }

    private fun measure(name: String, steps: Int, repeat: Int = 5, body: () -> Unit) {
        repeat(steps / 4) { body() } // Warm up the JIT.
        var bestNanos = Long.MAX_VALUE
        var bytes = 0L
        repeat(repeat) {
            val startBytes = threads.currentThreadAllocatedBytes
            val start = System.nanoTime()
            repeat(steps) { body() }
            val nanos = System.nanoTime() - start
            bytes = threads.currentThreadAllocatedBytes - startBytes
            bestNanos = minOf(bestNanos, nanos)
        }
        println("BENCH $name: ${bestNanos / steps / 1000.0} us/step, ${bytes / steps} bytes allocated/step")
    }
}
