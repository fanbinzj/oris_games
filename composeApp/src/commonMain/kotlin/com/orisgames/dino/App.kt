package com.orisgames.dino

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.orisgames.dino.storage.LeaderboardStorage
import com.orisgames.dino.ui.GameScreen

@Composable
fun App(storage: LeaderboardStorage) {
    MaterialTheme {
        GameScreen(storage)
    }
}
