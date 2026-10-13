package com.benmorrisrains.breakthebricks.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlayerRankTest {
    @Test
    fun newPlayersAreRankOne() {
        assertEquals(1, PlayerRank.Start.rank)
        assertEquals(0f, PlayerRank.Start.progress)
        assertEquals(50, PlayerRank.Start.pointsForNextRank)
    }

    @Test
    fun rankNToNPlusOneTakes50TimesNPoints() {
        assertEquals(1, PlayerRank(49).rank)
        assertEquals(2, PlayerRank(50).rank)
        assertEquals(2, PlayerRank(149).rank)
        assertEquals(3, PlayerRank(150).rank)
        assertEquals(10, PlayerRank(2_749).rank)
        assertEquals(11, PlayerRank(2_750).rank)
        assertEquals(2_750, PlayerRank.pointsToReach(11))
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
        val rank = PlayerRank(75) // rank 2 starts at 50, rank 3 at 150
        assertEquals(2, rank.rank)
        assertEquals(25, rank.pointsIntoRank)
        assertEquals(100, rank.pointsForNextRank)
        assertEquals(0.25f, rank.progress)
    }

    @Test
    fun addingPointsNeverLowersRank() {
        val rank = PlayerRank(140)
        assertEquals(PlayerRank(140), rank.adding(-30))
        assertEquals(3, rank.adding(10).rank)
        assertEquals(Long.MAX_VALUE, PlayerRank(Long.MAX_VALUE - 1).adding(5).totalPoints)
        assertEquals(PlayerRank.MAX_RANK, PlayerRank(Long.MAX_VALUE).rank)
        assertFailsWith<IllegalArgumentException> { PlayerRank(-1) }
    }
}
