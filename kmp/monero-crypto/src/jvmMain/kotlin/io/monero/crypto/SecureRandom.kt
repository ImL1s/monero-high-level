package io.monero.crypto

/**
 * JVM implementation of SecureRandom using java.security.SecureRandom.
 */
actual object SecureRandom {
    private val random = java.security.SecureRandom()

    actual fun nextBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return bytes
    }
}
