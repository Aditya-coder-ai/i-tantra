package com.talkmitra.offlinevoice.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryManagerTest {

    @Test
    fun testRegisterSentAndHandleAck() = runBlocking {
        val deliveryManager = DeliveryManager()
        val deferred = deliveryManager.registerSent("msg_abc_123")

        assertEquals(1L, deliveryManager.messagesSentCount)
        assertEquals(0L, deliveryManager.messagesAckedCount)

        // Simulate ACK from remote phone
        deliveryManager.handleAckReceived(AckPayload(messageId = "msg_abc_123"))

        val delivered = deferred.await()
        assertTrue(delivered)
        assertEquals(1L, deliveryManager.messagesAckedCount)
        assertTrue(deliveryManager.lastLatency >= 0L)
    }
}
