package com.benmorrisrains.breakthebricks.engine

/**
 * The grenade ball (Ben: earned at round 5). The round's first ball explodes on its first hit,
 * breaking the hit brick and up to [BLAST_SIZE] of the nearest other bricks, then carries on
 * as a normal ball. Broken bricks score their remaining hits.
 */
object GrenadeBall : Ability {
    override val id = "grenade"
    override val earnedAtRound = 5
    override val wagerTargetRound = 10

    /** How many bricks besides the hit one the blast breaks. */
    const val BLAST_SIZE = 5

    override fun onFirstHit(context: AbilityContext, column: Int, row: Int) {
        // Pick the targets before breaking anything so the order can't change the choice.
        val targets = blastTargets(context.board, column, row)
        context.breakBrick(column, row)
        for (brick in targets) context.breakBrick(brick.column, brick.row)
    }

    /**
     * The up to [BLAST_SIZE] bricks nearest to ([column], [row]), excluding that cell.
     * Nearest is by squared distance between cell centers; ties go to the higher row (smaller
     * row index), then the left column, so replays always pick the same bricks.
     */
    fun blastTargets(board: Board, column: Int, row: Int): List<Brick> =
        board.bricks()
            .filter { it.column != column || it.row != row }
            .sortedWith(
                compareBy<Brick>(
                    { (it.column - column) * (it.column - column) + (it.row - row) * (it.row - row) },
                    { it.row },
                    { it.column },
                ),
            )
            .take(BLAST_SIZE)
            .toList()
}

/** Abilities a game can earn by reaching their [Ability.earnedAtRound]. */
object Abilities {
    val earnable: List<Ability> = listOf(GrenadeBall)
}
