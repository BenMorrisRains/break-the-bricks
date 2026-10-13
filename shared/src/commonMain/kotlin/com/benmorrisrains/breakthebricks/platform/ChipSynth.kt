package com.benmorrisrains.breakthebricks.platform

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A tiny 8-bit style synthesizer shared by the sound effects ([ChipTones]) and the music loop
 * ([ChipMusic]). Everything is generated in code, so there are no audio files and nothing to
 * license, and the output is the same on every call and every platform.
 */
internal object ChipSynth {
    const val SAMPLE_RATE = 22_050

    /** NES-like voices: pulse waves of three widths, a triangle and a noise channel. */
    enum class Wave { Pulse12, Pulse25, Square, Triangle, Noise }

    /**
     * One note. The pitch slides linearly from [startHz] to [endHz]. With [decay] the volume
     * falls away over the note, which gives plucks and blips; without it the note holds.
     * [vibrato] is a pitch wobble in semitones that fades in over the note. For [Wave.Noise] the
     * pitch is how often a new random value is picked, so lower values sound rougher and deeper.
     */
    class Note(
        val startHz: Double,
        val endHz: Double = startHz,
        val wave: Wave = Wave.Square,
        val volume: Double = 0.3,
        val decay: Boolean = false,
        val vibrato: Double = 0.0,
    )

    /** Pitch of a MIDI note number (69 is A4 at 440 Hz). */
    fun hz(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    fun samplesFor(millis: Int) = SAMPLE_RATE * millis / 1000

    /**
     * Adds [note] into [out] from [start] for [length] samples. Each note fades in and out over
     * a few milliseconds so it never clicks, and starts its own fixed noise sequence, so the
     * result doesn't depend on what was rendered before.
     */
    fun add(out: DoubleArray, start: Int, length: Int, note: Note) {
        val count = minOf(length, out.size - start)
        if (count <= 0 || note.volume == 0.0) return
        val fade = minOf(samplesFor(3), count / 2).coerceAtLeast(1)
        var noise = 0x2F6E2B1 xor start // xorshift state, fixed per position
        var held = 0.0
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / count
            var hz = note.startHz + (note.endHz - note.startHz) * t
            if (note.vibrato != 0.0) {
                val wobble = sin(2 * PI * VIBRATO_HZ * i / SAMPLE_RATE) * note.vibrato * t
                hz *= 2.0.pow(wobble / 12.0)
            }
            val next = phase + hz / SAMPLE_RATE
            val wrapped = next >= 1.0 || i == 0
            phase = next % 1.0
            val raw = when (note.wave) {
                Wave.Pulse12 -> if (phase < 0.125) 1.0 else -1.0
                Wave.Pulse25 -> if (phase < 0.25) 1.0 else -1.0
                Wave.Square -> if (phase < 0.5) 1.0 else -1.0
                Wave.Triangle -> 4.0 * abs(phase - 0.5) - 1.0
                Wave.Noise -> {
                    if (wrapped) {
                        noise = noise xor (noise shl 13)
                        noise = noise xor (noise ushr 17)
                        noise = noise xor (noise shl 5)
                        held = (noise and 0xFFFF) / 32_767.5 - 1.0
                    }
                    held
                }
            }
            val edges = minOf(1.0, i.toDouble() / fade, (count - 1 - i).toDouble() / fade)
            val shape = if (note.decay) (1 - t) * (1 - t) else 1.0
            out[start + i] += raw * note.volume * edges * shape
        }
    }

    /** Mixed samples to signed 16-bit, clipped at full scale. */
    fun toPcm(mix: DoubleArray): ShortArray = ShortArray(mix.size) {
        (mix[it].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).roundToInt().toShort()
    }

    /** [samples] wrapped in a standard 44-byte RIFF/WAVE header: 16-bit mono PCM. */
    fun wav(samples: ShortArray): ByteArray {
        val dataSize = samples.size * 2
        val bytes = ByteArray(44 + dataSize)
        var at = 0
        fun ascii(text: String) = text.forEach { bytes[at++] = it.code.toByte() }
        fun int16(value: Int) {
            bytes[at++] = (value and 0xFF).toByte()
            bytes[at++] = ((value shr 8) and 0xFF).toByte()
        }
        fun int32(value: Int) {
            int16(value and 0xFFFF)
            int16((value ushr 16) and 0xFFFF)
        }
        ascii("RIFF"); int32(36 + dataSize); ascii("WAVE")
        ascii("fmt "); int32(16)
        int16(1) // PCM
        int16(1) // mono
        int32(SAMPLE_RATE)
        int32(SAMPLE_RATE * 2) // byte rate
        int16(2) // block align
        int16(16) // bits per sample
        ascii("data"); int32(dataSize)
        samples.forEach { int16(it.toInt()) }
        return bytes
    }

    private const val VIBRATO_HZ = 5.5
}
