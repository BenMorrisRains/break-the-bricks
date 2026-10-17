package com.benmorrisrains.breakthebricks.engine

/**
 * Tunable numbers for one game. World units are brick cells: x runs 0..[columns] left to right,
 * y runs 0..[rows] top to bottom, and the floor is the line y = [rows].
 */
data class GameConfig(
    val columns: Int = 7,
    val rows: Int = 9,
    /** Ball radius in cells. */
    val ballRadius: Float = 0.12f,
    /** Ball speed in cells per second. */
    val ballSpeed: Float = 18f,
    /** Seconds between two balls leaving the launch point. */
    val fireInterval: Float = 0.06f,
    /** Seconds the bricks take to drop a row between rounds, so the UI can animate it. */
    val advanceSeconds: Float = 0.2f,
    /** Smallest aim angle above horizontal, in radians (about 8 degrees). */
    val minAimAngle: Float = 0.14f,
    /** Bricks per new row are drawn between these bounds (inclusive); never just one (Ben, 2026-10-17). */
    val minBricksPerRow: Int = 2,
    val maxBricksPerRow: Int = 6,
    /** Value of the bricks in the row spawned at the start of a round. */
    val rowValues: RowValues = RowValues.FibonacciEighthsHalfSpeed,
    /** Rows of bricks on the board when round 1 starts. Higher ranks start with more. */
    val startingRows: Int = 1,
) {
    init {
        require(columns > 0 && rows > 1)
        require(fireInterval > 0f && advanceSeconds >= 0f && ballSpeed > 0f)
        require(minBricksPerRow in 1..columns && maxBricksPerRow in minBricksPerRow..columns)
        // At least one empty row above the floor row, or the game would be over before it began.
        require(startingRows in 1..rows - 2)
    }

    companion object {
        /** Physics runs at a fixed rate so every device and every replay sees the same game. */
        const val STEPS_PER_SECOND = 120
        const val STEP_SECONDS = 1f / STEPS_PER_SECOND
    }
}
