package com.benmorrisrains.breakthebricks.engine

/**
 * A special power such as the grenade ball. Abilities act on the first ball fired each round,
 * so they never need to change the physics code. Implementations must be deterministic.
 */
interface Ability {
    /** Stable id stored in the player's inventory, e.g. "grenade". */
    val id: String

    /** Round at which a game earns this ability (Ben: grenade at round 5). */
    val earnedAtRound: Int

    /** Round a wagered copy must reach to be kept. */
    val wagerTargetRound: Int

    /** Called when the round's first ball makes its first brick hit. */
    fun onFirstHit(context: AbilityContext, column: Int, row: Int)
}

/** What an ability may touch. Points for bricks it breaks are awarded by the engine. */
interface AbilityContext {
    val board: Board
    fun damage(column: Int, row: Int, hits: Long)
    fun breakBrick(column: Int, row: Int)
}
