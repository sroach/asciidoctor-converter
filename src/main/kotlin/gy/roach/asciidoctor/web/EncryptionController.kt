package gy.roach.asciidoctor.web

import gy.roach.asciidoctor.service.EncryptionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/encryption")
class EncryptionController(private val encryptionService: EncryptionService) {

    /**
     * Generate a new secure encryption key
     * GET /api/encryption/generate-key
     */
    @GetMapping("/generate-key")
    fun generateKey(): ResponseEntity<Map<String, String>> {
        val key = encryptionService.generateSecureKey()
        return ResponseEntity.ok(mapOf(
            "key" to key,
            "note" to "Store this key securely in ENCRYPTION_KEY environment variable"
        ))
    }

    /**
     * Encrypt a value (e.g., GitHub PAT)
     * POST /api/encryption/encrypt
     */
    @PostMapping("/encrypt")
    fun encrypt(@RequestBody request: EncryptRequest): ResponseEntity<Map<String, String>> {
        val encrypted = encryptionService.encrypt(request.value)
        return ResponseEntity.ok(mapOf(
            "encrypted" to encrypted
        ))
    }

    /**
     * Decrypt a value (for testing/verification only)
     * POST /api/encryption/decrypt
     */
    /*@PostMapping("/decrypt")
    fun decrypt(@RequestBody request: DecryptRequest): ResponseEntity<Map<String, String>> {
        return try {
            val decrypted = encryptionService.decrypt(request.encrypted)
            ResponseEntity.ok(mapOf(
                "decrypted" to decrypted
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "error" to "Decryption failed: ${e.message}"
            ))
        }
    }*/

    data class EncryptRequest(val value: String)
    data class DecryptRequest(val encrypted: String)
}
