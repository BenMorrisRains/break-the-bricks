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

    @Test
    fun gamesUseFibonacciEighthsByDefault() {
        assertEquals(RowValues.FibonacciEighths, GameConfig().rowValues)
    }

    @Test
    fun fibonacciSaturatesInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE, fibonacci(200))
    }
}
