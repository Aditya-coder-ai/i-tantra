package com.itantra.offlinevoice.communication.model

/**
 * Represents an offline peer identity identified by their 32-byte public key.
 */
data class PeerIdentity(
    val alias: String,
    val publicKey: ByteArray,
    val isVerified: Boolean = false
) {
    val hexPublicKey: String
        get() = publicKey.joinToString("") { "%02x".format(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PeerIdentity
        if (alias != other.alias) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (isVerified != other.isVerified) return false
        return true
    }

    override fun hashCode(): Int {
        var result = alias.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + isVerified.hashCode()
        return result
    }
}
