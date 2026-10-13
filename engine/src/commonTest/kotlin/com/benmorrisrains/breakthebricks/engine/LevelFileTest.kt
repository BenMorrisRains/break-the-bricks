package com.benmorrisrains.breakthebricks.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LevelFileTest {
    private val sample = """
        # two levels
        level 1
        name Warm Up   # trailing comment
        seed 101
        goal 5
        bricks 1 2
        stars 12 16

        level 2
        name Two Rows
        seed -7
        goal 9
        bricks 2 4
        stars 40 60
        row 1 . . 12 . . 1
        row . 2 . . . 2 .
    """.trimIndent()

    @Test
    fun parsesEveryField() {
        val (first, second) = LevelFile.parse(sample)
        assertEquals(Level(1, "Warm Up", 101, 5, 1, 2, 12, 16), first)
        assertEquals(2, second.number)
        assertEquals("Two Rows", second.name)
        assertEquals(-7L, second.seed)
        assertEquals(9, second.goalRounds)
        assertEquals(2, second.minBricksPerRow)
        assertEquals(4, second.maxBricksPerRow)
        assertEquals(40L, second.twoStarPoints)
        assertEquals(60L, second.threeStarPoints)
        assertEquals(
            listOf(listOf(1L, null, null, 12L, null, null, 1L), listOf(null, 2L, null, null, null, 2L, null)),
            second.layout,
        )
    }

    @Test
    fun emptyTextHasNoLevels() {
        assertEquals(emptyList(), LevelFile.parse("# nothing here\n\n"))
    }

    @Test
    fun errorsNameTheLine() {
        fun lineOf(text: String) = assertFailsWith<LevelFormatException> { LevelFile.parse(text) }.line
        val good = "level 1\nname A\nseed 1\ngoal 3\nbricks 1 2\nstars 5 9\n"
        assertEquals(1, lineOf("seed 4"))
        assertEquals(7, lineOf(good + "level 3"))
        assertEquals(7, lineOf(good + "row 1 1 1"))
        assertEquals(7, lineOf(good + "row 1 . . x . . ."))
        assertEquals(7, lineOf(good + "row 0 . . . . . ."))
        assertEquals(7, lineOf(good + "speed 3"))
        assertEquals(7, lineOf(good + "bricks 3 2"))
        assertEquals(7, lineOf(good + "bricks 1 8"))
        assertEquals(7, lineOf(good + "stars 9 5"))
        assertEquals(1, lineOf("level 1\nname A\nseed 1\nbricks 1 2\nstars 5 9\n"))
    }

    @Test
    fun aMissingKeyIsAnError() {
        val error = assertFailsWith<LevelFormatException> { LevelFile.parse("level 1\nname A\ngoal 3\nbricks 1 2\nstars 5 9") }
        assertTrue("seed" in error.message.orEmpty())
    }
}
