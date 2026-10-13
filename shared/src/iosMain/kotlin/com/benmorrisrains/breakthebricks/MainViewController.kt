package com.benmorrisrains.breakthebricks

import androidx.compose.ui.window.ComposeUIViewController
import com.benmorrisrains.breakthebricks.platform.RewardedAds
import com.benmorrisrains.breakthebricks.platform.createPlatformServices

/** The game's screen for the iOS app, which passes in its AdMob rewarded [ads]. */
fun MainViewController(ads: RewardedAds) = ComposeUIViewController { App(createPlatformServices(ads)) }
