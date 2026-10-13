package com.benmorrisrains.breakthebricks.engine

/** A brick in one grid cell. [hits] counts down to 0, when the brick breaks. */
class Brick(
    val column: Int,
    var row: Int,
    /** Hits left; also the points still to be earned from this brick. */
    var hits: Long,
) {
    /** The value the brick spawned with, used for color tiers. */
    val startingHits: Long = hits
}

/** The 7-column brick field. Row 0 is the top; a brick reaching row [GameConfig.rows] - 1 ends the game. */
class Board(val columns: Int, val rows: Int) {
    private val cells = arrayOfNulls<Brick>(columns * rows)

    operator fun get(column: Int, row: Int): Brick? =
        if (column in 0 until columns && row in 0 until rows) cells[row * columns + column] else null

    operator fun set(column: Int, row: Int, brick: Brick?) {
        require(column in 0 until columns && row in 0 until rows)
        cells[row * columns + column] = brick
    }

    fun bricks(): Sequence<Brick> = cells.asSequence().filterNotNull()

    fun clear() = cells.fill(null)
}

enum class BallState { Waiting, Flying, Landed }

class Ball(
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var state: BallState = BallState.Waiting,
)

/**
 * Where a round is. The UI only accepts aim input in [Aiming]. A game finishes in [GameOver]
 * (a brick reached the floor row) or, in a level, [LevelComplete] (its goal rounds survived).
 */
enum class RoundPhase {
    Aiming, Firing, Returning, Advancing, GameOver, LevelComplete;

    /** True once nothing more can happen in this game. */
    val isFinished: Boolean get() = this == GameOver || this == LevelComplete
}
