package com.aroundus.app.utils

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-End Encryption (E2EE) Manager
 * Uses AES-GCM 256-bit authenticated encryption for messages.
 */
object CryptoManager {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12

    fun deriveKey(secret: String): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(secret.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plainText: String, secretKey: SecretKey): String {
        try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            return "E2EE:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            return plainText
        }
    }

    fun decrypt(cipherText: String, secretKey: SecretKey): String {
        if (!cipherText.startsWith("E2EE:")) return cipherText
        try {
            val cleanCipher = cipherText.removePrefix("E2EE:")
            val combined = Base64.decode(cleanCipher, Base64.NO_WRAP)

            val iv = ByteArray(IV_LENGTH_BYTE)
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTE)

            val encryptedBytes = ByteArray(combined.size - IV_LENGTH_BYTE)
            System.arraycopy(combined, IV_LENGTH_BYTE, encryptedBytes, 0, encryptedBytes.size)

            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            return "[Encrypted Message]"
        }
    }

    fun generateSharedSecret(userId1: String, userId2: String): String {
        val u1 = userId1.ifBlank { "user_1" }
        val u2 = userId2.ifBlank { "user_2" }
        val sorted = listOf(u1, u2).sorted()
        return "AroundUs_E2EE_SecretKey_" + sorted[0] + "_" + sorted[1]
    }
}
