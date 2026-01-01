package io.monero.crypto

/**
 * ChaCha20-Poly1305 Authenticated Encryption with Associated Data (AEAD).
 * 
 * This is the encryption scheme used by Monero for wallet file encryption.
 * 
 * Implementation based on RFC 8439.
 */
object ChaCha20Poly1305 {

    private const val TAG_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val KEY_SIZE = 32

    /**
     * Encrypt data with ChaCha20-Poly1305.
     * 
     * @param key 32-byte encryption key
     * @param nonce 12-byte nonce (must be unique for each message with same key)
     * @param plaintext Data to encrypt
     * @param associatedData Additional data to authenticate but not encrypt (optional)
     * @return Ciphertext with appended 16-byte authentication tag
     */
    fun encrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray = ByteArray(0)
    ): ByteArray {
        require(key.size == KEY_SIZE) { "Key must be $KEY_SIZE bytes" }
        require(nonce.size == NONCE_SIZE) { "Nonce must be $NONCE_SIZE bytes" }

        // Generate Poly1305 key from first ChaCha20 block
        val poly1305Key = ChaCha20.generateBlock(key, nonce, 0u).copyOfRange(0, 32)

        // Encrypt plaintext with ChaCha20 starting at counter=1
        val ciphertext = ChaCha20.cipher(key, nonce, 1u, plaintext)

        // Generate authentication tag
        val tag = generateTag(poly1305Key, associatedData, ciphertext)

        // Append tag to ciphertext
        return ciphertext + tag
    }

    /**
     * Decrypt data with ChaCha20-Poly1305.
     * 
     * @param key 32-byte encryption key
     * @param nonce 12-byte nonce
     * @param ciphertextWithTag Ciphertext with appended authentication tag
     * @param associatedData Additional authenticated data (must match encryption)
     * @return Decrypted plaintext
     * @throws AuthenticationException if tag verification fails
     */
    fun decrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertextWithTag: ByteArray,
        associatedData: ByteArray = ByteArray(0)
    ): ByteArray {
        require(key.size == KEY_SIZE) { "Key must be $KEY_SIZE bytes" }
        require(nonce.size == NONCE_SIZE) { "Nonce must be $NONCE_SIZE bytes" }
        require(ciphertextWithTag.size >= TAG_SIZE) { "Ciphertext too short (missing tag)" }

        val ciphertext = ciphertextWithTag.copyOfRange(0, ciphertextWithTag.size - TAG_SIZE)
        val providedTag = ciphertextWithTag.copyOfRange(ciphertextWithTag.size - TAG_SIZE, ciphertextWithTag.size)

        // Generate Poly1305 key
        val poly1305Key = ChaCha20.generateBlock(key, nonce, 0u).copyOfRange(0, 32)

        // Verify tag
        val expectedTag = generateTag(poly1305Key, associatedData, ciphertext)

        if (!constantTimeEquals(providedTag, expectedTag)) {
            throw AuthenticationException("Authentication tag verification failed")
        }

        // Decrypt
        return ChaCha20.cipher(key, nonce, 1u, ciphertext)
    }

    /**
     * Generate Poly1305 authentication tag for ChaCha20-Poly1305 AEAD.
     * 
     * Format: AAD | pad | ciphertext | pad | len(AAD) | len(ciphertext)
     */
    private fun generateTag(poly1305Key: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
        val macData = buildMacData(aad, ciphertext)
        return Poly1305.mac(poly1305Key, macData)
    }

    /**
     * Build the data to be authenticated according to RFC 8439.
     */
    private fun buildMacData(aad: ByteArray, ciphertext: ByteArray): ByteArray {
        val aadPadLen = (16 - (aad.size % 16)) % 16
        val ctPadLen = (16 - (ciphertext.size % 16)) % 16

        val result = ByteArray(aad.size + aadPadLen + ciphertext.size + ctPadLen + 16)
        var offset = 0

        // AAD
        aad.copyInto(result, offset)
        offset += aad.size + aadPadLen

        // Ciphertext
        ciphertext.copyInto(result, offset)
        offset += ciphertext.size + ctPadLen

        // Lengths as 64-bit little-endian
        writeLittleEndian64(result, offset, aad.size.toLong())
        writeLittleEndian64(result, offset + 8, ciphertext.size.toLong())

        return result
    }

    private fun writeLittleEndian64(dest: ByteArray, offset: Int, value: Long) {
        for (i in 0..7) {
            dest[offset + i] = (value shr (i * 8)).toByte()
        }
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }

    /**
     * Exception thrown when authentication fails.
     */
    class AuthenticationException(message: String) : Exception(message)
}
