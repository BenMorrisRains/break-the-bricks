package com.benmorrisrains.breakthebricks.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class LevelTest {
    private val level = Level(
        number = 1, name = "Test", seed = 42, goalRounds = 3,
        minBricksPerRow = 1, maxBricksPerRow = 2, twoStarPoints = 10, threeStarPoints = 20,
        layout = listOf(listOf(null, 5L, null, null, null, null, 9L)),
    )

    private fun playRound(game: BrickGame, angle: Float = 1.0f) {
        game.aim(angle)
        game.fire()
        var steps = 0
        while (game.phase != RoundPhase.Aiming && !game.phase.isFinished) {
            game.step()
            if (++steps > 1_000_000) fail("round ${game.round} never ended")
        }
    }

    @Test
    fun layoutSitsBelowRoundOnesRow() {
        val game = level.newGame(DropPhysics { 3.5f })
        assertEquals(5L, game.board[1, 1]?.hits)
        assertEquals(9L, game.board[6, 1]?.hits)
        assertEquals(1, game.board[1, 1]?.row)
        val newRow = game.board.bricks().filter { it.row == 0 }.toList()
        assertTrue(newRow.size in 1..2)
        assertEquals(2 + newRow.size, game.board.bricks().count())
    }

    @Test
    fun levelSettingsShapeTheNewRows() {
        val game = level.newGame(DropPhysics { 3.5f })
        assertEquals(1, game.config.minBricksPerRow)
        assertEquals(2, game.config.maxBricksPerRow)
        assertEquals(3, game.goalRounds)
    }

    @Test
    fun survivingTheGoalRoundsCompletesTheLevel() {
        val game = level.newGame(DropPhysics { 3.5f })
        repeat(2) {
            playRound(game)
            assertEquals(RoundPhase.Aiming, game.phase)
        }
        game.drainEvents()
        val rowsBefore = game.board.bricks().map { it.row }.toList()
        playRound(game)
        assertEquals(RoundPhase.LevelComplete, game.phase)
        assertEquals(3, game.round)
        assertEquals(listOf<GameEvent>(GameEvent.BallLanded(3.5f, true), GameEvent.BallLanded(3.5f, false), GameEvent.BallLanded(3.5f, false), GameEvent.LevelCompleted(3, 0)), game.drainEvents())
        // The last round's bricks don't drop, and nothing more happens.
        assertEquals(rowsBefore, game.board.bricks().map { it.row }.toList())
        game.fire()
        game.advance(1f)
        assertEquals(RoundPhase.LevelComplete, game.phase)
        assertEquals(emptyList(), game.drainEvents())
    }

    @Test
    fun aBrickOnTheFloorBeforeTheGoalFailsTheLevel() {
        val deep = level.copy(goalRounds = 20, layout = List(7) { if (it == 6) List(7) { 1L } else List(7) { null } })
        val game = deep.newGame(DropPhysics { 3.5f })
        playRound(game) // the layout's bottom row starts on row 7 and drops onto the floor row
        assertEquals(RoundPhase.GameOver, game.phase)
        assertTrue(game.drainEvents().any { it is GameEvent.GameOver })
    }

    @Test
    fun aLayoutMustFitAboveTheFloor() {
        val tooTall = level.copy(layout = List(8) { List(7) { null } })
        assertFailsWith<IllegalArgumentException> { tooTall.newGame(DropPhysics { 3.5f }) }
        val tooWide = level.copy(layout = listOf(List(8) { null }))
        assertFailsWith<IllegalArgumentException> { tooWide.newGame(DropPhysics { 3.5f }) }
    }

    @Test
    fun valuesAheadMakeNewRowsWorthLaterRounds() {
        val game = level.copy(valuesAhead = 8).newGame(DropPhysics { 3.5f })
        val newRow = game.board.bricks().filter { it.row == 0 }.toList()
        // Round 1 is worth what round 9 is worth: fibonacci(9) = 34, / 8 rounded up = 5.
        assertTrue(newRow.isNotEmpty() && newRow.all { it.hits == 5L })
        assertEquals(1L, level.newGame(DropPhysics { 3.5f }).board.bricks().first { it.row == 0 }.hits)
    }

    @Test
    fun starsComeFromPassingAndPoints() {
        assertEquals(0, level.stars(passed = false, points = 500))
        assertEquals(1, level.stars(passed = true, points = 9))
        assertEquals(2, level.stars(passed = true, points = 10))
        assertEquals(2, level.stars(passed = true, points = 19))
        assertEquals(3, level.stars(passed = true, points = 20))
    }

    @Test
    fun everyAttemptAtALevelPlaysTheSame() {
        val shots = listOf(1.1f, 2.0f, 0.7f)
        val a = LevelBot.replay(level, shots, TestPhysics())
        val b = LevelBot.replay(level, shots, TestPhysics())
        assertEquals(a.phase, b.phase)
        assertEquals(a.points, b.points)
        assertEquals(a.steps, b.steps)
        assertEquals(a.drainEvents(), b.drainEvents())
    }
}
