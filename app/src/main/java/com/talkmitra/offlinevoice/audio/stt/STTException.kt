package com.talkmitra.offlinevoice.audio.stt

sealed class STTException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ModelNotFoundException(language: STTLanguage) : STTException("Model for ${language.displayName} not found on device.")
    class InitializationException(message: String) : STTException(message)
    class InferenceException(message: String) : STTException(message)
}
