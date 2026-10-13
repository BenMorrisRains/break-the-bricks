package com.benmorrisrains.breakthebricks.engine

/**
 * One hand-made level. A level is a fixed seed plus a few settings, so every attempt sees the
 * same starting bricks and the same new rows. New rows stop after [rowCount] rounds, and the
 * level is passed only by breaking every brick (Ben, 2026-10-13); clearing it in fewer rounds
 * earns the 2nd and 3rd star. Levels keep their own layouts and are not scaled by player rank.
 */
data class Level(
    val number: Int,
    val name: String,
    val seed: Long,
    /** Rounds that bring a new row. After that no rows come, and the board must be cleared. */
    val rowCount: Int,
    /** Bricks per new row, so early levels ease in with fewer. */
    val minBricksPerRow: Int,
    val maxBricksPerRow: Int,
    /** Clearing the board within this many rounds earns 2 or 3 stars. Any clear earns 1. */
    val twoStarRounds: Int,
    val threeStarRounds: Int,
    /** Bricks on the board before round 1, top row first; each row has [GameConfig.columns] cells. */
    val layout: List<List<Long?>> = emptyList(),
    /** New rows are worth what they would be this many rounds later, for tougher rows sooner. */
    val valuesAhead: Int = 0,
) {
    init {
        require(number >= 1) { "level numbers start at 1" }
        require(rowCount >= 1) { "level $number: at least 1 round of new rows" }
        require(valuesAhead >= 0) { "level $number: ahead can't be negative" }
        require(threeStarRounds in 1..twoStarRounds) { "level $number: star rounds must be 0 < three <= two" }
        require(layout.all { row -> row.all { it == null || it >= 1 } }) { "level $number: brick values must be at least 1" }
    }

    /** The game settings for this level on top of [base]. */
    fun config(base: GameConfig = GameConfig()): GameConfig =
        base.copy(
            minBricksPerRow = minBricksPerRow,
            maxBricksPerRow = maxBricksPerRow,
            rowValues = base.rowValues.startingAhead(valuesAhead),
        )

    /** A new attempt at this level. Abilities are earned as in any game. */
    fun newGame(
        physics: BallPhysics,
        abilities: List<Ability> = emptyList(),
        earnable: List<Ability> = Abilities.earnable,
        base: GameConfig = GameConfig(),
    ): BrickGame = BrickGame(
        config = config(base),
        seed = seed,
        physics = physics,
        abilities = abilities,
        earnable = earnable,
        layout = layout,
        levelRows = rowCount,
    )

    /** Stars for a finished attempt: 0 if it failed, else 1 to 3 by how few rounds the clear took. */
    fun stars(passed: Boolean, rounds: Int): Int = when {
        !passed -> 0
        rounds <= threeStarRounds -> 3
        rounds <= twoStarRounds -> 2
        else -> 1
    }
}
