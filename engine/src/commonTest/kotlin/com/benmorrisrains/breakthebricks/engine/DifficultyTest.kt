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
        assertEquals(3, config.minBricksPerRow, "fuller rows (Ben, 2026-10-17)")
        assertEquals(5, config.maxBricksPerRow)
        assertEquals((1..10).map { RowValues.FibonacciEighthsEveryThreeRounds.valueFor(it) }, (1..10).map { config.rowValues.valueFor(it) })
        assertEquals(GameConfig().columns, config.columns)
    }

    @Test
    fun startingRowsGrowEvery25RanksUpToThree() {
        val rows = listOf(1, 24, 25, 49, 50, 75, 500).map { Difficulty.forRank(it).startingRows }
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 3), rows)
    }

    @Test
    fun bricksPerRowRiseWithRankUpToSix() {
        fun bounds(rank: Int) = Difficulty.forRank(rank).let { it.minBricksPerRow to it.maxBricksPerRow }
        assertEquals(3 to 5, bounds(9))
        assertEquals(3 to 6, bounds(10), "Ben's rank-10 game had rows of 2")
        assertEquals(4 to 6, bounds(20))
        assertEquals(5 to 6, bounds(40))
        assertEquals(5 to 6, bounds(1000), "always a gap and some variety")
    }

    @Test
    fun rowValuesStartOneRoundAheadEvery10RanksUpToFive() {
        val eighths = RowValues.FibonacciEighthsEveryThreeRounds
        assertEquals(eighths.valueFor(1), Difficulty.forRank(9).rowValues.valueFor(1))
        assertEquals(eighths.valueFor(2), Difficulty.forRank(10).rowValues.valueFor(1))
        assertEquals(eighths.valueFor(6), Difficulty.forRank(50).rowValues.valueFor(1))
        assertEquals(eighths.valueFor(10), Difficulty.forRank(50).rowValues.valueFor(5))
        assertEquals(eighths.valueFor(6), Difficulty.forRank(500).rowValues.valueFor(1), "capped at 5 rounds ahead")
    }

    @Test
    fun difficultyStopsRisingByRank60() {
        val top = Difficulty.forRank(60)
        for (rank in listOf(61, 100, 1000)) {
            val config = Difficulty.forRank(rank)
            assertEquals(top.startingRows, config.startingRows)
            assertEquals(top.maxBricksPerRow, config.maxBricksPerRow)
            assertEquals(top.rowValues.valueFor(1), config.rowValues.valueFor(1))
        }
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
        assertEquals(setOf(0, 1, 2), rank50.board.bricks().map { it.row }.toSet())
        val firstRowValue = RowValues.FibonacciEighthsEveryThreeRounds.valueFor(6)
        assertTrue(rank50.board.bricks().all { it.hits == firstRowValue })
        for (row in 0..2) {
            val count = rank50.board.bricks().count { it.row == row }
            assertTrue(count in 5..6, "row $row has $count bricks")
        }
        assertEquals(listOf<GameEvent>(GameEvent.RoundStarted(1, 1)), rank50.drainEvents())
    }

    @Test
    fun abilitiesAreStillEarnedAtTheSameRound() {
        // A taller board than usual so a game that never breaks a brick reaches the big ball's round.
        val game = BrickGame(Difficulty.forRank(40).copy(rows = 20), seed = 2, physics = DropPhysics { 3.5f })
        val earned = mutableListOf<GameEvent.AbilityEarned>()
        while (game.phase != RoundPhase.GameOver && game.round < BigBall.earnedAtRound) {
            game.aim(1.0f)
            game.fire()
            while (game.phase != RoundPhase.Aiming && game.phase != RoundPhase.GameOver) game.advance(1f / 60)
            earned += game.drainEvents().filterIsInstance<GameEvent.AbilityEarned>()
        }
        assertEquals(BigBall.earnedAtRound, game.round)
        assertEquals(listOf(GameEvent.AbilityEarned(BigBall.id, BigBall.earnedAtRound)), earned)
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

    /**
     * Ben's 2026-10-13 playtest: ranking up made games too hard to progress. A greedy bot that
     * reaches about round 25 at rank 1 must still reach the Splitter's round at the hardest
     * setting, and lose only a few rounds by rank 20.
     */
    @Test
    fun aGoodPlayerKeepsProgressingAtHighRanks() {
        val seeds = listOf(7919L, 15838L)
        fun rounds(rank: Int) = seeds.map { RankBot.play(rank, it, maxRounds = 30).round }
        val rank1 = rounds(1)
        val rank20 = rounds(20)
        val hardest = rounds(60)
        assertTrue(rank1.all { it >= 20 }, "rank 1 reached $rank1")
        assertTrue(rank20.all { it >= 18 }, "rank 20 reached $rank20")
        assertTrue(hardest.all { it > Splitter.earnedAtRound }, "rank 60 reached $hardest")
    }
}
