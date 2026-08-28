package com.itantra.offlinevoice.translation

import android.util.Log
import com.itantra.offlinevoice.text.MessagePriority
import com.itantra.offlinevoice.text.ProcessedMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.PriorityBlockingQueue

/**
 * Work item in the priority translation queue.
 */
data class TranslationWorkItem(
    val message: ProcessedMessage,
    val targetLanguage: SupportedLanguage,
    val isEmergency: Boolean = message.priority == MessagePriority.CRITICAL,
    val deferred: CompletableDeferred<TranslationResult> = CompletableDeferred(),
    val queuedTimestamp: Long = System.currentTimeMillis()
) : Comparable<TranslationWorkItem> {

    override fun compareTo(other: TranslationWorkItem): Int {
        // 1. Emergency priority items always take precedence
        if (this.isEmergency && !other.isEmergency) return -1
        if (!this.isEmergency && other.isEmergency) return 1

        // 2. FIFO order for items with identical priority
        return this.queuedTimestamp.compareTo(other.queuedTimestamp)
    }
}

/**
 * Priority translation queue that processes emergency communications with lowest latency.
 */
class TranslationQueue(
    private val translationEngine: TranslationEngine,
    val capacity: Int = 100,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    private val queue = PriorityBlockingQueue<TranslationWorkItem>(capacity)
    private var workerJob: Job? = null

    val size: Int get() = queue.size
    val isEmpty: Boolean get() = queue.isEmpty()

    init {
        startWorker()
    }

    /**
     * Enqueues a message for translation and suspends until translation completes.
     */
    suspend fun enqueueAndWait(
        message: ProcessedMessage,
        targetLanguage: SupportedLanguage
    ): TranslationResult {
        if (queue.size >= capacity) {
            throw TranslationQueueFullException(capacity)
        }

        val workItem = TranslationWorkItem(
            message = message,
            targetLanguage = targetLanguage
        )

        if (workItem.isEmergency) {
            Log.w(TAG, "🚨 Enqueued EMERGENCY message ${message.messageId} with TOP priority for translation")
        } else {
            Log.d(TAG, "Enqueued normal message ${message.messageId} for translation")
        }

        queue.offer(workItem)
        return workItem.deferred.await()
    }

    private fun startWorker() {
        workerJob?.cancel()
        workerJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val item = queue.take() // Blocks until an item is available
                    val sourceLang = SupportedLanguage.fromCode(item.message.language)

                    val result = translationEngine.translate(
                        text = item.message.text,
                        sourceLanguage = sourceLang,
                        targetLanguage = item.targetLanguage,
                        isEmergency = item.isEmergency
                    )

                    item.deferred.complete(result)
                } catch (e: Exception) {
                    if (isActive) {
                        Log.e(TAG, "Error in translation worker: ${e.message}", e)
                    }
                }
            }
        }
    }

    fun clear() {
        queue.clear()
    }

    fun shutdown() {
        workerJob?.cancel()
        queue.clear()
    }

    companion object {
        private const val TAG = "TranslationQueue"
    }
}
