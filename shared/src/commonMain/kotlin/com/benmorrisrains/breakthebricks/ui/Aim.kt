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

/** Far end of the aim line drawn from the launch point. */
fun aimLineEnd(launchX: Float, floorY: Float, angle: Float, length: Float = AIM_LINE_CELLS): WorldPoint =
    WorldPoint(launchX + cos(angle) * length, floorY - sin(angle) * length)

/**
 * Drag-to-aim. Pressing or dragging points the aim at the finger; lifting fires. Dragging to
 * or below the floor cancels, and lifting there does nothing; dragging back above resumes.
 * Only acts while the engine is in [RoundPhase.Aiming]. Positions are in world cells.
 */
class AimGesture(private val engine: GameEngine) {
    /** True while a finger is down above the floor and the aim line should show. */
    var isAiming: Boolean = false
        private set

    fun press(x: Float, y: Float) = move(x, y)

    fun move(x: Float, y: Float) {
        if (engine.phase != RoundPhase.Aiming) {
            isAiming = false
            return
        }
        val config = engine.config
        val angle = aimAngleTo(engine.launchX, config.rows.toFloat(), x, y, config.minAimAngle)
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
