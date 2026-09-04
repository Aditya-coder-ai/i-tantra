package com.talkmitra.offlinevoice.communication

import com.talkmitra.offlinevoice.communication.model.MeshPacket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MeshPacketTest {

    @Test
    fun testSerializationAndDeserialization() {
        val messageId = ByteArray(16) { it.toByte() }
        val ephemeralPubKey = ByteArray(32) { (it * 2).toByte() }
        val blindTag = ByteArray(16) { (it * 3).toByte() }
        val ciphertext = "Hello offline world".toByteArray(Charsets.UTF_8)
        val authTag = ByteArray(16) { (it + 5).toByte() }

        val packet = MeshPacket(
            version = 1,
            type = MeshPacket.TYPE_DATA,
            ttl = 7,
            hopCount = 0,
            messageId = messageId,
            ephemeralSenderPubKey = ephemeralPubKey,
            blindRecipientTag = blindTag,
            ciphertext = ciphertext,
            authTag = authTag
        )

        val serialized = packet.toByteArray()
        val parsed = MeshPacket.parse(serialized)

        assertNotNull(parsed)
        assertEquals(packet.version, parsed!!.version)
        assertEquals(packet.type, parsed.type)
        assertEquals(packet.ttl, parsed.ttl)
        assertEquals(packet.hopCount, parsed.hopCount)
        assertArrayEquals(packet.messageId, parsed.messageId)
        assertArrayEquals(packet.ephemeralSenderPubKey, parsed.ephemeralSenderPubKey)
        assertArrayEquals(packet.blindRecipientTag, parsed.blindRecipientTag)
        assertArrayEquals(packet.ciphertext, parsed.ciphertext)
        assertArrayEquals(packet.authTag, parsed.authTag)
    }

    @Test
    fun testCorruptedMagicBytesReturnsNull() {
        val validBytes = MeshPacket(
            messageId = ByteArray(16),
            ephemeralSenderPubKey = ByteArray(32),
            blindRecipientTag = ByteArray(16),
            ciphertext = ByteArray(10),
            authTag = ByteArray(16)
        ).toByteArray()

        // Corrupt magic byte
        validBytes[0] = 0x00
        val parsed = MeshPacket.parse(validBytes)
        assertNull(parsed)
    }
}
