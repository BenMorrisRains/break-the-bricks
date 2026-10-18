package com.benmorrisrains.breakthebricks.engine

/**
 * The ghost ball (Ben, 2026-10-13; earned at round 50). The round's first
 * ball passes through the bricks in its way, hitting each once, until it reaches open space, then
 * turns solid and bounces around inside the group it got into.
 */
object GhostBall : Ability {
    override val id = "ghost"
    override val earnedAtRound = 50
    override val wagerTargetRound = 55

    override fun onFirstLaunch(context: AbilityContext, ball: Ball) {
        ball.ghost = true
    }
}
