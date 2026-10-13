package com.benmorrisrains.breakthebricks.engine

import kotlin.math.min

/**
 * How hard a free-play or wagered game is for a player of a given rank (plan doc, "Player rank
 * and difficulty"). Starting values to tune in playtests:
 * - starting rows: 1, plus 1 for every 15 ranks, up to 4;
 * - bricks per row: 1 to 3 at rank 1; the minimum rises by 1 every 12 ranks and the maximum
 *   by 1 every 10 ranks, up to 6 of the 7 columns;
 * - row values start 1 round further along for every 5 ranks.
 * Abilities and wager targets don't change with rank. The 10 hand-made levels don't use this.
 */
object Difficulty {
    const val RANKS_PER_STARTING_ROW = 15
    const val MAX_STARTING_ROWS = 4
    const val RANKS_PER_MIN_BRICK = 12
    const val RANKS_PER_MAX_BRICK = 10
    const val MAX_BRICKS_PER_ROW = 6
    const val RANKS_PER_ROUND_AHEAD = 5

    fun forRank(rank: Int, base: GameConfig = GameConfig()): GameConfig {
        require(rank >= 1) { "ranks start at 1" }
        val maxBricks = min(MAX_BRICKS_PER_ROW, 3 + rank / RANKS_PER_MAX_BRICK)
        val minBricks = min(maxBricks, 1 + rank / RANKS_PER_MIN_BRICK)
        return base.copy(
            startingRows = min(MAX_STARTING_ROWS, 1 + rank / RANKS_PER_STARTING_ROW),
            minBricksPerRow = minBricks,
            maxBricksPerRow = maxBricks,
            rowValues = RowValues.FibonacciEighths.startingAhead(rank / RANKS_PER_ROUND_AHEAD),
        )
    }
}
