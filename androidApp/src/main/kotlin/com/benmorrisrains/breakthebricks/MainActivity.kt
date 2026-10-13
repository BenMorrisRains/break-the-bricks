package com.benmorrisrains.breakthebricks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.benmorrisrains.breakthebricks.platform.MonetizationConfig
import com.benmorrisrains.breakthebricks.platform.createPlatformServices

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val services = createPlatformServices(
            this,
            MonetizationConfig(
                revenueCatApiKey = BuildConfig.REVENUECAT_API_KEY,
                rewardedAdUnitId = BuildConfig.ADMOB_REWARDED_AD_UNIT_ID,
                debug = BuildConfig.DEBUG,
            ),
        )
        setContent {
            App(services)
        }
    }
}
