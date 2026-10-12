package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.GameEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class BrickFlashesTest {
    @Test
    fun hitFlashesFullyThenFades() {
        val flashes = BrickFlashes(durationSeconds = 0.1f)
        flashes.update(0f, listOf(GameEvent.BrickHit(2, 3, hitsLeft = 4)))
        assertEquals(1f, flashes.intensity(2, 3))
        assertEquals(0f, flashes.intensity(3, 2))
        flashes.update(0.05f, emptyList())
        assertEquals(0.5f, flashes.intensity(2, 3), 1e-4f)
        flashes.update(0.06f, emptyList())
        assertEquals(0f, flashes.intensity(2, 3))
    }

    @Test
    fun brokenBricksAndNewRoundsClearFlashes() {
        val flashes = BrickFlashes()
        flashes.update(0f, listOf(GameEvent.BrickHit(0, 0, 0), GameEvent.BrickBroken(0, 0, 1)))
        assertEquals(0f, flashes.intensity(0, 0))
        flashes.update(0f, listOf(GameEvent.BrickHit(1, 1, 3), GameEvent.RoundStarted(2, 2)))
        assertEquals(0f, flashes.intensity(1, 1))
    }
}
