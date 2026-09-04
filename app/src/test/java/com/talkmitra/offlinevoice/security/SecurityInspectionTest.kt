package com.talkmitra.offlinevoice.security

import com.talkmitra.offlinevoice.text.ConfidenceStatus
import com.talkmitra.offlinevoice.text.MessagePriority
import com.talkmitra.offlinevoice.text.MessageType
import com.talkmitra.offlinevoice.text.ProcessedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.Base64

/**
 * Mandatory Security Packet Inspection Test (Section 22 of Specification).
 *
 * Verifies that sensitive spoken sentences never appear in plaintext anywhere in
 * the transmitted network packet, headers, raw JSON, or raw byte buffers.
 */
class SecurityInspectionTest {

    private lateinit var controllerA: SecurityController
    private lateinit var controllerB: SecurityController
    private lateinit var deviceIdA: String
    private lateinit var deviceIdB: String

    @Before
    fun setUp() {
        controllerA = SecurityController(InMemorySecureStorage())
        controllerB = SecurityController(InMemorySecureStorage())

        controllerA.initializeIdentity("Phone A")
        controllerB.initializeIdentity("Phone B")

        deviceIdA = controllerA.identityManager.getDeviceId()
        deviceIdB = controllerB.identityManager.getDeviceId()

        // Pair Phone A and Phone B
        val (offer, ephA) = controllerA.pairingManager.createPairingOffer()
        val (response, sessionB) = controllerB.pairingManager.processPairingOffer(offer)
        val sessionA = controllerA.pairingManager.processPairingResponse(offer, response, ephA.private)

        controllerA.pairingManager.confirmPairing(sessionA)
        controllerB.pairingManager.confirmPairing(sessionB)
    }

    @Test
    fun testPlaintextAbsenceInTransmittedPacket() {
        val secretSentence = "I need help. There is a fire."

        val processedMessage = ProcessedMessage(
            messageId = "VL-INSPECT-99",
            conversationId = "conv-inspect",
            senderId = deviceIdA,
            text = secretSentence,
            language = "en",
            messageType = MessageType.EMERGENCY,
            priority = MessagePriority.CRITICAL,
            timestamp = Instant.now().toString(),
            sequenceNumber = 1L,
            confidence = 0.98f,
            confidenceStatus = ConfidenceStatus.HIGH,
            isFinal = true,
            utf8ByteSize = secretSentence.toByteArray(Charsets.UTF_8).size,
            processingTimeMs = 28L
        )

        // 1. Encrypt message for Phone B
        val packet = controllerA.encryptOutgoing(processedMessage, deviceIdB)
        val packetJson = packet.toJson()

        // 2. Perform deep string inspection on JSON packet
        assertFalse(
            "Plaintext sentence must NOT appear in the JSON packet string",
            packetJson.contains(secretSentence)
        )
        assertFalse(
            "Individual sensitive words must NOT appear in plaintext",
            packetJson.contains("help") || packetJson.contains("fire")
        )

        // 3. Inspect individual fields
        assertFalse(packet.ciphertext.contains(secretSentence))
        assertFalse(packet.authenticationTag.contains(secretSentence))
        assertFalse(packet.nonce.contains(secretSentence))

        // 4. Inspect decoded ciphertext byte buffer
        val decodedCiphertextBytes = Base64.getDecoder().decode(packet.ciphertext)
        val ciphertextRawString = String(decodedCiphertextBytes, Charsets.ISO_8859_1)
        assertFalse(
            "Plaintext sentence must NOT appear in raw decoded ciphertext bytes",
            ciphertextRawString.contains(secretSentence)
        )

        // 5. Verify only expected routing metadata is present in packet headers
        assertEquals(deviceIdA, packet.senderId)
        assertEquals(deviceIdB, packet.recipientId)
        assertEquals(MessagePriority.CRITICAL, packet.priority)
        assertEquals("VL-INSPECT-99", packet.messageId)
        assertTrue(packet.ciphertext.isNotEmpty())
        assertTrue(packet.authenticationTag.isNotEmpty())

        // 6. Decrypt on Phone B and verify the recovered sentence matches perfectly
        val decryptedMessage = controllerB.decryptIncoming(packet)
        assertNotNull(decryptedMessage)
        assertEquals(secretSentence, decryptedMessage.text)
        assertEquals(MessageType.EMERGENCY, decryptedMessage.messageType)
        assertEquals(MessagePriority.CRITICAL, decryptedMessage.priority)
    }

    @Test
    fun testHindiPlaintextAbsenceInTransmittedPacket() {
        val hindiSecret = "यहाँ भारी आग लगी है और तत्काल सहायता चाहिए।"

        val processedMessage = ProcessedMessage(
            messageId = "VL-HINDI-01",
            conversationId = "conv-hindi",
            senderId = deviceIdA,
            text = hindiSecret,
            language = "hi",
            messageType = MessageType.EMERGENCY,
            priority = MessagePriority.CRITICAL,
            timestamp = Instant.now().toString(),
            sequenceNumber = 2L,
            confidence = 0.94f,
            confidenceStatus = ConfidenceStatus.HIGH,
            isFinal = true,
            utf8ByteSize = hindiSecret.toByteArray(Charsets.UTF_8).size,
            processingTimeMs = 30L
        )

        val packet = controllerA.encryptOutgoing(processedMessage, deviceIdB)
        val packetJson = packet.toJson()

        assertFalse(
            "Hindi plaintext must NOT appear in transmitted packet JSON",
            packetJson.contains(hindiSecret)
        )
        assertFalse(
            "Hindi sub-words must NOT appear in packet JSON",
            packetJson.contains("सहायता") || packetJson.contains("आग")
        )

        val decrypted = controllerB.decryptIncoming(packet)
        assertEquals(hindiSecret, decrypted.text)
    }
}
