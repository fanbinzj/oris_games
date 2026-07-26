package com.orisgames.dino

import com.orisgames.dino.storage.HighScoreStorage
import java.util.prefs.Preferences

class DesktopHighScoreStorage : HighScoreStorage {
    private val prefs = Preferences.userRoot().node("com/orisgames/dino")

    override fun load(): Int = prefs.getInt(KEY_BEST, 0)

    override fun save(score: Int) {
        prefs.putInt(KEY_BEST, score)
    }

    private companion object {
        const val KEY_BEST = "best_score"
    }
}
