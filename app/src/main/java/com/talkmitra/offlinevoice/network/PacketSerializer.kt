package com.talkmitra.offlinevoice.network

import com.talkmitra.offlinevoice.security.EncryptedMessagePacket
import org.json.JSONObject

/**
 * High-level Acknowledgement packet structure.
 */
data class AckPayload(
    val messageId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "DELIVERED"
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("messageId", messageId)
            put("timestamp", timestamp)
            put("status", status)
        }.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): AckPayload {
            val json = JSONObject(jsonStr)
            return AckPayload(
                messageId = json.getString("messageId"),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                status = json.optString("status", "DELIVERED")
            )
        }
    }
}

/**
 * Handshake payload exchanged immediately after socket connection to identify the peer.
 */
data class HandshakePayload(
    val deviceId: String,
    val displayName: String,
    val publicKeyBase64: String,
    val protocolVersion: String = "VoiceLink-Net-v1",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("displayName", displayName)
            put("publicKeyBase64", publicKeyBase64)
            put("protocolVersion", protocolVersion)
            put("timestamp", timestamp)
        }.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): HandshakePayload {
            val json = JSONObject(jsonStr)
            return HandshakePayload(
                deviceId = json.getString("deviceId"),
                displayName = json.getString("displayName"),
                publicKeyBase64 = json.optString("publicKeyBase64", ""),
                protocolVersion = json.optString("protocolVersion", "VoiceLink-Net-v1"),
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }
}

/**
 * Serializes high-level domain entities to wire frames and deserializes raw frames to entities.
 */
object PacketSerializer {

    /**
     * Serializes an EncryptedMessagePacket into a complete wire frame.
     */
    fun serializeEncryptedPacket(packet: EncryptedMessagePacket): ByteArray {
        val jsonBytes = packet.toJson().toByteArray(Charsets.UTF_8)
        return PacketFramer.frame(PacketType.DATA, jsonBytes)
    }

    /**
     * Deserializes a DATA frame payload into an EncryptedMessagePacket.
     */
    fun deserializeEncryptedPacket(payload: ByteArray): EncryptedMessagePacket {
        val jsonStr = String(payload, Charsets.UTF_8)
        return EncryptedMessagePacket.fromJson(jsonStr)
    }

    /**
     * Serializes an AckPayload into a complete ACK wire frame.
     */
    fun serializeAck(ack: AckPayload): ByteArray {
        val jsonBytes = ack.toJson().toByteArray(Charsets.UTF_8)
        return PacketFramer.frame(PacketType.ACK, jsonBytes)
    }

    /**
     * Deserializes an ACK frame payload into an AckPayload.
     */
    fun deserializeAck(payload: ByteArray): AckPayload {
        val jsonStr = String(payload, Charsets.UTF_8)
        return AckPayload.fromJson(jsonStr)
    }

    /**
     * Serializes a HandshakePayload into a HANDSHAKE wire frame.
     */
    fun serializeHandshake(handshake: HandshakePayload): ByteArray {
        val jsonBytes = handshake.toJson().toByteArray(Charsets.UTF_8)
        return PacketFramer.frame(PacketType.HANDSHAKE, jsonBytes)
    }

    /**
     * Deserializes a HANDSHAKE frame payload into a HandshakePayload.
     */
    fun deserializeHandshake(payload: ByteArray): HandshakePayload {
        val jsonStr = String(payload, Charsets.UTF_8)
        return HandshakePayload.fromJson(jsonStr)
    }

    /**
     * Generates a heartbeat PING frame.
     */
    fun serializePing(): ByteArray {
        return PacketFramer.frame(PacketType.PING, ByteArray(0))
    }

    /**
     * Generates a heartbeat PONG frame.
     */
    fun serializePong(): ByteArray {
        return PacketFramer.frame(PacketType.PONG, ByteArray(0))
    }
}
