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
 * of firing once per event. Moments (round start, abilities, level passed, game over, firing)
 * always play.
 *
 * Haptics are kept for things the player should feel, from light to heavy: firing, breaking a
 * brick and the new row arriving are light ticks; earning an ability and passing a level are a
 * medium click; the grenade going off and game over are heavy. Plain hits and wall bounces are
 * sound only, so a big volley rattles the ear without buzzing the hand.
 */
class GameFeedback(
    private val repeatGapSeconds: Float = 0.05f,
    private val lightTapGapSeconds: Float = 0.1f,
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
                is GameEvent.BallLanded -> if (event.isFirst) sounds += SoundEffect.BallReturn
                is GameEvent.RoundStarted -> {
                    sounds += SoundEffect.RoundStart
                    tap(HapticStrength.Light)
                }
                is GameEvent.AbilityTriggered -> {
                    sounds += SoundEffect.AbilityTrigger
                    tap(HapticStrength.Heavy)
                }
                is GameEvent.AbilityEarned -> {
                    sounds += SoundEffect.AbilityEarned
                    tap(HapticStrength.Medium)
                }
                is GameEvent.GameOver -> {
                    sounds += SoundEffect.GameOver
                    tap(HapticStrength.Heavy)
                }
                is GameEvent.LevelCompleted -> {
                    sounds += SoundEffect.LevelComplete
                    tap(HapticStrength.Medium)
                }
                // The shot's own fire sound covers it.
                is GameEvent.PowerUpUsed -> Unit
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
