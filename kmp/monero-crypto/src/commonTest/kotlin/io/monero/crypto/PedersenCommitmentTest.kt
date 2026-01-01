package io.monero.crypto

import kotlin.test.*

class PedersenCommitmentTest {
    
    @Test
    fun testAmountToScalar() {
        val amount = 1_000_000_000L  // 1 XMR
        val scalar = PedersenCommitment.amountToScalar(amount)
        
        assertEquals(32, scalar.size)
        
        // Convert back
        val recovered = PedersenCommitment.scalarToAmount(scalar)
        assertEquals(amount, recovered)
    }
    
    @Test
    fun testAmountToScalarZero() {
        val scalar = PedersenCommitment.amountToScalar(0)
        
        assertEquals(32, scalar.size)
        assertTrue(scalar.all { it == 0.toByte() })
    }
    
    @Test
    fun testAmountToScalarMaxValue() {
        val amount = Long.MAX_VALUE
        val scalar = PedersenCommitment.amountToScalar(amount)
        val recovered = PedersenCommitment.scalarToAmount(scalar)
        
        assertEquals(amount, recovered)
    }
    
    @Test
    fun testCommitment() {
        val mask = ByteArray(32) { 0x01 }
        val amount = 1_000_000_000L
        
        val commitment = PedersenCommitment.commit(mask, amount)
        
        assertEquals(32, commitment.size)
        // Commitment should not be all zeros
        assertFalse(commitment.all { it == 0.toByte() })
    }
    
    @Test
    fun testCommitWithRandomMask() {
        val amount = 1_000_000_000L
        
        val (commitment, mask) = PedersenCommitment.commitWithRandomMask(amount)
        
        assertEquals(32, commitment.size)
        assertEquals(32, mask.size)
        
        // Recreate commitment with the same mask
        val recreated = PedersenCommitment.commit(mask, amount)
        assertContentEquals(commitment, recreated)
    }
    
    @Test
    fun testCommitZeroMask() {
        val amount = 1_000_000L
        
        val commitment = PedersenCommitment.commitZeroMask(amount)
        
        assertEquals(32, commitment.size)
        // This is just amount*H
    }
    
    @Test
    fun testAddCommitments() {
        val mask1 = ByteArray(32) { 0x01 }
        val mask2 = ByteArray(32) { 0x02 }
        val amount1 = 100_000_000L
        val amount2 = 200_000_000L
        
        val c1 = PedersenCommitment.commit(mask1, amount1)
        val c2 = PedersenCommitment.commit(mask2, amount2)
        
        val sum = PedersenCommitment.addCommitments(c1, c2)
        
        assertEquals(32, sum.size)
        // The sum should be a valid point (non-zero)
        assertFalse(sum.all { it == 0.toByte() })
    }
    
    @Test
    fun testSumCommitments() {
        val commitments = listOf(
            PedersenCommitment.commit(ByteArray(32) { 0x01 }, 100_000_000),
            PedersenCommitment.commit(ByteArray(32) { 0x02 }, 200_000_000),
            PedersenCommitment.commit(ByteArray(32) { 0x03 }, 300_000_000)
        )
        
        val sum = PedersenCommitment.sumCommitments(commitments)
        
        assertEquals(32, sum.size)
    }
    
    @Test
    fun testGenerateBalancingMask() {
        val inputMask1 = ByteArray(32) { 0x10 }
        val inputMask2 = ByteArray(32) { 0x20 }
        val outputMask1 = ByteArray(32) { 0x05 }
        
        val balancingMask = PedersenCommitment.generateBalancingMask(
            inputMasks = listOf(inputMask1, inputMask2),
            outputMasksExceptLast = listOf(outputMask1)
        )
        
        assertEquals(32, balancingMask.size)
        
        // Verify: inputMask1 + inputMask2 = outputMask1 + balancingMask
        val inputSum = Ed25519.scalarAdd(inputMask1, inputMask2)
        val outputSum = Ed25519.scalarAdd(outputMask1, balancingMask)
        assertContentEquals(inputSum, outputSum)
    }
    
    @Test
    fun testGenerateRandomScalar() {
        val scalar1 = PedersenCommitment.generateRandomScalar()
        val scalar2 = PedersenCommitment.generateRandomScalar()
        
        assertEquals(32, scalar1.size)
        assertEquals(32, scalar2.size)
        
        // Should be different (with very high probability)
        assertFalse(scalar1.contentEquals(scalar2))
    }
    
    @Test
    fun testDeriveMask() {
        val sharedSecret = ByteArray(32) { (it * 2).toByte() }
        
        val mask0 = PedersenCommitment.deriveMask(sharedSecret, 0)
        val mask1 = PedersenCommitment.deriveMask(sharedSecret, 1)
        
        assertEquals(32, mask0.size)
        assertEquals(32, mask1.size)
        
        // Different indices should give different masks
        assertFalse(mask0.contentEquals(mask1))
        
        // Same inputs should be deterministic
        val mask0Again = PedersenCommitment.deriveMask(sharedSecret, 0)
        assertContentEquals(mask0, mask0Again)
    }
    
    @Test
    fun testEncodeDecodeAmount() {
        val amount = 12_345_678_900L
        val amountMask = ByteArray(32) { (it * 3).toByte() }
        
        val encoded = PedersenCommitment.encodeAmount(amount, amountMask)
        assertEquals(8, encoded.size)
        
        val decoded = PedersenCommitment.decodeAmount(encoded, amountMask)
        assertEquals(amount, decoded)
    }
    
    @Test
    fun testEncodeDecodeAmountZero() {
        val amount = 0L
        val amountMask = ByteArray(32) { 0xFF.toByte() }
        
        val encoded = PedersenCommitment.encodeAmount(amount, amountMask)
        val decoded = PedersenCommitment.decodeAmount(encoded, amountMask)
        
        assertEquals(0L, decoded)
    }
    
    @Test
    fun testDeriveAmountMask() {
        val sharedSecret = ByteArray(32) { it.toByte() }
        
        val mask = PedersenCommitment.deriveAmountMask(sharedSecret, 0)
        
        assertEquals(32, mask.size)
        
        // Should be deterministic
        val maskAgain = PedersenCommitment.deriveAmountMask(sharedSecret, 0)
        assertContentEquals(mask, maskAgain)
    }
    
    @Test
    fun testHPoint() {
        val h = PedersenCommitment.H
        
        assertEquals(32, h.size)
        // H should be a valid point
        assertTrue(Ed25519.isValidPoint(h))
    }
}
