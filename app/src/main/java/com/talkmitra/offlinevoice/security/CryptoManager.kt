package com.talkmitra.offlinevoice.security

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Core cryptographic engine providing authenticated encryption (AES-256-GCM),
 * elliptic curve key agreement (ECDH secp256r1), signatures (ECDSA), key derivation (HKDF-SHA256),
 * and secure random nonce generation.
 *
 * Designed to run completely offline without external native libraries or cloud dependencies.
 */
object CryptoManager {

    private const val EC_CURVE_NAME = "secp256r1"
    private const val EC_ALGORITHM = "EC"
    private const val ECDH_ALGORITHM = "ECDH"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private const val AES_GCM_CIPHER = "AES/GCM/NoPadding"
    private const val HMAC_SHA256 = "HmacSHA256"
    private const val GCM_TAG_LENGTH_BITS = 128
    const val GCM_NONCE_LENGTH_BYTES = 12
    const val AES_KEY_LENGTH_BYTES = 32

    private val secureRandom = SecureRandom()

    // =========================================================================
    // 1. Asymmetric Key Generation & Exchange (ECDH on secp256r1 / NIST P-256)
    // =========================================================================

    /**
     * Generates a new Elliptic Curve (secp256r1) key pair.
     */
    fun generateEcKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance(EC_ALGORITHM)
        val ecSpec = ECGenParameterSpec(EC_CURVE_NAME)
        kpg.initialize(ecSpec, secureRandom)
        return kpg.generateKeyPair()
    }

    /**
     * Serializes a public key to Base64 (X.509 standard format).
     */
    fun encodePublicKey(publicKey: PublicKey): String {
        return Base64.getEncoder().encodeToString(publicKey.encoded)
    }

    /**
     * Deserializes a public key from Base64 (X.509 format).
     */
    fun decodePublicKey(base64PublicKey: String): PublicKey {
        try {
            val keyBytes = Base64.getDecoder().decode(base64PublicKey)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance(EC_ALGORITHM)
            return keyFactory.generatePublic(keySpec)
        } catch (e: Exception) {
            throw InvalidKeyException("Failed to decode X.509 EC public key from Base64", e)
        }
    }

    /**
     * Computes the raw ECDH shared secret between a private key and peer's public key.
     */
    fun computeEcdhSharedSecret(privateKey: PrivateKey, peerPublicKey: PublicKey): ByteArray {
        try {
            val keyAgreement = KeyAgreement.getInstance(ECDH_ALGORITHM)
            keyAgreement.init(privateKey)
            keyAgreement.doPhase(peerPublicKey, true)
            return keyAgreement.generateSecret()
        } catch (e: Exception) {
            throw InvalidKeyException("ECDH key agreement failed with peer public key", e)
        }
    }

    // =========================================================================
    // 2. Key Derivation (HKDF-SHA256 - RFC 5869)
    // =========================================================================

    /**
     * Extracts and expands cryptographic key material using HKDF-SHA256.
     *
     * @param ikm Input Keying Material (e.g. ECDH shared secret)
     * @param salt Optional salt (if null or empty, an all-zero 32-byte salt is used per RFC 5869)
     * @param info Application-specific context and info tag
     * @param length Desired output key length in bytes
     */
    fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray? = null,
        info: ByteArray = ByteArray(0),
        length: Int = AES_KEY_LENGTH_BYTES
    ): ByteArray {
        val effectiveSalt = if (salt == null || salt.isEmpty()) ByteArray(32) else salt
        val prk = hmacSha256(effectiveSalt, ikm)

        var t = ByteArray(0)
        val okm = ByteArray(length)
        var generatedBytes = 0
        var round = 1

        while (generatedBytes < length) {
            val mac = Mac.getInstance(HMAC_SHA256)
            mac.init(SecretKeySpec(prk, HMAC_SHA256))
            mac.update(t)
            mac.update(info)
            mac.update(round.toByte())
            t = mac.doFinal()

            val toCopy = minOf(t.size, length - generatedBytes)
            System.arraycopy(t, 0, okm, generatedBytes, toCopy)
            generatedBytes += toCopy
            round++
        }
        return okm
    }

    /**
     * Computes standard HMAC-SHA256 over data.
     */
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(key, HMAC_SHA256))
        return mac.doFinal(data)
    }

    // =========================================================================
    // 3. Authenticated Encryption with Associated Data (AES-256-GCM)
    // =========================================================================

    /**
     * Encrypts plaintext using AES-256-GCM with Associated Authenticated Data (AAD).
     *
     * @param plaintext The raw message bytes to encrypt.
     * @param key 256-bit (32-byte) symmetric key.
     * @param iv 96-bit (12-byte) initialization vector / nonce.
     * @param aad Optional Associated Authenticated Data (e.g. packet header metadata).
     * @return Raw ciphertext concatenated with 16-byte authentication tag.
     */
    fun encryptAesGcm(
        plaintext: ByteArray,
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray? = null
    ): ByteArray {
        require(key.size == AES_KEY_LENGTH_BYTES) { "AES-256 requires exactly a 32-byte key, provided: ${key.size}" }
        require(iv.size == GCM_NONCE_LENGTH_BYTES) { "AES-GCM recommended nonce length is 12 bytes, provided: ${iv.size}" }

        try {
            val cipher = Cipher.getInstance(AES_GCM_CIPHER)
            val secretKeySpec = SecretKeySpec(key, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmSpec)

            if (aad != null && aad.isNotEmpty()) {
                cipher.updateAAD(aad)
            }

            return cipher.doFinal(plaintext)
        } catch (e: Exception) {
            throw SecurityException("Encryption failed: ${e.message}", e)
        }
    }

    /**
     * Decrypts ciphertext and verifies the 16-byte authentication tag using AES-256-GCM.
     *
     * @param ciphertextWithTag Ciphertext bytes concatenated with the 16-byte GCM authentication tag.
     * @param key 256-bit (32-byte) symmetric key.
     * @param iv 96-bit (12-byte) initialization vector / nonce.
     * @param aad Optional Associated Authenticated Data (must exactly match AAD provided during encryption).
     * @return Decrypted plaintext bytes.
     * @throws AuthenticationFailedException if tag or AAD verification fails.
     */
    fun decryptAesGcm(
        ciphertextWithTag: ByteArray,
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray? = null
    ): ByteArray {
        require(key.size == AES_KEY_LENGTH_BYTES) { "AES-256 requires exactly a 32-byte key, provided: ${key.size}" }
        require(iv.size == GCM_NONCE_LENGTH_BYTES) { "AES-GCM recommended nonce length is 12 bytes, provided: ${iv.size}" }

        try {
            val cipher = Cipher.getInstance(AES_GCM_CIPHER)
            val secretKeySpec = SecretKeySpec(key, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmSpec)

            if (aad != null && aad.isNotEmpty()) {
                cipher.updateAAD(aad)
            }

            return cipher.doFinal(ciphertextWithTag)
        } catch (e: AEADBadTagException) {
            throw AuthenticationFailedException("AEAD authentication tag mismatch. Ciphertext or header was modified in transit.", e)
        } catch (e: Exception) {
            throw AuthenticationFailedException("Decryption error: ${e.message}", e)
        }
    }

    /**
     * Derives a canonical 256-bit AES shared network channel key for immediate ad-hoc offline P2P communication.
     */
    fun deriveDefaultSessionKey(): ByteArray {
        return hkdfSha256(
            ikm = "VoiceLink-Emergency-Channel-Shared-Key-v1".toByteArray(Charsets.UTF_8),
            salt = "VoiceLink-Default-Salt".toByteArray(Charsets.UTF_8),
            info = "VoiceLink-P2P-AES-256-Key".toByteArray(Charsets.UTF_8),
            length = AES_KEY_LENGTH_BYTES
        )
    }

    // =========================================================================
    // 4. Digital Signatures & Fingerprinting
    // =========================================================================

    /**
     * Signs data using ECDSA (SHA256withECDSA).
     */
    fun signData(data: ByteArray, privateKey: PrivateKey): ByteArray {
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(privateKey, secureRandom)
        signature.update(data)
        return signature.sign()
    }

    /**
     * Verifies ECDSA signature against public key.
     */
    fun verifySignature(data: ByteArray, signatureBytes: ByteArray, publicKey: PublicKey): Boolean {
        return try {
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initVerify(publicKey)
            signature.update(data)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Computes SHA-256 digest of input data.
     */
    fun sha256(data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(data)
    }

    /**
     * Derives a standardized human-readable Device ID (e.g. `VL-7F3A92`)
     * from the SHA-256 hash of the public key bytes.
     */
    fun computeDeviceId(publicKeyBytes: ByteArray): String {
        val hash = sha256(publicKeyBytes)
        val hex = hash.take(3).joinToString("") { "%02X".format(it) }
        return "VL-$hex"
    }

    /**
     * Generates a 6-digit Short Authentication String (SAS) code for visual out-of-band verification.
     * Formatted as "### ###" (e.g. "482 917").
     */
    fun generateSasCode(keyA: ByteArray, keyB: ByteArray, salt: ByteArray): String {
        val combined = keyA + keyB + salt
        val hash = sha256(combined)
        // Convert first 4 bytes to an integer and modulo 1,000,000 for a 6-digit code
        val num = ((hash[0].toInt() and 0xFF) shl 24 or
                ((hash[1].toInt() and 0xFF) shl 16) or
                ((hash[2].toInt() and 0xFF) shl 8) or
                (hash[3].toInt() and 0xFF)) and 0x7FFFFFFF
        val code = num % 1_000_000
        val formatted = "%06d".format(code)
        return "${formatted.substring(0, 3)} ${formatted.substring(3, 6)}"
    }

    /**
     * Generates a cryptographically secure random nonce/IV.
     */
    fun generateSecureNonce(length: Int = GCM_NONCE_LENGTH_BYTES): ByteArray {
        val nonce = ByteArray(length)
        secureRandom.nextBytes(nonce)
        return nonce
    }
}
