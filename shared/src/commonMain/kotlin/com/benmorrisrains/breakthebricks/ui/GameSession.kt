package com.benmorrisrains.breakthebricks.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.benmorrisrains.breakthebricks.engine.BrickGame
import com.benmorrisrains.breakthebricks.engine.Difficulty
import com.benmorrisrains.breakthebricks.engine.GameEngine
import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.engine.GridPhysics
import com.benmorrisrains.breakthebricks.engine.PlayerRank
import com.benmorrisrains.breakthebricks.engine.RoundPhase
import com.benmorrisrains.breakthebricks.platform.PlatformServices
import com.benmorrisrains.breakthebricks.progress.RankStore
import kotlin.random.Random

/**
 * One run of play on screen: the current [engine], the best points, the player's rank, and the
 * sound and haptics for what happens. After a game over, [restart] starts a new game on a fresh
 * seed. Free-play games are built for the player's rank with [Difficulty.forRank], and each
 * game's points count toward the next rank when it ends.
 *
 * The seed is the only thing picked at random, and it is picked here, outside the engine; the
 * engine itself stays deterministic for a given seed and set of aims.
 */
class GameSession(
    private val services: PlatformServices,
    private val newSeed: () -> Long = { Random.nextLong() },
    private val newEngine: (seed: Long, rank: Int) -> GameEngine = { seed, rank ->
        BrickGame(Difficulty.forRank(rank), seed = seed, physics = GridPhysics)
    },
) {
    private val rankStore = RankStore(services.store)

    /** The saved rank, including the current game's points once it is over. */
    var playerRank: PlayerRank by mutableStateOf(rankStore.load())
        private set

    /** The rank the current game was built for. */
    var gameRank: Int by mutableStateOf(playerRank.rank)
        private set

    /** Set when the game just finished took the player up a rank, for the rank-up banner. */
    var rankUp: RankUp? by mutableStateOf(null)
        private set

    var engine: GameEngine by mutableStateOf(newEngine(newSeed(), gameRank))
        private set

    var bestPoints: Long by mutableLongStateOf(services.store.getLong(BEST_POINTS_KEY, 0L))
        private set

    private val feedback = GameFeedback()
    private var gameOverSeconds = 0f
    private var pointsCounted = false

    /** True once the game is over and the result has been on screen long enough to tap past. */
    val canRestart: Boolean
        get() = engine.phase == RoundPhase.GameOver && gameOverSeconds >= RESTART_DELAY_SECONDS

    /** Called every display frame with the time it covered and the events the engine produced. */
    fun onFrame(elapsedSeconds: Float, events: List<GameEvent>) {
        if (engine.phase == RoundPhase.GameOver) gameOverSeconds += elapsedSeconds
        for (event in events) {
            if (event !is GameEvent.GameOver) continue
            if (event.points > bestPoints) {
                bestPoints = event.points
                services.store.putLong(BEST_POINTS_KEY, event.points)
            }
            if (!pointsCounted) {
                pointsCounted = true
                playerRank = rankStore.addPoints(event.points)
                if (playerRank.rank > gameRank) rankUp = RankUp(from = gameRank, to = playerRank.rank)
            }
        }
        play(feedback.frame(elapsedSeconds, events))
    }

    /**
     * The rank and bar for the HUD while the current game has scored [points]. During a game the
     * bar fills live but the rank stays the one the game was built for, topping out at full;
     * the new rank shows once the game is over.
     */
    fun hudRank(points: Long): HudRank {
        if (pointsCounted) return HudRank(playerRank.rank, playerRank.progress)
        val live = playerRank.adding(points)
        return if (live.rank > gameRank) HudRank(gameRank, 1f) else HudRank(gameRank, live.progress)
    }

    /** Called when the player's release fired the round's balls. */
    fun onFired() = play(feedback.fired())

    /** Starts a new game if [canRestart]. Returns true if it did. */
    fun restart(): Boolean {
        if (!canRestart) return false
        gameRank = playerRank.rank
        rankUp = null
        pointsCounted = false
        engine = newEngine(newSeed(), gameRank)
        gameOverSeconds = 0f
        return true
    }

    private fun play(feedback: Feedback) {
        feedback.sounds.forEach(services.audio::play)
        feedback.haptic?.let(services.haptics::tap)
    }

    companion object {
        const val BEST_POINTS_KEY = "best_points"

        /** Keeps a tap that was meant for the last shot from skipping the game over screen. */
        const val RESTART_DELAY_SECONDS = 0.6f
    }
}

/** The game just finished moved the player from rank [from] to rank [to]. */
data class RankUp(val from: Int, val to: Int)

/** What the HUD shows: the [rank] and how full the bar to the next rank is, 0 to 1. */
data class HudRank(val rank: Int, val progress: Float)
