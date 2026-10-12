package com.benmorrisrains.breakthebricks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Bold monospace arcade text with a soft glow in its own color. */
internal fun arcadeStyle(color: Color, size: TextUnit, align: TextAlign = TextAlign.Start) = TextStyle(
    color = color,
    fontSize = size,
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    textAlign = align,
    shadow = Shadow(color = color.copy(alpha = 0.6f), blurRadius = 14f),
)

private val ButtonShape = RoundedCornerShape(6.dp)

/** A centered column of menu items, capped in width so it reads well on tablets and desktop. */
@Composable
internal fun MenuColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
internal fun MenuTitle(text: String, color: Color = NeonPalette.Magenta, size: TextUnit = 30.sp) {
    BasicText(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        style = arcadeStyle(color, size, TextAlign.Center),
    )
}

@Composable
internal fun MenuLabel(text: String, color: Color = NeonPalette.Purple, size: TextUnit = 14.sp) {
    BasicText(text = text, modifier = Modifier.fillMaxWidth(), style = arcadeStyle(color, size, TextAlign.Center))
}

/** A full-width neon outlined button. */
@Composable
internal fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = NeonPalette.Cyan,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(color.copy(alpha = 0.08f), ButtonShape)
            .border(2.dp, color, ButtonShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = text, style = arcadeStyle(color, 18.sp))
    }
}

/** A settings row: the label on the left and an ON / OFF switch on the right. */
@Composable
internal fun NeonToggle(label: String, on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val color = if (on) NeonPalette.Lime else NeonPalette.Dim
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(2.dp, NeonPalette.Purple.copy(alpha = 0.6f), ButtonShape)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text = label, modifier = Modifier.weight(1f), style = arcadeStyle(NeonPalette.Ink, 16.sp))
        Box(
            modifier = Modifier
                .widthIn(min = 64.dp)
                .border(2.dp, color, ButtonShape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(text = if (on) "ON" else "OFF", style = arcadeStyle(color, 14.sp))
        }
    }
}

/**
 * The player's rank with a bar of points to the next one. Draws nothing until task T15
 * provides a rank.
 */
@Composable
internal fun RankPanel(rank: RankProgress?, modifier: Modifier = Modifier) {
    if (rank == null) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MenuLabel("RANK ${rank.rank}", NeonPalette.Yellow, 16.sp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .border(1.dp, NeonPalette.Yellow.copy(alpha = 0.6f), ButtonShape),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(rank.fraction)
                    .fillMaxHeight()
                    .background(NeonPalette.Yellow, ButtonShape),
            )
        }
        MenuLabel("${formatPoints(rank.pointsToGo)} TO RANK ${rank.rank + 1}", NeonPalette.Purple, 12.sp)
    }
}
