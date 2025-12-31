package io.monero.crypto

/**
 * Keccak hash function implementation.
 * Monero uses Keccak-256 (NOT SHA3-256, which has different padding).
 *
 * Reference: https://keccak.team/keccak.html
 */
object Keccak {
    private const val RATE = 1088 / 8  // 136 bytes for Keccak-256
    private const val CAPACITY = 512 / 8  // 64 bytes
    private const val OUTPUT_LENGTH = 256 / 8  // 32 bytes

    // Round constants for Keccak-f[1600]
    private val RC = ulongArrayOf(
        0x0000000000000001uL, 0x0000000000008082uL, 0x800000000000808AuL,
        0x8000000080008000uL, 0x000000000000808BuL, 0x0000000080000001uL,
        0x8000000080008081uL, 0x8000000000008009uL, 0x000000000000008AuL,
        0x0000000000000088uL, 0x0000000080008009uL, 0x000000008000000AuL,
        0x000000008000808BuL, 0x800000000000008BuL, 0x8000000000008089uL,
        0x8000000000008003uL, 0x8000000000008002uL, 0x8000000000000080uL,
        0x000000000000800AuL, 0x800000008000000AuL, 0x8000000080008081uL,
        0x8000000000008080uL, 0x0000000080000001uL, 0x8000000080008008uL
    )

    // Rotation offsets
    private val ROTATIONS = arrayOf(
        intArrayOf(0, 36, 3, 41, 18),
        intArrayOf(1, 44, 10, 45, 2),
        intArrayOf(62, 6, 43, 15, 61),
        intArrayOf(28, 55, 25, 21, 56),
        intArrayOf(27, 20, 39, 8, 14)
    )

    /**
     * Computes Keccak-256 hash of the input data.
     *
     * @param data Input data to hash
     * @return 32-byte hash result
     */
    fun hash256(data: ByteArray): ByteArray {
        return keccak(data, OUTPUT_LENGTH)
    }

    /**
     * Computes Keccak hash with specified output length.
     *
     * @param data Input data to hash
     * @param outputLength Desired output length in bytes
     * @return Hash result of specified length
     */
    fun keccak(data: ByteArray, outputLength: Int): ByteArray {
        val state = ULongArray(25) // 5x5 state matrix

        // Absorb phase
        val padded = pad(data)
        for (i in padded.indices step RATE) {
            val block = padded.copyOfRange(i, minOf(i + RATE, padded.size))
            absorb(state, block)
            keccakF(state)
        }

        // Squeeze phase
        return squeeze(state, outputLength)
    }

    private fun pad(data: ByteArray): ByteArray {
        // Keccak padding: 10*1 pattern (NOT SHA3 which uses 0110*1)
        val padLen = RATE - (data.size % RATE)
        val padded = ByteArray(data.size + padLen)
        data.copyInto(padded)

        if (padLen == 1) {
            padded[data.size] = 0x81.toByte()
        } else {
            padded[data.size] = 0x01.toByte()
            padded[padded.size - 1] = 0x80.toByte()
        }

        return padded
    }

    private fun absorb(state: ULongArray, block: ByteArray) {
        for (i in 0 until minOf(block.size / 8, RATE / 8)) {
            var lane = 0uL
            for (j in 0 until 8) {
                if (i * 8 + j < block.size) {
                    lane = lane or ((block[i * 8 + j].toULong() and 0xFFuL) shl (j * 8))
                }
            }
            state[i] = state[i] xor lane
        }
    }

    private fun squeeze(state: ULongArray, outputLength: Int): ByteArray {
        val output = ByteArray(outputLength)
        var offset = 0

        while (offset < outputLength) {
            for (i in 0 until minOf(RATE / 8, (outputLength - offset + 7) / 8)) {
                val lane = state[i]
                for (j in 0 until 8) {
                    if (offset + j < outputLength) {
                        output[offset + j] = (lane shr (j * 8)).toByte()
                    }
                }
                offset += 8
                if (offset >= outputLength) break
            }
            if (offset < outputLength) {
                keccakF(state)
            }
        }

        return output
    }

    private fun keccakF(state: ULongArray) {
        for (round in 0 until 24) {
            theta(state)
            rhoPi(state)
            chi(state)
            iota(state, round)
        }
    }

    private fun theta(state: ULongArray) {
        val c = ULongArray(5)
        val d = ULongArray(5)

        for (x in 0 until 5) {
            c[x] = state[x] xor state[x + 5] xor state[x + 10] xor state[x + 15] xor state[x + 20]
        }

        for (x in 0 until 5) {
            d[x] = c[(x + 4) % 5] xor rotateLeft(c[(x + 1) % 5], 1)
        }

        for (x in 0 until 5) {
            for (y in 0 until 5) {
                state[x + y * 5] = state[x + y * 5] xor d[x]
            }
        }
    }

    private fun rhoPi(state: ULongArray) {
        val temp = ULongArray(25)
        for (x in 0 until 5) {
            for (y in 0 until 5) {
                val newX = y
                val newY = (2 * x + 3 * y) % 5
                temp[newX + newY * 5] = rotateLeft(state[x + y * 5], ROTATIONS[x][y])
            }
        }
        temp.copyInto(state)
    }

    private fun chi(state: ULongArray) {
        for (y in 0 until 5) {
            val row = ULongArray(5) { state[it + y * 5] }
            for (x in 0 until 5) {
                state[x + y * 5] = row[x] xor (row[(x + 1) % 5].inv() and row[(x + 2) % 5])
            }
        }
    }

    private fun iota(state: ULongArray, round: Int) {
        state[0] = state[0] xor RC[round]
    }

    private fun rotateLeft(value: ULong, bits: Int): ULong {
        return (value shl bits) or (value shr (64 - bits))
    }
}
