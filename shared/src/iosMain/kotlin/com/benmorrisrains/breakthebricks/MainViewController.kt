package com.benmorrisrains.breakthebricks

import androidx.compose.ui.window.ComposeUIViewController
import com.benmorrisrains.breakthebricks.platform.createPlatformServices

fun MainViewController() = ComposeUIViewController { App(createPlatformServices()) }
