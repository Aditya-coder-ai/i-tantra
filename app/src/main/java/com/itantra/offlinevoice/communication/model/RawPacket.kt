package com.itantra.offlinevoice.communication.model

/**
 * Raw binary packet received from or passed to a physical radio transport.
 */
data class RawPacket(
    val data: ByteArray,
    val sourceAddress: String? = null,
    val transportTier: TransportTier = TransportTier.BLUETOOTH_DIRECT
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RawPacket
        if (!data.contentEquals(other.data)) return false
        if (sourceAddress != other.sourceAddress) return false
        if (transportTier != other.transportTier) return false
        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + (sourceAddress?.hashCode() ?: 0)
        result = 31 * result + transportTier.hashCode()
        return result
    }
}
