package com.benmorrisrains.breakthebricks.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.benmorrisrains.breakthebricks.engine.GrenadeBall
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A neon hand grenade around [center]: a ribbed oval body, the fuse cap, the lever down its
 * side and the pull ring. Drawn about 2.5 times the ball's radius [r] so it reads at a glance;
 * the ball still collides as a plain ball of radius [r]. With [silhouette], it is one flat dim
 * shape (a locked ability). [sparkTime], when given, makes a spark flicker on the fuse.
 */
internal fun DrawScope.drawGrenade(center: Offset, r: Float, silhouette: Boolean = false, sparkTime: Float? = null) {
    val s = r * 2.5f
    val line = maxOf(1.5f, s * 0.09f)
    fun c(color: Color) = if (silhouette) NeonPalette.Dim else color
    if (!silhouette) drawCircle(NeonPalette.Orange.copy(alpha = 0.16f), radius = s * 1.35f, center = center)

    // Body: an upright oval, a little below center so the cap sits on top.
    val a = s * 0.75f
    val b = s * 0.9f
    val body = Offset(center.x, center.y + s * 0.15f)
    drawOval(c(NeonPalette.Lime).copy(alpha = if (silhouette) 1f else 0.3f), Offset(body.x - a, body.y - b), Size(a * 2, b * 2))
    if (!silhouette) {
        // Pineapple ribs, cut to the oval's outline.
        val rib = NeonPalette.Lime.copy(alpha = 0.75f)
        for (dy in listOf(-0.35f, 0.35f)) {
            val half = a * sqrt(1f - dy * dy)
            val ribY = body.y + dy * b
            drawLine(rib, Offset(body.x - half, ribY), Offset(body.x + half, ribY), strokeWidth = line * 0.7f)
        }
        for (dx in listOf(-0.45f, 0f, 0.45f)) {
            val half = b * sqrt(1f - dx * dx)
            val ribX = body.x + dx * a
            drawLine(rib, Offset(ribX, body.y - half), Offset(ribX, body.y + half), strokeWidth = line * 0.7f)
        }
    }
    drawOval(c(NeonPalette.Lime), Offset(body.x - a, body.y - b), Size(a * 2, b * 2), style = Stroke(width = line))

    // Fuse cap on top.
    val capTop = Offset(center.x - s * 0.28f, body.y - b - s * 0.32f)
    val capSize = Size(s * 0.56f, s * 0.36f)
    drawRect(NeonPalette.Dim, capTop, capSize)
    drawRect(c(NeonPalette.Ink), capTop, capSize, style = Stroke(width = line * 0.8f))

    // Lever (spoon) from the cap down the right side.
    val lever = Path().apply {
        moveTo(capTop.x + capSize.width, capTop.y + capSize.height * 0.3f)
        quadraticTo(body.x + a * 1.25f, capTop.y, body.x + a * 1.1f, body.y)
    }
    drawPath(lever, c(NeonPalette.Ink), style = Stroke(width = line, cap = StrokeCap.Round))

    // Pull ring on the left of the cap.
    drawCircle(
        c(NeonPalette.Yellow),
        radius = s * 0.22f,
        center = Offset(capTop.x - s * 0.12f, capTop.y + capSize.height * 0.2f),
        style = Stroke(width = line * 0.8f),
    )

    if (sparkTime != null && !silhouette) {
        // A lit fuse: a little star on the cap that flickers in size.
        val flicker = 0.6f + 0.4f * sin(sparkTime * 47f) * sin(sparkTime * 29f)
        val spark = Offset(center.x, capTop.y - s * 0.05f)
        val reach = s * 0.32f * flicker
        drawCircle(NeonPalette.Yellow.copy(alpha = 0.45f), radius = reach, center = spark, blendMode = BlendMode.Plus)
        for (i in 0 until 4) {
            val angle = i * PI.toFloat() / 4f + sparkTime * 9f
            val d = Offset(cos(angle), sin(angle)) * reach
            drawLine(NeonPalette.Ink, spark - d, spark + d, strokeWidth = line * 0.5f, cap = StrokeCap.Round)
        }
    }
}

/**
 * An ability's icon filling a square of [side] px around [center], for the power-up carousel and the
 * locked-ability previews. With [silhouette], a flat dim shape.
 */
internal fun DrawScope.drawAbilityIcon(abilityId: String, center: Offset, side: Float, silhouette: Boolean = false) {
    when (abilityId) {
        // The grenade with its glow spans about 2.2 times its body scale; see drawGrenade.
        GrenadeBall.id -> drawGrenade(center, r = side / 2.4f / 2.5f, silhouette = silhouette)
        AbilityIds.BIG_BALL -> drawBigBall(center, side * 0.34f, silhouette)
        AbilityIds.SPLITTER -> drawSplitter(center, side, silhouette)
        AbilityIds.DRILL -> drawDrill(center, side * 0.22f, silhouette)
        else -> {
            val color = if (silhouette) NeonPalette.Dim else NeonPalette.Purple
            drawCircle(color.copy(alpha = 0.3f), radius = side * 0.45f, center = center)
            drawCircle(color, radius = side * 0.28f, center = center)
        }
    }
}

/** One frame of an ability's short looping preview clip, [time] seconds in, filling the draw area. */
internal fun DrawScope.drawAbilityPreview(abilityId: String, time: Float) {
    when (abilityId) {
        GrenadeBall.id -> drawGrenadePreview(time)
        AbilityIds.BIG_BALL, AbilityIds.SPLITTER, AbilityIds.DRILL -> drawPlaceholderPreview(abilityId, time)
        else -> {
            val bob = sin(time * 3f) * size.minDimension * 0.05f
            drawAbilityIcon(abilityId, Offset(size.width / 2, size.height / 2 + bob), size.minDimension * 0.6f)
        }
    }
}

/** The grenade flies up into a tough brick and blows it away. */
private fun DrawScope.drawGrenadePreview(time: Float) {
    val t = time % PREVIEW_LOOP
    val cell = minOf(size.width / 3f, size.height / 3.2f)
    val brickSide = cell * 0.9f
    val brickCenter = Offset(size.width / 2, size.height * 0.1f + brickSide / 2)
    val brickTopLeft = brickCenter - Offset(brickSide / 2, brickSide / 2)
    val r = cell * 0.13f

    // The brick: there until the blast, then fading back in for the next loop.
    val brickAlpha = when {
        t < PREVIEW_HIT -> 1f
        t < PREVIEW_RESPAWN -> 0f
        else -> (t - PREVIEW_RESPAWN) / (PREVIEW_LOOP - PREVIEW_RESPAWN)
    }
    if (brickAlpha > 0f) {
        val color = NeonPalette.Magenta.copy(alpha = brickAlpha)
        drawRect(color.copy(alpha = 0.08f * brickAlpha), brickTopLeft, Size(brickSide, brickSide))
        drawRect(color.copy(alpha = 0.2f * brickAlpha), brickTopLeft, Size(brickSide, brickSide), style = Stroke(width = cell * 0.15f))
        drawRect(color, brickTopLeft, Size(brickSide, brickSide), style = Stroke(width = cell * 0.05f))
    }

    if (t < PREVIEW_HIT) {
        // The grenade flying up with its short orange trail.
        val startY = size.height - r * 3f
        val endY = brickCenter.y + brickSide / 2 + r * 2f
        val y = startY + (endY - startY) * (t / PREVIEW_HIT)
        val center = Offset(size.width / 2, y)
        drawLine(NeonPalette.Orange.copy(alpha = 0.35f), center + Offset(0f, r * 4f), center, strokeWidth = r * 1.4f, cap = StrokeCap.Round)
        drawGrenade(center, r, sparkTime = time)
        return
    }

    val e = t - PREVIEW_HIT
    if (e < 0.09f) {
        // The brick flashing white.
        drawRect(NeonPalette.Ink, brickTopLeft, Size(brickSide, brickSide))
    }
    if (e < 0.6f) {
        val k = e / 0.6f
        val fade = 1f - k
        val ease = 1f - (1f - k) * (1f - k)
        if (e < 0.15f) drawCircle(NeonPalette.Ink.copy(alpha = 0.8f * (1f - e / 0.15f)), radius = cell * 0.5f, center = brickCenter, blendMode = BlendMode.Plus)
        drawCircle(NeonPalette.Yellow.copy(alpha = fade), radius = cell * (0.4f + 1.3f * ease), center = brickCenter, style = Stroke(width = cell * 0.06f * fade + 1f))
        for (i in 0 until PREVIEW_SHRAPNEL) {
            val angle = i * 2f * PI.toFloat() / PREVIEW_SHRAPNEL + 0.3f
            val dir = Offset(cos(angle), sin(angle))
            val head = brickCenter + dir * (cell * (0.3f + 1.6f * ease))
            val tail = head - dir * (cell * 0.3f * fade)
            val color = if (i % 3 == 0) NeonPalette.Yellow else NeonPalette.Orange
            drawLine(color.copy(alpha = fade), tail, head, strokeWidth = cell * 0.05f, cap = StrokeCap.Round)
        }
        // Shards falling away.
        for (i in 0 until PREVIEW_SHARDS) {
            val angle = i * 2f * PI.toFloat() / PREVIEW_SHARDS + 1.1f
            val shard = brickCenter + Offset(cos(angle), sin(angle)) * (cell * 0.9f * ease) + Offset(0f, cell * 1.2f * k * k)
            val side = cell * 0.14f
            drawRect((if (i % 2 == 0) NeonPalette.Ink else NeonPalette.Orange).copy(alpha = fade), shard - Offset(side / 2, side / 2), Size(side, side))
        }
    }
}

/**
 * Placeholder looks for the big ball, splitter and drill until their full showcase effects
 * follow the engine work: simple neon shapes, nothing more.
 */
private fun DrawScope.drawBigBall(center: Offset, r: Float, silhouette: Boolean) {
    val color = if (silhouette) NeonPalette.Dim else NeonPalette.Ink
    if (!silhouette) drawCircle(NeonPalette.Cyan.copy(alpha = 0.25f), radius = r * 1.5f, center = center)
    drawCircle(color, radius = r, center = center)
}

/** Three small balls fanning out from one point. */
private fun DrawScope.drawSplitter(center: Offset, side: Float, silhouette: Boolean) {
    val color = if (silhouette) NeonPalette.Dim else NeonPalette.Cyan
    val r = side * 0.12f
    val origin = center + Offset(0f, side * 0.28f)
    for (angle in listOf(-0.6f, 0f, 0.6f)) {
        val dir = Offset(sin(angle), -cos(angle))
        val ball = origin + dir * (side * 0.5f)
        drawLine(color.copy(alpha = 0.5f), origin, ball, strokeWidth = r * 0.6f, cap = StrokeCap.Round)
        drawCircle(color, radius = r, center = ball)
    }
}

/** A ball with a drill point on top. */
private fun DrawScope.drawDrill(center: Offset, r: Float, silhouette: Boolean) {
    val color = if (silhouette) NeonPalette.Dim else NeonPalette.Yellow
    val ball = center + Offset(0f, r * 0.8f)
    val tip = Path().apply {
        moveTo(ball.x - r, ball.y)
        lineTo(ball.x, ball.y - r * 3f)
        lineTo(ball.x + r, ball.y)
        close()
    }
    drawPath(tip, color.copy(alpha = if (silhouette) 1f else 0.35f))
    drawPath(tip, color, style = Stroke(width = maxOf(1.5f, r * 0.25f)))
    drawCircle(color, radius = r, center = ball)
}

/**
 * A simple loop for the placeholder abilities: the ball flies up a column of bricks. The big
 * ball pops the first brick, the splitter splits into three on it, the drill goes through three.
 */
private fun DrawScope.drawPlaceholderPreview(abilityId: String, time: Float) {
    val t = time % PREVIEW_LOOP
    val cell = minOf(size.width / 3f, size.height / 4f)
    val brickSide = cell * 0.85f
    val x = size.width / 2
    val rows = if (abilityId == AbilityIds.DRILL) 3 else 1
    val topY = size.height * 0.08f
    val k = (t / PREVIEW_HIT).coerceAtMost(1f)
    val startY = size.height - cell * 0.4f
    val firstHitY = topY + rows * cell
    // How far up the ball has got; the drill keeps going through its bricks.
    val reach = if (abilityId == AbilityIds.DRILL) topY else firstHitY
    val y = startY + (reach - startY) * k
    for (i in 0 until rows) {
        val rowY = topY + i * cell
        val broken = y <= rowY + cell
        val alpha = when {
            !broken -> 1f
            t > PREVIEW_RESPAWN -> (t - PREVIEW_RESPAWN) / (PREVIEW_LOOP - PREVIEW_RESPAWN)
            else -> 0f
        }
        if (alpha <= 0f) continue
        val topLeft = Offset(x - brickSide / 2, rowY + (cell - brickSide) / 2)
        drawRect(NeonPalette.Lime.copy(alpha = alpha), topLeft, Size(brickSide, brickSide), style = Stroke(width = cell * 0.05f))
    }
    if (t >= PREVIEW_RESPAWN) return
    val r = cell * if (abilityId == AbilityIds.BIG_BALL) 0.24f else 0.12f
    if (abilityId == AbilityIds.SPLITTER && t > PREVIEW_HIT) {
        val e = (t - PREVIEW_HIT) / (PREVIEW_RESPAWN - PREVIEW_HIT)
        for (angle in listOf(-0.7f, 0f, 0.7f)) {
            val dir = Offset(sin(angle), cos(angle))
            drawCircle(NeonPalette.Cyan.copy(alpha = 1f - e), radius = r, center = Offset(x, firstHitY) + dir * (cell * 2.2f * e))
        }
        return
    }
    val ballY = if (t > PREVIEW_HIT) reach + (startY - reach) * ((t - PREVIEW_HIT) / (PREVIEW_RESPAWN - PREVIEW_HIT)) else y
    drawAbilityIcon(abilityId, Offset(x, ballY), r * if (abilityId == AbilityIds.BIG_BALL) 2.9f else 4.5f)
}

private const val PREVIEW_LOOP = 1.8f
private const val PREVIEW_HIT = 0.6f
private const val PREVIEW_RESPAWN = 1.45f
private const val PREVIEW_SHRAPNEL = 12
private const val PREVIEW_SHARDS = 8
