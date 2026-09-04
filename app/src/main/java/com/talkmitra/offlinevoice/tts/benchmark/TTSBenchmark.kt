package com.talkmitra.offlinevoice.tts.benchmark

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log
import com.talkmitra.offlinevoice.tts.TTSEngine
import com.talkmitra.offlinevoice.tts.TTSLanguage

/**
 * TTS performance benchmarking utility.
 *
 * Measures real metrics — does NOT fabricate values.
 * Results are reported via [TTSBenchmarkResult] for display in the
 * debug UI.
 */
class TTSBenchmark(
    private val context: Context,
    private val engine: TTSEngine
) {
    companion object {
        private const val TAG = "TTSBenchmark"

        /**
         * Representative test sentences for each language.
         * Used for standardised benchmarking across devices.
         */
        val TEST_SENTENCES: Map<TTSLanguage, List<String>> = mapOf(
            TTSLanguage.HINDI to listOf(
                "मुझे मदद चाहिए।",
                "आग लग गई है, कृपया मदद भेजिए।",
                "यह एक परीक्षण संदेश है। कृपया इसे अनदेखा करें।"
            ),
            TTSLanguage.GUJARATI to listOf(
                "મને મદદ જોઈએ.",
                "આગ લાગી છે, કૃપા કરીને મદદ મોકલો.",
                "આ એક પરીક્ષણ સંદેશ છે."
            ),
            TTSLanguage.MARATHI to listOf(
                "मला मदत हवी आहे.",
                "आग लागली आहे, कृपया मदत पाठवा.",
                "हा एक चाचणी संदेश आहे."
            ),
            TTSLanguage.KANNADA to listOf(
                "ನನಗೆ ಸಹಾಯ ಬೇಕು.",
                "ಬೆಂಕಿ ಹೊತ್ತಿಕೊಂಡಿದೆ, ದಯವಿಟ್ಟು ಸಹಾಯ ಕಳುಹಿಸಿ.",
                "ಇದು ಪರೀಕ್ಷಾ ಸಂದೇಶ."
            ),
            TTSLanguage.MALAYALAM to listOf(
                "എനിക്ക് സഹായം വേണം.",
                "തീ പിടിച്ചു, ദയവായി സഹായം അയയ്ക്കുക.",
                "ഇത് ഒരു പരീക്ഷണ സന്ദേശമാണ്."
            ),
            TTSLanguage.TAMIL to listOf(
                "எனக்கு உதவி வேண்டும்.",
                "தீ பிடித்துவிட்டது, தயவுசெய்து உதவி அனுப்புங்கள்.",
                "இது ஒரு சோதனை செய்தி."
            ),
            TTSLanguage.TELUGU to listOf(
                "నాకు సహాయం కావాలి.",
                "అగ్ని అంటుకుంది, దయచేసి సహాయం పంపండి.",
                "ఇది ఒక పరీక్ష సందేశం."
            ),
            TTSLanguage.ODIA to listOf(
                "ମୋତେ ସାହାଯ୍ୟ ଦରକାର।",
                "ନିଆଁ ଲାଗିଛି, ଦୟାକରି ସାହାଯ୍ୟ ପଠାନ୍ତୁ।",
                "ଏହା ଏକ ପରୀକ୍ଷା ସନ୍ଦେଶ।"
            ),
            TTSLanguage.BENGALI to listOf(
                "আমার সাহায্য দরকার।",
                "আগুন লেগেছে, দয়া করে সাহায্য পাঠান।",
                "এটি একটি পরীক্ষার বার্তা।"
            ),
            TTSLanguage.ENGLISH to listOf(
                "I need help.",
                "There is a fire in building five. Please send someone immediately.",
                "This is a test message. Please disregard this message."
            )
        )
    }

    /**
     * Runs a benchmark for a single text in a single language.
     *
     * @return [TTSBenchmarkResult] with real measurements.
     */
    fun measureTTS(text: String, language: TTSLanguage): TTSBenchmarkResult {
        Log.i(TAG, "Benchmarking ${language.displayName}: \"${text.take(40)}…\"")

        // Measure RAM before
        val ramBefore = getUsedMemoryBytes()

        // Synthesise
        val result = engine.synthesize(text, language)

        // Measure RAM after
        val ramAfter = getUsedMemoryBytes()

        // Get model info
        val modelInfo = engine.getVoiceInfo(language)

        return TTSBenchmarkResult(
            language = language,
            modelName = modelInfo?.voiceName ?: language.code,
            modelSizeBytes = modelInfo?.modelSizeBytes ?: -1L,
            inputText = text,
            textLength = text.length,
            sentenceCount = result.totalSentences,
            processingTimeMs = result.processingTimeMs,
            audioDurationMs = result.audioDurationMs,
            realTimeFactor = result.realTimeFactor,
            ramBeforeBytes = ramBefore,
            ramAfterBytes = ramAfter,
            sampleRate = result.sampleRate,
            totalSamples = result.audioData.size,
            quantization = modelInfo?.quantization?.label ?: "unknown"
        )
    }

    /**
     * Runs the full benchmark suite for a single language.
     * Tests all representative sentences.
     */
    fun benchmarkLanguage(language: TTSLanguage): List<TTSBenchmarkResult> {
        val sentences = TEST_SENTENCES[language] ?: return emptyList()
        return sentences.map { measureTTS(it, language) }
    }

    /**
     * Runs benchmarks for all languages that have models available.
     */
    fun benchmarkAll(): Map<TTSLanguage, List<TTSBenchmarkResult>> {
        val availableLanguages = engine.getSupportedLanguages()
        val results = mutableMapOf<TTSLanguage, List<TTSBenchmarkResult>>()

        for (language in availableLanguages) {
            try {
                results[language] = benchmarkLanguage(language)
            } catch (e: Exception) {
                Log.e(TAG, "Benchmark failed for ${language.displayName}: ${e.message}")
            }
        }

        return results
    }

    /**
     * Generates a human evaluation test procedure document.
     */
    fun generateEvaluationProcedure(): String = buildString {
        appendLine("# TTS Human Evaluation Test Procedure")
        appendLine()
        appendLine("## Instructions")
        appendLine("1. For each language, play the test sentences.")
        appendLine("2. Rate each on a 1–5 scale:")
        appendLine("   - 1 = Unintelligible")
        appendLine("   - 2 = Mostly unintelligible, some words recognisable")
        appendLine("   - 3 = Understandable with effort")
        appendLine("   - 4 = Clear and understandable")
        appendLine("   - 5 = Natural-sounding, clear pronunciation")
        appendLine()
        appendLine("## Criteria")
        appendLine("- **Pronunciation**: Are words pronounced correctly?")
        appendLine("- **Clarity**: Can the message be understood on first listen?")
        appendLine("- **Naturalness**: Does it sound like natural speech?")
        appendLine("- **Volume**: Is the volume appropriate?")
        appendLine("- **Flow**: Do sentences flow naturally?")
        appendLine("- **Numbers**: Are numbers spoken correctly (if present)?")
        appendLine("- **Emergency**: Is the emergency message clearly distinguishable?")
        appendLine()
        appendLine("## Test Sentences")
        appendLine()

        for (language in TTSLanguage.values()) {
            appendLine("### ${language.displayName} (${language.code}) — ${language.nativeName}")
            val sentences = TEST_SENTENCES[language] ?: continue
            for ((i, sentence) in sentences.withIndex()) {
                appendLine("${i + 1}. `$sentence`")
                appendLine("   - Pronunciation: ___/5")
                appendLine("   - Clarity: ___/5")
                appendLine("   - Naturalness: ___/5")
            }
            appendLine()
        }
    }

    // ── Memory measurement ───────────────────────────────────────────

    private fun getUsedMemoryBytes(): Long {
        return try {
            val runtime = Runtime.getRuntime()
            runtime.totalMemory() - runtime.freeMemory()
        } catch (_: Exception) {
            -1L
        }
    }
}
