package io.monero.crypto

/**
 * Bulletproofs+ (Range Proof) high-level placeholder implementation.
 *
 * In real Monero, Bulletproofs+ proves an amount committed in a Pedersen commitment
 * is within a 64-bit range without revealing the amount.
 *
 * This codebase currently uses simplified curve operations (see `Ed25519`), so this
 * implementation focuses on:
 * - API shape
 * - determinism
 * - basic range enforcement and commitment binding
 *
 * IMPORTANT: This is NOT a real zero-knowledge proof.
 */
object BulletproofPlus {

    const val DEFAULT_RANGE_BITS: Int = 64

    data class Proof(
        /** Commitment C = mask*G + amount*H (compressed point, 32 bytes). */
        val commitment: ByteArray,
        /** Number of range bits (Monero uses 64). */
        val rangeBits: Int,
        /** Opening mask (32-byte scalar). */
        val openingMask: ByteArray,
        /** Opening amount (uses Kotlin Long; effectively 0..Long.MAX_VALUE). */
        val openingAmount: Long,
        /** Transcript hash binding the proof to its inputs (32 bytes). */
        val transcript: ByteArray
    ) {
        init {
            require(commitment.size == 32) { "Commitment must be 32 bytes" }
            require(openingMask.size == 32) { "Opening mask must be 32 bytes" }
            require(transcript.size == 32) { "Transcript must be 32 bytes" }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Proof) return false

            return commitment.contentEquals(other.commitment) &&
                rangeBits == other.rangeBits &&
                openingMask.contentEquals(other.openingMask) &&
                openingAmount == other.openingAmount &&
                transcript.contentEquals(other.transcript)
        }

        override fun hashCode(): Int {
            var result = commitment.contentHashCode()
            result = 31 * result + rangeBits
            result = 31 * result + openingMask.contentHashCode()
            result = 31 * result + openingAmount.hashCode()
            result = 31 * result + transcript.contentHashCode()
            return result
        }
    }

    /**
     * Generate a range proof for a commitment.
     *
     * Note: This placeholder proof includes the opening (mask + amount). A real
     * Bulletproofs+ proof does NOT reveal these values.
     */
    fun prove(
        mask: ByteArray,
        amount: Long,
        rangeBits: Int = DEFAULT_RANGE_BITS,
        transcriptSalt: ByteArray = ByteArray(0)
    ): Proof {
        require(mask.size == 32) { "Mask must be 32 bytes" }
        require(rangeBits in 1..64) { "rangeBits must be in 1..64" }
        require(amount >= 0) { "Amount must be non-negative" }
        require(isAmountInRange(amount, rangeBits)) { "Amount out of range for $rangeBits-bit proof" }

        val commitment = PedersenCommitment.commit(mask, amount)
        val transcript = computeTranscript(commitment, rangeBits, mask, amount, transcriptSalt)

        return Proof(
            commitment = commitment,
            rangeBits = rangeBits,
            openingMask = mask.copyOf(),
            openingAmount = amount,
            transcript = transcript
        )
    }

    /**
     * Verify a proof against its embedded commitment.
     */
    fun verify(proof: Proof, transcriptSalt: ByteArray = ByteArray(0)): Boolean {
        return verify(proof.commitment, proof, transcriptSalt)
    }

    /**
     * Verify a proof against an explicit commitment.
     */
    fun verify(commitment: ByteArray, proof: Proof, transcriptSalt: ByteArray = ByteArray(0)): Boolean {
        if (commitment.size != 32) return false
        if (!commitment.contentEquals(proof.commitment)) return false
        if (proof.rangeBits !in 1..64) return false
        if (proof.openingAmount < 0) return false
        if (!isAmountInRange(proof.openingAmount, proof.rangeBits)) return false

        val recomputedCommitment = PedersenCommitment.commit(proof.openingMask, proof.openingAmount)
        if (!recomputedCommitment.contentEquals(commitment)) return false

        val expectedTranscript = computeTranscript(
            commitment = commitment,
            rangeBits = proof.rangeBits,
            mask = proof.openingMask,
            amount = proof.openingAmount,
            transcriptSalt = transcriptSalt
        )

        return expectedTranscript.contentEquals(proof.transcript)
    }

    private fun isAmountInRange(amount: Long, rangeBits: Int): Boolean {
        if (amount < 0) return false
        return when {
            rangeBits >= 63 -> amount >= 0
            else -> amount <= ((1L shl rangeBits) - 1L)
        }
    }

    private fun computeTranscript(
        commitment: ByteArray,
        rangeBits: Int,
        mask: ByteArray,
        amount: Long,
        transcriptSalt: ByteArray
    ): ByteArray {
        val domain = "BulletproofPlus".encodeToByteArray()
        val rb = byteArrayOf(rangeBits.toByte())
        val amt = longToLe8(amount)
        val data = domain + commitment + rb + mask + amt + transcriptSalt
        return Keccak.hash256(data)
    }

    private fun longToLe8(value: Long): ByteArray {
        val out = ByteArray(8)
        var v = value
        for (i in 0 until 8) {
            out[i] = (v and 0xFF).toByte()
            v = v ushr 8
        }
        return out
    }
}
