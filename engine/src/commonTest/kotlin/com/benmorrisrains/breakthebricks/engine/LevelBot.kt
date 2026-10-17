package com.benmorrisrains.breakthebricks.engine

import kotlin.math.PI

/**
 * A simple greedy player for checking levels: each round it tries a fan of angles, each with no
 * power-up and with each power-up it still has, replays the level from the start with each,
 * and keeps the shot that leaves the safest board. A power-up acts on one shot per level, so
 * the bot spends it on the round where it helps most right away. Deterministic, so it gives
 * the same result every run.
 */
object LevelBot {
    private const val ANGLE_COUNT = 25
    private val angles = List(ANGLE_COUNT) { i ->
        val margin = 0.2f
        margin + i * (PI.toFloat() - 2 * margin) / (ANGLE_COUNT - 1)
    }

    /** A finished run: the [angles] fired and the power-up [picks] (null for none), one per round. */
    class Run(val game: BrickGame, val angles: List<Float>, val picks: List<String?>)

    fun play(level: Level, physics: BallPhysics = GridPhysics): Run {
        val chosen = mutableListOf<Float>()
        val picks = mutableListOf<String?>()
        var game = level.newGame(physics)
        while (true) {
            val options = listOf<String?>(null) + game.activeAbilities.map { it.id }.filter { it !in game.spentAbilities }
            val (shot, next) = options
                .flatMap { pick -> angles.map { it to pick } }
                .map { shot -> shot to replay(level, chosen + shot.first, physics, picks + shot.second) }
                .maxBy { (_, game) -> score(game) }
            chosen += shot.first
            picks += shot.second
            game = next
            if (game.phase.isFinished) return Run(game, chosen, picks)
        }
    }

    /**
     * Plays [level] firing [shots] in order, one per round, with the power-up in [picks] at the
     * same index (none when null or missing), stepping until each round is over.
     */
    fun replay(level: Level, shots: List<Float>, physics: BallPhysics = GridPhysics, picks: List<String?> = emptyList()): BrickGame {
        val game = level.newGame(physics)
        for ((index, angle) in shots.withIndex()) {
            if (game.phase != RoundPhase.Aiming) break
            val pick = picks.getOrNull(index)
            if (pick == null) game.unselectAbility() else game.selectAbility(pick)
            game.aim(angle)
            game.fire()
            var steps = 0
            while (game.phase != RoundPhase.Aiming && !game.phase.isFinished) {
                game.step()
                check(++steps < 2_000_000) { "round ${game.round} never ended" }
            }
        }
        return game
    }

    /** Higher is better: win, then avoid the floor, then score. Low bricks with many hits count most. */
    private fun score(game: BrickGame): Double = when (game.phase) {
        RoundPhase.GameOver -> -1e18 + game.round
        RoundPhase.LevelComplete -> 1e15 - game.round * 1e6 + game.points
        else -> game.points - game.board.bricks().sumOf { brick ->
            val depth = (brick.row + 1).toDouble()
            brick.hits * depth * depth
        }
    }
}
