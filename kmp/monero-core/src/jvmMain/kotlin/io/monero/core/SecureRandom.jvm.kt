package io.monero.core

import java.security.SecureRandom

actual fun generateSecureRandom(size: Int): ByteArray {
    val bytes = ByteArray(size)
    SecureRandom().nextBytes(bytes)
    return bytes
}
