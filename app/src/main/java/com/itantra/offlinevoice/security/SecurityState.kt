package com.itantra.offlinevoice.security

/**
 * Represents the lifecycle and operational states of the VoiceLink security subsystem.
 *
 * These states are observed by the UI and connection manager to inform the user of
 * link security, active pairing operations, encryption/decryption progress, and security faults.
 */
enum class SecurityState {
    /** Device has no active session or pairing with the target peer. */
    UNPAIRED,

    /** Active pairing handshake in progress with a nearby peer. */
    PAIRING,

    /** Waiting for manual user confirmation of the Short Authentication String (SAS) code. */
    PAIRING_VERIFICATION,

    /** Peer device identity is verified and stored in the trusted devices repository. */
    PAIRED,

    /** Symmetric session key successfully agreed via ECDH + HKDF and ready for message traffic. */
    SESSION_ESTABLISHED,

    /** Outgoing message was successfully encrypted with AES-256-GCM and authenticated. */
    ENCRYPTED,

    /** Incoming message was successfully authenticated and decrypted to plaintext. */
    DECRYPTED,

    /** AEAD authentication tag mismatch or tampered header detected; packet rejected. */
    AUTHENTICATION_FAILED,

    /** Incoming packet rejected due to duplicate sequence number or out-of-window replay. */
    REPLAY_DETECTED,

    /** Packet received from a sender ID not present in the trusted devices repository. */
    UNKNOWN_DEVICE,

    /** Cryptographic failure such as key generation error, corrupted key, or storage fault. */
    KEY_ERROR
}
