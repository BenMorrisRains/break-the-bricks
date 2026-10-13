package com.benmorrisrains.breakthebricks.engine

/** Maps a round number (1-based) to the hit count and point value of that round's new bricks. */
fun interface RowValues {
    fun valueFor(round: Int): Long

    companion object {
        /** Ben's rule: 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, ... by round. */
        val Fibonacci = RowValues { round -> fibonacci(round) }

        /**
         * Ben's playtest call: Fibonacci divided by 8, rounded up, so values stay small for longer:
         * 1 for rounds 1 to 6, then 2, 3, 5, 7, 12, 18, 30, 48, ... Never below 1.
         * (The first playtest used 3, which got hard too quickly.)
         */
        val FibonacciEighths = fibonacciOver(8)

        /** Fibonacci by round divided by [divisor], rounded up. Never below 1. */
        fun fibonacciOver(divisor: Long): RowValues {
            require(divisor >= 1)
            return RowValues { round ->
                val value = fibonacci(round)
                value / divisor + if (value % divisor == 0L) 0 else 1
            }
        }
    }
}

/**
 * These values started [rounds] rounds further along: round 1 is worth what round 1 + [rounds]
 * is worth here. Higher player ranks use this to start on tougher rows.
 */
fun RowValues.startingAhead(rounds: Int): RowValues {
    require(rounds >= 0)
    if (rounds == 0) return this
    return RowValues { round -> valueFor(round + rounds) }
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
