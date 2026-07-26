package com.orisgames.dino.game

enum class GamePhase { Ready, Running, GameOver }

class Cactus(
    var x: Float,
    val width: Float,
    val height: Float,
    var passed: Boolean = false,
)

class Nugget(
    var x: Float,
    val y: Float,
)

class ScorePop(
    var x: Float,
    var y: Float,
    val text: String,
    var timeLeft: Float = GameConfig.POP_SECONDS,
)
