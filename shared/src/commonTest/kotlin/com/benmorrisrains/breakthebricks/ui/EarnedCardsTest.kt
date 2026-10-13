package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.Ability
import com.benmorrisrains.breakthebricks.engine.AbilityContext
import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.engine.GrenadeBall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EarnedCardsTest {
    private val earned = listOf(GameEvent.AbilityEarned(GrenadeBall.id, 5))

    @Test
    fun aCardFlipsInHoldsPlayForAboutASecondThenDropsIntoTheTray() {
        val cards = EarnedCards()
        cards.update(0f, earned)
        val card = cards.current!!
        assertEquals(GrenadeBall.id, card.abilityId)
        assertTrue(cards.holdsPlay)
        assertFalse(cards.hasLanded(GrenadeBall.id))
        assertEquals(0f, card.flip)

        cards.update(EarnedCards.FLIP_IN_SECONDS, emptyList())
        assertEquals(1f, card.flip, 1e-4f)
        assertEquals(0f, card.drop)

        cards.update(EarnedCards.HOLD_SECONDS + 0.01f, emptyList())
        assertFalse(cards.holdsPlay)
        assertTrue(card.drop > 0f)

        cards.update(EarnedCards.DROP_SECONDS, emptyList())
        assertNull(cards.current)
        assertTrue(cards.hasLanded(GrenadeBall.id))
        assertTrue(EarnedCards.DROP_START in 0.8f..1.5f)
    }

    @Test
    fun aTapSendsTheCardStraightToTheTray() {
        val cards = EarnedCards()
        cards.update(0f, earned)
        cards.skip()
        assertFalse(cards.holdsPlay)
        assertEquals(0f, cards.current!!.drop)
    }

    @Test
    fun cardsEarnedTogetherShowOneAfterAnother() {
        val cards = EarnedCards()
        cards.update(0f, earned + GameEvent.AbilityEarned("laser", 5))
        assertEquals(GrenadeBall.id, cards.current!!.abilityId)
        assertFalse(cards.hasLanded("laser"))
        cards.update(EarnedCards.TOTAL_SECONDS, emptyList())
        assertEquals("laser", cards.current!!.abilityId)
        assertTrue(cards.hasLanded(GrenadeBall.id))
    }

    @Test
    fun theTrayIconPulsesWhenItsAbilityFires() {
        val pulses = AbilityPulses(durationSeconds = 0.5f)
        assertEquals(0f, pulses.pulse(GrenadeBall.id))
        pulses.update(0f, listOf(GameEvent.AbilityTriggered(GrenadeBall.id, 0, 0)))
        assertEquals(1f, pulses.pulse(GrenadeBall.id))
        pulses.update(0.25f, emptyList())
        assertEquals(0.5f, pulses.pulse(GrenadeBall.id), 1e-4f)
        pulses.update(0.3f, emptyList())
        assertEquals(0f, pulses.pulse(GrenadeBall.id))
    }

    @Test
    fun lockedAbilitiesAreTheOnesTheGameDidNotReach() {
        val big = TestAbility("big", 5)
        val laser = TestAbility("laser", 10)
        val earnable = listOf(laser, big)
        assertEquals(listOf(big, laser), lockedAbilities(reachedRound = 3, earnable = earnable))
        assertEquals(listOf(laser), lockedAbilities(reachedRound = 5, earnable = earnable))
        assertTrue(lockedAbilities(reachedRound = 12, earnable = earnable).isEmpty())
    }

    @Test
    fun abilitiesHavePlayerFacingNames() {
        assertEquals("BIG BALL", abilityName(AbilityIds.BIG_BALL))
        assertEquals("SPLITTER BALL", abilityName(AbilityIds.SPLITTER))
        assertEquals("DRILL BALL", abilityName(AbilityIds.DRILL))
        assertEquals("GRENADE BALL", abilityName(GrenadeBall.id))
        assertEquals("GHOST BALL", abilityName(AbilityIds.GHOST))
        assertEquals("TIME FREEZE", abilityName(AbilityIds.TIME_FREEZE))
        assertEquals("5-WAY SPLITTER", abilityName(AbilityIds.SPLITTER_FIVE))
        assertEquals("LASER BALL", abilityName("laser"))
    }

    private class TestAbility(override val id: String, override val earnedAtRound: Int) : Ability {
        override val wagerTargetRound = earnedAtRound * 2
        override fun onFirstHit(context: AbilityContext, column: Int, row: Int) = Unit
    }
}
