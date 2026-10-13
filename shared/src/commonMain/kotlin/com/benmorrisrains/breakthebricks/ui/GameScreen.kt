package com.benmorrisrains.breakthebricks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benmorrisrains.breakthebricks.engine.GameEngine
import com.benmorrisrains.breakthebricks.engine.GameEvent
import com.benmorrisrains.breakthebricks.engine.Level

/** Longest frame fed to the engine, so a stall (app in background, debugger) doesn't jump the game. */
private const val MAX_FRAME_SECONDS = 0.1f

/**
 * The game: HUD on top, a thin divider, then the [GameCanvas]. Drives [engine] once per display
 * frame (60 or 120 Hz) and hands each frame's time and drained events to [onFrame] for sound and
 * haptics. [onFire] runs when a release fires the balls; [onGameOverTap] when the field is tapped
 * after a game over. While [paused] the engine isn't advanced; [onPause] adds a pause button to
 * the HUD. [level] is the level being played, or null in free play. [hudRank] gives the rank
 * and the bar to the next rank for the points scored so far.
 */
@Composable
fun GameScreen(
    engine: GameEngine,
    modifier: Modifier = Modifier,
    bestPoints: Long = 0L,
    onFrame: (elapsedSeconds: Float, events: List<GameEvent>) -> Unit = { _, _ -> },
    onFire: () -> Unit = {},
    onGameOverTap: () -> Unit = {},
    paused: Boolean = false,
    onPause: (() -> Unit)? = null,
    level: Level? = null,
    hudRank: (points: Long) -> HudRank = { HudRank(rank = 1, progress = 0f) },
) {
    val frame = remember { mutableLongStateOf(0L) }
    val flashes = remember(engine) { BrickFlashes() }
    val abilityCue = remember(engine) { AbilityCue() }
    val showcase = remember(engine) { AbilityShowcase() }
    val latestOnFrame by rememberUpdatedState(onFrame)
    LaunchedEffect(engine, paused) {
        if (paused) return@LaunchedEffect
        // Restarted on resume, so the time spent paused is never fed to the engine.
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val elapsed = ((now - last) / 1_000_000_000f).coerceIn(0f, MAX_FRAME_SECONDS)
                last = now
                // An earning card holds the game still for its moment; the effects keep playing.
                if (!showcase.holdsPlay) engine.advance(elapsed)
                val events = engine.drainEvents()
                flashes.update(elapsed, events)
                abilityCue.update(events)
                showcase.update(elapsed, events, engine)
                latestOnFrame(elapsed, events)
                frame.longValue++
            }
        }
    }
    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            GameHud(engine, frame, bestPoints, onPause = onPause, showcase = showcase)
            RankBar(engine, frame, hudRank)
            Box(Modifier.fillMaxWidth().height(1.dp).background(NeonPalette.Purple.copy(alpha = 0.5f)))
            GameCanvas(
                engine = engine,
                frame = frame,
                flashes = flashes,
                abilityCue = abilityCue,
                modifier = Modifier.fillMaxWidth().weight(1f),
                effects = showcase.effects,
                onFire = onFire,
                onGameOverTap = onGameOverTap,
                level = level,
            )
        }
        EarnedCardOverlay(engine, showcase, frame)
    }
}

/**
 * Points in large arcade digits at top center, the round on the left (in a level, the rows
 * still to come, see [roundLabel]), the best on the right. With [onPause], a pause button sits before the round.
 * With [showcase], the active abilities' tray sits right of the points.
 */
@Composable
fun GameHud(
    engine: GameEngine,
    frame: LongState,
    bestPoints: Long,
    modifier: Modifier = Modifier,
    onPause: (() -> Unit)? = null,
    showcase: AbilityShowcase? = null,
) {
    // Re-read the engine each frame but only recompose when the shown values change.
    val points by remember(engine) { derivedStateOf { frame.longValue; engine.points } }
    val round by remember(engine) { derivedStateOf { frame.longValue; engine.round } }
    Box(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            if (onPause != null) PauseButton(onPause)
            BasicText(text = roundLabel(round, engine.levelRows), style = arcadeStyle(NeonPalette.Purple, 14.sp))
        }
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            BasicText(text = formatPoints(points), style = arcadeStyle(NeonPalette.Ink, 34.sp))
            if (showcase != null) AbilityTray(engine, showcase, frame, Modifier.padding(start = 6.dp))
        }
        BasicText(
            text = "BEST ${formatPoints(maxOf(bestPoints, points))}",
            modifier = Modifier.align(Alignment.CenterEnd),
            style = arcadeStyle(NeonPalette.Purple, 14.sp),
        )
    }
}

/** The player's rank on the left and a neon bar of points to the next rank beside it. */
@Composable
fun RankBar(engine: GameEngine, frame: LongState, hudRank: (points: Long) -> HudRank, modifier: Modifier = Modifier) {
    val latestHudRank by rememberUpdatedState(hudRank)
    val shown by remember(engine) { derivedStateOf { frame.longValue; latestHudRank(engine.points) } }
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text = "RANK ${shown.rank}", style = arcadeStyle(NeonPalette.Cyan, 12.sp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(6.dp).background(NeonPalette.Dim)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(shown.progress.coerceIn(0f, 1f)).background(NeonPalette.Lime))
        }
    }
}

/** Two neon bars in a square outline, big enough to hit without aiming. */
@Composable
private fun PauseButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = Modifier
            .padding(end = 10.dp)
            .size(36.dp)
            .border(2.dp, NeonPalette.Purple, shape)
            .clickable(role = Role.Button, onClickLabel = "Pause", onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(2) { Box(Modifier.size(width = 5.dp, height = 16.dp).background(NeonPalette.Purple)) }
    }
}
