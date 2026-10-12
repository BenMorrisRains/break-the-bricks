package com.benmorrisrains.breakthebricks.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.benmorrisrains.breakthebricks.engine.BrickGame
import com.benmorrisrains.breakthebricks.engine.GameEngine
import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.engine.GridPhysics
import com.benmorrisrains.breakthebricks.engine.RoundPhase
import com.benmorrisrains.breakthebricks.platform.PlatformServices
import kotlin.random.Random

/**
 * One run of play on screen: the current [engine], the best points, and the sound and haptics
 * for what happens. After a game over, [restart] starts a new game on a fresh seed.
 *
 * The seed is the only thing picked at random, and it is picked here, outside the engine; the
 * engine itself stays deterministic for a given seed and set of aims.
 */
class GameSession(
    private val services: PlatformServices,
    private val newSeed: () -> Long = { Random.nextLong() },
    private val newEngine: (seed: Long) -> GameEngine = { seed -> BrickGame(seed = seed, physics = GridPhysics) },
) {
    var engine: GameEngine by mutableStateOf(newEngine(newSeed()))
        private set

    var bestPoints: Long by mutableLongStateOf(services.store.getLong(BEST_POINTS_KEY, 0L))
        private set

    private val feedback = GameFeedback()
    private var gameOverSeconds = 0f

    /** True once the game is over and the result has been on screen long enough to tap past. */
    val canRestart: Boolean
        get() = engine.phase == RoundPhase.GameOver && gameOverSeconds >= RESTART_DELAY_SECONDS

    /** Called every display frame with the time it covered and the events the engine produced. */
    fun onFrame(elapsedSeconds: Float, events: List<GameEvent>) {
        if (engine.phase == RoundPhase.GameOver) gameOverSeconds += elapsedSeconds
        for (event in events) {
            if (event is GameEvent.GameOver && event.points > bestPoints) {
                bestPoints = event.points
                services.store.putLong(BEST_POINTS_KEY, event.points)
            }
        }
        play(feedback.frame(elapsedSeconds, events))
    }

    /** Called when the player's release fired the round's balls. */
    fun onFired() = play(feedback.fired())

    /** Starts a new game if [canRestart]. Returns true if it did. */
    fun restart(): Boolean {
        if (!canRestart) return false
        engine = newEngine(newSeed())
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
