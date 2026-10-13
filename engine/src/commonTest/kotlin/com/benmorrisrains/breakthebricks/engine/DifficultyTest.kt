package com.benmorrisrains.breakthebricks.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class DifficultyTest {
    private val angles = listOf(1.2f, 2.0f, 0.6f, 1.57f, 2.5f, 0.9f, 1.8f, 1.1f, 2.2f, 0.4f)

    @Test
    fun rankOneIsTheGentleStart() {
        val config = Difficulty.forRank(1)
        assertEquals(1, config.startingRows)
        assertEquals(1, config.minBricksPerRow)
        assertEquals(3, config.maxBricksPerRow)
        assertEquals((1..10).map { RowValues.FibonacciEighths.valueFor(it) }, (1..10).map { config.rowValues.valueFor(it) })
        assertEquals(GameConfig().columns, config.columns)
    }

    @Test
    fun startingRowsGrowEvery15RanksUpToFour() {
        val rows = listOf(1, 14, 15, 29, 30, 45, 60, 500).map { Difficulty.forRank(it).startingRows }
        assertEquals(listOf(1, 1, 2, 2, 3, 4, 4, 4), rows)
    }

    @Test
    fun bricksPerRowRiseWithRankUpToSix() {
        fun bounds(rank: Int) = Difficulty.forRank(rank).let { it.minBricksPerRow to it.maxBricksPerRow }
        assertEquals(1 to 3, bounds(9))
        assertEquals(1 to 4, bounds(10))
        assertEquals(2 to 4, bounds(12))
        assertEquals(2 to 5, bounds(20))
        assertEquals(3 to 6, bounds(30))
        assertEquals(5 to 6, bounds(50))
        assertEquals(6 to 6, bounds(72))
        assertEquals(6 to 6, bounds(1000))
    }

    @Test
    fun rowValuesStartOneRoundAheadEvery5Ranks() {
        val eighths = RowValues.FibonacciEighths
        assertEquals(eighths.valueFor(1), Difficulty.forRank(4).rowValues.valueFor(1))
        assertEquals(eighths.valueFor(2), Difficulty.forRank(5).rowValues.valueFor(1))
        // The plan doc's example: a rank 50 first row is worth what round 11 is worth at rank 1.
        assertEquals(eighths.valueFor(11), Difficulty.forRank(50).rowValues.valueFor(1))
        assertEquals(eighths.valueFor(15), Difficulty.forRank(50).rowValues.valueFor(5))
    }

    @Test
    fun everyRankGivesAValidConfig() {
        for (rank in 1..2000) Difficulty.forRank(rank)
        assertFailsWith<IllegalArgumentException> { Difficulty.forRank(0) }
    }

    @Test
    fun higherRanksStartWithMoreRowsOfTougherBricks() {
        val rank1 = BrickGame(Difficulty.forRank(1), seed = 4, physics = TestPhysics())
        val rank50 = BrickGame(Difficulty.forRank(50), seed = 4, physics = TestPhysics())
        assertEquals(setOf(0), rank1.board.bricks().map { it.row }.toSet())
        assertEquals(setOf(0, 1, 2, 3), rank50.board.bricks().map { it.row }.toSet())
        val firstRowValue = RowValues.FibonacciEighths.valueFor(11)
        assertTrue(rank50.board.bricks().all { it.hits == firstRowValue })
        for (row in 0..3) {
            val count = rank50.board.bricks().count { it.row == row }
            assertTrue(count in 4..6, "row $row has $count bricks")
        }
        assertEquals(listOf<GameEvent>(GameEvent.RoundStarted(1, 1)), rank50.drainEvents())
    }

    @Test
    fun abilitiesAreStillEarnedAtTheSameRound() {
        val game = BrickGame(Difficulty.forRank(40), seed = 2, physics = DropPhysics { 3.5f })
        val earned = mutableListOf<GameEvent.AbilityEarned>()
        while (game.phase != RoundPhase.GameOver && game.round < GrenadeBall.earnedAtRound) {
            game.aim(1.0f)
            game.fire()
            while (game.phase != RoundPhase.Aiming && game.phase != RoundPhase.GameOver) game.advance(1f / 60)
            earned += game.drainEvents().filterIsInstance<GameEvent.AbilityEarned>()
        }
        assertEquals(GrenadeBall.earnedAtRound, game.round)
        assertEquals(listOf(GameEvent.AbilityEarned(GrenadeBall.id, GrenadeBall.earnedAtRound)), earned)
    }

    @Test
    fun sameSeedAndRankGiveTheSameGame() {
        fun play(rank: Int): List<Any> {
            val game = BrickGame(Difficulty.forRank(rank), seed = 21, physics = TestPhysics())
            val log = mutableListOf<Any>()
            var i = 0
            while (game.phase != RoundPhase.GameOver && i < 40) {
                game.aim(angles[i++ % angles.size])
                game.fire()
                var frames = 0
                while (game.phase != RoundPhase.Aiming && game.phase != RoundPhase.GameOver) {
                    game.advance(1f / 60)
                    if (++frames > 200_000) fail("round never ended")
                }
                log.addAll(game.drainEvents())
                log.addAll(game.board.bricks().map { Triple(it.column, it.row, it.hits) })
            }
            return log + game.points
        }
        assertEquals(play(33), play(33))
        assertTrue(play(33) != play(1), "rank changes the game")
    }

    @Test
    fun levelLayoutsDontTakeRankRows() {
        val layout = listOf(List<Long?>(7) { 1L })
        assertFailsWith<IllegalArgumentException> {
            BrickGame(Difficulty.forRank(30), seed = 1, physics = TestPhysics(), layout = layout)
        }
    }
}
