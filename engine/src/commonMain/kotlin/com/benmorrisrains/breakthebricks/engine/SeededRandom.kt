package com.benmorrisrains.breakthebricks.engine

/**
 * SplitMix64. All game randomness goes through this so a seed plus the player's inputs
 * reproduce a game exactly on every platform. Never use kotlin.random or the clock in the engine.
 */
class SeededRandom(seed: Long) {
    private var state: Long = seed

    fun nextLong(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** Uniform in [0, bound). */
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        return ((nextLong() ushr 1) % bound).toInt()
    }

    /** Uniform in [from, until]. */
    fun nextIntInclusive(from: Int, until: Int): Int = from + nextInt(until - from + 1)

    /** Uniform in [0, 1). */
    fun nextFloat(): Float = (nextLong() ushr 40).toFloat() / (1L shl 24).toFloat()
}
