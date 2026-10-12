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
    fun steeringRightTurnsTheAimRightAndOneFieldWidthSweepsTheWholeRange() {
        val up = PI.toFloat() / 2
        assertEquals(up, steerAngle(up, 3f, 3f, config.columns, min), 1e-5f)
        assertTrue(steerAngle(up, 3f, 4f, config.columns, min) < up)
        assertTrue(steerAngle(up, 3f, 2f, config.columns, min) > up)
        assertEquals(min, steerAngle(PI.toFloat() - min, 0f, config.columns.toFloat(), config.columns, min), 1e-5f)
    }

    @Test
    fun steeringIsClampedToTheAllowedRange() {
        assertEquals(min, steerAngle(1f, 0f, 50f, config.columns, min), 1e-5f)
        assertEquals(PI.toFloat() - min, steerAngle(1f, 50f, 0f, config.columns, min), 1e-5f)
    }

    @Test
    fun pressingBelowTheFloorShowsTheAimLineAtOnceAndSlidingSteersIt() {
        val engine = newGame()
        val gesture = AimGesture(engine)
        gesture.press(3f, floor + 0.5f)
        assertTrue(gesture.isAiming)
        assertEquals(PI.toFloat() / 2, engine.aimAngle, 1e-5f)
        gesture.move(4f, floor + 0.8f)
        assertTrue(engine.aimAngle < PI.toFloat() / 2, "sliding right aims right")
        assertTrue(gesture.release())
        assertEquals(RoundPhase.Firing, engine.phase)
    }

    @Test
    fun aTapBelowTheFloorFiresAtTheCurrentAim() {
        val engine = newGame()
        val gesture = AimGesture(engine)
        engine.aim(1f)
        gesture.press(5f, floor + 0.5f)
        assertTrue(gesture.release())
        assertEquals(1f, engine.aimAngle, 1e-5f)
        assertEquals(RoundPhase.Firing, engine.phase)
    }

    @Test
    fun steeringStartsFromTheCurrentAim() {
        val engine = newGame()
        val gesture = AimGesture(engine)
        engine.aim(1f)
        gesture.press(5f, floor + 0.5f)
        gesture.move(4f, floor + 0.5f)
        assertTrue(engine.aimAngle > 1f, "sliding left from an aim of 1 rad turns it left")
    }

    @Test
    fun slidingUpIntoTheFieldCancelsAndSlidingBackResumes() {
        val engine = newGame()
        val gesture = AimGesture(engine)
        gesture.press(3f, floor + 0.5f)
        gesture.move(3f, floor - 1f)
        assertFalse(gesture.isAiming)
        gesture.move(3f, floor + 0.2f)
        assertTrue(gesture.isAiming)
        gesture.move(3f, floor - 1f)
        assertFalse(gesture.release())
        assertEquals(RoundPhase.Aiming, engine.phase)
    }
}
