package com.benmorrisrains.breakthebricks.engine

/**
 * The contract between the engine and the UI. The UI calls [aim] and [fire] from touch input,
 * calls [advance] once per frame with the elapsed time, draws from the read-only state, and
 * plays effects for each drained [GameEvent].
 */
interface GameEngine {
    val config: GameConfig
    val board: Board
    /** This round's balls, first ball first. A split can add more than [ballCount] mid-round. */
    val balls: List<Ball>
    val phase: RoundPhase
    val round: Int
    val ballCount: Int
    val points: Long
    /** Launch point x in cells; the launch point always sits on the floor. */
    val launchX: Float
    /** Current aim in radians, measured up from the positive x axis. */
    val aimAngle: Float
    val activeAbilities: List<Ability>
    /** In a level, the rounds that bring a new row (then the board must be cleared); null in an endless game. */
    val levelRows: Int? get() = null

    /** Sets the aim while [phase] is [RoundPhase.Aiming]; clamped to the allowed range. */
    fun aim(angleRadians: Float)

    /** Fires this round's balls. Ignored outside [RoundPhase.Aiming]. */
    fun fire()

    /** Runs as many fixed physics steps as [elapsedSeconds] covers. */
    fun advance(elapsedSeconds: Float)

    /** Returns and clears the events produced since the last call. */
    fun drainEvents(): List<GameEvent>
}
