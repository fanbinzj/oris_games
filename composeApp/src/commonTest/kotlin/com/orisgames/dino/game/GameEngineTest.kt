package com.orisgames.dino.game

import com.orisgames.dino.storage.LeaderboardCodec
import com.orisgames.dino.storage.LeaderboardStorage
import com.orisgames.dino.storage.ScoreEntry
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val DT = 1f / 60f

private class FakeStorage(initial: List<ScoreEntry> = emptyList()) : LeaderboardStorage {
    var entries: List<ScoreEntry> = initial
    var saveCount = 0

    override fun load(): List<ScoreEntry> = entries

    override fun save(entries: List<ScoreEntry>) {
        this.entries = entries
        saveCount++
    }
}

private fun newEngine(best: Int = 0, seed: Int = 1): Pair<GameEngine, FakeStorage> {
    val storage = FakeStorage(if (best > 0) listOf(ScoreEntry("Seed", best)) else emptyList())
    return GameEngine(storage, Random(seed)) to storage
}

private fun GameEngine.step(seconds: Float) {
    var t = 0f
    while (t < seconds) {
        update(DT)
        t += DT
    }
}

class GameEngineTest {

    @Test
    fun startsInReadyStateOnTheGround() {
        val (engine, _) = newEngine()
        assertEquals(GamePhase.Ready, engine.phase)
        assertEquals(0, engine.score)
        assertTrue(engine.isOnGround)
        assertTrue(engine.cacti.isEmpty())
        assertTrue(engine.nuggets.isEmpty())
    }

    @Test
    fun tapStartsTheGame() {
        val (engine, _) = newEngine()
        engine.tap()
        assertEquals(GamePhase.Running, engine.phase)
        assertEquals(0, engine.score)
        assertEquals(GameConfig.BASE_SPEED, engine.speed)
    }

    @Test
    fun loadsBestScoreFromStorage() {
        val (engine, _) = newEngine(best = 120)
        assertEquals(120, engine.bestScore)
    }

    @Test
    fun jumpLeavesGroundAndLandsAgain() {
        val (engine, _) = newEngine()
        engine.start()
        engine.debugDisableSpawning()
        engine.jump()
        engine.update(DT)
        assertFalse(engine.isOnGround)
        engine.step(2f)
        assertTrue(engine.isOnGround)
        assertEquals(0f, engine.dinoVelocityY)
        assertEquals(GameConfig.GROUND_Y, engine.dinoBottomY)
    }

    @Test
    fun cannotDoubleJump() {
        val (engine, _) = newEngine()
        engine.start()
        engine.debugDisableSpawning()
        engine.jump()
        engine.step(0.2f)
        assertFalse(engine.isOnGround)
        val velocityBefore = engine.dinoVelocityY
        engine.jump()
        assertEquals(velocityBefore, engine.dinoVelocityY)
    }

    @Test
    fun jumpApexComfortablyClearsTallestCactus() {
        val (engine, _) = newEngine()
        engine.start()
        engine.debugDisableSpawning()
        engine.jump()
        var minBottom = GameConfig.GROUND_Y
        repeat(120) {
            engine.update(DT)
            minBottom = min(minBottom, engine.dinoBottomY)
        }
        val apexAltitude = GameConfig.GROUND_Y - minBottom
        assertTrue(
            apexAltitude > GameConfig.CACTUS_MAX_HEIGHT + 60f,
            "apex altitude $apexAltitude is not comfortably above the tallest cactus",
        )
    }

    @Test
    fun worldMovesLeftWhileRunning() {
        val (engine, _) = newEngine()
        engine.start()
        engine.debugDisableSpawning()
        engine.debugAddCactus(Cactus(x = 700f, width = 30f, height = 50f))
        engine.update(DT)
        assertTrue(engine.cacti.single().x < 700f)
    }

    @Test
    fun passingCactusScoresExactlyOnce() {
        val (engine, _) = newEngine()
        engine.start()
        engine.debugDisableSpawning()
        // Right edge already left of the dino: counts as passed on next update.
        engine.debugAddCactus(Cactus(x = GameConfig.DINO_X - 45f, width = 30f, height = 50f))
        engine.update(DT)
        assertEquals(GameConfig.CACTUS_SCORE, engine.score)
        engine.update(DT)
        assertEquals(GameConfig.CACTUS_SCORE, engine.score)
        assertEquals(0, engine.milestone)
    }

    @Test
    fun hittingCactusAsksForNameThenSavesNewBest() {
        val (engine, storage) = newEngine()
        engine.start()
        engine.debugDisableSpawning()
        engine.addScore(30)
        engine.debugAddCactus(Cactus(x = GameConfig.DINO_X, width = 40f, height = 50f))
        engine.update(DT)
        assertEquals(GamePhase.GameOver, engine.phase)
        assertTrue(engine.isNewRecord)
        assertTrue(engine.awaitingRecordName)
        assertEquals(0, storage.saveCount, "nothing persists before a name is given")

        val name = engine.submitRecordName("Momo")
        assertEquals("Momo", name)
        assertFalse(engine.awaitingRecordName)
        assertEquals(30, engine.bestScore)
        assertEquals(listOf(ScoreEntry("Momo", 30)), storage.entries)
        assertEquals(1, storage.saveCount)
        assertEquals(0, engine.lastRecordRank)
    }

    @Test
    fun gameOverWithLowerScoreKeepsExistingBest() {
        val (engine, storage) = newEngine(best = 100)
        engine.start()
        engine.debugDisableSpawning()
        engine.addScore(10)
        engine.debugAddCactus(Cactus(x = GameConfig.DINO_X, width = 40f, height = 50f))
        engine.update(DT)
        assertEquals(GamePhase.GameOver, engine.phase)
        assertFalse(engine.isNewRecord)
        assertEquals(100, engine.bestScore)
        // 10 points still makes the (non-full) local top list.
        assertTrue(engine.awaitingRecordName)
        engine.submitRecordName("Kid")
        assertEquals(100, engine.bestScore)
        assertEquals(1, engine.lastRecordRank)
        assertEquals(listOf(ScoreEntry("Seed", 100), ScoreEntry("Kid", 10)), storage.entries)
    }

    @Test
    fun grazingWithinForgivenessMarginDoesNotKill() {
        val (engine, _) = newEngine()
        engine.start()
        engine.debugDisableSpawning()
        // Derive both cases from config so tuning HITBOX_FORGIVENESS keeps
        // the test meaningful: the shrunken cactus box starts at
        // x + width * forgiveness; the dino's shrunken right edge is at
        // DINO_X + DINO_WIDTH * (1 - forgiveness).
        val forgiveness = GameConfig.HITBOX_FORGIVENESS
        val cactusWidth = 30f
        val dinoHitRight = GameConfig.DINO_X + GameConfig.DINO_WIDTH * (1f - forgiveness)

        val safeX = dinoHitRight - cactusWidth * forgiveness + 1f
        engine.debugAddCactus(Cactus(x = safeX, width = cactusWidth, height = 50f))
        engine.update(0.0005f)
        assertEquals(GamePhase.Running, engine.phase)

        val deadlyX = dinoHitRight - cactusWidth * forgiveness - 5f
        engine.debugAddCactus(Cactus(x = deadlyX, width = cactusWidth, height = 50f))
        engine.update(0.0005f)
        assertEquals(GamePhase.GameOver, engine.phase)
    }

    @Test
    fun nuggetEatenMidJumpScoresAndDisappears() {
        val (engine, _) = newEngine()
        engine.start()
        engine.debugDisableSpawning()
        // Placed so the nugget crosses the dino column while the dino rises.
        engine.debugAddNugget(Nugget(x = 172f, y = GameConfig.GROUND_Y - 150f))
        engine.jump()
        engine.step(0.5f)
        assertEquals(GameConfig.NUGGET_SCORE, engine.score)
        assertTrue(engine.nuggets.isEmpty())
    }

    @Test
    fun nuggetCannotBeEatenFromTheGround() {
        val (engine, _) = newEngine()
        engine.start()
        engine.debugDisableSpawning()
        engine.debugAddNugget(Nugget(x = 172f, y = GameConfig.GROUND_Y - 150f))
        engine.step(0.4f)
        assertEquals(0, engine.score)
    }

    @Test
    fun milestoneCelebrationTriggersAtEveryHundred() {
        val (engine, _) = newEngine()
        engine.start()
        engine.debugDisableSpawning()
        engine.addScore(90)
        assertEquals(0, engine.milestone)
        assertEquals(0f, engine.celebrationTimer)
        engine.addScore(10)
        assertEquals(1, engine.milestone)
        assertTrue(engine.celebrationTimer > 0f)
        engine.step(2f)
        assertEquals(0f, engine.celebrationTimer)
    }

    @Test
    fun speedSteppedByLevelAndCaps() {
        val (engine, _) = newEngine()
        engine.start()
        engine.debugDisableSpawning()
        assertEquals(GameConfig.BASE_SPEED, engine.speed)
        // Level 1 -> 2: one full speed step.
        engine.addScore(GameConfig.MILESTONE_STEP)
        engine.update(DT)
        assertEquals(GameConfig.BASE_SPEED + GameConfig.SPEED_STEP_PER_LEVEL, engine.speed)
        // Same level: no further speed change until the next milestone.
        engine.addScore(GameConfig.MILESTONE_STEP / 2)
        engine.update(DT)
        assertEquals(GameConfig.BASE_SPEED + GameConfig.SPEED_STEP_PER_LEVEL, engine.speed)
        // Many levels -> capped.
        engine.addScore(10000)
        engine.update(DT)
        assertEquals(GameConfig.MAX_SPEED, engine.speed)
    }

    @Test
    fun difficultyRampsWithLevel() {
        val (engine, _) = newEngine()
        // Higher levels: faster, tighter gaps, taller cacti — each a real step.
        assertTrue(engine.speedForLevel(3) > engine.speedForLevel(1))
        assertTrue(engine.minCactusGapSecondsForLevel(5) < engine.minCactusGapSecondsForLevel(1))
        assertTrue(engine.maxCactusHeightForLevel(5) > engine.maxCactusHeightForLevel(1))
        // Floors and caps hold at extreme levels.
        assertTrue(engine.minCactusGapSecondsForLevel(100) >= GameConfig.MIN_CACTUS_GAP_FLOOR)
        assertTrue(engine.maxCactusHeightForLevel(100) <= GameConfig.CACTUS_MAX_HEIGHT_CAP)
        assertEquals(GameConfig.MAX_SPEED, engine.speedForLevel(100))
    }

    @Test
    fun restartIsLockedBrieflyThenResetsButKeepsBest() {
        val (engine, _) = newEngine()
        engine.start()
        engine.debugDisableSpawning()
        engine.addScore(30)
        engine.debugAddCactus(Cactus(x = GameConfig.DINO_X, width = 40f, height = 50f))
        engine.update(DT)
        assertEquals(GamePhase.GameOver, engine.phase)
        engine.submitRecordName("Momo")

        engine.tap()
        assertEquals(GamePhase.GameOver, engine.phase, "restart must be locked right after death")

        engine.step(GameConfig.RESTART_LOCK_SECONDS + 0.1f)
        engine.tap()
        assertEquals(GamePhase.Running, engine.phase)
        assertEquals(0, engine.score)
        assertTrue(engine.cacti.isEmpty())
        assertEquals(30, engine.bestScore)
    }

    @Test
    fun restartStaysBlockedWhileAwaitingName() {
        val (engine, _) = newEngine()
        engine.start()
        engine.debugDisableSpawning()
        engine.addScore(30)
        engine.debugAddCactus(Cactus(x = GameConfig.DINO_X, width = 40f, height = 50f))
        engine.update(DT)
        assertTrue(engine.awaitingRecordName)

        engine.step(GameConfig.RESTART_LOCK_SECONDS + 0.5f)
        engine.tap()
        assertEquals(GamePhase.GameOver, engine.phase, "no restart while the name dialog is open")

        engine.submitRecordName("")
        engine.tap()
        assertEquals(GamePhase.Running, engine.phase)
    }

    @Test
    fun cactusGapDistancesStayWithinConfiguredBounds() {
        val (engine, _) = newEngine(seed = 7)
        engine.start()
        val projectedSpeed = min(GameConfig.MAX_SPEED, engine.speed * GameConfig.GAP_SPEED_HEADROOM)
        repeat(200) {
            val gap = engine.nextCactusGapDistance()
            assertTrue(gap >= GameConfig.MIN_CACTUS_GAP_SECONDS * projectedSpeed)
            assertTrue(gap <= GameConfig.MAX_CACTUS_GAP_SECONDS * projectedSpeed)
            // The gap must give full reaction time even at current speed.
            assertTrue(gap / engine.speed >= GameConfig.MIN_CACTUS_GAP_SECONDS)
        }
    }

    @Test
    fun spawnedCactusSizesWithinBounds() {
        val (engine, _) = newEngine(seed = 3)
        engine.start()
        engine.debugDisableSpawning()
        repeat(100) { engine.spawnCactus() }
        for (cactus in engine.cacti) {
            assertTrue(cactus.width >= GameConfig.CACTUS_MIN_WIDTH)
            assertTrue(cactus.width <= GameConfig.CACTUS_MAX_WIDTH)
            assertTrue(cactus.height >= GameConfig.CACTUS_MIN_HEIGHT)
            assertTrue(cactus.height <= GameConfig.CACTUS_MAX_HEIGHT)
            assertTrue(cactus.x > GameConfig.WORLD_WIDTH)
        }
    }

    @Test
    fun spawnedNuggetsAreOnlyReachableMidJump() {
        val (engine, _) = newEngine(seed = 5)
        engine.start()
        engine.debugDisableSpawning()
        repeat(100) { engine.spawnNugget() }
        for (nugget in engine.nuggets) {
            val altitude = GameConfig.GROUND_Y - nugget.y
            assertTrue(altitude >= GameConfig.NUGGET_MIN_ALTITUDE)
            assertTrue(altitude <= GameConfig.NUGGET_MAX_ALTITUDE)
            // Standing dino tops out at DINO_HEIGHT; nuggets must sit above that.
            assertTrue(altitude - GameConfig.NUGGET_RADIUS - GameConfig.NUGGET_PICKUP_BONUS > GameConfig.DINO_HEIGHT)
        }
    }

    @Test
    fun simpleAutoJumperSurvivesTwentySeconds() {
        val (engine, _) = newEngine(seed = 42)
        engine.start()
        var t = 0f
        while (t < 20f) {
            if (engine.isOnGround) {
                val dinoFront = GameConfig.DINO_X + GameConfig.DINO_WIDTH
                val next = engine.cacti.firstOrNull { it.x + it.width > GameConfig.DINO_X }
                if (next != null && (next.x - dinoFront) / engine.speed < 0.38f) {
                    engine.jump()
                }
            }
            engine.update(DT)
            t += DT
        }
        assertEquals(GamePhase.Running, engine.phase, "auto-jumper died with score ${engine.score}")
        assertTrue(engine.score > 0)
    }

    @Test
    fun perfectJumperSurvivesIntoHighLevelsAndNuggetsAppear() {
        // Fairness at hard levels: a perfectly-timed player must be able to
        // reach the high, fast levels — and nuggets must still spawn there.
        val (engine, _) = newEngine(seed = 7)
        engine.start()
        var t = 0f
        var nuggetSpawns = 0
        var prevNuggets = 0
        var maxLevel = 1
        while (t < 120f) {
            if (engine.isOnGround) {
                val dinoFront = GameConfig.DINO_X + GameConfig.DINO_WIDTH
                val next = engine.cacti.firstOrNull { it.x + it.width > GameConfig.DINO_X }
                if (next != null && (next.x - dinoFront) / engine.speed <= 0.30f) {
                    engine.jump()
                }
            }
            engine.update(DT)
            if (engine.nuggets.size > prevNuggets) nuggetSpawns += engine.nuggets.size - prevNuggets
            prevNuggets = engine.nuggets.size
            maxLevel = maxOf(maxLevel, engine.level)
            t += DT
        }
        assertEquals(GamePhase.Running, engine.phase, "perfect jumper died at level ${engine.level}, score ${engine.score}")
        assertTrue(maxLevel >= 8, "difficulty should be reachable; only got to level $maxLevel")
        assertTrue(nuggetSpawns > 0, "nuggets must still appear during high-level play")
    }

    @Test
    fun tapWhileRunningJumpsAndMidAirTapIsIgnored() {
        val (engine, _) = newEngine()
        engine.tap() // Ready -> Running
        engine.debugDisableSpawning()
        engine.tap() // jump
        engine.update(DT)
        assertFalse(engine.isOnGround)
        val velocity = engine.dinoVelocityY
        val score = engine.score
        engine.tap() // mid-air tap must be a no-op
        assertEquals(velocity, engine.dinoVelocityY)
        assertEquals(score, engine.score)
        assertEquals(GamePhase.Running, engine.phase)
    }

    @Test
    fun landingOnCactusWhileAirborneEndsGame() {
        val (engine, _) = newEngine()
        engine.start()
        engine.debugDisableSpawning()
        engine.jump()
        // Ride the arc past the apex, then descend into cactus-top range.
        while (engine.dinoVelocityY <= 0f) engine.update(DT)
        while (GameConfig.GROUND_Y - engine.dinoBottomY > 40f) engine.update(DT)
        engine.debugAddCactus(Cactus(x = GameConfig.DINO_X, width = 40f, height = 72f))
        engine.update(DT)
        assertEquals(GamePhase.GameOver, engine.phase)
        assertTrue(
            engine.dinoBottomY < GameConfig.GROUND_Y,
            "death should happen mid-air, not after landing",
        )
    }

    @Test
    fun deltaTimeIsClampedAndNonPositiveDeltasIgnored() {
        val (engine, _) = newEngine()
        engine.start()
        engine.debugDisableSpawning()
        engine.update(5f)
        assertEquals(GameConfig.MAX_FRAME_DELTA, engine.elapsed, 1e-4f)
        assertEquals(GameConfig.BASE_SPEED * GameConfig.MAX_FRAME_DELTA, engine.distance, 1e-2f)

        val distance = engine.distance
        val elapsed = engine.elapsed
        engine.update(0f)
        engine.update(-1f)
        assertEquals(distance, engine.distance)
        assertEquals(elapsed, engine.elapsed)
    }

    @Test
    fun firstCactusSpawnsAfterConfiguredDistance() {
        val (engine, _) = newEngine()
        engine.start()
        val secondsToAlmostThere = GameConfig.FIRST_CACTUS_DISTANCE / GameConfig.BASE_SPEED - 0.2f
        engine.step(secondsToAlmostThere)
        assertTrue(engine.cacti.isEmpty(), "cactus spawned before FIRST_CACTUS_DISTANCE")
        engine.step(0.5f)
        assertTrue(engine.cacti.isNotEmpty(), "no cactus after passing FIRST_CACTUS_DISTANCE")
    }

    @Test
    fun nuggetScheduleSpawnsAndResets() {
        val (engine, _) = newEngine()
        engine.start()
        engine.distanceToNextCactus = Float.MAX_VALUE
        engine.distanceToNextNugget = 50f
        engine.step(0.5f)
        assertEquals(1, engine.nuggets.size)
        // Schedule must have been re-armed within configured bounds
        // (allowing for the distance already travelled since the spawn).
        val travelledSinceSpawn = GameConfig.BASE_SPEED * 0.5f
        assertTrue(engine.distanceToNextNugget >= GameConfig.MIN_NUGGET_GAP_SECONDS * GameConfig.BASE_SPEED - travelledSinceSpawn)
        assertTrue(engine.distanceToNextNugget <= GameConfig.MAX_NUGGET_GAP_SECONDS * GameConfig.BASE_SPEED)
    }

    @Test
    fun nuggetSpawnIsSkippedWhenCactusIsTooClose() {
        val (engine, _) = newEngine()
        engine.start()
        // Next cactus well within the (small) safety clearance.
        engine.distanceToNextCactus = 20f
        engine.distanceToNextNugget = 1f
        engine.update(DT)
        assertTrue(engine.nuggets.isEmpty(), "nugget must be skipped next to a cactus")

        // With the conflict removed the nugget spawns.
        engine.distanceToNextCactus = Float.MAX_VALUE
        engine.distanceToNextNugget = 1f
        engine.update(DT)
        assertEquals(1, engine.nuggets.size)
    }

    @Test
    fun nuggetClearanceFitsWithinHighLevelGaps() {
        // Regression: the old clearance grew past the shrinking cactus gaps and
        // starved nuggets entirely at high levels. The clearance must stay
        // small enough that a safe slot always exists inside a gap.
        val (engine, _) = newEngine()
        engine.start()
        engine.addScore(2000) // high, fast, tight-gap level
        engine.update(DT)
        val minGapDist = engine.minCactusGapSecondsForLevel(engine.level) * engine.speed
        val clearance = GameConfig.NUGGET_CACTUS_CLEARANCE_SECONDS * engine.speed
        assertTrue(2 * clearance < minGapDist, "clearance too large -> nuggets starve")

        // Mid-gap (no cacti present, next cactus a full gap away): no conflict.
        engine.distanceToNextCactus = minGapDist
        assertFalse(engine.nuggetSpawnConflicts())
    }

    @Test
    fun milestonesTriggerAcrossHundredsIncludingOvershoot() {
        val (engine, _) = newEngine()
        engine.start()
        engine.debugDisableSpawning()
        engine.addScore(95)
        assertEquals(0, engine.milestone)
        assertEquals(1, engine.level, "game starts at level 1")
        engine.addScore(25) // 120: crosses 100 without hitting it exactly
        assertEquals(1, engine.milestone)
        assertEquals(2, engine.level, "level-up at every milestone")
        assertTrue(engine.celebrationTimer > 0f)
        engine.step(2f)
        assertEquals(0f, engine.celebrationTimer)
        engine.addScore(80) // exactly 200
        assertEquals(2, engine.milestone)
        assertEquals(GameConfig.CELEBRATION_SECONDS, engine.celebrationTimer)
    }

    @Test
    fun scorePopsRiseFadeAndExpire() {
        val (engine, _) = newEngine()
        engine.start()
        engine.debugDisableSpawning()
        engine.addScore(10)
        assertEquals("+10", engine.pops.single().text)
        val initialY = engine.pops.single().y
        engine.update(DT)
        assertTrue(engine.pops.single().y < initialY, "pop should rise")
        engine.step(GameConfig.POP_SECONDS + 0.1f)
        assertTrue(engine.pops.isEmpty(), "pop should expire")
    }

    @Test
    fun popsKeepFadingAfterGameOver() {
        val (engine, _) = newEngine()
        engine.start()
        engine.debugDisableSpawning()
        engine.addScore(25)
        engine.debugAddCactus(Cactus(x = GameConfig.DINO_X, width = 40f, height = 50f))
        engine.update(DT)
        assertEquals(GamePhase.GameOver, engine.phase)
        assertTrue(engine.pops.isNotEmpty())
        engine.step(GameConfig.POP_SECONDS + 0.1f)
        assertTrue(engine.pops.isEmpty(), "pops must finish fading on the game-over screen")
    }

    @Test
    fun savesExactlyOncePerGameOverAndTieRanksBelowExisting() {
        val (engine, storage) = newEngine()
        engine.start()
        engine.debugDisableSpawning()
        engine.addScore(30)
        engine.debugAddCactus(Cactus(x = GameConfig.DINO_X, width = 40f, height = 50f))
        engine.update(DT)
        engine.submitRecordName("A")
        engine.submitRecordName("B") // second submit must be a no-op
        engine.step(1f) // extra game-over frames must not save again
        assertEquals(1, storage.saveCount)
        assertEquals("A", engine.lastRecordName)

        val (tieEngine, _) = newEngine(best = 30)
        tieEngine.start()
        tieEngine.debugDisableSpawning()
        tieEngine.addScore(30)
        tieEngine.debugAddCactus(Cactus(x = GameConfig.DINO_X, width = 40f, height = 50f))
        tieEngine.update(DT)
        assertFalse(tieEngine.isNewRecord, "a tie is not a new record")
        tieEngine.submitRecordName("Tie")
        assertEquals(1, tieEngine.lastRecordRank, "a tie ranks below the earlier record")
        assertEquals(30, tieEngine.bestScore)
    }

    @Test
    fun blankNameGetsRandomKidFriendlyName() {
        val (engine, storage) = newEngine(seed = 9)
        engine.start()
        engine.debugDisableSpawning()
        engine.addScore(40)
        engine.debugAddCactus(Cactus(x = GameConfig.DINO_X, width = 40f, height = 50f))
        engine.update(DT)
        val name = engine.submitRecordName("   ")
        assertTrue(name.isNotBlank())
        assertTrue(name.contains(' '), "random names are 'Adjective Creature': $name")
        assertEquals(name, storage.entries.single().name)
    }

    @Test
    fun submittedNameIsSanitizedAndTruncated() {
        val (engine, storage) = newEngine()
        engine.start()
        engine.debugDisableSpawning()
        engine.addScore(40)
        engine.debugAddCactus(Cactus(x = GameConfig.DINO_X, width = 40f, height = 50f))
        engine.update(DT)
        val name = engine.submitRecordName("A|B\nCDEFGHIJKLMNOP")
        assertFalse(name.contains('|'))
        assertFalse(name.contains('\n'))
        assertTrue(name.length <= GameConfig.MAX_NAME_LENGTH)
        assertEquals(name, storage.entries.single().name)
    }

    @Test
    fun lowScoreOnFullBoardDoesNotQualify() {
        val seeded = (1..GameConfig.LEADERBOARD_SIZE).map { ScoreEntry("P$it", 100 * it) }
        val storage = FakeStorage(seeded)
        val engine = GameEngine(storage, Random(1))
        engine.start()
        engine.debugDisableSpawning()
        engine.addScore(50) // below the lowest seeded score (100)
        engine.debugAddCactus(Cactus(x = GameConfig.DINO_X, width = 40f, height = 50f))
        engine.update(DT)
        assertEquals(GamePhase.GameOver, engine.phase)
        assertFalse(engine.awaitingRecordName, "score below a full board must not prompt")
        assertEquals(0, storage.saveCount)

        engine.step(GameConfig.RESTART_LOCK_SECONDS + 0.1f)
        engine.tap()
        assertEquals(GamePhase.Running, engine.phase)
    }

    @Test
    fun leaderboardCapsAtConfiguredSizeAndDropsLowest() {
        val seeded = (1..GameConfig.LEADERBOARD_SIZE).map { ScoreEntry("P$it", 100 * it) }
        val storage = FakeStorage(seeded)
        val engine = GameEngine(storage, Random(1))
        engine.start()
        engine.debugDisableSpawning()
        engine.addScore(150) // beats the lowest (100), not the highest
        engine.debugAddCactus(Cactus(x = GameConfig.DINO_X, width = 40f, height = 50f))
        engine.update(DT)
        assertTrue(engine.awaitingRecordName)
        engine.submitRecordName("Newbie")
        assertEquals(GameConfig.LEADERBOARD_SIZE, storage.entries.size)
        assertTrue(storage.entries.any { it.name == "Newbie" })
        assertFalse(storage.entries.any { it.score == 100 }, "lowest entry must drop off")
    }

    @Test
    fun codecRoundTripsAndSkipsMalformedLines() {
        val entries = listOf(
            ScoreEntry("Speedy Rex", 420),
            ScoreEntry("A B", 10),
        )
        assertEquals(entries, LeaderboardCodec.decode(LeaderboardCodec.encode(entries)))

        val messy = "Speedy Rex|420\ngarbage line\n|55\nName|notanumber\nOk|7\n\n"
        assertEquals(
            listOf(ScoreEntry("Speedy Rex", 420), ScoreEntry("Ok", 7)),
            LeaderboardCodec.decode(messy),
        )
        assertEquals(emptyList(), LeaderboardCodec.decode(null))
        assertEquals(emptyList(), LeaderboardCodec.decode(""))
    }

    @Test
    fun highestNuggetIsReachableAtJumpApex() {
        val (engine, _) = newEngine()
        engine.start()
        engine.debugDisableSpawning()
        engine.jump()
        var minBottom = GameConfig.GROUND_Y
        repeat(120) {
            engine.update(DT)
            minBottom = min(minBottom, engine.dinoBottomY)
        }
        val apexAltitude = GameConfig.GROUND_Y - minBottom
        val maxReachableCenterAltitude =
            apexAltitude + GameConfig.DINO_HEIGHT + GameConfig.NUGGET_PICKUP_BONUS + GameConfig.NUGGET_RADIUS
        assertTrue(
            maxReachableCenterAltitude >= GameConfig.NUGGET_MAX_ALTITUDE + 20f,
            "highest nugget (${GameConfig.NUGGET_MAX_ALTITUDE}) must be comfortably reachable " +
                "(max reachable $maxReachableCenterAltitude)",
        )
    }
}
