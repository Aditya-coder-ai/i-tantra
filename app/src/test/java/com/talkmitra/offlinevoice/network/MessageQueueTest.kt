package com.talkmitra.offlinevoice.network

import com.talkmitra.offlinevoice.security.EncryptedMessagePacket
import com.talkmitra.offlinevoice.text.MessagePriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageQueueTest {

    private fun createDummyPacket(id: String): EncryptedMessagePacket {
        return EncryptedMessagePacket(
            version = 1,
            protocolVersion = "VoiceLink-Sec-v1",
            senderId = "VL-A",
            recipientId = "VL-B",
            sessionId = "s1",
            messageId = id,
            sequenceNumber = 1L,
            timestamp = "now",
            priority = MessagePriority.NORMAL,
            nonce = "nonce",
            ciphertext = "cipher",
            authenticationTag = "tag"
        )
    }

    @Test
    fun testEnqueueAndDrainFIFO() {
        val queue = MessageQueue(maxCapacity = 10)
        assertTrue(queue.isEmpty)

        val p1 = createDummyPacket("msg_1")
        val p2 = createDummyPacket("msg_2")
        val p3 = createDummyPacket("msg_3")

        queue.enqueue(p1)
        queue.enqueue(p2)
        queue.enqueue(p3)

        assertEquals(3, queue.size)
        assertFalse(queue.isEmpty)

        val drained = queue.drainAll()
        assertEquals(3, drained.size)
        assertEquals("msg_1", drained[0].messageId)
        assertEquals("msg_2", drained[1].messageId)
        assertEquals("msg_3", drained[2].messageId)

        assertTrue(queue.isEmpty)
    }

    @Test
    fun testCapacityBounding() {
        val queue = MessageQueue(maxCapacity = 3)
        queue.enqueue(createDummyPacket("msg_1"))
        queue.enqueue(createDummyPacket("msg_2"))
        queue.enqueue(createDummyPacket("msg_3"))
        queue.enqueue(createDummyPacket("msg_4")) // Evicts msg_1

        assertEquals(3, queue.size)
        val drained = queue.drainAll()
        assertEquals("msg_2", drained[0].messageId)
        assertEquals("msg_3", drained[1].messageId)
        assertEquals("msg_4", drained[2].messageId)
    }
}
