package com.benmorrisrains.breakthebricks.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaidLevelsTest {
    @Test
    fun levelsElevenToThirtyFollowTheFreeOnes() {
        assertEquals(PaidLevels.COUNT, PaidLevels.all.size)
        assertEquals((11..30).toList(), PaidLevels.all.map { it.number })
        assertEquals((1..30).toList(), Levels.all.map { it.number })
        assertEquals(Levels.all.size, Levels.all.map { it.seed }.toSet().size, "every level has its own seed")
        assertEquals(PaidLevels[21], Levels[21])
        assertTrue(PaidLevels.all.all { it.isPaid })
        assertTrue(FreeLevels.all.none { it.isPaid })
    }

    /** Claude's default (T18): a few levels lend one power-up each, and every power-up gets one. */
    @Test
    fun eachPowerUpHasAShowcaseLevel() {
        val showcases = PaidLevels.all.filter { it.power != null }
        assertEquals(Abilities.earnable, showcases.map { it.power })
        assertTrue(FreeLevels.all.none { it.power != null })
    }

    @Test
    fun aLentPowerUpIsActiveFromTheFirstShot() {
        for (level in PaidLevels.all) {
            val game = level.newGame(GridPhysics)
            assertEquals(RoundPhase.Aiming, game.phase, "level ${level.number}")
            assertEquals(listOfNotNull(level.power), game.activeAbilities, "level ${level.number}")
            assertEquals(level.power, game.selectedAbility)
        }
        // A staked or boosted copy of the same power-up doesn't make it act twice.
        val drill = PaidLevels.all.first { it.power == Drill }
        assertEquals(listOf(Drill), drill.newGame(GridPhysics, abilities = listOf(Drill)).activeAbilities)
    }

    /**
     * The plan's "done when": all 20 parse and the test bot clears each one, fast enough for
     * 2 stars, with 3 stars possible (not before the last row) but never slower than the bot.
     */
    @Test
    fun theBotClearsEveryLevel() {
        val problems = mutableListOf<String>()
        for (level in PaidLevels.all) {
            val game = botRuns.getValue(level.number).game
            val result = "level ${level.number}: ${game.phase} at round ${game.round} with ${game.points} points"
            if (game.phase != RoundPhase.LevelComplete) problems += "$result, should pass"
            else if (level.stars(passed = true, rounds = game.round) < 2) problems += "$result, should earn 2 stars"
            else if (level.threeStarRounds !in level.rowCount..maxOf(level.rowCount, game.round)) {
                problems += "$result, 3 stars should need rounds ${level.rowCount}..${game.round}"
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    /**
     * Every paid level has more to break than level 10, the last free one, and they get heavier:
     * the bot's points (one per hit) never drop by much from one level to the next, and the
     * last five average more than the first five. Random aiming never clears one.
     */
    @Test
    fun paidLevelsAreHarderThanLevelTenAndKeepGettingHarder() {
        val levelTen = LevelBot.play(FreeLevels[10]).game.points
        val points = PaidLevels.all.map { botRuns.getValue(it.number).game.points }
        assertTrue(points.all { it > levelTen }, "level 10 scores $levelTen, paid levels $points")
        assertTrue(points.takeLast(5).average() > points.take(5).average() * 1.5, "$points")
        val plain = PaidLevels.all.zip(points).filter { (level, _) -> level.power == null }.map { it.second }
        assertEquals(plain.sorted(), plain, "levels without a lent power-up get heavier in order")
        for (level in PaidLevels.all) {
            val random = SeededRandom(level.seed * 31)
            val passes = (1..RANDOM_TRIALS).count {
                val shots = List(level.rowCount + RANDOM_EXTRA_ROUNDS) { 0.3f + random.nextFloat() * 2.54f }
                LevelBot.replay(level, shots).phase == RoundPhase.LevelComplete
            }
            assertTrue(passes <= RANDOM_TRIALS / 10, "random aim clears level ${level.number} $passes times in $RANDOM_TRIALS")
        }
    }

    /** A showcase level suits its power-up: without it, the bot clears it more slowly or not at all. */
    @Test
    fun showcaseLevelsFavorTheirPowerUp() {
        for (level in PaidLevels.all.filter { it.power != null }) {
            val with = botRuns.getValue(level.number).game
            val without = LevelBot.play(level.copy(power = null)).game
            val slower = without.phase != RoundPhase.LevelComplete || without.round > with.round
            assertTrue(slower, "level ${level.number} (${level.power?.id}): ${without.phase} at round ${without.round} without it")
        }
    }

    @Test
    fun theBotsShotsReplayExactly() {
        val level = PaidLevels.all.first { it.power == Splitter }
        val run = botRuns.getValue(level.number)
        val replay = LevelBot.replay(level, run.angles)
        assertEquals(run.game.points, replay.points)
        assertEquals(run.game.phase, replay.phase)
        assertFalse(replay.board.bricks().any())
    }

    private companion object {
        const val RANDOM_TRIALS = 40
        const val RANDOM_EXTRA_ROUNDS = 8

        /** One bot run per level, shared by the tests above: the runs are the slow part. */
        val botRuns: Map<Int, LevelBot.Run> by lazy { PaidLevels.all.associate { it.number to LevelBot.play(it) } }
    }
}
