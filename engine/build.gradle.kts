// Pure Kotlin game engine: no Compose, no Android, no platform APIs.
// Android consumes the jvm target; iOS gets the native targets.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
