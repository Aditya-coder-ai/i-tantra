package com.talkmitra.offlinevoice.tts

/**
 * Sealed exception hierarchy for TTS errors.
 *
 * Mirrors [com.talkmitra.offlinevoice.audio.stt.STTException] style so error
 * handling is consistent across the audio pipeline.
 */
sealed class TTSException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The ONNX model file for the requested language was not found on disk. */
    class ModelNotFoundException(val language: TTSLanguage) :
        TTSException("TTS_ERROR_MODEL_NOT_FOUND: Model for ${language.displayName} (${language.code}) not found on device.")

    /** The requested language code is not in [TTSLanguage]. */
    class LanguageUnsupportedException(val languageCode: String) :
        TTSException("TTS_ERROR_LANGUAGE_UNSUPPORTED: Language code '$languageCode' is not supported.")

    /** The TTS runtime failed to initialise (native library missing, corrupt model, etc.). */
    class InitializationException(detail: String, cause: Throwable? = null) :
        TTSException("TTS_ERROR_INITIALIZATION: $detail", cause)

    /** The device ran out of memory while loading a model or during inference. */
    class OutOfMemoryException(detail: String, cause: Throwable? = null) :
        TTSException("TTS_ERROR_OUT_OF_MEMORY: $detail", cause)

    /** AudioTrack or AudioFocus request failed. */
    class PlaybackFailedException(detail: String, cause: Throwable? = null) :
        TTSException("TTS_ERROR_PLAYBACK_FAILED: $detail", cause)

    /** The input text was null, empty, or otherwise invalid. */
    class InvalidTextException(detail: String) :
        TTSException("TTS_ERROR_INVALID_TEXT: $detail")

    /** Inference produced an error or returned no audio. */
    class InferenceException(detail: String, cause: Throwable? = null) :
        TTSException("TTS_ERROR_INFERENCE: $detail", cause)
}
