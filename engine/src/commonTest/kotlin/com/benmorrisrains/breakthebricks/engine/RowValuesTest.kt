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
    fun fibonacciSaturatesInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE, fibonacci(200))
    }
}
