package com.orisgames.dino

import android.content.Context
import com.orisgames.dino.storage.LeaderboardCodec
import com.orisgames.dino.storage.LeaderboardStorage
import com.orisgames.dino.storage.ScoreEntry

class AndroidLeaderboardStorage(context: Context) : LeaderboardStorage {
    private val prefs = context.getSharedPreferences("dino_nugget_run", Context.MODE_PRIVATE)

    override fun load(): List<ScoreEntry> {
        val entries = LeaderboardCodec.decode(prefs.getString(KEY_BOARD, null))
        if (entries.isNotEmpty()) return entries
        // Migrate the pre-leaderboard single best score, if present.
        val legacyBest = prefs.getInt(KEY_LEGACY_BEST, 0)
        return if (legacyBest > 0) listOf(ScoreEntry(LEGACY_NAME, legacyBest)) else emptyList()
    }

    override fun save(entries: List<ScoreEntry>) {
        prefs.edit().putString(KEY_BOARD, LeaderboardCodec.encode(entries)).apply()
    }

    private companion object {
        const val KEY_BOARD = "leaderboard"
        const val KEY_LEGACY_BEST = "best_score"
        const val LEGACY_NAME = "Dino"
    }
}
