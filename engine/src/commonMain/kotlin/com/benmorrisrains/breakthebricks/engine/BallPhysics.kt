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
     * During [BallContacts.brickHit], [Ball.x] and [Ball.y] are at the point of contact.
     *
     * The ball is a circle of [Ball.radius]. While [Ball.pierce] is above 0 it passes through the
     * bricks it hits instead of bouncing, taking 1 off [Ball.pierce] per brick, and is not hit
     * again by a brick it is still inside. A [Ball.ghost] ball passes through every brick it hits
     * until it is clear of them all, then stops being a ghost. A ball that [Ball.exitsAtCeiling]
     * reports [BallContacts.exited] on reaching the ceiling and stops there.
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

    /** The ball reached the ceiling and left the field (only a ball that [Ball.exitsAtCeiling]). */
    fun exited() {}
}
