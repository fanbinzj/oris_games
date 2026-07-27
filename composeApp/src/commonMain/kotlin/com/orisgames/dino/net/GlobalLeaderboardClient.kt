package com.orisgames.dino.net

import com.orisgames.dino.game.GameConfig
import com.orisgames.dino.storage.LeaderboardCodec
import com.orisgames.dino.storage.ScoreEntry

/**
 * Client for the global leaderboard worker. Protocol is plain text using the
 * same "name|score" line format as local storage:
 *   GET  /top             -> top list
 *   POST /submit          -> body "name|score", responds with updated top list
 */
class GlobalLeaderboardClient(private val baseUrl: String) {
    val isEnabled: Boolean get() = baseUrl.isNotBlank()

    suspend fun top(): List<ScoreEntry> =
        LeaderboardCodec.decode(httpGetText("$baseUrl/top"))

    suspend fun submit(name: String, score: Int): List<ScoreEntry> =
        LeaderboardCodec.decode(
            httpPostText("$baseUrl/submit", "${LeaderboardCodec.sanitizeName(name)}|$score"),
        )
}

/**
 * Uploads local records that belong on the global board but are not there
 * yet (e.g. scores made before the global leaderboard was enabled, or while
 * offline). Returns the freshest global list.
 *
 * Robustness (all from the adversarial review of this feature):
 * - Dedupe compares the SANITIZED name so it matches what the worker stores;
 *   otherwise names with stripped characters re-upload every launch.
 * - Entries the worker would reject (empty sanitized name, score out of
 *   range) are skipped up front so they never desync the board.
 * - Each submit is guarded independently: one failure (network or a 4xx)
 *   must not stop the remaining entries from syncing.
 */
suspend fun syncLocalEntriesToGlobal(
    local: List<ScoreEntry>,
    global: List<ScoreEntry>,
    submit: suspend (ScoreEntry) -> List<ScoreEntry>,
): List<ScoreEntry> {
    var current = global
    for (entry in local) {
        val name = LeaderboardCodec.sanitizeName(entry.name)
        if (name.isEmpty() || entry.score <= 0 || entry.score > GameConfig.MAX_GLOBAL_SCORE) continue
        val qualifies = current.size < GameConfig.LEADERBOARD_SIZE ||
            entry.score > (current.lastOrNull()?.score ?: 0)
        val alreadyThere = current.any { it.name == name && it.score == entry.score }
        if (qualifies && !alreadyThere) {
            try {
                current = submit(entry.copy(name = name))
            } catch (_: Throwable) {
                // Skip this one; keep syncing the rest.
            }
        }
    }
    return current
}
