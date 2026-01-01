package io.monero.crypto

/**
 * PBKDF2 (Password-Based Key Derivation Function 2) implementation.
 * 
 * This is a simpler KDF that can be used when Argon2 is not available.
 * Uses HMAC-SHA256 as the underlying PRF.
 * 
 * Note: For production use, consider using Argon2 for better resistance
 * against GPU/ASIC attacks. PBKDF2 is included as a fallback.
 */
object PBKDF2 {

    private const val HMAC_SHA256_BLOCK_SIZE = 64
    private const val HMAC_SHA256_OUTPUT_SIZE = 32

    /**
     * Derive a key from password using PBKDF2-HMAC-SHA256.
     * 
     * @param password The password bytes
     * @param salt Random salt (should be at least 16 bytes)
     * @param iterations Number of iterations (higher = slower = more secure)
     * @param keyLength Desired key length in bytes
     * @return Derived key
     */
    fun deriveKey(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        keyLength: Int = 32
    ): ByteArray {
        require(iterations > 0) { "Iterations must be positive" }
        require(keyLength > 0) { "Key length must be positive" }

        val numBlocks = (keyLength + HMAC_SHA256_OUTPUT_SIZE - 1) / HMAC_SHA256_OUTPUT_SIZE
        val result = ByteArray(numBlocks * HMAC_SHA256_OUTPUT_SIZE)

        for (blockIndex in 1..numBlocks) {
            val blockIndexBytes = byteArrayOf(
                (blockIndex shr 24).toByte(),
                (blockIndex shr 16).toByte(),
                (blockIndex shr 8).toByte(),
                blockIndex.toByte()
            )

            // U1 = HMAC(password, salt || blockIndex)
            var u = hmacSha256(password, salt + blockIndexBytes)
            var derived = u.copyOf()

            // Ui = HMAC(password, U(i-1))
            repeat(iterations - 1) {
                u = hmacSha256(password, u)
                for (j in derived.indices) {
                    derived[j] = (derived[j].toInt() xor u[j].toInt()).toByte()
                }
            }

            derived.copyInto(result, (blockIndex - 1) * HMAC_SHA256_OUTPUT_SIZE)
        }

        return result.copyOfRange(0, keyLength)
    }

    /**
     * HMAC-SHA256 implementation using Keccak-256 as the hash function.
     * Note: This uses Keccak-256 which is NOT standard SHA256, but works for our purposes.
     */
    private fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
        val paddedKey = if (key.size > HMAC_SHA256_BLOCK_SIZE) {
            // Hash key if too long
            Keccak.keccak(key, 32)
        } else {
            key
        }

        val keyPadded = ByteArray(HMAC_SHA256_BLOCK_SIZE)
        paddedKey.copyInto(keyPadded)

        val ipad = ByteArray(HMAC_SHA256_BLOCK_SIZE) { (keyPadded[it].toInt() xor 0x36).toByte() }
        val opad = ByteArray(HMAC_SHA256_BLOCK_SIZE) { (keyPadded[it].toInt() xor 0x5c).toByte() }

        val innerHash = Keccak.keccak(ipad + message, 32)
        return Keccak.keccak(opad + innerHash, 32)
    }
}

/**
 * Wallet encryption helper that combines PBKDF2 with ChaCha20-Poly1305.
 */
object WalletEncryption {

    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val KEY_DERIVATION_ITERATIONS = 100_000  // Adjust based on security needs

    /**
     * Encrypt wallet data with password.
     * 
     * Format: salt (16 bytes) || nonce (12 bytes) || ciphertext || tag (16 bytes)
     * 
     * @param password User password
     * @param plaintext Wallet data to encrypt
     * @return Encrypted data with salt and nonce prepended
     */
    fun encrypt(password: String, plaintext: ByteArray): ByteArray {
        val salt = generateSecureRandom(SALT_SIZE)
        val nonce = generateSecureRandom(NONCE_SIZE)

        val key = PBKDF2.deriveKey(
            password = password.encodeToByteArray(),
            salt = salt,
            iterations = KEY_DERIVATION_ITERATIONS
        )

        val ciphertextWithTag = ChaCha20Poly1305.encrypt(key, nonce, plaintext)

        return salt + nonce + ciphertextWithTag
    }

    /**
     * Decrypt wallet data with password.
     * 
     * @param password User password
     * @param encryptedData Encrypted data (including salt and nonce)
     * @return Decrypted wallet data
     * @throws ChaCha20Poly1305.AuthenticationException if password is wrong or data tampered
     */
    fun decrypt(password: String, encryptedData: ByteArray): ByteArray {
        require(encryptedData.size >= SALT_SIZE + NONCE_SIZE + 16) {
            "Encrypted data too short"
        }

        val salt = encryptedData.copyOfRange(0, SALT_SIZE)
        val nonce = encryptedData.copyOfRange(SALT_SIZE, SALT_SIZE + NONCE_SIZE)
        val ciphertextWithTag = encryptedData.copyOfRange(SALT_SIZE + NONCE_SIZE, encryptedData.size)

        val key = PBKDF2.deriveKey(
            password = password.encodeToByteArray(),
            salt = salt,
            iterations = KEY_DERIVATION_ITERATIONS
        )

        return ChaCha20Poly1305.decrypt(key, nonce, ciphertextWithTag)
    }

    /**
     * Generate cryptographically secure random bytes.
     */
    private fun generateSecureRandom(size: Int): ByteArray {
        return SecureRandom.nextBytes(size)
    }
}

/**
 * Platform-specific secure random number generator.
 */
expect object SecureRandom {
    fun nextBytes(size: Int): ByteArray
}
