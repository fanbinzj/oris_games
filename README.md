# oris_games — Dino Nugget Run

A small endless-runner game for kids, built once in Kotlin and shipped to
multiple platforms with Kotlin Multiplatform + Compose Multiplatform:

- **Android** — native APK
- **Web** — Kotlin/Wasm, runs in any modern browser
- **Desktop (JVM)** — used for fast day-to-day testing on macOS

## Gameplay

The dino runs forward automatically. Tap (or press Space / Arrow Up) to jump.

- Jump over cacti to survive — landing on one ends the run.
- Chicken nuggets with wings fly by at jump height; eat them for bonus points.
- Every 100 points is a milestone celebration.
- When you die, your score is compared with your saved best score.

## Requirements

- JDK 17+ (this machine: Temurin 21 in `~/.jdks/jdk-21.0.11+10`; the system
  default java 15 is too old — export `JAVA_HOME` accordingly)
- Android SDK (only for the Android target); location read from `local.properties`

## Run / build

```bash
export JAVA_HOME="$HOME/.jdks/jdk-21.0.11+10/Contents/Home"

# Desktop window (fastest dev loop)
./gradlew :composeApp:run

# JVM unit tests for the game engine
./gradlew :composeApp:jvmTest

# Web: dev server with hot reload at http://localhost:8080
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Web: production static site → composeApp/build/dist/wasmJs/productionExecutable/
# (deployable as-is to GitHub Pages or any static host)
./gradlew :composeApp:wasmJsBrowserDistribution

# Android debug APK → composeApp/build/outputs/apk/debug/
./gradlew :composeApp:assembleDebug
```

## Project layout

```
composeApp/
├── src/commonMain/kotlin/com/orisgames/dino/
│   ├── game/        # pure-Kotlin engine: physics, spawning, collision, scoring
│   ├── ui/          # Compose canvas rendering + HUD (shared by all platforms)
│   ├── storage/     # HighScoreStorage interface
│   └── App.kt
├── src/commonTest/  # engine unit tests (run with jvmTest)
├── src/androidMain/ # MainActivity, SharedPreferences storage
├── src/jvmMain/     # desktop window entry, java.util.prefs storage
└── src/wasmJsMain/  # browser entry, localStorage storage, index.html
```

Game logic lives in `game/GameEngine.kt` and is UI-free (world units,
seconds, injected `Random`), so it is fully unit-testable. Tuning knobs are
all in `game/GameConfig.kt`.
