package com.benmorrisrains.breakthebricks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.benmorrisrains.breakthebricks.platform.PlatformServices
import com.benmorrisrains.breakthebricks.ui.GameScreen
import com.benmorrisrains.breakthebricks.ui.NeonPalette
import com.benmorrisrains.breakthebricks.ui.PreviewGameEngine

/**
 * Root composable for every platform. For now it runs the game screen on [PreviewGameEngine],
 * a stand-in with rough physics; task T6 swaps in the real engine and wires sound and haptics.
 */
@Composable
fun App(services: PlatformServices = remember { PlatformServices() }) {
    val engine = remember { PreviewGameEngine() }
    GameScreen(
        engine = engine,
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background)
            .safeDrawingPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
