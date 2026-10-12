package com.benmorrisrains.breakthebricks.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NeonPaletteTest {
    @Test
    fun toughestBricksAreMagentaAndWeakestAreCyan() {
        assertEquals(NeonPalette.Magenta, NeonPalette.brickColor(hits = 55, roundValue = 55))
        assertEquals(NeonPalette.Cyan, NeonPalette.brickColor(hits = 1, roundValue = 55))
    }

    @Test
    fun earlyRoundsAreCyan() {
        assertEquals(NeonPalette.Cyan, NeonPalette.brickColor(hits = 1, roundValue = 1))
    }
}
