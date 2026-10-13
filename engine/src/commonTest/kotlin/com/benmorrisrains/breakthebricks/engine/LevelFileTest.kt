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
        rows 5
        bricks 1 2
        stars 16 12

        level 2
        name Two Rows
        seed -7
        rows 9
        bricks 2 4
        stars 14 11
        ahead 3
        row 1 . . 12 . . 1
        row . 2 . . . 2 .
    """.trimIndent()

    @Test
    fun parsesEveryField() {
        val (first, second) = LevelFile.parse(sample)
        assertEquals(Level(1, "Warm Up", 101, 5, 1, 2, 16, 12), first)
        assertEquals(2, second.number)
        assertEquals("Two Rows", second.name)
        assertEquals(-7L, second.seed)
        assertEquals(9, second.rowCount)
        assertEquals(2, second.minBricksPerRow)
        assertEquals(4, second.maxBricksPerRow)
        assertEquals(14, second.twoStarRounds)
        assertEquals(11, second.threeStarRounds)
        assertEquals(3, second.valuesAhead)
        assertEquals(0, first.valuesAhead)
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
        val good = "level 1\nname A\nseed 1\nrows 3\nbricks 1 2\nstars 9 5\n"
        assertEquals(1, lineOf("seed 4"))
        assertEquals(7, lineOf(good + "level 3"))
        assertEquals(7, lineOf(good + "row 1 1 1"))
        assertEquals(7, lineOf(good + "row 1 . . x . . ."))
        assertEquals(7, lineOf(good + "row 0 . . . . . ."))
        assertEquals(7, lineOf(good + "speed 3"))
        assertEquals(7, lineOf(good + "bricks 3 2"))
        assertEquals(7, lineOf(good + "bricks 1 8"))
        assertEquals(7, lineOf(good + "stars 5 9"))
        assertEquals(7, lineOf(good + "ahead -1"))
        assertEquals(7, lineOf(good + "power laser"))
        assertEquals(1, lineOf("level 1\nname A\nseed 1\nbricks 1 2\nstars 9 5\n"))
    }

    @Test
    fun numberingCanStartLaterAndALevelCanLendAPowerUp() {
        val text = "level 11\nname A\nseed 1\nrows 3\nbricks 1 2\nstars 9 5\npower drill\n" +
            "level 12\nname B\nseed 2\nrows 3\nbricks 1 2\nstars 9 5\n"
        val (eleven, twelve) = LevelFile.parse(text, first = 11)
        assertEquals(11, eleven.number)
        assertEquals(Drill, eleven.power)
        assertEquals(null, twelve.power)
        // Numbering starts at 1 unless told otherwise.
        assertEquals(1, assertFailsWith<LevelFormatException> { LevelFile.parse(text) }.line)
    }

    @Test
    fun aMissingKeyIsAnError() {
        val error = assertFailsWith<LevelFormatException> { LevelFile.parse("level 1\nname A\nrows 3\nbricks 1 2\nstars 9 5") }
        assertTrue("seed" in error.message.orEmpty())
    }
}
