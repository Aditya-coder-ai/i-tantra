package com.talkmitra.offlinevoice.audio.stt

import android.content.Context
import java.io.File

/**
 * Manages STT model files on disk.
 * Models are stored in: /data/user/0/com.talkmitra.offlinevoice/files/models/stt/{lang}/
 */
class STTModelManager(private val context: Context) {

    fun getModelPath(language: STTLanguage): String {
        return File(context.filesDir, "models/stt/${language.code}").absolutePath
    }

    fun isModelReady(language: STTLanguage): Boolean {
        val dir = File(getModelPath(language))
        if (!dir.exists()) return false
        
        // Basic check for Sherpa-ONNX non-streaming (Zipformer) files
        // Actual implementation would verify tokens.txt and encoder/decoder/joiner .onnx files
        val files = dir.list() ?: return false
        return files.any { it.endsWith(".onnx") } && files.contains("tokens.txt")
    }

    fun getEncoderPath(language: STTLanguage): String = File(getModelPath(language), "encoder.onnx").absolutePath
    fun getDecoderPath(language: STTLanguage): String = File(getModelPath(language), "decoder.onnx").absolutePath
    fun getJoinerPath(language: STTLanguage): String = File(getModelPath(language), "joiner.onnx").absolutePath
    fun getTokensPath(language: STTLanguage): String = File(getModelPath(language), "tokens.txt").absolutePath
}
