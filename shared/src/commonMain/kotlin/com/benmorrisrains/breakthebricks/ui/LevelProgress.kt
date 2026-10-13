package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.FreeLevels
import com.benmorrisrains.breakthebricks.engine.GameEngine
import com.benmorrisrains.breakthebricks.engine.Level
import com.benmorrisrains.breakthebricks.engine.RoundPhase
import com.benmorrisrains.breakthebricks.platform.KeyValueStore

/**
 * Saved stars and best points per level. Level 1 is always open; each later level opens once
 * the one before it is passed. Stars and best points only ever go up.
 */
class LevelProgress(private val store: KeyValueStore, val levels: List<Level> = FreeLevels.all) {
    fun stars(number: Int): Int = store.getLong(starsKey(number), 0L).toInt()

    fun bestPoints(number: Int): Long = store.getLong(bestPointsKey(number), 0L)

    fun isUnlocked(number: Int): Boolean = number == 1 || stars(number - 1) >= 1

    val totalStars: Int get() = levels.sumOf { stars(it.number) }

    /** Saves a finished attempt that ended in [round] and returns the stars it earned. */
    fun record(level: Level, passed: Boolean, round: Int, points: Long): Int {
        val earned = level.stars(passed, round)
        if (earned > stars(level.number)) store.putLong(starsKey(level.number), earned.toLong())
        if (points > bestPoints(level.number)) store.putLong(bestPointsKey(level.number), points)
        return earned
    }

    companion object {
        fun starsKey(number: Int) = "level_${number}_stars"
        fun bestPointsKey(number: Int) = "level_${number}_best"
    }
}

/** Text shown over the field once a game is finished. */
data class FinishCard(val title: String, val note: String?, val hint: String, val won: Boolean)

/** The finish card for [engine], or null while it is still playing. [level] is null in free play. */
fun finishCard(engine: GameEngine, level: Level?): FinishCard? {
    if (!engine.phase.isFinished) return null
    if (level == null) return FinishCard("GAME OVER", null, "TAP TO CONTINUE", won = false)
    val passed = engine.phase == RoundPhase.LevelComplete
    val stars = level.stars(passed, engine.round)
    val note = if (passed) "${starText(stars)}  IN ${engine.round} ROUNDS" else "BREAK EVERY BRICK"
    return FinishCard(if (passed) "LEVEL ${level.number} CLEAR" else "GAME OVER", note, "TAP TO CONTINUE", won = passed)
}

/** "★★☆" for 2 of 3 stars. */
fun starText(stars: Int, of: Int = 3): String = "★".repeat(stars.coerceIn(0, of)) + "☆".repeat(of - stars.coerceIn(0, of))
