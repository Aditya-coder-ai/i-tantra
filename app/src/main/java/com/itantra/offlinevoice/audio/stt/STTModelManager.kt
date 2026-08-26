package com.itantra.offlinevoice.audio.stt

import android.content.Context
import java.io.File

/**
 * Manages STT model files on disk.
 * Models are stored in: /data/user/0/com.itantra.offlinevoice/files/models/stt/{lang}/
 */
class STTModelManager(private val context: Context) {

    fun getModelPath(language: STTLanguage): String {
        return File(context.filesDir, "models/stt/${language.code}").absolutePath
    }

    fun isModelReady(language: STTLanguage): Boolean {
        val dir = File(getModelPath(language))
        if (!dir.exists() || !dir.isDirectory) return false
        
        // Vosk models usually contain multiple files or subdirectories like 'am' and 'conf'
        val files = dir.list() ?: return false
        return files.isNotEmpty()
    }

    // These paths are specific to Sherpa-ONNX, keeping them for reference if needed, 
    // but Vosk usually takes the directory path itself.
    fun getVoskModelPath(language: STTLanguage): String = getModelPath(language)
}
