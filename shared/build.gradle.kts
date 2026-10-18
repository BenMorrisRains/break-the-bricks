import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Compose UI shared by Android, iOS and desktop. Game rules live in :engine.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm()

    androidLibrary {
        namespace = "com.benmorrisrains.breakthebricks.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.engine)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.uiToolingPreview)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.play.services.ads)
            implementation(libs.revenuecat.kmp.core)
        }
        iosMain.dependencies {
            implementation(libs.revenuecat.kmp.core)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
