package com.benmorrisrains.breakthebricks.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Ben, 2026-10-17: in a level each power-up acts on one shot only; using one every shot made levels too easy. */
class SingleUsePowerUpsTest {
    private fun playRound(game: BrickGame, angle: Float = 1.0f) {
        game.aim(angle)
        game.fire()
        var steps = 0
        while (game.phase != RoundPhase.Aiming && !game.phase.isFinished) {
            game.step()
            if (steps++ > 200_000) fail("round ${game.round} never ended")
        }
    }

    private fun drillLevel() = PaidLevels.all.first { it.power == Drill }

    @Test
    fun levelsUseSingleUsePowerUpsAndFreePlayDoesNot() {
        assertTrue(drillLevel().newGame(GridPhysics).singleUsePowerUps)
        assertFalse(BrickGame(seed = 1, physics = GridPhysics).singleUsePowerUps)
    }

    @Test
    fun nothingIsPickedUntilThePlayerPicksIt() {
        val game = drillLevel().newGame(GridPhysics)
        assertNull(game.selectedAbility)
        playRound(game)
        assertEquals(listOf<String?>(null), game.firedAbilities)
        assertTrue(game.spentAbilities.isEmpty(), "an unpicked power-up isn't used up")
    }

    @Test
    fun aPickedPowerUpActsOnOneShotThenIsSpent() {
        val game = drillLevel().newGame(GridPhysics)
        game.selectAbility(Drill.id)
        game.aim(1.2f)
        game.fire()
        assertEquals(setOf(Drill.id), game.spentAbilities)
        assertEquals(Drill, game.selectedAbility, "it stays picked while its shot flies")
        while (game.phase != RoundPhase.Aiming && !game.phase.isFinished) game.step()
        assertNull(game.selectedAbility, "unpicked once the shot is over")
        game.selectAbility(Drill.id)
        assertNull(game.selectedAbility, "a spent power-up can't be picked again")
        playRound(game)
        assertEquals(listOf<String?>(Drill.id, null), game.firedAbilities)
        assertEquals(listOf<Ability>(Drill), game.activeAbilities, "it stays in the tray, spent")
    }

    @Test
    fun aPickCanBeTakenBackBeforeShooting() {
        val game = drillLevel().newGame(GridPhysics)
        game.selectAbility(Drill.id)
        game.unselectAbility()
        assertNull(game.selectedAbility)
        playRound(game)
        assertTrue(game.spentAbilities.isEmpty())
    }

    @Test
    fun anAdBoostInALevelActsOnOneShot() {
        val game = FreeLevels[1].newGame(GridPhysics)
        assertTrue(game.boost(Splitter))
        playRound(game)
        assertEquals(setOf(Splitter.id), game.spentAbilities)
        assertNull(game.selectedAbility)
        assertFalse(game.boost(Splitter), "in a level the same power-up can't come back")
        playRound(game)
        assertEquals(listOf(Splitter.id, null), game.firedAbilities)
    }

    @Test
    fun freePlayKeepsItsPickEveryShot() {
        val game = BrickGame(seed = 3, physics = GridPhysics, abilities = listOf(Drill))
        assertEquals(Drill, game.selectedAbility)
        playRound(game)
        playRound(game)
        assertEquals(listOf<String?>(Drill.id, Drill.id), game.firedAbilities)
        assertTrue(game.spentAbilities.isEmpty())
        game.unselectAbility()
        assertEquals(Drill, game.selectedAbility, "free play has no empty pick")
    }

    @Test
    fun theSameShotsAndPicksReplayTheSameLevel() {
        val level = drillLevel()
        val angles = listOf(1.1f, 2.0f, 0.7f, 1.5f)
        val picks = listOf(null, Drill.id, null, Drill.id)
        val a = LevelBot.replay(level, angles, picks = picks)
        val b = LevelBot.replay(level, angles, picks = picks)
        assertEquals(a.points, b.points)
        assertEquals(a.firedAbilities, b.firedAbilities)
        assertEquals(listOf(null, Drill.id, null, null), a.firedAbilities, "the second pick of a spent power-up does nothing")
    }
}
