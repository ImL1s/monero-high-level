package io.monero.crypto

/**
 * ChaCha20 stream cipher implementation.
 * 
 * This is the core cipher used in ChaCha20-Poly1305 authenticated encryption,
 * which Monero uses for wallet file encryption.
 * 
 * Implementation based on RFC 8439 (ChaCha20 and Poly1305 for IETF Protocols).
 */
object ChaCha20 {

    private const val BLOCK_SIZE = 64  // 512 bits = 64 bytes
    private const val KEY_SIZE = 32    // 256 bits
    private const val NONCE_SIZE = 12  // 96 bits for IETF ChaCha20

    /**
     * Encrypt/decrypt data using ChaCha20 stream cipher.
     * ChaCha20 is a symmetric cipher, so encrypt and decrypt are the same operation (XOR).
     * 
     * @param key 32-byte key
     * @param nonce 12-byte nonce (IETF variant)
     * @param counter Initial counter value (usually 0 or 1)
     * @param data Data to encrypt/decrypt
     * @return Encrypted/decrypted data
     */
    fun cipher(key: ByteArray, nonce: ByteArray, counter: UInt, data: ByteArray): ByteArray {
        require(key.size == KEY_SIZE) { "Key must be $KEY_SIZE bytes, got ${key.size}" }
        require(nonce.size == NONCE_SIZE) { "Nonce must be $NONCE_SIZE bytes, got ${nonce.size}" }

        if (data.isEmpty()) return ByteArray(0)

        val result = ByteArray(data.size)
        var blockCounter = counter
        var offset = 0

        while (offset < data.size) {
            val keystreamBlock = generateBlock(key, nonce, blockCounter)
            val bytesToProcess = minOf(BLOCK_SIZE, data.size - offset)

            for (i in 0 until bytesToProcess) {
                result[offset + i] = (data[offset + i].toInt() xor keystreamBlock[i].toInt()).toByte()
            }

            offset += BLOCK_SIZE
            blockCounter++
        }

        return result
    }

    /**
     * Generate a single 64-byte keystream block.
     */
    internal fun generateBlock(key: ByteArray, nonce: ByteArray, counter: UInt): ByteArray {
        // Initialize state: constant, key, counter, nonce
        val state = initializeState(key, nonce, counter)

        // Copy for working state
        val workingState = state.copyOf()

        // 20 rounds (10 double rounds)
        repeat(10) {
            quarterRound(workingState, 0, 4, 8, 12)
            quarterRound(workingState, 1, 5, 9, 13)
            quarterRound(workingState, 2, 6, 10, 14)
            quarterRound(workingState, 3, 7, 11, 15)
            quarterRound(workingState, 0, 5, 10, 15)
            quarterRound(workingState, 1, 6, 11, 12)
            quarterRound(workingState, 2, 7, 8, 13)
            quarterRound(workingState, 3, 4, 9, 14)
        }

        // Add original state
        for (i in 0..15) {
            workingState[i] = (workingState[i] + state[i]).toUInt()
        }

        // Serialize to bytes (little-endian)
        return serializeState(workingState)
    }

    private fun initializeState(key: ByteArray, nonce: ByteArray, counter: UInt): UIntArray {
        val state = UIntArray(16)

        // Constants "expand 32-byte k"
        state[0] = 0x61707865u  // "expa"
        state[1] = 0x3320646eu  // "nd 3"
        state[2] = 0x79622d32u  // "2-by"
        state[3] = 0x6b206574u  // "te k"

        // Key (8 words = 32 bytes)
        for (i in 0..7) {
            state[4 + i] = littleEndianToUInt(key, i * 4)
        }

        // Counter (1 word = 4 bytes)
        state[12] = counter

        // Nonce (3 words = 12 bytes)
        for (i in 0..2) {
            state[13 + i] = littleEndianToUInt(nonce, i * 4)
        }

        return state
    }

    private fun quarterRound(state: UIntArray, a: Int, b: Int, c: Int, d: Int) {
        state[a] = (state[a] + state[b]).toUInt(); state[d] = rotl(state[d] xor state[a], 16)
        state[c] = (state[c] + state[d]).toUInt(); state[b] = rotl(state[b] xor state[c], 12)
        state[a] = (state[a] + state[b]).toUInt(); state[d] = rotl(state[d] xor state[a], 8)
        state[c] = (state[c] + state[d]).toUInt(); state[b] = rotl(state[b] xor state[c], 7)
    }

    private fun rotl(x: UInt, n: Int): UInt = (x shl n) or (x shr (32 - n))

    private fun littleEndianToUInt(bytes: ByteArray, offset: Int): UInt {
        return (bytes[offset].toUByte().toUInt()) or
                (bytes[offset + 1].toUByte().toUInt() shl 8) or
                (bytes[offset + 2].toUByte().toUInt() shl 16) or
                (bytes[offset + 3].toUByte().toUInt() shl 24)
    }

    private fun serializeState(state: UIntArray): ByteArray {
        val result = ByteArray(64)
        for (i in 0..15) {
            result[i * 4] = state[i].toByte()
            result[i * 4 + 1] = (state[i] shr 8).toByte()
            result[i * 4 + 2] = (state[i] shr 16).toByte()
            result[i * 4 + 3] = (state[i] shr 24).toByte()
        }
        return result
    }
}
