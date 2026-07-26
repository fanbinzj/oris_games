package com.orisgames.dino.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import com.orisgames.dino.game.Cactus
import com.orisgames.dino.game.GameConfig
import com.orisgames.dino.game.GameEngine
import com.orisgames.dino.game.GamePhase
import com.orisgames.dino.game.Nugget
import com.orisgames.dino.game.ScorePop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

private val CactusColor = Color(0xFF3E9B4F)
private val DinoColor = Color(0xFF38A169)
private val DinoDark = Color(0xFF2F7A3D)
private val DinoBelly = Color(0xFFA8E6C3)
private val NuggetColor = Color(0xFFE9A13B)
private val NuggetLight = Color(0xFFF6C36B)
private val WingColor = Color(0xFFFFF6E0)
private val StarColor = Color(0xFFFFC93C)
private val PopColor = Color(0xFF7A4E00)
private val EyeDark = Color(0xFF1F2933)
private val RainColor = Color(0xB3CFE8FF)
private val SnowColor = Color(0xE6FFFFFF)
private val MoonColor = Color(0xFFF4F1DE)
private val NightStarColor = Color(0xFFFFF8D6)

/**
 * Draws the whole game in world coordinates (800x450, y down). The world is
 * scaled to fit the canvas and anchored to the canvas bottom: tall (portrait)
 * screens get extra sky above the action, and screens wider than 16:9 get
 * centered pillarboxes instead of cropping the top of the jump arc.
 *
 * The scene theme follows the challenge level and crossfades during the
 * level-up celebration.
 */
fun DrawScope.drawGame(engine: GameEngine, textMeasurer: TextMeasurer) {
    val current = SceneThemes.forLevel(engine.level)
    val previous = SceneThemes.forLevel(engine.level - 1)
    // While the level-up celebration runs, fade from the previous scene in.
    val fadeIn = if (engine.milestone > 0 && engine.celebrationTimer > 0f) {
        1f - engine.celebrationTimer / GameConfig.CELEBRATION_SECONDS
    } else {
        1f
    }
    val theme = SceneThemes.blend(previous, current, fadeIn)

    drawRect(brush = Brush.verticalGradient(listOf(theme.skyTop, theme.skyBottom)))

    val scale = min(size.width / GameConfig.WORLD_WIDTH, size.height / GameConfig.WORLD_HEIGHT)
    val offsetX = (size.width - GameConfig.WORLD_WIDTH * scale) / 2f
    val offsetY = size.height - GameConfig.WORLD_HEIGHT * scale

    // Full-width ground strip so pillarboxed sides blend in instead of
    // showing sky at ground level.
    val groundTopPx = offsetY + GameConfig.GROUND_Y * scale
    drawRect(theme.dirt, topLeft = Offset(0f, groundTopPx), size = Size(size.width, size.height - groundTopPx))
    drawRect(theme.grass, topLeft = Offset(0f, groundTopPx), size = Size(size.width, 9f * scale))

    withTransform({
        translate(offsetX, offsetY)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        drawCelestialBodies(previous, current, fadeIn, engine.elapsed)
        drawClouds(engine.distance, theme.cloud)
        drawGround(engine.distance, theme)
        engine.cacti.forEach { drawCactus(it) }
        engine.nuggets.forEach { drawNugget(it, engine.elapsed) }
        drawDino(engine)
        drawWeather(previous.weather, 1f - fadeIn, engine.elapsed)
        drawWeather(current.weather, fadeIn, engine.elapsed)
        if (engine.celebrationTimer > 0f) {
            drawCelebrationStars(engine)
        }
        engine.pops.forEach { drawPop(it, textMeasurer) }
    }
}

/** Sun, moon, and night stars, cross-faded between the two active scenes. */
private fun DrawScope.drawCelestialBodies(
    previous: SceneTheme,
    current: SceneTheme,
    fadeIn: Float,
    elapsed: Float,
) {
    previous.sunColor?.let { drawSun(it, alpha = 1f - fadeIn) }
    current.sunColor?.let { drawSun(it, alpha = fadeIn) }
    if (previous.night) drawNightSky(alpha = 1f - fadeIn, elapsed = elapsed)
    if (current.night) drawNightSky(alpha = fadeIn, elapsed = elapsed)
}

private fun DrawScope.drawSun(color: Color, alpha: Float) {
    if (alpha <= 0f) return
    drawCircle(color.copy(alpha = 0.33f * alpha), radius = 52f, center = Offset(715f, 62f))
    drawCircle(color.copy(alpha = color.alpha * alpha), radius = 38f, center = Offset(715f, 62f))
}

private fun DrawScope.drawNightSky(alpha: Float, elapsed: Float) {
    if (alpha <= 0f) return
    // Crescent moon: bright disc with a sky-side bite taken out.
    drawCircle(MoonColor.copy(alpha = alpha), radius = 30f, center = Offset(700f, 70f))
    drawCircle(SceneThemes.NIGHT.skyTop.copy(alpha = alpha), radius = 26f, center = Offset(712f, 62f))
    for (i in 0 until 24) {
        val x = (i * 179.3f).mod(GameConfig.WORLD_WIDTH)
        val y = 12f + (i * 83.7f).mod(170f)
        val twinkle = 0.5f + 0.5f * sin(elapsed * 2.1f + i * 1.7f)
        drawCircle(
            NightStarColor.copy(alpha = alpha * (0.35f + 0.55f * twinkle)),
            radius = 1.4f + (i % 3) * 0.7f,
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.drawWeather(weather: Weather, alpha: Float, elapsed: Float) {
    if (alpha <= 0f) return
    when (weather) {
        Weather.Clear -> Unit
        Weather.Rain -> {
            for (i in 0 until 46) {
                val y = (i * 211.3f + elapsed * 720f).mod(GameConfig.GROUND_Y + 30f) - 15f
                val x = (i * 97.7f - elapsed * 120f).mod(GameConfig.WORLD_WIDTH + 60f) - 30f
                drawLine(
                    RainColor.copy(alpha = RainColor.alpha * alpha),
                    start = Offset(x, y),
                    end = Offset(x - 4f, y + 15f),
                    strokeWidth = 2f,
                )
            }
            // Deterministic double lightning flash every few seconds.
            val cycle = elapsed.mod(6.7f)
            val flash = when {
                cycle < 0.12f -> 0.32f
                cycle in 0.22f..0.34f -> 0.2f
                else -> 0f
            }
            if (flash > 0f) {
                drawRect(
                    Color.White.copy(alpha = flash * alpha),
                    topLeft = Offset(-2f, -2f),
                    size = Size(GameConfig.WORLD_WIDTH + 4f, GameConfig.WORLD_HEIGHT + 4f),
                )
            }
        }
        Weather.Snow -> {
            for (i in 0 until 36) {
                val fall = 55f + (i % 5) * 13f
                val y = (i * 173.3f + elapsed * fall).mod(GameConfig.GROUND_Y + 24f) - 12f
                val x = (i * 127.1f + sin(elapsed * 1.3f + i) * 18f - elapsed * 25f)
                    .mod(GameConfig.WORLD_WIDTH + 40f) - 20f
                drawCircle(
                    SnowColor.copy(alpha = SnowColor.alpha * alpha),
                    radius = 1.8f + (i % 3),
                    center = Offset(x, y),
                )
            }
        }
    }
}

private fun DrawScope.drawClouds(distance: Float, color: Color) {
    val span = GameConfig.WORLD_WIDTH + 260f
    for (i in 0 until 4) {
        val x = (GameConfig.WORLD_WIDTH + 130f) - ((distance * 0.25f + i * 335f) % span)
        val y = 52f + (i % 3) * 42f
        drawCloud(x, y, color)
    }
}

private fun DrawScope.drawCloud(x: Float, y: Float, color: Color) {
    drawRoundRect(
        color,
        topLeft = Offset(x - 34f, y + 2f),
        size = Size(72f, 18f),
        cornerRadius = CornerRadius(9f),
    )
    drawCircle(color, 18f, Offset(x - 16f, y + 2f))
    drawCircle(color, 24f, Offset(x + 4f, y - 2f))
    drawCircle(color, 15f, Offset(x + 24f, y + 3f))
}

private fun DrawScope.drawGround(distance: Float, theme: SceneTheme) {
    drawRect(
        theme.dirt,
        topLeft = Offset(-2f, GameConfig.GROUND_Y),
        size = Size(GameConfig.WORLD_WIDTH + 4f, GameConfig.WORLD_HEIGHT - GameConfig.GROUND_Y),
    )
    drawRect(
        theme.grass,
        topLeft = Offset(-2f, GameConfig.GROUND_Y),
        size = Size(GameConfig.WORLD_WIDTH + 4f, 9f),
    )

    // Scrolling dirt speckles, indexed in world space so rows stay stable.
    val tile = 48f
    val base = floor(distance / tile).toInt()
    for (j in -1..18) {
        val idx = base + j
        val x = idx * tile - distance
        if (x < -20f || x > GameConfig.WORLD_WIDTH + 20f) continue
        val row = ((idx % 3) + 3) % 3
        drawRoundRect(
            theme.speckle,
            topLeft = Offset(x, GameConfig.GROUND_Y + 24f + row * 14f),
            size = Size(14f, 4f),
            cornerRadius = CornerRadius(2f),
        )
    }
}

private fun DrawScope.drawCactus(cactus: Cactus) {
    val bodyWidth = cactus.width * 0.5f
    val bodyLeft = cactus.x + (cactus.width - bodyWidth) / 2f
    val top = GameConfig.GROUND_Y - cactus.height
    val arm = 7f

    drawRoundRect(
        CactusColor,
        topLeft = Offset(bodyLeft, top),
        size = Size(bodyWidth, cactus.height),
        cornerRadius = CornerRadius(bodyWidth / 2f),
    )
    if (cactus.height >= 54f) {
        val armY = top + cactus.height * 0.32f
        drawRoundRect(
            CactusColor,
            topLeft = Offset(cactus.x, armY + 10f),
            size = Size(bodyLeft - cactus.x + 2f, arm),
            cornerRadius = CornerRadius(3.5f),
        )
        drawRoundRect(
            CactusColor,
            topLeft = Offset(cactus.x, armY - 10f),
            size = Size(arm, 27f),
            cornerRadius = CornerRadius(3.5f),
        )
        val rightEdge = bodyLeft + bodyWidth - 2f
        drawRoundRect(
            CactusColor,
            topLeft = Offset(rightEdge, armY + 24f),
            size = Size(cactus.x + cactus.width - rightEdge, arm),
            cornerRadius = CornerRadius(3.5f),
        )
        drawRoundRect(
            CactusColor,
            topLeft = Offset(cactus.x + cactus.width - arm, armY + 6f),
            size = Size(arm, 25f),
            cornerRadius = CornerRadius(3.5f),
        )
    }
}

private fun DrawScope.drawNugget(nugget: Nugget, elapsed: Float) {
    val x = nugget.x
    val y = nugget.y
    val flap = sin(elapsed * 11f) * 26f

    rotate(degrees = -20f - flap, pivot = Offset(x - 12f, y - 6f)) {
        drawOval(WingColor, topLeft = Offset(x - 34f, y - 12f), size = Size(24f, 12f))
    }
    rotate(degrees = 20f + flap, pivot = Offset(x + 12f, y - 6f)) {
        drawOval(WingColor, topLeft = Offset(x + 10f, y - 12f), size = Size(24f, 12f))
    }

    drawOval(NuggetColor, topLeft = Offset(x - 18f, y - 15f), size = Size(36f, 30f))
    drawOval(NuggetLight, topLeft = Offset(x - 12f, y - 11f), size = Size(20f, 14f))
    drawCircle(Color.White, 2.5f, Offset(x - 7f, y - 8f))
}

private fun DrawScope.drawDino(engine: GameEngine) {
    val left = GameConfig.DINO_X
    val bottom = engine.dinoBottomY
    val top = bottom - GameConfig.DINO_HEIGHT
    val running = engine.phase == GamePhase.Running

    // Legs.
    if (running && !engine.isOnGround) {
        drawRoundRect(DinoColor, Offset(left + 12f, bottom - 12f), Size(12f, 9f), CornerRadius(4f))
        drawRoundRect(DinoColor, Offset(left + 30f, bottom - 12f), Size(12f, 9f), CornerRadius(4f))
    } else {
        val frontUp = running && (engine.distance / 26f).toInt() % 2 == 0
        val backLegHeight = if (frontUp) 14f else 10f
        val frontLegHeight = if (frontUp) 10f else 14f
        drawRoundRect(DinoColor, Offset(left + 10f, bottom - backLegHeight), Size(12f, backLegHeight), CornerRadius(4f))
        drawRoundRect(DinoColor, Offset(left + 32f, bottom - frontLegHeight), Size(12f, frontLegHeight), CornerRadius(4f))
    }

    // Tail.
    val tail = Path().apply {
        moveTo(left + 4f, top + 26f)
        lineTo(left - 16f, top + 14f)
        lineTo(left + 4f, top + 40f)
        close()
    }
    drawPath(tail, DinoColor)

    // Body and belly.
    drawRoundRect(DinoColor, Offset(left, top + 16f), Size(44f, 38f), CornerRadius(14f))
    drawOval(DinoBelly, Offset(left + 8f, top + 30f), Size(24f, 20f))

    // Head with a little bob while running.
    val bob = if (running && engine.isOnGround) sin(engine.distance / 14f) * 1.6f else 0f
    val headTop = top + bob
    drawRoundRect(DinoColor, Offset(left + 24f, headTop), Size(34f, 28f), CornerRadius(10f))

    val eyeCenter = Offset(left + 46f, headTop + 11f)
    if (engine.phase == GamePhase.GameOver) {
        val r = 4.5f
        drawLine(Color.White, Offset(eyeCenter.x - r, eyeCenter.y - r), Offset(eyeCenter.x + r, eyeCenter.y + r), strokeWidth = 2.5f)
        drawLine(Color.White, Offset(eyeCenter.x - r, eyeCenter.y + r), Offset(eyeCenter.x + r, eyeCenter.y - r), strokeWidth = 2.5f)
    } else {
        drawCircle(Color.White, 6f, eyeCenter)
        drawCircle(EyeDark, 3f, Offset(eyeCenter.x + 1.5f, eyeCenter.y))
    }
    drawCircle(DinoDark, 1.8f, Offset(left + 55f, headTop + 20f))
}

private fun DrawScope.drawCelebrationStars(engine: GameEngine) {
    val alpha = (engine.celebrationTimer / GameConfig.CELEBRATION_SECONDS).coerceIn(0f, 1f)
    val rise = (1f - alpha) * 24f
    for (s in 0 until 7) {
        val x = ((engine.milestone * 97 + s * 131) % 720 + 40).toFloat()
        val y = (60 + (engine.milestone * 41 + s * 73) % 170).toFloat() - rise
        val radius = 8f + (s % 3) * 4f
        drawStar(Offset(x, y), radius, StarColor.copy(alpha = alpha))
    }
}

private fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radius else radius * 0.45f
        val angle = (PI / 5 * i - PI / 2).toFloat()
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawPop(pop: ScorePop, textMeasurer: TextMeasurer) {
    val alpha = (pop.timeLeft / GameConfig.POP_SECONDS).coerceIn(0f, 1f)
    drawText(
        textMeasurer = textMeasurer,
        text = pop.text,
        topLeft = Offset(pop.x - 16f, pop.y - 24f),
        style = TextStyle(
            color = PopColor.copy(alpha = alpha),
            // toSp() inverts density and font scale, giving a size fixed in
            // world units regardless of device settings.
            fontSize = 20f.toSp(),
            fontWeight = FontWeight.Black,
        ),
    )
}
