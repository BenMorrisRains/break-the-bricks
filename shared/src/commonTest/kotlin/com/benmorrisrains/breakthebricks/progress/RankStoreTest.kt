package com.benmorrisrains.breakthebricks.progress

import com.benmorrisrains.breakthebricks.engine.PlayerRank
import com.benmorrisrains.breakthebricks.platform.InMemoryStore
import kotlin.test.Test
import kotlin.test.assertEquals

class RankStoreTest {
    private val store = InMemoryStore()
    private val ranks = RankStore(store)

    @Test
    fun aNewPlayerIsRankOne() {
        assertEquals(PlayerRank.Start, ranks.load())
    }

    @Test
    fun pointsAddUpAcrossGamesAndAreSaved() {
        assertEquals(PlayerRank(400), ranks.addPoints(400))
        assertEquals(PlayerRank(1_600), ranks.addPoints(1_200))
        assertEquals(1_600, store.getLong(RankStore.POINTS_KEY, 0))
        assertEquals(3, store.getLong(RankStore.RANK_KEY, 0))
        assertEquals(PlayerRank(1_600), RankStore(store).load())
    }

    @Test
    fun aCorruptNegativeTotalReadsAsZero() {
        store.putLong(RankStore.POINTS_KEY, -5)
        assertEquals(PlayerRank.Start, ranks.load())
    }
}
