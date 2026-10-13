package com.benmorrisrains.breakthebricks.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.benmorrisrains.breakthebricks.engine.Ability
import com.benmorrisrains.breakthebricks.engine.BrickGame
import com.benmorrisrains.breakthebricks.engine.Difficulty
import com.benmorrisrains.breakthebricks.engine.GameEngine
import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.engine.GridPhysics
import com.benmorrisrains.breakthebricks.engine.Level
import com.benmorrisrains.breakthebricks.engine.PlayerRank
import com.benmorrisrains.breakthebricks.engine.RoundPhase
import com.benmorrisrains.breakthebricks.engine.WagerGame
import com.benmorrisrains.breakthebricks.engine.WagerOutcome
import com.benmorrisrains.breakthebricks.platform.ChipMusic
import com.benmorrisrains.breakthebricks.platform.PlatformServices
import com.benmorrisrains.breakthebricks.progress.RankStore
import kotlin.random.Random

/**
 * One run of play on screen: the current [engine], the best points, and the sound and haptics
 * for what happens. [newGame] starts a new game: free play on a fresh seed, or a [Level] on its
 * own fixed seed. Once the game is finished, [result] says how it went.
 *
 * Free-play games are built for the player's rank with [Difficulty.forRank]; levels keep their
 * own layouts. Every game's points, free play or level, count toward the next rank when it ends.
 *
 * Free play carries the ability wager: after the first game, a game with anything in the
 * [bank] stakes one copy, which is active from round 1. Levels are never staked (default not
 * yet confirmed by Ben), but abilities earned in them are banked like any other.
 *
 * During a game the player can watch a rewarded ad for one power-up ([watchAdForBoost]). It is
 * on for that game only, from the next shot, and never enters the [bank], so it can't be
 * staked (Ben, 2026-10-13). One ad boost per game (default not yet confirmed by Ben).
 *
 * The seed is the only thing picked at random, and it is picked here, outside the engine; the
 * engine itself stays deterministic for a given seed and set of aims.
 */
class GameSession(
    private val services: PlatformServices,
    private val newSeed: () -> Long = { Random.nextLong() },
    private val newEngine: (seed: Long, rank: Int, abilities: List<Ability>) -> GameEngine = { seed, rank, abilities ->
        BrickGame(Difficulty.forRank(rank), seed = seed, physics = GridPhysics, abilities = abilities)
    },
    private val newLevelEngine: (Level) -> GameEngine = { it.newGame(GridPhysics) },
) {
    /** Saved stars and best points of the levels. */
    val progress = LevelProgress(services.store)

    /** The level being played, or null in free play. */
    var level: Level? by mutableStateOf<Level?>(null)
        private set

    private val rankStore = RankStore(services.store)

    /** The saved rank, including the current game's points once it is over. */
    var playerRank: PlayerRank by mutableStateOf(rankStore.load())
        private set

    /** The rank the current game was started at; free play is built for it. */
    var gameRank: Int by mutableStateOf(playerRank.rank)
        private set

    /** Set when the game just finished took the player up a rank, for the rank-up banner. */
    var rankUp: RankUp? by mutableStateOf(null)
        private set

    private var pointsCounted = false

    /** The saved ability inventory. */
    val bank = AbilityBank(services.store)

    var engine: GameEngine by mutableStateOf(newEngine(newSeed(), gameRank, emptyList()))
        private set

    /** The current game's wager; unstaked for levels, the first game and an empty inventory. */
    var wager: WagerGame by mutableStateOf(WagerGame())
        private set

    /** True once this game has had its ad power-up. */
    var boostUsed: Boolean by mutableStateOf(false)
        private set

    /** True while a rewarded ad is on screen. */
    var adShowing: Boolean by mutableStateOf(false)
        private set

    /** True when the last ad didn't pay out (none ready, failed, or closed early). */
    var adMissed: Boolean by mutableStateOf(false)
        private set

    /** Power-ups an ad can turn on right now: none once this game has had one, else any not already on. */
    val boostOffers: List<Ability>
        get() = if (boostUsed || adShowing || engine.phase.isFinished) {
            emptyList()
        } else {
            bank.abilities.filter { ability -> engine.activeAbilities.none { it.id == ability.id } }
        }

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
                stars = level?.stars(passed, engine.round) ?: 0,
                rankUp = rankUp,
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
                is GameEvent.GameOver -> finish(passed = false, round = event.round, points = event.points)
                is GameEvent.LevelCompleted -> finish(passed = true, round = event.round, points = event.points)
                else -> Unit
            }
        }
        play(feedback.frame(elapsedSeconds, events))
    }

    /** The saved rank as the home and results screens show it. */
    fun rankProgress(): RankProgress =
        RankProgress(playerRank.rank, playerRank.pointsIntoRank, playerRank.pointsForNextRank)

    /**
     * The rank and bar for the HUD while the current game has scored [points]. During a game the
     * bar fills live but the rank stays the one the game started at, topping out at full; the
     * new rank shows once the game is over.
     */
    fun hudRank(points: Long): HudRank {
        if (pointsCounted) return HudRank(playerRank.rank, playerRank.progress)
        val live = playerRank.adding(points)
        return if (live.rank > gameRank) HudRank(gameRank, 1f) else HudRank(gameRank, live.progress)
    }

    /** Called when the player's release fired the round's balls. */
    fun onFired() = play(feedback.fired())

    /** Whether the music loop should play; the music setting can still keep it silent. */
    fun setMusicPlaying(playing: Boolean) = services.audio.setMusicPlaying(playing)

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
        gameRank = playerRank.rank
        rankUp = null
        pointsCounted = false
        engine = if (level == null) newEngine(newSeed(), gameRank, listOfNotNull(wagered)) else newLevelEngine(level)
        bestPoints = services.store.getLong(bestPointsKey(level), 0L)
        gameOverSeconds = 0f
        bestBeforeGame = bestPoints
        boostUsed = false
        adMissed = false
        nextMusicTrack()
    }

    /** Every game plays the next music track, carrying on from where the last app run left off. */
    private fun nextMusicTrack() {
        val track = (services.store.getLong(MUSIC_TRACK_KEY, -1L) + 1).mod(ChipMusic.tracks.size)
        services.store.putLong(MUSIC_TRACK_KEY, track.toLong())
        services.audio.setMusicTrack(track)
    }

    /**
     * Shows a rewarded ad and, if the player watches it, turns [ability] on for the rest of this
     * game from the next shot. [onDone] gets whether it was granted. An ad that finishes after
     * the game was left grants nothing.
     */
    fun watchAdForBoost(ability: Ability, onDone: (granted: Boolean) -> Unit = {}) {
        if (ability !in boostOffers) return
        val game = engine
        adShowing = true
        adMissed = false
        services.ads.show { rewarded ->
            adShowing = false
            val granted = rewarded && engine === game && game.boost(ability)
            if (granted) boostUsed = true
            adMissed = !rewarded
            onDone(granted)
        }
    }

    /** Leaves the current game. A wager still open is lost; its copy was taken at the start. */
    fun quit() = wager.abandon()

    private fun finish(passed: Boolean, round: Int, points: Long) {
        val level = level
        if (level != null) progress.record(level, passed, round, points)
        if (!pointsCounted) {
            pointsCounted = true
            playerRank = rankStore.addPoints(points)
            if (playerRank.rank > gameRank) rankUp = RankUp(from = gameRank, to = playerRank.rank)
        }
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

        /** The music track the last game played. */
        const val MUSIC_TRACK_KEY = "music_track"

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
    /** Set when this game took the player up a rank. */
    val rankUp: RankUp? = null,
    val stake: Ability? = null,
    val wagerOutcome: WagerOutcome = WagerOutcome.Unstaked,
    val banked: List<Ability> = emptyList(),
    val stakeCopiesLeft: Int = 0,
)

/** The game just finished moved the player from rank [from] to rank [to]. */
data class RankUp(val from: Int, val to: Int)

/** What the HUD shows: the [rank] and how full the bar to the next rank is, 0 to 1. */
data class HudRank(val rank: Int, val progress: Float)
