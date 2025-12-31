package io.monero.core

/**
 * Generate cryptographically secure random bytes.
 * Platform-specific implementation required.
 */
expect fun generateSecureRandom(size: Int): ByteArray
