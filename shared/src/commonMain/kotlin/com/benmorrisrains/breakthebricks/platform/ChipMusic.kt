package com.benmorrisrains.breakthebricks.platform

import com.benmorrisrains.breakthebricks.platform.ChipSynth.Note
import com.benmorrisrains.breakthebricks.platform.ChipSynth.Wave
import com.benmorrisrains.breakthebricks.platform.ChipSynth.hz

/**
 * The in-game music: an 8-bar synthwave-flavored chiptune loop in A minor at 120 BPM, about
 * 16 seconds, synthesized with [ChipSynth] (no files, no licensing). It has four parts, like an
 * old console: a pumping triangle bass, a thin pulse arpeggio, a pulse lead, and noise drums.
 *
 * The loop is exactly [BARS] bars long and every note ends before the loop does, so it repeats
 * without a gap or a click. It is mixed quieter than the sound effects so hits stay on top.
 */
internal object ChipMusic {
    const val SAMPLE_RATE = ChipSynth.SAMPLE_RATE
    const val BPM = 120
    const val BARS = 8
    private const val STEPS_PER_BAR = 16 // sixteenth notes
    private const val STEPS = BARS * STEPS_PER_BAR

    /** Samples in the whole loop: 8 bars of 4 beats at [BPM]. */
    const val LENGTH = SAMPLE_RATE * 60 * 4 * BARS / BPM

    /** A chord: the bass root and three arpeggio notes, as MIDI numbers. */
    private class Chord(val bass: Int, val arp: List<Int>)

    private val Am = Chord(45, listOf(69, 72, 76))
    private val F = Chord(41, listOf(65, 69, 72))
    private val C = Chord(48, listOf(67, 72, 76))
    private val G = Chord(43, listOf(67, 71, 74))
    private val E = Chord(40, listOf(68, 71, 76))

    /** One chord per bar; the E at the end leads back into the Am at the top. */
    private val chords = listOf(Am, F, C, G, Am, F, C, E)

    /** Lead melody notes per bar: (start step, length in steps, MIDI note). */
    private val melody: List<List<Triple<Int, Int, Int>>> = listOf(
        listOf(Triple(0, 6, 76), Triple(6, 2, 74), Triple(8, 4, 72), Triple(12, 4, 69)),
        listOf(Triple(0, 4, 72), Triple(4, 4, 74), Triple(8, 8, 72)),
        listOf(Triple(0, 6, 76), Triple(6, 2, 79), Triple(8, 4, 76), Triple(12, 4, 72)),
        listOf(Triple(0, 8, 74), Triple(8, 4, 71), Triple(12, 4, 74)),
        listOf(Triple(0, 6, 81), Triple(6, 2, 79), Triple(8, 4, 76), Triple(12, 4, 72)),
        listOf(Triple(0, 4, 77), Triple(4, 4, 76), Triple(8, 8, 72)),
        listOf(Triple(0, 6, 76), Triple(6, 2, 74), Triple(8, 4, 72), Triple(12, 4, 67)),
        listOf(Triple(0, 8, 71), Triple(8, 4, 68), Triple(12, 4, 71)),
    )

    private val kick = Note(140.0, 45.0, Wave.Triangle, volume = 0.5, decay = true)
    private val snare = Note(ChipSynth.SAMPLE_RATE.toDouble(), wave = Wave.Noise, volume = 0.16, decay = true)
    private val snareBody = Note(200.0, 150.0, Wave.Triangle, volume = 0.12, decay = true)
    private val hat = Note(ChipSynth.SAMPLE_RATE.toDouble(), wave = Wave.Noise, volume = 0.05, decay = true)

    /** Signed 16-bit samples for the whole loop. Same output on every call. */
    fun pcm(): ShortArray {
        val mix = DoubleArray(LENGTH)
        fun at(step: Int) = (step.toLong() * LENGTH / STEPS).toInt()
        fun steps(count: Int) = at(count)
        fun play(step: Int, length: Int, note: Note) = ChipSynth.add(mix, at(step), length, note)

        for (bar in 0 until BARS) {
            val chord = chords[bar]
            val top = bar * STEPS_PER_BAR
            for (s in 0 until STEPS_PER_BAR) {
                val step = top + s
                // Bass: eighth notes pumping between the root and its octave, played short.
                if (s % 2 == 0) {
                    val midi = chord.bass + if (s % 4 == 2) 12 else 0
                    play(step, steps(2) * 3 / 4, Note(hz(midi), wave = Wave.Triangle, volume = 0.32))
                }
                // Arpeggio: sixteenths up the chord and to the octave.
                val arp = chord.arp.getOrElse(s % 4) { chord.arp[0] + 12 }
                play(step, steps(1), Note(hz(arp), wave = Wave.Pulse12, volume = 0.07, decay = true))
                // Drums: kick on beats 1 and 3, snare on 2 and 4, hats on the off eighths.
                when (s) {
                    0, 8 -> play(step, ChipSynth.samplesFor(110), kick)
                    4, 12 -> {
                        play(step, ChipSynth.samplesFor(120), snare)
                        play(step, ChipSynth.samplesFor(50), snareBody)
                    }
                    2, 6, 10, 14 -> play(step, ChipSynth.samplesFor(25), hat)
                }
            }
            for ((start, length, midi) in melody[bar]) {
                val note = Note(hz(midi), wave = Wave.Pulse25, volume = 0.1, vibrato = if (length >= 4) 0.25 else 0.0)
                // Released just before the next note so repeated pitches still sound separate.
                play(top + start, steps(length) - ChipSynth.samplesFor(20), note)
            }
        }
        for (i in mix.indices) mix[i] *= MASTER
        return ChipSynth.toPcm(mix)
    }

    /** [pcm] as a WAV file. */
    fun wav(): ByteArray = ChipSynth.wav(pcm())

    /** Overall level: the loop sits well under the sound effects. */
    private const val MASTER = 0.45
}
