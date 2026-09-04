package com.talkmitra.offlinevoice.communication

import com.talkmitra.offlinevoice.communication.crypto.CryptoEngine
import com.talkmitra.offlinevoice.communication.crypto.EncryptedEnvelope
import com.talkmitra.offlinevoice.communication.model.MeshPacket
import com.talkmitra.offlinevoice.communication.model.RawPacket
import com.talkmitra.offlinevoice.communication.transport.mesh.DeduplicationCache
import com.talkmitra.offlinevoice.communication.transport.mesh.MeshRouter
import com.talkmitra.offlinevoice.communication.transport.mesh.RoutingDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshRouterTest {

    private class MockCryptoEngine(private val isMatchingRecipient: Boolean) : CryptoEngine {
        override val localPublicKey: ByteArray = ByteArray(32)
        override fun initializeIdentity() {}
        override fun encrypt(recipientPublicKey: ByteArray, plaintext: ByteArray): EncryptedEnvelope {
            return EncryptedEnvelope(ByteArray(32), ByteArray(16), plaintext, ByteArray(16))
        }
        override fun matchesRecipientTag(ephemeralPublicKey: ByteArray, blindRecipientTag: ByteArray): Boolean {
            return isMatchingRecipient
        }
        override fun decrypt(ephemeralPublicKey: ByteArray, ciphertext: ByteArray, authTag: ByteArray): ByteArray {
            return ciphertext
        }
    }

    @Test
    fun testRecipientConsumesPacket() {
        val router = MeshRouter(MockCryptoEngine(isMatchingRecipient = true))
        val packet = MeshPacket(
            messageId = ByteArray(16) { 1 },
            ephemeralSenderPubKey = ByteArray(32),
            blindRecipientTag = ByteArray(16),
            ciphertext = "hello".toByteArray(),
            authTag = ByteArray(16)
        )

        val decision = router.processIncomingPacket(RawPacket(packet.toByteArray()))
        assertTrue(decision is RoutingDecision.Consume)
    }

    @Test
    fun testTransitNodeForwardsAndDecrementsTtl() {
        val router = MeshRouter(MockCryptoEngine(isMatchingRecipient = false))
        val packet = MeshPacket(
            ttl = 5,
            hopCount = 1,
            messageId = ByteArray(16) { 2 },
            ephemeralSenderPubKey = ByteArray(32),
            blindRecipientTag = ByteArray(16),
            ciphertext = "transit".toByteArray(),
            authTag = ByteArray(16)
        )

        val decision = router.processIncomingPacket(RawPacket(packet.toByteArray()))
        assertTrue(decision is RoutingDecision.Forward)
        val forwarded = (decision as RoutingDecision.Forward).updatedPacket
        assertEquals(4, forwarded.ttl.toInt())
        assertEquals(2, forwarded.hopCount.toInt())
    }

    @Test
    fun testExpiredTtlDropped() {
        val router = MeshRouter(MockCryptoEngine(isMatchingRecipient = false))
        val packet = MeshPacket(
            ttl = 1,
            messageId = ByteArray(16) { 3 },
            ephemeralSenderPubKey = ByteArray(32),
            blindRecipientTag = ByteArray(16),
            ciphertext = "expired".toByteArray(),
            authTag = ByteArray(16)
        )

        val decision = router.processIncomingPacket(RawPacket(packet.toByteArray()))
        assertTrue(decision is RoutingDecision.Drop)
    }
}
