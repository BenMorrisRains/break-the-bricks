package com.benmorrisrains.breakthebricks.ui

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The turning of the power-up carousel under the launch line (Ben, 2026-10-17): its icons sit on
 * a loop, a drag turns it, and on release it settles with one icon in the middle. Positions are
 * in slots: the icon at index i sits [offsetOf] slots right of the middle. Pure state, stepped
 * once per frame with [update], so it never touches the game.
 */
class CarouselWheel(private val settleRate: Float = 14f) {
    /** How far the wheel has turned, in slots; the icon at this index (wrapped) is in the middle. */
    var position = 0f
        private set
    private var target = 0f
    private var dragging = false

    /** Slots from the middle to the icon at [index] of [count], the short way round, in [-count/2, count/2). */
    fun offsetOf(index: Int, count: Int): Float {
        if (count <= 0) return 0f
        val half = count / 2f
        val raw = index - position
        return raw - count * floor((raw + half) / count)
    }

    /** The index of the icon nearest the middle. */
    fun centerIndex(count: Int): Int =
        if (count <= 0) 0 else position.roundToInt().mod(count)

    /** A drag moved the finger [slots] slots to the right; the icons follow it. */
    fun drag(slots: Float) {
        dragging = true
        position -= slots
        target = position
    }

    /** The finger let go: the wheel settles on the nearest icon. */
    fun release() {
        dragging = false
        target = position.roundToInt().toFloat()
    }

    /** Turns the short way until the icon at [index] of [count] is in the middle. */
    fun rotateTo(index: Int, count: Int) {
        if (count <= 0) return
        dragging = false
        target = (position + offsetOf(index, count)).roundToInt().toFloat()
    }

    /** The icon a tap [slotsFromMiddle] slots right of the middle lands on, or null between icons or off the ends. */
    fun indexAt(slotsFromMiddle: Float, count: Int): Int? {
        if (count <= 0) return null
        return (0 until count).firstOrNull { abs(offsetOf(it, count) - slotsFromMiddle) <= TAP_REACH }
    }

    /** Eases toward the settled spot; called once per frame. */
    fun update(elapsedSeconds: Float) {
        if (dragging || elapsedSeconds <= 0f) return
        val gap = target - position
        position = if (abs(gap) < 0.001f) target else target - gap * exp(-settleRate * elapsedSeconds)
    }

    companion object {
        /** How close to an icon's middle, in slots, a tap must be to land on it. */
        const val TAP_REACH = 0.45f
    }
}
