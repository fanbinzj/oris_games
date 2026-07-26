package com.orisgames.dino.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orisgames.dino.game.GameConfig
import com.orisgames.dino.game.GameEngine
import com.orisgames.dino.game.GamePhase
import com.orisgames.dino.storage.HighScoreStorage
import kotlin.math.min
import kotlin.math.sin

@Composable
fun GameScreen(storage: HighScoreStorage) {
    val engine = remember { GameEngine(storage) }
    var frameTick by remember { mutableLongStateOf(0L) }
    var jumpKeyHeld by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    engine.update((now - lastNanos) / 1_000_000_000f)
                }
                lastNanos = now
                frameTick++
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF9ADCF0))
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                val isJumpKey = event.key == Key.Spacebar || event.key == Key.DirectionUp
                when {
                    // Latch on KeyDown so OS auto-repeat can't fire extra
                    // taps (it would auto-restart right through the
                    // game-over screen while the key is held).
                    isJumpKey && event.type == KeyEventType.KeyDown -> {
                        if (!jumpKeyHeld) {
                            jumpKeyHeld = true
                            engine.tap()
                        }
                        true
                    }
                    isJumpKey && event.type == KeyEventType.KeyUp -> {
                        jumpKeyHeld = false
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                // Raw pointer loop instead of detectTapGestures: every finger
                // that lands counts as a tap, even while another finger is
                // resting on the screen (small kids hold devices that way).
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.changedToDown()) engine.tap()
                        }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION")
            frameTick // snapshot read so the canvas redraws every frame
            drawGame(engine, textMeasurer)
        }
        Hud(engine, frameTick)
    }
}

@Composable
private fun Hud(engine: GameEngine, frameTick: Long) {
    @Suppress("UNUSED_EXPRESSION")
    frameTick // snapshot read so the HUD recomposes every frame
    // safeDrawing keeps the HUD clear of the status bar / notch on Android
    // edge-to-edge; it resolves to zero on desktop and web.
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
    ) {
        Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
            GameText("SCORE ${engine.score}", 26.sp)
            GameText("BEST ${engine.bestScore}", 16.sp, Color(0xE6FFFFFF))
        }

        if (engine.phase == GamePhase.Running && engine.celebrationTimer > 0f) {
            val progress = engine.celebrationTimer / GameConfig.CELEBRATION_SECONDS
            GameText(
                "${engine.milestone * GameConfig.MILESTONE_STEP} POINTS!",
                30.sp,
                Color(0xFFFFC93C),
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .graphicsLayer { alpha = min(1f, progress * 2.5f) },
            )
        }

        when (engine.phase) {
            GamePhase.Ready -> ReadyOverlay(engine)
            GamePhase.GameOver -> GameOverOverlay(engine)
            GamePhase.Running -> Unit
        }
    }
}

@Composable
private fun BoxScope.ReadyOverlay(engine: GameEngine) {
    Column(
        Modifier.align(Alignment.Center).padding(bottom = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GameText("DINO NUGGET RUN", 40.sp, Color(0xFFFFFDF5), textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        GameText(
            "Jump over cacti. Catch the flying nuggets!",
            17.sp,
            Color(0xFFF4FBF6),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(30.dp))
        val pulse = 0.55f + 0.45f * sin(engine.elapsed * 5f)
        GameText(
            "TAP TO START",
            24.sp,
            Color(0xFFFFF3B0),
            Modifier.graphicsLayer { alpha = pulse },
        )
    }
}

@Composable
private fun BoxScope.GameOverOverlay(engine: GameEngine) {
    Surface(
        modifier = Modifier.align(Alignment.Center),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xF2FFFFFF),
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 36.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("OOPS!", color = Color(0xFFE25822), fontSize = 34.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Text(
                "Score: ${engine.score}",
                color = Color(0xFF2D3748),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            if (engine.isNewRecord) {
                Text(
                    "NEW RECORD!",
                    color = Color(0xFFDB9E0B),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                )
            } else {
                Text(
                    "Best: ${engine.bestScore}",
                    color = Color(0xFF718096),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(18.dp))
            val ready = engine.timeSinceGameOver >= GameConfig.RESTART_LOCK_SECONDS
            val pulse = 0.55f + 0.45f * sin(engine.elapsed * 5f)
            // Big graphical replay button so pre-readers know what to do.
            val buttonScale = if (ready) 1f + 0.06f * sin(engine.elapsed * 5f) else 1f
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        alpha = if (ready) 1f else 0.35f
                        scaleX = buttonScale
                        scaleY = buttonScale
                    }
                    .background(Color(0xFF38A169), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(32.dp)) {
                    val playArrow = Path().apply {
                        moveTo(size.width * 0.22f, 0f)
                        lineTo(size.width * 0.22f, size.height)
                        lineTo(size.width * 0.95f, size.height / 2f)
                        close()
                    }
                    drawPath(playArrow, Color.White)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Tap to play again",
                color = Color(0xFF38A169),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer { alpha = if (ready) pulse else 0f },
            )
        }
    }
}

private val HudShadow = Shadow(Color(0x59000000), Offset(2f, 3f), 4f)

@Composable
private fun GameText(
    text: String,
    fontSize: TextUnit,
    color: Color = Color.White,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        textAlign = textAlign,
        style = TextStyle(shadow = HudShadow),
    )
}
