package com.benmorrisrains.breakthebricks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benmorrisrains.breakthebricks.engine.Board
import com.benmorrisrains.breakthebricks.engine.Brick
import com.benmorrisrains.breakthebricks.engine.SeededRandom
import com.benmorrisrains.breakthebricks.engine.fibonacci
import com.benmorrisrains.breakthebricks.platform.PlatformServices
import com.benmorrisrains.breakthebricks.ui.NeonPalette

/**
 * Root composable for every platform. For now it shows a static sample board in the neon
 * palette; task T6 replaces the sample with the live game.
 */
@Composable
fun App(services: PlatformServices = remember { PlatformServices() }) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background)
            .safeDrawingPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        BasicText(
            text = "BREAK THE BRICKS",
            style = TextStyle(
                color = NeonPalette.Cyan,
                fontSize = 28.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            ),
        )
        SampleBoard(Modifier.fillMaxWidth().aspectRatio(7f / 9f))
    }
}

@Composable
private fun SampleBoard(modifier: Modifier = Modifier) {
    val round = 10
    val board = remember { sampleBoard(round) }
    val roundValue = fibonacci(round)
    Canvas(modifier) {
        val cell = size.width / board.columns
        val inset = cell * 0.06f
        board.bricks().forEach { brick ->
            val color = NeonPalette.brickColor(brick.hits, roundValue)
            val topLeft = Offset(brick.column * cell + inset, brick.row * cell + inset)
            val side = Size(cell - inset * 2, cell - inset * 2)
            // Soft glow under a crisp outline.
            drawRect(color.copy(alpha = 0.18f), topLeft, side, style = Stroke(width = cell * 0.16f))
            drawRect(color, topLeft, side, style = Stroke(width = cell * 0.05f))
        }
        val floorY = board.rows * cell - 1f
        drawLine(NeonPalette.Cyan, Offset(0f, floorY), Offset(size.width, floorY), strokeWidth = 2f)
    }
}

private fun sampleBoard(round: Int): Board {
    val board = Board(columns = 7, rows = 9)
    val random = SeededRandom(2026)
    for (row in 0 until 6) {
        val value = fibonacci((round - row).coerceAtLeast(1))
        for (column in 0 until 7) {
            if (random.nextInt(3) != 0) board[column, row] = Brick(column, row, value)
        }
    }
    return board
}
