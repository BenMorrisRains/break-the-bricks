package com.benmorrisrains.breakthebricks.engine

/**
 * The big ball (Ben: earned at round 10). The round's first ball flies at [SIZE] times the normal
 * size, and when it hits the face of a brick while hanging over the brick beside it, that brick
 * takes a hit too. Ben's playtest call (2026-10-13): big enough to hit two bricks at a time.
 */
object BigBall : Ability {
    override val id = "big_ball"
    override val earnedAtRound = 10
    override val wagerTargetRound = 15

    /** How many times the normal radius the first ball is. */
    const val SIZE = 3f

    override fun onFirstLaunch(context: AbilityContext, ball: Ball) {
        ball.size = SIZE
    }

    override fun onFirstBallHit(context: AbilityContext, ball: Ball, column: Int, row: Int) {
        val radius = ball.radius(context.config)
        // The centre is strictly inside the brick's span along the face it hit; a corner hit (or
        // a hit exactly on the seam, which already reports both bricks) has no neighbor to add.
        val onTopOrBottom = ball.x > column && ball.x < column + 1 && (ball.y <= row || ball.y >= row + 1)
        val onSide = ball.y > row && ball.y < row + 1 && (ball.x <= column || ball.x >= column + 1)
        when {
            onTopOrBottom && ball.x - radius < column -> context.damage(column - 1, row, 1)
            onTopOrBottom && ball.x + radius > column + 1 -> context.damage(column + 1, row, 1)
            onSide && ball.y - radius < row -> context.damage(column, row - 1, 1)
            onSide && ball.y + radius > row + 1 -> context.damage(column, row + 1, 1)
        }
    }
}
