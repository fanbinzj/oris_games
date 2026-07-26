package com.orisgames.dino

import com.orisgames.dino.storage.LeaderboardCodec
import com.orisgames.dino.storage.LeaderboardStorage
import com.orisgames.dino.storage.ScoreEntry
import kotlinx.browser.localStorage

/**
 * localStorage can throw (storage disabled, strict privacy modes, some
 * embedded webviews); fall back to an in-memory list so the game still runs.
 */
class BrowserLeaderboardStorage : LeaderboardStorage {
    private var cached: List<ScoreEntry> = emptyList()

    override fun load(): List<ScoreEntry> {
        cached = try {
            val entries = LeaderboardCodec.decode(localStorage.getItem(KEY_BOARD))
            if (entries.isNotEmpty()) {
                entries
            } else {
                // Migrate the pre-leaderboard single best score, if present.
                val legacyBest = localStorage.getItem(KEY_LEGACY_BEST)?.toIntOrNull() ?: 0
                if (legacyBest > 0) listOf(ScoreEntry(LEGACY_NAME, legacyBest)) else emptyList()
            }
        } catch (_: Throwable) {
            cached
        }
        return cached
    }

    override fun save(entries: List<ScoreEntry>) {
        cached = entries
        try {
            localStorage.setItem(KEY_BOARD, LeaderboardCodec.encode(entries))
        } catch (_: Throwable) {
            // Keep the in-memory list; persistence is best-effort.
        }
    }

    private companion object {
        const val KEY_BOARD = "dino-nugget-run-board"
        const val KEY_LEGACY_BEST = "dino-nugget-run-best"
        const val LEGACY_NAME = "Dino"
    }
}
