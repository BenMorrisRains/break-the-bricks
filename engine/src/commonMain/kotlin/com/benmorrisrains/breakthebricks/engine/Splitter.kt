package com.benmorrisrains.breakthebricks.engine

/**
 * The splitter (Ben: earned at round 10). On its first brick hit the round's first ball splits
 * into 3: it carries on bouncing as usual and two normal balls fly off either side of it.
 */
object Splitter : Ability {
    override val id = "splitter"
    override val earnedAtRound = 10
    override val wagerTargetRound = 15

    /** Balls added on the split, so 3 in all. */
    const val EXTRA_BALLS = 2

    /** Angle between the first ball and each new ball, in radians (about 17 degrees). */
    const val SPREAD_RADIANS = 0.3f

    override fun onFirstHit(context: AbilityContext, column: Int, row: Int) {
        context.splitFirstBall(EXTRA_BALLS, SPREAD_RADIANS)
    }
}
