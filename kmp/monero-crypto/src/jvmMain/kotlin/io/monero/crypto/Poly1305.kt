package io.monero.crypto

import java.math.BigInteger

/**
 * JVM Poly1305 implementation using BigInteger for 130-bit arithmetic.
 */
actual object Poly1305 {

    private val P = BigInteger.ONE.shiftLeft(130).subtract(BigInteger.valueOf(5))  // 2^130 - 5

    actual fun mac(key: ByteArray, message: ByteArray): ByteArray {
        require(key.size == 32) { "Key must be 32 bytes, got ${key.size}" }

        // Split key into r and s
        val r = clampR(key.copyOfRange(0, 16))
        val s = key.copyOfRange(16, 32)

        var accumulator = BigInteger.ZERO

        // Process message in 16-byte blocks
        var offset = 0
        while (offset < message.size) {
            val blockSize = minOf(16, message.size - offset)
            val block = message.copyOfRange(offset, offset + blockSize)

            // Add 0x01 byte after the block
            val n = bytesToBigIntLE(block + byteArrayOf(0x01))

            accumulator = accumulator.add(n)
            accumulator = accumulator.multiply(bytesToBigIntLE(r))
            accumulator = accumulator.mod(P)

            offset += 16
        }

        // Add s
        accumulator = accumulator.add(bytesToBigIntLE(s))

        // Take lower 128 bits
        return bigIntToBytesLE(accumulator, 16)
    }

    private fun clampR(r: ByteArray): ByteArray {
        val clamped = r.copyOf()
        clamped[3] = (clamped[3].toInt() and 0x0f).toByte()
        clamped[7] = (clamped[7].toInt() and 0x0f).toByte()
        clamped[11] = (clamped[11].toInt() and 0x0f).toByte()
        clamped[15] = (clamped[15].toInt() and 0x0f).toByte()
        clamped[4] = (clamped[4].toInt() and 0xfc).toByte()
        clamped[8] = (clamped[8].toInt() and 0xfc).toByte()
        clamped[12] = (clamped[12].toInt() and 0xfc).toByte()
        return clamped
    }

    private fun bytesToBigIntLE(bytes: ByteArray): BigInteger {
        val reversed = bytes.reversedArray()
        return BigInteger(1, reversed)
    }

    private fun bigIntToBytesLE(n: BigInteger, size: Int): ByteArray {
        val bytes = n.toByteArray()
        val unsigned = if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) {
            bytes.copyOfRange(1, bytes.size)
        } else {
            bytes
        }

        val result = ByteArray(size)
        val copyLen = minOf(unsigned.size, size)
        for (i in 0 until copyLen) {
            result[i] = unsigned[unsigned.size - 1 - i]
        }
        return result
    }
}
