package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.Abilities
import com.benmorrisrains.breakthebricks.engine.Ability
import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.engine.GrenadeBall

/**
 * Ids of the abilities in Ben's lineup (2026-10-13): big ball at round 5, splitter at 10, drill
 * at 20, the grenade at 30, then ghost ball, time freeze and the 5-way splitter at 40, 50 and 60.
 * The engine's abilities must use these ids to get their looks.
 */
object AbilityIds {
    const val BIG_BALL = "big_ball"
    const val SPLITTER = "splitter"
    const val DRILL = "drill"
    val GRENADE = GrenadeBall.id
    const val GHOST = "ghost"
    const val TIME_FREEZE = "time_freeze"
    const val SPLITTER_FIVE = "splitter_five"
}

/** The name players see for an ability: "GRENADE BALL". */
fun abilityName(abilityId: String): String = when (abilityId) {
    AbilityIds.BIG_BALL -> "BIG BALL"
    AbilityIds.SPLITTER -> "SPLITTER BALL"
    AbilityIds.DRILL -> "DRILL BALL"
    AbilityIds.GRENADE -> "GRENADE BALL"
    AbilityIds.GHOST -> "GHOST BALL"
    AbilityIds.TIME_FREEZE -> "TIME FREEZE"
    AbilityIds.SPLITTER_FIVE -> "5-WAY SPLITTER"
    else -> "${abilityId.uppercase().replace('_', ' ')} BALL"
}

/** Abilities a game that got to [reachedRound] did not earn, soonest first, to show off as locked. */
fun lockedAbilities(reachedRound: Int, earnable: List<Ability> = Abilities.earnable): List<Ability> =
    earnable.filter { it.earnedAtRound > reachedRound }.sortedBy { it.earnedAtRound }

/** One ability card on screen, [age] seconds after it started to flip in. */
class EarnedCard(val abilityId: String, val round: Int) {
    var age = 0f
        internal set

    /** 0 edge-on, 1 facing the player. */
    val flip: Float get() = easeOut((age / EarnedCards.FLIP_IN_SECONDS).coerceIn(0f, 1f))

    /** 0 while the card is up, 1 when it has shrunk into the power-up carousel. */
    val drop: Float
        get() = easeIn(((age - EarnedCards.DROP_START) / EarnedCards.DROP_SECONDS).coerceIn(0f, 1f))
}

/**
 * The earning moment (plan doc): when a milestone round is reached, play holds for about a
 * second while an ability card flips in, then the card drops into the power-up carousel. Several abilities
 * earned at once queue up and show one after another. Fed each frame with the drained events.
 */
class EarnedCards {
    private val queue = ArrayDeque<EarnedCard>()

    /** The card showing now, or null. */
    var current: EarnedCard? = null
        private set

    /** True while the game should not advance: the card is flipping in or held up. */
    val holdsPlay: Boolean get() = current?.let { it.age < DROP_START } ?: false

    fun update(elapsedSeconds: Float, events: List<GameEvent>) {
        current?.let { card ->
            card.age += elapsedSeconds
            if (card.age >= TOTAL_SECONDS) current = null
        }
        for (event in events) {
            if (event is GameEvent.AbilityEarned) queue += EarnedCard(event.abilityId, event.round)
        }
        if (current == null) current = queue.removeFirstOrNull()
    }

    /** A tap on the card: skip straight to it dropping into the tray. */
    fun skip() {
        current?.let { if (it.age < DROP_START) it.age = DROP_START }
    }

    /** False while [abilityId]'s card is still on its way to the tray, so the tray holds its slot empty. */
    fun hasLanded(abilityId: String): Boolean =
        current?.abilityId != abilityId && queue.none { it.abilityId == abilityId }

    companion object {
        const val FLIP_IN_SECONDS = 0.35f
        const val HOLD_SECONDS = 0.85f
        const val DROP_SECONDS = 0.45f
        const val DROP_START = FLIP_IN_SECONDS + HOLD_SECONDS
        const val TOTAL_SECONDS = DROP_START + DROP_SECONDS
    }
}

/** The power-up carousel's pulse: an ability's icon swells and glows each round when it fires. */
class AbilityPulses(private val durationSeconds: Float = 0.6f) {
    private val remaining = mutableMapOf<String, Float>()

    fun update(elapsedSeconds: Float, events: List<GameEvent>) {
        val iterator = remaining.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val left = entry.value - elapsedSeconds
            if (left <= 0f) iterator.remove() else entry.setValue(left)
        }
        for (event in events) {
            if (event is GameEvent.AbilityTriggered) remaining[event.abilityId] = durationSeconds
        }
    }

    /** 1 right as [abilityId] fires, fading to 0. */
    fun pulse(abilityId: String): Float = (remaining[abilityId] ?: 0f) / durationSeconds
}

private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)

private fun easeIn(t: Float): Float = t * t
