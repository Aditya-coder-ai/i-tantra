package com.itantra.offlinevoice.security

import com.itantra.offlinevoice.security.models.SessionInfo
import java.security.PrivateKey
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages active symmetric cryptographic sessions, session lifecycle,
 * sequence number monotonicity, and forward session rotation.
 */
class SessionManager(
    private val keyManager: KeyManager
) {

    /** Map of peerDeviceId -> active SessionInfo */
    private val activeSessionsByPeer = ConcurrentHashMap<String, SessionInfo>()

    /** Map of sessionId -> active SessionInfo */
    private val activeSessionsById = ConcurrentHashMap<String, SessionInfo>()

    companion object {
        const val DEFAULT_SESSION_EXPIRY_MS = 24 * 60 * 60 * 1000L // 24 hours
        const val MAX_MESSAGES_PER_SESSION = 1000L // Rotation threshold
    }

    /**
     * Establishes a new symmetric session with a paired peer using ECDH key agreement.
     *
     * @param peerDeviceId The unique device ID of the peer.
     * @param peerPublicKey The peer's decoded public key.
     * @param localPrivateKey The local private key to perform ECDH (defaults to device identity private key).
     */
    @Synchronized
    fun establishSession(
        peerDeviceId: String,
        peerPublicKey: PublicKey,
        localPrivateKey: PrivateKey = keyManager.getIdentityKeyPair().private
    ): SessionInfo {
        // Compute ECDH shared secret
        val rawSecret = CryptoManager.computeEcdhSharedSecret(localPrivateKey, peerPublicKey)

        // Derive 32-byte AES-256 session key
        val sessionKey = CryptoManager.hkdfSha256(
            ikm = rawSecret,
            salt = "VoiceLink-Session-Salt-v1".toByteArray(Charsets.UTF_8),
            info = "VoiceLink-AES-256-GCM-SessionKey".toByteArray(Charsets.UTF_8),
            length = CryptoManager.AES_KEY_LENGTH_BYTES
        )

        // Derive 16-byte unique Session ID
        val sessionIdBytes = CryptoManager.hkdfSha256(
            ikm = rawSecret,
            salt = sessionKey,
            info = "VoiceLink-SessionID".toByteArray(Charsets.UTF_8),
            length = 16
        )
        val sessionId = "SES-" + sessionIdBytes.take(6).joinToString("") { "%02X".format(it) }

        val sessionInfo = SessionInfo(
            sessionId = sessionId,
            peerDeviceId = peerDeviceId,
            sessionKey = sessionKey,
            txSequenceNumber = 0L,
            rxSequenceNumber = 0L,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + DEFAULT_SESSION_EXPIRY_MS,
            messagesCount = 0L
        )

        // Store active session
        activeSessionsByPeer[peerDeviceId]?.wipe()
        activeSessionsByPeer[peerDeviceId] = sessionInfo
        activeSessionsById[sessionId] = sessionInfo

        return sessionInfo
    }

    /**
     * Retrieves the active session for a peer device.
     */
    fun getSession(peerDeviceId: String): SessionInfo? {
        val session = activeSessionsByPeer[peerDeviceId] ?: return null
        if (session.isExpired()) {
            invalidateSession(peerDeviceId)
            return null
        }
        return session
    }

    /**
     * Retrieves an active session by its unique sessionId.
     */
    fun getSessionById(sessionId: String): SessionInfo? {
        val session = activeSessionsById[sessionId] ?: return null
        if (session.isExpired()) {
            activeSessionsById.remove(sessionId)
            activeSessionsByPeer.remove(session.peerDeviceId)
            session.wipe()
            return null
        }
        return session
    }

    /**
     * Rotates session key for forward secrecy (ratchets the key forward).
     */
    @Synchronized
    fun rotateSession(peerDeviceId: String): SessionInfo {
        val current = getSession(peerDeviceId)
            ?: throw MissingSessionException(peerDeviceId)

        // Ratchet the existing session key with HKDF
        val ratchetedKey = CryptoManager.hkdfSha256(
            ikm = current.sessionKey,
            salt = "VoiceLink-Ratchet-Salt".toByteArray(Charsets.UTF_8),
            info = "VoiceLink-Ratcheted-Key-${current.messagesCount}".toByteArray(Charsets.UTF_8),
            length = CryptoManager.AES_KEY_LENGTH_BYTES
        )

        val newSessionIdBytes = CryptoManager.sha256(ratchetedKey)
        val newSessionId = "SES-" + newSessionIdBytes.take(6).joinToString("") { "%02X".format(it) }

        val newSession = SessionInfo(
            sessionId = newSessionId,
            peerDeviceId = peerDeviceId,
            sessionKey = ratchetedKey,
            txSequenceNumber = 0L,
            rxSequenceNumber = 0L,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + DEFAULT_SESSION_EXPIRY_MS,
            messagesCount = 0L
        )

        current.wipe()
        activeSessionsByPeer[peerDeviceId] = newSession
        activeSessionsById[newSessionId] = newSession
        activeSessionsById.remove(current.sessionId)

        return newSession
    }

    /**
     * Checks if session needs rotation due to message volume or age.
     */
    fun shouldRotate(session: SessionInfo): Boolean {
        return session.messagesCount >= MAX_MESSAGES_PER_SESSION ||
                (System.currentTimeMillis() - session.createdAt) > (DEFAULT_SESSION_EXPIRY_MS / 2)
    }

    /**
     * Invalidates and wipes a session for a peer.
     */
    @Synchronized
    fun invalidateSession(peerDeviceId: String) {
        val session = activeSessionsByPeer.remove(peerDeviceId)
        if (session != null) {
            activeSessionsById.remove(session.sessionId)
            session.wipe()
        }
    }

    /**
     * Invalidates and wipes all active sessions.
     */
    @Synchronized
    fun clearAllSessions() {
        for (session in activeSessionsByPeer.values) {
            session.wipe()
        }
        activeSessionsByPeer.clear()
        activeSessionsById.clear()
    }
}
