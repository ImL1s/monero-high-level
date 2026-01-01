package io.monero.crypto

import platform.posix.*
import kotlinx.cinterop.*

/**
 * macOS/iOS implementation of SecureRandom using /dev/urandom.
 */
actual object SecureRandom {
    @OptIn(ExperimentalForeignApi::class)
    actual fun nextBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        
        memScoped {
            val fd = open("/dev/urandom", O_RDONLY)
            if (fd < 0) {
                throw RuntimeException("Failed to open /dev/urandom")
            }
            
            try {
                bytes.usePinned { pinned ->
                    val bytesRead = read(fd, pinned.addressOf(0), size.toULong())
                    if (bytesRead.toLong() != size.toLong()) {
                        throw RuntimeException("Failed to read from /dev/urandom")
                    }
                }
            } finally {
                close(fd)
            }
        }
        
        return bytes
    }
}
