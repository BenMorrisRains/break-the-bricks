package com.benmorrisrains.breakthebricks.engine

/**
 * The drill (Ben: earned at round 30). The round's first ball flies straight, breaking every
 * brick in its path whatever its number, and is gone the moment it touches any wall, side or top,
 * so it only drills what lies on its first path (Ben, 2026-10-18; on 2026-10-13 it bounced off
 * the side walls and left at the top).
 */
object Drill : Ability {
    override val id = "drill"
    override val earnedAtRound = 30
    override val wagerTargetRound = 35

    override fun onFirstLaunch(context: AbilityContext, ball: Ball) {
        ball.pierce = Int.MAX_VALUE
        ball.exitsAtWall = true
    }

    override fun onFirstBallHit(context: AbilityContext, ball: Ball, column: Int, row: Int) {
        context.breakBrick(column, row)
    }
}
