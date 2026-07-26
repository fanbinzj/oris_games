package com.orisgames.dino.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.use
import com.orisgames.dino.game.Cactus
import com.orisgames.dino.game.GameConfig
import com.orisgames.dino.game.GameEngine
import com.orisgames.dino.game.Nugget
import com.orisgames.dino.storage.InMemoryLeaderboardStorage
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders every scene theme offscreen. Serves as a smoke test that the
 * canvas code works for all levels, and drops PNGs into
 * build/theme-previews/ for eyeballing.
 */
class ThemePreviewTest {

    @OptIn(ExperimentalTime::class)
    @Test
    fun rendersEveryThemeWithoutCrashing() {
        val outDir = File("build/theme-previews").apply { mkdirs() }
        for (level in 1..SceneThemes.ALL.size) {
            val engine = GameEngine(InMemoryLeaderboardStorage(), Random(1))
            engine.start()
            engine.debugDisableSpawning()
            if (level > 1) {
                engine.addScore((level - 1) * GameConfig.MILESTONE_STEP)
            }
            // Settle the level-up crossfade and advance weather animations.
            var t = 0f
            while (t < 2.5f) {
                engine.update(1f / 60f)
                t += 1f / 60f
            }
            engine.debugAddCactus(Cactus(x = 500f, width = 36f, height = 60f))
            engine.debugAddNugget(Nugget(x = 620f, y = GameConfig.GROUND_Y - 150f))

            ImageComposeScene(width = 800, height = 450).use { scene ->
                scene.setContent {
                    val textMeasurer = rememberTextMeasurer()
                    Canvas(Modifier.fillMaxSize()) {
                        drawGame(engine, textMeasurer)
                    }
                }
                val image = scene.render(time = Duration.ZERO)
                val png = image.encodeToData(EncodedImageFormat.PNG)
                assertTrue(png != null && png.bytes.isNotEmpty(), "level $level rendered empty image")
                File(outDir, "theme-level-$level.png").writeBytes(png.bytes)
            }
        }
    }
}
