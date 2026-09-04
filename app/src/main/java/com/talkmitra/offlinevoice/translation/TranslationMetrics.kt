package com.talkmitra.offlinevoice.translation

import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe telemetry and performance metrics for offline machine translation.
 */
class TranslationMetrics {

    private val _totalTranslations = AtomicLong(0L)
    val totalTranslations: Long get() = _totalTranslations.get()

    private val _totalTranslationTimeNanos = AtomicLong(0L)
    val totalTranslationTimeMs: Long get() = _totalTranslationTimeNanos.get() / 1_000_000L

    private val _lastTranslationTimeMs = AtomicLong(0L)
    val lastTranslationTimeMs: Long get() = _lastTranslationTimeMs.get()

    private val _directTranslationsCount = AtomicLong(0L)
    val directTranslationsCount: Long get() = _directTranslationsCount.get()

    private val _pivotTranslationsCount = AtomicLong(0L)
    val pivotTranslationsCount: Long get() = _pivotTranslationsCount.get()

    private val _emergencyTranslationsCount = AtomicLong(0L)
    val emergencyTranslationsCount: Long get() = _emergencyTranslationsCount.get()

    private val _passthroughCount = AtomicLong(0L)
    val passthroughCount: Long get() = _passthroughCount.get()

    private val _failureCount = AtomicLong(0L)
    val failureCount: Long get() = _failureCount.get()

    private val _charactersProcessed = AtomicLong(0L)
    val charactersProcessed: Long get() = _charactersProcessed.get()

    val averageLatencyMs: Long
        get() {
            val count = _totalTranslations.get()
            return if (count > 0) totalTranslationTimeMs / count else 0L
        }

    fun recordTranslation(
        durationNanos: Long,
        charCount: Int,
        path: TranslationPath,
        isEmergency: Boolean = false
    ) {
        _totalTranslations.incrementAndGet()
        _totalTranslationTimeNanos.addAndGet(durationNanos)
        _lastTranslationTimeMs.set(durationNanos / 1_000_000L)
        _charactersProcessed.addAndGet(charCount.toLong())

        when (path) {
            TranslationPath.DIRECT -> _directTranslationsCount.incrementAndGet()
            TranslationPath.PIVOT_ENGLISH -> _pivotTranslationsCount.incrementAndGet()
            TranslationPath.SAME_LANGUAGE_PASSTHROUGH -> _passthroughCount.incrementAndGet()
            TranslationPath.FALLBACK_ORIGINAL -> _failureCount.incrementAndGet()
        }

        if (isEmergency) {
            _emergencyTranslationsCount.incrementAndGet()
        }
    }

    fun recordFailure() {
        _failureCount.incrementAndGet()
    }

    fun reset() {
        _totalTranslations.set(0L)
        _totalTranslationTimeNanos.set(0L)
        _lastTranslationTimeMs.set(0L)
        _directTranslationsCount.set(0L)
        _pivotTranslationsCount.set(0L)
        _emergencyTranslationsCount.set(0L)
        _passthroughCount.set(0L)
        _failureCount.set(0L)
        _charactersProcessed.set(0L)
    }
}
