package com.benmorrisrains.breakthebricks.engine

import kotlin.math.abs
import kotlin.math.max

/**
 * The grenade ball (Ben: earned at round 40). The round's first ball explodes on its first hit:
 * it takes out that brick and 3 to 5 bricks near it (Ben's playtest, 2026-10-13), whatever their
 * numbers, then carries on as a normal ball. Every broken brick scores its remaining hits.
 */
object GrenadeBall : Ability {
    override val id = "grenade"
    override val earnedAtRound = 40
    override val wagerTargetRound = 45

    /** Bricks the blast takes out besides the one hit: a seeded pick in this range. */
    val BLAST_BRICKS = 3..5

    /** How far (in cells, diagonals counting 1) the blast reaches from the hit brick. */
    const val BLAST_REACH = 2

    override fun onFirstHit(context: AbilityContext, column: Int, row: Int) {
        context.breakBrick(column, row)
        val count = BLAST_BRICKS.first + context.randomInt(BLAST_BRICKS.last - BLAST_BRICKS.first + 1)
        for ((c, r) in blastTargets(context, column, row, count)) context.breakBrick(c, r)
    }

    /**
     * Up to [count] bricks within [BLAST_REACH] of ([column], [row]), nearest first; bricks at
     * the same distance are taken in a seeded random order.
     */
    private fun blastTargets(context: AbilityContext, column: Int, row: Int, count: Int): List<Pair<Int, Int>> {
        val near = context.board.bricks()
            .map { it.column to it.row }
            .filter { (c, r) -> (c != column || r != row) && distance(c, r, column, row) <= BLAST_REACH }
            .sortedWith(compareBy({ it.second }, { it.first }))
            .toMutableList()
        // Seeded shuffle, then a stable sort by distance, so ties are broken at random.
        for (i in near.size - 1 downTo 1) {
            val j = context.randomInt(i + 1)
            val swap = near[i]
            near[i] = near[j]
            near[j] = swap
        }
        return near.sortedBy { (c, r) -> distance(c, r, column, row) }.take(count)
    }

    private fun distance(c1: Int, r1: Int, c2: Int, r2: Int) = max(abs(c1 - c2), abs(r1 - r2))
}
