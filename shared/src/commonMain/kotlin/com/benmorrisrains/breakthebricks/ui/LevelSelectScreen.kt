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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benmorrisrains.breakthebricks.engine.Abilities
import com.benmorrisrains.breakthebricks.engine.Level

private const val TILES_PER_ROW = 2

/**
 * The 10 free levels as neon tiles with their stars, opened from home. A locked level shows
 * dim and can't be tapped until the one before it is passed. Below them, the abilities a game
 * can earn play their preview clips.
 */
@Composable
fun LevelSelectScreen(
    progress: LevelProgress,
    onLevel: (Level) -> Unit,
    onBack: () -> Unit,
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
        LockedAbilities(Abilities.earnable, title = "ABILITIES TO EARN")
        NeonButton("BACK", onBack, color = NeonPalette.Cyan)
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

private fun neonText(color: Color, size: TextUnit) = arcadeStyle(color, size, TextAlign.Center)
