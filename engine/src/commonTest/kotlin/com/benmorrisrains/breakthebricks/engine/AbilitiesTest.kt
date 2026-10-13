package com.benmorrisrains.breakthebricks.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Ben's lineup (2026-10-13): big ball 5, splitter 10, drill 20, grenade 30; wager targets 5 later. */
class AbilitiesTest {
    private fun playRound(game: BrickGame, angle: Float = 1.0f, eachStep: () -> Unit = {}) {
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
    private fun gameWith(abilities: List<Ability>, physics: BallPhysics, vararg bricks: Triple<Int, Int, Long>): BrickGame {
        val game = BrickGame(seed = 1, physics = physics, abilities = abilities, earnable = emptyList())
        game.board.clear()
        for ((column, row, hits) in bricks) game.board[column, row] = Brick(column, row, hits)
        game.drainEvents()
        return game
    }

    private val straightUp = (PI / 2).toFloat()

    @Test
    fun theLineupHasBensRoundsAndIds() {
        assertEquals(listOf(BigBall, Splitter, Drill, GrenadeBall), Abilities.earnable)
        assertEquals(listOf("big_ball", "splitter", "drill", "grenade"), Abilities.earnable.map { it.id })
        assertEquals(listOf(5, 10, 20, 30), Abilities.earnable.map { it.earnedAtRound })
        assertEquals(listOf(10, 15, 25, 35), Abilities.earnable.map { it.wagerTargetRound })
    }

    @Test
    fun eachAbilityIsEarnedAtItsRoundAndStaysActive() {
        // A tall board so a game that never breaks a brick lasts past round 30.
        val game = BrickGame(GameConfig(rows = 40), seed = 3, physics = DropPhysics { 3.5f })
        val earned = game.drainEvents().filterIsInstance<GameEvent.AbilityEarned>().toMutableList()
        while (game.round < 31) {
            playRound(game)
            earned += game.drainEvents().filterIsInstance<GameEvent.AbilityEarned>()
            val expected = Abilities.earnable.filter { it.earnedAtRound <= game.round }
            assertEquals(expected, game.activeAbilities, "round ${game.round}")
        }
        assertEquals(
            listOf(
                GameEvent.AbilityEarned("big_ball", 5),
                GameEvent.AbilityEarned("splitter", 10),
                GameEvent.AbilityEarned("drill", 20),
                GameEvent.AbilityEarned("grenade", 30),
            ),
            earned,
        )
    }

    @Test
    fun bigBallMakesOnlyTheFirstBallTwiceTheSizeForOneFlight() {
        val sizes = mutableListOf<Pair<Int, Float>>()
        lateinit var game: BrickGame
        // Each ball flies for one step and lands; record which ball it was and its size.
        val physics = BallPhysics { ball, _, _, _, _ ->
            sizes += game.balls.indexOf(ball) to ball.size
            true
        }
        game = gameWith(listOf(BigBall), physics)
        playRound(game)
        playRound(game)
        assertEquals(listOf(0 to BigBall.SIZE, 0 to BigBall.SIZE, 1 to 1f), sizes)
        assertTrue(game.balls.all { it.size == 1f }, "balls are normal size again once landed")
    }

    @Test
    fun aBigBallLeavesFromTheFloorClearOfTheWall() {
        val launched = mutableListOf<Pair<Float, Float>>()
        val physics = BallPhysics { ball, _, _, _, _ ->
            launched += ball.x to ball.y
            ball.x = 0f // land against the left wall
            true
        }
        val game = gameWith(listOf(BigBall), physics)
        playRound(game)
        val r = game.config.ballRadius
        assertEquals(r, game.launchX)
        playRound(game)
        // The big ball rests on the floor and is pushed off the wall; the normal ball isn't moved.
        val bigR = game.config.ballRadius * BigBall.SIZE
        assertEquals(bigR, launched[1].first)
        assertEquals(game.config.rows - bigR, launched[1].second)
        assertEquals(r to game.config.rows - r, launched[2])
    }

    @Test
    fun theEngineHandsThePhysicsTheBigBallsRadius() {
        // Straight up into a brick: the big ball turns back at its bottom face plus its bigger radius.
        fun turnBackY(abilities: List<Ability>): Float {
            val game = gameWith(abilities, GridPhysics, Triple(3, 2, 50L))
            val firstBall = game.balls.first()
            var turnedAt = Float.NaN
            playRound(game, straightUp) {
                if (turnedAt.isNaN() && firstBall.vy > 0f) turnedAt = firstBall.y
            }
            return turnedAt
        }
        val r = GameConfig().ballRadius
        val step = GameConfig().ballSpeed * GameConfig.STEP_SECONDS
        val normal = turnBackY(emptyList())
        val big = turnBackY(listOf(BigBall))
        assertTrue(normal - (3f + r) in 0f..step, "normal ball turned at $normal")
        assertTrue(big - (3f + BigBall.SIZE * r) in 0f..step, "big ball turned at $big")
    }

    /** Records what an ability does to the board; the board itself is left alone. */
    private class RecordingContext : AbilityContext {
        override val board = Board(7, 9)
        override val config = GameConfig()
        val damaged = mutableListOf<Pair<Int, Int>>()
        override fun damage(column: Int, row: Int, hits: Long) {
            damaged += column to row
        }
        override fun breakBrick(column: Int, row: Int) = Unit
        override fun splitFirstBall(extraBalls: Int, maxTurnRadians: Float) = Unit
    }

    private fun bigBallHit(x: Float, y: Float, column: Int, row: Int): List<Pair<Int, Int>> {
        val context = RecordingContext()
        BigBall.onFirstBallHit(context, Ball(x, y, size = BigBall.SIZE), column, row)
        return context.damaged
    }

    @Test
    fun aBigBallOverhangingTheNextBrickHitsItToo() {
        val r = GameConfig().ballRadius * BigBall.SIZE
        // Under brick (3, 2), touching its bottom face (y = 3 + r).
        assertEquals(listOf(2 to 2), bigBallHit(3.2f, 3f + r, 3, 2))
        assertEquals(listOf(4 to 2), bigBallHit(3.8f, 3f + r, 3, 2))
        assertEquals(emptyList(), bigBallHit(3.5f, 3f + r, 3, 2))
        // On top of brick (3, 2) and against its left and right sides.
        assertEquals(listOf(4 to 2), bigBallHit(3.9f, 2f - r, 3, 2))
        assertEquals(listOf(3 to 1), bigBallHit(3f - r, 2.1f, 3, 2))
        assertEquals(listOf(3 to 3), bigBallHit(4f + r, 2.9f, 3, 2))
        // Corner hits, and hits exactly on a seam (both bricks are already hit), add nothing.
        assertEquals(emptyList(), bigBallHit(2.9f, 3.1f, 3, 2))
        assertEquals(emptyList(), bigBallHit(3f, 3f + r, 3, 2))
    }

    @Test
    fun aBigBallHitsTwoBricksAtOnceInAGame() {
        // From the middle of the floor, slightly right of straight up, so the ball meets the
        // bottom of brick (3, 2) near its right edge, hanging over brick (4, 2).
        fun play(abilities: List<Ability>): BrickGame {
            val game = gameWith(abilities, GridPhysics, Triple(3, 2, 50L), Triple(4, 2, 50L))
            playRound(game, 1.505f)
            return game
        }
        val big = play(listOf(BigBall))
        assertEquals(2L, big.points)
        // Both bricks took one hit; the drop moved them to row 3.
        assertEquals(49L, big.board[3, 3]?.hits)
        assertEquals(49L, big.board[4, 3]?.hits)
        val normal = play(emptyList())
        assertEquals(1L, normal.points)
        assertEquals(50L, normal.board[4, 3]?.hits)
    }

    @Test
    fun splitterSplitsTheFirstBallIntoThreeOnItsFirstHit() {
        val game = gameWith(listOf(Splitter), GridPhysics, Triple(3, 2, 50L))
        var mostBalls = 0
        var splitVelocities: List<Pair<Float, Float>> = emptyList()
        playRound(game, straightUp) {
            if (game.balls.size > mostBalls) {
                mostBalls = game.balls.size
                splitVelocities = game.balls.map { it.vx to it.vy }
            }
        }
        assertEquals(3, mostBalls)
        assertEquals(1, game.drainEvents().count { it is GameEvent.AbilityTriggered })
        // It bounced straight down; all three fly at the same speed, each turned from straight
        // down by up to the max turn, one in each third of that range so they fan out.
        val speed = game.config.ballSpeed
        val max = Splitter.MAX_TURN_RADIANS
        val turns = splitVelocities.map { (vx, vy) ->
            assertTrue(abs(sqrt(vx * vx + vy * vy) - speed) < 1e-3f)
            atan2(vy, vx) - (PI / 2).toFloat()
        }
        assertTrue(turns.all { it in -max..max }, "turns $turns")
        assertEquals(setOf(0, 1, 2), turns.map { floor((it + max) / (2 * max / 3)).toInt().coerceIn(0, 2) }.toSet())
        // Next round there are 2 balls again, not 4.
        assertEquals(2, game.balls.size)
        assertEquals(2, game.ballCount)
    }

    @Test
    fun onlyTheFirstBallSplitsAndOnlyOnce() {
        val game = gameWith(listOf(Splitter), GridPhysics, Triple(3, 2, 500L), Triple(2, 2, 500L), Triple(4, 2, 500L))
        playRound(game, straightUp)
        game.drainEvents()
        var mostBalls = 0
        playRound(game, straightUp) { mostBalls = maxOf(mostBalls, game.balls.size) }
        // Round 2 fires 2 balls; the first one adds 2 more, and none of the others split.
        assertEquals(4, mostBalls)
        assertEquals(1, game.drainEvents().count { it is GameEvent.AbilityTriggered })
        assertEquals(3, game.balls.size)
    }

    @Test
    fun splitBallsNeverFlyFlatterThanTheSmallestAimAngle() {
        lateinit var game: BrickGame
        // The first ball hits while flying nearly flat, so most turns would leave a ball flatter.
        val physics = BallPhysics { ball, _, config, _, contacts ->
            ball.vx = cos(0.05f) * config.ballSpeed
            ball.vy = -sin(0.05f) * config.ballSpeed
            contacts.brickHit(0, 0)
            false
        }
        game = gameWith(listOf(Splitter), physics, Triple(0, 0, 50L))
        game.aim(1f)
        game.fire()
        game.step() // the first ball leaves, hits and splits; the new balls haven't moved yet
        val config = game.config
        val minVy = sin(config.minAimAngle) * config.ballSpeed
        assertEquals(3, game.balls.size)
        for (ball in game.balls) assertTrue(abs(ball.vy) >= minVy - 1e-4f, "vy ${ball.vy}")
    }

    /** The three balls' velocities right after the split, for a game on [seed]. */
    private fun splitDirections(seed: Long): List<Pair<Float, Float>> {
        val game = BrickGame(seed = seed, physics = GridPhysics, abilities = listOf(Splitter), earnable = emptyList())
        game.board.clear()
        game.board[3, 2] = Brick(3, 2, 50)
        var split: List<Pair<Float, Float>> = emptyList()
        playRound(game, straightUp) {
            if (split.isEmpty() && game.balls.size == 3) split = game.balls.map { it.vx to it.vy }
        }
        return split
    }

    @Test
    fun splitDirectionsAreSeededRandom() {
        assertEquals(splitDirections(seed = 7), splitDirections(seed = 7))
        val seeds = (1L..6L).map { splitDirections(it) }
        assertTrue(seeds.toSet().size > 1, "different seeds scatter the balls differently")
    }

    @Test
    fun aSplitNeverChangesTheRowsThatCome() {
        fun nextRow(abilities: List<Ability>): List<Pair<Int, Long>> {
            val game = gameWith(abilities, GridPhysics, Triple(3, 2, 50L))
            playRound(game, straightUp)
            return game.board.bricks().filter { it.row == 0 }.map { it.column to it.hits }.sortedBy { it.first }.toList()
        }
        assertEquals(nextRow(emptyList()), nextRow(listOf(Splitter)))
    }

    @Test
    fun drillPassesTheFirstBallThroughThreeBricksHittingEachOnce() {
        val game = gameWith(
            listOf(Drill), GridPhysics,
            Triple(3, 1, 50L), Triple(3, 2, 50L), Triple(3, 3, 50L), Triple(3, 4, 50L), Triple(3, 5, 50L),
        )
        playRound(game, straightUp)
        // Rows 5, 4 and 3 drilled, a bounce off row 2, then straight back out and down.
        assertEquals(listOf(50L, 49L, 49L, 49L, 49L), (1..5).map { game.board[3, it + 1]?.hits })
        assertEquals(4L, game.points)
        assertEquals(1, game.drainEvents().count { it is GameEvent.AbilityTriggered })
    }

    @Test
    fun drillOnlyArmsTheFirstBall() {
        val pierces = mutableListOf<Int>()
        lateinit var game: BrickGame
        val physics = BallPhysics { ball, _, _, _, _ ->
            pierces += ball.pierce
            true
        }
        game = gameWith(listOf(Drill), physics)
        playRound(game)
        playRound(game)
        assertEquals(listOf(Drill.BRICKS, Drill.BRICKS, 0), pierces)
        assertTrue(game.balls.all { it.pierce == 0 })
    }

    @Test
    fun sameSeedAndAnglesGiveTheSameGameWithEveryAbility() {
        val angles = listOf(1.2f, 2.0f, 0.6f, 1.57f, 2.5f, 0.9f, 1.8f, 1.1f, 2.2f, 0.4f, 1.4f, 2.7f)
        fun play(): Triple<Long, Int, List<GameEvent>> {
            val game = BrickGame(seed = 2026, physics = GridPhysics, abilities = Abilities.earnable)
            val events = mutableListOf<GameEvent>()
            var i = 0
            while (game.phase != RoundPhase.GameOver && i < 40) {
                playRound(game, angles[i++ % angles.size])
                events += game.drainEvents()
            }
            return Triple(game.points, game.round, events)
        }
        val a = play()
        val b = play()
        assertTrue(a.third.count { it is GameEvent.AbilityTriggered } >= 4 * 3, "every ability fired")
        assertEquals(a, b)
    }
}
