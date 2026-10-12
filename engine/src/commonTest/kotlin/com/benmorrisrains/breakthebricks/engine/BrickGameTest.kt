package com.benmorrisrains.breakthebricks.engine

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class BrickGameTest {
    private val angles = listOf(1.2f, 2.0f, 0.6f, 1.57f, 2.5f, 0.9f, 1.8f, 1.1f, 2.2f, 0.4f, 1.4f, 2.7f)

    /** Fires [angle] and runs frames of [frameSeconds] until the round is over. */
    private fun playRound(game: BrickGame, angle: Float, frameSeconds: (Int) -> Float = { 1f / 60 }) {
        game.aim(angle)
        game.fire()
        var frame = 0
        while (game.phase != RoundPhase.Aiming && game.phase != RoundPhase.GameOver) {
            game.advance(frameSeconds(frame++))
            if (frame > 200_000) fail("round ${game.round} never ended")
        }
    }

    private fun playGame(seed: Long, frameSeconds: (Int) -> Float = { 1f / 60 }): Pair<BrickGame, List<GameEvent>> {
        val game = BrickGame(seed = seed, physics = TestPhysics())
        val events = mutableListOf<GameEvent>()
        var i = 0
        while (game.phase != RoundPhase.GameOver && i < 60) {
            playRound(game, angles[i % angles.size], frameSeconds)
            events += game.drainEvents()
            i++
        }
        return game to events
    }

    private fun boardSnapshot(game: BrickGame) =
        game.board.bricks().map { Triple(it.column, it.row, it.hits) }.toList()

    @Test
    fun sameSeedAndInputsGiveTheSameGame() {
        val (a, eventsA) = playGame(seed = 2026)
        val (b, eventsB) = playGame(seed = 2026)
        assertTrue(a.points > 0, "the test game should score")
        assertEquals(a.points, b.points)
        assertEquals(a.round, b.round)
        assertEquals(a.phase, b.phase)
        assertEquals(a.steps, b.steps)
        assertEquals(eventsA, eventsB)
        assertEquals(boardSnapshot(a), boardSnapshot(b))
    }

    @Test
    fun frameTimingDoesNotChangeTheGame() {
        val (smooth, smoothEvents) = playGame(seed = 99) { 1f / 120 }
        val (jittery, jitteryEvents) = playGame(seed = 99) { frame -> listOf(0.004f, 0.031f, 0.0167f, 0.09f, 0.002f)[frame % 5] }
        assertEquals(smooth.points, jittery.points)
        assertEquals(smooth.round, jittery.round)
        assertEquals(smoothEvents, jitteryEvents)
        assertEquals(boardSnapshot(smooth), boardSnapshot(jittery))
    }

    @Test
    fun replayingFiredAnglesReproducesTheGame() {
        val (original, _) = playGame(seed = 7)
        val replay = BrickGame(seed = 7, physics = TestPhysics())
        for (angle in original.firedAngles) playRound(replay, angle)
        assertEquals(original.points, replay.points)
        assertEquals(original.round, replay.round)
        assertEquals(boardSnapshot(original), boardSnapshot(replay))
    }

    @Test
    fun differentSeedsGiveDifferentRows() {
        val layouts = (1L..10L).map { seed -> boardSnapshot(BrickGame(seed = seed, physics = TestPhysics())) }.toSet()
        assertTrue(layouts.size > 1)
    }

    @Test
    fun startsAimingAtRoundOneWithOneBallAndATopRow() {
        val config = GameConfig()
        val game = BrickGame(config, seed = 1, physics = TestPhysics())
        assertEquals(RoundPhase.Aiming, game.phase)
        assertEquals(1, game.round)
        assertEquals(1, game.ballCount)
        assertEquals(1, game.balls.size)
        assertEquals(0L, game.points)
        assertEquals(config.columns / 2f, game.launchX)
        val bricks = game.board.bricks().toList()
        assertTrue(bricks.size in config.minBricksPerRow..config.maxBricksPerRow)
        assertTrue(bricks.all { it.row == 0 && it.hits == 1L })
        assertEquals(listOf<GameEvent>(GameEvent.RoundStarted(1, 1)), game.drainEvents())
        assertEquals(emptyList(), game.drainEvents())
    }

    @Test
    fun oneMoreBallEveryRoundAndRowsFollowTheRowValues() {
        val game = BrickGame(seed = 3, physics = DropPhysics { 3.5f })
        for (expectedRound in 1..7) {
            assertEquals(expectedRound, game.round)
            assertEquals(expectedRound, game.ballCount)
            assertEquals(expectedRound, game.balls.size)
            val topRow = game.board.bricks().filter { it.row == 0 }.toList()
            assertTrue(topRow.isNotEmpty())
            assertTrue(topRow.all { it.hits == game.config.rowValues.valueFor(expectedRound) })
            playRound(game, 1.0f)
        }
    }

    @Test
    fun launchPointMovesToWhereTheFirstBallLanded() {
        val landings = listOf(1.25f, 5.5f, 6.0f, 2.0f, 0.5f, 4.0f)
        val game = BrickGame(seed = 5, physics = DropPhysics { landings[it % landings.size] })
        playRound(game, 1.0f)
        assertEquals(1.25f, game.launchX)
        playRound(game, 1.0f) // balls 2 and 3 land at 5.5 and 6.0
        assertEquals(5.5f, game.launchX)
        assertTrue(game.balls.all { it.x == 5.5f && it.state == BallState.Waiting })
        val landed = game.drainEvents().filterIsInstance<GameEvent.BallLanded>()
        assertEquals(listOf(true, true, false), landed.map { it.isFirst })
    }

    @Test
    fun landingPointStaysInsideTheField() {
        val game = BrickGame(seed = 5, physics = DropPhysics { -3f })
        playRound(game, 1.0f)
        assertEquals(game.config.ballRadius, game.launchX)
    }

    @Test
    fun gameEndsWhenABrickReachesTheFloorRow() {
        val config = GameConfig()
        val game = BrickGame(config, seed = 11, physics = DropPhysics { 3.5f })
        repeat(config.rows - 2) {
            playRound(game, 1.0f)
            assertEquals(RoundPhase.Aiming, game.phase)
        }
        playRound(game, 1.0f)
        assertEquals(RoundPhase.GameOver, game.phase)
        assertEquals(config.rows - 1, game.round)
        assertTrue(game.board.bricks().any { it.row == config.rows - 1 })
        assertEquals(GameEvent.GameOver(config.rows - 1, 0), game.drainEvents().last())

        // Nothing changes after the game is over.
        game.aim(0.5f)
        game.fire()
        game.advance(1f)
        assertEquals(RoundPhase.GameOver, game.phase)
        assertEquals(config.rows - 1, game.round)
    }

    @Test
    fun eachHitTakesOneOffABrickAndScoresOnePoint() {
        // Every ball hits the brick at column 0, row 0 once, then drops.
        val physics = BallPhysics { _, board, _, _, contacts ->
            if (board[0, 0] != null) contacts.brickHit(0, 0)
            true
        }
        val config = GameConfig(minBricksPerRow = 7, maxBricksPerRow = 7, rowValues = RowValues.Fibonacci)
        val game = BrickGame(config, seed = 1, physics = physics)
        playRound(game, 1.0f) // round 1: 1 ball, brick value 1
        assertEquals(1L, game.points)
        val round1 = game.drainEvents()
        assertTrue(GameEvent.BrickHit(0, 0, 0) in round1)
        assertTrue(GameEvent.BrickBroken(0, 0, 1) in round1)

        playRound(game, 1.0f) // round 2: 2 balls, new brick value 1 at (0, 0): one hit breaks it
        assertEquals(2L, game.points)
        playRound(game, 1.0f) // round 3: 3 balls, brick value 2: two hits break it, the third finds nothing
        assertEquals(4L, game.points)
    }

    @Test
    fun aimIsClampedAboveTheFloor() {
        val config = GameConfig()
        val game = BrickGame(config, seed = 1, physics = TestPhysics())
        game.aim(0f)
        assertEquals(config.minAimAngle, game.aimAngle)
        game.aim(PI.toFloat())
        assertEquals(PI.toFloat() - config.minAimAngle, game.aimAngle)
        game.aim(-1f)
        assertEquals(config.minAimAngle, game.aimAngle)
    }

    @Test
    fun aimAndFireAreIgnoredWhileBallsAreMoving() {
        val game = BrickGame(seed = 1, physics = BallPhysics { _, _, _, _, _ -> false })
        game.aim(1.0f)
        game.fire()
        game.advance(0.1f)
        assertNotEquals(RoundPhase.Aiming, game.phase)
        game.aim(2.0f)
        game.fire()
        assertEquals(1.0f, game.aimAngle)
        assertEquals(listOf(1.0f), game.firedAngles)
    }

    @Test
    fun ballsLeaveInAStreamAtTheFireInterval() {
        var landing = true
        val game = BrickGame(seed = 1, physics = BallPhysics { _, _, _, _, _ -> landing })
        repeat(3) { playRound(game, 1.0f) }
        assertEquals(4, game.ballCount)
        landing = false
        game.fire()
        val interval = 7 // 0.06 s at 120 steps per second, rounded
        game.step()
        assertEquals(1, game.balls.count { it.state == BallState.Flying })
        repeat(interval - 1) { game.step() }
        assertEquals(1, game.balls.count { it.state == BallState.Flying })
        game.step()
        assertEquals(2, game.balls.count { it.state == BallState.Flying })
        assertEquals(RoundPhase.Firing, game.phase)
        repeat(2 * interval) { game.step() }
        assertEquals(4, game.balls.count { it.state == BallState.Flying })
        assertEquals(RoundPhase.Returning, game.phase)
    }

    @Test
    fun longFramesAreCapped() {
        val game = BrickGame(seed = 1, physics = BallPhysics { _, _, _, _, _ -> false })
        game.fire()
        game.advance(10f)
        val maxSteps = (BrickGame.MAX_FRAME_SECONDS * GameConfig.STEPS_PER_SECOND).toLong()
        assertTrue(game.steps <= maxSteps, "ran ${game.steps} steps")
    }

    @Test
    fun abilitiesFireOnTheFirstBallsFirstHitOnly() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val ability = object : Ability {
            override val id = "test"
            override val earnedAtRound = 5
            override val wagerTargetRound = 10
            override fun onFirstHit(context: AbilityContext, column: Int, row: Int) {
                calls += column to row
                context.breakBrick(1, 0)
            }
        }
        val physics = BallPhysics { _, board, _, _, contacts ->
            if (board[0, 0] != null) contacts.brickHit(0, 0)
            true
        }
        val config = GameConfig(minBricksPerRow = 7, maxBricksPerRow = 7)
        val game = BrickGame(config, seed = 1, physics = physics, abilities = listOf(ability))
        playRound(game, 1.0f) // 1 ball: hits (0,0), ability breaks (1,0) for its 1 remaining point
        assertEquals(listOf(0 to 0), calls)
        assertEquals(2L, game.points)
        assertTrue(GameEvent.AbilityTriggered("test", 0, 0) in game.drainEvents())
        playRound(game, 1.0f) // 2 balls: only the first ball triggers
        assertEquals(listOf(0 to 0, 0 to 0), calls)
    }
}
