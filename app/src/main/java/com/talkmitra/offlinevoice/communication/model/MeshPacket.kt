package com.talkmitra.offlinevoice.communication.model

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Binary wire format representation of an iTantra mesh packet.
 *
 * Wire Layout:
 * [0..1]   Magic Bytes (0x49 0x54 = "IT")
 * [2]      Protocol Version (0x01)
 * [3]      Message Type (0x01: DATA, 0x02: ACK, 0x03: BEACON, 0x04: SOS)
 * [4]      TTL (Time-To-Live, max hops remaining)
 * [5]      Hop Count (Total hops traversed)
 * [6..21]  Message ID (16 bytes UUID)
 * [22..53] Ephemeral Sender Public Key (32 bytes X25519)
 * [54..69] Blind Recipient Tag (16 bytes HKDF token)
 * [70..71] Payload Length (uint16)
 * [72..N]  Ciphertext (Encrypted message payload)
 * [N..N+16] Poly1305 Auth Tag (16 bytes)
 */
data class MeshPacket(
    val version: Byte = PROTOCOL_VERSION,
    val type: Byte = TYPE_DATA,
    val ttl: Byte = DEFAULT_TTL,
    val hopCount: Byte = 0,
    val messageId: ByteArray, // 16 bytes
    val ephemeralSenderPubKey: ByteArray, // 32 bytes
    val blindRecipientTag: ByteArray, // 16 bytes
    val ciphertext: ByteArray,
    val authTag: ByteArray // 16 bytes
) {
    init {
        require(messageId.size == 16) { "MessageId must be exactly 16 bytes" }
        require(ephemeralSenderPubKey.size == 32) { "EphemeralSenderPubKey must be exactly 32 bytes" }
        require(blindRecipientTag.size == 16) { "BlindRecipientTag must be exactly 16 bytes" }
        require(authTag.size == 16) { "AuthTag must be exactly 16 bytes" }
    }

    val messageIdUuid: UUID
        get() {
            val bb = ByteBuffer.wrap(messageId)
            return UUID(bb.long, bb.long)
        }

    fun toByteArray(): ByteArray {
        val totalSize = HEADER_SIZE + ENVELOPE_SIZE + 2 + ciphertext.size + authTag.size
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

        // Header
        buffer.put(MAGIC_BYTE_1)
        buffer.put(MAGIC_BYTE_2)
        buffer.put(version)
        buffer.put(type)
        buffer.put(ttl)
        buffer.put(hopCount)
        buffer.put(messageId)

        // Routing & Crypto Envelope
        buffer.put(ephemeralSenderPubKey)
        buffer.put(blindRecipientTag)

        // Payload Section
        buffer.putShort(ciphertext.size.toShort())
        buffer.put(ciphertext)
        buffer.put(authTag)

        return buffer.array()
    }

    companion object {
        const val MAGIC_BYTE_1: Byte = 0x49 // 'I'
        const val MAGIC_BYTE_2: Byte = 0x54 // 'T'
        const val PROTOCOL_VERSION: Byte = 0x01

        const val TYPE_DATA: Byte = 0x01
        const val TYPE_ACK: Byte = 0x02
        const val TYPE_BEACON: Byte = 0x03
        const val TYPE_EMERGENCY_SOS: Byte = 0x04

        const val DEFAULT_TTL: Byte = 7
        const val MAX_TTL: Byte = 15

        const val HEADER_SIZE = 22 // 2 (magic) + 1 (ver) + 1 (type) + 1 (ttl) + 1 (hop) + 16 (msgId)
        const val ENVELOPE_SIZE = 48 // 32 (pubKey) + 16 (blindTag)
        const val MIN_PACKET_SIZE = HEADER_SIZE + ENVELOPE_SIZE + 2 + 16 // 88 bytes

        fun parse(bytes: ByteArray): MeshPacket? {
            if (bytes.size < MIN_PACKET_SIZE) return null
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

            val magic1 = buffer.get()
            val magic2 = buffer.get()
            if (magic1 != MAGIC_BYTE_1 || magic2 != MAGIC_BYTE_2) return null

            val version = buffer.get()
            val type = buffer.get()
            val ttl = buffer.get()
            val hopCount = buffer.get()

            val msgId = ByteArray(16)
            buffer.get(msgId)

            val ephPubKey = ByteArray(32)
            buffer.get(ephPubKey)

            val blindTag = ByteArray(16)
            buffer.get(blindTag)

            val payloadLength = buffer.short.toInt() and 0xFFFF
            if (buffer.remaining() < payloadLength + 16) return null

            val ciphertext = ByteArray(payloadLength)
            buffer.get(ciphertext)

            val authTag = ByteArray(16)
            buffer.get(authTag)

            return MeshPacket(
                version = version,
                type = type,
                ttl = ttl,
                hopCount = hopCount,
                messageId = msgId,
                ephemeralSenderPubKey = ephPubKey,
                blindRecipientTag = blindTag,
                ciphertext = ciphertext,
                authTag = authTag
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MeshPacket
        if (version != other.version) return false
        if (type != other.type) return false
        if (ttl != other.ttl) return false
        if (hopCount != other.hopCount) return false
        if (!messageId.contentEquals(other.messageId)) return false
        if (!ephemeralSenderPubKey.contentEquals(other.ephemeralSenderPubKey)) return false
        if (!blindRecipientTag.contentEquals(other.blindRecipientTag)) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!authTag.contentEquals(other.authTag)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + type
        result = 31 * result + ttl
        result = 31 * result + hopCount
        result = 31 * result + messageId.contentHashCode()
        result = 31 * result + ephemeralSenderPubKey.contentHashCode()
        result = 31 * result + blindRecipientTag.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + authTag.contentHashCode()
        return result
    }
}
