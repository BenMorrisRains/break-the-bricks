package com.benmorrisrains.breakthebricks.engine

/**
 * The drill (Ben: earned at round 20). The round's first ball passes straight through up to
 * [BRICKS] bricks, hitting each once, then bounces normally.
 */
object Drill : Ability {
    override val id = "drill"
    override val earnedAtRound = 20
    override val wagerTargetRound = 25

    /** Bricks the first ball drills through. */
    const val BRICKS = 3

    override fun onFirstLaunch(context: AbilityContext, ball: Ball) {
        ball.pierce = BRICKS
    }
}
