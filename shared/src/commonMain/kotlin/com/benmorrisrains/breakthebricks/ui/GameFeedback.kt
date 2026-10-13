package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.platform.HapticStrength
import com.benmorrisrains.breakthebricks.platform.SoundEffect

/** What to play for one frame: each sound at most once, and at most one haptic tap. */
data class Feedback(val sounds: Set<SoundEffect>, val haptic: HapticStrength?) {
    companion object {
        val None = Feedback(emptySet(), null)
    }
}

/**
 * Turns each frame's drained [GameEvent]s into sounds and haptics. With dozens of balls in play
 * a frame can hold many hits, so the frequent sounds and the light tap are rate-limited instead
 * of firing once per event. Ability, game over and fire feedback always plays.
 * T13 tunes the feel; this is the first-playable mapping.
 */
class GameFeedback(
    private val repeatGapSeconds: Float = 0.05f,
    private val lightTapGapSeconds: Float = 0.08f,
) {
    private val sinceSound = mutableMapOf<SoundEffect, Float>()
    private var sinceTap = Float.MAX_VALUE

    /** Feedback for one display frame of [elapsedSeconds] that produced [events]. */
    fun frame(elapsedSeconds: Float, events: List<GameEvent>): Feedback {
        for (entry in sinceSound.entries) entry.setValue(entry.value + elapsedSeconds)
        sinceTap += elapsedSeconds
        if (events.isEmpty()) return Feedback.None

        val sounds = linkedSetOf<SoundEffect>()
        var haptic: HapticStrength? = null
        fun tap(strength: HapticStrength) {
            haptic = haptic?.let { maxOf(it, strength) } ?: strength
        }
        for (event in events) {
            when (event) {
                // A hit that breaks the brick plays the break sound instead.
                is GameEvent.BrickHit -> if (event.hitsLeft > 0) sounds += SoundEffect.BrickHit
                is GameEvent.BrickBroken -> {
                    sounds += SoundEffect.BrickBreak
                    tap(HapticStrength.Light)
                }
                GameEvent.WallBounce -> sounds += SoundEffect.WallBounce
                is GameEvent.AbilityTriggered -> {
                    sounds += SoundEffect.AbilityTrigger
                    tap(HapticStrength.Heavy)
                }
                is GameEvent.AbilityEarned -> {
                    sounds += SoundEffect.AbilityTrigger
                    tap(HapticStrength.Medium)
                }
                is GameEvent.GameOver -> {
                    sounds += SoundEffect.GameOver
                    tap(HapticStrength.Heavy)
                }
                is GameEvent.LevelCompleted -> tap(HapticStrength.Medium)
                is GameEvent.BallLanded, is GameEvent.RoundStarted -> Unit
            }
        }

        sounds.removeAll { it in REPEATING && (sinceSound[it] ?: Float.MAX_VALUE) < repeatGapSeconds }
        for (effect in sounds) if (effect in REPEATING) sinceSound[effect] = 0f
        if (haptic == HapticStrength.Light && sinceTap < lightTapGapSeconds) haptic = null
        if (haptic != null) sinceTap = 0f
        return Feedback(sounds, haptic)
    }

    /** Feedback for the player firing a round's balls. */
    fun fired(): Feedback {
        sinceTap = 0f
        return Feedback(setOf(SoundEffect.Fire), HapticStrength.Light)
    }

    private companion object {
        /** Sounds a volley of balls can trigger every frame. */
        val REPEATING = setOf(SoundEffect.BrickHit, SoundEffect.BrickBreak, SoundEffect.WallBounce)
    }
}
