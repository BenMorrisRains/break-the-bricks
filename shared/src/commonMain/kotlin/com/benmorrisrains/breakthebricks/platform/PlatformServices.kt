package com.benmorrisrains.breakthebricks.platform

/** Sound effects and music. Task T5 provides Android and iOS implementations. */
interface AudioPlayer {
    fun play(effect: SoundEffect)
    fun setMusicEnabled(enabled: Boolean)
    fun setEffectsEnabled(enabled: Boolean)
}

enum class SoundEffect { BrickHit, BrickBreak, WallBounce, Fire, AbilityTrigger, GameOver }

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

/** Bundle handed to the UI. Defaults do nothing so screens run before T5 lands. */
class PlatformServices(
    val audio: AudioPlayer = NoOpAudio,
    val haptics: Haptics = NoOpHaptics,
    val store: KeyValueStore = InMemoryStore(),
)

object NoOpAudio : AudioPlayer {
    override fun play(effect: SoundEffect) = Unit
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
