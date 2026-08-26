package com.itantra.offlinevoice.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

class CryptoManagerTest {

    @Test
    fun testEcKeyPairGenerationAndSerialization() {
        val keyPair = CryptoManager.generateEcKeyPair()
        assertNotNull(keyPair.public)
        assertNotNull(keyPair.private)

        val encodedPub = CryptoManager.encodePublicKey(keyPair.public)
        assertTrue(encodedPub.isNotEmpty())

        val decodedPub = CryptoManager.decodePublicKey(encodedPub)
        assertEquals(keyPair.public, decodedPub)
    }

    @Test
    fun testEcdhKeyAgreementMutualSecret() {
        val aliceKeyPair = CryptoManager.generateEcKeyPair()
        val bobKeyPair = CryptoManager.generateEcKeyPair()

        // Alice computes secret with Bob's public key
        val secretAlice = CryptoManager.computeEcdhSharedSecret(aliceKeyPair.private, bobKeyPair.public)

        // Bob computes secret with Alice's public key
        val secretBob = CryptoManager.computeEcdhSharedSecret(bobKeyPair.private, aliceKeyPair.public)

        // Both raw secrets must be identical
        assertTrue("ECDH shared secrets must match", secretAlice.contentEquals(secretBob))
        assertEquals(32, secretAlice.size)
    }

    @Test
    fun testHkdfSha256Derivation() {
        val ikm = "test-shared-secret-bytes".toByteArray(Charsets.UTF_8)
        val salt = "test-salt".toByteArray(Charsets.UTF_8)
        val info1 = "Context-1".toByteArray(Charsets.UTF_8)
        val info2 = "Context-2".toByteArray(Charsets.UTF_8)

        val key1 = CryptoManager.hkdfSha256(ikm, salt, info1, 32)
        val key2 = CryptoManager.hkdfSha256(ikm, salt, info2, 32)
        val key1Again = CryptoManager.hkdfSha256(ikm, salt, info1, 32)

        assertEquals(32, key1.size)
        assertEquals(32, key2.size)
        assertTrue("Deterministic derivation with same inputs must match", key1.contentEquals(key1Again))
        assertFalse("Different info tags must produce distinct keys", key1.contentEquals(key2))
    }

    @Test
    fun testAes256GcmEncryptDecryptWithAad() {
        val key = ByteArray(32) { (it + 1).toByte() }
        val iv = CryptoManager.generateSecureNonce(12)
        val plaintext = "Hello VoiceLink Offline Security!".toByteArray(Charsets.UTF_8)
        val aad = "Header-Sender-VL-001|Seq-10".toByteArray(Charsets.UTF_8)

        val ciphertextWithTag = CryptoManager.encryptAesGcm(plaintext, key, iv, aad)
        assertTrue(ciphertextWithTag.size > plaintext.size)

        val decrypted = CryptoManager.decryptAesGcm(ciphertextWithTag, key, iv, aad)
        assertEquals(String(plaintext, Charsets.UTF_8), String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun testAes256GcmTamperedCiphertextThrowsAuthenticationFailure() {
        val key = ByteArray(32) { (it + 1).toByte() }
        val iv = CryptoManager.generateSecureNonce(12)
        val plaintext = "Sensitive data".toByteArray(Charsets.UTF_8)
        val aad = "AAD".toByteArray(Charsets.UTF_8)

        val ciphertextWithTag = CryptoManager.encryptAesGcm(plaintext, key, iv, aad)

        // Tamper with one byte of ciphertext
        ciphertextWithTag[0] = (ciphertextWithTag[0].toInt() xor 0x01).toByte()

        try {
            CryptoManager.decryptAesGcm(ciphertextWithTag, key, iv, aad)
            fail("Expected AuthenticationFailedException on tampered ciphertext")
        } catch (e: AuthenticationFailedException) {
            assertTrue(e.message!!.contains("AEAD"))
        }
    }

    @Test
    fun testAes256GcmTamperedAadThrowsAuthenticationFailure() {
        val key = ByteArray(32) { (it + 1).toByte() }
        val iv = CryptoManager.generateSecureNonce(12)
        val plaintext = "Sensitive data".toByteArray(Charsets.UTF_8)
        val aad = "Valid-AAD-Metadata".toByteArray(Charsets.UTF_8)
        val tamperedAad = "Tampered-AAD-Metadata".toByteArray(Charsets.UTF_8)

        val ciphertextWithTag = CryptoManager.encryptAesGcm(plaintext, key, iv, aad)

        try {
            CryptoManager.decryptAesGcm(ciphertextWithTag, key, iv, tamperedAad)
            fail("Expected AuthenticationFailedException on tampered AAD")
        } catch (e: AuthenticationFailedException) {
            assertTrue(e.message!!.contains("AEAD") || e.message!!.contains("mismatch"))
        }
    }

    @Test
    fun testSasCodeGenerationSymmetric() {
        val secretA = "Shared-Secret-1".toByteArray(Charsets.UTF_8)
        val secretB = "Shared-Secret-2".toByteArray(Charsets.UTF_8)
        val salt = "Nonce-Salt".toByteArray(Charsets.UTF_8)

        val code1 = CryptoManager.generateSasCode(secretA, secretB, salt)
        val code2 = CryptoManager.generateSasCode(secretA, secretB, salt)

        assertEquals(code1, code2)
        // Format should be 6 digits split as "### ###" (7 characters total)
        assertEquals(7, code1.length)
        assertTrue(code1.matches(Regex("\\d{3} \\d{3}")))
    }

    @Test
    fun testEcdsaSignAndVerify() {
        val keyPair = CryptoManager.generateEcKeyPair()
        val data = "VoiceLink Signed Transaction".toByteArray(Charsets.UTF_8)

        val signature = CryptoManager.signData(data, keyPair.private)
        val verified = CryptoManager.verifySignature(data, signature, keyPair.public)
        assertTrue(verified)

        val otherKeyPair = CryptoManager.generateEcKeyPair()
        val invalidVerified = CryptoManager.verifySignature(data, signature, otherKeyPair.public)
        assertFalse(invalidVerified)
    }

    @Test
    fun testComputeDeviceIdFormat() {
        val keyPair = CryptoManager.generateEcKeyPair()
        val deviceId = CryptoManager.computeDeviceId(keyPair.public.encoded)

        assertTrue(deviceId.startsWith("VL-"))
        assertEquals(9, deviceId.length) // "VL-" (3 chars) + 6 hex chars = 9
    }
}
