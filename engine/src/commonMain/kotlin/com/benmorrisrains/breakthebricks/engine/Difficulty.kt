package com.benmorrisrains.breakthebricks.engine

import kotlin.math.min

/**
 * How hard a free-play or wagered game is for a player of a given rank (plan doc, "Player rank
 * and difficulty"). Softened after Ben's 2026-10-13 playtest ("as you rank up it gets very hard"),
 * and checked with [RankBot] simulations so a strong player still reaches the Big ball and
 * Splitter at the hardest setting:
 * - starting rows: 1, plus 1 for every 25 ranks, up to 3;
 * - bricks per row: 2 to 4 at rank 1, so a row is never a single brick (Ben, 2026-10-17); the
 *   minimum rises by 1 every 30 ranks and the maximum by 1 every 20 ranks, up to 6 of the 7 columns;
 * - row values start 1 round further along for every 10 ranks, up to 5 rounds.
 * Everything stops getting harder by rank 60. Abilities and wager targets don't change with
 * rank. The hand-made levels don't use this.
 */
object Difficulty {
    const val RANKS_PER_STARTING_ROW = 25
    const val MAX_STARTING_ROWS = 3
    const val RANKS_PER_MIN_BRICK = 30
    const val RANKS_PER_MAX_BRICK = 20
    const val MAX_BRICKS_PER_ROW = 6
    const val RANKS_PER_ROUND_AHEAD = 10
    const val MAX_ROUNDS_AHEAD = 5

    fun forRank(rank: Int, base: GameConfig = GameConfig()): GameConfig {
        require(rank >= 1) { "ranks start at 1" }
        val maxBricks = min(MAX_BRICKS_PER_ROW, 4 + rank / RANKS_PER_MAX_BRICK)
        val minBricks = min(maxBricks, 2 + rank / RANKS_PER_MIN_BRICK)
        return base.copy(
            startingRows = min(MAX_STARTING_ROWS, 1 + rank / RANKS_PER_STARTING_ROW),
            minBricksPerRow = minBricks,
            maxBricksPerRow = maxBricks,
            rowValues = base.rowValues.startingAhead(min(MAX_ROUNDS_AHEAD, rank / RANKS_PER_ROUND_AHEAD)),
        )
    }
}
