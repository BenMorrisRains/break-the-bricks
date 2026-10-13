package com.benmorrisrains.breakthebricks.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.benmorrisrains.breakthebricks.engine.Ability
import com.benmorrisrains.breakthebricks.engine.BrickGame
import com.benmorrisrains.breakthebricks.engine.GameEngine
import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.engine.GridPhysics
import com.benmorrisrains.breakthebricks.engine.Level
import com.benmorrisrains.breakthebricks.engine.RoundPhase
import com.benmorrisrains.breakthebricks.engine.WagerGame
import com.benmorrisrains.breakthebricks.engine.WagerOutcome
import com.benmorrisrains.breakthebricks.platform.PlatformServices
import kotlin.random.Random

/**
 * One run of play on screen: the current [engine], the best points, and the sound and haptics
 * for what happens. [newGame] starts a new game: free play on a fresh seed, or a [Level] on its
 * own fixed seed. Once the game is finished, [result] says how it went.
 *
 * Free play carries the ability wager: after the first game, a game with anything in the
 * [bank] stakes one copy, which is active from round 1. Levels are never staked (default not
 * yet confirmed by Ben), but abilities earned in them are banked like any other.
 *
 * The seed is the only thing picked at random, and it is picked here, outside the engine; the
 * engine itself stays deterministic for a given seed and set of aims.
 */
class GameSession(
    private val services: PlatformServices,
    private val newSeed: () -> Long = { Random.nextLong() },
    private val newEngine: (seed: Long, abilities: List<Ability>) -> GameEngine = { seed, abilities ->
        BrickGame(seed = seed, physics = GridPhysics, abilities = abilities)
    },
    private val newLevelEngine: (Level) -> GameEngine = { it.newGame(GridPhysics) },
) {
    /** Saved stars and best points of the levels. */
    val progress = LevelProgress(services.store)

    /** The level being played, or null in free play. */
    var level: Level? by mutableStateOf<Level?>(null)
        private set

    /** The saved ability inventory. */
    val bank = AbilityBank(services.store)

    var engine: GameEngine by mutableStateOf(newEngine(newSeed(), emptyList()))
        private set

    /** The current game's wager; unstaked for levels, the first game and an empty inventory. */
    var wager: WagerGame by mutableStateOf(WagerGame())
        private set

    /** The best of the current mode: free play, or the level being played. */
    var bestPoints: Long by mutableLongStateOf(services.store.getLong(BEST_POINTS_KEY, 0L))
        private set

    private val feedback = GameFeedback()
    private var gameOverSeconds = 0f

    /** The best before this game started, so a new best can be called out on the results. */
    private var bestBeforeGame = bestPoints

    /** True once the game is finished and the final board has been on screen long enough to tap past. */
    val canSkipToResults: Boolean
        get() = engine.phase.isFinished && gameOverSeconds >= SKIP_DELAY_SECONDS

    /** True once the game is finished and the final board has had its moment; time for the results. */
    val resultsDue: Boolean
        get() = engine.phase.isFinished && gameOverSeconds >= RESULTS_DELAY_SECONDS

    /** How the game went, once it is finished; null while it is still running. */
    val result: GameResult?
        get() {
            if (!engine.phase.isFinished) return null
            val passed = engine.phase == RoundPhase.LevelComplete
            return GameResult(
                round = engine.round,
                points = engine.points,
                bestPoints = bestPoints,
                isNewBest = engine.points > bestBeforeGame,
                level = level,
                passed = passed,
                stars = level?.stars(passed, engine.points) ?: 0,
                stake = wager.stake,
                wagerOutcome = wager.outcome,
                banked = wager.banked.mapNotNull { id -> bank.abilities.firstOrNull { it.id == id } },
                stakeCopiesLeft = wager.stake?.let { bank.inventory.count(it.id) } ?: 0,
            )
        }

    /** Called every display frame with the time it covered and the events the engine produced. */
    fun onFrame(elapsedSeconds: Float, events: List<GameEvent>) {
        if (engine.phase.isFinished) gameOverSeconds += elapsedSeconds
        for (event in events) {
            bank.save(wager.onEvent(event, bank.inventory))
            when (event) {
                is GameEvent.GameOver -> finish(passed = false, points = event.points)
                is GameEvent.LevelCompleted -> finish(passed = true, points = event.points)
                else -> Unit
            }
        }
        play(feedback.frame(elapsedSeconds, events))
    }

    /** Called when the player's release fired the round's balls. */
    fun onFired() = play(feedback.fired())

    /**
     * Drops the current game, finished or not, and starts a new one: [level] on its fixed seed,
     * or free play on a fresh seed when it is null. Free play must name a [stake] when
     * [AbilityBank.stakeRequired]; the staked copy leaves the inventory before play begins.
     */
    fun newGame(level: Level? = null, stake: Ability? = null) {
        quit()
        val wagered = if (level == null) stake else null
        require(level != null || wagered != null || !bank.stakeRequired) { "This game needs a stake" }
        val wager = WagerGame(wagered)
        bank.save(wager.start(bank.inventory))
        if (level == null) bank.markFirstGamePlayed()
        this.wager = wager
        this.level = level
        engine = if (level == null) newEngine(newSeed(), listOfNotNull(wagered)) else newLevelEngine(level)
        bestPoints = services.store.getLong(bestPointsKey(level), 0L)
        gameOverSeconds = 0f
        bestBeforeGame = bestPoints
    }

    /** Leaves the current game. A wager still open is lost; its copy was taken at the start. */
    fun quit() = wager.abandon()

    private fun finish(passed: Boolean, points: Long) {
        val level = level
        if (level != null) progress.record(level, passed, points)
        if (points > bestPoints) {
            bestPoints = points
            services.store.putLong(bestPointsKey(level), points)
        }
    }

    private fun bestPointsKey(level: Level?) =
        if (level == null) BEST_POINTS_KEY else LevelProgress.bestPointsKey(level.number)

    private fun play(feedback: Feedback) {
        feedback.sounds.forEach(services.audio::play)
        feedback.haptic?.let(services.haptics::tap)
    }

    companion object {
        const val BEST_POINTS_KEY = "best_points"

        /** Keeps a tap that was meant for the last shot from skipping the final board. */
        const val SKIP_DELAY_SECONDS = 0.6f

        /** How long the final board stays up before the results screen takes over. */
        const val RESULTS_DELAY_SECONDS = 1.5f
    }
}

/**
 * The end of one game, for the results screen. In a level, [passed] and [stars] say how it went.
 * In a wagered game, [stake] is the ability on the line, [wagerOutcome] whether it was kept, and
 * [stakeCopiesLeft] how many copies of it the player holds now. [banked] lists abilities earned
 * into the inventory during the game.
 */
data class GameResult(
    val round: Int,
    val points: Long,
    val bestPoints: Long,
    val isNewBest: Boolean,
    val level: Level? = null,
    val passed: Boolean = false,
    val stars: Int = 0,
    val stake: Ability? = null,
    val wagerOutcome: WagerOutcome = WagerOutcome.Unstaked,
    val banked: List<Ability> = emptyList(),
    val stakeCopiesLeft: Int = 0,
)
