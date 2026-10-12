package com.benmorrisrains.breakthebricks.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BoardTest {
    @Test
    fun outOfRangeReadsAreEmpty() {
        val board = Board(columns = 7, rows = 9)
        assertNull(board[-1, 0])
        assertNull(board[7, 0])
        assertNull(board[0, 9])
    }

    @Test
    fun storesAndListsBricks() {
        val board = Board(columns = 7, rows = 9)
        board[3, 2] = Brick(column = 3, row = 2, hits = 5)
        assertEquals(5, board[3, 2]?.hits)
        assertEquals(1, board.bricks().count())
        board.clear()
        assertEquals(0, board.bricks().count())
    }
}
