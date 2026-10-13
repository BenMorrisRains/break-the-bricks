package com.benmorrisrains.breakthebricks.platform

import java.io.ByteArrayInputStream
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

/**
 * The desktop services. Desktops have no vibration, so haptics do nothing. The desktop build is
 * for development, so its ads and store are the fakes: every ad pays out and the unlock is free.
 */
fun createPlatformServices(): PlatformServices {
    val store = PreferencesStore(Preferences.userRoot().node("com/benmorrisrains/breakthebricks"))
    return PlatformServices(
        audio = ClipAudio(),
        haptics = NoOpHaptics,
        store = store,
        purchases = FakePurchases(store),
    )
}

/**
 * Sound effects through javax.sound clips, a few per effect so quick hits overlap, and the music
 * as a looping clip per track. A machine with no audio device (such as CI) gets no clips and stays
 * silent instead of failing.
 */
internal class ClipAudio : ToggleableAudio() {
    private val clips: Map<SoundEffect, List<Clip>> = SoundEffect.entries.associateWith { effect ->
        val wav = ChipTones.wav(effect)
        List(VOICES) { openClip(wav) }.filterNotNull()
    }
    private val next = mutableMapOf<SoundEffect, Int>()

    // Each track's clip is built the first time it is wanted, so starting the app doesn't wait on it.
    private val tracks = mutableMapOf<Int, Clip?>()
    private val music: Clip? get() = tracks.getOrPut(musicTrack) { openClip(ChipMusic.wav(musicTrack)) }

    override fun playEffect(effect: SoundEffect) {
        val voices = clips.getValue(effect).ifEmpty { return }
        val index = (next[effect] ?: 0) % voices.size
        next[effect] = index + 1
        voices[index].run {
            stop()
            framePosition = 0
            start()
        }
    }

    override fun resumeMusic() {
        music?.loop(Clip.LOOP_CONTINUOUSLY)
    }

    override fun pauseMusic() {
        music?.stop()
    }

    override fun rewindMusic() {
        tracks[musicTrack]?.framePosition = 0
    }

    private fun openClip(wav: ByteArray): Clip? = try {
        AudioSystem.getClip().apply { open(AudioSystem.getAudioInputStream(ByteArrayInputStream(wav))) }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val VOICES = 4
    }
}

/**
 * Values in the Java user preferences. Keys are limited to [Preferences.MAX_KEY_LENGTH] and
 * strings to [Preferences.MAX_VALUE_LENGTH] characters.
 */
internal class PreferencesStore(private val prefs: Preferences) : KeyValueStore {
    override fun getLong(key: String, default: Long) = prefs.getLong(key, default)
    override fun getString(key: String): String? = prefs.get(key, null)

    override fun putLong(key: String, value: Long) {
        prefs.putLong(key, value)
        flush()
    }

    override fun putString(key: String, value: String) {
        prefs.put(key, value)
        flush()
    }

    private fun flush() {
        try {
            prefs.flush()
        } catch (_: BackingStoreException) {
            // The value stays in memory and Java retries the write later.
        }
    }
}
