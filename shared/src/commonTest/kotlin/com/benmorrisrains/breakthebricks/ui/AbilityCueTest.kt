package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.BigBall
import com.benmorrisrains.breakthebricks.engine.BrickGame
import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.engine.GridPhysics
import com.benmorrisrains.breakthebricks.engine.GrenadeBall
import com.benmorrisrains.breakthebricks.engine.Splitter
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AbilityCueTest {
    @Test
    fun noAbilityMeansNoGrenadeBall() {
        val engine = BrickGame(seed = 1, physics = GridPhysics, earnable = emptyList())
        assertFalse(AbilityCue().isArmed(engine))
    }

    @Test
    fun theGrenadeIsArmedUntilItExplodesAndRearmsNextRound() {
        val engine = BrickGame(seed = 1, physics = GridPhysics, abilities = listOf(GrenadeBall))
        val cue = AbilityCue()
        assertTrue(cue.isArmed(engine))
        cue.update(listOf(GameEvent.AbilityTriggered(GrenadeBall.id, 2, 3)))
        assertFalse(cue.isArmed(engine))
        cue.update(listOf(GameEvent.RoundStarted(6, 6)))
        assertTrue(cue.isArmed(engine))
    }

    @Test
    fun otherAbilitiesAreNotDrawnAsAGrenade() {
        val engine = BrickGame(seed = 1, physics = GridPhysics, abilities = listOf(BigBall), earnable = emptyList())
        assertFalse(AbilityCue().isArmed(engine))
    }

    @Test
    fun theGrenadeIsOnlyDrawnWhenItIsThePickedAbility() {
        val engine = BrickGame(seed = 1, physics = GridPhysics, abilities = listOf(GrenadeBall, Splitter), earnable = emptyList())
        val cue = AbilityCue()
        assertTrue(cue.isArmed(engine))
        engine.selectAbility(Splitter.id)
        assertFalse(cue.isArmed(engine))
    }
}
