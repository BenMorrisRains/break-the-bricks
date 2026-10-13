package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.GameEngine
import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.engine.GrenadeBall

/**
 * Whether the round's first ball is still an unexploded ability ball (the grenade), so the
 * canvas can draw it as one. Fed from drained [GameEvent]s. T11 brings the full showcase.
 */
class AbilityCue {
    private var triggeredThisRound = false

    fun update(events: List<GameEvent>) {
        for (event in events) {
            when (event) {
                is GameEvent.RoundStarted -> triggeredThisRound = false
                is GameEvent.AbilityTriggered -> triggeredThisRound = true
                else -> Unit
            }
        }
    }

    fun isArmed(engine: GameEngine): Boolean =
        engine.activeAbilities.any { it.id == GrenadeBall.id } && !triggeredThisRound
}
