package com.orisgames.dino.net

import com.orisgames.dino.game.GameConfig
import com.orisgames.dino.storage.LeaderboardCodec
import com.orisgames.dino.storage.ScoreEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class LeaderboardSyncTest {

    /**
     * Server double that mirrors backend/leaderboard-worker.js: sanitizes the
     * name, rejects invalid entries (throws, like a 400), keeps 100 and
     * returns the top 10.
     */
    private class FakeServer(initial: List<ScoreEntry> = emptyList()) {
        val all = initial.toMutableList()
        var submitCount = 0
        var rejectCount = 0

        fun top(): List<ScoreEntry> =
            all.sortedByDescending { it.score }.take(GameConfig.LEADERBOARD_SIZE)

        suspend fun submit(entry: ScoreEntry): List<ScoreEntry> {
            submitCount++
            val name = LeaderboardCodec.sanitizeName(entry.name)
            if (name.isEmpty() || entry.score <= 0 || entry.score > GameConfig.MAX_GLOBAL_SCORE) {
                rejectCount++
                error("400 rejected")
            }
            all.add(ScoreEntry(name, entry.score))
            all.sortByDescending { it.score }
            while (all.size > 100) all.removeAt(all.size - 1)
            return top()
        }
    }

    @Test
    fun uploadsMissingLocalRecords() = runTest {
        val server = FakeServer(listOf(ScoreEntry("Remote", 500)))
        val local = listOf(ScoreEntry("OldChamp", 2060), ScoreEntry("Kid", 40))
        val result = syncLocalEntriesToGlobal(local, server.top()) { server.submit(it) }
        assertEquals(2, server.submitCount)
        assertEquals(ScoreEntry("OldChamp", 2060), result.first())
        assertEquals(listOf("OldChamp", "Remote", "Kid"), result.map { it.name })
    }

    @Test
    fun skipsEntriesAlreadyOnTheBoardSoSyncIsIdempotent() = runTest {
        val server = FakeServer(listOf(ScoreEntry("OldChamp", 2060), ScoreEntry("Kid", 40)))
        val local = listOf(ScoreEntry("OldChamp", 2060), ScoreEntry("Kid", 40))
        val result = syncLocalEntriesToGlobal(local, server.top()) { server.submit(it) }
        assertEquals(0, server.submitCount, "second launch must not re-upload")
        assertEquals(2, result.size)
    }

    @Test
    fun namesWithStrippedCharactersDoNotReuploadOnSecondSync() = runTest {
        // First launch: a name containing '<' gets sanitized to "3 Momo".
        val server = FakeServer()
        val local = listOf(ScoreEntry("<3 Momo", 300))
        val afterFirst = syncLocalEntriesToGlobal(local, server.top()) { server.submit(it) }
        assertEquals(1, server.submitCount)
        assertEquals("3 Momo", afterFirst.first().name)

        // Second launch with the SAME raw local entry must recognize it as
        // already present (sanitized) and NOT upload it again.
        val afterSecond = syncLocalEntriesToGlobal(local, server.top()) { server.submit(it) }
        assertEquals(1, server.submitCount, "must not re-upload a sanitized name")
        assertEquals(1, afterSecond.size)
    }

    @Test
    fun oneRejectedEntryDoesNotBlockLaterEntries() = runTest {
        val server = FakeServer()
        // Local list sorted best-first: a too-high (worker-rejected) score sits
        // ahead of legitimate entries. Pre-filtering means it never even hits
        // the server, and the good entries below it still sync.
        val local = listOf(
            ScoreEntry("Cheater", 999999),
            ScoreEntry("Real1", 400),
            ScoreEntry("Real2", 200),
        )
        val result = syncLocalEntriesToGlobal(local, server.top()) { server.submit(it) }
        assertEquals(0, server.rejectCount, "invalid entries are filtered before submit")
        assertTrue(result.any { it.name == "Real1" })
        assertTrue(result.any { it.name == "Real2" })
    }

    @Test
    fun skipsScoresTooLowForAFullBoard() = runTest {
        val seeded = (1..GameConfig.LEADERBOARD_SIZE).map { ScoreEntry("P$it", 1000 + it) }
        val server = FakeServer(seeded)
        val local = listOf(ScoreEntry("Low", 5), ScoreEntry("High", 5000))
        val result = syncLocalEntriesToGlobal(local, server.top()) { server.submit(it) }
        assertEquals(1, server.submitCount, "only the qualifying entry uploads")
        assertEquals(ScoreEntry("High", 5000), result.first())
    }

    @Test
    fun fillsAnEmptyBoardFromLocalHistory() = runTest {
        val server = FakeServer()
        val local = listOf(ScoreEntry("A", 30), ScoreEntry("B", 20))
        val result = syncLocalEntriesToGlobal(local, server.top()) { server.submit(it) }
        assertEquals(2, server.submitCount)
        assertEquals(listOf("A", "B"), result.map { it.name })
    }

    @Test
    fun sameNameDifferentScoreStillUploads() = runTest {
        val server = FakeServer(listOf(ScoreEntry("Momo", 100)))
        val local = listOf(ScoreEntry("Momo", 250))
        val result = syncLocalEntriesToGlobal(local, server.top()) { server.submit(it) }
        assertEquals(1, server.submitCount)
        assertEquals(250, result.first().score)
    }
}
