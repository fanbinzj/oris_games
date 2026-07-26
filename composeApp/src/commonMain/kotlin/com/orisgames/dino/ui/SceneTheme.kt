package com.orisgames.dino.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

enum class Weather { Clear, Rain, Snow }

/** Colors and effects for one background scene. */
data class SceneTheme(
    val skyTop: Color,
    val skyBottom: Color,
    val grass: Color,
    val dirt: Color,
    val speckle: Color,
    val cloud: Color,
    val sunColor: Color?, // null = no sun disc (rain, night)
    val night: Boolean = false,
    val weather: Weather = Weather.Clear,
)

/** Scenes cycle with the challenge level: every level-up changes the world. */
object SceneThemes {
    val DAY = SceneTheme(
        skyTop = Color(0xFF9ADCF0),
        skyBottom = Color(0xFFD8F3FA),
        grass = Color(0xFF7CB342),
        dirt = Color(0xFFC8975F),
        speckle = Color(0xFFB07F4A),
        cloud = Color(0xE6FFFFFF),
        sunColor = Color(0xFFFFE08A),
    )
    val SUNSET = SceneTheme(
        skyTop = Color(0xFFFF9E7D),
        skyBottom = Color(0xFFFFDCA8),
        grass = Color(0xFF8D9F3E),
        dirt = Color(0xFFB98455),
        speckle = Color(0xFFA06F42),
        cloud = Color(0xF2FFE8D6),
        sunColor = Color(0xFFFFB74D),
    )
    val NIGHT = SceneTheme(
        skyTop = Color(0xFF1B2A52),
        skyBottom = Color(0xFF3C5480),
        grass = Color(0xFF46704F),
        dirt = Color(0xFF74604A),
        speckle = Color(0xFF5E4E3B),
        cloud = Color(0x66C7D4EC),
        sunColor = null,
        night = true,
    )
    val RAIN = SceneTheme(
        skyTop = Color(0xFF5C6E80),
        skyBottom = Color(0xFFA3B7C4),
        grass = Color(0xFF5F8F3E),
        dirt = Color(0xFFA57F52),
        speckle = Color(0xFF8F6C42),
        cloud = Color(0xFF77899A),
        sunColor = null,
        weather = Weather.Rain,
    )
    val SNOW = SceneTheme(
        skyTop = Color(0xFFBFD8E8),
        skyBottom = Color(0xFFEDF6FB),
        grass = Color(0xFFEAF3F9),
        dirt = Color(0xFFB6C6D4),
        speckle = Color(0xFF93A9BC),
        cloud = Color(0xF0FFFFFF),
        sunColor = Color(0xFFF5F0C8),
        weather = Weather.Snow,
    )

    val ALL = listOf(DAY, SUNSET, NIGHT, RAIN, SNOW)

    fun forLevel(level: Int): SceneTheme {
        val index = ((level - 1) % ALL.size + ALL.size) % ALL.size
        return ALL[index]
    }

    /** Linear crossfade between two scenes (weather/night handled by alpha at draw time). */
    fun blend(from: SceneTheme, to: SceneTheme, t: Float): SceneTheme {
        if (t >= 1f) return to
        if (t <= 0f) return from
        return to.copy(
            skyTop = lerp(from.skyTop, to.skyTop, t),
            skyBottom = lerp(from.skyBottom, to.skyBottom, t),
            grass = lerp(from.grass, to.grass, t),
            dirt = lerp(from.dirt, to.dirt, t),
            speckle = lerp(from.speckle, to.speckle, t),
            cloud = lerp(from.cloud, to.cloud, t),
        )
    }
}
