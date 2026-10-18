package com.benmorrisrains.breakthebricks.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class GrenadeBallTest {
    private fun playRound(game: BrickGame, angle: Float = 1.0f) {
        game.aim(angle)
        game.fire()
        var frame = 0
        while (game.phase != RoundPhase.Aiming && game.phase != RoundPhase.GameOver) {
            game.advance(1f / 60)
            if (frame++ > 200_000) fail("round ${game.round} never ended")
        }
    }

    /** Physics where every ball hits the brick at ([column], [row]) if there is one, then lands. */
    private fun hitOnce(column: Int, row: Int) = BallPhysics { _, board, _, _, contacts ->
        if (board[column, row] != null) contacts.brickHit(column, row)
        true
    }

    /** A round-1 game holding the grenade, with its board replaced by [bricks] (column, row, hits). */
    private fun grenadeGame(
        physics: BallPhysics,
        vararg bricks: Triple<Int, Int, Long>,
        config: GameConfig = GameConfig(),
        seed: Long = 1,
    ): BrickGame {
        val game = BrickGame(config, seed = seed, physics = physics, abilities = listOf(GrenadeBall), earnable = emptyList())
        game.board.clear()
        for ((column, row, hits) in bricks) game.board[column, row] = Brick(column, row, hits)
        game.drainEvents()
        return game
    }

    /** Bricks below the freshly spawned top row. */
    private fun cells(game: BrickGame) = game.board.bricks().filter { it.row > 0 }.map { it.column to it.row }.toSet()

    @Test
    fun grenadeIsEarnedOnReachingRoundFortyAndStaysActive() {
        // A tall board so a game that never breaks a brick lasts past round 40.
        val game = BrickGame(GameConfig(rows = 50), seed = 3, physics = DropPhysics { 3.5f }, earnable = listOf(GrenadeBall))
        assertEquals(40, GrenadeBall.earnedAtRound)
        assertEquals(45, GrenadeBall.wagerTargetRound)
        val earned = mutableListOf<GameEvent.AbilityEarned>()
        while (game.round < 40) {
            assertEquals(emptyList(), game.activeAbilities, "round ${game.round}")
            earned += game.drainEvents().filterIsInstance<GameEvent.AbilityEarned>()
            playRound(game)
        }
        earned += game.drainEvents().filterIsInstance<GameEvent.AbilityEarned>()
        assertEquals(listOf(GameEvent.AbilityEarned("grenade", 40)), earned)
        assertEquals(listOf<Ability>(GrenadeBall), game.activeAbilities)

        while (game.phase != RoundPhase.GameOver) {
            playRound(game)
            assertEquals(listOf<Ability>(GrenadeBall), game.activeAbilities, "round ${game.round}")
            assertTrue(game.drainEvents().none { it is GameEvent.AbilityEarned })
        }
        assertTrue(game.round > 40)
    }

    @Test
    fun grenadeOnlyExplodesFromRoundFortyOn() {
        val config = GameConfig(rows = 50, minBricksPerRow = 7, maxBricksPerRow = 7)
        val game = BrickGame(config, seed = 1, physics = hitOnce(0, 0), earnable = listOf(GrenadeBall))
        repeat(39) {
            playRound(game)
            assertTrue(game.drainEvents().none { it is GameEvent.AbilityTriggered }, "round ${game.round - 1}")
        }
        assertEquals(40, game.round)
        playRound(game)
        val events = game.drainEvents()
        assertEquals(1, events.count { it is GameEvent.AbilityTriggered })
        assertTrue(GameEvent.AbilityTriggered("grenade", 0, 0) in events)
    }

    @Test
    fun aStakedGrenadeIsNotDoubledWhenEarnedAgain() {
        val game = BrickGame(
            GameConfig(rows = 50), seed = 3, physics = DropPhysics { 3.5f },
            abilities = listOf(GrenadeBall), earnable = listOf(GrenadeBall),
        )
        repeat(39) { playRound(game) }
        assertEquals(40, game.round)
        assertTrue(GameEvent.AbilityEarned("grenade", 40) in game.drainEvents())
        assertEquals(listOf<Ability>(GrenadeBall), game.activeAbilities)
    }

    /** Near bricks of [blastGame]: 2 one cell from the hit brick at (3, 3) and 4 two cells away. */
    private val nearOne = listOf(3 to 2, 4 to 3)
    private val nearTwo = listOf(2 to 5, 5 to 5, 1 to 1, 5 to 2)
    private val far = 6 to 0

    /** Plays one grenade round on the board above; returns the game and the bricks broken, as they were placed. */
    private fun blastGame(seed: Long): Pair<BrickGame, Set<Pair<Int, Int>>> {
        val bricks = listOf(Triple(3, 3, 10L)) + (nearOne + nearTwo + far).map { (c, r) -> Triple(c, r, 7L) }
        val game = grenadeGame(hitOnce(3, 3), *bricks.toTypedArray(), seed = seed)
        playRound(game)
        val broken = game.drainEvents().filterIsInstance<GameEvent.BrickBroken>().map { it.column to it.row }.toSet()
        return game to broken
    }

    @Test
    fun theBlastTakesOutTheHitBrickAndThreeToFiveNearestBricks() {
        for (seed in 1L..20L) {
            val (game, broken) = blastGame(seed)
            assertTrue(3 to 3 in broken, "seed $seed")
            val blasted = broken - (3 to 3)
            assertTrue(blasted.size in GrenadeBall.BLAST_BRICKS, "seed $seed blasted $blasted")
            // Nearest first: both bricks one cell away go before any two cells away; never the far one.
            assertTrue(blasted.containsAll(nearOne), "seed $seed blasted $blasted")
            assertTrue(far !in blasted)
            // Every broken brick scores its remaining hits: 10 for the hit brick, 7 for each other.
            assertEquals(10L + 7L * blasted.size, game.points, "seed $seed")
        }
    }

    @Test
    fun theBlastIsSeeded() {
        assertEquals(blastGame(seed = 4).second, blastGame(seed = 4).second)
        val outcomes = (1L..20L).map { blastGame(it).second }.toSet()
        assertTrue(outcomes.size > 1, "different seeds blast different bricks")
        assertTrue(outcomes.map { it.size - 1 }.toSet() == GrenadeBall.BLAST_BRICKS.toSet(), "3, 4 and 5 all happen")
    }

    @Test
    fun theBlastOnlyReachesNearbyBricks() {
        val game = grenadeGame(hitOnce(3, 3), Triple(3, 3, 10L), Triple(4, 4, 7L), Triple(0, 3, 7L), Triple(3, 6, 7L))
        playRound(game)
        val broken = game.drainEvents().filterIsInstance<GameEvent.BrickBroken>().map { it.column to it.row }
        // Only (4, 4) is within reach; (0, 3) and (3, 6) are 3 cells away and only dropped a row.
        assertEquals(setOf(3 to 3, 4 to 4), broken.toSet())
        assertEquals(setOf(0 to 4, 3 to 7), cells(game))
    }

    @Test
    fun onlyTheFirstBallExplodesAndOnlyOnItsFirstHit() {
        // Every ball hits two bricks: (0, 0) then (6, 0).
        val physics = BallPhysics { _, board, _, _, contacts ->
            if (board[0, 0] != null) contacts.brickHit(0, 0)
            if (board[6, 0] != null) contacts.brickHit(6, 0)
            true
        }
        val config = GameConfig(minBricksPerRow = 7, maxBricksPerRow = 7)
        val game = grenadeGame(physics, Triple(0, 0, 50L), Triple(6, 0, 50L), Triple(3, 6, 50L), config = config)
        repeat(2) { playRound(game) }
        // Round 1: the blast takes (0, 0) and has nothing in reach; the ball's second contact takes 1 off (6, 0).
        // Round 2: a full row of 1s; ball 1 blasts at (0, 0) and its neighbors; only the first ball explodes.
        val triggered = game.drainEvents().filterIsInstance<GameEvent.AbilityTriggered>()
        assertEquals(listOf(GameEvent.AbilityTriggered("grenade", 0, 0)), triggered.take(1))
        assertEquals(2, triggered.size)
    }

    @Test
    fun sameSeedAndAnglesGiveTheSameGrenadeGame() {
        val angles = listOf(1.2f, 2.0f, 0.6f, 1.57f, 2.5f, 0.9f, 1.8f, 1.1f, 2.2f, 0.4f, 1.4f, 2.7f)
        fun play(): Pair<BrickGame, List<GameEvent>> {
            val game = BrickGame(seed = 2026, physics = TestPhysics(), abilities = listOf(GrenadeBall))
            val events = mutableListOf<GameEvent>()
            var i = 0
            while (game.phase != RoundPhase.GameOver && i < 60) {
                playRound(game, angles[i++ % angles.size])
                events += game.drainEvents()
            }
            return game to events
        }
        val (a, eventsA) = play()
        val (b, eventsB) = play()
        assertTrue(eventsA.any { it is GameEvent.AbilityTriggered }, "the test game should reach the grenade")
        assertEquals(eventsA, eventsB)
        assertEquals(a.points, b.points)
    }
}
