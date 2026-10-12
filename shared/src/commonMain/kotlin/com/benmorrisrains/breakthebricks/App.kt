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
import com.benmorrisrains.breakthebricks.ui.GameSession
import com.benmorrisrains.breakthebricks.ui.NeonPalette

/**
 * Root composable for every platform: the real engine and physics on the game screen, with
 * sound, haptics and the saved best score from [services]. Tap after a game over to play again.
 */
@Composable
fun App(services: PlatformServices = remember { PlatformServices() }) {
    val session = remember(services) { GameSession(services) }
    GameScreen(
        engine = session.engine,
        bestPoints = session.bestPoints,
        onFrame = session::onFrame,
        onFire = session::onFired,
        onGameOverTap = { session.restart() },
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background)
            .safeDrawingPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
