package com.benmorrisrains.breakthebricks.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlayerRankTest {
    @Test
    fun newPlayersAreRankOne() {
        assertEquals(1, PlayerRank.Start.rank)
        assertEquals(0f, PlayerRank.Start.progress)
        assertEquals(500, PlayerRank.Start.pointsForNextRank)
    }

    @Test
    fun rankNToNPlusOneTakes500TimesNPoints() {
        assertEquals(1, PlayerRank(499).rank)
        assertEquals(2, PlayerRank(500).rank)
        assertEquals(2, PlayerRank(1_499).rank)
        assertEquals(3, PlayerRank(1_500).rank)
        assertEquals(10, PlayerRank(27_499).rank)
        assertEquals(11, PlayerRank(27_500).rank)
        assertEquals(27_500, PlayerRank.pointsToReach(11))
    }

    @Test
    fun rankMatchesThresholdsAllTheWayUp() {
        for (rank in 1..3000) {
            val start = PlayerRank.pointsToReach(rank)
            assertEquals(rank, PlayerRank(start).rank)
            if (rank > 1) assertEquals(rank - 1, PlayerRank(start - 1).rank)
        }
    }

    @Test
    fun progressCountsPointsTowardTheNextRank() {
        val rank = PlayerRank(750) // rank 2 starts at 500, rank 3 at 1,500
        assertEquals(2, rank.rank)
        assertEquals(250, rank.pointsIntoRank)
        assertEquals(1_000, rank.pointsForNextRank)
        assertEquals(0.25f, rank.progress)
    }

    @Test
    fun addingPointsNeverLowersRank() {
        val rank = PlayerRank(1_400)
        assertEquals(PlayerRank(1_400), rank.adding(-300))
        assertEquals(3, rank.adding(100).rank)
        assertEquals(Long.MAX_VALUE, PlayerRank(Long.MAX_VALUE - 1).adding(5).totalPoints)
        assertEquals(PlayerRank.MAX_RANK, PlayerRank(Long.MAX_VALUE).rank)
        assertFailsWith<IllegalArgumentException> { PlayerRank(-1) }
    }
}
