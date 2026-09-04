package com.talkmitra.offlinevoice.network

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Supported wire-level packet types.
 */
enum class PacketType(val code: Byte) {
    DATA(0x01),
    ACK(0x02),
    PING(0x03),
    PONG(0x04),
    HANDSHAKE(0x05);

    companion object {
        fun fromCode(code: Byte): PacketType = entries.firstOrNull { it.code == code } ?: DATA
    }
}

/**
 * Parsed wire-level frame.
 */
data class RawFrame(
    val type: PacketType,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RawFrame
        if (type != other.type) return false
        return payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * Implements standard binary packet framing with CRC32 integrity verification.
 * Prevents split/chunked reads and fused socket buffer errors.
 */
object PacketFramer {
    const val MAGIC_BYTE_1: Byte = 0x56 // 'V'
    const val MAGIC_BYTE_2: Byte = 0x4C // 'L'
    const val PROTOCOL_VERSION: Byte = 0x01
    const val HEADER_SIZE = 8 // Magic(2) + Version(1) + Type(1) + Length(4)
    const val TRAILER_SIZE = 4 // CRC32(4)
    const val MIN_FRAME_SIZE = HEADER_SIZE + TRAILER_SIZE
    const val MAX_PAYLOAD_SIZE = 1024 * 1024 // 1 MB sanity limit

    /**
     * Encloses [payload] inside a complete framed binary packet.
     */
    fun frame(type: PacketType, payload: ByteArray): ByteArray {
        val payloadLength = payload.size
        val totalSize = HEADER_SIZE + payloadLength + TRAILER_SIZE
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

        // Header
        buffer.put(MAGIC_BYTE_1)
        buffer.put(MAGIC_BYTE_2)
        buffer.put(PROTOCOL_VERSION)
        buffer.put(type.code)
        buffer.putInt(payloadLength)

        // Payload
        buffer.put(payload)

        // CRC32 Checksum calculated over Version + Type + Length + Payload
        val crc = CRC32()
        crc.update(PROTOCOL_VERSION.toInt())
        crc.update(type.code.toInt())
        crc.update(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(payloadLength).array())
        crc.update(payload)

        buffer.putInt(crc.value.toInt())

        return buffer.array()
    }

    /**
     * Reassembles framed packets from a continuous, fragmented or coalesced socket stream.
     */
    class StreamFramer {
        private val buffer = ByteArrayOutputStream()

        /**
         * Appends incoming socket chunk and extracts all complete valid frames.
         */
        @Synchronized
        fun pushBytes(chunk: ByteArray, length: Int = chunk.size): List<RawFrame> {
            buffer.write(chunk, 0, length)
            val currentBytes = buffer.toByteArray()
            val frames = mutableListOf<RawFrame>()
            var offset = 0

            while (offset + MIN_FRAME_SIZE <= currentBytes.size) {
                // Look for Magic Bytes
                if (currentBytes[offset] != MAGIC_BYTE_1 || currentBytes[offset + 1] != MAGIC_BYTE_2) {
                    offset++
                    continue
                }

                // Read Header
                val version = currentBytes[offset + 2]
                val typeCode = currentBytes[offset + 3]
                val payloadLen = ByteBuffer.wrap(currentBytes, offset + 4, 4)
                    .order(ByteOrder.BIG_ENDIAN)
                    .int

                // Sanity check length
                if (payloadLen < 0 || payloadLen > MAX_PAYLOAD_SIZE) {
                    offset += 2 // Corrupted header, skip past magic
                    continue
                }

                val frameTotalSize = HEADER_SIZE + payloadLen + TRAILER_SIZE
                if (offset + frameTotalSize > currentBytes.size) {
                    // Frame is incomplete, wait for more socket bytes
                    break
                }

                // Extract Payload
                val payload = currentBytes.copyOfRange(offset + HEADER_SIZE, offset + HEADER_SIZE + payloadLen)

                // Verify CRC32
                val expectedCrc = ByteBuffer.wrap(currentBytes, offset + HEADER_SIZE + payloadLen, 4)
                    .order(ByteOrder.BIG_ENDIAN)
                    .int

                val crc = CRC32()
                crc.update(version.toInt())
                crc.update(typeCode.toInt())
                crc.update(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(payloadLen).array())
                crc.update(payload)

                if (crc.value.toInt() == expectedCrc) {
                    frames.add(RawFrame(PacketType.fromCode(typeCode), payload))
                    offset += frameTotalSize
                } else {
                    // Checksum mismatch, advance past magic
                    offset += 2
                }
            }

            // Compact remaining unprocessed bytes in buffer
            buffer.reset()
            if (offset < currentBytes.size) {
                buffer.write(currentBytes, offset, currentBytes.size - offset)
            }

            return frames
        }

        @Synchronized
        fun reset() {
            buffer.reset()
        }
    }
}
