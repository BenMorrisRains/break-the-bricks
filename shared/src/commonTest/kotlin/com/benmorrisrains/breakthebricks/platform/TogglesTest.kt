package com.benmorrisrains.breakthebricks.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class TogglesTest {
    private class RecordingAudio : ToggleableAudio() {
        val played = mutableListOf<SoundEffect>()
        override fun playEffect(effect: SoundEffect) {
            played += effect
        }
    }

    private class RecordingHaptics : ToggleableHaptics() {
        val taps = mutableListOf<HapticStrength>()
        override fun vibrate(strength: HapticStrength) {
            taps += strength
        }
    }

    @Test
    fun effectsToggleMutesAndUnmutes() {
        val audio = RecordingAudio()
        audio.play(SoundEffect.BrickHit)
        audio.setEffectsEnabled(false)
        audio.play(SoundEffect.BrickBreak)
        audio.setEffectsEnabled(true)
        audio.play(SoundEffect.Fire)
        assertEquals(listOf(SoundEffect.BrickHit, SoundEffect.Fire), audio.played)
    }

    @Test
    fun musicToggleDoesNotMuteEffects() {
        val audio = RecordingAudio()
        audio.setMusicEnabled(false)
        audio.play(SoundEffect.WallBounce)
        assertEquals(listOf(SoundEffect.WallBounce), audio.played)
        assertEquals(false, audio.musicEnabled)
    }

    @Test
    fun hapticsToggleStopsTaps() {
        val haptics = RecordingHaptics()
        haptics.tap(HapticStrength.Light)
        haptics.setEnabled(false)
        haptics.tap(HapticStrength.Heavy)
        haptics.setEnabled(true)
        haptics.tap(HapticStrength.Medium)
        assertEquals(listOf(HapticStrength.Light, HapticStrength.Medium), haptics.taps)
    }
}
