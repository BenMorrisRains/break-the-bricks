package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.engine.GrenadeBall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanvasEffectsTest {
    private val grenadeHit = listOf(
        GameEvent.AbilityTriggered(GrenadeBall.id, 3, 2),
        GameEvent.BrickHit(3, 2, 0),
        GameEvent.BrickBroken(3, 2, 40),
    )

    @Test
    fun aBrokenBrickBurstsIntoPixelsOfItsColor() {
        val effects = CanvasEffects()
        effects.update(0f, listOf(GameEvent.BrickBroken(1, 4, points = 1)), roundValue = 5)
        assertEquals(CanvasEffects.BREAK_PIXELS, effects.particles.size)
        assertTrue(effects.particles.all { it.tint == Tint.Cyan && it.shape == ParticleShape.Pixel })
        assertFalse(effects.isShaking)
        assertEquals(0f, effects.flash)
    }

    @Test
    fun breakSparksTakeTheBricksLastColor() {
        val effects = CanvasEffects()
        // A brick broken while still worth the full round value reads magenta.
        effects.update(0f, listOf(GameEvent.BrickBroken(0, 0, points = 8)), roundValue = 8)
        assertTrue(effects.particles.all { it.tint == Tint.Magenta })
    }

    @Test
    fun aGrenadeFlashesShakesRingsAndThrowsShrapnelWithoutAPlainBurst() {
        val effects = CanvasEffects()
        effects.update(0f, grenadeHit, roundValue = 5)
        assertEquals(1f, effects.flash)
        assertTrue(effects.isShaking)
        assertTrue(effects.shockwaves.isNotEmpty())
        assertEquals(1, effects.ghosts.size)
        assertEquals(CanvasEffects.SHRAPNEL, effects.particles.size)
        assertTrue(effects.particles.all { it.shape == ParticleShape.Streak })
    }

    @Test
    fun theHitBrickFlashesWhiteThenShatters() {
        val effects = CanvasEffects()
        effects.update(0f, grenadeHit, roundValue = 5)
        effects.update(CanvasEffects.GHOST_SECONDS + 0.01f, emptyList(), roundValue = 5)
        assertTrue(effects.ghosts.isEmpty())
        assertEquals(CanvasEffects.SHARDS, effects.particles.count { it.shape == ParticleShape.Pixel })
    }

    @Test
    fun everythingSettlesAndTheScreenStopsShaking() {
        val effects = CanvasEffects()
        effects.update(0f, grenadeHit, roundValue = 5)
        assertTrue(effects.shakeX() != 0f || effects.shakeY() != 0f)
        repeat(120) { effects.update(1f / 60f, emptyList(), roundValue = 5) }
        assertTrue(effects.particles.isEmpty())
        assertTrue(effects.shockwaves.isEmpty())
        assertTrue(effects.ghosts.isEmpty())
        assertEquals(0f, effects.flash)
        assertEquals(0f, effects.shakeX())
        assertEquals(0f, effects.shakeY())
    }

    @Test
    fun aHugeVolleyIsCapped() {
        val effects = CanvasEffects()
        val volley = (0 until 200).map { GameEvent.BrickBroken(it % 7, it % 9, 1) }
        effects.update(0f, volley, roundValue = 1)
        assertEquals(CanvasEffects.MAX_PARTICLES, effects.particles.size)
    }

    @Test
    fun theSameEventsAlwaysLookTheSame() {
        val a = CanvasEffects()
        val b = CanvasEffects()
        for (effects in listOf(a, b)) {
            effects.update(0f, grenadeHit, roundValue = 5)
            effects.update(0.05f, emptyList(), roundValue = 5)
        }
        assertEquals(a.particles.map { it.x to it.y }, b.particles.map { it.x to it.y })
    }

    @Test
    fun theSplitBallsEachGetTheirOwnColor() {
        // Before a split every ball is plain white.
        assertTrue((0 until 12).all { splitBallTint(it, ballCount = 12, total = 12) == null })
        // After it, the first ball and the two new ones (past the round's 12) stand out.
        val tints = (0 until 14).map { splitBallTint(it, ballCount = 12, total = 14) }
        assertEquals(Tint.Magenta, tints[0])
        assertEquals(listOf(Tint.Cyan, Tint.Lime), tints.subList(12, 14))
        assertTrue(tints.subList(1, 12).all { it == null })
        assertEquals(3, tints.filterNotNull().toSet().size)
    }

    @Test
    fun brickTiersMatchThePalette() {
        assertEquals(0, brickTier(hits = 1, roundValue = 1, tiers = 5))
        assertEquals(0, brickTier(hits = 1, roundValue = 55, tiers = 5))
        assertEquals(4, brickTier(hits = 55, roundValue = 55, tiers = 5))
        assertEquals(4, brickTier(hits = 90, roundValue = 55, tiers = 5))
    }
}
