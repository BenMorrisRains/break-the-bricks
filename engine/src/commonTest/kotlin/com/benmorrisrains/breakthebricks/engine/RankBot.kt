package com.benmorrisrains.breakthebricks.engine

import kotlin.math.PI

/**
 * A simple greedy player for checking free-play difficulty at a rank: each round it tries a fan
 * of angles, replays the game from the start with each one, and keeps the angle that leaves the
 * safest board. Deterministic, so it gives the same result every run. It plays with the earned
 * power-ups, using whichever the game has selected, like a player who never switches.
 */
object RankBot {
    private const val ANGLE_COUNT = 15
    private val angles = List(ANGLE_COUNT) { i ->
        val margin = 0.2f
        margin + i * (PI.toFloat() - 2 * margin) / (ANGLE_COUNT - 1)
    }

    /** Plays a free-play game at [rank] on [seed] until it ends or reaches [maxRounds]. */
    fun play(rank: Int, seed: Long, maxRounds: Int = 40, config: GameConfig = Difficulty.forRank(rank)): BrickGame {
        val chosen = mutableListOf<Float>()
        while (true) {
            val (angle, game) = angles
                .map { it to replay(config, seed, chosen + it) }
                .maxBy { (_, game) -> score(game) }
            chosen += angle
            if (game.phase.isFinished || game.round >= maxRounds) return game
        }
    }

    private fun replay(config: GameConfig, seed: Long, shots: List<Float>): BrickGame {
        val game = BrickGame(config, seed = seed, physics = GridPhysics)
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

    /** Higher is better: avoid the floor, then score. Low bricks with many hits count most. */
    private fun score(game: BrickGame): Double = when (game.phase) {
        RoundPhase.GameOver -> -1e18 + game.round
        else -> game.points - game.board.bricks().sumOf { brick ->
            val depth = (brick.row + 1).toDouble()
            brick.hits * depth * depth
        }
    }
}
