package com.talkmitra.offlinevoice.security

import com.talkmitra.offlinevoice.text.ConfidenceStatus
import com.talkmitra.offlinevoice.text.MessagePriority
import com.talkmitra.offlinevoice.text.MessageType
import com.talkmitra.offlinevoice.text.ProcessedMessage
import org.json.JSONObject
import java.util.Base64

/**
 * Versioned network transmission packet containing authenticated routing metadata
 * and AEAD AES-256-GCM ciphertext.
 *
 * Intermediate mesh nodes inspect only the routing metadata (senderId, recipientId,
 * messageId, priority, timestamp) to make forwarding decisions.
 * Plaintext message content is strictly encapsulated within the ciphertext.
 */
data class EncryptedMessagePacket(
    val version: Int = 1,
    val protocolVersion: String = "VoiceLink-Sec-v1",
    val senderId: String,
    val recipientId: String,
    val sessionId: String,
    val messageId: String,
    val sequenceNumber: Long,
    val timestamp: String,
    val priority: MessagePriority,
    val nonce: String,
    val ciphertext: String,
    val authenticationTag: String
) {

    /**
     * Computes canonical Associated Authenticated Data (AAD) bytes for AEAD integrity binding.
     * Any change to routing headers in transit will invalidate the AEAD tag.
     */
    fun computeAadBytes(): ByteArray {
        val canonicalAad = "$protocolVersion|$version|$senderId|$recipientId|$sessionId|$messageId|$sequenceNumber|$timestamp|${priority.name}"
        return canonicalAad.toByteArray(Charsets.UTF_8)
    }

    /**
     * Returns combined ciphertext + authentication tag bytes for standard AES-GCM cipher execution.
     */
    fun getCiphertextWithTagBytes(): ByteArray {
        val cipherBytes = Base64.getDecoder().decode(ciphertext)
        val tagBytes = Base64.getDecoder().decode(authenticationTag)
        return cipherBytes + tagBytes
    }

    /**
     * Serializes this packet to JSON for network transmission.
     */
    fun toJson(): String {
        val json = JSONObject()
        json.put("version", version)
        json.put("protocolVersion", protocolVersion)
        json.put("senderId", senderId)
        json.put("recipientId", recipientId)
        json.put("sessionId", sessionId)
        json.put("messageId", messageId)
        json.put("sequenceNumber", sequenceNumber)
        json.put("timestamp", timestamp)
        json.put("priority", priority.name)
        json.put("nonce", nonce)
        json.put("ciphertext", ciphertext)
        json.put("authenticationTag", authenticationTag)
        return json.toString()
    }

    companion object {
        /**
         * Creates an EncryptedMessagePacket from raw ciphertext+tag bytes.
         */
        fun create(
            version: Int = 1,
            protocolVersion: String = "VoiceLink-Sec-v1",
            senderId: String,
            recipientId: String,
            sessionId: String,
            messageId: String,
            sequenceNumber: Long,
            timestamp: String,
            priority: MessagePriority,
            nonceBytes: ByteArray,
            ciphertextWithTagBytes: ByteArray
        ): EncryptedMessagePacket {
            require(ciphertextWithTagBytes.size >= 16) { "Ciphertext with tag must be at least 16 bytes" }
            val cipherSize = ciphertextWithTagBytes.size - 16
            val cipherBytes = ByteArray(cipherSize)
            val tagBytes = ByteArray(16)

            System.arraycopy(ciphertextWithTagBytes, 0, cipherBytes, 0, cipherSize)
            System.arraycopy(ciphertextWithTagBytes, cipherSize, tagBytes, 0, 16)

            return EncryptedMessagePacket(
                version = version,
                protocolVersion = protocolVersion,
                senderId = senderId,
                recipientId = recipientId,
                sessionId = sessionId,
                messageId = messageId,
                sequenceNumber = sequenceNumber,
                timestamp = timestamp,
                priority = priority,
                nonce = Base64.getEncoder().encodeToString(nonceBytes),
                ciphertext = Base64.getEncoder().encodeToString(cipherBytes),
                authenticationTag = Base64.getEncoder().encodeToString(tagBytes)
            )
        }

        /**
         * Deserializes an EncryptedMessagePacket from a JSON string.
         */
        fun fromJson(jsonStr: String): EncryptedMessagePacket {
            try {
                val json = JSONObject(jsonStr)
                return EncryptedMessagePacket(
                    version = json.optInt("version", 1),
                    protocolVersion = json.optString("protocolVersion", "VoiceLink-Sec-v1"),
                    senderId = json.getString("senderId"),
                    recipientId = json.getString("recipientId"),
                    sessionId = json.getString("sessionId"),
                    messageId = json.getString("messageId"),
                    sequenceNumber = json.getLong("sequenceNumber"),
                    timestamp = json.getString("timestamp"),
                    priority = MessagePriority.valueOf(json.optString("priority", MessagePriority.NORMAL.name)),
                    nonce = json.getString("nonce"),
                    ciphertext = json.getString("ciphertext"),
                    authenticationTag = json.getString("authenticationTag")
                )
            } catch (e: Exception) {
                throw CorruptedPacketException("Malformed JSON encrypted message packet", e)
            }
        }

        /**
         * Serializes a ProcessedMessage to JSON plaintext payload.
         */
        fun serializeProcessedMessage(message: ProcessedMessage): ByteArray {
            val json = JSONObject()
            json.put("messageId", message.messageId)
            json.put("conversationId", message.conversationId)
            json.put("senderId", message.senderId)
            json.put("text", message.text)
            json.put("language", message.language)
            json.put("messageType", message.messageType.name)
            json.put("priority", message.priority.name)
            json.put("timestamp", message.timestamp)
            json.put("sequenceNumber", message.sequenceNumber)
            json.put("confidence", message.confidence.toDouble())
            json.put("confidenceStatus", message.confidenceStatus.name)
            json.put("isFinal", message.isFinal)
            json.put("utf8ByteSize", message.utf8ByteSize)
            json.put("processingTimeMs", message.processingTimeMs)
            return json.toString().toByteArray(Charsets.UTF_8)
        }

        /**
         * Deserializes a ProcessedMessage from JSON plaintext bytes.
         */
        fun deserializeProcessedMessage(payloadBytes: ByteArray): ProcessedMessage {
            try {
                val json = JSONObject(String(payloadBytes, Charsets.UTF_8))
                return ProcessedMessage(
                    messageId = json.getString("messageId"),
                    conversationId = json.optString("conversationId", "default"),
                    senderId = json.getString("senderId"),
                    text = json.getString("text"),
                    language = json.getString("language"),
                    messageType = MessageType.valueOf(json.optString("messageType", MessageType.NORMAL.name)),
                    priority = MessagePriority.valueOf(json.optString("priority", MessagePriority.NORMAL.name)),
                    timestamp = json.getString("timestamp"),
                    sequenceNumber = json.getLong("sequenceNumber"),
                    confidence = json.optDouble("confidence", 1.0).toFloat(),
                    confidenceStatus = ConfidenceStatus.valueOf(json.optString("confidenceStatus", ConfidenceStatus.HIGH.name)),
                    isFinal = json.optBoolean("isFinal", true),
                    utf8ByteSize = json.optInt("utf8ByteSize", json.getString("text").toByteArray(Charsets.UTF_8).size),
                    processingTimeMs = json.optLong("processingTimeMs", 0L)
                )
            } catch (e: Exception) {
                throw CorruptedPacketException("Failed to deserialize ProcessedMessage plaintext payload", e)
            }
        }
    }
}
