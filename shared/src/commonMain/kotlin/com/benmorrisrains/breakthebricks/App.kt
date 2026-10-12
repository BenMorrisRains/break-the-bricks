package com.benmorrisrains.breakthebricks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.benmorrisrains.breakthebricks.platform.GameSettings
import com.benmorrisrains.breakthebricks.platform.PlatformServices
import com.benmorrisrains.breakthebricks.ui.GameScreen
import com.benmorrisrains.breakthebricks.ui.GameSession
import com.benmorrisrains.breakthebricks.ui.GameShell
import com.benmorrisrains.breakthebricks.ui.HomeScreen
import com.benmorrisrains.breakthebricks.ui.NeonPalette
import com.benmorrisrains.breakthebricks.ui.PauseMenu
import com.benmorrisrains.breakthebricks.ui.ResultsScreen
import com.benmorrisrains.breakthebricks.ui.Screen
import com.benmorrisrains.breakthebricks.ui.SettingsScreen

/**
 * Root composable for every platform: home, the game with its pause menu, results and settings,
 * with sound, haptics, saved settings and the saved best score from [services].
 */
@Composable
fun App(services: PlatformServices = remember { PlatformServices() }) {
    val shell = remember(services) { GameShell(GameSession(services), GameSettings(services)) }
    val content = Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .padding(horizontal = 12.dp, vertical = 8.dp)
    Box(Modifier.fillMaxSize().background(NeonPalette.Background)) {
        when (val screen = shell.screen) {
            Screen.Home -> HomeScreen(
                bestPoints = shell.session.bestPoints,
                rank = shell.rank(),
                onPlay = shell::play,
                onSettings = shell::openSettings,
                modifier = content,
            )
            Screen.Game -> {
                val session = shell.session
                GameScreen(
                    engine = session.engine,
                    bestPoints = session.bestPoints,
                    onFrame = shell::onFrame,
                    onFire = session::onFired,
                    onGameOverTap = shell::onGameOverTap,
                    paused = shell.paused,
                    onPause = shell::pause,
                    modifier = content,
                )
                if (shell.paused) {
                    PauseMenu(
                        onResume = shell::resume,
                        onRestart = shell::play,
                        onSettings = shell::openSettings,
                        onHome = shell::goHome,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            is Screen.Results -> ResultsScreen(
                result = screen.result,
                rank = shell.rank(),
                onPlayAgain = shell::play,
                onHome = shell::goHome,
                modifier = content,
            )
            is Screen.Settings -> SettingsScreen(
                settings = shell.settings,
                onBack = shell::closeSettings,
                modifier = content,
            )
        }
    }
}
