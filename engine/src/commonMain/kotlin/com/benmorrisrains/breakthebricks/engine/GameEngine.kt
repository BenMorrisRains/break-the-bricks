package com.benmorrisrains.breakthebricks.engine

/**
 * The contract between the engine and the UI. The UI calls [aim], [selectAbility], [unselectAbility], [fire] and [redirect] from touch input,
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

    /**
     * The one active ability the next shot's first ball uses, or null when none is picked.
     * In free play it stays picked until the player picks another; until then it is the first
     * one active. With [singleUsePowerUps] it is null until picked and is unpicked once used.
     */
    val selectedAbility: Ability? get() = activeAbilities.firstOrNull()
    /** In a level, the rounds that bring a new row (then the board must be cleared); null in an endless game. */
    val levelRows: Int? get() = null

    /** Sets the aim while [phase] is [RoundPhase.Aiming]; clamped to the allowed range. */
    fun aim(angleRadians: Float)

    /** Picks which active ability the next shot uses. Only while [phase] is [RoundPhase.Aiming]; an id that isn't active or is spent is ignored. */
    fun selectAbility(abilityId: String) {}

    /**
     * True in a level: each power-up acts on one shot only and nothing is picked until the
     * player picks it. False in free play, where the pick acts on every shot.
     */
    val singleUsePowerUps: Boolean get() = false

    /** With [singleUsePowerUps], the active power-ups whose one shot is already used. */
    val spentAbilities: Set<String> get() = emptySet()

    /** With [singleUsePowerUps], unpicks the power-up so the next shot goes without one. Only while Aiming. */
    fun unselectAbility() {}

    /** Fires this round's balls. Ignored outside [RoundPhase.Aiming]. */
    fun fire()

    /**
     * Turns [ability] on for the rest of this game (for one shot, with [singleUsePowerUps]), from the next shot's first ball: a one-game
     * boost such as a rewarded ad's. It never touches the wager inventory. False when the game is
     * over or the ability is already on (or already waiting for the next shot).
     */
    fun boost(ability: Ability): Boolean = false

    /** True while a time freeze has play in slow motion and [redirect] is allowed. */
    val timeFrozen: Boolean get() = false

    /**
     * During a time freeze, sends every flying ball off along [angleRadians] (measured like
     * [aimAngle], any direction but too flat) at its own speed, and ends the freeze. Ignored otherwise.
     */
    fun redirect(angleRadians: Float) {}

    /** Runs as many fixed physics steps as [elapsedSeconds] covers. */
    fun advance(elapsedSeconds: Float)

    /** Returns and clears the events produced since the last call. */
    fun drainEvents(): List<GameEvent>
}
