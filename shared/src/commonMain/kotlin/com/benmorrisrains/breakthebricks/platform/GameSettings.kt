package com.benmorrisrains.breakthebricks.platform

/** The player's on/off settings, each saved under its own key. All start on. */
enum class Setting(val key: String) {
    Music("setting_music"),
    SoundEffects("setting_sound_effects"),
    Haptics("setting_haptics"),
}

/**
 * Reads and saves the [Setting] toggles in [services]' store and applies them to its audio
 * and haptics. Building one applies the saved values, so they hold from app start.
 */
class GameSettings(private val services: PlatformServices) {
    init {
        Setting.entries.forEach { apply(it, isOn(it)) }
    }

    fun isOn(setting: Setting): Boolean = services.store.getLong(setting.key, ON) != OFF

    fun set(setting: Setting, on: Boolean) {
        services.store.putLong(setting.key, if (on) ON else OFF)
        apply(setting, on)
    }

    private fun apply(setting: Setting, on: Boolean) = when (setting) {
        Setting.Music -> services.audio.setMusicEnabled(on)
        Setting.SoundEffects -> services.audio.setEffectsEnabled(on)
        Setting.Haptics -> services.haptics.setEnabled(on)
    }

    private companion object {
        const val ON = 1L
        const val OFF = 0L
    }
}
