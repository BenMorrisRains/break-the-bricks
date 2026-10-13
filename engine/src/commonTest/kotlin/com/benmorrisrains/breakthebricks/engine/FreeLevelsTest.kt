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
        assertTrue(levels.first().maxBricksPerRow <= 2)
        assertTrue(levels.first().layout.isEmpty())
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
}
