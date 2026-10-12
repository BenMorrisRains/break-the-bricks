package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.BrickGame
import com.benmorrisrains.breakthebricks.engine.GameConfig
import com.benmorrisrains.breakthebricks.engine.GridPhysics
import com.benmorrisrains.breakthebricks.engine.RoundPhase
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AimTest {
    private val config = GameConfig()
    private val floor = config.rows.toFloat()
    private val min = config.minAimAngle

    private fun newGame() = BrickGame(config, seed = 2026, physics = GridPhysics)

    @Test
    fun touchStraightAboveAimsStraightUp() {
        assertEquals(PI.toFloat() / 2, aimAngleTo(3.5f, floor, 3.5f, 2f, min)!!, 1e-5f)
    }

    @Test
    fun shallowTouchesClampToTheMinimumAngleOnEitherSide() {
        assertEquals(min, aimAngleTo(3.5f, floor, 7f, floor - 0.01f, min)!!, 1e-5f)
        assertEquals(PI.toFloat() - min, aimAngleTo(3.5f, floor, 0f, floor - 0.01f, min)!!, 1e-5f)
    }

    @Test
    fun touchAtOrBelowTheFloorCancels() {
        assertNull(aimAngleTo(3.5f, floor, 2f, floor, min))
        assertNull(aimAngleTo(3.5f, floor, 2f, floor + 0.5f, min))
    }

    @Test
    fun aimLineIsShortAndStraightWhateverTheAngle() {
        for (angle in listOf(min, 0.8f, PI.toFloat() / 2, 2.5f, PI.toFloat() - min)) {
            val end = aimLineEnd(3.5f, floor, angle)
            assertEquals(AIM_LINE_CELLS, hypot(end.x - 3.5f, end.y - floor), 1e-4f)
            assertTrue(end.y < floor)
        }
    }

    @Test
    fun pressAndReleaseFiresTowardTheFinger() {
        val engine = newGame()
        val gesture = AimGesture(engine)
        gesture.press(engine.launchX, 3f)
        assertTrue(gesture.isAiming)
        assertEquals(PI.toFloat() / 2, engine.aimAngle, 1e-5f)
        assertTrue(gesture.release())
        assertEquals(RoundPhase.Firing, engine.phase)
        assertFalse(gesture.isAiming)
    }

    @Test
    fun dragFollowsTheFingerUntilRelease() {
        val engine = newGame()
        val gesture = AimGesture(engine)
        gesture.press(engine.launchX, 3f)
        gesture.move(engine.launchX + 2f, floor - 2f)
        assertEquals(PI.toFloat() / 4, engine.aimAngle, 1e-5f)
    }

    @Test
    fun releasingBelowTheFloorDoesNotFire() {
        val engine = newGame()
        val gesture = AimGesture(engine)
        gesture.press(engine.launchX, 3f)
        gesture.move(engine.launchX, floor + 0.3f)
        assertFalse(gesture.isAiming)
        assertFalse(gesture.release())
        assertEquals(RoundPhase.Aiming, engine.phase)
    }

    @Test
    fun draggingBackAboveTheFloorResumesAiming() {
        val engine = newGame()
        val gesture = AimGesture(engine)
        gesture.press(engine.launchX, 3f)
        gesture.move(engine.launchX, floor + 0.3f)
        gesture.move(engine.launchX - 1f, 4f)
        assertTrue(gesture.release())
    }

    @Test
    fun pullingStraightDownFromBelowTheFloorAimsStraightUp() {
        assertEquals(PI.toFloat() / 2, pullBackAngle(2f, 9.5f, 2f, 10.5f, min)!!, 1e-5f)
    }

    @Test
    fun pullingDownAndLeftAimsUpAndRight() {
        assertEquals(PI.toFloat() / 4, pullBackAngle(3f, 9.5f, 2f, 10.5f, min)!!, 1e-5f)
        assertEquals(3 * PI.toFloat() / 4, pullBackAngle(3f, 9.5f, 4f, 10.5f, min)!!, 1e-5f)
    }

    @Test
    fun aTinyOrUpwardPullDoesNotAim() {
        assertNull(pullBackAngle(3f, 9.5f, 3.1f, 9.6f, min))
        assertNull(pullBackAngle(3f, 9.5f, 2f, 9.2f, min))
        assertNull(pullBackAngle(3f, 9.5f, 2f, 9.5f, min))
    }

    @Test
    fun slingshotFromBelowTheFloorFiresOppositeThePull() {
        val engine = newGame()
        val gesture = AimGesture(engine)
        // Pressing below the floor doesn't aim until the finger pulls past the dead zone.
        gesture.press(1f, floor + 0.5f)
        assertFalse(gesture.isAiming)
        gesture.move(0f, floor + 1.5f)
        assertTrue(gesture.isAiming)
        assertEquals(PI.toFloat() / 4, engine.aimAngle, 1e-5f)
        assertTrue(gesture.release())
        assertEquals(RoundPhase.Firing, engine.phase)
    }

    @Test
    fun aTapBelowTheFloorDoesNotFire() {
        val engine = newGame()
        val gesture = AimGesture(engine)
        gesture.press(1f, floor + 0.5f)
        assertFalse(gesture.release())
        assertEquals(RoundPhase.Aiming, engine.phase)
    }

    @Test
    fun pullingBackUpPastThePressPointCancelsTheSlingshot() {
        val engine = newGame()
        val gesture = AimGesture(engine)
        gesture.press(1f, floor + 0.5f)
        gesture.move(0f, floor + 1.5f)
        gesture.move(1f, floor + 0.2f)
        assertFalse(gesture.isAiming)
        assertFalse(gesture.release())
        assertEquals(RoundPhase.Aiming, engine.phase)
    }

    @Test
    fun inputIsIgnoredWhileBallsAreInFlight() {
        val engine = newGame()
        val gesture = AimGesture(engine)
        gesture.press(engine.launchX, 3f)
        gesture.release()
        val angle = engine.aimAngle
        gesture.press(0f, 8f)
        assertFalse(gesture.isAiming)
        assertEquals(angle, engine.aimAngle)
        assertFalse(gesture.release())
    }

    @Test
    fun cancelDropsTheShot() {
        val engine = newGame()
        val gesture = AimGesture(engine)
        gesture.press(engine.launchX, 3f)
        gesture.cancel()
        assertFalse(gesture.release())
        assertEquals(RoundPhase.Aiming, engine.phase)
    }
}
