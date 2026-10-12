package com.benmorrisrains.breakthebricks.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.Foundation.NSData
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

private val shared: PlatformServices by lazy {
    PlatformServices(
        audio = AvAudio(),
        haptics = ImpactHaptics(),
        store = UserDefaultsStore(NSUserDefaults.standardUserDefaults),
    )
}

/** The iOS services, created once per app run. */
fun createPlatformServices(): PlatformServices = shared

/** Sound effects through AVAudioPlayer, with a few players per effect so quick hits overlap. */
@OptIn(ExperimentalForeignApi::class)
internal class AvAudio : ToggleableAudio() {
    private val players: Map<SoundEffect, List<AVAudioPlayer>>
    private val next = mutableMapOf<SoundEffect, Int>()

    init {
        // Ambient: mixes with the player's own music and respects the silent switch.
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryAmbient, null)
        players = SoundEffect.entries.associateWith { effect ->
            val data = ChipTones.wav(effect).toNSData()
            List(VOICES) { AVAudioPlayer(data = data, error = null).also { it.prepareToPlay() } }
        }
    }

    override fun playEffect(effect: SoundEffect) {
        val voices = players.getValue(effect)
        val index = next[effect] ?: 0
        next[effect] = (index + 1) % voices.size
        voices[index].run {
            stop()
            currentTime = 0.0
            play()
        }
    }

    private companion object {
        const val VOICES = 4
    }
}

internal class ImpactHaptics : ToggleableHaptics() {
    private val generators = HapticStrength.entries.associateWith { strength ->
        UIImpactFeedbackGenerator(
            style = when (strength) {
                HapticStrength.Light -> UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
                HapticStrength.Medium -> UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
                HapticStrength.Heavy -> UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
            },
        ).also { it.prepare() }
    }

    override fun vibrate(strength: HapticStrength) {
        generators.getValue(strength).run {
            impactOccurred()
            prepare() // Keeps the Taptic Engine warm for the next hit.
        }
    }
}

internal class UserDefaultsStore(private val defaults: NSUserDefaults) : KeyValueStore {
    override fun getLong(key: String, default: Long) =
        if (defaults.objectForKey(key) == null) default else defaults.integerForKey(key)

    override fun putLong(key: String, value: Long) = defaults.setInteger(value, forKey = key)
    override fun getString(key: String): String? = defaults.stringForKey(key)
    override fun putString(key: String, value: String) = defaults.setObject(value, forKey = key)
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned {
    NSData.create(bytes = it.addressOf(0), length = size.toULong())
}
