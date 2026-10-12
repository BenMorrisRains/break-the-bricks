package com.benmorrisrains.breakthebricks.platform

import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopPlatformServicesTest {
    private val node = Preferences.userRoot().node("com/benmorrisrains/breakthebricks/test-${System.nanoTime()}")

    @AfterTest
    fun removeNode() = node.removeNode()

    @Test
    fun storeReturnsDefaultsForMissingKeys() {
        val store = PreferencesStore(node)
        assertEquals(7L, store.getLong("best", 7L))
        assertNull(store.getString("inventory"))
    }

    @Test
    fun storedValuesSurviveANewStore() {
        PreferencesStore(node).run {
            putLong("best", 1179L)
            putString("inventory", "grenade=2")
        }
        val reopened = PreferencesStore(Preferences.userRoot().node(node.absolutePath()))
        assertEquals(1179L, reopened.getLong("best", 0L))
        assertEquals("grenade=2", reopened.getString("inventory"))
    }

    @Test
    fun audioPlaysOrStaysSilentWithoutThrowing() {
        // CI runners have no sound card; this checks that is handled, and plays every effect
        // where a device exists.
        val audio = ClipAudio()
        SoundEffect.entries.forEach(audio::play)
        audio.setEffectsEnabled(false)
        audio.play(SoundEffect.GameOver)
    }
}
