package com.orisgames.dino

import android.content.Context
import com.orisgames.dino.storage.HighScoreStorage

class AndroidHighScoreStorage(context: Context) : HighScoreStorage {
    private val prefs = context.getSharedPreferences("dino_nugget_run", Context.MODE_PRIVATE)

    override fun load(): Int = prefs.getInt(KEY_BEST, 0)

    override fun save(score: Int) {
        prefs.edit().putInt(KEY_BEST, score).apply()
    }

    private companion object {
        const val KEY_BEST = "best_score"
    }
}
