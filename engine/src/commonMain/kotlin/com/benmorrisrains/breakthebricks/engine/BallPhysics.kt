package com.benmorrisrains.breakthebricks.engine

/**
 * Moves one ball through one fixed step. The engine owns rounds, damage and points;
 * the physics (task T3) owns the collision math and sits behind this interface.
 */
fun interface BallPhysics {
    /**
     * Moves a flying [ball] for [dt] seconds against the walls (x = 0 and x = columns), the ceiling
     * (y = 0) and the bricks on [board]. Reports every brick contact through [BallContacts.brickHit]
     * and every wall or ceiling bounce through [BallContacts.wallBounce]. The engine applies damage
     * inside [BallContacts.brickHit], so the brick may be gone from [board] when that call returns.
     *
     * Returns true when the ball reaches the floor (its bottom edge touches y = [GameConfig.rows]
     * while moving down), with [Ball.x] at the landing point. The engine then sets the ball's y,
     * velocity and state. Implementations never change [Ball.state] or the board themselves.
     */
    fun step(ball: Ball, board: Board, config: GameConfig, dt: Float, contacts: BallContacts): Boolean
}

/** What the physics reports back to the engine during a step. */
interface BallContacts {
    fun brickHit(column: Int, row: Int)
    fun wallBounce()
}
