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

        /**
         * Ben, 2026-10-17: values still climbed too quickly, so each [FibonacciEighths] value now
         * lasts two rounds: 1 for rounds 1 to 12, then 2, 2, 3, 3, 5, 5, 7, 7, 12, 12, ...
         * Levels use this; free play uses [FibonacciEighthsEveryThreeRounds].
         */
        val FibonacciEighthsHalfSpeed = FibonacciEighths.halfSpeed()

        /**
         * Ben, 2026-10-17 (free play): rows took too many rounds to go up, so the [FibonacciEighths]
         * values now step up every 3 rounds from the start: 1, 1, 1, 2, 2, 2, 3, 3, 3, 5, 5, 5, 7,
         * 7, 7, 12, ... It meets [FibonacciEighthsHalfSpeed] at round 30 (77) and climbs slower after.
         */
        val FibonacciEighthsEveryThreeRounds = RowValues { round ->
            // FibonacciEighths is 1 up to round 6, then a new value each round; step 1 is its round 6.
            FibonacciEighths.valueFor(5 + (round + 2) / 3)
        }

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

/** Each value lasts two rounds: round 2n - 1 and round 2n are worth what round n was. */
fun RowValues.halfSpeed(): RowValues = RowValues { round -> valueFor((round + 1) / 2) }

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
