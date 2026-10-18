package com.benmorrisrains.breakthebricks.platform

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Rewarded ads through Google AdMob. One ad is loaded ahead so it shows at once; asking while
 * none is ready loads one and shows it as soon as it arrives. The ad shows over the app's
 * activity on screen, which [CurrentActivity] keeps track of.
 */
internal class AdMobRewardedAds(app: Application, private val adUnitId: String) : RewardedAds {
    private val context: Context = app
    private val activity = CurrentActivity().also(app::registerActivityLifecycleCallbacks)
    private var ready: RewardedAd? = null
    private var loading = false

    /** The answer waiting on an ad that is still loading. */
    private var waiting: ((Boolean) -> Unit)? = null

    init {
        MobileAds.initialize(context) { load() }
    }

    override fun show(onFinished: (rewarded: Boolean) -> Unit) {
        val ad = ready
        if (ad == null) {
            waiting?.invoke(false) // Only the latest ask waits.
            waiting = onFinished
            load()
            return
        }
        val host = activity.current ?: return onFinished(false)
        ready = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onFinished(earned)
                load()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                onFinished(false)
                load()
            }
        }
        ad.show(host) { earned = true }
    }

    private fun load() {
        if (ready != null || loading) return
        loading = true
        RewardedAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loading = false
                    ready = ad
                    waiting?.let { onFinished ->
                        waiting = null
                        show(onFinished)
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    waiting?.let { onFinished ->
                        waiting = null
                        onFinished(false)
                    }
                }
            },
        )
    }

    /** Google's sample rewarded ad unit, which always fills with a test ad. */
    companion object {
        const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    }
}

/** The app's activity that is resumed, if any. */
private class CurrentActivity : Application.ActivityLifecycleCallbacks {
    var current: Activity? = null
        private set

    override fun onActivityResumed(activity: Activity) {
        current = activity
    }

    override fun onActivityPaused(activity: Activity) {
        if (current === activity) current = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
