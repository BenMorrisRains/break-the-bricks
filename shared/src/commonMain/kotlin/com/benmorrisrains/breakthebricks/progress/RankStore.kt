package com.benmorrisrains.breakthebricks.progress

import com.benmorrisrains.breakthebricks.engine.PlayerRank
import com.benmorrisrains.breakthebricks.platform.KeyValueStore

/**
 * The player's rank, saved in [store]. Every point from every game is added to one running
 * total; the rank is worked out from that total and saved beside it for screens that only need
 * the number.
 */
class RankStore(private val store: KeyValueStore) {
    fun load(): PlayerRank = PlayerRank(store.getLong(POINTS_KEY, 0L).coerceAtLeast(0L))

    /** Adds a finished game's [points] and returns the new rank. */
    fun addPoints(points: Long): PlayerRank {
        val rank = load().adding(points)
        store.putLong(POINTS_KEY, rank.totalPoints)
        store.putLong(RANK_KEY, rank.rank.toLong())
        return rank
    }

    companion object {
        const val POINTS_KEY = "rank_points"
        const val RANK_KEY = "rank"
    }
}
