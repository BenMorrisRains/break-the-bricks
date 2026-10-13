package com.benmorrisrains.breakthebricks.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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
        assertEquals(listOf(0 to 2f, 0 to 2f, 1 to 1f), sizes)
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
        assertEquals(2 * r, launched[1].first)
        assertEquals(game.config.rows - 2 * r, launched[1].second)
        assertEquals(r to game.config.rows - r, launched[2])
    }

    @Test
    fun theEngineHandsThePhysicsTheBigBallsRadius() {
        // Straight up into a brick: the big ball turns back at its bottom face plus 2r, not r.
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
        assertTrue(big - (3f + 2 * r) in 0f..step, "big ball turned at $big")
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
        // Bounced straight down; the new balls fly at the same speed, turned either side of it.
        val speed = game.config.ballSpeed
        assertTrue(abs(splitVelocities[0].first) < 1e-3f && abs(splitVelocities[0].second - speed) < 1e-3f)
        for ((vx, vy) in splitVelocities.drop(1)) {
            assertTrue(abs(sqrt(vx * vx + vy * vy) - speed) < 1e-3f)
            assertTrue(abs(abs(vx) - sin(Splitter.SPREAD_RADIANS) * speed) < 1e-3f)
        }
        assertTrue(splitVelocities[1].first * splitVelocities[2].first < 0f, "one goes each way")
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
        // The first ball hits while flying one spread above flat, so one new ball would fly flat.
        val physics = BallPhysics { ball, _, config, _, contacts ->
            ball.vx = cos(Splitter.SPREAD_RADIANS) * config.ballSpeed
            ball.vy = -sin(Splitter.SPREAD_RADIANS) * config.ballSpeed
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
        for (ball in game.balls.drop(1)) assertTrue(abs(ball.vy) >= minVy - 1e-4f, "vy ${ball.vy}")
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
    fun sameSeedAnglesAndPicksGiveTheSameGameWithEveryAbility() {
        val angles = listOf(1.2f, 2.0f, 0.6f, 1.57f, 2.5f, 0.9f, 1.8f, 1.1f, 2.2f, 0.4f, 1.4f, 2.7f)
        fun play(): Triple<Long, Int, List<GameEvent>> {
            val game = BrickGame(seed = 2026, physics = GridPhysics, abilities = Abilities.earnable)
            val events = mutableListOf<GameEvent>()
            var i = 0
            while (game.phase != RoundPhase.GameOver && i < 40) {
                game.selectAbility(Abilities.earnable[i % Abilities.earnable.size].id)
                playRound(game, angles[i++ % angles.size])
                events += game.drainEvents()
            }
            return Triple(game.points, game.round, events)
        }
        val a = play()
        val b = play()
        val fired = a.third.filterIsInstance<GameEvent.AbilityTriggered>().map { it.abilityId }.toSet()
        assertEquals(Abilities.earnable.map { it.id }.toSet(), fired, "every ability fired")
        assertEquals(a, b)
    }

    @Test
    fun theFirstActiveAbilityIsPickedUntilThePlayerPicksAnother() {
        val game = gameWith(listOf(BigBall, Drill), DropPhysics { 3.5f })
        assertEquals(BigBall, game.selectedAbility)
        game.selectAbility(Drill.id)
        assertEquals(Drill, game.selectedAbility)
        playRound(game)
        playRound(game)
        assertEquals(Drill, game.selectedAbility, "the pick carries over to later rounds")
        assertEquals(listOf<String?>("drill", "drill"), game.firedAbilities)
    }

    @Test
    fun withNoAbilityNothingIsPicked() {
        val game = gameWith(emptyList(), DropPhysics { 3.5f })
        assertEquals(null, game.selectedAbility)
        game.selectAbility(GrenadeBall.id)
        assertEquals(null, game.selectedAbility, "an ability that isn't active can't be picked")
        playRound(game)
        assertEquals(listOf<String?>(null), game.firedAbilities)
    }

    @Test
    fun onlyThePickedAbilityActsOnTheFirstBall() {
        val launched = mutableListOf<Pair<Float, Int>>()
        lateinit var game: BrickGame
        val physics = BallPhysics { ball, _, _, _, _ ->
            if (ball === game.balls.first()) launched += ball.size to ball.pierce
            true
        }
        game = gameWith(listOf(BigBall, Drill), physics)
        playRound(game)
        game.selectAbility(Drill.id)
        playRound(game)
        game.selectAbility(BigBall.id)
        playRound(game)
        assertEquals(listOf(2f to 0, 1f to Drill.BRICKS, 2f to 0), launched)
    }

    @Test
    fun onlyThePickedAbilityTriggersOnTheFirstHit() {
        val game = gameWith(listOf(GrenadeBall, Splitter), GridPhysics, Triple(3, 2, 50L))
        game.selectAbility(Splitter.id)
        var mostBalls = 0
        playRound(game, straightUp) { mostBalls = maxOf(mostBalls, game.balls.size) }
        assertEquals(3, mostBalls)
        assertEquals(listOf("splitter"), game.drainEvents().filterIsInstance<GameEvent.AbilityTriggered>().map { it.abilityId })
        assertTrue(game.board[3, 3] != null, "the grenade didn't go off (the brick has dropped a row)")
    }

    @Test
    fun thePickCanOnlyChangeWhileAiming() {
        val game = gameWith(listOf(BigBall, Drill), DropPhysics { 3.5f })
        game.fire()
        game.selectAbility(Drill.id)
        assertEquals(BigBall, game.selectedAbility)
    }

    @Test
    fun earningAnAbilityKeepsThePlayersPick() {
        val game = BrickGame(GameConfig(rows = 40), seed = 3, physics = DropPhysics { 3.5f })
        while (game.round < 10) playRound(game)
        assertEquals(listOf(BigBall, Splitter), game.activeAbilities)
        assertEquals(BigBall, game.selectedAbility)
        game.selectAbility(Splitter.id)
        while (game.round < 20) playRound(game)
        assertEquals(Splitter, game.selectedAbility, "earning the drill didn't move the pick")
    }

    @Test
    fun replayingAnglesAndPicksReproducesTheGame() {
        val angles = listOf(1.2f, 2.0f, 0.6f, 1.57f, 2.5f, 0.9f, 1.8f)
        val original = BrickGame(seed = 11, physics = GridPhysics, abilities = Abilities.earnable)
        var i = 0
        while (original.phase != RoundPhase.GameOver && i < 20) {
            if (i % 3 == 0) original.selectAbility(Abilities.earnable[(i / 3) % 4].id)
            playRound(original, angles[i++ % angles.size])
        }
        val replay = BrickGame(seed = 11, physics = GridPhysics, abilities = Abilities.earnable)
        for ((angle, id) in original.firedAngles.zip(original.firedAbilities)) {
            if (id != null) replay.selectAbility(id)
            playRound(replay, angle)
        }
        assertEquals(original.points, replay.points)
        assertEquals(original.round, replay.round)
        assertEquals(original.firedAbilities, replay.firedAbilities)
    }
}
