package com.itantra.offlinevoice.network

import com.itantra.offlinevoice.security.EncryptedMessagePacket
import com.itantra.offlinevoice.text.MessagePriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PacketSerializerTest {

    @Test
    fun testSerializeAndDeserializeEncryptedPacket() {
        val packet = EncryptedMessagePacket(
            version = 1,
            protocolVersion = "VoiceLink-Sec-v1",
            senderId = "VL-PHONEA",
            recipientId = "VL-PHONEB",
            sessionId = "sess_12345",
            messageId = "msg_998877",
            sequenceNumber = 42L,
            timestamp = "2026-08-28T12:00:00Z",
            priority = MessagePriority.CRITICAL,
            nonce = "bm9uY2UxMjM0NTY3OA==",
            ciphertext = "Y2lwaGVydGV4dGRhdGE=",
            authenticationTag = "dGFnMTIzNDU2Nzg5MDEyMw=="
        )

        val framedBytes = PacketSerializer.serializeEncryptedPacket(packet)
        assertNotNull(framedBytes)

        val framer = PacketFramer.StreamFramer()
        val frames = framer.pushBytes(framedBytes)
        assertEquals(1, frames.size)
        assertEquals(PacketType.DATA, frames[0].type)

        val restored = PacketSerializer.deserializeEncryptedPacket(frames[0].payload)
        assertEquals(packet.messageId, restored.messageId)
        assertEquals(packet.senderId, restored.senderId)
        assertEquals(packet.recipientId, restored.recipientId)
        assertEquals(packet.priority, restored.priority)
        assertEquals(packet.ciphertext, restored.ciphertext)
        assertEquals(packet.authenticationTag, restored.authenticationTag)
    }

    @Test
    fun testSerializeAndDeserializeAck() {
        val ack = AckPayload(messageId = "msg_test_ack_42", timestamp = 1724840000000L, status = "DELIVERED")
        val framedBytes = PacketSerializer.serializeAck(ack)

        val framer = PacketFramer.StreamFramer()
        val frames = framer.pushBytes(framedBytes)
        assertEquals(1, frames.size)
        assertEquals(PacketType.ACK, frames[0].type)

        val restored = PacketSerializer.deserializeAck(frames[0].payload)
        assertEquals("msg_test_ack_42", restored.messageId)
        assertEquals(1724840000000L, restored.timestamp)
        assertEquals("DELIVERED", restored.status)
    }

    @Test
    fun testSerializeAndDeserializeHandshake() {
        val handshake = HandshakePayload(
            deviceId = "VL-A7F92C",
            displayName = "Adi's Phone",
            publicKeyBase64 = "cHVibGljS2V5QmFzZTY0"
        )
        val framedBytes = PacketSerializer.serializeHandshake(handshake)

        val framer = PacketFramer.StreamFramer()
        val frames = framer.pushBytes(framedBytes)
        assertEquals(1, frames.size)
        assertEquals(PacketType.HANDSHAKE, frames[0].type)

        val restored = PacketSerializer.deserializeHandshake(frames[0].payload)
        assertEquals("VL-A7F92C", restored.deviceId)
        assertEquals("Adi's Phone", restored.displayName)
        assertEquals("cHVibGljS2V5QmFzZTY0", restored.publicKeyBase64)
    }
}
