package com.benmorrisrains.breakthebricks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.benmorrisrains.breakthebricks.engine.GridPhysics
import com.benmorrisrains.breakthebricks.engine.Level
import com.benmorrisrains.breakthebricks.platform.PlatformServices
import com.benmorrisrains.breakthebricks.ui.GameScreen
import com.benmorrisrains.breakthebricks.ui.GameSession
import com.benmorrisrains.breakthebricks.ui.LevelProgress
import com.benmorrisrains.breakthebricks.ui.LevelSelectScreen
import com.benmorrisrains.breakthebricks.ui.NeonPalette

/** Which screen is up: the level list, or a game of one level or of free play (level = null). */
private sealed interface Screen {
    data object Levels : Screen
    data class Playing(val level: Level?) : Screen
}

/**
 * Root composable for every platform. Opens on level select; a level or free play runs the real
 * engine and physics with sound, haptics and saved results from [services]. Tap after a game
 * ends to go back to the levels.
 */
@Composable
fun App(services: PlatformServices = remember { PlatformServices() }) {
    val progress = remember(services) { LevelProgress(services.store) }
    var screen by remember { mutableStateOf<Screen>(Screen.Levels) }
    val modifier = Modifier
        .fillMaxSize()
        .background(NeonPalette.Background)
        .safeDrawingPadding()
        .padding(horizontal = 12.dp, vertical = 8.dp)

    when (val current = screen) {
        Screen.Levels -> LevelSelectScreen(
            progress = progress,
            onLevel = { screen = Screen.Playing(it) },
            onFreePlay = { screen = Screen.Playing(null) },
            modifier = modifier,
        )
        is Screen.Playing -> {
            val level = current.level
            val session = remember(current) {
                if (level == null) {
                    GameSession(services)
                } else {
                    GameSession(
                        services,
                        newSeed = { level.seed },
                        newEngine = { level.newGame(GridPhysics) },
                        bestPointsKey = LevelProgress.bestPointsKey(level.number),
                        onFinished = { passed, points -> progress.record(level, passed, points) },
                    )
                }
            }
            GameScreen(
                engine = session.engine,
                bestPoints = session.bestPoints,
                onFrame = session::onFrame,
                onFire = session::onFired,
                onGameOverTap = { if (session.canRestart) screen = Screen.Levels },
                level = level,
                modifier = modifier,
            )
        }
    }
}
