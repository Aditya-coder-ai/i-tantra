package com.talkmitra.offlinevoice.network

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * High-level message delivery states.
 */
enum class MessageDeliveryStatus {
    CREATED,
    ENCRYPTED,
    QUEUED,
    SENT,
    DELIVERED,
    ACKNOWLEDGED,
    FAILED
}

data class DeliveryUpdate(
    val messageId: String,
    val status: MessageDeliveryStatus,
    val latencyMs: Long = 0L,
    val errorMessage: String? = null
)

/**
 * Tracks outgoing message deliveries, measures real network round-trip latency,
 * and handles application-level delivery acknowledgements.
 */
class DeliveryManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    private val _deliveryUpdates = MutableSharedFlow<DeliveryUpdate>(extraBufferCapacity = 128)
    val deliveryUpdates: Flow<DeliveryUpdate> = _deliveryUpdates.asSharedFlow()

    private val inFlightSendTimes = ConcurrentHashMap<String, Long>()
    private val inFlightDeferreds = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    private var totalSentCount = 0L
    private var totalAckedCount = 0L
    private var lastMeasuredLatencyMs = 0L
    private var totalLatencySum = 0L

    /**
     * Marks a message as QUEUED (e.g. while offline).
     */
    fun markQueued(messageId: String) {
        scope.launch {
            _deliveryUpdates.emit(
                DeliveryUpdate(messageId, MessageDeliveryStatus.QUEUED)
            )
        }
    }

    /**
     * Registers a message as SENT over the radio transport and starts ACK timeout tracking.
     */
    fun registerSent(
        messageId: String,
        timeoutMs: Long = 6000L,
        onTimeout: (() -> Unit)? = null
    ): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        val sendTime = System.currentTimeMillis()

        inFlightSendTimes[messageId] = sendTime
        inFlightDeferreds[messageId] = deferred
        totalSentCount++

        scope.launch {
            _deliveryUpdates.emit(
                DeliveryUpdate(messageId, MessageDeliveryStatus.SENT)
            )

            // Timeout watchdog
            delay(timeoutMs)
            if (inFlightDeferreds.remove(messageId) != null) {
                inFlightSendTimes.remove(messageId)
                deferred.complete(false)
                _deliveryUpdates.emit(
                    DeliveryUpdate(messageId, MessageDeliveryStatus.FAILED, errorMessage = "ACK timed out after ${timeoutMs}ms")
                )
                onTimeout?.invoke()
            }
        }

        return deferred
    }

    /**
     * Called when a valid ACK packet is received from the remote peer.
     */
    fun handleAckReceived(ack: AckPayload) {
        val sendTime = inFlightSendTimes.remove(ack.messageId)
        val deferred = inFlightDeferreds.remove(ack.messageId)

        val latency = if (sendTime != null) {
            val measured = System.currentTimeMillis() - sendTime
            lastMeasuredLatencyMs = measured
            totalLatencySum += measured
            measured
        } else {
            0L
        }

        totalAckedCount++
        deferred?.complete(true)

        Log.i(TAG, "✓ Message ${ack.messageId} ACK received! Network Latency = ${latency}ms")

        scope.launch {
            _deliveryUpdates.emit(
                DeliveryUpdate(
                    messageId = ack.messageId,
                    status = MessageDeliveryStatus.DELIVERED,
                    latencyMs = latency
                )
            )
        }
    }

    val messagesSentCount: Long get() = totalSentCount
    val messagesAckedCount: Long get() = totalAckedCount
    val lastLatency: Long get() = lastMeasuredLatencyMs
    val averageLatency: Long
        get() = if (totalAckedCount > 0) totalLatencySum / totalAckedCount else 0L

    companion object {
        private const val TAG = "DeliveryManager"
    }
}
