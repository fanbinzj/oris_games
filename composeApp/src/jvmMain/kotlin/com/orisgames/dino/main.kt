package com.orisgames.dino

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Dino Nugget Run",
        state = rememberWindowState(width = 1000.dp, height = 640.dp),
    ) {
        val storage = remember { DesktopHighScoreStorage() }
        App(storage)
    }
}
