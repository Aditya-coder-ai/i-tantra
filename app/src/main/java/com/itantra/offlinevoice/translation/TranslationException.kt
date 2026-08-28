package com.itantra.offlinevoice.translation

/**
 * Root exception for translation subsystem errors.
 */
open class TranslationException(message: String, cause: Throwable? = null) : Exception(message, cause)

class UnsupportedLanguagePairException(val pair: LanguagePair) :
    TranslationException("Unsupported offline translation language pair: ${pair.displayName} (${pair.key})")

class ModelNotFoundException(val modelKey: String) :
    TranslationException("Translation model '$modelKey' is not installed or available offline")

class ModelLoadException(val modelKey: String, cause: Throwable? = null) :
    TranslationException("Failed to load translation model '$modelKey' into memory", cause)

class TranslationTimeoutException(val timeoutMs: Long) :
    TranslationException("Translation operation timed out after ${timeoutMs}ms")

class TranslationQueueFullException(val capacity: Int) :
    TranslationException("Translation priority queue capacity of $capacity exceeded")
