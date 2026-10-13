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

    /**
     * Called on every brick hit by the round's first ball, after that hit's damage and after
     * [onFirstHit], with [ball] at the point of contact.
     */
    fun onFirstBallHit(context: AbilityContext, ball: Ball, column: Int, row: Int) {}
}

/** What an ability may touch. Points for bricks it breaks are awarded by the engine. */
interface AbilityContext {
    val board: Board
    val config: GameConfig
    fun damage(column: Int, row: Int, hits: Long)
    fun breakBrick(column: Int, row: Int)

    /** Seeded, uniform in [0, bound), from a stream that never changes the rows that come. */
    fun randomInt(bound: Int): Int

    /**
     * Once its current step is done, adds [extraBalls] normal balls at the round's first ball and
     * sends all of them, the first ball too, off in seeded random directions up to
     * [maxTurnRadians] either side of its heading, each in its own slice of that range so they
     * fan out. The new balls fly for this round only.
     */
    fun splitFirstBall(extraBalls: Int, maxTurnRadians: Float)
}

/** Abilities a game can earn by reaching their [Ability.earnedAtRound], in round order. */
object Abilities {
    val earnable: List<Ability> = listOf(BigBall, Splitter, Drill, GrenadeBall)
}
