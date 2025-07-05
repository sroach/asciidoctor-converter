package gy.roach.asciidoctor.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Service for encrypting and decrypting sensitive data like Personal Access Tokens.
 * Uses AES-256-GCM for authenticated encryption.
 */
@Service
class EncryptionService(@Value("\${app.encryption.key:default-key-change-me}") private val encryptionKey: String) {

    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
    }

    private val secretKey: SecretKey by lazy {
        // In production, use a proper key derivation function like PBKDF2
        val keyBytes = encryptionKey.toByteArray(StandardCharsets.UTF_8)
        val normalizedKey = ByteArray(32) // AES-256 requires 32-byte key
        System.arraycopy(keyBytes, 0, normalizedKey, 0, minOf(keyBytes.size, 32))
        SecretKeySpec(normalizedKey, ALGORITHM)
    }

    /**
     * Encrypts a plain text string using AES-256-GCM.
     * 
     * @param plainText The text to encrypt
     * @return Base64 encoded encrypted string with IV prepended
     */
    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        
        // Generate random IV
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        
        val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec)
        
        val encryptedData = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        
        // Prepend IV to encrypted data
        val encryptedWithIv = ByteArray(iv.size + encryptedData.size)
        System.arraycopy(iv, 0, encryptedWithIv, 0, iv.size)
        System.arraycopy(encryptedData, 0, encryptedWithIv, iv.size, encryptedData.size)
        
        return Base64.getEncoder().encodeToString(encryptedWithIv)
    }

    /**
     * Decrypts an encrypted string using AES-256-GCM.
     * 
     * @param encryptedText Base64 encoded encrypted string with IV prepended
     * @return The decrypted plain text
     * @throws Exception if decryption fails
     */
    fun decrypt(encryptedText: String): String {
        val encryptedWithIv = Base64.getDecoder().decode(encryptedText)
        
        // Extract IV and encrypted data
        val iv = ByteArray(GCM_IV_LENGTH)
        val encryptedData = ByteArray(encryptedWithIv.size - GCM_IV_LENGTH)
        System.arraycopy(encryptedWithIv, 0, iv, 0, iv.size)
        System.arraycopy(encryptedWithIv, iv.size, encryptedData, 0, encryptedData.size)
        
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)
        
        val decryptedData = cipher.doFinal(encryptedData)
        return String(decryptedData, StandardCharsets.UTF_8)
    }

    /**
     * Generates a new secure encryption key for configuration.
     * This is a utility method for generating keys - not for production use.
     */
    fun generateSecureKey(): String {
        val keyGenerator = KeyGenerator.getInstance(ALGORITHM)
        keyGenerator.init(256)
        val secretKey = keyGenerator.generateKey()
        return Base64.getEncoder().encodeToString(secretKey.encoded)
    }
}
