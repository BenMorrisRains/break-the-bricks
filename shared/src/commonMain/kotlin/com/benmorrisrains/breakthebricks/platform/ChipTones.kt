package com.benmorrisrains.breakthebricks.platform

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Placeholder chiptune sound effects, synthesized in code so every platform has real sounds
 * before task T13 brings the final SFX. Each effect is rendered once to 16-bit mono WAV bytes,
 * which Android (SoundPool), iOS (AVAudioPlayer) and desktop (javax.sound) all play directly.
 */
internal object ChipTones {
    const val SAMPLE_RATE = 22_050

    private enum class Wave { Square, Triangle, Noise }

    /** One tone: a linear pitch sweep from [startHz] to [endHz] over [millis]. */
    private class Note(
        val startHz: Double,
        val endHz: Double = startHz,
        val millis: Int,
        val wave: Wave = Wave.Square,
        val volume: Double = 0.3,
    )

    private fun notesFor(effect: SoundEffect): List<Note> = when (effect) {
        SoundEffect.BrickHit -> listOf(Note(880.0, millis = 35, volume = 0.3))
        SoundEffect.BrickBreak -> listOf(
            Note(660.0, 1320.0, millis = 60, volume = 0.3),
            Note(0.0, millis = 40, wave = Wave.Noise, volume = 0.2),
        )
        SoundEffect.WallBounce -> listOf(Note(440.0, millis = 25, wave = Wave.Triangle, volume = 0.35))
        SoundEffect.Fire -> listOf(Note(1200.0, 600.0, millis = 50, volume = 0.2))
        SoundEffect.AbilityTrigger -> listOf(523.25, 659.25, 783.99, 1046.5).map {
            Note(it, millis = 55, volume = 0.3)
        }
        SoundEffect.GameOver -> listOf(392.0, 329.63, 261.63, 196.0).map {
            Note(it, millis = 160, wave = Wave.Triangle, volume = 0.4)
        }
    }

    /** Signed 16-bit samples for [effect] at [SAMPLE_RATE]. Same output on every call. */
    fun pcm(effect: SoundEffect): ShortArray {
        val notes = notesFor(effect)
        val out = ShortArray(notes.sumOf { samplesFor(it.millis) })
        var noise = 0x2F6E2B1 // xorshift state for the noise channel; fixed so output never varies
        var offset = 0
        for (note in notes) {
            val count = samplesFor(note.millis)
            // 2 ms fade in and out so notes don't click.
            val fade = minOf(samplesFor(2), count / 2).coerceAtLeast(1)
            var phase = 0.0
            for (i in 0 until count) {
                val t = i.toDouble() / count
                val hz = note.startHz + (note.endHz - note.startHz) * t
                phase = (phase + hz / SAMPLE_RATE) % 1.0
                val raw = when (note.wave) {
                    Wave.Square -> if (phase < 0.5) 1.0 else -1.0
                    Wave.Triangle -> 4.0 * abs(phase - 0.5) - 1.0
                    Wave.Noise -> {
                        noise = noise xor (noise shl 13)
                        noise = noise xor (noise ushr 17)
                        noise = noise xor (noise shl 5)
                        (noise and 0xFFFF) / 32_767.5 - 1.0
                    }
                }
                val envelope = minOf(1.0, i.toDouble() / fade, (count - 1 - i).toDouble() / fade)
                out[offset + i] = (raw * note.volume * envelope * Short.MAX_VALUE).roundToInt().toShort()
            }
            offset += count
        }
        return out
    }

    /** [pcm] wrapped in a standard 44-byte RIFF/WAVE header. */
    fun wav(effect: SoundEffect): ByteArray {
        val samples = pcm(effect)
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

    private fun samplesFor(millis: Int) = SAMPLE_RATE * millis / 1000
}
