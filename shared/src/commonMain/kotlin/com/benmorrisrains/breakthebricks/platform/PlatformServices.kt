package com.benmorrisrains.breakthebricks.platform

/** Sound effects and music. Each platform builds one with `createPlatformServices`. */
interface AudioPlayer {
    fun play(effect: SoundEffect)

    /**
     * Whether the screen wants the music loop playing (during a game, not paused). It only
     * plays while the music setting is on too; turning it off and on again resumes the loop.
     */
    fun setMusicPlaying(playing: Boolean)

    /**
     * Picks which music track plays, by its index (0 until `ChipMusic.tracks.size`). A new
     * track starts from its beginning; picking the current one changes nothing.
     */
    fun setMusicTrack(track: Int)

    fun setMusicEnabled(enabled: Boolean)
    fun setEffectsEnabled(enabled: Boolean)
}

enum class SoundEffect {
    BrickHit, BrickBreak, WallBounce, Fire, BallReturn, RoundStart,
    AbilityTrigger, AbilityEarned, LevelComplete, GameOver,
}

/** Vibration feedback. */
interface Haptics {
    fun tap(strength: HapticStrength)
    fun setEnabled(enabled: Boolean)
}

enum class HapticStrength { Light, Medium, Heavy }

/** Small persistent values: best points, level progress, ability inventory, settings. */
interface KeyValueStore {
    fun getLong(key: String, default: Long): Long
    fun putLong(key: String, value: Long)
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

/**
 * Bundle handed to the UI. The defaults do nothing (the fake ad pays out at once and the fake
 * store sells the unlock at once), for previews and tests.
 */
class PlatformServices(
    val audio: AudioPlayer = NoOpAudio,
    val haptics: Haptics = NoOpHaptics,
    val store: KeyValueStore = InMemoryStore(),
    val ads: RewardedAds = FakeRewardedAds,
    val purchases: Purchases = FakePurchases(),
)

object NoOpAudio : AudioPlayer {
    override fun play(effect: SoundEffect) = Unit
    override fun setMusicPlaying(playing: Boolean) = Unit
    override fun setMusicTrack(track: Int) = Unit
    override fun setMusicEnabled(enabled: Boolean) = Unit
    override fun setEffectsEnabled(enabled: Boolean) = Unit
}

object NoOpHaptics : Haptics {
    override fun tap(strength: HapticStrength) = Unit
    override fun setEnabled(enabled: Boolean) = Unit
}

class InMemoryStore : KeyValueStore {
    private val longs = mutableMapOf<String, Long>()
    private val strings = mutableMapOf<String, String>()
    override fun getLong(key: String, default: Long) = longs[key] ?: default
    override fun putLong(key: String, value: Long) { longs[key] = value }
    override fun getString(key: String) = strings[key]
    override fun putString(key: String, value: String) { strings[key] = value }
}

/**
 * On/off handling shared by the platform audio players. The music loop plays only while the
 * game wants it, the music setting is on and the app is in the foreground; the platform player
 * just resumes and pauses it when that changes.
 */
internal abstract class ToggleableAudio : AudioPlayer {
    var effectsEnabled = true
        private set
    var musicEnabled = true
        private set

    /** True while the music loop should be audible. */
    var musicOn = false
        private set

    /** The track that plays, or carries on, when the music is on. */
    var musicTrack = 0
        private set

    private var musicWanted = false
    private var appActive = true

    final override fun play(effect: SoundEffect) {
        if (effectsEnabled) playEffect(effect)
    }

    protected abstract fun playEffect(effect: SoundEffect)

    /** Starts the loop, or carries on from where it was paused. */
    protected abstract fun resumeMusic()

    protected abstract fun pauseMusic()

    /** Sends [musicTrack], which just changed, back to its beginning. Called while it is paused. */
    protected abstract fun rewindMusic()

    final override fun setMusicPlaying(playing: Boolean) {
        musicWanted = playing
        updateMusic()
    }

    final override fun setMusicTrack(track: Int) {
        if (track == musicTrack) return
        if (musicOn) pauseMusic()
        musicTrack = track
        rewindMusic()
        if (musicOn) resumeMusic()
    }

    final override fun setMusicEnabled(enabled: Boolean) {
        musicEnabled = enabled
        updateMusic()
    }

    override fun setEffectsEnabled(enabled: Boolean) {
        effectsEnabled = enabled
    }

    /** Called by the platform when the app goes to the background (false) and comes back. */
    fun setAppActive(active: Boolean) {
        appActive = active
        updateMusic()
    }

    private fun updateMusic() {
        val on = musicWanted && musicEnabled && appActive
        if (on == musicOn) return
        musicOn = on
        if (on) resumeMusic() else pauseMusic()
    }
}

/** On/off handling shared by the platform haptics. */
internal abstract class ToggleableHaptics : Haptics {
    var enabled = true
        private set

    final override fun tap(strength: HapticStrength) {
        if (enabled) vibrate(strength)
    }

    protected abstract fun vibrate(strength: HapticStrength)

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}
