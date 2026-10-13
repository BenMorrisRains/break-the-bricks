package com.benmorrisrains.breakthebricks.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class TogglesTest {
    private class RecordingAudio : ToggleableAudio() {
        val played = mutableListOf<SoundEffect>()
        val music = mutableListOf<String>()
        override fun playEffect(effect: SoundEffect) {
            played += effect
        }
        override fun resumeMusic() {
            music += "resume"
        }
        override fun pauseMusic() {
            music += "pause"
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
    fun musicPlaysOnlyWhenWantedAndSwitchedOn() {
        val audio = RecordingAudio()
        assertEquals(emptyList(), audio.music, "silent until a game wants it")
        audio.setMusicPlaying(true)
        assertEquals(listOf("resume"), audio.music)
        audio.setMusicPlaying(true)
        assertEquals(listOf("resume"), audio.music, "asking again changes nothing")
        audio.setMusicEnabled(false)
        audio.setMusicPlaying(false)
        audio.setMusicPlaying(true)
        assertEquals(listOf("resume", "pause"), audio.music, "the toggle keeps it off")
        audio.setMusicEnabled(true)
        audio.setMusicPlaying(false)
        assertEquals(listOf("resume", "pause", "resume", "pause"), audio.music)
    }

    @Test
    fun musicPausesInTheBackgroundAndComesBack() {
        val audio = RecordingAudio()
        audio.setMusicPlaying(true)
        audio.setAppActive(false)
        assertEquals(false, audio.musicOn)
        audio.setAppActive(true)
        assertEquals(true, audio.musicOn)
        assertEquals(listOf("resume", "pause", "resume"), audio.music)
    }

    @Test
    fun effectsToggleDoesNotStopMusic() {
        val audio = RecordingAudio()
        audio.setMusicPlaying(true)
        audio.setEffectsEnabled(false)
        assertEquals(true, audio.musicOn)
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
