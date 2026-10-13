package com.benmorrisrains.breakthebricks.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

private var shared: PlatformServices? = null

/**
 * The iOS services, created once per app run. The rewarded [ads] (AdMob, behind the App
 * Tracking Transparency prompt) are built in Swift by the iOS app; the one-time purchase is
 * RevenueCat, with its API key from the app's Info.plist (`RevenueCatApiKey`).
 */
@OptIn(ExperimentalNativeApi::class)
fun createPlatformServices(ads: RewardedAds): PlatformServices {
    shared?.let { return it }
    val store = UserDefaultsStore(NSUserDefaults.standardUserDefaults)
    val monetization = MonetizationConfig(
        revenueCatApiKey = NSBundle.mainBundle.objectForInfoDictionaryKey("RevenueCatApiKey") as? String ?: "",
        debug = Platform.isDebugBinary,
    )
    return PlatformServices(
        audio = AvAudio(),
        haptics = ImpactHaptics(),
        store = store,
        ads = ads,
        purchases = monetization.purchases(store, ::RevenueCatPurchases),
    ).also { shared = it }
}

/**
 * Sound effects through AVAudioPlayer, with a few players per effect so quick hits overlap, and
 * the music through one looping player. The music pauses while the app is in the background.
 */
@OptIn(ExperimentalForeignApi::class)
internal class AvAudio : ToggleableAudio() {
    private val players: Map<SoundEffect, List<AVAudioPlayer>>
    private val next = mutableMapOf<SoundEffect, Int>()

    // Built the first time music is wanted, so starting the app doesn't wait on it.
    private val music: AVAudioPlayer? by lazy {
        AVAudioPlayer(data = ChipMusic.wav().toNSData(), error = null).apply {
            numberOfLoops = -1 // forever
            prepareToPlay()
        }
    }

    init {
        // Ambient: mixes with the player's own music and respects the silent switch.
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryAmbient, null)
        players = SoundEffect.entries.associateWith { effect ->
            val data = ChipTones.wav(effect).toNSData()
            List(VOICES) { AVAudioPlayer(data = data, error = null).also { it.prepareToPlay() } }
        }
        val center = NSNotificationCenter.defaultCenter
        val main = NSOperationQueue.mainQueue
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, main) { _ -> setAppActive(false) }
        center.addObserverForName(UIApplicationWillEnterForegroundNotification, null, main) { _ -> setAppActive(true) }
        // A call or alarm pauses the player; carry on with the loop once it is over.
        center.addObserverForName(AVAudioSessionInterruptionNotification, null, main) { _ ->
            if (musicOn) music?.play()
        }
    }

    override fun resumeMusic() {
        music?.play()
    }

    override fun pauseMusic() {
        music?.pause()
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
