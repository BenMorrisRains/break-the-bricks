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
    fun fibonacciThirdsIsFibonacciOverThreeRoundedUp() {
        val expected = listOf(1L, 1, 1, 1, 2, 3, 5, 7, 12, 19, 30, 48)
        assertEquals(expected, (1..12).map { RowValues.FibonacciThirds.valueFor(it) })
        assertEquals(Long.MAX_VALUE / 3 + 1, RowValues.FibonacciThirds.valueFor(200))
    }

    @Test
    fun gamesUseFibonacciThirdsByDefault() {
        assertEquals(RowValues.FibonacciThirds, GameConfig().rowValues)
    }

    @Test
    fun fibonacciSaturatesInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE, fibonacci(200))
    }
}
