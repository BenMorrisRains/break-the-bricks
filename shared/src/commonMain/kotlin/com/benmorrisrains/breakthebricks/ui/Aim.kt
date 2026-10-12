package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.GameEngine
import com.benmorrisrains.breakthebricks.engine.RoundPhase
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Length of the aim line in cells. The line is short and straight and never shows a bounce
 * or trajectory preview (Ben's rule), so it has a fixed length whatever is in its way.
 */
const val AIM_LINE_CELLS = 2f

/** A point in world cells; y grows downward. */
data class WorldPoint(val x: Float, val y: Float)

/**
 * Aim angle from the launch point on the floor to a touch, in radians up from the positive
 * x axis and clamped to [minAngle, PI - minAngle]. Null when the touch is at or below the
 * floor, which cancels the shot.
 */
fun aimAngleTo(launchX: Float, floorY: Float, touchX: Float, touchY: Float, minAngle: Float): Float? {
    val up = floorY - touchY
    if (up <= 0f) return null
    return atan2(up, touchX - launchX).coerceIn(minAngle, PI.toFloat() - minAngle)
}

/**
 * Steering aim for a finger that pressed below the floor at [pressX] while the aim was
 * [pressAngle]: sliding right turns the aim right and sliding left turns it left, and one
 * full field width ([columns] cells) sweeps the whole allowed range. Clamped like [aimAngleTo].
 */
fun steerAngle(pressAngle: Float, pressX: Float, touchX: Float, columns: Int, minAngle: Float): Float {
    val range = PI.toFloat() - 2 * minAngle
    return (pressAngle - (touchX - pressX) / columns * range).coerceIn(minAngle, PI.toFloat() - minAngle)
}

/** Far end of the aim line drawn from the launch point. */
fun aimLineEnd(launchX: Float, floorY: Float, angle: Float, length: Float = AIM_LINE_CELLS): WorldPoint =
    WorldPoint(launchX + cos(angle) * length, floorY - sin(angle) * length)

/**
 * Drag-to-aim, two ways; lifting fires either way.
 * - Press above the floor: the aim points at the finger. Dragging to or below the floor
 *   cancels, and lifting there does nothing; dragging back above resumes.
 * - Press below the floor (Ben's playtest ask, so the finger doesn't cover the aim line):
 *   the aim line shows at once and sliding left or right steers it, see [steerAngle].
 *   Sliding up into the field cancels; sliding back below resumes.
 * Only acts while the engine is in [RoundPhase.Aiming]. Positions are in world cells.
 */
class AimGesture(private val engine: GameEngine) {
    /** True while the finger's position gives a valid aim and the aim line should show. */
    var isAiming: Boolean = false
        private set

    /** Where a steering press started and the aim at that moment; null for a press above the floor. */
    private var steerFrom: Pair<Float, Float>? = null

    fun press(x: Float, y: Float) {
        steerFrom = if (y > engine.config.rows) x to engine.aimAngle else null
        move(x, y)
    }

    fun move(x: Float, y: Float) {
        if (engine.phase != RoundPhase.Aiming) {
            isAiming = false
            return
        }
        val config = engine.config
        val steer = steerFrom
        val angle = if (steer != null) {
            val (pressX, pressAngle) = steer
            if (y < config.rows) null else steerAngle(pressAngle, pressX, x, config.columns, config.minAimAngle)
        } else {
            aimAngleTo(engine.launchX, config.rows.toFloat(), x, y, config.minAimAngle)
        }
        isAiming = angle != null
        if (angle != null) engine.aim(angle)
    }

    /** Lifts the finger. Returns true if this fired the round's balls. */
    fun release(): Boolean {
        val fires = isAiming && engine.phase == RoundPhase.Aiming
        isAiming = false
        if (fires) engine.fire()
        return fires
    }

    /** The gesture was interrupted (e.g. the pointer was lost); nothing fires. */
    fun cancel() {
        isAiming = false
    }
}
