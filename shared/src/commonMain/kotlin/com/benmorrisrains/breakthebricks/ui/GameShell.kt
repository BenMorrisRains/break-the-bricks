package com.benmorrisrains.breakthebricks.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.benmorrisrains.breakthebricks.engine.Ability
import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.engine.Level
import com.benmorrisrains.breakthebricks.platform.GameSettings

/** The screens of the app. */
sealed interface Screen {
    data object Home : Screen
    data object Levels : Screen
    /** Picking the ability to stake before a free-play game. */
    data object Wager : Screen
    data object Game : Screen
    data class Results(val result: GameResult) : Screen
    /** Settings, and the screen to go back to when they close. */
    data class Settings(val returnTo: Screen) : Screen
    /** The offer to unlock paid [level] by the one-time purchase or an ad. */
    data class Paywall(val level: Level) : Screen
}

/**
 * Which screen is showing and whether the game is paused. The game stays in [session] while
 * the pause menu or settings are open, so it picks up exactly where it stopped.
 *
 * [rank] is where the player's rank and progress come from; null leaves the rank off the home
 * and results screens.
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
    fun play() {
        val level = session.level
        if (level == null) playFreePlay() else start(level)
    }

    /**
     * Home's Play: an endless game on a fresh seed. When the game needs a stake, the wager
     * screen comes first; any game still running is quit, which loses its open wager.
     */
    fun playFreePlay() {
        if (!session.bank.stakeRequired) return start(null)
        session.quit()
        paused = false
        screen = Screen.Wager
        syncMusic()
    }

    /** The wager screen's pick: stakes one copy of [ability] and starts free play with it active. */
    fun stake(ability: Ability) {
        if (screen != Screen.Wager || ability !in session.bank.stakeable) return
        start(null, ability)
    }

    /** Starts [level] once the level before it is passed; a paid level still locked shows the paywall first. */
    fun playLevel(level: Level) {
        if (!session.progress.isUnlocked(level.number)) return
        if (session.access.isPaywalled(level.number)) return openPaywall(level)
        start(level)
    }

    private fun openPaywall(level: Level) {
        session.quit()
        session.access.clearAdMissed()
        paused = false
        screen = Screen.Paywall(level)
        syncMusic()
    }

    /** The paywall's buy button: once the store says it's bought, the level starts. */
    fun buyUnlock() {
        session.unlock.buy { owned -> if (owned) startPaywalledLevel() }
    }

    /** The paywall's restore button: a restored purchase starts the level. */
    fun restorePurchases() {
        session.unlock.restore { owned -> if (owned) startPaywalledLevel() }
    }

    /** The paywall's ad button: once the ad pays out, the level is open for good and starts. */
    fun watchAdForLevel() {
        val paywall = screen as? Screen.Paywall ?: return
        session.access.watchAdToUnlock(paywall.level.number) { opened -> if (opened) startPaywalledLevel() }
    }

    private fun startPaywalledLevel() {
        val paywall = screen as? Screen.Paywall ?: return
        if (!session.access.isPaywalled(paywall.level.number)) start(paywall.level)
    }

    /** After passing a level, plays the one after it, if there is one. */
    fun playNextLevel() {
        val next = nextLevel() ?: return
        playLevel(next)
    }

    /** The level after the one just passed, or null when there is none to go to or it is still locked. */
    fun nextLevel(): Level? {
        val result = session.result ?: return null
        val level = result.level ?: return null
        if (!result.passed) return null
        return session.progress.levels.firstOrNull { it.number == level.number + 1 }?.takeIf { session.progress.isUnlocked(it) }
    }

    fun openLevels() {
        session.quit()
        paused = false
        screen = Screen.Levels
        syncMusic()
    }

    private fun start(level: Level?, stake: Ability? = null) {
        session.newGame(level, stake)
        paused = false
        screen = Screen.Game
        syncMusic()
    }

    fun pause() {
        if (screen == Screen.Game && !session.engine.phase.isFinished) paused = true
        syncMusic()
    }

    /** The pause menu's ad offer: once the ad pays out, play picks up with [ability] waiting for the next shot. */
    fun watchAdForBoost(ability: Ability) {
        if (screen != Screen.Game || !paused) return
        session.watchAdForBoost(ability) { granted -> if (granted && screen == Screen.Game) resume() }
    }

    fun resume() {
        paused = false
        syncMusic()
    }

    /** Leaves whatever is showing for the home screen; a game in progress is dropped and its wager lost. */
    fun goHome() {
        session.quit()
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
 * show it. [GameSession.rankProgress] fills it in from the saved rank.
 */
data class RankProgress(val rank: Int, val pointsIntoRank: Long, val pointsForNextRank: Long) {
    val fraction: Float
        get() = if (pointsForNextRank <= 0L) 0f else (pointsIntoRank.toFloat() / pointsForNextRank).coerceIn(0f, 1f)

    val pointsToGo: Long
        get() = (pointsForNextRank - pointsIntoRank).coerceAtLeast(0L)
}
