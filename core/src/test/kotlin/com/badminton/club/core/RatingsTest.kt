package com.badminton.club.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RatingsTest {
    @Test
    fun winnerGainsAndDeltasSumToZero() {
        val before = mapOf("A" to 1000.0, "B" to 1000.0, "C" to 1000.0, "D" to 1000.0)
        val after = applyResult(before, listOf("A", "B"), listOf("C", "D"), 21, 15)
        for (p in listOf("A", "B")) {
            assertTrue(after.getValue(p) > before.getValue(p))
        }
        for (p in listOf("C", "D")) {
            assertTrue(after.getValue(p) < before.getValue(p))
        }
        val totalBefore = before.values.sum()
        val totalAfter = after.values.sum()
        assertEquals(0.0, Math.round((totalAfter - totalBefore) * 1e6) / 1e6, 0.0)
    }

    @Test
    fun expectedScoreSymmetry() {
        assertEquals(
            1 - expectedScore(1200.0, 1000.0),
            expectedScore(1000.0, 1200.0),
            1e-9,
        )
    }

    @Test
    fun tieRejected() {
        try {
            applyResult(mapOf("A" to 1000.0, "B" to 1000.0), listOf("A"), listOf("B"), 21, 21)
            fail("expected RatingException")
        } catch (_: RatingException) {
        }
    }

    @Test
    fun scoreOutOfRangeRejected() {
        try {
            applyResult(mapOf("A" to 1000.0, "B" to 1000.0), listOf("A"), listOf("B"), 31, 20)
            fail("expected RatingException")
        } catch (_: RatingException) {
        }
    }

    @Test
    fun biggerMarginBiggerChange() {
        val before = mapOf("A" to 1000.0, "B" to 1000.0)
        val close = applyResult(before, listOf("A"), listOf("B"), 21, 20)
        val blowout = applyResult(before, listOf("A"), listOf("B"), 21, 5)
        assertTrue(blowout.getValue("A") - before.getValue("A") >
            close.getValue("A") - before.getValue("A"))
    }

    @Test
    fun upsetWinAwardsMore() {
        val before = mapOf("A" to 900.0, "B" to 1100.0)
        val after = applyResult(before, listOf("A"), listOf("B"), 21, 18)
        assertTrue(after.getValue("A") - before.getValue("A") > 0)
    }
}
