package com.benmorrisrains.breakthebricks.platform

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChipTonesTest {
    @Test
    fun everyEffectHasAudibleSamples() {
        SoundEffect.entries.forEach { effect ->
            val samples = ChipTones.pcm(effect)
            assertTrue(samples.isNotEmpty(), "$effect is empty")
            assertTrue(samples.any { it > 1000 || it < -1000 }, "$effect is silent")
        }
    }

    @Test
    fun effectsStayShortExceptGameOver() {
        SoundEffect.entries.filter { it != SoundEffect.GameOver }.forEach { effect ->
            val millis = ChipTones.pcm(effect).size * 1000 / ChipTones.SAMPLE_RATE
            assertTrue(millis <= 250, "$effect lasts $millis ms")
        }
    }

    @Test
    fun notesFadeInAndOutWithoutClicks() {
        SoundEffect.entries.forEach { effect ->
            val samples = ChipTones.pcm(effect)
            assertEquals(0, samples.first().toInt(), "$effect starts with a click")
            assertEquals(0, samples.last().toInt(), "$effect ends with a click")
        }
    }

    @Test
    fun outputIsTheSameEveryTime() {
        SoundEffect.entries.forEach { effect ->
            assertContentEquals(ChipTones.wav(effect), ChipTones.wav(effect))
        }
    }

    @Test
    fun wavHeaderDescribes16BitMonoPcm() {
        val samples = ChipTones.pcm(SoundEffect.BrickHit)
        val wav = ChipTones.wav(SoundEffect.BrickHit)
        assertEquals("RIFF", wav.ascii(0))
        assertEquals("WAVE", wav.ascii(8))
        assertEquals("fmt ", wav.ascii(12))
        assertEquals("data", wav.ascii(36))
        assertEquals(1, wav.int16(20)) // PCM
        assertEquals(1, wav.int16(22)) // mono
        assertEquals(ChipTones.SAMPLE_RATE, wav.int32(24))
        assertEquals(16, wav.int16(34))
        assertEquals(samples.size * 2, wav.int32(40))
        assertEquals(wav.size - 8, wav.int32(4))
        // Little-endian samples follow the header.
        val middle = samples.size / 2
        assertEquals(samples[middle], wav.int16(44 + middle * 2).toShort())
    }

    private fun ByteArray.ascii(at: Int) = (at until at + 4).map { this[it].toInt().toChar() }.joinToString("")
    private fun ByteArray.int16(at: Int) = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)
    private fun ByteArray.int32(at: Int) = int16(at) or (int16(at + 2) shl 16)
}
