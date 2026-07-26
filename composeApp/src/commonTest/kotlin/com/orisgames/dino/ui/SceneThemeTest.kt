package com.orisgames.dino.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SceneThemeTest {

    @Test
    fun scenesCycleWithLevel() {
        assertEquals(SceneThemes.DAY, SceneThemes.forLevel(1))
        assertEquals(SceneThemes.SUNSET, SceneThemes.forLevel(2))
        assertEquals(SceneThemes.NIGHT, SceneThemes.forLevel(3))
        assertEquals(SceneThemes.RAIN, SceneThemes.forLevel(4))
        assertEquals(SceneThemes.SNOW, SceneThemes.forLevel(5))
        assertEquals(SceneThemes.DAY, SceneThemes.forLevel(6), "cycle wraps around")
        assertEquals(SceneThemes.RAIN, SceneThemes.forLevel(9))
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
