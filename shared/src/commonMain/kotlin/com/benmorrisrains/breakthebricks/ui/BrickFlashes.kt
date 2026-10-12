package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.GameEvent

/** Tracks the short bright flash a brick shows on each hit. Fed from drained [GameEvent]s. */
class BrickFlashes(private val durationSeconds: Float = 0.12f) {
    private val remaining = mutableMapOf<Long, Float>()

    fun update(elapsedSeconds: Float, events: List<GameEvent>) {
        if (remaining.isNotEmpty()) {
            val iterator = remaining.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val left = entry.value - elapsedSeconds
                if (left <= 0f) iterator.remove() else entry.setValue(left)
            }
        }
        for (event in events) {
            when (event) {
                is GameEvent.BrickHit -> remaining[key(event.column, event.row)] = durationSeconds
                is GameEvent.BrickBroken -> remaining.remove(key(event.column, event.row))
                // Bricks drop a row between rounds, so old flashes would land on the wrong cell.
                is GameEvent.RoundStarted -> remaining.clear()
                else -> Unit
            }
        }
    }

    /** 1 right after a hit, fading to 0. */
    fun intensity(column: Int, row: Int): Float =
        (remaining[key(column, row)] ?: 0f) / durationSeconds

    private fun key(column: Int, row: Int): Long = (column.toLong() shl 32) or (row.toLong() and 0xFFFFFFFFL)
}
