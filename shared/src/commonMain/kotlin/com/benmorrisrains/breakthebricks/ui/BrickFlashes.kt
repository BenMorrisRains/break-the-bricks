package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.GameEvent

/** Tracks the short bright flash a brick shows on each hit. Fed from drained [GameEvent]s. */
class BrickFlashes(private val durationSeconds: Float = 0.12f) {
    /**
     * Seconds of flash left per cell, at row * [columns] + column. A flat array rather than a map,
     * since the canvas asks for every brick every frame. It grows to fit the first hits it sees.
     */
    private var remaining = FloatArray(0)
    private var columns = 0
    private var rows = 0

    fun update(elapsedSeconds: Float, events: List<GameEvent>) {
        for (i in remaining.indices) {
            if (remaining[i] > 0f) remaining[i] = (remaining[i] - elapsedSeconds).coerceAtLeast(0f)
        }
        for (event in events) {
            when (event) {
                is GameEvent.BrickHit -> {
                    fit(event.column, event.row)
                    remaining[event.row * columns + event.column] = durationSeconds
                }
                is GameEvent.BrickBroken -> if (event.column in 0 until columns && event.row in 0 until rows) {
                    remaining[event.row * columns + event.column] = 0f
                }
                // Bricks drop a row between rounds, so old flashes would land on the wrong cell.
                is GameEvent.RoundStarted -> remaining.fill(0f)
                else -> Unit
            }
        }
    }

    /** 1 right after a hit, fading to 0. */
    fun intensity(column: Int, row: Int): Float {
        if (column !in 0 until columns || row !in 0 until rows) return 0f
        return remaining[row * columns + column] / durationSeconds
    }

    /** Grows the grid to hold the cell at [column], [row], keeping the flashes already running. */
    private fun fit(column: Int, row: Int) {
        if (column < columns && row < rows) return
        val newColumns = maxOf(columns, column + 1)
        val newRows = maxOf(rows, row + 1)
        val grown = FloatArray(newColumns * newRows)
        for (r in 0 until rows) for (c in 0 until columns) grown[r * newColumns + c] = remaining[r * columns + c]
        remaining = grown
        columns = newColumns
        rows = newRows
    }
}
