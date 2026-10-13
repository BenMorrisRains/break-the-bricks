package com.benmorrisrains.breakthebricks.platform

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.io.File
import java.util.concurrent.locks.LockSupport

private var cached: PlatformServices? = null

/**
 * The Android services. They live as long as the process, so activity recreation reuses the
 * same SoundPool instead of loading the sounds again. The music pauses while no activity of the
 * app is on screen.
 *
 * Ads come from AdMob and the one-time purchase from RevenueCat, set up by [monetization].
 */
fun createPlatformServices(context: Context, monetization: MonetizationConfig = MonetizationConfig()): PlatformServices {
    cached?.let { return it }
    val app = context.applicationContext as Application
    val audio = SoundPoolAudio(app)
    app.registerActivityLifecycleCallbacks(VisibleActivities(audio::setAppActive))
    val store = SharedPreferencesStore(app.getSharedPreferences("break_the_bricks", Context.MODE_PRIVATE))
    return PlatformServices(
        audio = audio,
        haptics = VibratorHaptics(app),
        store = store,
        ads = AdMobRewardedAds(app, monetization.rewardedAdUnitId.ifBlank { AdMobRewardedAds.TEST_AD_UNIT_ID }),
        purchases = monetization.purchases(store, ::RevenueCatPurchases),
    ).also { cached = it }
}

/** Counts the app's started activities and reports when the app becomes visible or hidden. */
private class VisibleActivities(private val onVisible: (Boolean) -> Unit) : Application.ActivityLifecycleCallbacks {
    private var started = 0

    override fun onActivityStarted(activity: Activity) {
        if (started++ == 0) onVisible(true)
    }

    override fun onActivityStopped(activity: Activity) {
        if (--started == 0) onVisible(false)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

/**
 * Sound effects through SoundPool, which mixes overlapping hits with low latency, and the music
 * through a [LoopingTrack].
 */
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

    private val music = LoopingTrack()

    override fun playEffect(effect: SoundEffect) {
        // Returns 0 without playing if the sound is still loading; that's fine for a blip.
        soundIds[effect]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    override fun resumeMusic() = music.resume()

    override fun pauseMusic() = music.pause()
}

/**
 * The music loop, streamed to an AudioTrack by its own thread so it repeats without a gap. The
 * thread starts the first time music is wanted, renders the loop there, and then lives as long
 * as the process, parked while the music is paused.
 */
internal class LoopingTrack {
    @Volatile private var playing = false
    private var thread: Thread? = null

    @Synchronized
    fun resume() {
        playing = true
        val running = thread
        if (running == null) {
            thread = Thread(::run, "music").apply { isDaemon = true; start() }
        } else {
            LockSupport.unpark(running)
        }
    }

    fun pause() {
        playing = false
    }

    private fun run() {
        val samples = ChipMusic.pcm()
        val track = try {
            val minBuffer = AudioTrack.getMinBufferSize(
                ChipMusic.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
            )
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(ChipMusic.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBuffer * 2, ChipMusic.SAMPLE_RATE / 5)) // at least 100 ms
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Exception) {
            return // No audio output; the game plays on without music.
        }
        val chunk = ChipMusic.SAMPLE_RATE / 20 // 50 ms per write
        var at = 0
        try {
            while (true) {
                if (!playing) {
                    track.pause()
                    while (!playing) LockSupport.park(this)
                }
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
                val count = minOf(chunk, samples.size - at)
                val written = track.write(samples, at, count)
                if (written < 0) return // The track failed; stay silent.
                at = (at + written) % samples.size
            }
        } finally {
            track.release()
        }
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
