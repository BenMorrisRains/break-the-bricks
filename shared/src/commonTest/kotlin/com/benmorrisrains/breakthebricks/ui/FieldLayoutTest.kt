package com.benmorrisrains.breakthebricks.ui

import com.benmorrisrains.breakthebricks.engine.GameConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FieldLayoutTest {
    private val config = GameConfig()

    @Test
    fun tallScreensFitTheWidthAndCenterVertically() {
        val layout = FieldLayout(config, widthPx = 700f, heightPx = 2000f)
        assertEquals(100f, layout.cell)
        assertEquals(0f, layout.originX)
        assertEquals((2000f - 100f * (config.rows + FieldLayout.BELOW_FLOOR_CELLS)) / 2f, layout.originY)
    }

    @Test
    fun wideScreensFitTheHeightAndCenterHorizontally() {
        val layout = FieldLayout(config, widthPx = 2000f, heightPx = 1000f)
        assertEquals(100f, layout.cell)
        assertEquals((2000f - 700f) / 2f, layout.originX)
        assertEquals(0f, layout.originY)
        assertEquals(900f, layout.floorY)
    }

    @Test
    fun screenAndWorldRoundTrip() {
        val layout = FieldLayout(config, widthPx = 1080f, heightPx = 1700f)
        assertEquals(2.25f, layout.toWorldX(layout.toScreenX(2.25f)), 1e-4f)
        assertEquals(6.5f, layout.toWorldY(layout.toScreenY(6.5f)), 1e-4f)
    }

    @Test
    fun zeroSizeIsEmpty() {
        assertTrue(FieldLayout(config, 0f, 0f).isEmpty)
    }
}
