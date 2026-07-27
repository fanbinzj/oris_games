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
    // High ceiling so the game keeps getting faster through ~level 21 instead
    // of flattening early. Per-frame travel at this speed (1200/60 = 20 units)
    // stays well under a cactus's width, so collisions never tunnel.
    const val MAX_SPEED = 1200f
    // Each level-up (every MILESTONE_STEP points) is a distinct, noticeable
    // difficulty step: faster world, tighter gaps, taller and wider cacti.
    const val SPEED_STEP_PER_LEVEL = 46f
    const val CACTUS_GAP_MIN_STEP_PER_LEVEL = 0.02f
    const val CACTUS_GAP_MAX_STEP_PER_LEVEL = 0.08f
    // Floors kept just above the jump air-time (0.84s) so a perfectly-timed
    // player can always survive; the challenge comes from speed + width.
    const val MIN_CACTUS_GAP_FLOOR = 0.92f
    const val MAX_CACTUS_GAP_FLOOR = 1.25f
    const val CACTUS_HEIGHT_STEP_PER_LEVEL = 4f
    const val CACTUS_MAX_HEIGHT_CAP = 96f
    // Wider cacti at higher levels narrow the jump window -> more precise
    // timing required. Always clearable: jump covers speed*0.84 >> any width.
    const val CACTUS_WIDTH_STEP_PER_LEVEL = 2.6f
    const val CACTUS_MAX_WIDTH_CAP = 74f

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

    // One nugget spawns in the middle of every cactus gap. It hovers high
    // enough that a grounded dino runs safely under it, so ignoring it is
    // never a death — only jumping for it carries risk/reward.
    const val NUGGET_MIN_ALTITUDE = 120f
    const val NUGGET_MAX_ALTITUDE = 175f
    const val NUGGET_RADIUS = 18f
    const val NUGGET_PICKUP_BONUS = 8f
    // Skip the mid-gap nugget when the gap is too tight for a jump-and-recover,
    // so the game never baits the player into an unavoidable grab. At the
    // levels kids reach, gaps are wider than this, so nearly every gap gets one.
    const val NUGGET_MIN_REACHABLE_GAP_SECONDS = 1.25f

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

    // Must match MAX_SCORE in backend/leaderboard-worker.js: the worker
    // rejects higher scores with 400, so we never try to upload them.
    const val MAX_GLOBAL_SCORE = 50000

    // Base URL of the global leaderboard worker (no trailing slash).
    // Empty = global leaderboard disabled; the game falls back to the
    // local top list.
    const val GLOBAL_LEADERBOARD_URL = "https://dino-leaderboard.oris-games.workers.dev"
}
