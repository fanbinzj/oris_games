package com.orisgames.dino

import com.orisgames.dino.storage.LeaderboardCodec
import com.orisgames.dino.storage.LeaderboardStorage
import com.orisgames.dino.storage.ScoreEntry
import java.util.prefs.Preferences

class DesktopLeaderboardStorage : LeaderboardStorage {
    private val prefs = Preferences.userRoot().node("com/orisgames/dino")

    override fun load(): List<ScoreEntry> {
        val entries = LeaderboardCodec.decode(prefs.get(KEY_BOARD, null))
        if (entries.isNotEmpty()) return entries
        // Migrate the pre-leaderboard single best score, if present.
        val legacyBest = prefs.getInt(KEY_LEGACY_BEST, 0)
        return if (legacyBest > 0) listOf(ScoreEntry(LEGACY_NAME, legacyBest)) else emptyList()
    }

    override fun save(entries: List<ScoreEntry>) {
        prefs.put(KEY_BOARD, LeaderboardCodec.encode(entries))
    }

    private companion object {
        const val KEY_BOARD = "leaderboard"
        const val KEY_LEGACY_BEST = "best_score"
        const val LEGACY_NAME = "Dino"
    }
}
