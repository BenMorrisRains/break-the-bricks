package com.benmorrisrains.breakthebricks.engine

/**
 * The 5-way splitter (Ben, 2026-10-13; earned at round 70). Like the
 * [Splitter], but the round's first ball splits into 5 on its first hit.
 */
object SplitterFive : Ability {
    override val id = "splitter_five"
    override val earnedAtRound = 70
    override val wagerTargetRound = 75

    /** Balls added on the split, so 5 in all. */
    const val EXTRA_BALLS = 4

    override fun onFirstHit(context: AbilityContext, column: Int, row: Int) {
        context.splitFirstBall(EXTRA_BALLS, Splitter.MAX_TURN_RADIANS)
    }
}
