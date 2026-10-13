package com.benmorrisrains.breakthebricks.platform

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChipMusicTest {
    private val samples = ChipMusic.pcm()

    @Test
    fun theLoopIsExactlyEightBarsAt120Bpm() {
        // 8 bars of 4 beats at 120 BPM is 16 seconds.
        assertEquals(16 * ChipMusic.SAMPLE_RATE, samples.size)
        assertEquals(samples.size, ChipMusic.LENGTH)
    }

    @Test
    fun theLoopJoinsWithoutAClick() {
        assertEquals(0, samples.first().toInt())
        assertEquals(0, samples.last().toInt())
    }

    @Test
    fun everyBarIsPlayingAndNothingClips() {
        val bar = samples.size / ChipMusic.BARS
        for (i in 0 until ChipMusic.BARS) {
            val slice = samples.copyOfRange(i * bar, (i + 1) * bar)
            assertTrue(rms(slice) > 1_000, "bar ${i + 1} is too quiet")
        }
        assertTrue(samples.maxOf { abs(it.toInt()) } < Short.MAX_VALUE, "the music clips")
    }

    @Test
    fun theMusicSitsUnderTheSoundEffects() {
        val music = rms(samples)
        listOf(SoundEffect.BrickHit, SoundEffect.BrickBreak).forEach { effect ->
            assertTrue(music < rms(ChipTones.pcm(effect)), "music is louder than $effect")
        }
    }

    @Test
    fun outputIsTheSameEveryTime() {
        assertContentEquals(ChipMusic.wav(), ChipMusic.wav())
    }

    private fun rms(samples: ShortArray) = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
}
