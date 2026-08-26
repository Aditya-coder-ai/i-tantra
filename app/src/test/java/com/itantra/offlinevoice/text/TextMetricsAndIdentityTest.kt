package com.itantra.offlinevoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class TextMetricsAndIdentityTest {
    private val metrics = TextMetrics()

    @Before
    fun resetSequence() {
        MessageIdentity.resetSequence()
    }

    // --- Message ID uniqueness ---
    @Test
    fun testIdUniquenessAcross1000Messages() {
        val ids = (1..1000).map { MessageIdentity.generateMessageId() }.toSet()
        assertEquals("All 1000 IDs must be unique", 1000, ids.size)
    }

    @Test
    fun testIdHasVLPrefix() {
        val id = MessageIdentity.generateMessageId()
        assertTrue("ID should start with VL-", id.startsWith("VL-"))
    }

    // --- UTF-8 byte size ---
    @Test
    fun testAsciiByteSize() {
        // ASCII: 1 byte per character
        assertEquals(5, metrics.utf8ByteSize("Hello"))
    }

    @Test
    fun testDevanagariByteSize() {
        // Devanagari characters are 3 bytes each in UTF-8
        val hindi = "नमस्ते"
        val byteSize = metrics.utf8ByteSize(hindi)
        assertTrue("Devanagari must be > char count", byteSize > hindi.length)
        // 5 Unicode code points × 3 bytes = 15 bytes (with combining marks)
        assertTrue("Devanagari should use 3 bytes per codepoint", byteSize >= 15)
    }

    @Test
    fun testBengaliByteSize() {
        val bengali = "নমস্কার"
        val byteSize = metrics.utf8ByteSize(bengali)
        assertTrue("Bengali must use more bytes than char count", byteSize > bengali.length)
        assertTrue("Bengali should use 3 bytes per codepoint", byteSize >= 12)
    }

    @Test
    fun testTamilByteSize() {
        val tamil = "வணக்கம்"
        val byteSize = metrics.utf8ByteSize(tamil)
        assertTrue("Tamil must use more bytes than char count", byteSize > tamil.length)
    }

    @Test
    fun testAsciiVsIndicByteSizeDiffers() {
        // Similar character count, vastly different byte sizes
        val ascii = "Hello"       // 5 chars, 5 bytes
        val devanagari = "नमस्ते"  // ~5 chars, 15+ bytes
        assertNotEquals(
            "ASCII and Devanagari of similar char count must differ in byte size",
            metrics.utf8ByteSize(ascii),
            metrics.utf8ByteSize(devanagari)
        )
    }

    // --- Timestamp ---
    @Test
    fun testTimestampIsIso8601Parseable() {
        val ts = MessageIdentity.utcTimestamp()
        // Should not throw — proves round-trip parse works
        val parsed = Instant.parse(ts)
        assertTrue("Timestamp should be recent", parsed.epochSecond > 0)
    }

    @Test
    fun testTimestampIsUTC() {
        val ts = MessageIdentity.utcTimestamp()
        assertTrue("Timestamp must end with Z (UTC)", ts.endsWith("Z"))
    }

    // --- Sequence number ---
    @Test
    fun testSequenceNumberMonotonicallyIncreases() {
        val seq1 = MessageIdentity.nextSequenceNumber()
        val seq2 = MessageIdentity.nextSequenceNumber()
        val seq3 = MessageIdentity.nextSequenceNumber()
        assertEquals(1L, seq1)
        assertEquals(2L, seq2)
        assertEquals(3L, seq3)
    }

    @Test
    fun testSequenceResets() {
        MessageIdentity.nextSequenceNumber()
        MessageIdentity.nextSequenceNumber()
        MessageIdentity.resetSequence()
        assertEquals(1L, MessageIdentity.nextSequenceNumber())
    }

    // --- Empty string ---
    @Test
    fun testEmptyStringByteSize() {
        assertEquals(0, metrics.utf8ByteSize(""))
    }
}
