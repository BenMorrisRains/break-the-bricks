package com.benmorrisrains.breakthebricks.ui

import androidx.compose.ui.graphics.Color

/**
 * The only place colors are defined. Neon, old-school arcade look on a near-black field
 * (Ben's call: not Triumph's colors). Starting values; tune by eye on device.
 */
object NeonPalette {
    val Background = Color(0xFF07070F)
    val Ink = Color(0xFFF5F7FF)
    val Cyan = Color(0xFF00F0FF)
    val Lime = Color(0xFF39FF14)
    val Yellow = Color(0xFFFFE600)
    val Orange = Color(0xFFFF8A00)
    val Magenta = Color(0xFFFF2BD6)
    val Purple = Color(0xFF9D4DFF)
    val Dim = Color(0xFF3A3A55)

    /** Brick outline colors from lowest to highest value. */
    val BrickTiers = listOf(Cyan, Lime, Yellow, Orange, Magenta)

    /**
     * Color for a brick worth [hits] when the newest row is worth [roundValue]:
     * the toughest bricks on the board always read as magenta.
     */
    fun brickColor(hits: Long, roundValue: Long): Color =
        BrickTiers[brickTier(hits, roundValue, BrickTiers.size)]

    /** The color for an effect's [Tint]. */
    fun tint(tint: Tint): Color = when (tint) {
        Tint.Ink -> Ink
        Tint.Cyan -> Cyan
        Tint.Lime -> Lime
        Tint.Yellow -> Yellow
        Tint.Orange -> Orange
        Tint.Magenta -> Magenta
        Tint.Purple -> Purple
    }
}
