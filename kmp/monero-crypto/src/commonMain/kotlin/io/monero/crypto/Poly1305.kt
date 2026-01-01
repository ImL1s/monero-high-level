package io.monero.crypto

/**
 * Poly1305 message authentication code.
 * Platform-specific implementations for efficient big integer arithmetic.
 */
expect object Poly1305 {
    /**
     * Compute Poly1305 MAC.
     * 
     * @param key 32-byte one-time key (should be generated from ChaCha20)
     * @param message Message to authenticate
     * @return 16-byte authentication tag
     */
    fun mac(key: ByteArray, message: ByteArray): ByteArray
}
