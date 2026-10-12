package com.benmorrisrains.breakthebricks.engine

import kotlin.math.floor

/**
 * A deliberately simple stand-in for the real physics (task T3) so engine tests can play whole
 * games: the ball's center moves in a straight line, reflects off walls and the ceiling, and
 * reflects off a brick cell when it enters one.
 */
class TestPhysics : BallPhysics {
    override fun step(ball: Ball, board: Board, config: GameConfig, dt: Float, contacts: BallContacts): Boolean {
        val oldColumn = floor(ball.x).toInt()
        val oldRow = floor(ball.y).toInt()
        ball.x += ball.vx * dt
        ball.y += ball.vy * dt
        val r = config.ballRadius
        if (ball.x < r) { ball.x = 2 * r - ball.x; ball.vx = -ball.vx; contacts.wallBounce() }
        if (ball.x > config.columns - r) { ball.x = 2 * (config.columns - r) - ball.x; ball.vx = -ball.vx; contacts.wallBounce() }
        if (ball.y < r) { ball.y = 2 * r - ball.y; ball.vy = -ball.vy; contacts.wallBounce() }
        if (ball.vy > 0 && ball.y + r >= config.rows) return true
        val column = floor(ball.x).toInt()
        val row = floor(ball.y).toInt()
        if (board[column, row] != null) {
            contacts.brickHit(column, row)
            if (column != oldColumn) ball.vx = -ball.vx
            if (row != oldRow) ball.vy = -ball.vy
        }
        return false
    }
}

/** Physics where every ball drops straight to the floor at x = [landAt] without hitting anything. */
class DropPhysics(private val landAt: (Int) -> Float) : BallPhysics {
    private var launched = 0
    override fun step(ball: Ball, board: Board, config: GameConfig, dt: Float, contacts: BallContacts): Boolean {
        ball.x = landAt(launched++)
        return true
    }
}
