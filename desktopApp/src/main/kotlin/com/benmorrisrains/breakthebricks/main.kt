package com.benmorrisrains.breakthebricks

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Break The Bricks",
        // Roughly a phone in portrait.
        state = rememberWindowState(width = 430.dp, height = 900.dp),
    ) {
        App()
    }
}
