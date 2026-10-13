package com.benmorrisrains.breakthebricks.engine

/**
 * A special power such as the grenade ball. The player picks one active ability per shot
 * ([GameEngine.selectAbility]) and it acts on that round's first ball, so abilities never need
 * to change the physics code. Implementations must be deterministic.
 */
interface Ability {
    /** Stable id stored in the player's inventory, e.g. "grenade". */
    val id: String

    /** Round at which a game earns this ability (Ben's lineup: big ball 5, splitter 10, drill 20, grenade 30). */
    val earnedAtRound: Int

    /** Round a wagered copy must reach to be kept. */
    val wagerTargetRound: Int

    /**
     * Called as the round's first ball leaves the launch point, before it moves. An ability may
     * change that ball's [Ball.size] or [Ball.pierce]; both last until it lands.
     */
    fun onFirstLaunch(context: AbilityContext, ball: Ball) {}

    /** Called when the round's first ball makes its first brick hit. */
    fun onFirstHit(context: AbilityContext, column: Int, row: Int) {}
}

/** What an ability may touch. Points for bricks it breaks are awarded by the engine. */
interface AbilityContext {
    val board: Board
    val config: GameConfig
    fun damage(column: Int, row: Int, hits: Long)
    fun breakBrick(column: Int, row: Int)

    /**
     * Adds [extraBalls] normal balls beside the round's first ball once its current step is done,
     * aimed [spreadRadians] apart either side of it. They fly for this round only.
     */
    fun splitFirstBall(extraBalls: Int, spreadRadians: Float)
}

/** Abilities a game can earn by reaching their [Ability.earnedAtRound], in round order. */
object Abilities {
    val earnable: List<Ability> = listOf(BigBall, Splitter, Drill, GrenadeBall)
}
