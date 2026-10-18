package com.benmorrisrains.breakthebricks.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class WagerTest {
    private val grenade = GrenadeBall.id
    private val target = GrenadeBall.wagerTargetRound

    private fun inventory(grenades: Int) = AbilityInventory().add(grenade, grenades)

    @Test
    fun copiesAreCountedAndCappedAtNine() {
        val inventory = AbilityInventory()
        assertTrue(inventory.isEmpty)
        assertEquals(3, inventory.add(grenade, 3).count(grenade))
        assertEquals(AbilityInventory.MAX_COPIES, inventory.add(grenade, 12).count(grenade))
        assertEquals(9, inventory.add(grenade, 9).add(grenade).count(grenade))
        assertTrue(inventory.add(grenade).remove(grenade).isEmpty, "the last copy taken out leaves nothing")
        assertEquals(0, inventory.remove(grenade).count(grenade), "never below zero")
    }

    @Test
    fun theFirstGameAndAnEmptyInventoryPlayFree() {
        assertFalse(Wagering.stakeRequired(firstGamePlayed = false, inventory = inventory(3)))
        assertFalse(Wagering.stakeRequired(firstGamePlayed = true, inventory = AbilityInventory()))
        assertTrue(Wagering.stakeRequired(firstGamePlayed = true, inventory = inventory(1)))
        assertEquals(listOf<Ability>(GrenadeBall), Wagering.stakeable(inventory(1)))
        assertEquals(emptyList(), Wagering.stakeable(AbilityInventory()))
    }

    @Test
    fun theStakeLeavesTheInventoryAsTheGameStarts() {
        val wager = WagerGame(GrenadeBall)
        assertEquals(WagerOutcome.Open, wager.outcome)
        assertEquals(1, wager.start(inventory(2)).count(grenade))
        assertFailsWith<IllegalArgumentException> { WagerGame(GrenadeBall).start(AbilityInventory()) }
    }

    @Test
    fun reachingTheTargetRoundWinsTheStakeBackPlusOne() {
        val wager = WagerGame(GrenadeBall)
        var inventory = wager.start(inventory(2))
        for (round in 1 until GrenadeBall.wagerTargetRound) {
            inventory = wager.onEvent(GameEvent.RoundStarted(round, round), inventory)
        }
        assertEquals(WagerOutcome.Open, wager.outcome, "one round short isn't enough")
        assertEquals(1, inventory.count(grenade))

        inventory = wager.onEvent(GameEvent.RoundStarted(target, target), inventory)
        assertEquals(WagerOutcome.Won, wager.outcome)
        assertEquals(3, inventory.count(grenade), "stake back plus 1")

        inventory = wager.onEvent(GameEvent.RoundStarted(target + 1, target + 1), inventory)
        inventory = wager.onEvent(GameEvent.GameOver(round = target + 1, points = 99), inventory)
        assertEquals(WagerOutcome.Won, wager.outcome, "a won wager stays won after game over")
        assertEquals(3, inventory.count(grenade), "paid out once")
    }

    @Test
    fun aWinNeverGoesPastNineCopies() {
        val wager = WagerGame(GrenadeBall)
        val inventory = wager.start(inventory(9))
        assertEquals(9, wager.onEvent(GameEvent.RoundStarted(target, target), inventory).count(grenade))
    }

    @Test
    fun gameOverBeforeTheTargetLosesTheStake() {
        val wager = WagerGame(GrenadeBall)
        var inventory = wager.start(inventory(1))
        inventory = wager.onEvent(GameEvent.RoundStarted(target - 1, target - 1), inventory)
        inventory = wager.onEvent(GameEvent.GameOver(round = target - 1, points = 40), inventory)
        assertEquals(WagerOutcome.Lost, wager.outcome)
        assertTrue(inventory.isEmpty)
    }

    @Test
    fun quittingCountsAsALoss() {
        val wager = WagerGame(GrenadeBall)
        val inventory = wager.start(inventory(2))
        wager.abandon()
        assertEquals(WagerOutcome.Lost, wager.outcome)
        assertEquals(1, inventory.count(grenade), "the stake was already taken out at the start")
        assertEquals(1, wager.onEvent(GameEvent.RoundStarted(target, target), inventory).count(grenade), "no late win")
    }

    @Test
    fun earningBanksACopyEvenOfTheStakedAbility() {
        val free = WagerGame()
        var inventory = free.start(AbilityInventory())
        inventory = free.onEvent(GameEvent.AbilityEarned(grenade, GrenadeBall.earnedAtRound), inventory)
        assertEquals(1, inventory.count(grenade))
        assertEquals(listOf(grenade), free.banked)
        free.abandon()
        assertEquals(WagerOutcome.Unstaked, free.outcome)

        val staked = WagerGame(GrenadeBall)
        inventory = staked.start(inventory)
        inventory = staked.onEvent(GameEvent.AbilityEarned(grenade, GrenadeBall.earnedAtRound), inventory)
        assertEquals(1, inventory.count(grenade), "the staked copy sits out, so the earned one is the player's")
        assertEquals(listOf(grenade), staked.banked)
    }

    /** An ability that does nothing but carry a wager target, so real games can be settled either way. */
    private class TargetAbility(override val wagerTargetRound: Int) : Ability {
        override val id = "target"
        override val earnedAtRound = Int.MAX_VALUE
        override fun onFirstHit(context: AbilityContext, column: Int, row: Int) = Unit
    }

    /** Plays [seed] to game over with the real engine, settling a wager on [stake] as the app does. */
    private fun playStaked(
        seed: Long,
        stake: Ability = GrenadeBall,
        start: AbilityInventory = AbilityInventory().add(stake.id, 3),
    ): Triple<BrickGame, WagerGame, AbilityInventory> {
        val wager = WagerGame(stake)
        var inventory = wager.start(start)
        val game = BrickGame(seed = seed, physics = GridPhysics, abilities = listOfNotNull(wager.stake))
        val angles = listOf(1.3f, 0.5f, 2.4f, 1.0f, 1.9f, 0.3f, 2.8f, 1.57f, 0.8f, 2.1f)
        var shot = 0
        var frames = 0
        while (game.phase != RoundPhase.GameOver) {
            if (game.phase == RoundPhase.Aiming) {
                game.aim(angles[shot++ % angles.size])
                game.fire()
            }
            game.advance(1f / 60)
            for (event in game.drainEvents()) inventory = wager.onEvent(event, inventory)
            if (++frames > 2_000_000) fail("game never ended")
        }
        return Triple(game, wager, inventory)
    }

    @Test
    fun aRealGameWinsAtTheTargetRoundAndLosesShortOfIt() {
        for (seed in 1L..10L) {
            val lastRound = playStaked(seed, TargetAbility(Int.MAX_VALUE)).first.round
            val reached = playStaked(seed, TargetAbility(lastRound))
            assertTrue(reached.first.activeAbilities.any { it.id == "target" }, "the stake is active from round 1")
            assertEquals(WagerOutcome.Won, reached.second.outcome, "seed $seed reached round $lastRound")
            assertEquals(4, reached.third.count("target"), "seed $seed")

            val short = playStaked(seed, TargetAbility(lastRound + 1))
            assertEquals(WagerOutcome.Lost, short.second.outcome, "seed $seed ended at round $lastRound")
            assertEquals(2, short.third.count("target"), "seed $seed")
        }
    }

    @Test
    fun aRealStakedGrenadeSettlesByItsOwnTarget() {
        val (game, wager, inventory) = playStaked(2026)
        val won = game.round >= GrenadeBall.wagerTargetRound
        assertEquals(if (won) WagerOutcome.Won else WagerOutcome.Lost, wager.outcome)
        // Reaching round 30 also earned a grenade copy, staked or not.
        val earned = if (game.round >= GrenadeBall.earnedAtRound) 1 else 0
        assertEquals((if (won) 4 else 2) + earned, inventory.count(grenade))
    }

    @Test
    fun theSameSeedAndShotsSettleTheSameWay() {
        val first = playStaked(77)
        val second = playStaked(77)
        assertEquals(first.first.round, second.first.round)
        assertEquals(first.second.outcome, second.second.outcome)
        assertEquals(first.third, second.third)
    }
}
