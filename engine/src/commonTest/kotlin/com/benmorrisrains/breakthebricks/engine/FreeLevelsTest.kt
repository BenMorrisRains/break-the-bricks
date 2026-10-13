package com.benmorrisrains.breakthebricks.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FreeLevelsTest {
    @Test
    fun thereAreTenNumberedLevels() {
        assertEquals(FreeLevels.COUNT, FreeLevels.all.size)
        assertEquals((1..10).toList(), FreeLevels.all.map { it.number })
        assertEquals(FreeLevels.all.size, FreeLevels.all.map { it.seed }.toSet().size, "every level has its own seed")
        assertEquals(4, FreeLevels[4].number)
    }

    @Test
    fun levelsEaseInThenGetLonger() {
        val levels = FreeLevels.all
        assertTrue(levels.zipWithNext().all { (a, b) -> b.goalRounds >= a.goalRounds })
        assertTrue(levels.zipWithNext().all { (a, b) -> b.maxBricksPerRow >= a.maxBricksPerRow })
        assertTrue(levels.first().maxBricksPerRow <= 3)
        assertEquals(0, levels.first().valuesAhead)
    }

    /**
     * Ben's playtest (2026-10-13): the first levels were too easy; aiming at random passed them
     * every time. Every level now starts with bricks low enough to need real aim, so a random
     * aimer fails a fair share of attempts, and fails more often by level 10.
     */
    @Test
    fun randomAimingFailsEveryLevelOften() {
        val passRates = FreeLevels.all.map { level ->
            val random = SeededRandom(level.seed * 31)
            val passes = (1..RANDOM_TRIALS).count {
                val shots = List(level.goalRounds) { 0.3f + random.nextFloat() * 2.54f }
                LevelBot.replay(level, shots).phase == RoundPhase.LevelComplete
            }
            passes.toDouble() / RANDOM_TRIALS
        }
        assertTrue(passRates.all { it <= 0.8 }, "random aim passes too often: $passRates")
        assertTrue(passRates.last() <= 0.2, "level 10 should be hard without aim: $passRates")
        assertTrue(passRates.last() < passRates.first(), "level 10 should be harder than level 1: $passRates")
    }

    @Test
    fun everyLevelStartsCleanly() {
        for (level in FreeLevels.all) {
            val game = level.newGame(GridPhysics)
            assertEquals(RoundPhase.Aiming, game.phase, "level ${level.number}")
            assertEquals(1, game.round)
        }
    }

    /** The plan's "done when": all 10 levels playable and beatable. A simple bot passes each one, short of 3 stars. */
    @Test
    fun aSimpleBotBeatsEveryLevelWithoutThreeStars() {
        val problems = mutableListOf<String>()
        for (level in FreeLevels.all) {
            val run = LevelBot.play(level)
            val game = run.game
            val result = "level ${level.number}: ${game.phase} at round ${game.round} with ${game.points} points"
            if (game.phase != RoundPhase.LevelComplete) problems += "$result, should pass"
            else if (level.stars(passed = true, points = game.points) == 3) problems += "$result, 3 stars should need more"
            // The recorded shots replay to the same result.
            val replay = LevelBot.replay(level, run.angles)
            assertEquals(game.points, replay.points)
            assertEquals(game.phase, replay.phase)
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    private companion object {
        const val RANDOM_TRIALS = 40
    }
}
