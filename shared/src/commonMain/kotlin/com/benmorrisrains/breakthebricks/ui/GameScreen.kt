package com.benmorrisrains.breakthebricks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benmorrisrains.breakthebricks.engine.GameEngine
import com.benmorrisrains.breakthebricks.engine.GameEvent

/** Longest frame fed to the engine, so a stall (app in background, debugger) doesn't jump the game. */
private const val MAX_FRAME_SECONDS = 0.1f

/**
 * The game: HUD on top, a thin divider, then the [GameCanvas]. Drives [engine] once per display
 * frame (60 or 120 Hz) and hands each frame's time and drained events to [onFrame] for sound and
 * haptics. [onFire] runs when a release fires the balls; [onGameOverTap] when the field is tapped
 * after a game over.
 */
@Composable
fun GameScreen(
    engine: GameEngine,
    modifier: Modifier = Modifier,
    bestPoints: Long = 0L,
    onFrame: (elapsedSeconds: Float, events: List<GameEvent>) -> Unit = { _, _ -> },
    onFire: () -> Unit = {},
    onGameOverTap: () -> Unit = {},
) {
    val frame = remember { mutableLongStateOf(0L) }
    val flashes = remember(engine) { BrickFlashes() }
    val latestOnFrame by rememberUpdatedState(onFrame)
    LaunchedEffect(engine) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val elapsed = ((now - last) / 1_000_000_000f).coerceIn(0f, MAX_FRAME_SECONDS)
                last = now
                engine.advance(elapsed)
                val events = engine.drainEvents()
                flashes.update(elapsed, events)
                latestOnFrame(elapsed, events)
                frame.longValue++
            }
        }
    }
    Column(modifier) {
        GameHud(engine, frame, bestPoints)
        Box(Modifier.fillMaxWidth().height(1.dp).background(NeonPalette.Purple.copy(alpha = 0.5f)))
        GameCanvas(
            engine = engine,
            frame = frame,
            flashes = flashes,
            modifier = Modifier.fillMaxWidth().weight(1f),
            onFire = onFire,
            onGameOverTap = onGameOverTap,
        )
    }
}

/** Points in large arcade digits at top center, the round on the left, the best on the right. */
@Composable
fun GameHud(engine: GameEngine, frame: LongState, bestPoints: Long, modifier: Modifier = Modifier) {
    // Re-read the engine each frame but only recompose when the shown values change.
    val points by remember(engine) { derivedStateOf { frame.longValue; engine.points } }
    val round by remember(engine) { derivedStateOf { frame.longValue; engine.round } }
    Box(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
        BasicText(
            text = "ROUND $round",
            modifier = Modifier.align(Alignment.CenterStart),
            style = hudStyle(NeonPalette.Purple, 14.sp),
        )
        BasicText(
            text = formatPoints(points),
            modifier = Modifier.align(Alignment.Center),
            style = hudStyle(NeonPalette.Ink, 34.sp),
        )
        BasicText(
            text = "BEST ${formatPoints(maxOf(bestPoints, points))}",
            modifier = Modifier.align(Alignment.CenterEnd),
            style = hudStyle(NeonPalette.Purple, 14.sp),
        )
    }
}

private fun hudStyle(color: Color, size: TextUnit) = TextStyle(
    color = color,
    fontSize = size,
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    shadow = Shadow(color = color.copy(alpha = 0.6f), blurRadius = 14f),
)
