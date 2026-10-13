package com.benmorrisrains.breakthebricks.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/** One-game boosts, such as a rewarded ad's power-up: on from the next shot, never banked. */
class BoostTest {
    private fun playRound(game: BrickGame, angle: Float = 1.0f, eachStep: () -> Unit = {}) {
        game.aim(angle)
        game.fire()
        var steps = 0
        while (game.phase != RoundPhase.Aiming && !game.phase.isFinished) {
            game.step()
            eachStep()
            if (steps++ > 200_000) fail("round ${game.round} never ended")
        }
    }

    private fun newGame(seed: Long = 7) = BrickGame(seed = seed, physics = GridPhysics)

    @Test
    fun aBoostStartsOnTheNextShot() {
        val game = newGame()
        assertTrue(game.boost(BigBall))
        assertTrue(game.activeAbilities.isEmpty(), "nothing changes until the player shoots")
        game.fire()
        assertEquals(listOf<Ability>(BigBall), game.activeAbilities)
        assertEquals(listOf(0 to BigBall.id), game.boosts)
    }

    @Test
    fun theBoostedFirstBallIsBig() {
        val game = newGame()
        game.boost(BigBall)
        game.fire()
        game.step()
        assertTrue(game.balls.first().size > 1f, "bigger than a normal ball")
    }

    @Test
    fun aBoostTakenMidRoundWaitsForTheNextRound() {
        val game = newGame()
        game.fire()
        game.step()
        assertTrue(game.boost(Drill))
        assertTrue(game.activeAbilities.isEmpty())
        while (game.phase != RoundPhase.Aiming) game.step()
        assertTrue(game.activeAbilities.isEmpty(), "still waiting while aiming")
        game.fire()
        assertEquals(listOf<Ability>(Drill), game.activeAbilities)
        assertEquals(listOf(1 to Drill.id), game.boosts)
    }

    @Test
    fun anAbilityAlreadyOnCantBeBoostedTwice() {
        val staked = BrickGame(seed = 7, physics = GridPhysics, abilities = listOf(Splitter))
        assertFalse(staked.boost(Splitter))
        val game = newGame()
        assertTrue(game.boost(GrenadeBall))
        assertFalse(game.boost(GrenadeBall), "already waiting for the next shot")
        game.fire()
        assertFalse(game.boost(GrenadeBall))
        assertTrue(game.boost(BigBall), "a different power-up is still allowed by the engine")
    }

    @Test
    fun aFinishedGameTakesNoBoost() {
        val game = newGame()
        while (!game.phase.isFinished) playRound(game, angle = 1.4f)
        assertFalse(game.boost(BigBall))
    }

    @Test
    fun aBoostIsNotAnEarnedAbility() {
        val game = newGame()
        game.drainEvents()
        game.boost(BigBall)
        playRound(game)
        assertTrue(game.drainEvents().none { it is GameEvent.AbilityEarned }, "a boost banks nothing")
    }

    /** Earned at round 2, so a game always gets there. */
    private object EarlyBall : Ability {
        override val id = "early_ball"
        override val earnedAtRound = 2
        override val wagerTargetRound = 7
    }

    @Test
    fun earningABoostedAbilityStillReportsItOnce() {
        val game = BrickGame(seed = 7, physics = GridPhysics, earnable = listOf(EarlyBall))
        game.boost(EarlyBall)
        playRound(game)
        assertEquals(2, game.round)
        assertEquals(listOf(GameEvent.AbilityEarned(EarlyBall.id, 2)), game.drainEvents().filterIsInstance<GameEvent.AbilityEarned>())
        assertEquals(1, game.activeAbilities.count { it.id == EarlyBall.id })
    }

    @Test
    fun theSameSeedShotsAndBoostsReplayTheSameGame() {
        val angles = listOf(1.0f, 2.1f, 1.4f, 0.8f, 1.9f, 1.2f, 1.6f, 2.4f)
        fun play(): BrickGame {
            val game = newGame(seed = 42)
            for ((shot, angle) in angles.withIndex()) {
                if (game.phase.isFinished) break
                if (shot == 2) game.boost(Splitter)
                if (shot == 4) game.boost(Drill)
                playRound(game, angle)
            }
            return game
        }
        val a = play()
        val b = play()
        assertEquals(a.points, b.points)
        assertEquals(a.round, b.round)
        assertEquals(a.steps, b.steps)
        assertEquals(a.boosts, b.boosts)
        assertEquals(a.board.bricks().map { Triple(it.column, it.row, it.hits) }.toList(), b.board.bricks().map { Triple(it.column, it.row, it.hits) }.toList())
    }
}
