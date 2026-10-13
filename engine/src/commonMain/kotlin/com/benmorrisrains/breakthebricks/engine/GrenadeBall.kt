package com.benmorrisrains.breakthebricks.engine

/**
 * The grenade ball (Ben: earned at round 30). The round's first ball explodes on its first hit
 * and takes out that brick whatever its number, then carries on as a normal ball. The broken
 * brick scores its remaining hits. Ben's playtest call: only the hit brick, no neighbors.
 */
object GrenadeBall : Ability {
    override val id = "grenade"
    override val earnedAtRound = 30
    override val wagerTargetRound = 35

    override fun onFirstHit(context: AbilityContext, column: Int, row: Int) {
        context.breakBrick(column, row)
    }
}
