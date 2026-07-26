package com.orisgames.dino.game

import com.orisgames.dino.storage.Leaderboard
import com.orisgames.dino.storage.LeaderboardCodec
import com.orisgames.dino.storage.LeaderboardStorage
import com.orisgames.dino.storage.RandomNames
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Pure game logic for the dino runner. No platform or UI dependencies:
 * coordinates are world units (800x450, y grows downward), time is seconds.
 */
class GameEngine(
    storage: LeaderboardStorage,
    private val random: Random = Random.Default,
) {
    val leaderboard = Leaderboard(storage)

    var phase: GamePhase = GamePhase.Ready
        private set
    var score: Int = 0
        private set
    var bestScore: Int = leaderboard.best
        private set
    var isNewRecord: Boolean = false
        private set

    /** True after a qualifying run ends, until the player submits a name. */
    var awaitingRecordName: Boolean = false
        private set
    var lastRecordName: String? = null
        private set
    var lastRecordRank: Int = -1
        private set
    var milestone: Int = 0
        private set
    var celebrationTimer: Float = 0f
        private set

    /** Displayed challenge level: level-up at every milestone (100 points). */
    val level: Int
        get() = milestone + 1

    /** Y of the dino's feet. Equals GROUND_Y when standing. */
    var dinoBottomY: Float = GameConfig.GROUND_Y
        private set
    var dinoVelocityY: Float = 0f
        private set
    val isOnGround: Boolean
        get() = dinoBottomY >= GameConfig.GROUND_Y

    var speed: Float = GameConfig.BASE_SPEED
        private set
    var distance: Float = 0f
        private set
    var elapsed: Float = 0f
        private set
    var timeSinceGameOver: Float = 0f
        private set

    private val mutableCacti = mutableListOf<Cactus>()
    val cacti: List<Cactus> get() = mutableCacti

    private val mutableNuggets = mutableListOf<Nugget>()
    val nuggets: List<Nugget> get() = mutableNuggets

    private val mutablePops = mutableListOf<ScorePop>()
    val pops: List<ScorePop> get() = mutablePops

    internal var distanceToNextCactus = Float.MAX_VALUE
    internal var distanceToNextNugget = Float.MAX_VALUE

    /** Single entry point for taps, clicks, and jump keys. */
    fun tap() {
        when (phase) {
            GamePhase.Ready -> start()
            GamePhase.Running -> jump()
            GamePhase.GameOver ->
                if (!awaitingRecordName && timeSinceGameOver >= GameConfig.RESTART_LOCK_SECONDS) start()
        }
    }

    fun start() {
        mutableCacti.clear()
        mutableNuggets.clear()
        mutablePops.clear()
        score = 0
        milestone = 0
        celebrationTimer = 0f
        isNewRecord = false
        awaitingRecordName = false
        lastRecordName = null
        lastRecordRank = -1
        dinoBottomY = GameConfig.GROUND_Y
        dinoVelocityY = 0f
        speed = GameConfig.BASE_SPEED
        distance = 0f
        timeSinceGameOver = 0f
        distanceToNextCactus = GameConfig.FIRST_CACTUS_DISTANCE
        distanceToNextNugget = nextNuggetGapDistance()
        phase = GamePhase.Running
    }

    fun jump() {
        if (phase == GamePhase.Running && isOnGround) {
            dinoVelocityY = GameConfig.JUMP_VELOCITY
        }
    }

    fun update(rawDelta: Float) {
        val dt = min(rawDelta, GameConfig.MAX_FRAME_DELTA)
        if (dt <= 0f) return
        elapsed += dt
        when (phase) {
            GamePhase.Ready -> Unit
            GamePhase.GameOver -> {
                timeSinceGameOver += dt
                // Let leftover pops and celebration stars finish fading
                // instead of freezing on the game-over screen.
                updatePops(dt)
                celebrationTimer = max(0f, celebrationTimer - dt)
            }
            GamePhase.Running -> updateRunning(dt)
        }
    }

    private fun updateRunning(dt: Float) {
        val step = speed * dt
        distance += step

        dinoVelocityY += GameConfig.GRAVITY * dt
        dinoBottomY += dinoVelocityY * dt
        if (dinoBottomY >= GameConfig.GROUND_Y) {
            dinoBottomY = GameConfig.GROUND_Y
            dinoVelocityY = 0f
        }

        moveWorld(step)
        spawnIfDue(step)
        collectNuggets()
        scorePassedCacti()
        updatePops(dt)
        celebrationTimer = max(0f, celebrationTimer - dt)

        if (hitsCactus()) {
            endGame()
            return
        }
        speed = min(
            GameConfig.MAX_SPEED,
            GameConfig.BASE_SPEED + score * GameConfig.SPEED_PER_POINT,
        )
    }

    private fun moveWorld(step: Float) {
        mutableCacti.forEach { it.x -= step }
        mutableNuggets.forEach { it.x -= step }
        mutableCacti.removeAll { it.x + it.width < GameConfig.DESPAWN_X }
        mutableNuggets.removeAll { it.x + GameConfig.NUGGET_RADIUS < GameConfig.DESPAWN_X }
    }

    private fun spawnIfDue(step: Float) {
        distanceToNextCactus -= step
        if (distanceToNextCactus <= 0f) {
            spawnCactus()
            distanceToNextCactus = nextCactusGapDistance()
        }
        distanceToNextNugget -= step
        if (distanceToNextNugget <= 0f) {
            val deferral = nuggetSpawnConflictShift()
            if (deferral > 0f) {
                distanceToNextNugget = deferral
            } else {
                spawnNugget()
                distanceToNextNugget = nextNuggetGapDistance()
            }
        }
    }

    /**
     * Distance to postpone a nugget spawn so it keeps a safe horizontal
     * clearance from every cactus (present or already scheduled). Chasing a
     * nugget must never bait the player into an unavoidable collision.
     */
    internal fun nuggetSpawnConflictShift(): Float {
        val clearance = GameConfig.NUGGET_CACTUS_CLEARANCE_SECONDS * speed
        val candidateX = GameConfig.WORLD_WIDTH + GameConfig.SPAWN_MARGIN
        var shift = 0f
        for (cactus in mutableCacti) {
            val gap = candidateX - (cactus.x + cactus.width / 2f)
            if (gap < clearance) shift = max(shift, clearance - gap)
        }
        // The next cactus will spawn at candidateX + distanceToNextCactus.
        if (distanceToNextCactus < clearance) {
            shift = max(shift, clearance + distanceToNextCactus)
        }
        return shift
    }

    internal fun spawnCactus() {
        val width = randomBetween(GameConfig.CACTUS_MIN_WIDTH, GameConfig.CACTUS_MAX_WIDTH)
        val height = randomBetween(GameConfig.CACTUS_MIN_HEIGHT, GameConfig.CACTUS_MAX_HEIGHT)
        mutableCacti.add(Cactus(x = GameConfig.WORLD_WIDTH + GameConfig.SPAWN_MARGIN, width = width, height = height))
    }

    internal fun spawnNugget() {
        val altitude = randomBetween(GameConfig.NUGGET_MIN_ALTITUDE, GameConfig.NUGGET_MAX_ALTITUDE)
        mutableNuggets.add(Nugget(x = GameConfig.WORLD_WIDTH + GameConfig.SPAWN_MARGIN, y = GameConfig.GROUND_Y - altitude))
    }

    internal fun nextCactusGapDistance(): Float {
        // Convert seconds to distance at an inflated speed: if the game
        // accelerates while this gap is in flight, the actual travel time
        // still stays at or above MIN_CACTUS_GAP_SECONDS.
        val projectedSpeed = min(GameConfig.MAX_SPEED, speed * GameConfig.GAP_SPEED_HEADROOM)
        return randomBetween(GameConfig.MIN_CACTUS_GAP_SECONDS, GameConfig.MAX_CACTUS_GAP_SECONDS) * projectedSpeed
    }

    internal fun nextNuggetGapDistance(): Float =
        randomBetween(GameConfig.MIN_NUGGET_GAP_SECONDS, GameConfig.MAX_NUGGET_GAP_SECONDS) * speed

    private fun collectNuggets() {
        val left = GameConfig.DINO_X - GameConfig.NUGGET_PICKUP_BONUS
        val right = GameConfig.DINO_X + GameConfig.DINO_WIDTH + GameConfig.NUGGET_PICKUP_BONUS
        val top = dinoBottomY - GameConfig.DINO_HEIGHT - GameConfig.NUGGET_PICKUP_BONUS
        val bottom = dinoBottomY + GameConfig.NUGGET_PICKUP_BONUS

        val iterator = mutableNuggets.iterator()
        while (iterator.hasNext()) {
            val nugget = iterator.next()
            val closestX = nugget.x.coerceIn(left, right)
            val closestY = nugget.y.coerceIn(top, bottom)
            val dx = nugget.x - closestX
            val dy = nugget.y - closestY
            if (dx * dx + dy * dy <= GameConfig.NUGGET_RADIUS * GameConfig.NUGGET_RADIUS) {
                iterator.remove()
                addScore(GameConfig.NUGGET_SCORE, nugget.x, nugget.y - GameConfig.NUGGET_RADIUS)
            }
        }
    }

    private fun scorePassedCacti() {
        for (cactus in mutableCacti) {
            if (!cactus.passed && cactus.x + cactus.width < GameConfig.DINO_X) {
                cactus.passed = true
                addScore(GameConfig.CACTUS_SCORE, cactus.x + cactus.width / 2f, GameConfig.GROUND_Y - cactus.height - 12f)
            }
        }
    }

    internal fun addScore(points: Int, popX: Float = GameConfig.DINO_X, popY: Float = GameConfig.GROUND_Y - GameConfig.DINO_HEIGHT - 20f) {
        score += points
        mutablePops.add(ScorePop(popX, popY, "+$points"))
        val reached = score / GameConfig.MILESTONE_STEP
        if (reached > milestone) {
            milestone = reached
            celebrationTimer = GameConfig.CELEBRATION_SECONDS
        }
    }

    private fun updatePops(dt: Float) {
        mutablePops.forEach {
            it.y -= GameConfig.POP_RISE_SPEED * dt
            it.timeLeft -= dt
        }
        mutablePops.removeAll { it.timeLeft <= 0f }
    }

    private fun hitsCactus(): Boolean {
        val inset = GameConfig.HITBOX_FORGIVENESS
        val dinoInsetX = GameConfig.DINO_WIDTH * inset
        val dinoInsetY = GameConfig.DINO_HEIGHT * inset
        val dinoLeft = GameConfig.DINO_X + dinoInsetX
        val dinoRight = GameConfig.DINO_X + GameConfig.DINO_WIDTH - dinoInsetX
        val dinoTop = dinoBottomY - GameConfig.DINO_HEIGHT + dinoInsetY
        val dinoBottom = dinoBottomY - dinoInsetY

        for (cactus in mutableCacti) {
            val cactusInsetX = cactus.width * inset
            val cactusInsetY = cactus.height * inset
            val cactusLeft = cactus.x + cactusInsetX
            val cactusRight = cactus.x + cactus.width - cactusInsetX
            val cactusTop = GameConfig.GROUND_Y - cactus.height + cactusInsetY
            val cactusBottom = GameConfig.GROUND_Y

            if (dinoLeft < cactusRight && dinoRight > cactusLeft &&
                dinoTop < cactusBottom && dinoBottom > cactusTop
            ) {
                return true
            }
        }
        return false
    }

    private fun endGame() {
        phase = GamePhase.GameOver
        timeSinceGameOver = 0f
        isNewRecord = score > bestScore
        // Nothing is persisted yet: a qualifying run first asks the player
        // for a name (submitRecordName), then lands on the leaderboard.
        awaitingRecordName = leaderboard.qualifies(score)
    }

    /**
     * Records the finished run under the given name (a random kid-friendly
     * name if blank) and returns the name actually used.
     */
    fun submitRecordName(rawName: String): String {
        if (!awaitingRecordName) return lastRecordName ?: ""
        val name = LeaderboardCodec.sanitizeName(rawName).ifBlank { RandomNames.next(random) }
        lastRecordName = name
        lastRecordRank = leaderboard.add(name, score)
        bestScore = leaderboard.best
        awaitingRecordName = false
        return name
    }

    private fun randomBetween(from: Float, until: Float): Float =
        from + random.nextFloat() * (until - from)

    internal fun debugAddCactus(cactus: Cactus) {
        mutableCacti.add(cactus)
    }

    internal fun debugAddNugget(nugget: Nugget) {
        mutableNuggets.add(nugget)
    }

    internal fun debugDisableSpawning() {
        distanceToNextCactus = Float.MAX_VALUE
        distanceToNextNugget = Float.MAX_VALUE
    }
}
