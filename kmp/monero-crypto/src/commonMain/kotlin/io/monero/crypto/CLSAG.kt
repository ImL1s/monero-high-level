package io.monero.crypto

/**
 * CLSAG (Compact Linkable Spontaneous Anonymous Group) signature implementation.
 *
 * CLSAG is the ring signature scheme used in Monero since protocol v13 (2020).
 * It provides:
 * - Anonymity: signer is hidden among ring members
 * - Linkability: double-spending is detectable via key images
 * - Compactness: O(1) signature size vs O(n) for older schemes
 *
 * Reference: https://eprint.iacr.org/2019/654.pdf
 */
object CLSAG {

    /**
     * CLSAG signature structure
     */
    data class Signature(
        val c1: ByteArray,           // Initial challenge (32 bytes)
        val s: List<ByteArray>,      // Response scalars (n x 32 bytes)
        val keyImage: ByteArray,     // Key image I (32 bytes)
        val D: ByteArray             // Auxiliary commitment key image (32 bytes)
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Signature) return false
            return c1.contentEquals(other.c1) &&
                   s.size == other.s.size &&
                   s.zip(other.s).all { (a, b) -> a.contentEquals(b) } &&
                   keyImage.contentEquals(other.keyImage) &&
                   D.contentEquals(other.D)
        }

        override fun hashCode(): Int {
            var result = c1.contentHashCode()
            result = 31 * result + s.fold(0) { acc, bytes -> acc + bytes.contentHashCode() }
            result = 31 * result + keyImage.contentHashCode()
            result = 31 * result + D.contentHashCode()
            return result
        }
    }

    /**
     * Ring member data for CLSAG signing
     */
    data class RingMember(
        val publicKey: ByteArray,    // P_i: stealth address public key
        val commitment: ByteArray    // C_i: Pedersen commitment
    )

    /**
     * Generate a CLSAG signature
     *
     * @param message The message/transaction prefix hash to sign (32 bytes)
     * @param ring List of ring members (public keys + commitments)
     * @param realIndex Index of the real input in the ring (secret)
     * @param privateKey Private key of the real input (32 bytes)
     * @param privateCommitmentKey Private key for commitment (z = mask difference)
     * @return CLSAG signature
     */
    fun sign(
        message: ByteArray,
        ring: List<RingMember>,
        realIndex: Int,
        privateKey: ByteArray,
        privateCommitmentKey: ByteArray
    ): Signature {
        require(message.size == 32) { "Message must be 32 bytes" }
        require(ring.isNotEmpty()) { "Ring must not be empty" }
        require(realIndex in ring.indices) { "Real index out of bounds" }
        require(privateKey.size == 32) { "Private key must be 32 bytes" }
        require(privateCommitmentKey.size == 32) { "Private commitment key must be 32 bytes" }

        val n = ring.size
        val realMember = ring[realIndex]

        // Compute key image: I = x * Hp(P)
        val keyImage = Ed25519.computeKeyImage(privateKey, realMember.publicKey)

        // Compute auxiliary key image: D = z * Hp(P)
        val hp = Ed25519.hashToPoint(realMember.publicKey)
        val D = Ed25519.scalarMult(privateCommitmentKey, hp)

        // Compute aggregate public keys for the ring
        // mu_P and mu_C are domain-separated hashes
        val muP = computeMuP(message, ring, keyImage, D)
        val muC = computeMuC(message, ring, keyImage, D)

        // Compute W_i = mu_P * P_i + mu_C * C_i for each ring member
        val W = ring.map { member ->
            val term1 = Ed25519.scalarMult(muP, member.publicKey)
            val term2 = Ed25519.scalarMult(muC, member.commitment)
            pointAdd(term1, term2)
        }

        // Generate random scalar alpha for real input
        val alpha = generateRandomScalar()

        // Generate random scalars s_i for fake inputs
        val s = MutableList(n) { i ->
            if (i == realIndex) ByteArray(32) else generateRandomScalar()
        }

        // Compute L_realIndex = alpha * G and R_realIndex = alpha * Hp(P)
        val L_real = Ed25519.scalarMultBase(alpha)
        val R_real = Ed25519.scalarMult(alpha, hp)

        // Compute challenges in a ring starting from realIndex + 1
        val c = MutableList(n) { ByteArray(32) }

        // c_{realIndex+1} = H(domain || ... || L_real || R_real)
        val nextIndex = (realIndex + 1) % n
        c[nextIndex] = computeChallenge(message, ring, keyImage, D, W, L_real, R_real)

        // Compute remaining challenges
        for (offset in 1 until n) {
            val i = (realIndex + offset) % n
            val j = (i + 1) % n

            // L_i = s_i * G + c_i * W_i
            val L_i = pointAdd(
                Ed25519.scalarMultBase(s[i]),
                Ed25519.scalarMult(c[i], W[i])
            )

            // R_i = s_i * Hp(P_i) + c_i * (mu_P * I + mu_C * D)
            val hp_i = Ed25519.hashToPoint(ring[i].publicKey)
            val aggregateImage = pointAdd(
                Ed25519.scalarMult(muP, keyImage),
                Ed25519.scalarMult(muC, D)
            )
            val R_i = pointAdd(
                Ed25519.scalarMult(s[i], hp_i),
                Ed25519.scalarMult(c[i], aggregateImage)
            )

            c[j] = computeChallenge(message, ring, keyImage, D, W, L_i, R_i)
        }

        // Compute s for real input: s_realIndex = alpha - c_realIndex * (mu_P * x + mu_C * z)
        val aggregatePrivateKey = Ed25519.scalarAdd(
            Ed25519.scalarMul(muP, privateKey),
            Ed25519.scalarMul(muC, privateCommitmentKey)
        )
        s[realIndex] = Ed25519.scalarSub(alpha, Ed25519.scalarMul(c[realIndex], aggregatePrivateKey))

        return Signature(
            c1 = c[0],
            s = s.toList(),
            keyImage = keyImage,
            D = D
        )
    }

    /**
     * Verify a CLSAG signature
     *
     * @param message The message that was signed (32 bytes)
     * @param ring List of ring members
     * @param signature The CLSAG signature to verify
     * @return true if signature is valid
     */
    fun verify(
        message: ByteArray,
        ring: List<RingMember>,
        signature: Signature
    ): Boolean {
        require(message.size == 32) { "Message must be 32 bytes" }
        require(ring.isNotEmpty()) { "Ring must not be empty" }
        require(signature.s.size == ring.size) { "Signature size mismatch" }

        val n = ring.size

        // Verify key image is in prime-order subgroup
        if (!Ed25519.isValidPoint(signature.keyImage)) return false

        // Compute mu_P and mu_C
        val muP = computeMuP(message, ring, signature.keyImage, signature.D)
        val muC = computeMuC(message, ring, signature.keyImage, signature.D)

        // Compute W_i = mu_P * P_i + mu_C * C_i
        val W = ring.map { member ->
            val term1 = Ed25519.scalarMult(muP, member.publicKey)
            val term2 = Ed25519.scalarMult(muC, member.commitment)
            pointAdd(term1, term2)
        }

        // Compute aggregate key image
        val aggregateImage = pointAdd(
            Ed25519.scalarMult(muP, signature.keyImage),
            Ed25519.scalarMult(muC, signature.D)
        )

        // Recompute challenges starting from c[0]
        val c = MutableList(n) { ByteArray(32) }
        c[0] = signature.c1.copyOf()

        for (i in 0 until n) {
            val j = (i + 1) % n

            // L_i = s_i * G + c_i * W_i
            val L_i = pointAdd(
                Ed25519.scalarMultBase(signature.s[i]),
                Ed25519.scalarMult(c[i], W[i])
            )

            // R_i = s_i * Hp(P_i) + c_i * aggregateImage
            val hp_i = Ed25519.hashToPoint(ring[i].publicKey)
            val R_i = pointAdd(
                Ed25519.scalarMult(signature.s[i], hp_i),
                Ed25519.scalarMult(c[i], aggregateImage)
            )

            if (j == 0) {
                // Check if computed c[0] matches signature.c1
                val computed_c0 = computeChallenge(message, ring, signature.keyImage, signature.D, W, L_i, R_i)
                return computed_c0.contentEquals(signature.c1)
            } else {
                c[j] = computeChallenge(message, ring, signature.keyImage, signature.D, W, L_i, R_i)
            }
        }

        return false
    }

    /**
     * Compute mu_P = H("CLSAG_agg_0" || domain_data)
     */
    private fun computeMuP(
        message: ByteArray,
        ring: List<RingMember>,
        keyImage: ByteArray,
        D: ByteArray
    ): ByteArray {
        val domain = "CLSAG_agg_0".encodeToByteArray()
        val data = buildDomainData(domain, message, ring, keyImage, D)
        return Ed25519.scalarReduce64(Keccak.hash256(data) + ByteArray(32))
    }

    /**
     * Compute mu_C = H("CLSAG_agg_1" || domain_data)
     */
    private fun computeMuC(
        message: ByteArray,
        ring: List<RingMember>,
        keyImage: ByteArray,
        D: ByteArray
    ): ByteArray {
        val domain = "CLSAG_agg_1".encodeToByteArray()
        val data = buildDomainData(domain, message, ring, keyImage, D)
        return Ed25519.scalarReduce64(Keccak.hash256(data) + ByteArray(32))
    }

    /**
     * Compute round challenge c = H("CLSAG_round" || ... || L || R)
     */
    private fun computeChallenge(
        message: ByteArray,
        ring: List<RingMember>,
        keyImage: ByteArray,
        D: ByteArray,
        W: List<ByteArray>,
        L: ByteArray,
        R: ByteArray
    ): ByteArray {
        val domain = "CLSAG_round".encodeToByteArray()
        val data = buildList {
            add(domain)
            add(message)
            ring.forEach {
                add(it.publicKey)
                add(it.commitment)
            }
            add(keyImage)
            add(D)
            W.forEach { add(it) }
            add(L)
            add(R)
        }.fold(ByteArray(0)) { acc, bytes -> acc + bytes }

        return Ed25519.scalarReduce64(Keccak.hash256(data) + ByteArray(32))
    }

    private fun buildDomainData(
        domain: ByteArray,
        message: ByteArray,
        ring: List<RingMember>,
        keyImage: ByteArray,
        D: ByteArray
    ): ByteArray {
        return buildList {
            add(domain)
            add(message)
            ring.forEach {
                add(it.publicKey)
                add(it.commitment)
            }
            add(keyImage)
            add(D)
        }.fold(ByteArray(0)) { acc, bytes -> acc + bytes }
    }

    /**
     * Point addition (wrapper for Ed25519)
     */
    private fun pointAdd(p1: ByteArray, p2: ByteArray): ByteArray {
        // Use Keccak hash as a placeholder for proper point addition
        // TODO: Implement proper Ed25519 point addition
        if (p1.all { it == 0.toByte() }) return p2
        if (p2.all { it == 0.toByte() }) return p1
        return Keccak.hash256(p1 + p2)
    }

    /**
     * Generate a random scalar (32 bytes)
     */
    private fun generateRandomScalar(): ByteArray {
        // Use system random or secure random
        val bytes = ByteArray(64)
        kotlin.random.Random.nextBytes(bytes)
        return Ed25519.scalarReduce64(bytes)
    }
}
