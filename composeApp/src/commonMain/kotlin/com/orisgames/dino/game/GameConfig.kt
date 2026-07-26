package com.orisgames.dino.game

object GameConfig {
    const val WORLD_WIDTH = 800f
    const val WORLD_HEIGHT = 450f
    const val GROUND_Y = 370f

    const val DINO_X = 110f
    const val DINO_WIDTH = 56f
    const val DINO_HEIGHT = 64f

    const val GRAVITY = 2500f
    const val JUMP_VELOCITY = -1050f

    const val BASE_SPEED = 280f
    const val MAX_SPEED = 620f
    const val SPEED_PER_POINT = 0.35f

    const val CACTUS_SCORE = 10
    const val NUGGET_SCORE = 25
    const val MILESTONE_STEP = 100

    // Reaction gap between consecutive cacti, in seconds of travel time.
    const val MIN_CACTUS_GAP_SECONDS = 1.05f
    const val MAX_CACTUS_GAP_SECONDS = 1.9f
    const val FIRST_CACTUS_DISTANCE = 450f
    // Gaps are converted to distance at an inflated speed so that the travel
    // time never drops below MIN_CACTUS_GAP_SECONDS even if the game speeds
    // up while the gap is in flight.
    const val GAP_SPEED_HEADROOM = 1.3f

    const val CACTUS_MIN_WIDTH = 26f
    const val CACTUS_MAX_WIDTH = 44f
    const val CACTUS_MIN_HEIGHT = 42f
    const val CACTUS_MAX_HEIGHT = 72f

    const val MIN_NUGGET_GAP_SECONDS = 2.4f
    const val MAX_NUGGET_GAP_SECONDS = 4.6f
    // Altitude range (above ground) for nugget centers; only reachable mid-jump.
    const val NUGGET_MIN_ALTITUDE = 115f
    const val NUGGET_MAX_ALTITUDE = 190f
    const val NUGGET_RADIUS = 18f
    const val NUGGET_PICKUP_BONUS = 8f
    // A nugget never spawns closer than this (in seconds of travel) to a
    // cactus, so chasing a nugget cannot bait the player into a death.
    const val NUGGET_CACTUS_CLEARANCE_SECONDS = 0.9f

    // Hitboxes are shrunk by this fraction on every side to be kid-friendly.
    const val HITBOX_FORGIVENESS = 0.18f

    const val SPAWN_MARGIN = 40f
    const val DESPAWN_X = -60f

    const val MAX_FRAME_DELTA = 1f / 20f
    const val RESTART_LOCK_SECONDS = 0.7f
    const val CELEBRATION_SECONDS = 1.6f

    const val POP_SECONDS = 0.9f
    const val POP_RISE_SPEED = 46f

    const val LEADERBOARD_SIZE = 10
    const val MAX_NAME_LENGTH = 12

    // Base URL of the global leaderboard worker (no trailing slash), e.g.
    // "https://dino-leaderboard.<account>.workers.dev". Empty = global
    // leaderboard disabled; the game falls back to the local top list.
    const val GLOBAL_LEADERBOARD_URL = ""
}
