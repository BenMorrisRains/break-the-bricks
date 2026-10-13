package com.benmorrisrains.breakthebricks.engine

/**
 * The drill (Ben: earned at round 20). The round's first ball breaks every brick it touches,
 * whatever its number, without slowing, and leaves the field through the top wall instead of
 * bouncing back (Ben, 2026-10-13).
 */
object Drill : Ability {
    override val id = "drill"
    override val earnedAtRound = 20
    override val wagerTargetRound = 25

    override fun onFirstLaunch(context: AbilityContext, ball: Ball) {
        ball.pierce = Int.MAX_VALUE
        ball.exitsAtCeiling = true
    }

    override fun onFirstBallHit(context: AbilityContext, ball: Ball, column: Int, row: Int) {
        context.breakBrick(column, row)
    }
}
