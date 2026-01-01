package io.monero.crypto

/**
 * Pedersen Commitment implementation for Monero RingCT.
 * 
 * A Pedersen commitment is of the form: C = aG + bH
 * where:
 * - G is the Ed25519 base point
 * - H is a secondary generator point (hash-to-point of G)
 * - a is the blinding factor (mask)
 * - b is the value being committed to
 * 
 * The commitment hides the value while allowing verification that
 * inputs and outputs balance (sum of input commitments = sum of output commitments).
 */
object PedersenCommitment {
    
    /**
     * H generator point for commitments.
     * H = 8 * hash_to_point(G)
     * This is the standard Monero H point used in RingCT.
     */
    val H: ByteArray by lazy {
        // Standard Monero H point (compressed Edwards y-coordinate)
        // This is derived from: H = 8 * hash_to_point(G)
        hexToBytes(
            "8b655970153799af2aeadc9ff1add0ea6c7251d54154cfa92c173a0dd39c1f94"
        )
    }
    
    /**
     * Create a Pedersen commitment: C = mask*G + amount*H
     * 
     * @param mask Blinding factor (32 bytes scalar)
     * @param amount Amount in atomic units
     * @return Commitment point (32 bytes compressed)
     */
    fun commit(mask: ByteArray, amount: Long): ByteArray {
        require(mask.size == 32) { "Mask must be 32 bytes" }
        
        // Convert amount to scalar
        val amountScalar = amountToScalar(amount)
        
        // C = mask*G + amount*H
        val maskG = Ed25519.scalarMultBase(mask)
        val amountH = Ed25519.scalarMult(amountScalar, H)
        
        return Ed25519.pointAdd(maskG, amountH)
    }
    
    /**
     * Create a commitment with a random mask.
     * 
     * @param amount Amount in atomic units
     * @return Pair of (commitment, mask)
     */
    fun commitWithRandomMask(amount: Long): Pair<ByteArray, ByteArray> {
        val mask = generateRandomScalar()
        val commitment = commit(mask, amount)
        return Pair(commitment, mask)
    }
    
    /**
     * Create a zero commitment (used for transparent amounts).
     * C = 0*G + amount*H = amount*H
     */
    fun commitZeroMask(amount: Long): ByteArray {
        val amountScalar = amountToScalar(amount)
        return Ed25519.scalarMult(amountScalar, H)
    }
    
    /**
     * Add two commitments: C1 + C2
     */
    fun addCommitments(c1: ByteArray, c2: ByteArray): ByteArray {
        return Ed25519.pointAdd(c1, c2)
    }
    
    /**
     * Subtract two commitments: C1 - C2
     */
    fun subtractCommitments(c1: ByteArray, c2: ByteArray): ByteArray {
        return Ed25519.pointSub(c1, c2)
    }
    
    /**
     * Sum multiple commitments.
     */
    fun sumCommitments(commitments: List<ByteArray>): ByteArray {
        require(commitments.isNotEmpty()) { "Need at least one commitment" }
        
        var sum = commitments[0]
        for (i in 1 until commitments.size) {
            sum = addCommitments(sum, commitments[i])
        }
        return sum
    }
    
    /**
     * Verify that commitments balance: sum(inputs) = sum(outputs) + fee*H
     * 
     * For a valid transaction:
     * sum(mask_in)*G + sum(amount_in)*H = sum(mask_out)*G + sum(amount_out)*H
     * 
     * Since amounts must balance (minus fee), the masks must also sum correctly.
     */
    fun verifyBalance(
        inputCommitments: List<ByteArray>,
        outputCommitments: List<ByteArray>,
        fee: Long
    ): Boolean {
        if (inputCommitments.isEmpty() || outputCommitments.isEmpty()) return false
        
        val inputSum = sumCommitments(inputCommitments)
        val outputSum = sumCommitments(outputCommitments)
        val feeCommitment = commitZeroMask(fee)
        
        // Input sum should equal output sum + fee
        val expectedSum = addCommitments(outputSum, feeCommitment)
        
        return inputSum.contentEquals(expectedSum)
    }
    
    /**
     * Generate commitment mask that balances the transaction.
     * 
     * Given input masks and output masks (except last), calculate the last output mask
     * so that sum(input_masks) = sum(output_masks)
     */
    fun generateBalancingMask(
        inputMasks: List<ByteArray>,
        outputMasksExceptLast: List<ByteArray>
    ): ByteArray {
        require(inputMasks.isNotEmpty()) { "Need at least one input mask" }
        
        // Sum of input masks
        var inputSum = inputMasks[0].copyOf()
        for (i in 1 until inputMasks.size) {
            inputSum = Ed25519.scalarAdd(inputSum, inputMasks[i])
        }
        
        // Subtract existing output masks
        for (mask in outputMasksExceptLast) {
            inputSum = Ed25519.scalarSub(inputSum, mask)
        }
        
        return inputSum
    }
    
    /**
     * Convert amount to a 32-byte scalar (little-endian).
     */
    fun amountToScalar(amount: Long): ByteArray {
        val result = ByteArray(32)
        var remaining = amount
        for (i in 0 until 8) {
            result[i] = (remaining and 0xFF).toByte()
            remaining = remaining ushr 8
        }
        return result
    }
    
    /**
     * Convert scalar back to amount (assumes it fits in Long).
     */
    fun scalarToAmount(scalar: ByteArray): Long {
        require(scalar.size == 32) { "Scalar must be 32 bytes" }
        
        var amount = 0L
        for (i in 7 downTo 0) {
            amount = (amount shl 8) or (scalar[i].toLong() and 0xFF)
        }
        return amount
    }
    
    /**
     * Generate a random scalar for use as a blinding factor.
     */
    fun generateRandomScalar(): ByteArray {
        val random = kotlin.random.Random.Default
        val bytes = ByteArray(64)
        for (i in bytes.indices) {
            bytes[i] = random.nextInt(256).toByte()
        }
        return Ed25519.scalarReduce64(bytes)
    }
    
    /**
     * Derive a deterministic mask from a shared secret.
     * Used in ECDH to derive the commitment mask.
     * 
     * mask = Hs("commitment_mask" || shared_secret || output_index)
     */
    fun deriveMask(sharedSecret: ByteArray, outputIndex: Int): ByteArray {
        val prefix = "commitment_mask".encodeToByteArray()
        val indexBytes = ByteArray(4)
        indexBytes[0] = (outputIndex and 0xFF).toByte()
        indexBytes[1] = ((outputIndex shr 8) and 0xFF).toByte()
        indexBytes[2] = ((outputIndex shr 16) and 0xFF).toByte()
        indexBytes[3] = ((outputIndex shr 24) and 0xFF).toByte()
        
        val data = prefix + sharedSecret + indexBytes
        return Ed25519.hashToScalar(data)
    }
    
    /**
     * Derive amount mask for ECDH encoding.
     * Used to encrypt the amount for the recipient.
     * 
     * amount_mask = Hs("amount" || shared_secret || output_index)
     */
    fun deriveAmountMask(sharedSecret: ByteArray, outputIndex: Int): ByteArray {
        val prefix = "amount".encodeToByteArray()
        val indexBytes = ByteArray(4)
        indexBytes[0] = (outputIndex and 0xFF).toByte()
        indexBytes[1] = ((outputIndex shr 8) and 0xFF).toByte()
        indexBytes[2] = ((outputIndex shr 16) and 0xFF).toByte()
        indexBytes[3] = ((outputIndex shr 24) and 0xFF).toByte()
        
        val data = prefix + sharedSecret + indexBytes
        return Keccak.hash256(data)
    }
    
    /**
     * Encode (encrypt) amount using ECDH mask.
     */
    fun encodeAmount(amount: Long, amountMask: ByteArray): ByteArray {
        val amountBytes = amountToScalar(amount)
        val encoded = ByteArray(8)
        for (i in 0 until 8) {
            encoded[i] = (amountBytes[i].toInt() xor amountMask[i].toInt()).toByte()
        }
        return encoded
    }
    
    /**
     * Decode (decrypt) amount using ECDH mask.
     */
    fun decodeAmount(encoded: ByteArray, amountMask: ByteArray): Long {
        require(encoded.size == 8) { "Encoded amount must be 8 bytes" }
        
        val decoded = ByteArray(32)
        for (i in 0 until 8) {
            decoded[i] = (encoded[i].toInt() xor amountMask[i].toInt()).toByte()
        }
        return scalarToAmount(decoded)
    }
    
    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
