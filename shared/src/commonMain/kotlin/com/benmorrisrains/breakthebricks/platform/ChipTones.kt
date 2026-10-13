package com.benmorrisrains.breakthebricks.platform

import com.benmorrisrains.breakthebricks.platform.ChipSynth.Note
import com.benmorrisrains.breakthebricks.platform.ChipSynth.Wave
import com.benmorrisrains.breakthebricks.platform.ChipSynth.hz

/**
 * The chiptune sound effects, synthesized in code with [ChipSynth]: no audio files and no
 * licensing. Each effect is rendered once to 16-bit mono WAV bytes, which Android (SoundPool),
 * iOS (AVAudioPlayer) and desktop (javax.sound) all play directly.
 *
 * The sounds a volley repeats dozens of times a second (hit, break, wall) are short and soft;
 * the ones that mark a moment (grenade, ability earned, level passed, game over) are longer and
 * louder.
 */
internal object ChipTones {
    const val SAMPLE_RATE = ChipSynth.SAMPLE_RATE

    /** One timed note in a layer. */
    private class Step(val millis: Int, val note: Note)

    private fun rest(millis: Int) = Step(millis, Note(0.0, volume = 0.0))

    /** An effect is one or more layers that play at the same time, each a run of notes. */
    private fun layersFor(effect: SoundEffect): List<List<Step>> = when (effect) {
        // A crisp, short blip.
        SoundEffect.BrickHit -> listOf(
            listOf(Step(32, Note(1320.0, 1180.0, Wave.Pulse25, volume = 0.35, decay = true))),
        )
        // A rising pop with a puff of noise.
        SoundEffect.BrickBreak -> listOf(
            listOf(Step(50, Note(520.0, 1560.0, Wave.Square, volume = 0.32, decay = true))),
            listOf(rest(20), Step(50, Note(11_025.0, wave = Wave.Noise, volume = 0.22, decay = true))),
        )
        // A soft, low tick, so a hundred of them don't tire the ear.
        SoundEffect.WallBounce -> listOf(
            listOf(Step(24, Note(330.0, wave = Wave.Triangle, volume = 0.35, decay = true))),
        )
        // A falling "pew".
        SoundEffect.Fire -> listOf(
            listOf(Step(80, Note(1600.0, 480.0, Wave.Pulse12, volume = 0.3, decay = true))),
        )
        // The first ball home: a tiny high tick marking the next launch point.
        SoundEffect.BallReturn -> listOf(
            listOf(Step(20, Note(hz(96), wave = Wave.Pulse12, volume = 0.18, decay = true))),
        )
        // The new row is in and it's the player's turn: a low thud.
        SoundEffect.RoundStart -> listOf(
            listOf(Step(70, Note(190.0, 90.0, Wave.Triangle, volume = 0.45, decay = true))),
        )
        // The grenade going off: a rough noise blast over a falling boom.
        SoundEffect.AbilityTrigger -> listOf(
            listOf(Step(280, Note(3_000.0, 800.0, Wave.Noise, volume = 0.38, decay = true))),
            listOf(Step(260, Note(150.0, 35.0, Wave.Square, volume = 0.24, decay = true))),
        )
        // An ability earned: a rising arpeggio that lands on a held, wobbling top note.
        SoundEffect.AbilityEarned -> listOf(
            listOf(72, 76, 79).map { Step(60, Note(hz(it), wave = Wave.Pulse25, volume = 0.22)) } +
                Step(260, Note(hz(84), wave = Wave.Pulse25, volume = 0.22, decay = true, vibrato = 0.4)),
            listOf(Step(440, Note(hz(48), wave = Wave.Triangle, volume = 0.3, decay = true))),
        )
        // Level passed: a short victory jingle.
        SoundEffect.LevelComplete -> listOf(
            listOf(72, 76, 79, 84).map { Step(80, Note(hz(it), wave = Wave.Pulse25, volume = 0.22)) } +
                rest(40) + Step(80, Note(hz(79), wave = Wave.Pulse25, volume = 0.22)) +
                Step(380, Note(hz(84), wave = Wave.Pulse25, volume = 0.22, decay = true, vibrato = 0.4)),
            listOf(
                Step(320, Note(hz(48), wave = Wave.Triangle, volume = 0.3)),
                rest(40),
                Step(500, Note(hz(43), hz(48), Wave.Triangle, volume = 0.3, decay = true)),
            ),
        )
        // Game over: a slow falling line.
        SoundEffect.GameOver -> listOf(
            listOf(67, 64, 60).map { Step(160, Note(hz(it), wave = Wave.Triangle, volume = 0.4)) } +
                Step(420, Note(hz(55), hz(54), Wave.Triangle, volume = 0.4, decay = true, vibrato = 0.3)),
            listOf(55, 52, 48).map { Step(160, Note(hz(it), wave = Wave.Pulse12, volume = 0.1)) } +
                Step(420, Note(hz(43), wave = Wave.Pulse12, volume = 0.1, decay = true)),
        )
    }

    /** Signed 16-bit samples for [effect] at [SAMPLE_RATE]. Same output on every call. */
    fun pcm(effect: SoundEffect): ShortArray {
        val layers = layersFor(effect)
        val mix = DoubleArray(layers.maxOf { layer -> layer.sumOf { ChipSynth.samplesFor(it.millis) } })
        for (layer in layers) {
            var offset = 0
            for (step in layer) {
                val count = ChipSynth.samplesFor(step.millis)
                ChipSynth.add(mix, offset, count, step.note)
                offset += count
            }
        }
        return ChipSynth.toPcm(mix)
    }

    /** [pcm] as a WAV file. */
    fun wav(effect: SoundEffect): ByteArray = ChipSynth.wav(pcm(effect))
}
