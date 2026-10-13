package com.benmorrisrains.breakthebricks.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameSettingsTest {
    private class Recorder : AudioPlayer, Haptics {
        var music: Boolean? = null
        var effects: Boolean? = null
        var haptics: Boolean? = null
        override fun play(effect: SoundEffect) = Unit
        override fun setMusicPlaying(playing: Boolean) = Unit
        override fun setMusicEnabled(enabled: Boolean) { music = enabled }
        override fun setEffectsEnabled(enabled: Boolean) { effects = enabled }
        override fun tap(strength: HapticStrength) = Unit
        override fun setEnabled(enabled: Boolean) { haptics = enabled }
    }

    private val recorder = Recorder()
    private val store = InMemoryStore()
    private val services = PlatformServices(recorder, recorder, store)

    @Test
    fun everythingStartsOnAndIsAppliedAtOnce() {
        val settings = GameSettings(services)
        Setting.entries.forEach { assertTrue(settings.isOn(it), "$it") }
        assertEquals(true, recorder.music)
        assertEquals(true, recorder.effects)
        assertEquals(true, recorder.haptics)
    }

    @Test
    fun eachToggleReachesItsOwnService() {
        val settings = GameSettings(services)
        settings.set(Setting.SoundEffects, false)
        assertEquals(false, recorder.effects)
        assertEquals(true, recorder.music)
        settings.set(Setting.Music, false)
        assertEquals(false, recorder.music)
        settings.set(Setting.Haptics, false)
        assertEquals(false, recorder.haptics)
        settings.set(Setting.Haptics, true)
        assertEquals(true, recorder.haptics)
    }

    @Test
    fun togglesSurviveARestart() {
        GameSettings(services).apply {
            set(Setting.Music, false)
            set(Setting.Haptics, false)
        }

        val freshRecorder = Recorder()
        val relaunched = GameSettings(PlatformServices(freshRecorder, freshRecorder, store))
        assertFalse(relaunched.isOn(Setting.Music))
        assertTrue(relaunched.isOn(Setting.SoundEffects))
        assertFalse(relaunched.isOn(Setting.Haptics))
        assertEquals(false, freshRecorder.music, "saved values are applied on launch")
        assertEquals(true, freshRecorder.effects)
        assertEquals(false, freshRecorder.haptics)
    }

    @Test
    fun keysDoNotCollide() {
        assertEquals(Setting.entries.size, Setting.entries.map { it.key }.toSet().size)
    }
}
