package com.benmorrisrains.breakthebricks.engine

import kotlin.test.Test
import kotlin.test.assertEquals

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
