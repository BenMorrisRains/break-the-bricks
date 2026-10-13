package com.benmorrisrains.breakthebricks.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/** Ben's 2026-10-13 power-up changes: the drill v2, ghost ball, time freeze and 5-way splitter. */
class NewPowerUpsTest {
    private val straightUp = (PI / 2).toFloat()

    private fun playRound(game: BrickGame, angle: Float = straightUp, eachStep: () -> Unit = {}) {
        game.aim(angle)
        game.fire()
        var steps = 0
        while (game.phase != RoundPhase.Aiming && !game.phase.isFinished) {
            game.step()
            eachStep()
            if (steps++ > 200_000) fail("round ${game.round} never ended")
        }
    }

    /** A round-1 game with [abilities] active and its board replaced by [bricks] (column, row, hits). */
    private fun gameWith(
        abilities: List<Ability>,
        vararg bricks: Triple<Int, Int, Long>,
        config: GameConfig = GameConfig(),
        seed: Long = 1,
    ): BrickGame {
        val game = BrickGame(config, seed = seed, physics = GridPhysics, abilities = abilities, earnable = emptyList())
        game.board.clear()
        for ((column, row, hits) in bricks) game.board[column, row] = Brick(column, row, hits)
        game.drainEvents()
        return game
    }

    private fun speed(ball: Ball) = sqrt(ball.vx * ball.vx + ball.vy * ball.vy)

    // Drill

    @Test
    fun drillBreaksEveryBrickItTouchesAndLeavesThroughTheTop() {
        val column = (1..5).map { Triple(3, it, 50L) }
        val game = gameWith(listOf(Drill), *column.toTypedArray())
        var leftAt: Float? = null
        playRound(game) {
            if (leftAt == null && game.balls.first().state == BallState.Gone) leftAt = game.balls.first().y
        }
        val events = game.drainEvents()
        assertEquals((1..5).map { 3 to it }.toSet(), events.filterIsInstance<GameEvent.BrickBroken>().map { it.column to it.row }.toSet())
        assertEquals(250L, game.points, "every brick scores all its hits")
        assertEquals(1, events.count { it is GameEvent.AbilityTriggered })
        assertTrue(events.none { it is GameEvent.BallLanded }, "the drill never comes back down")
        assertEquals(game.config.ballRadius, leftAt!!, 1e-4f)
        assertEquals(3.5f, game.launchX, "with no ball landed, the launch point stays put")
    }

    @Test
    fun drillStillLeavesAfterBouncingOffTheSideWalls() {
        val game = gameWith(listOf(Drill), Triple(5, 4, 30L), Triple(1, 2, 30L))
        playRound(game, angle = 0.5f)
        val events = game.drainEvents()
        assertTrue(events.any { it == GameEvent.WallBounce }, "it bounced off a side wall")
        assertTrue(events.none { it is GameEvent.BallLanded })
    }

    @Test
    fun theNextBallToLandSetsTheLaunchPointWhenTheDrillLeaves() {
        val game = gameWith(listOf(Drill), Triple(3, 4, 50L))
        playRound(game)
        game.drainEvents()
        assertEquals(2, game.ballCount)
        playRound(game, angle = 1.2f)
        val landings = game.drainEvents().filterIsInstance<GameEvent.BallLanded>()
        assertEquals(1, landings.size, "only the second ball lands")
        assertTrue(landings.single().isFirst)
        assertEquals(landings.single().x, game.launchX)
        assertTrue(game.balls.all { it.state == BallState.Waiting && !it.exitsAtCeiling }, "the next round starts clean")
    }

    @Test
    fun onlyTheDrillBallLeavesThroughTheTop() {
        val game = gameWith(listOf(Drill), Triple(3, 4, 50L))
        playRound(game)
        playRound(game)
        game.selectAbility(Drill.id)
        val gone = mutableSetOf<Int>()
        playRound(game) { game.balls.forEachIndexed { i, b -> if (b.state == BallState.Gone) gone += i } }
        assertEquals(setOf(0), gone)
    }

    // Ghost ball

    /** A pocket at (3, 4): bricks above, left, right and, at (3, 5), below it. */
    private fun pocket(bottomHits: Long) = arrayOf(
        Triple(3, 5, bottomHits), Triple(3, 3, 10L),
        Triple(2, 3, 10L), Triple(2, 4, 10L), Triple(2, 5, 10L),
        Triple(4, 3, 10L), Triple(4, 4, 10L), Triple(4, 5, 10L),
    )

    @Test
    fun ghostPassesThroughABrickThenBouncesAroundInsideTheGroup() {
        val game = gameWith(listOf(GhostBall), *pocket(bottomHits = 3))
        playRound(game)
        // In through (3, 5), then up and down inside the pocket: (3, 3), (3, 5), (3, 3), (3, 5)
        // again, which breaks it, and one last bounce off (3, 3) before the ball falls out through
        // the gap. The bricks have since dropped a row.
        assertEquals(null, game.board[3, 6])
        assertEquals(7L, game.board[3, 4]?.hits)
        assertEquals(6L, game.points)
        assertTrue(game.balls.none { it.ghost })
    }

    @Test
    fun aNormalBallJustBouncesOffTheGroup() {
        val game = gameWith(emptyList(), *pocket(bottomHits = 3))
        playRound(game)
        assertEquals(2L, game.board[3, 6]?.hits)
        assertEquals(10L, game.board[3, 4]?.hits)
        assertEquals(1L, game.points)
    }

    @Test
    fun ghostPassesThroughAWholeStackToReachOpenSpace() {
        val game = gameWith(listOf(GhostBall), Triple(3, 5, 9L), Triple(3, 4, 9L), Triple(3, 3, 9L), Triple(3, 1, 2L))
        var hitsBeforeSolid = -1
        var wasGhost = false
        playRound(game) {
            val ball = game.balls.first()
            if (wasGhost && !ball.ghost && hitsBeforeSolid < 0) hitsBeforeSolid = game.points.toInt()
            wasGhost = ball.ghost
        }
        assertEquals(3, hitsBeforeSolid, "one hit on each brick of the stack while a ghost")
    }

    // 5-way splitter

    @Test
    fun theFiveWaySplitterSplitsTheFirstBallIntoFive() {
        val game = gameWith(listOf(SplitterFive), Triple(3, 2, 50L))
        var mostBalls = 0
        val headings = mutableListOf<Float>()
        playRound(game) {
            if (game.balls.size > mostBalls) {
                mostBalls = game.balls.size
                headings += game.balls.map { atan2(it.vy, it.vx) }
            }
        }
        assertEquals(5, mostBalls)
        assertEquals(5, headings.takeLast(5).toSet().size, "all five fly different ways")
        assertEquals(2, game.ballCount, "round 2 brings one more ball")
        assertEquals(2, game.balls.size, "the split balls only last the round")
    }

    // Time freeze

    private fun frozenGame(config: GameConfig = GameConfig()): BrickGame {
        val game = gameWith(listOf(TimeFreeze), Triple(3, 2, 50L), config = config)
        game.aim(straightUp)
        game.fire()
        var steps = 0
        while (!game.timeFrozen) {
            game.step()
            if (steps++ > 10_000) fail("the freeze never started")
        }
        return game
    }

    @Test
    fun theFirstHitSlowsPlayForThreeSeconds() {
        // A tall field so the slowed ball is still in the air when the freeze runs out.
        val game = frozenGame(GameConfig(rows = 30))
        val ball = game.balls.first()
        val y = ball.y
        game.step()
        val slowStep = abs(ball.y - y)
        assertEquals(game.config.ballSpeed * GameConfig.STEP_SECONDS * TimeFreeze.TIME_SCALE, slowStep, 1e-4f)
        repeat(TimeFreeze.STEPS - 2) { game.step() }
        assertTrue(game.timeFrozen)
        game.step()
        assertFalse(game.timeFrozen, "the freeze lasts ${TimeFreeze.STEPS} steps")
        val y2 = ball.y
        game.step()
        assertEquals(game.config.ballSpeed * GameConfig.STEP_SECONDS, abs(ball.y - y2), 1e-4f)
    }

    @Test
    fun aSwipeSendsEveryFlyingBallTheNewWayAndEndsTheFreeze() {
        val game = frozenGame()
        game.redirect(0.6f)
        assertFalse(game.timeFrozen)
        val ball = game.balls.first()
        assertEquals(0.6f, atan2(-ball.vy, ball.vx), 1e-4f)
        assertEquals(game.config.ballSpeed, speed(ball), 1e-3f)
        assertEquals(1, game.redirects.size)
        game.redirect(2.0f)
        assertEquals(1, game.redirects.size, "one swipe per freeze")
        assertEquals(0.6f, atan2(-ball.vy, ball.vx), 1e-4f)
    }

    @Test
    fun aSwipeCanSendTheBallsDownButNeverFlat() {
        val game = frozenGame()
        game.redirect(-0.01f)
        val ball = game.balls.first()
        assertEquals(-game.config.minAimAngle, atan2(-ball.vy, ball.vx), 1e-4f)
    }

    @Test
    fun redirectDoesNothingOutsideAFreeze() {
        val game = gameWith(listOf(TimeFreeze), Triple(3, 2, 50L))
        game.aim(straightUp)
        game.fire()
        repeat(3) { game.step() }
        val ball = game.balls.first()
        val vx = ball.vx
        game.redirect(0.5f)
        assertEquals(vx, ball.vx)
        assertTrue(game.redirects.isEmpty())
    }

    @Test
    fun ballsStillWaitingStayPutDuringTheFreeze() {
        // A long gap between balls so the second is still waiting when the first hits.
        val config = GameConfig(fireInterval = 2f)
        val game = gameWith(listOf(TimeFreeze), Triple(3, 2, 50L), config = config)
        playRound(game)
        // Round 2 has 2 balls.
        game.drainEvents()
        game.aim(straightUp)
        game.fire()
        var stepsFrozen = 0
        while (game.phase != RoundPhase.Aiming) {
            game.step()
            if (game.timeFrozen) {
                stepsFrozen++
                assertEquals(BallState.Waiting, game.balls[1].state)
            }
        }
        assertEquals(TimeFreeze.STEPS, stepsFrozen)
        assertTrue(game.drainEvents().filterIsInstance<GameEvent.BallLanded>().size == 2)
    }

    @Test
    fun theFreezeEndsWithTheRound() {
        val game = gameWith(listOf(TimeFreeze), Triple(3, 6, 50L))
        playRound(game)
        assertFalse(game.timeFrozen)
    }

    @Test
    fun replayingAnglesAndSwipesReproducesTheGame() {
        val angles = listOf(1.2f, 2.0f, 0.6f, 1.57f, 2.5f, 0.9f, 1.8f)
        val swipes = listOf(0.4f, 2.6f, -1.0f, 1.3f, -2.2f)
        fun play(game: BrickGame, swipeAt: (Long) -> Float?): List<GameEvent> {
            val events = mutableListOf<GameEvent>()
            var i = 0
            while (!game.phase.isFinished && i < 25) {
                game.aim(angles[i++ % angles.size])
                game.fire()
                while (game.phase != RoundPhase.Aiming && !game.phase.isFinished) {
                    swipeAt(game.steps)?.let(game::redirect)
                    game.step()
                }
                events += game.drainEvents()
            }
            return events
        }
        val original = BrickGame(seed = 7, physics = GridPhysics, abilities = listOf(TimeFreeze))
        var frozenFor = 0
        val originalEvents = play(original) {
            if (!original.timeFrozen) {
                frozenFor = 0
                null
            } else if (frozenFor++ == 40) {
                swipes[original.redirects.size % swipes.size]
            } else {
                null
            }
        }
        assertTrue(original.redirects.size >= 3, "the test game should swipe a few times")
        val replay = BrickGame(seed = 7, physics = GridPhysics, abilities = listOf(TimeFreeze))
        val byStep = original.redirects.toMap()
        val replayEvents = play(replay) { byStep[it] }
        assertEquals(originalEvents, replayEvents)
        assertEquals(original.points, replay.points)
        assertEquals(original.redirects, replay.redirects)
    }
}
