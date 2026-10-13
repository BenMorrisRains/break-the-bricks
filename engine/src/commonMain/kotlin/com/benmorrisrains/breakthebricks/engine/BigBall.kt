package com.benmorrisrains.breakthebricks.engine

/** The big ball (Ben: earned at round 5). The round's first ball flies at twice the normal size. */
object BigBall : Ability {
    override val id = "big_ball"
    override val earnedAtRound = 5
    override val wagerTargetRound = 10

    /** How many times the normal radius the first ball is. */
    const val SIZE = 2f

    override fun onFirstLaunch(context: AbilityContext, ball: Ball) {
        ball.size = SIZE
    }
}
