package com.orisgames.dino.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneThemeTest {

    @Test
    fun scenesCycleWithLevel() {
        assertEquals(SceneThemes.DAY, SceneThemes.forLevel(1))
        assertEquals(SceneThemes.CLOUDY, SceneThemes.forLevel(2))
        assertEquals(SceneThemes.SUNSET, SceneThemes.forLevel(3))
        assertEquals(SceneThemes.NIGHT, SceneThemes.forLevel(4))
        assertEquals(SceneThemes.RAIN, SceneThemes.forLevel(5))
        assertEquals(SceneThemes.STORM, SceneThemes.forLevel(6))
        assertEquals(SceneThemes.SNOW, SceneThemes.forLevel(7))
        assertEquals(SceneThemes.DAY, SceneThemes.forLevel(8), "cycle wraps around")
        assertEquals(SceneThemes.STORM, SceneThemes.forLevel(13))
    }

    @Test
    fun eachWeatherTypeAppearsInTheCycle() {
        val weathers = SceneThemes.ALL.map { it.weather }.toSet()
        assertTrue(weathers.containsAll(listOf(Weather.Rain, Weather.Storm, Weather.Snow)))
    }

    @Test
    fun outOfRangeLevelsDoNotCrash() {
        assertEquals(SceneThemes.SNOW, SceneThemes.forLevel(0), "level-1 blend source wraps backwards")
        SceneThemes.forLevel(-5)
        SceneThemes.forLevel(1000)
    }

    @Test
    fun blendEndpointsReturnTheExactThemes() {
        assertEquals(SceneThemes.DAY, SceneThemes.blend(SceneThemes.SNOW, SceneThemes.DAY, 1f))
        assertEquals(SceneThemes.SNOW, SceneThemes.blend(SceneThemes.SNOW, SceneThemes.DAY, 0f))
        val mid = SceneThemes.blend(SceneThemes.DAY, SceneThemes.NIGHT, 0.5f)
        assertEquals(SceneThemes.NIGHT.night, mid.night, "target scene decides night effects")
    }
}
