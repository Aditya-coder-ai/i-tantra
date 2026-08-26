package com.itantra.offlinevoice.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeyAndSessionManagerTest {

    private lateinit var storageA: SecureStorage
    private lateinit var keyManagerA: KeyManager
    private lateinit var sessionManagerA: SessionManager
    private lateinit var pairingManagerA: PairingManager

    private lateinit var storageB: SecureStorage
    private lateinit var keyManagerB: KeyManager
    private lateinit var sessionManagerB: SessionManager
    private lateinit var pairingManagerB: PairingManager

    @Before
    fun setUp() {
        storageA = InMemorySecureStorage()
        keyManagerA = KeyManager(storageA)
        sessionManagerA = SessionManager(keyManagerA)
        pairingManagerA = PairingManager(keyManagerA, sessionManagerA)

        storageB = InMemorySecureStorage()
        keyManagerB = KeyManager(storageB)
        sessionManagerB = SessionManager(keyManagerB)
        pairingManagerB = PairingManager(keyManagerB, sessionManagerB)
    }

    @Test
    fun testIdentityPersistenceAndFingerprint() {
        val identity = keyManagerA.getDeviceIdentity()
        assertNotNull(identity)
        assertTrue(identity.deviceId.startsWith("VL-"))
        assertEquals("EC-P256", identity.keyAlgorithm)

        // Load again from same storage
        val reloadedKeyManager = KeyManager(storageA)
        assertEquals(identity.deviceId, reloadedKeyManager.getDeviceIdentity().deviceId)
        assertEquals(identity.publicKeyBase64, reloadedKeyManager.getDeviceIdentity().publicKeyBase64)
    }

    @Test
    fun testPairingHandshakeAndSasAgreement() {
        // Step 1: Device A creates offer
        val (offer, ephKeyPairA) = pairingManagerA.createPairingOffer()
        assertEquals(keyManagerA.getDeviceIdentity().deviceId, offer.initiatorDeviceId)

        // Step 2: Device B processes offer
        val (response, sessionB) = pairingManagerB.processPairingOffer(offer)
        assertEquals(keyManagerB.getDeviceIdentity().deviceId, response.responderDeviceId)

        // Step 3: Device A processes response
        val sessionA = pairingManagerA.processPairingResponse(offer, response, ephKeyPairA.private)

        // Step 4: Verification code on both screens must be identical
        assertEquals("SAS codes on both devices must match exactly", sessionA.sasCode, sessionB.sasCode)
        assertEquals(7, sessionA.sasCode.length) // "482 917" format

        // Confirm pairing on both sides
        val trustedOnA = pairingManagerA.confirmPairing(sessionA)
        val trustedOnB = pairingManagerB.confirmPairing(sessionB)

        assertTrue(trustedOnA.isVerified)
        assertTrue(trustedOnB.isVerified)
        assertTrue(keyManagerA.isDeviceTrusted(keyManagerB.getDeviceIdentity().deviceId))
        assertTrue(keyManagerB.isDeviceTrusted(keyManagerA.getDeviceIdentity().deviceId))

        // Check that session keys were established
        val sessionOnA = sessionManagerA.getSession(keyManagerB.getDeviceIdentity().deviceId)
        assertNotNull(sessionOnA)
        assertFalse(sessionOnA!!.isExpired())
    }

    @Test
    fun testSessionRotationRatcheting() {
        // Direct pairing setup
        val pubKeyB = CryptoManager.decodePublicKey(keyManagerB.getDeviceIdentity().publicKeyBase64)
        val initialSession = sessionManagerA.establishSession(keyManagerB.getDeviceIdentity().deviceId, pubKeyB)
        val initialSessionId = initialSession.sessionId

        assertEquals(0L, initialSession.txSequenceNumber)
        initialSession.nextTxSequenceNumber()
        initialSession.nextTxSequenceNumber()
        assertEquals(2L, initialSession.txSequenceNumber)

        // Rotate session
        val rotatedSession = sessionManagerA.rotateSession(keyManagerB.getDeviceIdentity().deviceId)
        assertNotNull(rotatedSession)
        assertFalse(initialSessionId == rotatedSession.sessionId)
        assertEquals(0L, rotatedSession.txSequenceNumber)
    }

    @Test
    fun testSessionExpiry() {
        val pubKeyB = CryptoManager.decodePublicKey(keyManagerB.getDeviceIdentity().publicKeyBase64)
        val session = sessionManagerA.establishSession(keyManagerB.getDeviceIdentity().deviceId, pubKeyB)

        // Create an expired session info
        val expiredSession = session.copy(expiresAt = System.currentTimeMillis() - 1000L)
        assertTrue(expiredSession.isExpired())
    }
}
