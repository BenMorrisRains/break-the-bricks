package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.Drill
import com.benmorrisrains.breakthebricks.engine.FreeLevels
import com.benmorrisrains.breakthebricks.engine.GridPhysics
import com.benmorrisrains.breakthebricks.engine.Level
import com.benmorrisrains.breakthebricks.engine.PaidLevels
import com.benmorrisrains.breakthebricks.engine.RoundPhase
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
        assertEquals(30, progress.levels.size)
        assertTrue(progress.isUnlocked(1))
        assertFalse(progress.isUnlocked(2))
        assertEquals(0, progress.totalStars)
    }

    @Test
    fun aFailedAttemptOpensNothing() {
        assertEquals(0, progress.record(one, passed = false, round = 3, points = 500))
        assertFalse(progress.isUnlocked(2))
        assertEquals(500, progress.bestPoints(1))
    }

    @Test
    fun passingOpensTheNextLevelAndStarsOnlyGoUp() {
        assertEquals(3, progress.record(one, passed = true, round = one.threeStarRounds, points = 40))
        assertTrue(progress.isUnlocked(2))
        assertFalse(progress.isUnlocked(3))
        assertEquals(1, progress.record(one, passed = true, round = one.twoStarRounds + 1, points = 30))
        assertEquals(3, progress.stars(1))
        assertEquals(40, progress.bestPoints(1))
        assertEquals(3, progress.totalStars)
        // Saved, so a new LevelProgress on the same store sees it.
        assertEquals(3, LevelProgress(store).stars(1))
    }

    @Test
    fun paidLevelsStayLockedUntilTheyAreOpened() {
        for (number in 1..10) progress.record(FreeLevels[number], passed = true, round = 1, points = 1)
        assertTrue(progress.isUnlocked(10))
        assertFalse(progress.isUnlocked(11), "level 11 is paid and nothing has opened it")
        assertTrue(progress.isPaidLocked(11))
        assertFalse(progress.isPaidLocked(10))

        var open = false
        val paid = LevelProgress(store, paidLevelsOpen = { open })
        assertFalse(paid.isUnlocked(PaidLevels[11]))
        open = true
        assertTrue(paid.isUnlocked(PaidLevels[11]), "opened, and level 10 is passed")
        assertFalse(paid.isPaidLocked(11))
        assertFalse(paid.isUnlocked(12), "level 11 isn't passed yet")
    }

    @Test
    fun levelIntrosShowBeforeTheFirstShotAndNameALentPowerUp() {
        val drillLevel = PaidLevels.all.first { it.power == Drill }
        val game = drillLevel.newGame(GridPhysics)
        val intro = levelIntro(game, drillLevel)
        assertEquals(IntroCard("LEVEL ${drillLevel.number}", drillLevel.name.uppercase(), "TAP THE DRILL BALL TO USE IT: ONE SHOT THIS LEVEL"), intro)
        assertEquals("BREAK EVERY BRICK", levelIntro(one.newGame(GridPhysics), one)?.hint)
        assertNull(levelIntro(game, null), "no intro in free play")
        game.aim(1.2f)
        game.fire()
        assertNull(levelIntro(game, drillLevel), "gone once the first shot is away")
    }

    @Test
    fun finishCardsMatchTheResult() {
        // One row of one brick; 3 stars only for clearing it in round 1.
        val level = Level(4, "Test", 1, 1, 1, 1, twoStarRounds = 1, threeStarRounds = 1)
        val game = level.newGame(GridPhysics)
        assertNull(finishCard(game, level))
        val angles = listOf(1.2f, 1.9f, 0.8f, 1.5f, 2.3f, 0.5f)
        var shot = 0
        while (!game.phase.isFinished) {
            game.aim(angles[shot++ % angles.size])
            game.fire()
            while (game.phase != RoundPhase.Aiming && !game.phase.isFinished) game.step()
        }
        val card = finishCard(game, level)
        if (game.phase == RoundPhase.LevelComplete) {
            assertEquals("LEVEL 4 CLEAR", card?.title)
            assertEquals(true, card?.won)
            assertEquals("${starText(level.stars(true, game.round))}  IN ${game.round} ROUNDS", card?.note)
        } else {
            assertEquals("GAME OVER", card?.title)
            assertEquals("BREAK EVERY BRICK", card?.note)
        }
        assertEquals("★★☆", starText(2))
        assertEquals("☆☆☆", starText(0))
        assertEquals("ROW 3/8", roundLabel(3, 8))
        assertEquals("CLEAR IT!", roundLabel(9, 8))
        assertEquals("ROUND 7", roundLabel(7, null))
    }
}
