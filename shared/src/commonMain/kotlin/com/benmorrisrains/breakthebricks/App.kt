package com.benmorrisrains.breakthebricks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.benmorrisrains.breakthebricks.ui.LevelSelectScreen
import com.benmorrisrains.breakthebricks.ui.NeonPalette
import com.benmorrisrains.breakthebricks.ui.PauseMenu
import com.benmorrisrains.breakthebricks.ui.ResultsScreen
import com.benmorrisrains.breakthebricks.ui.Screen
import com.benmorrisrains.breakthebricks.ui.SettingsScreen
import com.benmorrisrains.breakthebricks.ui.WagerScreen
import com.benmorrisrains.breakthebricks.ui.quitWarning

/**
 * Root composable for every platform: home, level select, the wager screen, the game with its
 * pause menu, results and settings, with sound, haptics, saved settings, level progress, the
 * ability inventory and best scores from [services].
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
                onPlay = shell::playFreePlay,
                onLevels = shell::openLevels,
                onSettings = shell::openSettings,
                modifier = content,
            )
            Screen.Levels -> LevelSelectScreen(
                progress = shell.session.progress,
                onLevel = shell::playLevel,
                onBack = shell::goHome,
                modifier = content,
            )
            Screen.Wager -> WagerScreen(
                stakeable = shell.session.bank.stakeable,
                inventory = shell.session.bank.inventory,
                onStake = shell::stake,
                onBack = shell::goHome,
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
                    level = session.level,
                    modifier = content,
                )
                if (shell.paused) {
                    PauseMenu(
                        onResume = shell::resume,
                        onRestart = shell::play,
                        onSettings = shell::openSettings,
                        onHome = shell::goHome,
                        modifier = Modifier.fillMaxSize(),
                        warning = quitWarning(session.wager.stake, session.wager.outcome),
                    )
                }
            }
            is Screen.Results -> ResultsScreen(
                result = screen.result,
                rank = shell.rank(),
                onPlayAgain = shell::play,
                onHome = shell::goHome,
                // Level results carry more lines and buttons than a short phone screen holds.
                modifier = content.verticalScroll(rememberScrollState()),
                onNextLevel = if (shell.nextLevel() != null) shell::playNextLevel else null,
                onLevels = if (screen.result.level != null) shell::openLevels else null,
            )
            is Screen.Settings -> SettingsScreen(
                settings = shell.settings,
                onBack = shell::closeSettings,
                modifier = content,
            )
        }
    }
}
