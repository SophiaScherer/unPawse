package com.example.unpawse.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two pure functions behind cat detection. Both were written to be testable and neither was
 * tested; [sensitivityToMinConfidence] in particular is now user-visible — the Settings slider
 * renders the gate it produces — so a change to the mapping is a change to what the app promises.
 */
class CatDetectorTest {

    @Test
    fun `a frame at or above the gate is a cat`() {
        assertTrue(classify(catConfidence = 0.7f, minConfidence = 0.7f).isCat)
        assertTrue(classify(catConfidence = 0.95f, minConfidence = 0.7f).isCat)
    }

    @Test
    fun `a frame below the gate is not`() {
        assertFalse(classify(catConfidence = 0.69f, minConfidence = 0.7f).isCat)
        assertFalse(classify(catConfidence = 0f, minConfidence = 0.7f).isCat)
    }

    @Test
    fun `the raw confidence is reported either way`() {
        assertEquals(0.42f, classify(0.42f, 0.7f).confidence, 0.0001f)
        assertEquals(0.99f, classify(0.99f, 0.7f).confidence, 0.0001f)
    }

    /** Higher sensitivity accepts a cat more readily, i.e. produces a *lower* gate. */
    @Test
    fun `sensitivity maps inversely to the confidence gate`() {
        assertEquals(0.9f, sensitivityToMinConfidence(0f), 0.0001f)
        assertEquals(0.7f, sensitivityToMinConfidence(0.5f), 0.0001f)
        assertEquals(0.5f, sensitivityToMinConfidence(1f), 0.0001f)
    }

    @Test
    fun `out of range sensitivity is clamped rather than extrapolated`() {
        assertEquals(0.9f, sensitivityToMinConfidence(-1f), 0.0001f)
        assertEquals(0.5f, sensitivityToMinConfidence(2f), 0.0001f)
    }

    /**
     * The five slider stops are the whole point of the discrete steps — they must land on round
     * percentages, or the readout beside the slider reads as noise.
     */
    @Test
    fun `each slider stop lands on a round gate`() {
        val stops = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        val gates = stops.map { sensitivityToMinConfidence(it) }

        assertEquals(listOf(0.9f, 0.8f, 0.7f, 0.6f, 0.5f), gates.map { Math.round(it * 100) / 100f })
    }
}
