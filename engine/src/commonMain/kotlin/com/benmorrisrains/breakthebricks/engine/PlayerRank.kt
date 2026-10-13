package com.benmorrisrains.breakthebricks.engine

import kotlin.math.sqrt

/**
 * A player's rank from every point they have scored, across all games. Going from rank n to
 * n + 1 takes 500 x n points: rank 2 at 500 points, rank 3 at 1,500, rank 11 at 27,500. Points
 * only ever add up, so rank never goes down. (Raised from 50 x n after Ben's 2026-10-13 playtest:
 * one good game, worth thousands of points, was jumping a new player to rank 14.)
 */
data class PlayerRank(val totalPoints: Long) {
    init {
        require(totalPoints >= 0)
    }

    val rank: Int = rankFor(totalPoints)

    /** Points scored since reaching [rank]. */
    val pointsIntoRank: Long get() = totalPoints - pointsToReach(rank)

    /** Points between [rank] and the next one. */
    val pointsForNextRank: Long get() = POINTS_PER_RANK * rank

    /** How far the bar to the next rank is filled, from 0 to just under 1. */
    val progress: Float get() = (pointsIntoRank.toDouble() / pointsForNextRank).toFloat()

    /** This rank with [points] more scored. */
    fun adding(points: Long): PlayerRank = PlayerRank(saturatingAdd(totalPoints, points.coerceAtLeast(0)))

    companion object {
        const val POINTS_PER_RANK = 500L

        /** A new player: rank 1, no points. */
        val Start = PlayerRank(0)

        /** Total points needed to reach [rank]: 500 x (1 + 2 + ... + (rank - 1)). */
        fun pointsToReach(rank: Int): Long {
            require(rank >= 1)
            val r = rank.toLong()
            return POINTS_PER_RANK * r * (r - 1) / 2
        }

        fun rankFor(totalPoints: Long): Int {
            // Rank grows with the square root of points, so a short climb from a close guess is exact.
            var rank = sqrt(totalPoints / (POINTS_PER_RANK / 2.0)).toInt().coerceIn(1, MAX_RANK)
            while (rank > 1 && pointsToReach(rank) > totalPoints) rank--
            while (rank < MAX_RANK && pointsToReach(rank + 1) <= totalPoints) rank++
            return rank
        }

        /** Far beyond any real player; keeps the math inside Long. */
        const val MAX_RANK = 100_000

        private fun saturatingAdd(a: Long, b: Long) = if (a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
    }
}
