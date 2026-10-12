# Break The Bricks

An aim-and-shoot brick breaker for Android and iOS, built with Compose Multiplatform.

Aim, fire a stream of balls at numbered bricks, and every round the bricks drop a row, a new row appears, and you get one more ball. Brick values grow along the Fibonacci sequence, and players wager earned abilities (like the grenade ball) instead of money.

The full design and technical plan lives in the project's plan doc. Agent rules are in [CLAUDE.md](CLAUDE.md).

## Modules

| Module | What it holds |
| --- | --- |
| `engine` | Pure Kotlin game rules: board, balls, rounds, physics, scoring, abilities. No Compose. |
| `shared` | Compose UI shared by every platform, the neon palette, and platform service interfaces. |
| `androidApp` | Android entry point. |
| `iosApp` | Xcode project that hosts the shared UI. |
| `desktopApp` | Desktop window for fast local iteration. |

## Common commands

```sh
./gradlew :engine:jvmTest            # engine tests
./gradlew :shared:jvmTest            # shared UI logic tests
./gradlew :desktopApp:run            # play on desktop
./gradlew :androidApp:assembleDebug  # Android APK
```

Open `iosApp/iosApp.xcodeproj` in Xcode to run on iOS.
