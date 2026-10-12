package com.benmorrisrains.breakthebricks.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SeededRandomTest {
    @Test
    fun sameSeedGivesSameSequence() {
        val a = SeededRandom(42)
        val b = SeededRandom(42)
        repeat(1_000) { assertEquals(a.nextLong(), b.nextLong()) }
    }

    @Test
    fun differentSeedsDiffer() {
        assertNotEquals(SeededRandom(1).nextLong(), SeededRandom(2).nextLong())
    }

    @Test
    fun knownFirstValueIsStableAcrossPlatforms() {
        // Reference SplitMix64 output for seed 0. If this changes, every saved level seed changes.
        assertEquals(-2152535657050944081L, SeededRandom(0).nextLong())
    }

    @Test
    fun boundedValuesStayInRange() {
        val random = SeededRandom(7)
        repeat(10_000) {
            assertTrue(random.nextInt(7) in 0 until 7)
            assertTrue(random.nextIntInclusive(1, 6) in 1..6)
            val f = random.nextFloat()
            assertTrue(f >= 0f && f < 1f)
        }
    }
}
