package com.talkmitra.offlinevoice.security

/**
 * Base class for all cryptographic and security exceptions within VoiceLink.
 */
sealed class SecurityException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Thrown when AEAD authentication tag validation fails, indicating payload or header tampering. */
class AuthenticationFailedException(message: String = "AEAD authentication tag verification failed. Packet may be tampered.", cause: Throwable? = null) :
    SecurityException(message, cause)

/** Thrown when a packet violates the sliding replay window or has an expired timestamp. */
class ReplayAttackException(message: String = "Replayed or out-of-window packet detected.", cause: Throwable? = null) :
    SecurityException(message, cause)

/** Thrown when a packet with an identical messageId has already been seen and processed. */
class DuplicateMessageException(val messageId: String) :
    SecurityException("Duplicate message ID encountered: $messageId")

/** Thrown when an encrypted packet originates from a device not present in the trusted devices store. */
class UnknownDeviceException(val senderId: String) :
    SecurityException("Unknown or unverified sender device: $senderId")

/** Thrown when attempting to encrypt or decrypt with an expired or non-existent session. */
class SessionExpiredException(val sessionId: String) :
    SecurityException("Session has expired or does not exist: $sessionId")

/** Thrown when no active session is established with the target recipient. */
class MissingSessionException(val recipientId: String) :
    SecurityException("No active session found for recipient: $recipientId")

/** Thrown when a cryptographic key is invalid, corrupted, or unsupported. */
class InvalidKeyException(message: String, cause: Throwable? = null) :
    SecurityException(message, cause)

/** Thrown when an invalid or reused nonce/IV is detected. */
class InvalidNonceException(message: String = "Invalid or duplicate nonce detected.") :
    SecurityException(message)

/** Thrown when encrypted ciphertext format is malformed or corrupted. */
class CorruptedPacketException(message: String, cause: Throwable? = null) :
    SecurityException(message, cause)

/** Thrown when hardware Keystore or encrypted storage operations fail. */
class KeyStorageException(message: String, cause: Throwable? = null) :
    SecurityException(message, cause)

/** Thrown when the pairing handshake fails, is aborted, or verification codes do not match. */
class PairingFailedException(message: String, cause: Throwable? = null) :
    SecurityException(message, cause)
