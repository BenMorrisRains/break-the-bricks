package com.benmorrisrains.breakthebricks.engine

/**
 * One hand-made level. A level is a fixed seed plus a few settings, so every attempt sees the
 * same starting bricks and the same new rows. Pass it by surviving [goalRounds] rounds; points
 * earn the 2nd and 3rd star. Levels keep their own layouts and are not scaled by player rank.
 */
data class Level(
    val number: Int,
    val name: String,
    val seed: Long,
    /** Rounds to survive. The level is won when the balls of this round are all back. */
    val goalRounds: Int,
    /** Bricks per new row, so early levels ease in with fewer. */
    val minBricksPerRow: Int,
    val maxBricksPerRow: Int,
    /** Points for 2 and 3 stars. Passing the level always earns 1. */
    val twoStarPoints: Long,
    val threeStarPoints: Long,
    /** Bricks on the board before round 1, top row first; each row has [GameConfig.columns] cells. */
    val layout: List<List<Long?>> = emptyList(),
    /** New rows are worth what they would be this many rounds later, for tougher rows sooner. */
    val valuesAhead: Int = 0,
) {
    init {
        require(number >= 1) { "level numbers start at 1" }
        require(goalRounds >= 1) { "level $number: goal must be at least 1 round" }
        require(valuesAhead >= 0) { "level $number: ahead can't be negative" }
        require(twoStarPoints in 1..threeStarPoints) { "level $number: star points must be 0 < two <= three" }
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
        goalRounds = goalRounds,
    )

    /** Stars for a finished attempt: 0 if it failed, else 1 to 3 by points. */
    fun stars(passed: Boolean, points: Long): Int = when {
        !passed -> 0
        points >= threeStarPoints -> 3
        points >= twoStarPoints -> 2
        else -> 1
    }
}
