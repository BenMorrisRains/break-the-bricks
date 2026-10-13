package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.FreeLevels
import com.benmorrisrains.breakthebricks.engine.GridPhysics
import com.benmorrisrains.breakthebricks.engine.Level
import com.benmorrisrains.breakthebricks.platform.InMemoryStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelProgressTest {
    private val store = InMemoryStore()
    private val progress = LevelProgress(store)
    private val one = FreeLevels[1]

    @Test
    fun onlyLevelOneIsOpenAtFirst() {
        assertEquals(10, progress.levels.size)
        assertTrue(progress.isUnlocked(1))
        assertFalse(progress.isUnlocked(2))
        assertEquals(0, progress.totalStars)
    }

    @Test
    fun aFailedAttemptOpensNothing() {
        assertEquals(0, progress.record(one, passed = false, points = 500))
        assertFalse(progress.isUnlocked(2))
        assertEquals(500, progress.bestPoints(1))
    }

    @Test
    fun passingOpensTheNextLevelAndStarsOnlyGoUp() {
        assertEquals(3, progress.record(one, passed = true, points = one.threeStarPoints))
        assertTrue(progress.isUnlocked(2))
        assertFalse(progress.isUnlocked(3))
        assertEquals(1, progress.record(one, passed = true, points = 1))
        assertEquals(3, progress.stars(1))
        assertEquals(one.threeStarPoints, progress.bestPoints(1))
        assertEquals(3, progress.totalStars)
        // Saved, so a new LevelProgress on the same store sees it.
        assertEquals(3, LevelProgress(store).stars(1))
    }

    @Test
    fun finishCardsMatchTheResult() {
        val level = Level(4, "Test", 1, 1, 1, 1, twoStarPoints = 50_000, threeStarPoints = 50_000)
        val game = level.newGame(GridPhysics)
        assertNull(finishCard(game, level))
        game.aim(1.2f)
        game.fire()
        while (!game.phase.isFinished) game.step()
        val card = finishCard(game, level)
        assertEquals("LEVEL 4 CLEAR", card?.title)
        assertEquals(true, card?.won)
        assertEquals("★☆☆  ${game.points} PTS", card?.note)
        assertEquals("★★☆", starText(2))
        assertEquals("☆☆☆", starText(0))
        assertEquals("ROUND 3/8", roundLabel(3, 8))
        assertEquals("ROUND 7", roundLabel(7, null))
    }
}
