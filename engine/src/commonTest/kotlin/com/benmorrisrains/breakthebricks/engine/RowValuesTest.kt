package com.benmorrisrains.breakthebricks.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RowValuesTest {
    @Test
    fun fibonacciMatchesBensSequence() {
        val expected = listOf(1L, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89)
        assertEquals(expected, (1..11).map { RowValues.Fibonacci.valueFor(it) })
    }

    @Test
    fun fibonacciEighthsIsFibonacciOverEightRoundedUp() {
        val expected = listOf(1L, 1, 1, 1, 1, 1, 2, 3, 5, 7, 12, 18, 30, 48, 77)
        assertEquals(expected, (1..15).map { RowValues.FibonacciEighths.valueFor(it) })
        assertEquals(Long.MAX_VALUE / 8 + 1, RowValues.FibonacciEighths.valueFor(200))
    }

    @Test
    fun fibonacciOverAnyDivisorRoundsUp() {
        assertEquals(listOf(1L, 1, 1, 1, 2, 3, 5, 7, 12, 19), (1..10).map { RowValues.fibonacciOver(3).valueFor(it) })
        assertEquals((1..12).map { fibonacci(it) }, (1..12).map { RowValues.fibonacciOver(1).valueFor(it) })
    }

    /** Ben, 2026-10-17: values still climbed too fast, so each one lasts two rounds. */
    @Test
    fun halfSpeedHoldsEachValueForTwoRounds() {
        val expected = listOf(1L, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 3, 3, 5, 5, 7, 7, 12, 12, 18, 18)
        assertEquals(expected, (1..24).map { RowValues.FibonacciEighthsHalfSpeed.valueFor(it) })
        val half = RowValues.Fibonacci.halfSpeed()
        assertEquals(listOf(1L, 1, 1, 1, 2, 2, 3, 3), (1..8).map { half.valueFor(it) })
    }

    /** Ben, 2026-10-17 (free play): values took too many rounds to go up; now every 3 rounds. */
    @Test
    fun everyThreeRoundsStepsUpEveryThirdRound() {
        val expected = listOf(1L, 1, 1, 2, 2, 2, 3, 3, 3, 5, 5, 5, 7, 7, 7, 12, 12, 12, 18)
        assertEquals(expected, (1..19).map { RowValues.FibonacciEighthsEveryThreeRounds.valueFor(it) })
        assertEquals(RowValues.FibonacciEighthsHalfSpeed.valueFor(30), RowValues.FibonacciEighthsEveryThreeRounds.valueFor(30))
        assertTrue(RowValues.FibonacciEighthsEveryThreeRounds.valueFor(60) < RowValues.FibonacciEighthsHalfSpeed.valueFor(60))
        assertEquals(RowValues.FibonacciEighthsEveryThreeRounds, Difficulty.forRank(1).rowValues, "free play at rank 1")
    }

    @Test
    fun gamesUseHalfSpeedFibonacciEighthsByDefault() {
        assertEquals(RowValues.FibonacciEighthsHalfSpeed, GameConfig().rowValues)
    }

    @Test
    fun fibonacciSaturatesInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE, fibonacci(200))
    }

    @Test
    fun startingAheadShiftsRounds() {
        val ahead = RowValues.FibonacciEighths.startingAhead(3)
        assertEquals((4..13).map { RowValues.FibonacciEighths.valueFor(it) }, (1..10).map { ahead.valueFor(it) })
        assertEquals(RowValues.FibonacciEighths, RowValues.FibonacciEighths.startingAhead(0))
    }
}
