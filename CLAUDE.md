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
- The UI reads engine state and drains `GameEvent`s. It never changes game state except through `aim`, `selectAbility`, `unselectAbility`, `fire`, `boost` and `redirect` (a time-freeze swipe).
- Colors come only from `NeonPalette`. No color literals elsewhere.
- Platform features (sound, haptics, storage, purchases, ads) sit behind the interfaces in `shared/.../platform`. Common code never calls platform APIs directly.
- Abilities plug in through the `Ability` interface. Don't special-case an ability inside the physics.

## Game rules that must not drift (Ben's decisions)

- Aim-and-shoot: 7 columns, numbered bricks, one more ball every round.
- The aim line is short and straight. Never show a bounce or trajectory preview.
- No timer. A round ends when every ball is back; the game ends when a brick reaches the floor row.
- The launch point for the next round is where the first ball landed.
- Points: each hit takes 1 off a brick and scores 1 point. New-row values are the Fibonacci number divided by 8, rounded up (Ben, 2026-10-12 playtests), at half speed so each value lasts two rounds in levels (Ben, 2026-10-17: values still climbed too quickly): 1 for rounds 1 to 12, then 2, 2, 3, 3, 5, 5, 7, 7, 12, 12, ... Free play steps up every 3 rounds instead (Ben, 2026-10-17 playtest: too slow to go up): 1, 1, 1, 2, 2, 2, 3, 3, 3, 5, 5, 5, 7, ...
- A new row is never a single brick: at least 2 per row in every level, and 3 to 5 at rank 1 in free play, rising with rank to 5 or 6 (Ben, 2026-10-17: more bricks per row).
- Power-ups (Ben's lineup, 2026-10-13). Each is earned at its round as one copy; its wager target is 5 rounds later. One power-up acts per shot, on that round's first ball (Ben, 2026-10-13: switch power-ups before shooting).
- Power-ups are limited (Ben, 2026-10-17, "this is important"): each copy acts on one shot. They sit in a turning carousel under the launch line, each with its copies left over it ("x3"). Nothing is picked until the player taps one; a second tap unpicks it; the pick lasts one shot; one with no copies left stays dim. In free play the game starts with the player's banked copies (minus the stake, which sits out the game), earning one adds a copy, and each copy used leaves the bank. In a level each power-up (lent, earned or from an ad) acts on one shot only (Ben, 2026-10-17: using it every shot made levels too easy) and levels don't draw on the bank. The lineup:
  - Big ball, round 5 (target 10): the first ball is three times the normal size, and a face hit that overhangs the next brick hits that brick too (Ben, 2026-10-13 playtest: big enough to hit two bricks at a time).
  - Splitter, round 10 (target 15): the first ball splits into 3 on its first hit, and all three fly off in seeded random directions up to about 75 degrees either side of its heading (Ben, 2026-10-13 playtest: "go kind of crazy in different directions").
  - Drill, round 20 (target 25): the first ball breaks every brick it touches, whatever its number, and leaves through the top wall instead of bouncing back (Ben, 2026-10-13). The other balls set the launch point.
  - Grenade ball, round 30 (target 35): the first ball explodes on its first hit and destroys that brick plus 3 to 5 nearby bricks (a seeded pick, nearest first, up to 2 cells away), whatever their numbers (Ben, 2026-10-13 playtest; this replaces the 2026-10-12 hit-brick-only rule). Ben moved it off round 5 because it was too powerful early.
  - Ghost ball, round 40 (target 45): the first ball passes through the bricks in its way, hitting each once, until it reaches open space, then turns solid and bounces around inside the group (Ben, 2026-10-13).
  - Time freeze, round 50 (target 55): on the first ball's first hit, play slows to 15% for 3 seconds and balls still waiting hold back. One swipe in that time sends every flying ball the way the finger went (never flatter than the minimum aim angle) and ends the freeze. Swipes are recorded with their step for replays (Ben's idea).
  - 5-way splitter, round 60 (target 65): like the splitter, but into 5 balls (Ben, 2026-10-13).
  - The earn rounds for ghost ball, time freeze and 5-way splitter are Claude's default (every 10 rounds), not yet confirmed by Ben.
- No real-money wagers. Players stake earned abilities. A wager is won by reaching the ability's target round.
- Abilities are never sold for money.
- In a game, the player can watch a rewarded ad for one power-up (Ben, 2026-10-13). It is one copy for that game only, used on the next shot, and never enters the inventory, so it can't be staked. One per game (Claude's default).

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

- `engine/src/commonMain/.../engine`: `GameConfig`, `GameEngine` (the contract the UI uses), `Board`, `Brick`, `Ball`, `RoundPhase`, `GameEvent`, `Ability` (plus `BigBall`, `Splitter`, `Drill`, `GrenadeBall`, `GhostBall`, `TimeFreeze`, `SplitterFive`), `SeededRandom`, `RowValues`.
- `shared/src/commonMain/.../ui`: `NeonPalette` and, later, screens and the game canvas.
- `shared/src/commonMain/.../platform`: `AudioPlayer`, `Haptics`, `KeyValueStore`, `RewardedAds` (AdMob), `Purchases` (RevenueCat) and no-op or fake defaults. Store and ad IDs come from Gradle properties (Android) and `iosApp/Configuration/Config.xcconfig` (iOS), never from code.
- `androidApp`, `iosApp`, `desktopApp`: thin entry points that call `App()`.
