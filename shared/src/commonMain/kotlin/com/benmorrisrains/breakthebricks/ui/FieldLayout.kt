package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.GameConfig

/**
 * Maps engine world units (cells) to canvas pixels and back. The world is [GameConfig.columns]
 * cells wide and [GameConfig.rows] cells down to the floor, plus [BELOW_FLOOR_CELLS] under the
 * floor for the ball counter. It is scaled to fit the canvas and centered, so the game plays
 * the same on every screen.
 */
class FieldLayout(val config: GameConfig, val widthPx: Float, val heightPx: Float) {
    /** Size of one cell in pixels; 0 before the canvas has a size. */
    val cell: Float = maxOf(0f, minOf(widthPx / config.columns, heightPx / worldHeight(config)))
    val originX: Float = (widthPx - cell * config.columns) / 2f
    val originY: Float = (heightPx - cell * worldHeight(config)) / 2f

    val fieldWidth: Float get() = cell * config.columns
    val floorY: Float get() = toScreenY(config.rows.toFloat())
    val isEmpty: Boolean get() = cell <= 0f

    fun toScreenX(x: Float): Float = originX + x * cell
    fun toScreenY(y: Float): Float = originY + y * cell
    fun toWorldX(px: Float): Float = (px - originX) / cell
    fun toWorldY(py: Float): Float = (py - originY) / cell

    companion object {
        const val BELOW_FLOOR_CELLS = 1f

        fun worldHeight(config: GameConfig): Float = config.rows + BELOW_FLOOR_CELLS
    }
}
