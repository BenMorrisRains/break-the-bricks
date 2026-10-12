package com.benmorrisrains.breakthebricks.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benmorrisrains.breakthebricks.engine.Level

private const val TILES_PER_ROW = 2

/**
 * The 10 free levels as neon tiles with their stars, plus free play (the endless game).
 * A locked level shows dim and can't be tapped until the one before it is passed.
 */
@Composable
fun LevelSelectScreen(
    progress: LevelProgress,
    onLevel: (Level) -> Unit,
    onFreePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText("LEVELS", style = neonText(NeonPalette.Ink, 30.sp))
        val total = progress.levels.size * 3
        BasicText("★ ${progress.totalStars}/$total", style = neonText(NeonPalette.Yellow, 16.sp))
        for (row in progress.levels.chunked(TILES_PER_ROW)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (level in row) {
                    LevelTile(level, progress, onLevel, Modifier.weight(1f))
                }
                // Keep a short last row's tiles the same width.
                repeat(TILES_PER_ROW - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        NeonButton("FREE PLAY", NeonPalette.Purple, onFreePlay, Modifier.fillMaxWidth())
    }
}

@Composable
private fun LevelTile(level: Level, progress: LevelProgress, onLevel: (Level) -> Unit, modifier: Modifier) {
    val unlocked = progress.isUnlocked(level.number)
    val stars = progress.stars(level.number)
    val color = when {
        !unlocked -> NeonPalette.Dim
        stars == 3 -> NeonPalette.Lime
        stars > 0 -> NeonPalette.Cyan
        else -> NeonPalette.Magenta
    }
    Box(
        modifier
            .aspectRatio(1.4f)
            .border(2.dp, color, RoundedCornerShape(8.dp))
            .clickable(enabled = unlocked) { onLevel(level) }
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText("${level.number}", style = neonText(color, 28.sp))
            BasicText(level.name.uppercase(), style = neonText(if (unlocked) NeonPalette.Ink else NeonPalette.Dim, 11.sp))
            BasicText(
                if (unlocked) starText(stars) else "LOCKED",
                style = neonText(if (unlocked) NeonPalette.Yellow else NeonPalette.Dim, 14.sp),
            )
        }
    }
}

@Composable
private fun NeonButton(text: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .border(2.dp, color, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = neonText(color, 18.sp))
    }
}

private fun neonText(color: Color, size: TextUnit) = TextStyle(
    color = color,
    fontSize = size,
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    textAlign = TextAlign.Center,
    shadow = Shadow(color = color.copy(alpha = 0.6f), blurRadius = 12f),
)
