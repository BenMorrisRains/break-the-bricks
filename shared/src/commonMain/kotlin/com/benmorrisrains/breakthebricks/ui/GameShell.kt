package com.benmorrisrains.breakthebricks.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.engine.Level
import com.benmorrisrains.breakthebricks.platform.GameSettings

/** The screens of the app. */
sealed interface Screen {
    data object Home : Screen
    data object Levels : Screen
    data object Game : Screen
    data class Results(val result: GameResult) : Screen
    /** Settings, and the screen to go back to when they close. */
    data class Settings(val returnTo: Screen) : Screen
}

/**
 * Which screen is showing and whether the game is paused. The game stays in [session] while
 * the pause menu or settings are open, so it picks up exactly where it stopped.
 *
 * [rank] is where the player's rank and progress come from once task T15 saves them; until
 * then it gives null and the home and results screens leave the rank out.
 */
class GameShell(
    val session: GameSession,
    val settings: GameSettings,
    val rank: () -> RankProgress? = { null },
) {
    var screen: Screen by mutableStateOf(Screen.Home)
        private set

    /** True while the pause menu is up. The game doesn't advance while paused. */
    var paused: Boolean by mutableStateOf(false)
        private set

    /** Starts a new game of the same kind as the last one (that level, or free play). Play again and Restart. */
    fun play() = start(session.level)

    /** Home's Play: an endless game on a fresh seed. */
    fun playFreePlay() = start(null)

    fun playLevel(level: Level) {
        if (session.progress.isUnlocked(level.number)) start(level)
    }

    /** After passing a level, plays the one after it, if there is one. */
    fun playNextLevel() {
        val next = nextLevel() ?: return
        start(next)
    }

    /** The level after the one just passed, or null when there is none to go to. */
    fun nextLevel(): Level? {
        val result = session.result ?: return null
        val level = result.level ?: return null
        if (!result.passed) return null
        return session.progress.levels.firstOrNull { it.number == level.number + 1 }
    }

    fun openLevels() {
        paused = false
        screen = Screen.Levels
        syncMusic()
    }

    private fun start(level: Level?) {
        session.newGame(level)
        paused = false
        screen = Screen.Game
        syncMusic()
    }

    fun pause() {
        if (screen == Screen.Game && !session.engine.phase.isFinished) paused = true
        syncMusic()
    }

    fun resume() {
        paused = false
        syncMusic()
    }

    /** Leaves whatever is showing for the home screen; a game in progress is dropped. */
    fun goHome() {
        paused = false
        screen = Screen.Home
        syncMusic()
    }

    fun openSettings() {
        if (screen !is Screen.Settings) screen = Screen.Settings(returnTo = screen)
        syncMusic()
    }

    fun closeSettings() {
        (screen as? Screen.Settings)?.let { screen = it.returnTo }
        syncMusic()
    }

    /** Called every display frame the game runs; moves on to the results once they are due. */
    fun onFrame(elapsedSeconds: Float, events: List<GameEvent>) {
        session.onFrame(elapsedSeconds, events)
        if (session.resultsDue) showResults()
        syncMusic()
    }

    /** A tap on the final board skips straight to the results, after a short guard. */
    fun onGameOverTap() {
        if (session.canSkipToResults) showResults()
    }

    /** True while a game is being played: on screen, not paused and not over. */
    val wantsMusic: Boolean
        get() = screen == Screen.Game && !paused && !session.engine.phase.isFinished

    /** The music loop plays during a game and stops for menus, the pause menu and the end. */
    private fun syncMusic() = session.setMusicPlaying(wantsMusic)

    private fun showResults() {
        if (screen != Screen.Game) return
        session.result?.let { screen = Screen.Results(it) }
    }
}

/**
 * The player's rank and how far they are toward the next one, as the home and results screens
 * show it. Task T15 owns the rank rules and fills this in.
 */
data class RankProgress(val rank: Int, val pointsIntoRank: Long, val pointsForNextRank: Long) {
    val fraction: Float
        get() = if (pointsForNextRank <= 0L) 0f else (pointsIntoRank.toFloat() / pointsForNextRank).coerceIn(0f, 1f)

    val pointsToGo: Long
        get() = (pointsForNextRank - pointsIntoRank).coerceAtLeast(0L)
}
