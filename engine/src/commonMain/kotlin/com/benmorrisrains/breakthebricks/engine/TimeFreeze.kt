package com.benmorrisrains.breakthebricks.engine

/**
 * The time freeze (Ben's idea; earned at round 60). On the round's first
 * ball's first hit, play drops to slow motion for [STEPS] steps, and the player can swipe once
 * ([GameEngine.redirect]) to send every flying ball in a new direction. The swipe ends the freeze.
 */
object TimeFreeze : Ability {
    override val id = "time_freeze"
    override val earnedAtRound = 60
    override val wagerTargetRound = 65

    /** Fixed steps the freeze lasts: 3 seconds. */
    const val STEPS = 3 * GameConfig.STEPS_PER_SECOND

    /** How fast the balls move during the freeze, as a fraction of normal. */
    const val TIME_SCALE = 0.15f

    override fun onFirstHit(context: AbilityContext, column: Int, row: Int) {
        context.freezeTime(STEPS, TIME_SCALE)
    }
}
