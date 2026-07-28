package com.orisgames.dino.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orisgames.dino.game.GameConfig
import com.orisgames.dino.game.GameEngine
import com.orisgames.dino.game.GamePhase
import com.orisgames.dino.net.GlobalLeaderboardClient
import com.orisgames.dino.storage.LeaderboardStorage
import com.orisgames.dino.storage.ScoreEntry
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.launch

@Composable
fun GameScreen(storage: LeaderboardStorage) {
    val engine = remember { GameEngine(storage) }
    var frameTick by remember { mutableLongStateOf(0L) }
    var jumpKeyHeld by remember { mutableStateOf(false) }
    var showLeaderboard by remember { mutableStateOf(false) }
    var globalTop by remember { mutableStateOf<List<ScoreEntry>?>(null) }
    var globalFailed by remember { mutableStateOf(false) }
    val globalClient = remember { GlobalLeaderboardClient(GameConfig.GLOBAL_LEADERBOARD_URL) }
    val scope = rememberCoroutineScope()
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

    // Keep the game focused for keyboard input, and re-grab focus after the
    // name-entry field goes away.
    LaunchedEffect(engine.awaitingRecordName) {
        if (!engine.awaitingRecordName) focusRequester.requestFocus()
    }

    // On launch: fetch the global list for the WORLD line.
    // NOTE: we intentionally do NOT auto-upload this device's local records.
    // Doing so re-injected old (easy-mode) high scores back onto the global
    // board every launch. New scores reach the board only via a real run's
    // submitName() below.
    LaunchedEffect(Unit) {
        if (globalClient.isEnabled) {
            runCatching { globalTop = globalClient.top() }
        }
    }

    // Refresh the global list every time the panel opens.
    LaunchedEffect(showLeaderboard) {
        if (showLeaderboard && globalClient.isEnabled) {
            globalFailed = false
            runCatching { globalClient.top() }
                .onSuccess { globalTop = it }
                .onFailure { globalFailed = true }
        }
    }

    fun submitName(raw: String) {
        val score = engine.score
        val name = engine.submitRecordName(raw)
        if (globalClient.isEnabled && name.isNotBlank()) {
            scope.launch {
                runCatching { globalTop = globalClient.submit(name, score) }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF9ADCF0))
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                val isJumpKey = event.key == Key.Spacebar || event.key == Key.DirectionUp
                val blocked = showLeaderboard || engine.awaitingRecordName
                when {
                    // Latch on KeyDown so OS auto-repeat can't fire extra
                    // taps (it would auto-restart right through the
                    // game-over screen while the key is held).
                    isJumpKey && event.type == KeyEventType.KeyDown -> {
                        if (!jumpKeyHeld && !blocked) {
                            jumpKeyHeld = true
                            engine.tap()
                        }
                        !blocked
                    }
                    isJumpKey && event.type == KeyEventType.KeyUp -> {
                        jumpKeyHeld = false
                        !blocked
                    }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                // Raw pointer loop instead of detectTapGestures: every finger
                // that lands counts as a tap, even while another finger is
                // resting on the screen (small kids hold devices that way).
                // Overlay buttons consume their downs before this handler.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val blocked = showLeaderboard || engine.awaitingRecordName
                        event.changes.forEach { change ->
                            if (change.changedToDown() && !blocked) engine.tap()
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
        Hud(
            engine = engine,
            frameTick = frameTick,
            worldBest = globalTop?.firstOrNull()?.score ?: 0,
            onShowLeaderboard = { showLeaderboard = true },
            onSubmitName = ::submitName,
        )
        if (showLeaderboard) {
            LeaderboardOverlay(
                engine = engine,
                globalTop = globalTop,
                globalEnabled = globalClient.isEnabled,
                globalFailed = globalFailed,
                onClose = { showLeaderboard = false },
            )
        }
    }
}

/**
 * A press-to-activate button that CONSUMES its pointer events, so pressing it
 * never doubles as a jump/start tap in the global handler.
 */
private fun Modifier.pressButton(onPress: () -> Unit): Modifier = pointerInput(onPress) {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        onPress()
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
            if (event.changes.none { it.pressed }) break
        }
    }
}

@Composable
private fun Hud(
    engine: GameEngine,
    frameTick: Long,
    worldBest: Int,
    onShowLeaderboard: () -> Unit,
    onSubmitName: (String) -> Unit,
) {
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
            GameText("MY BEST ${engine.bestScore}", 16.sp, Color(0xE6FFFFFF))
            if (worldBest > 0) {
                GameText("WORLD $worldBest", 16.sp, Color(0xFFFFC93C))
            }
        }

        if (engine.phase != GamePhase.Ready) {
            GameText(
                "LEVEL ${engine.level}",
                20.sp,
                Color(0xFFFFF3B0),
                Modifier.align(Alignment.TopStart),
            )
        }

        if (engine.phase == GamePhase.Running && engine.celebrationTimer > 0f) {
            val progress = engine.celebrationTimer / GameConfig.CELEBRATION_SECONDS
            GameText(
                "LEVEL ${engine.level}!",
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
            GamePhase.GameOver ->
                if (engine.awaitingRecordName) {
                    NameEntryOverlay(engine, onSubmitName)
                } else {
                    GameOverOverlay(engine)
                }
            GamePhase.Running -> Unit
        }

        // TOP 10 lives in the bottom-right corner, away from the centered
        // "TAP TO START" / replay tap zones so it can't be misclicked. Hidden
        // during play and while the name dialog is open.
        val showLeaderboardButton = engine.phase == GamePhase.Ready ||
            (engine.phase == GamePhase.GameOver && !engine.awaitingRecordName)
        if (showLeaderboardButton) {
            PillButton("TOP 10", onShowLeaderboard, Modifier.align(Alignment.BottomEnd))
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
private fun BoxScope.NameEntryOverlay(engine: GameEngine, onSubmit: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val nameFocus = remember { FocusRequester() }
    Surface(
        modifier = Modifier.align(Alignment.Center),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xF2FFFFFF),
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("NEW TOP SCORE!", color = Color(0xFFDB9E0B), fontSize = 26.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text(
                "Score: ${engine.score}",
                color = Color(0xFF2D3748),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(GameConfig.MAX_NAME_LENGTH) },
                singleLine = true,
                placeholder = { Text("Your name") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit(name) }),
                modifier = Modifier.width(230.dp).focusRequester(nameFocus),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Leave empty for a surprise name!",
                color = Color(0xFF718096),
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .pressButton { onSubmit(name) }
                    .background(Color(0xFF38A169), RoundedCornerShape(24.dp))
                    .padding(horizontal = 40.dp, vertical = 12.dp),
            ) {
                Text("SAVE", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
    }
    LaunchedEffect(Unit) {
        nameFocus.requestFocus()
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
            val recordName = engine.lastRecordName
            if (recordName != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Saved as $recordName",
                    color = Color(0xFF718096),
                    fontSize = 14.sp,
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
                    .background(Color(0xFF38A169), CircleShape)
                    .pressButton { engine.tap() },
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

@Composable
private fun BoxScope.LeaderboardOverlay(
    engine: GameEngine,
    globalTop: List<ScoreEntry>?,
    globalEnabled: Boolean,
    globalFailed: Boolean,
    onClose: () -> Unit,
) {
    // Scrim consumes stray taps so nothing reaches the game underneath.
    Box(
        Modifier
            .fillMaxSize()
            .pressButton { }
            .background(Color(0x66000000)),
    )
    Surface(
        modifier = Modifier.align(Alignment.Center),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xF7FFFFFF),
        shadowElevation = 10.dp,
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp, vertical = 22.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("TOP 10", color = Color(0xFF2D6A4F), fontSize = 30.sp, fontWeight = FontWeight.Black)
            val subtitle = when {
                !globalEnabled -> "This device"
                globalFailed -> "Offline — showing this device"
                globalTop == null -> "Loading…"
                else -> "All players"
            }
            Text(subtitle, color = Color(0xFF718096), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))

            val entries = if (globalEnabled && !globalFailed && globalTop != null) {
                globalTop
            } else {
                engine.leaderboard.entries
            }
            if (entries.isEmpty()) {
                Text(
                    "No scores yet — go play!",
                    color = Color(0xFF2D3748),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            entries.take(GameConfig.LEADERBOARD_SIZE).forEachIndexed { index, entry ->
                Row(
                    Modifier.width(250.dp).padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val badgeColor = when (index) {
                        0 -> Color(0xFFE8B70C)
                        1 -> Color(0xFF9EA7B3)
                        2 -> Color(0xFFC98A4B)
                        else -> Color(0xFF74C69D)
                    }
                    Box(
                        Modifier.size(26.dp).background(badgeColor, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${index + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        entry.name,
                        color = Color(0xFF2D3748),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(140.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${entry.score}",
                        color = Color(0xFF2D6A4F),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            PillButton("BACK", onClose)
        }
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .pressButton(onClick)
            .background(Color(0xB3FFFFFF), RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(label, color = Color(0xFF2D6A4F), fontSize = 16.sp, fontWeight = FontWeight.Black)
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
