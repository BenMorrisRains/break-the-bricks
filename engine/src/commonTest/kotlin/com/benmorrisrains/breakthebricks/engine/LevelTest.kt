package com.benmorrisrains.breakthebricks.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class LevelTest {
    private val level = Level(
        number = 1, name = "Test", seed = 42, rowCount = 3,
        minBricksPerRow = 1, maxBricksPerRow = 2, twoStarRounds = 6, threeStarRounds = 4,
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
        assertEquals(3, game.levelRows)
    }

    private fun playToTheEnd(game: BrickGame): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        while (!game.phase.isFinished) {
            playRound(game)
            events += game.drainEvents()
            if (game.round > 100) fail("the level never ended")
        }
        return events
    }

    @Test
    fun newRowsStopAfterTheLevelsRowRounds() {
        val game = level.copy(layout = emptyList()).newGame(DropPhysics { 3.5f })
        repeat(3) { playRound(game) }
        assertEquals(4, game.round)
        assertEquals(RoundPhase.Aiming, game.phase)
        assertTrue(game.board.bricks().none { it.row == 0 }, "no new row in round 4")
        assertEquals(4, game.ballCount, "still one more ball every round")
    }

    @Test
    fun breakingEveryBrickAfterTheLastRowClearsTheLevel() {
        val game = level.newGame(ClearPhysics())
        val startingHits = game.board.bricks().sumOf { it.hits }
        val events = playToTheEnd(game)
        assertEquals(RoundPhase.LevelComplete, game.phase)
        assertTrue(game.board.bricks().none(), "the board is clear")
        assertTrue(game.round >= level.rowCount)
        assertEquals(GameEvent.LevelCompleted(game.round, game.points), events.last())
        assertTrue(game.points >= startingHits)
        // Nothing more happens once the level is clear.
        game.fire()
        game.advance(1f)
        assertEquals(RoundPhase.LevelComplete, game.phase)
        assertEquals(emptyList(), game.drainEvents())
    }

    @Test
    fun anEmptyBoardBeforeTheLastRowIsNotAClear() {
        val game = level.copy(layout = emptyList(), maxBricksPerRow = 1).newGame(ClearPhysics())
        playRound(game) // one ball breaks round 1's only brick
        assertTrue(game.board.bricks().all { it.row == 0 }, "only round 2's new row is left")
        assertEquals(RoundPhase.Aiming, game.phase)
        assertEquals(2, game.round)
    }

    @Test
    fun outlastingTheRowsWithBricksLeftIsNotAWin() {
        // Ben's playtest bug: the old goal said "clear" while bricks were still standing.
        val events = playToTheEnd(level.newGame(DropPhysics { 3.5f }))
        assertTrue(events.none { it is GameEvent.LevelCompleted })
        assertTrue(events.last() is GameEvent.GameOver)
    }

    @Test
    fun aBrickOnTheFloorBeforeTheGoalFailsTheLevel() {
        val deep = level.copy(rowCount = 20, layout = List(7) { if (it == 6) List(7) { 1L } else List(7) { null } })
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
    fun starsComeFromClearingInFewerRounds() {
        assertEquals(0, level.stars(passed = false, rounds = 3))
        assertEquals(3, level.stars(passed = true, rounds = 3))
        assertEquals(3, level.stars(passed = true, rounds = 4))
        assertEquals(2, level.stars(passed = true, rounds = 5))
        assertEquals(2, level.stars(passed = true, rounds = 6))
        assertEquals(1, level.stars(passed = true, rounds = 7))
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
