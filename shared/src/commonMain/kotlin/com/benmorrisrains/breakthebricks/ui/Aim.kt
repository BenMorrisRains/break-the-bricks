package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.GameEngine
import com.benmorrisrains.breakthebricks.engine.RoundPhase
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
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
 * How far in cells a finger must pull from where it pressed below the floor before the
 * slingshot aims, so a plain tap there never fires.
 */
const val PULL_DEAD_ZONE_CELLS = 0.25f

/**
 * Slingshot aim for a finger that pressed below the floor at ([anchorX], [anchorY]) and is
 * now at ([touchX], [touchY]): the ball goes the opposite way to the pull, so pulling down
 * and left aims up and right. Clamped like [aimAngleTo]. Null inside the dead zone or when
 * the pull isn't downward, which cancels the shot.
 */
fun pullBackAngle(anchorX: Float, anchorY: Float, touchX: Float, touchY: Float, minAngle: Float): Float? {
    val up = touchY - anchorY
    val across = anchorX - touchX
    if (up <= 0f || hypot(across, up) < PULL_DEAD_ZONE_CELLS) return null
    return atan2(up, across).coerceIn(minAngle, PI.toFloat() - minAngle)
}

/** Far end of the aim line drawn from the launch point. */
fun aimLineEnd(launchX: Float, floorY: Float, angle: Float, length: Float = AIM_LINE_CELLS): WorldPoint =
    WorldPoint(launchX + cos(angle) * length, floorY - sin(angle) * length)

/**
 * Drag-to-aim, two ways; lifting fires either way.
 * - Press above the floor: the aim points at the finger. Dragging to or below the floor
 *   cancels, and lifting there does nothing; dragging back above resumes.
 * - Press below the floor (Ben's playtest ask, so the finger doesn't cover the aim line):
 *   slingshot, see [pullBackAngle]. Pulling back up past the press point cancels.
 * Only acts while the engine is in [RoundPhase.Aiming]. Positions are in world cells.
 */
class AimGesture(private val engine: GameEngine) {
    /** True while the finger's position gives a valid aim and the aim line should show. */
    var isAiming: Boolean = false
        private set

    /** Where a slingshot press started, or null when the press was above the floor. */
    private var anchor: WorldPoint? = null

    fun press(x: Float, y: Float) {
        anchor = if (y > engine.config.rows) WorldPoint(x, y) else null
        move(x, y)
    }

    fun move(x: Float, y: Float) {
        if (engine.phase != RoundPhase.Aiming) {
            isAiming = false
            return
        }
        val config = engine.config
        val start = anchor
        val angle = if (start != null) {
            pullBackAngle(start.x, start.y, x, y, config.minAimAngle)
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
