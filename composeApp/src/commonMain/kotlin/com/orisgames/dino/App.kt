package com.orisgames.dino

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.orisgames.dino.storage.HighScoreStorage
import com.orisgames.dino.ui.GameScreen

@Composable
fun App(storage: HighScoreStorage) {
    MaterialTheme {
        GameScreen(storage)
    }
}
