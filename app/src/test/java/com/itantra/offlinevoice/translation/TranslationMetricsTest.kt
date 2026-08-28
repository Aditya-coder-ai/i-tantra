package com.itantra.offlinevoice.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationMetricsTest {

    @Test
    fun testMetricsRecording() {
        val metrics = TranslationMetrics()

        assertEquals(0L, metrics.totalTranslations)
        assertEquals(0L, metrics.averageLatencyMs)

        // Record 1st translation (direct, normal, 20ms)
        metrics.recordTranslation(20_000_000L, 25, TranslationPath.DIRECT, isEmergency = false)
        assertEquals(1L, metrics.totalTranslations)
        assertEquals(20L, metrics.lastTranslationTimeMs)
        assertEquals(1L, metrics.directTranslationsCount)
        assertEquals(0L, metrics.pivotTranslationsCount)
        assertEquals(25L, metrics.charactersProcessed)

        // Record 2nd translation (pivot, emergency, 40ms)
        metrics.recordTranslation(40_000_000L, 35, TranslationPath.PIVOT_ENGLISH, isEmergency = true)
        assertEquals(2L, metrics.totalTranslations)
        assertEquals(40L, metrics.lastTranslationTimeMs)
        assertEquals(1L, metrics.directTranslationsCount)
        assertEquals(1L, metrics.pivotTranslationsCount)
        assertEquals(1L, metrics.emergencyTranslationsCount)
        assertEquals(60L, metrics.charactersProcessed)
        assertEquals(30L, metrics.averageLatencyMs) // (20 + 40) / 2
    }

    @Test
    fun testMetricsReset() {
        val metrics = TranslationMetrics()
        metrics.recordTranslation(15_000_000L, 10, TranslationPath.DIRECT)
        assertTrue(metrics.totalTranslations > 0)

        metrics.reset()
        assertEquals(0L, metrics.totalTranslations)
        assertEquals(0L, metrics.directTranslationsCount)
    }
}
