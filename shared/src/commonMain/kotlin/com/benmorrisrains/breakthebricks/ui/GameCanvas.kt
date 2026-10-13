package com.benmorrisrains.breakthebricks.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import com.benmorrisrains.breakthebricks.engine.Ball
import com.benmorrisrains.breakthebricks.engine.BallState
import com.benmorrisrains.breakthebricks.engine.GameConfig
import com.benmorrisrains.breakthebricks.engine.GameEngine
import com.benmorrisrains.breakthebricks.engine.Level
import com.benmorrisrains.breakthebricks.engine.RoundPhase
import kotlin.math.hypot

/**
 * The play field: neon bricks with their numbers, balls, the floor, the ball counter and the
 * short aim line. It only reads [engine] state; touches go to the engine through [AimGesture].
 * Redraws whenever [frame] changes, which [GameScreen] bumps once per display frame.
 * [onFire] runs when a release fires the balls, [onGameOverTap] on a tap after the game is over.
 * [level] is the level being played, or null in free play; it sets the finish card's text.
 * [effects] adds break sparks and the grenade blast, including its screen shake.
 */
@Composable
fun GameCanvas(
    engine: GameEngine,
    frame: LongState,
    flashes: BrickFlashes,
    abilityCue: AbilityCue,
    modifier: Modifier = Modifier,
    effects: CanvasEffects? = null,
    onFire: () -> Unit = {},
    onGameOverTap: () -> Unit = {},
    level: Level? = null,
) {
    val gesture = remember(engine) { AimGesture(engine) }
    val textMeasurer = rememberTextMeasurer()
    val labels = remember(textMeasurer) { LabelCache(textMeasurer) }
    val latestOnFire by rememberUpdatedState(onFire)
    val latestOnGameOverTap by rememberUpdatedState(onGameOverTap)
    val input = Modifier.pointerInput(engine) {
        aimInput(engine, gesture, onFire = { latestOnFire() }, onGameOverTap = { latestOnGameOverTap() })
    }
    Canvas(modifier.then(input)) {
        frame.longValue
        val layout = FieldLayout(engine.config, size.width, size.height)
        if (layout.isEmpty) return@Canvas
        labels.resize(layout.cell)

        drawRect(NeonPalette.Background)
        val shakeX = (effects?.shakeX() ?: 0f) * layout.cell
        val shakeY = (effects?.shakeY() ?: 0f) * layout.cell
        translate(shakeX, shakeY) {
            drawGrid(layout)
            drawBricks(engine, layout, flashes, labels)
            effects?.let { drawBrickGhosts(it, layout) }
            drawFloor(layout)
            if (gesture.isAiming && engine.phase == RoundPhase.Aiming) drawAimLine(engine, layout)
            drawBalls(engine, layout, abilityCue.isArmed(engine), effects?.time)
            drawBallCounter(engine, layout, labels)
            effects?.let { drawSparks(it, layout) }
        }
        finishCard(engine, level)?.let { drawFinishCard(it, layout, labels) }
    }
}

/**
 * Finger down or drag aims, lift fires; see [AimGesture] for the cancel rule. After a game over,
 * a press is a tap to move on to the results instead.
 */
private suspend fun PointerInputScope.aimInput(
    engine: GameEngine,
    gesture: AimGesture,
    onFire: () -> Unit,
    onGameOverTap: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (engine.phase.isFinished) {
            down.consume()
            onGameOverTap()
            return@awaitEachGesture
        }
        val layout = FieldLayout(engine.config, size.width.toFloat(), size.height.toFloat())
        if (layout.isEmpty) return@awaitEachGesture
        try {
            gesture.press(layout.toWorldX(down.position.x), layout.toWorldY(down.position.y))
            down.consume()
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUp()) {
                    change.consume()
                    if (gesture.release()) onFire()
                    break
                }
                gesture.move(layout.toWorldX(change.position.x), layout.toWorldY(change.position.y))
                change.consume()
            }
        } finally {
            // No-op after a release; drops the shot if the pointer was lost or the gesture cancelled.
            gesture.cancel()
        }
    }
}

private fun DrawScope.drawGrid(layout: FieldLayout) {
    val color = NeonPalette.Dim.copy(alpha = 0.35f)
    for (column in 0..layout.config.columns) {
        val x = layout.toScreenX(column.toFloat())
        drawLine(color, Offset(x, layout.originY), Offset(x, layout.floorY), strokeWidth = 1f)
    }
}

private fun DrawScope.drawBricks(
    engine: GameEngine,
    layout: FieldLayout,
    flashes: BrickFlashes,
    labels: LabelCache,
) {
    val cell = layout.cell
    val roundValue = engine.config.rowValues.valueFor(engine.round.coerceAtLeast(1))
    val inset = cell * 0.08f
    val side = Size(cell - inset * 2, cell - inset * 2)
    // Soft outer glow in two passes under a crisp 2 to 3 px outline.
    val wideGlow = Stroke(width = cell * 0.24f)
    val nearGlow = Stroke(width = cell * 0.13f)
    val outline = Stroke(width = cell * 0.05f)
    for (brick in engine.board.bricks()) {
        val base = NeonPalette.brickColor(brick.hits, roundValue)
        val flash = flashes.intensity(brick.column, brick.row)
        val color = if (flash > 0f) lerp(base, NeonPalette.Ink, flash * 0.7f) else base
        val topLeft = Offset(
            layout.toScreenX(brick.column.toFloat()) + inset,
            layout.toScreenY(brick.row.toFloat()) + inset,
        )
        drawRect(color.copy(alpha = 0.05f + 0.25f * flash), topLeft, side)
        drawRect(color.copy(alpha = 0.07f), topLeft, side, style = wideGlow)
        drawRect(color.copy(alpha = 0.16f), topLeft, side, style = nearGlow)
        drawRect(color, topLeft, side, style = outline)

        val text = formatBrickValue(brick.hits)
        val label = labels.get(this, text, cell * brickFontFraction(text))
        val center = Offset(topLeft.x + side.width / 2, topLeft.y + side.height / 2)
        drawText(label, NeonPalette.Ink, center - Offset(label.size.width / 2f, label.size.height / 2f))
    }
}

private fun DrawScope.drawFloor(layout: FieldLayout) {
    val y = layout.floorY
    val start = Offset(layout.originX, y)
    val end = Offset(layout.originX + layout.fieldWidth, y)
    drawLine(NeonPalette.Cyan.copy(alpha = 0.25f), start, end, strokeWidth = layout.cell * 0.12f)
    drawLine(NeonPalette.Cyan, start, end, strokeWidth = maxOf(2f, layout.cell * 0.03f))
}

/** Short and straight from the launch ball. Never a bounce or trajectory preview. */
private fun DrawScope.drawAimLine(engine: GameEngine, layout: FieldLayout) {
    val config = engine.config
    val startY = config.rows - config.ballRadius
    val end = aimLineEnd(engine.launchX, startY, engine.aimAngle)
    val from = Offset(layout.toScreenX(engine.launchX), layout.toScreenY(startY))
    val to = Offset(layout.toScreenX(end.x), layout.toScreenY(end.y))
    val r = config.ballRadius * layout.cell
    drawLine(NeonPalette.Ink.copy(alpha = 0.18f), from, to, strokeWidth = r * 1.6f, cap = StrokeCap.Round)
    drawLine(NeonPalette.Ink, from, to, strokeWidth = r * 0.45f, cap = StrokeCap.Round)
}

/**
 * Balls are white, drawn at their own size (the big ball is twice as big); a drilling ball has a
 * yellow ring. Once the splitter fires, the three balls of the split each get their own neon
 * color (see [splitBallTint]). While [grenadeArmed], the round's first ball is drawn as a hand
 * grenade, its fuse sparking by [effectsTime] once it flies.
 */
private fun DrawScope.drawBalls(engine: GameEngine, layout: FieldLayout, grenadeArmed: Boolean, effectsTime: Float?) {
    val config = engine.config
    val grenade = engine.balls.firstOrNull()?.takeIf { grenadeArmed }
    for ((index, ball) in engine.balls.withIndex()) {
        val isGrenade = ball === grenade
        val tint = splitBallTint(index, engine.ballCount, engine.balls.size)?.let(NeonPalette::tint)
        when (ball.state) {
            BallState.Flying -> drawBall(ball.x, ball.y, layout, config, trailOf = ball, isGrenade = isGrenade, sparkTime = effectsTime, size = ball.size, drilling = ball.pierce > 0, tint = tint)
            BallState.Landed -> drawBall(ball.x, ball.y, layout, config, size = ball.size, tint = tint)
            BallState.Waiting -> Unit
        }
    }
    // Balls still waiting to leave all sit on the launch point, so one is drawn for them.
    if (engine.phase == RoundPhase.Aiming || engine.balls.any { it.state == BallState.Waiting }) {
        val nextIsGrenade = grenadeArmed && (engine.phase == RoundPhase.Aiming || grenade?.state == BallState.Waiting)
        drawBall(engine.launchX, config.rows - config.ballRadius, layout, config, isGrenade = nextIsGrenade)
    }
}

private fun DrawScope.drawBall(
    x: Float,
    y: Float,
    layout: FieldLayout,
    config: GameConfig,
    trailOf: Ball? = null,
    isGrenade: Boolean = false,
    sparkTime: Float? = null,
    size: Float = 1f,
    drilling: Boolean = false,
    tint: Color? = null,
) {
    val r = config.ballRadius * size * layout.cell
    val center = Offset(layout.toScreenX(x), layout.toScreenY(y))
    val color = if (isGrenade) NeonPalette.Orange else tint ?: NeonPalette.Ink
    if (trailOf != null) {
        val speed = hypot(trailOf.vx, trailOf.vy)
        if (speed > 0f) {
            val tail = center - Offset(trailOf.vx / speed, trailOf.vy / speed) * (r * 3.5f)
            val brush = Brush.linearGradient(
                listOf(color.copy(alpha = 0f), color.copy(alpha = 0.35f)),
                start = tail,
                end = center,
            )
            drawLine(brush, tail, center, strokeWidth = r * 1.4f, cap = StrokeCap.Round)
        }
    }
    if (isGrenade) {
        drawGrenade(center, r, sparkTime = sparkTime)
    } else {
        drawCircle(color.copy(alpha = 0.18f), radius = r * 2f, center = center)
        drawCircle(color, radius = r, center = center)
        if (drilling) drawCircle(NeonPalette.Yellow, radius = r * 1.5f, center = center, style = Stroke(width = maxOf(1.5f, r * 0.35f)))
    }
}

/** "x12" under the floor by the launch point: balls left to fire this round. */
private fun DrawScope.drawBallCounter(engine: GameEngine, layout: FieldLayout, labels: LabelCache) {
    val count = when (engine.phase) {
        RoundPhase.Aiming -> engine.ballCount
        RoundPhase.Firing -> engine.balls.count { it.state == BallState.Waiting }
        else -> 0
    }
    if (count <= 0) return
    val label = labels.get(this, "x$count", layout.cell * 0.3f)
    val half = label.size.width / 2f
    val x = layout.toScreenX(engine.launchX).coerceIn(layout.originX + half, layout.originX + layout.fieldWidth - half)
    val y = layout.floorY + (layout.cell * FieldLayout.BELOW_FLOOR_CELLS - label.size.height) / 2f
    drawText(label, NeonPalette.Ink, Offset(x - half, y))
}

private fun DrawScope.drawFinishCard(card: FinishCard, layout: FieldLayout, labels: LabelCache) {
    drawRect(
        NeonPalette.Background.copy(alpha = 0.7f),
        Offset(layout.originX, layout.originY),
        Size(layout.fieldWidth, layout.floorY - layout.originY),
    )
    val label = labels.get(this, card.title, layout.cell * 0.55f)
    val center = Offset(layout.originX + layout.fieldWidth / 2, (layout.originY + layout.floorY) / 2)
    drawText(label, if (card.won) NeonPalette.Lime else NeonPalette.Magenta, center - Offset(label.size.width / 2f, label.size.height / 2f))
    var top = center.y + label.size.height / 2f + layout.cell * 0.3f
    card.note?.let { text ->
        val note = labels.get(this, text, layout.cell * 0.3f)
        drawText(note, NeonPalette.Yellow, Offset(center.x - note.size.width / 2f, top))
        top += note.size.height + layout.cell * 0.2f
    }
    val hint = labels.get(this, card.hint, layout.cell * 0.22f)
    drawText(hint, NeonPalette.Cyan, Offset(center.x - hint.size.width / 2f, top))
}

/**
 * Measured text per label, so dozens of brick numbers aren't re-measured every frame. Each
 * label always asks for the same size at a given cell size, so the cache resets when it changes.
 */
private class LabelCache(private val measurer: TextMeasurer) {
    private val layouts = HashMap<String, TextLayoutResult>()
    private var cell = 0f

    fun resize(cell: Float) {
        if (cell != this.cell || layouts.size > MAX_ENTRIES) layouts.clear()
        this.cell = cell
    }

    fun get(density: Density, text: String, fontPx: Float): TextLayoutResult = layouts.getOrPut(text) {
        val style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = with(density) { fontPx.toSp() },
        )
        measurer.measure(text, style)
    }

    private companion object {
        const val MAX_ENTRIES = 256
    }
}
