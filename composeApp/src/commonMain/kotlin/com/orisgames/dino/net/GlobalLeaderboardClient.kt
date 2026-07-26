package com.orisgames.dino.net

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
