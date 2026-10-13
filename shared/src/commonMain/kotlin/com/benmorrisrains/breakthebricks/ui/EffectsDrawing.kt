package com.benmorrisrains.breakthebricks.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.hypot

/** Part of a shockwave's life during which its center glows white. */
private const val CORE_FLASH = 0.35f

/** The bricks a grenade just hit, flashing white in their cells. Drawn over the bricks. */
internal fun DrawScope.drawBrickGhosts(effects: CanvasEffects, layout: FieldLayout) {
    val cell = layout.cell
    val inset = cell * 0.08f
    val side = Size(cell - inset * 2, cell - inset * 2)
    for (ghost in effects.ghosts) {
        val color = NeonPalette.tint(ghost.tint)
        val topLeft = Offset(
            layout.toScreenX(ghost.column.toFloat()) + inset,
            layout.toScreenY(ghost.row.toFloat()) + inset,
        )
        // Swells a little as it flashes, right before it shatters.
        val grow = cell * 0.06f * (1f - ghost.fade)
        val swollen = Size(side.width + grow * 2, side.height + grow * 2)
        val at = topLeft - Offset(grow, grow)
        drawRect(color.copy(alpha = 0.9f), at, swollen)
        drawRect(color.copy(alpha = 0.3f), at, swollen, style = Stroke(width = cell * 0.24f))
    }
}

/**
 * Sparks, shrapnel, shockwave rings and the blast flash, over everything else on the field.
 * Additive blending makes overlapping sparks glow brighter, like neon.
 */
internal fun DrawScope.drawSparks(effects: CanvasEffects, layout: FieldLayout) {
    val cell = layout.cell
    for (wave in effects.shockwaves) {
        val center = Offset(layout.toScreenX(wave.x), layout.toScreenY(wave.y))
        val radius = wave.radius * cell
        val fade = 1f - wave.progress
        if (wave.progress < CORE_FLASH) {
            // A white-hot core at the blast point for the first instant.
            val core = 1f - wave.progress / CORE_FLASH
            drawCircle(NeonPalette.Ink.copy(alpha = 0.7f * core), radius = cell * 0.5f * (0.5f + 0.5f * core), center = center, blendMode = BlendMode.Plus)
        }
        drawCircle(
            NeonPalette.Orange.copy(alpha = 0.35f * fade),
            radius = radius,
            center = center,
            style = Stroke(width = cell * 0.3f * fade + 1f),
            blendMode = BlendMode.Plus,
        )
        drawCircle(
            NeonPalette.Yellow.copy(alpha = fade),
            radius = radius,
            center = center,
            style = Stroke(width = cell * 0.06f * fade + 1f),
        )
    }
    for (p in effects.particles) {
        val color = NeonPalette.tint(p.tint)
        val center = Offset(layout.toScreenX(p.x), layout.toScreenY(p.y))
        val size = p.size * cell
        when (p.shape) {
            ParticleShape.Pixel -> {
                val half = size / 2f
                drawRect(
                    color.copy(alpha = 0.3f * p.fade),
                    center - Offset(size, size),
                    Size(size * 2, size * 2),
                    blendMode = BlendMode.Plus,
                )
                drawRect(color.copy(alpha = p.fade), center - Offset(half, half), Size(size, size))
            }
            ParticleShape.Streak -> {
                val speed = hypot(p.vx, p.vy)
                if (speed <= 0f) continue
                // Longer while it is fast, shrinking to a dot as drag slows it.
                val length = (speed * 0.035f).coerceAtMost(0.45f) * cell
                val tail = center - Offset(p.vx / speed, p.vy / speed) * length
                drawLine(color.copy(alpha = 0.35f * p.fade), tail, center, strokeWidth = size * 2.2f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
                drawLine(color.copy(alpha = p.fade), tail, center, strokeWidth = size, cap = StrokeCap.Round)
            }
        }
    }
    if (effects.flash > 0f) {
        // The whole field lights up for a blink, brightest at the start.
        val alpha = 0.28f * effects.flash * effects.flash
        drawRect(
            NeonPalette.Ink.copy(alpha = alpha),
            Offset(layout.originX, layout.originY),
            Size(layout.fieldWidth, layout.floorY - layout.originY),
        )
    }
}
