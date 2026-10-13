package com.benmorrisrains.breakthebricks.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class WagerTest {
    private val grenade = GrenadeBall.id

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
        assertEquals(WagerOutcome.Open, wager.outcome, "round 9 isn't enough")
        assertEquals(1, inventory.count(grenade))

        inventory = wager.onEvent(GameEvent.RoundStarted(10, 10), inventory)
        assertEquals(WagerOutcome.Won, wager.outcome)
        assertEquals(3, inventory.count(grenade), "stake back plus 1")

        inventory = wager.onEvent(GameEvent.RoundStarted(11, 11), inventory)
        inventory = wager.onEvent(GameEvent.GameOver(round = 11, points = 99), inventory)
        assertEquals(WagerOutcome.Won, wager.outcome, "a won wager stays won after game over")
        assertEquals(3, inventory.count(grenade), "paid out once")
    }

    @Test
    fun aWinNeverGoesPastNineCopies() {
        val wager = WagerGame(GrenadeBall)
        val inventory = wager.start(inventory(9))
        assertEquals(9, wager.onEvent(GameEvent.RoundStarted(10, 10), inventory).count(grenade))
    }

    @Test
    fun gameOverBeforeTheTargetLosesTheStake() {
        val wager = WagerGame(GrenadeBall)
        var inventory = wager.start(inventory(1))
        inventory = wager.onEvent(GameEvent.RoundStarted(7, 7), inventory)
        inventory = wager.onEvent(GameEvent.GameOver(round = 7, points = 40), inventory)
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
        assertEquals(1, wager.onEvent(GameEvent.RoundStarted(10, 10), inventory).count(grenade), "no late win")
    }

    @Test
    fun earningBanksACopyExceptForTheStakedAbility() {
        val free = WagerGame()
        var inventory = free.start(AbilityInventory())
        inventory = free.onEvent(GameEvent.AbilityEarned(grenade, 5), inventory)
        assertEquals(1, inventory.count(grenade))
        assertEquals(listOf(grenade), free.banked)
        free.abandon()
        assertEquals(WagerOutcome.Unstaked, free.outcome)

        val staked = WagerGame(GrenadeBall)
        inventory = staked.start(inventory)
        inventory = staked.onEvent(GameEvent.AbilityEarned(grenade, 5), inventory)
        assertEquals(0, inventory.count(grenade), "the wager decides the staked copy")
        assertEquals(emptyList(), staked.banked)
    }

    /** Plays [seed] to game over with the real engine, settling a staked grenade as the app does. */
    private fun playStaked(seed: Long, start: AbilityInventory): Triple<BrickGame, WagerGame, AbilityInventory> {
        val wager = WagerGame(GrenadeBall)
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
    fun realGamesSettleByTheRoundReached() {
        var wins = 0
        var losses = 0
        for (seed in 1L..40L) {
            val (game, wager, inventory) = playStaked(seed, inventory(3))
            assertTrue(game.activeAbilities.contains(GrenadeBall), "the staked grenade is active from round 1")
            if (game.round >= GrenadeBall.wagerTargetRound) {
                assertEquals(WagerOutcome.Won, wager.outcome, "seed $seed reached round ${game.round}")
                assertEquals(4, inventory.count(grenade), "seed $seed")
                wins++
            } else {
                assertEquals(WagerOutcome.Lost, wager.outcome, "seed $seed ended at round ${game.round}")
                assertEquals(2, inventory.count(grenade), "seed $seed")
                losses++
            }
        }
        assertTrue(wins > 0 && losses > 0, "both paths should come up: $wins wins, $losses losses")
    }

    @Test
    fun theSameSeedAndShotsSettleTheSameWay() {
        val first = playStaked(77, inventory(1))
        val second = playStaked(77, inventory(1))
        assertEquals(first.first.round, second.first.round)
        assertEquals(first.second.outcome, second.second.outcome)
        assertEquals(first.third, second.third)
    }
}
