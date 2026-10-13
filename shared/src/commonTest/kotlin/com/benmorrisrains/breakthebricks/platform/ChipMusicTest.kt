package com.benmorrisrains.breakthebricks.platform

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChipMusicTest {
    private val tracks = ChipMusic.tracks.indices.map { ChipMusic.pcm(it) }

    @Test
    fun thereAreSeveralDifferentTracks() {
        assertTrue(tracks.size >= 4)
        assertEquals(tracks.size, ChipMusic.tracks.map { it.name }.toSet().size, "names are unique")
        assertEquals(tracks.size, ChipMusic.tracks.map { it.bpm }.toSet().size, "each has its own tempo")
    }

    @Test
    fun eachLoopIsExactlyEightBarsAtItsTempo() {
        ChipMusic.tracks.forEachIndexed { i, track ->
            // 8 bars of 4 beats: 32 beats.
            assertEquals(32.0 * 60 / track.bpm, tracks[i].size.toDouble() / ChipMusic.SAMPLE_RATE, 1e-9, track.name)
            assertEquals(tracks[i].size, track.length)
            assertEquals(ChipMusic.BARS, track.chords.size, track.name)
            assertEquals(ChipMusic.BARS, track.melody.size, track.name)
        }
        // The first track is the original 16-second loop.
        assertEquals(16 * ChipMusic.SAMPLE_RATE, tracks[0].size)
    }

    @Test
    fun melodiesStayInsideTheirBars() {
        ChipMusic.tracks.forEach { track ->
            track.melody.flatten().forEach { note ->
                assertTrue(note.start >= 0 && note.start + note.length <= 16, "${track.name}: note runs past its bar")
            }
        }
    }

    @Test
    fun eachLoopJoinsWithoutAClick() {
        tracks.forEachIndexed { i, samples ->
            assertEquals(0, samples.first().toInt(), ChipMusic.tracks[i].name)
            assertEquals(0, samples.last().toInt(), ChipMusic.tracks[i].name)
        }
    }

    @Test
    fun everyBarIsPlayingAndNothingClips() {
        tracks.forEachIndexed { t, samples ->
            val name = ChipMusic.tracks[t].name
            val bar = samples.size / ChipMusic.BARS
            for (i in 0 until ChipMusic.BARS) {
                val slice = samples.copyOfRange(i * bar, (i + 1) * bar)
                assertTrue(rms(slice) > 1_000, "$name bar ${i + 1} is too quiet")
            }
            assertTrue(samples.maxOf { abs(it.toInt()) } < Short.MAX_VALUE, "$name clips")
        }
    }

    @Test
    fun theMusicSitsUnderTheSoundEffects() {
        tracks.forEachIndexed { t, samples ->
            val music = rms(samples)
            listOf(SoundEffect.BrickHit, SoundEffect.BrickBreak).forEach { effect ->
                assertTrue(music < rms(ChipTones.pcm(effect)), "${ChipMusic.tracks[t].name} is louder than $effect")
            }
        }
    }

    @Test
    fun outputIsTheSameEveryTime() {
        ChipMusic.tracks.indices.forEach { assertContentEquals(ChipMusic.wav(it), ChipMusic.wav(it)) }
    }

    private fun rms(samples: ShortArray) = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
}
