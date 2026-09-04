package com.talkmitra.offlinevoice.security

import com.talkmitra.offlinevoice.text.ConfidenceStatus
import com.talkmitra.offlinevoice.text.MessagePriority
import com.talkmitra.offlinevoice.text.MessageType
import com.talkmitra.offlinevoice.text.ProcessedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Instant

class EncryptionDecryptionPipelineTest {

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

        // Establish pairing between A and B
        val (offer, ephA) = controllerA.pairingManager.createPairingOffer()
        val (response, sessionB) = controllerB.pairingManager.processPairingOffer(offer)
        val sessionA = controllerA.pairingManager.processPairingResponse(offer, response, ephA.private)

        controllerA.pairingManager.confirmPairing(sessionA)
        controllerB.pairingManager.confirmPairing(sessionB)
    }

    private fun createSampleMessage(
        text: String,
        language: String = "en",
        type: MessageType = MessageType.NORMAL,
        priority: MessagePriority = MessagePriority.NORMAL
    ): ProcessedMessage {
        return ProcessedMessage(
            messageId = "VL-${System.currentTimeMillis() % 1000000}",
            conversationId = "conv-test-01",
            senderId = deviceIdA,
            text = text,
            language = language,
            messageType = type,
            priority = priority,
            timestamp = Instant.now().toString(),
            sequenceNumber = 1L,
            confidence = 0.96f,
            confidenceStatus = ConfidenceStatus.HIGH,
            isFinal = true,
            utf8ByteSize = text.toByteArray(Charsets.UTF_8).size,
            processingTimeMs = 35L
        )
    }

    @Test
    fun testEnglishMessageEncryptDecryptRoundtrip() {
        val original = createSampleMessage("I need help. There is an emergency.")
        val packet = controllerA.encryptOutgoing(original, deviceIdB)

        assertNotNull(packet)
        assertTrue(packet.ciphertext.isNotEmpty())
        assertTrue(packet.authenticationTag.isNotEmpty())
        assertEquals(deviceIdA, packet.senderId)
        assertEquals(deviceIdB, packet.recipientId)

        val decrypted = controllerB.decryptIncoming(packet)
        assertEquals(original.messageId, decrypted.messageId)
        assertEquals(original.text, decrypted.text)
        assertEquals(original.language, decrypted.language)
        assertEquals(original.priority, decrypted.priority)
        assertEquals(original.messageType, decrypted.messageType)
    }

    @Test
    fun testHindiMessageEncryptDecryptRoundtrip() {
        val original = createSampleMessage("मुझे मदद चाहिए।", language = "hi")
        val packet = controllerA.encryptOutgoing(original, deviceIdB)

        val decrypted = controllerB.decryptIncoming(packet)
        assertEquals("मुझे मदद चाहिए।", decrypted.text)
        assertEquals("hi", decrypted.language)
        assertEquals(original.utf8ByteSize, decrypted.utf8ByteSize)
    }

    @Test
    fun testMultilingualAndUnicodeMessagesRoundtrip() {
        val testStrings = listOf(
            "助けが必要です。火事です！" to "ja",
            "أحتاج إلى مساعدة فورية" to "ar",
            "నాకు సహాయం కావాలి" to "te",
            "Emergency! 🚨 Coordinates: 18°31'N 73°51'E ±5m" to "en"
        )

        for ((text, lang) in testStrings) {
            val original = createSampleMessage(text, language = lang)
            val packet = controllerA.encryptOutgoing(original, deviceIdB)
            val decrypted = controllerB.decryptIncoming(packet)
            assertEquals("Decrypted Unicode text must match exactly", text, decrypted.text)
        }
    }

    @Test
    fun testEmergencyMessageRoundtrip() {
        val emergencyMsg = createSampleMessage(
            text = "CRITICAL EVACUATION REQUIRED",
            type = MessageType.EMERGENCY,
            priority = MessagePriority.CRITICAL
        )
        val packet = controllerA.encryptOutgoing(emergencyMsg, deviceIdB)
        assertEquals(MessagePriority.CRITICAL, packet.priority)

        val decrypted = controllerB.decryptIncoming(packet)
        assertEquals(MessageType.EMERGENCY, decrypted.messageType)
        assertEquals(MessagePriority.CRITICAL, decrypted.priority)
    }

    @Test
    fun testTamperedCiphertextRejection() {
        val original = createSampleMessage("Top secret coordinates")
        val packet = controllerA.encryptOutgoing(original, deviceIdB)

        // Alter ciphertext
        val tamperedCiphertext = packet.ciphertext.dropLast(4) + "ZZZZ"
        val tamperedPacket = packet.copy(ciphertext = tamperedCiphertext)

        try {
            controllerB.decryptIncoming(tamperedPacket)
            fail("Expected AuthenticationFailedException on modified ciphertext")
        } catch (e: AuthenticationFailedException) {
            assertTrue(e.message!!.contains("tamper") || e.message!!.contains("AEAD") || e.message!!.contains("tag"))
        }
    }

    @Test
    fun testTamperedHeaderAadRejection() {
        val original = createSampleMessage("Medical supply update", priority = MessagePriority.NORMAL)
        val packet = controllerA.encryptOutgoing(original, deviceIdB)

        // Adversary escalates priority in header from NORMAL to CRITICAL
        val tamperedPacket = packet.copy(priority = MessagePriority.CRITICAL)

        try {
            controllerB.decryptIncoming(tamperedPacket)
            fail("Expected AuthenticationFailedException when header AAD is modified")
        } catch (e: AuthenticationFailedException) {
            assertTrue(e.message!!.contains("AEAD") || e.message!!.contains("modified") || e.message!!.contains("mismatch"))
        }
    }

    @Test
    fun testUnknownSenderRejection() {
        val unknownController = SecurityController(InMemorySecureStorage())
        unknownController.initializeIdentity("Rogue Node")
        val rogueId = unknownController.identityManager.getDeviceId()

        // Create a forged packet with an untrusted sender ID
        val forgedPacket = EncryptedMessagePacket(
            version = 1,
            protocolVersion = "VoiceLink-Sec-v1",
            senderId = rogueId,
            recipientId = deviceIdB,
            sessionId = "SES-FAKE",
            messageId = "VL-9999",
            sequenceNumber = 1L,
            timestamp = Instant.now().toString(),
            priority = MessagePriority.NORMAL,
            nonce = "X49faK29m1L0P9ab",
            ciphertext = "dGVzdA==",
            authenticationTag = "dGVzdA=="
        )

        try {
            controllerB.decryptIncoming(forgedPacket)
            fail("Expected UnknownDeviceException from unverified sender")
        } catch (e: UnknownDeviceException) {
            assertEquals(rogueId, e.senderId)
        }
    }

    @Test
    fun testMalformedJsonPacketHandling() {
        val malformedJson = "{ invalid json content..."
        try {
            EncryptedMessagePacket.fromJson(malformedJson)
            fail("Expected CorruptedPacketException on malformed JSON")
        } catch (e: CorruptedPacketException) {
            assertTrue(e.message!!.contains("Malformed"))
        }
    }

    @Test
    fun testCryptoMetricsRecorded() {
        val original = createSampleMessage("Benchmark test message")
        controllerA.encryptOutgoing(original, deviceIdB)

        val metrics = controllerA.metrics.getSnapshot()
        assertTrue(metrics.totalEncryptedMessages >= 1)
        assertTrue(metrics.lastPlaintextBytes > 0)
        assertTrue(metrics.lastPacketSizeBytes > metrics.lastPlaintextBytes)
        assertTrue(metrics.lastEncryptionTimeMs >= 0.0)
    }
}
