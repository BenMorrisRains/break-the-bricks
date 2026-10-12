# Break The Bricks: rules for agents

Read this before changing anything. The design spec is the plan doc:
https://claude.ai/code/artifact/7f98ed93-56a6-4749-a3e7-a9c925093f9f
If code and the doc disagree, the doc wins; if the doc is silent, ask Ben in your thread.

## How work happens

- One task from the plan's task list (T1 to T14) per thread, one branch per task, one PR per task.
- Keep PRs small and focused on that task. Don't refactor other tasks' code in passing.
- Ben merges. Never merge, force-push `main`, or skip CI.
- A PR is ready for review only when CI is green and the PR description says what changed and how it was tested.

## Architecture rules

- `engine` is pure Kotlin. No Compose, Android, iOS or `java.*` imports, no clock, no threads.
- All randomness goes through `SeededRandom`. Never use `kotlin.random`, `Math.random` or the time in game logic.
- Physics runs at a fixed `GameConfig.STEPS_PER_SECOND`. Frame time is only fed to `GameEngine.advance`.
- The same seed plus the same inputs must always give the same game. Keep it that way, and test it.
- The UI reads engine state and drains `GameEvent`s. It never changes game state except through `aim` and `fire`.
- Colors come only from `NeonPalette`. No color literals elsewhere.
- Platform features (sound, haptics, storage, purchases, ads) sit behind the interfaces in `shared/.../platform`. Common code never calls platform APIs directly.
- Abilities plug in through the `Ability` interface. Don't special-case an ability inside the physics.

## Game rules that must not drift (Ben's decisions)

- Aim-and-shoot: 7 columns, numbered bricks, one more ball every round.
- The aim line is short and straight. Never show a bounce or trajectory preview.
- No timer. A round ends when every ball is back; the game ends when a brick reaches the floor row.
- The launch point for the next round is where the first ball landed.
- Points: each hit takes 1 off a brick and scores 1 point. New-row values follow Fibonacci by round.
- Grenade ball: earned at round 5. From then on, the first ball each round explodes on its first hit, destroying that brick and up to the 5 nearest bricks.
- No real-money wagers. Players stake earned abilities. A wager is won by reaching the ability's target round.
- Abilities are never sold for money.

## Tests

- Every engine change ships with `commonTest` tests in `engine/src/commonTest`.
- Run before pushing: `./gradlew :engine:jvmTest :shared:jvmTest :desktopApp:compileKotlin`.
- CI also builds the Android APK and the iOS simulator app and runs engine tests on iOS.

## Cloud sessions

The default Claude cloud environment can't reach `dl.google.com` (Google Maven). That blocks the Android Gradle plugin, the Android SDK, and the `androidx` libraries that Compose itself depends on. In that case:

- Only `:engine` builds locally, and only from a copy whose settings drop `google()`, `androidApp` and the Android plugin lines. `:shared` and `:desktopApp` can't compile there.
- Verify everything else with the CI run on your PR.
- Say in the PR which checks you ran locally and which you left to CI.
- Allowing `dl.google.com` and `maven.google.com` in the cloud environment's network settings removes this limit, except for iOS, which always needs CI or a Mac.

## Layout

- `engine/src/commonMain/.../engine`: `GameConfig`, `GameEngine` (the contract the UI uses), `Board`, `Brick`, `Ball`, `RoundPhase`, `GameEvent`, `Ability`, `SeededRandom`, `RowValues`.
- `shared/src/commonMain/.../ui`: `NeonPalette` and, later, screens and the game canvas.
- `shared/src/commonMain/.../platform`: `AudioPlayer`, `Haptics`, `KeyValueStore` and no-op defaults.
- `androidApp`, `iosApp`, `desktopApp`: thin entry points that call `App()`.
