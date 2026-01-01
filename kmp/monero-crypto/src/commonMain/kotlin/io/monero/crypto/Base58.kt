package io.monero.crypto

/**
 * Monero-specific Base58 encoding/decoding.
 *
 * Monero uses a modified Base58 encoding that differs from Bitcoin's Base58Check:
 * - Different alphabet (reordered to avoid similar-looking characters)
 * - Different checksum algorithm (Keccak instead of double SHA-256)
 * - Block-based encoding (8 bytes → 11 chars)
 *
 * Reference: https://monerodocs.org/cryptography/base58/
 */
object Base58 {
    /**
     * Monero's Base58 alphabet (different from Bitcoin's)
     */
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    /**
     * Sizes for block encoding
     * Full block: 8 bytes → 11 characters
     */
    private val ENCODED_BLOCK_SIZES = intArrayOf(0, 2, 3, 5, 6, 7, 9, 10, 11)
    private const val FULL_BLOCK_SIZE = 8
    private const val FULL_ENCODED_BLOCK_SIZE = 11

    /**
     * Encode data to Base58 with Monero's checksum
     *
     * @param data Raw data to encode
     * @return Base58 encoded string with checksum
     */
    fun encode(data: ByteArray): String {
        // Add 4-byte Keccak checksum
        val checksum = Keccak.hash256(data).copyOfRange(0, 4)
        val dataWithChecksum = data + checksum

        return encodeRaw(dataWithChecksum)
    }

    /**
     * Decode Base58 string with checksum verification
     *
     * @param encoded Base58 encoded string
     * @return Decoded data (without checksum)
     * @throws IllegalArgumentException if checksum is invalid
     */
    fun decode(encoded: String): ByteArray {
        val dataWithChecksum = decodeRaw(encoded)

        if (dataWithChecksum.size < 4) {
            throw IllegalArgumentException("Encoded data too short")
        }

        val data = dataWithChecksum.copyOfRange(0, dataWithChecksum.size - 4)
        val checksum = dataWithChecksum.copyOfRange(dataWithChecksum.size - 4, dataWithChecksum.size)

        // Verify checksum
        val expectedChecksum = Keccak.hash256(data).copyOfRange(0, 4)
        if (!checksum.contentEquals(expectedChecksum)) {
            throw IllegalArgumentException("Invalid checksum")
        }

        return data
    }

    /**
     * Encode raw data to Base58 (no checksum)
     */
    fun encodeRaw(data: ByteArray): String {
        if (data.isEmpty()) return ""

        val result = StringBuilder()

        // Process full 8-byte blocks
        var offset = 0
        while (offset + FULL_BLOCK_SIZE <= data.size) {
            result.append(encodeBlock(data.copyOfRange(offset, offset + FULL_BLOCK_SIZE)))
            offset += FULL_BLOCK_SIZE
        }

        // Process remaining bytes
        if (offset < data.size) {
            result.append(encodeBlock(data.copyOfRange(offset, data.size)))
        }

        return result.toString()
    }

    /**
     * Decode raw Base58 string (no checksum verification)
     */
    fun decodeRaw(encoded: String): ByteArray {
        if (encoded.isEmpty()) return ByteArray(0)

        val result = mutableListOf<Byte>()

        // Process full 11-character blocks
        var offset = 0
        while (offset + FULL_ENCODED_BLOCK_SIZE <= encoded.length) {
            result.addAll(decodeBlock(encoded.substring(offset, offset + FULL_ENCODED_BLOCK_SIZE), FULL_BLOCK_SIZE).toList())
            offset += FULL_ENCODED_BLOCK_SIZE
        }

        // Process remaining characters
        if (offset < encoded.length) {
            val remaining = encoded.length - offset
            val blockSize = ENCODED_BLOCK_SIZES.indexOf(remaining)
            if (blockSize == -1) {
                throw IllegalArgumentException("Invalid Base58 string length")
            }
            result.addAll(decodeBlock(encoded.substring(offset), blockSize).toList())
        }

        return result.toByteArray()
    }

    /**
     * Encode a single block (up to 8 bytes)
     */
    private fun encodeBlock(block: ByteArray): String {
        require(block.size <= FULL_BLOCK_SIZE) { "Block too large" }

        // Convert bytes to a big integer
        var num = 0UL
        for (byte in block) {
            num = num * 256UL + (byte.toInt() and 0xFF).toULong()
        }

        // Determine output size
        val outputSize = if (block.size == FULL_BLOCK_SIZE) {
            FULL_ENCODED_BLOCK_SIZE
        } else {
            ENCODED_BLOCK_SIZES[block.size]
        }

        // Convert to base58
        val chars = CharArray(outputSize)
        for (i in outputSize - 1 downTo 0) {
            chars[i] = ALPHABET[(num % 58UL).toInt()]
            num /= 58UL
        }

        return chars.concatToString()
    }

    /**
     * Decode a single Base58 block
     */
    private fun decodeBlock(block: String, expectedSize: Int): ByteArray {
        // Convert from base58 to big integer
        var num = 0UL
        for (char in block) {
            val digit = ALPHABET.indexOf(char)
            if (digit == -1) {
                throw IllegalArgumentException("Invalid Base58 character: $char")
            }
            num = num * 58UL + digit.toULong()
        }

        // Convert to bytes
        val result = ByteArray(expectedSize)
        for (i in expectedSize - 1 downTo 0) {
            result[i] = (num and 0xFFUL).toByte()
            num = num shr 8
        }

        return result
    }

    /**
     * Check if a string is valid Base58
     */
    fun isValid(encoded: String): Boolean {
        return encoded.all { it in ALPHABET }
    }

    /**
     * Validate a Monero address format
     *
     * @param address Base58 encoded address
     * @return True if the address has valid format and checksum
     */
    fun validateAddress(address: String): Boolean {
        return try {
            val decoded = decode(address)
            // Standard address: 69 bytes (1 prefix + 64 keys + 4 checksum in encoded)
            // Subaddress: 69 bytes
            // Integrated address: 77 bytes (1 prefix + 64 keys + 8 payment ID + 4 checksum)
            decoded.size in listOf(65, 69, 73, 77)
        } catch (e: Exception) {
            false
        }
    }
}
