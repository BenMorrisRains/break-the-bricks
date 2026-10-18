package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.BigBall
import com.benmorrisrains.breakthebricks.engine.BrickGame
import com.benmorrisrains.breakthebricks.engine.Drill
import com.benmorrisrains.breakthebricks.engine.GridPhysics
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The power-up carousel under the launch line (Ben, 2026-10-17). */
class CarouselWheelTest {
    private fun settle(wheel: CarouselWheel) = repeat(120) { wheel.update(1f / 60) }

    private fun assertNear(expected: Float, actual: Float) =
        assertTrue(abs(expected - actual) < 0.01f, "expected $expected but was $actual")

    @Test
    fun iconsSitOnALoopAroundTheMiddle() {
        val wheel = CarouselWheel()
        assertEquals(listOf(0f, 1f, 2f, -2f, -1f), (0 until 5).map { wheel.offsetOf(it, 5) })
        assertEquals(0, wheel.centerIndex(5))
    }

    @Test
    fun aDragTurnsItAndLettingGoSettlesOnTheNearestIcon() {
        val wheel = CarouselWheel()
        wheel.drag(-1.3f)
        assertNear(-1.3f, wheel.offsetOf(0, 4))
        wheel.release()
        settle(wheel)
        assertEquals(1, wheel.centerIndex(4))
        assertNear(0f, wheel.offsetOf(1, 4))
    }

    @Test
    fun itTurnsTheShortWayRound() {
        val wheel = CarouselWheel()
        wheel.rotateTo(4, 5)
        settle(wheel)
        assertEquals(4, wheel.centerIndex(5))
        assertNear(-1f, wheel.position)
    }

    @Test
    fun aTapFindsTheIconUnderIt() {
        val wheel = CarouselWheel()
        assertEquals(0, wheel.indexAt(0.2f, 3))
        assertEquals(1, wheel.indexAt(1.1f, 3))
        assertEquals(2, wheel.indexAt(-0.9f, 3))
        assertNull(wheel.indexAt(0.5f, 3), "between icons")
        assertNull(wheel.indexAt(0f, 0))
    }

    @Test
    fun theBadgeShowsCopiesLeftInLimitedGames() {
        val limited = limitedEngine(1, 1, com.benmorrisrains.breakthebricks.engine.AbilityInventory().add(Drill.id, 3))
        assertEquals("x3", copiesLabel(limited, Drill.id))
        assertTrue(trayPicks(limited), "even one limited power-up is tapped to pick it")
        tapTraySlot(limited, Drill.id)
        assertEquals(Drill, limited.selectedAbility)
        tapTraySlot(limited, Drill.id)
        assertNull(limited.selectedAbility, "a second tap unpicks it")
        val unlimited = BrickGame(seed = 1, physics = GridPhysics, abilities = listOf(BigBall))
        assertNull(copiesLabel(unlimited, BigBall.id))
    }
}
