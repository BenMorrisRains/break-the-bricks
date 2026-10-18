package com.benmorrisrains.breakthebricks.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Ben, 2026-10-17: power-ups are limited in free play too; each copy acts on one shot. */
class LimitedPowerUpsTest {
    private fun freePlay(copies: Map<String, Int>, earnable: List<Ability> = emptyList(), seed: Long = 3) = BrickGame(
        seed = seed,
        physics = GridPhysics,
        abilities = Abilities.earnable.filter { it.id in copies },
        earnable = earnable,
        singleUsePowerUps = true,
        copies = copies,
    )

    private fun playRound(game: BrickGame, angle: Float = 1.0f): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        game.aim(angle)
        game.fire()
        var steps = 0
        while (game.phase != RoundPhase.Aiming && !game.phase.isFinished) {
            game.step()
            if (steps++ > 200_000) fail("round ${game.round} never ended")
        }
        events += game.drainEvents()
        return events
    }

    @Test
    fun theGameStartsWithTheGivenCopies() {
        val game = freePlay(mapOf(Drill.id to 3, Splitter.id to 1))
        assertEquals(3, game.copiesLeft(Drill.id))
        assertEquals(1, game.copiesLeft(Splitter.id))
        assertEquals(0, game.copiesLeft(BigBall.id))
        assertNull(game.selectedAbility, "nothing is picked until tapped")
        assertNull(BrickGame(seed = 1, physics = GridPhysics).copiesLeft(Drill.id), "unlimited games have no counts")
    }

    @Test
    fun eachShotWithAPickUsesOneCopyAndThePickLastsOneShot() {
        val game = freePlay(mapOf(Drill.id to 2))
        game.selectAbility(Drill.id)
        val first = playRound(game)
        assertTrue(GameEvent.PowerUpUsed(Drill.id) in first)
        assertEquals(1, game.copiesLeft(Drill.id))
        assertNull(game.selectedAbility, "a new shot starts with nothing picked")
        val second = playRound(game)
        assertTrue(second.none { it is GameEvent.PowerUpUsed }, "an unpicked shot uses nothing")
        game.selectAbility(Drill.id)
        playRound(game)
        assertEquals(0, game.copiesLeft(Drill.id))
        assertEquals(setOf(Drill.id), game.spentAbilities)
        game.selectAbility(Drill.id)
        assertNull(game.selectedAbility, "no copies left, so it can't be picked")
        assertEquals(listOf(Drill.id, null, Drill.id), game.firedAbilities)
    }

    @Test
    fun earningAddsACopyEvenToOneRunOut() {
        val game = freePlay(mapOf(BigBall.id to 1), earnable = listOf(BigBall))
        game.selectAbility(BigBall.id)
        playRound(game)
        assertEquals(0, game.copiesLeft(BigBall.id))
        while (game.round < BigBall.earnedAtRound && !game.phase.isFinished) playRound(game, 1.3f)
        assertEquals(1, game.copiesLeft(BigBall.id))
        assertFalse(BigBall.id in game.spentAbilities)
    }

    @Test
    fun copiesNeverPassTheCap() {
        val game = freePlay(mapOf(BigBall.id to 20), earnable = listOf(BigBall))
        assertEquals(AbilityInventory.MAX_COPIES, game.copiesLeft(BigBall.id))
        while (game.round < BigBall.earnedAtRound && !game.phase.isFinished) playRound(game, 1.3f)
        assertEquals(AbilityInventory.MAX_COPIES, game.copiesLeft(BigBall.id))
    }

    @Test
    fun anAdCopyIsUsedFirstAndSaysSo() {
        val game = freePlay(mapOf(Drill.id to 1))
        assertTrue(game.boost(Drill), "free play can add a copy of one it already has")
        assertFalse(game.boost(Drill), "already waiting for the next shot")
        val events = playRound(game)
        assertTrue(GameEvent.PowerUpUsed(Drill.id, boosted = true) in events)
        assertEquals(1, game.copiesLeft(Drill.id), "the player's own copy is still there")
        game.selectAbility(Drill.id)
        assertTrue(GameEvent.PowerUpUsed(Drill.id, boosted = false) in playRound(game))
        assertEquals(0, game.copiesLeft(Drill.id))
    }

    @Test
    fun aLevelStillGivesEachPowerUpOneShot() {
        val level = PaidLevels.all.first { it.power == BigBall }
        val game = level.newGame(GridPhysics, earnable = listOf(BigBall))
        assertEquals(1, game.copiesLeft(BigBall.id))
        while (game.round < BigBall.earnedAtRound && !game.phase.isFinished) playRound(game, 1.3f)
        assertEquals(1, game.copiesLeft(BigBall.id), "earning one it already lent adds nothing in a level")
        assertFalse(game.boost(BigBall))
    }

    @Test
    fun theSameSeedShotsAndPicksReplayTheSameGame() {
        fun play(): Pair<Long, List<String?>> {
            val game = freePlay(mapOf(Drill.id to 2, Splitter.id to 2), earnable = Abilities.earnable, seed = 41)
            val angles = listOf(1.2f, 0.5f, 2.3f, 1.6f, 0.9f, 2.0f)
            var shot = 0
            while (!game.phase.isFinished && shot < 30) {
                if (shot % 3 == 0) game.selectAbility(if (shot % 2 == 0) Drill.id else Splitter.id)
                playRound(game, angles[shot++ % angles.size])
            }
            return game.points to game.firedAbilities
        }
        assertEquals(play(), play())
    }
}
