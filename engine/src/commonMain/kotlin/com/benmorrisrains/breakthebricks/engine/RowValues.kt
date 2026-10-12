package com.benmorrisrains.breakthebricks.engine

/** Maps a round number (1-based) to the hit count and point value of that round's new bricks. */
fun interface RowValues {
    fun valueFor(round: Int): Long

    companion object {
        /** Ben's rule: 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, ... by round. */
        val Fibonacci = RowValues { round -> fibonacci(round) }

        /**
         * Ben's playtest change: Fibonacci divided by 3, rounded up, so values grow more gently:
         * 1, 1, 1, 1, 2, 3, 5, 7, 12, 19, 30, ... by round. Never below 1.
         */
        val FibonacciThirds = RowValues { round ->
            val value = fibonacci(round)
            value / 3 + if (value % 3 == 0L) 0 else 1
        }
    }
}

/** fibonacci(1) = 1, fibonacci(2) = 1, fibonacci(3) = 2, ... Saturates at [Long.MAX_VALUE]. */
fun fibonacci(n: Int): Long {
    require(n >= 1) { "round numbers start at 1" }
    var previous = 0L
    var current = 1L
    repeat(n - 1) {
        val next = if (current > Long.MAX_VALUE - previous) Long.MAX_VALUE else previous + current
        previous = current
        current = next
    }
    return current
}
