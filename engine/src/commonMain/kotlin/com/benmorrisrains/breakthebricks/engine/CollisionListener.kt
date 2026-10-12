package com.benmorrisrains.breakthebricks.engine

/**
 * Receives contacts while [BallPhysics.step] moves a ball. Calls happen in a fixed order, so a
 * seeded replay sees the same calls every time.
 */
interface CollisionListener {
    /**
     * The ball touched [brick]. Apply the hit here (take 1 off, score, break, run abilities).
     * Physics reads the board again after every contact, so a brick removed here is gone for the
     * rest of the step. When one contact touches two bricks at once, both are reported, ordered
     * by row and then column, before the ball reflects once.
     */
    fun onBrickHit(brick: Brick) {}

    /** The ball bounced off the left wall, the right wall or the ceiling. */
    fun onWallBounce() {}

    companion object {
        /** Ignores every contact. */
        val None: CollisionListener = object : CollisionListener {}
    }
}

/** What happened to a ball during one [BallPhysics.step]. */
enum class StepOutcome {
    /** Still in the air (or has no velocity). */
    Flying,

    /** Reached the floor this step. The ball rests at [BallPhysics.restingY] with zero velocity. */
    Landed,
}
