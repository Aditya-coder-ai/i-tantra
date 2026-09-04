package com.talkmitra.offlinevoice.text

/**
 * Classifies a message's type and priority.
 *
 * **Critical safety rule:** Emergency classification requires explicit user confirmation.
 * Automatic keyword detection only produces a *suggestion* flag — it never labels a
 * message as [MessageType.EMERGENCY] without [userConfirmedEmergency] == true.
 */
class MessageClassifier {

    data class ClassificationResult(
        val type: MessageType,
        val priority: MessagePriority,
        /** True when emergency keywords were detected but user has not confirmed. */
        val emergencySuggested: Boolean
    )

    /**
     * Classifies a message.
     *
     * @param text The cleaned message text.
     * @param userConfirmedEmergency If true, the user has explicitly confirmed this is an emergency.
     *        This flag always takes precedence over keyword detection.
     * @return A [ClassificationResult] with type, priority, and suggestion flag.
     */
    fun classifyMessage(text: String, userConfirmedEmergency: Boolean = false): ClassificationResult {
        if (userConfirmedEmergency) {
            return ClassificationResult(
                type = MessageType.EMERGENCY,
                priority = MessagePriority.CRITICAL,
                emergencySuggested = false
            )
        }

        val containsEmergencyKeyword = hasEmergencyKeywords(text)

        return ClassificationResult(
            type = MessageType.NORMAL,
            priority = MessagePriority.NORMAL,
            emergencySuggested = containsEmergencyKeyword
        )
    }

    /**
     * Lightweight multilingual keyword scan. These keywords act as a *suggestion
     * trigger only* — the UI can prompt "Did you mean to send an emergency?" but
     * the pipeline will NOT auto-label EMERGENCY based on keywords alone.
     */
    private fun hasEmergencyKeywords(text: String): Boolean {
        val lower = text.lowercase()
        return EMERGENCY_KEYWORDS.any { keyword -> lower.contains(keyword) }
    }

    private companion object {
        /** Multilingual emergency keyword list for suggestion detection only. */
        val EMERGENCY_KEYWORDS = listOf(
            // English
            "help", "fire", "accident", "emergency", "danger", "sos",
            // Hindi
            "मदद", "आग", "दुर्घटना", "आपातकाल", "खतरा", "बचाओ",
            // Gujarati
            "મદદ", "આગ", "અકસ્માત", "ખતરો",
            // Marathi
            "मदत", "आग", "अपघात", "धोका",
            // Kannada
            "ಸಹಾಯ", "ಬೆಂಕಿ", "ಅಪಘಾತ", "ಅಪಾಯ",
            // Malayalam
            "സഹായം", "തീ", "അപകടം",
            // Tamil
            "உதவி", "தீ", "விபத்து", "ஆபத்து",
            // Telugu
            "సహాయం", "అగ్ని", "ప్రమాదం",
            // Odia
            "ସାହାଯ୍ୟ", "ଅଗ୍ନି", "ଦୁର୍ଘଟଣା",
            // Bengali
            "সাহায্য", "আগুন", "দুর্ঘটনা", "বিপদ"
        )
    }
}
