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
- The UI reads engine state and drains `GameEvent`s. It never changes game state except through `aim`, `selectAbility`, `fire` and `boost`.
- Colors come only from `NeonPalette`. No color literals elsewhere.
- Platform features (sound, haptics, storage, purchases, ads) sit behind the interfaces in `shared/.../platform`. Common code never calls platform APIs directly.
- Abilities plug in through the `Ability` interface. Don't special-case an ability inside the physics.

## Game rules that must not drift (Ben's decisions)

- Aim-and-shoot: 7 columns, numbered bricks, one more ball every round.
- The aim line is short and straight. Never show a bounce or trajectory preview.
- No timer. A round ends when every ball is back; the game ends when a brick reaches the floor row.
- The launch point for the next round is where the first ball landed.
- Points: each hit takes 1 off a brick and scores 1 point. New-row values are the Fibonacci number for the round divided by 8, rounded up (Ben, 2026-10-12 playtests): 1 for rounds 1 to 6, then 2, 3, 5, 7, 12, 18, 30, ...
- Power-ups (Ben's lineup, 2026-10-13). Each is earned at its round and stays available for the rest of the game; its wager target is 5 rounds later. One power-up acts per shot, on that round's first ball: with more than one earned, the player taps one in the HUD tray while aiming to pick it, and the pick stays until changed (Ben, 2026-10-13: switch power-ups before shooting):
  - Big ball, round 5 (target 10): the first ball is three times the normal size, and a face hit that overhangs the next brick hits that brick too (Ben, 2026-10-13 playtest: big enough to hit two bricks at a time).
  - Splitter, round 10 (target 15): the first ball splits into 3 on its first hit, and all three fly off in seeded random directions up to about 75 degrees either side of its heading (Ben, 2026-10-13 playtest: "go kind of crazy in different directions").
  - Drill, round 20 (target 25): the first ball passes through up to 3 bricks, hitting each once, then bounces normally.
  - Grenade ball, round 30 (target 35): the first ball explodes on its first hit and destroys that brick plus 3 to 5 nearby bricks (a seeded pick, nearest first, up to 2 cells away), whatever their numbers (Ben, 2026-10-13 playtest; this replaces the 2026-10-12 hit-brick-only rule). Ben moved it off round 5 because it was too powerful early.
- No real-money wagers. Players stake earned abilities. A wager is won by reaching the ability's target round.
- Abilities are never sold for money.
- In a game, the player can watch a rewarded ad for one power-up (Ben, 2026-10-13). It lasts that game only, starts on the next shot, and never enters the inventory, so it can't be staked. One per game (Claude's default).

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

- `engine/src/commonMain/.../engine`: `GameConfig`, `GameEngine` (the contract the UI uses), `Board`, `Brick`, `Ball`, `RoundPhase`, `GameEvent`, `Ability` (plus `BigBall`, `Splitter`, `Drill`, `GrenadeBall`), `SeededRandom`, `RowValues`.
- `shared/src/commonMain/.../ui`: `NeonPalette` and, later, screens and the game canvas.
- `shared/src/commonMain/.../platform`: `AudioPlayer`, `Haptics`, `KeyValueStore` and no-op defaults.
- `androidApp`, `iosApp`, `desktopApp`: thin entry points that call `App()`.
