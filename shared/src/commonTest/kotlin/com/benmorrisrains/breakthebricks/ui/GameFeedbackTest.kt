package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.platform.HapticStrength
import com.benmorrisrains.breakthebricks.platform.SoundEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GameFeedbackTest {
    private val frame = 1f / 60

    @Test
    fun aQuietFramePlaysNothing() {
        assertEquals(Feedback.None, GameFeedback().frame(frame, emptyList()))
    }

    @Test
    fun manyHitsInOneFramePlayEachSoundOnce() {
        val events = List(20) { GameEvent.BrickHit(it % 7, 2, 5) } + List(5) { GameEvent.WallBounce }
        val feedback = GameFeedback().frame(frame, events)
        assertEquals(setOf(SoundEffect.BrickHit, SoundEffect.WallBounce), feedback.sounds)
        assertNull(feedback.haptic)
    }

    @Test
    fun aBreakingHitPlaysTheBreakSoundAndALightTap() {
        val events = listOf(GameEvent.BrickHit(1, 1, 0), GameEvent.BrickBroken(1, 1, 1))
        val feedback = GameFeedback().frame(frame, events)
        assertEquals(setOf(SoundEffect.BrickBreak), feedback.sounds)
        assertEquals(HapticStrength.Light, feedback.haptic)
    }

    @Test
    fun repeatingSoundsAndLightTapsAreRateLimited() {
        val feedback = GameFeedback(repeatGapSeconds = 0.05f, lightTapGapSeconds = 0.08f)
        val volley = listOf(GameEvent.BrickBroken(0, 0, 1), GameEvent.WallBounce)
        assertEquals(2, feedback.frame(frame, volley).sounds.size)
        val tooSoon = feedback.frame(frame, volley)
        assertEquals(emptySet(), tooSoon.sounds)
        assertNull(tooSoon.haptic)
        feedback.frame(frame, emptyList())
        feedback.frame(frame, emptyList())
        // 4 frames (about 0.067 s) after the first: sounds are due again, the light tap is not.
        val later = feedback.frame(frame, volley)
        assertEquals(setOf(SoundEffect.BrickBreak, SoundEffect.WallBounce), later.sounds)
        assertNull(later.haptic)
    }

    @Test
    fun gameOverAndAbilitiesAlwaysPlayWithTheStrongestTap() {
        val feedback = GameFeedback()
        feedback.frame(frame, listOf(GameEvent.BrickBroken(0, 0, 1), GameEvent.WallBounce))
        val end = feedback.frame(frame, listOf(GameEvent.BrickBroken(0, 1, 1), GameEvent.GameOver(12, 300)))
        assertEquals(setOf(SoundEffect.GameOver), end.sounds)
        assertEquals(HapticStrength.Heavy, end.haptic)
        val grenade = GameFeedback().frame(frame, listOf(GameEvent.AbilityTriggered("grenade", 3, 4)))
        assertEquals(setOf(SoundEffect.AbilityTrigger), grenade.sounds)
        assertEquals(HapticStrength.Heavy, grenade.haptic)
    }

    @Test
    fun aNewRoundThudsAndTicks() {
        val round = GameFeedback().frame(frame, listOf(GameEvent.RoundStarted(round = 4, ballCount = 4)))
        assertEquals(setOf(SoundEffect.RoundStart), round.sounds)
        assertEquals(HapticStrength.Light, round.haptic)
    }

    @Test
    fun onlyTheFirstBallHomeTicks() {
        val feedback = GameFeedback()
        assertEquals(setOf(SoundEffect.BallReturn), feedback.frame(frame, listOf(GameEvent.BallLanded(2f, isFirst = true))).sounds)
        assertEquals(Feedback.None, feedback.frame(frame, listOf(GameEvent.BallLanded(2f, isFirst = false))))
    }

    @Test
    fun earningAnAbilityAndPassingALevelEachHaveTheirOwnJingle() {
        val earned = GameFeedback().frame(frame, listOf(GameEvent.AbilityEarned("grenade", 5)))
        assertEquals(setOf(SoundEffect.AbilityEarned), earned.sounds)
        assertEquals(HapticStrength.Medium, earned.haptic)
        val passed = GameFeedback().frame(frame, listOf(GameEvent.LevelCompleted(round = 20, points = 400)))
        assertEquals(setOf(SoundEffect.LevelComplete), passed.sounds)
        assertEquals(HapticStrength.Medium, passed.haptic)
    }

    @Test
    fun aStrongerTapIsNeverDroppedByTheLightTapLimit() {
        val feedback = GameFeedback()
        feedback.frame(frame, listOf(GameEvent.BrickBroken(0, 0, 1)))
        val grenade = feedback.frame(frame, listOf(GameEvent.AbilityTriggered("grenade", 3, 4)))
        assertEquals(HapticStrength.Heavy, grenade.haptic)
    }

    @Test
    fun firingPlaysTheFireSound() {
        val fired = GameFeedback().fired()
        assertEquals(setOf(SoundEffect.Fire), fired.sounds)
        assertEquals(HapticStrength.Light, fired.haptic)
    }
}
