package com.benmorrisrains.breakthebricks.engine

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GridPhysicsTest {
    private val config = GameConfig()
    private val r = config.ballRadius
    private val eps = 1e-4f

    /** Records contacts and applies hits like the engine will: take 1 off, remove at 0. */
    private class Recorder(private val board: Board) : BallContacts {
        val hits = mutableListOf<Pair<Int, Int>>()
        var wallBounces = 0

        override fun brickHit(column: Int, row: Int) {
            hits += column to row
            val brick = board[column, row] ?: error("hit an empty cell $column, $row")
            brick.hits -= 1
            if (brick.hits == 0L) board[column, row] = null
        }

        override fun wallBounce() {
            wallBounces++
        }
    }

    /** Runs one physics step; true when the ball landed. */
    private fun step(
        ball: Ball,
        board: Board,
        seconds: Float = GameConfig.STEP_SECONDS,
        listener: BallContacts = Recorder(board),
    ): Boolean = GridPhysics.step(ball, board, config, seconds, listener)

    private fun board() = Board(config.columns, config.rows)

    private fun Board.put(column: Int, row: Int, hits: Long = 100) {
        this[column, row] = Brick(column, row, hits)
    }

    private fun speed(ball: Ball) = sqrt(ball.vx * ball.vx + ball.vy * ball.vy)

    private fun assertNear(expected: Float, actual: Float, message: String? = null) =
        assertTrue(abs(expected - actual) < eps, "${message ?: ""} expected $expected but was $actual")

    @Test
    fun movesInAStraightLineWithNothingInTheWay() {
        val ball = Ball(x = 3.5f, y = 5f, vx = 6f, vy = -12f)
        val landed = step(ball, board(), seconds = 0.1f)
        assertFalse(landed)
        assertNear(4.1f, ball.x)
        assertNear(3.8f, ball.y)
    }

    @Test
    fun bouncesOffTheLeftWall() {
        val board = board()
        val recorder = Recorder(board)
        val ball = Ball(x = 0.5f, y = 5f, vx = -10f, vy = 0f)
        step(ball, board, seconds = 0.1f, listener = recorder)
        // Travels 1 cell: 0.38 to the wall line at x = r, then 0.62 back.
        assertNear(r + 0.62f, ball.x)
        assertEquals(10f, ball.vx)
        assertEquals(1, recorder.wallBounces)
    }

    @Test
    fun bouncesOffTheRightWallAndCeiling() {
        val board = board()
        val recorder = Recorder(board)
        val ball = Ball(x = 6.5f, y = 0.5f, vx = 10f, vy = -10f)
        step(ball, board, seconds = 0.1f, listener = recorder)
        assertNear(config.columns - r - 0.62f, ball.x)
        assertNear(r + 0.62f, ball.y)
        assertEquals(-10f, ball.vx)
        assertEquals(10f, ball.vy)
        // Wall then ceiling at the same moment count as one bounce.
        assertEquals(1, recorder.wallBounces)
    }

    @Test
    fun landsOnTheFloorAndStops() {
        val ball = Ball(x = 2f, y = 8f, vx = 5f, vy = 10f)
        val landed = step(ball, board(), seconds = 0.5f)
        assertTrue(landed)
        assertEquals(GridPhysics.restingY(config), ball.y)
        assertNear(2f + 5f * (GridPhysics.restingY(config) - 8f) / 10f, ball.x)
        assertEquals(0f, ball.vx)
        assertEquals(0f, ball.vy)
    }

    @Test
    fun aBallLeavingTheFloorDoesNotLandAtOnce() {
        val ball = Ball(x = 3.5f, y = GridPhysics.restingY(config), vx = 3f, vy = -17f)
        assertFalse(step(ball, board()))
        assertTrue(ball.y < GridPhysics.restingY(config))
    }

    @Test
    fun bouncesDownOffTheBottomOfABrick() {
        val board = board().apply { put(3, 2, hits = 5) }
        val recorder = Recorder(board)
        val ball = Ball(x = 3.5f, y = 4f, vx = 0f, vy = -10f)
        step(ball, board, seconds = 0.2f, listener = recorder)
        assertEquals(listOf(3 to 2), recorder.hits)
        assertEquals(4L, board[3, 2]?.hits)
        assertEquals(10f, ball.vy)
        // Up to the brick's bottom face at 3 + r, then back down for the rest of the 2 cells.
        val toFace = 4f - (3f + r)
        assertNear(3f + r + (2f - toFace), ball.y)
    }

    @Test
    fun bouncesOffTheSideOfABrick() {
        val board = board().apply { put(4, 3) }
        val recorder = Recorder(board)
        val ball = Ball(x = 2.5f, y = 3.5f, vx = 10f, vy = 0f)
        step(ball, board, seconds = 0.2f, listener = recorder)
        assertEquals(listOf(4 to 3), recorder.hits)
        assertEquals(-10f, ball.vx)
        assertNear(4f - r - (2f - (4f - r - 2.5f)), ball.x)
    }

    @Test
    fun hitsCountOncePerContact() {
        val board = board().apply { put(3, 0, hits = 3) }
        val recorder = Recorder(board)
        // Straight up into a brick in the top row: one contact, one hit.
        val ball = Ball(x = 3.5f, y = 2f, vx = 0f, vy = -18f)
        step(ball, board, seconds = 0.05f, listener = recorder)
        assertEquals(1, recorder.hits.size)
        assertEquals(2L, board[3, 0]?.hits)
    }

    @Test
    fun brokenBricksAreNotSolid() {
        val board = board().apply { put(3, 2, hits = 1) }
        val recorder = Recorder(board)
        val ball = Ball(x = 3.5f, y = 4f, vx = 0f, vy = -10f)
        step(ball, board, seconds = 0.2f, listener = recorder)
        assertEquals(null, board[3, 2])
        // Once the brick is gone the ball flies straight through its cell.
        ball.vy = -10f
        step(ball, board, seconds = 0.2f, listener = recorder)
        assertEquals(1, recorder.hits.size)
        assertTrue(ball.y < 3f)

        board[2, 2] = Brick(2, 2, hits = 0)
        val passer = Ball(x = 2.5f, y = 4f, vx = 0f, vy = -10f)
        step(passer, board, seconds = 0.2f, listener = recorder)
        assertEquals(1, recorder.hits.size)
        assertEquals(-10f, passer.vy)
    }

    @Test
    fun cornerHitReflectsOffTheEdgeTheBallPushesHardest() {
        // Mostly upward, just catching the brick's bottom-left corner: flips vy only.
        val board = board().apply { put(3, 2) }
        val recorder = Recorder(board)
        val ball = Ball(x = 3f - r * 0.5f, y = 4.5f, vx = 0.5f, vy = -16f)
        step(ball, board, seconds = 0.1f, listener = recorder)
        assertEquals(listOf(3 to 2), recorder.hits)
        assertEquals(0.5f, ball.vx)
        assertEquals(16f, ball.vy)

        // Mostly sideways into the same corner: flips vx only.
        val sideways = Ball(x = 2f, y = 3f + r * 0.5f, vx = 16f, vy = -0.5f)
        step(sideways, board, seconds = 0.1f, listener = recorder)
        assertEquals(2, recorder.hits.size)
        assertEquals(-16f, sideways.vx)
        assertEquals(-0.5f, sideways.vy)
    }

    @Test
    fun cornerHitAlwaysLeavesTheCorner() {
        // Diagonal hits on all four corners of a lone brick: each one bounces away, never sticks.
        val directions = listOf(1f to 1f, -1f to 1f, 1f to -1f, -1f to -1f)
        for ((dx, dy) in directions) {
            val board = board().apply { put(3, 4) }
            val recorder = Recorder(board)
            val cornerX = 3.5f - dx * 0.5f
            val cornerY = 4.5f - dy * 0.5f
            val ball = Ball(x = cornerX - dx * 1.2f, y = cornerY - dy * 1.0f, vx = dx * 12f, vy = dy * 10f)
            step(ball, board, seconds = 0.15f, listener = recorder)
            assertEquals(1, recorder.hits.size, "corner $dx,$dy")
            val awayX = (ball.x - cornerX) * ball.vx
            val awayY = (ball.y - cornerY) * ball.vy
            assertTrue(awayX + awayY > 0f, "ball still heading into corner $dx,$dy")
        }
    }

    @Test
    fun aBallOnTheSeamOfTwoTouchingBricksHitsBothAndReflectsOnce() {
        val board = board().apply {
            put(2, 3)
            put(3, 3)
        }
        val recorder = Recorder(board)
        val ball = Ball(x = 3f, y = 6f, vx = 0f, vy = -10f)
        step(ball, board, seconds = 0.3f, listener = recorder)
        assertEquals(listOf(2 to 3, 3 to 3), recorder.hits)
        assertEquals(0f, ball.vx)
        assertEquals(10f, ball.vy)
    }

    @Test
    fun neverSlipsBetweenTwoTouchingBricks() {
        // Aim at points just either side of the seam, at shallow and steep angles.
        for (offset in listOf(-0.01f, -0.0001f, 0.0001f, 0.01f)) {
            for (vx in listOf(-3f, 0f, 3f)) {
                val board = board().apply {
                    put(2, 3)
                    put(3, 3)
                }
                val recorder = Recorder(board)
                val ball = Ball(x = 3f + offset, y = 4f + r + 1e-3f, vx = vx, vy = -17f)
                step(ball, board, seconds = 0.05f, listener = recorder)
                assertEquals(1, recorder.hits.size, "offset $offset vx $vx")
                assertTrue(ball.vy > 0f, "offset $offset vx $vx went through")
                assertTrue(ball.y > 4f, "offset $offset vx $vx")
            }
        }
    }

    @Test
    fun innerCornerHitsBothSurfacesAndReversesTheBall() {
        // A brick hugging the left wall, ball coming diagonally into the angle under it.
        val board = board().apply { put(0, 2) }
        val recorder = Recorder(board)
        val ball = Ball(x = r + 1f, y = 3f + r + 1f, vx = -10f, vy = -10f)
        step(ball, board, seconds = 0.15f, listener = recorder)
        assertEquals(listOf(0 to 2), recorder.hits)
        assertEquals(1, recorder.wallBounces)
        assertEquals(10f, ball.vx)
        assertEquals(10f, ball.vy)

        // Two bricks forming an L: one above the ball's target point, one to its left.
        val lBoard = board().apply {
            put(3, 2)
            put(2, 3)
        }
        val lRecorder = Recorder(lBoard)
        val lBall = Ball(x = 3f + r + 1f, y = 3f + r + 1f, vx = -10f, vy = -10f)
        step(lBall, lBoard, seconds = 0.15f, listener = lRecorder)
        assertEquals(listOf(3 to 2, 2 to 3), lRecorder.hits)
        assertEquals(10f, lBall.vx)
        assertEquals(10f, lBall.vy)
    }

    @Test
    fun fastBallsDoNotTunnel() {
        // 2000 cells per second covers the whole board many times in one step.
        val board = board().apply { put(3, 2, hits = 1_000_000) }
        val recorder = Recorder(board)
        val ball = Ball(x = 3.5f, y = 6f, vx = 0f, vy = -2000f)
        step(ball, board, listener = recorder)
        assertTrue(recorder.hits.isNotEmpty())
        assertTrue(ball.y > 3f, "tunneled to ${ball.y}")
        // Bounced between brick and floor: it landed at the floor without passing the brick.
        assertTrue(ball.y <= GridPhysics.restingY(config))
    }

    @Test
    fun fastBallsCannotPassAThinWallOfBricks() {
        val board = board()
        for (column in 0 until config.columns) board.put(column, 4, hits = 1_000_000)
        val recorder = Recorder(board)
        val random = SeededRandom(7)
        repeat(500) {
            val angle = 0.2f + random.nextFloat() * 2.7f
            val ball = Ball(
                x = r + random.nextFloat() * (config.columns - 2 * r),
                y = 7f,
                vx = cos(angle) * 400f,
                vy = -sin(angle) * 400f,
            )
            var steps = 0
            while (steps++ < 20) {
                if (step(ball, board, listener = recorder)) break
                assertTrue(ball.y > 5f, "ball passed the wall at ${ball.x}, ${ball.y}")
            }
        }
    }

    @Test
    fun longRunsKeepTheBallInsideTheFieldAndOutOfBricks() {
        val random = SeededRandom(2026)
        val board = board()
        for (row in 0 until 6) {
            for (column in 0 until config.columns) {
                if (random.nextInt(3) != 0) board.put(column, row, hits = 1_000_000)
            }
        }
        val recorder = Recorder(board)
        repeat(40) {
            val angle = config.minAimAngle + random.nextFloat() * (kotlin.math.PI.toFloat() - 2 * config.minAimAngle)
            val ball = Ball(
                x = 3.5f,
                y = GridPhysics.restingY(config),
                vx = cos(angle) * config.ballSpeed,
                vy = -sin(angle) * config.ballSpeed,
            )
            val startSpeed = speed(ball)
            var steps = 0
            while (steps++ < GameConfig.STEPS_PER_SECOND * 20) {
                if (step(ball, board, listener = recorder)) break
                assertTrue(abs(speed(ball) - startSpeed) < 1e-3f, "speed drifted")
                assertTrue(ball.x >= r - eps && ball.x <= config.columns - r + eps, "left the field at ${ball.x}")
                assertTrue(ball.y >= r - eps, "went through the ceiling at ${ball.y}")
                assertOutsideBricks(ball, board)
            }
        }
        assertTrue(recorder.hits.isNotEmpty())
    }

    private fun assertOutsideBricks(ball: Ball, board: Board) {
        for (brick in board.bricks()) {
            val nearestX = ball.x.coerceIn(brick.column.toFloat(), brick.column + 1f)
            val nearestY = ball.y.coerceIn(brick.row.toFloat(), brick.row + 1f)
            val dx = ball.x - nearestX
            val dy = ball.y - nearestY
            assertTrue(
                sqrt(dx * dx + dy * dy) >= r - eps,
                "ball at ${ball.x}, ${ball.y} is inside brick ${brick.column}, ${brick.row}",
            )
        }
    }

    @Test
    fun sameStartGivesTheSameFlight() {
        fun fly(): List<Float> {
            val board = board()
            val random = SeededRandom(99)
            for (row in 0 until 5) for (column in 0 until config.columns) {
                if (random.nextInt(2) == 0) board.put(column, row, hits = 3)
            }
            val recorder = Recorder(board)
            val ball = Ball(x = 3.5f, y = GridPhysics.restingY(config), vx = 7f, vy = -16.5f)
            val trace = mutableListOf<Float>()
            repeat(GameConfig.STEPS_PER_SECOND * 10) {
                step(ball, board, listener = recorder)
                trace += ball.x
                trace += ball.y
            }
            trace += recorder.hits.size.toFloat()
            return trace
        }
        assertEquals(fly(), fly())
    }

    @Test
    fun zeroTimeOrZeroVelocityLeavesTheBallAlone() {
        val ball = Ball(x = 1f, y = 1f, vx = 5f, vy = 5f)
        step(ball, board(), seconds = 0f)
        assertEquals(1f, ball.x)
        val still = Ball(x = 1f, y = 1f)
        assertFalse(step(still, board()))
        assertEquals(1f, still.y)
    }
}
