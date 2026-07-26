package com.orisgames.dino

import com.orisgames.dino.storage.HighScoreStorage
import kotlinx.browser.localStorage

/**
 * localStorage can throw (storage disabled, strict privacy modes, some
 * embedded webviews); fall back to an in-memory best so the game still runs.
 */
class BrowserHighScoreStorage : HighScoreStorage {
    private var cached = 0

    override fun load(): Int {
        cached = try {
            localStorage.getItem(KEY_BEST)?.toIntOrNull() ?: 0
        } catch (_: Throwable) {
            cached
        }
        return cached
    }

    override fun save(score: Int) {
        cached = score
        try {
            localStorage.setItem(KEY_BEST, score.toString())
        } catch (_: Throwable) {
            // Keep the in-memory value; persistence is best-effort.
        }
    }

    private companion object {
        const val KEY_BEST = "dino-nugget-run-best"
    }
}
