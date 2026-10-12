package com.benmorrisrains.breakthebricks.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LabelsTest {
    @Test
    fun smallValuesShowInFull() {
        assertEquals("1", formatBrickValue(1))
        assertEquals("610", formatBrickValue(610))
        assertEquals("6765", formatBrickValue(6765))
    }

    @Test
    fun largeValuesShorten() {
        assertEquals("10K", formatBrickValue(10_946))
        assertEquals("832K", formatBrickValue(832_040))
        assertEquals("1.3M", formatBrickValue(1_346_269))
        assertEquals("2M", formatBrickValue(2_000_000))
        assertEquals("63B", formatBrickValue(63_245_986_000))
        assertTrue(formatBrickValue(Long.MAX_VALUE).length <= 5)
    }

    @Test
    fun longerLabelsGetSmallerText() {
        assertTrue(brickFontFraction("832K") < brickFontFraction("55"))
        assertEquals(brickFontFraction("1"), brickFontFraction("21"))
    }

    @Test
    fun pointsGetThousandsSeparators() {
        assertEquals("0", formatPoints(0))
        assertEquals("999", formatPoints(999))
        assertEquals("1,179", formatPoints(1179))
        assertEquals("12,345,678", formatPoints(12_345_678))
    }
}
