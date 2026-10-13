package com.benmorrisrains.breakthebricks.platform

import com.benmorrisrains.breakthebricks.platform.ChipSynth.Note
import com.benmorrisrains.breakthebricks.platform.ChipSynth.Wave
import com.benmorrisrains.breakthebricks.platform.ChipSynth.hz

/**
 * The in-game music: a set of 8-bar synthwave-flavored chiptune loops, synthesized with
 * [ChipSynth] (no files, no licensing). Each new game plays the next track in [tracks], so the
 * music changes every time the player comes in. Every track has the same four parts, like an
 * old console: a triangle bass, a thin pulse arpeggio, a pulse lead, and noise drums; tempo,
 * key, melody and the bass, arpeggio and drum patterns make each one different.
 *
 * Each loop is exactly [BARS] bars long and every note ends before the loop does, so it repeats
 * without a gap or a click. It is mixed quieter than the sound effects so hits stay on top.
 */
internal object ChipMusic {
    const val SAMPLE_RATE = ChipSynth.SAMPLE_RATE
    const val BARS = 8
    private const val STEPS_PER_BAR = 16 // sixteenth notes
    private const val STEPS = BARS * STEPS_PER_BAR

    /** A chord: the bass root and three arpeggio notes, as MIDI numbers. */
    class Chord(val bass: Int, val arp: List<Int>)

    /** One lead note: where it starts in its bar and how long it lasts, in sixteenths. */
    class LeadNote(val start: Int, val length: Int, val midi: Int)

    enum class Bass {
        /** Eighth notes pumping between the root and its octave. */
        Pump,
        /** Short sixteenths on the root, the driving synthwave bass. */
        Drive,
        /** Two long notes a bar: the root, then its fifth. */
        Long,
    }

    enum class Arp { Up, UpDown }

    enum class Drums {
        /** Kick on 1 and 3, snare on 2 and 4. */
        Backbeat,
        /** Kick on every beat, snare on 2 and 4. */
        FourOnTheFloor,
        /** Kick on 1, snare on 3: a slow, wide feel. */
        HalfTime,
    }

    class Track(
        val name: String,
        val bpm: Int,
        /** One chord per bar. */
        val chords: List<Chord>,
        /** Lead notes for each bar. */
        val melody: List<List<LeadNote>>,
        val bass: Bass,
        val arp: Arp,
        val drums: Drums,
        val lead: Wave = Wave.Pulse25,
    ) {
        /** Samples in the whole loop: [BARS] bars of 4 beats at [bpm]. */
        val length: Int get() = SAMPLE_RATE * 60 * 4 * BARS / bpm
    }

    private fun bar(vararg notes: Int): List<LeadNote> =
        notes.toList().chunked(3).map { (start, length, midi) -> LeadNote(start, length, midi) }

    /** In play order. Every tempo divides the sample rate evenly, so each loop is a whole number of samples. */
    val tracks: List<Track> = listOf(
        // A minor, 120 BPM: the first track, a steady backbeat. The E at the end leads back to Am.
        Track(
            name = "Neon Drive",
            bpm = 120,
            chords = listOf(
                Chord(45, listOf(69, 72, 76)), Chord(41, listOf(65, 69, 72)),
                Chord(48, listOf(67, 72, 76)), Chord(43, listOf(67, 71, 74)),
                Chord(45, listOf(69, 72, 76)), Chord(41, listOf(65, 69, 72)),
                Chord(48, listOf(67, 72, 76)), Chord(40, listOf(68, 71, 76)),
            ),
            melody = listOf(
                bar(0, 6, 76, 6, 2, 74, 8, 4, 72, 12, 4, 69),
                bar(0, 4, 72, 4, 4, 74, 8, 8, 72),
                bar(0, 6, 76, 6, 2, 79, 8, 4, 76, 12, 4, 72),
                bar(0, 8, 74, 8, 4, 71, 12, 4, 74),
                bar(0, 6, 81, 6, 2, 79, 8, 4, 76, 12, 4, 72),
                bar(0, 4, 77, 4, 4, 76, 8, 8, 72),
                bar(0, 6, 76, 6, 2, 74, 8, 4, 72, 12, 4, 67),
                bar(0, 8, 71, 8, 4, 68, 12, 4, 71),
            ),
            bass = Bass.Pump,
            arp = Arp.Up,
            drums = Drums.Backbeat,
        ),
        // D minor, 128 BPM: four on the floor with a driving bass.
        Track(
            name = "Night Grid",
            bpm = 128,
            chords = listOf(
                Chord(38, listOf(62, 65, 69)), Chord(34, listOf(62, 65, 70)),
                Chord(41, listOf(60, 65, 69)), Chord(36, listOf(60, 64, 67)),
                Chord(38, listOf(62, 65, 69)), Chord(34, listOf(62, 65, 70)),
                Chord(43, listOf(62, 67, 70)), Chord(45, listOf(61, 64, 69)),
            ),
            melody = listOf(
                bar(0, 4, 74, 4, 4, 77, 8, 6, 76, 14, 2, 74),
                bar(0, 8, 74, 8, 4, 70, 12, 4, 72),
                bar(0, 4, 72, 4, 4, 77, 8, 6, 76, 14, 2, 72),
                bar(0, 12, 76, 12, 4, 79),
                bar(0, 4, 81, 4, 4, 79, 8, 6, 77, 14, 2, 76),
                bar(0, 8, 74, 8, 4, 77, 12, 4, 74),
                bar(0, 4, 70, 4, 4, 74, 8, 4, 79, 12, 4, 77),
                bar(0, 8, 76, 8, 4, 73, 12, 4, 76),
            ),
            bass = Bass.Drive,
            arp = Arp.UpDown,
            drums = Drums.FourOnTheFloor,
        ),
        // C major, 140 BPM: bright and bouncy, with a square-wave lead.
        Track(
            name = "Arcade Dawn",
            bpm = 140,
            chords = listOf(
                Chord(48, listOf(72, 76, 79)), Chord(45, listOf(69, 72, 76)),
                Chord(41, listOf(69, 72, 77)), Chord(43, listOf(67, 71, 74)),
                Chord(48, listOf(72, 76, 79)), Chord(40, listOf(67, 71, 76)),
                Chord(41, listOf(69, 72, 77)), Chord(43, listOf(67, 71, 74)),
            ),
            melody = listOf(
                bar(0, 2, 76, 2, 2, 79, 4, 4, 84, 8, 2, 83, 10, 2, 81, 12, 4, 79),
                bar(0, 6, 76, 6, 2, 74, 8, 8, 72),
                bar(0, 2, 77, 2, 2, 81, 4, 4, 84, 8, 2, 84, 10, 2, 86, 12, 4, 84),
                bar(0, 8, 83, 8, 4, 79, 12, 4, 74),
                bar(0, 2, 76, 2, 2, 79, 4, 4, 84, 8, 2, 86, 10, 2, 88, 12, 4, 86),
                bar(0, 6, 83, 6, 2, 79, 8, 8, 76),
                bar(0, 4, 77, 4, 4, 81, 8, 4, 84, 12, 4, 81),
                bar(0, 8, 79, 8, 4, 83, 12, 4, 86),
            ),
            bass = Bass.Pump,
            arp = Arp.Up,
            drums = Drums.Backbeat,
            lead = Wave.Square,
        ),
        // E minor, 100 BPM: slow and spacey, half-time drums and long bass notes.
        Track(
            name = "Low Orbit",
            bpm = 100,
            chords = listOf(
                Chord(40, listOf(64, 67, 71)), Chord(36, listOf(64, 67, 72)),
                Chord(43, listOf(62, 67, 71)), Chord(38, listOf(62, 66, 69)),
                Chord(40, listOf(64, 67, 71)), Chord(36, listOf(64, 67, 72)),
                Chord(45, listOf(64, 69, 72)), Chord(35, listOf(63, 66, 71)),
            ),
            melody = listOf(
                bar(0, 12, 79, 12, 4, 78),
                bar(0, 8, 76, 8, 8, 72),
                bar(0, 12, 74, 12, 4, 71),
                bar(0, 16, 74),
                bar(0, 6, 79, 6, 6, 83, 12, 4, 81),
                bar(0, 8, 79, 8, 8, 76),
                bar(0, 8, 76, 8, 4, 72, 12, 4, 74),
                bar(0, 16, 75),
            ),
            bass = Bass.Long,
            arp = Arp.UpDown,
            drums = Drums.HalfTime,
        ),
    )

    private val kick = Note(140.0, 45.0, Wave.Triangle, volume = 0.5, decay = true)
    private val snare = Note(ChipSynth.SAMPLE_RATE.toDouble(), wave = Wave.Noise, volume = 0.16, decay = true)
    private val snareBody = Note(200.0, 150.0, Wave.Triangle, volume = 0.12, decay = true)
    private val hat = Note(ChipSynth.SAMPLE_RATE.toDouble(), wave = Wave.Noise, volume = 0.05, decay = true)

    /** Signed 16-bit samples for the whole loop of [tracks]`[index]`. Same output on every call. */
    fun pcm(index: Int = 0): ShortArray {
        val track = tracks[index]
        val mix = DoubleArray(track.length)
        fun at(step: Int) = (step.toLong() * track.length / STEPS).toInt()
        fun steps(count: Int) = at(count)
        fun play(step: Int, length: Int, note: Note) = ChipSynth.add(mix, at(step), length, note)
        fun bass(midi: Int, volume: Double = 0.32) = Note(hz(midi), wave = Wave.Triangle, volume = volume)

        for (bar in 0 until BARS) {
            val chord = track.chords[bar]
            val top = bar * STEPS_PER_BAR
            for (s in 0 until STEPS_PER_BAR) {
                val step = top + s
                // Bass, always played a little short so notes don't run together.
                when (track.bass) {
                    Bass.Pump -> if (s % 2 == 0) {
                        play(step, steps(2) * 3 / 4, bass(chord.bass + if (s % 4 == 2) 12 else 0))
                    }
                    Bass.Drive -> play(step, steps(1) * 3 / 4, bass(chord.bass, volume = 0.3))
                    Bass.Long -> if (s % 8 == 0) {
                        play(step, steps(8) * 7 / 8, bass(chord.bass + if (s == 8) 7 else 0, volume = 0.36))
                    }
                }
                // Arpeggio: sixteenths up the chord to the octave, or up and back down.
                val arp = when (track.arp) {
                    Arp.Up -> chord.arp.getOrElse(s % 4) { chord.arp[0] + 12 }
                    Arp.UpDown -> chord.arp[listOf(0, 1, 2, 1)[s % 4]]
                }
                play(step, steps(1), Note(hz(arp), wave = Wave.Pulse12, volume = 0.07, decay = true))
                drums(track.drums, s) { length, note -> play(step, ChipSynth.samplesFor(length), note) }
            }
            for (note in track.melody[bar]) {
                val tone = Note(hz(note.midi), wave = track.lead, volume = if (track.lead == Wave.Square) 0.08 else 0.1,
                    vibrato = if (note.length >= 4) 0.25 else 0.0)
                // Released just before the next note so repeated pitches still sound separate.
                play(top + note.start, steps(note.length) - ChipSynth.samplesFor(20), tone)
            }
        }
        for (i in mix.indices) mix[i] *= MASTER
        return ChipSynth.toPcm(mix)
    }

    /** The drum hits on sixteenth [s] of a bar, each as (length in ms, note). */
    private fun drums(style: Drums, s: Int, hit: (Int, Note) -> Unit) {
        fun snareHit() {
            hit(120, snare)
            hit(50, snareBody)
        }
        when (style) {
            Drums.Backbeat -> when (s) {
                0, 8 -> hit(110, kick)
                4, 12 -> snareHit()
                2, 6, 10, 14 -> hit(25, hat)
            }
            Drums.FourOnTheFloor -> {
                if (s % 4 == 0) hit(110, kick)
                if (s == 4 || s == 12) snareHit()
                if (s % 4 == 2) hit(25, hat)
            }
            Drums.HalfTime -> when (s) {
                0 -> hit(140, kick)
                8 -> snareHit()
                2, 4, 6, 10, 12, 14 -> hit(20, hat)
            }
        }
    }

    /** [pcm] as a WAV file. */
    fun wav(index: Int = 0): ByteArray = ChipSynth.wav(pcm(index))

    /** Overall level: the loop sits well under the sound effects. */
    private const val MASTER = 0.45
}
