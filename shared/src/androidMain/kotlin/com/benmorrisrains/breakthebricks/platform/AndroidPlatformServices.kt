package com.benmorrisrains.breakthebricks.platform

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.io.File

private var cached: PlatformServices? = null

/**
 * The Android services. They live as long as the process, so activity recreation reuses the
 * same SoundPool instead of loading the sounds again.
 */
fun createPlatformServices(context: Context): PlatformServices {
    cached?.let { return it }
    val app = context.applicationContext
    return PlatformServices(
        audio = SoundPoolAudio(app),
        haptics = VibratorHaptics(app),
        store = SharedPreferencesStore(app.getSharedPreferences("break_the_bricks", Context.MODE_PRIVATE)),
    ).also { cached = it }
}

/** Sound effects through SoundPool, which mixes overlapping hits with low latency. */
internal class SoundPoolAudio(context: Context) : ToggleableAudio() {
    private val pool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    // SoundPool loads from files, so each synthesized effect is written to the cache once.
    private val soundIds: Map<SoundEffect, Int> = run {
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
        SoundEffect.entries.associateWith { effect ->
            val file = File(dir, "${effect.name}.wav")
            file.writeBytes(ChipTones.wav(effect))
            pool.load(file.path, 1)
        }
    }

    override fun playEffect(effect: SoundEffect) {
        // Returns 0 without playing if the sound is still loading; that's fine for a blip.
        soundIds[effect]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
    }
}

internal class VibratorHaptics(context: Context) : ToggleableHaptics() {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    override fun vibrate(strength: HapticStrength) {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> vibrator.vibrate(
                    VibrationEffect.createPredefined(
                        when (strength) {
                            HapticStrength.Light -> VibrationEffect.EFFECT_TICK
                            HapticStrength.Medium -> VibrationEffect.EFFECT_CLICK
                            HapticStrength.Heavy -> VibrationEffect.EFFECT_HEAVY_CLICK
                        },
                    ),
                )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> vibrator.vibrate(
                    VibrationEffect.createOneShot(strength.millis, strength.amplitude),
                )
                else -> @Suppress("DEPRECATION") vibrator.vibrate(strength.millis)
            }
        } catch (_: SecurityException) {
            // Missing VIBRATE permission; haptics are optional.
        }
    }

    private val HapticStrength.millis: Long
        get() = when (this) {
            HapticStrength.Light -> 10
            HapticStrength.Medium -> 20
            HapticStrength.Heavy -> 40
        }

    private val HapticStrength.amplitude: Int
        get() = when (this) {
            HapticStrength.Light -> 80
            HapticStrength.Medium -> 160
            HapticStrength.Heavy -> 255
        }
}

internal class SharedPreferencesStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getLong(key: String, default: Long) = prefs.getLong(key, default)
    override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
}
