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
    ): BrickGame {
        val game = BrickGame(config, seed = 1, physics = physics, abilities = listOf(GrenadeBall), earnable = emptyList())
        game.board.clear()
        for ((column, row, hits) in bricks) game.board[column, row] = Brick(column, row, hits)
        game.drainEvents()
        return game
    }

    /** Bricks below the freshly spawned top row. */
    private fun cells(game: BrickGame) = game.board.bricks().filter { it.row > 0 }.map { it.column to it.row }.toSet()

    @Test
    fun grenadeIsEarnedOnReachingRoundFiveAndStaysActive() {
        val game = BrickGame(seed = 3, physics = DropPhysics { 3.5f })
        assertEquals(listOf(GrenadeBall), Abilities.earnable)
        val earned = mutableListOf<GameEvent.AbilityEarned>()
        while (game.round < 5) {
            assertEquals(emptyList(), game.activeAbilities, "round ${game.round}")
            earned += game.drainEvents().filterIsInstance<GameEvent.AbilityEarned>()
            playRound(game)
        }
        earned += game.drainEvents().filterIsInstance<GameEvent.AbilityEarned>()
        assertEquals(listOf(GameEvent.AbilityEarned("grenade", 5)), earned)
        assertEquals(listOf<Ability>(GrenadeBall), game.activeAbilities)

        while (game.phase != RoundPhase.GameOver) {
            playRound(game)
            assertEquals(listOf<Ability>(GrenadeBall), game.activeAbilities, "round ${game.round}")
            assertTrue(game.drainEvents().none { it is GameEvent.AbilityEarned })
        }
        assertTrue(game.round > 5)
    }

    @Test
    fun grenadeOnlyExplodesFromRoundFiveOn() {
        val game = BrickGame(GameConfig(minBricksPerRow = 7, maxBricksPerRow = 7), seed = 1, physics = hitOnce(0, 0))
        repeat(4) {
            playRound(game)
            assertTrue(game.drainEvents().none { it is GameEvent.AbilityTriggered }, "round ${game.round - 1}")
        }
        assertEquals(5, game.round)
        playRound(game)
        val events = game.drainEvents()
        assertEquals(1, events.count { it is GameEvent.AbilityTriggered })
        assertTrue(GameEvent.AbilityTriggered("grenade", 0, 0) in events)
    }

    @Test
    fun aStakedGrenadeIsNotDoubledWhenEarnedAgain() {
        val game = BrickGame(seed = 3, physics = DropPhysics { 3.5f }, abilities = listOf(GrenadeBall))
        repeat(4) { playRound(game) }
        assertEquals(5, game.round)
        assertTrue(GameEvent.AbilityEarned("grenade", 5) in game.drainEvents())
        assertEquals(listOf<Ability>(GrenadeBall), game.activeAbilities)
    }

    @Test
    fun explosionBreaksTheHitBrickAndTheFiveNearest() {
        val game = grenadeGame(
            hitOnce(3, 3),
            Triple(3, 3, 10L), // hit
            Triple(3, 2, 7L), Triple(4, 3, 7L), // distance 1
            Triple(2, 4, 7L), Triple(4, 4, 7L), // distance sqrt 2
            Triple(3, 5, 7L), // distance 2
            Triple(5, 5, 7L), Triple(0, 3, 7L), Triple(6, 6, 7L), // farther
        )
        playRound(game)
        // The drop moved the survivors down one row.
        assertEquals(setOf(5 to 6, 0 to 4, 6 to 7), cells(game))
        // Each broken brick scores its remaining hits: 10 for the hit brick, 7 for each of the 5.
        assertEquals(10L + 5 * 7L, game.points)
        val events = game.drainEvents()
        assertEquals(GameEvent.AbilityTriggered("grenade", 3, 3), events.first { it is GameEvent.AbilityTriggered })
        assertEquals(
            listOf(
                GameEvent.BrickBroken(3, 3, 9),
                GameEvent.BrickBroken(3, 2, 7),
                GameEvent.BrickBroken(4, 3, 7),
                GameEvent.BrickBroken(2, 4, 7),
                GameEvent.BrickBroken(4, 4, 7),
                GameEvent.BrickBroken(3, 5, 7),
            ),
            events.filterIsInstance<GameEvent.BrickBroken>(),
        )
    }

    @Test
    fun tiesGoToTheHigherRowThenTheLeftColumn() {
        val ring = listOf(2 to 3, 4 to 3, 3 to 2, 3 to 4, 2 to 2, 4 to 2, 2 to 4, 4 to 4)
        val board = Board(7, 9)
        for ((column, row) in ring) board[column, row] = Brick(column, row, 1)
        val targets = GrenadeBall.blastTargets(board, 3, 3).map { it.column to it.row }
        assertEquals(listOf(3 to 2, 2 to 3, 4 to 3, 3 to 4, 2 to 2), targets)
    }

    @Test
    fun explosionWithFewerThanFiveOtherBricksBreaksThemAll() {
        val game = grenadeGame(hitOnce(1, 1), Triple(1, 1, 1L), Triple(6, 7, 3L), Triple(0, 0, 2L))
        playRound(game)
        assertTrue(cells(game).isEmpty())
        assertEquals(6L, game.points)
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
        // Round 1: the blast takes (0, 0) and both other bricks, so the ball's second contact finds nothing.
        // Round 2: a full row of 1s; ball 1 blasts at (0, 0) then hits (6, 0) normally, ball 2 finds nothing.
        val triggered = game.drainEvents().filterIsInstance<GameEvent.AbilityTriggered>()
        assertEquals(listOf(GameEvent.AbilityTriggered("grenade", 0, 0)), triggered.take(1))
        assertEquals(2, triggered.size)
    }

    @Test
    fun sameSeedAndAnglesGiveTheSameGrenadeGame() {
        val angles = listOf(1.2f, 2.0f, 0.6f, 1.57f, 2.5f, 0.9f, 1.8f, 1.1f, 2.2f, 0.4f, 1.4f, 2.7f)
        fun play(): Pair<BrickGame, List<GameEvent>> {
            val game = BrickGame(seed = 2026, physics = TestPhysics())
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
