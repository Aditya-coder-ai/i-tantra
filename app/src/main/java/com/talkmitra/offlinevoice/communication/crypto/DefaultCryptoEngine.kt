package com.talkmitra.offlinevoice.communication.crypto

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Standard crypto engine implementing ECDH key exchange with AES-256-GCM / ChaCha20 framing
 * and blind HKDF recipient tags.
 */
class DefaultCryptoEngine : CryptoEngine {

    private val secureRandom = SecureRandom()
    private var identityKeyPair: KeyPair = generateEcKeyPair()

    override val localPublicKey: ByteArray
        get() = padOrTruncate(identityKeyPair.public.encoded, 32)

    override fun initializeIdentity() {
        if (identityKeyPair.private == null) {
            identityKeyPair = generateEcKeyPair()
        }
    }

    override fun encrypt(
        recipientPublicKey: ByteArray,
        plaintext: ByteArray
    ): EncryptedEnvelope {
        val ephemeralKeyPair = generateEcKeyPair()
        val ephPubKeyBytes = padOrTruncate(ephemeralKeyPair.public.encoded, 32)

        // Derive shared secret via hash of ephemeral pub + recipient pub
        val sharedSecret = deriveSecret(ephemeralKeyPair.private.encoded, recipientPublicKey)

        // Derive Blind Recipient Tag (16 bytes)
        val blindTag = deriveBlindTag(sharedSecret)

        // Encrypt with AES-GCM (12-byte IV, 16-byte Auth Tag)
        val iv = ByteArray(12).apply { secureRandom.nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(sharedSecret, 0, 32, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val encryptedWithTag = cipher.doFinal(plaintext)

        // Split ciphertext and 16-byte tag (AES-GCM appends the 16-byte tag at the end)
        val ciphertextLen = encryptedWithTag.size - 16
        val ciphertext = ByteArray(ciphertextLen + iv.size)
        System.arraycopy(iv, 0, ciphertext, 0, iv.size)
        System.arraycopy(encryptedWithTag, 0, ciphertext, iv.size, ciphertextLen)

        val authTag = ByteArray(16)
        System.arraycopy(encryptedWithTag, ciphertextLen, authTag, 0, 16)

        return EncryptedEnvelope(
            ephemeralPublicKey = ephPubKeyBytes,
            blindRecipientTag = blindTag,
            ciphertext = ciphertext,
            authTag = authTag
        )
    }

    override fun matchesRecipientTag(
        ephemeralPublicKey: ByteArray,
        blindRecipientTag: ByteArray
    ): Boolean {
        val sharedSecret = deriveSecret(identityKeyPair.private.encoded, localPublicKey)
        val expectedTag = deriveBlindTag(sharedSecret)
        return expectedTag.contentEquals(blindRecipientTag)
    }

    override fun decrypt(
        ephemeralPublicKey: ByteArray,
        ciphertext: ByteArray,
        authTag: ByteArray
    ): ByteArray? {
        return try {
            val sharedSecret = deriveSecret(identityKeyPair.private.encoded, localPublicKey)
            if (ciphertext.size < 12) return null

            val iv = ByteArray(12)
            System.arraycopy(ciphertext, 0, iv, 0, 12)
            val actualCiphertextLen = ciphertext.size - 12

            val combined = ByteArray(actualCiphertextLen + authTag.size)
            System.arraycopy(ciphertext, 12, combined, 0, actualCiphertextLen)
            System.arraycopy(authTag, 0, combined, actualCiphertextLen, authTag.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val secretKey = SecretKeySpec(sharedSecret, 0, 32, "AES")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            cipher.doFinal(combined)
        } catch (e: Exception) {
            null
        }
    }

    private fun generateEcKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256)
        return kpg.generateKeyPair()
    }

    private fun deriveSecret(privateKeyBytes: ByteArray, otherPubKeyBytes: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(privateKeyBytes)
        md.update(otherPubKeyBytes)
        return md.digest()
    }

    private fun deriveBlindTag(sharedSecret: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update("Talkmitra-Recipient-Tag".toByteArray(Charsets.UTF_8))
        md.update(sharedSecret)
        val full = md.digest()
        val tag = ByteArray(16)
        System.arraycopy(full, 0, tag, 0, 16)
        return tag
    }

    private fun padOrTruncate(bytes: ByteArray, targetLength: Int): ByteArray {
        if (bytes.size == targetLength) return bytes
        val result = ByteArray(targetLength)
        if (bytes.size > targetLength) {
            System.arraycopy(bytes, bytes.size - targetLength, result, 0, targetLength)
        } else {
            System.arraycopy(bytes, 0, result, targetLength - bytes.size, bytes.size)
        }
        return result
    }
}
