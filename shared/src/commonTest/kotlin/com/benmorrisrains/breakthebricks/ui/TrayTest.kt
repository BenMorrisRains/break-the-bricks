package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.BrickGame
import com.benmorrisrains.breakthebricks.engine.Drill
import com.benmorrisrains.breakthebricks.engine.GridPhysics
import com.benmorrisrains.breakthebricks.engine.PaidLevels
import com.benmorrisrains.breakthebricks.engine.RoundPhase
import com.benmorrisrains.breakthebricks.engine.Splitter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The HUD tray: in a level each power-up has one shot, armed and disarmed by tapping it. */
class TrayTest {
    private fun drillLevelGame() = PaidLevels.all.first { it.power == Drill }.newGame(GridPhysics)

    @Test
    fun inALevelEvenOnePowerUpCanBeTapped() {
        val game = drillLevelGame()
        assertTrue(trayPicks(game))
        tapTraySlot(game, Drill.id)
        assertEquals(Drill, game.selectedAbility)
        tapTraySlot(game, Drill.id)
        assertNull(game.selectedAbility, "a second tap disarms it")
    }

    @Test
    fun aSpentPowerUpStaysDimAndIgnoresTaps() {
        val game = drillLevelGame()
        tapTraySlot(game, Drill.id)
        game.aim(1.2f)
        game.fire()
        assertFalse(isSpent(game, Drill.id), "its own shot is still flying")
        while (game.phase != RoundPhase.Aiming && !game.phase.isFinished) game.step()
        assertTrue(isSpent(game, Drill.id))
        tapTraySlot(game, Drill.id)
        assertNull(game.selectedAbility)
        assertEquals(BOOST_TERMS_LEVEL, boostTerms(game))
    }

    @Test
    fun freePlayKeepsTheOldTray() {
        val one = BrickGame(seed = 1, physics = GridPhysics, abilities = listOf(Drill), earnable = emptyList())
        assertFalse(trayPicks(one))
        val two = BrickGame(seed = 1, physics = GridPhysics, abilities = listOf(Drill, Splitter), earnable = emptyList())
        assertTrue(trayPicks(two))
        tapTraySlot(two, Splitter.id)
        tapTraySlot(two, Splitter.id)
        assertEquals(Splitter, two.selectedAbility, "a second tap doesn't disarm in free play")
        assertEquals(BOOST_TERMS, boostTerms(two))
    }
}
