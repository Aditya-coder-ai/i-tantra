package com.talkmitra.offlinevoice.communication.transport.mesh

import com.talkmitra.offlinevoice.communication.crypto.CryptoEngine
import com.talkmitra.offlinevoice.communication.model.MeshPacket
import com.talkmitra.offlinevoice.communication.model.RawPacket

sealed interface RoutingDecision {
    /** The local device is the intended recipient; decrypt and deliver */
    data class Consume(val packet: MeshPacket) : RoutingDecision

    /** The local device is a transit node; forward opaque packet with updated TTL */
    data class Forward(val updatedPacket: MeshPacket) : RoutingDecision

    /** Packet is invalid, duplicate, or TTL expired; drop silently */
    data class Drop(val reason: String) : RoutingDecision
}

/**
 * Core mesh routing logic: validates packets, suppresses duplicates,
 * inspects opaque recipient tokens, and decrements TTL for blind forwarding.
 */
class MeshRouter(
    private val cryptoEngine: CryptoEngine,
    private val deduplicationCache: DeduplicationCache = DeduplicationCache()
) {

    fun processIncomingPacket(rawPacket: RawPacket): RoutingDecision {
        val packet = MeshPacket.parse(rawPacket.data)
            ?: return RoutingDecision.Drop("Invalid packet format or magic bytes")

        // 1. Loop prevention & Duplicate suppression
        if (deduplicationCache.isDuplicateOrSeen(packet.messageId)) {
            return RoutingDecision.Drop("Duplicate packet suppressed (MessageId already seen)")
        }

        // 2. Check if local node is the intended recipient
        val isRecipient = cryptoEngine.matchesRecipientTag(
            ephemeralPublicKey = packet.ephemeralSenderPubKey,
            blindRecipientTag = packet.blindRecipientTag
        )

        if (isRecipient) {
            return RoutingDecision.Consume(packet)
        }

        // 3. Transit Node: Check TTL
        if (packet.ttl <= 1) {
            return RoutingDecision.Drop("TTL expired (TTL <= 1)")
        }

        // 4. Prepare opaque forwarding packet (decrement TTL, increment Hop Count)
        val forwardedPacket = packet.copy(
            ttl = (packet.ttl - 1).toByte(),
            hopCount = (packet.hopCount + 1).toByte()
        )

        return RoutingDecision.Forward(forwardedPacket)
    }

    fun markMessageSent(messageId: ByteArray) {
        deduplicationCache.markSeen(messageId)
    }
}
