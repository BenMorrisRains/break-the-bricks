package com.benmorrisrains.breakthebricks.engine

import kotlin.math.PI

/**
 * A simple greedy player for checking levels: each round it tries a fan of angles, replays the
 * level from the start with each one, and keeps the angle that leaves the safest board.
 * Deterministic, so it gives the same result every run.
 */
object LevelBot {
    private const val ANGLE_COUNT = 25
    private val angles = List(ANGLE_COUNT) { i ->
        val margin = 0.2f
        margin + i * (PI.toFloat() - 2 * margin) / (ANGLE_COUNT - 1)
    }

    class Run(val game: BrickGame, val angles: List<Float>)

    fun play(level: Level, physics: BallPhysics = GridPhysics): Run {
        val chosen = mutableListOf<Float>()
        while (true) {
            val (angle, game) = angles
                .map { it to replay(level, chosen + it, physics) }
                .maxBy { (_, game) -> score(game) }
            chosen += angle
            if (game.phase.isFinished) return Run(game, chosen)
        }
    }

    /** Plays [level] firing [shots] in order, one per round, stepping until each round is over. */
    fun replay(level: Level, shots: List<Float>, physics: BallPhysics = GridPhysics): BrickGame {
        val game = level.newGame(physics)
        for (angle in shots) {
            if (game.phase != RoundPhase.Aiming) break
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
        RoundPhase.LevelComplete -> 1e15 + game.points
        else -> game.points - game.board.bricks().sumOf { brick ->
            val depth = (brick.row + 1).toDouble()
            brick.hits * depth * depth
        }
    }
}
