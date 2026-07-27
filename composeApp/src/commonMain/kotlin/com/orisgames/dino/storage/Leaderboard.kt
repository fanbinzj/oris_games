package com.orisgames.dino.storage

import com.orisgames.dino.game.GameConfig
import kotlin.random.Random

data class ScoreEntry(val name: String, val score: Int)

/** Persists the local (per-device) top list as a single encoded string. */
interface LeaderboardStorage {
    fun load(): List<ScoreEntry>
    fun save(entries: List<ScoreEntry>)
}

class InMemoryLeaderboardStorage : LeaderboardStorage {
    private var entries: List<ScoreEntry> = emptyList()

    override fun load(): List<ScoreEntry> = entries

    override fun save(entries: List<ScoreEntry>) {
        this.entries = entries
    }
}

/**
 * Line-based codec shared by local storage and the global backend protocol:
 * one entry per line, "name|score". Names are sanitized so they can never
 * break the format.
 */
object LeaderboardCodec {
    // Must match the worker's sanitize rules (backend/leaderboard-worker.js):
    // strip | \r \n < > so a name is stored identically on both sides, or the
    // sync dedupe (exact name+score match) would re-upload it every launch.
    fun sanitizeName(raw: String): String = raw
        .replace('|', ' ')
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace('<', ' ')
        .replace('>', ' ')
        .trim()
        .take(GameConfig.MAX_NAME_LENGTH)

    fun encode(entries: List<ScoreEntry>): String =
        entries.joinToString("\n") { "${sanitizeName(it.name)}|${it.score}" }

    fun decode(raw: String?): List<ScoreEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lines().mapNotNull { line ->
            val separator = line.lastIndexOf('|')
            if (separator <= 0) return@mapNotNull null
            val name = line.substring(0, separator).trim()
            val score = line.substring(separator + 1).trim().toIntOrNull() ?: return@mapNotNull null
            if (name.isEmpty() || score <= 0) return@mapNotNull null
            ScoreEntry(name.take(GameConfig.MAX_NAME_LENGTH), score)
        }
    }
}

/** The local top list: sorted best-first, capped, persisted on every add. */
class Leaderboard(private val storage: LeaderboardStorage) {
    var entries: List<ScoreEntry> = storage.load()
        .sortedByDescending { it.score }
        .take(GameConfig.LEADERBOARD_SIZE)
        private set

    val best: Int
        get() = entries.firstOrNull()?.score ?: 0

    fun qualifies(score: Int): Boolean =
        score > 0 && (entries.size < GameConfig.LEADERBOARD_SIZE || score > entries.last().score)

    /** Adds the entry, persists, and returns its 0-based rank. */
    fun add(name: String, score: Int): Int {
        val entry = ScoreEntry(LeaderboardCodec.sanitizeName(name), score)
        // Stable sort: on ties, earlier records keep the higher rank.
        entries = (entries + entry)
            .sortedByDescending { it.score }
            .take(GameConfig.LEADERBOARD_SIZE)
        storage.save(entries)
        return entries.indexOfFirst { it === entry }
    }
}

/** Kid-friendly fallback names for players who skip the name field. */
object RandomNames {
    private val ADJECTIVES = listOf(
        "Speedy", "Mighty", "Bouncy", "Turbo", "Happy",
        "Brave", "Zippy", "Sunny", "Lucky", "Rowdy",
    )
    private val CREATURES = listOf(
        "Dino", "Rex", "Raptor", "Nugget", "Stompy",
        "Chomper", "Spike", "Tricera", "Dactyl", "Roary",
    )

    fun next(random: Random): String =
        "${ADJECTIVES.random(random)} ${CREATURES.random(random)}"
}
