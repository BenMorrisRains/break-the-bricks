package com.benmorrisrains.breakthebricks.engine

/**
 * The splitter (Ben: earned at round 10). On its first brick hit the round's first ball splits
 * into 3, and all three fly off wildly in different, seeded random directions (Ben's playtest,
 * 2026-10-13: "go kind of crazy in different directions").
 */
object Splitter : Ability {
    override val id = "splitter"
    override val earnedAtRound = 10
    override val wagerTargetRound = 15

    /** Balls added on the split, so 3 in all. */
    const val EXTRA_BALLS = 2

    /** Furthest a ball turns from the first ball's heading, in radians (about 75 degrees). */
    const val MAX_TURN_RADIANS = 1.3f

    override fun onFirstHit(context: AbilityContext, column: Int, row: Int) {
        context.splitFirstBall(EXTRA_BALLS, MAX_TURN_RADIANS)
    }
}
