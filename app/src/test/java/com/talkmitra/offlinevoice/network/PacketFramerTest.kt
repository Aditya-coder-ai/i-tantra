package com.talkmitra.offlinevoice.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketFramerTest {

    @Test
    fun testFrameAndExtractSinglePacket() {
        val payload = "Hello VoiceLink Device-to-Device Network!".toByteArray(Charsets.UTF_8)
        val framedBytes = PacketFramer.frame(PacketType.DATA, payload)

        val framer = PacketFramer.StreamFramer()
        val frames = framer.pushBytes(framedBytes)

        assertEquals(1, frames.size)
        assertEquals(PacketType.DATA, frames[0].type)
        assertArrayEquals(payload, frames[0].payload)
    }

    @Test
    fun testStreamReassemblyChunkedReads() {
        val payload = "This is a long encrypted packet payload split across small TCP read buffers.".toByteArray(Charsets.UTF_8)
        val framedBytes = PacketFramer.frame(PacketType.DATA, payload)

        val framer = PacketFramer.StreamFramer()

        // Split into 7-byte chunks to simulate fragmented socket stream
        val chunkSize = 7
        val extractedFrames = mutableListOf<RawFrame>()

        var offset = 0
        while (offset < framedBytes.size) {
            val length = minOf(chunkSize, framedBytes.size - offset)
            val chunk = framedBytes.copyOfRange(offset, offset + length)
            val frames = framer.pushBytes(chunk)
            extractedFrames.addAll(frames)
            offset += length
        }

        assertEquals(1, extractedFrames.size)
        assertEquals(PacketType.DATA, extractedFrames[0].type)
        assertArrayEquals(payload, extractedFrames[0].payload)
    }

    @Test
    fun testMultiplePacketsInSingleSocketBuffer() {
        val payload1 = "Packet 1".toByteArray(Charsets.UTF_8)
        val payload2 = "Packet 2 ACK".toByteArray(Charsets.UTF_8)
        val payload3 = "Packet 3 Emergency".toByteArray(Charsets.UTF_8)

        val frame1 = PacketFramer.frame(PacketType.DATA, payload1)
        val frame2 = PacketFramer.frame(PacketType.ACK, payload2)
        val frame3 = PacketFramer.frame(PacketType.DATA, payload3)

        val combinedBuffer = frame1 + frame2 + frame3

        val framer = PacketFramer.StreamFramer()
        val frames = framer.pushBytes(combinedBuffer)

        assertEquals(3, frames.size)
        assertEquals(PacketType.DATA, frames[0].type)
        assertArrayEquals(payload1, frames[0].payload)

        assertEquals(PacketType.ACK, frames[1].type)
        assertArrayEquals(payload2, frames[1].payload)

        assertEquals(PacketType.DATA, frames[2].type)
        assertArrayEquals(payload3, frames[2].payload)
    }

    @Test
    fun testCorruptedChecksumRejection() {
        val payload = "Important message".toByteArray(Charsets.UTF_8)
        val framedBytes = PacketFramer.frame(PacketType.DATA, payload)

        // Corrupt one payload byte
        framedBytes[PacketFramer.HEADER_SIZE + 2] = (framedBytes[PacketFramer.HEADER_SIZE + 2] + 1).toByte()

        val framer = PacketFramer.StreamFramer()
        val frames = framer.pushBytes(framedBytes)

        // Must drop corrupted frame safely
        assertEquals(0, frames.size)
    }
}
