import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
}

android {
    namespace = "com.benmorrisrains.breakthebricks"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.benmorrisrains.breakthebricks"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        // Ads and the one-time purchase (T12), from Gradle properties (gradle.properties,
        // ~/.gradle/gradle.properties or -P). AdMob defaults to Google's public test IDs. With
        // no RevenueCat key, debug builds use a fake store and release builds sell nothing.
        // The key is RevenueCat's public Google SDK key (goog_...), never a secret key.
        val revenueCatKey = providers.gradleProperty("revenueCatAndroidKey").getOrElse("")
        val adMobAppId = providers.gradleProperty("adMobAndroidAppId").getOrElse("ca-app-pub-3940256099942544~3347511713")
        val adMobRewardedUnit = providers.gradleProperty("adMobAndroidRewardedUnitId").getOrElse("ca-app-pub-3940256099942544/5224354917")
        buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatKey\"")
        buildConfigField("String", "ADMOB_REWARDED_AD_UNIT_ID", "\"$adMobRewardedUnit\"")
        manifestPlaceholders["adMobAppId"] = adMobAppId
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
