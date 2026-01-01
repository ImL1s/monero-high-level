package io.monero.crypto

/**
 * Native Poly1305 implementation using a simple BigInt class for 130-bit arithmetic.
 * 
 * This mimics the JVM BigInteger-based implementation.
 */
actual object Poly1305 {

    actual fun mac(key: ByteArray, message: ByteArray): ByteArray {
        require(key.size == 32) { "Key must be 32 bytes, got ${key.size}" }

        // Split key into r and s
        val rBytes = clampR(key.copyOfRange(0, 16))
        val sBytes = key.copyOfRange(16, 32)
        
        val r = BigInt.fromBytesLE(rBytes)
        val s = BigInt.fromBytesLE(sBytes)
        val p = BigInt.P  // 2^130 - 5

        var acc = BigInt.ZERO

        // Process message in 16-byte blocks
        var offset = 0
        while (offset < message.size) {
            val blockSize = minOf(16, message.size - offset)
            val block = ByteArray(blockSize + 1)
            for (i in 0 until blockSize) {
                block[i] = message[offset + i]
            }
            block[blockSize] = 0x01  // Add hibit

            val n = BigInt.fromBytesLE(block)
            acc = acc.add(n)
            acc = acc.mul(r)
            acc = acc.mod(p)

            offset += 16
        }

        // Add s
        acc = acc.add(s)

        // Take lower 128 bits (16 bytes)
        return acc.toBytesLE(16)
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
    
    /**
     * Simple arbitrary precision integer for Poly1305.
     * Only supports non-negative integers.
     * Stores digits in base 2^32 (little-endian order: least significant first).
     */
    private class BigInt private constructor(private val digits: UIntArray) {
        
        companion object {
            val ZERO = BigInt(uintArrayOf(0u))
            
            // 2^130 - 5
            val P: BigInt by lazy {
                // 2^130 = 2^128 * 4, represented in base 2^32
                // 2^130 in base 2^32 is: [0, 0, 0, 0, 4] (5 digits)
                // 2^130 - 5 is: [0xFFFFFFFB, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0x3]
                BigInt(uintArrayOf(0xFFFFFFFBu, 0xFFFFFFFFu, 0xFFFFFFFFu, 0xFFFFFFFFu, 0x3u))
            }
            
            fun fromBytesLE(bytes: ByteArray): BigInt {
                if (bytes.isEmpty()) return ZERO
                
                // Calculate number of 32-bit digits needed
                val numDigits = (bytes.size + 3) / 4
                val digits = UIntArray(numDigits)
                
                for (i in bytes.indices) {
                    val digitIdx = i / 4
                    val shift = (i % 4) * 8
                    digits[digitIdx] = digits[digitIdx] or ((bytes[i].toUInt() and 0xFFu) shl shift)
                }
                
                return BigInt(digits).normalize()
            }
        }
        
        private fun normalize(): BigInt {
            // Remove leading zeros
            var len = digits.size
            while (len > 1 && digits[len - 1] == 0u) len--
            return if (len == digits.size) this else BigInt(digits.copyOf(len))
        }
        
        fun isZero(): Boolean = digits.size == 1 && digits[0] == 0u
        
        fun add(other: BigInt): BigInt {
            val maxLen = maxOf(digits.size, other.digits.size) + 1
            val result = UIntArray(maxLen)
            
            var carry = 0uL
            for (i in 0 until maxLen) {
                val a = if (i < digits.size) digits[i].toULong() else 0uL
                val b = if (i < other.digits.size) other.digits[i].toULong() else 0uL
                val sum = a + b + carry
                result[i] = sum.toUInt()
                carry = sum shr 32
            }
            
            return BigInt(result).normalize()
        }
        
        fun mul(other: BigInt): BigInt {
            if (isZero() || other.isZero()) return ZERO
            
            val result = UIntArray(digits.size + other.digits.size)
            
            for (i in digits.indices) {
                var carry = 0uL
                for (j in other.digits.indices) {
                    val prod = digits[i].toULong() * other.digits[j].toULong() + 
                               result[i + j].toULong() + carry
                    result[i + j] = prod.toUInt()
                    carry = prod shr 32
                }
                result[i + other.digits.size] = (result[i + other.digits.size].toULong() + carry).toUInt()
            }
            
            return BigInt(result).normalize()
        }
        
        fun mod(m: BigInt): BigInt {
            if (compare(m) < 0) return this
            
            var remainder = this
            
            // Simple repeated subtraction with shifting
            // Find the highest bit position of m
            val mBits = m.bitLength()
            val thisBits = remainder.bitLength()
            
            if (thisBits < mBits) return remainder
            
            var shift = thisBits - mBits
            var divisor = m.shiftLeft(shift)
            
            while (shift >= 0) {
                if (remainder.compare(divisor) >= 0) {
                    remainder = remainder.subtract(divisor)
                }
                divisor = divisor.shiftRight(1)
                shift--
            }
            
            return remainder.normalize()
        }
        
        private fun compare(other: BigInt): Int {
            if (digits.size != other.digits.size) {
                return digits.size.compareTo(other.digits.size)
            }
            for (i in digits.size - 1 downTo 0) {
                if (digits[i] != other.digits[i]) {
                    return digits[i].compareTo(other.digits[i])
                }
            }
            return 0
        }
        
        private fun subtract(other: BigInt): BigInt {
            // Assumes this >= other
            val result = UIntArray(digits.size)
            
            var borrow = 0L
            for (i in digits.indices) {
                val a = digits[i].toLong()
                val b = if (i < other.digits.size) other.digits[i].toLong() else 0L
                val diff = a - b - borrow
                if (diff < 0) {
                    result[i] = (diff + 0x100000000L).toUInt()
                    borrow = 1
                } else {
                    result[i] = diff.toUInt()
                    borrow = 0
                }
            }
            
            return BigInt(result).normalize()
        }
        
        private fun bitLength(): Int {
            if (isZero()) return 0
            val topDigit = digits[digits.size - 1]
            val topBits = 32 - topDigit.countLeadingZeroBits()
            return (digits.size - 1) * 32 + topBits
        }
        
        private fun shiftLeft(n: Int): BigInt {
            if (n == 0 || isZero()) return this
            
            val wordShift = n / 32
            val bitShift = n % 32
            
            val newLen = digits.size + wordShift + 1
            val result = UIntArray(newLen)
            
            for (i in digits.indices) {
                result[i + wordShift] = result[i + wordShift] or (digits[i] shl bitShift)
                if (bitShift > 0 && i + wordShift + 1 < newLen) {
                    result[i + wordShift + 1] = result[i + wordShift + 1] or (digits[i] shr (32 - bitShift))
                }
            }
            
            return BigInt(result).normalize()
        }
        
        private fun shiftRight(n: Int): BigInt {
            if (n == 0 || isZero()) return this
            
            val wordShift = n / 32
            val bitShift = n % 32
            
            if (wordShift >= digits.size) return ZERO
            
            val newLen = digits.size - wordShift
            val result = UIntArray(newLen)
            
            for (i in result.indices) {
                result[i] = digits[i + wordShift] shr bitShift
                if (bitShift > 0 && i + wordShift + 1 < digits.size) {
                    result[i] = result[i] or (digits[i + wordShift + 1] shl (32 - bitShift))
                }
            }
            
            return BigInt(result).normalize()
        }
        
        fun toBytesLE(size: Int): ByteArray {
            val result = ByteArray(size)
            for (i in 0 until size) {
                val digitIdx = i / 4
                val shift = (i % 4) * 8
                if (digitIdx < digits.size) {
                    result[i] = ((digits[digitIdx] shr shift) and 0xFFu).toByte()
                }
            }
            return result
        }
    }
}
